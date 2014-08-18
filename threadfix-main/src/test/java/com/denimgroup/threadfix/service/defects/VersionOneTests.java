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
package com.denimgroup.threadfix.service.defects;

import com.denimgroup.threadfix.data.entities.DefectTrackerType;
import com.denimgroup.threadfix.service.defects.mock.VersionOneRestUtilsMock;
import org.junit.Test;

import java.util.List;

import static com.denimgroup.threadfix.service.defects.util.TestConstants.*;
import static org.junit.Assert.assertTrue;

/**
 * Created by mac on 4/7/14.
 */
public class VersionOneTests {

    public AbstractDefectTracker getTracker() {
        DefectTrackerType type = new DefectTrackerType();

        type.setName(DefectTrackerType.VERSION_ONE);

        AbstractDefectTracker tracker = DefectTrackerFactory.getTracker(type);

        tracker.setUsername(VERSION_ONE_USERNAME);
        tracker.setPassword(VERSION_ONE_PASSWORD);
        tracker.setUrl(VERSION_ONE_URL);

        // TODO mock the appropriate class
        ((VersionOneDefectTracker) tracker).restUtils = new VersionOneRestUtilsMock();

        return tracker;
    }

    @Test
    public void testFactory() {
        AbstractDefectTracker tracker = getTracker();

        assertTrue("Tracker should have been HPQC but wasn't.", tracker instanceof VersionOneDefectTracker);
    }

    @Test
    public void testResponseWithSpecialCharacters() {
        AbstractDefectTracker tracker = getTracker();
        List<String> productNames = tracker.getProductNames();

        System.out.println(productNames);

    }

}
