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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class FileListAdapter extends ListAdapter<FileListAdapter.Row, RecyclerView.ViewHolder> {
    public interface OnChevronClickListener {
        void onChevronClick(File file);
    }

    public interface OnItemClickListener {
        void onItemClick(File file);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(File file);
    }

    public static final class Row {
        final File file;
        final String title;
        final String details;
        final boolean selected;
        final int depth;
        final boolean isDirectory;
        final boolean isExpanded;
        final boolean hasChildren;
        final boolean treeNode;

        private Row(File file, String title, String details, boolean selected,
                int depth, boolean isDirectory, boolean isExpanded, boolean hasChildren,
                boolean treeNode) {
            this.file = file;
            this.title = title;
            this.details = details;
            this.selected = selected;
            this.depth = depth;
            this.isDirectory = isDirectory;
            this.isExpanded = isExpanded;
            this.hasChildren = hasChildren;
            this.treeNode = treeNode;
        }

        public static Row file(File file, String details, boolean selected) {
            return new Row(file, null, details, selected, 0,
                    file != null && file.isDirectory(), false, false, false);
        }

        public static Row treeNode(File file, String details, boolean selected,
                int depth, boolean isDirectory, boolean isExpanded, boolean hasChildren) {
            return new Row(file, null, details, selected, depth, isDirectory, isExpanded,
                    hasChildren, true);
        }

        public static Row heading(String title) {
            return new Row(null, title, null, false, 0, false, false, false, false);
        }

        public boolean isHeading() {
            return file == null;
        }

        public File getFile() {
            return file;
        }

        /** Stable identity used by DiffUtil and RecyclerView stable ids. */
        public String key() {
            return isHeading() ? "heading:" + title : file.getAbsolutePath();
        }

        boolean contentsEqual(Row other) {
            return depth == other.depth
                    && selected == other.selected
                    && isDirectory == other.isDirectory
                    && isExpanded == other.isExpanded
                    && hasChildren == other.hasChildren
                    && treeNode == other.treeNode
                    && Objects.equals(title, other.title)
                    && Objects.equals(details, other.details);
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF_CALLBACK = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
            return oldItem.key().equals(newItem.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
            return oldItem.contentsEqual(newItem);
        }
    };

    private static final int TYPE_HEADING = 0;
    private static final int TYPE_ROW = 1;
    private static final String PAYLOAD_THUMBNAIL = "thumbnail";
    private static final int THUMBNAIL_EDGE = 72;

    private final LayoutInflater inflater;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> pendingThumbnails = Collections.synchronizedSet(new HashSet<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbnailCache = new LruCache<String, Bitmap>(12 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    private volatile boolean closed;
    private OnChevronClickListener chevronClickListener;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;
    private boolean animationsEnabled = true;

    public FileListAdapter(Context context) {
        super(DIFF_CALLBACK);
        inflater = LayoutInflater.from(context);
        setHasStableIds(true);
    }

    public void setOnChevronClickListener(OnChevronClickListener listener) {
        this.chevronClickListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
    }

    /**
     * Submits a whole new list. DiffUtil runs on a background thread, so only rows that
     * actually changed are rebound and only rows that were inserted/removed animate.
     */
    public void submitRows(List<Row> newRows, Runnable commitCallback) {
        submitList(new ArrayList<>(newRows), commitCallback);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).key().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeading() ? TYPE_HEADING : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADING) {
            return new HeadingHolder(inflater.inflate(R.layout.file_list_heading, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.file_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof HeadingHolder) {
            ((HeadingHolder) holder).bind(row);
        } else {
            ((RowHolder) holder).bind(row);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof RowHolder
                && payloads.contains(PAYLOAD_THUMBNAIL)) {
            Row row = getItem(position);
            if (row != null && !row.isHeading() && row.file != null) {
                ((RowHolder) holder).bindThumbnail(row);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    public void close() {
        closed = true;
        thumbnailExecutor.shutdownNow();
        thumbnailCache.evictAll();
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        final View indentSpacer;
        final ImageView treeChevron;
        final ImageView icon;
        final ImageView selectionMark;
        final TextView name;
        final TextView details;
        Boolean lastExpanded;
        String boundKey;

        RowHolder(View view) {
            super(view);
            indentSpacer = view.findViewById(R.id.indentSpacer);
            treeChevron = view.findViewById(R.id.treeChevron);
            icon = view.findViewById(R.id.fileIcon);
            selectionMark = view.findViewById(R.id.selectionMark);
            name = view.findViewById(R.id.fileName);
            details = view.findViewById(R.id.fileDetails);

            itemView.setOnClickListener(clicked -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemClickListener != null) {
                    Row row = getItem(position);
                    if (!row.isHeading() && row.file != null) {
                        itemClickListener.onItemClick(row.file);
                    }
                }
            });
            itemView.setOnLongClickListener(clicked -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemLongClickListener != null) {
                    Row row = getItem(position);
                    if (!row.isHeading() && row.file != null) {
                        return itemLongClickListener.onItemLongClick(row.file);
                    }
                }
                return false;
            });
            treeChevron.setOnClickListener(clicked -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && chevronClickListener != null) {
                    Row row = getItem(position);
                    if (!row.isHeading() && row.file != null) {
                        chevronClickListener.onChevronClick(row.file);
                    }
                }
            });
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            String rowKey = row.key();
            boolean sameItem = rowKey.equals(boundKey);
            int indentWidth = row.depth * dp(context, 14);
            ViewGroup.LayoutParams spacerParams = indentSpacer.getLayoutParams();
            if (spacerParams.width != indentWidth) {
                spacerParams.width = indentWidth;
                indentSpacer.setLayoutParams(spacerParams);
            }

            if (row.treeNode && row.isDirectory) {
                treeChevron.setVisibility(View.VISIBLE);
                float targetRotation = row.isExpanded ? 90f : 0f;
                boolean animateChevron = animationsEnabled && sameItem && lastExpanded != null
                        && lastExpanded != row.isExpanded;
                if (animateChevron) {
                    treeChevron.animate()
                            .rotation(targetRotation)
                            .setDuration(160)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    treeChevron.animate().cancel();
                    treeChevron.setRotation(targetRotation);
                }
                lastExpanded = row.isExpanded;
                treeChevron.setContentDescription(
                        row.isExpanded ? "Collapse folder" : "Expand folder");
            } else {
                treeChevron.animate().cancel();
                treeChevron.setRotation(0f);
                treeChevron.setVisibility(View.INVISIBLE);
                treeChevron.setContentDescription(null);
                lastExpanded = null;
            }
            boundKey = rowKey;

            itemView.setSelected(row.selected);
            name.setText(row.file.getName());
            details.setText(row.details);
            selectionMark.setVisibility(row.selected ? View.VISIBLE : View.GONE);

            String thumbnailKey = thumbnailKey(row.file);
            int iconResource = iconFor(row.file);
            if (thumbnailKey == null) {
                icon.setTag(null);
                icon.setImageResource(iconResource);
            } else {
                icon.setTag(thumbnailKey);
                Bitmap cached = thumbnailCache.get(thumbnailKey);
                if (cached != null) {
                    icon.setImageBitmap(cached);
                } else {
                    icon.setImageResource(iconResource);
                    requestThumbnail(row.file, thumbnailKey);
                }
            }
        }

        void bindThumbnail(Row row) {
            String key = thumbnailKey(row.file);
            if (key == null || !key.equals(icon.getTag())) {
                return;
            }
            Bitmap cached = thumbnailCache.get(key);
            if (cached != null) {
                icon.setImageBitmap(cached);
            }
        }
    }

    private static final class HeadingHolder extends RecyclerView.ViewHolder {
        final TextView heading;

        HeadingHolder(View view) {
            super(view);
            heading = (TextView) view;
        }

        void bind(Row row) {
            heading.setText(row.title);
            itemView.setSelected(false);
        }
    }

    private void requestThumbnail(File file, String key) {
        if (closed || !pendingThumbnails.add(key)) {
            return;
        }
        try {
            thumbnailExecutor.execute(() -> {
                Bitmap bitmap = createThumbnail(file);
                if (bitmap != null) {
                    thumbnailCache.put(key, bitmap);
                }
                pendingThumbnails.remove(key);
                if (bitmap == null || closed) {
                    return;
                }
                String path = file.getAbsolutePath();
                mainHandler.post(() -> {
                    if (closed) {
                        return;
                    }
                    int position = findPositionForPath(path);
                    if (position != RecyclerView.NO_POSITION) {
                        notifyItemChanged(position, PAYLOAD_THUMBNAIL);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            pendingThumbnails.remove(key);
        }
    }

    private int findPositionForPath(String path) {
        for (int position = 0; position < getItemCount(); position++) {
            File file = getItem(position).file;
            if (file != null && path.equals(file.getAbsolutePath())) {
                return position;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private Bitmap createThumbnail(File file) {
        String extension = extension(file);
        try {
            if (isImage(extension)) {
                return imageThumbnail(file);
            }
            if (isVideo(extension)) {
                return videoThumbnail(file);
            }
            if ("pdf".equals(extension)) {
                return pdfThumbnail(file);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private Bitmap imageThumbnail(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > THUMBNAIL_EDGE * 2
                || bounds.outHeight / options.inSampleSize > THUMBNAIL_EDGE * 2) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return scaleToEdge(decoded);
    }

    private Bitmap videoThumbnail(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return scaleToEdge(retriever.getFrameAtTime(-1));
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
    }

    private Bitmap pdfThumbnail(File file) throws IOException {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor)) {
            if (renderer.getPageCount() == 0) {
                return null;
            }
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                float scale = Math.min((float) THUMBNAIL_EDGE / page.getWidth(),
                        (float) THUMBNAIL_EDGE / page.getHeight());
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        }
    }

    private Bitmap scaleToEdge(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int largestSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largestSide <= THUMBNAIL_EDGE) {
            return bitmap;
        }
        float scale = (float) THUMBNAIL_EDGE / largestSide;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private int iconFor(File file) {
        if (file.isDirectory()) {
            return R.drawable.ic_file_folder;
        }
        String extension = extension(file);
        if (isImage(extension)) {
            return R.drawable.ic_file_image;
        }
        if (isVideo(extension)) {
            return R.drawable.ic_file_video;
        }
        if (isAudio(extension)) {
            return R.drawable.ic_file_audio;
        }
        if (isArchive(extension)) {
            return R.drawable.ic_file_archive;
        }
        if ("apk".equals(extension)) {
            return R.drawable.ic_file_apk;
        }
        if ("pdf".equals(extension)) {
            return R.drawable.ic_file_pdf;
        }
        if (isCode(extension)) {
            return R.drawable.ic_file_code;
        }
        if (isDocument(extension)) {
            return R.drawable.ic_file_document;
        }
        return R.drawable.ic_file_generic;
    }

    private String thumbnailKey(File file) {
        if (file == null || file.isDirectory()) {
            return null;
        }
        String extension = extension(file);
        if (!isImage(extension) && !isVideo(extension) && !"pdf".equals(extension)) {
            return null;
        }
        return file.getAbsolutePath() + ':' + file.lastModified() + ':' + file.length();
    }

    private String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImage(String extension) {
        return hasExtension(extension, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif");
    }

    private boolean isVideo(String extension) {
        return hasExtension(extension, "mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi", "mpeg", "mpg");
    }

    private boolean isAudio(String extension) {
        return hasExtension(extension, "mp3", "m4a", "aac", "wav", "ogg", "flac", "mid", "midi", "opus");
    }

    private boolean isArchive(String extension) {
        return hasExtension(extension, "zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    }

    private boolean isCode(String extension) {
        return hasExtension(extension, "java", "kt", "py", "js", "ts", "json", "xml", "html", "css", "gradle", "sh",
                "c", "cpp");
    }

    private boolean isDocument(String extension) {
        return hasExtension(extension, "txt", "rtf", "csv", "doc", "docx", "odt", "xls", "xlsx", "ppt", "pptx");
    }

    private boolean hasExtension(String extension, String... extensions) {
        for (String candidate : extensions) {
            if (candidate.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
