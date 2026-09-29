'use strict';
const $ = id => document.getElementById(id);
const PRESETS = {
  openrouter:['OpenRouter','openai','https://openrouter.ai/api/v1'],
  openai:['OpenAI','openai','https://api.openai.com/v1'],
  deepseek:['DeepSeek','openai','https://api.deepseek.com/v1'],
  groq:['Groq','openai','https://api.groq.com/openai/v1'],
  cerebras:['Cerebras','openai','https://api.cerebras.ai/v1'],
  mistral:['Mistral','openai','https://api.mistral.ai/v1'],
  together:['Together AI','openai','https://api.together.xyz/v1'],
  anthropic:['Anthropic','anthropic','https://api.anthropic.com/v1'],
  gemini:['Google Gemini','gemini','https://generativelanguage.googleapis.com/v1beta'],
  ollama:['Ollama local','ollama','http://127.0.0.1:11434']
};
let state = {providers:[],chats:[],chatId:null,models:[],pending:null,editingId:null,drawer:false};
const unique = () => Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);
const native = (method,...args) => {
  if (!window.OmniNative || typeof window.OmniNative[method] !== 'function') {
    toast('Esta vista requiere instalar la app Android. No es un sitio web autónomo.');return false;
  }
  window.OmniNative[method](...args);return true;
};
function toast(message){$('notice').textContent=String(message);$('notice').classList.remove('hidden');}
function clearToast(){$('notice').classList.add('hidden');$('notice').textContent='';}
function active(){return state.chats.find(c=>c.id===state.chatId)||null;}
function save(){native('saveChats',JSON.stringify(state.chats));}
function newChat(){
  if(state.pending) return;
  const c={id:unique(),title:'Nueva conversación',providerId:state.providers[0]?.id||'',model:state.providers[0]?.model||'',system:'',temperature:0.7,maxTokens:2048,messages:[],created:Date.now(),updated:Date.now()};
  state.chats.unshift(c);state.chatId=c.id;state.models=[];save();render();drawer(false);clearToast();
}
function deleteChat(id){
  if(state.pending)return;
  if(!confirm('¿Eliminar esta conversación y sus mensajes?'))return;
  state.chats=state.chats.filter(c=>c.id!==id);
  if(state.chatId===id)state.chatId=state.chats[0]?.id||null;
  if(!state.chatId)return newChat();save();render();
}
function selectChat(id){if(state.pending)return;state.chatId=id;state.models=[];render();drawer(false);clearToast();}
function drawer(open){state.drawer=open;$('sidebar').classList.toggle('open',open);$('shade').classList.toggle('hidden',!open);}
function renderSidebar(){
  const list=$('chatList');list.replaceChildren();
  state.chats.forEach(c=>{
    const row=document.createElement('div');row.className='chat-entry'+(c.id===state.chatId?' active':'');
    const btn=document.createElement('button');btn.className='chat-entry';btn.style.cssText='padding:0;border:0;flex:1;min-width:0';btn.title=c.title;
    const icon=document.createElement('i');icon.textContent='◌';const title=document.createElement('span');title.textContent=c.title;btn.append(icon,title);
    btn.onclick=()=>selectChat(c.id);
    const del=document.createElement('button');del.className='trash';del.textContent='×';del.title='Eliminar';del.onclick=()=>deleteChat(c.id);
    row.append(btn,del);list.append(row);
  });
}
function renderProviders(){
  const select=$('providerSelect');select.replaceChildren(new Option('Seleccionar proveedor',''));
  state.providers.forEach(p=>select.add(new Option(p.name,p.id)));
  const c=active();select.value=c?.providerId||'';
}
function renderMessages(){
  const root=$('messages');root.replaceChildren();const c=active();
  if(!c||!c.messages.length){
    const empty=document.createElement('div');empty.className='empty';empty.innerHTML='<div class="hero-mark">✧</div><h1>Una app. Cualquier modelo.</h1><p>Configura un proveedor y empieza a hablar con su IA. Cambia de modelo cuando quieras, sin perder el chat.</p>';
    const btn=document.createElement('button');btn.className='primary';btn.textContent='Conectar proveedor →';btn.onclick=()=>modal();empty.append(btn);root.append(empty);return;
  }
  c.messages.forEach((msg,i)=>{
    const item=document.createElement('div');item.className='message '+msg.role;
    const avatar=document.createElement('div');avatar.className='avatar';avatar.textContent=msg.role==='user'?'◉':'✧';
    const wrap=document.createElement('div');wrap.className='bubble-wrap';
    const bubble=document.createElement('div');bubble.className='bubble';bubble.textContent=msg.content||'…';bubble.id='bubble-'+i;
    const meta=document.createElement('div');meta.className='meta';
    const info=document.createElement('span');info.textContent=msg.role==='user'?'Tú':(msg.provider||'IA')+(msg.model?' · '+msg.model:'');
    const copy=document.createElement('button');copy.className='copy';copy.textContent='Copiar';copy.onclick=()=>native('copy',msg.content);
    meta.append(info,copy);wrap.append(bubble,meta);
    if(msg.role==='assistant'){item.append(avatar,wrap)}else item.append(wrap,avatar);
    root.append(item);
  });
  root.scrollTop=root.scrollHeight;
}
function render(){
  if(!active())return newChat();
  const c=active();renderSidebar();renderProviders();renderMessages();
  $('chatTitle').textContent=c.title;
  $('modelInput').value=c.model||'';$('systemPrompt').value=c.system||'';
  $('temperature').value=c.temperature??0.7;$('maxTokens').value=c.maxTokens||2048;
  const busy=!!state.pending;$('send').classList.toggle('hidden',busy);$('stop').classList.toggle('hidden',!busy);
  $('providerSelect').disabled=busy;$('modelInput').disabled=busy;
  $('prompt').disabled=busy;$('loadModels').disabled=busy;
}
function modal(p){
  const edit=p||null;state.editingId=edit?.id||null;
  $('modalTitle').textContent=edit?'Editar proveedor':'Nuevo proveedor';$('preset').value='';
  $('pName').value=edit?.name||'';$('pAdapter').value=edit?.adapter||'openai';
  $('pBase').value=edit?.baseUrl||'';$('pKey').value='';$('pModel').value=edit?.model||'';
  $('pModelsPath').value=edit?.modelsPath||'';$('pChatPath').value=edit?.chatPath||'';
  $('pAuth').value=edit?.authMode||'auto';$('pStream').checked=edit?.stream!==false;$('pTemperature').checked=edit?.sendTemperature!==false;$('pTokenField').value=edit?.tokenField||'max_tokens';
  $('pHeaders').value='';$('clearKey').checked=false;$('clearHeaders').checked=false;
  $('keyHint').textContent=edit?.hasKey?'Clave almacenada · dejar vacío para conservar.':'Clave opcional para servidores sin autenticación.';
  $('deleteProvider').classList.toggle('hidden',!edit);$('providerModal').classList.remove('hidden');
}
function closeModal(){$('providerModal').classList.add('hidden');}
function saveProvider(){
  const data={id:state.editingId||unique(),name:$('pName').value.trim(),adapter:$('pAdapter').value,baseUrl:$('pBase').value.trim(),apiKey:$('pKey').value.trim(),model:$('pModel').value.trim(),modelsPath:$('pModelsPath').value.trim(),chatPath:$('pChatPath').value.trim(),authMode:$('pAuth').value,stream:$('pStream').checked,sendTemperature:$('pTemperature').checked,tokenField:$('pTokenField').value,clearKey:$('clearKey').checked,clearHeaders:$('clearHeaders').checked};
  if(!data.name||!data.baseUrl)return toast('Completa nombre y URL base.');
  const headers=$('pHeaders').value.trim();
  if(headers){try{const parsed=JSON.parse(headers);if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw Error();data.headers=JSON.stringify(parsed)}catch(e){return toast('Cabeceras inválidas: utiliza un objeto JSON.')}}
  if(data.clearHeaders)data.headers='';
  if(!data.apiKey&&!data.clearKey&&$('pKey').value.trim()===''&&!state.editingId)data.apiKey='';
  if(native('saveProvider',JSON.stringify(data))) {$('saveProvider').disabled=true;$('pKey').value='';}
}
function handleProviderSave(items){
  const id=state.editingId;state.providers=items;
  const c=active();if(c&&!c.providerId)c.providerId=id||items.at(-1)?.id||'';
  if(c&&c.providerId===id){c.model=items.find(p=>p.id===id)?.model||c.model;}
  save();closeModal();$('saveProvider').disabled=false;render();clearToast();
}
function listModels(){const c=active();if(!c?.providerId)return toast('Primero selecciona un proveedor.');state.models=[];$('loadModels').textContent='…';native('listModels',c.providerId);}
function sendMessage(){
  if(state.pending)return;
  const c=active();const input=$('prompt');const content=input.value.trim();
  if(!content)return;
  if(!c.providerId)return toast('Conecta un proveedor en ⚙ antes de enviar.');
  c.model=$('modelInput').value.trim();if(!c.model)return toast('Escribe o selecciona el ID de modelo.');
  c.system=$('systemPrompt').value.trim();c.temperature=Number($('temperature').value);c.maxTokens=Number($('maxTokens').value);
  const settings={temperature:c.temperature,maxTokens:c.maxTokens};
  c.messages.push({role:'user',content});
  if(c.title==='Nueva conversación')c.title=content.slice(0,49)+(content.length>49?'…':'');
  const messages=[...(c.system?[{role:'system',content:c.system}]:[]),...c.messages.slice(-40).map(m=>({role:m.role,content:m.content}))];
  const id=unique();const provider=state.providers.find(p=>p.id===c.providerId);
  c.messages.push({role:'assistant',content:'',provider:provider?.name||'IA',model:c.model});
  state.pending={requestId:id,chatId:c.id,index:c.messages.length-1};c.updated=Date.now();
  input.value='';resizePrompt();save();render();clearToast();
  native('chat',JSON.stringify({requestId:id,providerId:c.providerId,model:c.model,messages,settings}));
}
function handleDelta(id,text){
  const pending=state.pending;if(!pending||pending.requestId!==id)return;
  const c=state.chats.find(v=>v.id===pending.chatId);if(!c)return;
  c.messages[pending.index].content+=String(text);
  const bubble=$('bubble-'+pending.index);
  if(bubble){bubble.textContent=c.messages[pending.index].content;$('messages').scrollTop=$('messages').scrollHeight;}
}
function finish(id,error){
  const pending=state.pending;if(!pending||pending.requestId!==id)return;
  const c=state.chats.find(v=>v.id===pending.chatId);
  if(c){const m=c.messages[pending.index];if(m&&!m.content)m.content=error?'[Sin respuesta]':'[Respuesta vacía]';c.updated=Date.now();}
  state.pending=null;save();render();if(error)toast(error);
}
function resizePrompt(){const t=$('prompt');t.style.height='auto';t.style.height=Math.min(t.scrollHeight,135)+'px';}
function exportChat(){const c=active();if(c)native('shareChat',JSON.stringify(c,null,2));}
window.__nativeReceive=function(serialized){
  let packet;try{packet=JSON.parse(serialized)}catch(e){return}
  const {type,requestId,data}=packet;
  if(type==='bootstrap'){
    state.providers=Array.isArray(data.providers)?data.providers:[];
    state.chats=Array.isArray(data.chats)?data.chats:[];
    state.chatId=state.chats[0]?.id||null;
    if(!state.chatId)newChat();else render();
    if(data.error)toast('No se pudo leer almacenamiento cifrado: '+data.error);
    if(!state.providers.length)modal();
  } else if(type==='providers') {
    handleProviderSave(data);
  } else if(type==='models') {
    if(active()?.providerId!==requestId)return;
    state.models=Array.isArray(data)?data:[];$('modelList').replaceChildren();
    state.models.forEach(m=>$('modelList').append(new Option(m,m)));
    $('loadModels').textContent='↻';
    if(!state.models.length)toast('El catálogo está vacío. Introduce un modelo manualmente.');
    else {clearToast();toast(state.models.length+' modelos detectados. Escribe en el campo MODELO para filtrarlos.');}
  } else if(type==='delta')handleDelta(requestId,data);
  else if(type==='done')finish(requestId,null);
  else if(type==='error'){
    if(requestId.startsWith('chat:'))finish(requestId.slice(5),data.message||'Error del proveedor');
    else {$('saveProvider').disabled=false;$('loadModels').textContent='↻';toast(data.message||'Error inesperado');}
  }
};
window.__back=function(){
  if(!$('providerModal').classList.contains('hidden')){closeModal();return 'handled'}
  if(state.drawer){drawer(false);return 'handled'}
  if(!$('options').classList.contains('hidden')){$('options').classList.add('hidden');return 'handled'}
  return 'exit';
};
$('openSidebar').onclick=()=>drawer(true);$('closeSidebar').onclick=()=>drawer(false);$('shade').onclick=()=>drawer(false);
$('newChat').onclick=newChat;$('addProvider').onclick=()=>modal(state.providers.find(p=>p.id===active()?.providerId));$('addProviderSide').onclick=()=>modal();$('emptyAdd').onclick=()=>modal();
$('closeModal').onclick=closeModal;$('freshProvider').onclick=()=>modal();$('saveProvider').onclick=saveProvider;
$('deleteProvider').onclick=()=>{const id=state.editingId;if(!id||!confirm('¿Eliminar conexión?'))return;native('deleteProvider',id);state.chats.forEach(c=>{if(c.providerId===id)c.providerId=''});closeModal();save()};
$('preset').onchange=()=>{const p=PRESETS[$('preset').value];if(p){$('pName').value=p[0];$('pAdapter').value=p[1];$('pBase').value=p[2];}};
$('providerSelect').onchange=()=>{const c=active();c.providerId=$('providerSelect').value;const p=state.providers.find(v=>v.id===c.providerId);c.model=p?.model||'';state.models=[];$('modelList').replaceChildren();save();render();};
$('modelInput').onchange=()=>{const c=active();if(c){c.model=$('modelInput').value.trim();save()}};
$('loadModels').onclick=listModels;$('send').onclick=sendMessage;
$('stop').onclick=()=>{if(state.pending)native('cancel',state.pending.requestId);};
$('prompt').oninput=resizePrompt;
$('prompt').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();sendMessage();}};
$('toggleOptions').onclick=()=>$('options').classList.toggle('hidden');
$('systemPrompt').onchange=()=>{const c=active();if(c){c.system=$('systemPrompt').value;save()}};
$('temperature').onchange=()=>{const c=active();if(c){c.temperature=Number($('temperature').value);save()}};
$('maxTokens').onchange=()=>{const c=active();if(c){c.maxTokens=Number($('maxTokens').value);save()}};
$('export').onclick=exportChat;
