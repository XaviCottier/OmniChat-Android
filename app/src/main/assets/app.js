(()=>{'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const canvas=$('#graph'),ctx=canvas.getContext('2d',{alpha:false});
const C={dir:'#29c4e6',code:'#7774ff',docs:'#f2a4c9',tests:'#f1b562',config:'#55d7b4',model:'#ef6ba8',data:'#80a7bd'};
const S={yaw:.45,pitch:-.25,zoom:1,nodeSize:1,motion:.35,profile:'radial',maxNodes:180,showLinks:true,showLabels:true,showParticles:true,autoRotate:true,paused:false,filter:'all',query:'',focus:'.',selected:null,hover:null,time:0};
let nodes=[],byId=new Map(),children=new Map(),history=['.'],historyPos=0,favorites=new Set(JSON.parse(localStorage.getItem('lumen-favs')||'[]')),last=performance.now(),fpsAcc=0,fpsN=0,fpsStamp=performance.now(),drag=null,pinch=0;
function add(id,parent,kind,size,role){nodes.push({id,name:id==='.'?'ARCHIE-DEMO':id.split('/').pop(),parent,kind,size,depth:id==='.'?0:id.split('/').length,role})}
add('.','', 'dir',4096,'Raíz');
['src','models','docs','tests','plugins','configs','data','assets'].forEach(x=>add(x,'.','dir',4096,'Estructura'));
[['src','cortex'],['src','iris'],['src','sentry'],['src','forge'],['src/iris','navigation'],['src/iris','vision'],['src/sentry','network'],['src/sentry','devices'],['models','bonsai'],['models/bonsai','experts'],['models','iris'],['models/iris','adapters'],['docs','architecture'],['docs','research'],['tests','unit'],['tests','integration'],['plugins','iot'],['plugins','vision'],['plugins','automation'],['configs','profiles'],['data','memory'],['data','knowledge'],['assets','materials'],['assets','shaders']].forEach(([p,n])=>add(p+'/'+n,p,'dir',4096,'Estructura'));
[
['src/main.cpp','src','Código'],['src/cortex/cortex.cpp','src/cortex','Núcleo'],['src/cortex/router.cpp','src/cortex','Núcleo'],['src/cortex/memory.cpp','src/cortex','Memoria'],['src/iris/navigation/navigation.cpp','src/iris/navigation','Percepción'],['src/iris/navigation/field.cpp','src/iris/navigation','Percepción'],['src/iris/vision/vision.cpp','src/iris/vision','Percepción'],['src/iris/vision/grounding.cpp','src/iris/vision','Percepción'],['src/sentry/network/network.rs','src/sentry/network','Seguridad'],['src/sentry/network/router.rs','src/sentry/network','Seguridad'],['src/sentry/devices/iot.rs','src/sentry/devices','IoT'],['src/forge/builder.py','src/forge','Código'],['src/forge/compiler.py','src/forge','Código'],['docs/architecture/CORTEX.md','docs/architecture','Conocimiento'],['docs/architecture/IRIS.md','docs/architecture','Conocimiento'],['docs/architecture/SENTRY.md','docs/architecture','Conocimiento'],['docs/research/navigation.md','docs/research','Conocimiento'],['docs/research/clustering.md','docs/research','Conocimiento'],['configs/archie.json','configs','Configuración'],['configs/profiles/performance.json','configs/profiles','Configuración'],['configs/profiles/research.json','configs/profiles','Configuración'],['tests/unit/cortex.test.js','tests/unit','Pruebas'],['tests/unit/iris.test.js','tests/unit','Pruebas'],['tests/integration/system.test.js','tests/integration','Pruebas'],['plugins/iot/plugin.json','plugins/iot','Integración'],['plugins/vision/plugin.json','plugins/vision','Integración'],['plugins/automation/plugin.json','plugins/automation','Integración'],['assets/shaders/node.vert','assets/shaders','Shader'],['assets/shaders/node.frag','assets/shaders','Shader'],['assets/materials/materials.json','assets/materials','Material']
].forEach(([id,p,r],i)=>add(id,p,'file',700+i*83,r));
for(let i=1;i<=32;i++)add('models/bonsai/experts/expert-'+String(i).padStart(2,'0')+'.json','models/bonsai/experts','file',1200+i*29,'Modelo');
for(let i=1;i<=18;i++){let d='models/iris/adapters/task-'+String(i).padStart(2,'0');add(d,'models/iris/adapters','dir',4096,'Estructura');add(d+'/adapter.json',d,'file',900+i*37,'Modelo')}
for(let i=1;i<=30;i++)add('data/memory/memory-'+String(i).padStart(3,'0')+'.md','data/memory','file',500+i*17,'Memoria');
for(let i=1;i<=30;i++)add('data/knowledge/concept-'+String(i).padStart(3,'0')+'.txt','data/knowledge','file',420+i*13,'Conocimiento');
byId=new Map(nodes.map(n=>[n.id,n])); for(const n of nodes){if(!children.has(n.parent))children.set(n.parent,[]);children.get(n.parent).push(n)}
$('#indexed').textContent=nodes.length+' ELEMENTOS INDEXADOS';

function hash(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function rnd(s,k=0){let x=(hash(s)+k*2654435761)>>>0;x^=x<<13;x^=x>>>17;x^=x<<5;return(x>>>0)/4294967295}
function topGroup(n){return n.id==='.'?'.':n.id.split('/')[0]}
function pos(n,i){
 const a=rnd(n.id,1)*Math.PI*2,b=(rnd(n.id,2)-.5)*Math.PI,r=70+n.depth*34+rnd(n.id,3)*90;
 if(n.id==='.')return{x:0,y:0,z:0};
 if(S.profile==='spiral'){let t=i*.48+n.depth*.7;return{x:Math.cos(t)*(55+n.depth*28),y:(i%17-8)*12,z:Math.sin(t)*(55+n.depth*28)}}
 if(S.profile==='rings'){let rr=60+n.depth*55;return{x:Math.cos(a)*rr,y:(n.depth-2.5)*45+Math.sin(b)*25,z:Math.sin(a)*rr}}
 if(S.profile==='lobes'){let types=['dir','file'],side=n.kind==='dir'?-1:1;return{x:side*(110+rnd(n.id,4)*160),y:Math.sin(a)*r,z:Math.cos(a)*r}}
 if(S.profile==='folders'){let groups=['src','models','docs','tests','plugins','configs','data','assets'],g=Math.max(0,groups.indexOf(topGroup(n))),ga=g/groups.length*Math.PI*2,cx=Math.cos(ga)*180,cz=Math.sin(ga)*180;return{x:cx+Math.cos(a)*r*.45,y:Math.sin(b)*r*.55,z:cz+Math.sin(a)*r*.45}}
 return{x:Math.cos(a)*Math.cos(b)*r,y:Math.sin(b)*r,z:Math.sin(a)*Math.cos(b)*r}
}
function color(n){if(n.kind==='dir')return C.dir;let x=n.id.toLowerCase();if(x.includes('model')||x.includes('expert')||x.includes('adapter'))return C.model;if(x.includes('/docs')||x.endsWith('.md'))return C.docs;if(x.includes('test'))return C.tests;if(x.includes('config')||x.endsWith('.json'))return C.config;if(x.includes('/data/'))return C.data;return C.code}
function descendants(root){if(root==='.')return new Set(nodes.map(n=>n.id));let out=new Set([root]),q=[root];for(let i=0;i<q.length;i++)for(const n of children.get(q[i])||[]){out.add(n.id);if(n.kind==='dir')q.push(n.id)}return out}
function visible(){
 const scope=descendants(S.focus),q=S.query.trim().toLowerCase();let arr=nodes.filter(n=>scope.has(n.id)&&(S.filter==='all'||n.kind===S.filter)&&(!q||n.id.toLowerCase().includes(q)));
 arr.sort((a,b)=>a.depth-b.depth||a.id.localeCompare(b.id));return arr.slice(0,S.maxNodes)
}
function rebuildTree(){
 const t=$('#tree');t.textContent='';let scope=S.focus==='.'?nodes:nodes.filter(n=>n.id===S.focus||n.id.startsWith(S.focus+'/'));let q=S.query.trim().toLowerCase();
 scope.filter(n=>(S.filter==='all'||n.kind===S.filter)&&(!q||n.id.toLowerCase().includes(q))).slice(0,120).forEach(n=>{
  const r=document.createElement('div');r.className='treeRow '+n.kind+(S.selected===n.id?' active':'');r.style.paddingLeft=Math.max(0,(n.depth-(byId.get(S.focus)?.depth||0)))*10+'px';r.innerHTML='<span class="ico">'+(n.kind==='dir'?'▱':'●')+'</span><span>'+esc(n.name)+'</span>';r.onclick=()=>select(n.id);r.ondblclick=()=>{if(n.kind==='dir')navigate(n.id)};t.appendChild(r)
 }); $('#empty').classList.toggle('hidden',visible().length>0)
}
function esc(s){return String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
function navigate(id,push=true){if(!byId.has(id)||byId.get(id).kind!=='dir')return;S.focus=id;S.selected=null;if(push){history=history.slice(0,historyPos+1);history.push(id);historyPos=history.length-1}$('#fieldTitle').textContent=id==='.'?'ARCHIE-DEMO':id;$('#fieldSubtitle').textContent='Demo integrada · '+descendants(id).size+' elementos';closeInspector();rebuildTree();flash('FOCO · '+id)}
function select(id){S.selected=id;let n=byId.get(id);if(!n)return;$('#insName').textContent=n.name;$('#insPath').textContent=n.id;$('#insKind').textContent=n.kind==='dir'?'CARPETA':'ARCHIVO';$('#insSize').textContent=formatSize(n.size);$('#insDepth').textContent=n.depth;$('#insRole').textContent=n.role;$('#enterBtn').style.display=n.kind==='dir'?'block':'none';$('#preview').textContent=n.kind==='dir'?'Carpeta '+n.id+'\n'+(children.get(n.id)||[]).length+' elementos directos.':preview(n);$('#note').value=localStorage.getItem('lumen-note:'+n.id)||'';$('#nodeFavBtn').textContent=(favorites.has(id)?'★':'☆')+' Favorito';$('#inspector').classList.add('open');rebuildTree()}
function preview(n){if(n.id.endsWith('.json'))return '{\n  "demo": true,\n  "path": "'+n.id+'",\n  "source": "Android standalone"\n}';if(n.id.endsWith('.md'))return '# '+n.name+'\n\nDocumento de demostración ARCHIE Lumenfield V4.';if(n.id.endsWith('.rs'))return 'pub fn '+safeName(n.name)+'() {\n    // Sentry / Rust demo\n}';if(n.id.endsWith('.py'))return 'def '+safeName(n.name)+'():\n    return "ARCHIE"';return '// '+n.id+'\n\nvoid main() { /* demo */ }'}
function safeName(s){return s.replace(/\W+/g,'_').replace(/^\d/,'_$&')}
function formatSize(v){return v<1024?v+' B':(v/1024).toFixed(1)+' KB'}
function closeInspector(){$('#inspector').classList.remove('open')}
function toggleFav(id){if(!id)return;favorites.has(id)?favorites.delete(id):favorites.add(id);localStorage.setItem('lumen-favs',JSON.stringify([...favorites]));$('#favBtn').textContent=favorites.has(id)?'★':'☆';if(S.selected)$('#nodeFavBtn').textContent=(favorites.has(id)?'★':'☆')+' Favorito'}

function resize(){let r=canvas.getBoundingClientRect(),d=Math.min(devicePixelRatio||1,2);let w=Math.max(1,Math.round(r.width*d)),h=Math.max(1,Math.round(r.height*d));if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}ctx.setTransform(d,0,0,d,0,0);return{w:r.width,h:r.height}}
function project(p,w,h){let cy=Math.cos(S.yaw),sy=Math.sin(S.yaw),cp=Math.cos(S.pitch),sp=Math.sin(S.pitch),x1=p.x*cy-p.z*sy,z1=p.x*sy+p.z*cy,y1=p.y*cp-z1*sp,z2=p.y*sp+z1*cp,scale=650/(720+z2);return{x:w/2+x1*scale*S.zoom,y:h/2+y1*scale*S.zoom,z:z2,s:scale}}
function render(now){
 const dt=Math.min(.05,(now-last)/1000);last=now;if(!S.paused){S.time+=dt;if(S.autoRotate&&!drag)S.yaw+=dt*.08*S.motion}
 const {w,h}=resize(),grad=ctx.createRadialGradient(w*.52,h*.48,0,w*.52,h*.48,Math.max(w,h)*.65);grad.addColorStop(0,'#111d2a');grad.addColorStop(.48,'#080d16');grad.addColorStop(1,'#03050a');ctx.fillStyle=grad;ctx.fillRect(0,0,w,h);
 if(S.showParticles){ctx.fillStyle='#2d5970';for(let i=0;i<80;i++){let x=rnd('p'+i,1)*w,y=rnd('p'+i,2)*h,a=.08+.18*(.5+.5*Math.sin(S.time*.5+i));ctx.globalAlpha=a;ctx.fillRect(x,y,1,1)}ctx.globalAlpha=1}
 let arr=visible(),mapped=arr.map((n,i)=>{let p=pos(n,i);if(S.motion&&!S.paused){let amp=Math.min(10,3+S.motion*5);p={x:p.x+Math.sin(S.time*.7+i)*amp,y:p.y+Math.cos(S.time*.55+i*.8)*amp*.6,z:p.z+Math.sin(S.time*.4+i*.3)*amp}}return{n,p,q:project(p,w,h)}});let map=new Map(mapped.map(x=>[x.n.id,x]));
 if(S.showLinks){ctx.lineWidth=.65;ctx.globalAlpha=.24;for(const x of mapped){let p=map.get(x.n.parent);if(!p)continue;ctx.strokeStyle=x.n.kind==='dir'?'#1b8fae':'#44477a';ctx.beginPath();ctx.moveTo(p.q.x,p.q.y);ctx.lineTo(x.q.x,x.q.y);ctx.stroke()}ctx.globalAlpha=1}
 mapped.sort((a,b)=>b.q.z-a.q.z);for(const x of mapped){let n=x.n,q=x.q,r=(n.kind==='dir'?5.8:3.9)*S.nodeSize*Math.max(.55,q.s),sel=S.selected===n.id,fav=favorites.has(n.id),col=color(n);ctx.save();ctx.globalAlpha=Math.max(.32,1-(q.z+300)/1100);ctx.shadowColor=col;ctx.shadowBlur=sel?22:8;ctx.fillStyle=col;if(n.kind==='dir'){ctx.translate(q.x,q.y);ctx.rotate(Math.PI/4);ctx.fillRect(-r,-r,r*2,r*2);ctx.rotate(-Math.PI/4);ctx.translate(-q.x,-q.y)}else{ctx.beginPath();ctx.arc(q.x,q.y,r,0,Math.PI*2);ctx.fill()}if(sel){ctx.strokeStyle='#fff';ctx.lineWidth=1.5;ctx.beginPath();ctx.arc(q.x,q.y,r+5,0,Math.PI*2);ctx.stroke()}if(fav){ctx.fillStyle='#ffd47b';ctx.font='10px sans-serif';ctx.fillText('★',q.x+r+3,q.y-r-2)}if(S.showLabels&&(sel||n.kind==='dir'&&n.depth<=2||S.zoom>1.45)){ctx.shadowBlur=0;ctx.globalAlpha=.85;ctx.fillStyle='#8fa0b5';ctx.font=(sel?'10px':'8px')+' sans-serif';ctx.fillText(n.name,q.x+r+5,q.y+3)}ctx.restore();x.hit={x:q.x,y:q.y,r:Math.max(11,r+4)}}canvas._mapped=mapped;$('#nodeCount').textContent=arr.length+' NODOS';
 fpsAcc+=1/dt;fpsN++;if(now-fpsStamp>600){$('#fps').textContent=Math.round(fpsAcc/fpsN)+' FPS';fpsAcc=0;fpsN=0;fpsStamp=now}
 requestAnimationFrame(render)
}
function hit(x,y){let m=canvas._mapped||[],best=null,d=1e9;for(const a of m){if(!a.hit)continue;let dd=Math.hypot(x-a.hit.x,y-a.hit.y);if(dd<a.hit.r&&dd<d){best=a.n;d=dd}}return best}

canvas.addEventListener('pointerdown',e=>{canvas.setPointerCapture(e.pointerId);drag={x:e.offsetX,y:e.offsetY,moved:false}});
canvas.addEventListener('pointermove',e=>{if(!drag)return;let dx=e.offsetX-drag.x,dy=e.offsetY-drag.y;if(Math.abs(dx)+Math.abs(dy)>2)drag.moved=true;S.yaw+=dx*.007;S.pitch=Math.max(-1.35,Math.min(1.35,S.pitch+dy*.007));drag.x=e.offsetX;drag.y=e.offsetY});
canvas.addEventListener('pointerup',e=>{if(drag&&!drag.moved){let n=hit(e.offsetX,e.offsetY);if(n)select(n.id)}drag=null});
canvas.addEventListener('wheel',e=>{e.preventDefault();S.zoom=Math.max(.5,Math.min(2.5,S.zoom*Math.exp(-e.deltaY*.001)));$('#zoomSet').value=S.zoom;$('#zoomOut').textContent=S.zoom.toFixed(2)},{passive:false});
canvas.addEventListener('touchstart',e=>{if(e.touches.length===2)pinch=Math.hypot(e.touches[0].clientX-e.touches[1].clientX,e.touches[0].clientY-e.touches[1].clientY)},{passive:true});
canvas.addEventListener('touchmove',e=>{if(e.touches.length===2&&pinch){let d=Math.hypot(e.touches[0].clientX-e.touches[1].clientX,e.touches[0].clientY-e.touches[1].clientY);S.zoom=Math.max(.5,Math.min(2.5,S.zoom*d/pinch));pinch=d;$('#zoomSet').value=S.zoom;$('#zoomOut').textContent=S.zoom.toFixed(2)}},{passive:true});

function flash(t){$('#status').textContent=t;clearTimeout(flash.t);flash.t=setTimeout(()=>$('#status').textContent='LISTO · DEMO INTEGRADA',1800)}
$('#searchInput').oninput=e=>{S.query=e.target.value;rebuildTree()};$$('.filters button').forEach(b=>b.onclick=()=>{$$('.filters button').forEach(x=>x.classList.remove('active'));b.classList.add('active');S.filter=b.dataset.filter;rebuildTree()});
$('#sourceBtn').onclick=$('#openBtn').onclick=()=>navigate('.');
$('#homeBtn').onclick=()=>navigate('.');
$('#backBtn').onclick=()=>{if(historyPos>0){historyPos--;navigate(history[historyPos],false)}};
$('#forwardBtn').onclick=()=>{if(historyPos<history.length-1){historyPos++;navigate(history[historyPos],false)}};
$('#refreshBtn').onclick=()=>{S.yaw+=.35;S.pitch=-.2;flash('CAMPO REORDENADO')};
$('#fitBtn').onclick=()=>{S.zoom=1;S.yaw=.45;S.pitch=-.25;$('#zoomSet').value=1;$('#zoomOut').textContent='1.00';flash('CAMPO REENCUADRADO')};
$('#favBtn').onclick=()=>toggleFav(S.selected);
$('#pauseBtn').onclick=()=>{S.paused=!S.paused;$('#pauseBtn').textContent=S.paused?'▶':'Ⅱ';flash(S.paused?'PAUSA':'MOVIMIENTO ACTIVO')};
$('#shotBtn').onclick=()=>{try{let data=canvas.toDataURL('image/png');if(window.Android?.savePng)Android.savePng(data);else{let a=document.createElement('a');a.href=data;a.download='ARCHIE-Lumenfield.png';a.click()}flash('CAPTURA GUARDADA')}catch(e){flash('CAPTURA NO DISPONIBLE')}};
$('#settingsBtn').onclick=()=>{$('#settings').classList.add('open');closeInspector()};$('#closeSettings').onclick=()=>$('#settings').classList.remove('open');$('#closeInspector').onclick=closeInspector;
$('#enterBtn').onclick=()=>{if(S.selected)navigate(S.selected)};$('#centerBtn').onclick=()=>{S.zoom=1.55;flash('NODO CENTRADO')};$('#nodeFavBtn').onclick=()=>toggleFav(S.selected);
$('#saveNote').onclick=()=>{if(S.selected){localStorage.setItem('lumen-note:'+S.selected,$('#note').value);flash('NOTA GUARDADA')}};
$('#profile').onchange=e=>{S.profile=e.target.value;$('#profileLabel').textContent=e.target.options[e.target.selectedIndex].text.toUpperCase();flash('PERFIL · '+e.target.options[e.target.selectedIndex].text)};
$('#nodeSize').oninput=e=>{S.nodeSize=+e.target.value;$('#sizeOut').textContent=S.nodeSize.toFixed(2)};$('#motion').oninput=e=>{S.motion=+e.target.value;$('#motionOut').textContent=S.motion.toFixed(2)};$('#zoomSet').oninput=e=>{S.zoom=+e.target.value;$('#zoomOut').textContent=S.zoom.toFixed(2)};$('#maxNodes').onchange=e=>{S.maxNodes=+e.target.value;rebuildTree()};$('#showLinks').onchange=e=>S.showLinks=e.target.checked;$('#showLabels').onchange=e=>S.showLabels=e.target.checked;$('#showParticles').onchange=e=>S.showParticles=e.target.checked;$('#autoRotate').onchange=e=>S.autoRotate=e.target.checked;
$('#resetVisual').onclick=()=>{Object.assign(S,{yaw:.45,pitch:-.25,zoom:1,nodeSize:1,motion:.35,profile:'radial',maxNodes:180,showLinks:true,showLabels:true,showParticles:true,autoRotate:true});$('#profile').value='radial';$('#profileLabel').textContent='NÚCLEO RADIAL';$('#nodeSize').value=1;$('#sizeOut').textContent='1.00';$('#motion').value=.35;$('#motionOut').textContent='.35';$('#zoomSet').value=1;$('#zoomOut').textContent='1.00';$('#maxNodes').value='180';$('#showLinks').checked=$('#showLabels').checked=$('#showParticles').checked=$('#autoRotate').checked=true;flash('VISUAL RESTABLECIDO')};
const types=[['Carpetas',C.dir],['Código',C.code],['Docs',C.docs],['Tests',C.tests],['Config',C.config],['Modelo',C.model]];$('#typeLegend').innerHTML=types.map(x=>'<div><span class="dot" style="background:'+x[1]+'"></span>'+x[0]+'</div>').join('');
rebuildTree();requestAnimationFrame(render);flash('ANDROID STANDALONE · '+nodes.length+' ELEMENTOS');
})();