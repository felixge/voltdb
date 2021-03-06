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

import java.util.ArrayList;

import org.hsqldb_voltpatches.VoltXMLElement;
import org.voltdb.catalog.Database;
import org.voltdb.expressions.AbstractExpression;
import org.voltdb.expressions.ExpressionUtil;
import org.voltdb.expressions.TupleValueExpression;

public class ParsedSelectStmt extends AbstractParsedStmt {

    public static class ParsedColInfo {
        public String alias;
        public String columnName;
        public String tableName;
        public AbstractExpression expression;
        public boolean finalOutput = true;
        public int index = 0;
        public int size;

        // orderby stuff
        public boolean orderBy = false;
        public boolean ascending = true;

        // groupby
        public boolean groupBy = false;
    }

    public ArrayList<ParsedColInfo> displayColumns = new ArrayList<ParsedColInfo>();
    public ArrayList<ParsedColInfo> orderColumns = new ArrayList<ParsedColInfo>();
    public AbstractExpression having = null;
    public ArrayList<ParsedColInfo> groupByColumns = new ArrayList<ParsedColInfo>();

    public long limit = -1;
    public long offset = 0;
    public long limitParameterId = -1;
    public long offsetParameterId = -1;
    public boolean grouped = false;
    public boolean distinct = false;

    @Override
    void parse(VoltXMLElement stmtNode, Database db) {
        String node;

        if ((node = stmtNode.attributes.get("limit")) != null)
            limit = Long.parseLong(node);
        if ((node = stmtNode.attributes.get("offset")) != null)
            offset = Long.parseLong(node);
        if ((node = stmtNode.attributes.get("limit_paramid")) != null)
            limitParameterId = Long.parseLong(node);
        if ((node = stmtNode.attributes.get("offset_paramid")) != null)
            offsetParameterId = Long.parseLong(node);
        if ((node = stmtNode.attributes.get("grouped")) != null)
            grouped = Boolean.parseBoolean(node);
        if ((node = stmtNode.attributes.get("distinct")) != null)
            distinct = Boolean.parseBoolean(node);

        // limit and offset can't have both value and parameter
        if (limit != -1) assert limitParameterId == -1 : "Parsed value and param. limit.";
        if (offset != 0) assert offsetParameterId == -1 : "Parsed value and param. offset.";

        for (VoltXMLElement child : stmtNode.children) {
            if (child.name.equalsIgnoreCase("columns"))
                parseDisplayColumns(child, db);
            else if (child.name.equalsIgnoreCase("querycondition"))
                parseQueryCondition(child, db);
            else if (child.name.equalsIgnoreCase("ordercolumns"))
                parseOrderColumns(child, db);
            else if (child.name.equalsIgnoreCase("groupcolumns")) {
                parseGroupByColumns(child, db);
            }
        }
    }

    void parseDisplayColumns(VoltXMLElement columnsNode, Database db) {
        for (VoltXMLElement child : columnsNode.children) {
            ParsedColInfo col = new ParsedColInfo();
            col.expression = parseExpressionTree(child, db);
            ExpressionUtil.assignLiteralConstantTypesRecursively(col.expression);
            ExpressionUtil.assignOutputValueTypesRecursively(col.expression);
            assert(col.expression != null);
            col.alias = child.attributes.get("alias");

            if (child.name.equals("columnref")) {
                col.columnName =
                    child.attributes.get("column");
                col.tableName =
                    child.attributes.get("table");
            }
            else
            {
                // XXX hacky, assume all non-column refs come from a temp table
                col.tableName = "VOLT_TEMP_TABLE";
                col.columnName = "";
            }
            // This index calculation is only used for sanity checking
            // materialized views (which use the parsed select statement but
            // don't go through the planner pass that does more involved
            // column index resolution).
            col.index = displayColumns.size();
            displayColumns.add(col);
        }
    }

    void parseQueryCondition(VoltXMLElement conditionNode, Database db) {
        if (conditionNode.children.size() == 0)
            return;

        VoltXMLElement exprNode = conditionNode.children.get(0);
        where = parseExpressionTree(exprNode, db);
        ExpressionUtil.assignLiteralConstantTypesRecursively(where);
        ExpressionUtil.assignOutputValueTypesRecursively(where);
    }

    void parseGroupByColumns(VoltXMLElement columnsNode, Database db) {
        for (VoltXMLElement child : columnsNode.children) {
            parseGroupByColumn(child, db);
        }
    }

    void parseGroupByColumn(VoltXMLElement groupByNode, Database db) {

        ParsedColInfo col = new ParsedColInfo();
        col.expression = parseExpressionTree(groupByNode, db);
        assert(col.expression != null);

        if (groupByNode.name.equals("columnref"))
        {
            col.alias = groupByNode.attributes.get("alias");
            col.columnName = groupByNode.attributes.get("column");
            col.tableName = groupByNode.attributes.get("table");
            col.groupBy = true;
        }
        else
        {
            throw new RuntimeException("GROUP BY with complex expressions not yet supported");
        }

        assert(col.alias.equalsIgnoreCase(col.columnName));
        assert(db.getTables().getIgnoreCase(col.tableName) != null);
        assert(db.getTables().getIgnoreCase(col.tableName).getColumns().getIgnoreCase(col.columnName) != null);
        org.voltdb.catalog.Column catalogColumn =
            db.getTables().getIgnoreCase(col.tableName).getColumns().getIgnoreCase(col.columnName);
        col.index = catalogColumn.getIndex();
        groupByColumns.add(col);
    }

