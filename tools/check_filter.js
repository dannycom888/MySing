#!/usr/bin/env node
/**
 * Offline check of the phone page's combined song filter
 * (v1.0.59). The function under test is NOT copied here: it is
 * extracted verbatim from the shipped page source
 * (server/PhonePage.kt, between the FILTER-BEGIN / FILTER-END
 * markers), so what runs here is byte-for-byte what phones run.
 *
 * Fixtures use real library songs, same as check_suggest.js.
 * The user's scenario: tap 中文 + 男歌手 and ALL Chinese
 * male-singer songs are listed at once — combined, uncapped.
 *
 * Run: node tools/check_filter.js   (exit 0 = all checks pass)
 */
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(
  path.join(__dirname, '..', 'app', 'src', 'main', 'java',
    'com', 'dannycom888', 'shieldkaraoke', 'server', 'PhonePage.kt'),
  'utf8');
const begin = src.indexOf('// FILTER-BEGIN');
const end = src.indexOf('// FILTER-END');
if (begin < 0 || end < 0 || end <= begin) {
  console.error('FAIL: FILTER markers not found in PhonePage.kt');
  process.exit(1);
}
const filterSongs = new Function(
  src.slice(begin, end) + '\nreturn filterSongs;')();

const SONGS = [
  { id: '1', artist: '张信哲', title: '爱如潮水', lang: 'zh', gender: 'm', pin: 'zxzarcs' },
  { id: '2', artist: '凤凰传奇', title: '最炫民族风', lang: 'zh', gender: 'f', pin: 'fhcqzxmzf' },
  { id: '3', artist: '周杰伦', title: '青花瓷', lang: 'zh', gender: 'm', pin: 'zjlqhc' },
  { id: '4', artist: '王菲', title: '致青春', lang: 'zh', gender: 'f', pin: 'wfzqc' },
  { id: '5', artist: 'Adele', title: 'Hello', lang: 'en', gender: 'f', pin: 'adelehello' },
];

let failures = 0;
function check(label, cond) {
  console.log((cond ? 'ok   ' : 'FAIL ') + label);
  if (!cond) failures++;
}
const titles = (list) => list.map((s) => s.title);
const sameSet = (a, b) => a.length === b.length && a.every((x) => b.includes(x));

// The user's example: 中文 + 男歌手 lists all Chinese male songs.
let r = filterSongs(SONGS, 'zh', 'm', 'all', [], [], '');
check('中文+男歌手 = 张信哲/周杰伦的歌', sameSet(titles(r), ['爱如潮水', '青花瓷']));

// Each axis on its own, and the other combinations.
r = filterSongs(SONGS, 'zh', 'f', 'all', [], [], '');
check('中文+女歌手 = 凤凰传奇/王菲的歌', sameSet(titles(r), ['最炫民族风', '致青春']));
r = filterSongs(SONGS, 'en', 'f', 'all', [], [], '');
check('英文+女歌手 = Hello', sameSet(titles(r), ['Hello']));
r = filterSongs(SONGS, 'en', 'm', 'all', [], [], '');
check('英文+男歌手 = 空', r.length === 0);
r = filterSongs(SONGS, '', '', 'all', [], [], '');
check('全部+不限 = 全部 5 首', r.length === 5);

// Favorites combine with the chips instead of replacing them.
r = filterSongs(SONGS, 'zh', '', 'fav', ['1', '5'], [], '');
check('收藏 ∩ 中文 = 只有爱如潮水', sameSet(titles(r), ['爱如潮水']));
r = filterSongs(SONGS, '', '', 'fav', ['1', '5'], [], '');
check('收藏（不限语言）= 爱如潮水 + Hello', sameSet(titles(r), ['爱如潮水', 'Hello']));

// Recent keeps its sung order and still honours the chips.
r = filterSongs(SONGS, '', '', 'recent', [], ['4', '2'], '');
check('最近唱过按唱过的顺序', titles(r).join() === '致青春,最炫民族风');
r = filterSongs(SONGS, 'zh', 'm', 'recent', [], ['4', '2'], '');
check('最近唱过 ∩ 中文男 = 空', r.length === 0);

// The search text joins the same filter (suggestions are a
// separate flow and are not touched by this).
r = filterSongs(SONGS, '', '', 'all', [], [], '青');
check('搜「青」命中青花瓷与致青春', sameSet(titles(r), ['青花瓷', '致青春']));
r = filterSongs(SONGS, 'zh', 'f', 'all', [], [], '青');
check('搜「青」∩ 中文女 = 致青春', sameSet(titles(r), ['致青春']));
r = filterSongs(SONGS, '', '', 'all', [], [], 'zjl');
check('拼音首字母 zjl 命中周杰伦', sameSet(titles(r), ['青花瓷']));

// No cap: a big combined result comes back whole (the library's
// 中文男 alone is ~380 songs — over the old 300-row slice).
const big = [];
for (let i = 0; i < 400; i++) {
  big.push({ id: 'b' + i, artist: '歌手' + i, title: '歌' + i, lang: 'zh', gender: 'm', pin: '' });
}
r = filterSongs(big, 'zh', 'm', 'all', [], [], '');
check('400 首中文男一首不少', r.length === 400);

console.log(failures === 0 ? 'ALL PASS' : failures + ' FAILURES');
process.exit(failures === 0 ? 0 : 1);
