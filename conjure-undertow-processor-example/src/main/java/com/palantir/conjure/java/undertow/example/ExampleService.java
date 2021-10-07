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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.undertow.annotations.Handle;
import com.palantir.conjure.java.undertow.annotations.HttpMethod;
import com.palantir.conjure.java.undertow.lib.BinaryResponseBody;
import java.io.Closeable;
import java.util.Optional;

public interface ExampleService {

    @Handle(method = HttpMethod.GET, path = "/simple")
    void simple();

    @Handle(method = HttpMethod.GET, path = "/ping")
    String ping();

    @Handle(method = HttpMethod.GET, path = "/pingAsync")
    ListenableFuture<String> pingAsync();

    @Handle(method = HttpMethod.GET, path = "/voidAsync")
    ListenableFuture<Void> voidAsync();

    @Handle(method = HttpMethod.GET, path = "/returnPrimitive")
    int returnPrimitive();

    @Handle(method = HttpMethod.GET, path = "/binary")
    BinaryResponseBody binary();

    @Handle(method = HttpMethod.GET, path = "/namedBinary")
    CustomBinaryResponseBody namedBinary();

    @Handle(method = HttpMethod.GET, path = "/optionalBinary")
    Optional<BinaryResponseBody> optionalBinary();

    @Handle(method = HttpMethod.GET, path = "/optionalNamedBinary")
    Optional<CustomBinaryResponseBody> optionalNamedBinary();

    @Handle(method = HttpMethod.POST, path = "/post")
    String post(@Handle.Body String body);

    interface CustomBinaryResponseBody extends Closeable, BinaryResponseBody {}
}
