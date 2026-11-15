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

## v1.0.3 跳舞小人 + 播放故障自愈

构建日期：2026-11-15（America/New_York）。本版两大块：①播放页跳舞小人（六人，节拍驱动）；②针对用户真机截图（v1.0.2 点歌后能进播放页、歌词能读出，但按钮显示"▶ 继续"= 未播放、没声音，且控制条约 2–4 秒自动消失）的播放故障自愈 + 自诊断。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.3.apk` |
| 大小 | 11,169,655 字节 |
| SHA-256 | `747a5ac7c4b5aa995cd6130e6b06f7e96cb81a7ed8668a240ff59d253a0d4723`（前缀 `747a5ac7c4b5aa99`） |
| versionName / versionCode | 1.0.3 / 4（aapt badging 实测，label 'MySing'） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **57 个，0 失败**（原 37 + 新增 20：BeatDetectorTest 4、FallbackStateMachineTest 3、DanceMotionTest 8、BabyFallTest 5） |

### 播放故障修复（单列）

根因未定（自定义 AudioSink/人声处理器链、播放器错误被吞、增益异常都在嫌疑内），本版按"自愈 + 自诊断"处理：

- **错误自愈**：`PlayerController` 新增 `Player.Listener.onPlayerError`。出错时自动用**不带自定义 AudioSink 的默认渲染器**重建播放器并从出错位置重播当前歌，只重试 1 次（重试状态机抽为纯类 `FallbackStateMachine`：CUSTOM → PLAIN_RETRY → FAILED，每首新歌重置，并有单测）。重试后若用户切到伴奏， plain 链路没有人声处理器，不崩、诊断行显示"伴奏不可用（已降级普通播放）"。
- **诊断行常驻**：播放页歌词区下方一行小字，永远显示：播放器状态（IDLE/BUFFERING/READY/ENDED）、position/duration 秒、isPlaying、sink 路径（`sink=custom` / `plain-retry` / `plain-failed`）、当前 BPM、当前舞者及点歌人。若发生播放错误，错误信息（`PlaybackException.errorCodeName` + message 首行）以橙红色单独一行显示在诊断行上方，**不自动消失**，供用户截图定位。
- **控制条永不自动隐藏**：按用户拍板（唱一半常要变调/切原唱伴奏，控制条必须随时够得着），删除 `showControls` 的 4 秒自动隐藏逻辑与全部隐藏路径，控制条在播放页常驻。
- **音频焦点**：播放前按音乐流请求音频焦点（`AudioFocusRequest`，USAGE_MEDIA / CONTENT_TYPE_MUSIC），`release()` 时放弃。
- **人声处理器保险**：`VocalCancelAudioProcessor.queueInput` 的 DSP 全程 try/catch，异常时把原始数据原样透传，处理器永远搞不死播放。

### 跳舞小人

- **阵容六人**（左→右）：现代女（金色长发、白紧身短袖+白超短裙、黑高跟短靴）、古代女（粉色**薄纱**汉服，层叠半透明纱袖纱裙）、现代女孩（粉卫衣双马尾）、古代女童（黄汉服双丫髻）、小婴儿（只穿白色纸尿裤、站姿学步）、小机器人（银白圆身、发光蓝眼、天线，**成人身高**与两位成年女子齐平）。
- **抠图方式**：六人分别取自用户逐版定稿图（现代女/古代女/两小女孩基底 v3、古代女薄纱版 v9、婴儿站姿版 v8、机器人成人高版 v7）。rembg（u2net）出全图 alpha 后，用逐种子单独计算的测地线代价（geodesic Voronoi，代价 = 255 − alpha）按人物归属切分，再用腐蚀取芯 + 最近芯归属清除粘连残片；个别粘连（古代女袖片、女童发髻、婴儿身后的现代女孩）用定点切除区/颜色规则/解析轮廓（婴儿：头圆+躯干椭圆+腿矩形+手臂胶囊）收尾。古代女薄纱保留 rembg 的半透明 alpha（纱的部分半透、主体核心强制不透明），融进深色背景正好。全部素材保留完整 1280 画布高、统一缩放至高 600px 存 `app/src/main/res/drawable-nodpi/dancer_*.png`（单张 65–195 KB），因此六人相对身高与共同脚基线天然一致（机器人按成人档、婴儿最矮）。已知小瑕疵：婴儿头顶与机器人左胯各有极少量异色像素残留，动态播放时不可见。
- **摆放**：播放页最底层、歌词层之下，底部居中；alpha 0.95，不压暗。歌词渲染逻辑零改动。
- **伴舞归属（按点歌人）**：没有全局选择——手机点歌页新增"选伴舞"区（六个按钮、当前选中高亮，文案"你选的伴舞，只在你点的歌时出来"），点选经 `POST /api/dancer`（JSON `{name, who}`，另兼容 `POST /dancer?name=X&who=robot` 查询参数形式）按**点歌人名字**存入 prefs（同名以最新为准）。播放某首歌时，出来的舞者 = 这首歌的点歌人所选的那位；点歌人中途改主意，电视实时换人（服务端 setter 直接 bump 状态版本触发重组）。电视点的歌（点歌人="电视"）或查不到选择时默认现代女。手机页用 localStorage 记住自己的选择并回显，`/api/queue` 回带全量 `dancers` 映射供页面同步高亮。电视控制条只保留"💃 小人开/关"开关（默认开、持久化），不再有选人 chip。
- **单个伴舞放大**：播放中只出选中的一位，高度约屏幕高 50%（手机横屏 compact 约 44%）。
- **动画（节拍驱动，纯函数 + 单测）**：`DanceMotion.pose()` 是播放位置的纯函数（phase = positionMs / 每拍毫秒），暂停即冻住、seek 不错拍，帧刷新复用现有 33ms 位置轮询。动作 = 上下弹跳（|sin|）+ 左右摇摆（±3–5°）+ 落拍缩放脉冲，四人相位按索引错开 0/0.25/0.5/0.75 拍；现代系弹跳为主、古代系摇摆为主；慢歌（<80 BPM）弹跳幅度降一档。
- **婴儿加戏**：平时站不稳的晃（摇摆幅度比别人大、弹跳频率 ×1.5）；不时摔一跤——摔倒周期为纯函数 `BabyFall`（输入拍数/BPM → 姿态）：每 12–20 拍摔一次，间隔由周期序号哈希推导（伪随机、不整齐）；约 0.3 秒侧倒（旋转至 ~84° + 下沉）、躺平 1 拍、约 0.4 秒弹起站好（带过冲回摆 + 缩放回弹）。单测验：周期内必摔、倒下/躺平/起身三段时序合法、躺平姿态角 ≥70°、确定性。
- **机器人机械舞步**：相位量化为 1/4 拍阶梯（角度/弹跳/缩放查表跳变），无正弦平滑；鼓掌时同样用顿挫式。
- **唱完一起鼓掌**：一首歌完整唱完（"不唱了"退出不鼓掌，沿用旧规矩）后，六人**全体出场**排成一排做鼓掌动作（固定 300ms 鼓掌节拍、小幅快速弹跳 + 左右倾摆、六人整齐同步），与 3.2 秒掌声音效同时开始。有下一首：在 5 秒换歌间奏画面上鼓掌，掌声放完（~3.2s）即收；没下一首：先在播放页放完这段鼓掌再回主页，不被切屏吞掉。鼓掌环节与伴舞归属无关。
- **BPM 测速**：`BeatDetector`（纯函数）：对能量包络做 onset-flux 自相关，滞后范围对应 60–160 BPM，取峰值后用低份额阈值（≥0.4×峰值）防八度（160 不报 80）。单测以合成点击序列验 60/96/120/160 BPM，误差均 ≤3（160 测得钳制 160、96 测得 ~96.8）。`TempoAnalyzer` 仿 `LoudnessAnalyzer` 用 MediaCodec 解码（最长 60 秒、100 fps RMS 包络）实测，结果按歌缓存在 prefs（`bpm_<songId>`，与 loudness 并列）；在 startSong 时与响度测量并行、互不依赖地跑（响度已缓存也照样测速）。测不出/未测时默认 96 BPM。

### 边界

扫色、队列、点歌、伴奏 DSP（除 try/catch 保险）、升降调、掌声音效、下一首横幅逻辑均未改动。HOME/BROWSE 不显示小人；普通暂停时小人随位置冻结。

- GitHub 发布：提交 `572c7c9` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.3 （附件 `MySing-1.0.3.apk`）。API 回读校验：asset size `11169655`、digest `sha256:747a5ac7c4b5aa995cd6130e6b06f7e96cb81a7ed8668a240ff59d253a0d4723`，与本地逐字一致。`dancers/` 抠图工作夹未进仓；keystore/APK 仍由 .gitignore 拦截。

## v1.0.4 开局闪退紧急修复（2026-11-15）

构建日期：2026-11-15（America/New_York）。v1.0.3 真机一打开就闪退，本版只修这一个 bug，功能与 v1.0.3 完全一致，不加新东西、不改行为。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.4.apk` |
| 大小 | 11,169,663 字节 |
| SHA-256 | `ce5591f6d4737082be91394deec9f4101f45d177e78d60afb5d8b4b718901c04`（前缀 `ce5591f6`） |
| versionName / versionCode | 1.0.4 / 5（aapt badging 实测，label 'MySing'） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **57 个，0 失败**（与 v1.0.3 相同） |

