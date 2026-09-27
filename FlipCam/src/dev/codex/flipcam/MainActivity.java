package dev.codex.flipcam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int BG=Color.rgb(10,24,32);
    private static final int MINT=Color.rgb(101,226,192);
    private final Handler handler=new Handler();
    private SurfaceView preview;
    private GridView grid;
    private TextView status;
    private Button photoButton,videoButton,flashButton,timerButton,gridButton,shutterButton,autoButton;
    private Camera camera;
    private MediaRecorder recorder;
    private File recording;
    private boolean surfaceReady,resumed,recordingVideo,takingPicture,flash,showGrid=true;
    private int mode=0,timer=0;
    private boolean manual;
    private String iso="auto",whiteBalance="auto",focus="auto",scene="auto",pictureSize="";
    private int ev=0,jpegQuality=90,contrast=5,saturation=5,sharpness=12,videoQuality=CamcorderProfile.QUALITY_480P;
    private long recordingStart;
    private final Runnable pendingPhoto=new Runnable(){@Override public void run(){takePhoto();}};
    private final Runnable timerTick=new Runnable() {
        @Override public void run() {
            if(recordingVideo) {
                long seconds=(System.currentTimeMillis()-recordingStart)/1000;
                status.setText(String.format(Locale.US,"REC %02d:%02d",seconds/60,seconds%60));
                handler.postDelayed(this,500);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loadPreferences();
        buildUi();
    }

    private void loadPreferences() {
        SharedPreferences pref=getPreferences(0);
        manual=pref.getBoolean("manual",false);
        iso=pref.getString("iso","auto");
        whiteBalance=pref.getString("whiteBalance","auto");
        focus=pref.getString("focus","auto");
        scene=pref.getString("scene","auto");
        pictureSize=pref.getString("pictureSize","");
        ev=pref.getInt("ev",0);
        jpegQuality=pref.getInt("jpegQuality",90);
        contrast=pref.getInt("contrast",5);
        saturation=pref.getInt("saturation",5);
        sharpness=pref.getInt("sharpness",12);
        videoQuality=pref.getInt("videoQuality",CamcorderProfile.QUALITY_480P);
        showGrid=pref.getBoolean("grid",true);
    }

    private void savePreferences() {
        getPreferences(0).edit().putBoolean("manual",manual).putString("iso",iso)
            .putString("whiteBalance",whiteBalance).putString("focus",focus)
            .putString("scene",scene).putString("pictureSize",pictureSize)
            .putInt("ev",ev).putInt("jpegQuality",jpegQuality)
            .putInt("contrast",contrast).putInt("saturation",saturation)
            .putInt("sharpness",sharpness).putInt("videoQuality",videoQuality)
            .putBoolean("grid",showGrid).apply();
    }

    private Button button(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(30,60,72)));
        b.setPadding(1,0,1,0);
        return b;
    }

    private void addButton(LinearLayout row,Button b) {
        row.addView(b,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        TextView title=new TextView(this);
        title.setText("FLIP / CAM");
        title.setTextColor(MINT);title.setTextSize(15);title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(16,0,8,0);
        root.addView(title,new LinearLayout.LayoutParams(-1,42));

        FrameLayout previewFrame=new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        preview=new SurfaceView(this);
        preview.getHolder().addCallback(this);
        previewFrame.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        grid=new GridView();
        previewFrame.addView(grid,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        root.addView(previewFrame,new LinearLayout.LayoutParams(-1,0,1));

        status=new TextView(this);
        status.setText("PHOTO  •  OK to shoot");
        status.setTextColor(Color.WHITE);status.setTextSize(15);status.setGravity(Gravity.CENTER);
        root.addView(status,new LinearLayout.LayoutParams(-1,35));

        LinearLayout proRow=new LinearLayout(this);
        autoButton=button("4 AUTO");Button settingsButton=button("5 CAMERA SETTINGS");
        addButton(proRow,autoButton);addButton(proRow,settingsButton);
        root.addView(proRow,new LinearLayout.LayoutParams(-1,47));
        autoButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){toggleManual();}});
        settingsButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showSettings();}});

        LinearLayout modes=new LinearLayout(this);
        photoButton=button("1 PHOTO");videoButton=button("2 VIDEO");Button qrButton=button("3 QR SCAN");
        addButton(modes,photoButton);addButton(modes,videoButton);addButton(modes,qrButton);
        root.addView(modes,new LinearLayout.LayoutParams(-1,55));
        photoButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){setMode(0);}});
        videoButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){setMode(1);}});
        qrButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){openQr();}});

        LinearLayout controls=new LinearLayout(this);
        flashButton=button("0 OFF");timerButton=button("* 0s");gridButton=button("GRID ON");Button gallery=button("# FILES");
        addButton(controls,flashButton);addButton(controls,timerButton);addButton(controls,gridButton);addButton(controls,gallery);
        root.addView(controls,new LinearLayout.LayoutParams(-1,54));
        flashButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){toggleFlash();}});
        timerButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){cycleTimer();}});
        gridButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showGrid=!showGrid;grid.invalidate();gridButton.setText(showGrid?"GRID ON":"GRID OFF");savePreferences();}});
        gallery.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){openGallery();}});

        shutterButton=button("●  TAKE PHOTO");
        shutterButton.setTextSize(19);
        shutterButton.setTextColor(BG);
        shutterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT));
        root.addView(shutterButton,new LinearLayout.LayoutParams(-1,70));
        shutterButton.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){shutter();}});
        setContentView(root);
        updateModeUi();
    }

    private final class GridView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        GridView(){super(MainActivity.this);setWillNotDraw(false);}
        @Override protected void onDraw(Canvas c) {
            if(!showGrid) return;
            p.setColor(Color.argb(115,255,255,255));p.setStrokeWidth(1);
            for(int n=1;n<=2;n++) {
                float x=getWidth()*n/3f,y=getHeight()*n/3f;
                c.drawLine(x,0,x,getHeight(),p);
                c.drawLine(0,y,getWidth(),y,p);
            }
        }
    }

    @Override protected void onResume(){super.onResume();resumed=true;openCamera();}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(pendingPhoto);takingPicture=false;if(recordingVideo) stopVideo();releaseCamera();super.onPause();}
    @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;openCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;releaseCamera();}

    private Camera.Size bestSize(List<Camera.Size> sizes,int wantedW,int wantedH) {
        Camera.Size best=null;double score=Double.MAX_VALUE;
        if(sizes==null) return null;
        for(Camera.Size s:sizes) {
            double current=Math.abs((double)s.width/s.height-(double)wantedW/wantedH)*1000
                    +Math.abs(s.width-wantedW)/5.0+Math.abs(s.height-wantedH)/5.0;
            if(current<score) {score=current;best=s;}
        }
        return best;
    }

    private void openCamera() {
        if(!resumed || !surfaceReady || camera!=null || recordingVideo) return;
        try {
            camera=Camera.open(0);
            Camera.Parameters p=camera.getParameters();
            int videoWidth=mode==1 && videoQuality==CamcorderProfile.QUALITY_720P?1280:mode==1?720:640;
            int videoHeight=mode==1 && videoQuality==CamcorderProfile.QUALITY_720P?720:480;
            Camera.Size size=bestSize(p.getSupportedPreviewSizes(),videoWidth,videoHeight);
            if(size!=null) p.setPreviewSize(size.width,size.height);
            Camera.Size picture=null;
            for(Camera.Size s:p.getSupportedPictureSizes()) {
                if(s.width*s.height<=12000000 && (picture==null || s.width*s.height>picture.width*picture.height)) picture=s;
            }
            if(!pictureSize.isEmpty()) for(Camera.Size s:p.getSupportedPictureSizes())
                if(pictureSize.equals(s.width+"x"+s.height)) {picture=s;break;}
            if(picture!=null) p.setPictureSize(picture.width,picture.height);
            List<String> focus=p.getSupportedFocusModes();
            String continuous=mode==1?Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO:Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            if(focus!=null && focus.contains(continuous)) p.setFocusMode(continuous);
            else if(focus!=null && focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            p.setRecordingHint(mode==1);
            List<String> flashes=p.getSupportedFlashModes();
            if(flashes!=null) {
                String wanted=flash?(mode==1?Camera.Parameters.FLASH_MODE_TORCH:Camera.Parameters.FLASH_MODE_ON):Camera.Parameters.FLASH_MODE_OFF;
                if(flashes.contains(wanted)) p.setFlashMode(wanted);
            }
            camera.setParameters(p);
            applySettings();
            flashButton.setEnabled(flashes!=null && flashes.size()>1);
            flashButton.setText(flashes==null?"NO FLASH":flash?"0 ON":"0 OFF");
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
            refreshStatus();
        } catch(Exception e) {
            releaseCamera();
            status.setText("Camera unavailable");
            Toast.makeText(this,"Camera unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void releaseCamera() {
        if(camera!=null) {
            try {camera.setPreviewCallback(null);camera.stopPreview();camera.release();} catch(Exception ignored) {}
            camera=null;
        }
    }

    private void setMode(int target) {
        if(recordingVideo) stopVideo();
        if(mode==target) return;
        handler.removeCallbacks(pendingPhoto);
        mode=target;takingPicture=false;
        releaseCamera();updateModeUi();openCamera();
    }

    private void updateModeUi() {
        photoButton.setTextColor(mode==0?MINT:Color.WHITE);
        videoButton.setTextColor(mode==1?MINT:Color.WHITE);
        autoButton.setText(manual?"4 MANUAL":"4 AUTO");
        autoButton.setTextColor(MINT);
        shutterButton.setText(mode==0?"●  TAKE PHOTO":"●  START VIDEO");
        timerButton.setEnabled(mode==0);
        timerButton.setText("* "+timer+"s");
        refreshStatus();
    }

    private void refreshStatus() {
        if(status==null || recordingVideo) return;
        String text=mode==0?"PHOTO":"VIDEO";
        text+=manual?"  /  MANUAL  /  "+iso:"  /  AUTO  /  "+scene.toUpperCase(Locale.US);
        status.setText(text);
    }

    private interface ParameterEdit {void change(Camera.Parameters p);}

    private void safeParameter(String name,ParameterEdit edit) {
        if(camera==null) return;
        try {
            Camera.Parameters p=camera.getParameters();
            edit.change(p);
            camera.setParameters(p);
        } catch(RuntimeException e) {android.util.Log.w("FlipCam","Unsupported "+name,e);}
    }

    private void applySettings() {
        if(camera==null) return;
        safeParameter("scene",new ParameterEdit(){@Override public void change(Camera.Parameters p){
            String wanted=manual?"auto":scene;
            if(p.getSupportedSceneModes()!=null && p.getSupportedSceneModes().contains(wanted)) p.setSceneMode(wanted);
        }});
        safeParameter("ISO",new ParameterEdit(){@Override public void change(Camera.Parameters p){
            String wanted=manual?iso:"auto";
            String supported=p.get("iso-values");
            if(supported!=null && (","+supported+",").contains(","+wanted+",")) p.set("iso",wanted);
        }});
        safeParameter("white balance",new ParameterEdit(){@Override public void change(Camera.Parameters p){
            String wanted=manual?whiteBalance:"auto";
            if(p.getSupportedWhiteBalance()!=null && p.getSupportedWhiteBalance().contains(wanted)) p.setWhiteBalance(wanted);
        }});
        safeParameter("focus",new ParameterEdit(){@Override public void change(Camera.Parameters p){
            String wanted=manual && !focus.equals("auto")?focus:
                mode==1?Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO:Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            if(p.getSupportedFocusModes()!=null && p.getSupportedFocusModes().contains(wanted)) p.setFocusMode(wanted);
        }});
        safeParameter("exposure",new ParameterEdit(){@Override public void change(Camera.Parameters p){
            int wanted=manual?ev:0;
            if(wanted>=p.getMinExposureCompensation() && wanted<=p.getMaxExposureCompensation()) p.setExposureCompensation(wanted);
        }});
        safeParameter("JPEG quality",new ParameterEdit(){@Override public void change(Camera.Parameters p){p.setJpegQuality(jpegQuality);}});
        safeParameter("contrast",new ParameterEdit(){@Override public void change(Camera.Parameters p){if(p.get("contrast")!=null) p.set("contrast",manual?contrast:5);}});
        safeParameter("saturation",new ParameterEdit(){@Override public void change(Camera.Parameters p){if(p.get("saturation")!=null) p.set("saturation",manual?saturation:5);}});
        safeParameter("sharpness",new ParameterEdit(){@Override public void change(Camera.Parameters p){if(p.get("sharpness")!=null) p.set("sharpness",manual?sharpness:12);}});
        Camera.Parameters checked=camera.getParameters();
        android.util.Log.i("FlipCamSetting","iso="+checked.get("iso")+" ev="+checked.getExposureCompensation()+" wb="+checked.getWhiteBalance()+" focus="+checked.getFocusMode()+" scene="+checked.getSceneMode()+" contrast="+checked.get("contrast"));
    }

    private void toggleManual() {
        if(recordingVideo) return;
        manual=!manual;
        savePreferences();
        applySettings();
        updateModeUi();
    }

    private interface Selection {void choose(String value);}

    private void choices(String title,final ArrayList<String> labels,final ArrayList<String> values,final Selection selection) {
        if(labels.isEmpty()) {Toast.makeText(this,"No options available",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle(title).setItems(labels.toArray(new String[labels.size()]),
            new android.content.DialogInterface.OnClickListener(){@Override public void onClick(android.content.DialogInterface d,int which){
                selection.choose(values.get(which));savePreferences();applySettings();refreshStatus();
            }}).setNegativeButton("Cancel",null).show();
    }

    private String titleCase(String raw) {
        String text=raw.replace('-', ' ');
        return text.length()==0?text:Character.toUpperCase(text.charAt(0))+text.substring(1);
    }

    private void pickIso(Camera.Parameters p) {
        String supported=p.get("iso-values");
        ArrayList<String> labels=new ArrayList<String>(), values=new ArrayList<String>();
        if(supported!=null) for(String option:supported.split(",")) {
            if(option.equals("auto") || option.matches("ISO[0-9]+")) {
                values.add(option);
                labels.add(option.equals("auto")?"Auto":option.replace("ISO","ISO "));
            }
        }
        choices("ISO",labels,values,new Selection(){@Override public void choose(String value){iso=value;}});
    }

    private void pickEv(Camera.Parameters p) {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        for(int i=p.getMinExposureCompensation();i<=p.getMaxExposureCompensation();i++) {
            double value=i*p.getExposureCompensationStep();
            labels.add(String.format(Locale.US,"EV %+.2f",value));values.add(String.valueOf(i));
        }
        choices("Exposure compensation",labels,values,new Selection(){@Override public void choose(String value){ev=Integer.parseInt(value);}});
    }

    private void pickWhiteBalance(Camera.Parameters p) {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        if(p.getSupportedWhiteBalance()!=null) for(String option:p.getSupportedWhiteBalance()) {
            if(option.equals("manual")) continue;
            values.add(option);labels.add(titleCase(option));
        }
        choices("White balance",labels,values,new Selection(){@Override public void choose(String value){whiteBalance=value;}});
    }

    private void pickFocus(Camera.Parameters p) {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        values.add("auto");labels.add("Auto focus");
        if(p.getSupportedFocusModes()!=null) {
            if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_MACRO)) {values.add("macro");labels.add("Macro / close up");}
            if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {values.add("infinity");labels.add("Infinity / distance");}
        }
        choices("Focus",labels,values,new Selection(){@Override public void choose(String value){focus=value;}});
    }

    private void pickScene(Camera.Parameters p) {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        String[] wanted={"auto","night","portrait","landscape","sports","hdr","food","sunset","night-portrait"};
        for(String option:wanted) if(p.getSupportedSceneModes()!=null && p.getSupportedSceneModes().contains(option)) {
            values.add(option);labels.add(titleCase(option));
        }
        choices("Auto scene",labels,values,new Selection(){@Override public void choose(String value){scene=value;}});
    }

    private void pickResolution(Camera.Parameters p) {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        for(Camera.Size size:p.getSupportedPictureSizes()) {
            String value=size.width+"x"+size.height;
            values.add(value);
            labels.add(value+String.format(Locale.US,"  (%.1f MP)",size.width*size.height/1000000.0));
        }
        choices("Photo resolution",labels,values,new Selection(){@Override public void choose(String value){
            pictureSize=value;releaseCamera();openCamera();
        }});
    }

    private void pickJpegQuality() {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        for(int quality:new int[]{70,80,90,95,100}) {labels.add(quality+"% "+(quality==100?"Maximum":""));values.add(String.valueOf(quality));}
        choices("JPEG quality",labels,values,new Selection(){@Override public void choose(String value){jpegQuality=Integer.parseInt(value);}});
    }

    private void pickVideoQuality() {
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_720P)) {labels.add("720p HD");values.add(String.valueOf(CamcorderProfile.QUALITY_720P));}
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_480P)) {labels.add("480p");values.add(String.valueOf(CamcorderProfile.QUALITY_480P));}
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_LOW)) {labels.add("Low / smaller file");values.add(String.valueOf(CamcorderProfile.QUALITY_LOW));}
        choices("Video resolution",labels,values,new Selection(){@Override public void choose(String value){
            videoQuality=Integer.parseInt(value);
            if(mode==1) {releaseCamera();openCamera();}
        }});
    }

    private void pickTone(final String key,Camera.Parameters p) {
        String maxText=p.get("max-"+key),minText=p.get("min-"+key),stepText=p.get(key+"-step");
        if(maxText==null || minText==null) {Toast.makeText(this,"Unavailable",Toast.LENGTH_SHORT).show();return;}
        int min,max,step;
        try {min=Integer.parseInt(minText);max=Integer.parseInt(maxText);step=stepText==null?1:Math.max(1,Integer.parseInt(stepText));}
        catch(NumberFormatException e) {return;}
        ArrayList<String> labels=new ArrayList<String>(),values=new ArrayList<String>();
        for(int value=min;value<=max;value+=step) {labels.add(String.valueOf(value));values.add(String.valueOf(value));}
        choices(titleCase(key),labels,values,new Selection(){@Override public void choose(String value){
            int selected=Integer.parseInt(value);
            if(key.equals("contrast")) contrast=selected;
            else if(key.equals("saturation")) saturation=selected;
            else sharpness=selected;
        }});
    }

    private void showSettings() {
        if(recordingVideo || camera==null) return;
        final Camera.Parameters p=camera.getParameters();
        final String[] items=manual?
            new String[]{"ISO  •  "+iso,"Exposure  •  "+String.format(Locale.US,"%+.2f EV",ev*p.getExposureCompensationStep()),
                "White balance  •  "+titleCase(whiteBalance),"Focus  •  "+titleCase(focus),
                "Photo resolution","JPEG quality  •  "+jpegQuality+"%","Video resolution",
                "Contrast  •  "+contrast,"Saturation  •  "+saturation,"Sharpness  •  "+sharpness}:
            new String[]{"Scene  •  "+titleCase(scene),"Photo resolution","JPEG quality  •  "+jpegQuality+"%","Video resolution"};
        new AlertDialog.Builder(this).setTitle(manual?"MANUAL SETTINGS":"AUTO SETTINGS")
            .setItems(items,new android.content.DialogInterface.OnClickListener(){@Override public void onClick(android.content.DialogInterface d,int which){
                if(manual) switch(which) {
                    case 0:pickIso(p);break;case 1:pickEv(p);break;case 2:pickWhiteBalance(p);break;
                    case 3:pickFocus(p);break;case 4:pickResolution(p);break;case 5:pickJpegQuality();break;
                    case 6:pickVideoQuality();break;case 7:pickTone("contrast",p);break;
                    case 8:pickTone("saturation",p);break;case 9:pickTone("sharpness",p);break;
                } else switch(which) {
                    case 0:pickScene(p);break;case 1:pickResolution(p);break;
                    case 2:pickJpegQuality();break;case 3:pickVideoQuality();break;
                }
            }}).setNegativeButton("Close",null).show();
    }

    private void toggleFlash() {
        if(recordingVideo) return;
        flash=!flash;
        flashButton.setText(flash?"0 ON":"0 OFF");
        if(camera==null) return;
        try {
            Camera.Parameters p=camera.getParameters();
            List<String> modes=p.getSupportedFlashModes();
            String wanted=flash?(mode==1?Camera.Parameters.FLASH_MODE_TORCH:Camera.Parameters.FLASH_MODE_ON):Camera.Parameters.FLASH_MODE_OFF;
            if(modes==null || !modes.contains(wanted)) {flash=false;flashButton.setText("NO FLASH");return;}
            p.setFlashMode(wanted);camera.setParameters(p);
        } catch(Exception e) {flash=false;flashButton.setText("NO FLASH");}
    }

    private void cycleTimer() {
        if(mode!=0) return;
        timer=timer==0?3:timer==3?10:0;
        timerButton.setText("* "+timer+"s");
    }

    private void zoom(int direction) {
        if(camera==null || recordingVideo) return;
        try {
            Camera.Parameters p=camera.getParameters();
            if(!p.isZoomSupported()) {Toast.makeText(this,"Zoom unavailable",Toast.LENGTH_SHORT).show();return;}
            int value=Math.max(0,Math.min(p.getMaxZoom(),p.getZoom()+direction));
            p.setZoom(value);camera.setParameters(p);
            status.setText("ZOOM "+value+"/"+p.getMaxZoom());
        } catch(Exception ignored) {}
    }

    private void shutter() {
        if(mode==1) {if(recordingVideo) stopVideo();else startVideo();return;}
        if(camera==null || takingPicture) return;
        takingPicture=true;
        if(timer>0) {
            status.setText("PHOTO IN "+timer+"...");
            handler.postDelayed(pendingPhoto,timer*1000L);
        } else takePhoto();
    }

    private File newFile(String extension) {
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),"FlipCam");
        if(!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Storage unavailable");
        String name="FC_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+extension;
        return new File(dir,name);
    }

    private void scan(File file,String mime) {
        MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{mime},null);
    }

    private void takePhoto() {
        if(camera==null || !resumed) {takingPicture=false;return;}
        try {
            camera.takePicture(null,null,new Camera.PictureCallback() {
                @Override public void onPictureTaken(byte[] jpeg,Camera source) {
                    try {
                        File file=newFile(".jpg");
                        FileOutputStream out=new FileOutputStream(file);
                        try {out.write(jpeg);} finally {out.close();}
                        scan(file,"image/jpeg");
                        status.setText("SAVED  "+file.getName());
                    } catch(Exception e) {status.setText("Photo save failed");}
                    takingPicture=false;
                    try {source.startPreview();} catch(Exception e) {releaseCamera();openCamera();}
                }
            });
        } catch(Exception e) {takingPicture=false;status.setText("Photo failed");}
    }

    private void startVideo() {
        if(camera==null || recordingVideo) return;
        try {
            recording=newFile(".mp4");
            camera.unlock();
            recorder=new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            int quality=CamcorderProfile.hasProfile(0,videoQuality)?videoQuality:CamcorderProfile.QUALITY_LOW;
            recorder.setProfile(CamcorderProfile.get(0,quality));
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.setOrientationHint(90);
            recorder.prepare();recorder.start();
            recordingVideo=true;recordingStart=System.currentTimeMillis();
            shutterButton.setText("■  STOP VIDEO");
            handler.post(timerTick);
        } catch(Exception e) {
            status.setText("Video failed: "+e.getMessage());
            if(recorder!=null) {try {recorder.reset();recorder.release();} catch(Exception ignored) {} recorder=null;}
            if(recording!=null) recording.delete();
            releaseCamera();openCamera();
        }
    }

    private void stopVideo() {
        if(!recordingVideo) return;
        recordingVideo=false;handler.removeCallbacks(timerTick);
        boolean saved=false;
        try {recorder.stop();saved=true;} catch(Exception ignored) {}
        try {recorder.reset();recorder.release();} catch(Exception ignored) {}
        recorder=null;
        if(saved && recording!=null) {scan(recording,"video/mp4");status.setText("SAVED  "+recording.getName());}
        else {if(recording!=null) recording.delete();status.setText("Video was too short or failed");}
        recording=null;shutterButton.setText("●  START VIDEO");
        releaseCamera();openCamera();
    }

    private void openQr() {
        if(recordingVideo) stopVideo();
        releaseCamera();
        try {
            Intent scan=new Intent(Intent.ACTION_MAIN);
            scan.setClassName("jp.co.sharp.android.readercamera","jp.co.sharp.android.readercamera.barcodereader.BarcodeReader");
            startActivity(scan);
        } catch(Exception e) {
            Toast.makeText(this,"QR scanner unavailable",Toast.LENGTH_LONG).show();
            openCamera();
        }
    }

    private void openGallery() {
        try {
            Intent gallery=new Intent(Intent.ACTION_VIEW,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivity(gallery);
        } catch(Exception e) {Toast.makeText(this,"Gallery unavailable",Toast.LENGTH_SHORT).show();}
    }

    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_1) {setMode(0);return true;}
        if(code==KeyEvent.KEYCODE_2) {setMode(1);return true;}
        if(code==KeyEvent.KEYCODE_3) {openQr();return true;}
        if(code==KeyEvent.KEYCODE_4) {toggleManual();return true;}
        if(code==KeyEvent.KEYCODE_5) {showSettings();return true;}
        if(code==KeyEvent.KEYCODE_0) {toggleFlash();return true;}
        if(code==KeyEvent.KEYCODE_STAR) {cycleTimer();return true;}
        if(code==KeyEvent.KEYCODE_POUND) {openGallery();return true;}
        if(code==KeyEvent.KEYCODE_DPAD_UP) {zoom(1);return true;}
        if(code==KeyEvent.KEYCODE_DPAD_DOWN) {zoom(-1);return true;}
        if(code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_CAMERA || code==KeyEvent.KEYCODE_F4 || code==KeyEvent.KEYCODE_VOLUME_UP) {
            if(event.getRepeatCount()==0) shutter();return true;
        }
        return super.onKeyDown(code,event);
    }

    @Override public void onBackPressed() {
        if(recordingVideo) stopVideo();
        else super.onBackPressed();
    }
}
