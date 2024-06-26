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

package io.helidon.security.integration.jersey;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.integration.common.SecurityTracing;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for an issue (JC-304):
 * Example in microprofile: idcs/IdcsResource, method "getCurrentSubject" has authentication optional, yet it redirects even
 * when not logged in.
 *
 * Current behavior: we are redirected to login page
 * Correct behavior: we should get an empty subject
 *
 * This must be fixed in integration with Jersey/web server, when we receive a FINISH command, we should check if optional...
 */
class OptionalSecurityTest {
    private static SecurityFilter secuFilter;
    private static SecurityClientBuilder<AuthenticationResponse> clientBuilder;
    private static Security security;
    private static FeatureConfig featureConfig;
    private static ResourceConfig serverConfig;
    private static SecurityTracing tracing;

    @BeforeAll
    static void init() {
        /*
         * Prepare parameters
         */
        security = Security.builder()
                .addAuthenticationProvider(OptionalSecurityTest::authenticate)
                .build();

        featureConfig = new FeatureConfig();

        serverConfig = ResourceConfig.forApplication(getApplication());

        AuthenticationResponse atr = AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();

        clientBuilder = mock(SecurityClientBuilder.class);
        when(clientBuilder.buildAndGet()).thenReturn(atr);

        tracing = SecurityTracing.get();
    }

    @Test
    void testOptional() {
        SecurityContext secuContext = security.createContext("context_id");
        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();

        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(true);

        /*
         * Instantiate the filter
         */
        secuFilter = new SecurityFilter(featureConfig,
                                        security,
                                        serverConfig,
                                        secuContext);

        /*
         * The actual tested method
         */
        secuFilter.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());

        assertThat(filterContext.isShouldFinish(), is(false));
        assertThat(secuContext.user(), is(Optional.empty()));
    }

    @Test
    void testNotOptional() {
        SecurityContext secuContext = security.createContext("context_id");
        SecurityFilter.FilterContext filterContext = new SecurityFilter.FilterContext();
        filterContext.setJerseyRequest(mock(ContainerRequest.class));
        SecurityDefinition methodSecurity = mock(SecurityDefinition.class);
        when(methodSecurity.authenticationOptional()).thenReturn(false);

        /*
         * Instantiate the filter
         */
        secuFilter = new SecurityFilter(featureConfig,
                                        security,
                                        serverConfig,
                                        secuContext);
        /*
         * The actual tested method
         */
        secuFilter.processAuthentication(filterContext, clientBuilder, methodSecurity, tracing.atnTracing());

        assertThat(filterContext.isShouldFinish(), is(true));
        assertThat(secuContext.user(), is(Optional.empty()));
    }

    private static Application getApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return Set.of(TheResource.class);
            }
        };
    }

    private static CompletionStage<AuthenticationResponse> authenticate(ProviderRequest request) {
        AuthenticationResponse res = AuthenticationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE_FINISH)
                .statusCode(301)
                .build();

        return CompletableFuture.completedFuture(res);
    }

    @Path("/")
    public static class TheResource {
        @GET
        @Authenticated
        public String getIt() {
            return "hello!";
        }
    }
}
