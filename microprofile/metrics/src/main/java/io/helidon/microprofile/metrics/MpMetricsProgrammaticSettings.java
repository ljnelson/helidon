/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.metrics.api.MetricsProgrammaticSettings;

/**
 * MP implementation of metrics programmatic settings.
 */
public class MpMetricsProgrammaticSettings implements MetricsProgrammaticSettings {

    /**
     * Creates a new instance (explicit for style checking and service loading).
     *
     */
    public MpMetricsProgrammaticSettings() {
    }

    @Override
    public String scopeTagName() {
        return "mp_scope";
    }

    @Override
    public String appTagName() {
        return "mp_app";
    }
}