package dev.codex.flipbrowse;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** One WebView, no background service, no third party library. */
public final class MainActivity extends Activity {
    private WebView web;
    private Button address;
    private TextView status;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private JSONArray bookmarks;
    private boolean home=true, failed=false, sslFailed=false, loading=false, clearHistoryPending=false;
    private String currentUrl="";
    private static final int DARK=Color.rgb(16,34,46), MINT=Color.rgb(104,224,193);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("browser",MODE_PRIVATE);
        try {bookmarks=new JSONArray(prefs.getString("bookmarks","[]"));}
        catch(Exception e) {bookmarks=new JSONArray();}
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(DARK);
        LinearLayout bar=new LinearLayout(this);
        address=new Button(this);address.setText("Address / Search");address.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14);
        address.setSingleLine(true);address.setAllCaps(false);
        address.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showAddress();}});
        bar.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        Button menu=new Button(this);menu.setText("Menu");menu.setAllCaps(false);menu.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14);
        menu.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showMenu();}});
        bar.addView(menu,new LinearLayout.LayoutParams(dp(72),dp(48)));
        root.addView(bar);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        web=new WebView(this);web.setBackgroundColor(DARK);web.setFocusable(true);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setTextSize(TypedValue.COMPLEX_UNIT_DIP,11);status.setTextColor(MINT);
        status.setPadding(dp(7),dp(3),dp(7),dp(3));status.setSingleLine(true);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        WebSettings settings=web.getSettings();
        settings.setDomStorageEnabled(true);settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        settings.setSupportZoom(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        applyPreferences();
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int value){
                progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.GONE);
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){
                if(url.startsWith("https://flipbrowse.invalid/")) {handleHomeLink(url);return true;}
                if(isWebUrl(url)) return false;
                openExternal(url);return true;
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap icon){
                failed=false;sslFailed=false;loading=true;
                home=url.startsWith("https://flipbrowse.invalid/");
                web.setBackgroundColor(home?DARK:Color.WHITE);
                if(!home) {currentUrl=url;address.setText(url);status.setText("Loading...");}
            }
            @Override public void onPageFinished(WebView v,String url){
                loading=false;progress.setVisibility(View.GONE);
                if(clearHistoryPending && home) {web.clearHistory();clearHistoryPending=false;}
                if(!home && !failed) status.setText("Globe: address  |  Menu: tools");
            }
            @Override public void onReceivedError(WebView v,int code,String description,String url){
                failed=true;loading=false;progress.setVisibility(View.GONE);
                status.setText(sslFailed?"Certificate rejected. Try another site.":"Cannot load page. Check Wi-Fi / try Reload.");
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){
                handler.cancel();sslFailed=true;failed=true;
                status.setText("Certificate rejected. Try another site.");
            }
        });
        web.setDownloadListener(new DownloadListener(){
            @Override public void onDownloadStart(String url,String agent,String disposition,String type,long size){
                confirmDownload(url,agent,disposition,type);
            }
        });
        String initial=getIntent().getDataString();
        if(initial!=null && isWebUrl(initial)) navigate(initial);
        else if(saved!=null && isWebUrl(saved.getString("url",""))) navigate(saved.getString("url"));
        else showHome();
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private boolean isWebUrl(String url){
        if(url==null) return false;
        Uri uri=Uri.parse(url);String scheme=uri.getScheme();
        return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) && uri.getHost()!=null;
    }
    private void applyPreferences(){
        web.getSettings().setJavaScriptEnabled(prefs.getBoolean("javascript",true));
        web.getSettings().setBlockNetworkImage(!prefs.getBoolean("images",true));
    }
    private void navigate(String input){
        String text=input.trim();if(text.length()==0) return;
        String url=text;
        if(!isWebUrl(url)) {
            if(text.indexOf(' ')<0 && text.indexOf('.')>=0 && text.indexOf(':')<0) url="https://"+text;
            else url="https://www.google.com/search?q="+Uri.encode(text);
        }
        currentUrl=url;home=false;web.setBackgroundColor(Color.WHITE);
        address.setText(url);web.loadUrl(url);web.requestFocus();
    }
    private void showHome(){
        web.stopLoading();home=true;failed=false;currentUrl="";web.setBackgroundColor(DARK);
        address.setText("Address / Search");status.setText("Globe: address  |  Menu: tools");
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>body{background:#10222e;color:#ecf7f5;font:16px sans-serif;margin:20px}"+
            "h1{font-size:30px;margin:24px 0 4px}p{color:#9cb4bd;font-size:13px;margin-bottom:25px}"+
            "a{display:block;background:#1c3745;color:#68e0c1;padding:14px;margin:9px 0;text-decoration:none;border-radius:8px}"+
            "a:focus{outline:3px solid #68e0c1;background:#294b59}</style></head><body>"+
            "<h1>Flip Browse</h1><p>Small phone. Open web.</p>"+
            "<a href='https://flipbrowse.invalid/search'>Search / enter address</a>"+
            "<a href='https://flipbrowse.invalid/bookmarks'>Bookmarks</a>"+
            "<a href='https://flipbrowse.invalid/settings'>Data saver settings</a>"+
            "<p>Use arrows + OK to select.<br>Back returns to the previous page.</p></body></html>";
        web.loadDataWithBaseURL("https://flipbrowse.invalid/",html,"text/html","UTF-8",null);
        web.requestFocus();
    }
    private void handleHomeLink(String url){
        String path=Uri.parse(url).getPath();
        if("/search".equals(path)) showAddress();
        else if("/bookmarks".equals(path)) showBookmarks();
        else if("/settings".equals(path)) showSettings();
    }
    private void showAddress(){
        final EditText input=new EditText(this);
        input.setSingleLine(true);input.setTextSize(TypedValue.COMPLEX_UNIT_DIP,18);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setSelectAllOnFocus(true);
        input.setText(currentUrl);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Address / Search")
            .setView(input).setPositiveButton("Go",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){navigate(input.getText().toString());}
            }).setNegativeButton("Cancel",null).create();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener(){
            public boolean onEditorAction(TextView v,int action,KeyEvent event){
                if(action==EditorInfo.IME_ACTION_GO || (event!=null && event.getAction()==KeyEvent.ACTION_UP &&
                    (event.getKeyCode()==KeyEvent.KEYCODE_ENTER || event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER))){
                    navigate(input.getText().toString());dialog.dismiss();return true;
                }return false;
            }
        });
        dialog.show();input.requestFocus();
    }
    private void showMenu(){
        final String[] items={"Address / Search","Back","Forward",loading?"Stop loading":"Reload","Home",
            "Bookmarks","Save bookmark","Zoom in","Zoom out","Data saver settings","Clear browsing data","Exit"};
        new AlertDialog.Builder(this).setTitle("Flip Browse").setItems(items,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int item){
                switch(item){
                    case 0:showAddress();break;
                    case 1:onBackPressed();break;
                    case 2:if(web.canGoForward()) web.goForward();else toast("No next page");break;
                    case 3:if(loading) {web.stopLoading();loading=false;progress.setVisibility(View.GONE);status.setText("Stopped");}
                        else if(home) showHome();else web.reload();break;
                    case 4:showHome();break;
                    case 5:showBookmarks();break;
                    case 6:saveBookmark();break;
                    case 7:web.zoomIn();break;
                    case 8:web.zoomOut();break;
                    case 9:showSettings();break;
                    case 10:clearData();break;
                    case 11:finish();break;
                }
            }
        }).show();
    }
    private void showSettings(){
        // Let the key that selected this item finish before opening another list.
        web.postDelayed(new Runnable(){public void run(){
        if(isFinishing()) return;
        final boolean images=prefs.getBoolean("images",true), javascript=prefs.getBoolean("javascript",true);
        new AlertDialog.Builder(MainActivity.this).setTitle("Data saver")
            .setItems(new String[]{"Images: "+(images?"ON":"OFF"),"JavaScript: "+(javascript?"ON":"OFF"),"Close"},
            new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){
                    if(which==2) return;
                    String key=which==0?"images":"javascript";
                    boolean enabled=!(which==0?images:javascript);
                    prefs.edit().putBoolean(key,enabled).apply();applyPreferences();
                    if(!home) web.reload();
                    toast((which==0?"Images":"JavaScript")+": "+(enabled?"ON":"OFF"));
                }
            }).show();
        }},180);
    }
    private void saveBookmark(){
        if(home || !isWebUrl(currentUrl)) {toast("Open a website first");return;}
        for(int i=0;i<bookmarks.length();i++) if(currentUrl.equals(bookmarks.optJSONObject(i).optString("url"))){toast("Already saved");return;}
        if(bookmarks.length()>=30) {toast("30 bookmarks saved. Remove one first.");return;}
        try {
            JSONObject item=new JSONObject();item.put("url",currentUrl);
            item.put("title",web.getTitle()==null?currentUrl:web.getTitle());bookmarks.put(item);storeBookmarks();toast("Bookmark saved");
        }catch(Exception e){toast("Could not save bookmark");}
    }
    private void storeBookmarks(){prefs.edit().putString("bookmarks",bookmarks.toString()).apply();}
    private void showBookmarks(){
        if(bookmarks.length()==0) {toast("No bookmarks yet. Menu > Save bookmark");return;}
        String[] names=new String[bookmarks.length()];
        for(int i=0;i<names.length;i++) names[i]=bookmarks.optJSONObject(i).optString("title");
        new AlertDialog.Builder(this).setTitle("Bookmarks").setItems(names,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,final int which){
                new AlertDialog.Builder(MainActivity.this).setTitle(bookmarks.optJSONObject(which).optString("title"))
                    .setItems(new String[]{"Open","Remove"},new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog,int action){
                            if(action==0) navigate(bookmarks.optJSONObject(which).optString("url"));
                            else {bookmarks.remove(which);storeBookmarks();toast("Bookmark removed");}
                        }
                    }).show();
            }
        }).show();
    }
    private void clearData(){
        new AlertDialog.Builder(this).setTitle("Clear browsing data?")
            .setMessage("Remove cookies, cache, history and site storage. Bookmarks stay saved.")
            .setPositiveButton("Clear",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){
                    web.stopLoading();web.clearCache(true);web.clearFormData();
                    CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();clearHistoryPending=true;showHome();
                    toast("Browsing data cleared");
                }
            }).setNegativeButton("Cancel",null).show();
    }
    private void openExternal(final String url){
        String scheme=Uri.parse(url).getScheme();
        if(!"tel".equals(scheme) && !"mailto".equals(scheme) && !"geo".equals(scheme)) {toast("Unsupported link");return;}
        new AlertDialog.Builder(this).setTitle("Open another app?").setMessage(url)
            .setPositiveButton("Open",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){
                    try {startActivity(new Intent(url.startsWith("tel:")?Intent.ACTION_DIAL:Intent.ACTION_VIEW,Uri.parse(url)));}
                    catch(Exception e){toast("No app for this link");}
                }
            }).setNegativeButton("Cancel",null).show();
    }
    private void confirmDownload(final String url,final String agent,String disposition,String type){
        if(!isWebUrl(url)) {toast("This download format is unsupported");return;}
        final String file=URLUtil.guessFileName(url,disposition,type).replaceAll("[\\\\/:*?\"<>|]","_");
        new AlertDialog.Builder(this).setTitle("Download file?").setMessage(file+"\nSaved in Downloads.")
            .setPositiveButton("Download",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){
                    try {
                        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
                        request.setTitle(file);request.addRequestHeader("User-Agent",agent);
                        String cookie=CookieManager.getInstance().getCookie(url);
                        if(cookie!=null) request.addRequestHeader("Cookie",cookie);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file);
                        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);toast("Download started");
                    }catch(Exception e){toast("Could not start download");}
                }
            }).setNegativeButton("Cancel",null).show();
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_F2 || code==KeyEvent.KEYCODE_MENU){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
                if(code==KeyEvent.KEYCODE_F2) showAddress();else showMenu();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(web.canGoBack()) web.goBack();else if(!home) showHome();else super.onBackPressed();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String url=intent.getDataString();if(isWebUrl(url)) navigate(url);}
    @Override public void onSaveInstanceState(Bundle state){if(!home) state.putString("url",currentUrl);super.onSaveInstanceState(state);}
    @Override protected void onPause(){web.onPause();web.pauseTimers();CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();web.resumeTimers();}}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