### 根因（初始化顺序 NPE）

`audio/PlayerController.kt` 属性初始化顺序错误：

- 第 52 行 `var player: ExoPlayer = buildPlayer(customSink = true)` 在对象构造时就执行 `buildPlayer()`；
- `buildPlayer()` 里 `built.addListener(listener)` 用到的 `listener` 声明在第 86 行，位于 `player` **之后**——构造到 `player` 时 `listener` 字段尚为 null，`addListener(null)` 抛 NullPointerException；
- `PlayerController` 是在 `MainActivity.onCreate` 里构造的，于是 App 一打开、构造即崩，表现为闪退。v1.0.3 新增的播放自愈（`onPlayerError` 监听器）正是这次把 `listener` 引入初始化链的改动，单测不构造真 ExoPlayer 所以没测出来。

### 修法

- 把 `listener` 与 `fallback`（原第 55 行，同样声明在 `player` 之后）一并移到 `player` 属性**之前**声明，并在代码里留了注释说明顺序约束。`player` 初始化链现在只引用声明在前的 `vocalProcessor`、`listener`、`fallback`；其余声明在后的属性（`loudnessEnhancer`、`onEnded`、`currentUri`、`audioManager` 等）只在方法/回调运行时读取，不参与初始化。
- **全仓同类模式扫描**：逐一核对全部 Kotlin 文件的类级/对象级属性初始化器（重点 v1.0.3 新增/改动的 `audio`、`state`、`ui`、`server`）——其余初始化器均为字面量、`const val`、构造参数、无参构造（`Catalog(emptyList())`、`PlayQueue()` 等）或字面量默认值的 delegate，未发现第二处同类问题。
- GitHub 发布：提交 `2cb333a` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.4 （附件 `MySing-1.0.4.apk`）。API 回读校验：asset size `11169663`、digest `sha256:ce5591f6d4737082be91394deec9f4101f45d177e78d60afb5d8b4b718901c04`，与本地逐字一致。

