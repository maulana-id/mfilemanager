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

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeMap;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.webkit.MimeTypeMap;
import androidx.documentfile.provider.DocumentFile;
import rikka.shizuku.Shizuku;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IRemoteProcess;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int INSTALL_PERMISSION_REQUEST_CODE = 102;
    private static final int RESTRICTED_FOLDER_REQUEST_CODE = 103;
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 104;
    private static final String PREFS_NAME = "file_manager_preferences";
    private static final String PREF_USER_AGREEMENT_VERSION = "user_agreement_version";
    private static final int USER_AGREEMENT_VERSION = 1;
    private static final String PREF_GROUPING = "grouping";
    private static final String PREF_SORTING = "sorting";
    private static final String PREF_FOLDERS_FIRST = "folders_first";
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String PREF_TREE_VIEW = "tree_view_enabled";
    private static final String PREF_ANIMATIONS = "animations_enabled";
    private static final String PREF_SHOW_HIDDEN = "show_hidden_files";
    private static final String GROUP_NONE = "none";
    private static final String GROUP_TYPE = "type";
    private static final String GROUP_DATE = "date";
    private static final String SORT_NAME = "name";
    private static final String SORT_DATE = "date";
    private static final String SORT_EXTENSION = "extension";
    private static final int CONFLICT_ASK = 0;
    private static final int CONFLICT_REPLACE_ALL = 1;
    private static final int CONFLICT_SKIP_ALL = 2;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final String FILE_OPERATION_CHANNEL_ID = "file_operations";
    private static final int FILE_OPERATION_NOTIFICATION_ID = 2122;
    private static final long DOUBLE_BACK_EXIT_WINDOW_MS = 2000;
    private static final String PREF_HTTP_PASSWORD = "http_share_password";
    private static final String PREF_HTTP_USERNAME = "http_share_username";
    private static final String DEFAULT_HTTP_USERNAME = "MFileManager";
    private static final String PREF_HTTP_ACTIVE = "http_share_active";
    private static final String PREF_HTTP_PATH = "http_share_path";
    private static final String PREF_HTTP_URLS = "http_share_urls";
    private static final String PREF_HTTP_ERROR = "http_share_error";
    private static final String PREF_FTP_ACTIVE = "ftp_share_active";
    private static final String PREF_FTP_PATH = "ftp_share_path";
    private static final String PREF_FTP_URLS = "ftp_share_urls";
    private static final String PREF_FTP_ERROR = "ftp_share_error";
    private static final String PREF_HTTP_READONLY = HttpShareService.PREF_HTTP_READONLY;
    private static final String PREF_FTP_READONLY = FtpShareService.PREF_FTP_READONLY;
    private static final String PREF_RESTRICTED_FOLDER_URI = "restricted_folder_uri";

    private RecyclerView fileList;
    private LinearLayoutManager fileLayoutManager;
    private RecyclerView.ItemAnimator fileItemAnimator;
    private FileListAdapter adapter;
    private boolean treeViewEnabled = true;
    private boolean animationsEnabled = true;
    private final Set<String> expandedPaths = new LinkedHashSet<>();
    private File lastClickedDirectory;
    private final List<File> expansionStack = new ArrayList<>();
    private EditText pathInput;
    private EditText searchInput;
    private TextView httpShareStatus;
    private ImageButton stopHttpShareButton;
    private ImageButton copyHttpShareUrlButton;
    private File homeDirectory;
    private File currentDirectory;
    private final Set<File> selectedFiles = new LinkedHashSet<>();
    private final List<File> clipboardFiles = new ArrayList<>();
    private File archiveClipboardFile;
    private final List<String> archiveClipboardEntries = new ArrayList<>();
    private char[] archiveClipboardPassword = new char[0];
    private File archiveBrowserFile;
    private ArchiveBrowserNode archiveBrowserRoot;
    private char[] archiveBrowserPassword = new char[0];
    private final Set<String> expandedArchivePaths = new LinkedHashSet<>();
    private int archiveLoadGeneration;
    private final List<File> rowFiles = new ArrayList<>();
    private final List<FileListAdapter.Row> displayRows = new ArrayList<>();
    private File pendingFocusDirectory;
    private File lastRenderedDirectory;
    private File lastRenderedArchiveFile;
    private String lastRenderedQuery = "";
    private boolean lastRenderedTreeView;
    private boolean hasRenderedOnce;
    private final ExecutorService archiveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicInteger renderGeneration = new java.util.concurrent.atomic.AtomicInteger(
            0);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private DocumentFile restrictedFolderRoot;
    private DocumentFile restrictedFolderDirectory;
    private final List<DocumentFile> restrictedFolderStack = new ArrayList<>();
    private File pendingShizukuRestrictedFolder;
    private String grouping = GROUP_NONE;
    private String sorting = SORT_NAME;
    private String searchQuery = "";
    private boolean foldersFirst = true;
    private boolean showHiddenFiles = false;
    private boolean darkTheme;
    private long lastBackPressedAt = -1;
    private String pendingHttpSharePath;
    private boolean pendingFtpShare;
    private File pendingInstallFile;
    private boolean operationNotificationPermissionRequested;
    private boolean operationNotificationPermissionPending;
    private String activeOperationTitle;
    private String activeOperationFile;
    private int activeOperationPercent = -1;
    private long activeOperationSpeedBytesPerSecond;
    private boolean shareStatusReceiverRegistered;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        toast("Shizuku access granted");
                        File pending = pendingShizukuRestrictedFolder;
                        pendingShizukuRestrictedFolder = null;
                        if (pending != null) {
                            showShizukuRestrictedFolder(pending);
                        }
                    } else {
                        pendingShizukuRestrictedFolder = null;
                        toast("Shizuku access was denied");
                    }
                }
            };
    private final BroadcastReceiver shareStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateHttpShareStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        String restrictedUri = preferences.getString(PREF_RESTRICTED_FOLDER_URI, null);
        if (restrictedUri != null) {
            try {
                restrictedFolderRoot = DocumentFile.fromTreeUri(this, Uri.parse(restrictedUri));
                restrictedFolderDirectory = restrictedFolderRoot;
            } catch (RuntimeException ignored) {
                restrictedFolderRoot = null;
            }
        }
        darkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(darkTheme
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        grouping = preferences.getString(PREF_GROUPING, GROUP_NONE);
        sorting = preferences.getString(PREF_SORTING, SORT_NAME);
        foldersFirst = preferences.getBoolean(PREF_FOLDERS_FIRST, true);
        treeViewEnabled = preferences.getBoolean(PREF_TREE_VIEW, true);
        animationsEnabled = preferences.getBoolean(PREF_ANIMATIONS, true);
        showHiddenFiles = preferences.getBoolean(PREF_SHOW_HIDDEN, false);

        fileList = findViewById(R.id.fileList);
        fileLayoutManager = new LinearLayoutManager(this);
        fileList.setLayoutManager(fileLayoutManager);
        fileItemAnimator = createFileItemAnimator();
        adapter = new FileListAdapter(this);
        adapter.setOnChevronClickListener(this::toggleExpandedFolder);
        adapter.setOnItemClickListener(this::handleFileClick);
        adapter.setOnItemLongClickListener(this::handleFileLongClick);
        adapter.setAnimationsEnabled(animationsEnabled);
        fileList.setItemAnimator(animationsEnabled ? fileItemAnimator : null);
        fileList.setAdapter(adapter);

        pathInput = findViewById(R.id.pathInput);
        searchInput = findViewById(R.id.searchInput);
        httpShareStatus = findViewById(R.id.httpShareStatus);
        stopHttpShareButton = findViewById(R.id.stopHttpShareButton);
        copyHttpShareUrlButton = findViewById(R.id.copyHttpShareUrlButton);
        homeDirectory = Environment.getExternalStorageDirectory();
        currentDirectory = homeDirectory;
        if (savedInstanceState != null) {
            String savedPath = savedInstanceState.getString("current_directory");
            if (savedPath != null) {
                File savedDirectory = new File(savedPath);
                if (savedDirectory.isDirectory()) {
                    currentDirectory = savedDirectory;
                }
            }
            ArrayList<String> savedExpanded = savedInstanceState.getStringArrayList("expanded_paths");
            if (savedExpanded != null) {
                expandedPaths.addAll(savedExpanded);
            }
            ArrayList<String> savedStack = savedInstanceState.getStringArrayList("expansion_stack");
            if (savedStack != null) {
                for (String path : savedStack) {
                    if (expandedPaths.contains(path)) {
                        File folder = new File(path);
                        expansionStack.remove(folder);
                        expansionStack.add(folder);
                    }
                }
            }
            String savedClicked = savedInstanceState.getString("last_clicked_directory");
            if (savedClicked != null) {
                lastClickedDirectory = new File(savedClicked);
            }
        }

        findViewById(R.id.settingsButton).setOnClickListener(this::showSettingsMenu);
        findViewById(R.id.drivesButton).setOnClickListener(view -> goToStorageDevices());
        findViewById(R.id.goButton).setOnClickListener(view -> goToPath());
        findViewById(R.id.homeButton).setOnClickListener(view -> goHome());
        findViewById(R.id.upButton).setOnClickListener(view -> goUp());
        findViewById(R.id.createButton).setOnClickListener(this::showCreateMenu);
        stopHttpShareButton.setOnClickListener(view -> stopHttpShare());
        copyHttpShareUrlButton.setOnClickListener(view -> copyHttpShareUrl());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (archiveBrowserFile != null) {
                    leaveArchiveBrowser();
                    showFiles();
                    return;
                }
                if (treeViewEnabled && !expandedPaths.isEmpty()) {
                    goUp();
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if (lastBackPressedAt >= 0
                        && now - lastBackPressedAt <= DOUBLE_BACK_EXIT_WINDOW_MS) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                lastBackPressedAt = now;
                if (currentDirectory.getParentFile() != null) {
                    goUp();
                } else {
                    toast("Press Back again to exit");
                }
            }
        });
        pathInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                goToPath();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                String newQuery = text.toString().trim().toLowerCase(Locale.ROOT);
                if (!newQuery.equals(searchQuery)) {
                    selectedFiles.clear();
                    searchQuery = newQuery;
                }
                renderFiles();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });
        if (preferences.getInt(PREF_USER_AGREEMENT_VERSION, 0) >= USER_AGREEMENT_VERSION) {
            requestStoragePermissions();
        } else {
            showUserAgreement();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shareStatusReceiverRegistered) {
            ContextCompat.registerReceiver(this, shareStatusReceiver,
                    new IntentFilter(HttpShareService.ACTION_STATUS_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            shareStatusReceiverRegistered = true;
        }
        reconcileShareState();
        updateHttpShareStatus();
    }

    @Override
    protected void onPause() {
        if (shareStatusReceiverRegistered) {
            unregisterReceiver(shareStatusReceiver);
            shareStatusReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("current_directory", currentDirectory.getAbsolutePath());
        outState.putStringArrayList("expanded_paths", new ArrayList<>(expandedPaths));
        ArrayList<String> stack = new ArrayList<>();
        for (File folder : expansionStack) {
            stack.add(folder.getAbsolutePath());
        }
        outState.putStringArrayList("expansion_stack", stack);
        if (lastClickedDirectory != null) {
            outState.putString("last_clicked_directory", lastClickedDirectory.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        renderGeneration.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(null);
        clearArchiveClipboard();
        leaveArchiveBrowser();
        if (adapter != null) {
            adapter.close();
        }
        archiveExecutor.shutdownNow();
        renderExecutor.shutdownNow();
        super.onDestroy();
    }

    private RecyclerView.ItemAnimator createFileItemAnimator() {
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(180);
        animator.setRemoveDuration(140);
        animator.setMoveDuration(200);
        animator.setChangeDuration(120);
        // Selection and thumbnail updates should not flash the whole row.
        animator.setSupportsChangeAnimations(false);
        return animator;
    }

    private void applyAnimationPreference() {
        adapter.setAnimationsEnabled(animationsEnabled);
        fileList.setItemAnimator(animationsEnabled ? fileItemAnimator : null);
    }

    private void handleFileClick(File clickedFile) {
        if (!selectedFiles.isEmpty()) {
            toggleSelectedFile(clickedFile);
            return;
        }
        if (clickedFile instanceof ArchiveEntryFile) {
            ArchiveEntryFile entry = (ArchiveEntryFile) clickedFile;
            if (entry.node.directory) {
                toggleExpandedArchiveEntry(entry.node);
            } else {
                selectedFiles.add(clickedFile);
                renderFiles();
            }
            return;
        }
        if (clickedFile.isFile() && ArchiveManager.isSupportedArchive(clickedFile)) {
            loadArchiveBrowser(clickedFile, new char[0]);
            return;
        }
        if (clickedFile.isDirectory()) {
            lastClickedDirectory = clickedFile;
            if (isRestrictedAndroidDataFolder(clickedFile)) {
                openRestrictedFolderAutomatically(clickedFile);
                return;
            }
            if (treeViewEnabled && !StorageHelper.isStorageRoot(currentDirectory)) {
                toggleExpandedFolder(clickedFile);
            } else {
                currentDirectory = clickedFile;
                selectedFiles.clear();
                showFiles();
            }
        } else {
            lastClickedDirectory = clickedFile.getParentFile();
            openFile(clickedFile);
        }
    }

    private boolean handleFileLongClick(File clickedFile) {
        if (!selectedFiles.contains(clickedFile)) {
            selectedFiles.add(clickedFile);
            updateSelection();
            renderFiles();
            return true;
        }
        updateSelection();
        showContextMenu(clickedFile);
        return true;
    }

    private void showUserAgreement() {
        ScrollView scrollView = new ScrollView(this);
        TextView agreementText = new TextView(this);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        agreementText.setPadding(padding, padding / 2, padding, padding / 2);
        agreementText.setText("Please read before using MFIle Manager.\n\n"
                + "- Storage access is used to browse and manage files you choose. Create, rename, copy, archive, extract, and delete actions are initiated by you; deletion and replacement may be difficult or impossible to undo. Keep your own backups.\n\n"
                + "- Files stay on this device unless you explicitly open/share them with another app or start HTTP/FTP sharing. When sharing is active, people on your local network with the share address and credentials may access the selected item.\n\n"
                + "- HTTP and FTP do not encrypt file contents or credentials in transit. Use sharing only on a trusted network and stop it when finished.\n\n"
                + "- You are responsible for having the right to access, copy, install, or share the files you choose, and for following applicable laws and third-party terms.\n\n"
                + "The app is provided as-is. This acknowledgement does not remove rights you have under applicable law.");
        scrollView.addView(agreementText);

        new AlertDialog.Builder(this)
                .setTitle("User agreement and responsibility")
                .setView(scrollView)
                .setPositiveButton("Agree and continue", (dialog, which) -> {
                    preferences.edit()
                            .putInt(PREF_USER_AGREEMENT_VERSION, USER_AGREEMENT_VERSION)
                            .apply();
                    requestStoragePermissions();
                })
                .setNegativeButton("Decline and exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } else {
                showFiles();
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            showFiles();
        } else {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESTRICTED_FOLDER_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    final int returnedFlags = data.getFlags();
                    int takeFlags = 0;
                    if ((returnedFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                        takeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    }
                    if ((returnedFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                        takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    }
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    preferences.edit().putString(PREF_RESTRICTED_FOLDER_URI, uri.toString()).apply();
                    restrictedFolderRoot = DocumentFile.fromTreeUri(this, uri);
                    toast("Restricted folder access granted");
                    showRestrictedFolderExplorer();
                } catch (SecurityException exception) {
                    toast("The selected folder permission could not be persisted");
                }
            } else {
                toast("Restricted folder access was cancelled");
            }
            return;
        }
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            File file = pendingInstallFile;
            pendingInstallFile = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().canRequestPackageInstalls()) {
                openApkInstaller(file);
            } else {
                toast("Allow this app to install unknown apps, then try again");
            }
            return;
        }
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                showFiles();
            } else {
                showPermissionDeniedMessage();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            boolean operationPermissionRequest = operationNotificationPermissionPending;
            operationNotificationPermissionPending = false;
            if (operationPermissionRequest && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && activeOperationTitle != null) {
                postOperationNotification(activeOperationTitle, activeOperationFile,
                            activeOperationPercent, activeOperationSpeedBytesPerSecond);
            }
            if (operationPermissionRequest && pendingHttpSharePath == null) {
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPendingShare();
            } else {
                pendingHttpSharePath = null;
                toast("Notification permission is needed to share files in the background");
            }
            return;
        }
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFiles();
            } else {
                showPermissionDeniedMessage();
            }
        }
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        SubMenu groupingMenu = popup.getMenu().addSubMenu("Group by");
        addSettingChoice(groupingMenu, "None", GROUP_NONE, grouping, PREF_GROUPING, true);
        addSettingChoice(groupingMenu, "File type", GROUP_TYPE, grouping, PREF_GROUPING, true);
        addSettingChoice(groupingMenu, "Modified date", GROUP_DATE, grouping, PREF_GROUPING, true);
        groupingMenu.setGroupCheckable(Menu.NONE, true, true);

        SubMenu sortingMenu = popup.getMenu().addSubMenu("Sort by");
        addSettingChoice(sortingMenu, "Name", SORT_NAME, sorting, PREF_SORTING, false);
        addSettingChoice(sortingMenu, "Modified date", SORT_DATE, sorting, PREF_SORTING, false);
        addSettingChoice(sortingMenu, "Extension", SORT_EXTENSION, sorting, PREF_SORTING, false);
        sortingMenu.setGroupCheckable(Menu.NONE, true, true);

        MenuItem pasteItem = popup.getMenu().add("Paste into current folder");
        pasteItem.setEnabled(!clipboardFiles.isEmpty() || !archiveClipboardEntries.isEmpty());
        pasteItem.setOnMenuItemClickListener(item -> {
            pasteClipboard(currentDirectory);
            return true;
        });

        MenuItem credentialsItem = popup.getMenu().add("File share login");
        credentialsItem.setOnMenuItemClickListener(item -> {
            showShareCredentialsDialog(null, false);
            return true;
        });

        MenuItem httpReadonlyItem = popup.getMenu().add("HTTP share — read-only");
        httpReadonlyItem.setCheckable(true)
                .setChecked(preferences.getBoolean(PREF_HTTP_READONLY, false));
        httpReadonlyItem.setOnMenuItemClickListener(item -> {
            boolean nowReadonly = !preferences.getBoolean(PREF_HTTP_READONLY, false);
            preferences.edit().putBoolean(PREF_HTTP_READONLY, nowReadonly).apply();
            if (preferences.getBoolean(PREF_HTTP_ACTIVE, false)) {
                startHttpShareService(preferences.getString(PREF_HTTP_PATH, ""));
            }
            toast(nowReadonly ? "HTTP sharing is now read-only" : "HTTP sharing is now read/write");
            return true;
        });

        MenuItem ftpReadonlyItem = popup.getMenu().add("FTP share — read-only");
        ftpReadonlyItem.setCheckable(true)
                .setChecked(preferences.getBoolean(PREF_FTP_READONLY, false));
        ftpReadonlyItem.setOnMenuItemClickListener(item -> {
            boolean nowReadonly = !preferences.getBoolean(PREF_FTP_READONLY, false);
            preferences.edit().putBoolean(PREF_FTP_READONLY, nowReadonly).apply();
            if (preferences.getBoolean(PREF_FTP_ACTIVE, false)) {
                startFtpShareService(preferences.getString(PREF_FTP_PATH, ""));
            }
            toast(nowReadonly ? "FTP sharing is now read-only" : "FTP sharing is now full-access");
            return true;
        });

        MenuItem licensesItem = popup.getMenu().add("Open source licenses");
        licensesItem.setOnMenuItemClickListener(item -> {
            OssLicensesMenuActivity.setActivityTitle("Open source licenses");
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
            return true;
        });

        MenuItem drivesItem = popup.getMenu().add("Storage devices");
        drivesItem.setOnMenuItemClickListener(item -> {
            goToStorageDevices();
            return true;
        });

        MenuItem restrictedFolderItem = popup.getMenu().add(
                restrictedFolderRoot == null ? "Grant restricted folder access"
                        : "Browse restricted folder");
        restrictedFolderItem.setOnMenuItemClickListener(item -> {
            if (restrictedFolderRoot == null) {
                requestRestrictedFolderAccess();
            } else {
                showRestrictedFolderExplorer();
            }
            return true;
        });

        MenuItem shizukuItem = popup.getMenu().add("Shizuku restricted access");
        shizukuItem.setOnMenuItemClickListener(item -> {
            showShizukuAccessDialog();
            return true;
        });

        MenuItem wirelessDebugItem = popup.getMenu().add("Wireless debugging guide");
        wirelessDebugItem.setOnMenuItemClickListener(item -> {
            showWirelessDebugGuide();
            return true;
        });

        MenuItem hiddenFilesItem = popup.getMenu().add("Show hidden files");
        hiddenFilesItem.setCheckable(true).setChecked(showHiddenFiles);
        hiddenFilesItem.setOnMenuItemClickListener(item -> {
            showHiddenFiles = !showHiddenFiles;
            preferences.edit().putBoolean(PREF_SHOW_HIDDEN, showHiddenFiles).apply();
            showFiles();
            return true;
        });

        MenuItem foldersItem = popup.getMenu().add("Folders first");
        foldersItem.setCheckable(true).setChecked(foldersFirst);
        foldersItem.setOnMenuItemClickListener(item -> {
            foldersFirst = !foldersFirst;
            preferences.edit().putBoolean(PREF_FOLDERS_FIRST, foldersFirst).apply();
            showFiles();
            return true;
        });

        MenuItem treeItem = popup.getMenu().add("Tree view");
        treeItem.setCheckable(true).setChecked(treeViewEnabled);
        treeItem.setOnMenuItemClickListener(item -> {
            treeViewEnabled = !treeViewEnabled;
            preferences.edit().putBoolean(PREF_TREE_VIEW, treeViewEnabled).apply();
            renderFiles();
            return true;
        });

        MenuItem animItem = popup.getMenu().add("Smooth animations");
        animItem.setCheckable(true).setChecked(animationsEnabled);
        animItem.setOnMenuItemClickListener(item -> {
            animationsEnabled = !animationsEnabled;
            preferences.edit().putBoolean(PREF_ANIMATIONS, animationsEnabled).apply();
            applyAnimationPreference();
            return true;
        });

        MenuItem themeItem = popup.getMenu().add("Dark theme");
        themeItem.setCheckable(true).setChecked(darkTheme);
        themeItem.setOnMenuItemClickListener(item -> {
            darkTheme = !darkTheme;
            preferences.edit().putBoolean(PREF_DARK_THEME, darkTheme).apply();
            AppCompatDelegate.setDefaultNightMode(darkTheme
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
            return true;
        });
        popup.show();
    }

    private void requestRestrictedFolderAccess() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary:Android"));
        }
        startActivityForResult(intent, RESTRICTED_FOLDER_REQUEST_CODE);
    }

    private void showRestrictedFolderExplorer() {
        if (restrictedFolderRoot == null) {
            requestRestrictedFolderAccess();
            return;
        }
        restrictedFolderStack.clear();
        restrictedFolderDirectory = restrictedFolderRoot;
        showRestrictedFolderDirectory();
    }

    private void showRestrictedFolderDirectory() {
        DocumentFile[] children;
        try {
            children = restrictedFolderDirectory.listFiles();
        } catch (RuntimeException exception) {
            toast("Restricted folder could not be read");
            return;
        }
        Arrays.sort(children, Comparator.comparing(
                child -> child.getName() == null ? "" : child.getName(),
                String.CASE_INSENSITIVE_ORDER));

        List<String> labels = new ArrayList<>();
        for (DocumentFile child : children) {
            if (!showHiddenFiles && child.getName() != null && child.getName().startsWith(".")) {
                continue;
            }
            labels.add((child.isDirectory() ? "[Folder] " : "[File] ")
                    + (child.getName() == null ? "Unnamed" : child.getName()));
        }
        if (labels.isEmpty()) {
            labels.add("(empty folder)");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(restrictedFolderDirectory.getName() == null
                        ? "Restricted folder" : restrictedFolderDirectory.getName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= children.length) {
                        return;
                    }
                    DocumentFile selected = null;
                    int visibleIndex = 0;
                    for (DocumentFile child : children) {
                        if (!showHiddenFiles && child.getName() != null
                                && child.getName().startsWith(".")) {
                            continue;
                        }
                        if (visibleIndex == which) {
                            selected = child;
                            break;
                        }
                        visibleIndex++;
                    }
                    if (selected == null) {
                        return;
                    }
                    if (selected.isDirectory()) {
                        restrictedFolderStack.add(restrictedFolderDirectory);
                        restrictedFolderDirectory = selected;
                        dialog.dismiss();
                        showRestrictedFolderDirectory();
                    } else {
                        openRestrictedDocument(selected);
                    }
                })
                .setNeutralButton("Change folder", (dialog, which) ->
                        requestRestrictedFolderAccess())
                .setPositiveButton("Close", null);
        if (!restrictedFolderStack.isEmpty()) {
            builder.setNegativeButton("Back", (dialog, which) -> {
                restrictedFolderDirectory = restrictedFolderStack.remove(
                        restrictedFolderStack.size() - 1);
                showRestrictedFolderDirectory();
            });
        }
        builder.show();
    }

    private void openRestrictedDocument(DocumentFile document) {
        if (document == null || document.getUri() == null) {
            toast("Could not open restricted file");
            return;
        }
        String mimeType = document.getType();
        if (mimeType == null) {
            mimeType = "*/*";
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(document.getUri(), mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Open restricted file"));
        } catch (ActivityNotFoundException exception) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setDataAndType(document.getUri(), mimeType);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Open or share restricted file"));
        }
    }

    private void showShizukuAccessDialog() {
        boolean binderReady = false;
        boolean granted = false;
        try {
            binderReady = Shizuku.pingBinder();
            granted = binderReady
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) {
        }

        String status = !binderReady
                ? "Shizuku is not running."
                : (granted ? "Shizuku permission is granted."
                        : "Shizuku is running, but permission is not granted.");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Shizuku restricted access")
                .setMessage(status + "\n\nShizuku provides elevated shell access for supported "
                        + "file operations. This app does not connect to Android devices or "
                        + "bypass Android permissions automatically.")
                .setNegativeButton("Close", null);
        if (!binderReady) {
            builder.setPositiveButton("Open Shizuku", (dialog, which) -> {
                try {
                    startActivity(getPackageManager().getLaunchIntentForPackage(
                            "moe.shizuku.privileged.api"));
                } catch (RuntimeException exception) {
                    toast("Install Shizuku first");
                }
            });
        } else if (!granted) {
            builder.setPositiveButton("Request permission", (dialog, which) ->
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE));
        }
        builder.show();
    }

    private void showWirelessDebugGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Wireless debugging")
                .setMessage("Wireless debugging is useful for development tools, but an app "
                        + "cannot safely obtain or reuse your ADB pairing keys without user "
                        + "action. Do not enter ADB ports or pairing codes into third-party apps.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open developer settings", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    } catch (ActivityNotFoundException exception) {
                        toast("Developer settings are unavailable");
                    }
                })
                .show();
    }

    private static final class ShizukuEntry {
        final String path;
        final String name;
        final boolean directory;

        ShizukuEntry(String path, String name, boolean directory) {
            this.path = path;
            this.name = name;
            this.directory = directory;
        }
    }

    private final List<String> shizukuFolderStack = new ArrayList<>();

    private boolean isRestrictedAndroidDataFolder(File folder) {
        if (folder == null) {
            return false;
        }
        String path = folder.getAbsolutePath();
        return path.contains("/Android/data")
                || path.contains("/Android/obb");
    }

    private boolean hasShizukuAccess() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void openRestrictedFolderAutomatically(File folder) {
        if (!hasShizukuAccess()) {
            try {
                if (!Shizuku.pingBinder()) {
                    toast("Shizuku is not running");
                    showShizukuAccessDialog();
                    return;
                }
            } catch (RuntimeException exception) {
                toast("Shizuku is not running");
                showShizukuAccessDialog();
                return;
            }
            pendingShizukuRestrictedFolder = folder;
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            return;
        }
        shizukuFolderStack.clear();
        showShizukuRestrictedFolder(folder);
    }

    private void showShizukuRestrictedFolder(File folder) {
        toast("Loading with Shizuku...");
        renderExecutor.execute(() -> {
            try {
                List<ShizukuEntry> entries = listShizukuFolder(folder.getAbsolutePath());
                mainHandler.post(() -> showShizukuFolderDialog(folder.getAbsolutePath(), entries));
            } catch (Exception exception) {
                mainHandler.post(() -> toast("Could not read folder with Shizuku"));
            }
        });
    }

    private List<ShizukuEntry> listShizukuFolder(String directory) throws Exception {
        String findCommand = "find " + shellQuote(directory)
                + " -mindepth 1 -maxdepth 1 -print0";
        byte[] output = runShizukuCommand(findCommand, true);
        List<ShizukuEntry> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index == output.length || output[index] == 0) {
                if (start < index) {
                    String path = new String(output, start, index - start, StandardCharsets.UTF_8);
                    int slash = path.lastIndexOf('/');
                    String name = slash >= 0 ? path.substring(slash + 1) : path;
                    boolean isDirectory = isShizukuDirectory(path);
                    entries.add(new ShizukuEntry(path, name, isDirectory));
                }
                start = index + 1;
            }
        }
        entries.sort((left, right) -> {
            if (left.directory != right.directory) {
                return left.directory ? -1 : 1;
            }
            return left.name.compareToIgnoreCase(right.name);
        });
        return entries;
    }

    private boolean isShizukuDirectory(String path) throws Exception {
        IRemoteProcess process = createShizukuProcess(
                "test -d " + shellQuote(path));
        try {
            return process.waitFor() == 0;
        } finally {
            process.destroy();
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private IRemoteProcess createShizukuProcess(String command) throws Exception {
        Method requireService = Shizuku.class.getDeclaredMethod("requireService");
        requireService.setAccessible(true);
        IShizukuService service = (IShizukuService) requireService.invoke(null);
        return service.newProcess(
                new String[] { "/system/bin/sh", "-c", command },
                null,
                "/");
    }

    private byte[] runShizukuCommand(String command, boolean readOutput) throws Exception {
        IRemoteProcess process = createShizukuProcess(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (readOutput) {
                ParcelFileDescriptor input = process.getInputStream();
                if (input != null) {
                    try (android.os.ParcelFileDescriptor.AutoCloseInputStream stream =
                                 new android.os.ParcelFileDescriptor.AutoCloseInputStream(input)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = stream.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }
                }
            }
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Shizuku command failed");
            }
            return output.toByteArray();
        } finally {
            process.destroy();
        }
    }

    private void showShizukuFolderDialog(String directory, List<ShizukuEntry> entries) {
        List<String> labels = new ArrayList<>();
        for (ShizukuEntry entry : entries) {
            labels.add((entry.directory ? "[Folder] " : "[File] ") + entry.name);
        }
        if (labels.isEmpty()) {
            labels.add("(empty folder)");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(directory)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= entries.size()) {
                        return;
                    }
                    ShizukuEntry entry = entries.get(which);
                    if (entry.directory) {
                        shizukuFolderStack.add(directory);
                        showShizukuRestrictedFolder(new File(entry.path));
                    } else {
                        copyShizukuFileToCache(entry);
                    }
                })
                .setPositiveButton("Close", null);
        if (!shizukuFolderStack.isEmpty()) {
            builder.setNegativeButton("Back", (dialog, which) -> {
                String parent = shizukuFolderStack.remove(shizukuFolderStack.size() - 1);
                showShizukuRestrictedFolder(new File(parent));
            });
        }
        builder.show();
    }

    private void copyShizukuFileToCache(ShizukuEntry entry) {
        toast("Copying with Shizuku...");
        renderExecutor.execute(() -> {
            File destination = new File(getCacheDir(),
                    "shizuku-" + System.currentTimeMillis() + "-" + entry.name);
            try {
                runShizukuCommand("cp " + shellQuote(entry.path)
                        + " " + shellQuote(destination.getAbsolutePath()), false);
                mainHandler.post(() -> openFile(destination));
            } catch (Exception exception) {
                mainHandler.post(() -> toast("Could not copy restricted file"));
            }
        });
    }

    private void addSettingChoice(SubMenu menu, String title, String value,
            String selectedValue, String preferenceKey, boolean isGrouping) {
        MenuItem item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, title);
        item.setCheckable(true).setChecked(value.equals(selectedValue));
        item.setOnMenuItemClickListener(selected -> {
            if (isGrouping) {
                grouping = value;
            } else {
                sorting = value;
            }
            preferences.edit().putString(preferenceKey, value).apply();
            showFiles();
            return true;
        });
    }

    private void showContextMenu(File pressedFile) {
        List<ActionItem> actions = new ArrayList<>();
        if (pressedFile instanceof ArchiveEntryFile) {
            actions.add(new ActionItem("Copy selected archive item(s)", R.drawable.ic_action_copy,
                    this::copySelected));
            actions.add(new ActionItem("Select all visible archive items", R.drawable.ic_action_check,
                    () -> {
                        for (File file : rowFiles) {
                            if (file instanceof ArchiveEntryFile) {
                                selectedFiles.add(file);
                            }
                        }
                        renderFiles();
                    }));
            actions.add(new ActionItem("Clear selection", R.drawable.ic_action_check, () -> {
                selectedFiles.clear();
                renderFiles();
            }));
            showActionDialog(actions, selectedFiles.size() + " selected");
            return;
        }
        if (selectedFiles.size() == 1 && pressedFile.isDirectory()) {
            actions.add(new ActionItem("Browse into this folder", R.drawable.ic_action_open, () -> {
                currentDirectory = pressedFile;
                selectedFiles.clear();
                showFiles();
            }));
            if (treeViewEnabled) {
                boolean isExpanded = expandedPaths.contains(pressedFile.getAbsolutePath());
                actions.add(new ActionItem(isExpanded ? "Collapse folder" : "Expand folder",
                        isExpanded ? R.drawable.ic_tree_collapse : R.drawable.ic_tree_expand,
                        () -> toggleExpandedFolder(pressedFile)));
            }
        }
        if (selectedFiles.size() == 1 && !pressedFile.isDirectory()) {
            actions.add(new ActionItem("Open with (App chooser)...", R.drawable.ic_action_open,
                    () -> openWithExternalAppChooser(pressedFile)));
            actions.add(new ActionItem("Open as...", R.drawable.ic_action_open,
                    () -> showOpenAsDialog(pressedFile)));
            actions.add(new ActionItem("Share via...", R.drawable.ic_action_share,
                    () -> shareFiles(Collections.singletonList(pressedFile))));
            actions.add(new ActionItem("Checksum (MD5 / SHA-256)", R.drawable.ic_file_code,
                    () -> showChecksumDialog(pressedFile)));
        } else if (selectedFiles.size() > 1) {
            actions.add(new ActionItem("Share " + selectedFiles.size() + " files via...",
                    R.drawable.ic_action_share,
                    () -> shareFiles(new ArrayList<>(selectedFiles))));
        }
        if (selectedFiles.size() == 1) {
            if (pressedFile.getParentFile() != null) {
                actions.add(new ActionItem("Rename", R.drawable.ic_action_rename,
                        () -> showRenameDialog(pressedFile)));
            }
            actions.add(new ActionItem("Properties", R.drawable.ic_action_info,
                    () -> showPropertiesDialog(pressedFile)));
        }
        if (selectedFiles.size() == 1 && ArchiveManager.isSupportedArchive(pressedFile)) {
            actions.add(new ActionItem("Browse archive", R.drawable.ic_file_archive,
                    () -> loadArchiveBrowser(pressedFile, new char[0])));
            actions.add(new ActionItem("Extract archive...", R.drawable.ic_file_archive,
                    () -> showArchivePasswordDialog(pressedFile, true)));
        }
        if (!selectedFiles.isEmpty()) {
            actions.add(new ActionItem("Compress " + selectedFiles.size() + " item(s)...",
                    R.drawable.ic_file_archive, this::showArchiveFormatChooser));
        }
        actions.add(new ActionItem("Share this file/folder over HTTP",
                R.drawable.ic_action_share, () -> requestHttpShare(pressedFile)));
        if (pressedFile.isDirectory()) {
            boolean ftpReadonly = preferences.getBoolean(PREF_FTP_READONLY, false);
            String ftpLabel = ftpReadonly
                    ? "Share folder over FTP (read-only)"
                    : "Share folder over FTP (full access)";
            actions.add(new ActionItem(ftpLabel,
                    R.drawable.ic_action_share, () -> requestFtpShare(pressedFile)));
        }
        actions.add(new ActionItem("Copy " + selectedFiles.size() + " item(s)",
                R.drawable.ic_action_copy, this::copySelected));
        if (!clipboardFiles.isEmpty() || !archiveClipboardEntries.isEmpty()) {
            File destination = pressedFile.isDirectory() ? pressedFile : currentDirectory;
            actions.add(new ActionItem("Paste here", R.drawable.ic_action_paste,
                    () -> pasteClipboard(destination)));
        }
        actions.add(new ActionItem("Delete " + selectedFiles.size() + " item(s)",
                R.drawable.ic_action_delete, this::deleteSelected));
        actions.add(new ActionItem("Select all visible", R.drawable.ic_action_check, () -> {
            for (File file : rowFiles) {
                if (file != null && (searchQuery.isEmpty()
                        || file.getName().toLowerCase(Locale.ROOT).contains(searchQuery))) {
                    selectedFiles.add(file);
                }
            }
            renderFiles();
        }));
        actions.add(new ActionItem("Clear selection", R.drawable.ic_action_check, () -> {
            selectedFiles.clear();
            renderFiles();
        }));

        showActionDialog(actions, selectedFiles.size() + " selected");
    }

    private void showActionDialog(List<ActionItem> actions, String title) {
        ArrayAdapter<ActionItem> actionAdapter = new ArrayAdapter<ActionItem>(this, 0, actions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView == null
                        ? LayoutInflater.from(MainActivity.this)
                                .inflate(R.layout.action_menu_item, parent, false)
                        : convertView;
                ActionItem action = getItem(position);
                ((ImageView) row.findViewById(R.id.actionIcon)).setImageResource(action.icon);
                ((TextView) row.findViewById(R.id.actionTitle)).setText(action.title);
                return row;
            }
        };
        new AlertDialog.Builder(this)
            .setTitle(title)
                .setAdapter(actionAdapter, (dialog, position) -> actions.get(position).action.run())
                .show();
    }

    private void toggleSelectedFile(File file) {
        if (!selectedFiles.remove(file)) {
            selectedFiles.add(file);
        }
        renderFiles();
    }

    private void showFiles() {
        pendingFocusDirectory = null;
        String path = archiveBrowserFile == null ? currentDirectory.getAbsolutePath()
            : archiveBrowserFile.getAbsolutePath() + "!/";
        if (!path.equals(pathInput.getText().toString())) {
            pathInput.setText(path);
        }
        // Directory listing now happens on the render thread, so opening a folder
        // never blocks the UI.
        renderFiles();
    }

    private void toggleExpandedFolder(File folder) {
        if (folder instanceof ArchiveEntryFile) {
            toggleExpandedArchiveEntry(((ArchiveEntryFile) folder).node);
            return;
        }
        String path = folder.getAbsolutePath();
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path);
            expansionStack.remove(folder);
            lastClickedDirectory = folder.getParentFile();
        } else {
            expandedPaths.add(path);
            expansionStack.remove(folder);
            expansionStack.add(folder);
            lastClickedDirectory = folder;
        }
        renderFiles();
        // Do NOT call focusDirectory here — we restore scroll position
        // inside renderFiles() so the view stays put when expanding/collapsing.
    }

    private void focusDirectory(File dir) {
        if (dir != null) {
            pendingFocusDirectory = dir;
        }
    }

    private void applyPendingFocus() {
        File focus = pendingFocusDirectory;
        if (focus == null || fileLayoutManager == null) {
            return;
        }
        for (int i = 0; i < displayRows.size(); i++) {
            File file = displayRows.get(i).getFile();
            if (file != null && file.equals(focus)) {
                fileLayoutManager.scrollToPositionWithOffset(i, 0);
                pendingFocusDirectory = null;
                return;
            }
        }
        // The target is no longer part of the committed list; do not let a stale
        // focus request scroll the list later.
        pendingFocusDirectory = null;
    }

    // ──────────────────────────────────────────────────────────
    // Async render with per-pass listFiles cache
    // ──────────────────────────────────────────────────────────

    private void renderFiles() {
        // Snapshot mutable state on the main thread
        final int gen = renderGeneration.incrementAndGet();
        final boolean treeView = treeViewEnabled;
        final File dir = currentDirectory;
        final File archiveFile = archiveBrowserFile;
        final ArchiveBrowserNode archiveRoot = archiveBrowserRoot;
        final String query = searchQuery;
        final String grp = grouping;
        final boolean showHidden = showHiddenFiles;
        final Set<String> expanded = new LinkedHashSet<>(expandedPaths);
        final Set<File> selected = new LinkedHashSet<>(selectedFiles);
        final Set<String> archiveExpanded = new LinkedHashSet<>(expandedArchivePaths);
        final Comparator<File> cmp = fileComparator();
        final SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d", Locale.getDefault());

        // A different folder, query, or view mode should not make the whole list flash.
        final boolean resetScroll = !hasRenderedOnce
                || !dir.equals(lastRenderedDirectory)
            || !sameFile(archiveFile, lastRenderedArchiveFile)
                || !query.equals(lastRenderedQuery)
                || treeView != lastRenderedTreeView;
        final boolean animateChanges = hasRenderedOnce && !resetScroll;

        renderExecutor.execute(() -> {
            // Abort if a newer render has been requested
            if (renderGeneration.get() != gen)
                return;

            // Per-pass cache: directory path → its children array
            Map<String, File[]> dirCache = new HashMap<>();

            List<FileListAdapter.Row> rows = new ArrayList<>();
            List<File> rowFilesList = new ArrayList<>();

            if (archiveFile != null && archiveRoot != null) {
                buildArchiveRows(archiveFile, archiveRoot, 0, query, archiveExpanded,
                        selected, rows, rowFilesList);
            } else if (StorageHelper.isStorageRoot(dir)) {
                List<StorageHelper.StorageDevice> devices = StorageHelper.getStorageDevices(MainActivity.this);
                for (StorageHelper.StorageDevice dev : devices) {
                    if (!query.isEmpty()
                            && !dev.label.toLowerCase(Locale.ROOT).contains(query)
                            && !dev.file.getName().toLowerCase(Locale.ROOT).contains(query)) {
                        continue;
                    }
                    boolean isExpanded = treeView && expanded.contains(dev.file.getAbsolutePath());
                    File[] children = cachedListFiles(dev.file, dirCache);
                    boolean hasChildren = children != null && children.length > 0;

                    rows.add(FileListAdapter.Row.storageDevice(
                            dev.file, dev.label, dev.getFormattedDetails(),
                            dev.usedPercent, dev.iconRes, selected.contains(dev.file),
                            0, hasChildren, isExpanded, treeView));
                    rowFilesList.add(dev.file);

                    if (treeView && isExpanded) {
                        buildTreeRows(dev.file, 1, query, expanded, selected, cmp, rows, rowFilesList,
                                dirCache, dateFmt, showHidden);
                    }
                }
            } else if (treeView) {
                buildTreeRows(dir, 0, query, expanded, selected, cmp, rows, rowFilesList,
                        dirCache, dateFmt, showHidden);
            } else {
                List<File> filtered = new ArrayList<>();
                File[] children = dir.listFiles();
                if (children != null) {
                    for (File f : children) {
                        if (!showHidden && f.getName().startsWith(".")) {
                            continue;
                        }
                        if (query.isEmpty()
                                || f.getName().toLowerCase(Locale.ROOT).contains(query)) {
                            filtered.add(f);
                        }
                    }
                }
                filtered.sort(cmp);

                if (GROUP_TYPE.equals(grp)) {
                    Map<String, List<File>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    for (File f : filtered) {
                        String key = f.isDirectory() ? "Folders"
                                : fileExtension(f).isEmpty() ? "Files without extension"
                                        : "Files ." + fileExtension(f);
                        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
                    }
                    for (Map.Entry<String, List<File>> e : groups.entrySet()) {
                        e.getValue().sort(cmp);
                        buildFlatRows(e.getKey(), e.getValue(), selected,
                                rows, rowFilesList, dirCache, dateFmt);
                    }
                } else if (GROUP_DATE.equals(grp)) {
                    Map<Long, List<File>> groups = new TreeMap<>(Collections.reverseOrder());
                    for (File f : filtered) {
                        long day = startOfDay(f.lastModified());
                        groups.computeIfAbsent(day, k -> new ArrayList<>()).add(f);
                    }
                    for (Map.Entry<Long, List<File>> e : groups.entrySet()) {
                        e.getValue().sort(cmp);
                        buildFlatRows(dateGroupName(e.getKey()), e.getValue(), selected,
                                rows, rowFilesList, dirCache, dateFmt);
                    }
                } else {
                    buildFlatRows(null, filtered, selected, rows, rowFilesList, dirCache, dateFmt);
                }
            }

            if (renderGeneration.get() != gen)
                return; // discard if superseded

            mainHandler.post(() -> {
                if (renderGeneration.get() != gen) {
                    return;
                }
                displayRows.clear();
                displayRows.addAll(rows);
                rowFiles.clear();
                rowFiles.addAll(rowFilesList);
                updateSelection();
                submitRowsWithAnimation(rows, animateChanges, () -> {
                    if (renderGeneration.get() != gen) {
                        return;
                    }
                    hasRenderedOnce = true;
                    lastRenderedDirectory = dir;
                    lastRenderedArchiveFile = archiveFile;
                    lastRenderedQuery = query;
                    lastRenderedTreeView = treeView;
                    restoreScrollAfterRender(resetScroll);
                    applyPendingFocus();
                });
            });
        });
    }

    private void submitRowsWithAnimation(List<FileListAdapter.Row> rows, boolean animateChanges,
            Runnable onCommit) {
        if (!animationsEnabled) {
            fileList.setItemAnimator(null);
            adapter.submitRows(rows, onCommit);
            return;
        }
        if (animateChanges) {
            // Only inserted/removed rows animate (for example the children of a
            // folder that was just expanded).
            fileList.setItemAnimator(fileItemAnimator);
            adapter.submitRows(rows, onCommit);
            return;
        }
        // Folder navigation, search, and the first render replace most of the list;
        // swap it without animating every row. Keep the animator detached until the
        // next update that should animate, so RecyclerView lays this swap out flat.
        fileList.setItemAnimator(null);
        adapter.submitRows(rows, onCommit);
    }

    private void restoreScrollAfterRender(boolean resetScroll) {
        if (!resetScroll || fileLayoutManager == null) {
            // RecyclerView keeps the first visible row in place when DiffUtil inserts
            // or removes rows around it, so expansions never make the list jump.
            return;
        }
        fileLayoutManager.scrollToPositionWithOffset(0, 0);
    }

    /**
     * Recursively builds tree rows into the provided lists using a shared dirCache.
     */
    private void buildTreeRows(File directory, int depth,
            String query, Set<String> expanded, Set<File> selected,
            Comparator<File> cmp,
            List<FileListAdapter.Row> rows, List<File> rowFiles,
            Map<String, File[]> dirCache,
            SimpleDateFormat dateFmt, boolean showHidden) {
        File[] children = cachedListFiles(directory, dirCache);
        if (children == null)
            return;

        List<File> childList = new ArrayList<>(Arrays.asList(children));
        childList.sort(cmp);

        for (File file : childList) {
            if (!showHidden && file.getName().startsWith(".")) {
                continue;
            }
            boolean isDir = file.isDirectory();
            boolean matchesSearch = query.isEmpty()
                    || file.getName().toLowerCase(Locale.ROOT).contains(query);
            if (!query.isEmpty() && !matchesSearch && !isDir)
                continue;

            boolean isExpanded = isDir && expanded.contains(file.getAbsolutePath());
            File[] grandChildren = isDir ? cachedListFiles(file, dirCache) : null;
            boolean hasChildren = grandChildren != null && grandChildren.length > 0;

            rows.add(FileListAdapter.Row.treeNode(
                    file,
                    fileDetailsFromCache(file, grandChildren, dateFmt),
                    selected.contains(file),
                    depth, isDir, isExpanded, hasChildren));
            rowFiles.add(file);

            if (isDir && isExpanded) {
                buildTreeRows(file, depth + 1, query, expanded, selected,
                        cmp, rows, rowFiles, dirCache, dateFmt, showHidden);
            }
        }
    }

    private void buildArchiveRows(File archive, ArchiveBrowserNode parent, int depth,
            String query, Set<String> expanded, Set<File> selected,
            List<FileListAdapter.Row> rows, List<File> files) {
        for (ArchiveBrowserNode node : parent.children.values()) {
            if (!query.isEmpty() && !node.name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            ArchiveEntryFile file = new ArchiveEntryFile(archive, node);
            boolean isExpanded = node.directory && expanded.contains(node.path);
            String details = node.directory
                    ? node.children.size() + (node.children.size() == 1 ? " item" : " items")
                    : node.size < 0 ? "File" : formatFileSize(node.size);
                rows.add(FileListAdapter.Row.archiveNode(file, details, selected.contains(file), depth,
                    node.directory, isExpanded, !node.children.isEmpty()));
            files.add(file);
            if (isExpanded) {
                buildArchiveRows(archive, node, depth + 1, query, expanded, selected, rows, files);
            }
        }
    }

    private void toggleExpandedArchiveEntry(ArchiveBrowserNode node) {
        if (expandedArchivePaths.remove(node.path)) {
            node.expanded = false;
        } else {
            expandedArchivePaths.add(node.path);
            node.expanded = true;
        }
        renderFiles();
    }

    private void leaveArchiveBrowser() {
        archiveLoadGeneration++;
        selectedFiles.removeIf(file -> file instanceof ArchiveEntryFile);
        archiveBrowserFile = null;
        archiveBrowserRoot = null;
        expandedArchivePaths.clear();
        Arrays.fill(archiveBrowserPassword, '\0');
        archiveBrowserPassword = new char[0];
        updateSelection();
    }

    private boolean sameFile(File first, File second) {
        return first == null ? second == null : first.equals(second);
    }

    private void buildFlatRows(String groupName, List<File> files, Set<File> selected,
            List<FileListAdapter.Row> rows, List<File> rowFiles,
            Map<String, File[]> dirCache, SimpleDateFormat dateFmt) {
        if (groupName != null) {
            rows.add(FileListAdapter.Row.heading(groupName));
            rowFiles.add(null);
        }
        for (File file : files) {
            File[] children = file.isDirectory() ? cachedListFiles(file, dirCache) : null;
            rows.add(FileListAdapter.Row.file(
                    file, fileDetailsFromCache(file, children, dateFmt), selected.contains(file)));
            rowFiles.add(file);
        }
    }

    /** Returns cached listFiles result, or fetches and stores it. */
    private File[] cachedListFiles(File dir, Map<String, File[]> cache) {
        String key = dir.getAbsolutePath();
        if (cache.containsKey(key))
            return cache.get(key);
        File[] result = dir.listFiles();
        cache.put(key, result);
        return result;
    }

    /**
     * Builds the detail string without calling listFiles() again — uses
     * already-fetched array.
     */
    private String fileDetailsFromCache(File file, File[] cachedChildren,
            SimpleDateFormat dateFmt) {
        String modified = dateFmt.format(new Date(file.lastModified()));
        if (file.isDirectory()) {
            String count = cachedChildren != null
                    ? cachedChildren.length + " item" + (cachedChildren.length == 1 ? "" : "s")
                    : "Folder";
            return count + "  " + modified;
        }
        return StorageHelper.formatSize(file.length()) + "  " + modified;
    }

    /** Legacy fileDetails used outside of render (e.g. file info dialog). */
    private String fileDetails(File file) {
        return fileDetailsFromCache(file,
                file.isDirectory() ? file.listFiles() : null,
                new SimpleDateFormat("MMM d", Locale.getDefault()));
    }

    private Comparator<File> fileComparator() {
        return (first, second) -> {
            if (foldersFirst) {
                int folderOrder = Boolean.compare(first.isFile(), second.isFile());
                if (folderOrder != 0) {
                    return folderOrder;
                }
            }
            int result;
            if (SORT_DATE.equals(sorting)) {
                result = Long.compare(second.lastModified(), first.lastModified());
            } else if (SORT_EXTENSION.equals(sorting)) {
                result = fileExtension(first).compareToIgnoreCase(fileExtension(second));
            } else {
                result = 0;
            }
            if (result == 0) {
                result = first.getName().compareToIgnoreCase(second.getName());
            }
            return result;
        };
    }

    private String fileExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private long startOfDay(long timestamp) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(timestamp);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    private String dateGroupName(long dayTimestamp) {
        long today = startOfDay(System.currentTimeMillis());
        Calendar yesterday = Calendar.getInstance();
        yesterday.setTimeInMillis(today);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (dayTimestamp == today) {
            return "Today";
        }
        if (dayTimestamp == yesterday.getTimeInMillis()) {
            return "Yesterday";
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(new Date(dayTimestamp));
    }

    private void goToPath() {
        String enteredPath = pathInput.getText().toString().trim();
        if (enteredPath.isEmpty()) {
            toast("Enter a folder path");
            return;
        }
        File requested = new File(enteredPath);
        if (!requested.isAbsolute()) {
            requested = new File(currentDirectory, enteredPath);
        }
        try {
            requested = requested.getCanonicalFile();
        } catch (IOException exception) {
            toast("Invalid folder path");
            return;
        }
        if (StorageHelper.isStorageRoot(requested)) {
            requested = new File(StorageHelper.STORAGE_ROOT_PATH);
        } else if (!requested.isDirectory() || !requested.canRead()) {
            toast("Folder not found or cannot be read");
            return;
        }
        leaveArchiveBrowser();
        currentDirectory = requested;
        selectedFiles.clear();
        pendingFocusDirectory = null;
        searchInput.setText("");
        showFiles();
        scrollToTop();
    }

    private void goToStorageDevices() {
        leaveArchiveBrowser();
        currentDirectory = new File(StorageHelper.STORAGE_ROOT_PATH);
        selectedFiles.clear();
        pendingFocusDirectory = null;
        searchInput.setText("");
        showFiles();
        scrollToTop();
    }

    private void goUp() {
        if (archiveBrowserFile != null) {
            leaveArchiveBrowser();
            showFiles();
            scrollToTop();
            return;
        }
        if (treeViewEnabled) {
            File targetToCollapse = null;
            if (lastClickedDirectory != null && expandedPaths.contains(lastClickedDirectory.getAbsolutePath())) {
                targetToCollapse = lastClickedDirectory;
            } else {
                for (int i = expansionStack.size() - 1; i >= 0; i--) {
                    File candidate = expansionStack.get(i);
                    if (expandedPaths.contains(candidate.getAbsolutePath())) {
                        targetToCollapse = candidate;
                        break;
                    }
                }
            }

            if (targetToCollapse != null) {
                expandedPaths.remove(targetToCollapse.getAbsolutePath());
                expansionStack.remove(targetToCollapse);
                lastClickedDirectory = targetToCollapse.getParentFile();
                renderFiles();
                focusDirectory(targetToCollapse);
                return;
            }
        }

        File previousDir = currentDirectory;
        File parent = currentDirectory.getParentFile();

        // The parent of default path (/storage/emulated/0) or /storage/emulated is /storage
        if (currentDirectory.equals(homeDirectory)
                || "/storage/emulated".equals(currentDirectory.getAbsolutePath())) {
            parent = new File(StorageHelper.STORAGE_ROOT_PATH);
        } else if (parent != null && "/storage/emulated".equals(parent.getAbsolutePath())) {
            parent = new File(StorageHelper.STORAGE_ROOT_PATH);
        }

        if (parent != null && (parent.canRead() || StorageHelper.isStorageRoot(parent))) {
            currentDirectory = parent;
            lastClickedDirectory = parent;
            selectedFiles.clear();
            showFiles();
            focusDirectory(previousDir);
        } else if (parent != null) {
            toast("Cannot read parent folder");
        }
    }

    private void goHome() {
        leaveArchiveBrowser();
        currentDirectory = homeDirectory;
        selectedFiles.clear();
        pendingFocusDirectory = null;
        searchInput.setText("");
        showFiles();
        scrollToTop();
    }

    private void scrollToTop() {
        if (fileLayoutManager != null) {
            fileLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private void showCreateMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("New folder").setOnMenuItemClickListener(item -> {
            showCreateDialog(true);
            return true;
        });
        popup.getMenu().add("New empty file").setOnMenuItemClickListener(item -> {
            showCreateDialog(false);
            return true;
        });
        popup.show();
    }

    private void showCreateDialog(boolean createDirectory) {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint(createDirectory ? "Folder name" : "File name");
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(createDirectory ? "New folder" : "New empty file")
                .setView(nameInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid name without path separators");
                        return;
                    }
                    File destination = new File(currentDirectory, name);
                    if (destination.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    if (!currentDirectory.canWrite()) {
                        toast("Current folder cannot be changed");
                        return;
                    }
                    boolean created;
                    try {
                        created = createDirectory ? destination.mkdir() : destination.createNewFile();
                    } catch (IOException exception) {
                        created = false;
                    }
                    if (!created) {
                        toast(createDirectory ? "Could not create folder" : "Could not create file");
                        return;
                    }
                    dialog.dismiss();
                    showFiles();
                    toast(createDirectory ? "Folder created" : "File created");
                }));
        dialog.show();
    }

    private void showRenameDialog(File source) {
        File parent = source.getParentFile();
        if (parent == null || !parent.canWrite()) {
            toast("This item cannot be renamed");
            return;
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(source.getName());
        nameInput.setSelectAllOnFocus(true);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename item")
                .setView(nameInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid name without path separators");
                        return;
                    }
                    if (source.getName().equals(name)) {
                        dialog.dismiss();
                        return;
                    }
                    File destination = new File(parent, name);
                    if (destination.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    if (!source.renameTo(destination)) {
                        toast("Could not rename item");
                        return;
                    }
                    for (int index = 0; index < clipboardFiles.size(); index++) {
                        if (clipboardFiles.get(index).equals(source)) {
                            clipboardFiles.set(index, destination);
                        }
                    }
                    selectedFiles.clear();
                    dialog.dismiss();
                    showFiles();
                    toast("Item renamed");
                }));
        dialog.show();
    }

    private boolean isValidLocalName(String name) {
        return !name.isEmpty() && !".".equals(name) && !"..".equals(name)
                && !name.contains("/") && !name.contains("\\") && name.indexOf('\0') < 0;
    }

    private void showPropertiesDialog(File file) {
        String type = file.isDirectory() ? "Folder"
                : fileExtension(file).isEmpty() ? "File" : fileExtension(file).toUpperCase(Locale.ROOT);
        String size = file.isDirectory()
                ? "Calculating..."
                : StorageHelper.formatSize(file.length());
        String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(file.lastModified()));
        String details = "Type: " + type
                + "\nSize: " + size
                + "\nModified: " + modified
                + "\nReadable: " + (file.canRead() ? "Yes" : "No")
                + "\nWritable: " + (file.canWrite() ? "Yes" : "No")
                + "\n\nPath: " + file.getAbsolutePath();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show();

        if (file.isDirectory()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                long[] stats = calculateFolderStats(file);
                String updatedSize = StorageHelper.formatSize(stats[0])
                        + " (" + stats[1] + " file" + (stats[1] == 1 ? "" : "s")
                        + ", " + stats[2] + " folder" + (stats[2] == 1 ? "" : "s") + ")";
                mainHandler.post(() -> {
                    if (dialog.isShowing()) {
                        String updatedDetails = "Type: " + type
                                + "\nSize: " + updatedSize
                                + "\nModified: " + modified
                                + "\nReadable: " + (file.canRead() ? "Yes" : "No")
                                + "\nWritable: " + (file.canWrite() ? "Yes" : "No")
                                + "\n\nPath: " + file.getAbsolutePath();
                        dialog.setMessage(updatedDetails);
                    }
                });
            });
        }
    }

    private long[] calculateFolderStats(File dir) {
        long totalBytes = 0;
        long files = 0;
        long folders = 0;
        List<File> queue = new ArrayList<>();
        queue.add(dir);
        int maxItems = 50000;
        while (!queue.isEmpty() && (files + folders) < maxItems) {
            File current = queue.remove(queue.size() - 1);
            File[] list = current.listFiles();
            if (list != null) {
                for (File child : list) {
                    if (child.isDirectory()) {
                        folders++;
                        queue.add(child);
                    } else {
                        files++;
                        totalBytes += child.length();
                    }
                }
            }
        }
        return new long[]{totalBytes, files, folders};
    }

    private void shareFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        List<File> fileOnlyList = new ArrayList<>();
        for (File f : files) {
            if (f != null && f.isFile()) {
                fileOnlyList.add(f);
            }
        }
        if (fileOnlyList.isEmpty()) {
            toast("Folders cannot be shared directly. Compress to ZIP first.");
            return;
        }
        try {
            Intent intent = new Intent();
            if (fileOnlyList.size() == 1) {
                File f = fileOnlyList.get(0);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                intent.setAction(Intent.ACTION_SEND);
                String ext = fileExtension(f);
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mimeType == null) mimeType = "*/*";
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            } else {
                ArrayList<Uri> uris = new ArrayList<>();
                for (File f : fileOnlyList) {
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
                }
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("*/*");
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share via"));
        } catch (Exception e) {
            toast("Unable to share: " + e.getMessage());
        }
    }

    private void showChecksumDialog(File file) {
        if (!file.isFile() || !file.canRead()) {
            toast("Cannot read file for checksum");
            return;
        }
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Calculating Checksum")
                .setMessage("Computing MD5 and SHA-256 for\n" + file.getName() + "...")
                .setCancelable(false)
                .show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.security.MessageDigest md5Digest = java.security.MessageDigest.getInstance("MD5");
                java.security.MessageDigest sha256Digest = java.security.MessageDigest.getInstance("SHA-256");

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        md5Digest.update(buffer, 0, read);
                        sha256Digest.update(buffer, 0, read);
                    }
                }

                String md5 = bytesToHex(md5Digest.digest());
                String sha256 = bytesToHex(sha256Digest.digest());

                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    String msg = "File: " + file.getName()
                            + "\nSize: " + StorageHelper.formatSize(file.length())
                            + "\n\nMD5:\n" + md5
                            + "\n\nSHA-256:\n" + sha256;

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("File Checksums")
                            .setMessage(msg)
                            .setPositiveButton("Copy MD5", (d, w) -> {
                                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                cb.setPrimaryClip(ClipData.newPlainText("MD5", md5));
                                toast("MD5 copied to clipboard");
                            })
                            .setNeutralButton("Copy SHA-256", (d, w) -> {
                                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                cb.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256));
                                toast("SHA-256 copied to clipboard");
                            })
                            .setNegativeButton("Close", null)
                            .show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    toast("Failed to calculate checksum: " + e.getMessage());
                });
            }
        });
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void openWithExternalAppChooser(File file) {
        String ext = fileExtension(file);
        String mimeType = "apk".equals(ext)
                ? "application/vnd.android.package-archive"
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mimeType == null) {
            mimeType = "*/*";
        }
        openFileWithMimeType(file, mimeType);
    }

    private String formatFileSize(long bytes) {
        return StorageHelper.formatSize(bytes);
    }

    private void showArchiveFormatChooser() {
        List<File> sources = getNormalizedSelection();
        if (sources.isEmpty()) {
            toast("Select files or folders to compress");
            return;
        }
        List<ArchiveManager.Format> formats = new ArrayList<>(Arrays.asList(
                ArchiveManager.Format.ZIP, ArchiveManager.Format.SEVEN_ZIP,
                ArchiveManager.Format.TAR_GZ, ArchiveManager.Format.TAR));
        List<String> labels = new ArrayList<>(Arrays.asList("ZIP", "7z", "TAR.GZ", "TAR"));
        if (sources.size() == 1 && sources.get(0).isFile()) {
            formats.add(ArchiveManager.Format.GZIP);
            labels.add("GZIP (single file)");
        }
        new AlertDialog.Builder(this)
                .setTitle("Compress " + sources.size() + " item(s) as")
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> showArchiveCreateDialog(sources, formats.get(which)))
                .show();
    }

    private String archiveBaseName(File source) {
        if (ArchiveManager.isSupportedArchive(source)) {
            return ArchiveManager.removeArchiveExtension(source);
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return source.isFile() && dot > 0 ? name.substring(0, dot) : name;
    }

    private void showArchiveCreateDialog(List<File> sources, ArchiveManager.Format format) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        String baseName = sources.size() == 1
                ? archiveBaseName(sources.get(0))
                : currentDirectory.getName();
        if (baseName.isEmpty()) {
            baseName = "Archive";
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(baseName);
        nameInput.setHint("Archive name");
        fields.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText passwordInput = null;
        if (format == ArchiveManager.Format.ZIP || format == ArchiveManager.Format.SEVEN_ZIP) {
            passwordInput = new EditText(this);
            passwordInput.setSingleLine(true);
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInput.setHint("Optional archive password");
            fields.addView(passwordInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        EditText archivePasswordInput = passwordInput;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create " + format.name().replace('_', '.'))
                .setMessage(format == ArchiveManager.Format.ZIP || format == ArchiveManager.Format.SEVEN_ZIP
                        ? "Password protection uses AES encryption. Leave blank for no password."
                        : "This format does not support password protection.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid archive name");
                        return;
                    }
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    String extension = format.extension();
                    String outputName = lowerName.endsWith(extension)
                            ? name
                            : name + extension;
                    File output = new File(currentDirectory, outputName);
                    if (output.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    char[] password = archivePasswordInput == null ? new char[0]
                            : archivePasswordInput.getText().toString().toCharArray();
                    dialog.dismiss();
                    runArchiveOperation("Creating " + output.getName(), progress -> {
                        try {
                            ArchiveManager.Result result = ArchiveManager.create(sources,
                                    output, format, password.length == 0 ? null : password);
                            double saved = result.savedPercent();
                            String ratio = saved >= 0
                                    ? String.format(Locale.getDefault(), "%.1f%% smaller", saved)
                                    : String.format(Locale.getDefault(), "%.1f%% larger", -saved);
                            return "Created " + output.getName() + "\n"
                                    + result.fileCount + " file(s)\nOriginal: "
                                    + formatFileSize(result.originalBytes) + "\nArchive: "
                                    + formatFileSize(result.archiveBytes) + "\n" + ratio;
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }, true);
                }));
        dialog.show();
    }

    private void showArchivePasswordDialog(File archive, boolean extract) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        EditText destinationInput = null;
        if (extract) {
            destinationInput = new EditText(this);
            destinationInput.setSingleLine(true);
            destinationInput.setText(ArchiveManager.removeArchiveExtension(archive));
            destinationInput.setHint("Extract to folder");
            fields.addView(destinationInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Password (leave blank if none)");
        fields.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText targetInput = destinationInput;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(extract ? "Extract archive" : "Browse archive")
                .setMessage("RAR creation is not supported; RAR archives can be browsed and extracted.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(extract ? "Extract" : "Browse", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    File destination = null;
                    if (extract) {
                        String name = targetInput.getText().toString().trim();
                        if (!isValidLocalName(name)) {
                            targetInput.setError("Enter a valid folder name");
                            return;
                        }
                        destination = new File(currentDirectory, name);
                        if (destination.exists()) {
                            targetInput.setError("That folder already exists");
                            return;
                        }
                    }
                    File extractFolder = destination;
                    char[] password = passwordInput.getText().toString().toCharArray();
                    dialog.dismiss();
                    if (extract) {
                        runArchiveOperation("Extracting " + archive.getName(), progress -> {
                            try {
                                progress.update("Scanning archive", 0, 0, 0, 0);
                                List<ArchiveManager.EntryInfo> entries = ArchiveManager.listEntries(
                                        archive, password.length == 0 ? null : password);
                                int totalFiles = 0;
                                for (ArchiveManager.EntryInfo entry : entries) {
                                    if (!entry.directory) {
                                        totalFiles++;
                                    }
                                }
                                int extractionTotal = totalFiles;
                                int count = ArchiveManager.extract(archive, extractFolder,
                                        password.length == 0 ? null : password,
                                        (fileName, completed, bytesCopied, totalBytes) ->
                                            progress.update(fileName, completed,
                                                extractionTotal, bytesCopied, totalBytes));
                                return "Extracted " + count + " file(s) to "
                                        + extractFolder.getAbsolutePath();
                            } finally {
                                Arrays.fill(password, '\0');
                            }
                        }, true);
                    } else {
                        loadArchiveBrowser(archive, password);
                    }
                }));
        dialog.show();
    }

    private void loadArchiveBrowser(File archive, char[] password) {
        leaveArchiveBrowser();
        int loadGeneration = ++archiveLoadGeneration;
        toast("Reading " + archive.getName());

        archiveExecutor.execute(() -> {
            ArchiveBrowserNode root = new ArchiveBrowserNode("", "", true, -1, -1);
            Exception failure = null;
            try {
                List<ArchiveManager.EntryInfo> entries = ArchiveManager.listEntries(archive,
                        password.length == 0 ? null : password);
                for (ArchiveManager.EntryInfo entry : entries) {
                    addArchiveBrowserEntry(root, entry);
                }
            } catch (Exception exception) {
                failure = exception;
            }
            Exception loadFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || loadGeneration != archiveLoadGeneration) {
                    Arrays.fill(password, '\0');
                    return;
                }
                if (loadFailure != null) {
                    boolean needsPassword = password.length == 0;
                    Arrays.fill(password, '\0');
                    if (needsPassword) {
                        showArchivePasswordDialog(archive, false);
                    } else {
                        String message = loadFailure.getMessage();
                        toast(message == null ? "The archive could not be read." : message);
                    }
                    return;
                }
                archiveBrowserFile = archive;
                archiveBrowserRoot = root;
                archiveBrowserPassword = password.clone();
                Arrays.fill(password, '\0');
                selectedFiles.clear();
                expandedArchivePaths.clear();
                searchInput.setText("");
                pathInput.setText(archive.getAbsolutePath() + "!/");
                renderFiles();
            });
        });
    }

    private void showArchiveBrowserDialog(File archive,
            List<ArchiveManager.EntryInfo> entries, char[] password) {
        ArchiveBrowserNode root = new ArchiveBrowserNode("", "", true, -1, -1);
        for (ArchiveManager.EntryInfo entry : entries) {
            addArchiveBrowserEntry(root, entry);
        }

        List<ArchiveBrowserNode> visible = new ArrayList<>();
        flattenArchiveBrowser(root, visible);
        final android.widget.Button[] copyButton = { null };
        final Runnable updateCopyButton = () -> {
            android.widget.Button button = copyButton[0];
            if (button != null) {
                List<ArchiveBrowserNode> selected = new ArrayList<>();
                collectSelectedArchiveNodes(root, selected);
                button.setText("Copy selected (" + selected.size() + ")");
                button.setEnabled(!selected.isEmpty());
            }
        };
        BaseAdapter rows = new BaseAdapter() {
            @Override
            public int getCount() {
                return visible.size();
            }

            @Override
            public ArchiveBrowserNode getItem(int position) {
                return visible.get(position);
            }

            @Override
            public long getItemId(int position) {
                return getItem(position).path.hashCode();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                TextView labelView;
                CheckBox checkBox;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                    labelView = (TextView) row.getChildAt(0);
                    checkBox = (CheckBox) row.getChildAt(1);
                } else {
                    row = new LinearLayout(MainActivity.this);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    int rowPadding = Math.round(4 * getResources().getDisplayMetrics().density);
                    row.setPadding(rowPadding, 0, rowPadding, 0);
                    labelView = new TextView(MainActivity.this);
                    labelView.setTextSize(13);
                    labelView.setSingleLine(true);
                    labelView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    checkBox = new CheckBox(MainActivity.this);
                    row.addView(labelView, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(checkBox, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                ArchiveBrowserNode node = getItem(position);
                StringBuilder label = new StringBuilder();
                for (int depth = 0; depth < node.depth; depth++) {
                    label.append("   ");
                }
                if (node.directory) {
                    label.append(node.expanded ? "[-] " : "[+] ");
                } else {
                    label.append("    ");
                }
                label.append(node.name);
                if (!node.directory && node.size >= 0) {
                    label.append("  ").append(formatFileSize(node.size));
                }
                labelView.setText(label.toString());
                row.setMinimumHeight(Math.round(34 * getResources().getDisplayMetrics().density));
                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(node.selected);
                checkBox.setOnCheckedChangeListener((button, checked) -> {
                    node.selected = checked;
                    updateCopyButton.run();
                });
                return row;
            }
        };

        ListView listView = new ListView(this);
        listView.setAdapter(rows);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ArchiveBrowserNode node = visible.get(position);
            if (node.directory) {
                node.expanded = !node.expanded;
                visible.clear();
                flattenArchiveBrowser(root, visible);
                rows.notifyDataSetChanged();
            } else {
                node.selected = !node.selected;
                rows.notifyDataSetChanged();
                updateCopyButton.run();
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            ArchiveBrowserNode node = visible.get(position);
            node.selected = !node.selected;
            rows.notifyDataSetChanged();
            updateCopyButton.run();
            return true;
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(400 * getResources().getDisplayMetrics().density)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(archive.getName())
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy selected", null)
                .create();
        final boolean[] copied = { false };
        dialog.setOnShowListener(ignored -> {
            android.widget.Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            copyButton[0] = button;
            button.setOnClickListener(view -> {
                List<ArchiveBrowserNode> selected = new ArrayList<>();
                collectSelectedArchiveNodes(root, selected);
                List<String> paths = normalizedArchiveSelections(selected);
                if (paths.isEmpty()) {
                    return;
                }
                clearArchiveClipboard();
                clipboardFiles.clear();
                archiveClipboardFile = archive;
                archiveClipboardEntries.addAll(paths);
                archiveClipboardPassword = password.clone();
                Arrays.fill(password, '\0');
                copied[0] = true;
                selectedFiles.clear();
                updateSelection();
                toast("Copied " + paths.size() + " archive item(s)");
                dialog.dismiss();
            });
            updateCopyButton.run();
        });
        dialog.setOnDismissListener(ignored -> {
            if (!copied[0]) {
                Arrays.fill(password, '\0');
            }
        });
        dialog.show();
    }

    private void addArchiveBrowserEntry(ArchiveBrowserNode root,
            ArchiveManager.EntryInfo entry) {
        String normalized = entry.name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] parts = normalized.split("/");
        ArchiveBrowserNode parent = root;
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part)) {
                return;
            }
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(part);
            boolean directory = index < parts.length - 1 || entry.directory;
            ArchiveBrowserNode child = parent.children.get(part);
            if (child == null) {
                child = new ArchiveBrowserNode(part, path.toString(), directory,
                        parent.depth + 1, directory ? -1 : entry.unpackedSize);
                child.parent = parent;
                parent.children.put(part, child);
            } else if (directory) {
                child.directory = true;
            } else {
                child.size = entry.unpackedSize;
            }
            parent = child;
        }
    }

    private void flattenArchiveBrowser(ArchiveBrowserNode parent,
            List<ArchiveBrowserNode> visible) {
        for (ArchiveBrowserNode child : parent.children.values()) {
            visible.add(child);
            if (child.directory && child.expanded) {
                flattenArchiveBrowser(child, visible);
            }
        }
    }

    private void collectSelectedArchiveNodes(ArchiveBrowserNode parent,
            List<ArchiveBrowserNode> selected) {
        for (ArchiveBrowserNode child : parent.children.values()) {
            if (child.selected) {
                selected.add(child);
            }
            collectSelectedArchiveNodes(child, selected);
        }
    }

    private List<String> normalizedArchiveSelections(List<ArchiveBrowserNode> selected) {
        selected.sort(Comparator.comparingInt(node -> node.path.length()));
        List<ArchiveBrowserNode> accepted = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (ArchiveBrowserNode candidate : selected) {
            boolean nested = false;
            for (ArchiveBrowserNode parent : accepted) {
                if (parent.directory && (candidate.path.equals(parent.path)
                        || candidate.path.startsWith(parent.path + "/"))) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                accepted.add(candidate);
                paths.add(candidate.path + (candidate.directory ? "/" : ""));
            }
        }
        return paths;
    }

    private String describeArchive(File archive, List<ArchiveManager.EntryInfo> entries) {
        StringBuilder description = new StringBuilder();
        long unpackedBytes = 0;
        int shown = Math.min(entries.size(), 250);
        for (int index = 0; index < entries.size(); index++) {
            ArchiveManager.EntryInfo entry = entries.get(index);
            if (entry.unpackedSize >= 0) {
                unpackedBytes += entry.unpackedSize;
            }
            if (index < shown) {
                description.append(entry.directory ? "[Folder] " : "[File] ")
                        .append(entry.name);
                if (!entry.directory && entry.unpackedSize >= 0) {
                    description.append("  (").append(formatFileSize(entry.unpackedSize)).append(')');
                }
                description.append('\n');
            }
        }
        if (entries.size() > shown) {
            description.append("... and ").append(entries.size() - shown).append(" more entrie(s)\n");
        }
        description.append("\nEntries: ").append(entries.size())
                .append("\nUnpacked: ").append(formatFileSize(unpackedBytes))
                .append("\nArchive: ").append(formatFileSize(archive.length()));
        if (unpackedBytes > 0) {
            double ratio = (1.0 - (double) archive.length() / unpackedBytes) * 100.0;
            description.append("\nRatio: ").append(String.format(Locale.getDefault(), "%.1f%%", ratio));
        }
        return description.toString();
    }

    private void runArchiveOperation(String title, ArchiveOperation operation, boolean refresh) {
        runArchiveOperation(title, operation, refresh, true, null);
    }

    private void runArchiveOperation(String title, ArchiveOperation operation, boolean refresh,
            boolean showResult, Runnable onComplete) {
        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        TextView progressText = new TextView(this);
        progressText.setText("Preparing...");
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.VERTICAL);
        progressContainer.setPadding(48, 24, 48, 8);
        progressContainer.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams progressTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressTextParams.topMargin = 12;
        progressContainer.addView(progressText, progressTextParams);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(progressContainer)
                .setCancelable(false)
                .create();
        progressDialog.show();
        requestOperationNotificationPermission();
        postOperationNotification(title, "Preparing...", -1, 0);
        long[] lastProgressUpdateAt = { 0 };
        String[] sampledFile = { null };
        long[] sampledAt = { 0 };
        long[] sampledBytes = { 0 };
        long[] speedBytesPerSecond = { 0 };
        archiveExecutor.execute(() -> {
            String result = null;
            Exception failure = null;
            try {
                result = operation.run((currentFile, completed, total,
                        fileBytesCopied, fileTotalBytes) -> {
                    long now = SystemClock.elapsedRealtime();
                    if (!currentFile.equals(sampledFile[0])) {
                        sampledFile[0] = currentFile;
                        sampledAt[0] = now;
                        sampledBytes[0] = fileBytesCopied;
                        speedBytesPerSecond[0] = 0;
                    } else if (fileTotalBytes > 0 && now > sampledAt[0]) {
                        long elapsed = now - sampledAt[0];
                        long deltaBytes = Math.max(0, fileBytesCopied - sampledBytes[0]);
                        speedBytesPerSecond[0] = deltaBytes * 1000 / elapsed;
                        sampledAt[0] = now;
                        sampledBytes[0] = fileBytesCopied;
                    }
                    if (lastProgressUpdateAt[0] == 0
                            || now - lastProgressUpdateAt[0] >= 100) {
                        lastProgressUpdateAt[0] = now;
                        int percent = fileTotalBytes > 0
                                ? (int) Math.min(100, fileBytesCopied * 100 / fileTotalBytes)
                                : total > 0
                                        ? (int) Math.min(100, (long) completed * 100 / total)
                                        : -1;
                        long speed = speedBytesPerSecond[0];
                        String speedText = fileTotalBytes > 0
                            ? (speed > 0
                                ? String.format(Locale.getDefault(), "%.2f MB/s",
                                    speed / (1024.0 * 1024.0))
                                : "Measuring speed...")
                                : null;
                        String details = (percent >= 0 ? percent + "%" : "Working...")
                                + (speedText == null ? "" : " | " + speedText)
                                + "\n" + currentFile;
                        mainHandler.post(() -> {
                            if (progressDialog.isShowing()) {
                                progress.setIndeterminate(percent < 0);
                                if (percent >= 0) {
                                    progress.setProgress(percent);
                                }
                                progressText.setText(details);
                            }
                            postOperationNotification(title, details, percent, speed);
                        });
                    }
                });
            } catch (Exception exception) {
                failure = exception;
            }
            String completedResult = result;
            Exception completedFailure = failure;
            mainHandler.post(() -> {
                cancelOperationNotification();
                activeOperationTitle = null;
                activeOperationFile = null;
                activeOperationPercent = -1;
                activeOperationSpeedBytesPerSecond = 0;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progressDialog.dismiss();
                if (completedFailure != null) {
                    String message = completedFailure.getMessage();
                    new AlertDialog.Builder(this)
                            .setTitle("Operation failed")
                            .setMessage(message == null ? "The operation could not be completed." : message)
                            .setPositiveButton("OK", null)
                            .show();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }
                if (refresh) {
                    showFiles();
                }
                if (showResult) {
                    new AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(completedResult)
                            .setPositiveButton("OK", null)
                            .show();
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    private interface ArchiveOperation {
        String run(OperationProgress progress) throws Exception;
    }

    private interface OperationProgress {
        void update(String currentFile, int completed, int total,
                long fileBytesCopied, long fileTotalBytes);
    }

    private void postOperationNotification(String title, String currentFile, int percent,
            long speedBytesPerSecond) {
        activeOperationTitle = title;
        activeOperationFile = currentFile;
        activeOperationPercent = percent;
        activeOperationSpeedBytesPerSecond = speedBytesPerSecond;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && manager.getNotificationChannel(FILE_OPERATION_CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(FILE_OPERATION_CHANNEL_ID,
                    "File operations", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String notificationTitle = percent >= 0 ? title + " - " + percent + "%" : title;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this,
                FILE_OPERATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_share)
                .setContentTitle(notificationTitle)
                .setContentText(currentFile.replace('\n', ' '))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(currentFile))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, Math.max(0, percent), percent < 0);
        manager.notify(FILE_OPERATION_NOTIFICATION_ID, notification.build());
    }

    private void requestOperationNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                && !operationNotificationPermissionRequested) {
            operationNotificationPermissionRequested = true;
            operationNotificationPermissionPending = true;
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void cancelOperationNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(FILE_OPERATION_NOTIFICATION_ID);
    }

    private void copySelected() {
        if (!selectedFiles.isEmpty() && selectedFiles.iterator().next() instanceof ArchiveEntryFile) {
            copySelectedArchiveEntries();
            return;
        }
        List<File> sources = getNormalizedSelection();
        if (sources.isEmpty()) {
            toast("Select a file or folder first");
            return;
        }
        clearArchiveClipboard();
        clipboardFiles.clear();
        clipboardFiles.addAll(sources);
        selectedFiles.clear();
        renderFiles();
        toast("Copied " + clipboardFiles.size() + " item(s)");
    }

    private void copySelectedArchiveEntries() {
        List<ArchiveEntryFile> candidates = new ArrayList<>();
        for (File file : selectedFiles) {
            if (file instanceof ArchiveEntryFile) {
                candidates.add((ArchiveEntryFile) file);
            }
        }
        candidates.sort(Comparator.comparingInt(entry -> entry.node.path.length()));
        List<ArchiveEntryFile> accepted = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (ArchiveEntryFile candidate : candidates) {
            boolean nested = false;
            for (ArchiveEntryFile parent : accepted) {
                if (parent.node.directory && candidate.node.path.startsWith(parent.node.path + "/")) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                accepted.add(candidate);
                paths.add(candidate.node.path + (candidate.node.directory ? "/" : ""));
            }
        }
        if (paths.isEmpty() || archiveBrowserFile == null) {
            toast("Select archive items first");
            return;
        }
        clearArchiveClipboard();
        clipboardFiles.clear();
        archiveClipboardFile = archiveBrowserFile;
        archiveClipboardEntries.addAll(paths);
        archiveClipboardPassword = archiveBrowserPassword.clone();
        selectedFiles.clear();
        updateSelection();
        renderFiles();
        toast("Copied " + paths.size() + " archive item(s)");
    }

    private void clearArchiveClipboard() {
        archiveClipboardFile = null;
        archiveClipboardEntries.clear();
        Arrays.fill(archiveClipboardPassword, '\0');
        archiveClipboardPassword = new char[0];
    }

    private List<File> getNormalizedSelection() {
        List<File> candidates = new ArrayList<>(selectedFiles);
        candidates.sort(Comparator.comparingInt(file -> file.getAbsolutePath().length()));
        List<File> normalized = new ArrayList<>();
        List<String> acceptedPaths = new ArrayList<>();
        for (File candidate : candidates) {
            try {
                String candidatePath = candidate.getCanonicalPath();
                boolean nested = false;
                for (String acceptedPath : acceptedPaths) {
                    if (candidatePath.equals(acceptedPath)
                            || candidatePath.startsWith(acceptedPath + File.separator)) {
                        nested = true;
                        break;
                    }
                }
                if (!nested) {
                    normalized.add(candidate);
                    acceptedPaths.add(candidatePath);
                }
            } catch (IOException exception) {
                normalized.add(candidate);
            }
        }
        return normalized;
    }

    private void pasteClipboard(File targetDirectory) {
        if (!archiveClipboardEntries.isEmpty()) {
            pasteArchiveClipboard(targetDirectory);
            return;
        }
        if (clipboardFiles.isEmpty()) {
            toast("Clipboard is empty");
            return;
        }
        if (!targetDirectory.isDirectory() || !targetDirectory.canWrite()) {
            toast("Destination folder cannot be written");
            return;
        }
        List<File> sources = new ArrayList<>(clipboardFiles);
        processPasteItem(sources, 0, targetDirectory, CONFLICT_ASK, new PasteProgress());
    }

    private void pasteArchiveClipboard(File targetDirectory) {
        if (!targetDirectory.isDirectory() || !targetDirectory.canWrite()) {
            toast("Destination folder cannot be written");
            return;
        }
        if (archiveClipboardFile == null || !archiveClipboardFile.isFile()
                || !archiveClipboardFile.canRead()) {
            toast("The source archive is no longer available");
            return;
        }
        File archive = archiveClipboardFile;
        List<String> entries = new ArrayList<>(archiveClipboardEntries);
        char[] password = archiveClipboardPassword.clone();
        runArchiveOperation("Extracting selected archive items", progress -> {
            int copied = 0;
            int skipped = 0;
            int failed = 0;
            try {
                for (String entry : entries) {
                    String path = entry.endsWith("/")
                            ? entry.substring(0, entry.length() - 1) : entry;
                    int separator = path.lastIndexOf('/');
                    String outputName = path.substring(separator + 1);
                    int completed = copied + skipped + failed;
                    progress.update(outputName, completed, entries.size(), 0, 0);
                    if (new File(targetDirectory, outputName).exists()) {
                        skipped++;
                        progress.update(outputName, completed + 1, entries.size(), 0, 0);
                        continue;
                    }
                    try {
                        int extracted = ArchiveManager.extractEntries(archive,
                                Collections.singletonList(entry), targetDirectory,
                            password.length == 0 ? null : password,
                            (fileName, extractedFiles, bytesCopied, totalBytes) ->
                                progress.update(fileName, completed, entries.size(),
                                    bytesCopied, totalBytes));
                        if (extracted > 0 || entry.endsWith("/")) {
                            copied++;
                        } else {
                            failed++;
                        }
                    } catch (Exception exception) {
                        deleteRecursively(new File(targetDirectory, outputName));
                        failed++;
                    }
                        progress.update(outputName, copied + skipped + failed,
                            entries.size(), 0, 0);
                }
                return "Paste complete. " + copied + " copied, " + skipped
                        + " skipped, " + failed + " failed";
            } finally {
                Arrays.fill(password, '\0');
            }
        }, true);
    }

    private void processPasteItem(List<File> sources, int index, File targetDirectory,
            int conflictPolicy, PasteProgress progress) {
        if (index >= sources.size()) {
            finishPaste(progress, false);
            return;
        }

        File source = sources.get(index);
        File destination = new File(targetDirectory, source.getName());
        try {
            String sourcePath = source.getCanonicalPath();
            String destinationPath = destination.getCanonicalPath();
            if (sourcePath.equals(destinationPath)
                    || (source.isDirectory()
                            && destinationPath.startsWith(sourcePath + File.separator))) {
                progress.failed++;
                processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
                return;
            }
        } catch (IOException exception) {
            progress.failed++;
            processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
            return;
        }

        if (destination.exists() && conflictPolicy == CONFLICT_ASK) {
            String[] choices = { "Replace", "Skip", "Replace all", "Skip all", "Cancel" };
            new AlertDialog.Builder(this)
                    .setTitle("Already exists: " + source.getName())
                    .setMessage("Replacing a folder merges it and overwrites matching files.")
                    .setItems(choices, (dialog, choice) -> {
                        switch (choice) {
                            case 0:
                                copyPasteItem(source, destination, progress,
                                    () -> processPasteItem(sources, index + 1,
                                        targetDirectory, CONFLICT_ASK, progress));
                                break;
                            case 1:
                                progress.skipped++;
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_ASK, progress);
                                break;
                            case 2:
                                copyPasteItem(source, destination, progress,
                                    () -> processPasteItem(sources, index + 1,
                                        targetDirectory, CONFLICT_REPLACE_ALL, progress));
                                break;
                            case 3:
                                progress.skipped++;
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_SKIP_ALL, progress);
                                break;
                            default:
                                finishPaste(progress, true);
                                break;
                        }
                    })
                    .setOnCancelListener(dialog -> finishPaste(progress, true))
                    .show();
            return;
        }

        if (destination.exists() && conflictPolicy == CONFLICT_SKIP_ALL) {
            progress.skipped++;
            processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
            return;
        }
        copyPasteItem(source, destination, progress,
                () -> processPasteItem(sources, index + 1,
                        targetDirectory, conflictPolicy, progress));
    }

    private void copyPasteItem(File source, File destination, PasteProgress pasteProgress,
            Runnable onComplete) {
        runArchiveOperation("Copying " + source.getName(), progress -> {
            int total = countItemsRecursively(source);
            int[] completed = { 0 };
            try {
                copyRecursively(source, destination, total, completed, progress);
                pasteProgress.copied++;
            } catch (IOException exception) {
                pasteProgress.failed++;
            }
            return null;
        }, false, false, onComplete);
    }

    private void finishPaste(PasteProgress progress, boolean cancelled) {
        showFiles();
        String result = (cancelled ? "Paste cancelled. " : "Paste complete. ")
                + progress.copied + " copied, " + progress.skipped + " skipped, "
                + progress.failed + " failed";
        toast(result);
    }

    private void deleteSelected() {
        List<File> files = getNormalizedSelection();
        if (files.isEmpty()) {
            toast("Select a file or folder first");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + files.size() + " item(s)?")
                .setMessage("Folders and their contents will be deleted recursively.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    selectedFiles.clear();
                    runArchiveOperation("Deleting files", progress -> {
                        int total = 0;
                        for (File file : files) {
                            total += countItemsRecursively(file);
                        }
                        int[] completed = { 0 };
                        int deleted = 0;
                        int failed = 0;
                        for (File file : files) {
                            if (deleteRecursively(file, total, completed, progress)) {
                                deleted++;
                            } else {
                                failed++;
                            }
                        }
                        return deleted + " deleted, " + failed + " failed";
                    }, true);
                })
                .show();
    }

    private void openFile(File file) {
        if (!file.isFile() || !file.canRead()) {
            toast("File cannot be opened");
            return;
        }
        String extension = fileExtension(file);
        String mimeType = "apk".equals(extension)
                ? "application/vnd.android.package-archive"
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mimeType == null) {
            mimeType = "*/*";
        }
        // Try internal viewers first
        if (openFileInternally(file, mimeType))
            return;
        openFileWithMimeType(file, mimeType);
    }

    /** Returns true if an internal viewer handled the file. */
    private boolean openFileInternally(File file, String mimeType) {
        if (mimeType == null)
            return false;
        if (mimeType.startsWith("image/")) {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("video/")) {
            Intent intent = new Intent(this, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("audio/")) {
            Intent intent = new Intent(this, AudioPlayerActivity.class);
            intent.putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("text/") || isTextExtension(fileExtension(file))) {
            Intent intent = new Intent(this, TextEditorActivity.class);
            intent.putExtra(TextEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        return false;
    }

    private boolean isTextExtension(String ext) {
        switch (ext) {
            case "txt":
            case "md":
            case "log":
            case "json":
            case "xml":
            case "yaml":
            case "yml":
            case "toml":
            case "ini":
            case "cfg":
            case "conf":
            case "sh":
            case "bash":
            case "py":
            case "js":
            case "ts":
            case "java":
            case "kt":
            case "c":
            case "cpp":
            case "h":
            case "cs":
            case "go":
            case "rs":
            case "rb":
            case "php":
            case "html":
            case "css":
            case "sql":
            case "csv":
            case "properties":
            case "gradle":
            case "bat":
            case "ps1":
                return true;
            default:
                return false;
        }
    }

    private void showOpenAsDialog(File file) {
        String[] labels = { "Automatic", "Text editor (built-in)", "Image viewer (built-in)",
                "Audio player (built-in)", "Video player (built-in)",
                "Text / source code", "Audio", "Video", "Image",
                "PDF", "APK installer", "Archive", "Any file type" };
        new AlertDialog.Builder(this)
                .setTitle("Open as")
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openFile(file);
                            break;
                        case 1: {
                            Intent i = new Intent(this, TextEditorActivity.class);
                            i.putExtra(TextEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 2: {
                            Intent i = new Intent(this, ImageViewerActivity.class);
                            i.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 3: {
                            Intent i = new Intent(this, AudioPlayerActivity.class);
                            i.putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 4: {
                            Intent i = new Intent(this, VideoPlayerActivity.class);
                            i.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 5:
                            openFileWithMimeType(file, "text/plain");
                            break;
                        case 6:
                            openFileWithMimeType(file, "audio/*");
                            break;
                        case 7:
                            openFileWithMimeType(file, "video/*");
                            break;
                        case 8:
                            openFileWithMimeType(file, "image/*");
                            break;
                        case 9:
                            openFileWithMimeType(file, "application/pdf");
                            break;
                        case 10:
                            openFileWithMimeType(file, "application/vnd.android.package-archive");
                            break;
                        case 11:
                            openFileWithMimeType(file, "application/zip");
                            break;
                        case 12:
                            openFileWithMimeType(file, "*/*");
                            break;
                    }
                })
                .show();
    }

    private void openFileWithMimeType(File file, String mimeType) {
        if (!file.isFile() || !file.canRead()) {
            toast("File cannot be opened");
            return;
        }
        if ("application/vnd.android.package-archive".equals(mimeType)) {
            openApkInstaller(file);
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (ActivityNotFoundException exception) {
            toast("No app can open this file");
        } catch (IllegalArgumentException | SecurityException exception) {
            toast("Unable to share this file with another app");
        }
    }

    private void openApkInstaller(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            toast("APK file cannot be read");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallFile = file;
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            permissionIntent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(permissionIntent, INSTALL_PERMISSION_REQUEST_CODE);
            } catch (ActivityNotFoundException exception) {
                pendingInstallFile = null;
                toast("Enable Install unknown apps for MFIle Manager in Android settings");
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (ActivityNotFoundException exception) {
            toast("No package installer is available");
        } catch (IllegalArgumentException | SecurityException exception) {
            toast("Unable to open this APK with the package installer");
        }
    }

    private void copyRecursively(File source, File destination, int total, int[] completed,
            OperationProgress progress) throws IOException {
        progress.update(source.getAbsolutePath(), completed[0], total, 0, 0);
        if (source.isDirectory()) {
            if (destination.exists() && !destination.isDirectory()
                    && !deleteRecursively(destination)) {
                throw new IOException("Cannot replace destination file");
            }
            if (!destination.mkdirs() && !destination.isDirectory()) {
                throw new IOException("Cannot create destination");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read source folder");
            }
            for (File child : children) {
                copyRecursively(child, new File(destination, child.getName()),
                        total, completed, progress);
            }
            completed[0]++;
            progress.update(source.getAbsolutePath(), completed[0], total, 0, 0);
        } else {
            if (destination.exists() && destination.isDirectory()
                    && !deleteRecursively(destination)) {
                throw new IOException("Cannot replace destination folder");
            }
            copyFile(source, destination, completed, total, progress);
        }
    }

    private void copyFile(File source, File destination, int[] completed, int total,
            OperationProgress progress) throws IOException {
        byte[] buffer = new byte[8192];
        long fileTotalBytes = source.length();
        long bytesCopied = 0;
        try (FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(destination)) {
            progress.update(source.getAbsolutePath(), completed[0], total,
                    bytesCopied, fileTotalBytes);
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                bytesCopied += bytesRead;
                progress.update(source.getAbsolutePath(), completed[0], total,
                        bytesCopied, fileTotalBytes);
            }
        }
        completed[0]++;
        progress.update(source.getAbsolutePath(), completed[0], total,
                bytesCopied, fileTotalBytes);
    }

    private boolean deleteRecursively(File file) {
        return deleteRecursively(file, 0, null, null);
    }

    private int countItemsRecursively(File file) {
        int count = 0;
        List<File> pending = new ArrayList<>();
        pending.add(file);
        while (!pending.isEmpty()) {
            File current = pending.remove(pending.size() - 1);
            count++;
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children != null) {
                    Collections.addAll(pending, children);
                }
            }
        }
        return count;
    }

    private boolean deleteRecursively(File file, int total, int[] completed,
            OperationProgress progress) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (completed != null) {
            progress.update(file.getAbsolutePath(), completed[0], total, 0, 0);
        }
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child, total, completed, progress)) {
                    return false;
                }
            }
        }
        boolean deleted = file.delete();
        if (completed != null) {
            completed[0]++;
            progress.update(file.getAbsolutePath(), completed[0], total, 0, 0);
        }
        return deleted;
    }

    private void updateSelection() {
        int count = selectedFiles.size();
        TextView selView = findViewById(R.id.selectionText);
        if (selView == null) {
            return;
        }
        if (count == 0 && clipboardFiles.isEmpty() && archiveClipboardEntries.isEmpty()) {
            selView.setVisibility(View.GONE);
            selView.setText("");
        } else {
            selView.setVisibility(View.VISIBLE);
            String text = count == 0 ? "Nothing selected" : count + " item(s) selected";
            if (!clipboardFiles.isEmpty()) {
                text += " | Clipboard: " + clipboardFiles.size() + " item(s)";
            } else if (!archiveClipboardEntries.isEmpty()) {
                text += " | Archive clipboard: " + archiveClipboardEntries.size() + " item(s)";
            }
            selView.setText(text);
        }
    }

    private static class PasteProgress {
        int copied;
        int skipped;
        int failed;
    }

    private static final class ArchiveBrowserNode {
        final String name;
        final String path;
        final int depth;
        final Map<String, ArchiveBrowserNode> children =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ArchiveBrowserNode parent;
        boolean directory;
        boolean expanded;
        boolean selected;
        long size;

        ArchiveBrowserNode(String name, String path, boolean directory, int depth, long size) {
            this.name = name;
            this.path = path;
            this.directory = directory;
            this.depth = depth;
            this.size = size;
        }
    }

    private static final class ArchiveEntryFile extends File {
        private final File archive;
        private final ArchiveBrowserNode node;

        ArchiveEntryFile(File archive, ArchiveBrowserNode node) {
            super(archive.getAbsolutePath() + "!/" + node.path);
            this.archive = archive;
            this.node = node;
        }

        @Override
        public String getName() {
            return node.name;
        }

        @Override
        public String getAbsolutePath() {
            return archive.getAbsolutePath() + "!/" + node.path;
        }

        @Override
        public String getCanonicalPath() {
            return getAbsolutePath();
        }

        @Override
        public File getParentFile() {
            return node.parent == null ? archive : new ArchiveEntryFile(archive, node.parent);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return node.directory;
        }

        @Override
        public boolean isFile() {
            return !node.directory;
        }

        @Override
        public boolean canRead() {
            return archive.canRead();
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public long length() {
            return Math.max(0, node.size);
        }

        @Override
        public long lastModified() {
            return archive.lastModified();
        }
    }

    private static class ActionItem {
        final String title;
        final int icon;
        final Runnable action;

        ActionItem(String title, int icon, Runnable action) {
            this.title = title;
            this.icon = icon;
            this.action = action;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showPermissionDeniedMessage() {
        toast("Storage permission is required to browse files");
    }

    private void requestHttpShare(File target) {
        if (!target.exists() || !target.canRead()) {
            toast("This item cannot be shared");
            return;
        }
        String username = preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME);
        String password = preferences.getString(PREF_HTTP_PASSWORD, "");
        if (!isValidHttpUsername(username) || password.length() < 8) {
            showShareCredentialsDialog(target, false);
            return;
        }
        pendingHttpSharePath = target.getAbsolutePath();
        pendingFtpShare = false;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            return;
        }
        launchPendingShare();
    }

    private void requestFtpShare(File target) {
        if (!target.isDirectory() || !target.canRead()) {
            toast("FTP sharing requires a readable folder");
            return;
        }
        String username = preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME);
        String password = preferences.getString(PREF_HTTP_PASSWORD, "");
        if (!isValidHttpUsername(username) || password.length() < 8) {
            showShareCredentialsDialog(target, true);
            return;
        }
        pendingHttpSharePath = target.getAbsolutePath();
        pendingFtpShare = true;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            return;
        }
        launchPendingShare();
    }

    private void showShareCredentialsDialog(File target, boolean ftpShare) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        EditText usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameInput.setHint("Username");
        usernameInput.setText(preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME));
        fields.addView(usernameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        passwordInput.setHint(preferences.getString(PREF_HTTP_PASSWORD, "").isEmpty()
                ? "Password (at least 8 characters)"
                : "New password, or blank to keep current");
        fields.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("File share login")
                .setMessage(
                        "Username: 1-64 printable characters, no colon. Password: at least 8 characters. HTTP and FTP do not encrypt credentials or file data.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String username = usernameInput.getText().toString().trim();
                    if (!isValidHttpUsername(username)) {
                        usernameInput.setError("Use 1-64 printable characters, without a colon");
                        return;
                    }
                    String typedPassword = passwordInput.getText().toString();
                    String password = typedPassword.isEmpty()
                            ? preferences.getString(PREF_HTTP_PASSWORD, "")
                            : typedPassword;
                    if (password.length() < 8) {
                        passwordInput.setError("Use at least 8 characters");
                        return;
                    }
                    if (preferences.getBoolean(PREF_HTTP_ACTIVE, false)) {
                        stopHttpShare();
                    } else if (preferences.getBoolean(PREF_FTP_ACTIVE, false)) {
                        stopHttpShare();
                    }
                    preferences.edit()
                            .putString(PREF_HTTP_USERNAME, username)
                            .putString(PREF_HTTP_PASSWORD, password)
                            .apply();
                    dialog.dismiss();
                    toast("File share login saved");
                    if (target != null) {
                        if (ftpShare) {
                            requestFtpShare(target);
                        } else {
                            requestHttpShare(target);
                        }
                    }
                }));
        dialog.show();
    }

    private boolean isValidHttpUsername(String username) {
        if (username.isEmpty() || username.length() > 64) {
            return false;
        }
        for (int index = 0; index < username.length(); index++) {
            char character = username.charAt(index);
            if (character < 0x21 || character > 0x7e || character == ':') {
                return false;
            }
        }
        return true;
    }

    private void launchPendingShare() {
        String path = pendingHttpSharePath;
        if (path == null) {
            return;
        }
        pendingHttpSharePath = null;
        boolean ftpShare = pendingFtpShare;
        pendingFtpShare = false;
        if (ftpShare) {
            if (preferences.getBoolean(PREF_FTP_ACTIVE, false)) {
                String activePath = preferences.getString(PREF_FTP_PATH, "");
                if (!path.equals(activePath)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Replace active FTP share?")
                            .setMessage("Only one folder can be shared over FTP at a time.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Replace", (dialog, which) -> startFtpShareService(path))
                            .show();
                    return;
                }
            }
            startFtpShareService(path);
            return;
        }
        if (preferences.getBoolean(PREF_HTTP_ACTIVE, false)) {
            String activePath = preferences.getString(PREF_HTTP_PATH, "");
            if (!path.equals(activePath)) {
                new AlertDialog.Builder(this)
                        .setTitle("Replace active HTTP share?")
                        .setMessage("Only one file or folder can be shared at a time.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Replace", (dialog, which) -> startHttpShareService(path))
                        .show();
                return;
            }
        }
        startHttpShareService(path);
    }

    private void startHttpShareService(String path) {
        Intent intent = new Intent(this, HttpShareService.class)
                .setAction(HttpShareService.ACTION_START)
                .putExtra(HttpShareService.EXTRA_SHARED_PATH, path);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (RuntimeException exception) {
            toast("Could not start HTTP sharing");
        }
    }

    private void startFtpShareService(String path) {
        Intent intent = new Intent(this, FtpShareService.class)
                .setAction(FtpShareService.ACTION_START)
                .putExtra(FtpShareService.EXTRA_SHARED_PATH, path);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (RuntimeException exception) {
            toast("Could not start FTP sharing");
        }
    }

    private void stopHttpShare() {
        Intent intent = new Intent(this, HttpShareService.class)
                .setAction(HttpShareService.ACTION_STOP);
        startService(intent);
        Intent ftpIntent = new Intent(this, FtpShareService.class)
                .setAction(FtpShareService.ACTION_STOP);
        startService(ftpIntent);
    }

    private void updateHttpShareStatus() {
        if (httpShareStatus == null || stopHttpShareButton == null) {
            return;
        }
        boolean httpActive = preferences.getBoolean(PREF_HTTP_ACTIVE, false);
        boolean ftpActive = preferences.getBoolean(PREF_FTP_ACTIVE, false);
        if (!httpActive && !ftpActive) {
            String httpError = preferences.getString(PREF_HTTP_ERROR, "");
            String ftpError = preferences.getString(PREF_FTP_ERROR, "");
            String error = !ftpError.isEmpty() ? ftpError : httpError;
            httpShareStatus.setText(error.isEmpty() ? "File sharing is off" : error);
            stopHttpShareButton.setVisibility(View.GONE);
            copyHttpShareUrlButton.setVisibility(View.GONE);
            return;
        }
        StringBuilder status = new StringBuilder();
        if (httpActive) {
            String path = preferences.getString(PREF_HTTP_PATH, "Shared item");
            String urls = preferences.getString(PREF_HTTP_URLS, "http://<device-ip>:8080");
            boolean httpReadonly = preferences.getBoolean(PREF_HTTP_READONLY, false);
            status.append("HTTP (").append(httpReadonly ? "read-only" : "read/write").append("): ")
                    .append(new File(path).getName())
                    .append(" | Port 8080\n").append(urls);
        }
        if (ftpActive) {
            String path = preferences.getString(PREF_FTP_PATH, "Shared folder");
            String urls = preferences.getString(PREF_FTP_URLS, "ftp://<device-ip>:2121");
            boolean ftpReadonly = preferences.getBoolean(PREF_FTP_READONLY, false);
            if (status.length() > 0) {
                status.append('\n');
            }
            status.append("FTP (").append(ftpReadonly ? "read-only" : "full access").append("): ")
                    .append(new File(path).getName())
                    .append(" | Port 2121\n").append(urls)
                    .append("\nPassive ports 21210-21219");
        }
        stopHttpShareButton.setVisibility(View.VISIBLE);
        stopHttpShareButton.setContentDescription("Stop file sharing");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopHttpShareButton.setTooltipText("Stop file sharing");
        }
        httpShareStatus.setText(status.toString());
        copyHttpShareUrlButton.setVisibility(View.VISIBLE);
    }

    private void reconcileShareState() {
        boolean httpRunning = isServiceRunning(HttpShareService.class);
        boolean ftpRunning = isServiceRunning(FtpShareService.class);
        SharedPreferences.Editor editor = preferences.edit();
        if (!httpRunning) {
            editor.putBoolean(PREF_HTTP_ACTIVE, false)
                    .remove(PREF_HTTP_PATH)
                    .remove(PREF_HTTP_URLS);
        }
        if (!ftpRunning) {
            editor.putBoolean(PREF_FTP_ACTIVE, false)
                    .remove(PREF_FTP_PATH)
                    .remove(PREF_FTP_URLS);
        }
        editor.apply();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service != null
                    && serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void copyHttpShareUrl() {
        String urls = preferences.getBoolean(PREF_HTTP_ACTIVE, false)
                ? preferences.getString(PREF_HTTP_URLS, "")
                : preferences.getString(PREF_FTP_URLS, "");
        int lineBreak = urls.indexOf('\n');
        String firstUrl = lineBreak < 0 ? urls : urls.substring(0, lineBreak);
        if (firstUrl.isEmpty() || firstUrl.contains("<device-ip>")) {
            toast("No local network address is available");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("File share address", firstUrl));
        toast("Share address copied");
    }
}