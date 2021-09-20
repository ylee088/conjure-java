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

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.undertow.lib.Endpoint;
import com.palantir.conjure.java.undertow.lib.UndertowRuntime;
import com.palantir.conjure.java.undertow.lib.UndertowService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.util.List;

// Hand-written target. InputInterfaceName + Endpoints
// @Generated...
public final class HandWrittenExampleServiceEndpoints implements UndertowService {

    private final ExampleService delegate;

    private HandWrittenExampleServiceEndpoints(ExampleService delegate) {
        this.delegate = delegate;
    }

    public static UndertowService of(ExampleService delegate) {
        return new HandWrittenExampleServiceEndpoints(delegate);
    }

    @Override
    public List<Endpoint> endpoints(UndertowRuntime runtime) {
        return ImmutableList.of(new SimpleEndpoint(runtime, delegate));
    }

    // TODO(ckozak): Implement toString both here _and_ in the conjure generator, nice for debugging.

    @SuppressWarnings("unused") // not necessary to suppress unused in generated code afaik
    private static final class SimpleEndpoint implements HttpHandler, Endpoint {
        private final UndertowRuntime runtime;

        private final ExampleService delegate;

        SimpleEndpoint(UndertowRuntime runtime, ExampleService delegate) {
            this.runtime = runtime;
            this.delegate = delegate;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws IOException {
            delegate.simple();
            exchange.setStatusCode(StatusCodes.NO_CONTENT);
        }

        @Override
        public HttpString method() {
            return Methods.GET;
        }

        @Override
        public String template() {
            return "/simple";
        }

        @Override
        public String serviceName() {
            return "ExampleService";
        }

        @Override
        public String name() {
            return "simple";
        }

        @Override
        public HttpHandler handler() {
            return this;
        }
    }
}
