package dev.codex.pocketmusic;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Set;

public final class MainActivity extends Activity {
    private TextView bluetoothStatus;
    private TextView headsetStatus;
    private static final int BG=Color.rgb(19,42,54);
    private static final int MINT=Color.rgb(101,214,181);
    private static final int WHITE=Color.rgb(245,250,248);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(16,16,16,16);
        page.setBackgroundColor(BG);
        TextView title=label("POCKET MUSIC",22,MINT,true);
        page.addView(title);
        page.addView(label("Music and headphones on your flip phone",12,WHITE,false));
        spacer(page,16);
        bluetoothStatus=label("",16,WHITE,true);
        headsetStatus=label("",14,WHITE,false);
        page.addView(bluetoothStatus);
        spacer(page,8);
        page.addView(headsetStatus);
        spacer(page,16);
        page.addView(button("1  Pair Bluetooth headphones",new View.OnClickListener() {
            @Override public void onClick(View v) { openBluetooth(); }
        }));
        spacer(page,8);
        page.addView(button("2  Open YouTube Music",new View.OnClickListener() {
            @Override public void onClick(View v) { openMusic(); }
        }));
        spacer(page,8);
        page.addView(button("3  Refresh status",new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        }));
        spacer(page,16);
        page.addView(label("Pair earbuds in Bluetooth Settings.\nConnect to the Internet before opening music.",11,WHITE,false));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
        refresh();
    }

    @Override protected void onResume() { super.onResume(); if(bluetoothStatus!=null) refresh(); }

    private TextView label(String value,int size,int color,boolean bold) {
        TextView text=new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        text.setLineSpacing(4,1); return text;
    }

    private void spacer(LinearLayout page,int dp) {
        View view=new View(this);
        int height=(int)(dp*getResources().getDisplayMetrics().density+.5f);
        page.addView(view,new LinearLayout.LayoutParams(1,height));
    }

    private Button button(String label,View.OnClickListener listener) {
        Button b=new Button(this);
        b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT));
        b.setOnClickListener(listener); return b;
    }

    private void refresh() {
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null) {
            bluetoothStatus.setText("Bluetooth: unavailable");
            headsetStatus.setText("No Bluetooth adapter found.");
            return;
        }
        bluetoothStatus.setText("Bluetooth: "+(adapter.isEnabled()?"ON":"OFF"));
        if(!adapter.isEnabled()) {
            headsetStatus.setText("Open Bluetooth Settings to turn it on.");
            return;
        }
        Set<BluetoothDevice> devices=adapter.getBondedDevices();
        StringBuilder names=new StringBuilder();
        for(BluetoothDevice device:devices) {
            String name=device.getName();
            if(name!=null && (name.toUpperCase().contains("SONY") || name.toUpperCase().contains("WF-"))) {
                if(names.length()>0) names.append(", ");
                names.append(name);
            }
        }
        headsetStatus.setText(names.length()>0 ? "Paired Sony: "+names.toString() : "No Sony headphones paired yet.");
    }

    private void openBluetooth() {
        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        catch(ActivityNotFoundException e) { Toast.makeText(this,"Bluetooth Settings unavailable",Toast.LENGTH_LONG).show(); }
    }

    private void openMusic() {
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/"));
        intent.setPackage("org.mozilla.firefox");
        try { startActivity(intent); }
        catch(ActivityNotFoundException e) {
            intent.setPackage(null);
            try { startActivity(intent); }
            catch(ActivityNotFoundException ignored) { Toast.makeText(this,"No browser installed",Toast.LENGTH_LONG).show(); }
        }
    }

    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(event.getRepeatCount()==0) {
            if(code==KeyEvent.KEYCODE_1) {openBluetooth();return true;}
            if(code==KeyEvent.KEYCODE_2) {openMusic();return true;}
            if(code==KeyEvent.KEYCODE_3) {refresh();return true;}
        }
        return super.onKeyDown(code,event);
    }
}
