'use strict';
const $ = id => document.getElementById(id);
const ICONS = {
  grid:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/><path d="M3 11h18"/>',
  chart:'<path d="M3 3v18h18M8 16v-4m5 4V7m5 9v-6"/>',
  copy:'<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/>',
  refresh:'<path d="M20 7a9 9 0 1 0 1 9M20 3v5h-5"/>',
  arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',
  files:'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8ZM14 2v6h6M8 13h8m-8 4h5"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',
  reveal:'<path d="M14 3h7v7m-1-6L10 14M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5"/>'
};
const icon = n => `<svg viewBox="0 0 24 24" aria-hidden="true">${ICONS[n] || ICONS.files}</svg>`;
document.querySelectorAll('[data-icon]').forEach(el => el.innerHTML = icon(el.dataset.icon));
const escapeHTML = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const bytes = n => {if (!n) return '0 B';const i = Math.min(4,Math.floor(Math.log(n)/Math.log(1024)));return `${(n/1024**i).toLocaleString(undefined,{maximumFractionDigits:i ? 1 : 0})} ${['B','KB','MB','GB','TB'][i]}`;};
const relative = p => state?.root ? p.slice(state.root.length+1) : p;
const date = t => new Date(t*1000).toLocaleDateString(undefined,{month:'short',day:'numeric',year:'numeric'});
let state = null, view = 'overview', plan = null, selected = new Set(), page = 0, total = 0;
let busyBefore = false, lastNotice = '', searchTimer, requestSerial = 0, renderingStamp = null;
const titles = {
  overview:['Everything, a little clearer.','Find what you need. Understand your space. Give every file a home.'],
  files:['Your files. Found.','A name, a folder, an extension. Start with whatever you remember.'],
  space:['Make sense of your space.','See what’s growing, what’s large, and what deserves a closer look.'],
  organize:['Less clutter. More clarity.','Simple categories. A transparent plan. Every move is your choice.'],
  duplicates:['One too many?','Find exact copies before deciding which files to keep.']
};
async function api(path, data) {
  const response = await fetch('/api/' + path, {
    method:data === undefined ? 'GET':'POST',
    headers:{'X-Findspace-Token':window.FINDSPACE_TOKEN,'Content-Type':'application/json'},
    ...(data === undefined ? {} : {body:JSON.stringify(data)})
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'Something went wrong.');
  return result;
}
function notice(message, error=false) {
  $('notice').textContent = message;
  $('notice').classList.toggle('error',error);
  $('notice').hidden = !message;
}
function guard(action) {return async (...args) => {try {await action(...args);} catch(e) {notice(e.message,true);}};}
function goto(next) {
  view = next;
  document.querySelectorAll('.view').forEach(el => el.hidden = el.id !== 'view-'+next);
  document.querySelectorAll('.nav').forEach(el => el.classList.toggle('active',el.dataset.view === next));
  $('pageTitle').textContent = titles[next][0];
  $('pageSubtitle').textContent = titles[next][1];
  $('breadcrumb').textContent = ({overview:'Overview',files:'Find a file',space:'Space explorer',organize:'Organize',duplicates:'Duplicate check'})[next];
  if (state?.root) guard(renderView)();
  if (next === 'files') setTimeout(()=>$('searchInput').focus(),50);
}
function fileTable(rows) {
  if (!rows.length) return '<div class="empty-panel">No files here yet. Try a different search or refresh the index.</div>';
  return `<table class="file-table"><thead><tr><th>FILE NAME</th><th>SIZE</th><th>MODIFIED</th><th><span class="sr-only">Show in folder</span></th></tr></thead><tbody>${rows.map(f=>{
    const ext = f.name.includes('.') ? f.name.split('.').pop().slice(0,4).toUpperCase():'FILE';
    return `<tr><td><div class="file-cell"><span class="file-badge" style="color:var(--${f.category})">${escapeHTML(ext)}</span><div class="file-info"><div class="file-name" title="${escapeHTML(f.name)}">${escapeHTML(f.name)}</div><div class="file-path" title="${escapeHTML(f.path)}">${escapeHTML(relative(f.path))}</div></div></div></td><td>${bytes(f.size)}</td><td>${date(f.modified)}</td><td><button class="icon-button" data-reveal="${f.id}" title="Show in folder" aria-label="Show ${escapeHTML(f.name)} in folder">${icon('reveal')}</button></td></tr>`;
  }).join('')}</tbody></table>`;
}
function categoryRows(groups, max=Infinity) {
  const largest = Math.max(1,...groups.map(g=>g.size));
  return groups.slice(0,max).map(g=>`<div class="storage-row"><span class="category-dot" style="background:var(--${g.category})"></span><span class="cat-name">${g.category}</span><span class="bar"><i style="width:${g.size/largest*100}%;background:var(--${g.category})"></i></span><span class="size">${bytes(g.size)}</span></div>`).join('');
}
async function renderView() {
  if (view === 'overview') {
    $('statCount').textContent = state.count.toLocaleString();
    $('statSize').textContent = bytes(state.size);
    $('statKinds').textContent = state.groups.length;
    $('overviewStorage').innerHTML = categoryRows(state.groups,5) || '<div class="empty-panel">No indexed files yet.</div>';
    const data = await api('files?limit=6');
    $('recentFiles').innerHTML = fileTable(data.items.slice(0,5));
  } else if (view === 'files') {
    const serial = ++requestSerial;
    const data = await api('files?'+new URLSearchParams({q:$('searchInput').value,kind:$('kindFilter').value,sort:$('sortFilter').value,offset:page*500}));
    if (serial !== requestSerial) return;
    total = data.total;
    $('resultCount').textContent = `${total.toLocaleString()} file${total===1?'':'s'} found`;
    $('fileResults').innerHTML = fileTable(data.items);
    $('pageInfo').textContent = total ? `${page*500+1}–${Math.min(total,(page+1)*500)} of ${total.toLocaleString()}`:'No results';
    $('prevPage').disabled = page === 0;
    $('nextPage').disabled = (page+1)*500 >= total;
  } else if (view === 'space') {
    $('spaceTotal').textContent = bytes(state.size);
    $('storageSegments').innerHTML = state.groups.map(g=>`<span title="${g.category}: ${bytes(g.size)}" style="width:${state.size?g.size/state.size*100:0}%;background:var(--${g.category})"></span>`).join('');
    $('categoryBreakdown').innerHTML = categoryRows(state.groups);
    $('folderBreakdown').innerHTML = state.folders.map(f=>`<div class="folder-row"><span>${escapeHTML(f.name)}</span><b>${bytes(f.size)}</b></div>`).join('');
    const data = await api('files?sort=size');
    $('largeFiles').innerHTML = fileTable(data.items.slice(0,12));
  } else if (view === 'organize') {
    $('history').innerHTML = state.history.map(h=>`<div class="history-row"><div><strong>${h.count} file${h.count===1?'':'s'} organized</strong><p>${new Date(h.created*1000).toLocaleString()}</p></div><button class="button secondary" data-undo="${h.batch}" ${state.busy?'disabled':''}>Undo moves</button></div>`).join('') || '<div class="empty-panel">Your organization history will appear here.</div>';
  } else if (view === 'duplicates') {
    renderDuplicates();
  }
}
function renderDuplicates() {
  const groups = state.duplicates;
  if (!groups.length) {
    $('duplicateResults').innerHTML = `<div class="panel empty-panel">${state.progress.message?.includes('exact duplicate')?'No exact duplicates found in this index.':'Run a check to find exact copies. Nothing is deleted.'}</div>`;
    return;
  }
  const space = groups.reduce((n,g)=>n+g.potential,0);
  $('duplicateResults').innerHTML = `<div class="duplicate-summary"><b>${groups.length} exact duplicate group${groups.length===1?'':'s'} · ${bytes(space)} in extra copies</b><br>This is potential space only. Review locations and backups before removing anything yourself.</div>`+groups.map(g=>`<article class="panel duplicate-group"><h3>${g.files.length} identical files <span>${bytes(g.size)} each</span></h3>${g.files.map(f=>`<div class="duplicate-file"><div><strong>${escapeHTML(f.name)}</strong><p>${escapeHTML(f.path)}</p></div><button class="button secondary" data-reveal="${f.id}">Show in folder</button></div>`).join('')}</article>`).join('');
}
async function poll() {
  state = await api('status');
  const isBusy = state.busy;
  $('operation').hidden = !isBusy;
  const stage = state.progress.stage;
  $('opTitle').textContent = ({starting:'Getting ready…',scanning:'Mapping your folder…',organizing:'Giving your files a home…','checking duplicates':'Comparing file contents…'})[stage] || 'Working locally…';
  $('opDetail').textContent = `${state.progress.count.toLocaleString()} files processed${state.progress.errors ? ` · ${state.progress.errors} skipped or failed` : ''}. You can keep searching the last completed index.`;
  ['scanBtn','refreshBtn','previewBtn','applyBtn','duplicatesBtn'].forEach(id=>$(id).disabled = isBusy || (id !== 'scanBtn' && !state.root));
  $('emptyState').hidden = !!state.root;
  $('workspace').hidden = !state.root;
  if (document.activeElement !== $('folderPath') && state.root && !$('folderPath').value) $('folderPath').value = state.root;
  $('scanStamp').textContent = state.last_scan ? 'Last indexed '+new Date(state.last_scan*1000).toLocaleString():'Ready when you are.';
  if (busyBefore && !isBusy) {
    const message = state.progress.message;
    if (message) {notice(message,state.progress.stage === 'error');lastNotice = message;}
    plan = null;
    $('planArea').hidden = true;
    await renderView();
  }
  const stamp = `${state.root}:${state.last_scan}`;
  if (stamp !== renderingStamp && state.root) {
    renderingStamp = stamp;
    const current = $('kindFilter').value;
    $('kindFilter').innerHTML = '<option value="">All types</option>'+state.groups.map(g=>`<option>${g.category}</option>`).join('');
    $('kindFilter').value = current;
    await renderView();
  }
  busyBefore = isBusy;
}
async function watch() {
  try {await poll();} catch(e) {notice('Findspace is disconnected. Keep the launcher window open, then reload this page.',true);}
  setTimeout(watch,state?.busy?650:2200);
}
async function startScan(path) {
  if (!path.trim()) throw new Error('Choose a folder or paste its full path first.');
  notice('');
  await api('scan',{path:path.trim()});
  busyBefore = true;
  await poll();
}
function renderPlan() {
  $('planArea').hidden = false;
  $('planHeading').textContent = `${plan.items.length} proposed moves · ${plan.skipped} left in place`;
  $('planRows').innerHTML = plan.items.length ? plan.items.map(f=>`<label class="plan-row"><input type="checkbox" data-select="${f.id}" ${selected.has(f.id)?'checked':''}><div><strong>${escapeHTML(relative(f.source))}</strong><p>→ <span>${escapeHTML(relative(f.destination))}</span></p></div><span>${bytes(f.size)}</span></label>`).join(''):'<div class="empty-panel">Nothing to organize with these settings. Existing folders, code and unknown types stay in place.</div>';
  updateSelection();
}
function updateSelection() {
  $('selectedCount').textContent = `${selected.size} file${selected.size===1?'':'s'} selected · ${bytes(plan.items.filter(i=>selected.has(i.id)).reduce((n,i)=>n+i.size,0))}`;
  $('applyBtn').disabled = !selected.size || state.busy;
}
function confirmAction(title,text,label) {
  $('confirmTitle').textContent = title;
  $('confirmText').textContent = text;
  $('acceptConfirm').textContent = label;
  $('confirmDialog').showModal();
  return new Promise(resolve=>{
    $('acceptConfirm').onclick = ()=>{$('confirmDialog').close();resolve(true);};
    $('cancelConfirm').onclick = ()=>{$('confirmDialog').close();resolve(false);};
    $('confirmDialog').oncancel = ()=>resolve(false);
  });
}
document.querySelectorAll('[data-view]').forEach(el=>el.onclick=()=>goto(el.dataset.view));
document.querySelectorAll('[data-goto]').forEach(el=>el.onclick=()=>goto(el.dataset.goto));
$('scanBtn').onclick = guard(()=>startScan($('folderPath').value));
$('folderPath').onkeydown = e=>{if(e.key==='Enter') guard(()=>startScan($('folderPath').value))();};
$('refreshBtn').onclick = guard(()=>startScan(state.root));
$('browseBtn').onclick = guard(async()=>{
  $('browseBtn').disabled=true;
  try {const r=await api('pick',{});if(r.path)$('folderPath').value=r.path;}
  finally {$('browseBtn').disabled=false;}
});
$('heroSearch').onclick = ()=>goto('files');
$('searchInput').oninput = ()=>{clearTimeout(searchTimer);page=0;searchTimer=setTimeout(guard(renderView),160);};
['kindFilter','sortFilter'].forEach(id=>$(id).onchange=()=>{page=0;guard(renderView)();});
$('prevPage').onclick=guard(async()=>{page=Math.max(0,page-1);await renderView();});
$('nextPage').onclick=guard(async()=>{page++;await renderView();});
$('previewBtn').onclick=guard(async()=>{
  notice('');
  plan=await api('plan',{scope:$('organizeScope').value,dated:$('dateFolders').checked});
  selected=new Set(plan.items.map(i=>i.id));
  renderPlan();
});
['organizeScope','dateFolders'].forEach(id=>$(id).onchange=()=>{plan=null;$('planArea').hidden=true;});
$('toggleSelection').onclick=()=>{selected=selected.size?new Set():new Set(plan.items.map(i=>i.id));renderPlan();};
$('applyBtn').onclick=guard(async()=>{
  if(!await confirmAction(`Move ${selected.size} files?`,'Only the selected files will move to the destinations shown above. Existing files are never overwritten. You can undo unchanged files from move history.','Move files'))return;
  await api('apply',{plan:plan.id,selected:[...selected]});
  notice('');busyBefore=true;await poll();
});
$('duplicatesBtn').onclick=guard(async()=>{await api('duplicates',{});notice('');busyBefore=true;await poll();});
document.addEventListener('change',e=>{if(e.target.dataset.select){e.target.checked?selected.add(e.target.dataset.select):selected.delete(e.target.dataset.select);updateSelection();}});
document.addEventListener('click',guard(async e=>{
  const reveal=e.target.closest('[data-reveal]');
  if(reveal){await api('reveal',{id:reveal.dataset.reveal});return;}
  const undo=e.target.closest('[data-undo]');
  if(undo){
    if(!await confirmAction('Restore these files?','Files return to their original locations. Edited files and occupied destinations are skipped. Empty category folders are left in place.','Restore files'))return;
    await api('undo',{batch:undo.dataset.undo});notice('');busyBefore=true;await poll();
  }
}));
document.addEventListener('keydown',e=>{
  if(e.key==='/'&&!['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName)&&!document.querySelector('dialog[open]')){e.preventDefault();goto('files');}
  if(e.key==='Escape'&&document.activeElement===$('searchInput')){$('searchInput').value='';page=0;guard(renderView)();}
});
$('helpBtn').onclick=()=>$('helpDialog').showModal();
['closeHelp','helpDone'].forEach(id=>$(id).onclick=()=>$('helpDialog').close());
watch();
