package org.ctdozlite.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Local Android prototype. No network or Zalo account access. */
public final class MainActivity extends Activity {
    private static final int PICK_GIF = 14;
    private static final int MAX_GIF_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TEMPLATES = 100;

    static { System.loadLibrary("ctdozlite_android"); }
    private native long nativeGifDimensions(byte[] bytes);
    private native String nativeNormalizeTemplate(String input);

    private final ArrayList<String> templates = new ArrayList<>();
    private LinearLayout galleryList;
    private LinearLayout templateList;
    private ImageView gifPreview;
    private EditText draft;
    private EditText templateSearch;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadTemplates();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name) + " • bản thử Android");
        title.setTextSize(22);
        content.addView(title);
        TextView notice = new TextView(this);
        notice.setText("GIF và mẫu tin được lưu trên máy này. Bản thử chưa kết nối hoặc gửi tin qua Zalo.");
        content.addView(notice);
        status = new TextView(this);
        content.addView(status);
        Button updates = new Button(this);
        updates.setText("Kiểm tra và tải APK cập nhật từ GitHub");
        updates.setEnabled(!BuildConfig.UPDATE_REPO.isEmpty());
        updates.setOnClickListener(v -> checkUpdate(updates));
        content.addView(updates);
        if (BuildConfig.UPDATE_REPO.isEmpty()) {
            TextView channel = new TextView(this);
            channel.setText("Kênh nội bộ chờ gắn repo GitHub của bạn; không lưu token trong APK.");
            content.addView(channel);
        }

        Button importGif = new Button(this);
        importGif.setText("Nhập GIF đã lưu");
        importGif.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/gif");
            startActivityForResult(pick, PICK_GIF);
        });
        content.addView(importGif);
        gifPreview = new ImageView(this);
        gifPreview.setAdjustViewBounds(true);
        gifPreview.setMaxHeight(400);
        content.addView(gifPreview);
        galleryList = new LinearLayout(this);
        galleryList.setOrientation(LinearLayout.VERTICAL);
        content.addView(galleryList);

        TextView templateHeading = new TextView(this);
        templateHeading.setText("Mẫu tin nhắn nhanh (cục bộ)");
        templateHeading.setTextSize(18);
        content.addView(templateHeading);
        draft = new EditText(this);
        draft.setHint("Bản nháp để chèn mẫu; chưa có chức năng gửi");
        draft.setMinLines(2);
        content.addView(draft);
        Button addTemplate = new Button(this);
        addTemplate.setText("Tạo mẫu");
        addTemplate.setOnClickListener(v -> editTemplate(-1));
        content.addView(addTemplate);
        templateSearch = new EditText(this);
        templateSearch.setSingleLine(true);
        templateSearch.setHint("Tìm mẫu tin");
        templateSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { renderTemplates(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        content.addView(templateSearch);
        templateList = new LinearLayout(this);
        templateList.setOrientation(LinearLayout.VERTICAL);
        content.addView(templateList);
        renderGallery();
        renderTemplates();
        handleSharedGif(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedGif(intent);
    }

    private void handleSharedGif(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        if (!"image/gif".equalsIgnoreCase(intent.getType())) {
            status.setText("Chỉ hỗ trợ nhận tệp GIF đã lưu.");
            return;
        }
        Bundle extras = intent.getExtras();
        Object payload = extras == null ? null : extras.get(Intent.EXTRA_STREAM);
        if (payload instanceof Uri) importGifUri((Uri) payload);
        else status.setText("Ứng dụng chia sẻ không cung cấp tệp GIF.");
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_GIF || result != RESULT_OK || data == null || data.getData() == null) return;
        importGifUri(data.getData());
    }

    private void importGifUri(Uri uri) {
        if (!"content".equalsIgnoreCase(uri.getScheme())) {
            status.setText("Chỉ nhập GIF qua trình chọn tệp an toàn của Android.");
            return;
        }
        status.setText("Đang kiểm tra và nhập GIF…");
        new Thread(() -> {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("Không đọc được tệp");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_GIF_BYTES) throw new IllegalArgumentException("GIF vượt quá 8 MB");
                output.write(buffer, 0, count);
            }
            byte[] bytes = output.toByteArray();
            if (nativeGifDimensions(bytes) == 0) throw new IllegalArgumentException("GIF không hợp lệ");
            Drawable preview = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)));
            File dir = new File(getFilesDir(), "personal_gifs");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalArgumentException("Không tạo được thư viện");
            String id = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            File target = new File(dir, id + ".gif");
            if (!target.exists() && dir.listFiles() != null && dir.listFiles().length >= 100) {
                throw new IllegalArgumentException("Thư viện đã đủ 100 GIF");
            }
            if (!target.exists()) {
                File temp = new File(dir, id + ".tmp");
                try (FileOutputStream file = new FileOutputStream(temp)) {
                    file.write(bytes);
                    file.getFD().sync();
                }
                if (!temp.renameTo(target)) throw new IllegalArgumentException("Không lưu được GIF");
            }
            String name = displayName(uri);
            getPreferences(MODE_PRIVATE).edit().putString("gif_" + id, name).apply();
            runOnUiThread(() -> {
                showPreview(preview);
                status.setText("Đã nhập " + name + " (" + bytes.length + " byte)");
                renderGallery();
            });
        } catch (Exception error) {
            runOnUiThread(() -> status.setText("Không nhập được GIF: " + error.getMessage()));
        }
        }, "gif-import").start();
    }

    private void renderGallery() {
        galleryList.removeAllViews();
        File dir = new File(getFilesDir(), "personal_gifs");
        File[] items = dir.listFiles((parent, name) -> name.matches("[0-9a-f]{64}\\.gif"));
        if (items == null || items.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có GIF trong thư viện.");
            galleryList.addView(empty);
            return;
        }
        for (int index = 0; index < Math.min(items.length, 24); index++) {
            File file = items[index];
            String id = file.getName().substring(0, 64);
            String name = getPreferences(MODE_PRIVATE).getString("gif_" + id, "GIF " + id.substring(0, 8));
            Button button = new Button(this);
            button.setText(name);
            button.setOnClickListener(v -> {
                status.setText("Đang mở GIF…");
                new Thread(() -> {
                    try {
                        Drawable preview = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file));
                        runOnUiThread(() -> {
                            showPreview(preview);
                            status.setText("Xem trước " + name + " • chưa gửi qua Zalo");
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> status.setText("Không xem được GIF"));
                    }
                }, "gif-preview").start();
            });
            galleryList.addView(button);
        }
        if (items.length > 24) {
            TextView more = new TextView(this);
            more.setText("Hiện 24/" + items.length + " GIF; tìm kiếm và phân trang đang phát triển.");
            galleryList.addView(more);
        }
    }

    private void checkUpdate(Button button) {
        button.setEnabled(false);
        status.setText("Đang kiểm tra release và SHA-256…");
        new Thread(() -> {
            try {
                UpdateClient.Result result = UpdateClient.checkAndDownload(
                    BuildConfig.UPDATE_REPO, BuildConfig.UPDATE_ASSET, getCacheDir());
                if (!samePackageAndSigner(result.apk)) {
                    result.apk.delete();
                    throw new IllegalArgumentException("APK khác định danh hoặc chữ ký ứng dụng hiện tại");
                }
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    status.setText("Đã xác minh APK release " + result.tag);
                    new AlertDialog.Builder(this)
                        .setTitle("Cài cập nhật đã xác minh?")
                        .setMessage("Android sẽ hỏi lại trước khi cài. Bản thử này chỉ dùng chữ ký debug; chưa phải kênh phát hành ổn định.")
                        .setNegativeButton("Để sau", null)
                        .setPositiveButton("Mở trình cài Android", (dialog, which) -> openInstaller())
                        .show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    status.setText("Chưa thể cập nhật: " + error.getMessage());
                });
            }
        }, "github-update-check").start();
    }

    private boolean samePackageAndSigner(File apk) {
        try {
            PackageManager manager = getPackageManager();
            PackageInfo current = manager.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo incoming = manager.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (incoming == null || !getPackageName().equals(incoming.packageName)
                || incoming.getLongVersionCode() <= current.getLongVersionCode()
                || current.signingInfo == null || incoming.signingInfo == null) return false;
            Signature[] existing = current.signingInfo.getApkContentsSigners();
            Signature[] update = incoming.signingInfo.getApkContentsSigners();
            return existing.length == 1 && update.length == 1
                && Arrays.equals(existing[0].toByteArray(), update[0].toByteArray());
        } catch (Exception error) { return false; }
    }

    private void openInstaller() {
        Uri uri = Uri.parse("content://" + getPackageName() + ".apkfiles/update.apk");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); }
        catch (Exception error) { status.setText("Android chưa cho phép mở trình cài APK."); }
    }

    private void showPreview(Drawable drawable) {
        gifPreview.setImageDrawable(drawable);
        if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).start();
    }

    private void loadTemplates() {
        try {
            JSONArray saved = new JSONArray(getPreferences(MODE_PRIVATE).getString("templates", "[]"));
            for (int i = 0; i < Math.min(saved.length(), MAX_TEMPLATES); i++) {
                String normalized = nativeNormalizeTemplate(saved.optString(i));
                if (normalized != null) templates.add(normalized);
            }
        } catch (Exception ignored) { templates.clear(); }
    }

    private void saveTemplates() {
        JSONArray saved = new JSONArray();
        for (String template : templates) saved.put(template);
        getPreferences(MODE_PRIVATE).edit().putString("templates", saved.toString()).apply();
    }

    private void renderTemplates() {
        templateList.removeAllViews();
        String query = templateSearch == null ? "" : templateSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < templates.size(); i++) {
            if (!templates.get(i).toLowerCase(Locale.ROOT).contains(query)) continue;
            final int position = i;
            LinearLayout row = new LinearLayout(this);
            Button insert = new Button(this);
            insert.setText(templates.get(i));
            insert.setOnClickListener(v -> draft.append((draft.length() == 0 ? "" : " ") + templates.get(position)));
            row.addView(insert, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = new Button(this);
            edit.setText("Sửa");
            edit.setOnClickListener(v -> editTemplate(position));
            row.addView(edit);
            Button remove = new Button(this);
            remove.setText("Xóa");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Xóa mẫu này khỏi máy?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> { templates.remove(position); saveTemplates(); renderTemplates(); })
                .show());
            row.addView(remove);
            templateList.addView(row);
        }
    }

    private void editTemplate(int position) {
        if (position < 0 && templates.size() >= MAX_TEMPLATES) {
            Toast.makeText(this, "Đã đủ 100 mẫu cục bộ", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText text = new EditText(this);
        text.setSingleLine(true);
        if (position >= 0) text.setText(templates.get(position));
        new AlertDialog.Builder(this)
            .setTitle(position < 0 ? "Tạo mẫu" : "Sửa mẫu")
            .setView(text)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", (dialog, which) -> {
                String normalized = nativeNormalizeTemplate(text.getText().toString());
                if (normalized == null) {
                    Toast.makeText(this, "Mẫu phải có 1–120 ký tự hợp lệ", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (position < 0) templates.add(normalized); else templates.set(position, normalized);
                saveTemplates();
                renderTemplates();
            }).show();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0).replaceAll("[\\p{Cntrl}]", "").trim();
                if (!name.isEmpty()) return name.length() <= 80 ? name : name.substring(0, 80);
            }
        } catch (Exception ignored) { }
        return "GIF đã nhập";
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }
}
