////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2015 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s):
//              Denim Group, Ltd.
//              Secure Decisions, a division of Applied Visions, Inc
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.framework.impl.spring;

import com.denimgroup.threadfix.data.entities.ModelField;
import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.entities.RouteParameterType;
import com.denimgroup.threadfix.framework.util.EventBasedTokenizer;
import com.denimgroup.threadfix.framework.util.EventBasedTokenizerRunner;
import com.denimgroup.threadfix.framework.util.FilePathUtils;
import com.denimgroup.threadfix.framework.util.java.EntityMappings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.CollectionUtils.map;

// TODO recognize String variables
// TODO support * values:
// from Spring documentation: Ant-style path patterns are supported (e.g. "/myPath/*.do").
public class SpringControllerEndpointParser implements EventBasedTokenizer {

    @Nonnull
    Set<SpringControllerEndpoint> endpoints = new TreeSet<SpringControllerEndpoint>();
    private int startLineNumber = 0, curlyBraceCount = 0, openParenCount = 0;
    private boolean inClass = false, afterOpenParen = false;
    boolean hasControllerAnnotation = false;

    @Nullable
    private String classEndpoint = null, currentMapping = null, lastValue = null,
            secondToLastValue = null, lastParam, lastParamType, pendingParamDataType, pendingParamName;

    private RouteParameterType pendingParamType;

    private boolean paramTypeAfterCloseParen = false;

    @Nonnull
    private final String filePath;
    @Nonnull
    private final String relativeFilePath;
    @Nullable
    private final File rootDirectory;
    @Nullable
    private ModelField currentModelObject = null;
    @Nonnull
    private List<String>
            classMethods  = list(),
            methodMethods = list();
    @Nonnull
    private Map<String, RouteParameter> currentParameters = map();

    private static final String
            VALUE           = "value",
            METHOD          = "method",
            REQUEST_PARAM   = "RequestParam",
            PATH_VARIABLE   = "PathVariable",
            REQUEST_MAPPING = "RequestMapping",
            COOKIE_VALUE    = "CookieValue",
            SESSION_ATTRIBUTE = "SessionAttribute",
            CLASS           = "class",
            PRE_AUTHORIZE   = "PreAuthorize",
            BINDING_RESULT  = "BindingResult",
            CONTROLLER      = "RestController",
            REST_CONTROLLER = "Controller";

    @Nonnull
    private Phase           phase           = Phase.ANNOTATION;
    @Nonnull
    private AnnotationState annotationState = AnnotationState.START;
    private SignatureState  signatureState  = SignatureState.START;

    @Nullable
    private EntityMappings entityMappings = null;

    private enum Phase {
        ANNOTATION, SIGNATURE, METHOD
    }

    private enum AnnotationState {
        START, ARROBA, REQUEST_MAPPING, VALUE, METHOD, METHOD_MULTI_VALUE, ANNOTATION_END, SECURITY_ANNOTATION
    }

    private enum SignatureState {
        START, ARROBA, REQUEST_PARAM, GET_ANNOTATION_VALUE, ANNOTATION_PARAMS, VALUE, GET_VARIABLE_NAME, GET_VARIABLE_TYPE
    }

    @Nonnull
    public static Set<SpringControllerEndpoint> parse(@Nullable File rootDirectory, @Nonnull File file, @Nullable EntityMappings entityMappings) {
        SpringControllerEndpointParser parser = new SpringControllerEndpointParser(rootDirectory, file.getAbsolutePath(), entityMappings);
        EventBasedTokenizerRunner.run(file, parser);
        return parser.endpoints;
    }

    SpringControllerEndpointParser(@Nullable File rootDirectory, @Nonnull String filePath) {
        this.filePath = filePath;
        this.rootDirectory = rootDirectory;

        if (rootDirectory != null && filePath.startsWith(rootDirectory.getAbsolutePath())) {
            this.relativeFilePath = FilePathUtils.getRelativePath(filePath, rootDirectory);
        } else {
            this.relativeFilePath = filePath;
        }
    }

