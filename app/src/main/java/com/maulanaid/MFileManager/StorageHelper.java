/*
 * Copyright 2024 maulana-id (https://github.com/maulana-id)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author maulana-id
 */

package com.maulanaid.MFileManager;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StorageHelper {

    public static final String STORAGE_ROOT_PATH = "/storage";

    private StorageHelper() {
    }

    public static final class StorageDevice {
        public final File file;
        public final String label;
        public final String type;
        public final int iconRes;
        public final long totalBytes;
        public final long freeBytes;
        public final long usedBytes;
        public final int usedPercent;
        public final boolean isPrimary;

        public StorageDevice(File file, String label, String type, int iconRes,
                             long totalBytes, long freeBytes, long usedBytes,
                             int usedPercent, boolean isPrimary) {
            this.file = file;
            this.label = label;
            this.type = type;
            this.iconRes = iconRes;
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.usedBytes = usedBytes;
            this.usedPercent = usedPercent;
            this.isPrimary = isPrimary;
        }

        public String getFormattedDetails() {
            if (totalBytes <= 0) {
                return "Storage device";
            }
            return String.format(Locale.getDefault(), "%s / %s (%d%% used)",
                    formatSize(usedBytes), formatSize(totalBytes), usedPercent);
        }

        public String getMaxAmountFormatted() {
            return formatSize(totalBytes);
        }

        public String getUsedAmountFormatted() {
            return formatSize(usedBytes);
        }

        public String getUsedPercentageFormatted() {
            return usedPercent + "%";
        }
    }

    public static boolean isStorageRoot(File dir) {
        if (dir == null) return false;
        String path = dir.getAbsolutePath();
        return STORAGE_ROOT_PATH.equals(path) || "/storage/emulated".equals(path);
    }

    public static List<StorageDevice> getStorageDevices(Context context) {
        List<StorageDevice> devices = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        // 1. Primary Internal Storage (default path)
        File primaryDir = Environment.getExternalStorageDirectory();
        if (primaryDir != null) {
            String path = primaryDir.getAbsolutePath();
            seenPaths.add(path);
            devices.add(buildDevice(primaryDir, "Internal Storage", "Internal",
                    R.drawable.ic_storage_internal, true));
        }

        // 2. StorageManager getStorageVolumes() (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context != null) {
            try {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    List<StorageVolume> volumes = sm.getStorageVolumes();
                    for (StorageVolume volume : volumes) {
                        File dir = getVolumeDirectory(volume);
                        if (dir == null || !dir.exists()) {
                            continue;
                        }
                        String absPath = dir.getAbsolutePath();
                        String description = volume.getDescription(context);
                        boolean isRemovable = volume.isRemovable();
                        boolean isPrimary = volume.isPrimary();

                        if (seenPaths.add(absPath)) {
                            int iconRes = isPrimary ? R.drawable.ic_storage_internal
                                    : (isRemovable ? R.drawable.ic_storage_sdcard : R.drawable.ic_storage_usb);
                            String type = isPrimary ? "Internal" : (isRemovable ? "SD Card" : "USB Drive");
                            String label = description;
                            if (label == null || label.trim().isEmpty() || label.equals("0")) {
                                label = isPrimary ? "Internal Storage" : (isRemovable ? "SD Card" : "External Storage");
                            }
                            devices.add(buildDevice(dir, label, type, iconRes, isPrimary));
                        } else if (isPrimary && !devices.isEmpty() && description != null
                                && !description.trim().isEmpty() && !description.equals("0")) {
                            // Update primary device label if system description is more descriptive
                            StorageDevice oldPrimary = devices.get(0);
                            devices.set(0, new StorageDevice(oldPrimary.file, description,
                                    oldPrimary.type, oldPrimary.iconRes, oldPrimary.totalBytes,
                                    oldPrimary.freeBytes, oldPrimary.usedBytes,
                                    oldPrimary.usedPercent, true));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Fallback / supplement with ContextCompat.getExternalFilesDirs() (works on all Android versions)
        if (context != null) {
            try {
                File[] externalDirs = ContextCompat.getExternalFilesDirs(context, null);
                if (externalDirs != null) {
                    for (File appDir : externalDirs) {
                        if (appDir == null) continue;
                        String path = appDir.getAbsolutePath();
                        int androidIndex = path.indexOf("/Android/");
                        if (androidIndex > 0) {
                            String rootPath = path.substring(0, androidIndex);
                            File rootDir = new File(rootPath);
                            if (rootDir.exists() && seenPaths.add(rootDir.getAbsolutePath())) {
                                boolean isRemovable = Environment.isExternalStorageRemovable(rootDir);
                                int icon = isRemovable ? R.drawable.ic_storage_sdcard : R.drawable.ic_storage_usb;
                                String type = isRemovable ? "SD Card" : "USB Drive";
                                String name = rootDir.getName();
                                String label = isRemovable ? "SD Card (" + name + ")" : "External Drive (" + name + ")";
                                devices.add(buildDevice(rootDir, label, type, icon, false));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 4. Also scan /storage directly for mounted drives (e.g. OTG, SD card mounts)
        File storageFolder = new File(STORAGE_ROOT_PATH);
        if (storageFolder.exists() && storageFolder.canRead()) {
            File[] children = storageFolder.listFiles();
            if (children != null) {
                for (File child : children) {
                    String name = child.getName();
                    if ("emulated".equals(name) || "self".equals(name) || "knox-emulated".equals(name)) {
                        continue;
                    }
                    String path = child.getAbsolutePath();
                    if (child.isDirectory() && seenPaths.add(path)) {
                        devices.add(buildDevice(child, "Mounted Disk (" + name + ")", "External",
                                R.drawable.ic_storage_sdcard, false));
                    }
                }
            }
        }

        return devices;
    }

    private static File getVolumeDirectory(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return volume.getDirectory();
            } catch (Throwable ignored) {
            }
        }
        try {
            Method getPathFile = volume.getClass().getMethod("getPathFile");
            return (File) getPathFile.invoke(volume);
        } catch (Throwable ignored) {
        }
        try {
            Method getPath = volume.getClass().getMethod("getPath");
            String path = (String) getPath.invoke(volume);
            if (path != null) return new File(path);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static StorageDevice buildDevice(File file, String label, String type,
                                             int iconRes, boolean isPrimary) {
        long total = 0;
        long free = 0;
        long used = 0;
        int percent = 0;

        try {
            StatFs stat = new StatFs(file.getAbsolutePath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            total = totalBlocks * blockSize;
            free = availableBlocks * blockSize;
            used = Math.max(0, total - free);
            if (total > 0) {
                percent = (int) Math.round(((double) used / total) * 100);
            }
        } catch (Throwable ignored) {
        }

        return new StorageDevice(file, label, type, iconRes, total, free, used, percent, isPrimary);
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.max(0, Math.min(digitGroups, units.length - 1));
        if (digitGroups == 0) {
            return bytes + " B";
        }
        return String.format(Locale.getDefault(), "%.1f %s",
                bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
