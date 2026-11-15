# Shield 卡拉OK v1 构建报告

构建日期：2026-11-14（America/New_York）
规格来源：`~/workspace/goals/nvidia-shield-ok/GOAL.md`（用户逐条定版，含 22:16 追加的手机横屏试用要求）

## 产物

| 项 | 值 |
|---|---|
| APK（工程根目录副本） | `~/workspace/shield-karaoke/Shield-Karaoke-1.0.0.apk` |
| APK（Gradle 原始输出） | `app/build/outputs/apk/release/app-release.apk` |
| 大小 | 10,337,787 字节（约 9.9 MB） |
| SHA-256（前 32 位） | `1cc70aae017febbf3fc5af9d1740cc53` |
| 包名 | `com.dannycom888.shieldkaraoke` |
| 应用名 | Shield 卡拉OK |
| versionName / versionCode | 1.0.0 / 1 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| 签名 | 已用与 MyLRC Auto 同一把共享 keystore 签名（`keystore.properties` 指向的 aalyrics-release.keystore，别名 aalyrics-local）；`apksigner verify` 通过，证书 CN=AALyrics Local，SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc` |
| 权限清单 | INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE（均只为局域网点歌服务：开 HTTP 服务 + 读本机局域网地址；App 不访问任何外网服务）、AndroidX 自动添加的 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION |

## 单元测试

`:app:testDebugUnitTest` —— **30 个测试，0 失败，0 错误，0 跳过**。

| 测试类 | 数 | 覆盖 |
|---|---|---|
| LibraryScannerTest | 5 | MP3+同名 LRC 配对、分类由文件夹决定、只认文件名不认标签、无 LRC 也收录、重扫增删生效、四夹之外的文件忽略、组合放 girl 文件夹照文件夹归类 |
| CatalogTest | 5 | **凤凰传奇验收例子**（假数据复现：凤凰传奇在 girl 文件夹，选中文→歌手能找到，且英文不串味）、首页歌手全合并、男/女歌手子筛选、按歌手取歌、拼音首字母+文字搜索 |
| PinyinInitialsTest | 3 | 凤凰传奇→fhcq、周杰伦→zjl、最炫民族风→zxmzf、ASCII 透传、首字母子串匹配 |
| PlayQueueTest | 8 | 首点即当前、点歌绝不打断正在唱、下一首提示文案格式、空队列无提示、重复点提醒、前面还有 X 首计数、唱完/不唱了推进、共用队列谁都能删（当前歌曲不可删） |
| LrcParserPortTest | 5 | 移植版解析器行为与 MyLRC Auto 一致：普通 LRC、增强 LRC 逐字时间、WordSweep 扫色计数、非法秒数标签拒绝、当前行二分查找 |
| AudioMathTest | 4 | 半音系数（含钳位）、音量均衡增益（拉向 -16 dBFS、±钳位、无测量值时单位增益）、dB→线性 |

拼音首字母表由真实歌库 1,092 首文件名离线生成（pypinyin），覆盖歌库实际出现的 1,154 个汉字，见 `library/PinyinData.kt`。

## 手机兼容自查（22:16 追加规格）

- ✅ `android.software.leanback` 声明 `required="false"`，`android.hardware.touchscreen` 同样 `required="false"` —— 不是 leanback-only，普通手机可直接安装（aapt2 badging 显示 `uses-feature-not-required: android.software.leanback`）。
- ✅ 双入口：清单同一 Activity 同时带 `LAUNCHER` 与 `LEANBACK_LAUNCHER`（badging 同时列出 launchable-activity 与 leanback-launchable-activity）；Shield 上进 TV 桌面，手机上进普通桌面。
- ✅ 横屏：Activity `screenOrientation="landscape"`（二进制清单值 0 = landscape 已核）。
- ✅ 小高度适配：界面按 `maxHeight < 500dp` 切紧凑档（Note10+ 横屏约 388dp 高）：首页入口卡 54dp、歌词当前行 27sp、控制条小字号，全部在紧凑档下完整排布；浏览/播放均用可滚动列表，不溢出。
- ✅ 操作双支持：所有可点元素同时支持触摸点击与方向键焦点（focusable + 焦点描边），手机触摸、Shield 遥控器都能用。
- ⚠️ 以上为静态/清单层自查；真机（Note10+、Shield）安装运行的实测仍需用户完成，本次构建环境无真机。

## 功能对照规格（GOAL.md）

- ✅ 纯本地 KTV 播放器，不处理麦克风；Media3/ExoPlayer 播 MP3。
- ✅ 歌库在外接硬盘，经 SAF 选文件夹后自动扫描四分类文件夹（可在任意层级下找到四夹）；打开 App 自动扫、新歌自动入单、删掉的自动移除；首页有“重新扫描”。
- ✅ 歌词同名匹配、只认文件名；LrcParser + WordSweep 整文件移植自 MyLRC Auto（仅改包名）；增强 LRC 青色发光行 + 金黄 #FFD54F 逐字扫色，普通 LRC 整行高亮。
- ✅ 首页入口并列：按歌手 / 按歌名 / 中文 / 英文 / 收藏 / 最近唱过；筛选合并规则与凤凰传奇验收例子已单测。
- ✅ 组合不单分：照文件夹归类，无组合文件夹/筛选。
- ✅ 播放控制只归遥控器：暂停/继续、重唱、原唱/伴奏（声道抵消 AudioProcessor，可实时切换）、升降调（半音，±12 钳位，切歌复位原调）、音量自动均衡（MediaCodec 测 RMS → LoudnessEnhancer 拉齐，增益计算有单测，结果按歌缓存）、不唱了（二次确认弹窗）；按键显示控制条、4 秒自动隐藏；播放中可离开去点歌、音乐不停，首页有“正在唱”入口返回。
- ✅ 队列规矩：点歌只入队不打断；上方提示条格式“下一首：{点歌人}点的 {歌手} - {歌名}”；唱完先播鼓掌（约 3.2 秒）+ 5 秒换人倒计时再接下一首；不唱了无鼓掌；没人点歌唱完回首页；无插歌、无打分（代码里不存在这两种功能）。
- ✅ 手机扫码点歌：首页二维码 = `http://{Shield 局域网IP}:8931/`（ZXing 生成，IP 动态取本机局域网地址）；内置 HTTP 服务（ServerSocket 实现，无第三方服务器依赖）；手机同 WiFi 打开网页免装 App；进页必填名字（localStorage 记住本机），点的歌带点歌人；搜索（文字+拼音首字母）、已点顺序、删歌（共用队列）、收藏/最近唱过只在手机页；点歌成功提示“已加入，前面还有 X 首”，重复点有提醒；电视不显示搜索与完整队列，只有下一首提示。
- ✅ 软件说明带“本软件由 Muse 协助制作”（首页底部 + 代码注释）。

