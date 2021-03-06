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

import com.denimgroup.threadfix.framework.impl.django.python.schema.AbstractPythonStatement;
import com.denimgroup.threadfix.framework.util.CodeParseUtil;

import java.util.List;

public class PythonStringPrimitive implements PythonValue {

    String value;
    AbstractPythonStatement sourceLocation;

    public PythonStringPrimitive() {

    }

    public PythonStringPrimitive(String value) {
        setValue(value);
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = CodeParseUtil.trim(value, new String[] { "\"", "'", "r'", "r\"" });
    }

    @Override
    public void resolveSubValue(PythonValue previousValue, PythonValue newValue) {

    }

    @Override
    public void resolveSourceLocation(AbstractPythonStatement source) {
        sourceLocation = source;
    }

    @Override
    public AbstractPythonStatement getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public PythonValue clone() {
        PythonStringPrimitive clone = new PythonStringPrimitive(value);
        clone.sourceLocation = this.sourceLocation;
        return clone;
    }

    @Override
    public List<PythonValue> getSubValues() {
        return null;
    }

    @Override
    public String toString() {
        if (this.value != null) {
            return '"' + this.value + '"';
        } else {
            return "<Unassigned String>";
        }
    }
}
