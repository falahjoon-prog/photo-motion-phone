package au.rhccity.photomotion;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private Uri saveUri;
    private OutputStream saveOut;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setKeepScreenOn(true);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, 1);
                } catch (Exception e) {
                    chooser = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooser = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back && window.__back()", v -> {
            if (!"true".equals(v)) runOnUiThread(MainActivity.super::onBackPressed);
        });
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    class Bridge {
        @JavascriptInterface
        public void request(final String id, final String method, final String url, final String headers, final String body) {
            new Thread(() -> {
                int code = 0;
                String text;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(120000);
                    JSONObject h = new JSONObject(headers);
                    for (java.util.Iterator<String> it = h.keys(); it.hasNext(); ) {
                        String k = it.next();
                        c.setRequestProperty(k, h.getString(k));
                    }
                    if (!body.isEmpty()) {
                        c.setDoOutput(true);
                        try (OutputStream o = c.getOutputStream()) {
                            o.write(body.getBytes("UTF-8"));
                        }
                    }
                    code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    }
                    text = bo.toString("UTF-8");
                } catch (Exception e) {
                    code = 0;
                    text = String.valueOf(e.getMessage());
                }
                js("window.__resp(" + JSONObject.quote(id) + "," + code + "," + JSONObject.quote(text) + ")");
            }).start();
        }

        @JavascriptInterface
        public void download(final String id, final String url) {
            new Thread(() -> {
                int code = 0;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(120000);
                    code = c.getResponseCode();
                    if (code == 200) {
                        InputStream in = c.getInputStream();
                        byte[] buf = new byte[3 * 262144];
                        int n;
                        while ((n = readFull(in, buf)) > 0) {
                            byte[] part = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
                            js("window.__dl(" + JSONObject.quote(id) + ",'" + Base64.encodeToString(part, Base64.NO_WRAP) + "')");
                        }
                    }
                } catch (Exception e) {
                    code = 0;
                }
                js("window.__dldone(" + JSONObject.quote(id) + "," + code + ")");
            }).start();
        }

        private int readFull(InputStream in, byte[] buf) throws java.io.IOException {
            int t = 0, n;
            while (t < buf.length && (n = in.read(buf, t, buf.length - t)) > 0) t += n;
            return t;
        }

        @JavascriptInterface
        public void saveBegin(String name, String mime) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Video.Media.MIME_TYPE, mime);
                v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photo Motion");
                v.put(MediaStore.Video.Media.IS_PENDING, 1);
                ContentResolver r = getContentResolver();
                saveUri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                saveOut = r.openOutputStream(saveUri);
            } catch (Exception e) {
                saveOut = null;
            }
        }

        @JavascriptInterface
        public void saveChunk(String b64) {
            try {
                if (saveOut != null) saveOut.write(Base64.decode(b64, Base64.DEFAULT));
            } catch (Exception e) {
                saveOut = null;
            }
        }

        @JavascriptInterface
        public boolean saveEnd() {
            boolean ok = saveOut != null;
            try {
                if (saveOut != null) saveOut.close();
                if (saveUri != null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(saveUri, v, null, null);
                }
            } catch (Exception e) {
                ok = false;
            }
            saveOut = null;
            return ok;
        }

        @JavascriptInterface
        public void toast(String m) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
        }
    }
}