## 鼓掌音效来源

`app/src/main/assets/applause.wav`（141,164 字节，3.2 秒）为**程序自合成**：Python 脚本以带通噪声脉冲 + 衰减包络叠加多组拍手声生成，不含任何第三方录音素材，无版权问题。

## 我按 KTV 惯例/规格精神自定的小决定（未停下来等批，清单如下）

1. 电视端点歌的点歌人记为“电视”（下一首提示显示“电视点的 …”），因为电视端没有输名字环节。
2. 升降调切歌自动复位原调（0 半音），与 GOAL.md 里预记的默认一致。
3. 唱完换人间隙定为 5 秒倒计时，期间全屏显示下一首预告 + 鼓掌。
4. 没有同名 LRC 的 MP3 也收录进歌单（能唱、只是没歌词滚动），而不是丢弃——歌库现状是 1,092 对齐全，此条只影响以后新加的无词文件。
5. 文件名分隔符兼容 " - "、" – "、" — " 与无空格的 "歌手-歌名"；无分隔符时整名当歌名、歌手为空。
6. 音量均衡目标 -16 dBFS RMS，增益钳位 +10/-12 dB；响度只测每首前 45 秒并按歌缓存，测不到就单位增益。
7. 手机点歌服务端口 8931；二维码在每次进首页时按当前局域网 IP 重新生成（IP 变了扫码仍有效）。
8. 收藏/最近唱过在电视端为首页入口，在手机端为列表页签，两边共用同一份本地数据（SharedPreferences）。
9. 应用图标/横幅为程序生成的简单位图（深蓝底金色音符），正式美术可后续替换，不影响功能。
10. 歌库文件夹通过 SAF 选择（Shield/Android TV 访问外接硬盘的标准方式），选一次后持久化授权，后续打开自动扫描。

