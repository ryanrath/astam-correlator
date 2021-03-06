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
//     Contributor(s):
//              Denim Group, Ltd.
//              Secure Decisions, a division of Applied Visions, Inc
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.framework.impl.rails;

import com.denimgroup.threadfix.data.enums.ParameterDataType;
import com.denimgroup.threadfix.framework.impl.rails.model.RailsController;
import com.denimgroup.threadfix.framework.impl.rails.model.RailsControllerMethod;
import com.denimgroup.threadfix.framework.util.EventBasedTokenizer;
import com.denimgroup.threadfix.framework.util.EventBasedTokenizerRunner;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.StreamTokenizer;
import java.util.*;

import static com.denimgroup.threadfix.CollectionUtils.list;

/**
 * Created by sgerick on 4/23/2015.
 */
public class RailsControllerParser implements EventBasedTokenizer {

    private static final SanitizedLogger LOG = new SanitizedLogger("RailsParser");

    private enum ControllerState {
        INIT, MODULE, CLASS, METHOD, PARAMS
    }


    private Deque<String> tokenQueue;
    private boolean _continue;
    private Map<String, Map<String, ParameterDataType>> modelMap;
    private List<RailsController> railsControllers;
    private Stack<String> moduleNameStack = new Stack<String>(); // NOTE - This only ever adds to the stack, there
                                                                 // are too many scope-block declaration types
                                                                 // and permutations to properly detect scope open/close
                                                                 // for detection of module begin/eng

    private RailsController currentRailsController;
    private RailsControllerMethod currentCtrlMethod;
    private String currentParamName;

    private ControllerState currentCtrlState = ControllerState.INIT;

    public static Collection<RailsController> parse(@Nonnull File rootFile) {
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            LOG.error("Root file not found or is not directory. Exiting.");
            return null;
        }
        File ctrlDir = new File(rootFile,"app/controllers");
        if (!ctrlDir.exists() || !ctrlDir.isDirectory()) {
            LOG.error("{rootFile}/app/controllers/ not found or is not directory. Exiting.");
            return null;
        }

        Collection<File> rubyFiles = (Collection<File>) FileUtils.listFiles(ctrlDir,
                new WildcardFileFilter("*_controller.rb"), TrueFileFilter.INSTANCE);

        RailsControllerParser parser = new RailsControllerParser();
        parser.modelMap = RailsModelParser.parse(rootFile);
        parser.railsControllers = list();

        for (File rubyFile : rubyFiles) {
            parser._continue = true;
            parser.tokenQueue = new ArrayDeque<String>();
            parser.currentRailsController = null;
            parser.currentCtrlMethod = null;
            parser.currentParamName = null;

            EventBasedTokenizerRunner.runRails(rubyFile, false, false, parser);

            if (parser.currentRailsController != null
                    && parser.currentCtrlMethod != null
                    && parser.currentCtrlMethod.getMethodName() != null) {
                parser.currentRailsController.addControllerMethod(parser.currentCtrlMethod);
            }
            if (parser.currentRailsController != null
                    && parser.currentRailsController.getControllerMethods() != null
                    && parser.currentRailsController.getControllerMethods().size() > 0) {
                parser.currentRailsController.setControllerFile(rubyFile);
                parser.railsControllers.add(parser.currentRailsController);
            }
        }

