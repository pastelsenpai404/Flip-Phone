package dev.codex.flipdeck;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private DeckView deck;
    private final ArrayList<AppEntry> apps=new ArrayList<AppEntry>();
    private static final int HOME=0, ALL=1, TOOLS=2;
    private static final int BG=Color.rgb(12,25,34);
    private static final int PANEL=Color.rgb(25,46,57);
    private static final int PANEL2=Color.rgb(31,58,69);
    private static final int MINT=Color.rgb(101,226,192);
    private static final int ORANGE=Color.rgb(255,181,106);
    private static final int WHITE=Color.rgb(242,249,246);
    private static final int MUTED=Color.rgb(154,183,188);
    private static final String[] HOME_LABELS={"PHONE","MESSAGES","CONTACTS","MUSIC","CAMERA","PHOTOS","BROWSER","SETTINGS","ALL APPS"};
    private static final String[] TOOL_LABELS={"BLUETOOTH","WI-FI","SOUND","DISPLAY","FILES","CALENDAR","POCKET MUSIC","POCKET HEARTS","ALL APPS"};

    private static final class AppEntry {
        String label, packageName, activityName;
        Drawable icon;
        AppEntry(String label,String packageName,String activityName,Drawable icon) {
            this.label=label;this.packageName=packageName;this.activityName=activityName;this.icon=icon;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        deck=new DeckView(this);
        setContentView(deck);
        refreshApps();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(deck!=null) {deck.page=HOME;deck.selected=0;deck.invalidate();}
    }

    @Override protected void onResume() {
        super.onResume();
        refreshApps();
        if(deck!=null) deck.invalidate();
    }

    @Override public void onBackPressed() {
        if(deck.page!=HOME) {deck.page=HOME;deck.selected=0;deck.invalidate();}
    }

    private void refreshApps() {
        PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<AppEntry> fresh=new ArrayList<AppEntry>();
        for(ResolveInfo info:pm.queryIntentActivities(query,0)) {
            if(info.activityInfo==null) continue;
            String pkg=info.activityInfo.packageName;
            if(getPackageName().equals(pkg)) continue;
            CharSequence name=info.loadLabel(pm);
            fresh.add(new AppEntry(name==null?pkg:name.toString(),pkg,info.activityInfo.name,info.loadIcon(pm)));
        }
        final Collator collator=Collator.getInstance();
        Collections.sort(fresh,new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a,AppEntry b) {return collator.compare(a.label,b.label);}
        });
        apps.clear();apps.addAll(fresh);
        if(deck!=null && deck.page==ALL) deck.selected=Math.max(0,Math.min(deck.selected,apps.size()-1));
    }

    private boolean launchPackage(String pkg) {
        Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null) return false;
        startActivity(i);return true;
    }

    private boolean launchIntent(Intent i) {
        try {startActivity(i);return true;}
        catch(ActivityNotFoundException e) {return false;}
        catch(SecurityException e) {return false;}
    }

    private void unavailable(String label) {Toast.makeText(this,label+" unavailable",Toast.LENGTH_SHORT).show();}

    private void openHome(int index) {
        boolean ok=true;
        switch(index) {
            case 0: ok=launchIntent(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:")));break;
            case 1: ok=launchPackage("jp.co.sharp.android.messaging");
                if(!ok) ok=launchIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
                if(!ok) ok=launchIntent(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")));break;
            case 2: ok=launchPackage("com.android.contacts");break;
            case 3: ok=launchPackage("dev.codex.pocketmusic");
                if(!ok) ok=launchPackage("com.spotify.music");
                if(!ok) ok=launchPackage("com.android.music");break;
            case 4: ok=launchPackage("jp.co.sharp.android.camera");
                if(!ok) ok=launchIntent(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));break;
            case 5: ok=launchPackage("jp.co.sharp.android.cm");
                if(!ok) ok=launchIntent(new Intent(Intent.ACTION_VIEW,MediaStore.Images.Media.EXTERNAL_CONTENT_URI));break;
            case 6: ok=launchPackage("org.mozilla.firefox");
                if(!ok) ok=launchPackage("mark.via.gp");
                if(!ok) ok=launchIntent(new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")));break;
            case 7: ok=launchIntent(new Intent(Settings.ACTION_SETTINGS));break;
            case 8: deck.page=ALL;deck.selected=0;refreshApps();deck.invalidate();return;
        }
        if(!ok) unavailable(HOME_LABELS[index]);
    }

    private void openTool(int index) {
        boolean ok=true;
        switch(index) {
            case 0: ok=launchIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));break;
            case 1: ok=launchIntent(new Intent(Settings.ACTION_WIFI_SETTINGS));break;
            case 2: ok=launchIntent(new Intent(Settings.ACTION_SOUND_SETTINGS));break;
            case 3: ok=launchIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS));break;
            case 4: ok=launchPackage("jp.co.sharp.android.cm");break;
            case 5: ok=launchPackage("com.pranavpandey.calendar");
                if(!ok) ok=launchPackage("com.android.calendar");break;
            case 6: ok=launchPackage("dev.codex.pocketmusic");break;
            case 7: ok=launchPackage("dev.codex.pockethearts");break;
            case 8: deck.page=ALL;deck.selected=0;refreshApps();deck.invalidate();return;
        }
        if(!ok) unavailable(TOOL_LABELS[index]);
    }

    private void openApp(int index) {
        if(index<0 || index>=apps.size()) return;
        AppEntry entry=apps.get(index);
        Intent i=new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(entry.packageName,entry.activityName);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(!launchIntent(i)) unavailable(entry.label);
    }

    private final class DeckView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final RectF r=new RectF();
        int page=HOME,selected=0;
        DeckView(Context c) {super(c);setFocusable(true);setFocusableInTouchMode(true);requestFocus();}

        @Override protected void onDraw(Canvas actual) {
            super.onDraw(actual);
            Canvas c=actual;
            c.save();c.scale(getWidth()/360f,getHeight()/560f);
            c.drawColor(BG);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(17,38,47));c.drawCircle(372,5,100,p);
            p.setColor(Color.rgb(17,36,45));c.drawCircle(-30,560,100,p);
            if(page==HOME) drawHome(c);
            else if(page==TOOLS) drawTools(c);
            else drawApps(c);
            c.restore();
            postInvalidateDelayed(30000);
        }

        private void drawHome(Canvas c) {
            text(c,"FLIP / DECK",18,30,17,MINT,true,Paint.Align.LEFT);
            text(c,"NP601SH",342,30,12,MUTED,true,Paint.Align.RIGHT);
            String clock=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
            String date=new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(new Date());
            text(c,clock,18,94,53,WHITE,true,Paint.Align.LEFT);
            text(c,date.toUpperCase(Locale.getDefault()),20,119,13,MUTED,true,Paint.Align.LEFT);
            status(c);
            grid(c,HOME_LABELS);
            text(c,"0  TOOLS",18,540,12,ORANGE,true,Paint.Align.LEFT);
            text(c,"#  ALL APPS",342,540,12,MINT,true,Paint.Align.RIGHT);
        }

        private void drawTools(Canvas c) {
            sectionHeader(c,"QUICK TOOLS","0 / MENU TO RETURN");
            status(c);
            grid(c,TOOL_LABELS);
            text(c,"*  HOME",18,540,12,ORANGE,true,Paint.Align.LEFT);
            text(c,"#  ALL APPS",342,540,12,MINT,true,Paint.Align.RIGHT);
        }

        private void sectionHeader(Canvas c,String title,String sub) {
            text(c,"FLIP / DECK",18,30,17,MINT,true,Paint.Align.LEFT);
            text(c,title,18,87,30,WHITE,true,Paint.Align.LEFT);
            text(c,sub,20,113,12,MUTED,true,Paint.Align.LEFT);
        }

        private void status(Canvas c) {
            int level=0;
            Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null) {
                int value=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
                int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
                if(scale>0) level=value*100/scale;
            }
            BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
            WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            String btText=(bt!=null && bt.isEnabled())?"BT ON":"BT OFF";
            String wifiText=(wifi!=null && wifi.isWifiEnabled())?"WI-FI ON":"WI-FI OFF";
            chip(c,18,127,92,25,btText,ORANGE);
            chip(c,117,127,103,25,wifiText,MINT);
            chip(c,227,127,115,25,"BAT "+level+"%",WHITE);
        }

        private void chip(Canvas c,float x,float y,float w,float h,String label,int color) {
            p.setColor(PANEL2);c.drawRoundRect(x,y,x+w,y+h,7,7,p);
            text(c,label,x+w/2,y+17,11,color,true,Paint.Align.CENTER);
        }

        private void grid(Canvas c,String[] labels) {
            float start=164, cellW=102, cellH=103, gap=9;
            for(int i=0;i<9;i++) {
                int row=i/3,col=i%3;
                float x=18+col*(cellW+gap),y=start+row*(cellH+gap);
                boolean focus=selected==i;
                p.setColor(focus?MINT:PANEL);
                c.drawRoundRect(x,y,x+cellW,y+cellH,13,13,p);
                if(!focus) {
                    p.setColor(PANEL2);c.drawRoundRect(x+1,y+1,x+cellW-1,y+cellH-1,12,12,p);
                }
                int ink=focus?BG:WHITE;
                if(page==TOOLS) toolIcon(c,i,x+12,y+18,focus?BG:(i==8?MINT:ORANGE));
                else icon(c,i,x+12,y+18,focus?BG:(i==8?MINT:ORANGE));
                text(c,String.valueOf(i+1),x+cellW-12,y+23,12,focus?BG:MUTED,true,Paint.Align.RIGHT);
                String label=labels[i];
                int size=label.length()>10?11:13;
                text(c,label,x+11,y+82,size,ink,true,Paint.Align.LEFT);
            }
        }

        private void drawApps(Canvas c) {
            text(c,"FLIP / DECK",18,30,17,MINT,true,Paint.Align.LEFT);
            text(c,"ALL APPS",18,79,30,WHITE,true,Paint.Align.LEFT);
            text(c,apps.size()+" INSTALLED",342,79,12,MUTED,true,Paint.Align.RIGHT);
            p.setColor(PANEL2);c.drawRoundRect(18,94,342,96,1,1,p);
            if(apps.isEmpty()) text(c,"No launchable apps found",180,250,16,MUTED,false,Paint.Align.CENTER);
            int first=Math.max(0,(selected/6)*6);
            for(int row=0;row<6 && first+row<apps.size();row++) {
                int index=first+row;
                AppEntry entry=apps.get(index);
                float y=105+row*67;
                boolean focus=index==selected;
                p.setColor(focus?MINT:PANEL);
                c.drawRoundRect(18,y,342,y+58,11,11,p);
                if(entry.icon!=null) {
                    entry.icon.setBounds(29,(int)y+11,65,(int)y+47);
                    entry.icon.draw(c);
                }
                p.setTextSize(17);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                String label=fit(entry.label,239);
                text(c,label,77,y+36,17,focus?BG:WHITE,true,Paint.Align.LEFT);
                text(c,String.valueOf(index+1),329,y+34,11,focus?BG:MUTED,true,Paint.Align.RIGHT);
            }
            text(c,"*  HOME",18,540,12,ORANGE,true,Paint.Align.LEFT);
            text(c,"UP / DOWN  BROWSE",342,540,12,MINT,true,Paint.Align.RIGHT);
        }

        private String fit(String value,float max) {
            if(p.measureText(value)<=max) return value;
            while(value.length()>1 && p.measureText(value+"...")>max) value=value.substring(0,value.length()-1);
            return value+"...";
        }

        private void icon(Canvas c,int i,float x,float y,int color) {
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(i) {
                case 0: // phone
                    path.reset();path.moveTo(x+4,y+3);path.lineTo(x+10,y+9);path.lineTo(x+7,y+13);
                    path.cubicTo(x+11,y+21,x+17,y+27,x+25,y+29);path.lineTo(x+29,y+25);
                    path.lineTo(x+35,y+31);path.lineTo(x+30,y+36);
                    path.cubicTo(x+16,y+38,x+1,y+22,x+2,y+8);path.close();c.drawPath(path,p);break;
                case 1: // message
                    c.drawRoundRect(x+2,y+5,x+35,y+30,5,5,p);
                    path.reset();path.moveTo(x+10,y+30);path.lineTo(x+7,y+36);path.lineTo(x+22,y+30);c.drawPath(path,p);break;
                case 2: // contacts
                    c.drawCircle(x+19,y+11,7,p);c.drawArc(x+3,y+18,x+35,y+39,185,170,false,p);break;
                case 3: // music
                    c.drawLine(x+16,y+6,x+16,y+31,p);c.drawLine(x+16,y+6,x+33,y+2,p);
                    c.drawLine(x+33,y+2,x+33,y+26,p);c.drawOval(x+4,y+27,x+16,y+35,p);c.drawOval(x+21,y+22,x+33,y+30,p);break;
                case 4: // camera
                    c.drawRoundRect(x+2,y+9,x+35,y+32,4,4,p);c.drawCircle(x+19,y+20,7,p);c.drawLine(x+9,y+9,x+13,y+5,p);c.drawLine(x+13,y+5,x+24,y+5,p);break;
                case 5: // photos
                    c.drawRoundRect(x+2,y+5,x+35,y+34,3,3,p);c.drawCircle(x+27,y+13,3,p);
                    path.reset();path.moveTo(x+4,y+29);path.lineTo(x+14,y+18);path.lineTo(x+21,y+25);path.lineTo(x+26,y+21);path.lineTo(x+34,y+30);c.drawPath(path,p);break;
                case 6: // browser
                    c.drawCircle(x+19,y+19,16,p);c.drawOval(x+12,y+3,x+26,y+35,p);
                    c.drawLine(x+4,y+19,x+34,y+19,p);break;
                case 7: // settings
                    c.drawCircle(x+19,y+19,13,p);c.drawCircle(x+19,y+19,5,p);
                    for(int n=0;n<8;n++) {double a=n*Math.PI/4;c.drawLine(x+19+(float)Math.cos(a)*14,y+19+(float)Math.sin(a)*14,x+19+(float)Math.cos(a)*18,y+19+(float)Math.sin(a)*18,p);}break;
                case 8: // all apps
                    c.drawRoundRect(x+2,y+4,x+15,y+17,2,2,p);c.drawRoundRect(x+22,y+4,x+35,y+17,2,2,p);
                    c.drawRoundRect(x+2,y+24,x+15,y+37,2,2,p);c.drawRoundRect(x+22,y+24,x+35,y+37,2,2,p);break;
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void toolIcon(Canvas c,int i,float x,float y,int color) {
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(i) {
                case 0: // Bluetooth
                    path.reset();path.moveTo(x+18,y+2);path.lineTo(x+18,y+37);path.lineTo(x+29,y+27);path.lineTo(x+12,y+10);
                    path.moveTo(x+18,y+2);path.lineTo(x+29,y+12);path.lineTo(x+12,y+29);c.drawPath(path,p);break;
                case 1: // Wi-Fi
                    c.drawArc(x+1,y+5,x+37,y+32,212,116,false,p);
                    c.drawArc(x+8,y+13,x+30,y+33,215,110,false,p);
                    c.drawCircle(x+19,y+31,2,p);break;
                case 2: // Sound
                    path.reset();path.moveTo(x+3,y+15);path.lineTo(x+11,y+15);path.lineTo(x+21,y+7);
                    path.lineTo(x+21,y+33);path.lineTo(x+11,y+25);path.lineTo(x+3,y+25);path.close();c.drawPath(path,p);
                    c.drawArc(x+18,y+7,x+36,y+33,-65,130,false,p);break;
                case 3: // Display
                    c.drawRoundRect(x+2,y+4,x+36,y+30,3,3,p);c.drawLine(x+19,y+30,x+19,y+36,p);c.drawLine(x+10,y+36,x+28,y+36,p);break;
                case 4: // Files
                    path.reset();path.moveTo(x+2,y+11);path.lineTo(x+14,y+11);path.lineTo(x+18,y+15);
                    path.lineTo(x+36,y+15);path.lineTo(x+34,y+34);path.lineTo(x+3,y+34);path.close();c.drawPath(path,p);break;
                case 5: // Calendar
                    c.drawRoundRect(x+3,y+6,x+35,y+36,3,3,p);c.drawLine(x+3,y+16,x+35,y+16,p);
                    c.drawLine(x+12,y+3,x+12,y+11,p);c.drawLine(x+26,y+3,x+26,y+11,p);
                    c.drawCircle(x+13,y+24,1,p);c.drawCircle(x+24,y+24,1,p);break;
                case 6: // Music
                    c.drawLine(x+16,y+6,x+16,y+31,p);c.drawLine(x+16,y+6,x+33,y+2,p);
                    c.drawLine(x+33,y+2,x+33,y+26,p);c.drawOval(x+4,y+27,x+16,y+35,p);c.drawOval(x+21,y+22,x+33,y+30,p);break;
                case 7: // Heart
                    path.reset();path.moveTo(x+19,y+35);
                    path.cubicTo(x+10,y+27,x+2,y+19,x+4,y+11);path.cubicTo(x+6,y+3,x+15,y+3,x+19,y+10);
                    path.cubicTo(x+23,y+3,x+32,y+3,x+34,y+11);path.cubicTo(x+36,y+19,x+28,y+27,x+19,y+35);c.drawPath(path,p);break;
                case 8: // Apps
                    c.drawRoundRect(x+2,y+4,x+15,y+17,2,2,p);c.drawRoundRect(x+22,y+4,x+35,y+17,2,2,p);
                    c.drawRoundRect(x+2,y+24,x+15,y+37,2,2,p);c.drawRoundRect(x+22,y+24,x+35,y+37,2,2,p);break;
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align) {
            p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);p.setTextAlign(align);c.drawText(s,x,y,p);
        }

        private void activate() {
            if(page==HOME) openHome(selected);
            else if(page==TOOLS) openTool(selected);
            else openApp(selected);
        }

        @Override public boolean onKeyDown(int code,KeyEvent event) {
            if(code==KeyEvent.KEYCODE_DPAD_UP) {selected=Math.max(0,selected-(page==ALL?1:3));invalidate();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_DOWN) {selected=Math.min((page==ALL?apps.size():9)-1,selected+(page==ALL?1:3));invalidate();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_LEFT) {selected=Math.max(0,selected-(page==ALL?6:1));invalidate();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_RIGHT) {selected=Math.min((page==ALL?apps.size():9)-1,selected+(page==ALL?6:1));invalidate();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_5 && page==ALL) {
                if(event.getRepeatCount()==0) activate();return true;
            }
            if(code>=KeyEvent.KEYCODE_1 && code<=KeyEvent.KEYCODE_9 && page!=ALL) {
                if(event.getRepeatCount()==0) {selected=code-KeyEvent.KEYCODE_1;activate();}return true;
            }
            if(code==KeyEvent.KEYCODE_0 || code==KeyEvent.KEYCODE_MENU) {
                page=page==TOOLS?HOME:TOOLS;selected=0;invalidate();return true;
            }
            if(code==KeyEvent.KEYCODE_POUND) {page=ALL;selected=0;refreshApps();invalidate();return true;}
            if(code==KeyEvent.KEYCODE_STAR || code==KeyEvent.KEYCODE_BACK) {page=HOME;selected=0;invalidate();return true;}
            if(code==KeyEvent.KEYCODE_CALL) {openHome(0);return true;}
            return super.onKeyDown(code,event);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX()*360/getWidth(),y=e.getY()*560/getHeight();
            if(page==ALL) {
                if(y>104 && y<505) {int index=(selected/6)*6+(int)((y-105)/67);if(index<apps.size()) {selected=index;activate();}}
                else if(y>512) {page=HOME;selected=0;invalidate();}
                return true;
            }
            if(y>=164 && y<491 && x>=18 && x<342) {
                int col=(int)((x-18)/111),row=(int)((y-164)/112);
                if(col<3 && row<3) {selected=row*3+col;activate();invalidate();}
            } else if(y>515) {
                if(x<150) {page=page==HOME?TOOLS:HOME;selected=0;}
                else {page=ALL;selected=0;refreshApps();}
                invalidate();
            }
            return true;
        }
    }
}