## v1.0.5 姿势帧真跳舞 + 小人左移加大 + 歌库缓存（2026-11-15）

构建日期：2026-11-15（America/New_York）。三件事：①伴舞从"单张图整体晃动"升级为每人 10 种姿势帧真跳舞；②小人移到歌词左侧并加大；③选歌画面歌手/歌曲出现慢——加歌库缓存两段式加载。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.5.apk` |
| 大小 | 12,788,220 字节（目标 <16MB；60 张姿势帧 WebP 共约 1.8MB） |
| SHA-256 | `926f432c8c00576a06c66cea0c401888cda1685e939aa89111f8516e63f85a39`（前缀 `926f432c`） |
| versionName / versionCode | 1.0.5 / 6（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **70 个，0 失败**（57 旧 + PoseCycle 7 + LibraryCache 6） |

### 姿势帧（每人 10 种）

- 六个角色各 10 个不同舞蹈姿势（2 张生成表 × 5 姿势），逐张肉眼自检：形象与定版一致、姿势互不相同、无畸形。古代女 B 表首版第 5 个姿势生成成了小女孩（串角色），已重生成 B2 表（五人全为成年古代女），管线只用 B2。
- 抠图归一化：同角色 10 帧共用同一缩放（最高帧内容高映射到该角色 v1.0.3 的相对内容高）、同一 600px 画布、脚底同一基线，切换不跳位。资源为 WebP（q93）`dancer_<key>_01..10`，PNG 母版留在 `dancers/poses/frames_png/`。
- 抠图分离方法换代：旧法（列谷值切分定种子）在一张表上把切分线落进了第 4 人身体里。现改为**强 alpha 连通域定种子**：两人物粘连成一个连通域时，先按"两块面积相当"原则做渐进腐蚀分裂（防止在手腕处假分裂），仍分不开（薄纱大面积交叠）再按该连通域自身的质量谷值切分种子；测地线归属（alpha 加权代价）随后刻出真实边界。另加守卫：任一帧 alpha 质量低于同角色中位数的 35% 直接报错停产，不再静默出坏帧。
- **现代女 A 表一处外科修补**：第 4 人的上举前臂伸进第 3 人发梢，rembg 在两者间桥接出不透明 alpha，任何测地线变体都把前臂判给第 3 人（第 3 帧长出一条无主手臂、第 4 帧丢臂）。定版修法：在该表的副本上把这条前臂+手按背景藏青色涂除（多边形+指尖椭圆，坐标记在 `cutout_poses.py` 的 CHARS 注释里），第 4 人保留上臂——视觉上是手臂举到发后，成立；第 3 帧恢复干净。管线 CHARS 已指向修补版（`poses-modern-woman-apatched`）。
- 残余小瑕（接受）：婴儿第 3 帧左缘、机器人第 7 帧右下一块极淡的地板阴影残迹（低 alpha，舞台深色背景上不可见）。

### 帧切换规则（PoseCycle）

- 纯函数 `ui/PoseCycle.kt`：帧序号只由播放位置 + BPM 推导，**每半拍前进一帧**、顺序循环 1→10→1；与 DanceMotion 同一节拍相位（暂停冻结、seek 对齐）。机器人不做插值，帧格进本身即顿挫；婴儿摔跤逻辑不变（对当前帧做旋转倒地）。
- 整体弹跳/摇摆保留但幅度约减半（帧本身已在动）：真人弹跳 1.0/0.45→0.55/0.28、摇摆 3°/5°→2°/3.5°、缩放脉动 0.05/0.03→0.03/0.02；机器人弹跳表 (0,1,0.35,0)→(0,0.55,0.2,0)（旋转 ±5° 不变）；婴儿舞蹈弹跳 0.5→0.35、旋转 7/2.5→5/1.8。慢歌（<80 BPM）降幅规则沿用。
- 鼓掌环节不变：仍是六人一排用原单帧抠图 + 拍手动作，本次未做鼓掌姿势帧。

### 位置左移 + 加大

- 播放页舞台改为 Row：左侧区（屏宽 32%）放伴舞，锚定左区底部、控制条上方；歌词列占其余宽度。间奏/鼓掌排的覆盖方式不变。
- 伴舞高度：**屏高 70%（普通布局）/ 62%（紧凑布局，手机横屏）**——用户追加要求（原定 55%/46% 嫌小）；左区宽度经最宽帧（机器人 T 姿势画布宽高比约 0.70）核算，70% 高度下不侵入歌词列。

### 歌库缓存（选歌秒出）

- 新增 `library/LibraryCache.kt`：纯编解码 `LibraryCache`（首行 `MSING-CATALOG-CACHE \t 1 \t <转义后的 treeUri>`；每首一行制表符分隔：id/歌手/歌名/分类/音频引用/歌词引用；反斜杠转义；解码容错——版本/树 URI 不符或文件损坏一律视为无缓存，坏行跳过）+ `LibraryCacheStore`（filesDir `catalog_cache.txt`，临时文件+重命名原子写，任何失败都当无缓存）。
- 两段式加载：启动先读缓存立即填充歌曲列表（歌手/歌曲秒出），随后照常后台全量扫描，扫完整体替换并回写缓存（新增/删除仍在扫完后生效）；所选文件夹（treeUri）变了旧缓存自动作废。手动"重新扫描"行为不变；首次无缓存时沿用现有"正在扫描歌库…"提示。
- 编译期小修：`PoseCycle` 返回式多余括号、`MainActivity` 补 `fillMaxHeight` import 各一处。

### 发布

- GitHub 发布：提交 `e174a6f` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.5 （附件 `MySing-1.0.5.apk`）。API 回读校验：asset size `12788220`、digest `sha256:926f432c8c00576a06c66cea0c401888cda1685e939aa89111f8516e63f85a39`，与本地逐字一致。

## v1.0.6 二维码常驻 + 手机已点歌单 + 尾奏静止 + 帧速减半（2026-11-15）

构建日期：2026-11-15（America/New_York）。四件事：①二维码始终出现在电视屏幕上；②手机点歌页下拉看全部已点歌和自己的顺序；③歌词唱完后小人停跳（尾奏静止）；④小人换动作太快，姿势帧改为每拍一帧。其余功能一律未动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.6.apk` |
| 大小 | 12,788,221 字节 |
| SHA-256 | `4abd814035eb055fd86e8e67578fad7b28b104a6252fd60555276387146a9813`（前缀 `4abd8140`） |
| versionName / versionCode | 1.0.6 / 7（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **83 个，0 失败**（70 旧 + DancerActivity 8 + PhonePage 5；PoseCycle 7 个改一拍口径、数量不变） |

