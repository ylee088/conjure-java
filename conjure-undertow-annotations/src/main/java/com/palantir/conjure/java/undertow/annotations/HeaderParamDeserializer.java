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

package com.palantir.conjure.java.undertow.annotations;

import com.palantir.conjure.java.undertow.lib.Deserializer;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import java.util.Collections;

public final class HeaderParamDeserializer<T> implements Deserializer<T> {

    private final HttpString headerName;
    private final CollectionParamDecoder<? extends T> decoder;

    public HeaderParamDeserializer(String headerName, ParamDecoder<? extends T> decoder) {
        this(headerName, new SingleParamDecoder<>(decoder));
    }

    public HeaderParamDeserializer(String headerName, CollectionParamDecoder<? extends T> decoder) {
        Preconditions.checkNotNull(headerName, "Header name is required");
        this.headerName = Preconditions.checkNotNull(
                HttpString.tryFromString(headerName), "Failed to parse header", SafeArg.of("headerName", headerName));
        this.decoder = Preconditions.checkNotNull(decoder, "Decoder is required");
    }

    @Override
    public T deserialize(HttpServerExchange exchange) {
        HeaderValues maybeValues = exchange.getRequestHeaders().get(headerName);
        return Preconditions.checkNotNull(
                decoder.decode(
                        maybeValues == null
                                ? Collections.emptyList()
                                : Collections.unmodifiableCollection(maybeValues)),
                "Decoder produced a null value");
    }

    @Override
    public String toString() {
        return "HeaderParamDeserializer{headerName=" + headerName + ", decoder=" + decoder + '}';
    }
}
