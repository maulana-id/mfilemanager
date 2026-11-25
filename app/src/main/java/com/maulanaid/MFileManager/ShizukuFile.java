package com.maulanaid.MFileManager;

import java.io.File;

/**
 * Virtual {@link File} representing a file or directory accessed through Shizuku shell privileges.
 */
public final class ShizukuFile extends File {
    private final String remotePath;
    private final String entryName;
    private final boolean directory;
    private final long size;
    private final long lastModifiedMillis;

    public ShizukuFile(String path, String name, boolean directory, long size, long lastModifiedMillis) {
        super(normalizePath(path));
        this.remotePath = normalizePath(path);
        this.entryName = resolveName(this.remotePath, name);
        this.directory = directory;
        this.size = Math.max(0L, size);
        this.lastModifiedMillis = Math.max(0L, lastModifiedMillis);
    }

    public static ShizukuFile directory(String path) {
        String normalized = normalizePath(path);
        return new ShizukuFile(normalized, resolveName(normalized, null), true, 0L, 0L);
    }

    public static ShizukuFile wrap(File file, boolean directory) {
        if (file instanceof ShizukuFile) {
            return (ShizukuFile) file;
        }
        String path = normalizePath(file.getAbsolutePath());
        return new ShizukuFile(path, file.getName(), directory,
                directory ? 0L : Math.max(0L, file.length()),
                Math.max(0L, file.lastModified()));
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String trimmed = path;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String resolveName(String path, String name) {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if ("/".equals(path)) {
            return "/";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @Override
    public String getName() {
        return entryName;
    }

    @Override
    public String getAbsolutePath() {
        return remotePath;
    }

    @Override
    public String getCanonicalPath() {
        return remotePath;
    }

    @Override
    public File getParentFile() {
        if ("/".equals(remotePath)) {
            return null;
        }
        int slash = remotePath.lastIndexOf('/');
        String parentPath = slash <= 0 ? "/" : remotePath.substring(0, slash);
        if (ShizukuFileHelper.isRestrictedPath(parentPath)) {
            return directory(parentPath);
        }
        return new File(parentPath);
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isFile() {
        return !directory;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public long length() {
        return directory ? 0L : size;
    }

    @Override
    public long lastModified() {
        return lastModifiedMillis;
    }
}
