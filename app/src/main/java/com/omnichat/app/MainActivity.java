package com.omnichat.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView web;
    private SecureStore vault;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, HttpURLConnection> active = new ConcurrentHashMap<>();
    private volatile boolean pageReady = false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(Color.rgb(11, 14, 26));
        getWindow().setNavigationBarColor(Color.rgb(11, 14, 26));
        try { vault = new SecureStore(this); }
        catch (Exception error) { throw new RuntimeException("Unable to initialize encrypted storage", error); }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(11, 14, 26));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.addJavascriptInterface(new Bridge(), "OmniNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) { pageReady = true; send("bootstrap", "boot", bootstrap()); }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private JSONObject bootstrap() {
        JSONObject data = new JSONObject();
        try {
            data.put("providers", publicProviders());
            data.put("chats", new JSONArray(vault.get("chats", "[]")));
        } catch (Exception error) { try { data.put("error", error.getMessage()); } catch (Exception ignored) {} }
        return data;
    }

    private JSONArray allProviders() throws Exception { return new JSONArray(vault.get("providers", "[]")); }
    private JSONArray publicProviders() throws Exception {
        JSONArray list = allProviders();
        JSONArray clean = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = new JSONObject(list.getJSONObject(i).toString());
            item.put("hasKey", !item.optString("apiKey").isEmpty());
            item.remove("apiKey");
            item.remove("headers"); // custom headers may contain credentials
            item.put("hasHeaders", !list.getJSONObject(i).optString("headers").isEmpty());
            clean.put(item);
        }
        return clean;
    }
    private JSONObject findProvider(String id) throws Exception {
        JSONArray all = allProviders();
        for (int i = 0; i < all.length(); i++) {
            JSONObject p = all.getJSONObject(i);
            if (id.equals(p.optString("id"))) return p;
        }
        throw new IllegalArgumentException("Provider not found");
    }
    private void send(String type, String requestId, Object payload) {
        JSONObject obj = new JSONObject();
        try { obj.put("type", type).put("requestId", requestId).put("data", payload); }
        catch (Exception ignored) { return; }
        final String js = "window.__nativeReceive(" + JSONObject.quote(obj.toString()) + ")";
        main.post(() -> { if (web != null && pageReady) web.evaluateJavascript(js, null); });
    }
    private JSONObject error(Exception e) {
        JSONObject j = new JSONObject();
        try { j.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
        catch (Exception ignored) {}
        return j;
    }
    private static String safe(String x) { return x == null ? "" : x.trim(); }
    private void validate(JSONObject p) throws Exception {
        String id = safe(p.optString("id"));
        String name = safe(p.optString("name"));
        String adapter = safe(p.optString("adapter"));
        String base = safe(p.optString("baseUrl"));
        if (id.isEmpty() || name.isEmpty() || base.isEmpty()) throw new IllegalArgumentException("ID, name and URL required");
        if (!(adapter.equals("openai") || adapter.equals("anthropic") || adapter.equals("gemini") || adapter.equals("ollama")))
            throw new IllegalArgumentException("Unknown protocol");
        URI uri = new URI(base);
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("Use a valid HTTP(S) base URL without embedded credentials");
        if (base.startsWith("http://") && !safe(p.optString("apiKey")).isEmpty())
            throw new IllegalArgumentException("API keys require HTTPS. HTTP is only allowed without a key (e.g. local Ollama).");
        if (base.startsWith("http://") && !safe(p.optString("headers")).isEmpty())
            throw new IllegalArgumentException("Custom headers require HTTPS.");
        String auth = p.optString("authMode", "auto");
        if (!(auth.equals("auto") || auth.equals("bearer") || auth.equals("x-api-key") || auth.equals("none")))
            throw new IllegalArgumentException("Unsupported authentication mode");
        String headers = p.optString("headers", "").trim();
        if (!headers.isEmpty()) {
            JSONObject h = new JSONObject(headers);
            for (String k : h.keySet()) if (k.contains("\n") || k.contains("\r") || h.optString(k).contains("\n") || h.optString(k).contains("\r"))
                throw new IllegalArgumentException("Invalid header");
        }
    }
    private class Bridge {
        @JavascriptInterface public void saveProvider(String raw) {
            storage.execute(() -> {
                try {
                    JSONObject p = new JSONObject(raw);
                    JSONArray old = allProviders();
                    JSONArray next = new JSONArray();
                    boolean replaced = false;
                    for (int i = 0; i < old.length(); i++) {
                        JSONObject prior = old.getJSONObject(i);
                        if (prior.optString("id").equals(p.optString("id"))) {
                            if (p.optString("apiKey").isEmpty() && !p.optBoolean("clearKey")) p.put("apiKey", prior.optString("apiKey"));
                            if (!p.has("headers")) p.put("headers", prior.optString("headers"));
                            next.put(p); replaced = true;
                        } else next.put(prior);
                    }
                    if (!replaced) next.put(p);
                    p.remove("clearKey");
                    validate(p);
                    vault.put("providers", next.toString());
                    send("providers", "save", publicProviders());
                } catch (Exception e) { send("error", "save", error(e)); }
            });
        }
        @JavascriptInterface public void deleteProvider(String id) {
            storage.execute(() -> {
                try {
                    JSONArray old = allProviders(); JSONArray next = new JSONArray();
                    for (int i = 0; i < old.length(); i++) if (!id.equals(old.getJSONObject(i).optString("id"))) next.put(old.getJSONObject(i));
                    vault.put("providers", next.toString());
                    send("providers", "delete", publicProviders());
                } catch (Exception e) { send("error", "delete", error(e)); }
            });
        }
        @JavascriptInterface public void saveChats(String raw) {
            storage.execute(() -> {
                try { new JSONArray(raw); vault.put("chats", raw); }
                catch (Exception e) { send("error", "chats", error(e)); }
            });
        }
        @JavascriptInterface public void listModels(String id) {
            pool.execute(() -> {
                try { send("models", id, ApiClient.getModels(findProvider(id))); }
                catch (Exception e) { send("error", "models:" + id, error(e)); }
            });
        }
        @JavascriptInterface public void chat(String raw) {
            pool.execute(() -> {
                String requestId = "";
                try {
                    JSONObject request = new JSONObject(raw);
                    requestId = request.getString("requestId");
                    JSONObject p = findProvider(request.getString("providerId"));
                    final String id = requestId;
                    ApiClient.chat(p, request, conn -> active.put(id, conn),
                            delta -> send("delta", id, delta));
                    send("done", id, new JSONObject().put("ok", true));
                } catch (Exception e) { send("error", "chat:" + requestId, error(e)); }
                finally { active.remove(requestId); }
            });
        }
        @JavascriptInterface public void cancel(String id) {
            HttpURLConnection connection = active.remove(id);
            if (connection != null) connection.disconnect();
            send("done", id, new JSONObject());
        }
        @JavascriptInterface public void shareChat(String content) {
            main.post(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT, content);
                startActivity(Intent.createChooser(intent, "Share conversation"));
            });
        }
        @JavascriptInterface public void copy(String content) {
            main.post(() -> ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("OmniChat", content)));
        }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.__back && window.__back()", result -> {
            if ("\"exit\"".equals(result)) MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy() {
        pageReady = false;
        for (HttpURLConnection c : active.values()) c.disconnect();
        active.clear(); pool.shutdownNow(); storage.shutdown();
        if (web != null) { web.removeJavascriptInterface("OmniNative"); web.destroy(); }
        super.onDestroy();
    }
}
