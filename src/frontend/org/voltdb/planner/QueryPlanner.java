/* This file is part of VoltDB.
 * Copyright (C) 2008-2012 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.planner;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

import org.hsqldb_voltpatches.HSQLInterface;
import org.hsqldb_voltpatches.HSQLInterface.HSQLParseException;
import org.hsqldb_voltpatches.VoltXMLElement;
import org.json_voltpatches.JSONException;
import org.json_voltpatches.JSONObject;
import org.voltdb.catalog.Cluster;
import org.voltdb.catalog.Database;
import org.voltdb.compiler.DatabaseEstimates;
import org.voltdb.compiler.ScalarValueHints;
import org.voltdb.planner.microoptimizations.MicroOptimizationRunner;
import org.voltdb.plannodes.AbstractPlanNode;
import org.voltdb.plannodes.PlanNodeList;
import org.voltdb.utils.BuildDirectoryUtils;

/**
 * The query planner accepts catalog data, SQL statements from the catalog, then
 * outputs the plan with the lowest cost according to the cost model.
 *
 */
public class QueryPlanner {
    PlanAssembler m_assembler;
    HSQLInterface m_HSQL;
    DatabaseEstimates m_estimates;
    Cluster m_cluster;
    Database m_db;
    String m_recentErrorMsg;
    boolean m_useGlobalIds;
    boolean m_quietPlanner;
    final boolean m_fullDebug;

    /**
     * Initialize planner with physical schema info and a reference to HSQLDB parser.
     *
     * @param catalogCluster Catalog info about the physical layout of the cluster.
     * @param catalogDb Catalog info about schema, metadata and procedures.
     * @param HSQL HSQLInterface pointer used for parsing SQL into XML.
     * @param useGlobalIds
     */
    public QueryPlanner(Cluster catalogCluster, Database catalogDb,
                        HSQLInterface HSQL, DatabaseEstimates estimates,
                        boolean useGlobalIds, boolean suppressDebugOutput) {
        assert(HSQL != null);
        assert(catalogCluster != null);
        assert(catalogDb != null);

        m_HSQL = HSQL;
        m_assembler = new PlanAssembler(catalogCluster, catalogDb);
        m_db = catalogDb;
        m_cluster = catalogCluster;
        m_estimates = estimates;
        m_useGlobalIds = useGlobalIds;
        m_quietPlanner = suppressDebugOutput;
        m_fullDebug = System.getProperties().contains("compilerdebug");
    }