### 二维码常驻（角落小码）

- 根布局（`MainActivity` 的 `BoxWithConstraints`）加覆盖层 `CornerQrCard`：首页、选歌页、播放页（含换歌间隙与鼓掌画面，都在播放页内）始终显示；内容仍是本次启动的局域网点歌地址，沿用 `Qr.encode` / `Qr.lanIpAddress()` 既有逻辑。
- **位置**：全局统一右下角——end 间距 24dp（紧凑 12dp）、bottom 偏移 **88dp（紧凑 70dp）**，正好让开播放页底部常驻的诊断行 + 控制条；选歌页同位置。
- **尺寸/形态**：二维码本体 **92dp 见方（紧凑/手机横屏 72dp）**，白色圆角卡（圆角 10dp、内边距 7/5dp）+ 1dp 半透明深色描边（黑 45%）+ 下方小字“扫码点歌”（11/9sp）；二维码本身黑白对比不动，保证扫得出。
- **不重复**：首页头部已有大二维码时不显示小码（`screen != HOME` 才挂小码）；无局域网地址时大小码都不显示。
- **不可聚焦**：卡片不带任何 clickable/focusable 修饰，D-pad 焦点不会落上去。
- 选歌页两个列表（歌手/歌曲）加底部 `contentPadding` **224dp（紧凑 186dp）**＝小码 bottom 偏移 + 卡高 + 余量，最后一行能完整滚出小码遮挡区，不挡点歌操作。

