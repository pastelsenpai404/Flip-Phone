package dev.codex.flipdeck;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;

public final class RecentsService extends AccessibilityService {
    private static RecentsService active;

    static boolean showNotifications() {
        final RecentsService service=active;
        return service!=null && service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(android.content.Intent intent) {
        if(active==this) active=null;
        return super.onUnbind(intent);
    }
}
