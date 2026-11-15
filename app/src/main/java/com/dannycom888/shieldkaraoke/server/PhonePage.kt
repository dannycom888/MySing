package com.dannycom888.shieldkaraoke.server

/** The single-file phone ordering page served by [KaraokeHttpServer]. */
object PhonePage {

    fun html(): String = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MySing 点歌</title>
<style>
body{font-family:sans-serif;background:#0d1b3e;color:#fff;margin:0;padding:12px}
h1{font-size:20px;color:#FFD54F;margin:6px 0}
input,button,select{font-size:16px;padding:10px;border-radius:8px;border:1px solid #446}
input{background:#16295c;color:#fff;width:100%;box-sizing:border-box}
button{background:#FFD54F;color:#111;border:none;font-weight:bold}
.chip{background:#22397a;color:#fff;margin:2px;padding:8px 12px;border-radius:16px}
.chip.on{background:#FFD54F;color:#111}
.song{display:flex;justify-content:space-between;align-items:center;padding:10px 4px;border-bottom:1px solid #223}
.song button{padding:6px 14px}
.small{color:#9db4e8;font-size:13px}
#queue div{padding:6px 4px;border-bottom:1px solid #223}
.del{background:#a33;color:#fff}
.hidden{display:none}
</style></head><body>
<div id="nameGate">
<h1>🎤 MySing</h1>
<p>先输入你的名字，点的歌会显示是谁点的。</p>
<input id="nameInput" placeholder="你的名字">
<p><button id="enterBtn" style="width:100%">进入点歌</button></p>
</div>
<div id="main" class="hidden">
<h1>🎤 点歌 <span class="small" id="who"></span></h1>
<input id="q" placeholder="搜歌名/歌手，或拼音首字母（如 fh cq）">
<div style="margin:8px 0">
<button class="chip on" data-lang="">全部</button>
<button class="chip" data-lang="zh">中文</button>
<button class="chip" data-lang="en">英文</button>
<button class="chip" data-g="">男女不限</button>
<button class="chip" data-g="m">男歌手</button>
<button class="chip" data-g="f">女歌手</button>
<button class="chip" id="favTab">❤ 收藏</button>
<button class="chip" id="recentTab">🕘 最近唱过</button>
</div>
<div id="msg" class="small"></div>
<div id="songs"></div>
<h1>📋 已点顺序</h1>
<div id="queue"></div>
</div>
<script>
var name = localStorage.getItem('sk_name') || '';
var lang='', gender='', mode='all', favs=[], recents=[];
function show(){ document.getElementById('nameGate').classList.toggle('hidden', !!name);
  document.getElementById('main').classList.toggle('hidden', !name);
  document.getElementById('who').textContent = name ? ('（'+name+'）') : ''; if(name){loadSongs(); loadQueue();} }
document.getElementById('enterBtn').onclick = function(){
  var v = document.getElementById('nameInput').value.trim();
  if(!v){ alert('请输入名字'); return; }
  name = v; localStorage.setItem('sk_name', v); show(); };
function loadSongs(){
  var url;
  if(mode==='fav'){ url='/api/songs'; }
  else if(mode==='recent'){ url='/api/songs'; }
  else { url='/api/songs?lang='+lang+'&gender='+gender+'&q='+encodeURIComponent(document.getElementById('q').value); }
  fetch(url).then(function(r){return r.json()}).then(function(d){
    var list = d.songs;
    if(mode==='fav') list = list.filter(function(s){return favs.indexOf(s.id)>=0});
    if(mode==='recent') list = recents.map(function(id){return list.find(function(s){return s.id===id})}).filter(Boolean);
    var el = document.getElementById('songs'); el.innerHTML='';
    list.slice(0,300).forEach(function(s){
      var row=document.createElement('div'); row.className='song';
      var fav = favs.indexOf(s.id)>=0;
      row.innerHTML='<span>'+s.artist+' - '+s.title+'</span>';
      var b=document.createElement('button'); b.textContent='点歌';
      b.onclick=function(){ order(s.id); };
      var f=document.createElement('button'); f.textContent=fav?'❤':'🤍';
      f.onclick=function(){ toggleFav(s.id, !fav); };
      var wrap=document.createElement('span'); wrap.appendChild(f); wrap.appendChild(b);
      row.appendChild(wrap); el.appendChild(row);
    });
  });
}
function order(id){
  fetch('/api/order',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({id:id, name:name})}).then(function(r){return r.json()}).then(function(d){
    var m = d.message || d.error || '';
    if(d.duplicate) m += '（提醒：这首已经在队列里了）';
    document.getElementById('msg').textContent = m; loadQueue();
  });
}
function toggleFav(id, fav){
  fetch('/api/fav',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({id:id, fav:fav})}).then(function(){ loadQueue(); });
}
function loadQueue(){
  fetch('/api/queue').then(function(r){return r.json()}).then(function(d){
    favs = d.favorites||[]; recents = d.recents||[];
    var el=document.getElementById('queue'); el.innerHTML='';
    if(d.current) el.innerHTML += '<div>▶ 正在唱：'+d.current.artist+' - '+d.current.title+'（'+d.current.by+'点的）</div>';
    (d.waiting||[]).forEach(function(e,i){
      var row=document.createElement('div');
      row.textContent=(i+1)+'. '+e.artist+' - '+e.title+'（'+e.by+'点的） ';
      var b=document.createElement('button'); b.textContent='删'; b.className='del';
      b.onclick=function(){ fetch('/api/remove',{method:'POST',headers:{'Content-Type':'application/json'},
        body: JSON.stringify({index:i})}).then(loadQueue); };
      row.appendChild(b); el.appendChild(row);
    });
    if(!d.current && !(d.waiting||[]).length) el.innerHTML='<div class="small">还没有人点歌</div>';
  });
}
document.getElementById('q').addEventListener('input', function(){ mode='all'; loadSongs(); });
document.querySelectorAll('[data-lang]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-lang]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); lang=c.getAttribute('data-lang'); mode='all'; loadSongs(); }; });
document.querySelectorAll('[data-g]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-g]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); gender=c.getAttribute('data-g'); mode='all'; loadSongs(); }; });
document.getElementById('favTab').onclick=function(){ mode='fav'; loadSongs(); };
document.getElementById('recentTab').onclick=function(){ mode='recent'; loadSongs(); };
setInterval(loadQueue, 5000);
show();
</script></body></html>"""
}