    /**
     * Get the best plan for the SQL statement given, assuming the given costModel.
     *
     * @param costModel The current cost model to evaluate plans with.
     * @param sql SQL stmt text to be planned.
     * @param sql Suggested join order to be used for the query
     * @param stmtName The name of the sql statement to be planned.
     * @param procName The name of the procedure containing the sql statement to be planned.
     * @param singlePartition Is the stmt single-partition?
     * @param paramHints
     * @return The best plan found for the SQL statement or null if none can be found.
     */
    public CompiledPlan compilePlan(
            AbstractCostModel costModel,
            String sql,
            String joinOrder,
            String stmtName,
            String procName,
            boolean singlePartition,
            int maxTablesPerJoin,
            ScalarValueHints[] paramHints) {
        assert(costModel != null);
        assert(sql != null);
        assert(stmtName != null);
        assert(procName != null);

        // reset any error message
        m_recentErrorMsg = null;

        // set the usage of global ids in the plan assembler
        AbstractPlanNode.setUseGlobalIds(m_useGlobalIds);

        // use HSQLDB to get XML that describes the semantics of the statement
        // this is much easier to parse than SQL and is checked against the catalog
        VoltXMLElement xmlSQL = null;
        try {
            xmlSQL = m_HSQL.getXMLCompiledStatement(sql);
        } catch (HSQLParseException e) {
            // XXXLOG probably want a real log message here
            m_recentErrorMsg = e.getMessage();
            return null;
        }

        if (!m_quietPlanner && m_fullDebug) {
            // output the xml from hsql to disk for debugging
            PrintStream xmlDebugOut =
                BuildDirectoryUtils.getDebugOutputPrintStream("statement-hsql-xml", procName + "_" + stmtName + ".xml");
            xmlDebugOut.println(xmlSQL.toString());
            xmlDebugOut.close();
        }

        // get a parsed statement from the xml
        AbstractParsedStmt initialParsedStmt = null;
        try {
            initialParsedStmt = AbstractParsedStmt.parse(sql, xmlSQL, m_db, joinOrder);
        }
        catch (Exception e) {
            m_recentErrorMsg = e.getMessage();
            return null;
        }
        if (initialParsedStmt == null)
        {
            m_recentErrorMsg = "Failed to parse SQL statement: " + sql;
            return null;
        }
        if ((initialParsedStmt.tableList.size() > maxTablesPerJoin) && (initialParsedStmt.joinOrder == null)) {
            m_recentErrorMsg = "Failed to parse SQL statement: " + sql + " because a join of > 5 tables was requested"
                               + " without specifying a join order. See documentation for instructions on manually" +
                                 " specifying a join order";
            return null;
        }

        if (!m_quietPlanner && m_fullDebug) {
            // output a description of the parsed stmt
            PrintStream parsedDebugOut =
                BuildDirectoryUtils.getDebugOutputPrintStream("statement-parsed", procName + "_" + stmtName + ".txt");
            parsedDebugOut.println(initialParsedStmt.toString());
            parsedDebugOut.close();
        }

        // get ready to find the plan with minimal cost
        CompiledPlan rawplan = null;
        CompiledPlan bestPlan = null;
        String bestFilename = null;
        double minCost = Double.MAX_VALUE;

        // index of the currently being "costed" plan
        int i = 0;

        PlanStatistics stats = null;

        // iterate though all the variations on the abstract parsed stmts
        for (AbstractParsedStmt parsedStmt : ExpressionEquivalenceProcessor.getEquivalentStmts(initialParsedStmt)) {

            // set up the plan assembler for this particular plan
            m_assembler.setupForNewPlans(parsedStmt, singlePartition);

            // loop over all possible plans
            while (true) {

                try {
                    rawplan = m_assembler.getNextPlan();
                }
                // on exception, set the error message and bail...
                catch (PlanningErrorException e) {
                    m_recentErrorMsg = e.getMessage();
                    return null;
                }

                // stop this while loop when no more plans are generated
                if (rawplan == null)
                    break;

                // run the set of microptimizations, which may return many plans (or not)
                List<CompiledPlan> optimizedPlans = MicroOptimizationRunner.applyAll(rawplan);

                // iterate through the subset of plans
                for (CompiledPlan plan : optimizedPlans) {

                    // add in the sql to the plan
                    plan.sql = sql;

                    // this plan is final, resolve all the column index references
                    plan.fragments.get(0).planGraph.resolveColumnIndexes();

                    // compute resource usage using the single stats collector
                    stats = new PlanStatistics();
                    AbstractPlanNode planGraph = plan.fragments.get(0).planGraph;

                    // compute statistics about a plan
                    boolean result = planGraph.computeEstimatesRecursively(stats, m_cluster, m_db, m_estimates, paramHints);
                    assert(result);

                    // compute the cost based on the resources using the current cost model
                    plan.cost = costModel.getPlanCost(stats);

                    // filename for debug output
                    String filename = String.valueOf(i++);

                    // find the minimum cost plan
                    if (plan.cost < minCost) {
                        minCost = plan.cost;
                        // free the PlanColumns held by the previous best plan
                        bestPlan = plan;
                        bestFilename = filename;
                    }

                    // GENERATE JSON DEBUGGING OUTPUT BEFORE WE CLEAN UP THE
                    // PlanColumns
                    // convert a tree into an execution list
                    PlanNodeList nodeList = new PlanNodeList(planGraph);

                    // get the json serialized version of the plan
                    String json = null;

                    if (!m_quietPlanner) {
                        if (m_fullDebug) {
                            try {
                                String crunchJson = nodeList.toJSONString();
                                //System.out.println(crunchJson);
                                //System.out.flush();
                                JSONObject jobj = new JSONObject(crunchJson);
                                json = jobj.toString(4);
                            } catch (JSONException e2) {
                                // Any plan that can't be serialized to JSON to
                                // write to debugging output is also going to fail
                                // to get written to the catalog, to sysprocs, etc.
                                // Just bail.
                                m_recentErrorMsg = "Plan for sql: '" + sql +
                                                   "' can't be serialized to JSON";
                                return null;
                            }

                            // output a description of the parsed stmt
                            json = "PLAN:\n" + json;
                            json = "COST: " + String.valueOf(plan.cost) + "\n" + json;
                            assert (plan.sql != null);
                            json = "SQL: " + plan.sql + "\n" + json;

                            // write json to disk
                            PrintStream candidatePlanOut =
                                    BuildDirectoryUtils.getDebugOutputPrintStream("statement-all-plans/" + procName + "_" + stmtName,
                                                                                  filename + "-json.txt");
                            candidatePlanOut.println(json);
                            candidatePlanOut.close();

                            // create a graph friendly version
                            candidatePlanOut =
                                    BuildDirectoryUtils.getDebugOutputPrintStream("statement-all-plans/" + procName + "_" + stmtName,
                                                                                  filename + ".dot");
                            candidatePlanOut.println(nodeList.toDOTString("name"));
                            candidatePlanOut.close();
                        }

                        // get the explained plan for the node
                        plan.explainedPlan = planGraph.toExplainPlanString();
                        PrintStream candidatePlanOut =
                                BuildDirectoryUtils.getDebugOutputPrintStream("statement-all-plans/" + procName + "_" + stmtName,
                                                                              filename + ".txt");

                        candidatePlanOut.println(plan.explainedPlan);
                        candidatePlanOut.close();
                    }
                }
            }
        }

        // make sure we got a winner
        if (bestPlan == null) {
            m_recentErrorMsg = "Unable to plan for statement. Error unknown.";
            return null;
        }

        // reset all the plan node ids for a given plan
        // this makes the ids deterministic
        bestPlan.resetPlanNodeIds();

        if (!m_quietPlanner)
        {
            // find out where debugging is going
            String prefix = BuildDirectoryUtils.getBuildDirectoryPath() +
                    "/" + BuildDirectoryUtils.rootPath + "statement-all-plans/" +
                    procName + "_" + stmtName + "/";
            String winnerFilename, winnerFilenameRenamed;
            File winnerFile, winnerFileRenamed;

            // if outputting full stuff
            if (m_fullDebug) {
                // rename the winner json plan
                winnerFilename = prefix + bestFilename + "-json.txt";
                winnerFile = new File(winnerFilename);
                winnerFilenameRenamed = prefix + "WINNER-" + bestFilename + "-json.txt";
                winnerFileRenamed = new File(winnerFilenameRenamed);
                winnerFile.renameTo(winnerFileRenamed);

                // rename the winner dot plan
                winnerFilename = prefix + bestFilename + ".dot";
                winnerFile = new File(winnerFilename);
                winnerFilenameRenamed = prefix + "WINNER-" + bestFilename + ".dot";
                winnerFileRenamed = new File(winnerFilenameRenamed);
                winnerFile.renameTo(winnerFileRenamed);
            }

            // rename the winner explain plan
            winnerFilename = prefix + bestFilename + ".txt";
            winnerFile = new File(winnerFilename);
            winnerFilenameRenamed = prefix + "WINNER-" + bestFilename + ".txt";
            winnerFileRenamed = new File(winnerFilenameRenamed);
            winnerFile.renameTo(winnerFileRenamed);

            if (m_fullDebug) {
                // output the plan statistics to disk for debugging
                PrintStream plansOut =
                    BuildDirectoryUtils.getDebugOutputPrintStream("statement-stats", procName + "_" + stmtName + ".txt");
                plansOut.println(stats.toString());
                plansOut.close();
            }
        }

        // split up the plan everywhere we see send/recieve into multiple plan fragments
        bestPlan = Fragmentizer.fragmentize(bestPlan, m_db);

        // DTXN/EE can't handle plans that have more than 2 fragments yet.
        if (bestPlan.fragments.size() > 2) {
            m_recentErrorMsg = "Unable to plan for statement. Possibly " +
                "joining partitioned tables in a multi-partition procedure " +
                "using a column that is not the partitioning attribute " +
                "or a non-equality operator. " +
                "This is statement not supported at this time.";
            return null;
        }

        return bestPlan;
    }

    public String getErrorMessage() {
        return m_recentErrorMsg;
    }
}
