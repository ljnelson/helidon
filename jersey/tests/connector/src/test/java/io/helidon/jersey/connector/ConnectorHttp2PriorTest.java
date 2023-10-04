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

package io.helidon.jersey.connector;

import java.util.List;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;

/**
 * Tests HTTP/2 integration of Jakarta REST client with the Helidon connector that uses
 * WebClient to execute HTTP requests. Assumes prior knowledge.
 */
@ServerTest
class ConnectorHttp2PriorTest extends ConnectorBase {

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(keystore -> keystore
                        .keystore(Resource.create("certificate.p12"))
                        .keystorePassphrase("helidon"))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().orElseThrow())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        serverBuilder.putSocket("https", socketBuilder -> socketBuilder.tls(tls));
        serverBuilder.addProtocol(Http2Config.create());
    }

    ConnectorHttp2PriorTest(WebServer server) {
        int port = server.port("https");

        Tls tls = Tls.builder()
                .trustAll(true)
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        ClientConfig config = new ClientConfig();
        config.connectorProvider(HelidonConnectorProvider.create());       // use Helidon's provider
        config.property(HelidonProperties.TLS, tls);
        config.property(HelidonProperties.PROTOCOL_CONFIGS,
                List.of(Http2ClientProtocolConfig.builder()
                        .priorKnowledge(true)
                        .build()));
        client(ClientBuilder.newClient(config));
        baseURI("https://localhost:" + port);
    }
}