        return parser.railsControllers;
    }

    private String buildCurrentModuleName() {
        if (moduleNameStack.empty()) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String moduleName : moduleNameStack) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(moduleName);
            }
            return sb.toString().replaceAll("::", "\\/");
        }
    }

    @Override
    public boolean shouldContinue() {
        return _continue;
    }

    @Override
    public void processToken(int type, int lineNumber, String stringValue) {
        String charValue = null;
        if (type > 0)
            charValue = String.valueOf(Character.toChars(type));

        if (stringValue != null) {
            tokenQueue.add(stringValue);
        } else if (charValue != null) {
            tokenQueue.add(charValue);
        }
        if (tokenQueue.size() > 10)
            tokenQueue.remove();

        switch (currentCtrlState) {
            case CLASS:
                processClass(type, stringValue, charValue);
                break;
            case METHOD:
                processMethod(type, stringValue, charValue);
                break;
            case PARAMS:
                processParams(type, stringValue, charValue);
                break;
            case MODULE:
                processModule(type, stringValue, charValue);
                break;
        }




        if (stringValue != null) {
            String s = stringValue.toLowerCase();
            if (s.equals("private")) {
                _continue = false;
            } else if (s.equals("module")) {
                currentCtrlState = ControllerState.MODULE;
            } else if (s.equals("class")) {
                currentCtrlState = ControllerState.CLASS;
                if (currentRailsController == null) {
                    currentRailsController = new RailsController();
                    currentRailsController.setModuleName(buildCurrentModuleName());
                    moduleNameStack.clear();
                }
            } else if (s.equals("def")) {
                currentCtrlState = ControllerState.METHOD;
                if (currentCtrlMethod == null)
                    currentCtrlMethod = new RailsControllerMethod();
                else {
                    currentRailsController.addControllerMethod(currentCtrlMethod);
                    currentCtrlMethod = new RailsControllerMethod();
                }

            } else if (s.equals("params")) {
                currentCtrlState = ControllerState.PARAMS;

            }
        }
    }

    private void processModule(int type, String stringValue, String charValue) {
        if (type == StreamTokenizer.TT_WORD && stringValue != null) {
            moduleNameStack.push(stringValue);
            currentCtrlState = ControllerState.INIT;
        }
    }

    private void processClass(int type, String stringValue, String charValue) {
        if (type == StreamTokenizer.TT_WORD && stringValue != null) {
            String ctrlName = stringValue;
            if (ctrlName.endsWith("Controller")) {
                int i = ctrlName.lastIndexOf("Controller");
                ctrlName = ctrlName.substring(0, i);
            }
            currentRailsController.setControllerName(ctrlName);
            currentCtrlState = ControllerState.INIT;
        }
    }

    private void processMethod(int type, String stringValue, String charValue) {
        if (type == StreamTokenizer.TT_WORD && stringValue != null) {
            currentCtrlMethod.setMethodName(stringValue);
            currentCtrlState = ControllerState.INIT;
        }
    }

    private void processParams(int type, String stringValue, String charValue) {
        if (type == StreamTokenizer.TT_WORD && stringValue.startsWith(":")
                && stringValue.length() > 1) {
            stringValue = stringValue.substring(1);
            // addMethodParam(stringValue);
            if (currentParamName == null)
                currentParamName = stringValue;
            else
                currentParamName = currentParamName.concat(".").concat(stringValue);
            return;
        } else if ("[".equals(charValue) || "]".equals(charValue)) {
            return;
        } else {
            addMethodParam(currentParamName);
            currentParamName = null;
            currentCtrlState = ControllerState.INIT;
            return;
        }

    }

    private void addMethodParam(String stringValue) {
        for (String s : tokenQueue) {   //  .new .create, Model.attr1, Model.attr2
            if (s != null && stringValue != null && (s.endsWith(".new") || s.endsWith(".create"))
                    && s.toLowerCase().startsWith(stringValue)) {
                Map<String, ParameterDataType> modelParams = modelMap.get(stringValue);
                if(modelParams == null ) return;
                for (Map.Entry<String, ParameterDataType>  p : modelParams.entrySet()) {
                    String param = stringValue.concat(".").concat(p.getKey());
                    if (currentCtrlMethod.getMethodParams() == null
                            || !currentCtrlMethod.getMethodParams().keySet().contains(param)) {
                        currentCtrlMethod.addMethodParam(param, findTypeFromMatch(param));
                    }
                }
                return;
            }
        }
        if (currentCtrlMethod != null && (currentCtrlMethod.getMethodParams() == null
                || !currentCtrlMethod.getMethodParams().keySet().contains(stringValue))) {
            currentCtrlMethod.addMethodParam(stringValue, findTypeFromMatch(stringValue));
        }
    }

    private ParameterDataType findTypeFromMatch(String parameterName){
        ParameterDataType paramType = ParameterDataType.STRING;
        String controllerName = currentRailsController.getControllerName().toLowerCase();
        String modelName = null;

        if (controllerName.endsWith("s")){
            modelName = controllerName.substring(0, controllerName.length() - 1 );
        }

        if(modelName != null && modelMap.containsKey(modelName)){
            Map<String, ParameterDataType> modelParamMap = modelMap.get(modelName);

            if(modelParamMap.containsKey(parameterName)){
                return modelParamMap.get(parameterName);
            }

           if(StringUtils.contains(parameterName, ".")){
                parameterName = parameterName.substring(parameterName.indexOf(".") + 1, parameterName.length());
                if(modelParamMap.containsKey(parameterName)){
                    return modelParamMap.get(parameterName);
                }
            }
        }

        //check if the param is an attribute of another model
        if(StringUtils.contains(parameterName, ".")){
            modelName = parameterName.substring(0, parameterName.indexOf("."));
            if(modelMap.containsKey(modelName)){
                Map<String, ParameterDataType> modelParamMap = modelMap.get(modelName);
                parameterName = parameterName.substring(parameterName.indexOf(".") + 1, parameterName.length());
                if(modelParamMap.containsKey(parameterName)){
                    return modelParamMap.get(parameterName);
                }
            }
        }

        return paramType;
    }

}
