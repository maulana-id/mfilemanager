package com.maulanaid.MFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Shell-backed file operations executed via Shizuku for Android restricted directories
 * such as {@code Android/data} and {@code Android/obb}.
 */
public final class ShizukuFileHelper {
    private static final String RECORD_PREFIX = "@@MFM@@\t";
    private static final int IO_BUFFER_SIZE = 16384;

    public interface ProgressCallback {
        void onProgress(String currentItem, int completed, int total, long bytesCopied, long totalBytes);
    }

    private ShizukuFileHelper() {
    }

    public static boolean isRestrictedAndroidDataFolder(File folder) {
        return folder != null && isRestrictedPath(folder.getAbsolutePath());
    }

    public static boolean isRestrictedPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.endsWith("/Android/data")
                || path.contains("/Android/data/")
                || path.endsWith("/Android/obb")
                || path.contains("/Android/obb/");
    }

    public static boolean isShizukuTarget(File file) {
        return file instanceof ShizukuFile || isRestrictedAndroidDataFolder(file);
    }

    public static boolean isBinderReady() {
        try {
            return Shizuku.pingBinder();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean hasShizukuAccess() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static IRemoteProcess createProcess(String command) throws Exception {
        Method requireService = Shizuku.class.getDeclaredMethod("requireService");
        requireService.setAccessible(true);
        IShizukuService service = (IShizukuService) requireService.invoke(null);
        return service.newProcess(
                new String[] { "/system/bin/sh", "-c", command },
                null,
                "/");
    }

    public static byte[] runCommand(String command, boolean readOutput) throws Exception {
        IRemoteProcess process = createProcess(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (readOutput) {
                drainStream(process.getInputStream(), output);
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Shizuku command failed (" + status + ")");
            }
            return output.toByteArray();
        } finally {
            process.destroy();
        }
    }

    private static void drainStream(ParcelFileDescriptor descriptor, ByteArrayOutputStream output)
            throws IOException {
        if (descriptor == null) {
            return;
        }
        try (ParcelFileDescriptor.AutoCloseInputStream stream =
                     new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output != null) {
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    public static String buildListCommand(String directory) {
        String quoted = shellQuote(directory);
        return "D=" + quoted + "; "
                + "[ -d \"$D\" ] || exit 2; "
                + "cd \"$D\" || exit 2; "
                + "if command -v stat >/dev/null 2>&1; then "
                + "find . -mindepth 1 -maxdepth 1 -exec stat -c '@@MFM@@\t%F\t%s\t%Y\t%n' {} +; "
                + "else "
                + "find . -mindepth 1 -maxdepth 1 -print0; "
                + "fi";
    }

    public static List<ShizukuFile> listDirectory(String directory) throws Exception {
        String normalizedDir = normalizeDir(directory);
        IRemoteProcess process = createProcess(buildListCommand(normalizedDir));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status;
        try {
            drainStream(process.getInputStream(), output);
            status = process.waitFor();
        } finally {
            process.destroy();
        }

        byte[] bytes = output.toByteArray();
        if (status != 0 && bytes.length == 0) {
            throw new IOException("Could not read directory with Shizuku (" + status + ")");
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains(RECORD_PREFIX)) {
            return parseStatOutput(normalizedDir, text, true);
        }
        return parseNullSeparatedOutput(normalizedDir, bytes);
    }

    static List<ShizukuFile> parseStatOutput(String parentDir, String text, boolean resolveSymlinks)
            throws Exception {
        String normalizedParent = normalizeDir(parentDir);
        List<ShizukuFile> result = new ArrayList<>();
        int cursor = text.indexOf(RECORD_PREFIX);
        while (cursor >= 0) {
            int start = cursor + RECORD_PREFIX.length();
            int next = text.indexOf(RECORD_PREFIX, start);
            String record = next >= 0 ? text.substring(start, next) : text.substring(start);
            while (record.endsWith("\n") || record.endsWith("\r")) {
                record = record.substring(0, record.length() - 1);
            }

            int tab1 = record.indexOf('\t');
            int tab2 = tab1 >= 0 ? record.indexOf('\t', tab1 + 1) : -1;
            int tab3 = tab2 >= 0 ? record.indexOf('\t', tab2 + 1) : -1;
            if (tab1 >= 0 && tab2 > tab1 && tab3 > tab2) {
                String type = record.substring(0, tab1).trim().toLowerCase(Locale.ROOT);
                long size = parseLongSafe(record.substring(tab1 + 1, tab2).trim());
                long mtimeSeconds = parseLongSafe(record.substring(tab2 + 1, tab3).trim());
                String rawName = record.substring(tab3 + 1);
                if (rawName.startsWith("./")) {
                    rawName = rawName.substring(2);
                }
                if (!rawName.isEmpty() && !".".equals(rawName) && !"..".equals(rawName)) {
                    String fullPath = joinPath(normalizedParent, rawName);
                    boolean isDir;
                    if (type.contains("directory")) {
                        isDir = true;
                    } else if (type.contains("link") && resolveSymlinks) {
                        isDir = isDirectory(fullPath);
                    } else {
                        isDir = false;
                    }
                    result.add(new ShizukuFile(fullPath, rawName, isDir, isDir ? 0L : size,
                            mtimeSeconds * 1000L));
                }
            }
            cursor = next;
        }
        return result;
    }

    private static List<ShizukuFile> parseNullSeparatedOutput(String parentDir, byte[] output)
            throws Exception {
        String normalizedParent = normalizeDir(parentDir);
        List<ShizukuFile> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index == output.length || output[index] == 0) {
                if (start < index) {
                    String rawName = new String(output, start, index - start, StandardCharsets.UTF_8);
                    if (rawName.startsWith("./")) {
                        rawName = rawName.substring(2);
                    }
                    int slash = rawName.lastIndexOf('/');
                    String name = slash >= 0 ? rawName.substring(slash + 1) : rawName;
                    if (!name.isEmpty() && !".".equals(name) && !"..".equals(name)) {
                        String fullPath = joinPath(normalizedParent, name);
                        boolean isDir = isDirectory(fullPath);
                        entries.add(new ShizukuFile(fullPath, name, isDir, 0L, 0L));
                    }
                }
                start = index + 1;
            }
        }
        return entries;
    }

    public static boolean exists(String path) throws Exception {
        return runTestCommand("test -e " + shellQuote(path));
    }

    public static boolean isDirectory(String path) throws Exception {
        return runTestCommand("test -d " + shellQuote(path));
    }

    public static boolean isFile(String path) throws Exception {
        return runTestCommand("test -f " + shellQuote(path));
    }

    private static boolean runTestCommand(String command) throws Exception {
        IRemoteProcess process = createProcess(command);
        try {
            return process.waitFor() == 0;
        } finally {
            process.destroy();
        }
    }

    public static void mkdir(String path) throws Exception {
        runCommand("mkdir " + shellQuote(path), false);
    }

    public static void mkdirs(String path) throws Exception {
        runCommand("mkdir -p " + shellQuote(path), false);
    }

    public static void createNewFile(String path) throws Exception {
        String q = shellQuote(path);
        runCommand("if [ -e " + q + " ]; then exit 1; fi; : > " + q, false);
    }

    public static void rename(String sourcePath, String destinationPath) throws Exception {
        String src = shellQuote(sourcePath);
        String dst = shellQuote(destinationPath);
        runCommand("if [ -e " + dst + " ]; then exit 1; fi; mv " + src + " " + dst, false);
    }

    public static void deleteRecursively(String path) throws Exception {
        runCommand("rm -rf " + shellQuote(path), false);
    }

    public static void copyWithinShizuku(String sourcePath, String destinationPath, boolean isDirectory)
            throws Exception {
        String src = shellQuote(sourcePath);
        String dst = shellQuote(destinationPath);
        if (isDirectory) {
            runCommand("if [ -e " + dst + " ] && [ ! -d " + dst + " ]; then rm -rf " + dst
                    + "; fi; mkdir -p " + dst + " && cp -rf " + src + "/. " + dst + "/", false);
        } else {
            runCommand("if [ -d " + dst + " ]; then rm -rf " + dst + "; fi; cp -f " + src
                    + " " + dst, false);
        }
    }

    public static File stageToCache(Context context, ShizukuFile remoteFile, ProgressCallback progress)
            throws Exception {
        File stageRoot = new File(context.getCacheDir(),
                "shizuku_stage/" + Integer.toHexString(remoteFile.getAbsolutePath().hashCode()));
        if (!stageRoot.exists() && !stageRoot.mkdirs()) {
            throw new IOException("Could not create cache staging directory");
        }
        File localFile = new File(stageRoot, remoteFile.getName());
        readFileToLocal(remoteFile.getAbsolutePath(), localFile, remoteFile.length(), progress);
        return localFile;
    }

    public static void readFileToLocal(String remotePath, File localDestination, long totalBytes,
            ProgressCallback progress) throws Exception {
        File parent = localDestination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create destination folder");
        }
        IRemoteProcess process = createProcess("cat " + shellQuote(remotePath));
        try {
            ParcelFileDescriptor inputFd = process.getInputStream();
            if (inputFd == null) {
                throw new IOException("Could not open Shizuku input stream");
            }
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            long copied = 0L;
            if (progress != null) {
                progress.onProgress(remotePath, 0, 1, 0L, totalBytes);
            }
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(inputFd);
                 OutputStream output = new FileOutputStream(localDestination)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    copied += read;
                    if (progress != null) {
                        progress.onProgress(remotePath, 0, 1, copied, totalBytes);
                    }
                }
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Failed to read restricted file (" + status + ")");
            }
        } finally {
            process.destroy();
        }
    }

    public static void writeLocalToRemote(File localSource, String remotePath, ProgressCallback progress)
            throws Exception {
        long totalBytes = localSource.length();
        IRemoteProcess process = createProcess("cat > " + shellQuote(remotePath));
        try {
            ParcelFileDescriptor outputFd = process.getOutputStream();
            if (outputFd == null) {
                throw new IOException("Could not open Shizuku output stream");
            }
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            long copied = 0L;
            if (progress != null) {
                progress.onProgress(remotePath, 0, 1, 0L, totalBytes);
            }
            try (InputStream input = new FileInputStream(localSource);
                 OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(outputFd)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    copied += read;
                    if (progress != null) {
                        progress.onProgress(remotePath, 0, 1, copied, totalBytes);
                    }
                }
                output.flush();
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Failed to write restricted file (" + status + ")");
            }
        } finally {
            process.destroy();
        }
    }

    public static void copyRemoteToLocalRecursively(ShizukuFile source, File destination,
            int[] completed, int total, ProgressCallback progress) throws Exception {
        if (progress != null) {
            progress.onProgress(source.getAbsolutePath(), completed[0], total, 0L, 0L);
        }
        if (source.isDirectory()) {
            if (destination.exists() && !destination.isDirectory() && !deleteLocalRecursively(destination)) {
                throw new IOException("Cannot replace destination file");
            }
            if (!destination.mkdirs() && !destination.isDirectory()) {
                throw new IOException("Cannot create destination folder");
            }
            List<ShizukuFile> children = listDirectory(source.getAbsolutePath());
            for (ShizukuFile child : children) {
                copyRemoteToLocalRecursively(child, new File(destination, child.getName()),
                        completed, total, progress);
            }
            completed[0]++;
            if (progress != null) {
                progress.onProgress(source.getAbsolutePath(), completed[0], total, 0L, 0L);
            }
        } else {
            if (destination.exists() && destination.isDirectory() && !deleteLocalRecursively(destination)) {
                throw new IOException("Cannot replace destination folder");
            }
            readFileToLocal(source.getAbsolutePath(), destination, source.length(),
                    (item, c, t, bytesCopied, totalBytes) -> {
                        if (progress != null) {
                            progress.onProgress(item, completed[0], total, bytesCopied, totalBytes);
                        }
                    });
            completed[0]++;
            if (progress != null) {
                progress.onProgress(source.getAbsolutePath(), completed[0], total,
                        source.length(), source.length());
            }
        }
    }

    public static void copyLocalToRemoteRecursively(File source, String remoteDestination,
            int[] completed, int total, ProgressCallback progress) throws Exception {
        if (progress != null) {
            progress.onProgress(source.getAbsolutePath(), completed[0], total, 0L, 0L);
        }
        if (source.isDirectory()) {
            if (exists(remoteDestination) && !isDirectory(remoteDestination)) {
                deleteRecursively(remoteDestination);
            }
            mkdirs(remoteDestination);
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read source folder");
            }
            for (File child : children) {
                copyLocalToRemoteRecursively(child, joinPath(remoteDestination, child.getName()),
                        completed, total, progress);
            }
            completed[0]++;
            if (progress != null) {
                progress.onProgress(source.getAbsolutePath(), completed[0], total, 0L, 0L);
            }
        } else {
            if (isDirectory(remoteDestination)) {
                deleteRecursively(remoteDestination);
            }
            writeLocalToRemote(source, remoteDestination, (item, c, t, bytesCopied, totalBytes) -> {
                if (progress != null) {
                    progress.onProgress(source.getAbsolutePath(), completed[0], total,
                            bytesCopied, totalBytes);
                }
            });
            completed[0]++;
            if (progress != null) {
                progress.onProgress(source.getAbsolutePath(), completed[0], total,
                        source.length(), source.length());
            }
        }
    }

    public static int countItemsRecursively(String path) throws Exception {
        String command = "D=" + shellQuote(path) + "; "
                + "if [ -d \"$D\" ]; then find \"$D\" | wc -l; "
                + "elif [ -e \"$D\" ]; then echo 1; "
                + "else echo 0; fi";
        byte[] output = runCommand(command, true);
        String text = new String(output, StandardCharsets.UTF_8).trim();
        int count = (int) parseLongSafe(text);
        return Math.max(1, count);
    }

    public static long[] calculateFolderStats(String directory) throws Exception {
        String command = "D=" + shellQuote(directory) + "; "
                + "[ -d \"$D\" ] || exit 2; "
                + "cd \"$D\" || exit 2; "
                + "if command -v stat >/dev/null 2>&1; then "
                + "find . -mindepth 1 -exec stat -c '@@MFM@@\t%F\t%s\t0\t%n' {} +; "
                + "else "
                + "find . -mindepth 1 -print0; "
                + "fi";
        IRemoteProcess process = createProcess(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            drainStream(process.getInputStream(), output);
            process.waitFor();
        } finally {
            process.destroy();
        }
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        long totalBytes = 0L;
        long files = 0L;
        long folders = 0L;
        if (text.contains(RECORD_PREFIX)) {
            List<ShizukuFile> entries = parseStatOutput(directory, text, false);
            int limit = Math.min(entries.size(), 50000);
            for (int i = 0; i < limit; i++) {
                ShizukuFile entry = entries.get(i);
                if (entry.isDirectory()) {
                    folders++;
                } else {
                    files++;
                    totalBytes += entry.length();
                }
            }
        }
        return new long[] { totalBytes, files, folders };
    }

    public static String[] computeChecksums(String remotePath) throws Exception {
        IRemoteProcess process = createProcess("cat " + shellQuote(remotePath));
        try {
            ParcelFileDescriptor inputFd = process.getInputStream();
            if (inputFd == null) {
                throw new IOException("Could not read restricted file");
            }
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(inputFd)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    md5.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                }
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Failed to read file for checksum (" + status + ")");
            }
            return new String[] { bytesToHex(md5.digest()), bytesToHex(sha256.digest()) };
        } finally {
            process.destroy();
        }
    }

    public static String joinPath(String parent, String child) {
        String normalizedParent = normalizeDir(parent);
        if ("/".equals(normalizedParent)) {
            return "/" + child;
        }
        return normalizedParent + "/" + child;
    }

    private static String normalizeDir(String directory) {
        if (directory == null || directory.isEmpty()) {
            return "/";
        }
        String normalized = directory;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean deleteLocalRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteLocalRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
