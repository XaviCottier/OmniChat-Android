package com.lumenfield.viewer;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.content.*;
import java.util.*;

public class MainActivity extends Activity {
    private LumenView view;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new LumenView(this);
        setContentView(view);
        immersive();
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) {
            immersive();
            if (view != null) view.postDelayed(this::immersive, 250);
        }
    }

    @Override public void onBackPressed() {
        if (view != null && view.goBack()) return;
        super.onBackPressed();
    }

    static final class Node {
        String id,parent,name,kind,role;
        int depth;
        float x,y,z;
        Node(String id,String parent,String kind,String role,int depth){
            this.id=id; this.parent=parent; this.kind=kind; this.role=role; this.depth=depth;
            this.name=".".equals(id)?"ARCHIE-DEMO":id.substring(id.lastIndexOf('/')+1);
        }
    }

    static final class LumenView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
        final ArrayList<Node> nodes=new ArrayList<>();
        final HashMap<String,Node> byId=new HashMap<>();
        final ArrayList<RectF> buttons=new ArrayList<>();
        final ArrayList<String> buttonIds=new ArrayList<>();
        final ScaleGestureDetector scale;
        float density;
        float yaw=.55f,pitch=-.28f,zoom=1f;
        float downX,downY,lastX,lastY;
        boolean dragging=false;
        String focus=".",selected=null;
        long lastTap=0;
        int profile=0;
        String[] profiles={"RADIAL","ESPIRAL","ANILLOS","CARPETAS"};
        Random rnd=new Random(7);

        LumenView(Context c){
            super(c);
            density=getResources().getDisplayMetrics().density;
            setBackgroundColor(Color.rgb(4,6,11));
            text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            buildDemo();
            layoutNodes();
            scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    zoom=Math.max(.45f,Math.min(2.5f,zoom*d.getScaleFactor()));
                    invalidate(); return true;
                }
            });
        }

        float dp(float v){return v*density;}
        int col(String s){
            if("dir".equals(s)) return Color.rgb(41,196,230);
            if(s.contains("Modelo")) return Color.rgb(239,107,168);
            if(s.contains("Conocimiento")) return Color.rgb(242,164,201);
            if(s.contains("Pruebas")) return Color.rgb(241,181,98);
            if(s.contains("Config")) return Color.rgb(85,215,180);
            if(s.contains("Seguridad")) return Color.rgb(83,215,182);
            return Color.rgb(119,116,255);
        }

        void add(String id,String parent,String kind,String role){
            int depth=".".equals(id)?0:id.split("/").length;
            Node n=new Node(id,parent,kind,role,depth); nodes.add(n); byId.put(id,n);
        }

        void buildDemo(){
            add(".","","dir","Raíz");
            String[] roots={"src","models","docs","tests","plugins","configs","data","assets"};
            for(String r:roots)add(r,".","dir","Estructura");
            String[][] dirs={
              {"src","cortex"},{"src","iris"},{"src","sentry"},{"src","forge"},
              {"src/iris","navigation"},{"src/iris","vision"},{"src/sentry","network"},{"src/sentry","devices"},
              {"models","bonsai"},{"models/bonsai","experts"},{"models","iris"},{"models/iris","adapters"},
              {"docs","architecture"},{"docs","research"},{"tests","unit"},{"tests","integration"},
              {"plugins","iot"},{"plugins","vision"},{"plugins","automation"},
              {"configs","profiles"},{"data","memory"},{"data","knowledge"},{"assets","materials"},{"assets","shaders"}
            };
            for(String[] d:dirs)add(d[0]+"/"+d[1],d[0],"dir","Estructura");
            String[][] files={
              {"src/main.cpp","src","Código"},{"src/cortex/cortex.cpp","src/cortex","Núcleo"},
              {"src/cortex/router.cpp","src/cortex","Núcleo"},{"src/cortex/memory.cpp","src/cortex","Memoria"},
              {"src/iris/navigation/navigation.cpp","src/iris/navigation","Percepción"},{"src/iris/navigation/field.cpp","src/iris/navigation","Percepción"},
              {"src/iris/vision/vision.cpp","src/iris/vision","Percepción"},{"src/iris/vision/grounding.cpp","src/iris/vision","Percepción"},
              {"src/sentry/network/network.rs","src/sentry/network","Seguridad"},{"src/sentry/network/router.rs","src/sentry/network","Seguridad"},
              {"src/sentry/devices/iot.rs","src/sentry/devices","IoT"},{"src/forge/builder.py","src/forge","Código"},
              {"src/forge/compiler.py","src/forge","Código"},{"docs/architecture/CORTEX.md","docs/architecture","Conocimiento"},
              {"docs/architecture/IRIS.md","docs/architecture","Conocimiento"},{"docs/architecture/SENTRY.md","docs/architecture","Conocimiento"},
              {"docs/research/navigation.md","docs/research","Conocimiento"},{"docs/research/clustering.md","docs/research","Conocimiento"},
              {"configs/archie.json","configs","Configuración"},{"configs/profiles/performance.json","configs/profiles","Configuración"},
              {"configs/profiles/research.json","configs/profiles","Configuración"},{"tests/unit/cortex.test.js","tests/unit","Pruebas"},
              {"tests/unit/iris.test.js","tests/unit","Pruebas"},{"tests/integration/system.test.js","tests/integration","Pruebas"},
              {"plugins/iot/plugin.json","plugins/iot","Integración"},{"plugins/vision/plugin.json","plugins/vision","Integración"},
              {"plugins/automation/plugin.json","plugins/automation","Integración"},{"assets/shaders/node.vert","assets/shaders","Shader"},
              {"assets/shaders/node.frag","assets/shaders","Shader"},{"assets/materials/materials.json","assets/materials","Material"}
            };
            for(String[] f:files)add(f[0],f[1],"file",f[2]);
            for(int i=1;i<=32;i++)add(String.format(Locale.US,"models/bonsai/experts/expert-%02d.json",i),"models/bonsai/experts","file","Modelo");
            for(int i=1;i<=18;i++){
                String d=String.format(Locale.US,"models/iris/adapters/task-%02d",i);
                add(d,"models/iris/adapters","dir","Estructura");
                add(d+"/adapter.json",d,"file","Modelo");
            }
            for(int i=1;i<=30;i++)add(String.format(Locale.US,"data/memory/memory-%03d.md",i),"data/memory","file","Memoria");
            for(int i=1;i<=30;i++)add(String.format(Locale.US,"data/knowledge/concept-%03d.txt",i),"data/knowledge","file","Conocimiento");
        }

        int hash(String s){
            int h=0x811c9dc5;
            for(int i=0;i<s.length();i++){h^=s.charAt(i); h*=0x01000193;}
            return h;
        }
        float r01(String s,int salt){
            int x=hash(s)+salt*0x9e3779b9;
            x^=x<<13; x^=x>>>17; x^=x<<5;
            return (x & 0x7fffffff)/(float)0x7fffffff;
        }

        void layoutNodes(){
            for(int i=0;i<nodes.size();i++){
                Node n=nodes.get(i);
                if(".".equals(n.id)){n.x=n.y=n.z=0;continue;}
                float a=r01(n.id,1)*(float)Math.PI*2f;
                float b=(r01(n.id,2)-.5f)*(float)Math.PI;
                float rr=70+n.depth*36+r01(n.id,3)*90;
                if(profile==1){
                    float t=i*.47f+n.depth*.8f;
                    n.x=(float)Math.cos(t)*(55+n.depth*28);
                    n.y=((i%17)-8)*12;
                    n.z=(float)Math.sin(t)*(55+n.depth*28);
                }else if(profile==2){
                    float rad=60+n.depth*55;
                    n.x=(float)Math.cos(a)*rad;
                    n.y=(n.depth-2.5f)*42+(float)Math.sin(b)*25;
                    n.z=(float)Math.sin(a)*rad;
                }else if(profile==3){
                    String[] gs={"src","models","docs","tests","plugins","configs","data","assets"};
                    String g=n.id.contains("/")?n.id.substring(0,n.id.indexOf('/')):n.id;
                    int gi=0; for(int k=0;k<gs.length;k++) if(gs[k].equals(g)) gi=k;
                    float ga=gi/(float)gs.length*(float)Math.PI*2f;
                    float cx=(float)Math.cos(ga)*180, cz=(float)Math.sin(ga)*180;
                    n.x=cx+(float)Math.cos(a)*rr*.45f;
                    n.y=(float)Math.sin(b)*rr*.55f;
                    n.z=cz+(float)Math.sin(a)*rr*.45f;
                }else{
                    n.x=(float)(Math.cos(a)*Math.cos(b)*rr);
                    n.y=(float)(Math.sin(b)*rr);
                    n.z=(float)(Math.sin(a)*Math.cos(b)*rr);
                }
            }
            invalidate();
        }

        boolean inFocus(Node n){
            if(".".equals(focus)) return true;
            return n.id.equals(focus)||n.id.startsWith(focus+"/");
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            int W=getWidth(),H=getHeight();
            float top=dp(66), left=dp(210), foot=dp(26);
            drawBackground(c,W,H);
            drawTop(c,W,top);
            drawSidebar(c,left,top,H-foot);
            drawFooter(c,W,H,foot);

            RectF stage=new RectF(left,top,W,H-foot);
            c.save(); c.clipRect(stage);
            drawGraph(c,stage);
            c.restore();

            if(selected!=null)drawInspector(c,W,H,top,foot);
        }

        void drawBackground(Canvas c,int W,int H){
            RadialGradient g=new RadialGradient(W*.62f,H*.5f,Math.max(W,H)*.55f,
                new int[]{Color.rgb(18,29,44),Color.rgb(7,11,18),Color.rgb(3,5,9)},
                new float[]{0,.5f,1},Shader.TileMode.CLAMP);
            p.setShader(g); c.drawRect(0,0,W,H,p); p.setShader(null);
        }

        void drawTop(Canvas c,int W,float top){
            p.setColor(Color.rgb(8,12,19)); c.drawRect(0,0,W,top,p);
            p.setColor(Color.rgb(28,37,51)); c.drawRect(0,top-dp(1),W,top,p);
            p.setColor(Color.rgb(26,54,82)); c.drawRoundRect(dp(12),dp(10),dp(54),dp(52),dp(9),dp(9),p);
            text.setColor(Color.WHITE); text.setTextSize(dp(22)); text.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("A",dp(26),dp(39),text);
            text.setTextSize(dp(19)); c.drawText("ARCHIE",dp(68),dp(31),text);
            text.setTypeface(Typeface.DEFAULT); text.setColor(Color.rgb(93,106,126)); text.setTextSize(dp(8)); c.drawText("LUMENFIELD / V4 · ANDROID NATIVO",dp(68),dp(46),text);

            buttons.clear();buttonIds.clear();
            float x=W-dp(300);
            drawButton(c,x,dp(12),dp(52),dp(38),"HOME","home");x+=dp(58);
            drawButton(c,x,dp(12),dp(52),dp(38),"ATRÁS","back");x+=dp(58);
            drawButton(c,x,dp(12),dp(72),dp(38),profiles[profile],"profile");x+=dp(78);
            drawButton(c,x,dp(12),dp(52),dp(38),"RESET","reset");
        }

        void drawButton(Canvas c,float x,float y,float w,float h,String label,String id){
            RectF r=new RectF(x,y,x+w,y+h);buttons.add(r);buttonIds.add(id);
            p.setColor(Color.rgb(12,18,28));c.drawRoundRect(r,dp(6),dp(6),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.rgb(36,55,76));c.drawRoundRect(r,dp(6),dp(6),p);p.setStyle(Paint.Style.FILL);
            text.setTextSize(dp(8));text.setColor(Color.rgb(120,160,185));text.setTypeface(Typeface.DEFAULT_BOLD);
            float tw=text.measureText(label);c.drawText(label,x+(w-tw)/2,y+h/2+dp(3),text);
        }

        void drawSidebar(Canvas c,float left,float top,float bottom){
            p.setColor(Color.rgb(7,11,18));c.drawRect(0,top,left,bottom,p);
            p.setColor(Color.rgb(28,37,51));c.drawRect(left-dp(1),top,left,bottom,p);
            text.setTypeface(Typeface.DEFAULT_BOLD);text.setTextSize(dp(8));text.setColor(Color.rgb(41,196,230));c.drawText("ORIGEN",dp(12),top+dp(22),text);
            p.setColor(Color.rgb(10,20,30));c.drawRoundRect(dp(10),top+dp(32),left-dp(10),top+dp(82),dp(7),dp(7),p);
            text.setColor(Color.WHITE);text.setTextSize(dp(11));c.drawText(focus.equals(".")?"ARCHIE-DEMO":trim(focus,20),dp(18),top+dp(52),text);
            text.setColor(Color.rgb(80,98,120));text.setTextSize(dp(7));c.drawText("DEMO INTEGRADA · SIN ARCHIVOS EXTERNOS",dp(18),top+dp(68),text);

            text.setTextSize(dp(8));text.setColor(Color.rgb(41,196,230));c.drawText("EXPLORADOR",dp(12),top+dp(108),text);
            ArrayList<Node> list=new ArrayList<>();
            for(Node n:nodes) if(n.parent.equals(focus)) list.add(n);
            float y=top+dp(128);
            for(int i=0;i<list.size()&&i<16;i++){
                Node n=list.get(i);
                p.setColor(n.id.equals(selected)?Color.rgb(15,30,43):Color.TRANSPARENT);
                c.drawRoundRect(dp(8),y-dp(13),left-dp(8),y+dp(9),dp(5),dp(5),p);
                p.setColor(col(n.kind.equals("dir")?"dir":n.role));c.drawCircle(dp(18),y-dp(3),dp(n.kind.equals("dir")?4:3),p);
                text.setTextSize(dp(8));text.setColor(Color.rgb(145,160,182));text.setTypeface(Typeface.DEFAULT);
                c.drawText(trim(n.name,25),dp(29),y,text); y+=dp(27);
            }
            if(list.isEmpty()){
                text.setColor(Color.rgb(84,100,120)); text.setTextSize(dp(8)); c.drawText("Sin hijos directos",dp(16),y,text);
            }
        }

        void drawFooter(Canvas c,int W,int H,float foot){
            p.setColor(Color.rgb(6,9,14));c.drawRect(0,H-foot,W,H,p);
            p.setColor(Color.rgb(28,37,51));c.drawRect(0,H-foot,W,H-foot+dp(1),p);
            text.setTextSize(dp(7));text.setColor(Color.rgb(83,101,124));text.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("LISTO · ANDROID NATIVO · DEMO INTEGRADA",dp(12),H-dp(9),text);
            String s=nodes.size()+" ELEMENTOS INDEXADOS";
            c.drawText(s,W-dp(12)-text.measureText(s),H-dp(9),text);
        }

        static final class P3{float x,y,z,s;Node n;}
        P3 project(Node n,RectF r){
            float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
            float x1=n.x*cy-n.z*sy,z1=n.x*sy+n.z*cy,y1=n.y*cp-z1*sp,z2=n.y*sp+z1*cp;
            float sc=650f/(720f+z2);
            P3 q=new P3();q.x=r.centerX()+x1*sc*zoom;q.y=r.centerY()+y1*sc*zoom;q.z=z2;q.s=sc;q.n=n;return q;
        }

        void drawGraph(Canvas c,RectF r){
            ArrayList<P3> ps=new ArrayList<>();
            for(Node n:nodes)if(inFocus(n))ps.add(project(n,r));
            HashMap<String,P3> mp=new HashMap<>();for(P3 q:ps)mp.put(q.n.id,q);
            p.setStrokeWidth(dp(.7f));
            for(P3 q:ps){
                P3 par=mp.get(q.n.parent); if(par==null)continue;
                p.setColor(Color.argb(80,50,96,125));c.drawLine(par.x,par.y,q.x,q.y,p);
            }
            Collections.sort(ps,(a,b)->Float.compare(b.z,a.z));
            for(P3 q:ps){
                Node n=q.n;float rad=dp((n.kind.equals("dir")?5.2f:3.5f)*Math.max(.6f,q.s));
                p.setColor(col(n.kind.equals("dir")?"dir":n.role));
                p.setShadowLayer(dp(n.id.equals(selected)?10:5),0,0,p.getColor());setLayerType(LAYER_TYPE_SOFTWARE,p);
                if(n.kind.equals("dir"))c.drawRect(q.x-rad,q.y-rad,q.x+rad,q.y+rad,p);else c.drawCircle(q.x,q.y,rad,p);
                p.clearShadowLayer();
                if(n.id.equals(selected)){
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.4f));p.setColor(Color.WHITE);c.drawCircle(q.x,q.y,rad+dp(5),p);p.setStyle(Paint.Style.FILL);
                }
                if(n.kind.equals("dir")&&n.depth<=2 || n.id.equals(selected)){
                    text.setTextSize(dp(7));text.setColor(Color.rgb(140,157,181));text.setTypeface(Typeface.DEFAULT);
                    c.drawText(trim(n.name,18),q.x+rad+dp(4),q.y+dp(2),text);
                }
            }
        }

        void drawInspector(Canvas c,int W,int H,float top,float foot){
            Node n=byId.get(selected);if(n==null)return;
            float width=dp(250),x=W-width,bottom=H-foot;
            p.setColor(Color.argb(245,10,15,24));c.drawRect(x,top,W,bottom,p);
            p.setColor(Color.rgb(35,50,68));c.drawRect(x,top,x+dp(1),bottom,p);
            text.setTypeface(Typeface.DEFAULT_BOLD);text.setTextSize(dp(8));text.setColor(Color.rgb(75,91,111));c.drawText("INSPECTOR",x+dp(16),top+dp(24),text);
            text.setTextSize(dp(15));text.setColor(Color.WHITE);c.drawText(trim(n.name,24),x+dp(16),top+dp(48),text);
            text.setTypeface(Typeface.DEFAULT);text.setTextSize(dp(8));text.setColor(Color.rgb(101,118,141));c.drawText(trim(n.id,35),x+dp(16),top+dp(66),text);

            info(c,x+dp(16),top+dp(88),"TIPO",n.kind.equals("dir")?"CARPETA":"ARCHIVO");
            info(c,x+dp(128),top+dp(88),"PROFUNDIDAD",String.valueOf(n.depth));
            info(c,x+dp(16),top+dp(135),"ROL",n.role);
            info(c,x+dp(128),top+dp(135),"HIJOS",String.valueOf(childCount(n.id)));

            if(n.kind.equals("dir")){
                RectF enter=new RectF(x+dp(16),top+dp(190),W-dp(16),top+dp(228));buttons.add(enter);buttonIds.add("enter");
                p.setColor(Color.rgb(11,31,43));c.drawRoundRect(enter,dp(6),dp(6),p);
                text.setTextSize(dp(9));text.setColor(Color.rgb(76,215,241));text.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText("ENTRAR EN CARPETA",enter.left+dp(18),enter.centerY()+dp(3),text);
            }
            text.setTextSize(dp(8));text.setColor(Color.rgb(80,96,118));c.drawText("TOCA EL MISMO NODO DOS VECES PARA ENTRAR",x+dp(16),bottom-dp(24),text);
        }

        void info(Canvas c,float x,float y,String k,String v){
            text.setTypeface(Typeface.DEFAULT_BOLD);text.setTextSize(dp(7));text.setColor(Color.rgb(70,85,104));c.drawText(k,x,y,text);
            text.setTextSize(dp(9));text.setColor(Color.rgb(200,214,231));c.drawText(trim(v,18),x,y+dp(17),text);
        }

        int childCount(String id){int k=0;for(Node n:nodes)if(n.parent.equals(id))k++;return k;}

        String trim(String s,int m){return s.length()<=m?s:s.substring(0,Math.max(1,m-1))+"…";}

        Node hit(float x,float y){
            float top=dp(66),left=dp(210),foot=dp(26);RectF r=new RectF(left,top,getWidth(),getHeight()-foot);
            Node best=null;float bd=99999;
            for(Node n:nodes)if(inFocus(n)){
                P3 q=project(n,r);float d=(float)Math.hypot(x-q.x,y-q.y);
                float rr=dp(n.kind.equals("dir")?12:10);
                if(d<rr&&d<bd){best=n;bd=d;}
            }
            return best;
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            if(scale.isInProgress())return true;
            float x=e.getX(),y=e.getY();
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=lastX=x;downY=lastY=y;dragging=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=x-lastX,dy=y-lastY;
                    if(Math.abs(x-downX)+Math.abs(y-downY)>dp(5))dragging=true;
                    if(x>dp(210)&&y>dp(66)&&x<getWidth()-dp(selected!=null?250:0)){
                        yaw+=dx*.006f;pitch=Math.max(-1.3f,Math.min(1.3f,pitch+dy*.006f));invalidate();
                    }
                    lastX=x;lastY=y;return true;
                case MotionEvent.ACTION_UP:
                    if(!dragging)handleTap(x,y);
                    return true;
            }
            return true;
        }

        void handleTap(float x,float y){
            for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).contains(x,y)){action(buttonIds.get(i));return;}
            float top=dp(66),left=dp(210);
            if(x<left&&y>top){
                ArrayList<Node> list=new ArrayList<>();for(Node n:nodes)if(n.parent.equals(focus))list.add(n);
                int idx=(int)((y-(top+dp(115)))/dp(27));
                if(idx>=0&&idx<list.size()){select(list.get(idx));return;}
            }
            Node n=hit(x,y);
            if(n!=null){
                long now=System.currentTimeMillis();
                if(selected!=null&&selected.equals(n.id)&&now-lastTap<500&&n.kind.equals("dir"))navigate(n.id);
                else select(n);
                lastTap=now;
            }else{selected=null;invalidate();}
        }

        void select(Node n){selected=n.id;invalidate();}
        void navigate(String id){Node n=byId.get(id);if(n==null||!n.kind.equals("dir"))return;focus=id;selected=null;invalidate();}
        void action(String id){
            if("home".equals(id)){focus=".";selected=null;}
            else if("back".equals(id))goBack();
            else if("reset".equals(id)){yaw=.55f;pitch=-.28f;zoom=1f;}
            else if("profile".equals(id)){profile=(profile+1)%profiles.length;layoutNodes();}
            else if("enter".equals(id)&&selected!=null)navigate(selected);
            invalidate();
        }

        boolean goBack(){
            if(selected!=null){selected=null;invalidate();return true;}
            if(!".".equals(focus)){
                Node n=byId.get(focus);focus=(n==null||n.parent==null||n.parent.isEmpty())?".":n.parent;invalidate();return true;
            }
            return false;
        }
    }
}
