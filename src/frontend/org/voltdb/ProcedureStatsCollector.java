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

package org.voltdb;

import java.util.ArrayList;
import java.util.Iterator;

import org.voltdb.catalog.Procedure;
import org.voltdb.logging.VoltLogger;

/**
 * Derivation of StatsSource to expose timing information of procedure invocations.
 *
 */
final class ProcedureStatsCollector extends SiteStatsSource {

    private static final VoltLogger log = new VoltLogger("HOST");

    /**
     * Record procedure execution time ever N invocations
     */
    final int timeCollectionInterval = 20;

    /**
     * Number of times this procedure has been invoked.
     */
    private long m_invocations = 0;
    private long m_lastInvocations = 0;

    /**
     * Number of timed invocations
     */
    private long m_timedInvocations = 0;
    private long m_lastTimedInvocations = 0;

    /**
     * Total amount of timed execution time
     */
    private long m_totalTimedExecutionTime = 0;
    private long m_lastTotalTimedExecutionTime = 0;

    /**
     * Shortest amount of time this procedure has executed in
     */
    private long m_minExecutionTime = Long.MAX_VALUE;
    private long m_lastMinExecutionTime = Long.MAX_VALUE;

    /**
     * Longest amount of time this procedure has executed in
     */
    private long m_maxExecutionTime = Long.MIN_VALUE;
    private long m_lastMaxExecutionTime = Long.MIN_VALUE;

    /**
     * Time the procedure was last started
     */
    private long m_currentStartTime = -1;

    /**
     * Count of the number of aborts (user initiated or DB initiated)
     */
    private long m_abortCount = 0;
    private long m_lastAbortCount = 0;

    /**
     * Count of the number of errors that occured during procedure execution
     */
    private long m_failureCount = 0;
    private long m_lastFailureCount = 0;

    /**
     * Whether to return results in intervals since polling or since the beginning
     */
    private boolean m_interval = false;

    private final Procedure m_catProc;
    private final int m_partitionId;

    public ProcedureStatsCollector(int siteId, int partitionId, Procedure catProc) {
        super(siteId, false);
        m_catProc = catProc;
        m_partitionId = partitionId;
    }

    /**
     * Called when a procedure begins executing. Caches the time the procedure starts.
     */
    public final void beginProcedure() {
        if (m_invocations % timeCollectionInterval == 0) {
            m_currentStartTime = System.nanoTime();
        }
    }

    /**
     * Called after a procedure is finished executing. Compares the start and end time and calculates
     * the statistics.
     */
    public final void endProcedure(boolean aborted, boolean failed) {
        if (m_currentStartTime > 0) {
            final long endTime = System.nanoTime();
            final long delta = endTime - m_currentStartTime;
            if (delta < 0)
            {
                if (Math.abs(delta) > 1000000000)
                {
                    log.info("Procedure: " + m_catProc.getTypeName() +
                             " recorded a negative execution time larger than one second: " +
                             delta);
                }
            }
            else
            {
                m_totalTimedExecutionTime += delta;
                m_timedInvocations++;
                m_minExecutionTime = Math.min( delta, m_minExecutionTime);
                m_maxExecutionTime = Math.max( delta, m_maxExecutionTime);
                m_lastMinExecutionTime = Math.min( delta, m_lastMinExecutionTime);
                m_lastMaxExecutionTime = Math.max( delta, m_lastMaxExecutionTime);
            }
            m_currentStartTime = -1;
        }
        if (aborted) {
            m_abortCount++;
        }
        if (failed) {
            m_failureCount++;
        }
        m_invocations++;
    }

