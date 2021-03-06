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

package com.denimgroup.threadfix.service;

import com.denimgroup.threadfix.data.dao.WebAttackSurfaceDao;
import com.denimgroup.threadfix.data.entities.*;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.data.enums.SourceCodeAccessLevel;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.engine.ProjectConfig;
import com.denimgroup.threadfix.framework.engine.ThreadFixInterface;
import com.denimgroup.threadfix.framework.engine.cleaner.PathCleaner;
import com.denimgroup.threadfix.framework.engine.cleaner.PathCleanerFactory;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabase;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import com.denimgroup.threadfix.service.translator.FindingProcessorFactory;
import com.denimgroup.threadfix.util.ProtobufMessageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

/**
 * Created by csotomayor on 3/3/2017.
 */
@Service
@Transactional(readOnly = false)
public class WebAttackSurfaceServiceImpl implements WebAttackSurfaceService{
    private static final SanitizedLogger LOG = new SanitizedLogger(WebAttackSurfaceServiceImpl.class);

    @Autowired
    private ApplicationVersionService applicationVersionService;

    @Autowired
    private WebAttackSurfaceDao webAttackSurfaceDao;

    private WebAttackSurface createWebAttackSurface(Finding finding, Endpoint endpoint) {
        WebAttackSurface webAttackSurface = new WebAttackSurface();

        SurfaceLocation surfaceLocation = new SurfaceLocation();
        surfaceLocation.setUrl(finding.getSurfaceLocation().getUrl());
        surfaceLocation.setHttpMethod(finding.getSurfaceLocation().getHttpMethod());
        surfaceLocation.setParameter(finding.getSurfaceLocation().getParameter());
        webAttackSurface.setSurfaceLocation(surfaceLocation);

        DataFlowElement dataFlowElement = new DataFlowElement();
        dataFlowElement.setLineNumber(endpoint.getStartingLineNumber());
        dataFlowElement.setSourceFileName(endpoint.getFilePath());
        //dataFlowElement.setFinding(finding);
        webAttackSurface.setDataFlowElement(dataFlowElement);

        Application app = finding.getScan().getApplication();
        webAttackSurface.setApplication(app);

        return webAttackSurface;
    }

    @Override
    @Transactional(readOnly = false)
    public void storeWebAttackSurface(Application application, Scan scan) {
        SourceCodeAccessLevel accessLevel = FindingProcessorFactory.getSourceCodeAccessLevel(application, scan);
        if (accessLevel != SourceCodeAccessLevel.FULL) return;

        File rootFile = FindingProcessorFactory.getRootFile(application);
        FrameworkType frameworkType = FindingProcessorFactory.getFrameworkType(application, accessLevel, rootFile, scan);

        ProjectConfig config = new ProjectConfig(frameworkType, accessLevel, rootFile, "/");

        PathCleaner cleaner = PathCleanerFactory.getPathCleaner(
                config.getFrameworkType(), ThreadFixInterface.toPartialMappingList(scan));

        EndpointDatabase database = EndpointDatabaseFactory.getDatabase(config.getRootFile(),
                config.getFrameworkType(), cleaner);

        if (database == null) {
            LOG.error("Endpoint database not found.");
            return;
        }

        for (Finding finding : scan.getFindings()) {
            Endpoint endpoint = database.findBestMatch(ThreadFixInterface.toEndpointQuery(finding));
            if (endpoint == null || webAttackSurfaceDao.getFilePaths().contains(endpoint.getFilePath())) {
                continue;
            }

            WebAttackSurface webAttackSurface = createWebAttackSurface(finding, endpoint);
            webAttackSurfaceDao.saveOrUpdate(webAttackSurface);
            //TODO: look into this
            webAttackSurface.setUuid(ProtobufMessageUtils.createUUIDFromInt(webAttackSurface.getId()).getValue());
            webAttackSurfaceDao.saveOrUpdate(webAttackSurface);

        }
    }
}
