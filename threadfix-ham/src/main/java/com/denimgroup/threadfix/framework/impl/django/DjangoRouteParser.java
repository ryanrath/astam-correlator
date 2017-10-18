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

package com.denimgroup.threadfix.framework.impl.django;

import com.denimgroup.threadfix.framework.util.EventBasedTokenizer;
import com.denimgroup.threadfix.framework.util.EventBasedTokenizerRunner;
import com.denimgroup.threadfix.logging.SanitizedLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.StreamTokenizer;
import java.util.Map;
import java.util.StringTokenizer;

import static com.denimgroup.threadfix.CollectionUtils.map;

/**
 * Created by csotomayor on 5/12/2017.
 */
public class DjangoRouteParser implements EventBasedTokenizer{

    public static final SanitizedLogger LOG = new SanitizedLogger(DjangoRouteParser.class);
    public static final boolean logParsing = false;

    //alias, path
    private Map<String, String> importPathMap = map();
    //url, djangoroute
    private Map<String, DjangoRoute> routeMap = map();

    private String sourceRoot;

    private String rootPath = "";

    public DjangoRouteParser(String sourceRoot, String rootPath) {
        this.sourceRoot = sourceRoot;
        this.rootPath = rootPath;
    }

    public static Map parse(String sourceRoot, String rootPath, @Nonnull File file) {
        DjangoRouteParser routeParser = new DjangoRouteParser(sourceRoot, rootPath);
        EventBasedTokenizerRunner.run(file, routeParser);
        return routeParser.routeMap;
    }

    private static final String
        IMPORT_START = "from",
        IMPORT = "import",
        ALIAS = "as",
        URL = "url",
        URLPATTERNS = "urlpatterns",
        REGEXSTART = "r",
        INCLUDE = "include",
        TEMPLATE = "TemplateView.as_view",
        REDIRECT = "RedirectView.as_view",
        CACHE = "cache";

    private enum Phase {
        PARSING, IN_IMPORT, IN_URL, IN_COMMENT
    }
    private Phase           currentPhase        = Phase.PARSING;
    private ImportState     currentImportState  = ImportState.START;
    private UrlState        currentUrlState     = UrlState.START;

    @Override
    public boolean shouldContinue() {
        return true;
    }

    private void log(Object string) {
        if (logParsing && string != null) {
            LOG.debug(string.toString());
        }
    }

    //each url file can reference other url files to be parsed, call recursively
    //urls are found in urlpatterns = [..] section with reference to view (controller)
    @Override
    public void processToken(int type, int lineNumber, String stringValue) {

        log("type  : " + type);
        log("string: " + stringValue);
        log("phase: " + currentPhase + " ");

        if (URL.equals(stringValue) || URLPATTERNS.equals(stringValue)){
            currentPhase = Phase.IN_URL;
            currentUrlState = UrlState.START;
        } else if (IMPORT_START.equals(stringValue)) {
            currentPhase = Phase.IN_IMPORT;
            currentImportState = ImportState.START;
        } else if (type == '#') {
            currentPhase = Phase.IN_COMMENT;
        }

        switch (currentPhase) {
            case IN_IMPORT:
                processImport(type, stringValue);
                break;
            case IN_URL:
                processUrl(type, stringValue);
                break;
            case IN_COMMENT:
                break;
        }
    }

    private enum ImportState {
        START, ROOTIMPORTPATH, FILENAME, ALIASKEYWORD, ALIAS
    }
    private String alias, path;
    private void processImport(int type, String stringValue) {
        log(currentImportState);

        switch (currentImportState) {
            case START:
                alias = ""; path = "";
                if (IMPORT_START.equals(stringValue))
                    currentImportState = ImportState.ROOTIMPORTPATH;
                break;
            case ROOTIMPORTPATH:
                if (IMPORT.equals(stringValue))
                    currentImportState = ImportState.FILENAME;
                else
                    path = DjangoPathCleaner.cleanStringFromCode(stringValue);
                break;
            case FILENAME:
                if (ALIAS.equals(stringValue)) {
                    importPathMap.remove(alias);
                    alias = "";
                    currentImportState = ImportState.ALIAS;
                } else if (stringValue != null){
                    alias = stringValue;
                    path += "/" + stringValue;
                    importPathMap.put(alias, path);
                }
                break;
            case ALIAS:
                if (importPathMap.containsKey(alias)) importPathMap.remove(alias);

                if (type == StreamTokenizer.TT_WORD) {
                    alias += stringValue;
                    importPathMap.put(alias, path);
                } else if (type == '_') {
                    alias += "_";
                    importPathMap.put(alias, path);
                }
                break;
        }
    }

