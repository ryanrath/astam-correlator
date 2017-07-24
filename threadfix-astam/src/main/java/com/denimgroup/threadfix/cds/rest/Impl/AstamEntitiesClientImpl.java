// Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// This material is based on research sponsored by the Department of Homeland
// Security (DHS) Science and Technology Directorate, Cyber Security Division
// (DHS S&T/CSD) via contract number HHSP233201600058C.

package com.denimgroup.threadfix.cds.rest.Impl;

import com.denimgroup.threadfix.cds.rest.AstamEntitiesClient;
import com.denimgroup.threadfix.cds.rest.response.RestResponse;
import com.denimgroup.threadfix.data.entities.AstamConfiguration;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import com.google.protobuf.InvalidProtocolBufferException;
import com.secdec.astam.common.data.models.Entities;

/**
 * Created by amohammed on 7/11/2017.
 */
public class AstamEntitiesClientImpl implements AstamEntitiesClient {

    private static final SanitizedLogger LOGGER = new SanitizedLogger(AstamEntitiesClientImpl.class);

    final HttpUtils httpUtils;

    private final static String CONTROLLER_ENTITIES = "entities/",
            CWE = "cwe/",
            CAPEC = "capec/",
            EXTERNAL_TOOL = "externalTool/",
            EXCEPTION_MESSAGE = "InvalidProtocolBufferException while attempting to parse retrieved protobuf data.";

    public AstamEntitiesClientImpl(AstamConfiguration astamConfiguration){
        httpUtils = new HttpUtils(astamConfiguration);
    }

    @Override
    public RestResponse<Entities.CWESet> getAllCWEs() {
        RestResponse<Entities.CWESet> response = httpUtils.httpGet(CONTROLLER_ENTITIES + CWE);
        if(response.success){
            try{
                response.object = Entities.CWESet.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse createCWE(Entities.CWE cwe) {
        byte[] entity = cwe.toByteArray();
        RestResponse response = httpUtils.httpPost(CONTROLLER_ENTITIES + CWE, entity);
        return response;
    }

    @Override
    public RestResponse<Entities.CWE> getCWE(String cweIdParam) {
        RestResponse<Entities.CWE> response = httpUtils.httpGet(CONTROLLER_ENTITIES + CWE, cweIdParam);
        if(response.success) {
            try {
                response.object = Entities.CWE.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse updateCWE(String cweIdParam, Entities.CWE cwe) {
        byte[] entity = cwe.toByteArray();
        RestResponse response = httpUtils.httpPut(CONTROLLER_ENTITIES + CWE, cweIdParam, entity);
        return response;
    }

    @Override
    public RestResponse<Entities.CAPECSet> getAllCAPECs() {
        RestResponse<Entities.CAPECSet> response = httpUtils.httpGet(CONTROLLER_ENTITIES + CAPEC);
        if(response.success){
            try {
                response.object = Entities.CAPECSet.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse createCAPEC(Entities.CAPEC capec) {
        byte[] entity = capec.toByteArray();
        RestResponse response = httpUtils.httpPost(CONTROLLER_ENTITIES + CAPEC, entity);
        return response;
    }

    @Override
    public RestResponse<Entities.CAPEC> getCAPEC(String capecIdParam) {
        RestResponse<Entities.CAPEC> response = httpUtils.httpGet(CONTROLLER_ENTITIES + CAPEC, capecIdParam);

        if(response.success){
            try {
                response.object = Entities.CAPEC.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse updateCAPEC(String capecIdParam, Entities.CAPEC capec) {
        byte[] entity = capec.toByteArray();
        RestResponse response = httpUtils.httpPut(CONTROLLER_ENTITIES + CAPEC, capecIdParam, entity);
        return response;
    }

    @Override
    public RestResponse<Entities.ExternalToolSet> getAllExternalTools() {
        RestResponse<Entities.ExternalToolSet> response = httpUtils.httpGet(CONTROLLER_ENTITIES + EXTERNAL_TOOL);

        if(response.success){
            try {
                response.object = Entities.ExternalToolSet.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse createExternalTool(Entities.ExternalTool externalTool) {
        byte[] entity = externalTool.toByteArray();
        RestResponse response = httpUtils.httpPost(CONTROLLER_ENTITIES + EXTERNAL_TOOL, entity);
        return response;
    }

    @Override
    public RestResponse<Entities.ExternalTool> getExternalTool(String externalToolIdParam) {
        RestResponse<Entities.ExternalTool> response = httpUtils.httpGet(
                CONTROLLER_ENTITIES + EXTERNAL_TOOL,
                externalToolIdParam);
        if(response.success){
            try {
                response.object = Entities.ExternalTool.parseFrom(response.data);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error(EXCEPTION_MESSAGE, e);
            }
        }
        return response;
    }

    @Override
    public RestResponse updateExternalTool( String externalToolIdParam, Entities.ExternalTool externalTool) {
        byte[] entity = externalTool.toByteArray();
        RestResponse response = httpUtils.httpPut(CONTROLLER_ENTITIES + EXTERNAL_TOOL,
                externalToolIdParam, entity);
        return response;
    }

    @Override
    public RestResponse deleteExternalTool(String externalToolIdParam) {
        RestResponse response = httpUtils.httpDelete(CONTROLLER_ENTITIES + EXTERNAL_TOOL, externalToolIdParam);
        return response;
    }
}
