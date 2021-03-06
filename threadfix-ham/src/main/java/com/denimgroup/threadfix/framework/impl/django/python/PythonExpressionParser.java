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

package com.denimgroup.threadfix.framework.impl.django.python;

import com.denimgroup.threadfix.framework.impl.django.python.runtime.*;
import com.denimgroup.threadfix.framework.impl.django.python.runtime.expressions.*;
import com.denimgroup.threadfix.framework.impl.django.python.schema.AbstractPythonStatement;
import com.denimgroup.threadfix.framework.util.CodeParseUtil;
import com.denimgroup.threadfix.framework.util.ScopeTracker;
import com.denimgroup.threadfix.logging.SanitizedLogger;

import java.util.ArrayList;
import java.util.List;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.framework.impl.django.python.runtime.InterpreterUtil.*;

/**
 * Parses individual python code strings to generate a binary expression tree.
 */
public class PythonExpressionParser {

    private PythonValueBuilder valueBuilder = new PythonValueBuilder();
    private ExpressionDeconstructor expressionDeconstructor = new ExpressionDeconstructor();
    private PythonCodeCollection codebase;

    static final SanitizedLogger LOG = new SanitizedLogger(PythonExpressionParser.class);

    enum OperationType {
        UNKNOWN, INVALID,
        PRIMITIVE_OPERATION,
        MEMBER_ACCESS, TUPLE_REFERENCE, INDEXER, RETURN_STATEMENT,
        PARAMETER_ENTRY, // For tuples (a, b, c) and multi-assignment 'a, b = 1, 2'
        FUNCTION_CALL
    }

    public PythonExpressionParser() {

    }

    public PythonExpressionParser(PythonCodeCollection codebase) {
        this.codebase = codebase;
    }

