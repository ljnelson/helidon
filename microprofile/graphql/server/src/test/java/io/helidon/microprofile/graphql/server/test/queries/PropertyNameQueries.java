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

package io.helidon.microprofile.graphql.server.test.queries;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with types that have different property
 * names through {@link jakarta.json.bind.annotation.JsonbProperty} or {@link Name}.
 */
@GraphQLApi
@ApplicationScoped
public class PropertyNameQueries {

    @Inject
    private TestDB testDB;

    public PropertyNameQueries() {
    }

    @Query("query1")
    public TypeWithNameAndJsonbProperty getDefaultType() {
        return testDB.getTypeWithNameAndJsonbProperty();
    }

}
