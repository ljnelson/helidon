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
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.SQLRunnable;
import io.helidon.integrations.jdbc.UncheckedSQLException;

import static javax.transaction.xa.XAException.XAER_DUPID;
import static javax.transaction.xa.XAException.XAER_INVAL;
import static javax.transaction.xa.XAException.XAER_NOTA;
import static javax.transaction.xa.XAException.XAER_PROTO;
import static javax.transaction.xa.XAException.XAER_RMERR;
import static javax.transaction.xa.XAResource.TMENDRSCAN;
import static javax.transaction.xa.XAResource.TMFAIL;
import static javax.transaction.xa.XAResource.TMJOIN;
import static javax.transaction.xa.XAResource.TMNOFLAGS;
import static javax.transaction.xa.XAResource.TMONEPHASE;
import static javax.transaction.xa.XAResource.TMRESUME;
import static javax.transaction.xa.XAResource.TMSTARTRSCAN;
import static javax.transaction.xa.XAResource.TMSUCCESS;
import static javax.transaction.xa.XAResource.TMSUSPEND;
import static javax.transaction.xa.XAResource.XA_OK;
import static javax.transaction.xa.XAResource.XA_RDONLY;

/**
 * An {@link XAResource} that adapts an ordinary arbitrary {@link
 * Connection} to the XA contract.
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.</p>
 */
final class LocalXAResource implements XAResource {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(LocalXAResource.class.getName());

    private static final Xid[] EMPTY_XID_ARRAY = new Xid[0];


    /*
     * Instance fields.
     */


    private final Function<? super Xid, ? extends Connection> connectionFunction;