    public PythonExpression processString(String stringValue, List<PythonValue> subjects, AbstractPythonStatement scope) {

        try {

            stringValue = Language.stripComments(stringValue);

            PythonExpression result = null;

            List<String> expressions = expressionDeconstructor.deconstruct(stringValue);

            //  Ensure this is a supported expression
            for (String subexpr : expressions) {
                if (Language.PYTHON_KEYWORDS.contains(subexpr)) {
                    return new IndeterminateExpression();
                }
            }

            OperationType operationType = OperationType.UNKNOWN;
            List<OperationType> expressionOperations = new ArrayList<OperationType>(expressions.size());
            String operationTypeIndicator = null;

            ScopeTracker scopeTracker = new ScopeTracker();

            int primaryEndIndex = 0;
            for (int i = 0; i < expressions.size(); i++) {
                String subexpr = expressions.get(i);
                for (int m = 0; m < subexpr.length(); m++) {
                    scopeTracker.interpretToken(subexpr.charAt(m));
                }
                OperationType subexprOperation = detectOperationType(subexpr);

                if (subexprOperation != OperationType.INVALID && subexprOperation != OperationType.UNKNOWN &&
                        !scopeTracker.isInString() && !scopeTracker.isInScopeOrString()) {

                    if (i > 0) {
                        OperationType lastType = expressionOperations.get(i - 1);
                        if (subexprOperation == OperationType.TUPLE_REFERENCE && lastType == OperationType.UNKNOWN || lastType == OperationType.MEMBER_ACCESS) {
                            // Any '(..)'-format expression is treated as a TUPLE_REFERENCE. A function call would
                            // have a symbol name detected as an UNKNOWN or MEMBER_ACCESS operation followed by a TUPLE_REFERENCE.
                            // ie 'someFunc(..)' -> 'someFunc', '(..)' -> 'UNKNOWN', 'TUPLE_REFERENCE'
                            subexprOperation = OperationType.FUNCTION_CALL;
                        }

                        //  An indexer expression 'arr[0]' will have an UNKNOWN token 'arr' followed by the INDEXER token '[0]'
                        if (subexprOperation == OperationType.INDEXER && lastType != OperationType.UNKNOWN) {
                            subexprOperation = OperationType.UNKNOWN;
                        }
                    }

                    if (operationType == OperationType.UNKNOWN && subexprOperation != OperationType.PARAMETER_ENTRY &&
                            (subexprOperation != OperationType.INDEXER) &&
                            (subexprOperation != OperationType.TUPLE_REFERENCE || i == 0)) {
                        operationType = subexprOperation;
                        operationTypeIndicator = subexpr;
                        primaryEndIndex = i;
                    }

                    expressionOperations.add(subexprOperation);

                } else {
                    expressionOperations.add(subexprOperation);
                }
            }

            //  If no high-level expressions have been detected it may be a partial expression, attempt to use that
            //  as the primary operation.

            if (operationType == OperationType.UNKNOWN) {
                for (int i = 0; i < expressionOperations.size(); i++) {
                    OperationType type = expressionOperations.get(i);
                    if (type != OperationType.UNKNOWN && type != OperationType.INVALID) {
                        operationType = type;
                        primaryEndIndex = i;
                        operationTypeIndicator = expressions.get(i);
                        break;
                    }
                }
            }

            //  Function parameters parsed as a partial expression will have a subject defined, and
            //  have the parameters interpreted as a TUPLE reference. Correct this case to be
            //  a function call.
            if (operationType == OperationType.TUPLE_REFERENCE && subjects != null && subjects.size() > 0) {
                operationType = OperationType.FUNCTION_CALL;
            }

            //  An indexer expression MUST be parsed along with a set of subjects that it is indexing.
            if (operationType == OperationType.INDEXER && (subjects == null || subjects.size() == 0)) {
                operationType = OperationType.UNKNOWN;
            }

            switch (operationType) {
                case PRIMITIVE_OPERATION:
                    result = parsePrimitiveOperation(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex,
                            operationType,
                            operationTypeIndicator
                    );
                    break;
                case FUNCTION_CALL:
                    result = parseFunctionCall(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex
                    );
                    break;
                case MEMBER_ACCESS:
                    result = parseMemberAccess(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex
                    );
                    break;
                case INDEXER:
                    result = parseIndexer(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex
                    );
                    break;
                case RETURN_STATEMENT:
                    result = parseReturnStatement(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex
                    );
                    break;
                case TUPLE_REFERENCE:
                    result = parseTupleReference(
                            expressions,
                            subjects,
                            expressionOperations,
                            primaryEndIndex
                    );
                    break;
                default:
                    result = null;
            }

            int scopingIndentation = InterpreterUtil.countSpacingLevel(stringValue);

            if (result == null) {
                LOG.debug("Parsing " + stringValue + " resulted in an IndeterminateExpression");
                result = new IndeterminateExpression();
                result.setScopingIndentation(scopingIndentation);
                return result;
            } else {
                resolveSubValues(result);
                if (this.codebase != null && scope != null) {
                    resolveSourceLocations(result, scope, codebase);
                }
                LOG.debug("Finished parsing " + stringValue + " into its expression chain: " + result.toString());
                result.setScopingIndentation(scopingIndentation);
                return result;
            }
        } catch (StackOverflowError soe) {
            LOG.warn("Stack overflow occurred while executing expression parser");
            return new IndeterminateExpression();
        }
    }

    OperationType detectOperationType(String expression) {
        if (expression.equals("return")) {
            return OperationType.RETURN_STATEMENT;
        } else if (expression.equals(".")) {
            return OperationType.MEMBER_ACCESS;
        } else if (PrimitiveOperationExpression.interpretOperator(expression) != PrimitiveOperationType.UNKNOWN) {
            return OperationType.PRIMITIVE_OPERATION;
        } else if (expression.startsWith("(")) {
            return OperationType.TUPLE_REFERENCE;
        } else if (expression.equals(",")) {
            return OperationType.PARAMETER_ENTRY;
        } else if (expression.startsWith("[")) {
            return OperationType.INDEXER;
        } else if (Language.PYTHON_KEYWORDS.contains(expression)) {
            return OperationType.INVALID;
        } else {
            return OperationType.UNKNOWN;
        }
    }

