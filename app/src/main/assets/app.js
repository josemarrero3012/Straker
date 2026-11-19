(function initStraker() {
'use strict';
const L = Ledger, key = 'straker.ledger.v1';
const main = document.getElementById('main'), nav = document.getElementById('nav'), overlay = document.getElementById('overlay');
const tabs = {home:['Inicio','home'],bets:['Apuestas','ticket'],banks:['Bancas','wallet'],moves:['Movimientos','swap']};
const paths = {
 home:'M3 10l9-7 9 7v10h-6v-6H9v6H3z', ticket:'M3 7h18v4a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4z M15 8v1m0 3v1m0 3v1',
 wallet:'M3 7l13-4 2 4M3 7h17v13H3z M20 11h-6v5h6 M17 13.5h.1',
 swap:'M3 7h16l-4-4m4 4-4 4M21 17H5l4 4m-4-4 4-4', plus:'M12 5v14M5 12h14',
 arrow:'M5 12h14m-6-6 6 6-6 6',chevron:'m9 5 7 7-7 7',close:'m6 6 12 12M18 6 6 18',
 trend:'m3 17 6-6 4 4 8-10m-6 0h6v6',clock:'M12 3a9 9 0 1 0 .01 0M12 7v6l4 2',
 flag:'M5 21V4m0 0c6-4 9 4 15 0v10c-7 4-9-4-15 0',down:'M12 3v13m-6-6 6 6 6-6M4 20h16',
 up:'M12 17V4m-6 6 6-6 6 6M4 20h16',bank:'m2 8 10-5 10 5H2zm2 12h16M6 10v7m6-7v7m6-7v7',
 ball:'M12 2a10 10 0 1 0 .01 0M11 8l5 2-1 5-5 1-3-4zM11 8l-1-5m6 7 5-1m-6 6 3 4m-8-3-2 4m-1-8-4-1',
};
function icon(name) { return `<svg class="icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${paths[name]}"/></svg>`; }
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const eur = new Intl.NumberFormat('es-ES',{style:'currency',currency:'EUR'});
const money = v => eur.format(v/100), signed = v => (v>0?'+':'') + money(v);
const date = (v, full=false) => new Intl.DateTimeFormat('es-ES',full ? {day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'} : {day:'numeric',month:'short'}).format(new Date(v));
const oddsText = n => (n/1000).toLocaleString('es-ES',{minimumFractionDigits:2,maximumFractionDigits:3});
const button = (action, label, symbol, style='primary') => `<button type="button" class="btn ${style}" data-action="${action}">${icon(symbol)}${label}</button>`;
const link = (action,label) => `<button type="button" class="textbtn" data-action="${action}">${label}${icon('chevron')}</button>`;
const emptyPanel = (symbol,title,description,action='',label='') => `<div class="emptypanel"><span class="emptyicon">${icon(symbol)}</span><div><strong>${title}</strong><p>${description}</p>${action?`<button class="empty-action" data-action="${action}">${label} →</button>`:''}</div></div>`;
const options = choices => choices.map(([value,text])=>`<option value="${esc(value)}">${esc(text)}</option>`).join('');
const newest = list => [...list].reverse();
let s;
try {
  const raw = localStorage.getItem(key);
  s = raw ? JSON.parse(raw) : L.empty();
  if(s.version !== 1 || !['accounts','bets','moves'].every(k => Array.isArray(s[k]))) throw Error();
} catch {
  main.innerHTML='<h1>Datos inaccesibles</h1><p>No se ha podido abrir el registro local. Cierra y vuelve a abrir Straker.</p>';
  return;
}
let tab='home', betFilter='all', moveFilter='all', restoreFocus=null, timer;
const accountName = id => s.accounts.find(a=>a.id===id)?.name || 'Exterior';
const accountChoices = (books=false) => options(s.accounts.filter(a=>!books||a.type==='book').map(a=>[a.id,`${a.name} · ${money(L.balances(s)[a.id])}`]));
function notify(msg) {
  clearTimeout(timer); const t=document.getElementById('toast'); t.textContent=msg;
  timer=setTimeout(()=>{t.textContent='';},2600);
}
function betRow(b) {
 return `<button class="row" data-action="bet-detail" data-id="${esc(b.id)}"><span class="iconcell">${icon(b.sport==='Fútbol'?'ball':'ticket')}</span><span class="row-main"><strong>${esc(b.event)}</strong><span class="sub">${esc(accountName(b.account))} · Cuota ${oddsText(b.odds)}</span></span><span class="trailing"><strong>${money(b.stake)}</strong><span class="chip ${b.status}">${L.statuses[b.status]}</span></span></button>`;
}
function moveRow(m) {
 const symbol = !m.from?'down':!m.to?'up':'swap';
 return `<button class="row" data-action="move-detail" data-id="${esc(m.id)}"><span class="iconcell">${icon(symbol)}</span><span class="row-main"><strong>${L.kinds[m.kind]}</strong><span class="sub">${m.from?esc(accountName(m.from)):'Exterior'} → ${m.to?esc(accountName(m.to)):'Exterior'}</span></span><span class="trailing"><strong class="${!m.from?'positive':!m.to?'negative':''}">${!m.from?'+':!m.to?'−':''}${money(m.amount)}</strong><span class="sub">${date(m.at)}</span></span></button>`;
}
function bankCard(a) {
 return `<button class="account-card" data-action="bank-detail" data-id="${esc(a.id)}"><span class="account-top"><span class="initials">${esc(a.name.slice(0,2).toLocaleUpperCase('es'))}</span>${icon('chevron')}</span><strong>${esc(a.name)}</strong><span class="micro">${a.type==='book'?esc(a.bookmaker):'Cuenta bancaria'}</span><span class="metric-value">${money(L.balances(s)[a.id])}</span></button>`;
}
const list = (items, renderer) => `<div class="list">${items.map(renderer).join('')}</div>`;
const head = (title) => `<div class="pagehead"><div><div class="date">${new Intl.DateTimeFormat('es-ES',{weekday:'long',day:'numeric',month:'long'}).format(new Date())}</div><h1>${title}</h1></div><span class="visual-mark" aria-hidden="true"><i></i><i></i><i></i></span></div>`;
const section = (title, body, action='',text='Ver todas') => `<section class="section"><div class="sectionhead"><h2>${title}</h2>${action?link(action,text):''}</div>${body}</section>`;
const noBanks = () => emptyPanel('wallet','Tu primera banca','Asocia una casa de apuestas y anota el saldo con el que empiezas.','new-bank','Crear banca');
const noBets = () => emptyPanel('ticket','Aún no hay apuestas','Las apuestas que registres aparecerán aquí.');
const filterStrip = (items, current, group) => `<div class="tabstrip" aria-label="Filtrar">${Object.entries(items).map(([value,label])=>`<button class="filter ${value===current?'on':''}" aria-pressed="${value===current}" data-action="${group}" data-id="${esc(value)}">${esc(label)}</button>`).join('')}</div>`;
function home() {
 const stats=L.summary(s), n=s.accounts.length, count=s.bets.length;
 const pitch=`<svg class="pitch" viewBox="0 0 220 200" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.4"><rect x="14" y="8" width="185" height="180" rx="4"/><path d="M14 98h185M60 8v38h94V8M81 8v16h51V8M60 188v-38h94v38M81 188v-16h51v16"/><circle cx="106" cy="98" r="27"/><circle cx="106" cy="98" r="2" fill="currentColor"/><path d="M84 46q22 24 45 0m-45 104q22-24 45 0"/></svg>`;
 return head('Mi panel')+`<div class="overview"><section class="hero">${pitch}<div class="hero-body"><div class="hero-label">${icon('wallet')} Saldo disponible</div><strong class="amount">${money(stats.total)}</strong><div class="hero-sub">Total en tus cuentas</div></div><div class="hero-foot"><span>${icon('bank')}${n} ${n===1?'cuenta':'cuentas'}</span><span>${icon('ticket')}${count} ${count===1?'apuesta':'apuestas'}</span></div></section><div class="metrics"><div class="metric"><div class="metric-label">${icon('trend')} Resultado neto</div><span class="metric-value ${stats.profit>0?'positive':stats.profit<0?'negative':''}">${signed(stats.profit)}</span></div><div class="metric"><div class="metric-label">${icon('clock')} En juego</div><span class="metric-value">${money(stats.risk)}</span></div></div></div><div class="actions">${button('new-bet','Nueva apuesta','plus')}${button('new-move','Movimiento','swap','secondary')}</div><div class="twocol">${section('Últimas apuestas', count?list(newest(s.bets).slice(0,4),betRow):noBets(),count?'to-bets':'')}${section('Mis bancas',n?`<div class="account-grid">${newest(s.accounts).slice(0,2).map(bankCard).join('')}</div>`:noBanks(),n?'to-banks':'')}</div>`;
}
function render() {
 nav.innerHTML=Object.entries(tabs).map(([k,[name,i]])=>`<button class="navbtn ${k===tab?'active':''}" ${k===tab?'aria-current="page"':''} data-action="to-${k}">${icon(i)}${name}</button>`).join('');
 if(tab==='home') main.innerHTML=home();
 if(tab==='bets') {
   const bets=newest(s.bets).filter(b=>betFilter==='all'||b.status===betFilter);
   main.innerHTML=head('Apuestas')+button('new-bet','Nueva apuesta','plus')+filterStrip({all:'Todas',pending:'Pendientes',won:'Ganadas',lost:'Perdidas',void:'Nulas',cash:'Cash out'},betFilter,'filter-bet')+(bets.length?list(bets,betRow):s.bets.length?emptyPanel('ticket','Sin coincidencias','No hay apuestas con este estado.'):noBets());
 }
 if(tab==='banks') {
   const books=s.accounts.filter(a=>a.type==='book'), banks=s.accounts.filter(a=>a.type==='bank');
   const cards = arr=>`<div class="account-grid">${arr.map(bankCard).join('')}</div>`;
   main.innerHTML=head('Mis bancas')+button('new-bank','Nueva cuenta','plus')+section('Casas de apuestas',books.length?cards(books):emptyPanel('wallet','Sin bancas de apuestas','Crea una banca con el nombre de tu casa de apuestas.'))+(banks.length?section('Cuentas bancarias',cards(banks)):'');
 }
 if(tab==='moves') {
   const moves=newest(s.moves).filter(m=>moveFilter==='all'||m.kind===moveFilter||(moveFilter==='deposit'&&m.kind==='opening'));
   main.innerHTML=head('Movimientos')+button('new-move','Nuevo movimiento','plus')+filterStrip({all:'Todos',deposit:'Ingresos',internal:'Traspasos',transfer:'Transferencias',withdrawal:'Retiradas'},moveFilter,'filter-move')+(moves.length?list(moves,moveRow):emptyPanel('swap','Sin movimientos',s.moves.length?'No hay movimientos de este tipo.':'Aquí verás las entradas, salidas y cambios entre cuentas.'));
 }
}
function open(title,content) {
 if(overlay.hidden) restoreFocus=document.activeElement;
 overlay.innerHTML=`<section class="sheet" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><div class="panehead"><h2 id="dialog-title" tabindex="-1">${title}</h2><button class="close" aria-label="Cerrar" data-action="close">${icon('close')}</button></div>${content}</section>`;
 overlay.hidden=false; document.body.classList.add('modal-open'); document.getElementById('shell').setAttribute('aria-hidden','true'); document.getElementById('dialog-title').focus();
}
function close() {
 overlay.hidden=true;overlay.innerHTML='';document.body.classList.remove('modal-open');document.getElementById('shell').removeAttribute('aria-hidden');
 if(restoreFocus?.isConnected)restoreFocus.focus(); restoreFocus=null;
}
const field = (title, html) => `<label>${title}${html}</label>`;
const input = (name, placeholder, attrs='') => `<input name="${name}" placeholder="${placeholder}" ${attrs}>`;
const numeric = (name,placeholder,req=true) => input(name,placeholder,`inputmode="decimal" autocomplete="off" maxlength="16" ${req?'required':''}`);
const select = (name, html) => `<select name="${name}" required>${html}</select>`;
const form = (kind, body, submit, attrs='') => `<form data-form="${kind}" ${attrs}>${body}<p class="error" role="alert"></p><button class="btn primary" type="submit">${submit}${icon('arrow')}</button></form>`;
const facts = rows => `<dl class="facts">${rows.map(([k,v])=>`<div><dt>${k}</dt><dd>${v}</dd></div>`).join('')}</dl>`;
function newBank(next='') {
 const bookOnly=next==='bet';
 open(bookOnly?'Crea tu primera banca':'Nueva cuenta',form('bank',
 (next?`<p class="hint">${bookOnly?'Para registrar una apuesta, elige una casa y anota tu saldo inicial.':'Crea una cuenta para registrar el movimiento.'}</p>`:'')+
 field('Tipo de cuenta',select('type',options(bookOnly?[['book','Banca de apuestas']]:[['book','Banca de apuestas'],['bank','Cuenta bancaria']])) )+
 field('Nombre de la cuenta',input('name','Ej. Banca principal','maxlength="40" required'))+
 `<div data-part="bookmaker">${field('Casa de apuestas',input('bookmaker','Nombre de la casa','maxlength="40" required'))}</div>`+
 field('Saldo inicial (€)',numeric('amount','0,00',false)),next?'Crear y continuar':'Crear cuenta',`data-next="${next}"`));
}
function newBet(preselected='') {
 if(!s.accounts.some(a=>a.type==='book')) return newBank('bet');
 open('Nueva apuesta',form('bet',
 field('Evento',input('event','Ej. Valencia – Sevilla','maxlength="80" required'))+
 field('Selección (opcional)',input('pick','Ej. Más de 2,5 goles','maxlength="100"'))+
 field('Deporte',select('sport',options(['Fútbol','Baloncesto','Tenis','Esports','Multideporte','Otro'].map(v=>[v,v]))))+
 field('Banca',select('account',accountChoices(true)))+
 `<div class="pair">${field('Importe (€)',numeric('stake','10,00'))}${field('Cuota decimal',numeric('odds','1,85'))}</div>`+
 '<p class="hint">Se descontará el importe de la banca y la apuesta quedará pendiente.</p>','Registrar apuesta'));
 if(preselected) overlay.querySelector('[name="account"]').value=preselected;
}
function newMove(preselected='') {
 if(!s.accounts.length)return newBank('move');
 open('Nuevo movimiento',form('move',field('Tipo de movimiento',select('kind',options([['deposit','Ingreso'],['internal','Traspaso'],['transfer','Transferencia'],['withdrawal','Retirada']])) )+
 '<p class="hint" data-part="explain"></p>'+
 `<div data-part="from">${field('Cuenta de origen',select('from',accountChoices()))}</div><div data-part="to">${field('Cuenta de destino',select('to',accountChoices()))}</div>`+
 field('Importe (€)',numeric('amount','0,00'))+field('Concepto (opcional)',input('note','Ej. Recarga de banca','maxlength="120"')),'Registrar movimiento'));
 if(preselected)overlay.querySelector('[name="to"]').value=preselected;
 updateFields();
}
function betDetail(id) {
 const b=s.bets.find(v=>v.id===id);if(!b)return;
 open('Detalle de apuesta',`<h3 class="detail-title">${esc(b.event)}</h3><span class="sub">${esc(b.sport)} · ${date(b.at,true)}</span>${facts([['Banca',esc(accountName(b.account))],...(b.pick?[['Selección',esc(b.pick)]]:[]),['Importe',money(b.stake)],['Cuota',oddsText(b.odds)],['Estado',`<span class="chip ${b.status}">${L.statuses[b.status]}</span>`],...(b.status==='pending'?[]:[['Retorno',money(L.payout(b))],['Resultado neto',signed(L.payout(b)-b.stake)]])])}`+
 form('settle',field('Estado de la apuesta',select('status',options(Object.entries(L.statuses))))+`<div data-part="cash">${field('Retorno total del cash out (€)',numeric('cash','0,00'))}<p class="hint">Introduce el importe total abonado por la casa, incluida la parte recuperada de la apuesta.</p></div>`,'Guardar estado',`data-id="${esc(id)}"`));
 overlay.querySelector('[name="status"]').value=b.status;
 if(b.status==='cash')overlay.querySelector('[name="cash"]').value=(b.cash/100).toFixed(2).replace('.',',');
 updateFields();
}
function bankDetail(id) {
 const a=s.accounts.find(v=>v.id===id);if(!a)return;
 open('Detalle de cuenta',`<h3 class="detail-title">${esc(a.name)}</h3>${facts([['Tipo',a.type==='book'?'Banca de apuestas':'Cuenta bancaria'],...(a.bookmaker?[['Casa de apuestas',esc(a.bookmaker)]]:[]),['Saldo disponible',money(L.balances(s)[id])],...(a.type==='book'?[['En juego',money(s.bets.filter(b=>b.account===id&&b.status==='pending').reduce((v,b)=>v+b.stake,0))]]:[])])}<div class="stack">${a.type==='book'?`<button class="btn primary" data-action="new-bet" data-id="${esc(id)}">${icon('plus')}Nueva apuesta</button>`:''}<button class="btn secondary" data-action="new-move" data-id="${esc(id)}">${icon('swap')}Nuevo movimiento</button></div>`);
}
function moveDetail(id) {
 const m=s.moves.find(v=>v.id===id);if(!m)return;
 open('Detalle de movimiento',`<h3 class="detail-title">${L.kinds[m.kind]}</h3>${facts([['Importe',money(m.amount)],['Origen',esc(accountName(m.from))],['Destino',esc(accountName(m.to))],['Fecha',date(m.at,true)],...(m.note?[['Concepto',esc(m.note)]]:[])])}`);
}
function togglePart(name,on) {
 const part=overlay.querySelector(`[data-part="${name}"]`); if(!part)return;
 part.hidden=!on;part.querySelectorAll('input,select').forEach(el=>{el.disabled=!on;});
}
function updateFields() {
 const f=overlay.querySelector('form'); if(!f)return;
 if(f.dataset.form==='bank')togglePart('bookmaker',f.elements.type.value==='book');
 if(f.dataset.form==='settle')togglePart('cash',f.elements.status.value==='cash');
 if(f.dataset.form==='move'){
   const kind=f.elements.kind.value, both=['internal','transfer'].includes(kind);
   togglePart('from',kind!=='deposit');togglePart('to',kind!=='withdrawal');
   const dest=f.elements.to;
   Array.from(dest.options).forEach(o=>o.disabled=both&&o.value===f.elements.from.value);
   if(both&&dest.selectedOptions[0]?.disabled)dest.value=Array.from(dest.options).find(o=>!o.disabled)?.value||'';
   const help={deposit:'Entrada de dinero desde el exterior.',withdrawal:'Salida de dinero fuera de tus cuentas de Straker.',internal:'Mueve saldo entre dos cuentas que gestionas en Straker.',transfer:'Registra una transferencia entre dos cuentas de Straker.'};
   const needsAccount=both&&s.accounts.length<2;
   overlay.querySelector('[data-part="explain"]').innerHTML=needsAccount?'Necesitas dos cuentas para este movimiento. <button class="empty-action" type="button" data-action="second-bank">Crear otra cuenta →</button>':help[kind];
   f.querySelector('[type="submit"]').disabled=needsAccount;
 }
}
document.addEventListener('click',e=>{
 if(e.target===overlay)return close();
 const b=e.target.closest('button[data-action]');if(!b)return;
 const action=b.dataset.action, id=b.dataset.id;
 if(action.startsWith('to-')){tab=action.slice(3);render();window.scrollTo(0,0);return;}
 switch(action){
  case 'close':close();break;
  case 'new-bank':newBank();break;
  case 'second-bank':newBank('move');break;
  case 'new-bet':newBet(id);break;
  case 'new-move':newMove(id);break;
  case 'bet-detail':betDetail(id);break;
  case 'bank-detail':bankDetail(id);break;
  case 'move-detail':moveDetail(id);break;
  case 'filter-bet':betFilter=id;render();break;
  case 'filter-move':moveFilter=id;render();break;
 }
});
overlay.addEventListener('change',updateFields);
overlay.addEventListener('submit',e=>{
 e.preventDefault();
 const f=e.target, data=Object.fromEntries(new FormData(f)), draft=JSON.parse(JSON.stringify(s)), kind=f.dataset.form;
 let created, message;
 try{
   if(kind==='bank'){created=L.createAccount(draft,data);message='Cuenta creada';}
   if(kind==='bet'){L.addBet(draft,data);message='Apuesta registrada';}
   if(kind==='move'){L.addMove(draft,data);message='Movimiento registrado';}
   if(kind==='settle'){L.settle(draft,f.dataset.id,data.status,data.cash);message='Estado actualizado';}
   try{localStorage.setItem(key,JSON.stringify(draft));}catch{throw Error('No se ha podido guardar. Comprueba el espacio disponible y vuelve a intentarlo.');}
 }catch(err){f.querySelector('.error').textContent=err.message;return;}
 const next=f.dataset.next;
 s=draft;close();
 if(kind==='bet')betFilter='all';if(kind==='move')moveFilter='all';
 render();notify(message);
 if(next==='bet')newBet(created.id);if(next==='move')newMove(created.id);
});
window.strakerBack=()=>{
 if(!overlay.hidden){close();return true;}
 if(tab!=='home'){tab='home';render();window.scrollTo(0,0);return true;}
 return false;
};
document.addEventListener('keydown',e=>{
 if(e.key==='Escape'&&!overlay.hidden){e.preventDefault();close();}
 if(e.key==='Tab'&&!overlay.hidden){
   const items=Array.from(overlay.querySelectorAll('button,input,select,textarea')).filter(el=>!el.disabled&&el.getClientRects().length);
   const first=items[0], last=items[items.length-1];
   if(e.shiftKey&&(document.activeElement===first||document.activeElement.id==='dialog-title')){e.preventDefault();last?.focus();}
   if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
 }
});
render();
})();