    private enum UrlState {
        START, REGEX, VIEWOPTIONS, VIEWPATH, INCLUDE, TEMPLATE, REDIRECT, CACHE
    }
    private StringBuilder regexBuilder;
    private boolean inRegex = false;
    private String viewPath = "";
    private void processUrl(int type, String stringValue) {
        log(currentUrlState);
        switch (currentUrlState) {
            case START:
                regexBuilder = new StringBuilder("");
                if (REGEXSTART.equals(stringValue))
                    currentUrlState = UrlState.REGEX;
                break;
            case REGEX:
                if (stringValue!= null && stringValue.startsWith("^")){
                    inRegex = true;
                    regexBuilder.append(stringValue.substring(1,stringValue.length()-1));
                    currentUrlState = UrlState.VIEWOPTIONS;
                } /*

                TODO adjust to handle regex values

                else if (type == '(' || type == '$') {
                    inRegex = false;
                    currentUrlState = UrlState.VIEWOPTIONS;
                } else if (inRegex)
                    regexBuilder.append(stringValue);

                    */
                break;
            case VIEWOPTIONS:
                if (type != StreamTokenizer.TT_WORD) break;
                if (INCLUDE.equals(stringValue)) {
                    viewPath = "";
                    currentUrlState = UrlState.INCLUDE;
                } else if (TEMPLATE.equals(stringValue))
                    currentUrlState = UrlState.TEMPLATE;
                else if (REDIRECT.equals(stringValue))
                    currentUrlState = UrlState.REDIRECT;
                else if (CACHE.equals(stringValue))
                    currentUrlState = UrlState.CACHE;
                else {
                    viewPath = stringValue;
                    currentUrlState = UrlState.VIEWPATH;
                }
                break;
            case VIEWPATH:
                if (type == StreamTokenizer.TT_WORD) {
                    viewPath += stringValue;
                } else if (type == '_') {
                    viewPath += "_";
                } else {
                    //run through controller parser
                    /*
                    should have two entries:
                    0 - controller(view) path
                    1 - method name
                     */
                    StringTokenizer tokenizer = new StringTokenizer(viewPath, ".");
                    String pathToken = tokenizer.nextToken();
                    String methodToken = tokenizer.nextToken();

                    if (importPathMap.containsKey(pathToken))
                        viewPath = importPathMap.get(pathToken);
                    else
                        viewPath = pathToken;

                    File controller = new File(sourceRoot, viewPath + ".py");
                    if (controller.exists()) {
                        String urlPath = rootPath + "/" + regexBuilder.toString();
                        routeMap.put(urlPath, DjangoControllerParser.parse(controller, urlPath, methodToken));
                    }
                    currentPhase = Phase.PARSING;
                    currentUrlState = UrlState.START;
                }
                break;
            case INCLUDE:
                //run back through url parser
                if (type == StreamTokenizer.TT_WORD) {
                    viewPath += stringValue;
                } else if (type == '_') {
                    viewPath += "_";
                } else if (!viewPath.isEmpty() &&
                        (importPathMap.containsKey(viewPath) || importPathMap.containsKey(viewPath.split("\\.")[0]))) {
                    File importFile = new File(sourceRoot + "/" + importPathMap.get(viewPath));
                    if (importFile.isDirectory()) {
                        for (File file : importFile.listFiles())
                            routeMap.putAll(DjangoRouteParser.parse(sourceRoot, rootPath+regexBuilder.toString(), file));
                    } else {
                        routeMap.putAll(DjangoRouteParser.parse(sourceRoot, rootPath+regexBuilder.toString(), importFile));
                    }
                }
                break;
            case TEMPLATE:
                //TODO
                break;
            case REDIRECT:
                //TODO
                break;
            case CACHE:
                //TODO
                break;
        }
    }
}