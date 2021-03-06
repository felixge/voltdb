/* This file is part of VoltDB.
 * Copyright (C) 2008-2012 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.sysprocs.saverestore;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.voltdb.MockVoltDB;
import org.voltdb.VoltDB;
import org.voltdb.VoltSystemProcedure.SynthesizedPlanFragment;
import org.voltdb.VoltTable;
import org.voltdb.catalog.Table;
import org.voltdb.sysprocs.SysProcFragmentId;

public class TestReplicatedTableSaveFileState extends TestCase
{
    private static final String TABLE_NAME = "test_table";
    private static final String DATABASE_NAME = "database";

    @Override
    public void setUp()
    {
        m_state = new ReplicatedTableSaveFileState(TABLE_NAME, 0);
        m_siteInput =
            ClusterSaveFileState.constructEmptySaveFileStateVoltTable();
    }

    public void testLoadOperation()
    {
        assertEquals(m_state.getTableName(), TABLE_NAME);

        addHostToTestData(0);
        addHostToTestData(1);
        addHostToTestData(3);
        m_siteInput.resetRowPosition();
        while (m_siteInput.advanceRow())
        {
            try
            {
                // this will add the active row of m_siteInput
                m_state.addHostData(m_siteInput);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                assertTrue(false);
            }
        }
        assertTrue(m_state.isConsistent());
        assertTrue(m_state.getConsistencyResult().
                   contains("has consistent savefile state"));
        Set<Integer> sites = m_state.getHostsWithThisTable();
        assertTrue(sites.contains(0));
        assertTrue(sites.contains(1));
        assertFalse(sites.contains(2));
        assertTrue(sites.contains(3));
    }

    public void testInconsistentIsReplicated()
    {
        addHostToTestData(0);
        addBadHostToTestData(1);
        m_siteInput.resetRowPosition();
        while (m_siteInput.advanceRow())
        {
            try
            {
                // this will add the active row of m_siteInput
                m_state.addHostData(m_siteInput);
            }
            catch (IOException e)
            {
                assertTrue(m_state.getConsistencyResult().
                           contains("but has a savefile which indicates partitioning at site"));
                return;
            }
        }
        assertTrue(false);
    }

    // Things that should get added:
    // Add some non-exec sites
    //

    /*
     * Test the easiest possible restore plan: table is replicated before and
     * after save/restore, and every site has a copy of the table
     */
    public void testEasyRestorePlan() throws Exception
    {
        MockVoltDB catalog_creator =
            new MockVoltDB();
        VoltDB.replaceVoltDBInstanceForTest(catalog_creator);
        catalog_creator.addTable(TABLE_NAME, true);

        int number_of_sites = 4;

        for (int i = 0; i < number_of_sites; ++i)
        {
            addHostToTestData(i);
            catalog_creator.addPartition(i);
            catalog_creator.addHost(i);
            catalog_creator.addSite(i, i, i, true);
        }
        // Add some non-exec sites for more test coverage
        catalog_creator.addSite(number_of_sites, 0, 0, false);
        catalog_creator.addSite(number_of_sites + 1, 1, 0, false);
        m_siteInput.resetRowPosition();
        while (m_siteInput.advanceRow())
        {
            try
            {
                // this will add the active row of m_siteInput
                m_state.addHostData(m_siteInput);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                assertTrue(false);
            }
        }

        Table test_table = catalog_creator.getTable(TABLE_NAME);

        SynthesizedPlanFragment[] test_plan =
            m_state.generateRestorePlan(test_table);
        assertEquals(test_plan.length, number_of_sites + 1);
        for (int i = 0; i < number_of_sites - 1; ++i)
        {
            assertEquals(test_plan[i].fragmentId,
                         SysProcFragmentId.PF_restoreLoadReplicatedTable);
            assertFalse(test_plan[i].multipartition);
            assertEquals(test_plan[i].siteId, i);
            assertEquals(test_plan[i].parameters.toArray()[0], TABLE_NAME);
        }
        assertEquals(test_plan[number_of_sites].fragmentId,
                     SysProcFragmentId.PF_restoreLoadReplicatedTableResults);
        assertFalse(test_plan[number_of_sites].multipartition);
        checkPlanDependencies(test_plan);
        assertEquals(test_plan[number_of_sites].parameters.toArray()[0],
                     m_state.getRootDependencyId());
        catalog_creator.shutdown(null);
    }

    /*
     * Test the restore plan when one of the sites doesn't have access to
     * a copy of the table
     */
    public void testSiteMissingTableRestorePlan() throws Exception
    {
        MockVoltDB catalog_creator = new MockVoltDB();
        VoltDB.replaceVoltDBInstanceForTest(catalog_creator);
        catalog_creator.addTable(TABLE_NAME, true);

        int number_of_sites = 4;

        for (int i = 0; i < number_of_sites - 1; ++i)
        {
            addHostToTestData(i);
            catalog_creator.addPartition(i);
            catalog_creator.addHost(i);
            catalog_creator.addSite(i, i, i, true);
        }
        catalog_creator.addPartition(number_of_sites - 1);
        catalog_creator.addHost(number_of_sites - 1);
        catalog_creator.addSite(number_of_sites - 1,
                                number_of_sites - 1,
                                number_of_sites - 1,
                                true);
        // Add some non-exec sites for more test coverage
        catalog_creator.addSite(number_of_sites, 0, 0, false);
        catalog_creator.addSite(number_of_sites + 1, 1, 0, false);

        m_siteInput.resetRowPosition();
        while (m_siteInput.advanceRow())
        {
            try
            {
                // this will add the active row of m_siteInput
                m_state.addHostData(m_siteInput);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                assertTrue(false);
            }
        }

        Table test_table = catalog_creator.getTable(TABLE_NAME);

        SynthesizedPlanFragment[] test_plan =
            m_state.generateRestorePlan(test_table);
        assertEquals(test_plan.length, number_of_sites + 1);
        for (int i = 0; i < number_of_sites - 1; ++i)
        {
            assertEquals(test_plan[i].fragmentId,
                         SysProcFragmentId.PF_restoreLoadReplicatedTable);
            assertFalse(test_plan[i].multipartition);
            assertEquals(test_plan[i].siteId, i);
            assertEquals(test_plan[i].parameters.toArray()[0], TABLE_NAME);
        }
        assertEquals(test_plan[number_of_sites - 1].fragmentId,
                     SysProcFragmentId.PF_restoreDistributeReplicatedTable);
        assertEquals(test_plan[number_of_sites - 1].siteId, 0);
        assertFalse(test_plan[number_of_sites - 1].multipartition);
        assertEquals(test_plan[number_of_sites - 1].parameters.toArray()[0],
                     TABLE_NAME);
        assertEquals(test_plan[number_of_sites - 1].parameters.toArray()[1], 3);

        assertEquals(test_plan[number_of_sites].fragmentId,
                     SysProcFragmentId.PF_restoreLoadReplicatedTableResults);
        assertFalse(test_plan[number_of_sites].multipartition);
        checkPlanDependencies(test_plan);
        assertEquals(test_plan[number_of_sites].parameters.toArray()[0],
                     m_state.getRootDependencyId());
        catalog_creator.shutdown(null);
    }

    private void addHostToTestData(int hostId)
    {
        m_siteInput.addRow(hostId, "host", hostId, "ohost", "cluster", DATABASE_NAME,
                           TABLE_NAME, 0, "TRUE", 0, 1);
    }

    private void addBadHostToTestData(int hostId)
    {
        m_siteInput.addRow(hostId, "host", hostId, "ohost", "cluster", DATABASE_NAME,
                           TABLE_NAME, 0, "FALSE", 0, 2);
    }

    private void checkPlanDependencies(SynthesizedPlanFragment[] plan)
    {
        Set<Integer> aggregate_deps = new HashSet<Integer>();
        for (int dependency_id : plan[plan.length - 1].inputDepIds)
        {
            aggregate_deps.add(dependency_id);
        }
        Set<Integer> plan_deps = new HashSet<Integer>();
        for (int i = 0; i < plan.length - 1; ++i)
        {
            int fragment_dep = -1;
            if (plan[i].fragmentId ==
                SysProcFragmentId.PF_restoreLoadReplicatedTable)
            {
                fragment_dep = (Integer) plan[i].parameters.toArray()[1];
            }
            else if (plan[i].fragmentId ==
                SysProcFragmentId.PF_restoreDistributeReplicatedTable)
            {
                fragment_dep = (Integer) plan[i].parameters.toArray()[2];
            }
            plan_deps.add(fragment_dep);
        }
        assertTrue(aggregate_deps.equals(plan_deps));
    }

    private ReplicatedTableSaveFileState m_state;
    private VoltTable m_siteInput;
}