## 已知限制 / 待真机验证

- 人声抑制效果随歌曲混音而异（声道抵消的固有限制，立项时已向用户说明）。
- 升降调使用 ExoPlayer PlaybackParameters 的 Sonic 变调，实际音质需 Shield 真机试听。
- HTTP 服务为明文局域网（家庭内网场景）；手机需与 Shield 同一 WiFi（访客网络隔离时扫码打不开，属网络层限制）。
- 本次未做仪器测试（无真机/模拟器）；UI 行为以单测覆盖的纯逻辑 + 编译通过为准。

## v1.0.1 改名+图标（2026-11-14）

只改名与图标，功能代码零改动。

| 项 | 值 |
|---|---|
| APK | `~/workspace/shield-karaoke/MySing-1.0.1.apk` |
| 大小 | 10,432,843 字节 |
| SHA-256 | `f81466a88788366b44b4697db545e51e…`（前缀 f81466a88788366b） |
| 包名 | `com.dannycom888.shieldkaraoke`（不变） |
| 应用名 | **MySing**（原“Shield 卡拉OK”） |
| versionName / versionCode | 1.0.1 / 2 |
| 签名 | 同一把共享 keystore；`apksigner verify` 通过，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与 v1.0.0 完全一致 |
| 单元测试 | `:app:testDebugUnitTest` 30 个，0 失败、0 错误、0 跳过（与 v1.0.0 相同） |

改名覆盖位置：`strings.xml` 的 app_name、TV 主屏标题（MainActivity）、手机点歌网页 `<title>` 与 `<h1>`（PhonePage）、KaraokeApp 类注释；代码内已无“Shield 卡拉OK”字样（包名与主题名 Theme.ShieldKaraoke 为内部标识，未动）。

图标接入方式（定稿图 `icon/mysing-icon-1024.png`，重跑 draw_icon7.py 后 SHA-256 与定稿一致、未漂移）：
- 自适应图标：`mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml`，背景纯色 `@color/ic_launcher_background` = #051E3C；前景为 draw_icon7.py 新增输出的透明底版本 `icon/mysing-foreground.png`（参数与定稿图完全一致：M 斜体 SCALE 2.6 / K=0.21、麦克风 h360 / ang −44 / center 570,540、同一套 glow；图案已在安全圆内，整图铺满前景画布、不再额外放大），缩放为 432×432 放 `drawable-xxxhdpi/ic_launcher_foreground.png`。
- 旧版 mipmap：用定稿图缩放生成 mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192 的 `ic_launcher.png` 与 `ic_launcher_round.png`（圆版同图）。Manifest 改为 `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"`，旧的 `drawable/app_icon.png` 已删除。
- 电视横幅：`drawable/app_banner.png`（320×180）已重做——同款深蓝底 + 定稿图标 + 白色粗体“MySing”字样。
- APK 内校验：aapt badging 显示 label='MySing'、icon 指向自适应 XML、banner 为 320×180；各密度 mipmap 与 432×432 透明前景均在包内。

