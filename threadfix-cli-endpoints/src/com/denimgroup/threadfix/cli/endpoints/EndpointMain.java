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
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////

package com.denimgroup.threadfix.cli.endpoints;

import com.denimgroup.threadfix.data.entities.RouteParameter;
import com.denimgroup.threadfix.data.entities.RouteParameterType;
import com.denimgroup.threadfix.data.enums.FrameworkType;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabase;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory;
import com.denimgroup.threadfix.framework.util.EndpointValidationStatistics;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.data.interfaces.Endpoint.PrintFormat.JSON;

public class EndpointMain {
    private static final String FRAMEWORK_COMMAND = "-framework=";
    enum Logging {
        ON, OFF
    }

    static Logging logging = Logging.OFF;
    static Endpoint.PrintFormat printFormat = Endpoint.PrintFormat.DYNAMIC;
    static FrameworkType framework = FrameworkType.DETECT;
    static boolean simplePrint = false;
    static String pathListFile = null;

    public static void main(String[] args) {
        if (checkArguments(args)) {
            resetLoggingConfiguration();
            if (pathListFile != null) {
                System.out.println("Loading path list file at '" + pathListFile + "'");
                List<String> fileContents;
                try {
                    fileContents = FileUtils.readLines(new File(pathListFile));
                    List<File> requestedTargets = list();
                    int lineNo = 1;
                    for (String line : fileContents) {
                        line = line.trim();
                        if (!line.startsWith("#") && !line.isEmpty()) {
                            File asFile = new File(line);
                            if (!asFile.exists()) {
                                System.out.println("WARN - Unable to find input path '" + line + "' at line " + lineNo + " of " + pathListFile);
                            } else if (!asFile.isDirectory()) {
                                System.out.println("WARN - Input path '" + line + "' is not a directory, at line " + lineNo + " of " + pathListFile);
                            } else {
                                requestedTargets.add(asFile);
                            }
                        }
                        ++lineNo;
                    }

                    for (File file : requestedTargets) {
                        System.out.println("Beginning endpoint detection for '" + file.getName() + "'");
                        listEndpoints(file);
                        System.out.println("Finished endpoint detection for '" + file.getName() + "'");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Unable to read path-list at " + pathListFile);
                    printError();
                }
            } else {
                listEndpoints(new File(args[0]));
            }
            if (printFormat != JSON) {
                System.out.println("To enable logging include the -debug argument");
            }
        } else {
            printError();
        }
    }

    private static boolean checkArguments(String[] args) {
        if (args.length == 0) {
            return false;
        }

        File rootFile = new File(args[0]);

        if (rootFile.exists() && rootFile.isDirectory() || args[0].startsWith("-path-list-file")) {

            List<String> arguments = list(args);

            if (rootFile.exists()) {
                arguments.remove(0);
            }

            for (String arg : arguments) {
                if (arg.equals("-debug")) {
                    logging = Logging.ON;
                } else if (arg.equals("-lint")) {
                    printFormat = Endpoint.PrintFormat.LINT;
                } else if (arg.equals("-json")) {
                    printFormat = JSON;
                } else if (arg.contains(FRAMEWORK_COMMAND)) {
                    String frameworkName = arg.substring(arg.indexOf(
                            FRAMEWORK_COMMAND) + FRAMEWORK_COMMAND.length(), arg.length());
                    framework = FrameworkType.getFrameworkType(frameworkName);
                } else if (arg.equals("-simple")) {
                    simplePrint = true;
                } else if (arg.startsWith("-path-list-file=")) {
                    String[] parts = arg.split("=");
                    String path = parts[1];
                    if (path == null || path.isEmpty()) {
                        System.out.println("Invalid -path-list-file argument, value is empty");
                        continue;
                    }
                    if (path.startsWith("\"") || path.startsWith("'")) {
                        path = path.substring(1);
                    }
                    if (path.endsWith("\"") || path.endsWith("'")) {
                        path = path.substring(0, path.length() - 1);
                    }
                    pathListFile = path;
                } else {
                    System.out.println("Received unsupported option " + arg + ", valid arguments are -lint, -debug, -json, -path-list-file, and -simple");
                    return false;
                }
            }

            return true;

        } else {
            System.out.println("Please enter a valid file path as the first parameter.");
        }

        return false;
    }

    static void printError() {
        System.out.println("The first argument should be a valid file path to scan. Other flags supported: -lint, -debug, -json, -path-list-file, -simple");
    }

    private static void listEndpoints(File rootFile) {
        List<Endpoint> endpoints = list();

        EndpointDatabase database = (framework.equals(FrameworkType.DETECT)) ?
                EndpointDatabaseFactory.getDatabase(rootFile) :
                EndpointDatabaseFactory.getDatabase(rootFile, framework);

        if (database != null) {
            endpoints = database.generateEndpoints();
        }

        Collections.sort(endpoints);

        if (endpoints.isEmpty()) {
            System.out.println("No endpoints were found.");

        } else {
            System.out.println("Generated " + endpoints.size() + " endpoints");
            if (!simplePrint) {
                if (printFormat == JSON) {
                    Endpoint.Info[] infos = getEndpointInfo(endpoints);

                    try {
                        String s = new ObjectMapper().writeValueAsString(infos);
                        System.out.println(s);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    for (Endpoint endpoint : endpoints) {
                        System.out.println(endpoint.getCSVLine(printFormat));
                    }
                }
            }
        }

        List<RouteParameter> detectedParameters = list();
        for (Endpoint endpoint : endpoints) {
            detectedParameters.addAll(endpoint.getParameters().values());
        }

        System.out.println("Generated " + detectedParameters.size() + " parameters");

        int numOptional = 0;
        int numHaveDataType = 0;
        int numHaveParamType = 0;
        int numHaveAcceptedValues = 0;
        for (RouteParameter param : detectedParameters) {
            if (param.isOptional()) {
                ++numOptional;
            }
            if (param.getDataType() != null) {
                ++numHaveDataType;
            }
            if (param.getParamType() != RouteParameterType.UNKNOWN) {
                ++numHaveParamType;
            }
            if (param.getAcceptedValues() != null && param.getAcceptedValues().size() > 0) {
                ++numHaveAcceptedValues;
            }
        }

        int numParams = detectedParameters.size();
        System.out.println("- " + numOptional + "/" + numParams + " parameters are optional");
        System.out.println("- " + (numParams - numHaveDataType) + "/" + numParams + " are missing their data type");
        System.out.println("- " + (numParams - numHaveParamType) + "/" + numParams + " are missing their parameter type");
        System.out.println("- " + numHaveAcceptedValues + "/" + numParams + " have a list of accepted values");
    }

    private static Endpoint.Info[] getEndpointInfo(List<Endpoint> endpoints) {
        Endpoint.Info[] endpointsInfos = new Endpoint.Info[endpoints.size()];

        for (int i = 0; i < endpoints.size(); i++) {
            endpointsInfos[i] = Endpoint.Info.fromEndpoint(endpoints.get(i));
        }

        return endpointsInfos;
    }

    private static void resetLoggingConfiguration() {
        ConsoleAppender console = new ConsoleAppender(); //create appender
        String pattern = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(pattern));

        if (logging == Logging.ON) {
            console.setThreshold(Level.DEBUG);
        } else {
            console.setThreshold(Level.ERROR);
        }

        console.activateOptions();
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(console);
    }
}