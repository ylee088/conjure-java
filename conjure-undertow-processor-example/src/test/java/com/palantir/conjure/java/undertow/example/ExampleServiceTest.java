/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.undertow.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.undertow.runtime.ConjureHandler;
import com.palantir.conjure.java.undertow.runtime.ConjureUndertowRuntime;
import io.undertow.Undertow;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import org.junit.jupiter.api.Test;

class ExampleServiceTest {

    @Test
    void testSimpleRequest() throws IOException {
        Undertow server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(1)
                .addHttpListener(0, "localhost")
                .setHandler(ConjureHandler.builder()
                        .runtime(ConjureUndertowRuntime.builder().build())
                        .services(ExampleServiceEndpoints.of(new ExampleResource()))
                        .build())
                .build();
        server.start();
        try {
            int port = getPort(server);
            HttpURLConnection connection =
                    (HttpURLConnection) new URL("http://localhost:" + port + "/simple").openConnection();
            assertThat(connection.getResponseCode()).isEqualTo(204);
        } finally {
            server.stop();
        }
    }

    @Test
    void testPing() throws IOException {
        Undertow server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(1)
                .addHttpListener(0, "localhost")
                .setHandler(ConjureHandler.builder()
                        .runtime(ConjureUndertowRuntime.builder().build())
                        .services(ExampleServiceEndpoints.of(new ExampleResource()))
                        .build())
                .build();
        server.start();
        try {
            int port = getPort(server);
            HttpURLConnection connection =
                    (HttpURLConnection) new URL("http://localhost:" + port + "/ping").openConnection();
            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(connection.getContentType()).startsWith("application/json");
            assertThat(connection.getInputStream()).hasContent("\"pong\"");
        } finally {
            server.stop();
        }
    }

    private static int getPort(Undertow server) {
        InetSocketAddress address = (InetSocketAddress)
                Iterables.getOnlyElement(server.getListenerInfo()).getAddress();
        return address.getPort();
    }
}
