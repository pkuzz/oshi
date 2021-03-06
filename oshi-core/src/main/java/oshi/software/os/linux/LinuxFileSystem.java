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
package oshi.software.os.linux;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.util.FileUtil;
import oshi.util.ParseUtil;

import static oshi.util.Util.getOrDefault;

/**
 * The Linux File System contains {@link OSFileStore}s which are a storage pool,
 * device, partition, volume, concrete file system or other implementation
 * specific means of file storage. In Linux, these are found in the /proc/mount
 * filesystem, excluding temporary and kernel mounts.
 *
 * @author widdis[at]gmail[dot]com
 */
public class LinuxFileSystem implements FileSystem {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(LinuxFileSystem.class);

    // Linux defines a set of virtual file systems
    private final List<String> pseudofs = Arrays.asList(new String[] { //
            "rootfs", // Minimal fs to support kernel boot
            "sysfs", // SysFS file system
            "proc", // Proc file system
            "devtmpfs", // Dev temporary file system
            "devpts", // Dev pseudo terminal devices file system
            "securityfs", // Kernel security file system
            "cgroup", // Cgroup file system
            "pstore", // Pstore file system
            "hugetlbfs", // Huge pages support file system
            "configfs", // Config file system
            "selinuxfs", // SELinux file system
            "systemd-1", // Systemd file system
            "binfmt_misc", // Binary format support file system
            "mqueue", // Message queue file system
            "debugfs", // Debug file system
            "nfsd", // NFS file system
            "sunrpc", // Sun RPC file system
            "rpc_pipefs", // Sun RPC file system
            "fusectl", // FUSE control file system
            // NOTE: FUSE's fuseblk is not evalued because used as file system
            // representation of a FUSE block storage
            // "fuseblk" // FUSE block file system
            // "tmpfs", // Temporary file system
            // NOTE: tmpfs is evaluated apart, because Linux uses it for
            // RAMdisks
    });

    // System path mounted as tmpfs
    private final List<String> tmpfsPaths = Arrays.asList(new String[] { "/dev/shm", "/run", "/sys", "/proc" });

    /**
     * Checks if file path equals or starts with an element in the given list
     *
     * @param aList
     *            A list of path prefixes
     * @param charSeq
     *            a path to check
     * @return true if the charSeq exactly equals, or starts with the directory
     *         in aList
     */
    private boolean listElementStartsWith(List<String> aList, String charSeq) {
        for (String match : aList) {
            if (charSeq.equals(match) || charSeq.startsWith(match + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets File System Information.
     *
     * @return An array of {@link OSFileStore} objects representing mounted
     *         volumes. May return disconnected volumes with
     *         {@link OSFileStore#getTotalSpace()} = 0.
     */
    @Override
    public OSFileStore[] getFileStores() {
        // Map uuids with device path as key
        Map<String, String> uuidMap = new HashMap<>();
        File uuidDir = new File("/dev/disk/by-uuid");
        if (uuidDir != null && uuidDir.listFiles() != null) {
            for (File uuid : uuidDir.listFiles()) {
                try {
                    // Store UUID as value with path (e.g., /dev/sda1) as key
                    uuidMap.put(uuid.getCanonicalPath(), uuid.getName().toLowerCase());
                } catch (IOException e) {
                    LOG.error("Couldn't get canonical path for {}. {}", uuid.getName(), e);
                }
            }
        }

        // List file systems
        List<OSFileStore> fsList = new ArrayList<>();

        // Parse /proc/self/mounts to get fs types
        List<String> mounts = FileUtil.readFile("/proc/self/mounts");
        for (String mount : mounts) {
            String[] split = mount.split(" ");
            // As reported in fstab(5) manpage, struct is:
            // 1st field is volume name
            // 2nd field is path with spaces escaped as \040
            // 3rd field is fs type
            // 4th field is mount options (ignored)
            // 5th field is used by dump(8) (ignored)
            // 6th field is fsck order (ignored)
            if (split.length < 6) {
                continue;
            }

            // Exclude pseudo file systems
            String path = split[1].replaceAll("\\\\040", " ");
            String type = split[2];
            if (this.pseudofs.contains(type) || path.equals("/dev") || listElementStartsWith(this.tmpfsPaths, path)) {
                continue;
            }

            String name = split[0].replaceAll("\\\\040", " ");
            if (path.equals("/")) {
                name = "/";
            }
            String volume = split[0].replaceAll("\\\\040", " ");
            String uuid = getOrDefault(uuidMap, split[0], "");
            long totalSpace = new File(path).getTotalSpace();
            long usableSpace = new File(path).getUsableSpace();

            String description;
            if (volume.startsWith("/dev")) {
                description = "Local Disk";
            } else if (volume.equals("tmpfs")) {
                description = "Ram Disk";
            } else if (type.startsWith("nfs") || type.equals("cifs")) {
                description = "Network Disk";
            } else {
                description = "Mount Point";
            }

            OSFileStore osStore = new OSFileStore(name, volume, path, description, type, uuid, usableSpace, totalSpace);
            fsList.add(osStore);
        }

        return fsList.toArray(new OSFileStore[fsList.size()]);
    }

    @Override
    public long getOpenFileDescriptors() {
        return getFileDescriptors(0);
    }

    @Override
    public long getMaxFileDescriptors() {
        return getFileDescriptors(2);
    }

    /**
     * Returns a value from the Linux system file /proc/sys/fs/file-nr.
     *
     * @param index
     *            The index of the value to retrieve. 0 returns the total
     *            allocated file descriptors. 1 returns the number of used file
     *            descriptors for kernel 2.4, or the number of unused file
     *            descriptors for kernel 2.6. 2 returns the maximum number of
     *            file descriptors that can be allocated.
     * @return Corresponding file descriptor value from the Linux system file.
     */
    private long getFileDescriptors(int index) {
        String filename = "/proc/sys/fs/file-nr";
        if (index < 0 || index > 2) {
            throw new IllegalArgumentException("Index must be between 0 and 2.");
        }
        List<String> osDescriptors = FileUtil.readFile(filename);
        if (!osDescriptors.isEmpty()) {
            String[] splittedLine = osDescriptors.get(0).split("\\D+");
            return ParseUtil.parseLongOrDefault(splittedLine[index], 0L);
        }
        return 0L;
    }
}