    /**
     * Update the rowValues array with the latest statistical information.
     * This method is overrides the super class version
     * which must also be called so that it can update its columns.
     * @param values Values of each column of the row of stats. Used as output.
     */
    @Override
    protected void updateStatsRow(Object rowKey, Object rowValues[]) {
        super.updateStatsRow(rowKey, rowValues);
        rowValues[columnNameToIndex.get("PARTITION_ID")] = m_partitionId;
        rowValues[columnNameToIndex.get("PROCEDURE")] = m_catProc.getClassname();
        long invocations = m_invocations;
        long totalTimedExecutionTime = m_totalTimedExecutionTime;
        long timedInvocations = m_timedInvocations;
        long minExecutionTime = m_minExecutionTime;
        long maxExecutionTime = m_maxExecutionTime;
        long abortCount = m_abortCount;
        long failureCount = m_failureCount;


        if (m_interval) {
            invocations = m_invocations - m_lastInvocations;
            m_lastInvocations = m_invocations;

            totalTimedExecutionTime = m_totalTimedExecutionTime - m_lastTotalTimedExecutionTime;
            m_lastTotalTimedExecutionTime = m_totalTimedExecutionTime;

            timedInvocations = m_timedInvocations - m_lastTimedInvocations;
            m_lastTimedInvocations = m_timedInvocations;

            abortCount = m_abortCount - m_lastAbortCount;
            m_lastAbortCount = m_abortCount;

            failureCount = m_failureCount - m_lastFailureCount;
            m_lastFailureCount = m_failureCount;

            minExecutionTime = m_lastMinExecutionTime;
            maxExecutionTime = m_lastMaxExecutionTime;
            m_lastMinExecutionTime = Long.MAX_VALUE;
            m_lastMaxExecutionTime = Long.MIN_VALUE;
        }

        rowValues[columnNameToIndex.get("INVOCATIONS")] = invocations;
        rowValues[columnNameToIndex.get("TIMED_INVOCATIONS")] = timedInvocations;
        rowValues[columnNameToIndex.get("MIN_EXECUTION_TIME")] = minExecutionTime;
        rowValues[columnNameToIndex.get("MAX_EXECUTION_TIME")] = maxExecutionTime;
        if (timedInvocations != 0) {
            rowValues[columnNameToIndex.get("AVG_EXECUTION_TIME")] =
                 (totalTimedExecutionTime / timedInvocations);
        } else {
            rowValues[columnNameToIndex.get("AVG_EXECUTION_TIME")] = 0L;
        }
        rowValues[columnNameToIndex.get("ABORTS")] = abortCount;
        rowValues[columnNameToIndex.get("FAILURES")] = failureCount;
    }

    /**
     * Specifies the columns of statistics that are added by this class to the schema of a statistical results.
     * @param columns List of columns that are in a stats row.
     */
    @Override
    protected void populateColumnSchema(ArrayList<VoltTable.ColumnInfo> columns) {
        super.populateColumnSchema(columns);
        columns.add(new VoltTable.ColumnInfo("PARTITION_ID", VoltType.INTEGER));
        columns.add(new VoltTable.ColumnInfo("PROCEDURE", VoltType.STRING));
        columns.add(new VoltTable.ColumnInfo("INVOCATIONS", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("TIMED_INVOCATIONS", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("MIN_EXECUTION_TIME", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("MAX_EXECUTION_TIME", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("AVG_EXECUTION_TIME", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("ABORTS", VoltType.BIGINT));
        columns.add(new VoltTable.ColumnInfo("FAILURES", VoltType.BIGINT));
    }

    @Override
    protected Iterator<Object> getStatsRowKeyIterator(boolean interval) {
        m_interval = interval;
        return new Iterator<Object>() {
            boolean givenNext = false;
            @Override
            public boolean hasNext() {
                if (!m_interval) {
                    if (m_invocations == 0) {
                        return false;
                    }
                } else if (m_invocations - m_lastInvocations == 0){
                    return false;
                }
                return !givenNext;
            }

            @Override
            public Object next() {
                if (!givenNext) {
                    givenNext = true;
                    return new Object();
                }
                return null;
            }

            @Override
            public void remove() {}

        };
    }

    @Override
    public String toString() {
        return m_catProc.getTypeName();
    }
}