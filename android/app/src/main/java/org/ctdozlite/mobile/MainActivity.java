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
    private native String nativeExpandTemplate(String input, String recipient, String time, String date);
    private native long nativeAuthStart();
    private native void nativeAuthCancel(long generation);
    private native String nativeAuthStatus(long generation);
    private native byte[] nativeAuthTake(long generation);
    private native String nativeAuthRestore(byte[] bytes);
    private native String nativeRecentConversations();
    private boolean recentLoading;
    private int recentUiGeneration;
    private long authGeneration;
    private final android.os.Handler authHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final ArrayList<String> templates = new ArrayList<>();
    private LinearLayout galleryList;
    private LinearLayout templateList;
    private ImageView gifPreview;
    private EditText draft;
    private EditText templateSearch;
    private TextView status;
    private EditText gifSearch;
    private final ArrayList<GifGallery.Entry> galleryEntries = new ArrayList<>();
    private boolean favoritesOnly;
    private int galleryPage, galleryGeneration, previewGeneration;
    private ScrollView toolsScreen;
    private MobileShell mobileShell;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadTemplates();
        ScrollView scroll = new ScrollView(this);
        toolsScreen = scroll;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name) + " • bản thử Android");
        title.setTextSize(22);
        content.addView(title);
        Button backToMessages = new Button(this);
        backToMessages.setText("‹ Về Tin nhắn");
        backToMessages.setOnClickListener(v -> mobileShell.show());
        content.addView(backToMessages);
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
        gifSearch = new EditText(this);
        gifSearch.setSingleLine(true);
        gifSearch.setHint("Tìm GIF theo tên hoặc thẻ");
        gifSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { galleryPage = 0; renderGalleryRows(); }
            public void afterTextChanged(Editable text) { }
        });
        content.addView(gifSearch);
        Button favorites = new Button(this);
        favorites.setText("Chỉ hiện yêu thích: Tắt");
        favorites.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly; galleryPage = 0;
            favorites.setText("Chỉ hiện yêu thích: " + (favoritesOnly ? "Bật" : "Tắt"));
            renderGalleryRows();
        });
        content.addView(favorites);
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
        mobileShell = new MobileShell(this, () -> setContentView(toolsScreen), this::showLogin, this::loadRecent);
        mobileShell.show();
        handleSharedGif(getIntent());
    }

    @Override protected void onStart() {
        super.onStart();
        if(mobileShell!=null && authGeneration==0)restoreStoredSession();
    }
    @Override protected void onStop() {
        super.onStop(); cancelAuth();
    }
    private void cancelAuth() {
        if(authGeneration!=0) nativeAuthCancel(authGeneration);
        authGeneration=0;authHandler.removeCallbacksAndMessages(null);
    }
    private void restoreStoredSession() {
        new Thread(() -> {
            byte[] bytes=null;
            String label=null;
            try {
                SessionVault vault=new SessionVault(this);
                if(!vault.exists())return;
                bytes=vault.read();
                org.json.JSONObject result=new org.json.JSONObject(nativeAuthRestore(bytes));
                if(result.optString("phase").equals("restored_unverified")) label="Phiên trên máy: "+result.optString("name")+" • chưa xác minh máy chủ";
                else if(!result.optString("phase").equals("busy")) label="Không khôi phục được phiên trên máy. Tệp cũ vẫn được giữ.";
            } catch(Exception error) { label="Không mở được kho phiên bảo vệ. Tệp cũ vẫn được giữ."; }
            finally { if(bytes!=null)Arrays.fill(bytes,(byte)0); }
            final String message=label;
            if(message!=null)runOnUiThread(()->{if(authGeneration==0&&!isFinishing()&&!isDestroyed())mobileShell.setLocalSessionLabel(message);});
        },"session-vault-restore").start();
    }
    private void loadRecent() {
        if(recentLoading)return;
        final int requestGeneration=++recentUiGeneration;
        recentLoading=true;mobileShell.setRecentLoading(true);
        new Thread(()->{
            String response=nativeRecentConversations();
            runOnUiThread(()->{
                if(requestGeneration!=recentUiGeneration)return;
                recentLoading=false;mobileShell.setRecentLoading(false);
                if(isFinishing()||isDestroyed())return;
                try{
                    org.json.JSONObject result=new org.json.JSONObject(response);
                    if(result.optString("phase").equals("loaded"))mobileShell.acceptRecent(result.getJSONObject("data").put("account",result.optString("account")),false);
                    else Toast.makeText(this,result.optString("phase").equals("requires_login")?"Cần phiên đăng nhập trước khi tải hội thoại.":result.optString("message","Tác vụ đã hủy do phiên thay đổi."),Toast.LENGTH_LONG).show();
                }catch(Exception error){Toast.makeText(this,"Không đọc được danh sách hội thoại.",Toast.LENGTH_LONG).show();}
            });
        },"recent-conversations").start();
    }
    private void showLogin() {
        cancelAuth();
        recentUiGeneration++;recentLoading=false;mobileShell.setRecentLoading(false);
        mobileShell.clearRecent();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,48,24,48);page.setBackgroundColor(0xffffffff);
        TextView heading=new TextView(this);heading.setText("Đăng nhập bằng QR");heading.setTextSize(24);page.addView(heading);
        TextView info=new TextView(this);info.setText("Luồng thiết bị đồng hành thử nghiệm. Khi tạo mã, dùng Zalo trên điện thoại đã đăng nhập để quét và xác nhận. Chưa hỗ trợ đăng nhập tài khoản chính bằng số điện thoại.");page.addView(info);
        ImageView qr=new ImageView(this);qr.setAdjustViewBounds(true);page.addView(qr,new LinearLayout.LayoutParams(-1,320));
        TextView progress=new TextView(this);progress.setText("Chưa tạo mã. Không quét bằng tài khoản thật khi chưa kiểm chứng xong.");page.addView(progress);
        Button start=new Button(this);start.setText("Tạo mã QR");page.addView(start);
        Button cancel=new Button(this);cancel.setText("Hủy và về Tin nhắn");cancel.setOnClickListener(v->{cancelAuth();qr.setImageDrawable(null);mobileShell.show();});page.addView(cancel);
        Button check=new Button(this);check.setText("Kiểm tra kho bảo vệ bằng dữ liệu giả");page.addView(check);
        check.setOnClickListener(v->{ check.setEnabled(false);new Thread(()->{boolean ok;try{SessionVault.selfTest(this);ok=true;}catch(Exception error){ok=false;}final boolean passed=ok;runOnUiThread(()->{check.setEnabled(true);progress.setText(passed?"Kiểm tra Keystore: khôi phục, dữ liệu ngẫu nhiên và chống sửa đổi đều qua.":"Thiết bị chưa vượt qua kiểm tra kho bảo vệ.");});},"vault-selftest").start(); });
        start.setOnClickListener(v->{cancelAuth();start.setEnabled(false);authGeneration=nativeAuthStart();final long generation=authGeneration;
            authHandler.post(new Runnable(){private boolean rendered; public void run(){
                if(authGeneration!=generation||generation==0)return;
                try {
                    org.json.JSONObject state=new org.json.JSONObject(nativeAuthStatus(generation));String phase=state.optString("phase");
                    if(phase.equals("waiting_scan")) {
                        if(!rendered) {
                        int width=state.optInt("width");String pixels=state.optString("pixels");
                        if(width<21||width>177||pixels.length()!=width*width)throw new IllegalArgumentException();
                        int side=width+8;android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(side,side,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xffffffff);
                        for(int y=0;y<width;y++)for(int x=0;x<width;x++)if(pixels.charAt(y*width+x)=='1')bitmap.setPixel(x+4,y+4,0xff000000);
                        qr.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(bitmap,side*4,side*4,false));rendered=true;
                        }
                        progress.setText("Chờ quét mã bằng Zalo trên điện thoại.");
                    } else if(phase.equals("waiting_confirm"))progress.setText("Đã quét. Chờ xác nhận trên điện thoại.");
                    else if(phase.equals("requesting"))progress.setText("Đang yêu cầu mã QR…");
                    else if(phase.equals("authenticated")) {
                        byte[] session=nativeAuthTake(generation);qr.setImageDrawable(null);progress.setText("Đang bảo vệ phiên trên thiết bị…");
                        new Thread(()->{boolean ok=false;try{new SessionVault(MainActivity.this).save(session);ok=true;}catch(Exception error){}finally{if(session!=null)Arrays.fill(session,(byte)0);}final boolean saved=ok;runOnUiThread(()->{start.setEnabled(true);progress.setText(saved?"Đã lưu phiên bảo vệ. Về Tin nhắn để tải tin gần đây.":"Không lưu được phiên bảo vệ. Hãy hủy và thử lại.");});},"session-vault-save").start();return;
                    } else {qr.setImageDrawable(null);progress.setText(state.optString("message","Đã kết thúc hoặc hủy đăng nhập."));start.setEnabled(true);return;}
                    authHandler.postDelayed(this,500);
                } catch(Exception error){cancelAuth();qr.setImageDrawable(null);progress.setText("Không đọc được trạng thái đăng nhập.");start.setEnabled(true);}
            }});
        });
        ScrollView loginScroll = new ScrollView(this);
        loginScroll.setFillViewport(true);
        loginScroll.addView(page);
        setContentView(loginScroll);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedGif(intent);
    }

    private void handleSharedGif(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        setContentView(toolsScreen);
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
        final int generation = ++galleryGeneration;
        new Thread(() -> {
            ArrayList<GifGallery.Entry> entries = new ArrayList<>();
            File dir = new File(getFilesDir(), "personal_gifs");
            File[] files = dir.listFiles((parent, name) -> name.matches("[0-9a-f]{64}\\.gif"));
            if (files != null) for (File file : files) {
                String id = file.getName().substring(0, 64);
                entries.add(new GifGallery.Entry(id,
                    getPreferences(MODE_PRIVATE).getString("gif_" + id, "GIF " + id.substring(0, 8)),
                    getPreferences(MODE_PRIVATE).getString("gif_tags_" + id, ""),
                    getPreferences(MODE_PRIVATE).getBoolean("gif_fav_" + id, false)));
            }
            runOnUiThread(() -> {
                if (generation != galleryGeneration || isFinishing() || isDestroyed()) return;
                galleryEntries.clear(); galleryEntries.addAll(entries); renderGalleryRows();
            });
        }, "gif-gallery-index").start();
    }

    private void renderGalleryRows() {
        if (galleryList == null) return;
        galleryList.removeAllViews();
        java.util.List<GifGallery.Entry> selected = GifGallery.filter(galleryEntries, gifSearch.getText().toString(), favoritesOnly);
        galleryPage = Math.min(galleryPage, GifGallery.pageCount(selected.size()) - 1);
        if (selected.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Không có GIF phù hợp.");
            galleryList.addView(empty);
            return;
        }
        TextView count = new TextView(this);
        count.setText(selected.size() + " GIF • Trang " + (galleryPage + 1) + "/" + GifGallery.pageCount(selected.size()));
        galleryList.addView(count);
        for (GifGallery.Entry entry : GifGallery.page(selected, galleryPage)) {
            File file = new File(new File(getFilesDir(), "personal_gifs"), entry.id + ".gif");
            String name = entry.name;
            Button button = new Button(this);
            button.setText((entry.favorite ? "★ " : "") + name);
            button.setOnClickListener(v -> {
                final int generation = ++previewGeneration;
                status.setText("Đang mở GIF…");
                new Thread(() -> {
                    try {
                        Drawable preview = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file));
                        runOnUiThread(() -> {
                            if (generation != previewGeneration || isFinishing() || isDestroyed()) return;
                            showPreview(preview);
                            status.setText("Xem trước " + name + " • chưa gửi qua Zalo");
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> status.setText("Không xem được GIF"));
                    }
                }, "gif-preview").start();
            });
            galleryList.addView(button);
            Button manage = new Button(this);
            manage.setText("Tên, thẻ, yêu thích hoặc xóa");
            manage.setOnClickListener(v -> manageGif(entry, file));
            galleryList.addView(manage);
        }
        LinearLayout pages = new LinearLayout(this);
        Button previous = new Button(this); previous.setText("Trang trước"); previous.setEnabled(galleryPage > 0);
        previous.setOnClickListener(v -> { galleryPage--; renderGalleryRows(); }); pages.addView(previous);
        Button next = new Button(this); next.setText("Trang sau"); next.setEnabled(galleryPage + 1 < GifGallery.pageCount(selected.size()));
        next.setOnClickListener(v -> { galleryPage++; renderGalleryRows(); }); pages.addView(next); galleryList.addView(pages);
    }

    private void manageGif(GifGallery.Entry entry, File file) {
        new AlertDialog.Builder(this).setTitle(entry.name)
            .setItems(new String[]{"Sửa tên và thẻ", entry.favorite ? "Bỏ yêu thích" : "Thêm yêu thích", "Xóa GIF"}, (dialog, action) -> {
                if (action == 0) {
                    LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
                    EditText name = new EditText(this); name.setSingleLine(true); name.setText(entry.name); form.addView(name);
                    EditText tags = new EditText(this); tags.setSingleLine(true); tags.setHint("Thẻ: vui, mèo, chào hỏi…"); tags.setText(entry.tags); form.addView(tags);
                    new AlertDialog.Builder(this).setTitle("Tên và thẻ GIF").setView(form).setNegativeButton("Hủy", null)
                        .setPositiveButton("Lưu", (d, which) -> {
                            String label = name.getText().toString().trim(), keywords = tags.getText().toString().trim();
                            if (label.isEmpty() || label.length() > 160 || keywords.length() > 300) { status.setText("Tên cần 1–160 ký tự; thẻ tối đa 300 ký tự."); return; }
                            getPreferences(MODE_PRIVATE).edit().putString("gif_" + entry.id, label).putString("gif_tags_" + entry.id, keywords).apply(); renderGallery();
                        }).show();
                } else if (action == 1) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("gif_fav_" + entry.id, !entry.favorite).apply(); renderGallery();
                } else {
                    new AlertDialog.Builder(this).setTitle("Xóa GIF khỏi máy?").setMessage(entry.name)
                        .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, which) -> new Thread(() -> {
                            boolean deleted = !file.exists() || file.delete();
                            if (deleted) getPreferences(MODE_PRIVATE).edit().remove("gif_" + entry.id).remove("gif_tags_" + entry.id).remove("gif_fav_" + entry.id).apply();
                            runOnUiThread(() -> { if (deleted) { previewGeneration++; gifPreview.setImageDrawable(null); renderGallery(); } status.setText(deleted ? "Đã xóa GIF cục bộ." : "Không xóa được GIF."); });
                        }, "gif-delete").start()).show();
                }
            }).show();
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
            insert.setOnClickListener(v -> previewTemplate(templates.get(position)));
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

    private void previewTemplate(String template) {
        EditText recipient = new EditText(this);
        recipient.setSingleLine(true);
        recipient.setHint("Tên người nhận để thay {ten}");
        new AlertDialog.Builder(this).setTitle("Chuẩn bị mẫu tin")
            .setMessage("Hỗ trợ {ten}, {gio}, {ngay}. Tên chỉ dùng trong bản nháp cục bộ.")
            .setView(recipient).setNegativeButton("Hủy", null)
            .setPositiveButton("Xem trước", (dialog, which) -> {
                String name = recipient.getText().toString().trim();
                if (name.isEmpty()) name = "bạn";
                if (name.codePointCount(0, name.length()) > 160) {
                    status.setText("Tên người nhận quá dài."); return;
                }
                java.util.Date now = new java.util.Date();
                String time = new java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(now);
                String date = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(now);
                String text = nativeExpandTemplate(template, name, time, date);
                if (text == null) { status.setText("Không tạo được bản xem trước."); return; }
                new AlertDialog.Builder(this).setTitle("Xem trước • " + name)
                    .setMessage(text).setNegativeButton("Hủy", null)
                    .setPositiveButton("Chèn vào bản nháp", (confirm, choice) -> {
                        draft.append((draft.length() == 0 ? "" : "\n") + text);
                        status.setText("Đã chèn mẫu. Bản thử chưa gửi tin qua Zalo.");
                    }).show();
            }).show();
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
