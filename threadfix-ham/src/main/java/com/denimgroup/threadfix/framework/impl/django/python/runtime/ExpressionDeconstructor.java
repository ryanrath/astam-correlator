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
//              Secure Decisions, a division of Applied Visions, Inc
//
////////////////////////////////////////////////////////////////////////

package com.denimgroup.threadfix.framework.impl.django.python.runtime;

import com.denimgroup.threadfix.framework.impl.django.python.Language;
import com.denimgroup.threadfix.framework.util.ScopeTracker;

import java.util.List;

import static com.denimgroup.threadfix.CollectionUtils.list;

public class ExpressionDeconstructor {

    static final List<Character> SPECIAL_CHARS = list('%', '+', '-', '/', '*', '=', '.', '(', ')', '[', ']', '{', '}', ':', ',', '#');

    public List<String> deconstruct(String fullExpression) {
        return deconstruct(fullExpression, 10000);
    }

    public List<String> deconstruct(String fullExpression, int maxExpressions) {
        List<String> expressions = list();

        //   Takes an expression string ie 5 + 5 == 10 and splits it into sub-expression strings '5', '+', '5', '==', '10'

        boolean isSpecialExpression = false;
        StringBuilder workingSubExpression = new StringBuilder();

        // TODO - Could definitely be optimized

        boolean isMultilineString = false;
        int numConsecutiveQuotes = 0;
        int lastChar = -1;
        ScopeTracker scopeTracker = new ScopeTracker();
        for (int i = 0; i < fullExpression.length(); i++) {
            char c = fullExpression.charAt(i);

            boolean wasMultilineString = false;
            if (c == '"' && lastChar == '"' && scopeTracker.getStringStartToken() != '\'') {
                if (++numConsecutiveQuotes == 2) {
                    wasMultilineString = isMultilineString;
                    isMultilineString = !isMultilineString;
                }
            } else {
                numConsecutiveQuotes = 0;
            }

            if (!isMultilineString && !wasMultilineString) {
                scopeTracker.interpretToken(c);
            }

            if (expressions.size() >= maxExpressions) {
                workingSubExpression.append(c);
                continue;
            }

            if (!isMultilineString && !wasMultilineString && !scopeTracker.isInString() && !scopeTracker.isInScopeOrString() && (!scopeTracker.enteredGlobalScope() || isSpecialExpression)) {
                if (SPECIAL_CHARS.contains(c)) {
                    if (workingSubExpression.length() > 0 && !isSpecialExpression) {
                        expressions.add(workingSubExpression.toString().trim());
                        workingSubExpression = new StringBuilder();
                        workingSubExpression.append(c);
                    } else if (workingSubExpression.length() == 0 || isSpecialExpression) {
                        workingSubExpression.append(c);
                    }
                    isSpecialExpression = true;
                } else if (c == ' ') {
                    if (workingSubExpression.length() > 0) {
                        expressions.add(workingSubExpression.toString().trim());
                        workingSubExpression = new StringBuilder();
                        isSpecialExpression = false;
                    }
                } else {
                    if (isSpecialExpression) {
                        if (workingSubExpression.length() > 0) {
                            expressions.add(workingSubExpression.toString().trim());
                            workingSubExpression = new StringBuilder();
                        }
                        isSpecialExpression = false;
                    }

                    workingSubExpression.append(c);
                }
            } else {
                if (((scopeTracker.enteredString() && !scopeTracker.isInScope()) || scopeTracker.exitedGlobalScope()) && workingSubExpression.length() > 0) {
                    expressions.add(workingSubExpression.toString().trim());
                    workingSubExpression = new StringBuilder();
                    isSpecialExpression = false;
                }
                workingSubExpression.append(c);
            }

            lastChar = c;
        }

        if (workingSubExpression.length() > 0) {
            expressions.add(workingSubExpression.toString().trim());
        }

        //  Collapse decimal numbers (ie 12.34 would otherwise be '12', '.', '34' instead of '12.34')
        for (int i = 1; i < expressions.size() - 1; i++) {
            String last = expressions.get(i - 1);
            String current = expressions.get(i);
            String next = expressions.get(i + 1);

            if (Language.isNumber(last) && Language.isNumber(next) && current.equals(".")) {
                StringBuilder combined = new StringBuilder();
                combined.append(last);
                combined.append('.');
                combined.append(next);
                expressions.remove(i + 1);
                expressions.remove(i);
                expressions.remove(i - 1);
                expressions.add(i - 1, combined.toString());
                i -= 2;
            }
        }

        return expressions;
    }


}
