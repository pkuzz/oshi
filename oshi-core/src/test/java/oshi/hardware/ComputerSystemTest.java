/**
 * Oshi (https://github.com/dblock/oshi)
 *
 * Copyright (c) 2010 - 2017 The Oshi Project Team
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Maintainers:
 * dblock[at]dblock[dot]org
 * widdis[at]gmail[dot]com
 * enrico.bianchi[at]gmail[dot]com
 *
 * Contributors:
 * https://github.com/dblock/oshi/graphs/contributors
 */
package oshi.hardware;

import org.junit.Test;
import org.threeten.bp.LocalDate;
import oshi.SystemInfo;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests Computer System
 */
public class ComputerSystemTest {

    /**
     * Test Computer System
     */
    @Test
    public void testComputerSystem() {
        SystemInfo si = new SystemInfo();
        ComputerSystem cs = si.getHardware().getComputerSystem();
        assertNotNull(cs.getManufacturer());
        assertNotNull(cs.getModel());
        assertNotNull(cs.getSerialNumber());

        Firmware fw = cs.getFirmware();
        assertNotNull(fw);
        assertNotNull(fw.getManufacturer());
        assertNotNull(fw.getName());
        assertNotNull(fw.getDescription());
        assertNotNull(fw.getVersion());
        assertTrue(fw.getReleaseDate() == null || !fw.getReleaseDate().isAfter(LocalDate.now()));

        Baseboard bb = cs.getBaseboard();
        assertNotNull(bb);
        assertNotNull(bb.getManufacturer());
        assertNotNull(bb.getModel());
        assertNotNull(bb.getVersion());
        assertNotNull(bb.getSerialNumber());
    }
}
