# MFile Manager

**Version 1.1** | Android application ID: `com.maulanaid.MFileManager`

MFile Manager is an Android file manager built around a VS Code-inspired visual design. It provides a full-featured file browsing and management experience across shared storage and, with optional elevated access, inside the system-restricted `Android/data` and `Android/obb` directories.

---

## Table of Contents

- [How the App Works](#how-the-app-works)
- [File Browser](#file-browser)
- [Tree View and Folder Focus](#tree-view-and-folder-focus)
- [File Operations](#file-operations)
- [Built-in Viewers](#built-in-viewers)
- [Archive Support](#archive-support)
- [Restricted Folder Access](#restricted-folder-access)
- [Network Sharing](#network-sharing)
- [First-Run Acknowledgement](#first-run-acknowledgement)
- [Requirements](#requirements)
- [Building](#building)
- [Project Layout](#project-layout)
- [Versioning](#versioning)
- [License](#license)

---

## How the App Works

When the app opens it requests storage permission appropriate for the Android version installed on the device. On Android 11 and newer (API 30+) it asks for the special "All files access" (MANAGE_EXTERNAL_STORAGE) permission; on older versions it asks for the standard external storage read and write permissions. Once permission is granted the main file list opens at the primary shared storage root (typically `/storage/emulated/0`).

The interface follows a VS Code Dark+ / Light+ colour palette that the user can switch at any time from Settings. The main screen is a scrollable list of folders and files. Each row shows a type-specific icon, the item's name, its last modified date, and its size. Image, video, and PDF files automatically generate small thumbnail previews in the background; other types keep their format icon. Thumbnails are cached in memory for the duration of the browsing session.

Navigation controls sit in a floating rail at the bottom-right of the screen for comfortable one-handed use: a Home button that returns to the storage root, and an Up button that moves to the parent directory. At the top of the screen a path field accepts an absolute path or a path relative to the current directory; pressing Go jumps there immediately. Pressing the system Back button navigates to the parent directory. At the storage root, Back prompts the user before exiting; pressing it twice within two seconds exits without a prompt.

The search field at the top of the list filters by name within the current scope. When a folder is focused (see Tree View and Folder Focus below) the search scope narrows to that folder and the placeholder text updates to reflect it.

---

## File Browser

**Grouping and sorting.** Open Settings from the top-right corner to change how the list is organised. Items can be grouped by file type, by modified date, or shown as a single ungrouped list. Within any group the list can be sorted by name, by modified date (newest first), or by extension. An optional "Folders first" setting shows all directories before all files within each group. These preferences are saved on the device and restored the next time the app opens.

**Row interactions.** Tapping a folder opens it and loads its contents. Tapping a file opens it with Android's app chooser. The first long-press on any row enters selection mode and selects that row. While selection is active, single taps toggle individual rows; long-pressing an unselected row adds it without opening the action menu, while long-pressing an already-selected row opens the icon-and-label action menu.

**Selection actions.** The action menu and the toolbar offer: copy, paste, delete, rename (single item only), properties (single item only), select all, clear selection, compress (create archive), share over HTTP, and open as a chosen MIME type. The toolbar Create control adds a new folder or an empty file at the current location.

**Properties.** For a single selected item, Properties shows name, path, size (recursively totalled for folders), item count inside a folder, and modified date. For files it also shows the MD5 and SHA-256 checksums, computed on demand.

**Paste conflicts.** When pasting into a destination that already contains items with the same name, the app asks individually about each conflict. The choices are Replace (overwrite the destination), Skip (keep the destination), Replace all (apply Replace to all remaining conflicts automatically), Skip all (apply Skip to all remaining conflicts automatically), or Cancel (stop the paste operation). Items already pasted are not rolled back. When a folder is replaced, its contents are merged: matching files are overwritten and files that exist only in the destination are kept.

**Deletion.** Deleting selected items shows a confirmation dialog, because the operation is applied recursively to any selected folders. The app does not use a recycle bin; deleted items cannot be recovered from within the app.

---

## Tree View and Folder Focus

Enabling Tree view in Settings changes the browsing mode from a directory-by-directory navigation to an inline expandable tree. Tapping a folder in tree view toggles it open or closed. Only the rows belonging to that folder are inserted or removed from the list; unrelated rows are not rebuilt or re-animated.

**Folder Focus.** The last folder that was expanded or collapsed becomes the focused folder. The focused row is highlighted with a 1.5 dp accent-coloured border so it is easy to identify at a glance. The search field scopes itself to the contents of the focused folder: results show only items whose names match within that subtree, and the search placeholder updates to "Search in [folder name]". Navigating to a new directory or returning home clears the focus.

---

## File Operations

**Copy and paste.** Copy is available from the selection action menu. Paste is available from the same menu when items are on the clipboard, and also from Settings when the current directory is empty. The clipboard persists until the app is killed or new items are copied.

**Rename.** Select a single item and choose Rename. The dialog pre-fills the current name and, for files, positions the cursor before the extension.

**Create.** The Create toolbar button opens a dialog where you choose between a new folder and a new empty file, then enter the name.

**Delete.** Select one or more items and choose Delete. A confirmation dialog lists the selection before proceeding. Folder deletion is always recursive.

**Checksums.** Open Properties for a single file to see its MD5 and SHA-256 checksums. For large files the computation runs on a background thread and the values appear when ready.

**Open as.** When a file's default type is not what you want, Open as lets you choose from a list of MIME types: text, audio, video, image, PDF, APK, archive, or any type. The selected MIME type is passed to Android's chooser.

---

## Built-in Viewers

MFile Manager includes viewers for common file types that open directly within the app rather than through an external application.

**Image viewer.** Opens image files for inline viewing with pinch-to-zoom and pan gestures. The viewer handles JPEG, PNG, GIF, WebP, BMP, and other formats decodable by Android's BitmapFactory. For Shizuku-accessed files the image is staged through the app cache before display.

**Video player.** Opens video files with Android's MediaPlayer. Playback starts automatically. For Shizuku-accessed files the video is staged from the elevated shell before the player opens.

**Audio player.** Opens audio files with MediaPlayer and shows a minimal playback interface. For Shizuku-accessed files audio is staged through the app cache.

**Text editor.** Opens text files for reading and editing. The editor shows the raw file content. After editing, saving writes the content back to the original path. For normal files the write uses a standard Java OutputStream. For files accessed through Shizuku the content is written back via the elevated shell so changes persist to the restricted location.

---

## Archive Support

**Creating archives.** Select files and folders in the file list, long-press to open the action menu, and choose Compress. Supported output formats are ZIP, 7z, TAR, and TAR.GZ. GZIP compression is available when exactly one file is selected. ZIP and 7z support optional password protection; TAR, TAR.GZ, and GZ do not. After compression finishes a result dialog reports the original size, the archive size, and the compression ratio.

**Browsing archives.** Tap an archive file or long-press and choose Browse archive. The app opens an inline tree of the archive's entries, showing each entry's name and compressed size. You can navigate into sub-entries and browse the archive structure without extracting it. Password-protected archives prompt for the password before the entry list is shown.

**Extracting archives.** Long-press an archive and choose Extract archive. The contents are unpacked into a new folder in the same directory as the archive. Absolute paths and path traversal sequences inside the archive are rejected during extraction for safety. Encrypted archives prompt for the password.

**Supported formats.** ZIP, 7z, TAR, TAR.GZ, and GZ for browsing and extraction. RAR files can be browsed and extracted (including password-protected RAR archives supported by the reader) but cannot be created by the app. Use ZIP or 7z to create password-protected archives.

---

## Restricted Folder Access

Android restricts access to `Android/data` and `Android/obb` on the shared storage volume. MFile Manager provides two ways to access these directories.

**Storage Access Framework (SAF).** From Settings, choose "Grant restricted folder access" and select a folder with Android's document picker. The persisted grant is remembered across app restarts and can be browsed from "Browse restricted folder" in Settings. SAF access is integrated for navigation and opening files, but copy, rename, and delete operations within the main file list do not extend to SAF-only grants.

**Shizuku.** When the Shizuku service is running and permission has been granted, tapping `Android/data` or `Android/obb` in the file list opens those directories directly in the main file browser using an elevated shell back end, with no separate dialog or standalone text interface. The experience is indistinguishable from browsing any unrestricted folder: folders expand and collapse with the same animations, files show thumbnails and metadata, and all the usual actions are available.

The Shizuku back end supports listing directory contents, reading and writing files, copying, moving, renaming, deleting, creating folders, and computing MD5 and SHA-256 checksums, all through the elevated shell. Files that the user opens (images, video, audio) are staged to the app cache first, then passed to the viewer or external app. Text files edited in the built-in editor are written back to the restricted path so the edit persists. Shizuku does not grant storage permissions to the app's Android UID and does not change the behaviour of unrestricted paths.

**Settings status.** "Settings -> Shizuku restricted access" shows whether the Shizuku binder is running and whether the app holds permission. If permission has not been granted, tapping `Android/data` or `Android/obb` automatically requests it before opening.

**Wireless debugging guide.** The Settings screen includes a step-by-step guide for enabling Wireless ADB debugging, which is required to run Shizuku without a USB connection. MFile Manager does not connect to ADB ports directly; the guide is informational only.

---

## Network Sharing

### HTTP File Sharing

HTTP sharing serves a selected file or folder over a local web server on port 8080.

Before sharing, configure a username and password in "Settings -> HTTP share login". The username defaults to "MFile Manager"; usernames must be 1 to 64 printable ASCII characters and cannot contain a colon. Passwords must be at least 8 characters.

To start a share, long-press a file or folder in the file list and choose "Share this file/folder over HTTP". Only one item can be shared at a time; starting a new share while one is active asks for confirmation. After sharing starts, the in-app status bar and the persistent Android notification both show the share address and a Stop control. The notification is required on Android 13 and newer for the Stop action to be reachable from outside the app.

Another device on the same local network can open one of the displayed `http://<device-ip>:8080` addresses in a browser and enter the configured credentials. The browser page follows the app's light/dark theme preference. Within the shared folder the browser shows each item's name, modified date, and size. Available actions depend on what is shared: browsing a folder allows navigating into sub-folders, downloading individual files, selecting multiple files for a combined streaming ZIP download, uploading files, and deleting selected items. Sharing a single file is download-only.

Upload conflict handling mirrors the in-app paste flow: Replace, Skip, Replace all, Skip all, or Cancel remaining. Replacements are written atomically. After an upload batch completes the browser refreshes the listing. Deletion requires browser confirmation and cannot remove the shared root itself.

The server verifies that all requested paths remain inside the shared item and rejects paths outside it. The server uses HTTP Basic authentication. Traffic is not encrypted; use HTTP sharing only on a trusted private network.

### FTP File Sharing

FTP sharing serves a selected folder over a local FTP server on port 2121.

Configure the shared username and password in "Settings -> File share login" (the same credentials used for HTTP). Long-press a folder in the file list and choose "Share folder over FTP (read-only)". FTP sharing is available for folders only; use HTTP sharing to share a single file.

Clients connect in passive mode using an address shown in the in-app status bar, port 2121, and the configured credentials. FTP access is strictly read-only: clients can list and download files but cannot upload, overwrite, rename, or delete anything. The passive data port range is 21210 to 21219; allow TCP port 2121 and that range through the local firewall if connections fail.

Stop the FTP service using the in-app Stop control or the persistent notification. FTP traffic is not encrypted; use it only on a trusted private network.

---

## First-Run Acknowledgement

On first launch the app shows an acknowledgement screen before requesting storage permission. The notice explains how storage access works, warns that deletion and replacement operations cannot be undone from within the app, notes that HTTP and FTP sharing does not encrypt traffic, and states that sharing exposes selected files to any authenticated device on the local network. Declining exits without requesting storage access. The acknowledgement is versioned so a future material change in the app's storage use can require the user to read and accept it again.

---

## Requirements

- Android 7.0 or newer (API level 24 minimum)
- All files access permission (Android 11+) or external storage permission (Android 10 and older)
- Shizuku (optional) for direct access to `Android/data` and `Android/obb`

---

## Building

Install JDK 17 and Android SDK Platform 34, then configure the SDK path via `ANDROID_HOME` or a `local.properties` file in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug APK from the project root:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Install it on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/MFIle-Manager-universal-debug.apk
```

Run the unit tests:

```sh
./gradlew testDebugUnitTest
```

---

## Project Layout

```text
.
├── app/
│   ├── build.gradle                 # Android app configuration and dependencies
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/                    # Application source code
│       └── res/                     # Layouts, strings, drawables, and themes
├── gradle/wrapper/                  # Gradle Wrapper configuration
├── build.gradle                     # Root build file and plugin versions
├── gradle.properties                # Shared build properties and app version
├── settings.gradle                  # Project and plugin repository declarations
└── gradlew                          # Gradle Wrapper for Linux and macOS
```

---

## Versioning

The app version is managed in `gradle.properties`:

```properties
appVersionCode=2
appVersionName=1.1
```

Increase `appVersionCode` by at least one for every release that is distributed as an update. Set `appVersionName` to the human-readable version shown to users. Keep `versionCode` strictly increasing even when `versionName` stays the same or is changed.

---

## License

This application and its source code are proprietary and are not offered under a public source-code license.

Third-party libraries bundled in the app remain governed by their own licenses. Before publishing, include any required license and attribution notices in the app or its distribution materials and verify each dependency's terms. The app's "Settings -> Open source licenses" screen displays dependency license information generated during the build.
