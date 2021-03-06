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
package com.denimgroup.threadfix.cds.service;

import com.secdec.astam.common.data.models.Entities;

/**
 * Created by amohammed on 7/22/2017.
 */
public interface AstamEntitiesPushService {
    void pushEntitiesToAstam(Entities.ExternalToolSet externalToolSet);

    void pushExternalToolsSet(Entities.ExternalToolSet externalToolSet);

    boolean pushExternalTool(Entities.ExternalTool externalTool, boolean doesExist);

    void pushCweSet(Entities.CWESet cweSet);

    boolean pushCwe(Entities.CWE cwe, boolean doesExist);
}
