package dev.codex.flippost;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import com.sun.mail.imap.IMAPSSLStore;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Properties;
import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.UIDFolder;
import javax.mail.internet.ContentType;
import org.json.JSONArray;
import org.json.JSONObject;

final class MailClient {
    static final class Item {
        long uid,date;String subject,from;boolean unread;
        Item(long uid,long date,String subject,String from,boolean unread){this.uid=uid;this.date=date;this.subject=subject;this.from=from;this.unread=unread;}
    }
    private static synchronized void configureMime(){
        MailcapCommandMap map=new MailcapCommandMap();
        map.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
        map.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
        map.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
        map.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
        CommandMap.setDefaultCommandMap(map);
    }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("post",Context.MODE_PRIVATE);}
    static boolean configured(Context c){return prefs(c).getString("mail_password","").length()>0;}
    private static IMAPSSLStore connect(Context context)throws Exception{
        configureMime();SharedPreferences pref=prefs(context);
        String host=pref.getString("mail_host",""),user=pref.getString("mail_user","");
        Properties props=new Properties();
        props.setProperty("mail.imaps.connectiontimeout","8000");props.setProperty("mail.imaps.timeout","10000");
        props.setProperty("mail.imaps.writetimeout","10000");props.setProperty("mail.imaps.ssl.checkserveridentity","true");
        props.setProperty("mail.imaps.ssl.protocols","TLSv1.2");props.setProperty("mail.imaps.peek","true");
        props.setProperty("mail.imaps.partialfetch","true");props.setProperty("mail.imaps.fetchsize","16384");
        props.setProperty("mail.imaps.connectionpoolsize","1");
        IMAPSSLStore store=new IMAPSSLStore(Session.getInstance(props),null);
        try{store.connect(host,993,user,Secrets.decrypt(context,pref.getString("mail_password","")));return store;}
        catch(Exception e){try{store.close();}catch(Exception ignored){}throw e;}
    }
    static ArrayList<Item> sync(Context context,boolean notify)throws Exception{
        String account=accountKey(context);
        IMAPSSLStore store=null;Folder folder=null;
        try{
            store=connect(context);folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);
            UIDFolder uids=(UIDFolder)folder;long validity=uids.getUIDValidity();
            int count=folder.getMessageCount();ArrayList<Item> items=new ArrayList<Item>();long newest=0;
            if(count>0){
                Message[] messages=folder.getMessages(Math.max(1,count-49),count);
                FetchProfile profile=new FetchProfile();profile.add(FetchProfile.Item.ENVELOPE);profile.add(FetchProfile.Item.FLAGS);profile.add(UIDFolder.FetchProfileItem.UID);folder.fetch(messages,profile);
                for(int i=messages.length-1;i>=0;i--){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    Message message=messages[i];long uid=uids.getUID(message);newest=Math.max(newest,uid);
                    Address[] senders=message.getFrom();String from=senders==null||senders.length==0?"Unknown sender":senders[0].toString();
                    if(senders!=null&&senders.length>0&&senders[0] instanceof javax.mail.internet.InternetAddress)from=((javax.mail.internet.InternetAddress)senders[0]).toUnicodeString();
                    java.util.Date date=message.getReceivedDate();
                    items.add(new Item(uid,date==null?0:date.getTime(),clip(message.getSubject()==null?"(No subject)":message.getSubject(),300),clip(from,300),!message.isSet(Flags.Flag.SEEN)));
                }
            }
            SharedPreferences pref=prefs(context);long old=pref.getLong("mail_newest",0),oldValidity=pref.getLong("mail_validity",0);
            if(!account.equals(accountKey(context))||Thread.currentThread().isInterrupted())throw new InterruptedException();
            JSONArray data=new JSONArray();int incoming=0;
            for(Item item:items){JSONObject row=new JSONObject();row.put("uid",item.uid);row.put("date",item.date);row.put("subject",item.subject);row.put("from",item.from);row.put("unread",item.unread);data.put(row);if(item.uid>old&&item.unread)incoming++;}
            pref.edit().putString("mail_cache",data.toString()).putLong("mail_newest",newest).putLong("mail_validity",validity).putLong("mail_synced",System.currentTimeMillis()).apply();
            if(notify&&old>0&&oldValidity==validity&&incoming>0)Notices.show(context,901,"New mail",incoming+" new message(s)",new android.content.Intent(context,MainActivity.class).putExtra("tab",1));
            return items;
        }finally{if(folder!=null)try{folder.close(false);}catch(Exception ignored){}if(store!=null)try{store.close();}catch(Exception ignored){}}
    }
    static ArrayList<Item> cached(Context context){
        ArrayList<Item> result=new ArrayList<Item>();
        try{JSONArray data=new JSONArray(prefs(context).getString("mail_cache","[]"));for(int i=0;i<data.length();i++){JSONObject row=data.getJSONObject(i);result.add(new Item(row.getLong("uid"),row.optLong("date"),row.optString("subject"),row.optString("from"),row.optBoolean("unread")));}}
        catch(Exception ignored){}return result;
    }
    static String read(Context context,long uid)throws Exception{
        String account=accountKey(context);File cached=bodyFile(context,uid);
        if(cached.exists()){
            InputStreamReader reader=new InputStreamReader(new FileInputStream(cached),"UTF-8");
            StringBuilder result=new StringBuilder();char[] buffer=new char[2048];int n;
            try{while(result.length()<40000&&(n=reader.read(buffer))>0)result.append(buffer,0,n);}finally{reader.close();}
            return result.toString();
        }
        IMAPSSLStore store=null;Folder folder=null;
        try{
            store=connect(context);folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);
            if(((UIDFolder)folder).getUIDValidity()!=prefs(context).getLong("mail_validity",0))return "Mailbox changed. Refresh Mail before opening this message.";
            Message message=((UIDFolder)folder).getMessageByUID(uid);
            if(message==null)return "This message is no longer in INBOX. Refresh Mail.";
            String body=text(message,0);
            if(body.length()==0)body="No readable text. Images and attachments are not downloaded.";
            if(account.equals(accountKey(context))&&!Thread.currentThread().isInterrupted()){
                FileOutputStream out=new FileOutputStream(cached);try{out.write(body.getBytes("UTF-8"));}finally{out.close();}
                trimBodies(context);
            }
            return body;
        }finally{if(folder!=null)try{folder.close(false);}catch(Exception ignored){}if(store!=null)try{store.close();}catch(Exception ignored){}}
    }
    private static String accountKey(Context context){SharedPreferences p=prefs(context);return p.getString("mail_namespace","")+p.getString("mail_user","")+p.getString("mail_host","")+p.getString("mail_password","");}
    private static File bodyFile(Context context,long uid){
        SharedPreferences p=prefs(context);String namespace=p.getString("mail_namespace","default").replaceAll("[^a-zA-Z0-9-]","");
        return new File(context.getFilesDir(),"mail-body-"+namespace+"-"+p.getLong("mail_validity",0)+"-"+uid+".txt");
    }
    static void clearBodies(Context context){File[] files=context.getFilesDir().listFiles();if(files!=null)for(File file:files)if(file.getName().startsWith("mail-body-")&&file.getName().endsWith(".txt"))file.delete();}
    private static void trimBodies(Context context){
        File[] files=context.getFilesDir().listFiles(new java.io.FilenameFilter(){public boolean accept(File dir,String name){return name.startsWith("mail-body-")&&name.endsWith(".txt");}});
        if(files==null||files.length<=50)return;
        java.util.Arrays.sort(files,new java.util.Comparator<File>(){public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});
        for(int i=50;i<files.length;i++)files[i].delete();
    }
    private static String text(Part part,int depth)throws Exception{
        if(depth>6||Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())||part.getFileName()!=null)return "";
        if(part.isMimeType("text/plain")||part.isMimeType("text/html")){
            ContentType type=new ContentType(part.getContentType());String charset=type.getParameter("charset");if(charset==null)charset="UTF-8";
            InputStream stream=part.getInputStream();InputStreamReader reader;
            try{reader=new InputStreamReader(stream,charset);}catch(Exception e){reader=new InputStreamReader(stream,"UTF-8");}
            StringBuilder body=new StringBuilder();char[] buffer=new char[2048];int count;
            try{while(body.length()<32000&&(count=reader.read(buffer,0,Math.min(buffer.length,32000-body.length())))>0){body.append(buffer,0,count);if(Thread.currentThread().isInterrupted())throw new InterruptedException();}}
            finally{reader.close();}
            String value=body.toString();if(part.isMimeType("text/html"))value=Html.fromHtml(value).toString();
            return value+(body.length()>=32000?"\n[Message preview limited to 32,000 characters]":"");
        }
        if(part.isMimeType("multipart/*")){
            Multipart multi=(Multipart)part.getContent();
            for(int i=0;i<Math.min(multi.getCount(),20);i++)if(multi.getBodyPart(i).isMimeType("text/plain")){String body=text(multi.getBodyPart(i),depth+1);if(body.length()>0)return body;}
            for(int i=0;i<Math.min(multi.getCount(),20);i++){String body=text(multi.getBodyPart(i),depth+1);if(body.length()>0)return body;}
        }
        return "";
    }
    private static String clip(String value,int length){return value.length()>length?value.substring(0,length):value;}
}
