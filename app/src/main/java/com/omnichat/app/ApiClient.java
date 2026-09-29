package com.omnichat.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    private ApiClient() {}
    interface ConnectionReady { void accept(HttpURLConnection connection); }
    interface Delta { void accept(String text); }

    static JSONArray getModels(JSONObject p) throws Exception {
        String kind = p.getString("adapter");
        String path = p.optString("modelsPath", "").trim();
        if (path.isEmpty()) path = kind.equals("ollama") ? "/api/tags" : "/models";
        URL url = new URL(endpoint(p, path));
        HttpURLConnection c = open(url, p);
        try {
            c.setRequestMethod("GET");
            int status = c.getResponseCode();
            String body = read(c, status >= 400);
            if (status >= 400) throw httpError(status, body, p.optString("apiKey"));
            JSONObject root = new JSONObject(body);
            JSONArray result = new JSONArray();
            JSONArray source = kind.equals("gemini") ? root.optJSONArray("models") :
                    kind.equals("ollama") ? root.optJSONArray("models") : root.optJSONArray("data");
            if (source == null) throw new IllegalArgumentException("Provider did not return a compatible models array. Enter model ID manually.");
            for (int i = 0; i < source.length(); i++) {
                JSONObject m = source.optJSONObject(i);
                if (m == null) continue;
                String id = kind.equals("ollama") ? m.optString("name") :
                        kind.equals("gemini") ? m.optString("name").replaceFirst("^models/", "") : m.optString("id");
                if (!id.isEmpty()) result.put(id);
            }
            return result;
        } finally { c.disconnect(); }
    }

    static void chat(JSONObject p, JSONObject req, ConnectionReady onConnected, Delta onDelta) throws Exception {
        String kind = p.getString("adapter");
        String model = req.getString("model").trim();
        if (model.isEmpty()) throw new IllegalArgumentException("Select or enter a model ID");
        JSONArray messages = req.getJSONArray("messages");
        JSONObject settings = req.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        int max = Math.max(1, Math.min(32768, settings.optInt("maxTokens", 2048)));
        double temp = Math.max(0, Math.min(2, settings.optDouble("temperature", 0.7)));
        JSONObject payload = new JSONObject();
        boolean stream = p.optBoolean("stream", true);
        boolean sendTemperature = p.optBoolean("sendTemperature", true);
        String tokenField = p.optString("tokenField", "max_tokens");
        String route;
        switch (kind) {
            case "anthropic": {
                route = "/messages";
                payload.put("model", model).put("max_tokens", max).put("stream", stream);
                if (sendTemperature) payload.put("temperature", temp);
                JSONArray arr = new JSONArray();
                StringBuilder system = new StringBuilder();
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject m = messages.getJSONObject(i);
                    String role = m.optString("role");
                    if (role.equals("system")) { if (system.length() > 0) system.append("\n"); system.append(m.optString("content")); }
                    else if (role.equals("user") || role.equals("assistant")) arr.put(new JSONObject().put("role", role).put("content", m.optString("content")));
                }
                if (system.length() > 0) payload.put("system", system.toString());
                payload.put("messages", arr);
                break;
            }
            case "gemini": {
                String modelId = model.replaceFirst("^models/", "");
                route = "/models/" + URLEncoder.encode(modelId, "UTF-8") + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent");
                JSONArray contents = new JSONArray();
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject m = messages.getJSONObject(i);
                    String role = m.optString("role");
                    if (role.equals("system")) {
                        payload.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", m.optString("content")))));
                    } else if (role.equals("user") || role.equals("assistant")) {
                        contents.put(new JSONObject().put("role", role.equals("assistant") ? "model" : "user")
                                .put("parts", new JSONArray().put(new JSONObject().put("text", m.optString("content")))));
                    }
                }
                payload.put("contents", contents);
                JSONObject generation = new JSONObject().put("maxOutputTokens", max);
                if (sendTemperature) generation.put("temperature", temp);
                payload.put("generationConfig", generation);
                break;
            }
            case "ollama": {
                route = "/api/chat";
                payload.put("model", model).put("messages", messages).put("stream", stream);
                JSONObject options = new JSONObject().put("num_predict", max);
                if (sendTemperature) options.put("temperature", temp);
                payload.put("options", options);
                break;
            }
            default: {
                route = p.optString("chatPath", "").trim();
                if (route.isEmpty()) route = "/chat/completions";
                payload.put("model", model).put("messages", messages).put("stream", stream);
                if (sendTemperature) payload.put("temperature", temp);
                if (!tokenField.equals("none")) {
                    if (!(tokenField.equals("max_tokens") || tokenField.equals("max_completion_tokens"))) throw new IllegalArgumentException("Unsupported token field");
                    payload.put(tokenField, max);
                }
            }
        }
        HttpURLConnection c = open(new URL(endpoint(p, route)), p);
        onConnected.accept(c);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "text/event-stream, application/x-ndjson, application/json");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int status = c.getResponseCode();
            if (status >= 400) throw httpError(status, read(c, true), p.optString("apiKey"));
            String contentType = c.getContentType() == null ? "" : c.getContentType().toLowerCase();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                if (contentType.contains("text/event-stream")) {
                    String line;
                    StringBuilder event = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (event.length() > 0) { emitEvent(kind, event.toString(), onDelta); event.setLength(0); }
                        } else if (line.startsWith("data:")) {
                            if (event.length() > 0) event.append('\n');
                            event.append(line.substring(5).trim());
                        }
                    }
                    if (event.length() > 0) emitEvent(kind, event.toString(), onDelta);
                } else if (kind.equals("ollama") || contentType.contains("ndjson")) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) emitEvent(kind, line, onDelta);
                    }
                } else {
                    StringBuilder body = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    if (body.length() > 0) emitEvent(kind, body.toString(), onDelta);
                }
            }
        } finally { c.disconnect(); }
    }

    private static void emitEvent(String kind, String data, Delta cb) throws Exception {
        if (data.isEmpty() || data.equals("[DONE]")) return;
        JSONObject item = new JSONObject(data);
        JSONObject error = item.optJSONObject("error");
        if (error != null) throw new IllegalStateException(error.optString("message", "Provider error"));
        String text = "";
        if (kind.equals("anthropic")) {
            JSONObject delta = item.optJSONObject("delta");
            if (delta != null) text = delta.optString("text", "");
            else {
                JSONArray blocks = item.optJSONArray("content");
                if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
                    JSONObject b = blocks.optJSONObject(i);
                    if (b != null) text += b.optString("text", "");
                }
            }
            if (item.optString("type").equals("error")) throw new IllegalStateException(item.toString());
        } else if (kind.equals("gemini")) {
            JSONArray candidates = item.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null) for (int i = 0; i < parts.length(); i++) text += parts.getJSONObject(i).optString("text", "");
                }
            }
        } else if (kind.equals("ollama")) {
            JSONObject message = item.optJSONObject("message");
            if (message != null) text = message.optString("content", "");
            else text = item.optString("response", "");
        } else {
            JSONArray choices = item.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.optJSONObject("delta");
                JSONObject message = choice.optJSONObject("message");
                Object content = delta != null ? delta.opt("content") : message != null ? message.opt("content") : null;
                if (content instanceof String) text = (String) content;
                else if (content instanceof JSONArray) {
                    JSONArray blocks = (JSONArray) content;
                    for (int i = 0; i < blocks.length(); i++) {
                        JSONObject b = blocks.optJSONObject(i);
                        if (b != null) text += b.optString("text", "");
                    }
                }
            }
        }
        if (!text.isEmpty()) cb.accept(text);
    }

    private static String endpoint(JSONObject p, String route) {
        String base = p.optString("baseUrl").trim().replaceAll("/+$", "");
        if (route.startsWith("https://") || route.startsWith("http://")) return route;
        String kind = p.optString("adapter");
        // Anthropic /v1/messages; Gemini /v1beta/models; OpenAI base normally includes /v1.
        if (kind.equals("anthropic") && !base.matches(".*/v[0-9]+$")) base += "/v1";
        if (kind.equals("gemini") && !base.matches(".*/v[0-9]+(beta|alpha)?$")) base += "/v1beta";
        if (base.endsWith(route)) return base;
        return base + (route.startsWith("/") ? route : "/" + route);
    }
    private static HttpURLConnection open(URL url, JSONObject p) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false); // Prevent leaking tokens to redirect targets.
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent", "OmniChat-Android/0.1");
        String key = p.optString("apiKey").trim();
        String adapter = p.optString("adapter");
        if (!"https".equalsIgnoreCase(url.getProtocol()) && (!key.isEmpty() || !p.optString("headers").isEmpty()))
            throw new IllegalArgumentException("Refusing to send credentials over HTTP");
        String auth = p.optString("authMode", "auto");
        if (adapter.equals("anthropic")) c.setRequestProperty("anthropic-version", "2023-06-01");
        if (!key.isEmpty()) {
            if (auth.equals("none")) { /* Custom headers can reference ${API_KEY}. */ }
            else if (auth.equals("bearer")) c.setRequestProperty("Authorization", "Bearer " + key);
            else if (auth.equals("x-api-key")) c.setRequestProperty("x-api-key", key);
            else if (auth.equals("auto")) {
                if (adapter.equals("anthropic")) c.setRequestProperty("x-api-key", key);
                else if (adapter.equals("gemini")) c.setRequestProperty("x-goog-api-key", key);
                else c.setRequestProperty("Authorization", "Bearer " + key);
            } else throw new IllegalArgumentException("Unknown auth mode");
        }
        String headers = p.optString("headers", "");
        if (!headers.isEmpty()) {
            JSONObject custom = new JSONObject(headers);
            for (java.util.Iterator<String> it = custom.keys(); it.hasNext();) {
                String h = it.next();
                c.setRequestProperty(h, custom.optString(h).replace("${API_KEY}", key));
            }
        }
        return c;
    }
    private static String read(HttpURLConnection c, boolean error) throws Exception {
        InputStream in = error ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] data = new char[4096]; int n;
            while ((n = r.read(data)) > 0 && b.length() < 65536) b.append(data, 0, n);
        }
        return b.toString();
    }
    private static Exception httpError(int status, String raw, String key) {
        String explanation;
        switch (status) {
            case 400: explanation = "Invalid model or request parameters"; break;
            case 401: explanation = "Invalid or expired API key"; break;
            case 402: explanation = "Payment required or provider billing/quota restriction"; break;
            case 403: explanation = "Provider denied access"; break;
            case 404: explanation = "Endpoint or model not found"; break;
            case 429: explanation = "Rate limit or provider quota reached"; break;
            case 500: case 502: case 503: case 504: explanation = "Provider server unavailable"; break;
            default: explanation = "Provider HTTP error";
        }
        String detail = raw == null ? "" : raw.replace(key.isEmpty() ? "\u0000" : key, "[redacted]");
        if (detail.length() > 650) detail = detail.substring(0, 650) + "…";
        return new IllegalStateException("HTTP " + status + " — " + explanation + (detail.isEmpty() ? "" : ": " + detail));
    }
}
