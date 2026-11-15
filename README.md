# MySing

A local karaoke player for Android TV / NVIDIA Shield (also runs on phones, landscape). It plays your own MP3 files with enhanced (word-by-word) LRC lyrics: a cyan glowing current line with a gold sweep that fills each character smoothly from left to right. TV is controlled with the remote; guests order songs from their phones by scanning a QR code on the TV — over the home LAN only, no internet, no accounts.

Current version: **1.0.2** (versionCode 3). Per-version notes live on the [releases page](https://github.com/dannycom888/MySing/releases).

This software was made with Muse's assistance.（本软件由 Muse 协助制作）

## Install — start here

**Download the latest APK:** from the [releases page](https://github.com/dannycom888/MySing/releases) — pick the newest release.

1. Copy the APK to your Shield / TV box / phone and install it directly (allow "install from unknown sources" for your file manager if asked). **No KingInstaller needed** — that tool is only for Android Auto apps; MySing is a regular TV/phone app and sideloads normally.
2. Put your songs on a USB drive or external hard disk: each MP3 with a same-name enhanced `.lrc` file beside it, organised in the four category folders (Chinese male / Chinese female / English male / English female singers). Plain (line-only) LRC files also work — those lines just highlight as a whole, without the word sweep.
3. Plug the drive into the Shield, open MySing, and pick the folder that contains the four category folders. The library scans automatically (1,092 songs in the reference library).
4. Sing: pick by singer or by song title, or scan the QR code on the TV with a phone on the same home WiFi to order songs (enter your name first, so the "next up" banner can show who ordered it).

**Songs are not included.** MySing is a player only — it provides no music and downloads nothing. You bring your own MP3 + LRC files.

## Features

- Word-by-word karaoke sweep: gold fills each character gradually from left to right (not character-by-character jumps), on top of the cyan glowing current line
- Original vocal / accompaniment toggle (software vocal suppression — results vary by mix)
- Pitch shift by semitone while singing; resets for each new song
- Applause when a song finishes in full; "不唱了" (stop) skips to the next queued song
- One shared queue, strictly in order — no cutting in; the next-song banner at the top of the playback screen shows "下一首：某某点的 歌手 - 歌名"
- Phone ordering over the home LAN: QR code on the TV, no app install on the phone, works fully offline
- Per-song volume normalisation, favourites, recently sung, pinyin-initial search

## 中文说明

MySing 是一个本地卡拉 OK 播放器，为 Android TV / NVIDIA Shield 设计（手机横屏也能用）。播放你自己的 MP3 + 增强型 LRC 歌词：当前行整行青色发光，金黄色扫色在每个字内部从左到右平滑渐变填充。电视用遥控器操作；客人用手机扫电视上的二维码点歌 — 只走家里局域网，不联网、不用账号。

当前版本：**1.0.2**（versionCode 3）。各版本说明见 [Releases 页面](https://github.com/dannycom888/MySing/releases)。

本软件由 Muse 协助制作。

### 安装 — 先看这里

**下载最新 APK：** 到 [Releases 页面](https://github.com/dannycom888/MySing/releases)选最新版下载。

1. 把 APK 拷到 Shield / 电视盒子 / 手机上直接安装（系统若提示，允许文件管理器“安装未知应用”即可）。**不需要 KingInstaller** — 那是 Android Auto 应用专用的安装工具；MySing 是普通电视/手机应用，正常侧加载就行。
2. 歌曲放外接硬盘/U盘：每首 MP3 旁边放一个同名的增强型 `.lrc`，按四个分类文件夹放好（中文男歌手 / 中文女歌手 / 英文男歌手 / 英文女歌手）。普通（只有整行时间）的 LRC 也能用，只是那种歌词整行高亮、没有逐字扫色。
3. 硬盘插上 Shield，打开 MySing，选择“装着四个分类文件夹的那一层文件夹”，歌库自动扫描入库（参考歌库 1,092 首）。
4. 开唱：按歌手或按歌名点歌；也可以用手机扫电视上的二维码点歌（手机与 Shield 连同一个家里 WiFi，先输入你的名字，“下一首”横幅会显示是谁点的）。

**本软件不提供任何歌曲。** MySing 只是一个播放器，不含音乐、不下载任何东西；MP3 和 LRC 文件请自备。

### 功能

- 逐字渐变扫色：金色在每个字内部从左到右慢慢填满（不是整字跳变），底层是整行青色发光
- 原唱 / 伴奏切换（软件消音，效果随歌曲混音而异）
- 升降调：播放中按半音调，换歌自动归零
- 一首完整唱完有鼓掌声；点“不唱了”直接切到队列里的下一首
- 一个共享队列，严格按顺序，不插歌；播放界面顶部横幅显示“下一首：某某点的 歌手 - 歌名”
- 手机扫码点歌：走家里局域网，手机不用装 App，完全离线可用
- 每首歌音量自动均衡、收藏、最近唱过、拼音首字母搜索