### 手机点歌页「已点歌单」下拉

- **服务端接口零改动**：现有 `GET /api/queue` 早已返回 `current{artist,title,by}` 与 `waiting[{artist,title,by}]`，点歌人、正在唱一应俱全，纯前端即可。
- 位置：点歌页顶部（标题正下方、搜索框上方）新增可展开/收起面板「📋 已点歌单（共 N 首）▾/▴」，点标题行切换；默认展开。原来页面底部那份「已点顺序」列表**并入此面板**（同一份数据、同一批删歌按钮，不再两处重复）。
- **位次口径**：展开列表按顺序＝正在唱一首（标“▶ 正在唱”，不占位次）＋待唱队列；每行：序号、歌手 - 歌名、点歌人。**“第 X 位”＝该歌在待唱队列中的序号（第 1 位即下一首唱），正在唱的不计入位次**；「共 N 首」＝正在唱（0 或 1 首）＋待唱数。
- **认出自己**：点歌人 == 本机 localStorage 里进门填的名字（`sk_name`）的行整行高亮（金色左边框＋深金底），并挂徽标「我点的 · 第 X 位」（正在唱的是我的则标「我点的」）；面板头部汇总三态：我有待唱 →「我点的下一首在第 X 位」；正在唱是我的且无待唱 →「正在唱的就是我点的 🎤」；都没有 →「我还没点歌」。
- **删歌不变**：每行删歌按钮照旧，谁都能删（与电视规则一致），走原有 `POST /api/remove`。
- **刷新不跳**：沿用现有 5 秒轮询 `loadQueue`；展开/收起状态存在页面变量里、轮询不重置；队列数据签名（current+waiting 的 JSON）未变时跳过列表 DOM 重建，滚动位置不跳。

### 尾奏静止（歌词唱完小人停跳）

- 新增纯函数 `ui/DancerActivity.kt`（+8 个单测）：`lastLyricEndMs(lines)` 取最后一行结束时间——增强 LRC 末行尾部的空片段就是该行的结束标记（end marker），其 `startMs` 即结束时间；末行无结束标记（普通行/无尾标记）时用**末行 startMs（`timeMs`）兜底**；结束标记早于行首的异常数据同样回落行首。`isDancing(positionMs, end)`：位置 ≤ 结束时间跳，超过就停。
- 播放页接线：静止时帧固定为该角色**第 1 帧**（中立站立帧）、Pose 归零（无弹跳/摇摆/下沉——婴儿不摔、机器人也停）；诊断行在尾段静止时追加「伴舞：尾奏静止」。
- 边界：**无同名 LRC 的歌没有“唱完”参照（end 为 null），整首照常跳，绝不误停**；前奏阶段照跳，只改尾段；鼓掌环节在歌曲 ENDED 之后，不受影响。

