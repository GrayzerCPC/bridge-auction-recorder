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
    private String pendingFileName;
    private String pendingMimeType;
    private String pendingContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                repairBiddingUi();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidFiles(), "AndroidFiles");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void repairBiddingUi() {
        String js = "(function(){" +
                "window.legalBid=function(b){" +
                "const d=data(),bids=d.bids,dealerIndex=seats.indexOf(d.dealer),callerIndex=(dealerIndex+bids.length)%4,callerSide=callerIndex%2;" +
                "if(b==='P')return true;" +
                "if(b!=='X'&&b!=='XX'){const last=[...bids].reverse().find(x=>!['P','X','XX'].includes(x));if(!last)return true;const a=bidInfo(last),c=bidInfo(b);return c.level>a.level||(c.level===a.level&&strains.indexOf(c.strain)>strains.indexOf(a.strain));}" +
                "let i=-1;for(let n=bids.length-1;n>=0;n--){if(bids[n]!=='P'){i=n;break;}}if(i<0)return false;" +
                "const action=bids[i],lastCaller=(dealerIndex+i)%4,lastSide=lastCaller%2;" +
                "if(b==='X')return action!=='X'&&action!=='XX'&&lastSide!==callerSide;" +
                "if(b==='XX')return action==='X'&&lastSide!==callerSide;return false;};" +
                "const grid=document.querySelector('.bidgrid');if(!grid)return;grid.innerHTML='';" +
                "const calls=['P','X','XX','1C','1D','1H','1S','1NT','2C','2D','2H','2S','2NT','3C','3D','3H','3S','3NT','4C','4D','4H','4S','4NT','5C','5D','5H','5S','5NT','6C','6D','6H','6S','6NT','7C','7D','7H','7S','7NT'];" +
                "calls.forEach(function(bid){const btn=document.createElement('button');btn.dataset.bid=bid;btn.textContent=names[bid];btn.onclick=function(){const board=data();if(auctionFinished()){alert('This auction has ended. Use Undo if you need to correct it.');return;}if(!window.legalBid(bid)){alert('That bid is not legal after the current auction.');return;}board.bids.push(bid);save();};grid.appendChild(btn);});" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public class AndroidFiles {
        @JavascriptInterface
        public void saveTextFile(final String fileName, final String mimeType, final String content) {
            runOnUiThread(() -> {
                pendingFileName = sanitizeFileName(fileName);
                pendingMimeType = (mimeType == null || mimeType.trim().isEmpty()) ? "text/plain" : mimeType;
                pendingContent = content == null ? "" : content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingMimeType);
                intent.putExtra(Intent.EXTRA_TITLE, pendingFileName);
                startActivityForResult(intent, REQ_SAVE_FILE);
            });
        }

        @JavascriptInterface
        public void openJsonFile() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
                startActivityForResult(intent, REQ_OPEN_JSON);
            });
        }
    }

    private String sanitizeFileName(String name) {
        String result = (name == null || name.trim().isEmpty()) ? "bridge-session.json" : name.trim();
        return result.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE_FILE) {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception("Could not open selected file");
                out.write(pendingContent.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "Backup saved", Toast.LENGTH_LONG).show();
                webView.evaluateJavascript("window.androidFileSaved && window.androidFileSaved(" + JSONObject.quote(pendingFileName) + ");", null);
            } catch (Exception e) {
                Toast.makeText(this, "Could not save file", Toast.LENGTH_LONG).show();
                webView.evaluateJavascript("window.androidFileSaveFailed && window.androidFileSaveFailed();", null);
            } finally {
                pendingFileName = null;
                pendingMimeType = null;
                pendingContent = null;
            }
        } else if (requestCode == REQ_OPEN_JSON) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                String js = "window.receiveImportedJson && window.receiveImportedJson(" + JSONObject.quote(sb.toString()) + ");";
                webView.evaluateJavascript(js, null);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open backup", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidFiles");
            webView.destroy();
        }
        super.onDestroy();
    }
}
