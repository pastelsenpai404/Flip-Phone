package dev.codex.flippost;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()))return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(intent);if(parts==null||parts.length==0||parts[0]==null)return;
        String address=parts[0].getDisplayOriginatingAddress();if(address==null)address="Unknown";
        if(MailClient.prefs(context).getBoolean("sms_notice",true))
            Notices.show(context,1000+(address.hashCode()&0x0fffffff),"New SMS","From "+address,new Intent(context,MainActivity.class).putExtra("sender",address));
    }
}
