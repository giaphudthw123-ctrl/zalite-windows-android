package org.ctdozlite.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Worker-only encrypted template snapshots with optimistic revision checks and verified migration. */
final class TemplateStore {
    private static final Object STORE_LOCK=new Object();
    private final SessionVault vault;
    private final SharedPreferences legacy;
    private final Function<String,String> normalize;
    static final class Snapshot {
        final long revision; final List<String> items;
        Snapshot(long revision,List<String> items){this.revision=revision;this.items=Collections.unmodifiableList(new ArrayList<>(items));}
    }
    TemplateStore(Context context,SharedPreferences legacy,Function<String,String> normalize){this(SessionVault.templates(context),legacy,normalize);}
    private TemplateStore(SessionVault vault,SharedPreferences legacy,Function<String,String> normalize){this.vault=vault;this.legacy=legacy;this.normalize=normalize;}
    private List<String> decodeItems(JSONArray array) throws Exception {
        if(array.length()>100)throw new IOException("Template count exceeds current format");
        List<String> items=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            Object value=array.get(i);if(!(value instanceof String))throw new IOException("Invalid template type");
            String text=normalize.apply((String)value);if(text==null)throw new IOException("Invalid template");items.add(text);
        }
        return items;
    }
    private Snapshot readVault() throws Exception {
        byte[] bytes=vault.read();
        try{
            if(bytes.length>128*1024)throw new IOException("Template snapshot too large");
            JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!(json.get("format") instanceof Integer)||!(json.get("revision") instanceof Integer||json.get("revision") instanceof Long))throw new IOException("Invalid template metadata types");
            long revision=json.getLong("revision");
            if(json.getInt("format")!=1||revision<1)throw new IOException("Unsupported template snapshot");
            return new Snapshot(revision,decodeItems(json.getJSONArray("items")));
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    private void writeVerified(Snapshot snapshot) throws Exception {
        JSONObject document=new JSONObject().put("format",1).put("revision",snapshot.revision).put("items",new JSONArray(snapshot.items));
        byte[] bytes=document.toString().getBytes(StandardCharsets.UTF_8);
        try{vault.save(bytes);}finally{Arrays.fill(bytes,(byte)0);}
        Snapshot verified=readVault();
        if(verified.revision!=snapshot.revision||!verified.items.equals(snapshot.items))throw new IOException("Template verification failed");
    }
    Snapshot load() throws Exception {synchronized(STORE_LOCK){return loadLocked();}}
    private Snapshot loadLocked() throws Exception {
        Snapshot current=vault.exists()?readVault():new Snapshot(0,Collections.emptyList());
        if(legacy.contains("templates")){
            String raw=legacy.getString("templates",null);
            if(raw==null||raw.length()>128*1024)throw new IOException("Invalid legacy templates");
            List<String> old=decodeItems(new JSONArray(raw));
            if(current.revision==0){current=new Snapshot(1,old);writeVerified(current);}
            else if(!current.items.equals(old))throw new IOException("Conflicting legacy templates; both copies retained");
            if(!legacy.edit().remove("templates").commit())throw new IOException("Legacy cleanup incomplete");
        }
        return current;
    }
    Snapshot save(long expected,List<String> proposed) throws Exception {
        synchronized(STORE_LOCK){
            Snapshot current=loadLocked();if(current.revision!=expected)throw new IOException("Template revision changed");
            if(expected==Long.MAX_VALUE)throw new IOException("Template revision exhausted");
            Snapshot next=new Snapshot(expected+1,decodeItems(new JSONArray(proposed)));writeVerified(next);return next;
        }
    }
    static void selfTest(Context context,Function<String,String> normalize) throws Exception {
        String prefName="zalite-template-vault-selftest";
        SharedPreferences prefs=context.getSharedPreferences(prefName,Context.MODE_PRIVATE);
        SessionVault testVault=SessionVault.templateSelfTest(context);
        TemplateStore store=new TemplateStore(testVault,prefs,normalize);
        try{
            testVault.deleteSelfTest();prefs.edit().clear().putString("templates","not-json").commit();
            boolean invalid=false;try{store.load();}catch(Exception expected){invalid=true;}
            if(!invalid||!prefs.contains("templates")||testVault.exists())throw new IOException("Failed migration lost source");
            if(!prefs.edit().putString("templates","[\"fixture_{ten}\"]").commit())throw new IOException("Fixture write failed");
            Snapshot migrated=store.load();if(migrated.revision!=1||prefs.contains("templates")||!migrated.items.equals(Collections.singletonList("fixture_{ten}")))throw new IOException("Migration failed");
            Snapshot saved=store.save(1,Collections.singletonList("saved-fixture"));
            boolean stale=false;try{store.save(1,Collections.singletonList("stale-fixture"));}catch(Exception expected){stale=true;}
            if(!stale||!store.load().items.equals(saved.items))throw new IOException("Stale save accepted");
            if(!prefs.edit().putString("templates","[\"conflicting-fixture\"]").commit())throw new IOException("Fixture write failed");
            boolean conflict=false;try{store.load();}catch(Exception expected){conflict=true;}
            if(!conflict||!prefs.contains("templates"))throw new IOException("Conflicting source removed");
            prefs.edit().remove("templates").commit();
            java.nio.file.Path path=new java.io.File(context.getNoBackupFilesDir(),"templates-vault-selftest.bin").toPath();
            byte[] sealed=java.nio.file.Files.readAllBytes(path);
            if(new String(sealed,StandardCharsets.UTF_8).contains("saved-fixture"))throw new IOException("Plaintext fixture persisted");
            sealed[sealed.length-1]^=1;java.nio.file.Files.write(path,sealed);
            boolean tampered=false;try{store.load();}catch(Exception expected){tampered=true;}
            if(!tampered||!Arrays.equals(sealed,java.nio.file.Files.readAllBytes(path)))throw new IOException("Tampered vault not retained");
        }finally{testVault.deleteSelfTest();context.deleteSharedPreferences(prefName);}
    }
}
