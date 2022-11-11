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
package io.helidon.integrations.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Objects;

/**
 * A {@link Statement} that delegates to another {@link Statement}.
 *
 * @param <S> the type of the {@link Statement} subclass
 */
public class DelegatingStatement<S extends Statement> implements Statement {

    private final Connection connection;

    private final S delegate;

    /**
     * Creates a new {@link DelegatingStatement}.
     *
     * @param connection the {@link Connection} that created this
     * {@link DelegatingStatement}; must not be {@code null}
     *
     * @param delegate the {@link Statement} instance to which all
     * operations will be delegated; must not be {@code null}
     *
     * @exception NullPointerException if either argument is {@code
     * null}
     *
     * @see #getConnection()
     */
    public DelegatingStatement(Connection connection, S delegate) {
        super();
        this.connection = Objects.requireNonNull(connection, "connection");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    protected final S delegate() {
        return this.delegate;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        // NOTE
        return new DelegatingResultSet(this, this.delegate().executeQuery(sql));
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return this.delegate().executeUpdate(sql);
    }

    @Override
    public void close() throws SQLException {
        this.delegate().close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return this.delegate().getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        this.delegate().setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return this.delegate().getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        this.delegate().setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        this.delegate().setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return this.delegate().getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        this.delegate().setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        this.delegate().cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return this.delegate().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        this.delegate().clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        this.delegate().setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return this.delegate().execute(sql);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        // NOTE
        return new DelegatingResultSet(this, this.delegate().getResultSet());
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return this.delegate().getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return this.delegate().getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        this.delegate().setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return this.delegate().getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        this.delegate().setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return this.delegate().getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return this.delegate().getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return this.delegate().getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        this.delegate().addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        this.delegate().clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return this.delegate().executeBatch();
    }

    /**
     * Returns the {@link Connection} {@linkplain
     * #DelegatingStatement(Connection, Statement) supplied at
     * construction time}.
     *
     * @return the {@link Connection} {@linkplain
     * #DelegatingStatement(Connection, Statement) supplied at
     * construction time}; never {@code null}
     *
     * @exception SQLException not thrown by the default
     * implementation of this method
     *
     * @see #DelegatingStatement(Connection, Statement)
     */
    @Override
    public Connection getConnection() throws SQLException {
        // NOTE
        return this.connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return this.delegate().getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        // NOTE
        return new DelegatingResultSet(this, this.delegate().getGeneratedKeys());
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return this.delegate().executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return this.delegate().executeUpdate(sql, columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return this.delegate().executeUpdate(sql, columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return this.delegate().execute(sql, autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return this.delegate().execute(sql, columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return this.delegate().execute(sql, columnNames);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return this.delegate().getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.delegate().isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        this.delegate().setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return this.delegate().isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        this.delegate().closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return this.delegate().isCloseOnCompletion();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return this.delegate().getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        this.delegate().setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return this.delegate().getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        return this.delegate().executeLargeBatch();
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        return this.delegate().executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return this.delegate().executeLargeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return this.delegate().executeLargeUpdate(sql, columnIndexes);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return this.delegate().executeLargeUpdate(sql, columnNames);
    }

    @Override
    public String enquoteLiteral(String val) throws SQLException {
        return this.delegate().enquoteLiteral(val);
    }

    @Override
    public String enquoteIdentifier(String identifier, boolean alwaysQuote) throws SQLException {
        return this.delegate().enquoteIdentifier(identifier, alwaysQuote);
    }

    @Override
    public boolean isSimpleIdentifier(String identifier) throws SQLException {
        return this.delegate().isSimpleIdentifier(identifier);
    }

    @Override
    public String enquoteNCharLiteral(String val) throws SQLException {
        return this.delegate().enquoteNCharLiteral(val);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : this.delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || this.delegate().isWrapperFor(iface);
    }

}
