package dev.codex.flipdeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.ArrayList;

public final class VoiceActivity extends Activity {
    private static final int DICTATE=201, BG=Color.rgb(12,25,34), MINT=Color.rgb(101,226,192);
    private SharedPreferences prefs;
    private EditText text;
    private TextView status;
    private Button language,speak;
    private boolean english;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("flipdeck_voice",MODE_PRIVATE);english=prefs.getBoolean("english",false);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(BG);
        TextView title=label("VOICE TYPE",22);title.setTextColor(MINT);root.addView(title);
        language=button("",new Runnable(){public void run(){chooseLanguage();}});root.addView(language);language.setText(english?"English / EN":"Thai / TH");
        status=label("",13);root.addView(status);
        text=new EditText(this);text.setTextColor(Color.WHITE);text.setHintTextColor(Color.LTGRAY);text.setHint("Your words appear here");text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,17);
        text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);text.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);text.setGravity(48);text.setText(prefs.getString("text",""));root.addView(text,new LinearLayout.LayoutParams(-1,0,1));
        speak=button("Speak / F3",new Runnable(){public void run(){dictate();}});root.addView(speak);
        LinearLayout row=new LinearLayout(this);row.addView(button("Copy",new Runnable(){public void run(){copy();}}),new LinearLayout.LayoutParams(0,dp(44),1));row.addView(button("Share",new Runnable(){public void run(){share();}}),new LinearLayout.LayoutParams(0,dp(44),1));root.addView(row);
        root.addView(label("Menu: language / clear   Back: save & return",11));setContentView(root);speak.requestFocus();updateStatus();
    }
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,size);v.setPadding(0,dp(4),0,dp(4));return v;}
    private Button button(String s,final Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,14);b.setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){action.run();}});return b;}
    private Intent request(){Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,english?"en-US":"th-TH");i.putExtra(RecognizerIntent.EXTRA_PROMPT,english?"Speak English":"พูดภาษาไทย");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);return i;}
    private void updateStatus(){status.setText(request().resolveActivity(getPackageManager())!=null?"Choose language, then F3. Review before using.":"Thai / English speech engine needs setup first.");}
    private void chooseLanguage(){new AlertDialog.Builder(this).setTitle("Language").setSingleChoiceItems(new String[]{"ไทย (th-TH)","English (en-US)"},english?1:0,new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){english=which==1;prefs.edit().putBoolean("english",english).apply();language.setText(english?"English / EN":"Thai / TH");d.dismiss();}}).show();}
    private void dictate(){save();if(request().resolveActivity(getPackageManager())==null){new AlertDialog.Builder(this).setTitle("Speech engine required").setMessage("This phone has no Thai / English speech recognizer. A compatible engine, computer or online transcription connection must be set up first. Your draft stays saved.").setPositiveButton("OK",null).show();return;}try{startActivityForResult(request(),DICTATE);}catch(Exception e){status.setText("Speech unavailable. Check engine and network.");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=DICTATE)return;if(result!=RESULT_OK||data==null){status.setText("Cancelled. Draft unchanged.");return;}final ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(results==null||results.isEmpty()){status.setText("No words detected. Try again.");return;}if(results.size()==1){append(results.get(0));return;}new AlertDialog.Builder(this).setTitle("Choose your words").setItems(results.toArray(new String[results.size()]),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){append(results.get(which));}}).setNegativeButton("Cancel",null).show();}
    private void append(String s){if(s==null||s.trim().length()==0)return;text.append((text.length()==0?"":" ")+s.trim());save();status.setText("Saved. Review, copy or share.");speak.requestFocus();}
    private void save(){if(text!=null)prefs.edit().putString("text",text.getText().toString()).apply();}
    private void copy(){if(text.length()==0)return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Voice text",text.getText().toString()));Toast.makeText(this,"Copied. Paste into your message.",0).show();}
    private void share(){if(text.length()==0)return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text.getText().toString());try{startActivity(Intent.createChooser(i,"Use voice text"));}catch(Exception e){Toast.makeText(this,"Copy and paste instead",0).show();}}
    private void menu(){new AlertDialog.Builder(this).setItems(new String[]{"Language","Clear draft"},new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){if(which==0)chooseLanguage();else new AlertDialog.Builder(VoiceActivity.this).setTitle("Clear voice draft?").setPositiveButton("Clear",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){text.setText("");save();}}).setNegativeButton("Cancel",null).show();}}).show();}
    @Override protected void onPause(){save();super.onPause();}
    @Override public boolean onKeyDown(int code,KeyEvent e){if(code==KeyEvent.KEYCODE_F3){if(e.getRepeatCount()==0)dictate();return true;}if(code==KeyEvent.KEYCODE_MENU){if(e.getRepeatCount()==0)menu();return true;}return super.onKeyDown(code,e);}
}
