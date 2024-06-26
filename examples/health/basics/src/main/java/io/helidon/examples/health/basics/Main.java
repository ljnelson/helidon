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
package io.helidon.examples.health.basics;

import java.time.Duration;

import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * Main class of health check integration example.
 */
public final class Main {

    private static long serverStartTime;

    private Main() {
    }

    /**
     * Start the example. Prints endpoints to standard output.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        serverStartTime = System.currentTimeMillis();
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())
                .addReadiness(() -> HealthCheckResponse.named("exampleHealthCheck")
                        .up()
                        .withData("time", System.currentTimeMillis())
                        .build())
                .addStartup(() -> HealthCheckResponse.named("exampleStartCheck")
                        .status(isStarted())
                        .withData("time", System.currentTimeMillis())
                        .build())
                .build();

        Routing routing = Routing.builder()
                .register(health)
                .get("/hello", (req, res) -> res.send("Hello World!"))
                .build();

        WebServer ws = WebServer.create(routing);

        ws.start()
                .thenApply(webServer -> {
                    String endpoint = "http://localhost:" + webServer.port();
                    System.out.println("Hello World started on " + endpoint + "/hello");
                    System.out.println("Health checks available on " + endpoint + "/health");
                    return null;
                });

    }

    private static boolean isStarted() {
        return Duration.ofMillis(System.currentTimeMillis() - serverStartTime).getSeconds() >= 8;
    }
}
