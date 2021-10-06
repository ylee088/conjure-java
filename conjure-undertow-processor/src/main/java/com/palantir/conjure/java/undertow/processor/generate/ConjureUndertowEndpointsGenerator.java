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

package com.palantir.conjure.java.undertow.processor.generate;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.undertow.lib.Endpoint;
import com.palantir.conjure.java.undertow.lib.ReturnValueWriter;
import com.palantir.conjure.java.undertow.lib.Serializer;
import com.palantir.conjure.java.undertow.lib.TypeMarker;
import com.palantir.conjure.java.undertow.lib.UndertowRuntime;
import com.palantir.conjure.java.undertow.lib.UndertowService;
import com.palantir.conjure.java.undertow.processor.data.EndpointDefinition;
import com.palantir.conjure.java.undertow.processor.data.EndpointName;
import com.palantir.conjure.java.undertow.processor.data.ReturnType;
import com.palantir.conjure.java.undertow.processor.data.ServiceDefinition;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import org.immutables.value.Value;

public final class ConjureUndertowEndpointsGenerator {

    private static final String DELEGATE_NAME = "delegate";
    private static final String RUNTIME_NAME = "runtime";
    private static final String EXCHANGE_NAME = "exchange";
    private static final String RETURN_VALUE = "returnValue";

    private final ServiceDefinition serviceDefinition;

    public ConjureUndertowEndpointsGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    public TypeSpec generate() {
        FieldSpec delegate = FieldSpec.builder(serviceDefinition.serviceInterface(), DELEGATE_NAME)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();
        ImmutableList<TypeSpec> endpoints = serviceDefinition.endpoints().stream()
                .map(endpoint -> endpoint(serviceDefinition, endpoint))
                .collect(ImmutableList.toImmutableList());
        return TypeSpec.classBuilder(serviceDefinition.undertowService())
                .addAnnotation(AnnotationSpec.builder(ClassName.get(Generated.class))
                        .addMember("value", "$S", getClass().getCanonicalName())
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get(UndertowService.class))
                .addField(delegate)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(serviceDefinition.serviceInterface(), DELEGATE_NAME)
                        .addStatement("this.$N = $N", delegate, DELEGATE_NAME)
                        .build())
                .addMethod(MethodSpec.methodBuilder("of")
                        .addParameter(serviceDefinition.serviceInterface(), DELEGATE_NAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addStatement("return new $T($N)", serviceDefinition.undertowService(), DELEGATE_NAME)
                        .returns(UndertowService.class)
                        .build())
                .addMethod(MethodSpec.methodBuilder("endpoints")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(UndertowRuntime.class, serviceDefinition.conjureRuntimeArgName())
                        .returns(ParameterizedTypeName.get(List.class, Endpoint.class))
                        .addStatement(
                                "return $T.of($L)",
                                ImmutableList.class,
                                endpoints.stream()
                                        .map(endpoint -> CodeBlock.of(
                                                "new $N($N, $N)", endpoint.name, RUNTIME_NAME, DELEGATE_NAME))
                                        .collect(CodeBlock.joining(", ")))
                        .build())
                .addTypes(endpoints)
                .build();
    }

    private static String endpointClassName(EndpointName endpointName) {
        String name = endpointName.get();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Endpoint";
    }

