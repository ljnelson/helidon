/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.google;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link GoogleConfigMain}.
 */
public class GoogleConfigMainTest extends GoogleMainTest {
    static int port;

    @BeforeAll
    public static void initClass() throws InterruptedException {
        port = GoogleConfigMain.start(0);
    }

    @AfterAll
    public static void destroyClass() throws InterruptedException {
        stopServer(GoogleConfigMain.getTheServer());
    }

    @Override
    int port() {
        return port;
    }
}
