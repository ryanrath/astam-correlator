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
package com.denimgroup.threadfix.cds.service.integration;

import com.denimgroup.threadfix.cds.service.*;
import com.denimgroup.threadfix.data.dao.ApplicationDao;
import com.denimgroup.threadfix.data.entities.Application;
import com.denimgroup.threadfix.mapper.AstamEntitiesMapper;
import com.secdec.astam.common.data.models.Appmgmt.ApplicationRegistration;
import com.secdec.astam.common.data.models.Entities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * Created by amohammed on 6/29/2017.
 */
@Service
public class AstamPushServiceImpl implements AstamPushService {

    private final ApplicationDao applicationDao;
    private final AstamRemoteApplicationService astamApplicationService;
    private final AstamRemoteFindingsService astamFindingsService;
    private final AstamRemoteAttackSurfaceService astamAttackSurfaceService;


    private AstamApplicationPushService applicationPushService;

    private AstamAttackSurfacePushService attackSurfacePushService;

    private AstamFindingsPushService findingsPushService;

    private AstamEntitiesPushService astamEntitiesPushService;

    @Autowired
    public AstamPushServiceImpl(ApplicationDao applicationDao,
                                AstamRemoteApplicationService astamApplicationService,
                                AstamRemoteFindingsService astamFindingsService,
                                AstamRemoteAttackSurfaceService astamAttackSurfaceService,
                                AstamEntitiesPushService astamEntitiesPushService,
                                AstamApplicationPushService astamApplicationPushService,
                                AstamAttackSurfacePushService astamAttackSurfacePushService,
                                AstamFindingsPushServiceImpl astamFindingsPushService) {

        this.applicationDao = applicationDao;
        this.astamApplicationService = astamApplicationService;
        this.astamFindingsService = astamFindingsService;
        this.astamAttackSurfaceService = astamAttackSurfaceService;

        this.astamEntitiesPushService = astamEntitiesPushService;
        this.applicationPushService = astamApplicationPushService;
        this.attackSurfacePushService = astamAttackSurfacePushService;
        this.findingsPushService = astamFindingsPushService;
    }

    @Override
    public void pushAllToAstam(){
        List<Application> applicationList = applicationDao.retrieveAll();
        for (int i=0; i<applicationList.size(); i++) {
            Application app = applicationList.get(i);
            pushSingleAppToAstam(app);
        }
    }

    @Override
    public void pushSingleAppToAstam(Application app){
        int appId = app.getId();
        pushAppMngmtToAstam(app);
        //pushEntitiesToAstam(app);
        //TODO:
        //pushAttackSurfaceToAstam(appId);
        //pushFindingsToAstam(appId);
    }


    @Override
    public void pushEntitiesToAstam(Application app){
        AstamEntitiesMapper astamMapper = new AstamEntitiesMapper();
        Entities.ExternalToolSet externalToolSet = astamMapper.getExternalToolSet(app);
        astamEntitiesPushService.pushEntitiesToAstam(externalToolSet);
    }

    @Override
    public void pushAppMngmtToAstam(Application app) {
        //boolean success = false;

        ApplicationRegistration appRegistration = astamApplicationService.getAppRegistration(app);
        applicationPushService.pushAppRegistration(app.getId(), appRegistration);

        //TODO: create local enitites (ApplicationVersion/SourceCodeStatus, Deployment and Environment) when an Application is created.
        //TODO: fix relationships between entities as required by CDS
       /*if(!success){
            return;
        }*/
        /*
        success = false;
        Appmgmt.ApplicationEnvironment appEnvironment = astamApplicationService.getAppEnvironment();
        success = applicationPushService.pushAppEnvironment(appEnvironment, false);
        if(!success){
            return;
        }

        success = false;
        Appmgmt.ApplicationVersion appVersion = astamApplicationService.getAppVersion();
        success = applicationPushService.pushAppVersion(appVersion, false);
        if(!success){
            return;
        }

        success = false;
        Appmgmt.ApplicationDeployment appDeployment = astamApplicationService.getAppDeployment();
        success = applicationPushService.pushAppDeployment(appDeployment, false);*/
    }

    @Override
    public void pushFindingsToAstam(int applicationId){
        //TODO:
      /*  astamFindingsService.setup(applicationId);
        boolean success = false;


        Findings.RawFindingsSet rawFindingsSet = astamFindingsService.getRawFindingsSet();
        findingsPushService.pushRawFindingsSet(rawFindingsSet);

        //Don't push Sast or Dast findings until RawFindings are pushed
        Findings.SastFindingSet sastFindingSet = astamFindingsService.getSastFindings();
        findingsPushService.pushSastFindingSet(sastFindingSet);

        Findings.DastFindingSet dastFindingSet = astamFindingsService.getDastFindings();
        findingsPushService.pushDastFindingSet(dastFindingSet);

        Findings.CorrelationResultSet correlationResultSet = astamFindingsService.getCorrelatedResultSet();
        findingsPushService.p

        Findings.CorrelatedFindingSet correlatedFindingSet = astamFindingsService.getCorrelatedFindings();
        findingsPushService.pushCorrelatedFindingSet(correlatedFindingSet);*/

    }

    @Override
    public void pushAttackSurfaceToAstam(int applicationId){
        //TODO:
     /*   astamApplicationService.setup(applicationId);

        boolean success = false;
        while(!success){
            Attacksurface.RawDiscoveredAttackSurface rawDiscoveredAttackSurface = astamAttackSurfaceService.getRawDiscoveredAttackSurface();
            success = attackSurfacePushService.pushRawDiscoveredAttackSurface(rawDiscoveredAttackSurface, false);
        }

        Attacksurface.EntryPointWebSet entryPointWebSet = astamAttackSurfaceService.getEntryPointWebSet();
        attackSurfacePushService.pushEntryPointWebSet(entryPointWebSet);*/
    }
}