    void parseOrderColumns(VoltXMLElement columnsNode, Database db) {
        for (VoltXMLElement child : columnsNode.children) {
            parseOrderColumn(child, db);
        }
    }

    void parseOrderColumn(VoltXMLElement orderByNode, Database db) {
        // make sure everything is kosher
        assert(orderByNode.name.equalsIgnoreCase("operation"));
        String operationType = orderByNode.attributes.get("type");
        assert(operationType != null);
        assert(operationType.equalsIgnoreCase("orderby"));

        // get desc/asc
        String desc = orderByNode.attributes.get("desc");
        boolean descending = (desc != null) && (desc.equalsIgnoreCase("true"));

        // get the columnref expression inside the orderby node
        VoltXMLElement child = orderByNode.children.get(0);
        assert(child != null);

        // Cases:
        // inner child could be columnref, in which case it's just a normal
        // column.  Just make a ParsedColInfo object for it and the planner
        // will do the right thing later
        if (child.name.equals("columnref"))
        {
            String alias = child.attributes.get("alias");
            // create the orderby column
            ParsedColInfo col = new ParsedColInfo();
            col.alias = alias;
            col.expression = parseExpressionTree(child, db);
            ExpressionUtil.assignLiteralConstantTypesRecursively(col.expression);
            ExpressionUtil.assignOutputValueTypesRecursively(col.expression);
            col.columnName = child.attributes.get("column");
            col.tableName = child.attributes.get("table");
            col.orderBy = true;
            col.ascending = !descending;
            orderColumns.add(col);
        }
        // inner child could be an operation, which forks into two subcases:
        else if (child.name.equals("operation"))
        {
            ParsedColInfo order_col = new ParsedColInfo();
            order_col.columnName = "";
            order_col.orderBy = true;
            order_col.ascending = !descending;
            // I'm not sure anyone actually cares about this table name
            order_col.tableName = "VOLT_TEMP_TABLE";
            AbstractExpression order_exp = parseExpressionTree(child, db);
            // 2) It's a simplecolumn operation.  This means that the alias that
            //    we have should refer to a column that we compute
            //    somewhere else (like an aggregate, mostly).
            //    Look up that column in the displayColumns list,
            //    and then create a new order by column with a magic TVE.  This
            //    means that we can only ORDER BY a pre-computed expression
            //    that is also in the display columns, but I'm not sure there's
            //    a way to not do that that is valid SQL
            if (order_exp instanceof TupleValueExpression)
            {
                String alias = child.attributes.get("alias");
                ParsedColInfo orig_col = null;
                for (ParsedColInfo col : displayColumns)
                {
                    if (col.alias.equals(alias))
                    {
                        orig_col = col;
                    }
                }
                // We need the original column expression so we can extract
                // the value size and type for our TVE that refers back to it
                if (orig_col == null)
                {
                    throw new PlanningErrorException("Unable to find source " +
                                                     "column for simplecolumn: " +
                                                     alias);
                }
                assert(orig_col.tableName.equals("VOLT_TEMP_TABLE"));
                // Construct our fake TVE that will point back at the input
                // column.
                TupleValueExpression tve = (TupleValueExpression) order_exp;
                tve.setColumnAlias(alias);
                tve.setColumnName("");
                tve.setColumnIndex(-1);
                tve.setTableName("VOLT_TEMP_TABLE");
                tve.setValueSize(orig_col.expression.getValueSize());
                tve.setValueType(orig_col.expression.getValueType());
                order_col.alias = alias;
                order_col.expression = tve;
            }
            // 1) it's an actual complex expression, which we will (for now)
            //    compute in the order by node.  Create a new column, hand it the
            //    parsed expression, and stick it in the order by columns.
            else
            {
                ExpressionUtil.assignLiteralConstantTypesRecursively(order_exp);
                ExpressionUtil.assignOutputValueTypesRecursively(order_exp);
                order_col.expression = order_exp;
            }
            orderColumns.add(order_col);
        }
        else
        {
            throw new RuntimeException("ORDER BY parsed with strange child node type: " + child.name);
        }
    }

    @Override
    public String toString() {
        String retval = super.toString() + "\n";

        retval += "LIMIT " + String.valueOf(limit) + "\n";
        retval += "OFFSET " + String.valueOf(limit) + "\n";

        retval += "DISPLAY COLUMNS:\n";
        for (ParsedColInfo col : displayColumns) {
            retval += "\tColumn: " + col.alias + ": ";
            retval += col.expression.toString() + "\n";
        }

        retval += "ORDER COLUMNS:\n";
        for (ParsedColInfo col : orderColumns) {
            retval += "\tColumn: " + col.alias + ": ASC?: " + col.ascending + ": ";
            retval += col.expression.toString() + "\n";
        }

        retval += "GROUP_BY COLUMNS:\n";
        for (ParsedColInfo col : groupByColumns) {
            retval += "\tColumn: " + col.alias + ": ";
            retval += col.expression.toString() + "\n";
        }

        retval = retval.trim();

        return retval;
    }
}
