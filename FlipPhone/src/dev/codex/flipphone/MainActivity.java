package dev.codex.flipphone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Hardware keypad dialer. Telecom continues to own ringing and in-call controls. */
public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(12,25,34), PANEL=Color.rgb(25,46,57),
        MINT=Color.rgb(101,226,192), WHITE=Color.rgb(242,249,246), MUTED=Color.rgb(154,183,188),
        RED=Color.rgb(255,140,146);
    private static final String[] TABS={"DIAL","CALLS","PEOPLE","STARS"};
    private final ArrayList<Entry> people=new ArrayList<Entry>(), calls=new ArrayList<Entry>(), stars=new ArrayList<Entry>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler();
    private Future<?> refresh;
    private SharedPreferences prefs;
    private PhoneView view;
    private int page=0, selected=0, generation=0, menuSelected=0;
    private String number="", search="", dataStatus="", simLabel="";
    private boolean loading=false, destroyed=false;
    private final ArrayList<Entry> filtered=new ArrayList<Entry>();
    private String[] menu;
    private Entry menuTarget;
    private static final class Entry {
        String name, number, detail="";
        long contactId;
        boolean missed;
        Entry(String name,String number,long id){this.name=name==null?"":name;this.number=number==null?"":number;contactId=id;}
        String title(){return name.length()==0?number:name;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("phone",MODE_PRIVATE);loadStars();
        view=new PhoneView();setContentView(view);
        if(state!=null){number=cleanNumber(state.getString("number",""));page=state.getInt("page",0);search=state.getString("search","");}
        readIntent(getIntent());
    }
    @Override protected void onResume(){super.onResume();refreshData();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readIntent(intent);view.invalidate();}
    private void readIntent(Intent intent){
        if(intent.hasExtra("tab")){page=Math.max(0,Math.min(3,intent.getIntExtra("tab",0)));selected=0;menu=null;return;}
        Uri data=intent.getData();
        if(data!=null && "tel".equalsIgnoreCase(data.getScheme())){
            number=cleanNumber(data.getSchemeSpecificPart());page=0;selected=0;menu=null;
        }else if(Intent.ACTION_DIAL.equals(intent.getAction())){number="";page=0;selected=0;menu=null;}
    }
    private String cleanNumber(String raw){
        if(raw==null)return "";
        StringBuilder result=new StringBuilder();
        for(int i=0;i<raw.length() && result.length()<60;i++){
            char c=raw.charAt(i);
            if((c>='0'&&c<='9') || c=='*' || c=='#' || c==',' || c==';' || (c=='+'&&result.length()==0))result.append(c);
        }
        return result.toString();
    }
    private String normalized(String value){return PhoneNumberUtils.normalizeNumber(value);}
    private void refreshData(){
        final int token=++generation;
        if(refresh!=null)refresh.cancel(true);
        loading=true;view.invalidate();
        TelephonyManager telephony=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        simLabel=telephony!=null&&telephony.getSimState()==TelephonyManager.SIM_STATE_ABSENT?"NO SIM":"PHONE";
        refresh=worker.submit(new Runnable(){public void run(){
            final ArrayList<Entry> loadedPeople=new ArrayList<Entry>(), loadedCalls=new ArrayList<Entry>();
            String error="";
            Cursor cursor=null;
            try{
                cursor=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.CONTACT_ID},
                    null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC");
                if(cursor!=null)while(cursor.moveToNext()&&!Thread.currentThread().isInterrupted()){
                    String phone=cursor.getString(1);if(phone==null||phone.length()==0)continue;
                    Entry entry=new Entry(cursor.getString(0),phone,cursor.getLong(2));loadedPeople.add(entry);
                }
            }catch(Exception e){error="Contacts unavailable";}
            finally{if(cursor!=null)cursor.close();cursor=null;}
            try{
                cursor=getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.NUMBER_PRESENTATION},
                    null,null,CallLog.Calls.DATE+" DESC");
                SimpleDateFormat date=new SimpleDateFormat("dd MMM HH:mm",Locale.getDefault());
                if(cursor!=null)while(loadedCalls.size()<100&&cursor.moveToNext()&&!Thread.currentThread().isInterrupted()){
                    int type=cursor.getInt(2);
                    Entry entry=new Entry(cursor.getString(1),cursor.getInt(5)==CallLog.Calls.PRESENTATION_ALLOWED?cursor.getString(0):"",0);
                    if(entry.number.length()==0)entry.name="Private / unknown";
                    entry.missed=type==CallLog.Calls.MISSED_TYPE;
                    String label=type==CallLog.Calls.INCOMING_TYPE?"IN":type==CallLog.Calls.OUTGOING_TYPE?"OUT":entry.missed?"MISSED":"OTHER";
                    long duration=cursor.getLong(4);
                    entry.detail=label+"  "+date.format(new Date(cursor.getLong(3)))+"  "+(duration/60)+":"+String.format(Locale.US,"%02d",duration%60);
                    loadedCalls.add(entry);
                }
            }catch(Exception e){error=error.length()==0?"Call history unavailable":"Contacts / history unavailable";}
            finally{if(cursor!=null)cursor.close();}
            if(Thread.currentThread().isInterrupted())return;
            final String message=error;
            handler.post(new Runnable(){public void run(){
                if(destroyed||token!=generation)return;
                people.clear();people.addAll(loadedPeople);calls.clear();calls.addAll(loadedCalls);
                dataStatus=message;loading=false;filterPeople();clampSelection();view.invalidate();
            }});
        }});
    }
    private void filterPeople(){
        filtered.clear();String query=search.toLowerCase(Locale.ROOT);String digits=normalized(search);
        for(Entry item:people)if(query.length()==0 || item.name.toLowerCase(Locale.ROOT).contains(query) ||
            item.number.contains(search) || (digits.length()>0&&normalized(item.number).contains(digits)))filtered.add(item);
    }
    private ArrayList<Entry> list(){return page==1?calls:page==2?filtered:stars;}
    private void clampSelection(){selected=Math.max(0,Math.min(selected,list().size()-1));}
    private Entry target(){
        if(page==0){
            Entry result=new Entry("",number,0);
            for(Entry entry:people)if(normalized(entry.number).equals(normalized(number))&&number.length()>0){result.name=entry.name;result.contactId=entry.contactId;break;}
            return result;
        }
        ArrayList<Entry> rows=list();return rows.size()==0?new Entry("","",0):rows.get(Math.min(selected,rows.size()-1));
    }
    private void setPage(int next){page=(next+4)%4;selected=0;menu=null;view.invalidate();}
    private void append(char digit){
        if(page!=0){page=0;number="";selected=0;menu=null;}
        if(number.length()<60)number+=digit;view.invalidate();
    }
    private void placeCall(Entry entry){
        String dial=cleanNumber(entry.number);
        if(dial.length()==0 || normalized(dial).length()==0){toast("Enter a phone number first");return;}
        if(PhoneNumberUtils.isEmergencyNumber(dial)){
            openSystemDialer(dial);return;
        }
        TelephonyManager phone=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        if(phone!=null&&phone.getSimState()==TelephonyManager.SIM_STATE_ABSENT){toast("Insert a SIM to call");return;}
        try{startActivity(new Intent(Intent.ACTION_CALL,Uri.fromParts("tel",dial,null)));}
        catch(Exception e){toast("Could not start call");}
    }
    private void openSystemDialer(String dial){
        Intent intent=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",dial,null));
        intent.setClassName("com.android.dialer","com.android.dialer.activities.DialtactsActivity");
        try{startActivity(intent);}catch(Exception e){toast("System phone app unavailable");}
    }
    private boolean starred(Entry entry){
        String phone=normalized(entry.number);if(phone.length()==0)return false;
        for(Entry star:stars)if(phone.equals(normalized(star.number)))return true;return false;
    }
    private void loadStars(){
        try{JSONArray saved=new JSONArray(prefs.getString("stars","[]"));
            for(int i=0;i<Math.min(saved.length(),40);i++){
                JSONObject row=saved.optJSONObject(i);if(row==null)continue;
                stars.add(new Entry(row.optString("name"),row.optString("number"),row.optLong("id")));
            }
        }catch(Exception ignored){}
    }
    private void toggleStar(Entry entry){
        if(normalized(entry.number).length()==0){toast("No phone number selected");return;}
        boolean found=false;
        for(int i=0;i<stars.size();i++)if(normalized(stars.get(i).number).equals(normalized(entry.number))){stars.remove(i);found=true;break;}
        if(!found){if(stars.size()>=40){toast("40 stars saved. Remove one first.");return;}stars.add(new Entry(entry.name,entry.number,entry.contactId));}
        JSONArray saved=new JSONArray();
        try{for(Entry star:stars){JSONObject row=new JSONObject();row.put("name",star.name);row.put("number",star.number);row.put("id",star.contactId);saved.put(row);}}
        catch(Exception ignored){}
        prefs.edit().putString("stars",saved.toString()).apply();clampSelection();view.invalidate();toast(found?"Removed from stars":"Saved to stars");
    }
    private void showMenu(boolean detail){
        menuTarget=target();menuSelected=0;
        menu=detail?new String[]{"Use this number",starred(menuTarget)?"Remove star":"Add star","Send message","Save as contact","View contact","Close"}:
            new String[]{"Search contacts",starred(menuTarget)?"Remove star":"Add star","Send message","Save as contact","Refresh lists","Clear number / search","Exit"};
        view.invalidate();
    }
    private void activateMenu(){
        int item=menuSelected;boolean detail=menu.length==6;Entry entry=menuTarget;menu=null;
        if(item==0){if(detail){number=cleanNumber(entry.number);page=0;selected=0;}else searchContacts();}
        else if(item==1)toggleStar(entry);
        else if(item==2){
            if(entry.number.length()==0)toast("No number selected");
            else try{
                Intent message=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",entry.number,null));
                message.setClassName("dev.codex.flippost","dev.codex.flippost.MainActivity");
                try{startActivity(message);}catch(android.content.ActivityNotFoundException missing){message.setComponent(null);startActivity(message);}
            }catch(Exception e){toast("Messages app unavailable");}
        }else if(item==3){
            Intent intent=new Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE,entry.number);
            if(entry.name.length()>0)intent.putExtra(ContactsContract.Intents.Insert.NAME,entry.name);
            try{startActivity(intent);}catch(Exception e){toast("Contact editor unavailable");}
        }else if(item==4){
            if(!detail)refreshData();
            else if(entry.contactId>0)try{startActivity(new Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contactId)));}catch(Exception e){toast("Contact unavailable");}
            else toast("No contact linked to this number");
        }else if(item==5&&!detail){number="";search="";filterPeople();selected=0;}
        else if(item==6)finish();
        view.invalidate();
    }
    private void searchContacts(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(TypedValue.COMPLEX_UNIT_DIP,18);
        input.setInputType(InputType.TYPE_CLASS_TEXT);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setText(search);input.setSelectAllOnFocus(true);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Name / phone number").setView(input)
            .setPositiveButton("Search",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){applySearch(input.getText().toString());}})
            .setNegativeButton("Cancel",null).create();
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener(){
            public boolean onEditorAction(android.widget.TextView text,int action,KeyEvent event){
                if(action==EditorInfo.IME_ACTION_SEARCH){applySearch(input.getText().toString());dialog.dismiss();return true;}return false;
            }
        });dialog.show();input.requestFocus();
    }
    private void applySearch(String text){search=text.trim();filterPeople();page=2;selected=0;menu=null;view.invalidate();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        boolean digit=code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9;
        if(digit||code==KeyEvent.KEYCODE_STAR||code==KeyEvent.KEYCODE_POUND){
            if(down&&event.getRepeatCount()==0){menu=null;append(code==KeyEvent.KEYCODE_STAR?'*':code==KeyEvent.KEYCODE_POUND?'#':(char)('0'+code-KeyEvent.KEYCODE_0));}
            if(down&&code==KeyEvent.KEYCODE_0&&event.getRepeatCount()==1&&page==0&&number.equals("0")){number="+";view.invalidate();}
            return true;
        }
        if(code==KeyEvent.KEYCODE_CALL){if(down&&event.getRepeatCount()==0)placeCall(menu==null?target():menuTarget);return true;}
        if(code==KeyEvent.KEYCODE_MENU){if(down&&event.getRepeatCount()==0){if(menu==null)showMenu(false);else{menu=null;view.invalidate();}}return true;}
        if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_DEL){
            if(down){if(menu!=null)menu=null;
                else if(page==0&&number.length()>0)number=event.getRepeatCount()>0?"":number.substring(0,number.length()-1);
                else if(page==2&&search.length()>0){search="";filterPeople();selected=0;}
                else if(page!=0)setPage(0);else if(event.getRepeatCount()==0)finish();view.invalidate();}
            return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(down&&menu==null)setPage(page+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){
            if(down){int delta=code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1;
                if(menu!=null)menuSelected=Math.max(0,Math.min(menu.length-1,menuSelected+delta));
                else if(page!=0){selected+=delta;clampSelection();}view.invalidate();}return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER){
            if(down&&event.getRepeatCount()==0){if(menu!=null)activateMenu();else if(page==0)placeCall(target());else if(list().size()>0)showMenu(true);}return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onSaveInstanceState(Bundle state){state.putString("number",number);state.putInt("page",page);state.putString("search",search);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;++generation;worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}

    private final class PhoneView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect=new RectF();
        PhoneView(){super(MainActivity.this);setFocusable(true);setFocusableInTouchMode(true);requestFocus();}
        void box(Canvas c,float x,float y,float w,float h,int color){paint.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,10,10,paint);}
        void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){
            paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(value,x,y,paint);
        }
        String fit(String value,float width,float size){
            paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            if(paint.measureText(value)<=width)return value;
            int count=paint.breakText(value,true,Math.max(0,width-paint.measureText("...")),null);
            return value.substring(0,count)+"...";
        }
        @Override protected void onDraw(Canvas c){
            c.drawColor(BG);c.save();c.scale(getWidth()/360f,getHeight()/560f);
            text(c,"Flip Phone",18,33,22,WHITE,true);text(c,simLabel,279,30,12,MINT,true);
            for(int i=0;i<4;i++){if(page==i)box(c,12+i*86,51,80,34,PANEL);text(c,TABS[i],22+i*86,74,12,page==i?MINT:MUTED,true);}
            if(page==0)drawDial(c);else drawList(c);
            text(c,menu==null?(page==0?"Back: erase   |   Menu: tools":"OK: details   |   Call key: call") : "Up / Down + OK   |   Back: close",18,548,12,MUTED,false);
            if(menu!=null)drawMenu(c);
            c.restore();
        }
        void drawDial(Canvas c){
            box(c,16,105,328,94,PANEL);
            String display=number.length()==0?"Enter number":number;
            float size=number.length()==0?24:32;
            paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            int visible=paint.breakText(display,false,296,null);
            text(c,display.substring(display.length()-visible),30,148,size,number.length()==0?MUTED:WHITE,false);
            Entry entry=target();text(c,entry.name.length()==0?(number.length()==0?"Use the keypad below":"Ready to dial"):fit(entry.name,294,15),30,180,15,MINT,false);
            String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};
            String[] letters={"","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","hold for +",""};
            for(int i=0;i<12;i++){
                float x=16+(i%3)*112,y=216+(i/3)*62;
                box(c,x,y,104,54,PANEL);text(c,digits[i],x+43,y+31,27,WHITE,true);
                if(letters[i].length()>0)text(c,letters[i],x+(i==10?24:37),y+46,9,MUTED,false);
            }
            box(c,16,475,328,48,MINT);text(c,"CALL  /  OK",125,505,17,BG,true);
        }
        void drawList(Canvas c){
            ArrayList<Entry> rows=list();
            String label=page==1?"RECENT 100":page==2?(search.length()==0?"CONTACTS":"SEARCH: "+search):"FAVORITES";
            text(c,fit(label,264,13),18,111,13,MUTED,true);text(c,""+rows.size(),310,111,13,MINT,true);
            if(rows.size()==0){
                box(c,16,158,328,180,PANEL);
                text(c,loading?"Loading...":page==1?"No recent calls":page==2?"No contacts found":"Your favorite people",32,208,21,WHITE,true);
                text(c,page==3?"Menu > Add star to save a number":page==2?"Menu > Search contacts / Save as contact":"Calls appear here after using your SIM",32,252,13,MUTED,false);
            }else{
                int start=(selected/6)*6;
                for(int i=0;i<6&&start+i<rows.size();i++){
                    Entry entry=rows.get(start+i);float y=126+i*62;
                    if(selected==start+i)box(c,12,y,336,57,PANEL);
                    text(c,fit(entry.title(),304,19),25,y+24,19,entry.missed?RED:WHITE,true);
                    String sub=page==1?entry.detail:entry.number;
                    text(c,fit(sub,304,12),25,y+45,12,MUTED,false);
                }
            }
            String footer=loading?"Refreshing...":dataStatus.length()>0?dataStatus:page==2?"Left / Right: switch tabs":"Left / Right: switch tabs";
            text(c,fit(footer,324,12),18,523,12,MUTED,false);
        }
        void drawMenu(Canvas c){
            paint.setColor(Color.argb(210,0,0,0));c.drawRect(0,0,360,560,paint);
            int height=100+menu.length*43;float top=(560-height)/2f;
            box(c,16,top,328,height,PANEL);
            text(c,fit(menuTarget.title().length()>0?menuTarget.title():"Phone tools",291,19),32,top+34,19,WHITE,true);
            text(c,fit(menuTarget.number.length()>0?menuTarget.number:"Choose an action",291,13),32,top+57,13,MUTED,false);
            for(int i=0;i<menu.length;i++){
                float y=top+75+i*43;if(i==menuSelected)box(c,26,y,308,38,BG);
                text(c,menu[i],38,y+25,16,i==menuSelected?MINT:WHITE,false);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=event.getX()*360/getWidth(),y=event.getY()*560/getHeight();
            if(menu!=null){
                float top=(560-(100+menu.length*43))/2f;int index=(int)((y-top-75)/43);
                if(y>=top+75&&index>=0&&index<menu.length){menuSelected=index;activateMenu();}return true;
            }
            if(y>=51&&y<=85){int tab=(int)((x-12)/86);if(tab>=0&&tab<4)setPage(tab);return true;}
            if(page==0){
                if(y>=475&&y<=523)placeCall(target());
                else if(y>=216&&y<456){int row=(int)((y-216)/62),col=(int)((x-16)/112);if(x>=16&&col>=0&&col<3&&row<4){String keys="123456789*0#";append(keys.charAt(row*3+col));}}
            }else if(y>=126&&y<498){int index=(selected/6)*6+(int)((y-126)/62);if(index<list().size()){selected=index;showMenu(true);}}
            invalidate();return true;
        }
    }
}
