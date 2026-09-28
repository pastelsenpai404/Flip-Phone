package dev.codex.flippost;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

final class SmsData {
    static final class ThreadItem {
        long id,date;String address,name,preview;int unread;
        ThreadItem(long id,String address,String preview,long date){this.id=id;this.address=address;name=address;this.preview=preview;this.date=date;}
    }
    static final class Text {
        String body;long date;int type;
        Text(String body,long date,int type){this.body=body;this.date=date;this.type=type;}
    }
    static ArrayList<ThreadItem> inbox(Context context)throws Exception{
        LinkedHashMap<Long,ThreadItem> groups=new LinkedHashMap<Long,ThreadItem>();Cursor cursor=null;
        try{
            cursor=context.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.THREAD_ID,Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.READ},null,null,Telephony.Sms.DATE+" DESC");
            int scanned=0;
            if(cursor!=null)while(cursor.moveToNext()&&scanned++<5000){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                long id=cursor.getLong(0),date=cursor.getLong(3);String sender=cursor.getString(1);if(sender==null)sender="Unknown";
                ThreadItem item=groups.get(id);
                if(item==null){if(groups.size()>=200)continue;String body=cursor.getString(2);item=new ThreadItem(id,sender,body==null?"":body.substring(0,Math.min(body.length(),140)),date);groups.put(id,item);}
                if(cursor.getInt(4)==0&&date>MailClient.prefs(context).getLong("viewed."+sender,0))item.unread++;
            }
        }finally{if(cursor!=null)cursor.close();}
        ArrayList<ThreadItem> result=new ArrayList<ThreadItem>(groups.values());
        for(ThreadItem item:result){
            Cursor contact=null;
            try{contact=context.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(item.address)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null);
                if(contact!=null&&contact.moveToFirst())item.name=contact.getString(0);
            }catch(Exception ignored){}finally{if(contact!=null)contact.close();}
        }
        return result;
    }
    static ArrayList<Text> thread(Context context,long id,String address)throws Exception{
        ArrayList<Text> result=new ArrayList<Text>();Cursor cursor=null;
        try{
            cursor=context.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.TYPE},
                id>0?Telephony.Sms.THREAD_ID+"=?":Telephony.Sms.ADDRESS+"=?",new String[]{id>0?String.valueOf(id):address},Telephony.Sms.DATE+" DESC");
            if(cursor!=null)while(cursor.moveToNext()&&result.size()<100){
                String body=cursor.getString(0);if(body==null)body="";
                result.add(new Text(body.length()>16000?body.substring(0,16000)+"\n[Preview truncated]":body,cursor.getLong(1),cursor.getInt(2)));
            }
        }finally{if(cursor!=null)cursor.close();}
        Collections.reverse(result);return result;
    }
}