    PythonExpression parsePrimitiveOperation(List<String> expressions,
                                                   List<PythonValue> subjects,
                                                   List<OperationType> expressionTypes,
                                                   int primaryEndIndex,
                                                   OperationType type,
                                                   String operationIndicator) {

        //  NOTE - Does not conform to PEMDAS order of operations! Operations are parsed left-to-right!

        PrimitiveOperationType primitiveType = PrimitiveOperationExpression.interpretOperator(operationIndicator);

        List<PythonValue> operands = null;
        PrimitiveOperationExpression result = new PrimitiveOperationExpression(primitiveType);

        /* Gather Operands */
        int nextOperationIdx = findNextOperation(expressionTypes, primaryEndIndex + 1);
        if (nextOperationIdx < 0) {
            nextOperationIdx = expressions.size() - 1;
        }
        OperationType nextOperation = expressionTypes.get(nextOperationIdx);
        //  If the next expression is a tuple and is also the last expression, parse that
        //  tuple directly and use it as our subject
        if (nextOperationIdx == expressions.size() - 1) {
            if (nextOperation == OperationType.TUPLE_REFERENCE) {
                String group = expressions.get(nextOperationIdx);
                PythonValue pyGroup = valueBuilder.buildFromSymbol(group);
                if (pyGroup != null) {
                    if (pyGroup instanceof PythonTuple) {
                        operands = new ArrayList<PythonValue>(((PythonTuple) pyGroup).getEntries());
                    } else if (pyGroup instanceof PythonStringPrimitive) {
                        // Multiple string can be concatenated via ("a" "b" "c")
                        operands = list(pyGroup);
                    }
                }
            } else {
                PythonValue operand;
                if (nextOperation == OperationType.FUNCTION_CALL) {
                    String functionCall = reconstructExpression(expressions, nextOperationIdx - 1, nextOperationIdx + 1);
                    operand = processString(functionCall, null, null);
                } else if (nextOperationIdx != primaryEndIndex) {
                    operand = tryMakeValue(expressions.get(nextOperationIdx), null);
                } else {
                    operand = new PythonIndeterminateValue();
                }
                operands = list((PythonValue)operand);
            }
        } else {
            String remainingExpression = reconstructExpression(expressions, primaryEndIndex + 1);

            if (nextOperation == OperationType.PARAMETER_ENTRY) {
                //  It's a tuple
                String[] parts = CodeParseUtil.splitByComma(remainingExpression, false);
                operands = new ArrayList<PythonValue>(parts.length);
                for (String part : parts) {
                    PythonValue parsed = tryMakeValue(part, null);
                    operands.add(parsed);
                }
            } else {
                PythonValue operand = tryMakeValue(remainingExpression, null);
                operands = list((PythonValue) operand);
            }
        }

        if (subjects == null) {
            subjects = tryMakeSubjectValues(expressions, expressionTypes, primaryEndIndex - 1, null);
        }

        if (operands != null && subjects != null) {
            result.setOperands(operands);
            result.setSubjects(subjects);
            result = PrimitiveOperationExpression.rectifyOrderOfOperations(result);
            return result;
        } else {
            return new IndeterminateExpression();
        }
    }

    PythonExpression parseFunctionCall(List<String> expressions,
                                       List<PythonValue> subjects,
                                       List<OperationType> expressionTypes,
                                       int primaryEndIndex) {

        FunctionCallExpression callExpression = new FunctionCallExpression();

        /* Collect operands */
        List<PythonValue> operands = list();
        String primaryOperand = expressions.get(primaryEndIndex);
        String[] parameterEntries = gatherGroupEntries(primaryOperand);
        for (String entry : parameterEntries) {
            operands.add(tryMakeValue(entry, null));
        }

        /* Collect subjects */
        if (subjects == null) {
            subjects = tryMakeSubjectValues(expressions, expressionTypes, primaryEndIndex - 1, list((PythonValue)callExpression));
        }

        //  The function call may have trailing expressions, if so, then this function call is a subject of
        //  the following expressions
        if (subjects != null) {
            callExpression.setSubjects(subjects);
            callExpression.setParameters(operands);

            if (primaryEndIndex != expressions.size() - 1) {
                //  There are trailing expressions, generate it with this as the subject
                String remainingExpressions = reconstructExpression(expressions, primaryEndIndex + 1);
                PythonExpression trailingExpression = processString(remainingExpressions, list((PythonValue)callExpression), null);
                return trailingExpression;
            }

            return callExpression;
        } else {
            return new IndeterminateExpression();
        }
    }

