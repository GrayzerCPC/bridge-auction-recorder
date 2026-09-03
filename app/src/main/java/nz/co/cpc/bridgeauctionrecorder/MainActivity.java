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

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidFiles(), "AndroidFiles");
        webView.loadUrl("file:///android_asset/www/index.html");
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
