package org.ctdozlite.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Window;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.util.*;

/** UI-thread window policy; dialog windows require their own FLAG_SECURE. */
final class CaptureProtection {
    private static final Map<Activity,List<WeakReference<AlertDialog>>> dialogs=new WeakHashMap<>();
    private static void apply(Window window,boolean enabled) {
        if(window==null)return;
        if(enabled)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    static void set(Activity activity,boolean enabled) {
        apply(activity.getWindow(),enabled);
        List<WeakReference<AlertDialog>> values=dialogs.get(activity);
        if(values!=null)values.removeIf(reference->{AlertDialog dialog=reference.get();if(dialog==null)return true;apply(dialog.getWindow(),enabled);return false;});
    }
    static final class Builder extends AlertDialog.Builder {
        private final Activity owner;
        Builder(Activity activity){super(activity);owner=activity;}
        @Override public AlertDialog create() {
            AlertDialog dialog=super.create();
            boolean enabled=(owner.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0;
            apply(dialog.getWindow(),enabled);
            List<WeakReference<AlertDialog>> values=dialogs.computeIfAbsent(owner,key->new ArrayList<>());
            values.removeIf(reference->reference.get()==null);values.add(new WeakReference<>(dialog));
            return dialog;
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
}
