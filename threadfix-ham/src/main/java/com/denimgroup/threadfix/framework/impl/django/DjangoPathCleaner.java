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

package com.denimgroup.threadfix.framework.impl.django;

import com.denimgroup.threadfix.framework.engine.cleaner.DefaultPathCleaner;
import com.denimgroup.threadfix.framework.engine.partial.PartialMapping;

import java.io.File;
import java.util.List;

/**
 * Created by csotomayor on 5/15/2017.
 */
public class DjangoPathCleaner extends DefaultPathCleaner {

    public DjangoPathCleaner(List<PartialMapping> partialMappings) {
        super(partialMappings);
    }

    public static File buildPath(String root, String input) {
        StringBuilder builder = new StringBuilder(64);
        builder.append(root).append(File.separator).append(input);
        File file = new File(builder.toString());
        return file;
    }

    public static String cleanStringFromCode(String input) {
        return input == null? "" : input.replace('.',File.separatorChar);
    }
}
