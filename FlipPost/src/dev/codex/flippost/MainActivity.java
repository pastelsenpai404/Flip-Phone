package dev.codex.flippost;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(12,25,34), PANEL=Color.rgb(25,46,57), MINT=Color.rgb(101,226,192), WHITE=Color.rgb(242,249,246), MUTED=Color.rgb(154,183,188);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler();
    private Future<?> task;
    private FrameLayout content;
    private TextView status;
    private Button smsTab,mailTab;
    private SharedPreferences prefs;
    private ArrayList<SmsData.ThreadItem> sms=new ArrayList<SmsData.ThreadItem>();
    private ArrayList<MailClient.Item> mail=new ArrayList<MailClient.Item>();
    private int tab=0,screen=0,generation=0; // screen: inbox, thread, compose, mail text
    private long threadId;private String sender="";
    private EditText recipient,body;
    private boolean destroyed;
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){handler.removeCallbacks(refreshSms);handler.postDelayed(refreshSms,400);}};
    private final Runnable refreshSms=new Runnable(){public void run(){if(screen==1)showThread(threadId,sender);else if(screen==0&&tab==0)loadSms();}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=MailClient.prefs(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        TextView title=text("Flip Post",22,WHITE,true);title.setPadding(dp(12),dp(8),dp(12),dp(5));root.addView(title);
        LinearLayout tabs=new LinearLayout(this);
        smsTab=button("SMS",new Runnable(){public void run(){goTab(0);}});
        mailTab=button("MAIL",new Runnable(){public void run(){goTab(1);}});
        Button compose=button("NEW SMS",new Runnable(){public void run(){showCompose("","");}});
        tabs.addView(smsTab,new LinearLayout.LayoutParams(0,dp(42),1));tabs.addView(mailTab,new LinearLayout.LayoutParams(0,dp(42),1));tabs.addView(compose,new LinearLayout.LayoutParams(0,dp(42),1));root.addView(tabs);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",11,MUTED,false);status.setPadding(dp(8),dp(4),dp(8),dp(4));status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);root.addView(status);
        setContentView(root);MailSyncService.schedule(this);
        readIntent(getIntent());
    }
    private void readIntent(Intent intent){
        String address=intent.getStringExtra("sender");
        if(address!=null){showThread(0,address);return;}
        Uri data=intent.getData();
        if(data!=null&&("sms".equals(data.getScheme())||"smsto".equals(data.getScheme()))){
            String raw=data.getSchemeSpecificPart(),message=intent.getStringExtra("sms_body");int query=raw.indexOf('?');
            if(query>=0){if(message==null)message=Uri.parse("https://sms.invalid/?"+raw.substring(query+1)).getQueryParameter("body");raw=raw.substring(0,query);}
            showCompose(raw,message==null?"":message);return;
        }
        if(intent.getBooleanExtra("compose",false)){showCompose("","");return;}
        goTab(intent.getIntExtra("tab",0)==1?1:0);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);saveDraft();setIntent(intent);readIntent(intent);}
    @Override protected void onResume(){super.onResume();getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);if(screen==0&&tab==0)loadSms();}
    @Override protected void onPause(){saveDraft();getContentResolver().unregisterContentObserver(observer);super.onPause();}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView text(String value,int size,int color,boolean bold){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(TypedValue.COMPLEX_UNIT_DIP,size);view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));view.setIncludeFontPadding(true);return view;
    }
    private Button button(String label,final Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});return b;}
    private EditText input(String hint,int type,boolean single){
        EditText edit=new EditText(this);edit.setHint(hint);edit.setInputType(type);edit.setSingleLine(single);edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP,16);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN|(single?EditorInfo.IME_ACTION_NEXT:EditorInfo.IME_ACTION_DONE));return edit;
    }
    private void display(View view){content.removeAllViews();content.addView(view,new FrameLayout.LayoutParams(-1,-1));smsTab.setTextColor(tab==0?MINT:WHITE);mailTab.setTextColor(tab==1?MINT:WHITE);}
    private void cancelTask(){++generation;if(task!=null)task.cancel(true);}
    private void goTab(int next){saveDraft();recipient=null;body=null;cancelTask();tab=next;screen=0;sender="";if(tab==0)loadSms();else showMail();}
    private void empty(String heading,String detail){LinearLayout panel=column();panel.setPadding(dp(20),dp(28),dp(20),dp(20));panel.addView(text(heading,22,WHITE,true));TextView hint=text(detail,15,MUTED,false);hint.setPadding(0,dp(16),0,dp(16));panel.addView(hint);display(panel);}
    private String date(long stamp){return stamp==0?"":new SimpleDateFormat("dd MMM HH:mm",Locale.getDefault()).format(new Date(stamp));}
    private void loadSms(){
        cancelTask();final int token=generation;status.setText("Loading SMS...");
        task=worker.submit(new Runnable(){public void run(){
            try{final ArrayList<SmsData.ThreadItem> rows=SmsData.inbox(MainActivity.this);handler.post(new Runnable(){public void run(){if(destroyed||token!=generation)return;sms=rows;showSms();}});}
            catch(Exception e){handler.post(new Runnable(){public void run(){if(destroyed||token!=generation)return;empty("SMS unavailable","Check SMS permissions in app settings.");status.setText("Menu: tools");}});}
        }});
    }
    private void showSms(){
        tab=0;screen=0;
        if(sms.size()==0)empty("Your messages, together","Incoming SMS will appear here.\nUse NEW SMS to write a message.\nSMS needs a working SIM.");
        else{
            ListView list=new ListView(this);list.setAdapter(new BaseAdapter(){
                public int getCount(){return sms.size();}public Object getItem(int p){return sms.get(p);}public long getItemId(int p){return sms.get(p).id;}
                public View getView(int p,View convert,ViewGroup parent){SmsData.ThreadItem item=sms.get(p);return row(item.name+(item.unread>0?"  ("+item.unread+")":""),item.preview,date(item.date),item.unread>0);}
            });
            list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> p,View v,int pos,long id){SmsData.ThreadItem item=sms.get(pos);showThread(item.id,item.address);}});display(list);list.requestFocus();
        }
        status.setText("SMS  |  OK: open  |  Menu: tools");
    }
    private View row(String title,String preview,String subtitle,boolean unread){
        LinearLayout row=column();row.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView top=text(title,16,unread?MINT:WHITE,true);top.setSingleLine(true);top.setEllipsize(TextUtils.TruncateAt.END);row.addView(top);
        TextView body=text(preview,14,WHITE,false);body.setMaxLines(2);body.setEllipsize(TextUtils.TruncateAt.END);row.addView(body);
        row.addView(text(subtitle,11,MUTED,false));return row;
    }
    private void showThread(final long id,final String address){
        saveDraft();recipient=null;body=null;tab=0;screen=1;threadId=id;sender=address;cancelTask();final int token=generation;
        empty(address,"Loading conversation...");status.setText("Back: inbox");
        task=worker.submit(new Runnable(){public void run(){
            try{final ArrayList<SmsData.Text> messages=SmsData.thread(MainActivity.this,id,address);handler.post(new Runnable(){public void run(){
                if(destroyed||token!=generation)return;
                LinearLayout panel=column();TextView heading=text(address,17,MINT,true);heading.setPadding(dp(12),dp(8),dp(12),dp(8));panel.addView(heading);
                final ScrollView scroll=new ScrollView(MainActivity.this);LinearLayout bubbles=column();bubbles.setPadding(dp(10),0,dp(10),dp(10));
                long latest=0;
                for(SmsData.Text message:messages){
                    latest=Math.max(latest,message.date);LinearLayout bubble=column();bubble.setPadding(dp(10),dp(8),dp(10),dp(8));
                    GradientDrawable background=new GradientDrawable();background.setColor(PANEL);background.setCornerRadius(dp(8));bubble.setBackground(background);
                    TextView bodyText=text(message.body,15,WHITE,false);bodyText.setTextIsSelectable(true);bubble.addView(bodyText);
                    String label=message.type==Telephony.Sms.MESSAGE_TYPE_INBOX?"IN":message.type==Telephony.Sms.MESSAGE_TYPE_FAILED?"FAILED":"OUT";
                    bubble.addView(text(label+"  "+date(message.date),11,message.type==Telephony.Sms.MESSAGE_TYPE_INBOX?MUTED:MINT,false));
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(message.type==1?0:dp(16),dp(6),message.type==1?dp(16):0,0);bubbles.addView(bubble,params);
                }
                if(messages.size()==0)bubbles.addView(text("Waiting for messages from this sender.",15,MUTED,false));
                prefs.edit().putLong("viewed."+address,latest).apply();scroll.addView(bubbles);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
                panel.addView(button("Reply",new Runnable(){public void run(){showCompose(address,"");}}),new LinearLayout.LayoutParams(-1,dp(44)));
                display(panel);scroll.post(new Runnable(){public void run(){scroll.fullScroll(View.FOCUS_DOWN);}});status.setText("Latest 100 messages  |  Back: inbox");
            }});}catch(Exception e){handler.post(new Runnable(){public void run(){if(token!=generation||destroyed)return;empty(address,"Could not read conversation.");}});}
        }});
    }
    private void showCompose(String to,String message){
        saveDraft();cancelTask();tab=0;screen=2;
        LinearLayout panel=column();panel.setPadding(dp(10),dp(8),dp(10),dp(8));
        recipient=input("Phone number",InputType.TYPE_CLASS_PHONE,true);body=input("Message",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,false);
        // SHARP's phone IME can insert the same digit as DigitsKeyListener.
        // Receive this field's keypad keys ourselves; the body uses the normal IME.
        recipient.setInputType(InputType.TYPE_NULL);recipient.setFocusable(true);recipient.setFocusableInTouchMode(true);recipient.setCursorVisible(true);
        recipient.setOnFocusChangeListener(new View.OnFocusChangeListener(){public void onFocusChange(View v,boolean focused){if(focused){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);status.setText("Number: keypad  |  OK: message  |  Menu: tools");}else status.setText("Back: save draft  |  Menu: tools");}});
        recipient.setText(to.length()>0?to:prefs.getString("draft_to",""));
        body.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1600)});body.setGravity(android.view.Gravity.TOP);
        body.setText(message.length()>0?message:to.length()>0&&!to.equals(prefs.getString("draft_to",""))?"":prefs.getString("draft_body",""));
        panel.addView(recipient,new LinearLayout.LayoutParams(-1,-2));panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        final EditText composeBody=body;
        final EditText composeTo=recipient;
        final Runnable autosave=new Runnable(){public void run(){if(screen==2&&recipient==composeTo&&body==composeBody)saveDraft();}};
        final TextView count=text("",11,MUTED,false);panel.addView(count);final Runnable update=new Runnable(){public void run(){try{count.setText(composeBody.length()+" characters / "+SmsManager.getDefault().divideMessage(composeBody.getText().toString()).size()+" SMS part(s)");}catch(Exception e){count.setText(composeBody.length()+" characters");}}};
        body.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){update.run();handler.removeCallbacks(autosave);handler.postDelayed(autosave,600);}public void afterTextChanged(android.text.Editable e){}});update.run();
        recipient.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(autosave);handler.postDelayed(autosave,600);}public void afterTextChanged(android.text.Editable e){}});
        LinearLayout buttons=new LinearLayout(this);
        buttons.addView(button("Save draft",new Runnable(){public void run(){saveDraft();toast("Draft saved");goTab(0);}}),new LinearLayout.LayoutParams(0,dp(44),1));
        buttons.addView(button("Send",new Runnable(){public void run(){confirmSms();}}),new LinearLayout.LayoutParams(0,dp(44),1));panel.addView(buttons);
        display(panel);recipient.requestFocus();status.setText("Number: keypad  |  OK: message  |  Menu: tools");
    }
    private void saveDraft(){if(screen==2&&recipient!=null&&body!=null)prefs.edit().putString("draft_to",recipient.getText().toString()).putString("draft_body",body.getText().toString()).apply();}
    private void confirmSms(){
        final String to=recipient.getText().toString().replaceAll("[\\s().-]",""),message=body.getText().toString();
        if(!to.matches("\\+?[0-9]{1,20}")){toast("Enter a valid phone number");return;}
        if(message.trim().length()==0){toast("Write a message first");return;}
        TelephonyManager phone=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(phone!=null&&phone.getSimState()==TelephonyManager.SIM_STATE_ABSENT){toast("Insert a SIM to send SMS");return;}
        final ArrayList<String> parts=SmsManager.getDefault().divideMessage(message);
        new AlertDialog.Builder(this).setTitle("Send SMS?").setMessage(to+"\n"+parts.size()+" SMS part(s). Carrier charges may apply.")
            .setPositiveButton("Send",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){sendSms(to,message,parts);}}).setNegativeButton("Cancel",null).show();
    }
    private void sendSms(String to,String message,ArrayList<String> parts){
        if(prefs.getInt("send_left",0)>0&&System.currentTimeMillis()-prefs.getLong("send_started",0)<120000){toast("Previous SMS is still sending");return;}
        recipient.setText(to);saveDraft();String id=UUID.randomUUID().toString();ArrayList<PendingIntent> sent=new ArrayList<PendingIntent>();
        for(int i=0;i<parts.size();i++){Intent result=new Intent(this,SentReceiver.class).setAction("dev.codex.flippost.SMS_SENT").putExtra("send_id",id).putExtra("part",i);sent.add(PendingIntent.getBroadcast(this,id.hashCode()+i,result,PendingIntent.FLAG_ONE_SHOT));}
        prefs.edit().putString("send_id",id).putString("send_to",to).putString("send_body",message).putInt("send_left",parts.size()).putBoolean("send_failed",false).putLong("send_started",System.currentTimeMillis()).apply();
        try{SmsManager.getDefault().sendMultipartTextMessage(to,null,parts,sent,null);toast("Sending SMS...");goTab(0);}
        catch(Exception e){prefs.edit().putInt("send_left",0).apply();toast("SMS could not be sent. Draft kept.");}
    }
    private void showMail(){
        tab=1;screen=0;mail=MailClient.cached(this);
        if(!MailClient.configured(this)){
            LinearLayout panel=column();panel.setPadding(dp(18),dp(24),dp(18),dp(12));panel.addView(text("Your mail, on this phone",22,WHITE,true));
            TextView hint=text("Add an IMAP account to read your inbox.\nEncrypted connection, text previews, no remote images.",15,MUTED,false);hint.setPadding(0,dp(16),0,dp(16));panel.addView(hint);
            panel.addView(button("Set up mail account",new Runnable(){public void run(){setupMail();}}));display(panel);status.setText("Mail account required");return;
        }
        if(mail.size()==0)empty("Mail inbox",prefs.getString("mail_user","")+"\nMenu > Refresh mail to connect.");
        else{
            ListView list=new ListView(this);list.setAdapter(new BaseAdapter(){public int getCount(){return mail.size();}public Object getItem(int p){return mail.get(p);}public long getItemId(int p){return mail.get(p).uid;}public View getView(int p,View v,ViewGroup parent){MailClient.Item item=mail.get(p);return row(item.from,item.subject,date(item.date),item.unread);}});
            list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> a,View v,int p,long id){readMail(mail.get(p));}});display(list);list.requestFocus();
        }
        status.setText("Mail: latest 50  |  Menu: refresh / account");
    }
    private void syncMail(){
        if(!MailClient.configured(this)){setupMail();return;}cancelTask();final int token=generation;status.setText("Connecting to mail...");
        task=worker.submit(new Runnable(){public void run(){try{MailClient.sync(MainActivity.this,false);handler.post(new Runnable(){public void run(){if(token!=generation||destroyed)return;showMail();status.setText("Mail updated "+date(System.currentTimeMillis()));}});}
            catch(Exception e){final String error=mailError(e);handler.post(new Runnable(){public void run(){if(token!=generation||destroyed)return;status.setText(error);new AlertDialog.Builder(MainActivity.this).setTitle("Mail could not connect").setMessage(error).setPositiveButton("OK",null).show();}});}}});
    }
    private String mailError(Exception e){if(e instanceof javax.mail.AuthenticationFailedException)return "Sign-in rejected. Check account / app password.";if(e instanceof InterruptedException)return "Mail cancelled";return "Check Wi-Fi, IMAP host and TLS certificate. Cached mail is kept.";}
    private void readMail(final MailClient.Item item){
        cancelTask();screen=3;final int token=generation;empty(item.subject,"Loading message text...");status.setText("Back: mail inbox");
        task=worker.submit(new Runnable(){public void run(){try{final String message=MailClient.read(MainActivity.this,item.uid);handler.post(new Runnable(){public void run(){if(token!=generation||destroyed)return;
            ScrollView scroll=new ScrollView(MainActivity.this);LinearLayout panel=column();panel.setPadding(dp(14),dp(12),dp(14),dp(14));panel.addView(text(item.subject,20,WHITE,true));panel.addView(text(item.from+"\n"+date(item.date),12,MINT,false));TextView text=text(message,15,WHITE,false);text.setPadding(0,dp(18),0,0);text.setTextIsSelectable(true);panel.addView(text);scroll.addView(panel);display(scroll);scroll.requestFocus();
        }});}catch(Exception e){final String error=mailError(e);handler.post(new Runnable(){public void run(){if(token!=generation||destroyed)return;empty(item.subject,error);}});}}});
    }
    private void setupMail(){
        final LinearLayout panel=column();panel.setPadding(dp(12),0,dp(12),0);
        final EditText host=input("IMAP host (TLS port 993)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,true),user=input("Email / username",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,true),password=input("App password (blank = keep saved)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,true);
        host.setText(prefs.getString("mail_host","imap.gmail.com"));user.setText(prefs.getString("mail_user",""));
        panel.addView(text("Gmail: use an App Password with 2-Step Verification. OAuth-only accounts are not supported by this setup.",12,MUTED,false));panel.addView(host);panel.addView(user);panel.addView(password);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Mail account").setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            final String server=host.getText().toString().trim(),account=user.getText().toString().trim(),secret=password.getText().toString();
            if(!server.matches("[A-Za-z0-9][A-Za-z0-9.-]*")||account.length()==0){toast("Enter host and email / username");return;}
            boolean changed=!server.equals(prefs.getString("mail_host",""))||!account.equals(prefs.getString("mail_user",""));
            if(secret.length()==0&&(!MailClient.configured(MainActivity.this)||changed)){toast("Enter this account's app password");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            cancelTask();final int token=generation;
            task=worker.submit(new Runnable(){public void run(){try{
                String saved=secret.length()==0?prefs.getString("mail_password",""):Secrets.encrypt(MainActivity.this,secret);
                final String encrypted=saved;handler.post(new Runnable(){public void run(){if(destroyed||token!=generation)return;
                    MailClient.clearBodies(MainActivity.this);
                    prefs.edit().putString("mail_host",server).putString("mail_user",account).putString("mail_password",encrypted).putString("mail_namespace",UUID.randomUUID().toString()).remove("mail_cache").remove("mail_newest").remove("mail_validity").apply();
                    password.setText("");dialog.dismiss();MailSyncService.schedule(MainActivity.this);tab=1;screen=0;showMail();syncMail();
                }});
            }catch(Exception e){handler.post(new Runnable(){public void run(){if(destroyed)return;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);toast("Secure storage unavailable. Account was not saved.");}});}}});
        }});
    }
    private void menu(){
        final String[] items=tab==1?new String[]{"Refresh mail","Mail account","Mail check every 30 min: "+(prefs.getBoolean("mail_poll",false)?"ON":"OFF"),"Remove mail account","SMS inbox","Close"}:
            new String[]{"New SMS / draft","Refresh inbox","SMS notifications: "+(prefs.getBoolean("sms_notice",true)?"ON":"OFF"),"System SMS / MMS app","Mail inbox","Discard draft","Close"};
        new AlertDialog.Builder(this).setTitle("Flip Post").setItems(items,new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){
            if(tab==1){if(which==0)syncMail();else if(which==1)handler.postDelayed(new Runnable(){public void run(){setupMail();}},180);
                else if(which==2){prefs.edit().putBoolean("mail_poll",!prefs.getBoolean("mail_poll",false)).apply();MailSyncService.schedule(MainActivity.this);toast("Mail check setting saved");}
                else if(which==3)forgetMail();else if(which==4)goTab(0);
            }else{if(which==0)showCompose("","");else if(which==1)goTab(0);else if(which==2){prefs.edit().putBoolean("sms_notice",!prefs.getBoolean("sms_notice",true)).apply();toast("SMS notification setting saved");}
                else if(which==3){String current=Telephony.Sms.getDefaultSmsPackage(MainActivity.this);Intent app=current==null?null:getPackageManager().getLaunchIntentForPackage(current);if(app!=null)startActivity(app);else toast("System SMS app unavailable");}
                else if(which==4)goTab(1);else if(which==5)discardDraft();}
        }}).show();
    }
    private void discardDraft(){
        handler.postDelayed(new Runnable(){public void run(){if(isFinishing())return;new AlertDialog.Builder(MainActivity.this).setTitle("Discard draft?").setMessage("Remove the unsent draft. Existing messages stay saved.")
            .setPositiveButton("Discard",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){
                if(recipient!=null)recipient.setText("");if(body!=null)body.setText("");prefs.edit().remove("draft_to").remove("draft_body").apply();toast("Draft discarded");goTab(0);
            }}).setNegativeButton("Cancel",null).show();}},180);
    }
    private void forgetMail(){
        handler.postDelayed(new Runnable(){public void run(){new AlertDialog.Builder(MainActivity.this).setTitle("Remove mail account?").setMessage("Remove saved sign-in and cached headers from this phone.")
            .setPositiveButton("Remove",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){cancelTask();
                prefs.edit().remove("mail_password").remove("mail_host").remove("mail_user").remove("mail_cache").remove("mail_newest").remove("mail_validity").remove("mail_namespace").putBoolean("mail_poll",false).apply();
                MailClient.clearBodies(MainActivity.this);
                try{Secrets.forget(MainActivity.this);}catch(Exception ignored){}MailSyncService.schedule(MainActivity.this);showMail();}}).setNegativeButton("Cancel",null).show();}},180);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(screen==2&&recipient!=null&&getCurrentFocus()==recipient){
            if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9){
                if(event.getAction()==KeyEvent.ACTION_DOWN){
                    String value=recipient.getText().toString();
                    if(event.getRepeatCount()==0&&value.length()<21)recipient.setText(value+(char)('0'+code-KeyEvent.KEYCODE_0));
                    else if(code==KeyEvent.KEYCODE_0&&event.getRepeatCount()==1&&value.equals("0"))recipient.setText("+");
                    recipient.setSelection(recipient.length());
                }return true;
            }
            if(code==KeyEvent.KEYCODE_DEL&&recipient.length()>0){
                if(event.getAction()==KeyEvent.ACTION_DOWN){String value=recipient.getText().toString();recipient.setText(event.getRepeatCount()>0?"":value.substring(0,value.length()-1));recipient.setSelection(recipient.length());}return true;
            }
            if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)body.requestFocus();return true;}
        }
        if(code==KeyEvent.KEYCODE_F1||code==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(code==KeyEvent.KEYCODE_F1)goTab(0);else menu();}return true;}
        if(screen==0&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)goTab(tab==0?1:0);return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(screen!=0){saveDraft();goTab(tab);}else super.onBackPressed();}
    @Override protected void onDestroy(){destroyed=true;++generation;worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