    PythonExpression parseMemberAccess(List<String> expressions,
                                       List<PythonValue> subjects,
                                       List<OperationType> expressionTypes,
                                       int primaryEndIndex) {

        MemberExpression memberExpression = new MemberExpression();

        int lastMemberAccessExpression = primaryEndIndex;
        OperationType currentExpressionType = OperationType.MEMBER_ACCESS;
        while (lastMemberAccessExpression + 1 < expressions.size() && (currentExpressionType == OperationType.MEMBER_ACCESS || currentExpressionType == OperationType.UNKNOWN)) {
            currentExpressionType = expressionTypes.get(++lastMemberAccessExpression);
        }

        if (currentExpressionType != OperationType.MEMBER_ACCESS && currentExpressionType != OperationType.UNKNOWN) {
            --lastMemberAccessExpression;
        }

        for (int i = 0; i <= lastMemberAccessExpression; i++) {
            if (expressionTypes.get(i) != OperationType.MEMBER_ACCESS) {
                memberExpression.appendPath(expressions.get(i));
            }
        }

        if (subjects == null) {
            String primaryMember = expressions.get(primaryEndIndex - 1);
            PythonVariable variable = new PythonVariable(primaryMember);
            memberExpression.setSubjects(list((PythonValue)variable));
            memberExpression.removePath(0);
        } else {
            memberExpression.setSubjects(subjects);
        }

        if (lastMemberAccessExpression != expressionTypes.size() - 1) {
            String remainingExpression = reconstructExpression(expressions, lastMemberAccessExpression + 1);
            PythonExpression trailingExpression = processString(remainingExpression, list((PythonValue)memberExpression), null);
            return trailingExpression;
        }

        return memberExpression;
    }

    PythonExpression parseIndexer(List<String> expressions,
                                       List<PythonValue> subjects,
                                       List<OperationType> expressionTypes,
                                       int primaryEndIndex) {

        IndexerExpression indexerExpression = new IndexerExpression();

        String indexerText = expressions.get(primaryEndIndex);
        indexerText = CodeParseUtil.trim(indexerText, new String[] { "[", "]" }, 1);
        List<String> indexerSubExpressions = expressionDeconstructor.deconstruct(indexerText);

        if (indexerSubExpressions.size() <= 1) {
            PythonValue indexerValue = tryMakeValue(indexerText, null);
            indexerExpression.setIndexerValue(indexerValue);
        } else {
            PythonValue indexerValue = processString(indexerText, null, null);
            indexerExpression.setIndexerValue(indexerValue);
        }

        if (subjects == null) {
            int endIndex = primaryEndIndex;
            if (endIndex > 0) {
                endIndex -= 1;
            } else {
                endIndex = 0;
            }
            subjects = tryMakeSubjectValues(expressions, expressionTypes, endIndex, null);
            if (subjects != null) {
                indexerExpression.setSubjects(subjects);
            }
        }

        if (primaryEndIndex != expressions.size() - 1) {
            String remainingExpressionText = reconstructExpression(expressions, primaryEndIndex + 1);
            PythonExpression remainingExpression = processString(remainingExpressionText, list((PythonValue)indexerExpression), null);
            return remainingExpression;
        }

        return indexerExpression;
    }

    PythonExpression parseReturnStatement(List<String> expressions,
                                  List<PythonValue> subjects,
                                  List<OperationType> expressionTypes,
                                  int primaryEndIndex) {

        ReturnExpression returnExpression = new ReturnExpression();

        if (primaryEndIndex != expressions.size() - 1) {
            String subjectExpression = reconstructExpression(expressions, primaryEndIndex + 1);
            PythonValue value = tryMakeValue(subjectExpression, null);
            returnExpression.addSubject(value);
        }

        return returnExpression;
    }

