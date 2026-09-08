package nz.co.cpc.bridgeauctionrecorder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_FILE = 1001;
    private static final int REQ_OPEN_JSON = 1002;
    private WebView webView;
    private String pendingFileName, pendingMimeType, pendingContent;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this); webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE); setContentView(webView);
        WebSettings settings=webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setDatabaseEnabled(true); settings.setAllowFileAccess(true); settings.setAllowContentAccess(true); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);installFastBiddingUi();}});
        webView.setWebChromeClient(new WebChromeClient()); webView.addJavascriptInterface(new AndroidFiles(),"AndroidFiles"); webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void installFastBiddingUi(){
        String js="(function(){"+
        "const style=document.createElement('style');style.textContent=`main{padding:6px 7px 16px}.card{padding:8px;margin-bottom:6px;border-radius:10px}.section-title{margin-bottom:4px}.auction .seat,.auction .bid{padding:5px}.bid{min-height:34px}.finalBidRow{margin-top:5px;gap:5px}.finalBidDisplay{min-height:36px;padding:7px 9px}.finalBidRow .undoBid{min-width:90px;min-height:36px}.bidgrid{display:block!important;margin-top:5px!important}.callrow{display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin-bottom:5px}.contractgrid{display:grid;grid-template-columns:repeat(5,1fr);gap:4px}.bidgrid button{min-height:45px!important;padding:5px 2px!important;font-size:18px;border-radius:7px}.callrow button{font-size:17px}.contractgrid .clubs,.contractgrid .spades{color:#111}.contractgrid .diamonds,.contractgrid .hearts{color:#d32626}.contractgrid .notrump{color:#17324d}.bidgrid button:disabled{background:#d9dde0;color:#92989d;border-color:#c8cdd1;opacity:1;cursor:default}.tools{margin-top:5px}`;document.head.appendChild(style);"+
        "window.legalBid=function(b){const d=data(),bs=d.bids,di=seats.indexOf(d.dealer),ci=(di+bs.length)%4,side=ci%2;if(b==='P')return true;if(b!=='X'&&b!=='XX'){const last=[...bs].reverse().find(x=>!['P','X','XX'].includes(x));if(!last)return true;const a=bidInfo(last),c=bidInfo(b);return c.level>a.level||(c.level===a.level&&strains.indexOf(c.strain)>strains.indexOf(a.strain));}let i=-1;for(let n=bs.length-1;n>=0;n--){if(bs[n]!=='P'){i=n;break;}}if(i<0)return false;const action=bs[i],ls=((di+i)%4)%2;if(b==='X')return action!=='X'&&action!=='XX'&&ls!==side;if(b==='XX')return action==='X'&&ls!==side;return false;};"+
        "const grid=document.querySelector('.bidgrid');if(!grid)return;grid.innerHTML='';const callrow=document.createElement('div');callrow.className='callrow';const cg=document.createElement('div');cg.className='contractgrid';grid.append(callrow,cg);"+
        "function add(parent,bid,cls){const x=document.createElement('button');x.dataset.bid=bid;x.textContent=names[bid];if(cls)x.className=cls;x.onclick=function(){if(x.disabled)return;const b=data();b.bids.push(bid);save();};parent.appendChild(x);}"+
        "add(callrow,'P');add(callrow,'X');add(callrow,'XX');for(let l=1;l<=7;l++){add(cg,l+'C','clubs');add(cg,l+'D','diamonds');add(cg,l+'H','hearts');add(cg,l+'S','spades');add(cg,l+'NT','notrump');}"+
        "window.updateFastButtons=function(){const done=auctionFinished();grid.querySelectorAll('[data-bid]').forEach(x=>{x.disabled=done||!window.legalBid(x.dataset.bid);});};const oldRender=render;render=function(){oldRender();window.updateFastButtons();};render();"+
        "})();"; webView.evaluateJavascript(js,null);
    }

    public class AndroidFiles {
        @JavascriptInterface public void saveTextFile(final String fileName,final String mimeType,final String content){runOnUiThread(()->{pendingFileName=sanitizeFileName(fileName);pendingMimeType=(mimeType==null||mimeType.trim().isEmpty())?"text/plain":mimeType;pendingContent=content==null?"":content;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(pendingMimeType);intent.putExtra(Intent.EXTRA_TITLE,pendingFileName);startActivityForResult(intent,REQ_SAVE_FILE);});}
        @JavascriptInterface public void openJsonFile(){runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/json","text/plain","application/octet-stream"});startActivityForResult(intent,REQ_OPEN_JSON);});}
    }
    private String sanitizeFileName(String name){String r=(name==null||name.trim().isEmpty())?"bridge-session.json":name.trim();return r.replaceAll("[\\\\/:*?\"<>|]","-");}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==REQ_SAVE_FILE){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception();out.write(pendingContent.getBytes(StandardCharsets.UTF_8));out.flush();Toast.makeText(this,"Backup saved",Toast.LENGTH_LONG).show();webView.evaluateJavascript("window.androidFileSaved && window.androidFileSaved("+JSONObject.quote(pendingFileName)+");",null);}catch(Exception e){Toast.makeText(this,"Could not save file",Toast.LENGTH_LONG).show();}finally{pendingFileName=pendingMimeType=pendingContent=null;}}else if(requestCode==REQ_OPEN_JSON){try(InputStream in=getContentResolver().openInputStream(uri);BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=reader.readLine())!=null)sb.append(line).append('\n');webView.evaluateJavascript("window.receiveImportedJson && window.receiveImportedJson("+JSONObject.quote(sb.toString())+");",null);}catch(Exception e){Toast.makeText(this,"Could not open backup",Toast.LENGTH_LONG).show();}}}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("AndroidFiles");webView.destroy();}super.onDestroy();}
}
