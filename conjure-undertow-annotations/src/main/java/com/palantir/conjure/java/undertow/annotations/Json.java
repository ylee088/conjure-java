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

import com.palantir.conjure.java.undertow.lib.BodySerDe;
import com.palantir.conjure.java.undertow.lib.Deserializer;
import com.palantir.conjure.java.undertow.lib.Serializer;
import com.palantir.conjure.java.undertow.lib.TypeMarker;
import com.palantir.conjure.java.undertow.runtime.ConjureUndertowRuntime;

public final class Json implements SerializerFactory<Object>, DeserializerFactory<Object> {

    private final BodySerDe bodySerDe;

    public Json() {
        this(ConjureUndertowRuntime.builder().build().bodySerDe());
    }

    public Json(BodySerDe bodySerDe) {
        this.bodySerDe = bodySerDe;
    }

    @Override
    public <T> Deserializer<T> deserializerFor(TypeMarker<T> type) {
        return bodySerDe.deserializer(type);
    }

    @Override
    public <T> Serializer<T> serializerFor(TypeMarker<T> type) {
        return bodySerDe.serializer(type);
    }
}
