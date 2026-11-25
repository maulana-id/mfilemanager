package com.maulanaid.MFileManager;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ShizukuFileHelperTest {

    @Test
    public void detectsRestrictedAndroidPaths() {
        assertTrue(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/data"));
        assertTrue(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/data/com.example/files"));
        assertTrue(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/obb"));
        assertTrue(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/obb/com.example"));

        assertFalse(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android"));
        assertFalse(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/media"));
        assertFalse(ShizukuFileHelper.isRestrictedPath("/storage/emulated/0/Android/data_backup"));
        assertFalse(ShizukuFileHelper.isRestrictedPath(null));
    }

    @Test
    public void quotesShellArgumentsSafely() {
        assertEquals("'simple'", ShizukuFileHelper.shellQuote("simple"));
        assertEquals("'my folder/file name.txt'", ShizukuFileHelper.shellQuote("my folder/file name.txt"));
        assertEquals("'it'\"'\"'s a $test'", ShizukuFileHelper.shellQuote("it's a $test"));
    }

    @Test
    public void parsesStatOutputIntoShizukuFiles() throws Exception {
        String output = ""
                + "@@MFM@@\tdirectory\t4096\t1700000000\t./com.example.app\n"
                + "@@MFM@@\tregular file\t12345\t1700000123\t./save data\twith tab.json\n"
                + "@@MFM@@\tregular empty file\t0\t1700000456\t./.nomedia\n";

        List<ShizukuFile> files = ShizukuFileHelper.parseStatOutput(
                "/storage/emulated/0/Android/data", output, false);

        assertEquals(3, files.size());

        ShizukuFile dir = files.get(0);
        assertEquals("com.example.app", dir.getName());
        assertEquals("/storage/emulated/0/Android/data/com.example.app", dir.getAbsolutePath());
        assertTrue(dir.isDirectory());
        assertFalse(dir.isFile());
        assertEquals(0L, dir.length());
        assertEquals(1700000000000L, dir.lastModified());

        ShizukuFile json = files.get(1);
        assertEquals("save data\twith tab.json", json.getName());
        assertFalse(json.isDirectory());
        assertTrue(json.isFile());
        assertEquals(12345L, json.length());
        assertEquals(1700000123000L, json.lastModified());

        ShizukuFile dotfile = files.get(2);
        assertEquals(".nomedia", dotfile.getName());
        assertTrue(dotfile.isFile());
        assertEquals(0L, dotfile.length());
    }

    @Test
    public void shizukuFileParentTransitionsBetweenRestrictedAndRegular() {
        ShizukuFile nested = ShizukuFile.directory("/storage/emulated/0/Android/data/com.example/files");
        File appDir = nested.getParentFile();
        assertNotNull(appDir);
        assertTrue(appDir instanceof ShizukuFile);
        assertEquals("/storage/emulated/0/Android/data/com.example", appDir.getAbsolutePath());

        File dataDir = appDir.getParentFile();
        assertNotNull(dataDir);
        assertTrue(dataDir instanceof ShizukuFile);
        assertEquals("/storage/emulated/0/Android/data", dataDir.getAbsolutePath());

        File androidDir = dataDir.getParentFile();
        assertNotNull(androidDir);
        assertFalse(androidDir instanceof ShizukuFile);
        assertEquals("/storage/emulated/0/Android", androidDir.getAbsolutePath());

        assertEquals(new File("/storage/emulated/0/Android/data"), dataDir);
    }
}
