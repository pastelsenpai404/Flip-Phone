package dev.codex.flipdeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private DeckView deck;
    private final ArrayList<AppEntry> apps=new ArrayList<AppEntry>();
    private static final int HOME=0, ALL=1, TOOLS=2, PHOTO=3;
    private static final int BG=Color.rgb(12,25,34);
    private static final int PANEL=Color.argb(165,25,46,57);
    private static final int PANEL2=Color.argb(158,31,58,69);
    private static final int MINT=Color.rgb(101,226,192);
    private static final int ORANGE=Color.rgb(255,181,106);
    private static final int WHITE=Color.rgb(242,249,246);
    private static final int MUTED=Color.rgb(154,183,188);
    private static final String[] HOME_LABELS={"MUSIC","PHOTOS","CALENDAR","NOTES","CALCULATOR","ALARM","FILES","SETTINGS","ALL APPS"};
    private static final String[] TOOL_LABELS={"BLUETOOTH","WALLPAPER","SOUND","DISPLAY","STORAGE","BATTERY","APP SETTINGS","DATE & TIME","ALL APPS"};
    private static final int PICK_WALLPAPER=101;
    private Bitmap customWallpaper;
    private Drawable phoneWallpaper;
    private int wallpaperMode;

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
        wallpaperMode=getPreferences(0).getInt("wallpaper_mode",0);
        loadWallpaper();
        deck.page=PHOTO;
        refreshApps();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(deck!=null) {deck.page=PHOTO;deck.selected=0;deck.invalidate();}
    }

    @Override protected void onResume() {
        super.onResume();
        refreshApps();
        if(wallpaperMode==0) loadWallpaper();
        if(deck!=null) deck.invalidate();
    }

    @Override public void onBackPressed() {
        if(deck.page!=PHOTO) {deck.page=PHOTO;deck.selected=0;deck.invalidate();}
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

    private void openPhone() {
        if(!launchIntent(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:")))) unavailable("Phone");
    }
    private void openMail() {
        boolean ok=launchPackage("jp.co.sharp.android.messaging");
        if(!ok) ok=launchIntent(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")));
        if(!ok) unavailable("Messages");
    }
    private void openContacts() {
        boolean ok=launchPackage("jp.co.sharp.android.addressbook.app");
        if(!ok) ok=launchPackage("com.android.contacts");
        if(!ok) unavailable("Contacts");
    }
    private void openBrowser() {
        boolean ok=launchPackage("com.android.browser");
        if(!ok) ok=launchPackage("org.mozilla.firefox");
        if(!ok) unavailable("Browser");
    }
    private void openCamera() {
        boolean ok=launchPackage("jp.co.sharp.android.camera");
        if(!ok) ok=launchIntent(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        if(!ok) unavailable("Camera");
    }

    private void loadWallpaper() {
        if(customWallpaper!=null) {customWallpaper.recycle();customWallpaper=null;}
        phoneWallpaper=null;
        if(wallpaperMode==1) {
            customWallpaper=BitmapFactory.decodeFile(new File(getFilesDir(),"wallpaper.jpg").getAbsolutePath());
        } else if(wallpaperMode==0) {
            try {phoneWallpaper=WallpaperManager.getInstance(this).getDrawable();}
            catch(Exception ignored) {phoneWallpaper=null;}
        }
        if(deck!=null) deck.invalidate();
    }

    private void wallpaperMenu() {
        new AlertDialog.Builder(this).setTitle("Wallpaper")
            .setItems(new String[]{"Choose a photo","Use phone wallpaper","No photo"},
                new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog,int which) {
                        if(which==0) {
                            Intent pick=new Intent(Intent.ACTION_GET_CONTENT);
                            pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
                            try {startActivityForResult(Intent.createChooser(pick,"Choose a photo"),PICK_WALLPAPER);}
                            catch(ActivityNotFoundException e) {unavailable("Photo picker");}
                        } else {
                            wallpaperMode=which==1?0:2;
                            getPreferences(0).edit().putInt("wallpaper_mode",wallpaperMode).apply();
                            loadWallpaper();
                        }
                    }
                }).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_WALLPAPER || result!=RESULT_OK || data==null || data.getData()==null) return;
        File temp=new File(getCacheDir(),"wallpaper-source");
        InputStream in=null;FileOutputStream out=null;
        try {
            in=getContentResolver().openInputStream(data.getData());
            out=new FileOutputStream(temp);
            byte[] chunk=new byte[8192];int count;
            while((count=in.read(chunk))!=-1) out.write(chunk,0,count);
            out.close();out=null;in.close();in=null;
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(temp.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0) throw new Exception("Invalid image");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>1200 || bounds.outHeight/options.inSampleSize>1800) options.inSampleSize*=2;
            Bitmap photo=BitmapFactory.decodeFile(temp.getAbsolutePath(),options);
            if(photo==null) throw new Exception("Could not decode image");
            int orientation=new ExifInterface(temp.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            int degrees=orientation==ExifInterface.ORIENTATION_ROTATE_90?90:orientation==ExifInterface.ORIENTATION_ROTATE_180?180:orientation==ExifInterface.ORIENTATION_ROTATE_270?270:0;
            if(degrees!=0) {
                Matrix matrix=new Matrix();matrix.postRotate(degrees);
                Bitmap rotated=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),matrix,true);
                photo.recycle();photo=rotated;
            }
            FileOutputStream saved=new FileOutputStream(new File(getFilesDir(),"wallpaper.jpg"));
            photo.compress(Bitmap.CompressFormat.JPEG,88,saved);saved.close();photo.recycle();
            wallpaperMode=1;
            getPreferences(0).edit().putInt("wallpaper_mode",1).apply();
            loadWallpaper();
            deck.page=PHOTO;deck.selected=0;deck.invalidate();
        } catch(Exception e) {
            Toast.makeText(this,"Could not set this photo",Toast.LENGTH_LONG).show();
        } finally {
            try {if(in!=null) in.close();} catch(Exception ignored) {}
            try {if(out!=null) out.close();} catch(Exception ignored) {}
            temp.delete();
        }
    }

    private void openHome(int index) {
        boolean ok=true;
        switch(index) {
            case 0: ok=launchPackage("dev.codex.pocketmusic");
                if(!ok) ok=launchPackage("com.spotify.music");
                if(!ok) ok=launchPackage("com.android.music");break;
            case 1: ok=launchPackage("jp.co.sharp.android.cm");
                if(!ok) ok=launchIntent(new Intent(Intent.ACTION_VIEW,MediaStore.Images.Media.EXTERNAL_CONTENT_URI));break;
            case 2: ok=launchPackage("com.pranavpandey.calendar");
                if(!ok) ok=launchPackage("com.android.calendar");break;
            case 3: ok=launchPackage("jp.co.sharp.android.memopad");break;
            case 4: ok=launchPackage("jp.co.sharp.android.calc");break;
            case 5: ok=launchPackage("jp.co.sharp.android.timerapps");break;
            case 6: ok=launchPackage("jp.co.sharp.android.cm");break;
            case 7: ok=launchIntent(new Intent(Settings.ACTION_SETTINGS));break;
            case 8: deck.page=ALL;deck.selected=0;refreshApps();deck.invalidate();return;
        }
        if(!ok) unavailable(HOME_LABELS[index]);
    }

    private void openTool(int index) {
        boolean ok=true;
        switch(index) {
            case 0: ok=launchIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));break;
            case 1: wallpaperMenu();return;
            case 2: ok=launchIntent(new Intent(Settings.ACTION_SOUND_SETTINGS));break;
            case 3: ok=launchIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS));break;
            case 4: ok=launchIntent(new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"));
                if(!ok) ok=launchIntent(new Intent(Settings.ACTION_SETTINGS));break;
            case 5: ok=launchIntent(new Intent("android.intent.action.POWER_USAGE_SUMMARY"));
                if(!ok) ok=launchIntent(new Intent(Settings.ACTION_SETTINGS));break;
            case 6: ok=launchIntent(new Intent(Settings.ACTION_APPLICATION_SETTINGS));break;
            case 7: ok=launchIntent(new Intent(Settings.ACTION_DATE_SETTINGS));break;
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
            if(customWallpaper!=null) {
                int bw=customWallpaper.getWidth(),bh=customWallpaper.getHeight();
                float desired=360f/560f,ratio=(float)bw/bh;
                Rect source;
                if(ratio>desired) {int width=(int)(bh*desired);source=new Rect((bw-width)/2,0,(bw+width)/2,bh);}
                else {int height=(int)(bw/desired);source=new Rect(0,(bh-height)/2,bw,(bh+height)/2);}
                p.setColor(Color.WHITE);c.drawBitmap(customWallpaper,source,new RectF(0,0,360,560),p);
            } else if(phoneWallpaper!=null) {
                phoneWallpaper.setBounds(0,0,360,560);
                phoneWallpaper.draw(c);
            }
            if(customWallpaper!=null || phoneWallpaper!=null) {
                p.setColor(Color.argb(page==PHOTO?43:73,4,15,22));c.drawRect(0,0,360,560,p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(85,17,38,47));c.drawCircle(372,5,100,p);
            p.setColor(Color.argb(85,17,36,45));c.drawCircle(-30,560,100,p);
            if(page==PHOTO) drawPhoto(c);
            else if(page==HOME) drawHome(c);
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

        private void drawPhoto(Canvas c) {
            p.setColor(Color.argb(124,4,18,26));c.drawRoundRect(13,13,347,155,17,17,p);
            text(c,"FLIP / DECK",25,38,17,MINT,true,Paint.Align.LEFT);
            String clock=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
            String date=new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(new Date());
            text(c,clock,25,102,54,WHITE,true,Paint.Align.LEFT);
            text(c,date.toUpperCase(Locale.getDefault()),27,129,13,WHITE,true,Paint.Align.LEFT);
            int level=batteryLevel();
            text(c,"BAT "+level+"%",332,41,12,WHITE,true,Paint.Align.RIGHT);
            p.setColor(Color.argb(153,6,22,30));c.drawRoundRect(0,446,360,560,0,0,p);
            String[] dock={"MUSIC","PHOTOS","APPS"};
            for(int i=0;i<3;i++) {
                float x=17+i*112;
                p.setColor(selected==i?MINT:Color.argb(183,25,46,57));
                c.drawRoundRect(x,463,x+102,521,12,12,p);
                text(c,dock[i],x+51,498,15,selected==i?BG:WHITE,true,Paint.Align.CENTER);
            }
            text(c,"*  GRID 1–9",18,544,11,ORANGE,true,Paint.Align.LEFT);
            text(c,"#  ALL APPS",342,544,11,MINT,true,Paint.Align.RIGHT);
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
            int level=batteryLevel();
            BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
            WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            String btText=(bt!=null && bt.isEnabled())?"BT ON":"BT OFF";
            String wifiText=(wifi!=null && wifi.isWifiEnabled())?"WI-FI ON":"WI-FI OFF";
            chip(c,18,127,92,25,btText,ORANGE);
            chip(c,117,127,103,25,wifiText,MINT);
            chip(c,227,127,115,25,"BAT "+level+"%",WHITE);
        }

        private int batteryLevel() {
            Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery==null) return 0;
            int value=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
            int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            return scale>0?value*100/scale:0;
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
                case 0: // music
                    c.drawLine(x+16,y+6,x+16,y+31,p);c.drawLine(x+16,y+6,x+33,y+2,p);
                    c.drawLine(x+33,y+2,x+33,y+26,p);c.drawOval(x+4,y+27,x+16,y+35,p);c.drawOval(x+21,y+22,x+33,y+30,p);break;
                case 1: // photos
                    c.drawRoundRect(x+2,y+5,x+35,y+34,3,3,p);c.drawCircle(x+27,y+13,3,p);
                    path.reset();path.moveTo(x+4,y+29);path.lineTo(x+14,y+18);path.lineTo(x+21,y+25);path.lineTo(x+26,y+21);path.lineTo(x+34,y+30);c.drawPath(path,p);break;
                case 2: // calendar
                    c.drawRoundRect(x+3,y+6,x+35,y+36,3,3,p);c.drawLine(x+3,y+16,x+35,y+16,p);
                    c.drawLine(x+12,y+3,x+12,y+11,p);c.drawLine(x+26,y+3,x+26,y+11,p);
                    c.drawCircle(x+13,y+24,1,p);c.drawCircle(x+24,y+24,1,p);break;
                case 3: // notes
                    c.drawRoundRect(x+4,y+3,x+34,y+37,3,3,p);
                    c.drawLine(x+10,y+12,x+28,y+12,p);c.drawLine(x+10,y+20,x+28,y+20,p);c.drawLine(x+10,y+28,x+23,y+28,p);break;
                case 4: // calculator
                    c.drawRoundRect(x+5,y+2,x+33,y+38,3,3,p);c.drawLine(x+10,y+11,x+28,y+11,p);
                    for(int row=0;row<2;row++) for(int col=0;col<3;col++) c.drawCircle(x+12+col*7,y+21+row*8,1.5f,p);break;
                case 5: // alarm
                    c.drawCircle(x+19,y+21,12,p);c.drawLine(x+19,y+21,x+19,y+13,p);c.drawLine(x+19,y+21,x+25,y+25,p);
                    c.drawArc(x+2,y+1,x+15,y+14,195,130,false,p);c.drawArc(x+23,y+1,x+36,y+14,215,130,false,p);break;
                case 6: // files
                    path.reset();path.moveTo(x+2,y+11);path.lineTo(x+14,y+11);path.lineTo(x+18,y+15);
                    path.lineTo(x+36,y+15);path.lineTo(x+34,y+34);path.lineTo(x+3,y+34);path.close();c.drawPath(path,p);break;
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
                case 1: // Wallpaper
                    c.drawRoundRect(x+2,y+4,x+36,y+35,3,3,p);c.drawCircle(x+27,y+12,3,p);
                    path.reset();path.moveTo(x+4,y+30);path.lineTo(x+15,y+18);path.lineTo(x+22,y+25);path.lineTo(x+27,y+20);path.lineTo(x+34,y+29);c.drawPath(path,p);break;
                case 2: // Sound
                    path.reset();path.moveTo(x+3,y+15);path.lineTo(x+11,y+15);path.lineTo(x+21,y+7);
                    path.lineTo(x+21,y+33);path.lineTo(x+11,y+25);path.lineTo(x+3,y+25);path.close();c.drawPath(path,p);
                    c.drawArc(x+18,y+7,x+36,y+33,-65,130,false,p);break;
                case 3: // Display
                    c.drawRoundRect(x+2,y+4,x+36,y+30,3,3,p);c.drawLine(x+19,y+30,x+19,y+36,p);c.drawLine(x+10,y+36,x+28,y+36,p);break;
                case 4: // Storage
                    c.drawRoundRect(x+4,y+6,x+34,y+34,3,3,p);c.drawLine(x+9,y+14,x+29,y+14,p);
                    c.drawLine(x+9,y+23,x+29,y+23,p);c.drawCircle(x+28,y+29,1,p);break;
                case 5: // Battery
                    c.drawRoundRect(x+3,y+10,x+32,y+30,3,3,p);c.drawLine(x+34,y+17,x+34,y+23,p);
                    path.reset();path.moveTo(x+20,y+12);path.lineTo(x+14,y+22);path.lineTo(x+21,y+22);path.lineTo(x+17,y+29);c.drawPath(path,p);break;
                case 6: // App settings
                    c.drawCircle(x+19,y+19,13,p);c.drawCircle(x+19,y+19,5,p);
                    for(int n=0;n<8;n++) {double a=n*Math.PI/4;c.drawLine(x+19+(float)Math.cos(a)*14,y+19+(float)Math.sin(a)*14,x+19+(float)Math.cos(a)*18,y+19+(float)Math.sin(a)*18,p);}break;
                case 7: // Date and time
                    c.drawCircle(x+19,y+20,15,p);c.drawLine(x+19,y+20,x+19,y+10,p);c.drawLine(x+19,y+20,x+27,y+25,p);break;
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
            if(page==PHOTO) openHome(selected==0?0:selected==1?1:8);
            else if(page==HOME) openHome(selected);
            else if(page==TOOLS) openTool(selected);
            else openApp(selected);
        }

        @Override public boolean onKeyDown(int code,KeyEvent event) {
            if(code==KeyEvent.KEYCODE_F1) {if(event.getRepeatCount()==0) openMail();return true;}
            if(code==KeyEvent.KEYCODE_F2) {if(event.getRepeatCount()==0) openBrowser();return true;}
            if(code==KeyEvent.KEYCODE_F3) {page=ALL;selected=0;refreshApps();invalidate();return true;}
            if(code==KeyEvent.KEYCODE_F4 || code==KeyEvent.KEYCODE_CAMERA) {if(event.getRepeatCount()==0) openCamera();return true;}
            if(code==KeyEvent.KEYCODE_CALL) {if(event.getRepeatCount()==0) openPhone();return true;}
            if(page==PHOTO) {
                if(code==KeyEvent.KEYCODE_DPAD_LEFT) {selected=Math.max(0,selected-1);invalidate();return true;}
                if(code==KeyEvent.KEYCODE_DPAD_RIGHT) {selected=Math.min(2,selected+1);invalidate();return true;}
                if(code==KeyEvent.KEYCODE_DPAD_UP) {page=HOME;selected=0;invalidate();return true;}
                if(code==KeyEvent.KEYCODE_DPAD_DOWN) {if(event.getRepeatCount()==0) openContacts();return true;}
                if(code>=KeyEvent.KEYCODE_1 && code<=KeyEvent.KEYCODE_9) {
                    if(event.getRepeatCount()==0) openHome(code-KeyEvent.KEYCODE_1);return true;
                }
            }
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
            if(code==KeyEvent.KEYCODE_STAR) {page=page==HOME?PHOTO:HOME;selected=0;invalidate();return true;}
            if(code==KeyEvent.KEYCODE_BACK) {page=PHOTO;selected=0;invalidate();return true;}
            return super.onKeyDown(code,event);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX()*360/getWidth(),y=e.getY()*560/getHeight();
            if(page==PHOTO) {
                if(y>=463 && y<=521) {int index=(int)((x-17)/112);if(index>=0 && index<3) {selected=index;activate();}}
                else if(y>521) {page=x<160?HOME:ALL;selected=0;if(page==ALL) refreshApps();invalidate();}
                else {page=HOME;selected=0;invalidate();}
                return true;
            }
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
