package pe.berdi.foliopdf;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Folio PDF: envuelve la app web (incluida en assets/www) y la conecta con Android. */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSave;
    private final Map<String, Uri> incoming = Collections.synchronizedMap(new HashMap<>());
    private volatile String pendingJson;
    private int counter = 0;
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setUserAgentString(s.getUserAgentString() + " FolioPDFAndroid");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/incoming/", path -> {
                    Uri u = incoming.get(path);
                    if (u == null) return null;
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        Map<String, String> h = new HashMap<>();
                        h.put("Cache-Control", "no-store");
                        return new WebResourceResponse("application/pdf", null, 200, "OK", h, in);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
                notifyIncoming();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(i, REQ_OPEN);
                } catch (Exception e) {
                    fileCallback = null;
                    cb.onReceiveValue(null);
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "FolioAndroid");
        handleIntent(getIntent());
        web.loadUrl(START);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @SuppressWarnings("deprecation")
    private void handleIntent(Intent it) {
        if (it == null) return;
        Uri u = null;
        String action = it.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            u = it.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            Object extra = it.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) u = (Uri) extra;
            ClipData cd = it.getClipData();
            if (u == null && cd != null && cd.getItemCount() > 0) u = cd.getItemAt(0).getUri();
        }
        if (u == null) return;
        String key = "f" + (++counter) + ".pdf";
        incoming.put(key, u);
        try {
            JSONObject o = new JSONObject();
            o.put("url", "/incoming/" + key);
            o.put("name", nameOf(u));
            pendingJson = o.toString();
        } catch (Exception ignored) { }
        notifyIncoming();
    }

    private void notifyIncoming() {
        if (pageReady && pendingJson != null) {
            web.evaluateJavascript("window.folioIncoming && folioIncoming()", null);
        }
    }

    private String nameOf(Uri u) {
        String n = null;
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) n = c.getString(0);
        } catch (Exception ignored) { }
        if (n == null) n = u.getLastPathSegment();
        if (n == null || n.isEmpty()) n = "documento.pdf";
        if (!n.toLowerCase(Locale.ROOT).endsWith(".pdf")) n += ".pdf";
        return n;
    }

    private void reply(String status) {
        runOnUiThread(() -> web.evaluateJavascript("window.folioSaved && folioSaved('" + status + "')", null));
    }

    private class Bridge {
        @JavascriptInterface
        public String takeIncoming() {
            String j = pendingJson;
            pendingJson = null;
            return j;
        }

        @JavascriptInterface
        public void save(String b64, String name) {
            try {
                pendingSave = Base64.decode(b64, Base64.DEFAULT);
            } catch (Exception e) {
                reply("error");
                return;
            }
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (Exception e) {
                    pendingSave = null;
                    reply("error");
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN) {
            if (fileCallback == null) return;
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                ClipData cd = data.getClipData();
                if (cd != null) {
                    out = new Uri[cd.getItemCount()];
                    for (int k = 0; k < out.length; k++) out[k] = cd.getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(out);
            fileCallback = null;
        } else if (req == REQ_SAVE) {
            if (res != RESULT_OK || data == null || data.getData() == null || pendingSave == null) {
                pendingSave = null;
                reply("cancel");
                return;
            }
            String status = "error";
            Uri dest = data.getData();
            for (String mode : new String[]{"wt", "w"}) {
                try (OutputStream os = getContentResolver().openOutputStream(dest, mode)) {
                    if (os != null) { os.write(pendingSave); status = "saved"; break; }
                } catch (Exception ignored) { }
            }
            pendingSave = null;
            reply(status);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.folioBack && folioBack()) ? '1' : '0'", v -> {
            if (!"\"1\"".equals(v)) MainActivity.super.onBackPressed();
        });
    }
}