    private SpringControllerEndpointParser(@Nullable File rootDirectory,
                                           @Nonnull String filePath,
                                           @Nullable EntityMappings entityMappings) {
        this.rootDirectory = rootDirectory;
        this.filePath = filePath;
        this.entityMappings = entityMappings;

        if (rootDirectory != null && filePath.startsWith(rootDirectory.getAbsolutePath())) {
            this.relativeFilePath = FilePathUtils.getRelativePath(filePath, rootDirectory);
        } else {
            this.relativeFilePath = filePath;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !inClass || hasControllerAnnotation;
    }

    @Override
    public void processToken(int type, int lineNumber, String stringValue) {
        switch (phase) {
            case ANNOTATION: parseAnnotation(type, lineNumber, stringValue); break;
            case SIGNATURE:  parseSignature(type, stringValue);              break;
            case METHOD:     parseMethod(type, lineNumber);                  break;
        }

        if (type == CLOSE_PAREN) {
            openParenCount++;
        } else if (type == OPEN_PAREN) {
            openParenCount--;
        }
    }

    private void setState(SignatureState state) {
        signatureState = state;
    }

    private void parseSignature(int type, @Nullable String stringValue) {

        if (openParenCount == 0 && type == OPEN_CURLY) {
            curlyBraceCount = 1;
            phase = Phase.METHOD;
        }

        switch (signatureState) {
            case START:
                if (type == ARROBA) {
                    setState(SignatureState.ARROBA);
                } else if (stringValue != null && stringValue.equals(BINDING_RESULT) &&
                        lastParamType != null && lastParam != null) {
                    currentModelObject = new ModelField(lastParamType, lastParam, false); // should be type and variable name
                }
                break;
            case ARROBA:
                if (stringValue != null &&
                        (stringValue.equals(REQUEST_PARAM) ||
                        stringValue.equals(PATH_VARIABLE) ||
                        stringValue.equals(COOKIE_VALUE) ||
                        stringValue.equals(SESSION_ATTRIBUTE))) {

                    setState(SignatureState.REQUEST_PARAM);
                    if (stringValue.equals(PATH_VARIABLE)) {
                        pendingParamType = RouteParameterType.PARAMETRIC_ENDPOINT;
                    } else if (stringValue.equals(REQUEST_PARAM)) {
                        pendingParamType = RouteParameterType.QUERY_STRING;
                    } else if (stringValue.equals(COOKIE_VALUE)) {
                        pendingParamType = RouteParameterType.COOKIE;
                    } else {
                        pendingParamType = RouteParameterType.SESSION;
                    }
                } else {
                    setState(SignatureState.START);
                }
                break;
            case REQUEST_PARAM:
                if (type == OPEN_PAREN) {
                    setState(SignatureState.GET_ANNOTATION_VALUE);
                } else {
                    setState(SignatureState.GET_VARIABLE_NAME);
                }
                break;
            case GET_ANNOTATION_VALUE:
                if (type == DOUBLE_QUOTE) {
                    pendingParamName = stringValue;
                    setState(SignatureState.GET_VARIABLE_TYPE);
                } else if ("value".equals(stringValue)) {
                    setState(SignatureState.VALUE);
                } else {
                    setState(SignatureState.ANNOTATION_PARAMS);
                }
                break;
            case GET_VARIABLE_TYPE:
                if (paramTypeAfterCloseParen) {
                    if (type == ')') {
                        paramTypeAfterCloseParen = false;
                    } else {
                        break;
                    }
                }
                if (stringValue != null) {
                    pendingParamDataType = stringValue;
                    RouteParameter newParam = RouteParameter.fromDataType(pendingParamName, pendingParamDataType);
                    newParam.setParamType(pendingParamType);
                    currentParameters.put(pendingParamName, newParam);

                    pendingParamDataType = null;
                    pendingParamName = null;
                    pendingParamType = RouteParameterType.UNKNOWN;
                    setState(SignatureState.START);
                }
                break;
            case ANNOTATION_PARAMS:
                if ("value".equals(stringValue)) {
                    setState(SignatureState.VALUE);
                } else if (type == CLOSE_PAREN) {
                    setState(SignatureState.GET_VARIABLE_NAME);
                }
                break;
            case VALUE:
                if (type == DOUBLE_QUOTE) {
                    paramTypeAfterCloseParen = true;
                    pendingParamName = stringValue;
                    setState(SignatureState.GET_VARIABLE_TYPE);
                } else if (type != EQUALS) {
                    setState(SignatureState.GET_VARIABLE_NAME);
                }
                break;
            case GET_VARIABLE_NAME:
                if (openParenCount == -1) { // this means we're not in an annotation
                    if (type == COMMA || type == CLOSE_PAREN) {
                        RouteParameter newParam = new RouteParameter(lastValue);
                        newParam.setDataType(secondToLastValue);
                        newParam.setParamType(pendingParamType);
                        currentParameters.put(lastValue, newParam);
                        pendingParamType = RouteParameterType.UNKNOWN;
                        setState(SignatureState.START);
                    }
                }
                break;

        }

        // TODO tighten this up a bit
        if (openParenCount == -1 && type == COMMA) {
            lastParam = lastValue;
            lastParamType = secondToLastValue;
        }

        if (stringValue != null) {
            secondToLastValue = lastValue;
            lastValue = stringValue;
        }
    }

    private void parseMethod(int type, int lineNumber) {
        if (type == OPEN_CURLY) {
            curlyBraceCount += 1;
        } else if (type == CLOSE_CURLY) {
            if (curlyBraceCount == 1) {
                addEndpoint(lineNumber);
                signatureState = SignatureState.START;
                phase = Phase.ANNOTATION;
            } else {
                curlyBraceCount -= 1;
            }
        }
    }

    private void parseAnnotation(int type, int lineNumber, @Nullable String stringValue) {
        switch(annotationState) {
            case START:
                if (type == ARROBA) {
                    annotationState = AnnotationState.ARROBA;
                } else if (stringValue != null && stringValue.equals(CLASS)) {
                    inClass = true;
                }
                break;
            case ARROBA:
                if (REQUEST_MAPPING.equals(stringValue)) {
                    annotationState = AnnotationState.REQUEST_MAPPING;
                } else if (PRE_AUTHORIZE.equals(stringValue)) {
                    annotationState = AnnotationState.SECURITY_ANNOTATION;
                } else if (CONTROLLER.equals(stringValue) || REST_CONTROLLER.equals(stringValue)) {
                    hasControllerAnnotation = true;
                    annotationState = AnnotationState.START;
                } else {
                    annotationState = AnnotationState.START;
                }
                break;
            case SECURITY_ANNOTATION:
                if ('"' == type) {
                    parseSecurityString(stringValue);
                } else if (')' == type) {
                    annotationState = AnnotationState.START;
                }
                break;
            case REQUEST_MAPPING:
                if (stringValue != null && stringValue.equals(VALUE)) {
                    annotationState = AnnotationState.VALUE;
                } else if (stringValue != null && stringValue.equals(METHOD)) {
                    annotationState = AnnotationState.METHOD;
                } else if (stringValue != null && stringValue.equals(CLASS)) {
                    inClass = true;
                    annotationState = AnnotationState.START;
                } else if (afterOpenParen && type == DOUBLE_QUOTE) {
                    // If it immediately starts with a quoted value, use it
                    if (inClass) {
                        currentMapping = stringValue;
                        startLineNumber = lineNumber;
                        annotationState = AnnotationState.ANNOTATION_END;
                    } else {
                        classEndpoint = stringValue;
                        annotationState = AnnotationState.START;
                    }
                } else if (type == CLOSE_PAREN){
                    annotationState = AnnotationState.ANNOTATION_END;
                }

                afterOpenParen = type == OPEN_PAREN;

                break;
            case VALUE:
                if (stringValue != null) {
                    if (inClass) {
                        currentMapping = stringValue;
                        startLineNumber = lineNumber;
                    } else {
                        classEndpoint = stringValue;
                    }
                    annotationState = AnnotationState.REQUEST_MAPPING;
                }
                break;
            case METHOD:
                if (stringValue != null) {
                    if (inClass) {
                        methodMethods.add(stringValue);
                    } else {
                        classMethods.add(stringValue);
                    }
                    annotationState = AnnotationState.REQUEST_MAPPING;
                } else if (type == OPEN_CURLY){
                    annotationState = AnnotationState.METHOD_MULTI_VALUE;
                }
                break;
            case METHOD_MULTI_VALUE:
                if (stringValue != null) {
                    if (inClass) {
                        methodMethods.add(stringValue);
                    } else {
                        classMethods.add(stringValue);
                    }
                } else if (type == CLOSE_CURLY) {
                    annotationState = AnnotationState.REQUEST_MAPPING;
                }
                break;
            case ANNOTATION_END:
                if (inClass) {
                    annotationState = AnnotationState.START;
                    phase = Phase.SIGNATURE;
                } else {
                    annotationState = AnnotationState.START;
                }
                break;
        }
    }

    String currentAuthString = null;
    String globalAuthString  = null;

    private void parseSecurityString(String stringValue) {
        if (inClass) {
            currentAuthString = stringValue;
        } else {
            globalAuthString = stringValue;
        }
    }

    private void addEndpoint(int endLineNumber) {
        if (classEndpoint != null) {
            if (currentMapping != null) {
                if (classEndpoint.endsWith("/") || currentMapping.startsWith("/")) {
                    currentMapping = classEndpoint + currentMapping;
                } else {
                    currentMapping = classEndpoint + "/" + currentMapping;
                }
            } else {
                currentMapping = classEndpoint;
            }
        }

        while (currentMapping != null && currentMapping.contains("//")) {
            currentMapping = currentMapping.replace("//","/");
        }

        if (currentMapping != null && currentMapping.indexOf('/') != 0) {
            currentMapping = "/" + currentMapping;
        }

        // It's ok to add a default method here because we must be past the class-level annotation
        if (classMethods.isEmpty()) {
            classMethods.add("RequestMethod.GET");
        }

        if (methodMethods.isEmpty()) {
            methodMethods.addAll(classMethods);
        }

        assert currentMapping != null : "Current mapping should not be null at this point. Check the state machine.";

        String primaryMethod = null;
        SpringControllerEndpoint primaryEndpoint = null;
        if (methodMethods.size() > 0) {
            primaryMethod = methodMethods.get(0).replace("RequestMethod.", "");
            primaryEndpoint = new SpringControllerEndpoint(relativeFilePath, currentMapping,
                    primaryMethod,
                    currentParameters,
                    startLineNumber,
                    endLineNumber,
                    currentModelObject);
            endpoints.add(primaryEndpoint);
        }

        for (String method : methodMethods) {
            method = method.replace("RequestMethod.", "");
            if (primaryMethod.equals(method)) {
                continue;
            }

            SpringControllerEndpoint endpoint = new SpringControllerEndpoint(relativeFilePath, currentMapping,
                    method,
                    currentParameters,
                    startLineNumber,
                    endLineNumber,
                    currentModelObject);

            if (entityMappings != null) {
                endpoint.expandParameters(entityMappings, null);
            }

            if (globalAuthString != null) {
                if (currentAuthString != null) {
                    endpoint.setAuthorizationString(globalAuthString + " and " + currentAuthString);
                } else {
                    endpoint.setAuthorizationString(globalAuthString);
                }
            } else if (currentAuthString != null) {
                endpoint.setAuthorizationString(currentAuthString);
            }

            primaryEndpoint.addVariant(endpoint);
        }

        currentMapping = null;
        methodMethods = list();
        startLineNumber = -1;
        curlyBraceCount = 0;
        currentParameters = map();
        currentModelObject = null;
    }

}
