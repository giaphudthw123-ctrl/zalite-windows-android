package org.ctdozlite.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Mobile navigation and local draft workspace. Never represents fixture chats as live data. */
final class MobileShell {
    interface SendActions {
        void template(String recipient,java.util.function.Consumer<String> insert);
        void request(String account,String target,boolean group,String name,String text,java.util.function.Consumer<org.json.JSONObject> done);
        void status(String account,String target,boolean group,java.util.function.Consumer<org.json.JSONObject> done);
    }
    private final SendActions sends;
    private final Activity activity;
    private final Runnable tools;
    private final Runnable login;
    private final Runnable refresh;
    private org.json.JSONArray conversations=new org.json.JSONArray();
    private boolean demo, limited, recentLoading, inConversation;
    private final java.util.Map<String,String> drafts=new java.util.HashMap<>();
    private String conversationAccount="";
    private String searchQuery="";
    private String activeConversationKey="";
    private long composerGeneration;
    void clearRecent(){conversations=new org.json.JSONArray();drafts.clear();demo=false;limited=false;conversationAccount="";searchQuery="";selected=0;if(root!=null)show();}
    void setRecentLoading(boolean loading) { recentLoading=loading;if(selected==0&&!inConversation&&body!=null)select(0); }
    void acceptRecent(org.json.JSONObject data,boolean isDemo) {
        String account=data.optString("account");
        if(!isDemo&&!account.equals(conversationAccount)){drafts.clear();conversationAccount=account;}
        conversations=data.optJSONArray("conversations");if(conversations==null)conversations=new org.json.JSONArray();
        demo=isDemo;limited=data.optBoolean("limited");if(selected==0&&!inConversation&&body!=null)select(0);
    }
    private final int blue = Color.rgb(0, 104, 255), muted = Color.rgb(116, 126, 141);
    private final String[] labels = {"Tin nhắn", "Danh bạ", "Khám phá", "Nhật ký", "Cá nhân"};
    private LinearLayout root, body, navigation;
    private int selected;
    private String localSessionLabel = "Zalite • chưa kết nối tài khoản Zalo";
    private TextView connectionNote;
    void setLocalSessionLabel(String value) { localSessionLabel=value; if(connectionNote!=null)connectionNote.setText(value); }
    MobileShell(Activity activity, Runnable tools, Runnable login,Runnable refresh,SendActions sends) { this.activity = activity; this.tools = tools; this.login = login;this.refresh=refresh;this.sends=sends; }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private ImageButton icon(int kind,String description,int color,Runnable action) {
        ImageButton button=new ImageButton(activity);button.setImageDrawable(new MobileIcon(kind));button.setColorFilter(color);button.setBackgroundColor(Color.TRANSPARENT);button.setPadding(dp(12),dp(12),dp(12),dp(12));button.setContentDescription(description);button.setTooltipText(description);button.setOnClickListener(v->action.run());return button;
    }
    void show() {
        composerGeneration++;
        inConversation=false;
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        activity.setContentView(root);
        root.requestApplyInsets();
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(16),dp(8),dp(12),dp(8)); header.setBackgroundColor(blue);
        EditText search = new EditText(activity); search.setSingleLine(true); search.setTextSize(16); search.setHint("Tìm kiếm"); search.setHintTextColor(0xffd9eaff); search.setTextColor(Color.WHITE); search.setBackgroundColor(Color.TRANSPARENT);
        search.setText(searchQuery);
        ImageView magnifier=new ImageView(activity);magnifier.setImageDrawable(new MobileIcon(5));magnifier.setColorFilter(Color.WHITE);header.addView(magnifier,new LinearLayout.LayoutParams(dp(24),dp(24)));
        header.addView(search, new LinearLayout.LayoutParams(0,dp(44),1));
        header.addView(icon(6,"Mở tiện ích cục bộ",Color.WHITE,tools),new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(header);
        TextView notice = text(localSessionLabel,12,0xff526479); connectionNote=notice; notice.setPadding(dp(16),dp(8),dp(16),dp(8)); notice.setBackgroundColor(0xffeef5ff); root.addView(notice);
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
            public void onTextChanged(CharSequence s,int start,int before,int count){ searchQuery=s.toString(); select(0); }
            public void afterTextChanged(android.text.Editable e){}
        });
        select(selected);
    }
    private void select(int index) {
        composerGeneration++;
        inConversation=false;
        selected=index; body.removeAllViews();
        for(int i=0;i<navigation.getChildCount();i++) {
            LinearLayout tab=(LinearLayout)navigation.getChildAt(i); ((ImageView)tab.getChildAt(0)).setColorFilter(i==index?blue:muted); ((TextView)tab.getChildAt(1)).setTextColor(i==index?blue:muted);
        }
        if(index==0) {
            LinearLayout filters=new LinearLayout(activity); filters.setPadding(dp(16),dp(14),dp(16),dp(14));
            filters.setGravity(Gravity.CENTER_VERTICAL);filters.setPadding(dp(16),0,dp(4),0);
            TextView all=text("Tất cả",16,blue); all.setTypeface(null,Typeface.BOLD); filters.addView(all,new LinearLayout.LayoutParams(0,dp(48),1));all.setGravity(Gravity.CENTER_VERTICAL);
            ImageButton reload=icon(13,recentLoading?"Đang tải tin gần đây":"Tải tin gần đây",blue,refresh);reload.setEnabled(!recentLoading);filters.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(filters);
            if(conversations.length()>0)renderConversations();
            else {
                empty("Tin nhắn của bạn", "Kết nối tài khoản bằng mã QR thiết bị đồng hành.", "Đăng nhập bằng QR", login);
                if(BuildConfig.DEBUG){Button examples=new Button(activity);examples.setAllCaps(false);examples.setText("Xem hội thoại mẫu • dữ liệu thử");examples.setOnClickListener(v->loadExamples());body.addView(examples);}
            }
        } else if(index==1) empty("Danh bạ", "Bạn bè  •  Nhóm  •  OA\nChưa có dữ liệu danh bạ Zalo được kết nối.", "Mở tiện ích", tools);
        else if(index==2) empty("Tiện ích của bạn", "Thư viện GIF cá nhân, mẫu trả lời nhanh và cập nhật ứng dụng.", "GIF và mẫu tin", tools);
        else if(index==3) empty("Nhật ký", "Chưa có kết nối dữ liệu Nhật ký Zalo.", "Về tin nhắn", () -> select(0));
        else empty("Cá nhân", "Zalite\nDữ liệu cục bộ trên thiết bị này", "Bản nháp của tôi", this::draft);
    }
    private void renderConversations() {
        TextView banner=text(demo?"DỮ LIỆU THỬ • không phải tài khoản thật":limited?"Tin gần đây đã giới hạn; chưa phải toàn bộ lịch sử":"Tin gần đây • chưa phải toàn bộ lịch sử",12,muted);banner.setPadding(dp(16),dp(8),dp(16),dp(8));body.addView(banner);
        ScrollView scroll=new ScrollView(activity);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<conversations.length();i++){
            org.json.JSONObject entry=conversations.optJSONObject(i);if(entry==null)continue;
            String name=entry.optString("name","Liên hệ");org.json.JSONArray messages=entry.optJSONArray("messages");org.json.JSONObject last=messages==null||messages.length()==0?null:messages.optJSONObject(messages.length()-1);
            if(!ConversationSearch.matches(name,last==null?"":last.optString("text"),searchQuery))continue;
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(16),dp(12));
            TextView avatar=text(name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(java.util.Locale.ROOT),20,blue);avatar.setGravity(Gravity.CENTER);avatar.setBackground(background(0xffe3efff,24));row.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout details=new LinearLayout(activity);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(12),0,dp(8),0);TextView title=text(name,16,0xff172334);title.setTypeface(null,Typeface.BOLD);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title);
            TextView preview=text(last==null?"":last.optString("text"),13,muted);preview.setMaxLines(1);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(preview);row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(text(time(entry.optLong("ts")),11,muted));row.setOnClickListener(v->openConversation(entry));rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
            android.view.View divider=new android.view.View(activity);divider.setBackgroundColor(0xfff0f2f5);rows.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(rows.getChildCount()==0){TextView none=text("Không tìm thấy trong hội thoại đã tải trên máy.",14,muted);none.setPadding(dp(16),dp(24),dp(16),dp(24));rows.addView(none);}
    }
    private String time(long ts){return ts<=0?"":new java.text.SimpleDateFormat("HH:mm",java.util.Locale.ROOT).format(new java.util.Date(ts));}
    private void openConversation(org.json.JSONObject entry){
        final long composition=++composerGeneration;
        inConversation=true;activeConversationKey=(entry.optBoolean("group")?"g:":"u:")+entry.optString("id");root.removeAllViews();
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(8),dp(12),dp(8));header.setBackgroundColor(blue);
        header.addView(icon(7,"Về danh sách tin nhắn",Color.WHITE,this::show),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout title=new LinearLayout(activity);title.setOrientation(LinearLayout.VERTICAL);title.addView(text(entry.optString("name"),18,Color.WHITE));title.addView(text(demo?"Dữ liệu thử • không kết nối Zalo":"Gửi thử nghiệm • cần xác nhận mỗi tin",11,0xffdbeaff));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(0xffeaf0f6);LinearLayout messages=new LinearLayout(activity);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(dp(12),dp(16),dp(12),dp(16));scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        org.json.JSONArray rows=entry.optJSONArray("messages");if(rows!=null)for(int i=0;i<rows.length();i++){
            org.json.JSONObject message=rows.optJSONObject(i);if(message==null)continue;boolean outgoing=message.optBoolean("outgoing");
            LinearLayout line=new LinearLayout(activity);line.setGravity(outgoing?Gravity.RIGHT:Gravity.LEFT);line.setPadding(0,0,0,dp(10));
            LinearLayout bubble=new LinearLayout(activity);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(12),dp(9),dp(12),dp(7));bubble.setBackground(background(outgoing?0xffd9eaff:Color.WHITE,12));
            TextView content=text(message.optString("text"),16,0xff172334);content.setMaxWidth(activity.getResources().getDisplayMetrics().widthPixels-dp(80));bubble.addView(content);bubble.addView(text(time(message.optLong("ts")),10,muted));line.addView(bubble);messages.addView(line);
            if(message.has("send_state"))bubble.addView(text(SendStatus.describe(message.optString("send_state")),10,muted));
        }
        scroll.post(()->scroll.fullScroll(android.view.View.FOCUS_DOWN));
        LinearLayout toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(4),0,dp(4),0);toolbar.setBackgroundColor(0xfff7f9fc);
        ImageButton gif=icon(15,"GIF và sticker cá nhân",blue,tools);toolbar.addView(gif,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageButton quick=icon(14,"Mẫu tin nhanh",blue,tools);toolbar.addView(quick,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(toolbar);
        String key=(entry.optBoolean("group")?"g:":"u:")+entry.optString("id");
        final boolean demoChat=demo;final String account=conversationAccount;
        LinearLayout composer=new LinearLayout(activity);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(4),dp(4),dp(4),dp(4));
        EditText input=new EditText(activity);input.setTextSize(16);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(background(0xfff1f4f8,20));input.setMinLines(2);input.setMaxLines(5);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});input.setHint(demoChat?"Nhập tin thử…":"Tin nhắn");input.setText(drafts.getOrDefault(key,""));composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton send=icon(12,demoChat?"Gửi tin thử trên máy":"Gửi tin nhắn",blue,()->{});composer.addView(send,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(composer);
        toolbar.addView(icon(9,"Chèn biểu cảm",muted,()->new android.app.AlertDialog.Builder(activity).setTitle("Biểu cảm").setItems(new String[]{"😊","❤️","👍","😂","🎉","🙏"},(dialog,which)->{String value=new String[]{"😊","❤️","👍","😂","🎉","🙏"}[which];int start=Math.max(0,input.getSelectionStart()),end=Math.max(start,input.getSelectionEnd());input.getText().replace(start,end,value);}).setNegativeButton("Đóng",null).show()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView result=text(demoChat?SendStatus.describe("local_demo"):"Gửi thử nghiệm chưa được kiểm chứng bằng tài khoản thật",11,muted);result.setPadding(dp(12),dp(4),dp(12),dp(6));root.addView(result);
        quick.setOnClickListener(v->{
            String expected=input.getText().toString();int start=Math.max(0,input.getSelectionStart()),end=Math.max(start,input.getSelectionEnd());
            sends.template(entry.optString("name"),expanded->{
                if(!inConversation||composition!=composerGeneration||!activeConversationKey.equals(key)||!conversationAccount.equals(account)||demo!=demoChat)return;
                try{String merged=TemplateComposer.insert(input.getText().toString(),expected,expanded,start,end);input.setText(merged);input.setSelection(start+expanded.length());result.setText("Đã chèn mẫu vào bản nháp • chưa gửi");}catch(IllegalArgumentException error){result.setText(error.getMessage());}
            });
        });
        ImageButton check=icon(8,"Trạng thái lượt gửi",muted,()->{});toolbar.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));
        check.setOnClickListener(v->{if(demoChat)result.setText(SendStatus.describe("local_demo"));else sends.status(account,entry.optString("id"),entry.optBoolean("group"),value->{String phase=value.optString("phase");result.setText(value.optString("message",SendStatus.describe(phase)));send.setEnabled(!phase.equals("sending"));if(SendStatus.canClearDraft(input.getText().toString(),value.optString("text"),phase)){drafts.remove(key);input.setText("");}});});
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){drafts.put(key,s.toString());}public void afterTextChanged(android.text.Editable e){}});
        send.setOnClickListener(v->{
            String content=input.getText().toString();if(content.trim().isEmpty())return;send.setEnabled(false);
            java.util.function.Consumer<org.json.JSONObject> completed=value->{
                send.setEnabled(true);String phase=value.optString("phase");result.setText(value.optString("message",SendStatus.describe(phase)));
                if((phase.equals("accepted")||phase.equals("local_demo"))&&demo==demoChat&&conversationAccount.equals(account)){
                    try{org.json.JSONArray list=entry.optJSONArray("messages");if(list==null){list=new org.json.JSONArray();entry.put("messages",list);}long now=System.currentTimeMillis();list.put(new org.json.JSONObject().put("id",value.optString("ticket")).put("text",content).put("ts",now).put("outgoing",true).put("send_state",phase));while(list.length()>100){list.remove(0);limited=true;}entry.put("ts",now);if(input.getText().toString().equals(content)){drafts.remove(key);input.setText("");}if(inConversation&&activeConversationKey.equals(key))openConversation(entry);}catch(org.json.JSONException error){result.setText("Đã nhận kết quả nhưng không cập nhật được phần xem trước.");}
                }
            };
            if(demoChat){new android.app.AlertDialog.Builder(activity).setTitle("Gửi trong bản xem thử").setMessage("Người nhận: "+SendStatus.previewName(entry.optString("name"))+"\n\n"+content+"\n\nChỉ thêm dữ liệu mẫu trên máy, không gửi qua Zalo.").setPositiveButton("Thêm tin thử",(dialog,which)->{org.json.JSONObject value=new org.json.JSONObject();try{value.put("phase","local_demo");}catch(org.json.JSONException error){throw new IllegalStateException(error);}completed.accept(value);}).setNegativeButton("Hủy",(dialog,which)->send.setEnabled(true)).setOnCancelListener(dialog->send.setEnabled(true)).show();}
            else sends.request(account,entry.optString("id"),entry.optBoolean("group"),entry.optString("name"),content,completed);
        });
    }
    private void loadExamples(){
        try{
            long now=System.currentTimeMillis();org.json.JSONArray rows=new org.json.JSONArray();
            String[] names={"Khách hàng mẫu","Nhóm kinh doanh mẫu","Gia đình mẫu"};
            for(int i=0;i<names.length;i++){
                org.json.JSONArray messages=new org.json.JSONArray();messages.put(new org.json.JSONObject().put("text","Chào bạn, đây là nội dung thử để kiểm tra bố cục hội thoại.").put("outgoing",false).put("ts",now-60000));messages.put(new org.json.JSONObject().put("text","Mình đã nhận thông tin. Đây là bản xem thử, chưa gửi qua Zalo.").put("outgoing",true).put("ts",now));
                rows.put(new org.json.JSONObject().put("id","fixture-"+i).put("name",names[i]).put("group",i==1).put("ts",now).put("messages",messages));
            }
            acceptRecent(new org.json.JSONObject().put("conversations",rows),true);
        }catch(org.json.JSONException error){throw new IllegalStateException(error);}
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
