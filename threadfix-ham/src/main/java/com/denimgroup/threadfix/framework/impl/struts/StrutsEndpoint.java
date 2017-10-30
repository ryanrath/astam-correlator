////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2014 Denim Group, Ltd.
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
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.framework.impl.struts;

import com.denimgroup.threadfix.data.enums.ParameterDataType;
import com.denimgroup.threadfix.framework.engine.AbstractEndpoint;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.denimgroup.threadfix.CollectionUtils.setFrom;

public class StrutsEndpoint extends AbstractEndpoint {

    private String filePath;
    private String urlPath;
    private Pattern pathRegex = null;

    private Set<String> methods;
    private Map<String, ParameterDataType> parameters;

    public StrutsEndpoint(String filePath, String urlPath,
                          Collection<String> methods, Map<String, ParameterDataType> parameters) {
        this.filePath = filePath;
        this.urlPath = urlPath;
        this.methods = setFrom(methods);
        this.parameters = parameters;

        String regexString = "^" + urlPath
                .replaceAll("\\{.+\\}", "([^\\/]+)")
                .replaceAll("/", "\\\\/");

        if (!regexString.endsWith("*")) {
            regexString += "$";
        }
        pathRegex = Pattern.compile(regexString);
    }

    @Nonnull
    @Override
    public int compareRelevance(String endpoint) {

        if (urlPath.equalsIgnoreCase(endpoint)) {
            return 100;
        } else if (pathRegex.matcher(endpoint).find()) {
            return pathRegex.toString().length();
        } else {
            return -1;
        }
    }

    @Nonnull
    @Override
    public Map<String, ParameterDataType> getParameters() {
        return parameters;
    }

    @Nonnull
    @Override
    public Set<String> getHttpMethods() {
        return methods;
    }

    @Nonnull
    @Override
    public String getUrlPath() {
        return urlPath;
    }

    @Nonnull
    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public int getStartingLineNumber() {
        return 0;
    }

    @Override
    public int getLineNumberForParameter(String parameter) {
        return 0;
    }

    @Override
    public boolean matchesLineNumber(int lineNumber) {
        return true;
    }

    @Nonnull
    @Override
    protected List<String> getLintLine() {
        return null;
    }
}
