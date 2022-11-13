/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A {@link DelegatingConnection} whose {@link #close()} method
 * performs a close only if the {@link #isCloseable()} method returns
 * {@code true}.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are not necessarily safe for concurrent
 * use by multiple threads.</p>
 *
 * @see #isCloseable()
 *
 * @see #setCloseable(boolean)
 *
 * @see #close()
 */
public class ConditionallyCloseableConnection extends DelegatingConnection {


    /*
     * Instance fields.
     */


    private final SQLRunnable closedChecker;


    /**
     * Whether or not the {@link #close()} method will actually close
     * this {@link DelegatingConnection}.
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     */
    private volatile boolean closeable;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ConditionallyCloseableConnection} and
     * {@linkplain #setCloseable(boolean) sets its closeable status to
     * <code>true</code>}.
     *
     * @param delegate the {@link Connection} to wrap; must not be
     * {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean,
     * boolean)
     *
     * @see #setCloseable(boolean)
     */
    public ConditionallyCloseableConnection(Connection delegate) {
        this(delegate, true, false);
    }

    /**
     * Creates a new {@link ConditionallyCloseableConnection}.
     *
     * @param delegate the {@link Connection} to wrap; must not be
     * {@code null}
     *
     * @param closeable the initial value for this {@link
     * ConditionallyCloseableConnection}'s {@linkplain #isCloseable()
     * closeable} status
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #setCloseable(boolean)
     *
     * @see ConditionallyCloseableConnection(Connection, boolean, boolean)
     */
    public ConditionallyCloseableConnection(Connection delegate, boolean closeable) {
        this(delegate, closeable, false);
    }

    /**
     * Creates a new {@link ConditionallyCloseableConnection}.
     *
     * @param delegate the {@link Connection} to wrap; must not be
     * {@code null}
     *
     * @param closeable the initial value for this {@link
     * ConditionallyCloseableConnection}'s {@linkplain #isCloseable()
     * closeable} status
     *
     * @param strictClosedChecking if {@code true}, then <em>this</em>
     * {@link ConditionallyCloseableConnection}'s {@link #isClosed()}
     * method will be invoked before every operation that cannot take
     * place on a closed connection, and, if it returns {@code true},
     * the operation in question will fail with a {@link
     * SQLException}
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #setCloseable(boolean)
     *
     * @see DelegatingConnection#DelegatingConnection(Connection)
     */
    public ConditionallyCloseableConnection(Connection delegate, boolean closeable, boolean strictClosedChecking) {
        super(delegate);
        this.closeable = closeable;
        this.closedChecker = strictClosedChecking ? this::failWhenClosed : ConditionallyCloseableConnection::noop;
    }


    /*
     * Instance methods.
     */


    /**
     * Overrides the {@link DelegatingConnection#close()} method so
     * that when it is invoked this {@link
     * ConditionallyCloseableConnection} is {@linkplain
     * Connection#close() closed} only if it {@linkplain
     * #isCloseable() is closeable}.
     *
     * <p>Overrides should normally call {@code super.close()} as part
     * of their implementation.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #isCloseable()
     */
    @Override // DelegatingConnection
    public void close() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        if (this.isCloseable()) {
            super.close();
        }
    }

    /**
     * Returns {@code true} if a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection}.
     *
     * <p>This method returns {@code true} when {@link
     * #setCloseable(boolean)} has been called with a value of {@code
     * true} and the {@link #isClosed()} method returns {@code
     * false}.</p>
     *
     * @return {@code true} if a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection};
     * {@code false} in all other cases
     *
     * @exception SQLException if {@link #isClosed()} throws a {@link
     * SQLException}
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosed()
     */
    public final boolean isCloseable() throws SQLException {
        // this.checkOpen(); // Deliberately omitted.
        return this.closeable && !this.isClosed();
    }

    /**
     * Sets the closeable status of this {@link
     * ConditionallyCloseableConnection}.
     *
     * <p>Note that calling this method with a value of {@code true}
     * does not necessarily mean that the {@link #isCloseable()}
     * method will subsequently return {@code true}, since the {@link
     * #isClosed()} method may return {@code true}.</p>
     *
     * @param closeable whether or not a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection}
     *
     * @see #isCloseable()
     *
     * @see #close()
     *
     * @see Connection#close()
     *
     * @see #isClosed()
     */
    public final void setCloseable(boolean closeable) {
        // this.checkOpen(); // Deliberately omitted.
        this.closeable = closeable;
    }

    @Override // DelegatingConnection
    public Statement createStatement() throws SQLException {
        this.checkOpen();
        return super.createStatement();
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql) throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql);
    }

    @Override // DelegatingConnection
    public String nativeSQL(String sql) throws SQLException {
        this.checkOpen();
        return super.nativeSQL(sql);
    }

    @Override // DelegatingConnection
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.checkOpen();
        super.setAutoCommit(autoCommit);
    }

    @Override // DelegatingConnection
    public boolean getAutoCommit() throws SQLException {
        this.checkOpen();
        return super.getAutoCommit();
    }

    @Override // DelegatingConnection
    public void commit() throws SQLException {
        this.checkOpen();
        super.commit();
    }

    @Override // DelegatingConnection
    public void rollback() throws SQLException {
        this.checkOpen();
        super.rollback();
    }

    /**
     * Returns {@code true} if and only if this {@link
     * ConditionallyCloseableConnection} either is, or is to be
     * considered to be, closed, such that operations which must throw
     * a {@link SQLException} when invoked on a closed connection will
     * do so.
     *
     * <p>The default implementation of this method simply calls
     * {@link DelegatingConnection#close() super.close()}.  Subclasses
     * often wish to change this behavior, normally in conjunction
     * with changing the behavior of the {@link #close()} method as
     * well.</p>
     *
     * <p>Subclasses that override this method must not directly or
     * indirectly call {@link #checkOpen()} or undefined behavior may
     * result.</p>
     *
     * @return {@code true} if and only if this {@link
     * ConditionallyCloseableConnection} either is, or is to be
     * considered to be, closed
     *
     * @exception SQLException if a database access error occurs
     *
     * @see #checkOpen()
     */
    @Override // DelegatingConnection
    public boolean isClosed() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec (and common sense).
        return super.isClosed();
    }

    @Override // DelegatingConnection
    public DatabaseMetaData getMetaData() throws SQLException {
        this.checkOpen();
        return super.getMetaData();
    }

    @Override // DelegatingConnection
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.checkOpen();
        super.setReadOnly(readOnly);
    }

    @Override // DelegatingConnection
    public boolean isReadOnly() throws SQLException {
        this.checkOpen();
        return super.isReadOnly();
    }

    @Override // DelegatingConnection
    public void setCatalog(String catalog) throws SQLException {
        this.checkOpen();
        super.setCatalog(catalog);
    }

    @Override // DelegatingConnection
    public String getCatalog() throws SQLException {
        this.checkOpen();
        return super.getCatalog();
    }

    @Override // DelegatingConnection
    public void setTransactionIsolation(int level) throws SQLException {
        this.checkOpen();
        super.setTransactionIsolation(level);
    }

    @Override // DelegatingConnection
    public int getTransactionIsolation() throws SQLException {
        this.checkOpen();
        return super.getTransactionIsolation();
    }

    @Override // DelegatingConnection
    public SQLWarning getWarnings() throws SQLException {
        this.checkOpen();
        return super.getWarnings();
    }

    @Override // DelegatingConnection
    public void clearWarnings() throws SQLException {
        this.checkOpen();
        super.clearWarnings();
    }

    @Override // DelegatingConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        this.checkOpen();
        return super.getTypeMap();
    }

    @Override // DelegatingConnection
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.checkOpen();
        super.setTypeMap(map);
    }

    @Override // DelegatingConnection
    public void setHoldability(int holdability) throws SQLException {
        this.checkOpen();
        super.setHoldability(holdability);
    }

    @Override // DelegatingConnection
    public int getHoldability() throws SQLException {
        this.checkOpen();
        return super.getHoldability();
    }

    @Override // DelegatingConnection
    public Savepoint setSavepoint() throws SQLException {
        this.checkOpen();
        return super.setSavepoint();
    }

    @Override // DelegatingConnection
    public Savepoint setSavepoint(String name) throws SQLException {
        this.checkOpen();
        return super.setSavepoint(name);
    }

    @Override // DelegatingConnection
    public void rollback(Savepoint savepoint) throws SQLException {
        this.checkOpen();
        super.rollback(savepoint);
    }

    @Override // DelegatingConnection
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.checkOpen();
        super.releaseSavepoint(savepoint);
    }

    @Override // DelegatingConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        this.checkOpen();
        return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, columnIndexes);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, columnNames);
    }

    @Override // DelegatingConnection
    public Clob createClob() throws SQLException {
        this.checkOpen();
        return super.createClob();
    }

    @Override // DelegatingConnection
    public Blob createBlob() throws SQLException {
        this.checkOpen();
        return super.createBlob();
    }

    @Override // DelegatingConnection
    public NClob createNClob() throws SQLException {
        this.checkOpen();
        return super.createNClob();
    }

    @Override // DelegatingConnection
    public SQLXML createSQLXML() throws SQLException {
        this.checkOpen();
        return super.createSQLXML();
    }

    @Override // DelegatingConnection
    public boolean isValid(int timeout) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        return super.isValid(timeout);
    }

    @Override // DelegatingConnection
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            this.checkOpen();
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
        super.setClientInfo(name, value);
    }

    @Override // DelegatingConnection
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            this.checkOpen();
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
        super.setClientInfo(properties);
    }

    @Override // DelegatingConnection
    public String getClientInfo(String name) throws SQLException {
        this.checkOpen();
        return super.getClientInfo(name);
    }

    @Override // DelegatingConnection
    public Properties getClientInfo() throws SQLException {
        this.checkOpen();
        return super.getClientInfo();
    }

    @Override // DelegatingConnection
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        this.checkOpen();
        return super.createArrayOf(typeName, elements);
    }

    @Override // DelegatingConnection
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        this.checkOpen();
        return super.createStruct(typeName, attributes);
    }

    @Override // DelegatingConnection
    public void setSchema(String schema) throws SQLException {
        this.checkOpen();
        super.setSchema(schema);
    }

    @Override // DelegatingConnection
    public String getSchema() throws SQLException {
        this.checkOpen();
        return super.getSchema();
    }

    @Override // DelegatingConnection
    public void abort(Executor executor) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.abort(executor);
    }

    @Override // DelegatingConnection
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.checkOpen();
        super.setNetworkTimeout(executor, milliseconds);
    }

    @Override // DelegatingConnection
    public int getNetworkTimeout() throws SQLException {
        this.checkOpen();
        return super.getNetworkTimeout();
    }

    @Override // DelegatingConnection
    public void beginRequest() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.beginRequest();
    }

    @Override // DelegatingConnection
    public void endRequest() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.endRequest();
    }

    @Override // DelegatingConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        this.checkOpen();
        return super.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override // DelegatingConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        this.checkOpen();
        return super.setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override // DelegatingConnection
    public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.checkOpen();
        super.setShardingKey(shardingKey, superShardingKey);
    }

    @Override // DelegatingConnection
    public void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.checkOpen();
        super.setShardingKey(shardingKey);
    }

    @Override // DelegatingConnection
    public <T> T unwrap(Class<T> iface) throws SQLException {
        this.checkOpen();
        return super.unwrap(iface);
    }

    @Override // DelegatingConnection
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        this.checkOpen();
        return super.isWrapperFor(iface);
    }

    /**
     * Ensures this {@link ConditionallyCloseableConnection} is
     * {@linkplain #isClosed() not closed}, if {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)
     * strict closed checking was enabled at construction time}, or
     * simply returns if {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)
     * strict closed checking was <em>not</em> enabled at construction
     * time}.
     *
     * <p>This method is called from almost every method in this
     * class.</p>
     *
     * @exception SQLException if this {@link
     * ConditionallyCloseableConnection} was {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)
     * created with strict closed checking enabled} and an invocation
     * of the {@link #isClosed()} method returns {@code true}, or if
     * some other database access error occurs
     */
    private void checkOpen() throws SQLException {
        this.closedChecker.run();
    }

    /**
     * Invokes the {@link #isClosed()} method, and, if it returns
     * {@code true}, throws a new {@link SQLException} indicating that
     * the operation cannot proceed.
     *
     * <p>If this {@link ConditionallyCloseableConnection} was
     * {@linkplain #ConditionallyCloseableConnection(Connection,
     * boolean, boolean) created with strict closed checking enabled},
     * then this method will be called by the {@link #checkOpen()}
     * method.  Otherwise this method is not called internally by
     * default implementations of the methods in the {@link
     * ConditionallyCloseableConnection} class.  Subclasses may call
     * this method directly for any reason.</p>
     *
     * <p>An override of this method must not call {@link
     * #checkOpen()} or undefined behavior may result.</p>
     *
     * @exception SQLException when an invocation of the {@link
     * #isClosed()} method returns {@code true}
     */
    protected final void failWhenClosed() throws SQLException {
        if (this.isClosed()) {
            throw new SQLNonTransientConnectionException("Connection is closed", "08000");
        }
    }


    /*
     * Static methods.
     */


    // (Invoked by method reference only.)
    private static void noop() {

    }

}
