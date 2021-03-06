////////////////////////////////////////////////////////////////////////
//
//     Copyright (C) 2017 Applied Visions - http://securedecisions.com
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
//     This material is based on research sponsored by the Department of Homeland
//     Security (DHS) Science and Technology Directorate, Cyber Security Division
//     (DHS S&T/CSD) via contract number HHSP233201600058C.
//
//     Contributor(s):
//              Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.cds.messaging;

import com.denimgroup.threadfix.cds.service.AstamApplicationImporter;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import com.secdec.astam.common.messaging.Messaging;
import com.secdec.astam.common.messaging.Messaging.AstamMessage.DataMessage.DataAction;
import com.secdec.astam.common.messaging.Messaging.AstamMessage.DataMessage.DataSetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.secdec.astam.common.messaging.Messaging.AstamMessage.DataMessage.DataEntity.DATA_APPLICATION_REGISTRATION;

/**
 * Created by amohammed on 7/19/2017.
 * This class triggers actions based on messages broadcasted by the CDS
 */

@Component
public class AstamMessageTriggerImpl implements AstamMessageTrigger{

    private static final SanitizedLogger LOGGER = new SanitizedLogger(AstamMessageTriggerImpl.class);

    @Autowired
    private AstamApplicationImporter applicationImporter;

    public AstamMessageTriggerImpl(){}

    @Override
    public void parse(Messaging.AstamMessage message){
        Messaging.AstamMessage.DataMessage dataMessage = message.getDataMessage();
        if(dataMessage == null){
            return;
        }

        Messaging.AstamMessage.DataMessage.DataEntity dataEntity = dataMessage.getDataEntity();
        //TODO: subscribe to Version, Deployment, Environment events
        if(dataEntity == DATA_APPLICATION_REGISTRATION){
            DataAction dataAction = dataMessage.getDataAction();
            DataSetType dataSetType = dataMessage.getDataSetType();
            if(dataAction == DataAction.DATA_CREATE || dataAction == DataAction.DATA_UPDATE){


                if(dataSetType == DataSetType.DATA_SET_SINGLE || dataSetType == DataSetType.DATA_SET_COMPLETE){
                    List<String> uuids = dataMessage.getEntityIdsList();
                    //applicationImporter.importApplications(uuids);
                }
            } else if(dataAction == DataAction.DATA_DELETE){
                if(dataSetType == DataSetType.DATA_SET_SINGLE || dataSetType == DataSetType.DATA_SET_COMPLETE) {
                    List<String> uuids = dataMessage.getEntityIdsList();
                    //applicationImporter.deleteApplications(uuids);
                }
            }
        }

    }

}