## v1.0.2 字内渐变扫色

- 产物：`MySing-1.0.2.apk`，10,432,842 字节，SHA-256 `8695e5f95ad23f77`（完整值 8695e5f95ad23f77b30e911327c388a28a6b0f338a708f55dc38a702cca5a08d）。
- 版本：aapt badging 实测 package `com.dannycom888.shieldkaraoke`、versionName 1.0.2 / versionCode 3、application-label 'MySing'。
- 签名：apksigner verify 通过，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与 v1.0.0/v1.0.1 逐字一致——同一把共享 keystore，可直接覆盖安装。
- 测试：单元测试 37 个（原 30 + 新增 SweepProgressTest 7），0 失败 / 0 错误 / 0 跳过。
- 渲染实现：当前行先整行以青色发光底绘制，再把同一行金色（#FFD54F）文本叠加绘制并用 Canvas clip 裁到扫色分界线 x——分界线 = 已唱完字符的整宽 + 正在唱的字符按其自身时间窗进度 p 的字内偏移（`SweepProgress.progress(nowMs, startMs, endMs)` 纯函数，p 夹 0..1；字的结束时间 = 下一个字的开始时间，沿用既有逐字时间轴推导，行尾最后一字由结束标记界定、无界定时按旧逻辑整字点亮）；播放位置轮询从 200ms 提速到 33ms（约 30fps），分界线肉眼连续右移，不再逐字整字跳变。普通 LRC（无逐字标签）行为完全不变：整行青色、无扫色。其余功能零改动。

## GitHub 发布 + AAB

2026-11-14 夜，用户明确授权后正式发布到 GitHub，并顺手备好 Google Play 用的 AAB。

- 仓库：https://github.com/dannycom888/MySing （public，分支 main，首个提交 `0ffdcf8`）。源码参照 MyLrcAuto 仓库的 .gitignore 与 README 结构：双语 README 顶部即安装说明（从 Releases 下载 APK 直接侧加载，**不需要 KingInstaller**），注明歌曲文件自备、本软件不提供任何歌曲，署名行“本软件由 Muse 协助制作”。
- 安全自查（提交前 `git ls-files` 全量核对）：跟踪文件 66 个，**无** `*.keystore`、`*.jks`、`keystore.properties`、`local.properties`、APK/AAB 被跟踪——签名文件由 .gitignore 拦下（`git check-ignore` 实测命中），keystore 本体在工程外（`~/workspace/aalyrics-local/keys/`，从未进工程目录）；APK 只走 Release 附件，不进源码仓。
- Release：https://github.com/dannycom888/MySing/releases/tag/v1.0.2 （tag v1.0.2，附件 `MySing-1.0.2.apk`，说明含 v1.0.2 字内渐变扫色、v1.0.1 定名+图标、v1.0.0 首版功能清单与安装提示）。
- API 回读校验：GitHub API 返回 asset size `10432842`、digest `sha256:8695e5f95ad23f77b30e911327c388a28a6b0f338a708f55dc38a702cca5a08d`，与本地 APK 的字节数与 SHA-256 **逐字一致**。
- AAB：`:app:bundleRelease` 构建成功（`signReleaseBundle` 已执行），产物存为 `~/workspace/shield-karaoke/MySing-1.0.2.aab`，9,967,363 字节，SHA-256 `2939b8bc4729b816`（完整值 2939b8bc4729b81645e1999ec00d6c4a7cf6b3983ef661a2195b30ad10d18302）。版本校验方法：bundletool 1.18.3（Gradle 缓存中的 jar，以 BundleToolMain 主类方式运行）`dump manifest` 实测 AAB 内 versionName `1.0.2` / versionCode `3`。签名校验：`keytool -printcert -jarfile` 显示证书 SHA-256 `13:36:EE:0A:…:2B:EF:CC`，即 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与 APK 同一把共享 keystore。
