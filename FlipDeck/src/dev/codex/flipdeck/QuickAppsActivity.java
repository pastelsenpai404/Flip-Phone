package dev.codex.flipdeck;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class QuickAppsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!RecentsService.showRecents())
            Toast.makeText(this,"Enable Flip Deck shortcuts in Accessibility",Toast.LENGTH_LONG).show();
        finish();
    }
}