### 姿势帧减速

- `PoseCycle.FRAMES_PER_BEAT` 2 → **1**：姿势帧从每半拍进一帧改为**每 1 拍进一帧**（100–120 BPM 时约每秒 2 次，原约 4 次）。整体弹跳/摇摆的节拍相位（DanceMotion）不变，只改帧推进速率；`PoseCycleTest` 相应断言已改一拍口径（半拍不再进帧、相位偏移需满一拍才进一帧）。

### 发布

- GitHub 发布：提交 `76b3d1d` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.6 （附件 `MySing-1.0.6.apk`）。API 回读校验：asset size `12788221`、digest `sha256:4abd814035eb055fd86e8e67578fad7b28b104a6252fd60555276387146a9813`，与本地逐字一致。

## v1.0.7 姿势扩容 40 种 + 尾奏喘气 + 前奏邀请 + 手机选伴舞看图（2026-11-15）

构建日期：2026-11-15（America/New_York）。四件事：①伴舞姿势每人 10 → 40 种；②尾奏静止时加缓慢喘气；③前奏做邀请动作（用户追加："前奏也别跳，做邀请的动作"）；④手机点歌页选伴舞改图片卡（用户追加："手机上我好象没看到这几个小人"）。其余功能一律未动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.7.apk` |
| 大小 | 17,733,098 字节（240 帧总量，未超 ~20MB 线，WebP 维持 q93 不降质） |
| SHA-256 | `3b20d14c3f68e85039fdf6c625eec3a6308ae451ac722cb9d945c30eda58e24f`（前缀 `3b20d14c`） |
| versionName / versionCode | 1.0.7 / 8（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **111 个，0 失败**（83 旧 + 呼吸 9 + PoseCycle 40 帧 1 + 三态/邀请 13 + DancerImages 4 + PhonePage 图片卡 1） |
| 资源校验 | aapt2 资源表实测舞者条目 **252** = 240 姿势帧 + 6 单帧 + 6 邀请帧 |

### 姿势扩容：每人 40 种

- 素材：`dancers/poses/extra40/` 36 张表（每人 6 张 × 5 姿势 = 180 个新姿势），沿用 v1.0.5 抠图管线（rembg + 测地线分离 + 坏帧守卫），编号 _11…_40，WebP q93。
- **同角色 40 帧共用同一缩放与脚底基线**：缩放不是重算的——先用已发货帧反推每人的像素级缩放（在已发货帧上回测，预测误差 ≤3px）：现代女 0.573404 / 古代女 0.539446 / 现代女孩 0.396226 / 古代女童 0.380299 / 婴儿 0.314702；机器人公式反推 0.58176 与实测共识 0.5800 有系统性偏差（天线细尖在不同运行里过阈值位置不同），取共识值 **0.5800**（8 张未受影响的已发货帧验证，预测误差 ≤3px）。画布高 600、底边距 12 全员一致；新姿势比旧画布宽的只加透明边（古代女新画布宽 417、机器人 438），显示按高度驱动，看不出差别。
- `PoseCycle` 循环长度即帧表长度（10 → 40 自动生效，`FRAMES_PER_BEAT=1` 不变）；鼓掌仍用原单帧。
- **抠图坏帧与处理**（全部逐帧目检过 6 张总览图）：
  - **现代女 30**（X4 第 5 姿）：rembg 对整个人给出均匀发虚的 alpha（p50 仅 0.22），常规 firming 后只剩暗鬼影。改走**轮廓重建 alpha**（归属掩膜 >0.10 → 填洞 → 最大连通域 → σ1.2 羽化）：成品明亮干净；代价是左臂在发丝后略有缺损（头旁可见手掌），属可接受。
  - **机器人整批被坏帧守卫拦下**：X3 第 25 帧（行走姿）的强 alpha 核心丢失、身体被邻居吞掉（第 23 帧涨到 2.5 倍量、第 25 帧仅剩 12k）；X5 第 33 帧（抱头姿）是 rembg alpha 整体发虚（p50 0.14）无核心。改用**列质量谷值切片播种核心**（每片取最大强斑、虚图退到最大淡斑）后两张表分离正常；终检 30 帧质量最小 140,377 / 中位 184,798，守卫通过。教训：先前一版曾对第 17/34 帧误用轮廓重建（实为切片分离后 alpha 已健康），填洞扫进了背景板——已撤销重跑，两帧回归标准 firming。
  - **单帧重抠三帧**（`recut_frames.py`，紧裁 + rembg + 最大连通域，同缩放/基线回填）：机器人 17（测地线票把跳跃机器人竖切成半个）、现代女 24（邻座的前臂与腿靴经中 alpha 板层融了进来；其 X3 表的谷值分裂本身失效——1735/1820 两谷挤在一人身上，改用实测切片 1300–1830）、古代女 14（邻座 raised 袖+手以游离碎片形式挂入）。
  - **碎片清理**（`clean_fragments.py`）：现代女 34、机器人 25 各去掉小块游离碎片。
  - 全量审计：240 帧不透明度审计无近空帧；现代女 14 / 古代女 37 肢体间有淡背景残留（≤0.5 alpha、轮廓内侧），与 v1.0.5 已发货帧同级，深色舞台上不可见，保留。

### 尾奏喘气

- `DancerActivity.breathPose(positionMs, profile)` 纯函数：静止（尾奏）态不再全冻——以**播放位置**为时钟（暂停冻结，与全局口径一致），周期 **2.0 秒**（用户追加调整：原定约 3.2 秒，"喘气快点"），scale 1.000↔1.018 + 竖直微升（bounceFraction 峰值 0.08 ≈ 身高 0.4%），仍停第 1 帧、不换帧、不跟节拍；机器人幅度减半。+9 个单测。

### 前奏邀请

- 素材：定版图 `dancers/media-generation-…-invite-sheet-….webp`——实际布局是**六人一排**（非 3×2 网格，第一版按网格抠全错位）；六人紧邻、alpha 相连，整图以六个躯干种子点测地线分离（淡 alpha 惩罚 ×12、无膨胀域），下半身再以实测列间隙覆盖分区（古代女↔现代女孩边界按袖口/手掌实测位置分段取 742/765；婴儿↔机器人取 1552——婴儿宽站位右脚达 x~1560，与机器人脚交错，测地线在上半身的表现可信、只覆盖 y≥780）。六人内容高统一对齐各自单帧站姿高（定版图与姿势表不是同一生成尺度，不能套用姿势缩放），画布高 600、基线一致。成品六人完整，接触点有少量边缘小碎片（前奏数秒、电视左侧小图，不可见级）。
- 三态纯函数：`DancerActivity.phase()` → INVITING / DANCING / STILL；前奏判定 = 位置 < 第一行歌词 startMs（`firstLyricStartMs`，无歌词歌无前奏态、整首照跳，与尾奏 null 口径对称）。前奏显示该角色 `dancer_<角色>_invite.webp`（`Dancer.inviteResId`），配 `invitePose()` 缓慢"请"律动：周期 2.0 秒，前倾峰值 2.4° + 微沉（sink 峰值 0.07），不换帧、不跟拍；机器人幅度减半。第一行歌词开始即切跳舞。诊断行加「伴舞：前奏邀请」。+13 个单测。

### 手机选伴舞看图

- 图片来源：六人现成的**单帧站姿图**（`dancer_<角色>.png`，即鼓掌环节用的那张）。
- 路由：`GET /dancer-img/<key>`（key 与 `data-who` 同口径：modern/ancient/girl/ancient_girl/baby/robot）；纯函数 `DancerImages.keyForPath` 校验（未知 key 404，不给错图）+4 个单测。字节由 App 侧 provider 提供：首次请求时把单帧解码、等比缩到高 300px、转 PNG 字节后**缓存**，之后请求直接发缓存（不逐请求解码）；二进制响应绕开原文本路由表，服务端其余路由零改动。
- 页面：选伴舞区由六个纯文字 chip 改为 **3×2 图片卡**（图高 96px + 图下名字），点选逻辑、`POST /api/dancer` 提交、localStorage 记忆、选中高亮（`.dcard.on` 金边）全部沿用原有脚本（仍走 `data-who`）。PhonePage +1 个单测守住图片卡与路由引用。

### 发布

- GitHub 发布：提交见仓库 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.7 （附件 `MySing-1.0.7.apk`）。API 回读校验见发布后记录。
