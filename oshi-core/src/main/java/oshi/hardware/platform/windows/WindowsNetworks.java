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
package oshi.hardware.platform.windows;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.WinDef.ULONG;
import com.sun.jna.platform.win32.WinNT.OSVERSIONINFO;

import oshi.hardware.NetworkIF;
import oshi.hardware.common.AbstractNetworks;
import oshi.jna.platform.windows.IPHlpAPI;
import oshi.jna.platform.windows.IPHlpAPI.MIB_IFROW;
import oshi.jna.platform.windows.IPHlpAPI.MIB_IFROW2;
import oshi.jna.platform.windows.Kernel32;

/**
 * @author widdis[at]gmail[dot]com
 */
public class WindowsNetworks extends AbstractNetworks {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(WindowsNetworks.class);

    // Save Windows version info for 32 bit/64 bit branch later
    private static final byte majorVersion;
    static {
        OSVERSIONINFO lpVersionInfo = new OSVERSIONINFO();
        // GetVersionEx() isn't accurate for Win8+ but is sufficient for
        // detecting versions 5.x and earlier
        if (!Kernel32.INSTANCE.GetVersionEx(lpVersionInfo)) {
            majorVersion = lpVersionInfo.dwMajorVersion.byteValue();
        } else {
            majorVersion = 0;
        }
    }

    /**
     * Updates interface network statistics on the given interface. Statistics
     * include packets and bytes sent and received, and interface speed.
     *
     * @param netIF
     *            The interface on which to update statistics
     */
    public static void updateNetworkStats(NetworkIF netIF) {
        // MIB_IFROW2 requires Vista (6.0) or later.
        if (majorVersion >= 6) {
            // Create new MIB_IFROW2 and set index to this interface index
            MIB_IFROW2 ifRow = new MIB_IFROW2();
            ifRow.InterfaceIndex = new ULONG(netIF.getNetworkInterface().getIndex());
            if (0 != IPHlpAPI.INSTANCE.GetIfEntry2(ifRow)) {
                // Error, abort
                LOG.error("Failed to retrieve data for interface {}, {}", netIF.getNetworkInterface().getIndex(),
                        netIF.getName());
                return;
            }
            netIF.setBytesSent(ifRow.OutOctets);
            netIF.setBytesRecv(ifRow.InOctets);
            netIF.setPacketsSent(ifRow.OutUcastPkts);
            netIF.setPacketsRecv(ifRow.InUcastPkts);
            netIF.setOutErrors(ifRow.OutErrors);
            netIF.setInErrors(ifRow.InErrors);
            netIF.setSpeed(ifRow.ReceiveLinkSpeed);
        } else {
            // Create new MIB_IFROW and set index to this interface index
            MIB_IFROW ifRow = new MIB_IFROW();
            ifRow.dwIndex = netIF.getNetworkInterface().getIndex();
            if (0 != IPHlpAPI.INSTANCE.GetIfEntry(ifRow)) {
                // Error, abort
                LOG.error("Failed to retrieve data for interface {}, {}", netIF.getNetworkInterface().getIndex(),
                        netIF.getName());
                return;
            }
            netIF.setBytesSent(ifRow.dwOutOctets);
            netIF.setBytesRecv(ifRow.dwInOctets);
            netIF.setPacketsSent(ifRow.dwOutUcastPkts);
            netIF.setPacketsRecv(ifRow.dwInUcastPkts);
            netIF.setOutErrors(ifRow.dwOutErrors);
            netIF.setInErrors(ifRow.dwInErrors);
            netIF.setSpeed(ifRow.dwSpeed);
        }
        netIF.setTimeStamp(System.currentTimeMillis());
    }
}