    private final ConcurrentMap<Xid, Association> associations;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link LocalXAResource}.
     *
     * @param connectionFunction a {@link Function} that accepts a
     * {@link Xid} (supplied by the {@link #start(Xid, int)} method)
     * and returns a {@link Connection} to associate with the global
     * transaction; must not be {@code null}; must never return {@code
     * null}; must be safe for concurrent use by multiple threads;
     * will never be invoked with a {@code null} {@link Xid}
     *
     * @see #start(Xid, int)
     */
    LocalXAResource(Function<? super Xid, ? extends Connection> connectionFunction) {
        super();
        this.connectionFunction = Objects.requireNonNull(connectionFunction, "connectionFunction");
        this.associations = new ConcurrentHashMap<>();
    }


    /*
     * Instance methods.
     */


    @Override // XAResource
    public void start(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "start", new Object[] {xid, flagsToString(flags)});
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMJOIN:
            remappingFunction = LocalXAResource::join;
            break;
        case TMNOFLAGS:
            remappingFunction = this::start;
            break;
        case TMRESUME:
            remappingFunction = LocalXAResource::resume;
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL).initCause(new IllegalArgumentException("xid: " + xid
                                                                                                   + "; flags: "
                                                                                                   + flagsToString(flags)));
        }

        try {
            this.associations.compute(xid, remappingFunction);
        } catch (UncheckedXAException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            throw (XAException) new XAException(XAER_RMERR).initCause(e);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "start");
        }
    }

    // (Remapping BiFunction.)
    private Association start(Xid x, Association a) {
        if (a != null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_DUPID)
                                           .initCause(new IllegalArgumentException("xid: " + x + "; association: " + a)));
        }
        Connection c;
        try {
            c = this.connectionFunction.apply(x);
        } catch (RuntimeException e) {
            throw new UncheckedXAException((XAException) new XAException(XAER_RMERR).initCause(e));
        }
        if (c == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_RMERR)
                                           .initCause(new NullPointerException("connectionFunction.apply(" + x + ")")));
        }
        return new Association(Association.BranchState.ACTIVE, x, c);
    }

    @Override // XAResource
    public void end(Xid xid, int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "end", new Object[] {xid, flagsToString(flags)});
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        BiFunction<Xid, Association, Association> remappingFunction;
        switch (flags) {
        case TMFAIL:
        case TMSUCCESS:
            remappingFunction = LocalXAResource::activeToIdle;
            break;
        case TMSUSPEND:
            remappingFunction = LocalXAResource::suspend;
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL).initCause(new IllegalArgumentException("xid: " + xid
                                                                                                   + "; flags: "
                                                                                                   + flagsToString(flags)));
        }

        try {
            this.associations.compute(xid, remappingFunction);
        } catch (UncheckedXAException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            throw (XAException) new XAException(XAER_RMERR).initCause(e);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "end");
        }
    }

    @Override // XAResource
    public int prepare(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "prepare", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        Object association = computeAssociation(xid,
                                                EnumSet.of(Association.BranchState.IDLE),
                                                LocalXAResource::prepare,
                                                false);
        int returnValue = association == null ? XA_RDONLY : XA_OK;

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "prepare", returnValue);
        }
        return returnValue;
    }

    @Override // XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "commit", new Object[] {xid, onePhase});
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        computeAssociation(xid,
                           EnumSet.of(Association.BranchState.IDLE,
                                      Association.BranchState.PREPARED),
                           LocalXAResource::commitAndReset,
                           true);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "commit");
        }
    }

    @Override // XAResource
    public void rollback(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "rollback", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        computeAssociation(xid,
                           EnumSet.of(Association.BranchState.IDLE,
                                      Association.BranchState.PREPARED,
                                      Association.BranchState.ROLLBACK_ONLY),
                           LocalXAResource::rollbackAndReset,
                           true);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "rollback");
        }
    }

    @Override // XAResource
    public void forget(Xid xid) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "forget", xid);
        }

        if (xid == null) {
            throw (XAException) new XAException(XAER_INVAL).initCause(new NullPointerException("xid"));
        }

        computeAssociation(xid,
                           EnumSet.of(Association.BranchState.HEURISTICALLY_COMPLETED),
                           LocalXAResource::forgetAndReset,
                           false);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "forget");
        }
    }

    @Override // XAResource
    public Xid[] recover(int flags) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "recover", flagsToString(flags));
        }

        switch (flags) {
        case TMENDRSCAN:
        case TMNOFLAGS:
        case TMSTARTRSCAN:
            break;
        default:
            throw (XAException) new XAException(XAER_INVAL)
                .initCause(new IllegalArgumentException("flags: " + flagsToString(flags)));
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "recover", EMPTY_XID_ARRAY);
        }
        return EMPTY_XID_ARRAY;
    }

    @Override // XAResource
    public boolean isSameRM(XAResource xaResource) throws XAException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "isSameRM", xaResource);
            LOGGER.exiting(this.getClass().getName(), "isSameRM", this == xaResource);
        }
        return this == xaResource;
    }

    @Override // XAResource
    public int getTransactionTimeout() {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "getTransactionTimeout");
            LOGGER.exiting(this.getClass().getName(), "getTransactionTimeout", 0);
        }
        return 0;
    }

    @Override // XAResource
    public boolean setTransactionTimeout(int transactionTimeoutInSeconds) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "setTransactionTimeout", transactionTimeoutInSeconds);
            LOGGER.exiting(this.getClass().getName(), "setTransactionTimeout", false);
        }
        return false;
    }

    private Association computeAssociation(Xid xid,
                                           EnumSet<Association.BranchState> legalBranchStates,
                                           UnaryOperator<Association> f,
                                           boolean removeXidOnError)
        throws XAException {
        try {
            return this.associations.compute(xid, (x, a) -> remap(x, a, legalBranchStates, f));
        } catch (RuntimeException e) {
            if (removeXidOnError) {
                this.associations.remove(xid);
            }
            if (e.getCause() instanceof XAException xaException) {
                throw xaException;
            } else if (e instanceof IllegalTransitionException) {
                throw (XAException) new XAException(XAER_PROTO).initCause(e);
            } else {
                throw (XAException) new XAException(XAER_RMERR).initCause(e);
            }
        }
    }


    /*
     * Static methods.
     */


    // (Invoked only in context of a remapping BiFunction, from
    // computeAssociation().)
    private static Association remap(Xid xid,
                                     Association a,
                                     EnumSet<Association.BranchState> legalBranchStates,
                                     UnaryOperator<Association> remapOperator) {
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_NOTA)
                                           .initCause(new NullPointerException("xid: " + xid + "; association: null")));
        }
        if (legalBranchStates.contains(a.branchState())) {
            return remapOperator.apply(a);
        }
        throw new UncheckedXAException((XAException) new XAException(XAER_PROTO)
                                       .initCause(new IllegalStateException("xid: " + xid + "; association: " + a)));
    }

    // (Remapping BiFunction.)
    private static Association activeToIdle(Xid x, Association a) {
        return a.activeToIdle();
    }

    // (Remapping BiFunction.)
    private static Association suspend(Xid x, Association a) {
        return a.suspend();
    }

    // (Remapping BiFunction.)
    private static Association join(Xid x, Association a) {
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_PROTO)
                                           .initCause(new NullPointerException("xid: " + x + "; association: null")));
        } else if (a.suspended()) {
            assert a.branchState() == Association.BranchState.IDLE;
            throw new UncheckedXAException((XAException) new XAException(XAER_PROTO)
                                           .initCause(new IllegalStateException("xid: " + x + "; association: " + a)));
        }
        switch (a.branchState()) {
        case ACTIVE:
            return a;
        case IDLE:
            return a.idleToActive();
        default:
            throw new IllegalTransitionException("xid: " + x + "; association: " + a);
        }
    }

    // (Remapping BiFunction.)
    private static Association resume(Xid x, Association a) {
        if (a == null) {
            throw new UncheckedXAException((XAException) new XAException(XAER_NOTA)
                                           .initCause(new NullPointerException("xid: " + x + "; association: null")));
        }
        return a.resume();
    }

    // (UnaryOperator for supplying via method reference to remap()
    // above.)
    private static Association commitAndReset(Association a) {
        try {
            a = a.commitAndReset();
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        // Critically important: remove the association.
        return null;
    }

    // (UnaryOperator for supplying via method reference to remap()
    // above.)
    private static Association rollbackAndReset(Association a) {
        try {
            a = a.rollbackAndReset();
        } catch (SQLException sqlException) {
            throw new UncheckedSQLException(sqlException);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        // Critically important: remove the association.
        return null;
    }

    // (UnaryOperator for supplying via method reference to remap()
    // above.)
    private static Association prepare(Association a) {
        assert !a.suspended(); // can't be in T2
        try {
            if (a.connection().isReadOnly()) {
                a = a.reset();
                assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
                // Critically important: remove the association.
                a = null;
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        return a;
    }

    // (UnaryOperator for supplying via method reference to remap()
    // above.)
    private static Association forgetAndReset(Association a) {
        try {
            a = a.forgetAndReset();
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        assert a.branchState() == Association.BranchState.NON_EXISTENT_TRANSACTION;
        // Critically important: remove the association.
        return null;
    }

    private static String flagsToString(int flags) {
        switch (flags) {
        case TMENDRSCAN:
            return "TMENDRSCAN (" + flags + ")";
        case TMFAIL:
            return "TMFAIL (" + flags + ")";
        case TMJOIN:
            return "TMJOIN (" + flags + ")";
        case TMNOFLAGS:
            return "TMNOFLAGS (" + flags + ")";
        case TMONEPHASE:
            return "TMONEPHASE (" + flags + ")";
        case TMRESUME:
            return "TMRESUME (" + flags + ")";
        case TMSTARTRSCAN:
            return "TMSTARTRSCAN (" + flags + ")";
        case TMSUCCESS:
            return "TMSUCCESS (" + flags + ")";
        case TMSUSPEND:
            return "TMSUSPEND (" + flags + ")";
        default:
            return String.valueOf(flags);
        }
    }

    private static Association adjust(Association a, SQLException e) {
        return a;
    }


    /*
     * Inner and nested classes.
     */


    private static final class IllegalTransitionException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private IllegalTransitionException(String message) {
            super(message);
        }

    }

    private static record Association(BranchState branchState,
                                      Xid xid,
                                      boolean suspended,
                                      Connection connection,
                                      boolean priorAutoCommit) {

        // Branch Association States: (XA specification, table 6-2)
        // T0: Not Associated
        // T1: Associated
        // T2: Association Suspended

        // Branch States: (XA specification, table 6-4)
        // S0: Non-existent Transaction
        // S1: Active
        // S2: Idle
        // S3: Prepared
        // S4: Rollback Only
        // S5: Heuristically Completed

        private Association(BranchState branchState, Xid xid, Connection connection) {
            this(branchState, xid, false, connection);
        }

        private Association(BranchState branchState, Xid xid, boolean suspended, Connection connection) {
            this(branchState, xid, suspended, connection, true /* JDBC default; will be set from connection anyway */);
        }

        private Association {
            Objects.requireNonNull(xid, "xid");
            switch (branchState) {
            case IDLE:
                break;
            case ACTIVE:
            case HEURISTICALLY_COMPLETED:
            case NON_EXISTENT_TRANSACTION:
            case PREPARED:
            case ROLLBACK_ONLY:
                if (suspended) {
                    throw new IllegalArgumentException("suspended");
                }
                break;
            default:
                throw new IllegalArgumentException("branchState: " + branchState);
            }
            try {
                priorAutoCommit = connection.getAutoCommit();
                if (priorAutoCommit) {
                    connection.setAutoCommit(false);
                }
            } catch (SQLException sqlException) {
                throw new UncheckedSQLException(sqlException);
            }
            // T0, T1 or T2; S0 or S2
        }

        public boolean suspended() {
            assert this.suspended ? this.branchState() == BranchState.IDLE : true;
            return this.suspended;
        }

        private Association activeToIdle() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(*) was called and didn't fail with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Active     -> Idle       (S1 -> S2)
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                  break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association activeToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(*) was called and failed with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Active     -> Rollback Only (S1 -> S4)
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                  break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association idleToActive() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMJOIN) was called and didn't fail
                    // with an XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated (T1 -> T1; unchanged)
                    // Idle       -> Active     (S2 -> S1)
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association idleToRollbackOnly() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(*) was called and failed with an
                    // XAER_RB* code and we are not suspended
                    //
                    // Associated -> Associated    (T1 -> T1; unchanged)
                    // Idle       -> Rollback Only (S2 -> S4)
                    return new Association(BranchState.ROLLBACK_ONLY,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association suspend() {
            if (!this.suspended()) {
                switch (this.branchState()) {
                case ACTIVE:
                    // OK; end(TMSUSPEND) was called and we are not
                    // suspended
                    //
                    // Associated -> Association Suspended (T1 -> T2)
                    // Active     -> Idle                  (S1 -> S2)
                    return new Association(BranchState.IDLE,
                                           this.xid(),
                                           true,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association resume() {
            if (this.suspended()) {
                switch (this.branchState()) {
                case IDLE:
                    // OK; start(TMRESUME) was called and we are
                    // suspended
                    //
                    // Association Suspended -> Associated (T2 -> T1)
                    // Idle                  -> Active     (S2 -> S1)
                    return new Association(BranchState.ACTIVE,
                                           this.xid(),
                                           false,
                                           this.connection(),
                                           this.priorAutoCommit());
                default:
                    break;
                }
            }
            throw new IllegalTransitionException(this.toString());
        }

        private Association commitAndReset() throws SQLException {
            Connection c = this.connection();
            return this.runAndReset(c::commit, c::rollback);
        }

        private Association rollbackAndReset() throws SQLException {
            return this.runAndReset(this.connection()::rollback, null);
        }

        private Association runAndReset(SQLRunnable r, SQLRunnable rollbackRunnable)
            throws SQLException {
            Association a;
            SQLException sqlException = null;
            try {
                r.run();
            } catch (SQLException e) {
                sqlException = e;
                if (rollbackRunnable != null) {
                    try {
                        rollbackRunnable.run();
                    } catch (SQLException e2) {
                        e.setNextException(e2);
                    }
                }
            } finally {
                try {
                    a = this.reset(sqlException);
                } catch (SQLException e) {
                    a = null;
                    if (sqlException == null) {
                        sqlException = e;
                    } else if (sqlException != e) {
                        sqlException.setNextException(e);
                    }
                } finally {
                    if (sqlException != null) {
                        throw sqlException;
                    }
                }
            }
            return a;
        }

        private Association forgetAndReset() throws SQLException {
            return this.reset();
        }

        private Association reset() throws SQLException {
            return this.reset(null);
        }

        private Association reset(SQLException sqlException) throws SQLException {
            // TO DO: the SQLException passed in here is to allow this
            // method to decide whether to place the Association it
            // returns into a rollback state (XAER_RB*) or heuristic
            // state.  As of this writing it is ignored but everything
            // else is in place to deal with it properly.
            Connection connection = this.connection();
            connection.setAutoCommit(this.priorAutoCommit());
            return new Association(BranchState.NON_EXISTENT_TRANSACTION,
                                   this.xid(),
                                   false,
                                   connection,
                                   this.priorAutoCommit());
        }

        private enum BranchState {
            NON_EXISTENT_TRANSACTION, // S0
            ACTIVE, // S1
            IDLE, // S2
            PREPARED, // S3
            ROLLBACK_ONLY, // S4
            HEURISTICALLY_COMPLETED; // S5
        }

    }

}
