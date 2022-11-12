/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.integrations.jta.jdbc;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.transaction.xa.Xid;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

final class TestJTAConnection {

    private static final Logger LOGGER = Logger.getLogger(TestJTAConnection.class.getName());

    private static final JTAEnvironmentBean jtaEnvironmentBean = new JTAEnvironmentBean();

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TransactionSynchronizationRegistry tsr;

    private TestJTAConnection() throws SQLException {
        super();
    }

    @BeforeEach
    final void initializeH2DataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;INIT=SET TRACE_LEVEL_FILE=4");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.h2ds = ds;
    }

    @BeforeEach
    final void initializeTransactionManager() throws SystemException {
        this.tm = jtaEnvironmentBean.getTransactionManager(); // com.arjuna.ats.jta.TransactionManager.transactionManager();
        this.tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
    }

    @BeforeEach
    final void initializeTransactionSynchronizationRegistry() throws SystemException {
        this.tsr = jtaEnvironmentBean.getTransactionSynchronizationRegistry();
    }

    @AfterEach
    final void rollback() throws SQLException, SystemException {
        switch (this.tm.getStatus()) {
        case STATUS_NO_TRANSACTION:
            break;
        default:
            this.tm.rollback();
            break;
        }
        this.tm.setTransactionTimeout(0);
    }

    @DisplayName("Spike")
    @Test
    final void testSpike()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException {
        LOGGER.info("Starting testSpike()");
        tm.begin();

        try (Connection physicalConnection = h2ds.getConnection();
             Connection logicalConnection = JTAConnection.connection(tm::getTransaction,
                                                                     tsr::registerInterposedSynchronization,
                                                                     (x, y) -> {},
                                                                     physicalConnection)) {

            assertThat(logicalConnection, instanceOf(JTAConnection.class));
            assertThat(logicalConnection, instanceOf(Enlisted.class));
            assertThat(logicalConnection, instanceOf(ConditionallyCloseableConnection.class));

            // Trigger an Object method; make sure nothing blows up
            // (in case we're using proxies).
            logicalConnection.toString();

            // Up until this point, the connection should not be enlisted.
            assertThat(((Enlisted) logicalConnection).xid(), nullValue());

            // That means it should be closeable.
            assertThat(((ConditionallyCloseableConnection) logicalConnection).isCloseable(), is(true));
            assertThat(logicalConnection.isClosed(), is(false));
            
            // Trigger harmless Connection method; make sure nothing
            // blows up.
            logicalConnection.getHoldability();

            // Almost all Connection methods will cause enlistment to
            // happen.  getHoldability(), just invoked, is one of
            // them.
            Xid xid = ((Enlisted) logicalConnection).xid();
            assertThat(xid, not(nullValue()));

            // That means the Connection is no longer closeable.
            assertThat(((ConditionallyCloseableConnection) logicalConnection).isCloseable(), is(false));

            // Should be a no-op.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(false));
            
            // Should get the same Xid back whenever we call xid()
            // once we're enlisted.
            assertThat(((Enlisted) logicalConnection).xid(), sameInstance(xid));

            // Ensure JDBC constructs' backlinks work correctly.
            try (Statement s = logicalConnection.createStatement()) {
                assertThat(s, not(nullValue()));
                assertThat(s.getConnection(), sameInstance(logicalConnection));
                try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
                    assertThat(rs.getStatement(), sameInstance(s));
                }
            }

            // Commit AND DISASSOCIATE the transaction, which can only
            // happen with a call to TransactionManager.commit(), not
            // just Transaction.commit().
            tm.commit();

            // Transaction is over; the Xid should be null.
            assertThat(((Enlisted) logicalConnection).xid(), nullValue());

            // Transaction is over; the connection should be closeable again.
            assertThat(((ConditionallyCloseableConnection) logicalConnection).isCloseable(), is(true));

            // We should be able to actually close it early.  The
            // auto-close should not fail, either.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(true));
        }

        LOGGER.info("Ending testSpike()");
    }

}
