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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Android prototype with explicit companion authentication and local tools. */
public final class MainActivity extends Activity implements MobileShell.SendActions {
    private static final int PICK_GIF = 14;
    private static final int MAX_GIF_BYTES = GifFiles.MAX_FILE_BYTES;
    private static final int MAX_TEMPLATES = 100;

    static { System.loadLibrary("ctdozlite_android"); }
    private native long nativeGifDimensions(byte[] bytes);
    private native String nativeNormalizeTemplate(String input);
    private native String nativeExpandTemplate(String input, String recipient, String time, String date);
    private native long nativeAuthStart();
    private native void nativeAuthCancel(long generation);
    private native String nativeAuthStatus(long generation);
    private native byte[] nativeAuthTake(long generation);
    private native String nativeAuthRestore(byte[] bytes, long expectedGeneration);
    private native long nativeAuthRestoreRevision();
    private native String nativeAuthLocalState();
    private native boolean nativeAuthIsSession(long generation);
    private native String nativeRecentConversations();
    private native void nativeRecentCancel();
    private native String nativePrepareSend(String account,String target,boolean group,String text);
    private native String nativeConfirmSend(long ticket);
    private native void nativeCancelSendPreview(long ticket);
    private native String nativeSendStatus(String account,String target,boolean group);
    private AlertDialog sendConfirmation;
    private boolean sendPreparing;
    private boolean recentLoading;
    private boolean sessionSaving;
    private final UiLifecycle uiLifecycle = new UiLifecycle();
    private long authGeneration;
    private Runnable resetQrUi;
    private final android.os.Handler authHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final ArrayList<String> templates = new ArrayList<>();
    private static final java.util.concurrent.ExecutorService TEMPLATE_IO=java.util.concurrent.Executors.newSingleThreadExecutor();
    private TemplateStore templateStore;
    private long templateRevision;
    private boolean templatesReady,templatesLoading,templatesSaving;
    private String templatesProblem="";
    private final ArrayList<AlertDialog> templateDialogs=new ArrayList<>();
    private AlertDialog templatePickerDialog;
    private Runnable refreshTemplatePicker;
    private LinearLayout galleryList;
    private LinearLayout templateList;
    private ImageView gifPreview;
    private EditText draft;
    private EditText templateSearch;
    private TextView status;
    private EditText gifSearch;
    private final ArrayList<GifGallery.Entry> galleryEntries = new ArrayList<>();
    private boolean favoritesOnly;
    private int galleryPage, galleryGeneration;
    private volatile int previewGeneration;
    private ScrollView toolsScreen;
    private LinearLayout toolsRoot;
    private static final java.util.concurrent.ExecutorService GIF_IO=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean galleryReady;
    private String galleryProblem="";
    private AlertDialog gifPickerDialog;
    private Runnable refreshGifPicker;
    private MobileShell mobileShell;
    private View gifSection,templateSection,updateSection;
    private static final java.util.concurrent.ExecutorService PRIVACY_IO=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean captureEnabled=true,captureLoaded,captureBusy;
    private String captureProblem="";
    private android.widget.Switch captureSwitch;
    private TextView captureStatus;
    private boolean captureRendering;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CaptureProtection.set(this,true);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        toolsScreen = scroll;
        toolsRoot=new LinearLayout(this);toolsRoot.setOrientation(LinearLayout.VERTICAL);toolsRoot.setBackgroundColor(android.graphics.Color.WHITE);
        toolsRoot.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        toolsRoot.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);
        scroll.addView(content);
        showTools();

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name) + " • bản thử Android");
        title.setTextSize(22);
        content.addView(title);
        TextView backToMessages = new TextView(this);
        backToMessages.setText("‹  Tiện ích của bạn");backToMessages.setTextSize(20);backToMessages.setTextColor(0xff172334);backToMessages.setPadding(pixels(16),pixels(16),pixels(16),pixels(16));backToMessages.setContentDescription("Quay lại màn hình Zalite");
        backToMessages.setOnClickListener(v -> mobileShell.show());
        toolsRoot.addView(backToMessages,0);
        TextView notice = new TextView(this);
        notice.setText("GIF và mẫu là thư viện trên thiết bị. Trong hội thoại, bấm Mẫu tin để xem trước và chèn; thao tác chèn không tự gửi.");
        content.addView(notice);
        status = new TextView(this);
        content.addView(status);
        Button updates = new Button(this);
        updates.setText("Kiểm tra và tải APK cập nhật từ GitHub");
        updates.setEnabled(!BuildConfig.UPDATE_REPO.isEmpty());
        updates.setOnClickListener(v -> checkUpdate(updates));
        content.addView(updates);
        updateSection=updates;
        if(!BuildConfig.UPDATE_REPO.isEmpty()) {
            TextView channel=new TextView(this);channel.setText("Kênh cập nhật: "+BuildConfig.UPDATE_REPO);content.addView(channel);
        }
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
        gifSection=importGif;
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
        templateSection=templateHeading;
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
        mobileShell = new MobileShell(this, this::showTools, this::showLogin, this::loadRecent,this,this::openUtility);
        mobileShell.show();
        handleSharedGif(getIntent());
        reloadTemplates();
        loadCaptureSetting();
    }

    private int pixels(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void showTools(){viewChanged();setContentView(toolsRoot);toolsRoot.requestApplyInsets();}
    @Override public void viewChanged(){previewGeneration++;stopGif(gifPreview);if(gifPickerDialog!=null)gifPickerDialog.cancel();}
    private void openUtility(String section) {
        if(section.equals("privacy")){showPrivacy();return;}
        showTools();
        View target=section.equals("gif")?gifSection:section.equals("templates")?templateSection:updateSection;
        toolsScreen.post(()->toolsScreen.smoothScrollTo(0,target.getTop()));
    }
    private void showPrivacy() {
        viewChanged();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(0xfff3f5f8);
        page.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        TextView back=new TextView(this);back.setText("‹  Bảo mật và riêng tư");back.setTextSize(20);back.setTextColor(0xff172334);back.setPadding(pixels(16),pixels(18),pixels(16),pixels(18));back.setBackgroundColor(android.graphics.Color.WHITE);back.setOnClickListener(v->mobileShell.show());page.addView(back);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(pixels(16),pixels(20),pixels(16),pixels(24));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        captureSwitch=new android.widget.Switch(this);captureSwitch.setText("Hạn chế chụp màn hình");captureSwitch.setTextSize(16);captureSwitch.setPadding(0,pixels(12),0,pixels(12));content.addView(captureSwitch);
        TextView explanation=new TextView(this);explanation.setText("Áp dụng cho cửa sổ và hộp thoại của Zalite trên Android. Hệ thống sẽ hạn chế ảnh chụp và chia sẻ màn hình. Không ngăn máy ảnh, thiết bị đã can thiệp, bàn phím hoặc ứng dụng chọn tệp bên ngoài. Đây không phải hạn chế lưu/chuyển tiếp ở phía người nhận.");explanation.setTextSize(14);explanation.setTextColor(0xff526479);explanation.setLineSpacing(pixels(4),1);content.addView(explanation);
        captureStatus=new TextView(this);captureStatus.setPadding(0,pixels(18),0,pixels(12));content.addView(captureStatus);
        Button retry=new Button(this);retry.setText("Đọc lại cài đặt");retry.setAllCaps(false);retry.setOnClickListener(v->loadCaptureSetting());content.addView(retry);
        TextView storage=new TextView(this);storage.setPadding(0,pixels(24),0,0);storage.setText("Phiên và kho mẫu được mã hóa riêng bằng Keystore. GIF và bản nháp cũ chưa được mã hóa toàn bộ. Chưa có khóa PIN hoặc sao lưu mã hóa chuyển máy trên Android.");storage.setTextColor(0xff526479);content.addView(storage);
        captureSwitch.setOnCheckedChangeListener((button,enabled)->{if(!captureRendering)saveCaptureSetting(enabled);});
        setContentView(page);page.requestApplyInsets();renderCaptureSetting();
    }
    private void renderCaptureSetting() {
        if(captureSwitch==null)return;
        captureRendering=true;captureSwitch.setChecked(captureEnabled);captureSwitch.setEnabled(captureLoaded&&!captureBusy);captureRendering=false;
        captureStatus.setText(captureBusy?"Đang đọc/lưu cài đặt…":!captureProblem.isEmpty()?captureProblem:captureEnabled?"Đang bật cho cửa sổ Zalite.":"Đang tắt. Nội dung có thể xuất hiện trong ảnh chụp màn hình.");
    }
    private void loadCaptureSetting() {
        if(captureBusy)return;captureBusy=true;renderCaptureSetting();
        PRIVACY_IO.execute(()->{boolean enabled=true;String problem="";try{enabled=new CaptureSettings(this).read();}catch(Exception error){problem="Không đọc được cài đặt. Giữ bảo vệ bật; hãy thử đọc lại trước khi thay đổi.";}final boolean value=enabled;final String message=problem;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;captureBusy=false;captureEnabled=value;captureLoaded=message.isEmpty();captureProblem=message;CaptureProtection.set(this,value);renderCaptureSetting();});});
    }
    private void saveCaptureSetting(boolean enabled) {
        if(!captureLoaded||captureBusy){renderCaptureSetting();return;}
        captureBusy=true;captureProblem="";
        // Protection is immediate when enabling; disabling waits for verified persistence.
        if(enabled){captureEnabled=true;CaptureProtection.set(this,true);}renderCaptureSetting();
        PRIVACY_IO.execute(()->{boolean ok=false;try{new CaptureSettings(this).save(enabled);ok=true;}catch(Exception error){}final boolean saved=ok;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;captureBusy=false;captureEnabled=saved?enabled:true;captureLoaded=saved;captureProblem=saved?"":"Không lưu/xác minh được cài đặt. Giữ bảo vệ bật; hãy đọc lại trước khi thử tiếp.";CaptureProtection.set(this,captureEnabled);renderCaptureSetting();});});
    }

    @Override protected void onStart() {
        super.onStart();
        uiLifecycle.resume();
        if(mobileShell!=null && authGeneration==0)restoreStoredSession();
        if(templateStore!=null&&!templatesLoading&&!templatesSaving)reloadTemplates();
    }
    @Override protected void onStop() {
        uiLifecycle.stop();
        previewGeneration++;stopGif(gifPreview);if(gifPickerDialog!=null)gifPickerDialog.cancel();
        for(AlertDialog dialog:new ArrayList<>(templateDialogs))dialog.cancel();
        if(sendConfirmation!=null)sendConfirmation.cancel();
        nativeRecentCancel();
        recentLoading=false;
        mobileShell.setRecentLoading(false);
        cancelAuth();
        super.onStop();
    }
    private void cancelAuth() {
        if(authGeneration!=0) {
            nativeAuthCancel(authGeneration);
            if(resetQrUi!=null)resetQrUi.run();
        }
        authGeneration=0;authHandler.removeCallbacksAndMessages(null);
    }
    private void restoreStoredSession() {
        final long uiTicket=uiLifecycle.ticket();
        final long revision=nativeAuthRestoreRevision();
        if(revision<0) {
            try {
                org.json.JSONObject local=new org.json.JSONObject(nativeAuthLocalState());
                if(local.optString("phase").equals("available")) mobileShell.setLocalSessionLabel("Phiên trong bộ nhớ: "+local.optString("name")+" • chưa đồng bộ tin");
            }catch(Exception error){mobileShell.setLocalSessionLabel("Không đọc được trạng thái phiên trong bộ nhớ.");}
            return;
        }
        new Thread(() -> {
            byte[] bytes=null;
            String label=null;
            boolean restored=false;
            try {
                SessionVault vault=new SessionVault(this);
                if(!vault.exists())return;
                bytes=vault.read();
                org.json.JSONObject result=new org.json.JSONObject(nativeAuthRestore(bytes,revision));
                String phase=result.optString("phase");
                if(phase.equals("restored_unverified")) { restored=true;label="Phiên trên máy: "+result.optString("name")+" • chưa xác minh máy chủ"; }
                else if(phase.equals("restore_error")) label="Không khôi phục được phiên trên máy. Tệp cũ vẫn được giữ.";
            } catch(Exception error) { label="Không mở được kho phiên bảo vệ. Tệp cũ vẫn được giữ."; }
            finally { if(bytes!=null)Arrays.fill(bytes,(byte)0); }
            final String message=label;
            final boolean installed=restored;
            if(message!=null)runOnUiThread(()->{if(uiLifecycle.accepts(uiTicket)&&authGeneration==0&&!isFinishing()&&!isDestroyed()){if(installed)mobileShell.clearRecent();mobileShell.setLocalSessionLabel(message);}});
        },"session-vault-restore").start();
    }
    private void loadRecent() {
        if(recentLoading)return;
        final long uiTicket=uiLifecycle.ticket();
        recentLoading=true;mobileShell.setRecentLoading(true);
        new Thread(()->{
            String response=nativeRecentConversations();
            runOnUiThread(()->{
                if(!uiLifecycle.accepts(uiTicket))return;
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

    private static org.json.JSONObject sendResult(String phase,String message) {
        org.json.JSONObject value=new org.json.JSONObject();
        try{value.put("phase",phase);if(message!=null)value.put("message",message);}catch(org.json.JSONException error){throw new IllegalStateException(error);}
        return value;
    }
    @Override public void status(String account,String target,boolean group,java.util.function.Consumer<org.json.JSONObject> done) {
        try{done.accept(new org.json.JSONObject(nativeSendStatus(account,target,group)));}
        catch(Exception error){done.accept(sendResult("error","Không đọc được trạng thái lượt gửi."));}
    }
    @Override public void request(String account,String target,boolean group,String name,String text,java.util.function.Consumer<org.json.JSONObject> done) {
        if(sendPreparing){done.accept(sendResult("error","Đang xử lý lượt gửi trước."));return;}
        sendPreparing=true;final long uiTicket=uiLifecycle.ticket();
        new Thread(()->{
            org.json.JSONObject parsed;
            try{parsed=new org.json.JSONObject(nativePrepareSend(account,target,group,text));}catch(Exception error){parsed=sendResult("error","Không chuẩn bị được lượt gửi.");}
            final org.json.JSONObject prepared=parsed;
            runOnUiThread(()->{
                long ticket=prepared.optLong("ticket");
                if(!uiLifecycle.accepts(uiTicket)||isFinishing()||isDestroyed()){if(ticket!=0)nativeCancelSendPreview(ticket);sendPreparing=false;return;}
                if(!prepared.optString("phase").equals("prepared")||ticket==0){sendPreparing=false;done.accept(prepared);return;}
                sendConfirmation=new CaptureProtection.Builder(this).setTitle("Xác nhận gửi thử nghiệm")
                    .setMessage("Từ tài khoản: "+account+"\nĐến: "+SendStatus.previewName(name)+"\nMã "+(group?"nhóm: ":"liên hệ: ")+target+"\n\n"+text+"\n\nLuồng Android chưa được kiểm chứng với tài khoản thật. Nếu mất phản hồi, không tự gửi lại; hãy kiểm tra lịch sử trước.")
                    .setNegativeButton("Hủy",(dialog,which)->{nativeCancelSendPreview(ticket);sendPreparing=false;done.accept(sendResult("cancelled",null));})
                    .setOnCancelListener(dialog->{nativeCancelSendPreview(ticket);sendPreparing=false;done.accept(sendResult("cancelled",null));})
                    .setPositiveButton("Gửi một lần",(dialog,which)->{
                        if(!uiLifecycle.accepts(uiTicket)){nativeCancelSendPreview(ticket);sendPreparing=false;done.accept(sendResult("cancelled",null));return;}
                        new Thread(()->{
                            org.json.JSONObject result;
                            try{result=new org.json.JSONObject(nativeConfirmSend(ticket));}catch(Exception error){result=sendResult("unknown",null);}
                            final org.json.JSONObject receipt=result;
                            runOnUiThread(()->{sendPreparing=false;if(uiLifecycle.accepts(uiTicket)&&!isFinishing()&&!isDestroyed())done.accept(receipt);});
                        },"confirmed-text-send").start();
                    }).create();
                sendConfirmation.setOnDismissListener(dialog->sendConfirmation=null);
                sendConfirmation.show();
            });
        },"prepare-text-send").start();
    }
    private void showLogin() {
        if(sessionSaving){Toast.makeText(this,"Đang bảo vệ phiên. Vui lòng chờ lưu xong.",Toast.LENGTH_SHORT).show();return;}
        cancelAuth();
        uiLifecycle.invalidate();nativeRecentCancel();recentLoading=false;mobileShell.setRecentLoading(false);
        mobileShell.clearRecent();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,48,24,48);page.setBackgroundColor(0xffffffff);
        TextView heading=new TextView(this);heading.setText("Đăng nhập bằng QR");heading.setTextSize(24);page.addView(heading);
        TextView info=new TextView(this);info.setText("Luồng thiết bị đồng hành thử nghiệm. Khi tạo mã, dùng Zalo trên điện thoại đã đăng nhập để quét và xác nhận. Chưa hỗ trợ đăng nhập tài khoản chính bằng số điện thoại.");page.addView(info);
        ImageView qr=new ImageView(this);qr.setAdjustViewBounds(true);page.addView(qr,new LinearLayout.LayoutParams(-1,320));
        TextView progress=new TextView(this);progress.setText("Chưa tạo mã. Không quét bằng tài khoản thật khi chưa kiểm chứng xong.");page.addView(progress);
        Button start=new Button(this);start.setText("Tạo mã QR");page.addView(start);
        resetQrUi=()->{qr.setImageDrawable(null);start.setEnabled(true);progress.setText("Đã hủy mã QR khi rời màn hình. Bấm Tạo mã QR để thử lại.");};
        Button cancel=new Button(this);cancel.setText("Hủy và về Tin nhắn");cancel.setOnClickListener(v->{cancelAuth();qr.setImageDrawable(null);mobileShell.show();});page.addView(cancel);
        Button check=new Button(this);check.setText("Kiểm tra kho bảo vệ bằng dữ liệu giả");page.addView(check);
        check.setOnClickListener(v->{ check.setEnabled(false);new Thread(()->{boolean ok;try{SessionVault.selfTest(this);TemplateStore.selfTest(this,this::nativeNormalizeTemplate);ok=true;}catch(Exception error){ok=false;}final boolean passed=ok;runOnUiThread(()->{check.setEnabled(true);progress.setText(passed?"Kiểm tra Keystore và kho mẫu: mã hóa, chuyển đổi, giữ dữ liệu lỗi và chặn ghi cũ đều qua.":"Thiết bị chưa vượt qua kiểm tra kho bảo vệ.");});},"vault-selftest").start(); });
        start.setOnClickListener(v->{cancelAuth();start.setEnabled(false);authGeneration=nativeAuthStart();final long generation=authGeneration;
            if(generation==0){start.setEnabled(true);progress.setText("Không bắt đầu được đăng nhập. Hãy thử lại.");return;}
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
                        // Authentication is complete: leaving this screen must not cancel the Session.
                        authGeneration=0;resetQrUi=null;sessionSaving=true;
                        cancel.setText("Về Tin nhắn");cancel.setOnClickListener(v->mobileShell.show());
                        mobileShell.setLocalSessionLabel("Phiên tạm trong bộ nhớ • đang lưu bảo vệ");
                        new Thread(()->{boolean ok=false;try{ok=new SessionVault(MainActivity.this).saveIfCurrent(session,()->nativeAuthIsSession(generation));}catch(Exception error){}finally{if(session!=null)Arrays.fill(session,(byte)0);}final boolean saved=ok;runOnUiThread(()->{sessionSaving=false;if(isFinishing()||isDestroyed()||!nativeAuthIsSession(generation))return;start.setEnabled(true);mobileShell.setLocalSessionLabel(saved?"Đã lưu phiên bảo vệ • chưa đồng bộ tin":"Phiên chỉ ở bộ nhớ • chưa lưu được khi đóng ứng dụng");progress.setText(saved?"Đã lưu phiên bảo vệ. Về Tin nhắn để tải tin gần đây.":"Không lưu được phiên bảo vệ. Có thể tiếp tục trong phiên tạm; đóng ứng dụng có thể cần đăng nhập lại.");});},"session-vault-save").start();return;
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
        showTools();
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
        final long importTicket=uiLifecycle.ticket();
        GIF_IO.execute(() -> {
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
            Drawable preview = decodeGif(bytes);
            String id = gifFiles().put(bytes);
            String name = displayName(uri);
            if(!getPreferences(MODE_PRIVATE).contains("gif_"+id))getPreferences(MODE_PRIVATE).edit().putString("gif_" + id, name).apply();
            runOnUiThread(() -> {
                if(isFinishing()||isDestroyed())return;
                if(uiLifecycle.accepts(importTicket))showPreview(preview);
                status.setText("Đã nhập " + name + " (" + bytes.length + " byte)");
                renderGallery();
            });
        } catch (Exception error) {
            runOnUiThread(() -> {if(!isDestroyed())status.setText("Không nhập được GIF. Kiểm tra quyền đọc và định dạng; tối đa 8 MiB/tệp, 100 GIF hoặc 256 MiB cho thư viện.");});
        }
        });
    }

    private void renderGallery() {
        final int generation = ++galleryGeneration;
        galleryReady=false;galleryProblem="";
        GIF_IO.execute(() -> {
            ArrayList<GifGallery.Entry> entries = new ArrayList<>();
            String problem="";
            try {for (String id : gifFiles().ids()) {
                entries.add(new GifGallery.Entry(id,
                    getPreferences(MODE_PRIVATE).getString("gif_" + id, "GIF " + id.substring(0, 8)),
                    getPreferences(MODE_PRIVATE).getString("gif_tags_" + id, ""),
                    getPreferences(MODE_PRIVATE).getBoolean("gif_fav_" + id, false)));
            }}catch(Exception error){problem="Không mở được thư viện GIF. Tệp hiện có được giữ lại.";}
            final String failure=problem;
            runOnUiThread(() -> {
                if (generation != galleryGeneration || isFinishing() || isDestroyed()) return;
                galleryReady=failure.isEmpty();galleryProblem=failure;galleryEntries.clear();galleryEntries.addAll(entries);renderGalleryRows();if(refreshGifPicker!=null)refreshGifPicker.run();
            });
        });
    }

    private void renderGalleryRows() {
        if (galleryList == null) return;
        galleryList.removeAllViews();
        if(!galleryReady){TextView note=new TextView(this);note.setText(galleryProblem.isEmpty()?"Đang mở thư viện GIF…":galleryProblem);galleryList.addView(note);return;}
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
            String name = entry.name;
            Button button = new Button(this);
            button.setText((entry.favorite ? "★ " : "") + name);
            button.setOnClickListener(v -> {
                final int generation = ++previewGeneration;
                status.setText("Đang mở GIF…");
                GIF_IO.execute(() -> {
                    if(generation!=previewGeneration)return;
                    try {
                        Drawable preview = decodeGif(gifFiles().read(entry.id));
                        runOnUiThread(() -> {
                            if (generation != previewGeneration || isFinishing() || isDestroyed()) return;
                            showPreview(preview);
                            status.setText("Xem trước " + name + " • chưa gửi qua Zalo");
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> {if(generation==previewGeneration&&!isDestroyed())status.setText("Không xem được GIF: tệp thiếu, bị thay đổi hoặc không hợp lệ.");});
                    }
                });
            });
            galleryList.addView(button);
            Button manage = new Button(this);
            manage.setText("Tên, thẻ, yêu thích hoặc xóa");
            manage.setOnClickListener(v -> manageGif(entry));
            galleryList.addView(manage);
        }
        LinearLayout pages = new LinearLayout(this);
        Button previous = new Button(this); previous.setText("Trang trước"); previous.setEnabled(galleryPage > 0);
        previous.setOnClickListener(v -> { galleryPage--; renderGalleryRows(); }); pages.addView(previous);
        Button next = new Button(this); next.setText("Trang sau"); next.setEnabled(galleryPage + 1 < GifGallery.pageCount(selected.size()));
        next.setOnClickListener(v -> { galleryPage++; renderGalleryRows(); }); pages.addView(next); galleryList.addView(pages);
    }

    private void manageGif(GifGallery.Entry entry) {
        new CaptureProtection.Builder(this).setTitle(entry.name)
            .setItems(new String[]{"Sửa tên và thẻ", entry.favorite ? "Bỏ yêu thích" : "Thêm yêu thích", "Xóa GIF"}, (dialog, action) -> {
                if (action == 0) {
                    LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
                    EditText name = new EditText(this); name.setSingleLine(true); name.setText(entry.name); form.addView(name);
                    EditText tags = new EditText(this); tags.setSingleLine(true); tags.setHint("Thẻ: vui, mèo, chào hỏi…"); tags.setText(entry.tags); form.addView(tags);
                    new CaptureProtection.Builder(this).setTitle("Tên và thẻ GIF").setView(form).setNegativeButton("Hủy", null)
                        .setPositiveButton("Lưu", (d, which) -> {
                            String label = name.getText().toString().trim(), keywords = tags.getText().toString().trim();
                            if (label.isEmpty() || label.length() > 160 || keywords.length() > 300) { status.setText("Tên cần 1–160 ký tự; thẻ tối đa 300 ký tự."); return; }
                            getPreferences(MODE_PRIVATE).edit().putString("gif_" + entry.id, label).putString("gif_tags_" + entry.id, keywords).apply(); renderGallery();
                        }).show();
                } else if (action == 1) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("gif_fav_" + entry.id, !entry.favorite).apply(); renderGallery();
                } else {
                    new CaptureProtection.Builder(this).setTitle("Xóa GIF khỏi máy?").setMessage(entry.name)
                        .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, which) -> GIF_IO.execute(() -> {
                            boolean removed=false;try{gifFiles().remove(entry.id);removed=true;}catch(Exception error){}final boolean deleted=removed;
                            if (deleted) getPreferences(MODE_PRIVATE).edit().remove("gif_" + entry.id).remove("gif_tags_" + entry.id).remove("gif_fav_" + entry.id).apply();
                            runOnUiThread(() -> { if (deleted) { previewGeneration++; stopGif(gifPreview); renderGallery(); } status.setText(deleted ? "Đã xóa GIF cục bộ." : "Không xóa được GIF."); });
                        })).show();
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
                    new CaptureProtection.Builder(this)
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
        stopGif(gifPreview);
        gifPreview.setImageDrawable(drawable);
        if (drawable instanceof AnimatedImageDrawable) {((AnimatedImageDrawable) drawable).setRepeatCount(1);((AnimatedImageDrawable) drawable).start();}
    }
    @Override public void gif(String recipient,java.util.function.Consumer<GifChoice> select) {
        if(gifPickerDialog!=null&&gifPickerDialog.isShowing())return;
        final long ticket=uiLifecycle.ticket();final java.util.concurrent.atomic.AtomicLong request=new java.util.concurrent.atomic.AtomicLong();final int[] page={0};final GifChoice[] selected={null};final boolean[] favorites={false};
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(pixels(12),0,pixels(12),0);
        TextView note=new TextView(this);note.setText("Chọn GIF cho bản nháp. Chưa gửi GIF qua Zalo.");note.setTextSize(12);content.addView(note);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Tìm GIF theo tên hoặc thẻ");content.addView(search);
        android.widget.CheckBox favorite=new android.widget.CheckBox(this);favorite.setText("Chỉ yêu thích");content.addView(favorite);
        ScrollView rowsScroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rowsScroll.addView(rows);content.addView(rowsScroll,new LinearLayout.LayoutParams(-1,pixels(100)));
        LinearLayout pages=new LinearLayout(this);Button previous=new Button(this);previous.setText("‹");previous.setContentDescription("Trang GIF trước");Button next=new Button(this);next.setText("›");next.setContentDescription("Trang GIF sau");TextView count=new TextView(this);count.setGravity(android.view.Gravity.CENTER);pages.addView(previous,new LinearLayout.LayoutParams(pixels(48),pixels(42)));pages.addView(count,new LinearLayout.LayoutParams(0,pixels(42),1));pages.addView(next,new LinearLayout.LayoutParams(pixels(48),pixels(42)));content.addView(pages);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setContentDescription("Xem trước GIF đã chọn");content.addView(preview,new LinearLayout.LayoutParams(-1,pixels(80)));
        TextView details=new TextView(this);details.setTextSize(12);details.setText("Chạm một GIF để kiểm tra và xem trước.");content.addView(details);
        AlertDialog dialog=new CaptureProtection.Builder(this).setTitle("GIF • "+SendStatus.previewName(recipient)).setView(content).setNegativeButton("Đóng",null).setPositiveButton("Chọn vào bản nháp",null).create();gifPickerDialog=dialog;
        Runnable refresh=()->{
            request.incrementAndGet();selected[0]=null;stopGif(preview);if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);rows.removeAllViews();
            if(!galleryReady){count.setText("…");previous.setEnabled(false);next.setEnabled(false);TextView message=new TextView(this);message.setText(galleryProblem.isEmpty()?"Đang mở thư viện…":galleryProblem);rows.addView(message);return;}
            java.util.List<GifGallery.Entry> matching=GifGallery.filter(galleryEntries,search.getText().toString(),favorites[0]);page[0]=Math.max(0,Math.min(page[0],GifGallery.pageCount(matching.size())-1));count.setText(matching.size()+" GIF • "+(page[0]+1)+"/"+GifGallery.pageCount(matching.size()));previous.setEnabled(page[0]>0);next.setEnabled(page[0]+1<GifGallery.pageCount(matching.size()));
            if(matching.isEmpty()){TextView empty=new TextView(this);empty.setText("Chưa có GIF phù hợp. Nhập GIF ở Cá nhân → GIF.");rows.addView(empty);}
            for(GifGallery.Entry entry:GifGallery.page(matching,page[0])){
                Button pick=new Button(this);pick.setAllCaps(false);pick.setText((entry.favorite?"★ ":"")+entry.name);rows.addView(pick,new LinearLayout.LayoutParams(-1,-2));
                pick.setOnClickListener(v->{long generation=request.incrementAndGet();selected[0]=null;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);stopGif(preview);details.setText("Đang kiểm tra tệp và mở xem trước…");
                    GIF_IO.execute(()->{if(generation!=request.get())return;try{byte[] bytes=gifFiles().read(entry.id);long size=nativeGifDimensions(bytes);Drawable drawable=decodeGif(bytes,size);GifChoice choice=new GifChoice(entry.id,entry.name,bytes.length,size);
                        runOnUiThread(()->{if(!uiLifecycle.accepts(ticket)||!dialog.isShowing()||generation!=request.get()||isFinishing()||isDestroyed())return;selected[0]=choice;preview.setImageDrawable(drawable);if(drawable instanceof AnimatedImageDrawable){((AnimatedImageDrawable)drawable).setRepeatCount(1);((AnimatedImageDrawable)drawable).start();}details.setText(choice.name+" • "+choice.width+"×"+choice.height+" • "+choice.bytes+" byte\nChạm ảnh để phát lại; chưa gửi.");preview.setOnClickListener(image->{if(drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)drawable).start();});dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);});
                    }catch(Exception error){runOnUiThread(()->{if(dialog.isShowing()&&generation==request.get())details.setText("Không mở được GIF: tệp thiếu, bị thay đổi hoặc không hợp lệ. Bản nháp chưa đổi.");});}});
                });
            }
        };
        refreshGifPicker=refresh;previous.setOnClickListener(v->{page[0]--;refresh.run();});next.setOnClickListener(v->{page[0]++;refresh.run();});favorite.setOnCheckedChangeListener((button,checked)->{favorites[0]=checked;page[0]=0;refresh.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){page[0]=0;refresh.run();}public void afterTextChanged(Editable value){}});
        dialog.setOnDismissListener(value->{request.incrementAndGet();stopGif(preview);if(gifPickerDialog==dialog){gifPickerDialog=null;refreshGifPicker=null;}});dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(selected[0]==null||!uiLifecycle.accepts(ticket))return;GifChoice choice=selected[0];dialog.dismiss();select.accept(choice);});
        refresh.run();renderGallery();
    }
    private GifFiles gifFiles(){return new GifFiles(new File(getFilesDir(),"personal_gifs"));}
    private static void stopGif(ImageView view){if(view!=null){Drawable old=view.getDrawable();if(old instanceof AnimatedImageDrawable)((AnimatedImageDrawable)old).stop();view.setImageDrawable(null);view.setOnClickListener(null);}}
    private Drawable decodeGif(byte[] bytes) throws Exception {return decodeGif(bytes,nativeGifDimensions(bytes));}
    private Drawable decodeGif(byte[] bytes,long dimensions) throws Exception {
        if(dimensions==0)throw new java.io.IOException("GIF không hợp lệ");
        return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),(decoder,info,source)->{
            if(!"image/gif".equals(info.getMimeType()))throw new IllegalArgumentException("Không phải GIF");
            int width=info.getSize().getWidth(),height=info.getSize().getHeight();double scale=Math.min(1.0,384.0/Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setOnPartialImageListener(error->false);
        });
    }

    private void reloadTemplates() {
        if(templatesLoading||templatesSaving)return;
        templatesLoading=true;templatesReady=false;templatesProblem="";renderTemplates();
        TEMPLATE_IO.execute(()->{
            TemplateStore store=null;
            TemplateStore.Snapshot snapshot=null;
            try{store=new TemplateStore(this,getPreferences(MODE_PRIVATE),this::nativeNormalizeTemplate);snapshot=store.load();}catch(Exception error){}
            final TemplateStore.Snapshot loaded=snapshot;
            final TemplateStore openedStore=store;
            runOnUiThread(()->{
                if(isDestroyed())return;
                templatesLoading=false;templateStore=openedStore;
                if(loaded!=null){templates.clear();templates.addAll(loaded.items);templateRevision=loaded.revision;templatesReady=true;}
                else templatesProblem="Không mở được kho mẫu bảo vệ. Dữ liệu cũ vẫn được giữ; hãy thử lại.";
                renderTemplates();if(refreshTemplatePicker!=null)refreshTemplatePicker.run();
            });
        });
    }

    private void persistTemplates(ArrayList<String> proposed,java.util.function.Consumer<Boolean> done) {
        if(!templatesReady||templatesSaving||templateStore==null){Toast.makeText(this,"Kho mẫu chưa sẵn sàng hoặc đang lưu.",Toast.LENGTH_SHORT).show();done.accept(false);return;}
        templatesSaving=true;final TemplateStore destination=templateStore;final long revision=templateRevision;
        TEMPLATE_IO.execute(()->{
            TemplateStore.Snapshot snapshot=null;
            try{snapshot=destination.save(revision,proposed);}catch(Exception error){}
            final TemplateStore.Snapshot saved=snapshot;
            runOnUiThread(()->{
                if(isDestroyed())return;
                templatesSaving=false;
                if(saved!=null){templates.clear();templates.addAll(saved.items);templateRevision=saved.revision;templatesReady=true;templatesProblem="";}
                else{templatesReady=false;templatesProblem="Không lưu hoặc xác minh được kho mẫu. Giữ bản nhập và mở lại kho trước khi thử tiếp.";}
                renderTemplates();if(refreshTemplatePicker!=null)refreshTemplatePicker.run();done.accept(saved!=null);
            });
        });
    }

    private AlertDialog showTemplateDialog(AlertDialog.Builder builder) {
        AlertDialog dialog=builder.create();templateDialogs.add(dialog);
        dialog.setOnDismissListener(value->templateDialogs.remove(dialog));dialog.show();return dialog;
    }

    private void renderTemplates() {
        if(templateList==null)return;
        templateList.removeAllViews();
        if(!templatesReady){
            TextView note=new TextView(this);note.setText(templatesLoading?"Đang mở kho mẫu bảo vệ…":templatesProblem.isEmpty()?"Kho mẫu chưa mở.":templatesProblem);templateList.addView(note);
            Button retry=new Button(this);retry.setText("Mở lại kho mẫu");retry.setEnabled(!templatesLoading&&!templatesSaving);retry.setOnClickListener(v->reloadTemplates());templateList.addView(retry);return;
        }
        String query=templateSearch==null?"":templateSearch.getText().toString();
        for(int i=0;i<templates.size();i++) {
            final int position=i;final String text=templates.get(i);
            if(!ConversationSearch.matches(text,"",query))continue;
            LinearLayout row=new LinearLayout(this);
            Button pick=new Button(this);pick.setAllCaps(false);pick.setText(text);pick.setOnClickListener(v->previewTemplate(text));row.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=new Button(this);edit.setText("Sửa");edit.setOnClickListener(v->editTemplate(position));row.addView(edit);
            Button remove=new Button(this);remove.setText("Xóa");remove.setOnClickListener(v->{
                if(!templatesReady||templatesSaving)return;
                final long revision=templateRevision;
                showTemplateDialog(new CaptureProtection.Builder(this).setMessage("Xóa mẫu này khỏi máy?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(dialog,which)->{
                    if(revision!=templateRevision){Toast.makeText(this,"Kho mẫu đã thay đổi. Hãy chọn lại.",Toast.LENGTH_SHORT).show();return;}
                    ArrayList<String> next=new ArrayList<>(templates);next.remove(position);persistTemplates(next,ok->{});
                }));
            });row.addView(remove);templateList.addView(row);
        }
    }

    @Override public void template(String recipient,java.util.function.Consumer<String> insert) {
        if(templatePickerDialog!=null&&templatePickerDialog.isShowing())return;
        final long ticket=uiLifecycle.ticket();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(16,8,16,8);
        TextView note=new TextView(this);note.setText("Chèn vào bản nháp, không tự gửi. {ten}, {gio}, {ngay} được thay cục bộ.");content.addView(note);
        Button create=new Button(this);create.setAllCaps(false);create.setText("Tạo mẫu mới");create.setOnClickListener(v->editTemplate(-1));content.addView(create);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Tìm mẫu tin…");content.addView(search);
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.round(240*getResources().getDisplayMetrics().density)));
        Runnable refresh=()->{
            rows.removeAllViews();
            if(!templatesReady){TextView message=new TextView(this);message.setText(templatesLoading?"Đang mở kho mẫu…":templatesProblem);rows.addView(message);Button retry=new Button(this);retry.setText("Mở lại kho mẫu");retry.setEnabled(!templatesLoading&&!templatesSaving);retry.setOnClickListener(v->reloadTemplates());rows.addView(retry);return;}
            for(int i=0;i<templates.size();i++){
                final int index=i;final String text=templates.get(i);
                if(!ConversationSearch.matches(text,"",search.getText().toString()))continue;
                LinearLayout row=new LinearLayout(this);
                Button pick=new Button(this);pick.setAllCaps(false);pick.setText(text);pick.setMaxLines(2);pick.setEllipsize(android.text.TextUtils.TruncateAt.END);pick.setOnClickListener(v->{if(uiLifecycle.accepts(ticket))previewExpandedTemplate(text,recipient,insert);});row.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
                Button edit=new Button(this);edit.setAllCaps(false);edit.setText("Sửa");edit.setOnClickListener(v->editTemplate(index));row.addView(edit,new LinearLayout.LayoutParams(Math.round(64*getResources().getDisplayMetrics().density),-2));rows.addView(row);
            }
            if(rows.getChildCount()==0){TextView empty=new TextView(this);empty.setText(templates.isEmpty()?"Chưa có mẫu. Bấm Tạo mẫu mới.":"Không tìm thấy mẫu phù hợp.");rows.addView(empty);}
        };
        refreshTemplatePicker=refresh;refresh.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(Editable e){}});
        AlertDialog picker=new CaptureProtection.Builder(this).setTitle("Mẫu tin nhanh • trên máy").setView(content).setNegativeButton("Đóng",null).create();templatePickerDialog=picker;templateDialogs.add(picker);
        picker.setOnDismissListener(dialog->{templateDialogs.remove(picker);if(templatePickerDialog==picker){templatePickerDialog=null;refreshTemplatePicker=null;}});picker.show();
    }

    private void previewExpandedTemplate(String template,String recipient,java.util.function.Consumer<String> insert) {
        final long ticket=uiLifecycle.ticket();String name=SendStatus.previewName(recipient);if(name.isEmpty())name="bạn";
        java.util.Date now=new java.util.Date();String time=new java.text.SimpleDateFormat("HH:mm",Locale.ROOT).format(now),date=new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(now);
        String expanded=nativeExpandTemplate(template,name,time,date);
        if(expanded==null||expanded.length()>4000){Toast.makeText(this,"Mẫu sau khi thay biến quá dài hoặc không hợp lệ.",Toast.LENGTH_LONG).show();return;}
        final String text=expanded;
        showTemplateDialog(new CaptureProtection.Builder(this).setTitle("Xem trước • "+name).setMessage(text).setNegativeButton("Hủy",null).setPositiveButton("Chèn vào ô nhập",(dialog,which)->{
            if(!uiLifecycle.accepts(ticket))return;
            if(templatePickerDialog!=null)templatePickerDialog.dismiss();insert.accept(text);
        }));
    }

    private void previewTemplate(String template) {
        EditText recipient=new EditText(this);recipient.setSingleLine(true);recipient.setHint("Tên người nhận để thay {ten}");
        showTemplateDialog(new CaptureProtection.Builder(this).setTitle("Chuẩn bị mẫu tin").setView(recipient).setNegativeButton("Hủy",null).setPositiveButton("Xem trước",(dialog,which)->previewExpandedTemplate(template,recipient.getText().toString(),text->draft.append((draft.length()==0?"":"\n")+text))));
    }

    private void editTemplate(int position) {
        if(!templatesReady||templatesSaving){Toast.makeText(this,"Kho mẫu chưa sẵn sàng hoặc đang lưu.",Toast.LENGTH_SHORT).show();return;}
        if(position<0&&templates.size()>=MAX_TEMPLATES){Toast.makeText(this,"Bản thử hiện hỗ trợ 100 mẫu; không phải hạn mức Zalo.",Toast.LENGTH_LONG).show();return;}
        if(position>=templates.size())return;
        final long revision=templateRevision;
        EditText text=new EditText(this);text.setMinLines(2);text.setMaxLines(5);text.setHint("Nội dung mẫu, hỗ trợ {ten}, {gio}, {ngay}");
        if(position>=0)text.setText(templates.get(position));
        AlertDialog editor=showTemplateDialog(new CaptureProtection.Builder(this).setTitle(position<0?"Tạo mẫu":"Sửa mẫu").setView(text).setNegativeButton("Hủy",null).setPositiveButton("Lưu",null));
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String normalized=nativeNormalizeTemplate(text.getText().toString());
            if(normalized==null){text.setError("Mẫu phải có 1–120 ký tự hợp lệ.");return;}
            if(revision!=templateRevision){text.setError("Kho mẫu đã thay đổi. Hãy mở lại mẫu.");return;}
            ArrayList<String> next=new ArrayList<>(templates);if(position<0)next.add(normalized);else next.set(position,normalized);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            persistTemplates(next,ok->{if(ok)editor.dismiss();else{editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);text.setError("Không lưu được. Bản nhập vẫn ở đây; hãy thử mở lại kho.");}});
        });
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

}