    PythonExpression parseTupleReference(List<String> expressions,
                                          List<PythonValue> subjects,
                                          List<OperationType> expressionTypes,
                                          int primaryEndIndex) {

        //  A direct tuple reference will occur if parentheses are used to order operations. All
        //  other cases are implemented as subsets of the other expression types.

        //  The tuple reference needs to be the first entry in the expressions. Any preceding
        //  expressions must be parsed before the tuple reference is parsed.
        if (primaryEndIndex != 0) {
            return null;
        }

        ScopingExpression result = new ScopingExpression();

        String tupleString = expressions.get(primaryEndIndex);
        PythonValue tupleExpression = tryMakeValue(tupleString, null);
        result.addSubject(tupleExpression);

        if (expressions.size() > 1) {
            String remainingString = reconstructExpression(expressions, primaryEndIndex + 1);
            PythonValue remainingExpression = tryMakeValue(remainingString, list((PythonValue)result));
            if (remainingExpression instanceof PythonExpression) {
                return (PythonExpression)remainingExpression;
            } else {
                //  A trailing expression was added that could not be parsed as an expression - this makes
                //  no sense as a value would directly follow another value, which is invalid syntax.
                return null;
            }
        } else {
            return result;
        }
    }

    String[] gatherGroupEntries(String groupExpression) {
        groupExpression = CodeParseUtil.trim(groupExpression, new String[] { "[", "]", "{", "}", "(", ")" }, 1);
        return CodeParseUtil.splitByComma(groupExpression);
    }

    int findNextOperation(List<OperationType> operations, int startIndex) {
        for (int i = startIndex; i < operations.size(); i++) {
            OperationType current = operations.get(i);
            if (current != OperationType.UNKNOWN) {
                return i;
            }
        }
        return -1;
    }

    String reconstructExpression(List<String> expressionParts, int startIndex, int endIndex) {
        StringBuilder sb = new StringBuilder();

        String lastPart = null;
        for (int i = startIndex; i < expressionParts.size() && i < endIndex; i++) {
            String part = expressionParts.get(i);
            if (i > startIndex && lastPart.equals("return")) {
                sb.append(' ');
            }
            sb.append(part);
            lastPart = part;
        }

        return sb.toString();
    }

    String reconstructExpression(List<String> expressionParts, int startIndex) {
        return reconstructExpression(expressionParts, startIndex, Integer.MAX_VALUE);
    }

    List<PythonValue> tryMakeSubjectValues(List<String> expressions,
                                           List<OperationType> expressionTypes,
                                           int endIndex,
                                           List<PythonValue> expressionSubject) {

        OperationType subjectType = OperationType.UNKNOWN;
        for (int i = 0; i < endIndex; i++) {
            OperationType exprType = expressionTypes.get(i);
            if (exprType == OperationType.PARAMETER_ENTRY ||
                    exprType == OperationType.TUPLE_REFERENCE ||
                    exprType == OperationType.MEMBER_ACCESS) {
                subjectType = exprType;
                break;
            }
        }

        List<PythonValue> subjects = null;

        switch (subjectType) {
            case PARAMETER_ENTRY:
            case TUPLE_REFERENCE:
                String[] entries = gatherGroupEntries(reconstructExpression(expressions, 0, endIndex + 1));
                if (entries != null) {
                    subjects = list();
                    for (String entry : entries) {
                        subjects.add(tryMakeValue(entry, expressionSubject));
                    }
                }
                break;

            case MEMBER_ACCESS:
                String memberPath = reconstructExpression(expressions, 0, endIndex + 1);
                PythonValue value = new PythonVariable(memberPath);
                subjects = list(value);
                break;

            default:
                String subjectExpression = reconstructExpression(expressions, 0, endIndex + 1);
                PythonValue asValue = valueBuilder.buildFromSymbol(subjectExpression);
                if (isValidValue(asValue)) {
                    subjects = list(asValue);
                } else {
                    PythonExpression asExpression = processString(subjectExpression, expressionSubject, null);
                    subjects = list((PythonValue)asExpression);
                }
                break;
        }

        return subjects;
    }
}
