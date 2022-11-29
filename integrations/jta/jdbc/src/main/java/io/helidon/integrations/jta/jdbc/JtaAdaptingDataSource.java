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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import io.helidon.integrations.jdbc.AbstractDataSource;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * An {@link AbstractDataSource} that wraps another {@link DataSource} that might not behave correctly in the presence
 * of JTA transaction management, such as one supplied by any of several freely and commercially available connection
 * pools, and that makes a non-JTA-aware {@link DataSource} behave as sensibly as possible in the presence of a
 * JTA-managed transaction.
 */
public final class JtaAdaptingDataSource extends AbstractDataSource {


    /*
     * Instance fields.
     */


    private final AuthenticatedConnectionSupplier acs;

    private final UnauthenticatedConnectionSupplier uacs;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaAdaptingDataSource}.
     *
     * @param tm a {@link TransactionManager}; must not be {@code null} unless {@code ds} is also an {@link
     * XADataSource}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null} unless {@code ds} is also an
     * {@link XADataSource}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param ds a {@link DataSource} that may not be XA-compliant; must not be {@code null}; normally supplied by a
     * connection pool implementation
     *
     * @exception NullPointerException if {@code tm}, {@code tsr} or {@code ds} is {@code null} when it is not permitted
     * to be
     */
    // Undefined behavior if ds ends up supplying the return value of an invocation of XAConnection#getConnection().
    public JtaAdaptingDataSource(TransactionManager tm,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 DataSource ds) {
        this(tm == null ? null : tm::getTransaction, tsr, interposedSynchronizations, ec, ds);
    }

    /**
     * Creates a new {@link JtaAdaptingDataSource}.
     *
     * @param ts a {@link TransactionSupplier}; must not be {@code null} unless {@code ds} is also an {@link
     * XADataSource}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null} unless {@code ds} is also an
     * {@link XADataSource}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param ds a {@link DataSource} that may not be XA-compliant; must not be {@code null}; normally supplied by a
     * connection pool implementation
     *
     * @exception NullPointerException if {@code ts}, {@code tsr} or {@code ds} is {@code null} when it is not permitted
     * to be
     */
    // Undefined behavior if ds ends up supplying the return value of an invocation of XAConnection#getConnection().
    public JtaAdaptingDataSource(TransactionSupplier ts,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 DataSource ds) {
        super();
        if (Objects.requireNonNull(ds, "ds") instanceof XADataSource) {
            // Some connection pools offer an object that implements both DataSource and XADataSource. If so, assume
            // they know what they're doing.
            this.acs = ds::getConnection;
            this.uacs = ds::getConnection;
        } else {
            Objects.requireNonNull(ts, "ts");
            Objects.requireNonNull(tsr, "tsr");
            this.acs = (u, p) -> JtaConnection.connection(ts, tsr, interposedSynchronizations, ec, ds.getConnection(u, p));
            this.uacs = () -> JtaConnection.connection(ts, tsr, interposedSynchronizations, ec, ds.getConnection());
        }
    }

    @Override // DataSource
    public Connection getConnection(String username, String password) throws SQLException {
        return this.acs.getConnection(username, password);
    }

    @Override // DataSource
    public Connection getConnection() throws SQLException {
        return this.uacs.getConnection();
    }


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    private interface UnauthenticatedConnectionSupplier {

        Connection getConnection() throws SQLException;

    }

    @FunctionalInterface
    private interface AuthenticatedConnectionSupplier {

        Connection getConnection(String username, String password) throws SQLException;

    }

}
