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

import javax.transaction.xa.XAException;

/**
 * A {@linkplain FunctionalInterface functional interface} whose implementations can convert a kind of {@link Exception}
 * encountered in the context of an {@linkplain XARoutine XA routine} to an appropriate {@link XAException}, according
 * to the rules in the <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">XA specification</a> as
 * expressed in the {@linkplain javax.transaction.xa.XAResource documentation for the <code>XAResource</code>
 * interface}.
 *
 * @see #convert(XARoutine, Exception)
 *
 * @see XARoutine
 *
 * @see XAException#errorCode
 *
 * @see javax.transaction.xa.XAResource
 *
 * @see <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">The XA Specification</a>
 */
@FunctionalInterface
public interface ExceptionConverter {


    /**
     * Converts the supplied {@link Exception} encountered in the context of the supplied {@link XARoutine} to an
     * appropriate {@link XAException}, following the rules of the <a
     * href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">XA specification</a>.
     *
     * @param xaRoutine the {@link XARoutine}; must not be {@code null}
     *
     * @param exception the {@link Exception} to convert; may be {@code null}
     *
     * @return a suitable non-{@code null} {@link XAException}
     *
     * @exception NullPointerException if {@code routine} is {@code null}
     *
     * @see XAException
     */
    XAException convert(XARoutine xaRoutine, Exception exception);


    /**
     * An enum describing XA routines modeled by an {@link javax.transaction.xa.XAResource} implementation.
     */
    enum XARoutine {

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#start(Xid, int)} method.
         */
        START,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#end(Xid, int)} method.
         */
        END,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#prepare(Xid)} method.
         */
        PREPARE,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#commit(Xid, boolean)} method.
         */
        COMMIT,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#rollback(Xid)} method.
         */
        ROLLBACK,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#recover(int)} method.
         */
        RECOVER,

        /**
         * An enum constant modeling the {@link javax.transaction.xa.XAResource#forget(Xid)} method.
         */
        FORGET;

    }

}