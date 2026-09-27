package dev.codex.flipdeck;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class RecentsService extends AccessibilityService {
    private static RecentsService active;
    private String foregroundPackage="";

    static boolean showRecents() {
        final RecentsService service=active;
        if(service==null) return false;
        new Handler(service.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                if(active==service) service.performGlobalAction(GLOBAL_ACTION_RECENTS);
            }
        },150);
        return true;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        active=this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName()!=null)
            foregroundPackage=event.getPackageName().toString();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(code!=KeyEvent.KEYCODE_F4 && code!=KeyEvent.KEYCODE_CAMERA) return false;
        if("dev.codex.flipcam".equals(foregroundPackage)) return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
            Intent launch=getPackageManager().getLaunchIntentForPackage("dev.codex.flipcam");
            if(launch==null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {startActivity(launch);} catch(Exception e) {return false;}
        }
        return true;
    }
    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(android.content.Intent intent) {
        if(active==this) active=null;
        return super.onUnbind(intent);
    }
}
