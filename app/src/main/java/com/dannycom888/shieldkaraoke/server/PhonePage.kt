package com.dannycom888.shieldkaraoke.server

/** The single-file phone ordering page served by [KaraokeHttpServer]. */
object PhonePage {

    /**
     * [bootId] is the serving server's [BootSession] id, embedded
     * into the page so its queue poll can spot a page left over
     * from a previous TV-app session and reload itself (v1.0.8).
     * The default keeps plain `html()` calls (tests, previews)
     * working; an empty id disables the staleness check, matching
     * [BootSession.isStale]'s "unknown is not stale" rule.
     */
    fun html(bootId: String = ""): String = """<!DOCTYPE html>
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
#qpanel{background:#16295c;border:1px solid #446;border-radius:10px;padding:8px 10px;margin:10px 0}
#qhead{font-size:17px;font-weight:bold;color:#FFD54F;cursor:pointer}
#qarrow{float:right}
#qsummary{margin-top:2px}
#qlist div{padding:6px 4px;border-bottom:1px solid #223}
#qlist .mine{background:#4a3a08;border-left:4px solid #FFD54F}
.badge{background:#FFD54F;color:#111;border-radius:8px;padding:1px 6px;font-size:12px;font-weight:bold;white-space:nowrap}
.del{background:#a33;color:#fff}
.hidden{display:none}
.dcards{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:8px 0}
.dcard{background:#16295c;border:3px solid #446;border-radius:12px;padding:6px 2px;text-align:center;cursor:pointer}
.dcard img{height:96px;max-width:100%;object-fit:contain}
.dcard span{display:block;margin-top:2px;font-size:14px}
.dcard.on{border-color:#FFD54F;background:#4a3a08}
</style></head><body>
<div id="nameGate">
<h1>🎤 MySing</h1>
<p>先输入你的名字，点的歌会显示是谁点的。</p>
<input id="nameInput" placeholder="你的名字">
<p><button id="enterBtn" style="width:100%">进入点歌</button></p>
</div>
<div id="main" class="hidden">
<h1>🎤 点歌 <span class="small" id="who"></span></h1>
<div id="qpanel">
<div id="qhead">📋 已点歌单（共 <span id="qcount">0</span> 首）<span id="qarrow">▴</span></div>
<div id="qsummary" class="small"></div>
<div id="qlist"></div>
</div>
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
<h1>💃 选伴舞</h1>
<p class="small">你选的伴舞，只在你点的歌时出来；唱到你的歌时想换人，点一下电视上立刻换。</p>
<div id="dancers" class="dcards">
<div class="dcard" data-who="modern"><img src="/dancer-anim/modern" onerror="this.onerror=null;this.src='/dancer-img/modern'" alt="现代女"><span>现代女</span></div>
<div class="dcard" data-who="ancient"><img src="/dancer-anim/ancient" onerror="this.onerror=null;this.src='/dancer-img/ancient'" alt="古代女"><span>古代女</span></div>
<div class="dcard" data-who="girl"><img src="/dancer-anim/girl" onerror="this.onerror=null;this.src='/dancer-img/girl'" alt="现代女孩"><span>现代女孩</span></div>
<div class="dcard" data-who="ancient_girl"><img src="/dancer-anim/ancient_girl" onerror="this.onerror=null;this.src='/dancer-img/ancient_girl'" alt="古代女童"><span>古代女童</span></div>
<div class="dcard" data-who="baby"><img src="/dancer-anim/baby" onerror="this.onerror=null;this.src='/dancer-img/baby'" alt="小婴儿"><span>小婴儿</span></div>
<div class="dcard" data-who="robot"><img src="/dancer-anim/robot" onerror="this.onerror=null;this.src='/dancer-img/robot'" alt="机器人"><span>机器人</span></div>
</div>
<div id="msg" class="small"></div>
<div id="songs"></div>
</div>
<script>
var name = localStorage.getItem('sk_name') || '';
var lang='', gender='', mode='all', favs=[], recents=[];
var myDancer = localStorage.getItem('sk_dancer') || 'modern';
var qOpen = true, qSig = '';
var bootId = '$bootId';
var reloading = false;
function netErr(){ document.getElementById('msg').textContent = '发送失败：连不上电视，请确认同一个 WiFi，或关掉页面重新扫码'; }
function paintDancers(){ document.querySelectorAll('[data-who]').forEach(function(c){
  c.classList.toggle('on', c.getAttribute('data-who')===myDancer); }); }
function show(){ document.getElementById('nameGate').classList.toggle('hidden', !!name);
  document.getElementById('main').classList.toggle('hidden', !name);
  document.getElementById('who').textContent = name ? ('（'+name+'）') : ''; if(name){paintDancers(); loadSongs(); loadQueue();} }
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
  }).catch(netErr);
}
function order(id){
  fetch('/api/order',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({id:id, name:name})}).then(function(r){return r.json()}).then(function(d){
    var m = d.message || d.error || '';
    if(d.duplicate) m += '（提醒：这首已经在队列里了）';
    document.getElementById('msg').textContent = m; loadQueue();
  }).catch(netErr);
}
function toggleFav(id, fav){
  fetch('/api/fav',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({id:id, fav:fav})}).then(function(){ loadQueue(); }).catch(netErr);
}
function paintQPanel(){
  document.getElementById('qlist').style.display = qOpen ? '' : 'none';
  document.getElementById('qarrow').textContent = qOpen ? '▴' : '▾';
}
document.getElementById('qhead').onclick = function(){ qOpen = !qOpen; paintQPanel(); };
function renderQueue(d){
  var waiting = d.waiting || [];
  document.getElementById('qcount').textContent = waiting.length + (d.current ? 1 : 0);
  var myNext = -1;
  waiting.forEach(function(e,i){ if(e.by === name && myNext < 0) myNext = i; });
  document.getElementById('qsummary').textContent =
    myNext >= 0 ? ('我点的下一首在第 ' + (myNext + 1) + ' 位') :
    (d.current && d.current.by === name) ? '正在唱的就是我点的 🎤' : '我还没点歌';
  var sig = JSON.stringify([d.current, waiting]);
  if(sig === qSig){ paintQPanel(); return; }
  qSig = sig;
  var el = document.getElementById('qlist'); el.innerHTML = '';
  if(d.current){
    var c = document.createElement('div');
    if(d.current.by === name) c.className = 'mine';
    c.textContent = '▶ 正在唱：' + d.current.artist + ' - ' + d.current.title + '（' + d.current.by + '点的）';
    if(d.current.by === name){
      var cb = document.createElement('span'); cb.className = 'badge'; cb.textContent = '我点的';
      c.appendChild(document.createTextNode(' ')); c.appendChild(cb);
    }
    el.appendChild(c);
  }
  waiting.forEach(function(e,i){
    var row = document.createElement('div');
    var mine = e.by === name;
    if(mine) row.className = 'mine';
    row.textContent = (i + 1) + '. ' + e.artist + ' - ' + e.title + '（' + e.by + '点的）';
    if(mine){
      var b = document.createElement('span'); b.className = 'badge';
      b.textContent = '我点的 · 第 ' + (i + 1) + ' 位';
      row.appendChild(document.createTextNode(' ')); row.appendChild(b);
    }
    row.appendChild(document.createTextNode(' '));
    var del = document.createElement('button'); del.textContent = '删'; del.className = 'del';
    del.onclick = function(){ fetch('/api/remove',{method:'POST',headers:{'Content-Type':'application/json'},
      body: JSON.stringify({index:i})}).then(loadQueue).catch(netErr); };
    row.appendChild(del); el.appendChild(row);
  });
  if(!d.current && !waiting.length) el.innerHTML = '<div class="small">还没有人点歌</div>';
  paintQPanel();
}
function loadQueue(){
  fetch('/api/queue').then(function(r){return r.json()}).then(function(d){
    if(d.boot && bootId && d.boot !== bootId && !reloading){
      reloading = true;
      document.getElementById('msg').textContent = '电视已重启，正在刷新页面…';
      setTimeout(function(){ location.reload(); }, 800);
      return;
    }
    favs = d.favorites||[]; recents = d.recents||[];
    var ds = d.dancers||{};
    if(ds[name] && ds[name]!==myDancer){ myDancer=ds[name]; localStorage.setItem('sk_dancer', myDancer); }
    paintDancers();
    renderQueue(d);
  }).catch(netErr);
}
document.getElementById('q').addEventListener('input', function(){ mode='all'; loadSongs(); });
document.querySelectorAll('[data-lang]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-lang]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); lang=c.getAttribute('data-lang'); mode='all'; loadSongs(); }; });
document.querySelectorAll('[data-g]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-g]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); gender=c.getAttribute('data-g'); mode='all'; loadSongs(); }; });
document.querySelectorAll('[data-who]').forEach(function(c){ c.onclick=function(){
  myDancer=c.getAttribute('data-who'); localStorage.setItem('sk_dancer', myDancer); paintDancers();
  fetch('/api/dancer',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({name:name, who:myDancer})}).catch(netErr);
}; });
document.getElementById('favTab').onclick=function(){ mode='fav'; loadSongs(); };
document.getElementById('recentTab').onclick=function(){ mode='recent'; loadSongs(); };
setInterval(loadQueue, 5000);
show();
</script></body></html>"""
}
