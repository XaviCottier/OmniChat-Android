package com.lumenfield.viewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_ZIP = 4004;
    private File webRoot;
    private WebView webView;
    private LocalServer server;
    private TextView status;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webRoot = new File(getFilesDir(), "lumenfield-v4-web");
        File index = new File(webRoot, "index.html");
        if (index.isFile()) launchViewer();
        else showImporter();
    }

    private void showImporter() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.rgb(7, 10, 16));

        TextView title = new TextView(this);
        title.setText("ARCHIE LUMENFIELD V4\nANDROID DEMO");
        title.setTextColor(Color.rgb(225, 235, 245));
        title.setTextSize(24);
        title.setGravity(android.view.Gravity.CENTER);

        TextView info = new TextView(this);
        info.setText("\nSelecciona etapa-02-navegacion.zip una sola vez.\nLa app extrae la build web V4 y la ejecuta con un backend demo Android integrado.");
        info.setTextColor(Color.rgb(150, 170, 190));
        info.setTextSize(15);
        info.setGravity(android.view.Gravity.CENTER);
        info.setPadding(0, 20, 0, 30);

        Button b = new Button(this);
        b.setText("ELEGIR ZIP V4");
        b.setOnClickListener(v -> pickZip());

        status = new TextView(this);
        status.setText("");
        status.setTextColor(Color.rgb(80, 220, 190));
        status.setTextSize(13);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(0, 24, 0, 0);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip","application/octet-stream","application/x-zip-compressed"});
        startActivityForResult(i, PICK_ZIP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ZIP || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (status != null) status.setText("Extrayendo build V4...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                importZip(uri);
                runOnUiThread(this::launchViewer);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (status != null) status.setText("ERROR: " + e.getMessage());
                });
            }
        });
    }

    private void importZip(Uri uri) throws Exception {
        deleteRecursive(webRoot);
        if (!webRoot.mkdirs() && !webRoot.isDirectory()) throw new IOException("No se pudo crear almacenamiento interno");

        int extracted = 0;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry e;
            byte[] buf = new byte[1024 * 128];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName().replace('\\','/');
                int marker = name.indexOf("/fuentes/dist/");
                String rel = null;
                if (marker >= 0) rel = name.substring(marker + "/fuentes/dist/".length());
                else if (name.startsWith("fuentes/dist/")) rel = name.substring("fuentes/dist/".length());
                if (rel == null || rel.isEmpty()) { zin.closeEntry(); continue; }

                File out = new File(webRoot, rel);
                String rootPath = webRoot.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(rootPath)) throw new IOException("ZIP inválido");

                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    File p = out.getParentFile();
                    if (p != null) p.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zin.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    extracted++;
                }
                zin.closeEntry();
            }
        }
        if (!new File(webRoot, "index.html").isFile()) throw new IOException("No encontré fuentes/dist/index.html dentro del ZIP");
        if (extracted < 3) throw new IOException("Build web incompleta");
    }

    private void launchViewer() {
        if (server != null) server.stop();
        try {
            server = new LocalServer(webRoot);
            server.start();
        } catch (Exception e) {
            showImporter();
            status.setText("ERROR servidor local: " + e.getMessage());
            return;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView, new ViewGroup.LayoutParams(-1, -1));
        webView.loadUrl("http://127.0.0.1:" + server.getPort() + "/");
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) for (File c : cs) deleteRecursive(c);
        }
        f.delete();
    }

    static final class LocalServer {
        private final File root;
        private final ExecutorService pool = Executors.newCachedThreadPool();
        private final Map<String,String> notes = new ConcurrentHashMap<>();
        private final JSONObject state;
        private final JSONArray allNodes;
        private final JSONArray allInsights;
        private volatile boolean running;
        private ServerSocket socket;
        private int port;

        LocalServer(File root) throws Exception {
            this.root = root;
            this.allNodes = buildNodes();
            this.allInsights = buildInsights(allNodes);
            this.state = new JSONObject()
                    .put("errors", 0)
                    .put("loaded", true)
                    .put("name", "ARCHIE Lumenfield V4 Android Demo")
                    .put("root", "ARCHIE-DEMO")
                    .put("total", allNodes.length())
                    .put("truncated", false)
                    .put("source", "DEMO ANDROID INTEGRADO");
        }

        int getPort() { return port; }

        void start() throws Exception {
            socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            port = socket.getLocalPort();
            running = true;
            pool.execute(() -> {
                while (running) {
                    try {
                        Socket accepted = socket.accept();
                        pool.execute(() -> handle(accepted));
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            });
        }

        void stop() {
            running = false;
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            pool.shutdownNow();
        }

        private void handle(Socket client) {
            try (Socket c = client) {
                c.setSoTimeout(10000);
                BufferedInputStream in = new BufferedInputStream(c.getInputStream());
                OutputStream out = new BufferedOutputStream(c.getOutputStream());

                String requestLine = readLine(in);
                if (requestLine == null || requestLine.isEmpty()) return;
                String[] p = requestLine.split(" ");
                if (p.length < 2) return;
                String method = p[0];
                String rawPath = p[1];

                int contentLength = 0;
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int k = line.indexOf(':');
                    if (k > 0 && line.substring(0,k).trim().equalsIgnoreCase("Content-Length")) {
                        try { contentLength = Integer.parseInt(line.substring(k+1).trim()); } catch (Exception ignored) {}
                    }
                }
                byte[] body = new byte[Math.max(0, contentLength)];
                int off = 0;
                while (off < body.length) {
                    int n = in.read(body, off, body.length - off);
                    if (n < 0) break;
                    off += n;
                }

                URI uri = new URI("http://127.0.0.1" + rawPath);
                String path = uri.getPath();
                Map<String,String> q = query(uri.getRawQuery());

                if (path.startsWith("/api/")) {
                    String json = api(method, path, q, new String(body, 0, off, StandardCharsets.UTF_8));
                    sendBytes(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
                    return;
                }

                if (path.equals("/")) path = "/index.html";
                File f = new File(root, path.substring(1));
                String rootPath = root.getCanonicalPath() + File.separator;
                if (!f.getCanonicalPath().startsWith(rootPath) || !f.isFile()) {
                    sendBytes(out, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                sendFile(out, f, mime(f.getName()));
            } catch (Exception e) {
                try {
                    OutputStream out = client.getOutputStream();
                    sendBytes(out, 500, "text/plain; charset=utf-8", ("Server error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }

        private String api(String method, String path, Map<String,String> q, String body) throws Exception {
            switch (path) {
                case "/api/state":
                    return state.toString();
                case "/api/demo-location":
                    return new JSONObject().put("path","ARCHIE-DEMO").put("label","Demo Android integrado").toString();
                case "/api/pick":
                    return new JSONObject().put("path","ARCHIE-DEMO").toString();
                case "/api/select":
                    return new JSONObject().put("root","ARCHIE-DEMO").put("total",allNodes.length()).put("errors",0).put("truncated",false).toString();
                case "/api/refresh":
                    return new JSONObject().put("root","ARCHIE-DEMO").put("total",allNodes.length()).toString();
                case "/api/graph":
                    return graph(q.getOrDefault("focus","."), intVal(q.get("limit"), 2400)).toString();
                case "/api/insights": {
                    JSONObject obj = new JSONObject();
                    obj.put("nodes", allInsights);
                    obj.put("sampled", allInsights.length());
                    obj.put("skipped", 0);
                    obj.put("errors", 0);
                    return obj.toString();
                }
                case "/api/preview": {
                    String id = q.getOrDefault("id","");
                    JSONObject node = findNode(id);
                    if (node == null) throw new IOException("Archivo no encontrado");
                    String text = "// Vista previa demo Android\n// " + id + "\n\n" + previewText(id);
                    return new JSONObject().put("id",id).put("text",text).put("truncated",false).put("size",text.length()).toString();
                }
                case "/api/note": {
                    String id = q.getOrDefault("id","");
                    if ("PUT".equalsIgnoreCase(method)) {
                        JSONObject o = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                        notes.put(o.optString("id",id), o.optString("text",""));
                        return new JSONObject().put("saved",true).toString();
                    }
                    return new JSONObject().put("id",id).put("text",notes.getOrDefault(id,"")).toString();
                }
                case "/api/find": {
                    String qq = q.getOrDefault("q","").toLowerCase(Locale.ROOT);
                    JSONArray a = new JSONArray();
                    for (int i=0;i<allNodes.length() && a.length()<24;i++) {
                        JSONObject n = allNodes.getJSONObject(i);
                        if (!qq.isEmpty() && n.optString("id").toLowerCase(Locale.ROOT).contains(qq)) a.put(n);
                    }
                    return a.toString();
                }
                default:
                    if (path.startsWith("/api/profile") || path.startsWith("/api/profiles")) return new JSONArray().toString();
                    return new JSONObject().put("ok",true).toString();
            }
        }

        private JSONObject graph(String focus, int limit) throws Exception {
            limit = Math.max(1, Math.min(36000, limit));
            Set<String> include = new LinkedHashSet<>();
            Map<String,JSONObject> byId = new HashMap<>();
            Map<String,List<JSONObject>> children = new HashMap<>();
            for (int i=0;i<allNodes.length();i++) {
                JSONObject n = allNodes.getJSONObject(i);
                byId.put(n.getString("id"), n);
                children.computeIfAbsent(n.optString("parent",""), k -> new ArrayList<>()).add(n);
            }
            if (!byId.containsKey(focus)) focus = ".";
            List<String> chain = new ArrayList<>();
            String at = focus;
            while (at != null && !at.isEmpty()) {
                chain.add(at);
                if (".".equals(at)) break;
                JSONObject n = byId.get(at);
                at = n == null ? "." : n.optString("parent",".");
            }
            Collections.reverse(chain);
            include.addAll(chain);
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(focus);
            while (!queue.isEmpty() && include.size()<limit) {
                String cur = queue.removeFirst();
                for (JSONObject n : children.getOrDefault(cur, Collections.emptyList())) {
                    String id = n.getString("id");
                    if (include.add(id)) queue.addLast(id);
                    if (include.size()>=limit) break;
                }
            }
            JSONArray nodes = new JSONArray();
            JSONArray links = new JSONArray();
            for (String id : include) {
                JSONObject n = byId.get(id);
                if (n != null) nodes.put(n);
            }
            for (String id : include) {
                if (".".equals(id)) continue;
                JSONObject n = byId.get(id);
                if (n != null && include.contains(n.optString("parent"))) {
                    links.put(new JSONObject().put("source",n.optString("parent")).put("target",id));
                }
            }
            return new JSONObject()
                    .put("nodes",nodes).put("links",links)
                    .put("total",allNodes.length()).put("focus",focus)
                    .put("omitted",include.size()<allNodes.length())
                    .put("errors",0).put("truncated",false);
        }

        private JSONObject findNode(String id) throws Exception {
            for (int i=0;i<allNodes.length();i++) {
                JSONObject n = allNodes.getJSONObject(i);
                if (id.equals(n.optString("id"))) return n;
            }
            return null;
        }

        private static JSONArray buildNodes() throws Exception {
            JSONArray a = new JSONArray();
            long now = System.currentTimeMillis();
            addNode(a, ".", "ARCHIE-DEMO", "", "dir", 4096, now, 0);

            String[] roots = {"src","models","docs","tests","plugins","configs","data","assets"};
            for (String r : roots) addNode(a,r,r,".","dir",4096,now,1);

            String[][] dirs = {
                    {"src","cortex"},{"src","iris"},{"src","sentry"},{"src","forge"},
                    {"src/iris","navigation"},{"src/iris","vision"},{"src/sentry","network"},{"src/sentry","devices"},
                    {"models","bonsai"},{"models","iris"},{"models/bonsai","experts"},{"models/iris","adapters"},
                    {"docs","architecture"},{"docs","research"},{"tests","unit"},{"tests","integration"},
                    {"plugins","iot"},{"plugins","vision"},{"plugins","automation"},
                    {"configs","profiles"},{"data","memory"},{"data","knowledge"},{"assets","materials"},{"assets","shaders"}
            };
            for (String[] d : dirs) {
                String id = d[0] + "/" + d[1];
                int depth = id.split("/").length;
                addNode(a,id,d[1],d[0],"dir",4096,now,depth);
            }

            String[][] files = {
                    {"src/main.cpp","src"},{"src/cortex/cortex.cpp","src/cortex"},{"src/cortex/router.cpp","src/cortex"},{"src/cortex/memory.cpp","src/cortex"},
                    {"src/iris/navigation/navigation.cpp","src/iris/navigation"},{"src/iris/navigation/field.cpp","src/iris/navigation"},{"src/iris/vision/vision.cpp","src/iris/vision"},{"src/iris/vision/grounding.cpp","src/iris/vision"},
                    {"src/sentry/network/network.rs","src/sentry/network"},{"src/sentry/network/router.rs","src/sentry/network"},{"src/sentry/devices/iot.rs","src/sentry/devices"},
                    {"src/forge/builder.py","src/forge"},{"src/forge/compiler.py","src/forge"},
                    {"docs/architecture/CORTEX.md","docs/architecture"},{"docs/architecture/IRIS.md","docs/architecture"},{"docs/architecture/SENTRY.md","docs/architecture"},
                    {"docs/research/navigation.md","docs/research"},{"docs/research/clustering.md","docs/research"},
                    {"configs/archie.json","configs"},{"configs/profiles/performance.json","configs/profiles"},{"configs/profiles/research.json","configs/profiles"},
                    {"tests/unit/cortex.test.js","tests/unit"},{"tests/unit/iris.test.js","tests/unit"},{"tests/integration/system.test.js","tests/integration"},
                    {"plugins/iot/plugin.json","plugins/iot"},{"plugins/vision/plugin.json","plugins/vision"},{"plugins/automation/plugin.json","plugins/automation"},
                    {"assets/shaders/node.vert","assets/shaders"},{"assets/shaders/node.frag","assets/shaders"},{"assets/materials/materials.json","assets/materials"}
            };
            for (String[] f : files) addNode(a,f[0],baseName(f[0]),f[1],"file",800 + f[0].length()*31,now,f[0].split("/").length);

            for (int i=1;i<=32;i++) {
                String id=String.format(Locale.US,"models/bonsai/experts/expert-%02d.json",i);
                addNode(a,id,baseName(id),"models/bonsai/experts","file",1400+i*23,now,4);
            }
            for (int i=1;i<=18;i++) {
                String dir=String.format(Locale.US,"models/iris/adapters/task-%02d",i);
                addNode(a,dir,baseName(dir),"models/iris/adapters","dir",4096,now,4);
                String id=dir+"/adapter.json";
                addNode(a,id,"adapter.json",dir,"file",1100+i*37,now,5);
            }
            for (int i=1;i<=30;i++) {
                String id=String.format(Locale.US,"data/memory/memory-%03d.md",i);
                addNode(a,id,baseName(id),"data/memory","file",600+i*19,now,3);
            }
            for (int i=1;i<=30;i++) {
                String id=String.format(Locale.US,"data/knowledge/concept-%03d.txt",i);
                addNode(a,id,baseName(id),"data/knowledge","file",500+i*17,now,3);
            }
            return a;
        }

        private static JSONArray buildInsights(JSONArray nodes) throws Exception {
            JSONArray out = new JSONArray();
            for (int i=0;i<nodes.length();i++) {
                JSONObject n = nodes.getJSONObject(i);
                String id = n.getString("id");
                String role = role(id, n.optString("kind"));
                JSONArray terms = new JSONArray();
                for (String t : (id + " " + role).toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
                    if (t.length()>=4 && terms.length()<8) terms.put(t);
                }
                JSONArray refs = new JSONArray();
                if (id.contains("iris") && !"src/cortex/router.cpp".equals(id)) refs.put("src/cortex/router.cpp");
                if (id.contains("sentry")) refs.put("src/cortex/cortex.cpp");
                out.put(new JSONObject()
                        .put("id",id).put("role",role).put("terms",terms)
                        .put("headings",new JSONArray()).put("references",refs)
                        .put("sampled","file".equals(n.optString("kind"))));
            }
            return out;
        }

        private static String role(String id, String kind) {
            if ("dir".equals(kind)) return "Estructura";
            String x=id.toLowerCase(Locale.ROOT);
            if (x.contains("test")) return "Pruebas";
            if (x.contains("docs") || x.endsWith(".md")) return "Conocimiento";
            if (x.contains("config") || x.endsWith(".json")) return "Configuración";
            if (x.contains("iris") || x.contains("vision")) return "Percepción";
            if (x.contains("sentry") || x.contains("network")) return "Seguridad";
            if (x.contains("cortex")) return "Núcleo";
            if (x.contains("plugin")) return "Integración";
            return "Código";
        }

        private static void addNode(JSONArray a, String id, String name, String parent, String kind, long size, long modified, int depth) throws Exception {
            a.put(new JSONObject()
                    .put("id",id).put("name",name).put("parent",parent)
                    .put("kind",kind).put("size",size).put("modified",modified).put("depth",depth));
        }

        private static String previewText(String id) {
            if (id.endsWith(".json")) return "{\n  \"demo\": true,\n  \"source\": \"ARCHIE Android\"\n}";
            if (id.endsWith(".md")) return "# " + baseName(id) + "\n\nARCHIE Lumenfield Android demo.";
            if (id.endsWith(".rs")) return "pub fn demo() { /* Sentry / Rust */ }";
            if (id.endsWith(".py")) return "def demo():\n    return 'ARCHIE'";
            if (id.endsWith(".cpp") || id.endsWith(".vert") || id.endsWith(".frag")) return "// ARCHIE Lumenfield demo\nvoid main() {}";
            return "ARCHIE Lumenfield demo node";
        }

        private static String baseName(String p) {
            int i=p.lastIndexOf('/');
            return i<0?p:p.substring(i+1);
        }

        private static Map<String,String> query(String raw) throws Exception {
            Map<String,String> m=new HashMap<>();
            if (raw==null || raw.isEmpty()) return m;
            for (String part:raw.split("&")) {
                int k=part.indexOf('=');
                String a=k<0?part:part.substring(0,k);
                String b=k<0?"":part.substring(k+1);
                m.put(URLDecoder.decode(a,"UTF-8"),URLDecoder.decode(b,"UTF-8"));
            }
            return m;
        }

        private static int intVal(String s, int d) {
            try { return Integer.parseInt(s); } catch(Exception e) { return d; }
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            int prev=-1, cur;
            while ((cur=in.read())!=-1) {
                if (prev=='\r' && cur=='\n') break;
                if (prev!=-1) b.write(prev);
                prev=cur;
                if (b.size()>32768) throw new IOException("Header demasiado grande");
            }
            if (cur==-1 && prev!=-1) b.write(prev);
            return b.toString("UTF-8");
        }

        private static void sendBytes(OutputStream out, int code, String type, byte[] data) throws IOException {
            String h="HTTP/1.1 "+code+" "+(code==200?"OK":"ERROR")+"\r\n"+
                    "Content-Type: "+type+"\r\n"+
                    "Content-Length: "+data.length+"\r\n"+
                    "Cache-Control: no-cache\r\n"+
                    "Connection: close\r\n\r\n";
            out.write(h.getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.flush();
        }

        private static void sendFile(OutputStream out, File f, String type) throws IOException {
            String h="HTTP/1.1 200 OK\r\n"+
                    "Content-Type: "+type+"\r\n"+
                    "Content-Length: "+f.length()+"\r\n"+
                    "Cache-Control: no-cache\r\n"+
                    "Connection: close\r\n\r\n";
            out.write(h.getBytes(StandardCharsets.UTF_8));
            try (InputStream in=new BufferedInputStream(new FileInputStream(f))) {
                byte[] buf=new byte[128*1024]; int n;
                while((n=in.read(buf))>0) out.write(buf,0,n);
            }
            out.flush();
        }

        private static String mime(String name) {
            String n=name.toLowerCase(Locale.ROOT);
            if (n.endsWith(".html")) return "text/html; charset=utf-8";
            if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
            if (n.endsWith(".css")) return "text/css; charset=utf-8";
            if (n.endsWith(".json")) return "application/json; charset=utf-8";
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".webp")) return "image/webp";
            if (n.endsWith(".svg")) return "image/svg+xml";
            if (n.endsWith(".woff2")) return "font/woff2";
            if (n.endsWith(".wasm")) return "application/wasm";
            return "application/octet-stream";
        }
    }
}
