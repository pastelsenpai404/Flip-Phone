package dev.codex.flippost;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notices {
    static void show(Context context,int id,String title,String text,Intent launch){
        PendingIntent open=PendingIntent.getActivity(context,id,launch,PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(context).setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE).build();
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,notification);
    }
}
