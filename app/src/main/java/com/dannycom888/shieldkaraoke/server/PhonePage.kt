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
     *
     * v1.0.13: the page is bilingual. A 中文/EN switch at the top
     * flips every string through the I18N dictionary below; the
     * choice is per-phone (localStorage `mysing_lang`) because
     * guests differ — William needs English while everyone else
     * reads Chinese. Order/dancer requests carry the language so
     * the server's replies come back in the same language. Song
     * and singer names are file-name data and never translated.
     *
     * v1.0.21: [customNames] carries the host's dancer renames
     * (dancer key -> custom name, set on the TV admin page). They
     * are injected as CUSTOM_DANCER_NAMES and override the baked
     * stage names in BOTH languages — a person's name is not
     * translated. Phones only ever read names this way; there is
     * no rename endpoint on the server.
     */
    fun html(bootId: String = "", customNames: Map<String, String> = emptyMap()): String {
        val customNamesJson = customNamesJson(customNames)
        return """<!DOCTYPE html>
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
#sug{background:#16295c;border:1px solid #446;border-radius:10px;margin-top:6px;max-height:42vh;overflow-y:auto}
.sugrow{padding:14px 10px;border-bottom:1px solid #223;font-size:17px;cursor:pointer}
.sugrow:active{background:#22397a}
.sugmore{padding:10px;color:#9db4e8;font-size:13px}
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
#langBar{text-align:right;margin-bottom:2px}
#langBar .chip{padding:5px 12px;font-size:14px}
</style></head><body>
<div id="langBar"><button class="chip" id="langZh">中文</button><button class="chip" id="langEn">EN</button></div>
<div id="nameGate">
<h1>🎤 MySing</h1>
<p data-i="gateHint">先输入你的名字，点的歌会显示是谁点的。</p>
<input id="nameInput" data-iph="namePh" placeholder="你的名字">
<p><button id="enterBtn" data-i="enter" style="width:100%">进入点歌</button></p>
</div>
<div id="main" class="hidden">
<h1>🎤 <span data-i="orderTitle">点歌</span> <span class="small" id="who"></span></h1>
<div id="qpanel">
<div id="qhead"><span data-i="queuePre">📋 已点歌单（共 </span><span id="qcount">0</span><span data-i="queuePost"> 首）</span><span id="qarrow">▴</span></div>
<div id="qsummary" class="small"></div>
<div id="qlist"></div>
</div>
<input id="q" data-iph="searchPh" placeholder="搜歌名/歌手，或拼音首字母（如 fh cq）">
<div id="sug" class="hidden"></div>
<div style="margin:8px 0">
<button class="chip on" data-lang="" data-i="fAll">全部</button>
<button class="chip" data-lang="zh" data-i="fZh">中文</button>
<button class="chip" data-lang="en" data-i="fEn">英文</button>
<button class="chip" data-g="" data-i="fAny">男女不限</button>
<button class="chip" data-g="m" data-i="fM">男歌手</button>
<button class="chip" data-g="f" data-i="fF">女歌手</button>
<button class="chip" id="favTab" data-i="fFav">❤ 收藏</button>
<button class="chip" id="recentTab" data-i="fRecent">🕘 最近唱过</button>
</div>
<h1 data-i="dancerTitle">💃 选伴舞</h1>
<p class="small" data-i="dancerHint">你选的伴舞，只在你点的歌时出来；唱到你的歌时想换人，点一下电视上立刻换。</p>
<div id="dancers" class="dcards">
<div class="dcard" data-who="modern"><img src="/dancer-anim/modern" onerror="this.onerror=null;this.src='/dancer-img/modern'" alt="Amy"><span data-dancer="modern">Amy</span></div>
<div class="dcard" data-who="ancient"><img src="/dancer-anim/ancient" onerror="this.onerror=null;this.src='/dancer-img/ancient'" alt="秀颖"><span data-dancer="ancient">秀颖</span></div>
<div class="dcard" data-who="sumo"><img src="/dancer-anim/sumo" onerror="this.onerror=null;this.src='/dancer-img/sumo'" alt="姿三四郎"><span data-dancer="sumo">姿三四郎</span></div>
<div class="dcard" data-who="ancient_girl"><img src="/dancer-anim/ancient_girl" onerror="this.onerror=null;this.src='/dancer-img/ancient_girl'" alt="汉娜"><span data-dancer="ancient_girl">汉娜</span></div>
<div class="dcard" data-who="baby"><img src="/dancer-anim/baby" onerror="this.onerror=null;this.src='/dancer-img/baby'" alt="哪吒"><span data-dancer="baby">哪吒</span></div>
<div class="dcard" data-who="robot"><img src="/dancer-anim/robot" onerror="this.onerror=null;this.src='/dancer-img/robot'" alt="瓦力"><span data-dancer="robot">瓦力</span></div>
</div>
<div id="msg" class="small"></div>
<div id="songs"></div>
</div>
<script>
var CUSTOM_DANCER_NAMES = $customNamesJson;
var I18N = {
zh: {
  title: 'MySing 点歌',
  gateHint: '先输入你的名字，点的歌会显示是谁点的。',
  namePh: '你的名字', enter: '进入点歌', nameAlert: '请输入名字',
  orderTitle: '点歌',
  queuePre: '📋 已点歌单（共 ', queuePost: ' 首）',
  myNext: '我点的下一首在第 {n} 位', myPlaying: '正在唱的就是我点的 🎤', myNone: '我还没点歌',
  nowPlaying: '▶ 正在唱：{song}（{by}点的）', waitingRow: '{song}（{by}点的）',
  mine: '我点的', minePos: '我点的 · 第 {n} 位', del: '删', queueEmpty: '还没有人点歌',
  searchPh: '搜歌名/歌手，或拼音首字母（如 fh cq）',
  fAll: '全部', fZh: '中文', fEn: '英文', fAny: '男女不限', fM: '男歌手', fF: '女歌手',
  fFav: '❤ 收藏', fRecent: '🕘 最近唱过',
  dancerTitle: '💃 选伴舞',
  dancerHint: '你选的伴舞，只在你点的歌时出来；唱到你的歌时想换人，点一下电视上立刻换。',
  order: '点歌', dup: '（提醒：这首已经在队列里了）',
  accBadge: '伴奏',
  sugMore: '还有更多结果，继续输入缩小范围',
  netErr: '发送失败：连不上电视，请确认同一个 WiFi，或关掉页面重新扫码',
  tvRestarted: '电视已重启，正在刷新页面…',
  dancers: {modern: 'Amy', ancient: '秀颖', sumo: '姿三四郎', ancient_girl: '汉娜', baby: '哪吒', robot: '瓦力'}
},
en: {
  title: 'MySing — Order Songs',
  gateHint: 'Enter your name first — the songs you order will show who picked them.',
  namePh: 'Your name', enter: 'Start ordering', nameAlert: 'Please enter your name',
  orderTitle: 'Order songs',
  queuePre: '📋 Queue (', queuePost: ' songs)',
  myNext: 'Your next song is #{n} in line', myPlaying: 'Your song is playing now 🎤', myNone: "You haven't ordered yet",
  nowPlaying: '▶ Now playing: {song} (ordered by {by})', waitingRow: '{song} (ordered by {by})',
  mine: 'Mine', minePos: 'Mine · #{n}', del: 'Del', queueEmpty: 'No songs queued yet',
  searchPh: 'Search title/artist, or pinyin initials (e.g. fh cq)',
  fAll: 'All', fZh: 'Chinese', fEn: 'English', fAny: 'Any singer', fM: 'Male', fF: 'Female',
  fFav: '❤ Favorites', fRecent: '🕘 Recent',
  dancerTitle: '💃 Pick your dancer',
  dancerHint: 'Your dancer comes out only for songs you ordered. Tap anytime during your song to switch — the TV updates right away.',
  order: 'Order', dup: " (Note: it's already in the queue)",
  accBadge: 'Acc.',
  sugMore: 'More matches — keep typing to narrow it down',
  netErr: "Couldn't send — can't reach the TV. Check you're on the same Wi-Fi, or close this page and scan again.",
  tvRestarted: 'The TV app restarted — refreshing…',
  dancers: {modern: 'Amy', ancient: 'Xiuying', sumo: 'Sanshiro', ancient_girl: 'Hannah', baby: 'Nezha', robot: 'WALL-E'}
}
};
var ui = localStorage.getItem('mysing_lang') || 'zh';
if (ui !== 'en') ui = 'zh';
function t(k){ var d = I18N[ui] || I18N.zh; return (d[k] !== undefined) ? d[k] : (I18N.zh[k] || k); }
function fmt(s, map){ return s.replace(/\{(\w+)\}/g, function(m, k){ return map[k] !== undefined ? map[k] : m; }); }
function applyLang(){
  document.title = t('title');
  document.documentElement.lang = (ui === 'en') ? 'en' : 'zh-CN';
  document.querySelectorAll('[data-i]').forEach(function(el){ el.textContent = t(el.getAttribute('data-i')); });
  document.querySelectorAll('[data-iph]').forEach(function(el){ el.placeholder = t(el.getAttribute('data-iph')); });
  document.querySelectorAll('[data-dancer]').forEach(function(el){
    var k = el.getAttribute('data-dancer');
    var nm = CUSTOM_DANCER_NAMES[k] || t('dancers')[k];
    el.textContent = nm;
    var card = el.closest ? el.closest('.dcard') : null;
    var im = card ? card.querySelector('img') : null;
    if (im) im.alt = nm;
  });
  document.getElementById('langZh').classList.toggle('on', ui === 'zh');
  document.getElementById('langEn').classList.toggle('on', ui === 'en');
  qSig = '';
  if (name) { loadSongs(); loadQueue(); updateSuggest(); }
}
function setUi(l){ ui = l; localStorage.setItem('mysing_lang', l); applyLang(); }
document.getElementById('langZh').onclick = function(){ setUi('zh'); };
document.getElementById('langEn').onclick = function(){ setUi('en'); };
var name = localStorage.getItem('sk_name') || '';
var lang='', gender='', mode='all', favs=[], recents=[];
var myDancer = localStorage.getItem('sk_dancer') || 'modern';
var qOpen = true, qSig = '';
var bootId = '$bootId';
var reloading = false;
function netErr(){ document.getElementById('msg').textContent = t('netErr'); }
function paintDancers(){ document.querySelectorAll('[data-who]').forEach(function(c){
  c.classList.toggle('on', c.getAttribute('data-who')===myDancer); }); }
function show(){ document.getElementById('nameGate').classList.toggle('hidden', !!name);
  document.getElementById('main').classList.toggle('hidden', !name);
  document.getElementById('who').textContent = name ? ('（'+name+'）') : ''; if(name){paintDancers(); loadSongs(); loadQueue(); loadCatalog();} }
document.getElementById('enterBtn').onclick = function(){
  var v = document.getElementById('nameInput').value.trim();
  if(!v){ alert(t('nameAlert')); return; }
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
      row.innerHTML='<span>'+s.artist+' - '+s.title+'</span>' +
        (s.acc ? ' <span class="badge">'+t('accBadge')+'</span>' : '');
      var b=document.createElement('button'); b.textContent=t('order');
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
    body: JSON.stringify({id:id, name:name, lang:ui})}).then(function(r){return r.json()}).then(function(d){
    var m = d.message || d.error || '';
    if(d.duplicate) m += t('dup');
    document.getElementById('msg').textContent = m; loadQueue();
  }).catch(netErr);
}
// --- Search suggestions (v1.0.18) --------------------------------
// The whole catalog is fetched once and kept here; suggestions are
// then computed locally on every keystroke, so the first character
// already lists matches with zero network wait. The pure ranking
// function between the SUGGEST markers is exercised offline by
// tools/check_suggest.js against real Chinese song fixtures.
var allSongs = null;
function loadCatalog(){
  if(allSongs !== null) return;
  fetch('/api/songs').then(function(r){return r.json()}).then(function(d){
    allSongs = d.songs || []; updateSuggest();
  }).catch(function(){ allSongs = []; netErr(); });
}
// SUGGEST-BEGIN
var SUG_LIMIT = 20;
function suggestRank(songs, q, lang, gender){
  q = (q || '').trim().toLowerCase();
  if(!q) return {list: [], more: false};
  var qPin = q.replace(/\s+/g, '');
  var pinOk = /^[a-z0-9]+$/.test(qPin);
  var t0 = [], t1 = [], t2 = [];
  for(var i = 0; i < songs.length; i++){
    var s = songs[i];
    if(lang && s.lang !== lang) continue;
    if(gender && s.gender !== gender) continue;
    var title = (s.title || '').toLowerCase();
    var artist = (s.artist || '').toLowerCase();
    if(title.indexOf(q) === 0) t0.push(s);
    else if(title.indexOf(q) >= 0 || artist.indexOf(q) >= 0) t1.push(s);
    else if(pinOk && s.pin && s.pin.indexOf(qPin) >= 0) t2.push(s);
  }
  var all = t0.concat(t1, t2);
  return {list: all.slice(0, SUG_LIMIT), more: all.length > SUG_LIMIT};
}
// SUGGEST-END
function hideSuggest(){
  var el = document.getElementById('sug');
  el.classList.add('hidden'); el.innerHTML = '';
}
function updateSuggest(){
  var box = document.getElementById('sug');
  var q = document.getElementById('q').value;
  if(!q.trim() || !allSongs){ hideSuggest(); return; }
  var res = suggestRank(allSongs, q, lang, gender);
  if(!res.list.length){ hideSuggest(); return; }
  box.innerHTML = '';
  res.list.forEach(function(s){
    var row = document.createElement('div'); row.className = 'sugrow';
    row.textContent = s.artist ? (s.title + ' — ' + s.artist) : s.title;
    row.onclick = function(){ pickSuggest(s); };
    box.appendChild(row);
  });
  if(res.more){
    var hint = document.createElement('div'); hint.className = 'sugmore';
    hint.textContent = t('sugMore'); box.appendChild(hint);
  }
  box.classList.remove('hidden');
}
function pickSuggest(s){
  order(s.id);
  document.getElementById('q').value = '';
  hideSuggest();
  loadSongs();
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
    myNext >= 0 ? fmt(t('myNext'), {n: myNext + 1}) :
    (d.current && d.current.by === name) ? t('myPlaying') : t('myNone');
  var sig = JSON.stringify([d.current, waiting]);
  if(sig === qSig){ paintQPanel(); return; }
  qSig = sig;
  var el = document.getElementById('qlist'); el.innerHTML = '';
  if(d.current){
    var c = document.createElement('div');
    if(d.current.by === name) c.className = 'mine';
    c.textContent = fmt(t('nowPlaying'), {song: d.current.artist + ' - ' + d.current.title, by: d.current.by});
    if(d.current.by === name){
      var cb = document.createElement('span'); cb.className = 'badge'; cb.textContent = t('mine');
      c.appendChild(document.createTextNode(' ')); c.appendChild(cb);
    }
    el.appendChild(c);
  }
  waiting.forEach(function(e,i){
    var row = document.createElement('div');
    var mine = e.by === name;
    if(mine) row.className = 'mine';
    row.textContent = (i + 1) + '. ' + fmt(t('waitingRow'), {song: e.artist + ' - ' + e.title, by: e.by});
    if(mine){
      var b = document.createElement('span'); b.className = 'badge';
      b.textContent = fmt(t('minePos'), {n: i + 1});
      row.appendChild(document.createTextNode(' ')); row.appendChild(b);
    }
    row.appendChild(document.createTextNode(' '));
    var del = document.createElement('button'); del.textContent = t('del'); del.className = 'del';
    del.onclick = function(){ fetch('/api/remove',{method:'POST',headers:{'Content-Type':'application/json'},
      body: JSON.stringify({index:i})}).then(loadQueue).catch(netErr); };
    row.appendChild(del); el.appendChild(row);
  });
  if(!d.current && !waiting.length) el.innerHTML = '<div class="small">' + t('queueEmpty') + '</div>';
  paintQPanel();
}
function loadQueue(){
  fetch('/api/queue').then(function(r){return r.json()}).then(function(d){
    if(d.boot && bootId && d.boot !== bootId && !reloading){
      reloading = true;
      document.getElementById('msg').textContent = t('tvRestarted');
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
document.getElementById('q').addEventListener('input', function(){ mode='all'; if(allSongs === null) loadCatalog(); updateSuggest(); loadSongs(); });
document.querySelectorAll('[data-lang]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-lang]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); lang=c.getAttribute('data-lang'); mode='all'; loadSongs(); updateSuggest(); }; });
document.querySelectorAll('[data-g]').forEach(function(c){ c.onclick=function(){
  document.querySelectorAll('[data-g]').forEach(function(x){x.classList.remove('on')});
  c.classList.add('on'); gender=c.getAttribute('data-g'); mode='all'; loadSongs(); updateSuggest(); }; });
document.querySelectorAll('[data-who]').forEach(function(c){ c.onclick=function(){
  myDancer=c.getAttribute('data-who'); localStorage.setItem('sk_dancer', myDancer); paintDancers();
  fetch('/api/dancer',{method:'POST',headers:{'Content-Type':'application/json'},
    body: JSON.stringify({name:name, who:myDancer, lang:ui})}).catch(netErr);
}; });
document.getElementById('favTab').onclick=function(){ mode='fav'; hideSuggest(); loadSongs(); };
document.getElementById('recentTab').onclick=function(){ mode='recent'; hideSuggest(); loadSongs(); };
setInterval(loadQueue, 5000);
applyLang();
show();
</script></body></html>"""
    }

    /** JSON object literal for the page's CUSTOM_DANCER_NAMES
     *  (dancer key -> custom display name). Keys are the app's
     *  own fixed dancer keys; values are host-typed names and go
     *  through [jsEscape] so a quote or backslash in a name can
     *  never break the page's script block. */
    private fun customNamesJson(customNames: Map<String, String>): String {
        if (customNames.isEmpty()) return "{}"
        return customNames.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
            "\"${jsEscape(it.key)}\":\"${jsEscape(it.value)}\""
        }
    }

    private fun jsEscape(s: String): String = buildString {
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c.code < 0x20 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
}
