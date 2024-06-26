/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.io.IOException;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.tests.junit5.AddBean;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Tests for default exceptions.
 */
@AddBean(ExceptionQueries.class)
@AddBean(TestDB.class)
class DefaultCheckedExceptionIT extends AbstractGraphQlCdiIT {

    @Inject
    DefaultCheckedExceptionIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    void testBlackListAndWhiteList() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        assertMessageValue("query { checkedQuery1(throwException: true) }", "exception", true);
    }
}
