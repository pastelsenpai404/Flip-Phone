package dev.codex.flippost;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class SentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!"dev.codex.flippost.SMS_SENT".equals(intent.getAction()))return;
        android.content.SharedPreferences prefs=MailClient.prefs(context);
        String id=intent.getStringExtra("send_id");if(id==null||!id.equals(prefs.getString("send_id","")))return;
        int left=Math.max(0,prefs.getInt("send_left",1)-1);
        boolean failed=prefs.getBoolean("send_failed",false)||getResultCode()!=Activity.RESULT_OK;
        android.content.SharedPreferences.Editor edit=prefs.edit().putInt("send_left",left).putBoolean("send_failed",failed);
        if(left==0&&!failed&&prefs.getString("draft_to","").equals(prefs.getString("send_to",""))&&prefs.getString("draft_body","").equals(prefs.getString("send_body","")))edit.remove("draft_to").remove("draft_body");
        edit.apply();
        if(getResultCode()!=Activity.RESULT_OK)Notices.show(context,902,"SMS not sent","Your draft is kept. Check SIM / signal.",new Intent(context,MainActivity.class).putExtra("compose",true));
        if(left==0)Toast.makeText(context,failed?"SMS failed. Draft kept.":"SMS sent",Toast.LENGTH_LONG).show();
    }
}
