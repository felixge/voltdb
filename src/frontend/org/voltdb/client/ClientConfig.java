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

package org.voltdb.client;

/**
 * Container for configuration settings for a Client
 */
public class ClientConfig {

    static final long DEFAULT_PROCEDURE_TIMOUT_MS = 2 * 60 * 1000; // default timeout is 2 minutes;
    static final long DEFAULT_CONNECTION_TIMOUT_MS = 2 * 60 * 1000; // default timeout is 2 minutes;

    /**
     * Clients may queue a wide variety of messages and a lot of them
     * so give them a large max arena size.
     */
    private static final int m_defaultMaxArenaSize = 134217728;

    final String m_username;
    final String m_password;
    final ClientStatusListenerExt m_listener;
    int m_expectedOutgoingMessageSize = 128;
    int m_maxArenaSizes[] = new int[] {
            m_defaultMaxArenaSize,//16
            m_defaultMaxArenaSize,//32
            m_defaultMaxArenaSize,//64
            m_defaultMaxArenaSize,//128
            m_defaultMaxArenaSize,//256
            m_defaultMaxArenaSize,//512
            m_defaultMaxArenaSize,//1024
            m_defaultMaxArenaSize,//2048
            m_defaultMaxArenaSize,//4096
            m_defaultMaxArenaSize,//8192
            m_defaultMaxArenaSize,//16384
            m_defaultMaxArenaSize,//32768
            m_defaultMaxArenaSize,//65536
            m_defaultMaxArenaSize,//131072
            m_defaultMaxArenaSize//262144
    };
    boolean m_heavyweight = false;
    int m_maxOutstandingTxns = 3000;
    StatsUploaderSettings m_statsSettings = null;
    long m_procedureCallTimeoutMS = DEFAULT_PROCEDURE_TIMOUT_MS;
    long m_connectionResponseTimeoutMS = DEFAULT_CONNECTION_TIMOUT_MS;

    /**
     * Configuration for a client with no authentication credentials that will
     * work with a server with security disabled. Also specifies no status listener.
     */
    public ClientConfig() {
        m_username = "";
        m_password = "";
        m_listener = null;
    }

    /**
     * Configuration for a client that specifies authentication credentials. The username and
     * password can be null or the empty string.
     * @param username
     * @param password
     */
    public ClientConfig(String username, String password) {
        this(username, password, (ClientStatusListenerExt) null);
    }

    /**
     * Configuration for a client that specifies authentication credentials. The username and
     * password can be null or the empty string. Also specifies a status listener.
     * @param username
     * @param password
     * @param listener
     */
    @Deprecated
    public ClientConfig(String username, String password, ClientStatusListener listener) {
        this(username, password, new ClientStatusListenerWrapper(listener));
    }

    /**
     * Configuration for a client that specifies authentication credentials. The username and
     * password can be null or the empty string. Also specifies a status listener.
     * @param username
     * @param password
     * @param listener
     */
    public ClientConfig(String username, String password, ClientStatusListenerExt listener) {
        if (username == null) {
            m_username = "";
        } else {
            m_username = username;
        }
        if (password == null) {
            m_password = "";
        } else {
            m_password = password;
        }
        m_listener = listener;
    }

    /**
     * Set the timeout for procedure call. If the timeout expires before the call returns,
     * the procedure callback will be called with status {@link ClientResponse#CONNECTION_TIMEOUT}.
     * Synchronous procedures will throw an exception. If a response comes back after the
     * expiration has triggered, then a callback method
     * {@link ClientStatusListenerExt#lateProcedureResponse(ClientResponse, String, int)}
     * will be called.
     *
     * Default value is 2 minutes if not set. Value of 0 means forever.
     *
     * Note that while specified in MS, this timeout is only accurate to within a second or so.
     *
     * @param ms Timeout value in milliseconds.
     */
    public void setProcedureCallTimeout(long ms) {
        assert(ms >= 0);
        if (ms < 0) ms = 0;
        // 0 implies infinite, but use LONG_MAX to reduce branches to test
        if (ms == 0) ms = Long.MAX_VALUE;
        m_procedureCallTimeoutMS = ms;
    }

    /**
     * Set the timeout for reading from a connection. If a connection receives no responses,
     * either from procedure calls or &amp;Pings, for the timeout time in milliseconds,
     * then the connection will be assumed dead and the closed connection callback will
     * be called.
     *
     * Default value is 2 minutes if not set. Value of 0 means forever.
     *
     * Note that while specified in MS, this timeout is only accurate to within a second or so.
     *
     * @param ms Timeout value in milliseconds.
     */
    public void setConnectionResponseTimeout(long ms) {
        assert(ms >= 0);
        if (ms < 0) ms = 0;
        // 0 implies infinite, but use LONG_MAX to reduce branches to test
        if (ms == 0) ms = Long.MAX_VALUE;
        m_connectionResponseTimeoutMS = ms;
    }

    /**
     * Set the maximum size of memory pool arenas before falling back to using heap byte buffers.
     * @param maxArenaSizes
     */
    public void setMaxArenaSizes(int maxArenaSizes[]) {
        m_maxArenaSizes = maxArenaSizes;
    }

    /**
     * Request a client with more threads. Useful when a client has multiple NICs or is using
     * 10-gig E
     */
    public void setHeavyweight(boolean heavyweight) {
        final int cores = Runtime.getRuntime().availableProcessors();
        m_heavyweight = cores > 4 ? heavyweight : false;
    }

    /**
     * Provide a hint indicating how large messages will be once serialized. Ensures
     * efficient message buffer allocation.
     * @param size
     */
    public void setExpectedOutgoingMessageSize(int size) {
        this.m_expectedOutgoingMessageSize = size;
    }

    /**
     * Provide configuration information for uploading statistics about client performance
     * via JDBC
     * @param statsSettings
     */
    public void setStatsUploaderSettings(StatsUploaderSettings statsSettings) {
        m_statsSettings = statsSettings;
    }

    /**
     * Set the maximum number of outstanding requests that will be submitted before
     * blocking. Similar to the number of concurrent connections in a traditional synchronous API.
     * Defaults to 2k.
     * @param maxOutstanding
     */
    public void setMaxOutstandingTxns(int maxOutstanding) {
        if (maxOutstanding < 1) {
            throw new IllegalArgumentException(
                    "Max outstanding must be greater than 0, " + maxOutstanding + " was specified");
        }
        m_maxOutstandingTxns = maxOutstanding;
    }
}
