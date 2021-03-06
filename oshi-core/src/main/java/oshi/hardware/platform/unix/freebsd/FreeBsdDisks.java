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
package oshi.hardware.platform.unix.freebsd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oshi.hardware.Disks;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.util.ExecutingCommand;
import oshi.util.ParseUtil;
import oshi.util.platform.unix.freebsd.BsdSysctlUtil;

import static oshi.util.Util.getOrDefault;

/**
 * FreeBSD hard disk implementation.
 *
 * @author widdis[at]gmail[dot]com
 */
public class FreeBsdDisks implements Disks {

    private static final long serialVersionUID = 1L;

    private static final Pattern MOUNT_PATTERN = Pattern.compile("/dev/(\\S+p\\d+) on (\\S+) .*");

    // Create map indexed by device name to populate data from multiple commands
    private static final Map<String, HWDiskStore> diskMap = new HashMap<>();
    // Map of partitions to mount points
    private static final Map<String, String> mountMap = new HashMap<>();

    @Override
    public HWDiskStore[] getDisks() {
        // Parse 'mount' to map partitions to mount point
        mountMap.clear();
        for (String mnt : ExecutingCommand.runNative("mount")) {
            Matcher m = MOUNT_PATTERN.matcher(mnt);
            if (m.matches()) {
                mountMap.put(m.group(1), m.group(2));
            }
        }

        // Get list of valid disks
        diskMap.clear();
        List<String> devices = Arrays.asList(BsdSysctlUtil.sysctl("kern.disks", "").split("\\s+"));

        // Temporary list to hold partitions
        List<HWPartition> partList = new ArrayList<>();

        // Run iostat -Ix to enumerate disks by name and get kb r/w
        List<String> disks = ExecutingCommand.runNative("iostat -Ix");
        long timeStamp = System.currentTimeMillis();
        for (String line : disks) {
            String[] split = line.split("\\s+");
            if (split.length < 7 || !devices.contains(split[0])) {
                continue;
            }
            HWDiskStore store = new HWDiskStore();
            store.setName(split[0]);
            store.setReads((long) ParseUtil.parseDoubleOrDefault(split[1], 0d));
            store.setWrites((long) ParseUtil.parseDoubleOrDefault(split[2], 0d));
            // In KB
            store.setReadBytes((long) (ParseUtil.parseDoubleOrDefault(split[3], 0d) * 1024));
            store.setWriteBytes((long) (ParseUtil.parseDoubleOrDefault(split[4], 0d) * 1024));
            // In seconds, multiply for ms
            store.setTransferTime((long) (ParseUtil.parseDoubleOrDefault(split[6], 0d) * 1000));
            store.setTimeStamp(timeStamp);
            diskMap.put(split[0], store);
        }

        // Now grab geom output for disks
        List<String> geom = ExecutingCommand.runNative("geom disk list");

        HWDiskStore store = null;
        for (String line : geom) {
            if (line.startsWith("Geom name:")) {
                // Process partition list on current store, if any
                if (store != null) {
                    setPartitions(store, partList);
                }
                String device = line.substring(line.lastIndexOf(' ') + 1);
                // Get the device.
                if (devices.contains(device)) {
                    store = diskMap.get(device);
                    // If for some reason we didn't have one, create
                    // a new value here.
                    if (store == null) {
                        store = new HWDiskStore();
                        store.setName(device);
                    }
                }
            }
            // If we don't have a valid store, don't bother parsing anything
            // until we do.
            if (store == null) {
                continue;
            }
            line = line.trim();
            if (line.startsWith("Mediasize:")) {
                String[] split = line.split("\\s+");
                if (split.length > 1) {
                    store.setSize(ParseUtil.parseLongOrDefault(split[1], 0L));
                }
            }
            if (line.startsWith("descr:")) {
                store.setModel(line.replace("descr:", "").trim());
            }
            if (line.startsWith("ident:")) {
                store.setSerial(line.replace("ident:", "").replace("(null)", "").trim());
            }
        }

        // Now grab geom output for partitions
        geom = ExecutingCommand.runNative("geom part list");
        store = null;
        HWPartition partition = null;
        for (String line : geom) {
            line = line.trim();
            if (line.startsWith("Geom name:")) {
                String device = line.substring(line.lastIndexOf(' ') + 1);
                // Get the device.
                if (devices.contains(device)) {
                    store = diskMap.get(device);
                    // If for some reason we didn't have one, create
                    // a new value here.
                    if (store == null) {
                        store = new HWDiskStore();
                        store.setName(device);
                    }
                }
            }
            // If we don't have a valid store, don't bother parsing anything
            // until we do.
            if (store == null) {
                continue;
            }
            if (line.contains("Name:")) {
                // Save the current partition, if any
                if (partition != null) {
                    partList.add(partition);
                    partition = null;
                }
                // Verify new entry is a partition
                // (will happen in 'providers' section)
                String part = line.substring(line.lastIndexOf(' ') + 1);
                if (part.startsWith(store.getName())) {
                    // Create a new partition.
                    partition = new HWPartition();
                    partition.setIdentification(part);
                    partition.setName(part);
                    partition.setMountPoint(getOrDefault(mountMap, part, ""));
                }
            }
            // If we don't have a valid store, don't bother parsing anything
            // until we do.
            if (partition == null) {
                continue;
            }
            String[] split = line.split("\\s+");
            if (split.length < 2) {
                continue;
            }
            if (line.startsWith("Mediasize:")) {
                partition.setSize(ParseUtil.parseLongOrDefault(split[1], 0L));
            } else if (line.startsWith("rawuuid:")) {
                partition.setUuid(split[1]);
            } else if (line.startsWith("type:")) {
                partition.setType(split[1]);
            }
        }
        // Process last partition list
        if (store != null) {
            setPartitions(store, partList);
        }

        // Populate result array
        List<HWDiskStore> diskList = new ArrayList<>(diskMap.keySet().size());
        diskList.addAll(diskMap.values());
        Collections.sort(diskList);

        return diskList.toArray(new HWDiskStore[diskList.size()]);
    }

    private void setPartitions(HWDiskStore store, List<HWPartition> partList) {
        HWPartition[] partitions = new HWPartition[partList.size()];
        int index = 0;
        Collections.sort(partList);
        for (HWPartition partition : partList) {
            // FreeBSD Major # is 0.
            // Minor # is filesize of /dev entry.
            partition.setMinor(ParseUtil
                    .parseIntOrDefault(ExecutingCommand.getFirstAnswer("stat -f %i /dev/" + partition.getName()), 0));
            partitions[index++] = partition;
        }
        partList.clear();
        store.setPartitions(partitions);
    }
}
