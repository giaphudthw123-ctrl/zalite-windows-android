package org.ctdozlite.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Mobile navigation and local draft workspace. Never represents fixture chats as live data. */
final class MobileShell {
    private final Activity activity;
    private final Runnable tools;
    private final Runnable login;
    private final int blue = Color.rgb(0, 104, 255), muted = Color.rgb(116, 126, 141);
    private final String[] labels = {"Tin nhắn", "Danh bạ", "Khám phá", "Nhật ký", "Cá nhân"};
    private LinearLayout root, body, navigation;
    private int selected;
    MobileShell(Activity activity, Runnable tools, Runnable login) { this.activity = activity; this.tools = tools; this.login = login; }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    void show() {
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        activity.setContentView(root);
        root.requestApplyInsets();
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(16),dp(8),dp(12),dp(8)); header.setBackgroundColor(blue);
        EditText search = new EditText(activity); search.setSingleLine(true); search.setTextSize(16); search.setHint("Tìm kiếm"); search.setHintTextColor(0xffd9eaff); search.setTextColor(Color.WHITE); search.setBackgroundColor(Color.TRANSPARENT);
        header.addView(search, new LinearLayout.LayoutParams(0,dp(44),1));
        TextView add = text("+",30,Color.WHITE); add.setGravity(Gravity.CENTER); add.setContentDescription("Mở tiện ích cục bộ"); add.setOnClickListener(v -> tools.run()); header.addView(add,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(header);
        TextView notice = text("Zalite • chưa kết nối tài khoản Zalo",12,0xff526479); notice.setPadding(dp(16),dp(8),dp(16),dp(8)); notice.setBackgroundColor(0xffeef5ff); root.addView(notice);
        body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        navigation = new LinearLayout(activity); navigation.setPadding(0,dp(4),0,dp(4)); root.addView(navigation,new LinearLayout.LayoutParams(-1,dp(64)));
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            LinearLayout tab = new LinearLayout(activity); tab.setOrientation(LinearLayout.VERTICAL); tab.setGravity(Gravity.CENTER); tab.setContentDescription(labels[i]);
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(new MobileIcon(i)); tab.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
            tab.addView(text(labels[i],10,muted)); tab.setOnClickListener(v -> select(index)); navigation.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){ if(s.length()>0) { body.removeAllViews(); empty("Tìm kiếm hội thoại", "Chưa có dữ liệu tài khoản để tìm. GIF và mẫu tin có tìm kiếm riêng trong Tiện ích.", "Mở tiện ích", tools); } else select(selected); }
            public void afterTextChanged(android.text.Editable e){}
        });
        select(selected);
    }
    private void select(int index) {
        selected=index; body.removeAllViews();
        for(int i=0;i<navigation.getChildCount();i++) {
            LinearLayout tab=(LinearLayout)navigation.getChildAt(i); ((ImageView)tab.getChildAt(0)).setColorFilter(i==index?blue:muted); ((TextView)tab.getChildAt(1)).setTextColor(i==index?blue:muted);
        }
        if(index==0) {
            LinearLayout filters=new LinearLayout(activity); filters.setPadding(dp(16),dp(14),dp(16),dp(14));
            TextView all=text("Tất cả",16,blue); all.setTypeface(null,Typeface.BOLD); filters.addView(all); TextView unread=text("Chưa đọc",16,muted); unread.setPadding(dp(24),0,0,0); filters.addView(unread); body.addView(filters);
            empty("Tin nhắn của bạn", "Kết nối tài khoản bằng mã QR thiết bị đồng hành.", "Đăng nhập bằng QR", login);
        } else if(index==1) empty("Danh bạ", "Bạn bè  •  Nhóm  •  OA\nChưa có dữ liệu danh bạ Zalo được kết nối.", "Mở tiện ích", tools);
        else if(index==2) empty("Tiện ích của bạn", "Thư viện GIF cá nhân, mẫu trả lời nhanh và cập nhật ứng dụng.", "GIF và mẫu tin", tools);
        else if(index==3) empty("Nhật ký", "Chưa có kết nối dữ liệu Nhật ký Zalo.", "Về tin nhắn", () -> select(0));
        else empty("Cá nhân", "Zalite\nDữ liệu cục bộ trên thiết bị này", "Bản nháp của tôi", this::draft);
    }
    private void empty(String title,String description,String action,Runnable click) {
        LinearLayout space=new LinearLayout(activity); space.setOrientation(LinearLayout.VERTICAL); space.setGravity(Gravity.CENTER); space.setPadding(dp(28),dp(24),dp(28),dp(24));
        TextView heading=text(title,22,0xff172334); heading.setTypeface(null,Typeface.BOLD); heading.setGravity(Gravity.CENTER); space.addView(heading);
        TextView detail=text(description,14,muted); detail.setGravity(Gravity.CENTER); detail.setLineSpacing(dp(4),1); detail.setPadding(0,dp(14),0,dp(24)); space.addView(detail);
        Button button=new Button(activity); button.setText(action); button.setAllCaps(false); button.setTextColor(blue); button.setBackground(background(0xffe9f2ff,22)); button.setPadding(dp(24),0,dp(24),0); button.setOnClickListener(v->click.run()); space.addView(button,new LinearLayout.LayoutParams(-2,dp(48))); body.addView(space,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void draft() {
        root.removeAllViews();
        LinearLayout header=new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL); header.setBackgroundColor(blue); header.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button back=new Button(activity); back.setText("‹"); back.setOnClickListener(v->show()); header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout identity=new LinearLayout(activity); identity.setOrientation(LinearLayout.VERTICAL); identity.addView(text("Bản nháp của tôi",18,Color.WHITE)); identity.addView(text("Chỉ lưu trên thiết bị",12,0xffd9eaff)); header.addView(identity); root.addView(header);
        TextView canvas=text("Nội dung ở đây chưa được gửi qua Zalo.",13,muted); canvas.setGravity(Gravity.CENTER); canvas.setBackgroundColor(0xffeaf0f6); root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout toolbar=new LinearLayout(activity); toolbar.setPadding(dp(12),dp(4),dp(12),dp(4));
        Button gif=new Button(activity); gif.setText("GIF / Sticker"); gif.setOnClickListener(v->tools.run()); toolbar.addView(gif);
        Button templates=new Button(activity); templates.setText("Mẫu tin nhanh"); templates.setOnClickListener(v->tools.run()); toolbar.addView(templates); root.addView(toolbar);
        EditText input=new EditText(activity); input.setHint("Nhập nội dung bản nháp…"); input.setMinLines(2); input.setMaxLines(6); input.setPadding(dp(16),dp(12),dp(16),dp(12)); input.setText(activity.getPreferences(0).getString("local_chat_draft","")); root.addView(input);
        input.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){activity.getPreferences(0).edit().putString("local_chat_draft",s.toString()).apply();} public void afterTextChanged(android.text.Editable e){} });
    }
}
