/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.service.employee;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.metrics.serviceapi.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentSupport;

/**
 * Simple Employee rest application.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml and JSON support registration
        Single<WebServer> server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonbSupport.create())
                .build()
                .start();

        server.thenAccept(ws -> {
            System.out.println("WEB server is up!");
            System.out.println("Web client at: http://localhost:" + ws.port()
                                       + "/public/index.html");
            ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
        }).exceptionally(t -> {
            System.err.println("Startup failed: " + t.getMessage());
            t.printStackTrace(System.err);
            return null;
        });

        // Server threads are not daemon. No need to block. Just react.

        return server;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with a health check, and a service
     */
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create();
        EmployeeService employeeService = new EmployeeService(config);
        HealthSupport health = HealthSupport.builder().addLiveness(HealthChecks.healthChecks())
                .build(); // Adds a convenient set of checks

        return Routing.builder()
                .register("/public", StaticContentSupport.builder("public")
                        .welcomeFileName("index.html"))
                .register(health) // Health at "/health"
                .register(metrics) // Metrics at "/metrics"
                .register("/employees", employeeService)
                .build();
    }

}