    private static TypeSpec endpoint(ServiceDefinition service, EndpointDefinition endpoint) {
        List<AdditionalField> additionalFields = new ArrayList<>();
        MethodSpec.Builder handlerBuilder = MethodSpec.methodBuilder("handleRequest")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HttpServerExchange.class, EXCHANGE_NAME)
                .addException(Exception.class);
        ReturnType returnType = endpoint.returns();
        TypeName responseTypeName =
                returnType.asyncInnerType().orElseGet(returnType::returnType).box();
        if (returnType.asyncInnerType().isEmpty() && returnType.isVoid()) {
            handlerBuilder
                    .addStatement(invokeDelegate(endpoint))
                    .addStatement("$N.setStatusCode($T.NO_CONTENT)", EXCHANGE_NAME, StatusCodes.class);
        } else {
            additionalFields.add(ImmutableAdditionalField.builder()
                    .field(FieldSpec.builder(
                                    ParameterizedTypeName.get(ClassName.get(Serializer.class), responseTypeName),
                                    returnType.serializerFieldName(),
                                    Modifier.PRIVATE,
                                    Modifier.FINAL)
                            .build())
                    .constructorInitializer(CodeBlock.builder()
                            .addStatement(
                                    "this.$N = $L.serializer(new $T<$T>() {}, $N)",
                                    returnType.serializerFieldName(),
                                    Instantiables.instantiate(returnType.serializerFactory()),
                                    TypeMarker.class,
                                    responseTypeName,
                                    RUNTIME_NAME)
                            .build())
                    .build());
            if (returnType.asyncInnerType().isPresent()) {
                handlerBuilder.addStatement(
                        "$N.async().register($L, this, $N)", RUNTIME_NAME, invokeDelegate(endpoint), EXCHANGE_NAME);
            } else {
                handlerBuilder.addStatement("write($L, $N)", invokeDelegate(endpoint), EXCHANGE_NAME);
            }
        }
        TypeSpec.Builder endpointBuilder = TypeSpec.classBuilder(endpointClassName(endpoint.endpointName()))
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(HttpHandler.class)
                .addSuperinterface(Endpoint.class)
                .addField(UndertowRuntime.class, RUNTIME_NAME, Modifier.PRIVATE, Modifier.FINAL)
                .addField(service.serviceInterface(), DELEGATE_NAME, Modifier.PRIVATE, Modifier.FINAL)
                .addFields(
                        additionalFields.stream().map(AdditionalField::field).collect(ImmutableList.toImmutableList()))
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(UndertowRuntime.class, RUNTIME_NAME)
                        .addParameter(service.serviceInterface(), DELEGATE_NAME)
                        .addStatement("this.$1N = $1N", RUNTIME_NAME)
                        .addStatement("this.$1N = $1N", DELEGATE_NAME)
                        .addCode(additionalFields.stream()
                                .map(AdditionalField::constructorInitializer)
                                .collect(CodeBlock.joining("")))
                        .build())
                .addMethod(handlerBuilder.build());

        if (!TypeName.VOID.equals(returnType.returnType())) {
            endpointBuilder.addSuperinterface(
                    ParameterizedTypeName.get(ClassName.get(ReturnValueWriter.class), responseTypeName));
            endpointBuilder.addMethod(MethodSpec.methodBuilder("write")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(responseTypeName, RETURN_VALUE)
                    .addParameter(HttpServerExchange.class, EXCHANGE_NAME)
                    .addException(IOException.class)
                    .addStatement(
                            "this.$N.serialize($N, $N)", returnType.serializerFieldName(), RETURN_VALUE, EXCHANGE_NAME)
                    .build());
        }

        return endpointBuilder
                .addMethod(MethodSpec.methodBuilder("method")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(HttpString.class)
                        .addStatement(
                                "return $T.$N",
                                Methods.class,
                                endpoint.httpMethod().name())
                        .build())
                .addMethod(MethodSpec.methodBuilder("template")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        .addStatement("return $S", endpoint.httpPath().path())
                        .build())
                .addMethod(MethodSpec.methodBuilder("serviceName")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        .addStatement("return $S", endpoint.serviceName().get())
                        .build())
                .addMethod(MethodSpec.methodBuilder("name")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        .addStatement("return $S", endpoint.endpointName().get())
                        .build())
                .addMethod(MethodSpec.methodBuilder("handler")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(HttpHandler.class)
                        .addStatement("return this")
                        .build())
                .build();
    }

    // TODO(ckozak): handle parameters
    private static CodeBlock invokeDelegate(EndpointDefinition endpoint) {
        CodeBlock args = endpoint.arguments().stream()
                .map(arg -> CodeBlock.of("$N", arg.argName().get()))
                .collect(CodeBlock.joining(","));
        return CodeBlock.of(
                "this.$N.$N($L)", DELEGATE_NAME, endpoint.endpointName().get(), args);
    }

    @Value.Immutable
    interface AdditionalField {
        FieldSpec field();

        CodeBlock constructorInitializer();
    }
}
