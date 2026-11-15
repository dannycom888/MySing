#!/usr/bin/env node
/**
 * Offline check of the phone page's search-suggestion ranking
 * (v1.0.18). The function under test is NOT copied here: it is
 * extracted verbatim from the shipped page source
 * (server/PhonePage.kt, between the SUGGEST-BEGIN / SUGGEST-END
 * markers), so what runs here is byte-for-byte what phones run.
 *
 * Fixtures use real library songs. Their `pin` values are the
 * pinyin-initials keys the server computes with the real Kotlin
 * table; the 张信哲 one is pinned against that table by
 * KaraokeHttpServerSocketTest, the rest match PinyinInitialsTest.
 *
 * Run: node tools/check_suggest.js   (exit 0 = all checks pass)
 */
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(
  path.join(__dirname, '..', 'app', 'src', 'main', 'java',
    'com', 'dannycom888', 'shieldkaraoke', 'server', 'PhonePage.kt'),
  'utf8');
const begin = src.indexOf('// SUGGEST-BEGIN');
const end = src.indexOf('// SUGGEST-END');
if (begin < 0 || end < 0 || end <= begin) {
  console.error('FAIL: SUGGEST markers not found in PhonePage.kt');
  process.exit(1);
}
const suggestRank = new Function(
  src.slice(begin, end) + '\nreturn suggestRank;')();

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
const titles = (r) => r.list.map((s) => s.title);

// The user's scenario: first character typed, matches already listed.
let r = suggestRank(SONGS, '爱', '', '');
check('first char 爱 hits 爱如潮水', titles(r).includes('爱如潮水'));

// Typing more narrows down progressively.
r = suggestRank(SONGS, '爱如', '', '');
check('爱如 still hits 爱如潮水', titles(r).includes('爱如潮水'));
check('爱如 narrows to just it', r.list.length === 1);
r = suggestRank(SONGS, '爱如潮水', '', '');
check('full title hits', titles(r).includes('爱如潮水'));

// Ranking: title-prefix beats title-substring (青花瓷 vs 致青春 for 青).
r = suggestRank(SONGS, '青', '', '');
check('青 ranks 青花瓷 (prefix) before 致青春 (substring)',
  titles(r).indexOf('青花瓷') === 0 && titles(r).indexOf('致青春') === 1);

// Pinyin initials still hit, with or without the space guests type.
r = suggestRank(SONGS, 'zxz', '', '');
check('pinyin zxz hits 张信哲', titles(r).includes('爱如潮水'));
r = suggestRank(SONGS, 'fh cq', '', '');
check('pinyin "fh cq" (with space) hits 凤凰传奇', titles(r).includes('最炫民族风'));
r = suggestRank(SONGS, 'fhcq', '', '');
check('pinyin fhcq hits 凤凰传奇', titles(r).includes('最炫民族风'));

// English titles match case-insensitively, prefix first.
r = suggestRank(SONGS, 'hel', '', '');
check('hel hits Hello', titles(r).includes('Hello'));

// The language/gender chips narrow suggestions like the song list.
r = suggestRank(SONGS, '爱', 'en', '');
check('lang=en hides the Chinese hit', r.list.length === 0);
r = suggestRank(SONGS, '爱', '', 'f');
check('gender=f hides the male-singer hit', r.list.length === 0);
r = suggestRank(SONGS, '爱', 'zh', 'm');
check('lang=zh,gender=m keeps the hit', titles(r).includes('爱如潮水'));

// Empty query lists nothing (no dropdown noise before typing).
r = suggestRank(SONGS, '  ', '', '');
check('blank query lists nothing', r.list.length === 0 && !r.more);

// Cap at 20 with the "more" flag driving the keep-typing hint row.
const many = [];
for (let i = 1; i <= 25; i++) {
  many.push({ id: 'm' + i, artist: '歌手' + i, title: '情歌第' + i + '首', lang: 'zh', gender: 'm', pin: 'gsqgd' + i + 's' });
}
r = suggestRank(many, '情', '', '');
check('25 matches cap at 20 with more=true', r.list.length === 20 && r.more === true);
r = suggestRank(many.slice(0, 20), '情', '', '');
check('exactly 20 matches: more=false', r.list.length === 20 && r.more === false);

if (failures) {
  console.error(failures + ' check(s) failed');
  process.exit(1);
}
console.log('all suggestion checks passed');
