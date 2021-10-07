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

package com.palantir.conjure.java.undertow.processor.data;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.undertow.annotations.CollectionParamDecoder;
import com.palantir.conjure.java.undertow.annotations.Handle;
import com.palantir.conjure.java.undertow.annotations.ParamDecoder;
import com.palantir.conjure.java.undertow.processor.data.ParameterDecoderType.DecoderType;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tokens.auth.AuthHeader;
import com.squareup.javapoet.TypeName;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class ParamTypesResolver {

    private static final ImmutableSet<Class<?>> PARAM_ANNOTATION_CLASSES =
            ImmutableSet.of(Handle.Body.class, Handle.PathParam.class, Handle.QueryParam.class, Handle.Header.class);
    private static final ImmutableSet<String> PARAM_ANNOTATIONS =
            PARAM_ANNOTATION_CLASSES.stream().map(Class::getCanonicalName).collect(ImmutableSet.toImmutableSet());
    private static final String paramEncoderMethod;
    private static final String listParamEncoderMethod;

    static {
        try {
            paramEncoderMethod =
                    ParamDecoder.class.getMethod("decode", String.class).getName();
            listParamEncoderMethod = CollectionParamDecoder.class
                    .getMethod("decode", Collection.class)
                    .getName();
        } catch (NoSuchMethodException e) {
            throw new SafeRuntimeException("Method renamed: are you sure you want to cause a break?", e);
        }
    }

    private final ResolverContext context;

    public ParamTypesResolver(ResolverContext context) {
        this.context = context;
    }

    @SuppressWarnings("CyclomaticComplexity")
    public Optional<ParameterType> getParameterType(EndpointName endpointName, VariableElement variableElement) {
        List<AnnotationMirror> paramAnnotationMirrors = new ArrayList<>();
        for (AnnotationMirror annotationMirror : variableElement.getAnnotationMirrors()) {
            TypeElement annotationTypeElement =
                    MoreElements.asType(annotationMirror.getAnnotationType().asElement());
            if (PARAM_ANNOTATIONS.contains(
                    annotationTypeElement.getQualifiedName().toString())) {
                paramAnnotationMirrors.add(annotationMirror);
            }
        }

        if (paramAnnotationMirrors.isEmpty()) {
            if (context.isAssignable(variableElement.asType(), InputStream.class)) {
                return Optional.of(ParameterTypes.rawBody());
            } else if (context.isSameTypes(variableElement.asType(), AuthHeader.class)) {
                return Optional.of(ParameterTypes.header("Authorization", Optional.empty()));
            } else {
                context.reportError(
                        "At least one annotation should be present or type should be InputStream",
                        variableElement,
                        SafeArg.of("requestBody", InputStream.class),
                        SafeArg.of("supportedAnnotations", PARAM_ANNOTATION_CLASSES));
                return Optional.empty();
            }
        }

        if (paramAnnotationMirrors.size() > 1) {
            context.reportError(
                    "Only single annotation can be used",
                    variableElement,
                    SafeArg.of("annotations", paramAnnotationMirrors));
            return Optional.empty();
        }

        // TODO(12345): More validation of values.

        AnnotationReflector annotationReflector =
                ImmutableAnnotationReflector.of(Iterables.getOnlyElement(paramAnnotationMirrors));
        if (annotationReflector.isAnnotation(Handle.Body.class)) {
            // default annotation param values are not available at annotation processing time
            String serializerName = InstanceVariables.joinCamelCase(endpointName.get(), "Serializer");
            TypeMirror serializer = annotationReflector.getAnnotationValue(TypeMirror.class);
            return Optional.of(ParameterTypes.body(TypeName.get(serializer), serializerName));
        } else if (annotationReflector.isAnnotation(Handle.Header.class)) {
            return Optional.of(ParameterTypes.header(
                    annotationReflector.getStringValueField(),
                    getParameterDecoder(
                            endpointName, variableElement, annotationReflector, DecoderTypeAndMethod.LIST)));
        } else if (annotationReflector.isAnnotation(Handle.PathParam.class)) {
            return Optional.of(ParameterTypes.path(getPathParameterDecoder(
                    endpointName,
                    variableElement,
                    annotationReflector,
                    DecoderTypeAndMethod.PARAM,
                    DecoderTypeAndMethod.LIST)));
        } else if (annotationReflector.isAnnotation(Handle.QueryParam.class)) {
            return Optional.of(ParameterTypes.query(
                    annotationReflector.getAnnotationValue(String.class),
                    getParameterDecoder(
                            endpointName, variableElement, annotationReflector, DecoderTypeAndMethod.LIST)));
        }

        throw new SafeIllegalStateException("Not possible");
    }

    private Optional<ParameterDecoderType> getPathParameterDecoder(
            EndpointName endpointName,
            VariableElement variableElement,
            AnnotationReflector annotationReflector,
            DecoderTypeAndMethod decoderTypeAndMethod,
            DecoderTypeAndMethod listDecoderTypeAndMethod) {
        Optional<TypeName> decoderTypeName =
                annotationReflector.getFieldMaybe("decoder", TypeMirror.class).map(TypeName::get);

        Optional<TypeName> listEncoderTypeName = annotationReflector
                .getFieldMaybe("listEncoder", TypeMirror.class)
                .map(TypeName::get);

        if (decoderTypeName.isPresent() && listEncoderTypeName.isPresent()) {
            context.reportError("Only one of decoder and listEncoder can be set", variableElement);

            return Optional.empty();
        }

        if (decoderTypeName.isPresent()) {
            return getParameterDecoder(endpointName, variableElement, decoderTypeName, decoderTypeAndMethod);
        }
        return getParameterDecoder(endpointName, variableElement, listEncoderTypeName, listDecoderTypeAndMethod);
    }

    private Optional<ParameterDecoderType> getParameterDecoder(
            EndpointName endpointName,
            VariableElement variableElement,
            AnnotationReflector annotationReflector,
            DecoderTypeAndMethod decoderTypeAndMethod) {
        return getParameterDecoder(
                endpointName,
                variableElement,
                Optional.of(TypeName.get(annotationReflector.getAnnotationValue("decoder", TypeMirror.class))),
                decoderTypeAndMethod);
    }

    private Optional<ParameterDecoderType> getParameterDecoder(
            EndpointName endpointName,
            VariableElement variableElement,
            Optional<TypeName> typeName,
            DecoderTypeAndMethod decoderTypeAndMethod) {
        return typeName.map(encoderJavaType -> ImmutableParameterDecoderType.builder()
                .type(decoderTypeAndMethod.decoderType)
                .decoderJavaType(encoderJavaType)
                .decoderFieldName(InstanceVariables.joinCamelCase(
                        endpointName.get(), variableElement.getSimpleName().toString(), "Decoder"))
                .decoderMethodName(decoderTypeAndMethod.method)
                .build());
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private enum DecoderTypeAndMethod {
        PARAM(DecoderTypes.param(), paramEncoderMethod),
        LIST(DecoderTypes.listParam(), listParamEncoderMethod),
        ;

        private final DecoderType decoderType;
        private final String method;

        DecoderTypeAndMethod(DecoderType decoderType, String method) {
            this.decoderType = decoderType;
            this.method = method;
        }
    }
}
