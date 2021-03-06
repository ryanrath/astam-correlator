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


import com.denimgroup.threadfix.cds.rest.AstamApplicationClient;
import com.denimgroup.threadfix.cds.rest.response.RestResponse;
import com.denimgroup.threadfix.cds.service.AstamApplicationImporter;
import com.denimgroup.threadfix.data.dao.ApplicationDao;
import com.denimgroup.threadfix.data.dao.OrganizationDao;
import com.denimgroup.threadfix.data.entities.Application;
import com.denimgroup.threadfix.data.entities.Organization;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import com.denimgroup.threadfix.mapper.ThreadfixApplicationMapper;
import com.secdec.astam.common.data.models.Appmgmt;
import com.secdec.astam.common.data.models.Appmgmt.ApplicationRegistration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*import com.denimgroup.threadfix.service.ApplicationService;*/

@Service
public class AstamApplicationImporterImpl implements AstamApplicationImporter {

    private final SanitizedLogger log = new SanitizedLogger(AstamApplicationImporterImpl.class);

    private AstamApplicationClient astamApplicationClient;
    private ApplicationDao applicationDao;
    private OrganizationDao organizationDao;

/*
    @Lazy
    @Autowired
    private ApplicationService applicationService;
*/

    AstamApplicationClient applicationClient;


    @Autowired
    public AstamApplicationImporterImpl(AstamApplicationClient applicationClient){
        this.applicationClient = applicationClient;
    }

    @Override
    public void importAllApplications(){
        RestResponse<Appmgmt.ApplicationRegistrationSet> response = astamApplicationClient.getAllAppRegistrations();
        if(response.responseCode == 200 || response.success){
            Appmgmt.ApplicationRegistrationSet appRegistrationSet = response.getObject();
            if(appRegistrationSet != null && appRegistrationSet.getApplicationRegistrationsList() != null) {
                List<ApplicationRegistration> appRegistrationsList = appRegistrationSet.getApplicationRegistrationsList();
                for (ApplicationRegistration appRegistration : appRegistrationsList) {
                    importApplication(appRegistration);
                }
            }
        }else{//TODO: Handle response codes here.
            log.debug("AstamApImporter importAllApps received a " + response.responseCode + " responseCode.");
        }
    }

    @Override
    public void importApplications(List<String> uuids){
        for(String uuid : uuids){
            importApplication(uuid);
        }
    }

    private void importApplication(ApplicationRegistration appRegistration){
        ThreadfixApplicationMapper applicationMapper = new ThreadfixApplicationMapper();
        Application application = applicationMapper.createApplication(appRegistration);
        if(application == null  && StringUtils.isBlank(application.getName())) {
            return;
        }

        if (application.getFrameworkType().equals(FrameworkType.NONE.toString()))
            application.setFrameworkType(FrameworkType.DETECT.toString());
        // check if it exists
        Organization organization = organizationDao.retrieveByName(application.getOrganization().getName());
        if(organization == null) {
            organizationDao.saveOrUpdate(application.getOrganization());
        }

        int id = retrieveByUuid(application.getUuid());
        if (id != 0){
            application.setId(id);
        }
        applicationDao.saveOrUpdate(application);
    }


    @Override
    public void importApplication(String uuid){
        RestResponse<ApplicationRegistration> response = astamApplicationClient.getAppRegistration(uuid);
        ApplicationRegistration  appRegistration = response.getObject();
        importApplication(appRegistration);
    }


    @Override
    public void deleteApplications(List<String> uuids) {
        List<Application> apps = getCorrespondingApplications(uuids);

        for(Application app : apps){
            deleteApplication(app);
        }
    }

    private void deleteApplication(Application application){
        //TODO: fix this. Add dependency for threadfix-services-interfaces and autowire using @Lazy
   /*     applicationService.deactivateApplication(application);*/
    }

    @Override
    public void deleteApplication(String uuid) {

    }

    //TODO: implemnt this in Dao/Hibernate retriveByUuid(String uuid)
    private int retrieveByUuid(String uuid){
        int appId = 0;
        List<Application> apps = applicationDao.retrieveAllActive();
        for(Application app : apps){
            if(uuid.equalsIgnoreCase(app.getUuid())){
                appId =  app.getId();
                break;
            }
        }
        return appId;
    }

    //TODO: change this also
    private List<Application> getCorrespondingApplications(List<String> uuids){
        List<Application> apps = applicationDao.retrieveAllActive();
        List<Application> correspondingApps = new ArrayList<>();

        for(Application app : apps){
            if (uuids.contains(app.getUuid())){

                if(!correspondingApps.contains(app)){
                    correspondingApps.add(app);
                }
            }
        }
        return correspondingApps;
    }

}
