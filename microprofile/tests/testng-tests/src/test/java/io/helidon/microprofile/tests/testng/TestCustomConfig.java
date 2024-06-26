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

package io.helidon.microprofile.tests.testng;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.testng.annotations.Test;

import static io.helidon.microprofile.tests.testng.TestCustomConfig.PROPERTY_VALUE;
import static io.helidon.microprofile.tests.testng.TestDefaults.PROPERTY_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddConfig(key = PROPERTY_NAME, value = PROPERTY_VALUE)
@AddConfig(key = "second-key", value = "test-custom-config-second-value")
public class TestCustomConfig {
    static final String PROPERTY_VALUE = "test-custom-config-value";

    @Inject
    private BeanManager beanManager;

    @Inject
    @ConfigProperty(name = PROPERTY_NAME, defaultValue = PROPERTY_VALUE)
    private String shouldExist;

    @Inject
    @ConfigProperty(name = "second-key", defaultValue = PROPERTY_VALUE)
    private String anotherValue;

    @Test
    void testIt() {
        assertThat(beanManager, notNullValue());
        assertThat(shouldExist, is(PROPERTY_VALUE));
        assertThat(anotherValue, is("test-custom-config-second-value"));
    }
}