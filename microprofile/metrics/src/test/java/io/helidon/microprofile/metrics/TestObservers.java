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
package io.helidon.microprofile.metrics;

import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddExtension(TestObservers.TestExtension.class)
public class TestObservers {

    @Test
    void testDiscoveryObserver() {
        assertThat("Observer's discoveries", TestDiscoveryObserverImpl.discoveries().size(),  is(not(0)));
    }

    @Test
    void testRegistrationObserver() {
        assertThat("Observer's registrations", TestRegistrationObserverImpl.registrations(), is(not(0)));
    }

    public static class TestExtension implements Extension {

        void enroll(@Observes BeforeBeanDiscovery bbd, BeanManager beanManager) {
            MetricsCdiExtension metricsCdiExtension = beanManager.getExtension(MetricsCdiExtension.class);
            metricsCdiExtension.enroll(TestDiscoveryObserverImpl.instance());
            metricsCdiExtension.enroll(TestRegistrationObserverImpl.instance());
        }
    }
}
