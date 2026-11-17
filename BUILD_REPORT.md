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

- GitHub 发布：提交 `51c2a08` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.7 （附件 `MySing-1.0.7.apk`）。API 回读校验：asset size `17733098`、digest `sha256:3b20d14c3f68e85039fdf6c625eec3a6308ae451ac722cb9d945c30eda58e24f`，与本地逐字一致。

## v1.0.8 防呆修复：旧点歌页静默失败 + 服务启动健壮性 + 屏幕常亮（2026-11-15）

构建日期：2026-11-15（America/New_York）。本版专治一类已确诊的“静默失败”，功能行为不变。根因：用户手机上留着旧会话的点歌页，在电视（试唱手机）上点歌后电视无反应、手机也无任何提示；关掉旧页、重新扫码开新页立即恢复——断点在旧页面一侧，而旧版页面所有 fetch 都没有失败处理，请求发不出去就被静默吞掉，电视端自然“什么都没有，停在主页不动”。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.8.apk` |
| 大小 | 17,733,106 字节 |
| SHA-256 | `7866a776853ba382cf88d8fbaea1b222f3cc0faa7bc61141209e9982e102a996`（前缀 `7866a776`） |
| versionName / versionCode | 1.0.8 / 9（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **118 个，0 失败**（111 旧 + BootSession 5 + PhonePage 防呆 2；常亮一项无可测纯逻辑，未硬凑测试） |

### 会话标识防旧页（boot id）

- 新增纯函数 `server/BootSession.kt`（+5 个单测）：`newId(nowMs, randomBits)` 以启动毫秒 + 随机数生成 base36 短串（只含 `[0-9a-z-]`，可安全裸嵌进 JS 字符串/JSON/URL）；`isStale(pageBoot, currentBoot)` 判定旧页——页面 id 为空/未知时**不判旧**（太老的页面根本没有比对脚本，判旧也只会造成刷新循环）。
- `KaraokeHttpServer` 在构造时（即每次 App 启动）生成 `bootId`；点歌页 HTML 嵌入门时 id（`var bootId = '…'`）；`/api/queue` 响应带上当前 `boot`。
- 手机页沿用现有 5 秒 `loadQueue` 轮询：每次比对响应里的 boot 与页面内嵌 id，不一致（=电视 App 已重启/换版）先在 `#msg` 显示「电视已重启，正在刷新页面…」，800ms 后 `location.reload()` 自愈成新页面（带 `reloading` 一次性闩，不会循环刷新）。页面的 JS 比对与 `BootSession.isStale` 同口径。

### 发送失败要出字

- PhonePage 全部 6 处 fetch（歌曲加载、点歌 order、收藏 fav、删歌 remove、选伴舞 dancer、队列轮询 queue）统一补 `.catch(netErr)`：网络级失败时在页面 `#msg` 区显示「发送失败：连不上电视，请确认同一个 WiFi，或关掉页面重新扫码」。此前任何一处失败都是无声无息——这正是本次故障“什么都没有”的直接来源。HTTP 层面的业务错误（400/404）原本就有 `d.error` 文案，不受影响。

### HTML 禁缓存

- 最终采用**真实 HTTP 响应头**（未用 meta 兜底）：`handle()` 写 socket 时对 `GET /` 与 `/index.html` 的响应追加 `Cache-Control: no-store`。现有 Triple(status, contentType, body) 路由结构未动——响应头本来就写在 handle() 里，按路径判定即可，不需要为加一个头改结构。杜绝手机浏览器把旧版页面从 HTTP 缓存里端出来。

### 服务启动健壮性

- `KaraokeHttpServer.start()` 改为先建未绑定 `ServerSocket`、开 `reuseAddress` 再 `bind`；bind 失败隔 500ms 重试，最多 5 次（常量 `MAX_BIND_ATTEMPTS` / `BIND_RETRY_DELAY_MS`）。场景：App 重启/覆盖安装时旧进程刚死、端口短暂被占，旧版一次失败就整场无声死亡。
- 最终仍失败（或 accept 循环中途死亡）时，状态经 `@Volatile alive / lastError` + `statusListener` 暴露到 App 层：`KaraokeApp` 把它镜像为 Compose 状态 `serverAlive / serverError`，**显示位置：电视首页标题下方的状态区**——歌库状态行下面追加一行橙红字「⚠ 点歌服务未启动，手机暂时扫不了码（原因）」，服务恢复/绑定成功即自动消失。未新造 UI。

### 屏幕常亮（用户追加）

- 用户问电视（试唱手机）怎么保持常亮——此前 App 没有任何 KEEP_SCREEN_ON 处理，只能跟系统超时走。`MainActivity.onCreate` 加 `window.addFlags(FLAG_KEEP_SCREEN_ON)`：App 在前台期间屏幕常亮，退到后台/关 App 即恢复系统行为。**不加设置开关**（点歌机就该开着常亮，简单防呆），manifest 无需新权限（该 flag 不是权限）。

### 发布

- GitHub 发布：提交 `deba1e2` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.8 （附件 `MySing-1.0.8.apk`）。API 回读校验：asset size `17733106`、digest `sha256:7866a776853ba382cf88d8fbaea1b222f3cc0faa7bc61141209e9982e102a996`，与本地逐字一致。

## v1.0.9 紧急修复：手机点中文歌永远没反应——请求读取字节/字符混淆（2026-11-15）

### 现象与根因

- 用户实测（v1.0.7/v1.0.8）：手机点歌页一切正常（能开页、能选伴舞、能搜歌），点**中文歌**后手机无任何回音、电视队列不进歌、电视停在主页不动；点英文歌正常。重扫二维码开新页一度“恢复”，其实是误打误撞——与旧页无关。
- **根因（源码确诊）**：`server/KaraokeHttpServer.kt` 的 `handle()` 用 `BufferedReader(InputStreamReader(UTF_8))` 读请求，却按 HTTP 的 `Content-Length`（**字节数**）去读**字符数**：`CharArray(contentLength)` + `reader.read` 循环。POST /api/order 的 JSON 里带中文歌 id（如 `CHINESE_BOY|张信哲 - 爱如潮水`）与中文点歌人名时是多字节字符，字节数 > 字符数，字符循环永远读不满 → handler 线程无限阻塞、请求挂死，手机端 fetch 既收不到成功也收不到失败（连接一直挂着），队列自然不进歌。纯 ASCII 的英文歌 body 字节数 == 字符数，恰好能读满——这就是为什么 GET 全通、唯独点中文歌死。
- 这是覆盖洞：此前所有服务端测试都停在路由级（直接调 route()），从没用真 socket 发过带中文的 POST。

### 修法

- **请求读取改字节级**：整个 socket 输入只用一个 `BufferedInputStream`；请求行与头逐行按字节读到 `\n`（新 `readHeaderLine`，ISO-8859-1 字节无损解码后再做 URL 解码）；body 按 `Content-Length` **精确读满 N 个字节**后才 UTF-8 解码。字符流彻底退出请求读取路径。路由、boot 会话、Cache-Control: no-store、Connection: close、舞者图片二进制路由等现有行为全部保持。
- **socket 读超时**：accept 到的 socket 设 `soTimeout = 15_000`（常量 `SOCKET_TIMEOUT_MS`），畸形/半截/弃置的请求只会超时断开，绝不许把 handler 线程永久挂住。
- **测试基建补强**：`testImplementation("org.json:json:20240303")` —— 本地单测的 android.jar 里 org.json 是惰性桩（returnDefaultValues 也救不了），真 socket 测试要跑真 JSON 路由必须用真实现。

### 新增回归测试（真实 socket 级，4 个）

`server/KaraokeHttpServerSocketTest`：真服务器（临时端口）+ 含中英文歌的小目录，原始 socket 按字节发请求（沙箱拦截 Java IPv4 环回，客户端走 ::1）：

1. **中文点歌**：POST /api/order（点歌人名“小明”+ 中文歌 id）→ 200，且队列里真有这首、点歌人正确。
2. **分段发送**：头与 body 分两次 flush、间隔 250ms → 仍正确收单入队。
3. **英文点歌**：纯 ASCII 路径照常 200 入队。
4. **畸形请求不打死服务**：先发一行垃圾请求，再发中文点歌 → 仍 200。

### 交付

| 项 | 值 |
|---|---|
| APK | `MySing-1.0.9.apk`，**17,733,096 字节**，SHA-256 `d97a01a17378576b7a144bf851d7882ce67b991873784f9fec528e06604f28bd` |
| versionName / versionCode | 1.0.9 / 10（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **122 个，0 失败**（118 旧 + 新增 socket 级回归 4） |

### 发布

- GitHub 发布：提交 `0300bc5` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.9 （附件 `MySing-1.0.9.apk`）。API 回读校验：asset size `17733096`、digest `sha256:d97a01a17378576b7a144bf851d7882ce67b991873784f9fec528e06604f28bd`，与本地逐字一致。

## v1.0.10 舞蹈柔化：帧间交叉淡化（2026-11-15）

### 反馈与定性

- 用户反馈（2026-11-15 12:16）：跳舞动作太僵硬。
- 定性：每人 40 帧姿势、一拍换一帧（v1.0.6 用户定版，此前“动作变化太快”后定的，**本版不动**），帧与帧之间是瞬时硬切——幻灯片感正源于此。本版只治“硬切”：不动节拍数、不动姿势图、不动整体律动（DanceMotion 的弹跳/摇摆本来就是连续的，未改）。

### 改法

- **纯函数**：`PoseCycle` 新增 `FADE_FRACTION = 0.35` 与 `blend(positionMs, bpm, frameCount, phaseOffsetBeats)`，返回 `Blend(previousIndex, currentIndex, currentAlpha)`：由 position 在当前拍内的相位驱动——相位 0 → 全上一帧（alpha 0）；相位 ≥0.35 → 全当前帧（alpha 1）；中间线性。`currentIndex` 的步进公式与 `frameIndex()` 完全同源（单测有全程一致性断言）。纯函数的位置驱动意味着暂停时相位天然冻结，淡化不会在暂停时继续跑（单测 blendFreezesWithPosition + 既有纯函数性保证）。
- **绘制层**：`MainActivity.DancerImage` 新增可选 `blend` 参数。淡化进行中（alpha < 1）时在同一 Box 里叠画两帧——抽出 `DancerFrame` 承载原有的整图变换（弹跳/摇摆/下沉/缩放），上一帧与当前帧**同 rect、同变换，只差 alpha**（各乘原 0.95 基准：上一帧 0.95×(1−α)、当前帧 0.95×α）。不做预合成，两张图的绘制开销可接受。淡化结束即回到原来的单图绘制，INVITING/STILL/无 blend 时行为与旧版逐字一致。
- **机器人例外**：调用点只在 `dancerPhase == DANCING && profile != ROBOT` 时传 blend——机器人保持硬切（机械感是它的角色设定，用户定的）。鼓掌画面（庆祝行）不传 blend，全员同步鼓掌保持齐整。小婴儿照常淡化；BabyFall 摔倒那套是整体变换、叠在两帧之上同施，不受影响。

### 新增单测（8 个，PoseCycleTest）

1. **blendStartsFullyOnPreviousFrame**：拍边界相位 0 → 全上一帧；起点处上一帧回绕到末帧。
2. **blendIsLinearThroughTheFadeWindow**：相位 0.175（窗口正中）→ alpha 0.5。
3. **blendCompletesAtThirtyFivePercentAndStays**：相位 0.35 起 alpha 恒 1，直到下一拍。
4. **blendCurrentFrameAlwaysMatchesFrameIndex**：全程扫位断言 currentIndex 与 frameIndex 一致、双索引在界内、alpha ∈ [0,1]。
5. **blendPreviousIsOneStepBehindAcrossTheWrap**：跨周期回绕处上一帧为末帧而非负数。
6. **blendDegenerateFrameCountsAreSafe**：帧数 0/1/负 → Blend(0,0,1)。
7. **blendHonoursPhaseOffset**：整拍偏移整体平移淡化，与帧周期同口径。
8. **blendFreezesWithPosition**：同位置重复求值恒等——暂停冻结的纯函数性保证。

### 交付

| 项 | 值 |
|---|---|
| APK | `MySing-1.0.10.apk`，**17,733,103 字节**，SHA-256 `331ab0836ebeb89c58a476fc08f7b8a8e98a96d35274622605658123f8b92b5e` |
| versionName / versionCode | 1.0.10 / 11（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **130 个，0 失败**（122 旧 + 新增 blend 单测 8） |

### 发布

- GitHub 发布：提交 `ebc5307` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.10 （附件 `MySing-1.0.10.apk`）。API 回读校验：asset size `17733103`、digest `sha256:331ab0836ebeb89c58a476fc08f7b8a8e98a96d35274622605658123f8b92b5e`，与本地逐字一致。

待用户真机验：跳舞时帧与帧之间应是“化”过去而不是“跳”过去；机器人仍是干脆的机械步进；暂停时小人连同淡化一起定格。

## v1.0.11 选伴舞卡片动画化（2026-11-15）

### 反馈与定性

- 用户要求（2026-11-15 12:45）：手机点歌页上选伴舞的图片卡做成动画——选人时就能看到每个小人跳舞的样子。v1.0.7 的卡片是静态站立图，本版让卡片本身动起来；点选/高亮/localStorage/提交逻辑、卡片尺寸（图高 96px）全不动。

### 改法

- **构建期合成动画 WebP**：脚本 `dancers/make_anim.py`（留在工程里）用每位角色已有的 40 帧姿势图（`res/drawable-nodpi`，与电视播放页同一套）等距抽 10 帧（第 1/5/9/…/37 帧），PIL 合成无限循环的动画 WebP：每帧 280ms、高度 420px（保持宽高比与透明通道）、有损质量 80。各帧原始宽度不一，统一贴到最宽画布（水平居中、底边对齐，保持共享地板基线）再合成。产物放 `app/src/main/assets/dancer_anim/<key>.webp`，key 与 `DancerKeys` / `/dancer-img` 同口径。
- **六个动画文件大小**：modern 144,118 B、ancient 145,292 B、girl 82,480 B、ancient_girl 87,104 B、baby 56,950 B、robot 161,748 B，合计 677,692 B（单文件目标 <600KB、合计 <3MB，均大幅达标，未触发减帧/降尺寸）。
- **服务端**：新增 `DancerAnims`（照 `DancerImages` 模式：key 校验、未知 key 404）与路由 `GET /dancer-anim/<key>`，与静态图共用 handle() 的二进制绕行块，按路由回 `Content-Type: image/webp`。字节由 KaraokeApp 从 assets 读一次并缓存（`dancerAnimProvider`），不逐请求读盘。
- **点歌页**：六张卡的 `<img>` 改用 `/dancer-anim/<key>`，加 `onerror` 回落到 `/dancer-img/<key>` 静态图（动画加载失败不许开天窗），其余一字未动。

### 新增/更新单测（9 个）

1. **DancerAnimsTest（6 个，新增）**：六个 key 全部解析、pathFor 往返、未知/大写/带后缀 key 不解析、其他路径不解析、动画与静态图两条路由前缀互不相交、asset 路径与打包文件名口径一致。
2. **KaraokeHttpServerSocketTest（+3，真 socket）**：`GET /dancer-anim/modern` 返回 200 + `image/webp` + 假载荷字节逐字往返（含非 UTF-8 字节）；未知 key 404；合法 key 但服务端无此资源 404（页面据此回落静态图）。
3. **PhonePageTest（更新 1 个）**：选伴舞卡断言改为动画源 `/dancer-anim/<key>` + `onerror` 回落 `/dancer-img/<key>`，data-who 点选链与旧断言保持。

### 交付

| 项 | 值 |
|---|---|
| APK | `MySing-1.0.11.apk`，**18,428,561 字节**，SHA-256 `c6340ea150dc1b0072350aa032ca0fe81f40f17c985722dd80fd6b3becf88fcb` |
| APK 增量 | 较 v1.0.10（17,733,103 B）+695,458 B ≈ 六个动画 677,692 B + 路由/页面代码增量；aapt 实测六个 `assets/dancer_anim/*.webp` 均在包内、大小与源文件逐字一致 |
| versionName / versionCode | 1.0.11 / 12（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **139 个，0 失败**（130 旧 + 新增 9） |

### 发布

- GitHub 发布：提交 `ebfdb37` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.11 （附件 `MySing-1.0.11.apk`）。API 回读校验：asset size `18428561`、digest `sha256:c6340ea150dc1b0072350aa032ca0fe81f40f17c985722dd80fd6b3becf88fcb`，与本地逐字一致。

待用户真机验：手机重新扫码打开点歌页，选伴舞六张卡应都在循环跳舞；点谁选谁与电视出场规则不变。

## v1.0.12 相扑手换人 + 全员命名（2026-11-15）

构建日期：2026-11-15（America/New_York）。两件事：①现代女孩下场，日本相扑手顶替她的阵容位置（全套素材新生产）；②六位伴舞全部改用艺名。其余功能一律未动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.12.apk` |
| 大小 | 18,831,550 字节（较 v1.0.11 的 18,428,561 增 402,989 B：相扑帧/单帧/动画体量大于小女孩所致） |
| SHA-256 | `f0e9e67cdb0552964b803d5254bd431b01af126091378ad909239b8df179ff2e`（前缀 `f0e9e67c`） |
| versionName / versionCode | 1.0.12 / 13（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **145 个，0 失败**（139 旧 + DancerKeys 阵容/命名 4 + DanceMotion 相扑律动 2；PhonePage 卡片断言同步更新） |
| 资源校验 | aapt2 资源表实测舞者条目 **252**（与 v1.0.11 持平）= 240 姿势帧 + 6 单帧 + 6 邀请帧；其中 sumo 42 条、modern_girl 0 条 |

### 相扑手全套生产（定版母版 v5：灰腰带 + 白前襟，用户 2026-11-15 12:57 定版）

- **站立单帧**：从定版母版 rembg 抠图，内容高 500px、画布 305×600，即鼓掌排与手机静态图用的 `dancer_sumo.png`；500px 同时是姿势帧的缩放锚点（与各角色"单帧站姿高 = 姿势 target_h"的既有口径一致）。
- **姿势表 8 张**（poses-sumo-a…h，每张 5 姿、横排、深藏青底，均以母版为参考图生成）：踏步/四股、沉晃、深蹲、拍手、推掌、转身、挥手、撒盐、拍肚皮、竖拇指、指天、V 字庆祝等，全是合大体型的沉稳动作，无腾空无劈叉。生成坏版修补：C 表初版第 3/4 姿重复、D 表初版第 5 姿只张一臂，各重生成 1 次后采用新版（旧版移入 `poses/rejected/`，不进管线）。
- **抠图**（新脚本 `poses/cutout_sumo.py`，复用 v1.0.5 的 `cutout_poses` 分离管线）：40 帧共用同一缩放（最高帧内容高 → 500px，scale 0.5562）、同一画布 468×600、脚底基线距底 12；质量守卫通过（alpha 质量最小 180,994 / 中位 231,264，无近空帧），逐帧总览目检无缺肢、无邻座碎片。**一处镜像修补**：A 表两个四股抬腿生成成了同侧腿，帧 _02 做水平翻转成左右一对（角色正面左右对称，翻转不可见）。WebP q93 进资源，PNG 母版留 `poses/frames_png/`。
- **邀请帧**：单人邀请图（一臂前伸掌心向上“请”+ 微鞠躬，灰带白襟与母版一致）rembg 抠图，内容高对齐 500、同画布。
- **律动**：`DanceMotion.Profile.SUMO` 新档——沉、稳：左右沉晃为主（半频摇摆 ±4.5°、一周期两拍）、弹跳低而钝（压平正弦、峰值 0.30）、每拍落点带 0.10 膝沉（sinkFraction）；无摔倒。帧间交叉淡化照常（非机器人，v1.0.10 的调用点按 `profile != ROBOT` 天然生效）；鼓掌/喘气/邀请走通用真人分支。
- **动画卡**：`dancers/make_anim.py` 生产范围 girl → sumo（参数不变：抽 10 帧、280ms、420px 高），产物 `assets/dancer_anim/sumo.webp` 120,432 B；其余五人重跑结果与 v1.0.11 逐字同大小（管线确定性）。

### 阵容与命名落地

- `DancerKeys` 新增 `SUMO="sumo"` 顶替 `GIRL`（`ALL` 顺序不变位：modern / ancient / **sumo** / ancient_girl / baby / robot）；现代女孩的 40 帧 + 单帧 + 邀请帧 + 动画资源全部移出 APK（工程 `dancers/` 源图保留）。服务端 `DancerImages`/`DancerAnims` 走 `DancerKeys.isValid` 校验，换人自动生效；鼓掌排由 `Dancers.ALL` 驱动，自动换成相扑站立帧。
- **旧 "girl" 选择回落**：TV 侧 Prefs 写入时就校验 key、读取后经 `Dancers.byKey` 兜底；手机 localStorage 里的旧值在新页面上不再对应任何卡片、提交会被服务端 400 拒掉——两条路都回落默认春丽，不崩（DancerKeysTest 守住此口径）。
- **全员艺名**（`Dancers.label` + `DancerKeys.LABELS` + 手机点歌页卡片文字三处同步）：现代女=**春丽**、古代女=**林语嫣**、相扑手=**姿三四郎**、古代女童=**小蛮**、小婴儿=**哪吒**、机器人=**瓦力**。key 不变，只换显示名。

### 发布

- GitHub 发布：代码提交与本报告提交已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.12 （附件 `MySing-1.0.12.apk`）。API 回读校验见发布后记录（asset size / digest 与本地逐字一致）。

待用户真机验：手机点歌页选伴舞第三张卡应是姿三四郎在跳（动画卡）；点他的歌上电视，伴舞是相扑手沉晃踏步；唱完鼓掌排里站的是相扑手；其余五人卡片名字应分别是春丽、林语嫣、小蛮、哪吒、瓦力。

## v1.0.13 中英双语切换（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户的儿子伟霖（William）看不懂中文。用户已确认范围（2026-11-15 13:01–13:02）：电视首页加中英切换、手机点歌页每台手机各自切换、服务端提示跟手机语言走。本版只做语言，不动任何功能行为。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.13.apk` |
| 大小 | 18,831,558 字节（较 v1.0.12 的 18,831,550 仅 +8 B：纯文案/逻辑增量，无新素材） |
| SHA-256 | `138f86d961451f7bc77f52b20f4819a73d20fcb2a2cfeccf8e3837a9be2f1ca0`（前缀 `138f86d9`） |
| versionName / versionCode | 1.0.13 / 14（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **160 个，0 失败**（145 旧 + 新增 15；PhonePage 旧断言 1 处随卡片结构同步更新） |

### 电视端

- **字符串表**：新建 `i18n` 包——`UiLang`（zh/en + parse，缺省/未知一律中文）、`TvStrings`（**49 个 key**，zh/en 两张表 key 对齐，缺 key 由测试守红）。MainActivity 全部用户文案改走 `s(TvKey.…)`：首页入口与状态行、选歌页 chips、播放页控制条/下一首横幅/正在唱行/换歌倒计时/播放错误行、不唱了确认框、鼓掌空档页、常驻二维码角标、页脚 credit。**诊断行按规格保留中文现状**（技术小字）。
- **切换开关**：首页标题行右侧（大二维码左侧）竖排两个 chip「中文 / EN」，各自用自己的语言标注、当前语言金色高亮，与既有状态区不冲突；选择写入 `Prefs.uiLangCode` 持久化，重启保持，切换即时全界面生效。
- **细节**：下一首横幅改由 `PlayQueue.nextUpText(lang)` 从字符串表生成（中文格式与旧版逐字一致，旧测试不改即过）；电视自点歌的点歌人显示名随语言（电视 / TV）；**歌名、歌手名是文件名数据，一律不翻译**，只经模板的 %s 槽位原样穿过。

### 手机点歌页

- 页面顶部（名字门与主区之外，两阶段都够得着）加「中文 / EN」小开关；选择存本机 `localStorage mysing_lang`，**每台手机独立**——伟霖的手机切英文，其他客人的手机仍是中文，互不影响，也不读电视的设置。
- 全页文案进 JS 内 `I18N` 字典（zh/en 各 **32 个文案键 + 6 个伴舞名**）：标题、名字门、已点歌单面板（含我的位次/徽章/删按钮/空状态）、搜索占位、筛选 chips、选伴舞区、点歌按钮、重复点歌提醒、发送失败提示、电视重启刷新提示，切换时静态文案（data-i / data-iph / data-dancer）与队列面板一并重渲染。
- 伴舞英文名与电视端共用同一套映射（权威在 `DancerKeys.LABELS_EN`，页面内联同名表、测试双边钉死）：Chun-Li / Lin Yuyan / Sanshiro / Xiaoman / Nezha / WALL-E。

### 服务端提示双语清单（`ServerStrings`，5 条）

点歌/删歌/选伴舞请求在 JSON 里带 `lang` 字段（删歌、收藏无回执文案，不带），服务端按语言回提示；没带 lang 的老页面照旧中文。

| 场景 | 中文 | English |
|---|---|---|
| 点歌成功（马上轮到） | 马上开唱！ | You're up — singing now! |
| 点歌成功（排队） | 已加入，前面还有 X 首 | Added — X song(s) ahead of you |
| 没填名字点歌 | 请先输入你的名字 | Please enter your name first |
| 歌不存在 | 找不到这首歌 | Song not found |
| 选伴舞参数不对 | 名字或伴舞选择不对 | Invalid name or dancer choice |

### 新增/更新单测（15 个）

1. **StringTablesTest（6 个，新增）**：TvStrings / ServerStrings 的 zh、en 两表 key 集合与枚举全集逐一相等（缺 key 即红）、双侧无空串、带参模板渲染抽查（歌库数/调值/倒计时/正在唱行含中文歌名原样穿过）、服务端 5 条提示中英逐条断言、UiLang.parse 缺省中文。
2. **KaraokeHttpServerSocketTest（+4，真 socket）**：不带 lang 点歌回中文「马上开唱！」；`lang=en` 回「You're up — singing now!」；排队计数中英各一条（「已加入，前面还有 1 首」/「Added — 2 song(s) ahead of you」）；404 错误文案跟语言走（找不到这首歌 / Song not found）。
3. **PhonePageTest（+3、更新 1）**：语言开关存在且在名字门之前、`mysing_lang` 读写、双语字典关键文案抽查（每区至少一条 × 两语）、英文伴舞名与 DancerKeys 口径一致、点歌/选伴舞请求体带 `lang:ui`；伴舞卡断言随 data-dancer 结构更新。
4. **DancerKeysTest（+1）**：LABELS_EN 六名齐全、未知 key 回落 Chun-Li。
5. **PlayQueueTest（+1）**：英文横幅 `Next up: … (ordered by …)`，中文歌名原样不译。

### 发布

- GitHub 发布：代码提交 `020c5d1`、报告提交 `e1d6bcc` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.13 （附件 `MySing-1.0.13.apk`）。API 回读校验：asset size `18831558`、digest `sha256:138f86d961451f7bc77f52b20f4819a73d20fcb2a2cfeccf8e3837a9be2f1ca0`，与本地逐字一致。

待用户真机验：电视首页点「EN」，全界面变英文、重开 App 仍是英文；伟霖的手机在点歌页点「EN」，页面变英文且点歌回提示是英文，其他手机不受影响；电视切回中文一切如旧。

## v1.0.14 掌声换血（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户原话（13:20）“掌声太难听了”。旧掌声自 v1.0.0 起就是程序合成的带通噪声爆发按节拍等间隔叠放（22.05 kHz、3.2 秒），规整得假、且高频刺耳。本版整体换掉声音本体，触发与画面逻辑不动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.14.apk` |
| 大小 | 19,025,608 字节（较 v1.0.13 的 18,831,558 增 194,050 B，几乎全是新掌声 WAV 335,204 B 替换旧 141,164 B 的差额） |
| SHA-256 | `d1baade066244feff39dc95c833d2a5e1ed218bac778a199d66c9a8df3ad54a8`（前缀 `d1baade0`） |
| versionName / versionCode | 1.0.14 / 15（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **162 个，0 失败**（160 旧 + 新增 2） |

### 新掌声合成（`audio/make_applause.py`，Python + numpy/scipy，固定种子 20261115 可复现）

- **单次拍手模型**：白噪声过 3 阶 Butterworth 带通（每下随机抖动，主体 850–2700 Hz、少数“前排”拍手亮到 3300 Hz）；包络约 1 ms 快起音 + 指数衰减（tau 9–18 ms，实际可闻长度约 40–80 ms）；40% 的拍手带一个 8–20 ms 偏移、30–50% 音量的“双掌错位”轻击（flam）；12% 的事件是双连击（90–160 ms 后第二下，55–80% 音量）。
- **人群调度**：非齐次泊松过程（thinning），速率包络即人群密度：前 0.4 秒从峰值的 34% 涨满（约 175 下/秒，相当于四五十人热烈鼓掌）、中段饱满带 ±7% 自然起伏、2.7 秒后按时间常数 0.5 秒指数稀疏收尾。全程共 **574 个拍手事件**（含双连击）。
- **后处理**：6.8 kHz 二阶低通轻微柔化高频；tanh 软限幅加密度而不爆；整体 RMS 约 **-16.2 dBFS**（与归一化后的歌曲响度大致齐平）、峰值 **-1.0 dBFS**、零削波；末尾 120 ms 余弦渐弱到真零（实测最后 30 ms 已静音、3.6–3.8 秒段 RMS -61 dB），尾巴绝不被切。频谱实测 82.7% 能量落在 900–2500 Hz 拍手主带。
- **格式**：44.1 kHz / 16-bit / 单声道 WAV，恰好 **3.800 秒**，335,204 字节；写入 `app/src/main/assets/applause.wav`（同名替换），另在工程根留 `applause-v2.wav` 同一文件供试听。APK 内实测与源文件逐字一致。

### 时长对齐结论

鼓掌画面的时长**不由声音长度驱动**，而由 `MainActivity` 的常量 `APPLAUSE_MS` 驱动，本版随新声从 3200 改为 **3800**，三处使用全部自动对齐：

- **无下一首**：庆祝画面在播放页停留 `APPLAUSE_MS + 300` = 4.1 秒才回首页 > 声音 3.8 秒，稀疏尾巴完整播完；
- **小人鼓掌动画窗口**：`APPLAUSE_MS + 200` = 4.0 秒，覆盖整个声音；
- **有下一首**：换歌间隙是固定 5 秒倒计时，掌声在其中并发播放，3.8 秒 < 5 秒，尾音落完还留约 1.2 秒给下一位准备。

触发点不变：仍只有整首自然唱完才触发（`onSongEndedNaturally`），“不唱了”走 `quitCurrent`、没有掌声，本版未动任何触发逻辑。

### 旧合成清理

旧 `applause.wav`（141,164 B、22.05 kHz）已被新文件同名替换；App 内从来没有运行时合成代码——播放一直是 `MediaPlayer` 直读 asset（`playApplause()`），无其他残留可删。v1.0.0 当年的离线生成脚本未入库，无从删起。

### 新增单测（2 个，`audio/ApplauseAssetTest`）

1. **格式与时长**：从测试工作目录读真实 asset、手工解析 RIFF/fmt/data 块，断言 PCM / 单声道 / 44100 Hz / 16-bit、时长落在 3.5–4.0 秒。
2. **电平与收尾**：断言整体 RMS 落在 -24..-10 dBFS、峰值落在 0.5..1.0；尾部 0.8 秒 RMS 小于中段 70%（稀疏收尾）、最后 30 ms 最大幅度 < 0.02（落在静音、未被切断）。（初版断言曾用“尾部峰值 < 中段 RMS”，被真实感的稀疏尾巴里一记响亮的单下拍手证伪——那正是真房间的声音，已按此口径修正。）

### 发布

- GitHub 发布：代码提交 `f342456` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.14 （附件 `MySing-1.0.14.apk`）。API 回读校验：asset size `19025608`、digest `sha256:d1baade066244feff39dc95c833d2a5e1ed218bac778a199d66c9a8df3ad54a8`，与本地逐字一致。

待用户真机验：唱完一首完整的歌，掌声应是人群感的——开头哗地起来、中间饱满、最后稀稀拉拉几下落完，不刺耳；不唱了切歌时仍无掌声。

## v1.0.15 红字残留修复（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 13:33 截图——5 Seconds of Summer《Youngblood》正在正常播放（playing=true、进度 5/203s 在走、歌词金扫正常），底部却整首挂着橙红字「播放错误：ERROR_CODE_FAILED_RUNTIME_CHECK: Unexpected runtime error」，诊断行 sink=plain-retry。即开播首试失败、自愈切备用通道后已播好，但 v1.0.3 定的"错误永不自动隐藏"让横幅成了误导性残留。本版只修显示判定，播放与自愈行为一律未动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.15.apk` |
| 大小 | 19,025,594 字节（较 v1.0.14 的 19,025,608 仅 -14 B，纯逻辑小改） |
| SHA-256 | `66431371a3ac92faa8285c90722995d8d3cb830f109236b0ec36ad0e618902d4`（前缀 `66431371`） |
| versionName / versionCode | 1.0.15 / 16（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **170 个，0 失败**（162 旧 + 新增 8） |

### 修复：ErrorDisplayPolicy（新增纯类，`audio/ErrorDisplayPolicy.kt`）

判定输入：是否有错误记录、FallbackStateMachine 路径、以及一帧实时观测（READY / isPlaying / 播放位置）。规则：

- **无错误** → 不显示（并顺手清掉内部闩锁）；
- **FAILED 路径**（备用通道也挂）→ **永远显示**，那是真故障，必须留给用户看；
- **PLAIN_RETRY 路径** → 显示到"恢复被确认"为止：READY + isPlaying + 位置比上一帧观测前进，三者齐了即判恢复、横幅清除；清除后**闩锁**到本首结束——之后用户暂停或短暂卡顿都不会把红字招回来（小字诊断行仍保留 sink=plain-retry，降级事实有据可查）；
- CUSTOM 路径带错误 → 显示（该状态只在错误处理瞬间存在，保守处理）。

接线：`PlayerController` 新增 `visibleError()`（读实时播放器状态喂判定类，返回应显示的错误文本或 null）；`load()` 与 `handleError()` 都调 `errorDisplay.reset()`——新歌、新错误都重新武装横幅。`MainActivity` 播放页沿用现有 33ms 轮询回路顺带刷新 `errorBanner` 状态，诊断区改读它（不再直读 `lastError`）。

### 换歌重置核对（任务第 3 项）

现状本来就已重置、无需改动：`PlayerController.load()` 每首新歌都清 `lastError` 并 `fallback.onNewLoad()` 回到 CUSTOM。本版新增的 UI 侧 `errorBanner` 状态也在 `startSong()` 里同步清零，上一首的残留不会带进下一首。

### 新增单测（8 个，`audio/ErrorDisplayPolicyTest`）

1. **无错误永不显示**：三条路径 × hasError=false 全 false。
2. **未恢复继续显示**：备用通道还在 buffering / READY 但没播 / 在播但位置不动（卡死），红字都在。
3. **恢复推进即清除且闩锁**：位置在 READY+playing 下前进 → 清除；之后暂停、buffering 都不再复现。
4. **前进必须在 playing 时观测到**：暂停期间的位置跳变（seek）不算恢复；开始播且继续前进才清。
5. **FAILED 永远显示**：即使 READY+playing+位置在走（理论组合）也照显。
6. **CUSTOM 带错误显示**：保守口径钉死。
7. **reset 忘掉闩锁**：恢复后 reset，新一轮必须重新确认推进才再清。
8. **错误记录本身被清也复位**：hasError=false 一帧即清闩锁，下一条错误从未恢复状态开始。

### 发布

- GitHub 发布：代码提交 `cdfdd94`、报告提交 `a8ce540` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.15 （附件 `MySing-1.0.15.apk`）。API 回读校验：asset size `19025594`、digest `sha256:66431371a3ac92faa8285c90722995d8d3cb830f109236b0ec36ad0e618902d4`，与本地逐字一致。

待用户真机验：再遇开播首试失败、自愈切 plain-retry 播好的歌（如截图那首 Youngblood），红字应在开播确认推进后（约一两帧轮询内）自动消失，只剩诊断小字里的 sink=plain-retry；若某首歌备用通道也播不出来，红字应整首保留。

## v1.0.16 真掌声打包（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 13:35 试听 v1.0.14 的合成掌声后只回了三个字——"掌声不象"。合成路线就此作废，改用真实录音：从 Wikimedia Commons 取两段公有领域真掌声，各剪成 3.8 秒试听（A 大场版、B 小场版），用户 13:36 亲自试听后选定 **A 大场版**。本版只换 asset 一个文件，播放路径与全部对齐常量一律未动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.16.apk` |
| 大小 | 19,025,883 字节（较 v1.0.15 的 19,025,594 仅 +289 B，新 wav 大 288 B + 版本号） |
| SHA-256 | `6e61e0c827865aa46ce479b4f5821d58336e550472c0d18cf56198a3e02e02a2`（前缀 `6e61e0c8`） |
| versionName / versionCode | 1.0.16 / 17（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **170 个，0 失败**（个数不变，`ApplauseAssetTest` 按真录音口径更新，见下） |

### 掌声来源与加工

- **来源**：Wikimedia Commons 公有领域录音《Slow starting applause》（由 pdsounds.org 提供，Public domain；原始文件 `File:Slow starting applause.ogg`）。用户试听落选的 B 版出自同站《Applause i.ogg》（同为公有领域）。
- **加工**：截取原录音 12 秒处起 3.8 秒；淡入 0.3 秒、淡出自 2.3 秒起 1.5 秒；转 44.1 kHz / 单声道 / 16-bit WAV；整体响度对齐到 **RMS -16.2 dBFS、峰值 -1.0 dB**，与归一化后歌曲电平齐平。
- **成品**：工程根 `applause-final-a.wav`（335,492 B，SHA-256 `2076fcf2a872487e28644b395f8471f7557b3047a5d2ad5169dc3f2addc795f3`），已同名替换 `app/src/main/assets/applause.wav`；解包校验 APK 内文件与母带逐字一致。时长仍是 3.8 秒，`APPLAUSE_MS=3800` 等对齐常量未动，触发点不变（整首唱完才有，"不唱了"没有）。

### 测试更新（`audio/ApplauseAssetTest`，仍 2 个）

类注释与断言口径从"离线合成产物"换成真录音母带实测值：时长断言收紧到 3.7–3.9 秒（实测 3.803）；RMS 断言 -20..-13 dBFS（实测 -16.2）、峰值 0.75..0.98（实测 0.891）；收尾保留"尾 0.8 秒 RMS < 中段 70%"（实测比值 0.36），新增"末 100 ms RMS < -35 dBFS"（实测 -40.7），"末 30 ms 最大 < 0.02"照旧（实测 0.0061）。

### 发布

- GitHub 发布：代码提交 `eca9962` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.16 （附件 `MySing-1.0.16.apk`）。API 回读校验：asset size `19025883`、digest `sha256:6e61e0c827865aa46ce479b4f5821d58336e550472c0d18cf56198a3e02e02a2`，与本地逐字一致。

待用户真机验：唱完一首完整的歌，掌声应是真人群鼓掌——他已试听选定的就是这一段；响度应与歌声齐平，不再是合成味。

## v1.0.17 姿势重排（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：v1.0.10 的交叉淡化上线后，用户真机仍报"动作还是不连贯"，并于 13:42 在选项卡上点选确诊为**「姿势乱跳，前后接不上」**——40 帧是图集格子顺序、不是舞步顺序，淡化治不了顺序病。本版只重排帧序，帧图本身一张不换、不重绘，播放时序（每拍一帧、淡化比例、机器人步进例外、三段状态）一律不动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.17.apk` |
| 大小 | 19,025,886 字节（较 v1.0.16 的 19,025,883 仅 +3 B——无素材变动，只有顺序表代码与版本号） |
| SHA-256 | `9cb5af095a7a35fe9cb654863831baf82e1a678ba2dbb44af337ce04ea486922`（前缀 `9cb5af09`） |
| versionName / versionCode | 1.0.17 / 18（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **179 个，0 失败**（170 旧 + 新增 `DancerPoseOrderTest` 9 个，见下） |

### 重排方法（离线工具 `dancers/resequence.py`，可复跑）

1. **距离**：每位舞者的 40 帧先按电视渲染的实际几何合成到统一画布（固定高度、底部居中），缩到 46×60 缩略图，帧间距离 = 预乘 RGBA（RGB 先乘 alpha，alpha 通道本身也计入）的逐像素 RMS 差——透明像素不贡献，轮廓移位/变形代价大、近似姿势代价小。
2. **求解**：在 40 帧上求闭环巡回使相邻总代价最小（回边 末帧→首帧同样计入）：多起点贪心最近邻（每个候选第二帧各跑一次 + 300 次随机重启）后 2-opt 改进到无可改进。**帧 0（中立站立帧）钉在巡回首位**——开唱时从站立姿势起舞，尾奏站立帧也正是它。
3. **产物**：`dancers/resequence_orders.json`（机器可读顺序 + 代价）与下方 Kotlin 字面量逐字核对一致。

### 六人重排前后平均相邻代价（闭环 40 条边的均值，工具实跑）

| 舞者 | 重排前 | 重排后 | 降幅 |
|---|---|---|---|
| 春丽（modern） | 0.2625 | 0.2116 | −19.4% |
| 林语嫣（ancient） | 0.2835 | 0.2276 | −19.7% |
| 姿三四郎（sumo） | 0.2359 | 0.1838 | −22.1% |
| 小蛮（ancient_girl） | 0.2051 | 0.1658 | −19.2% |
| 哪吒（baby） | 0.1879 | 0.1484 | −21.0% |
| 瓦力（robot） | 0.2976 | 0.2565 | −13.8% |

说明：降幅不是数量级的——图集里的姿势本身差异就大，最优闭环也只能把"最不像邻居的邻居"尽量配对；真正的观感要靠用户眼睛验（见验货件）。

### 代码落点

- 新增 `ui/DancerPoseOrder.kt`：六人顺序表（键为 `DancerKeys`）+ `orderFor(key)`，未知键回落恒等序。
- `ui/PoseCycle.kt`：新增吃 `order: List<Int>` 的 `frameIndex` / `blend` 重载——拍步计算与原函数完全一致，只把步号经顺序表映射（第 k 拍显示 `order[k mod 40]` 号帧，淡化的 previous 同样映射，跨环淡化从巡回末帧回到首帧）。原有恒等序函数一字未动，既有 16 个 PoseCycle 测试原样通过。
- `ui/MainActivity.kt`：播放页两处调用（frameIndex 与 blend）改传 `DancerPoseOrder.orderFor(dancer.key)`，其余不动。

### 帧号假设核查（任务要求先查再动）

- **BabyFall 与帧号无关**：它在 `DanceMotion.pose()` 里只吃绝对拍数，算的是整体姿态变换（倾倒/躺平/弹起的旋转与下沉），从不引用帧号；帧选择是 MainActivity 里与之正交的另一条线。重排不影响摔倒节奏，无需改动。
- **站立帧不经 PoseCycle**：`DancerActivity.STILL_FRAME = 0` 在 MainActivity 里直接取 `dancer.frames[0]`，重排只作用于 DANCING 阶段的 PoseCycle 映射，帧 0 本人仍是站立帧；且顺序表首位钉的正是帧 0，跳舞↔站立衔接不跳。
- **鼓掌帧序列不在范围**：鼓掌画面用的是每人的单张立绘 + `DanceMotion.clapPose` 整体变换，本来就没有帧序列，未动。手机选人动画卡（assets/dancer_anim）是构建期抽帧的静态素材，本版未重做，仍按旧序抽帧播放——它只是选人预览，不影响电视跳舞。

### 新增测试（`ui/DancerPoseOrderTest`，9 个）

六人顺序表均为 0..39 的真排列（无重复无缺失）；每人巡回首位为帧 0；未知/已退役键（"girl"）回落恒等序；`PoseCycle` 按序取帧逐拍等于顺序表且 40 拍绕回；按序结果恒等于"恒等步号经顺序表映射"；按序淡化的 current/previous 是舞步邻居、alpha 与恒等版逐点一致、跨环 previous 为巡回末帧；恒等序与旧行为逐点等价；空顺序表安全；按序取帧同样是位置的纯函数（暂停冻结）。

### 验货件

`pose-order-preview.webp`（工程根，559,816 B）：春丽 40 帧按**新顺序**拼成的循环动画（`dancers/make_pose_order_preview.py`，280 ms/帧、高 420 px，与 make_anim 同路子），供用户在装机前先肉眼验新序是否顺。

### 发布

- GitHub 发布：代码提交 `6c80d8c` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.17 （附件 `MySing-1.0.17.apk`）。API 回读校验：asset size `19025886`、digest `sha256:9cb5af095a7a35fe9cb654863831baf82e1a678ba2dbb44af337ce04ea486922`，与本地逐字一致。

待用户真机验：唱一首看小人 — 姿势应顺着一条舞步链走，不再前后乱跳；先看工程根的 pose-order-preview.webp（春丽新序循环）也能验个大概。若仍觉跳，说明图集姿势本身跨度太大、靠重排已到上限，下一步就得考虑补画中间姿势（另议）。

## v1.0.18 手机点歌页搜索联想（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 14:00 提出的朋友聚会场景需求——按歌名搜索时，输入第一个字就要在下面列出歌名带这个字的歌，点一下就点上，不用输完全名。本版只做这一个功能，电视端不动。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.18.apk` |
| 大小 | 19,025,881 字节（较 v1.0.17 的 19,025,886 为 −5 B——只有页面脚本、一个 JSON 字段与版本号变动，无素材变动，包体差异来自打包时间戳/对齐） |
| SHA-256 | `fe8615044cd01ed94bc4fd0ad36784d02162808c43fa93a980df2016a068b565`（前缀 `fe861504`） |
| versionName / versionCode | 1.0.18 / 19（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **185 个，0 失败**（179 旧 + 新增 6 个，见下）；另有 Node 离线校验 15 项全过（见下） |

### 实现方式：纯前端过滤（全量歌单一次拉取，逐键本地联想）

先查的现状：点歌页原搜索是逐键请求服务端 `/api/songs?q=` 过滤（无防抖、旧响应可能盖新响应）；而 `/api/songs` 无参时本就能一次返回全量歌单（收藏/最近模式早已这么用），唯一缺口是拼音首字母键只在服务端。本版据此选纯前端：

- **服务端（唯一改动）**：`/api/songs` 每首歌的 JSON 补 `pin` 字段 = `PinyinInitials.initialsOf(artist + title)`。`initialsOf` 是逐字映射，拼接串的包含检查恰好覆盖服务端 `matches()` 的三项检查（歌手键/歌名键/拼接键），口径不退化。
- **页面**：进页（名字门禁通过）时 `loadCatalog()` 一次拉全量歌单缓存；搜索框每次 `input` 即时用纯函数 `suggestRank` 本地算联想——第 1 个字就出结果，无网络等待、无过期响应问题。匹配口径：歌名/歌手子串包含（任意位置）+ 拼音首字母（查询去空格后比 `pin`，`fh cq` 也能命中）；语言/性别筛选 chip 对联想同样生效，与主列表口径一致。排序三档：歌名以输入开头 → 歌名/歌手子串命中 → 拼音命中；上限 20 条，超出追加一行不可点提示（新 i18n key `sugMore`：还有更多结果，继续输入缩小范围 / More matches — keep typing to narrow it down）。
- **点中即点歌**：联想条目整条可点（17px 字、14px 上下内边距的大点击区），显示「歌名 — 歌手」；点击走既有 `order()` 通道（回执马上开唱！/已加入，前面还有 X 首、重复点歌提醒一律照旧），随后清空搜索框与联想列表并刷新主列表。
- **布局**：联想框 `#sug` 固定在搜索框正下方（筛选 chip 之前），`max-height:42vh` 内部滚动，出现/消失不顶动搜索区。
- **不退化核对**：按歌手/分类浏览、已点歌单面板、伴舞选择、中英切换、名字门禁的代码路径未动；切语言时联想按新语言重算（提示行随之换语言），点收藏/最近 tab 时联想收起。

### 测试与验证

- **Node 离线校验（`tools/check_suggest.js`，可复跑）**：从 PhonePage.kt 原文抽取 `SUGGEST-BEGIN/END` 标记间的出厂 `suggestRank` 函数本体（不是副本），用真实歌名 fixture（张信哲-爱如潮水、凤凰传奇-最炫民族风、周杰伦-青花瓷、王菲-致青春、Adele-Hello，`pin` 值与服务端真实字表对齐）跑 15 项断言全过：第一个字「爱」即命中、多字渐进缩小（爱→爱如→全名）、「青」时青花瓷（开头）排致青春（中间包含）之前、拼音 `zxz`/`fhcq`/`fh cq`（带空格）均命中、语言/性别筛选生效、空白查询不出列表、25 条命中截断 20 条且 more 标记正确。写作中还抓到一个 fixture 笔误（小情歌的「情」≠「青」），换成致青春后通过——函数本身无误。
- **服务端 socket 端到端（新增 2 个）**：`songsEndpointCarriesPinyinKeys` 真实 socket 拉 `/api/songs`，断言中文歌 `pin` 为真实字表算出的 `zxzarcs`、英文歌 `adelehello`；`suggestionPickFlowLandsInQueue` 按页面真实流程走完一遍——拉目录、取「爱」开头的歌、POST 下单（点歌人小明），断言回执「马上开唱！」且队列里确有此歌、点歌人无误。
- **页面结构（新增 4 个 PhonePageTest）**：联想框位于搜索框之下/筛选之前且内部滚动、条目「歌名 — 歌手」格式；全量目录只拉一次、纯函数标记与 20 条上限在位；点联想走 `order()` 且清空搜索框；溢出提示双语齐备。

### 发布

- GitHub 发布：代码提交 `9ce091c` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.18 （附件 `MySing-1.0.18.apk`）。API 回读校验：asset size `19025881`、digest `sha256:fe8615044cd01ed94bc4fd0ad36784d02162808c43fa93a980df2016a068b565`，与本地逐字一致。

待用户真机验：朋友手机扫码进点歌页，搜歌名打第一个字（如「爱」），下方应立刻列出带这个字的歌；点任意一条即点上、回执照旧、搜索框自动清空。拼音首字母（zxz、fh cq）和中英切换下的提示行也顺手验一下。

## v1.0.19 电视端版面与遥控队列管理（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 2026-11-15 14:04–14:05 连发三条新规——“在电视右也列出已点的歌，拿遥控器的人可以删歌、切歌”“将二维码固定在右上面”，并在切歌口径选项里点选“两个都要”（跳过当前 + 队列置顶都要）。**两条旧规就此推翻作废**：① “队列只在手机、电视只显示下一首横幅”（v1 原案）→ 电视播放页右侧常驻已点歌单面板；② “不插歌”（2026-11-14 21:50 定）→ 电视遥控可把队列中的歌置顶为下一首。置顶是**电视遥控专属**：手机点歌页与服务端接口一律未加（服务端源码 grep `promote` 为 0 处，手机仍只能点歌与删歌）。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.19.apk` |
| 大小 | 19,042,264 字节 |
| SHA-256 | `35f4309b1b92b25cd6e990cee25e3faaff9486dd011206cfa75d3389627ecae5`（前缀 `35f4309b`） |
| versionName / versionCode | 1.0.19 / 20（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **195 个，0 失败**（185 旧 + 新增 10 个，见下） |

### 做了什么

- **二维码固定右上角**：根部角落码由右下角（BottomEnd，下方留 70/88dp）改为右上角（TopEnd，上/右留 12/24dp），尺寸仍 92dp / 紧凑 72dp、“扫码点歌”标签与双语口径不变，焦点永不落码的原则不变。**首页大二维码取消**：首页标题行末尾改放同一张角落卡（行内布局，不遮语言切换钮）；选歌页改为右侧预留卡宽（88/112dp）防筛选钮与列表行滑到码下（原底部 186/224dp 留白随之取消）；播放页顶部“下一首”横幅右端预留 90/114dp 避开码区。
- **右侧已点歌单面板（仅播放页）**：舞台 Row 右侧、二维码下方（顶部留 70/88dp 避码），宽屏宽 23%。行模型为纯逻辑 `QueuePanelModel`：正在唱的标 ▶、不占序号；后面按 1、2、3… 编号，每条「歌手 - 歌名」+ 点歌人小字。当前与队列皆空时面板不渲染（不留空框）；仅有当前歌时显示 ▶ 行 + “暂无排队”。队列变化走既有 `KaraokeState.version` 状态源实时刷新，无新线程；删除/置顶都经 `KaraokeState` 落到同一 `PlayQueue`，手机端已点面板同源同步可见（已核对：手机删歌走的正是同一 `removeWaitingAt`）。小人左侧 32% 屏宽区与歌词扫光未动，只是歌词列随舞台收窄。
- **遥控删歌 / 插歌置顶**：面板等待行可聚焦；OK 展开行内动作（「下一首唱」仅对第 2 位及以后出现——已是第一位的歌置顶无意义，实际口径：**动作不出现**；「删除」恒有），焦点自动移入第一个动作，**点中动作再按 OK 才执行**，返回键先收动作、再把焦点还给控制条。删后焦点落回同位置的行（删空则回控制条），置顶后焦点回到第 1 位行。动作执行走 `PlayQueue.promoteWaitingAt`（新增纯逻辑：移到队首、其余相对顺序不变、当前歌不动、越界返回 null）与既有 `removeWaitingAt`，均经 `KaraokeState` 同步 bump。触屏点行同样展开动作。
- **焦点进出路径**：控制条按**上键** → 面板第一首等待行（代码内显式处理，不靠空间猜测）；面板内上下键在行间移动、最下行再往下由框架空间搜索落回控制条；面板内**返回键** → 先收行内动作 / 再回控制条首颗钮（暂停/继续），不退出播放页；焦点落到任意控制条钮上即视为已离开面板。面板行/动作钮获得焦点时置位 `panelFocused`，是返回键分流的依据。
- **切歌（跳过当前）**：核对现状——控制条既有「不唱了」即此功能：二次确认弹窗 → 停当前、不鼓掌（鼓掌只在自然唱完路径）→ 接下一首或回首页，行为与“切歌”一致，**未新造按钮**，中文文案仍为「不唱了」。
- **双语**：新 key 4 个全进 TvStrings 双语表（QUEUE_PANEL_TITLE 已点歌单/Queue、QUEUE_PANEL_NEXT 下一首唱/Play next、QUEUE_PANEL_DELETE 删除/Delete、QUEUE_PANEL_NO_WAITING 暂无排队/Nothing queued），既有 key 对等测试自动覆盖，另补渲染抽查；歌名歌手名照旧不翻译。

### 测试与验证

- **新增 10 个**：PlayQueueTest +5（置顶保序、置顶已在首位无变化、越界/空队列安全、置顶后横幅与接歌口径、删后再置顶序号不错乱）；QueuePanelModelTest +5（▶ 行不编号、空队列无行且面板隐藏、只有当前歌也显示、未播时从 1 编号、置顶动作仅对 index>0 提供）。StringTablesTest 既有对等/非空测试自动覆盖新 key，另加新文案渲染断言。
- **未改动核对**：手机点歌页（PhonePage.kt）与服务端（KaraokeHttpServer.kt）本版零改动（git diff 不含两文件）；服务端无置顶接口，手机无法置顶。

### 发布

- GitHub 发布：代码提交 `5f40cc5` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.19 （附件 `MySing-1.0.19.apk`）。API 回读校验：asset size `19042264`、digest `sha256:35f4309b1b92b25cd6e990cee25e3faaff9486dd011206cfa75d3389627ecae5`，与本地逐字一致。

待用户真机验：① 首页/选歌/播放/换歌间隙四处二维码都在右上角、首页不再有大码；② 唱歌时右侧面板出队列（▶ + 编号 + 点歌人），手机新点一首面板即时多一行、手机删一首面板即时少一行；③ 遥控：控制条按上进面板、OK 展开动作、「下一首唱」后横幅与面板首位即变、删除后序号顺移、返回键两段回控制条；④ 已是下一首的那首展开时只有「删除」；⑤ 英文模式下面板标题/动作文案跟随切换。焦点在真机的细微手感（面板最下行按下是否稳落控制条、触屏点行是否同步聚焦）以真机为准。

## v1.0.20 空闲随机垫场（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 2026-11-15 14:13 新规——“没人点歌时就放随意歌”。**旧规就此推翻作废**：“队列空回首页、不自动乱放别的”（v1 原案）→ 凡没有真人点歌在唱、等待队列为空，电视自动随机放歌垫场，电视不冷场。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.20.apk` |
| 大小 | 19,058,643 字节 |
| SHA-256 | `ceccca8f8e6ec52a5ae1ec4ce67e1a0358347b0502d0d8ebe4a844efa3d7eb31`（前缀 `ceccca8f`） |
| versionName / versionCode | 1.0.20 / 21（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **208 个，0 失败**（195 旧 + 新增 13 个，见下） |

### 做了什么

- **状态建模**：`QueueEntry` 新增类型化来源标记 `source: EntrySource { ORDERED, FILLER }`（默认 ORDERED，真人点歌的全部既有构造一字未改）。垫场歌是独立构造的 FILLER 条目，**从不进 PlayQueue**——只活在 `state.nowPlaying` 里。鼓掌判定、下一首横幅、面板标注、让路判定全部读这个标记，不靠点歌人名字符串猜。连带好处：手机端队列 JSON 只读 PlayQueue（已核对服务端源码），垫场歌天然不出现在手机已点歌单里；垫场歌也不进「最近唱过」。
- **触发**（纯逻辑 `FillerPolicy.shouldAutoStart` 把关：开关开 + 歌库就绪 + 真队列全空 + 无正在播放/庆祝中）：(a) App 启动后歌库就绪（缓存先行与正式扫描两处都挂）且无人点歌 → 开始随机，首页自动切到播放页；(b) 真人队列唱完变空 → 掌声（属于刚唱完的真人歌）播完后无缝接随机；(c) 随机歌自然放完 → 直接再随机一首（无掌声、无换歌倒计时）。随机歌在浏览页/首页上结束时只在后台续播，绝不把用户从当前页拽回播放页。
- **让路**：垫场期间真队列恒为空，任何真人点歌（手机或电视）到达即走既有 `order()` 的“空队列立即开唱”通道，`onShouldStart` 直接 `load()` 新歌——垫场歌当场切掉，不进任何历史。真人队列非空时垫场永不启动（`queueIdle` 门）。
- **随机挑选**（纯逻辑 `FillerPicker`）：随机源 = 全部曲库索引（只用扫描目录，不读标签）；最近 8 首不重复窗口（曲库小时窗口缩为曲库数−1，单曲库也能一直放），种子可注入、单测可复现。
- **垫场歌的待遇**：唱完不鼓掌（`onSongEndedNaturally` 按来源分流，连鼓掌场面都不出）；伴舞走点歌人为空的既有默认口径 = 默认春丽；右侧队列面板把垫场歌显示为 ▶ 当前行、副标「随机」（新 TvStrings key `QUEUE_PANEL_RANDOM`，ZH 随机 / EN Random，行模型 `Row.isFiller` 承载），等待行永远只有真人点歌；顶部下一首横幅只看等待队列，垫场时无真人排队就不显示、不预告随机；播放页标题行只显示歌名、不挂“某某点的”。
- **开关**：播放控制条在小人开关旁加「🎲 随机开/关」chip（新 key `FILLER_ON/FILLER_OFF`，ZH 随机开/随机关、EN Random on/off），`Prefs.randomFillerEnabled` 持久化、默认开。关掉时若正在放垫场歌 → 立即停掉回首页空闲态（旧行为）；关着时启动 App 不自动播；重新打开且舞台空闲则立即开始垫场。遥控「不唱了」切掉垫场歌 = 直接换下一首随机（与确认弹窗“接着唱下一首”文案一致）。
- **不许动核对**：真人点歌的回执、重复提醒、鼓掌、接歌、面板删歌/置顶、手机端行为全部未改（PlayQueue 的 add/advance/promote/remove 逻辑零改动，只有 QueueEntry 加了默认字段）；服务端与手机点歌页本版零改动。

### 测试与验证

- **新增 13 个**：`FillerPickerTest` 10 个（空曲库无歌、单曲库恒放、双曲库严格交替、20 首曲库 200 连抽窗口内零重复、5 首曲库窗口缩至 4 且无饿死、reset 清历史；策略门：四道门缺一不可、鼓掌只给 ORDERED、只有 FILLER 让路、QueueEntry 默认 ORDERED）；`KaraokeStateTest` 2 个（垫场在播时来单经 onShouldStart 立即开唱且队列无垫场残影；真人歌在唱时点歌只排队不重复开唱）；`QueuePanelModelTest` +1（垫场当前行 isFiller 标记与可见性）；`StringTablesTest` 既有对等测试自动覆盖新 key，另补新文案渲染断言。
- 待用户真机验：① 冷启动 App、歌库就绪后应自动开始随机放歌并切到播放页；② 垫场中手机点一首中文歌 → 垫场歌立刻被切、真人歌开唱，回执照旧；③ 垫场歌放完不鼓掌、直接接下一首随机，面板当前行标「随机」、手机已点歌单里始终看不到垫场歌；④ 真人歌唱完 → 鼓掌 → 队列空了接随机；⑤ 控制条关掉「随机」→ 垫场歌停、回首页，重开 App 不自动播；再打开开关 → 空闲时立即开始垫场；⑥ 连放十几首垫场，留意不应短期内重歌。

### 发布

- GitHub 发布：代码提交 `8521130` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.20 （附件 `MySing-1.0.20.apk`）。API 回读校验：asset size `19058643`、digest `sha256:ceccca8f8e6ec52a5ae1ec4ce67e1a0358347b0502d0d8ebe4a844efa3d7eb31`，与本地逐字一致。

## v1.0.21 — 电视后台改舞者名（2026-11-15）

用户需求（2026-11-15 14:14）：电视上做个后台，可以改小人的名字，朋友来了改成朋友的名字。

- 首页新增「后台」入口，设置页列出六位舞者与当前显示名；遥控选中按 OK 弹系统编辑框改名（去空白、空名不存、上限 10 字），存 Prefs；页底「恢复默认名」二次确认后一键清掉全部自定义名。
- 显示名收敛为单一解析口径：有自定义名时中英模式都原样用它（人名不翻译），没有才走默认双语名。电视端标注、手机点歌页选人卡片（服务端只读下发）全部跟随；按点歌人保存的选人记录存的是舞者 key，不受改名影响。
- 手机端无改名能力：服务端无 rename 路由（源码注释与 grep 双证），改名只在电视后台。
- 产物：MySing-1.0.21.apk，19,058,663 字节，SHA-256 f3e334f4a51e4956a7c56fe03bb0910cd556f684efa1251d59cec536bfa9c58f，versionName 1.0.21 / versionCode 22，共享 keystore 签名。单测 222 个 0 失败。代码提交 88fb649 已推送；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.21 ，API 回读 asset size 19058663、digest 与本地逐字一致。
- 待用户真机验：后台改一位舞者名 → 手机重扫点歌页，选人卡片应显示新名；恢复默认后卡片回默认名。

## v1.0.22 — 舞者加动作（60 姿势）+ 节拍重音 + 三条同日追加（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 2026-11-15 14:47——“我非常喜欢小人跳舞，可以多加一些动作，跳舞跟着拍子”，及同日三条追加（邀请放慢 / 没前奏直接跳 / 尾奏三段收尾，见下各单列一行）。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.22.apk` |
| 大小 | 22,246,757 字节 |
| SHA-256 | `e99d63d204d6fd782c98ebcabf533e64bdc11f3ffb315876839daa24038e35af`（前缀 `e99d63d2`） |
| versionName / versionCode | 1.0.22 / 23（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **237 个，0 失败**（222 旧口径更新 + 新增/改写，见下） |
| 舞者资源 | aapt2 实测 372 条 dancer 资源（旧 252 + 新 120） |

### 每位舞者 +20 姿势（40 → 60）

- **实际入库帧数：六人全部 60/60，无一缩水。** modern_woman / ancient_woman / sumo / ancient_girl / baby / robot 各新画 4 张表 × 5 姿（`dancers/poses/extra60/`，驱动 `gen_sheets.py`），姿势与旧 40 个明显不同（转身、踢腿、招手、跳跃、摆臂、蹲伏等）。相扑严格沿 v5 定版母图（灰腰带 + 白色前襟 + 流苏，逐帧核对无漂移）；其余五人沿各自既有母图。
- **抠图同管线同标定**（`cutout_poses60.py`）：新 20 帧与旧 40 帧共用同一角色缩放与脚底基线——五位基础舞者的缩放按 cutout_poses40 同法从原始 A/B 表反推并与已发货帧 1..10 逐帧验高（diff ≤3 px，缩放值与 v1.0.7 记录逐字一致：modern 0.573404 / ancient 0.539446 / ancient_girl 0.380299 / baby 0.314702 / robot 0.5800 共识值）；相扑 scale = 500 / 旧表最大内容高，与 40 张已发货母图验高：中位 diff 2 px，唯一离群是母图 35 号（12 px，v1.0.12 手工调整过的帧，已在脚本里改成稳健判定并点名记录）。画布高 600、底边距 12、画布宽与已发货 _40 逐人一致。
- **坏帧处理（如实记录）**：① 机器人 Y3 表初版第 4、5 人物手臂伸展过宽、核心粘连（只分出 4 个核心），整表重画（收紧手臂幅度）后通过；② 相扑 Y1 两姿同侧四股——42 号帧按既定修法水平镜像，与 41 号成左右一对；43 号撒盐姿势手边盐粒保留（属动作本身）；③ 定量 QA（藏青残留像素 + 脱离分量扫描）后定点清理（`clean60.py`，逐分量可审计）：多帧去除被手臂/纱袖围住的整块藏青背景（ancient_woman_48 去 25,708 px、_53 去 20,464 px 等），modern_49 / ancient_woman_41 / ancient_girl_47 各去掉一块邻位人物的头发/手/袖碎片，baby_42 去掉 456 px 漂浮碎片；机器人 46 号帧（敬礼）生成时头部着色偏深灰，是生成色差、轮廓与五官完整，保留入库。
- **重新编排**：`dancers/resequence.py` 对 60 帧重跑闭环最优序（帧 0 站立仍钉首位），`DancerPoseOrder` 更新为 60 长排列（单测排列断言同步改 0..59）。平均相邻代价（与 v1.0.17 同口径：渲染画布几何上的预乘 RGBA 缩略图 RMS）：

  | 舞者 | 重排前 | 重排后 | 降幅 |
  |---|---|---|---|
  | modern 春丽 | 0.2618 | 0.2109 | −19.5% |
  | ancient 林语嫣 | 0.2789 | 0.2198 | −21.2% |
  | sumo 姿三四郎 | 0.2362 | 0.1805 | −23.6% |
  | ancient_girl 小蛮 | 0.1933 | 0.1549 | −19.9% |
  | baby 哪吒 | 0.1790 | 0.1385 | −22.6% |
  | robot 瓦力 | 0.2948 | 0.2489 | −15.6% |

- **手机选人动画卡**已用新 60 帧按新舞序重抽样（`make_anim.py`：舞序第 0/6/12/…/54 位共 10 帧，280 ms/帧、高 420 px），六张共 713,666 B。工程根 `pose-order-preview.webp`（851,410 B）同步重做：春丽 60 帧按新序循环，供装机前肉眼验舞步链。

### 节拍收紧：律动重音

- 现状核查（先说清）：姿势切换相位本就由播放位置 ÷ 每首歌实测 BPM 推导、落在拍点网格上，本版未动这条链；新增的是**律动重音**。
- **重音脉冲落点一句话**：脉冲加在 `DanceMotion` 的整体变换 `Pose.sinkFraction`（下沉量）上，由与换帧同一个 beats 相位纯函数 `beatAccent()` 指数衰减生成——拍点处峰值、拍间衰减，不另起时钟，暂停即冻结。
- 分角色幅度（沿各人既有律动口径）：现代组强 0.12、古代组柔 0.06、相扑半频 0.08（与既有 0.10 膝沉叠加，峰值 ≈0.18）、婴儿 0.05（仅 DANCING 段）、机器人保持硬步进——每拍第一 1/4 拍整段 0.10、其余为 0，无衰减曲线。慢歌（<80 BPM）全部按既有 0.7 系数自动减弱。

### 三条同日追加（各单列一行）

- **邀请动作放慢**：前奏邀请的前倾周期由 2.0 秒改为 **4.0 秒**（`DancerActivity.INVITE_PERIOD_MS` 2_000 → 4_000，整整慢一倍），倾角幅度不变、仍平滑正弦、暂停冻结。
- **没前奏直接跳**：第一行歌词 `startMs` 小于 **2.5 秒**（命名常量 `MIN_INTRO_MS = 2500`，纯函数 `hasIntro()` 判定）视为没前奏，从 0 秒起直接 DANCING、不做邀请；2.5 秒之后才进入 INVITING；无 LRC 的歌整首跳舞（既有口径不变）。
- **尾奏三段收尾**：最后一行歌词结束 → ① **定格**最后一个舞姿 **2.0 秒**（`OUTRO_HOLD` 新状态，`OUTRO_HOLD_MS = 2000`；帧定格、整体变换在这 2 秒内平滑回正）→ ② 约 **0.4 秒淡化**（`OUTRO_SETTLE_MS = 400`）换到既有站立帧 → ③ 按既有喘气口径站立呼吸到歌结束（周期 2.0 秒、机器人半幅不变）。定格期间 BabyFall 不触发（HOLD 与 STILL 同口径）；机器人定格与换帧保持硬步进、不淡化；尾奏短于 2 秒时歌自然结束、鼓掌流程照旧；无 LRC 不进这套。

### 不许动核对

三段状态判定（除新增 OUTRO_HOLD 与 INVITING 门槛外）、BabyFall 节奏（12–20 拍周期，HOLD/STILL/鼓掌不摔）、淡化比例（FADE_FRACTION 0.35、机器人例外）、每拍一帧、鼓掌画面与掌声（仍 v1.0.16 真实录音，未扩帧）全部未动。

### 测试与验证

- **237 个单测 0 失败**：新增/改写覆盖——邀请周期 4 秒断言；`hasIntro` 边界（2499 直跳 / 2500 邀请 / null 直跳）与 2.4 s 直跳、2.6 s 先邀请的相位测试；重音 6 测（拍点峰值且逐拍重复、衰减包络纯度、现代 > 古代、相扑半频叠加 0.18 vs 衰减拍 ≈0.107、机器人 120 BPM 硬步进、慢歌减弱）；尾奏 6 测（定格起止边界、回正中点值、0.4 秒淡化窗口、喘气相位接续、短尾奏带总时长、无 LRC 不定格）；`DancerPoseOrderTest` 全面改 60（排列 0..59、IDENTITY 60、绕环 60 拍）。其中机器人硬步进测试初版误用 60 BPM（慢歌区，0.7 系数使峰值为 0.07），已改 120 BPM 并注明原因——实现无误、错在测试口径。
- 待用户真机验：① 唱一首快歌：小人动作明显变多（60 个轮换）、每拍有一下沉重音、姿势链顺滑；② 前奏长的歌：邀请动作明显变慢（4 秒一周期）；③ 第一句歌词来得快的歌：小人从头直接跳；④ 尾奏长的歌：最后动作定格 2 秒缓缓回正 → 淡化站立 → 喘气；短尾奏歌鼓掌照旧；⑤ 机器人仍是硬步进顿挫感、无淡化。

### 发布

- GitHub 发布：代码提交 `fc87361` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.22 （附件 `MySing-1.0.22.apk`）。API 回读校验：asset size `22246757`、digest `sha256:e99d63d204d6fd782c98ebcabf533e64bdc11f3ffb315876839daa24038e35af`，与本地逐字一致。

## v1.0.23 — 换歌交接仪式（2026-11-15）

构建日期：2026-11-15（America/New_York）。动因：用户 2026-11-15 15:25–15:26 的想法并确认流程无误——“歌曲最后6人一起出来鼓掌，放大第二首点歌者歌手歌名，然后6人一起做个邀请动作，其余人退下，留一人”，并追加“留一人再邀请一次”。

| 项 | 值 |
|---|---|
| 产物 | `~/workspace/shield-karaoke/MySing-1.0.23.apk` |
| 大小 | 22,263,160 字节 |
| SHA-256 | `e66326df3f346f11b7f6a20b956d8d71075a06b65550b0bbba066715a03f4fb5`（前缀 `e66326df`） |
| versionName / versionCode | 1.0.23 / 24（aapt badging 实测） |
| 签名 | 同一把共享 keystore，证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与历版逐字一致，可覆盖安装 |
| 单元测试 | `:app:testDebugUnitTest` **250 个，0 失败**（237 旧 + 新增 `CeremonyTest` 13 个） |

### 仪式时间轴（纯状态机 `ui/Ceremony.kt`，常量全抽出）

只在「一首完整唱完、且有下一首真人点歌」时走仪式；无下一首（回首页/接随机垫场）维持原鼓掌收尾；“不唱了”照旧无鼓掌无仪式。总长 **8,400 ms ≈ 8.5 秒**（3.8 + 2 + 0.6 + 2），取代原固定 5 秒倒计时（`interludeSeconds` 不再在此路径使用）：

1. **鼓掌段 3.8 s**（`CLAP_MS`，与掌声同长）：六人排队鼓掌（既有 `clapPose` 与真实掌声原样），画面中央大字卡（卡片底、32sp 金字）显示锁定下一首的「下一首：某某点的 歌手 - 歌名」——文案直接用锁定条目走 `TvStrings` 的 `NEXT_UP` 双语口径，与顶部横幅同词；仪式期间顶部横幅让位（不重复同一句），大字卡下保留小号剩余秒数。
2. **六人同邀 2.0 s**（`GROUP_INVITE_MS`）：六人同时换邀请帧做一个完整邀请手势——`Ceremony.invitePose` 把 v1.0.22 的 4 秒鞠躬周期按双倍速压进 2 秒（形状、幅度、机器人半幅全保留），手势起止倾角为 0，段与段衔接无姿势跳变。
3. **五人退下 0.6 s**（`EXIT_MS`）：整排六人淡出（`leavingAlpha` 1→0），同时留守者在其常规伴舞位置（歌词左侧 32% 区、70%/62% 高）淡入（`keeperSoloAlpha` 0→1，与前者处处互补为 1）——是溶解交接、不是瞬移；留守者站位与正常播放渲染完全一致，下一首开播时无缝接管。
4. **一人独邀 2.0 s**（`SOLO_INVITE_MS`）：留守者单独再做一个完整邀请手势。
5. **开唱**：仪式结束即 `startSong(锁定条目)`；该舞者的 DancerActivity 判定照旧——有前奏续邀请态、没前奏直接跳舞；独邀手势结束时倾角归 0，与歌曲前奏邀请的起点平滑相接，无消失重入或闪烁。

**留守者解析**：仪式开始时按下一首点歌人走既有 `Prefs.dancerFor` → `Dancers.byKey` 锁定（电视点的默认春丽、旧“girl”记录回落默认，均沿既有口径）；`Ceremony.keeperIndex` 按 key 在 `DancerKeys.ALL` 中定位（与 v1.0.21 的自定义显示名无关，名字只影响称呼、不影响是谁）。

**中断口径（实际采用）**：仪式锁定开始时的下一首（该条目此时已是队列 `current`）。只动等待列表的操作（删等待歌、置顶、新点歌）不影响锁定、不打断仪式。驱动协程在**每个段落边界**复查锁定：若锁定条目已不是队列 `current`，仪式在该边界中止，按届时队列重新判定——有新 `current` 就立即开唱它（`START_CURRENT`），队列已空则走空闲路径（垫场/回首页，`END_TO_IDLE`）；判定为纯函数 `Ceremony.boundaryAction` 并有单测。遥控“不唱了”等走既有 token 机制使仪式驱动直接作废（歌已结束、控制无对象，未做特殊处理）。下一首的加载仍在仪式结束时由 `startSong` 完成（本地文件加载快，与原 5 秒间隙同口径，仪式不阻塞任何预热）。

### 测试与验证

- 新增 `CeremonyTest` 13 个：总时长=四段之和=8400；五段边界逐点（3799/3800、5799/5800、6399/6400、8399/8400）与负 elapsed；各段内进度；退下/留守两条 alpha 曲线（边界值 + 退出段内处处互补为 1）；仪式邀请手势（起止倾角 0、中点达满幅 `INVITE_LEAN_DEG`、机器人半幅）；留守者解析（六人全对位、null/旧 key/乱码回落 0）；中断判定三分支。既有 237 个全过，只增不减。
- 待用户真机验：① 连点两首，第二首有人点：第一首唱完 → 六人鼓掌 + 中央大字卡报第二首（谁点的、歌手、歌名）→ 六人同邀 → 五人淡去、下一首的舞者在左侧淡入 → 她独邀一次 → 第二首开唱，前奏/直跳衔接自然；② 队列只有一首：唱完仍是原鼓掌收尾（无仪式），随后回首页或接随机垫场；③ 唱一半“不唱了”：无鼓掌无仪式，直接下一首或回首页；④ 仪式进行中用手机删一首等待歌/置顶另一首：仪式不中断，仍按原定的下一首开唱。

### 发布

- GitHub 发布：代码提交 `ca0ac20` 已推送 main；Release https://github.com/dannycom888/MySing/releases/tag/v1.0.23 （附件 `MySing-1.0.23.apk`）。API 回读校验：asset size `22263160`、digest `sha256:e66326df3f346f11b7f6a20b956d8d71075a06b65550b0bbba066715a03f4fb5`，与本地逐字一致。

## v1.0.24 — 垫场没画面没歌词修复 + 播放页空间让位（2026-11-15）

用户真机报告（2026-11-15 16:09）：随机垫场（FILLER）在播时电视上没画面、没歌词；同日 16:48 追加：播放页尽量留空间给歌词和小人。

### 根因（查实，非猜测）

播放本身从未分叉：`startSong()` 对 FILLER 与 ORDERED 一视同仁地加载音频、LRC、舞者与标题——垫场歌的歌词一直在内存里装好，只是没人看得见。**分叉只在屏幕导航**：旧 `maybeStartFiller(moveToPlayer)` 只有冷启动那条默认路径（moveToPlayer=true 且当时恰好在 HOME）才把播放页带到前面；而 `startNextFillerOrHome()` 恒以 `moveToPlayer=false` 调用——也就是说“点播唱完、队列排空接垫场”和“垫场续播”这两条主路径**永远不切屏**。于是队列在主人逛 Browse/Admin 时排空、或主人垫场中途退回首页后垫场续播，都会变成声音在放、画面停在别的页面：没画面、没歌词。附带一个不一致漏口：旧版“不唱了”退出最后一首点播（advanceAfterQuit 返回 null）直接回首页、根本不试垫场，与自然唱完的排空路径口径不同。

### 修法

- 新增纯类 `FillerSession`（queue/FillerPicker.kt）建模“垫场会话”：会话的第一首垫场是**入场边沿**——从任何页面（Home/Browse/Admin）把播放页带到前面一次；会话内续播永不动屏幕（主人主动离开不硬拽回；他从首页“正在唱”卡进播放页时，因 startSong 早已按点播同路加载，歌词/舞者/标题完整在屏）。会话在点播开播、关掉随机开关、或垫场开播失败致舞台空闲时结束，下一次垫场重新成为边沿。
- `maybeStartFiller()` 去掉 moveToPlayer 参数，边沿判定收敛到会话模型；“不唱了”退出最后一首后同样先试垫场、开不了才回首页（补上漏口）。
- 新增 `FillerSessionTest` 7 个用例（首首是边沿、续播不是边沿、点播/停止结束会话、完整聚会弧线等）；全量 **257 个测试 0 失败**（250 旧 + 7 新）。

### 播放页空间让位（纯布局，逻辑零改动）

- **已点歌单面板**：由 Row 里占死整列改为舞台内**右上半透明浮层**——宽度 23% → **19%**，背景 alpha 0.55 → **0.65**（浮在歌词上仍可读）；舞台/歌词恢复全宽。遥控 ↑ 进面板、行内 OK 出「下一首唱/删除」、返回两段式的焦点路径与操作口径完全不变；面板为空仍不渲染。
- **伴舞长高**：高度 屏高 70% → **80%**（紧凑 62% → **72%**）；左侧区宽 32% → **36%**。
- **控制条**：仍常驻不隐藏；共享 Chip 纵向 padding 6dp → **4dp**（各页按钮同减 2dp 高，播放控制条直接受益），字号 13sp 不变、可读可聚焦。
- **歌词字号**：当前行 40 → **42sp**（紧凑 27 → 28sp），其余行 25 → **26sp**（紧凑 17 → 18sp）；行距结构与扫色实现未动。
- 下一首横幅维持 36/26dp 细条；右上角二维码位置尺寸未动；仪式大字卡未动。

### 产物与发布

- MySing-1.0.24.apk：22,263,154 字节，SHA-256 9d4e9b1a191081115fe266f1a7004661337d7d8c4d2308ddc711a64abb75f8d2，versionName 1.0.24 / versionCode 25，共享 keystore 签名（1336ee0a…befcc）。注：构建中断留下的同名旧文件（与 v1.0.23 同字节的不可信件）已删除，本包为源码干净重建。
- 待用户真机验：① 冷启动歌库就绪 → 垫场开播并切播放页，歌词/舞者/标题齐全；② 点播唱完队列空 → 接垫场、画面留播放页、歌词继续滚；③ 垫场中途回首页、再从“正在唱”卡进播放页 → 垫场歌词完整，续播不硬拽人；④ 面板变浮层后歌词变宽、舞者长高，遥控 ↑ 进面板删歌/置顶照旧。

## v1.0.25 — 播放页铺满修复 + 脚下聚光灯光圈 + 泡泡背景（2026-11-15）

用户真机截图（2026-11-15 17:31，Note10+ 横屏，v1.0.24）：垫场已正常上屏播放，但指认「下面空间太多」——控制条下方一大条空、舞台内容挤在上部、歌词只露两行；同日追加两条：17:36「小人脚下做个聚光灯形成的圆圈，向外发光」、17:39「整个背景做成泡泡在后面飘下」。

### 根因（像素级查实，非猜测）

先排除两个嫌疑：根布局不缺 fillMaxSize（setContent 根 Box、PlayerScreen 的 Box/Column 层层 fillMaxSize，实测应用藏青底色一直画到截图底边 y=719/720）；截图左黑边（≈57px ≈ 35dp）是 Note10+ 横屏时打孔摄像头的安全内缩，上黑边是状态栏、右浅条是导航栏——都是窗口级 insets，不是应用信箱化，也不是屏内空白的来源。

**真根因在控制条横向溢出**：紧凑横屏下 10 颗芯片（暂停/重唱/原唱/降调/调/升调/小人开/随机开/不唱了/去点歌）+ 8dp 间距的总宽超过了 Column 的可用宽度，Compose Row 把最后一颗「🎵 去点歌」挤进只剩约 30dp 的余量里——截图右下角裁图实证：它变成一条竖排长 pill（🎵 在顶、去/点/歌逐字竖排），从 y≈450 一直拉到 y≈705。这一颗把整个控制行从约 26dp **撑高到约 156dp**（占窗口高 38%）；其余九颗按 Row 默认顶部对齐贴在行顶（实测芯片带 y450–510），于是按钮下方那条「空白」其实是被撑爆的控制行内部，舞台（weight 1f）只分到残余高度——小人和歌词被压在上部、歌词只剩固定块的头两行。v1.0.24 把 Chip 纵向 padding 6→4dp 只省高度、没碰横向总宽，所以没治到根。

### 修法与前后数值

- **控制条装得下**：紧凑档 Chip 横向 padding 12 → **8dp**、字号 13 → **12sp**，控制行间距 8 → **6dp**（非紧凑档不变）；Chip 文字加 **maxLines = 1** 硬规则——今后任何窗口下标签只许裁切、永不许再逐字竖排撑爆行高。控制行恢复约 24dp 高，诊断行在上、控制条贴屏幕最底，下方空白条消失。
- **歌词吃满舞台**：新增纯函数 `LyricsWindow`——当前行中心落在舞台高 **40%** 处、上方固定 2 行已唱、下方待唱行数按剩余高度自适应填满（上限 24）。对比：旧版固定 6 行（-2..+3）顶对齐挂在列首；紧凑舞台（约 300dp）新版显示 **2+1+5 = 8 行**，全尺寸舞台（约 430dp）约 7–8 行。行距、字号、扫色实现未动。
- **小人贴舞台底**：伴舞区改 BoxWithConstraints 自测高度，身高 = **舞台高 × 80%（紧凑 72%）** 从底边起算（旧版是屏高 × 同比例、被压扁的舞台强行裁矮，本人还悬空感强）；脚底基线落在舞台底边。仪式留守者几何同步对齐播放页（区宽 0.32 → **0.36**、身高 屏高×0.62/0.70 → **舞台高×0.72/0.80**），交接不再有尺寸台阶。
- **聚光灯光圈**（单列参数）：`SpotlightGeom` + `spotlightPool` 绘制原语，画在舞者身后——椭圆光池横向半径 = **舞者宽 × 1.2**、纵向压扁比 **0.28**、暖白金 **#FFF6D8**（中心 alpha 0.50 → 0.55 处 0.21 → 边缘 0，与金色扫光同色系），椭圆径向渐变（画布纵向缩放实现真椭圆衰减），中心点落在脚底基线下约 0.35×纵半径处；**静态不闪烁**（稳为先，未做节拍呼吸）。适用：播放页独舞、鼓掌六人排每人一个、仪式六人排每人一个、留守者独邀段保留；光圈 alpha 随仪式行/留守者的淡入淡出同步（五人退下时光跟着人一起消失）。
- **泡泡背景**（单列参数）：根布局第一层、全 App 四页共用——数量 **20 个（紧凑 14）**、半径 **6–42dp**、透明度 **0.05–0.15**、穿屏周期 **7–14 秒**（慢端落在 8–14 秒带）、左右摇摆 **4–14dp / 周期 3–7 秒**；泡泡体为径向渐变（中部近透明、0.92 处边缘略亮成膜）+ 左上高光点；固定种子 `20261115` 确定性伪随机生成（`Bubbles` 纯模型可单测）；`withFrameMillis` 帧时钟驱动、状态节流到约 30fps，不新起计时器，App 退后台停帧即停绘；Canvas 不接焦点不拦点击，永远在所有内容之后。
- 纯布局/绘制改动：播放、队列、垫场、仪式的状态逻辑零改动。新增单测 16 个（LyricsWindowTest 6 + BubblesTest 7 + SpotlightGeomTest 3），全量 **273 个 0 失败**。

### 产物与发布

- MySing-1.0.25.apk：22,263,158 字节，SHA-256 4f475dd00cea7f48108a2816614aefbf657191902e31798fd3a3e77e3e96a740，versionName 1.0.25 / versionCode 26，共享 keystore 签名（1336ee0a…befcc）。
- 待用户真机验：① 播放页控制条贴底、「去点歌」横排正常、下方无空白条；② 歌词铺满舞台（当前行约 40% 高、下方多行待唱）；③ 小人脚踩舞台底、脚下暖金光圈，鼓掌/仪式时每人脚下都有、退下时光随人淡出；④ 首页/选歌页/后台页背景有泡泡缓缓飘落、不挡字；⑤ 泡泡在场时歌词扫色与小人动画流畅度无变化。

## v1.0.26 — 设置归置：选文件夹/改名/改颜色进后台、首页只留小齿轮 + 中英（2026-11-15）

用户三条定调（2026-11-15）：18:19「选文件夹，改名，改颜色进后台，改中英在前台就可改」；18:20「留个小齿轮在前台」；18:27「后台弄个版本号，我老忘记装到那个版本」。首页本身保留不动（删首页已叫停，启动落点未碰）。

### 歌库进后台

- 首页移除「📁 选择歌库文件夹」「🔄 重新扫描」两颗 EntryCard 及首页内的 SAF 选择器；后台页新增「歌库」区：当前歌库文件夹（DocumentFile 报告的文件夹名，无名时回落解码后的 tree 路径；未选过显示「尚未选择歌库文件夹」）、「选择歌库文件夹」「重新扫描」两颗卡、扫描状态与曲目数文案（SCANNING / PICK_FOLDER / LIBRARY_COUNT 与首页同一套键）、点歌服务失败的橙红警告（SERVER_DOWN + 错误详情）。
- 选择器处理抽成共用 `onLibraryFolderPicked()`：takePersistableUriPermission → 写 prefs.libraryTreeUri → loadLibrary，**与原首页逻辑逐字一致，一行未改**；重扫仍是 rescan(已存 tree)。
- 例外（指路，不是把选择器放回前台）：`libraryTreeUri == null`（从未选过文件夹）时，首页第二行留一颗指引卡「⚙ 请进后台选择歌库文件夹 / ⚙ Choose the library folder in Settings」，点击直达后台页；选过之后不再出现。
- 首页的状态行（扫描中/曲目数）与服务警告按「在后台同样可见」的口径处理：首页保留一眼可见，后台歌库区同步全套显示。首页其余元素（六入口、正在唱卡、右上二维码、语言键、credit 行）全未动。

### 首页小齿轮

- 原先占语言键列第三位的「⚙ 后台」文字大芯片换成 `GearButton`：圆形小按钮（紧凑 36dp / 全尺寸 46dp，cardBg 底），白色 ⚙ 字形（18/24sp），可聚焦可点、聚焦时金色描边（与全 App 焦点语言一致），位置仍在右上语言键列内（中文/EN 之下、二维码左侧区域）；腾出的位置不补任何新元素。
- 英文模式下同样是齿轮图标；字形本身无文字，故带双语无障碍描述 ADMIN_GEAR_DESC（zh「后台设置」/ en「Settings」）。后台页标题与既有内容不变。

### 扫光颜色（新功能，后台三选一）

- 新增纯逻辑枚举 `state/SweepColor`（含 ARGB 与存名解析，可单测）：**金色 #FFD54F（默认，即扫光自 v1.0.2 以来的原色，不选不改）/ 浅绿 #A9E5A1 / 浅粉 #FFB7C5**——后两色与 MyLRC v1.0.36 定版逐值一致；橙/红按用户既有否决不提供。
- 持久化：Prefs 新键 `sweep_color` 存枚举名；`SweepColor.fromStoredName()` 对 null/未知值回落 GOLD，坏值永远弄不坏扫光。
- 作用面：只换歌词逐字扫色的填充色（含普通 LRC 的整行高亮色），当前行的青色底光 GLOW_COLOR 不变；UI 其它金色（标题/芯片/横幅的 `gold`）不在作用面内。
- 生效时机：**即时**——选择存进 Activity 状态，播放页重组后下一帧即是新颜色，播放中途改色当场变，不用等下一首；重启后按 prefs 恢复。
- 后台页「扫光颜色」区三颗芯片（金色/浅绿/浅粉），选中态为既有 Chip 的金底黑字。

### 后台版本号

- 后台页底部小字常显一行：zh「版本 v1.0.26」/ en「Version 1.0.26」（TvKey.ADMIN_VERSION，zh 模板自带 v 前缀、en 不带）。
- 取值来自 `installedVersionName()` = PackageManager.getPackageInfo(本包).versionName——**已装包的真实版本号，代码里没有任何写死的版本字符串**，以后每版自动跟着变，零维护。

### 附带

- 后台页内容变长（歌库 + 扫光 + 改名三区），整页改可纵向滚动（verticalScroll），免得紧凑横屏下底部行被裁。
- 改名（v1.0.21）与首页中英切换均未动。
- 新增单测 6 个：SweepColorTest 5（三色齐全且无橙/红、ARGB 逐值、默认金、存名往返、坏值回落）+ StringTablesTest.adminSettingsStringsRender 1（新 10 键双语渲染抽查；双语 parity 由既有测试自动覆盖新键）。全量 **279 个 0 失败**（v1.0.25 为 273）。

### 产物与发布

- MySing-1.0.26.apk：22,279,535 字节，SHA-256 0957f9e3a7e78466f41379f2334af6b26d4a8c37c88d6681be2412a64442bbc3，versionName 1.0.26 / versionCode 27，共享 keystore 签名（1336ee0a…befcc）。
- 待用户真机验：① 首页右上是小齿轮（语言键下方），点/OK 进后台，首页不再有选文件夹/重扫两颗大卡；② 后台歌库区显示当前文件夹，选文件夹与重扫可用（流程与旧首页一致），服务异常时后台同样有橙红警告；③ 全新安装（未选过文件夹）时首页有指引卡直达后台；④ 后台「扫光颜色」切浅绿/浅粉后回播放页，正在唱的歌词扫色立即变色、重启不丢；⑤ 后台底部有小字「版本 v1.0.26」（英文模式为 Version 1.0.26）。

## v1.0.27 — 遥控「现在唱」随意切歌（2026-11-15）

用户定调（2026-11-15）：18:29「前台左边做个已点歌排队，遥控可以随意切歌」，18:30 更正「右边，我说错了」——队列面板位置不变（播放页右侧浮层，v1.0.24 的形态原样未动），本版只加遥控特权：在面板里对任意一首等待歌曲可以立即切过去唱。

### 新动作「现在唱」

- 右侧队列面板里，等待歌曲展开的动作行由两样（「下一首唱」置顶、「删除」）变三样，新「现在唱」排第一（TvKey.QUEUE_PANEL_PLAY_NOW，zh「现在唱」/ en「Play now」，双语 parity 由既有测试自动覆盖）。对队列第一首同样可用——等于跳过当前歌的剩余部分直接唱它（QueuePanelModel.canPlayNow 对所有等待行恒真，与 canPromote 的 index>0 口径刻意不同）。
- 两段式口径不变：OK 展开动作行 → 焦点移入 → 再 OK 才执行；返回两段收起/回控制条。焦点路径补齐第三个动作：新增 PanelFocusTarget.PlayNowAction + playNowRequester，展开行时焦点落在打头的「现在唱」上，D-pad 右行依次是「下一首唱」「删除」。执行后动作行收起，焦点回控制条（如同删空列表后的落点）。

### 切歌语义

- 纯逻辑：PlayQueue.jumpWaitingAt(index) 与 promote/remove 并列——摘出目标条目成为新 current 并返回它，其余等待顺序原样保留，被替换的旧 current 直接丢弃（切掉的歌不走 finish 语义）；索引越界返回 null 且什么都不改。KaraokeState.jumpWaitingAt(index) 同构暴露给 TV UI（bump + onQueueChanged，返回待开唱条目），且刻意不触发 onShouldStart——开唱由 Activity 驱动。
- MainActivity.jumpToWaiting：先做状态摘歌，摘不到（那一行已被手机删掉）就直接返回、当前歌照唱不动；摘到才停播——被切的当前歌与「不唱了」完全同待遇：quitting 守卫下 controller.stop()、无鼓掌、清仪式（celebrationToken++ + clearCeremony）、interlude 清零，随后走普通 startSong 开唱选中歌，其伴舞按它点歌人的既有选择解析。
- 当前是垫场歌时同样立即让位：垫场条目本就不在队列里，无物可清；startSong 的既有逻辑会结束垫场会话（onOrderedStarted），垫场不进队列、不进最近唱过的口径与既有一致。
- 面板只出现在播放页，理论上不存在「无歌在唱」的时刻；万一发生，队列摘歌本身即完成兜底（目标成为 current 并被开唱）。

### 手机零特权（硬核查）

- 服务端与手机点歌页未加任何「现在唱」接口或按钮。对 server 源码整目录 grep `jump|playNow|play_now|PLAY_NOW`（含注释）：**0 命中**。KaraokeState 的接缝注释按既有 promote 的写法注明「TV UI only — the phone server never calls this」，该方法只被 MainActivity 调用。

### 测试

- 新增单测 8 个：PlayQueueTest 6（摘任意位成 current 且余序不变、摘第一首、摘最后一首、越界不变含空队列、摘后 advance 自新 current 继续、被摘条目保留点歌人且横幅指向新的下一首）+ QueuePanelModelTest 1（canPlayNow 每个等待行都给、当前行不给）+ KaraokeStateTest 1（接缝返回条目并成 current、不触发 onShouldStart、过期索引 no-op）。全量 **287 个 0 失败**（v1.0.26 为 279）。

### 产物与发布

- MySing-1.0.27.apk：22,279,543 字节，SHA-256 bbcb23e4ddc93cf53fc255cb9e503f18e4a0fa60c95b14ad59963e0aa5852627，versionName 1.0.27 / versionCode 28，共享 keystore 签名（1336ee0a…befcc）。
- 待用户真机验：① 唱着歌时遥控上键进右侧面板，OK 展开任意一首等待歌曲，动作行是「现在唱 / 下一首唱 / 删除」三颗（第一首只有「现在唱 / 删除」），焦点落在「现在唱」；② 对第二、三首执行「现在唱」——当前歌立刻停（无鼓掌无仪式），选中歌开唱，其余排队顺序不变；③ 对第一首执行「现在唱」等于直接跳过当前歌；④ 垫场在唱、队列里有歌时执行「现在唱」，垫场立即让位；⑤ 手机点歌页无任何「现在唱」入口（预期如此）。

## v1.0.28 — 歌词固定安全带：面板与二维码再也压不到歌词（2026-11-15）

用户定调（2026-11-15 19:06）：「歌词再向左移，不要让歌词被任何遮挡」。实证是他 17:31 的截图——右侧队列面板是浮层，长歌词的行尾滑到面板下面被压住。本版把播放页舞台改成三段恒定分区，歌词只住中间那条带。

### 安全带几何（新增纯函数 ui/LyricsBand.kt，全仓唯一出处）

舞台横向恒为三段：[小人区][间隙 12dp][歌词带][保留带]。保留带 = 队列面板宽 + 12dp 边距，**恒定保留**——面板显示或隐藏、小人在场或隐藏，歌词带的左右缘都不变、不跳动；小人区改为恒定保留（小人隐藏时留空盒），同理。面板与二维码都只住在保留带内，物理上够不到歌词。

| 口径 | 改前（v1.0.27） | 改后（v1.0.28） |
|---|---|---|
| TV（屏宽 960dp、舞台 912dp）歌词列/带 | 小人区 345.6 后吃满剩余，列宽 566.4，右缘贴舞台右缘；面板浮层 182.4 压住列右侧 182.4 | 带左缘 357.6（小人区 345.6 + 间隙 12）、带宽 360.0、带右缘 717.6；保留带 194.4（面板 182.4 + 边距 12），面板左缘 729.6 恰在带右缘 +12 处 |
| 紧凑（屏宽 800dp、舞台 776dp） | 列宽 488，面板压 152 | 带左缘 300（288 + 12）、带宽 312、带右缘 612；保留带 164（面板 152 + 12） |

歌词在带内仍居中——带从右侧收掉保留带后，居中文本整体左移，即用户要的「再向左移」。「正在唱」标题行随歌词列一起进带。面板的焦点进出、三动作（现在唱/下一首唱/删除）行为与 v1.0.27 逐字一致，未动。

### 长行策略：带内整行缩放适配（新增纯函数 LyricFit）

- 改前：maxLines=1 硬裁切——超宽行在列内居中裁掉两端，行尾可藏到面板下。
- 改后：每行先用 TextMeasurer 按基字号实测自然宽（当前行按粗体、侧行按常规分别测），超带宽则按比例缩字号整行适配（LyricFit.scaleFor）；缩放下限 0.6 倍基字号（当前行 42→25.2sp、紧凑 28→16.8sp；侧行 26→15.6sp、紧凑 18→10.8sp）——再小电视上不可读，触底后仍超宽的行宁可由带内裁切兜底。歌词 Text 布局被约束在带内，任何情况下出不了带、进不了保留带。扫光行两层（青底/扫色）共用同一缩放后字号，扫光边界仍由实际布局推导，精度不变。

### 纵向复核（逐项核对，无重叠）

- 顶部横幅：在舞台上方的 Column 行内，右端为二维码预留 90/114dp；歌词窗在舞台内、位于横幅之下，不重叠。
- 二维码：根浮层右上，卡宽约 84dp（紧凑）/108dp，横向落在保留带（164/194.4dp）之内，纵向只压横幅行；队列面板 topPad 70/88dp 既有口径已让面板避开它。歌词带右缘比保留带还左一间隙，二维码够不到歌词。
- 诊断行 / 错误横幅 / 控制条：都在舞台下方的 Column 内顺序排布，与歌词窗不重叠。
- 仪式大字卡：只在不唱歌词的换歌间隙居中出现（既有口径，按任务定义不算遮挡，未动）。庆祝六人行是唱完后的舞台底部浮层（既有行为，未动）。

### 测试

- 新增单测 10 个：LyricsBandTest 6（TV/紧凑两档几何手算值、面板显隐两态带缘恒等、面板左缘恰在带右缘 +GAP、三段一隙铺满舞台、带恒正且不出舞台、退化宽度夹零）+ LyricFitTest 4（合身不缩、适中按比例缩、超长触底 0.6、退化输入不缩）。全量 **297 个 0 失败**（v1.0.27 为 287）。

### 产物与发布

- MySing-1.0.28.apk：22,279,557 字节，SHA-256 c274c96ed4eb975957469c71b50f464fb14405e1075e17a8db3da2527c4a6c9b，versionName 1.0.28 / versionCode 29，共享 keystore 签名（1336ee0a…befcc）。
- 待用户真机验：① 点几首长歌名的歌，队列面板在场时歌词行尾完整可见、不再被压；② 面板里删光队列（面板消失）时歌词位置纹丝不动；③ 关掉小人开关，歌词位置同样不动；④ 长歌词行整体略小一号但整行读得全；⑤ 二维码、横幅、控制条与歌词互不重叠（同前）。

## v1.0.29 — 播放页补小齿轮：唱歌页上也能进后台（2026-11-15）

### 起因

v1.0.26 把进后台的小齿轮只放在首页。用户真机反馈（19:06「我没看到小齿轮」，19:20 附播放页截图）：他日常停在唱歌页——垫场常开、几乎不回首页——唱歌页上没有齿轮，对他等于没有。截图同时证实播放页横幅行左端空着、齿轮有地方放。

### 改法（纯 UI 入口新增，播放/队列/歌词逻辑一行未动）

- **播放页横幅行左端加同款齿轮**：复用首页那颗 `GearButton` 组件本体（同一组件、同一尺寸 36dp 紧凑 / 46dp、白色 ⚙、聚焦金描边、双语无障碍名「后台设置 / Settings」），不存在两颗齿轮漂移的可能。位置：播放页顶部横幅行最左端。横幅行因此由纯文字高 26/36dp 改为恰好齿轮高 36/46dp（+10dp，舞台相应少 10dp 高）——齿轮不压舞台、不压歌词带；「下一首」文字改从齿轮右侧起、行内垂直居中，右端二维码预留（90/114dp）与既有让位口径逐字未动；舞台内居中呈现的「正在唱」标题在歌词带内（v1.0.28），完全不受影响，无微偏。
- **返回落点按门记账**：新增 `adminReturnTo`（默认 HOME）。首页齿轮与未选文件夹时的指引卡进后台记 HOME，播放页齿轮记 PLAYER；后台页的返回键（BackHandler）与页内「返回」卡都改回 `adminReturnTo`——从唱歌页进后台改完设置，返回直接回唱歌页，不再被扔回首页。
- **进出后台的播放口径（核对结论：既有行为本就不停播，无需改齐）**：播放由 Activity 级 PlayerController 驱动，与 PlayerScreen 是否在组合中无关——切到后台页只是换屏，正在唱的歌（含垫场）照常唱、FillerSession 不受触碰；后台期间歌自然唱完的鼓掌/仪式/垫场衔接也都在 Activity 层照常推进（与从首页进后台时逐字一致）。返回播放页后轮询协程重启、位置/歌词/小人按控制器实时位置重组，无缝续上。一个既有口径如实记录：若在后台期间「最后一首点播唱完 + 掌声结束 + 垫场关闭」三者同时成立，既有收尾会把屏幕切回首页（从首页进后台时同样如此）——本版按任务口径沿用未改，它只换屏、不停播。
- **焦点**：齿轮照旧 `.focusable()` 空间导航——不加任何 requestFocus，不抢播放页默认焦点（控制条首钮的既有口径未动）；从控制条方向键向上/向左可达齿轮，齿轮向下可回舞台/控制条，无死角；面板展开与仪式进行时齿轮一直在横幅行内、行为与横幅文字等固定元素一致。首页齿轮保留不动。

### 测试

- 全量单测 **297 个 0 失败**（与 v1.0.28 持平：本版是纯入口与导航落点改动，无新纯逻辑可测，未删任何测试）。

### 产物与发布

- MySing-1.0.29.apk：22,279,539 字节，SHA-256 fe831af9b079c911ff0ac7d3d3bef622de1b73aa9135b4319996bfea03c365d9，versionName 1.0.29 / versionCode 30，共享 keystore 签名（1336ee0a…befcc）。
- 待用户真机验：① 唱歌页横幅行左端有小齿轮，点/OK 进后台，歌继续唱不中断；② 后台按返回直接回唱歌页（不是首页）；③ 垫场唱着时进后台改个颜色再返回，垫场没断、画面跟得上；④ 遥控从控制条能把焦点移到齿轮、再移回来；⑤ 首页齿轮照旧、返回落点仍是首页。

---

## v1.0.30 — 机器人静态图重做 + 六人图方块残留清除 + 开头邀请根因修复（2026-11-15）

### 起因：三条同晚真机反馈

1. 19:44「出来鼓掌的机器人没左脚」。
2. 19:46「一起出来的6人方块背景不协调，拿掉吧」（脚下聚光圈是用户点名要的，不在此列）。
3. 19:48「开头单人邀请动作没出现」。

### 一、机器人静态图：源文件本身残缺，取完好姿势帧重做

- 坐实：`dancer_robot.png` 本身生成坏了——画面左侧腿在髋部断裂、带金色火花状残片、整条小腿残缺无脚；另一腿完好。该图用于鼓掌排队行与尾奏站立（Dancers.kt 注释 "still used for the applause celebration row"），所以缺脚只在这两个场景露出来；跳舞 60 帧完好，用户跳舞时看不出。
- 修法：取跳舞姿势帧 `dancer_robot_01.webp`（正立、双臂举拳、与旧静态图同一姿势气质、双脚齐全、抠图干净）重做静态图，同文件名同引用，零代码改动。
- **关键规格（新旧对比）**：旧图画布 241×600，人物 bbox (0,54)–(241,581)，人物高 527、脚底基线 y=581；新图画布 243×600（人物略宽 2px，UI 按高度缩放无感），人物 bbox (0,54)–(243,581)——**人物顶 54、人物高 527、脚底基线 581 与旧图逐像素一致**，鼓掌排队六人对齐、尾奏站立衔接不跳位。附带好处：尾奏站立本就用姿势帧 01（STILL_FRAME=0），新静态图与它同一形象，两处衔接完全一致。

### 二、方块背景：来源是生成图自带的原稿残留，不是代码

- **代码侧先查清、无罪**：鼓掌行（`showCelebration`）与仪式六人排（`CeremonyStage`）的 Composable 都没有给舞者垫任何背景 Box/卡片，`DancerImage`/`DancerFrame` 是裸 Image；舞台上唯一画在小人身后的东西是脚下聚光圈（用户点名保留）。方块全部烘焙在图片文件里——六人同框时每人带着原姿势原稿的残留碎片/暗角，与舞台不协调；独舞/独邀场景未动（用户没意见，口径不扩散）。
- **逐张目检口径**：12 张图（6 静态 + 6 邀请）逐张在洋红与舞台藏青两种底色上合成目检 + alpha 连通域与「远离不透明核心的暗色半透明像素」普查交叉验证。
- **查出并已修**：
  - `dancer_baby.png`（静态）：混入邻座人物碎片——头后粉色蝴蝶结、左臂后紫红块、右侧白衣角、左脚后邻座白鞋。整图替换为姿势帧 `dancer_baby_01.webp`（双臂上举站立、与旧图同姿势、零残留）：画布 157×600 → 178×600，人物顶 328 / 高 264 / 脚底基线 592 与旧图逐像素一致。
  - `dancer_modern_woman.png`（静态）：右侧有深蓝色直边条带 + 粉色楔形碎片（邻座袖口残片）。外科擦除三轮（色规 + 连通域），本人腿脚毫发无损，普查残留由 12 → 7（边缘像素级）。
  - `dancer_modern_woman_invite.webp`：手下方一条断开的灰色碎片（196px）+ 3px 斑点 + 一团半透明暗钩（约 1,237px）——全部擦除，手与发梢完好。
  - `dancer_ancient_woman_invite.webp`：左边缘 69px 斑点 + 21px 斑点 + 半透明晕（边缘 24px 带整段清掉）。注：她右侧「飘着的手」是她本人手臂经薄纱袖相连、右腰侧黑影是她本人发尾——均属本体，未动。
  - `dancer_baby_invite.webp`：右下角混入机器人鞋尖（银色，x≥126, y≥530 整块擦除）+ 右缘一条细线（x 134–139, y 385–485 擦除）。他双眼正常（洋红合成图上曾显紫色是合成伪影，3 倍放大证伪，未「修」眼睛）。
  - 机器人新静态图天然干净（普查仅剩天线尖 176px，是本体）。
- **查完完好、未动**：`dancer_ancient_girl.png`、`dancer_sumo.png`（静态，两脚两臂齐全、与批准的相扑 v5 定版一致）、`dancer_ancient_woman.png`（静态：普查的 1,352px 经逐点取色证实全是她本人的耳坠红穗、发花、裙褶暗影与薄纱袖）、`dancer_robot_invite.webp`（双腿双脚齐全、普查仅 4px）、`dancer_ancient_girl_invite.webp`、`dancer_sumo_invite.webp`。手机选人动画卡（assets/dancer_anim/）是姿势帧抽样、与静态图无关，未动；跳舞 60 帧未动。

### 三、开头邀请没出现：根因是片头字幕行毒化了首行判定

- **根因（真实歌库数据坐实）**：本歌库的增强 LRC 每首开头都有一段带时间戳的片头块——标题行（多在 0 秒，如「曲婉婷 - 我的歌声里」）+ 词：/曲：等署名行（0–10 秒），真正的第一句 sung 歌词常在 10–30 秒。`DancerActivity.firstLyricStartMs` 原先取的是解析后的**第一行**（含片头行），于是 `hasIntro`（首行 ≥ MIN_INTRO_MS=2500 才算有前奏）把几乎所有歌判成「没前奏」，小人从 0 秒就跳舞、邀请永不出现。全库 1,092 首实测：首行 <2.5s 的有 1,071 首；其中**首句真词 ≥2.5s 却被片头堵死的 924 首**；今天能正常邀请的只有 12 首。真文件例：Beyond《不再犹豫》标题 0.00 / 词 1.60 / 曲 2.60 / 首句 31.02s；《海阔天空》标题 0.72 / 词 7.87 / 曲 8.87 / 首句 18.35s；曲婉婷《我的歌声里》标题 0 / 词 4.82 / 曲 9.64 / 首句 14.47s。
- **修法**：`DancerActivity` 新增 `isCreditLine`（署名标签模式：作词|作曲|编曲|词曲|词|曲|制作人… 后跟全/半角冒号，含英文 "Lyrics by…" 形；必须带冒号——「词不达意」「曲终人散」这类以同字开头的真歌词不误杀）与 `isTitleLine`（文本恰为本歌「歌手 - 歌名」或「歌名 - 歌手」整对才算标题行，别的歌的标题行、只含 " - " 的普通歌词都不跳过）；`firstLyricStartMs` 改名 `firstSungStartMs(lines, artist, title)`，返回第一行非片头行的起点。播放页与诊断行两个调用点同步换口径（都从当前条目的 Song 取歌手/歌名）。
- **次查结论（口径未变、无需改）**：垫场歌与点播歌共用 PlayerScreen 同一条小人渲染路径（全工程只有一处 phase 计算，垫场条目同样携带 Song），不存在垫场独缺邀请的第二条路；换歌仪式独邀结束后开唱走的也是同一 phase 纯函数（无闩锁），修复后有前奏的歌在仪式后会继续邀请到首句真词为止。
- **显示口径未动**：片头行仍照旧显示/扫光（用户截图里一直如此），只有小人的前奏边界换了口径。
- **回归测试（真实文件原文夹具）**：新增 6 个——《海阔天空》真实开头（标题 0.72s、首句 18.35s → 0–18.35s 全程 INVITING、到点转 DANCING）、任务口径夹具（署名行在 0.5s 内、首句 18s）、《大地》式署名行打头（作词 0s、首句 26s）、Youngblood 真实开头（标题 0.58s、首句 0.75s → 仍判无前奏、从 0 跳舞，用户自定规则）、署名标签识别（含「作曲 : mc六道」的冒号前空格形与误杀护栏）、标题行整对匹配护栏。

### 测试

- 全量单测 **303 个 0 失败**（297 基线 + 6 新增，只增不减）。舞者资源条目仍 372（APK 资源表核对）。

### 产物与发布

- MySing-1.0.30.apk：22,409,755 字节，SHA-256 82e9d2b0116dd73fccdd7f13be079c4b988082913ef3ba1a4ee9fc8140fae2ca，versionName 1.0.30 / versionCode 31，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.30 已发布，API 回读附件 size/digest 与本地一致。
- 待用户真机验：① 鼓掌排队里机器人双脚齐全、六人脚下只有光圈、再无方块；② 尾奏站立的机器人双脚齐全；③ 随便点一首前奏长的中文歌（如《海阔天空》），开头小人先做邀请动作、到第一句歌词才开始跳；④ 前奏短的歌（如 Youngblood）照旧一上来就跳。

---

## v1.0.31 — 首页退役 + 改称伴舞 + 二维码卡缩小 + 随机开关去掉 + 左上品牌标题（2026-11-15）

### 起因：用户 19:54–20:04 连下五令

19:54 附首页截图「这页不要了」；同条「将小人改成伴舞，别叫小人」「2维码外的白底小些」；19:57「随机开按扭去掉，没人点歌自动就开」；20:03「左上方做两个吧，中文页面用 一起唱卡拉OK，英文界面用 MySing」；20:04 对品牌字色补一句「金色的挺漂亮」——沿用首页大标题的金色。

### 一、首页退役：舞台即主页

- `Screen.HOME` 与 `HomeScreen` 整体删除，`Screen` 只剩 PLAYER / BROWSE / ADMIN。开 App 直达唱歌舞台；**首次启动未选过歌库文件夹 → 直达后台页**（onCreate 读 prefs 后落点，`adminReturnTo` 默认 PLAYER，返回落到待机舞台）。
- **六个选歌入口并入浏览页顶部**：与旧首页同款 `EntryCard` + 同六个 TvKey（按歌手/按歌名/中文/英文/收藏/最近唱过），动作仍走 `openBrowse(...)` 写浏览页自己的 `listType/filterLang/filterGender/special` 状态——与下方既有筛选芯片是**同一套状态**（点「中文」入口，下方「中文」芯片随之点亮），不是两套入口。
- **歌库状态行随迁浏览页顶部小字**：扫描中/歌库共 N 首/先选歌库文件夹三态文案与旧首页逐字一致，其下服务器未启动的橙红警告同迁；状态行右侧放浏览页自己的齿轮门（进后台记 BROWSE，返回回浏览页）。
- **「正在唱」卡取消**：舞台即主页，浏览页返回键直接回舞台，没有第二落点。原首页底部的署名行「本软件由 Muse 协助制作」移到**后台页底部、版本号下方**（同色同字号口径）。
- **旧「回首页」路径全清**：队列唱空+鼓掌收尾（自然结束无下一首、仪式 END_TO_IDLE、「不唱了」切掉最后一首、垫场续不上）四处原先的 `screen = HOME` 全部改为停在舞台待机——垫场无条件开启（见第四件），这些路径上 `maybeStartFiller()` 照常接上随机歌，舞台不会静默。浏览页 BackHandler 改回 PLAYER；播放页原 BackHandler 删除（舞台按返回 = 系统默认退出 App；队列面板的两段式 Back 不受影响，它有自己的 enabled 条件）。
- **焦点**：播放页进入时新增一次性 `LaunchedEffect` 把焦点明确落在控制条首钮（暂停钮）——舞台成启动屏后不让横幅行的齿轮/语言芯片在树序上抢默认焦点；从浏览/后台返回舞台同理落控制条。

### 二、改称伴舞

- 全量 grep 用户可见文案，「小人」共四处，全部改「伴舞」：控制条开关 `💃 小人开/关` → `💃 伴舞开/关`（TvKey.DANCERS_ON/OFF 中文值）、后台提示 `选一位小人…` → `选一位伴舞…`、恢复默认确认文案 `六位小人的名字…` → `六位伴舞的名字…`。英文侧本来就是 Dancers，未动；诊断行本来就是「舞者/伴舞」，未动；服务端手机页无「小人」字样（选人区本就叫伴舞）。代码标识符、Prefs key（`dancers_enabled`）一律未动，不丢任何既有设置。源码里仅剩两处 KDoc 注释引用用户当年的原话（DancerImages/DancerAnims 的历史出处说明），非可见文案，保留。
- 新增回归断言：遍历全部 TvKey 中文值，断言不再含「小人」二字（`StringTablesTest.v1031Wording`）。

### 三、二维码白卡缩小

- 同一组件 `CornerQrCard` 一处改、全页生效：QR 边长 92→76dp（紧凑 72→60dp），卡内边距 7→4dp（紧凑 5→3dp），「扫码点歌」小字 11→10sp（紧凑 9→8sp），卡片整体明显小一圈。**扫码可靠性口径**：缩的只是卡片自己的白边——`Qr.encode` 的位图内含 ZXing 默认的 4 模块静区，本版逐像素未动，静区规范不受影响；二维码本体仍是纯黑白。

### 四、随机开关去掉：没人点歌自动就开

- 控制条 🎲 芯片删除（控制条剩九颗）；`toggleFiller()`、`fillerOn` 状态、Prefs 的 `randomFillerEnabled` 读写一并删除（旧安装里留下的 `random_filler_enabled` 键从此无人读，无害）。
- `FillerPolicy.shouldAutoStart` 去掉 `enabled` 门，签名改为三门：歌库就绪 + 真队列全空 + 无在播。触发点不变（歌库就绪、队列排空、唱完/切歌收尾）；真点歌切入垫场、垫场唱完不鼓掌、「不唱了」对垫场 = 换下一首随机，口径逐字未动。`startNextFillerOrHome` 随首页退役改名 `startNextFillerOrIdle`（续不上就停在舞台待机，不再有「回首页」）。
- 字符串表 `FILLER_ON/FILLER_OFF` 两键双语同删（双语对等测试守恒）。

### 五、左上品牌标题：金色，中文「一起唱卡拉OK」/ 英文「MySing」

- 位置：播放页横幅行左端、小齿轮右侧（中英芯片紧随其右，同排——中英切换仍在前台可改，是从退役首页搬来的原班芯片，持久化口径不变）。
- 新增 TvKey.BRAND_TITLE：中文「一起唱卡拉OK」、英文「MySing」，双语对等测试覆盖。字色 **金色 #FFD54F**——即代码里既有的 `gold` 常量（0xFFFFD54F），正是删除前首页「MySing」大标题用的同一色值，删首页前取定沿用；字号 24sp（紧凑 17sp）加粗，与旧首页标题同档。标题纯展示、不可聚焦，不抢控制条焦点；横幅行高维持齿轮高 36/46dp 未动，行中央的「下一首」文字与歌词带内的「正在唱」标题均不受影响，舞台不压。

### 顺手清理的死键

- 随本版变更成孤儿的 TvKey 一并删除（双语表同步）：`NOW_SINGING_CARD`（正在唱卡）、`PICK_FOLDER_GUIDE`（首页未选文件夹指引卡）、`FILLER_ON/FILLER_OFF`（随机开关）。

### 测试改动逐项说明

- 基线 303 → **304 个 0 失败**（只增不减）。
- 改写 2 处（行为变更所致，覆盖不减反增）：
  1. `FillerPickerTest.「auto start needs every gate open」`：删去「switch off」一条断言（开关门本身已不存在），其余三门逐门 veto 断言保留，签名改三参。
  2. `StringTablesTest`：删 `FILLER_ON/OFF` 四条断言与 `PICK_FOLDER_GUIDE` 两条断言（键已随功能删除）；`adminSettingsStringsRender` 的导语注释同步（原提及首页指引卡）。
- 新增 1 个：`StringTablesTest.v1031Wording`——品牌标题双语定值、伴舞开/关定值、后台两处伴舞文案定值、全表中文值「小人」清零断言。

### 产物与发布

- MySing-1.0.31.apk：22,409,760 字节，SHA-256 245c1062d50434b7bc14f702f810f3078fbcf1c31de4281a82bf385d68baf724，versionName 1.0.31 / versionCode 32，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.31 已发布，API 回读附件 size/digest 与本地一致。代码提交 64613f9。
- 导航图一句话：启动 → 唱歌舞台（从未选过歌库 → 后台页选文件夹，返回落舞台）；舞台上返回键退出 App（队列面板展开/聚焦时先收面板、回控制条）；「🎵去点歌」进浏览页，返回键回舞台；舞台与浏览页各有一颗齿轮进后台，后台返回键从哪扇门进回哪扇门。
- 待用户真机验：① 开 App 直接是唱歌舞台、左上有金色「一起唱卡拉OK」+ 中文/EN 芯片，切 EN 后标题变 MySing；② 控制条是「💃 伴舞开」、没有 🎲 芯片，没人点歌照样自动随机开唱；③ 右上角二维码卡小了一圈、手机仍能扫；④ 去点歌页顶部有状态行小字 + 六个大入口 + 齿轮，返回回舞台；⑤ 后台页最底部版本号下面有署名行；⑥ 遥控开机焦点在控制条上。

---

## v1.0.32 — 邀请时向前一大步（2026-11-15）

### 起因：用户 20:16 一句话

「邀请的时候伴舞向前一大步，变大。跳舞时再退后」——前奏邀请段她迎向观众走近、站得更大，第一句开唱就退回原位原大小继续跳。

### 实现

- **包络函数**：新增纯函数 `DancerActivity.stepProgress(positionMs, firstSungStartMs, profile)`——前奏开头 700ms 内以 smoothstep 从 0 涨到 1（迈出）、整个邀请段保持 1、第一句开唱后再用 700ms 滑回 0（退后）；全程只由播放位置与第一句真歌词的开始时间决定，暂停即定格在迈步半途；无前奏的歌（MIN_INTRO_MS 规则下本无 INVITING）、无 LRC 的歌恒为 0，尾奏三段（OUTRO_HOLD/STILL）远在退后窗口之外同样恒 0；斜坡窗口另有 min(700ms, 前奏长/2) 的防溢出钳制，两条斜坡永不重叠。
- **两档尺寸**：进度驱动的是她的**布局高度**——跳舞高为舞台高 80%（紧凑 72%），迈满时涨到 95%（紧凑 88%）；走布局而非图层缩放，是为了让她 wrapper 里的脚下聚光圈按她自己的包围盒一起变大，且舞台区 BottomCenter 底锚不变、脚始终踩在舞台底边、向上长高即“走近”感；缩放叠加在既有 DanceMotion 姿态变换之上，邀请的前倾律动与节拍重音分毫未动。
- **机器人量化档数**：瓦力的包络量化为 **4 档硬切**（0 / ⅓ / ⅔ / 1，round 取整）——迈出与退后都是一格一格顿着变，与他既有的步进帧切、¼ 拍姿态表同一口径；其余五人平滑过渡。
- **包围盒与歌词带**：LyricsBand 几何一字未动——她变大只长高与长宽，宽度按帧宽高比（468/600）随高同涨；若长大后的宽度会越过伴舞区右缘（歌词带就起于其外一间隙处），她按恰好溢出的居中量左移，右缘钉死在区界上，生长只向左压在气泡背景上，绝不压歌词带（16:9 屏幕上大档宽度约占区宽 94%，通常并不触发左移）。
- **不套用的排面**：鼓掌六人排与换歌仪式六人排/独邀段的 DancerImage 调用一律未动；仪式独邀结束后若下一首自有前奏，其歌曲自身的 INVITING 迈步会自然接管。

### 测试

- 基线 304 → **315 个 0 失败**（只增不减，零改写）。新增 11 个全在 `DancerActivityTest` 的 v1.0.32 段：斜坡常量 700ms、无词/短前奏恒 0、起点为 0、涨坡中点恰 0.5 且单调、邀请段全程 1、开唱后退坡中点 0.5 并归零、全程扫查不越 [0,1]、机器人量化档位集合与中点 ⅔、机器人同样涨满与退尽、包络与 INVITING 相位对齐且不漏进尾奏。

### 产物与发布

- MySing-1.0.32.apk：22,409,756 字节，SHA-256 2834bf16a9f7c4a4dd4fc440284ee7914cb45cd007bba2774534908eced61e99，versionName 1.0.32 / versionCode 33，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.32 已发布，API 回读附件 size/digest 与本地逐字一致。代码提交 e594e82。
- （过程注记：首轮 assembleRelease 的 Gradle 进程在运行环境重启后成僵尸并占住缓存锁，一度令后续构建假成功/锁超时；已全数杀掉并以 `:app:clean :app:assembleRelease` 干净重建，产物经 badging + 签名指纹 + DEX 内 stepProgress 符号三重核对后才拷贝发布。）
- 待用户真机验：① 点一首前奏长的歌（如《海阔天空》），邀请时她应向前迈一大步变大、第一句开唱平滑退回原位；② 瓦力伴舞时迈步应一格一格顿着变；③ 前奏短的歌（如 Youngblood）照旧直接跳、不迈步；④ 她变大时歌词不被压、聚光圈跟着变大。

## v1.0.33 — 补中间帧（2026-11-15）

### 起因：用户 20:20–21:10 两句话

「总感觉伴舞太机械，能再顺滑些吗」——重排（v1.0.17/v1.0.22）与 35% 淡化都已用满，姿势之间的跨度本身太大。用户点选「补中间帧，治本」，并定下总纲：「我非常喜欢那些舞者，尽量做得完美，让他和市面上的卡拉ok软件分别开来，做到遥遥领先」——补帧覆盖面随之扩围：全环间隙中高于该舞者中位距离者全补，最狠的 5 处一处补两张（1/3、2/3）。资产阶段已先行完成（提交 340d4f0，`dancers/inbetween/MANIFEST.md` 有逐间隙清单：五人各 30 处间隙、40 张，合计 200 张，放弃 0）；本版做集成。

### 集成

- **资源入库**：200 张沿用既有帧的命名续号——每位舞者的补帧接在原 60 帧之后编号 `_61`…`_100`（如 `dancer_modern_woman_61.webp`），进 `drawable-nodpi`，与原帧同一资源口径（600 高画布、底边基线、人物居中——集成前逐项量过：补帧人物 bbox 横向中心 0.500、基线 587，与原帧 0.499–0.501 / 587–588 一致，渲染是高度驱动+居中，接入不会跳位或忽大忽小）。舞者资源条目总数 **372 → 572**（aapt2 dump resources 实数）。`Dancers.frames` 五人各扩为 100 个引用，瓦力保持 60。
- **顺序表扩展**：新帧索引 60..99 按间隙升序分配、双补间隙内按播放顺序 t1 → 中点 → t2；`DancerPoseOrder` 五人的舞步链由 60 步扩为 **100 步**——原 60 帧的相对顺序一字未动，补帧按 MANIFEST 精确插在所桥接的那一步之后（含环尾第 59 步之后、绕回帧 0 之前）。瓦力 60 步原样。帧 0 仍是站立帧，`STILL_FRAME` 语义不变；邀请帧、静态图（鼓掌排）、仪式排面引用的都是 resId/inviteResId 与帧 0，逐一核对不受影响。生成脚本 `dancers/inbetween/integrate_v1033.py` 在落盘前把磁盘文件间隙集与 MANIFEST 表格逐项交叉核对（间隙集、双补集全等才继续），产物记录在 `dancers/inbetween/inbetween_orders.json`。
- **步进与淡化不变**：`PoseCycle` 仍一拍一步走扩展后的环（其 ordered overload 本就按 order.size 取模，零改动），交叉淡化 35% 口径不变——相邻帧更近之后，同一淡化自然更像运动；节拍重音相位不变。**BabyFall 核实**：它只吃绝对拍数（`stateAt(beats, bpm)`，12–20 拍的伪随机周期全在拍域里算），不引用任何帧号/步数，步数 60→100 对它零影响。
- **手机动画卡重采样**：`dancers/make_anim.py` 已更新——五人按新 100 步顺序在位置 0,10,…,90 采样（补帧自然入卡），瓦力仍按 60 步顺序 0,6,…,54 采样且成品与旧卡**字节一致**（md5 未变）。五人新卡合计与旧版同量级（732,506 字节总量含瓦力）。

### 每人步数变化

| 舞者 | 步数 | 补帧 | 桥接间隙 |
|---|---|---|---|
| 春丽 modern | 60 → 100 | 40（单补 25 处 + 双补 5 处×3） | 30 |
| 林语嫣 ancient | 60 → 100 | 40 | 30 |
| 姿三四郎 sumo | 60 → 100 | 40 | 30 |
| 小蛮 ancient_girl | 60 → 100 | 40 | 30 |
| 哪吒 baby | 60 → 100 | 40 | 30 |
| 瓦力 robot | 60（不变） | 0 | — |

### 测试改动逐项

- 新增 `DancerInbetweenOrderTest`（6 个）：五人 100 步/瓦力 60 步与全排列、原 60 帧相对顺序与 v1.0.32 逐字一致、瓦力整表未动、按 MANIFEST 记录重建期望序列与现表逐项全等、每人恰 30 间隙（25 单 + 5 双）且 60..99 各用一次、100 步环一拍一步含绕回。
- 改写 `DancerPoseOrderTest` 4 处：全排列测试拆为瓦力 60/五人 100 两条；一拍一步的环绕验证改用瓦力表（60 步口径不变，100 步口径由新测试覆盖）；blend 环绕断言改按 order.size/last 通用化；BABY 帧号范围 0..59 → 0..99。
- 总数 **315 → 322，全过、0 失败**。

### 产物与发布

- MySing-1.0.33.apk：28,721,104 字节（较 v1.0.32 +6,311,348，约 +6.0 MB，即 200 张补帧与重采样动画卡），SHA-256 5e6ddd5c9284087474c1415272b7650c958099d5e58b047bfeed01c1ab3a5d8f，versionName 1.0.33 / versionCode 34，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.33 已发布，API 回读附件 size/digest 与本地逐字一致。
- 待用户真机验：五位真人伴舞跳起来应明显连贯（尤其原来跨度最大的动作衔接）；瓦力照旧一格一格；鼓掌排、邀请、尾奏站立与 v1.0.32 一致无变化。

## v1.0.34 — 默认名更换：林语嫣 → 陈秀颖、小蛮 → 汉娜（2026-11-15）

用户令：「将林语嫣名换成陈秀颖，小蛮换成汉娜」。改的是**出厂默认名**，不是改名功能本身。

### 改动

- 默认名全量替换（app/src 内旧名残留为 0）：`state/DancerKeys.kt` 的 LABELS（ancient 陈秀颖、ancient_girl 汉娜）与 LABELS_EN（Chen Xiuying / Hannah）、`ui/Dancers.kt` 两处标签、`server/PhonePage.kt` 写死的卡片 alt/span 与中英两份 JS dancers 字典。
- 英文名：陈秀颖 → **Chen Xiuying**；汉娜 → **Hannah**（通行译名）。
- **自定义名口径不动**：后台改过名的舞者，Prefs 自定义名仍优先于新默认名（恢复默认/未改过才显新名）——`DancerNamesTest` 新增一条把这条口径连同新默认名一起钉死。
- 历史不改：BUILD_REPORT 旧章节与 `dancers/inbetween/` 的 v1.0.33 素材生产记录（MANIFEST、集成脚本）里出现的旧名是当时史实，原样保留。

### 测试

- 定值/对等测试按新名更新：`DancerKeysTest`、`PhonePageTest`（中英两份字典与卡片）。
- 新增 `DancerNamesTest.v1034RenamedDefaultsAndCustomStillWins`：新默认名双语定值 + 这两位舞者的自定义名仍压过新默认名。
- 总数 **322 → 323，全过、0 失败**。

### 产物与发布

- MySing-1.0.34.apk：28,721,108 字节，SHA-256 2143a382980962e3760e2798b76e4643d5d369ae0440d83360889e86f39d5b43，versionName 1.0.34 / versionCode 35，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.34 已发布，API 回读附件 size/digest 与本地逐字一致。
- 待用户真机验：电视端与手机点歌页上，古代女显示「陈秀颖」、古代女童显示「汉娜」（若此前在后台给这两位改过名，仍显示改过的名字，属预期）。

## v1.0.35 — 诊断行搬家 + 春丽邀请图补擦（2026-11-15）

### 起因

用户 23:01 发播放页截图问：控制条上方那排淡蓝小字是什么——是 v1.0.3 起常驻舞台的诊断行（状态/进度/playing/sink/bpm/伴舞），试机排障用的仪表盘。用户点选：「挪到后台页，舞台上不显示」。同版并入用户 23:06 报告的春丽邀请图残留缺陷（见下）。

### 诊断行搬家

- **舞台移除**：播放页删掉诊断行 Text（紧凑布局同样无）。舞台 Box 本就是 weight(1f)，腾出的高度自动归还舞台——歌词窗口按舞台实高自适应、控制条照旧贴底，不留空带。
- **错误横幅不动**：`PLAY_ERROR` 橙红横幅（ErrorDisplayPolicy）是故障信号、不是诊断行，留在舞台原处，口径未动。
- **后台页新增「诊断」区**：位置在后台页底部、版本号上方、署名附近；标题走新键 `TvKey.ADMIN_DIAG_TITLE`（诊断 / Diagnostics，对等测试守恒）。内容就是原来那份 `diagnosticsText`——拼装函数一字未改，字段与顺序全同（状态 · 位置/时长 · playing · sink · bpm · 舞者（含点歌人）· 伴舞阶段 · 伴奏降级提示）。
- **数值保活**：舞台的 33ms 轮询在 PlayerScreen 组合内，离开舞台即停；后台页自带 250ms 轻量 ticker 写同一份 `positionMs`/`durationMs` 状态，诊断区读 `diagTick` 订阅刷新——暂停/结束时静止的数值也会跟着状态翻。进后台不影响播放（Activity 级控制器，本就不停）。诊断数据的生产端无其他消费者因搬家断掉；`errorBanner` 仍由舞台 ticker 维护，返回舞台即恢复刷新。TvStrings 与 PlayerController 里指称诊断行位置的注释同步对齐新位置。

### 春丽邀请图补擦（用户 23:06 报告，打包前并入）

- 缺陷：`dancer_modern_woman_invite.webp` 她左侧腰边（画面右方、腰高处）悬空一只多余的手，手下方挂一小块灰色碎片——v1.0.30 清过这张图的其他碎片，这只手藏在她前臂下端、alpha 连通域与本体相连，漏网。
- 修法：外科式擦除，分三刀（多余手主体+灰片、手腕灰点、肘下两根手指尖+拇指尖残点），擦除范围全在缺陷包围盒内；盒外可见像素与原图逐像素一致（仅 24 个 alpha=0 像素的不可见 RGB 被编码器归一化）。画布 256×600、人物 bbox、基线（586）一律未动；无损 WebP 回存（87,588 → 83,734 字节）。擦后在海军蓝与洋红两种底上以全图+交界放大双重复核：无手、无灰片、无残痕，她本人两手（右臂伸出邀请、左手抚胸）完好。打包后又从 APK 内抽出该资源复验：缺陷区 alpha 全零。

### 二维码：本版不动

23:03 曾追加「二维码卡再收一版（字样上移、白底再减）」，用户 23:05 撤回（「先别做，我再想想」，且方向改为字样靠近底部，未拍板）。开发中短暂动过的 `CornerQrCard` 已逐字还原并与 v1.0.34 的 HEAD 版本比对一致——本版二维码卡与 v1.0.34 完全相同。

### 测试

- 新增 `StringTablesTest.v1035DiagSectionTitle`：钉死 ADMIN_DIAG_TITLE 双语定值（对等/非空由既有全表测试覆盖）。
- 总数 **323 → 324，全过、0 失败**。

### 产物与发布

- MySing-1.0.35.apk：28,717,243 字节，SHA-256 c282bcb7b0d708ee38890530b96a8cfa4ef9ce7fbd53d1bb09de65a2fa3a25c6，versionName 1.0.35 / versionCode 36，共享 keystore 签名（1336ee0a…befcc）。GitHub Release v1.0.35 已发布，API 回读附件 size/digest 与本地逐字一致。
- 待用户真机验：① 唱歌页控制条上方再无淡蓝小字、舞台更高；② 后台页底部「诊断」区数值是活的（进度在走、暂停会停）；③ 春丽做邀请动作时腰边干净、无多余的手；④ 二维码卡与上一版一模一样。

## v1.0.36 — 治抖（速度折减）+ 春丽 100 帧体检 + 雨一直下邀请修复 + 改名 Amy/秀颖 + 双语 logo + 二维码定版（2026-11-15）

APK：`MySing-1.0.36.apk`，29,005,620 字节，SHA-256 `a3a828334aecf35fcd5b5183c8079c9d9a93360365f9b445e80e607a90accd4c`，versionCode 37，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **333 个，0 失败**（v1.0.35 的 324 + 新增 9：速度曲线/回归钉点 7 + 标题行空格 2）。舞步链长不变（100 步，无删帧）。

### 一、治抖：动作幅度随速度收（用户 23:08「跳舞时，人抖动得很厉害」，23:12 方案确认）

主线已量测排除帧对齐（相邻帧脚底基线平均差 <1px）；病因在 DanceMotion 的幅度叠乘：弹跳+摇摆 ±4.5°+重拍脉冲（现代 accent 0.12）只有 <80 BPM 有 0.7 折减，快歌满格泵动。

- 新增纯函数 `DanceMotion.tempoFactor(bpm)`：≤110 BPM = 1.0；110→140 平滑降到 0.40；140→160 缓降到 0.35 封顶（160 以上恒 0.35）。与慢歌 0.7 折减叠乘（区间不重叠，不双重打折）。
- 作用面：五位真人的 accent/bounce/sway（humanPose、sumoPose 全量幅度项）；哪吒仅常规 DANCING 的 wobble/bounce/accent 受折减，**摔跤状态机（FALLING/DOWN/RISING）豁免**——那是定时落点笑点、不是逐拍幅度（取舍在此注明）；**瓦力全豁免**——步进机械感是本色（离散档位含 1.04 缩放一字未动，clap 亦然）。
- `PoseCycle.fadeFraction(bpm)`：常速 0.35 不变，120→150 渐增到 0.50（快歌切换化开）；blend 按淡化比例归一；瓦力硬切例外不变。
- 回归钉点：96 BPM 下 MODERN/ANCIENT/SUMO/哪吒采样值与改前逐值一致（hand-computed pins）；原 120 BPM 的 accent 精确值测试移到 96 BPM（120 已入折减区，钉点须落在 1.0 区，原断言数值不变）。

### 二、春丽 100 帧全分辨率体检（用户 23:13「春丽中间有断手断脚」）

方法：10 张品红拼图逐张看 + 全部可疑帧全分辨率双底（海军蓝/棋盘）复核 + alpha 连通域/低 alpha 残云/画布边缘三项程序普查。基线全系 582–587，无对齐问题。处置汇总：**通过 83 / 擦除 9 / 重生成 8 / 删帧 0**。

**擦除（外科擦除，人物/画布/基线不动）**
- _03：靴侧 171px 悬空深色碎片 + 残云。
- _34：画面左侧悬空断手（与主体仅以低 alpha 细线桥接，通用规则删不掉——区域外科擦除：x[90,235]×y[270,470] 内非本体像素全删，本体以 alpha>110 核心膨胀 3px 保护）+ 细线圈 + 身后黑色模糊团（邻格虚化残留，按亮度<85+连通再擦）+ 6.7k 残云。
- _39：腿周 16,330px 烤入式投影云（全系最重）整体清除（实删 20,279px 含边缘晕）。
- _50：左侧 x≈120–145 竖排虚线残条 + **第三只手**（右臂末端两只手：腰前握着的是本尊，其下张开的手掌为多余手——按行段切除 + 指条切除 + 晕边清理，共删约 750px）。
- _27/_28/_29/_54/_86：200–500px 级淡残云轻擦。

**重生成替换（同姿势意图，设计零漂移：金色长发/白短上衣+白短裙/黑短靴；rembg u2net 抠图→主连通域→alpha 紧实→按原帧高/邻帧均高合成 455×600、基线 586、中心对齐原帧；全部一次过 QA，与舞步邻帧并排复核）**
- _13：短发变体+黑方块残影 → 双手贴脸颊、交叉步（原高 491）。
- _14：短发变体+黑方块 → 宽站位、右臂指左上/左臂指右下（482）。
- _23：抬腿至膝盖截断+左臂被画布切断+背后黑块 → 双腿双臂完整、臂侧展（邻帧均高 481）。
- _30：发际线撕裂+右手熔团+比例拉长 → 脸完整、左臂高举、右手贴耳（邻帧均高 497）。
- _44：爆炸 puff 发型 → 星跳、发丝分缕飞扬（461）。
- _49：右侧漂浮碎片+后靴熔化 → 手抚头顶、后踢腿靴形完整（574）。
- _53：大摆裙串模 → 贴身短裙、三分侧身、双拳胸前（494）。
- _60：举手被画布顶边切掉 30px → 手臂微弯、手完整入画、仰脸（575）。

**通过（83 帧，含主线拼图疑点复核不成立的）**：_19（全分辨率下为长发同款，拼图误判）、_46（指尖擦顶边 6px、手完整）、_11/_37（品红透过半透明发缘的伪影）、_20（脸侧黑影为拼图伪影）均无问题；其余 78 帧拼图+普查双清。手机动画卡已按现行采样重采样（采样位 tour[30] 正是被替换的 _30；六卡全部重出，modern 139,912 字节）。

### 三、《雨一直下》没邀请修复（用户 23:31 报告）

根因（主线查实）：该歌前奏 40 秒（首唱 00:40.44），但其 LRC 标题行是「张宇 -雨一直下」——横杠前有空格、后无空格；`isTitleLine` 用 `split(" - ")`（两侧都要空格）切不开 → 标题行被当成 0.60 秒的第一句歌词 → 判无前奏。

- 修法：不再切分。横杠两侧空格归一化（`\s*-\s*` → `-`）后**整行**与本歌歌手-歌名对（两种顺序）比对——严格性不变（必须整行恰好相等，真歌词不可能误中），且不误伤名字自带横杠者（Toy-Box、《Candy-》尾横杠歌名——切分法会把它们切成三段误杀，整行比对不会）。
- 回归夹具：该歌真实开头四行（标题 0.60「张宇 -雨一直下」/ 词：十一郎 1.60 / 曲：张宇 2.60 / 首唱 40.44），全程 INVITING 至 40.44；另加空格变体与 Toy-Box/Candy- 钉点。
- 全库离线扫描（复刻 LrcParser+splitArtistTitle+isCreditLine+firstSungStartMs+hasIntro，对 karaoke-convert/output 的 1,092 个增强 LRC 各跑旧/新逻辑）：**旧逻辑判有前奏 961 首 → 新逻辑 966 首（+5、−0）**。新增 5 首：周杰伦- 夜曲、小虎队- 红蜻蜓、高凌风- 牽不到你的手、李玟- Di Da Di、Sober-P!nk（均为「歌手- 歌名/歌手 -歌名」式间距）。注：《雨一直下》在 output 语料里的标题行拼接为「张宇 - 雨一直下」（两侧空格，旧逻辑已认），用户硬盘上那份是「张宇 -雨一直下」——新逻辑两种都认，夹具按硬盘形状钉死。

### 四、改名（用户 23:36）

照 v1.0.34 口径：现代女默认名 春丽 → **Amy**（中英同字，EN 原 Chun-Li 亦改 Amy）；古代女默认名 陈秀颖 → **秀颖**（EN: Chen Xiuying → Xiuying）。DancerKeys LABELS/LABELS_EN、ui/Dancers.kt displayName、PhonePage 卡片与中英 JS 字典、DancerNamesTest/DancerKeysTest/PhonePageTest/StringTablesTest 钉点全量同步；app/src 内旧名零残留；用户自定义名优先口径不变；历史注释与旧章节不改。

### 五、双语 logo 替换横幅文字标题（用户 23:47）

- 素材：`brand/logo-zh-source.jpg`（一起来唱卡拉OK 彩字标）、`brand/logo-en-source.jpg`（MySing / Your Personal Karaoke），均 1408×768 JPG、棋盘格是烤进像素的假透明底。
- 抠图：rembg（u2net，逐张单进程——批量跑会被 1GB 级模型下载+推理挤爆内存 OOM，逐张无事）。中文标一次干净（单连通域、海军蓝合成目检无残格无白边）。英文标副标题带被 rembg 啃伤（Y 缺角+整块棋盘格被当阴影保留）：定向修复——y≥458 起渐入区内，alpha = max(饱和度字母遮罩（金字 sat 高、棋盘格≈0），高斯模糊后的 rembg 阴影×0.55)，且非字母像素 RGB 同步换模糊版（纹理在 RGB 里、只糊 alpha 不够）——副标题完整可读、格纹全消、阴影成柔边。
- 成品：裁到内容包围盒+6px 发光余量 → `brand_logo_zh.webp`（859×449）/ `brand_logo_en.webp`（740×400），drawable-nodpi，q95。
- 代码：唱歌页横幅行齿轮后原 BRAND_TITLE 文字 Text 换成 Image——中文界面中文标、英文界面英文标（随 uiLang），高 = 横幅行 36/46dp，宽按图比例（aspectRatio 取 painter 固有尺寸），chips 与布局次序不动。
- 死键清理：`TvKey.BRAND_TITLE` 已无引用，自枚举与中英双表删除，StringTablesTest 中两条钉点断言随之移除（理由注在原测试处）；对等测试照常守恒（键的集合两边同步删）。

### 六、二维码卡定版（用户 23:51 拍板，取代此前一切二维码讨论）

`CornerQrCard`（全页共用同一组件）：只收白边——内边距 4/3dp → **2dp**，QR 76/60dp → **56/44dp**，整卡明显变小；「扫码点歌」保持在码**下方**、贴卡底（2dp 底边距）。码本身逐像素不动：`Qr.encode` 位图与 4 模块静区一字未改、不嵌任何图，扫码可靠性不变。横幅让位 padding（90/114）与队列面板 topPad（70/88）对新卡尺寸仍有富余，未动（收紧纯属 cosmetic 且有重叠风险）。

### 交付

GitHub Release v1.0.36（API 回读校验大小与 digest）；GOAL.md 进度同步。设备侧待用户验：快歌抖动是否消、春丽新帧顺滑、《雨一直下》邀请、Amy/秀颖 新名、双语 logo 显示、二维码新卡扫码。

## v1.0.37 — 其余五位伴舞全帧体检（2026-11-16）

APK：`MySing-1.0.37.apk`，28,940,709 字节，SHA-256 `5464ca4d9597d37cdfe68769bb8141295c2a7add03acaf608a5c2e4cb6cdada2`，versionCode 38，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **333 个，0 失败**（本版零代码改动、仅版本号与帧资源；链长断言无调整——零删帧）。GitHub Release v1.0.37 已发布并 API 回读校验（大小与 digest 与本地一致）。

口径与 v1.0.36 Amy 体检完全一致：程序普查（alpha 连通域/低 alpha 云/画布边缘/基线/身高/中心）+ 品红联系表全看 + 可疑帧全分辨率双底（海军蓝舞台底/白底）复核。修复阶梯：外科擦除 → 同姿势重生成（逐张单进程 rembg、合成量测对齐才入库） → 删帧（本版未用）。基线普查结论：五人基线 585–587，无对齐问题，病灶全在帧内容。

### 汇总计数（通过 = 未列入问题清单的帧）

| 伴舞 | 帧数 | 擦除修复 | 重生成 | 删帧 |
|---|---|---|---|---|
| 秀颖 ancient_woman | 100 | _06/_21/_88 圆盘、_25 团块、_52 前片 + 全组碎片/云雾普擦 | 8 | 0 |
| 姿三四郎 sumo | 100 | _32 多余手、_56 断臂残桩、_04/_22 碎片、_43 盐雾云 | 3 | 0 |
| 汉娜 ancient_girl | 100 | _27/_28 云、_23/_47/_100 等碎片（仅擦除） | 0 | 0 |
| 哪吒 baby | 100 | _03/_04 暗块、_36 团块、_28/_08 云、_33 微点 | 29 | 0 |
| 瓦力 robot | 60 | 全组云雾普擦、_16 邻人切片+蓝团、_22 地面反光 | 7 | 0 |

另：六人静态图/邀请图 10 张快速复核，9 张通过；baby_invite 右下角有一小块机器人鞋尖残留（253 px）已擦除。

### 秀颖（ancient_woman）

- 擦除：_06/_21/_88 头侧海军蓝圆盘（纱带环内烤死盘）、_25 裙左团块、_52 身前竖片——navy 定向擦除后双底干净、纱带恢复半透明。全组另过一遍通用碎片/云雾擦除。
- 重生成 8 帧：_04 袖片脱离悬空、_07 袖片碎块——重生成后袖子成整片；_37 额前白斑+身后黑片与人物熔合不可擦，整帧重生成（发髻额发完整、黑片消失）；_01/_48/_80/_86/_87 弧带环内中性黑烟核——两轮擦除后仍留"光晕盘"，升级重生成。
- 弧带四帧（48/80/86/87）救援记：子代理三轮重生成全败——u2net 会把任何被带环围住的环内填成海军蓝盘、合成的最大连通域规则又会删掉不沾身的飘带。主线改用**背景色清除**救回：取生成图角落底色（6,16,43 一带），抠图内蓝调主导（B−R>10）且色距 <34 的像素即盘体，擦除；发丝暖色与纱上粉调不受影响。48 一次成功；86 用子代理第 2 次生成（近完美、仅肩后一团灰雾——海军蓝底上呈软阴影级，另擦掉步摇旁 1,010 px 脱落碎片）入库；87 双灰点定点擦除后入库；80 的生成图盘体是海军蓝+灰烟渐变、颜色清除不净，改用同批无环构图生成图（双臂舒展、飘带横流），按高度合成、带尖两侧各自然出画 10 px（与原图集袖带出画帧同例），双底干净。_48 合成后发髻内有几粒海军蓝小点（发内、舞台不可见），记录在案不处理。

### 姿三四郎（sumo，严守定版：灰腰带+白色前襟流苏）

- 擦除：_32 右肩后多一只无主手——五轮量测盒切除（最后一刀 x68–84/y300–316），背缘留亚像素台阶、显示尺寸不可见；_56 举臂后多一段平切断臂残桩，两轮盒切除干净；_04/_22 通用碎片、_43 撒盐盐雾云修剪。_03 的 sagari 流苏尖发暗蓝是原定版组内方差，不动。
- 重生成 3 帧：_49 双臂手部残缺锯齿、_50 右手被画布切、_57 双臂只剩肩桩——均按原姿势意图重生成，手脚完整、腰带定版一致。_49 子代理说明与参考图冲突（说明写双足下蹲、参考是抬腿），以参考图为准和解（抬腿+双臂完整）；_50 同理以参考图（双臂平展）为准，说明版（撒盐）生成图留档未用。

### 汉娜（ancient_girl）

- 仅擦除，无重生成：_27 云 3,729 px、_28 云 2,539 px、_23 碎片 118、_47 碎片 117+云 222、_100 碎片 71，及一批小碎片/云。_21/_36 的"紫眼"是品红联系表合成伪影、原图正常，通过。_47 左髻旁留一小缕灰丝（两轮门控擦除均 0 命中，舞台不可见），通过附注。

### 哪吒（baby）

- 擦除：_03 臂后海军蓝矩形块 6,787 px、_04 暗条 4,593 px、_36 举手后团块 377+11 px（指尖残留深色为手部自身烤入阴影，通过）、_28 云 1,729、_08 云 580、_33 微点。
- 重生成 29 帧（本版最大项）：_41–62、_68、_69、_70、_72、_77、_80、_81 原是**粉蝴蝶结女孩版**——与哪吒男孩定版（白尿布、无头饰）串模，系 v1.0.33 补间帧生成时漂移。整批按同姿势逐帧重生成为男孩版，合成几何逐帧核对（高=原高、基线相等、中心 ±2），双底 QA 全过；主线再逐帧独立复验（29/29 男孩、无蝴蝶结、姿势与原图 1:1）。

### 瓦力（robot）

- 擦除：全组云雾普擦（最大 _18 22,608、_23 6,194）。_16 右侧混入邻座机器人切片+49,742 px 海军蓝团：通用+navy（头部椭圆保护）→ 逐行间隙切 14,321 → 孤岛盒 → x≥352 扫尾，最终残留 2 px、脸屏完好。_22 脚下地面反光三轮清除（基线 586→529 是反光垫高消除、机器人本体未动）。事故记录：navy 规则**不可重入**——对 _16 二次运行啃掉脸屏玻璃（屏色同为蓝黑），已恢复并以头部保护单次重做；连通域规则对机器人危险（头颈连接细，扩展规则曾把 _47/_38 整个头当碎片），已回滚、只用一档规则。_38 通过附注（白底脸屏有轻微洗色、海军蓝底干净）；_47/_38/_16/_18 普查里的"远碎片"经定位都是自身头/手被半透明关节隔开的标记伪影，通过。
- 重生成 7 帧：_11 右腿原已是半透明幽灵腿（实心 2,643+半透明 ~5,500 px）、_13 雾头（头半透明、LED 眼糊）、_19 浓雾熔合+手臂靠雾桥悬空、_24 前臂脱离+木桩腿+鞋只剩影、_25 双腿下段褪成黑透、_46 缺手+悬空手+幽灵头——均按原姿势重生成，头实心、LED 脸清、天线在位、腿脚实心。_24 入库版为镜像姿势（举臂侧与参考相反、正向重生成被工具拒绝），舞步内读感自然，接受并注记；_13/_46 天线为定版小尖（非发光球），与设计图一致。
- _32 收尾新发现（终普查）：站立腿膝下整段泡在海军蓝块里——采样证实该区无任何亮像素、鞋从未可见，属断腿帧而非残影；先误用 navy 擦除（啃掉阴影里的鞋面）后改判重生成：按原姿势生成、站立腿实心到鞋完整，再定点擦除两腿间 2,814 px 蓝污；合成锚回 git 原始几何（高 450、基线 559——原图本就因蓝团垫高浮起，新帧与原行为一致）。

### 手机动画卡与交付

- `dancers/make_anim.py` 重采样：ancient/sumo/ancient_girl/baby/robot 五卡更新（采样位含被换帧），modern（Amy）未动不变。
- 终普查：460 帧基线无异常（robot _22 529、_32 559 为上述已裁定项），无 >400 px 真远碎片残留。
- 设备侧待用户验：五人新帧实机顺滑度（尤其哪吒 29 帧男孩版衔接、秀颖弧带四帧、瓦力 _32）。

## v1.0.38 — 伴奏接通 + 扫光单 layout + 绿色加深 + sumo_32 残点（2026-11-16）

APK：`MySing-1.0.38.apk`，29,008,000 字节，SHA-256 `0585fff85d8c100751769222125997eba0b85583155c5c300df140cb413fb1ba`，versionCode 39，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **343 个，0 失败**（333 基线 +10：处理器格式/算法 7 + 双通道挂载结构钉点 2 + SweepLine 单 layout 结构钉点 1；SweepColorTest 色值断言按新绿更新）。GitHub Release v1.0.38 已发布并 API 回读校验。

### 一、伴奏接通（用户 2026-11-16 00:23「原唱和伴奏没什么区别」）

主线已实测算法本身够强（对歌库原文件跑 (L−R)/2，残余能量仅 18–37%），病在处理器没接通，本版查实是**三层**：

1. **格式门**：`VocalCancelAudioProcessor.onConfigure` 只认 PCM_16BIT + 恰双声道，其余一律 NOT_SET 被 sink 旁路。改：接受 PCM_16BIT 与 PCM_FLOAT、声道 ≥2（前两声道消音、其余声道原样直通），内部按格式分发 16 位/浮点两条消音路径，输出格式与输入一致；单声道与非 PCM 编码仍 NOT_SET。原 try/catch 全直通保命逻辑保留，(L−R)/2 算法本体不动。
2. **备用通道没挂**：plain-retry 播放器用裸 `ExoPlayer.Builder(context)` 构建，根本没有处理器，走备用的歌切伴奏等于没切。改：全工程只剩一个构建点，首发与重试都经 `KaraokeRenderersFactory` 挂**同一个** vocalProcessor 实例（结构钉点测试守：Builder 调用点恰 1 处、无裸构建）。`FallbackStateMachine` 的路径自此只表示第几次尝试，不再是两条不同的音频链，类注释已对齐。
3. **空包缺陷（本版新查实）**：旧消音路径用绝对寻址 putShort 写出缓冲，从不推进 position，`flip()` 后发布的是**空缓冲**——自定义通道上开伴奏等于没声音数据（新测试先以此形态失败、修复后转绿，为证）。改：写完显式 `output.position(size)` 再 flip。

**降级口径改实**：`accompanimentUnavailable` 不再是按路径写的存储字段，改为实时计算 `wantAccompaniment && vocalProcessor.configured && !vocalProcessor.isActive()`——只有处理器被 sink 配置过、且确实拒绝了本歌格式（单声道/非 PCM）才算降级；未配置（未知）不报、路径不再背锅。诊断行文案同步改实：「伴奏不可用（已降级普通播放）」→「伴奏不可用（此歌格式不支持消音）」。`setAccompaniment` 不再按路径分叉，两条路径都直接置 `vocalProcessor.enabled`。

### 二、扫光双层错位（用户 2026-11-16 00:29 截图：长英文行金/青横向错位约半字）

`SweepLine` 由「两个独立 Text 叠画」改为**单 Text 单 layout**：底层 Text 的 `onTextLayout` 捕获其自身 `TextLayoutResult`，扫光层在同一 draw 作用域内 `drawContent()` 画完青底后，save + clipRect 到原有 boundary（SweepProgress charWindows + getHorizontalPosition 口径不变），再以 `result.multiParagraph.paint(canvas, color = sweepColor)` 用**同一份 layout** 重绘——两层字形坐标由构造保证逐像素一致。注：本工程 Compose 1.10 的工件里没有 `DrawScope.drawText` 扩展（编译实证），故用 MultiParagraph.paint 的颜色覆盖重载，等价且同源。普通 LRC（无逐字标签）路径未动。结构钉点测试守：SweepLine 内恰一个 Text、含 onTextLayout 与 multiParagraph.paint 与 clipRect。

### 三、扫光绿色加深（用户 2026-11-16 00:33 看色卡亲选）

`SweepColor.LIGHT_GREEN`：#A9E5A1 → **#34C759**。金 #FFD54F、浅粉 #FFB7C5 不动；只改 MySing，MyLRC 未动。枚举名与后台 chip 文案（浅绿/Light green）沿用旧称，未在本版任务范围内。

### 四、姿三四郎 _32 左缘残点

主线复核 v1.0.37 成品时发现 `dancer_sumo_32.webp` 左缘中部残点：连通域普查共 3 个组件，本体之外为 67 px 碎片（x84–101/y308–316，原 _32 多余手五轮盒切后的边角）+ 3 px 微点（x124–125/y266–267）。仅对这两处独立组件清零 alpha（共 70 px），lossless + exact 保存——与原图逐像素比对：改动恰 70 px、人物本体 0 像素变化、画布 468×600 与基线不动；APK 内抽出该资源复验与源文件逐像素一致。

- 设备侧待用户验：随便点一首走过的歌切原唱/伴奏应立竿见影（消音后残余约两三成）；长英文行扫光不再双行；绿色深度是否合眼（后台扫光颜色选绿）。

## v1.0.39 — sumo_32 肩后指尖残桩外科擦除（2026-11-16）

APK：`MySing-1.0.39.apk`，29,007,390 字节，SHA-256 `85eb284d999bd2c64f0572fab10ee832d2e2c443a1f4ca3a2970fa3f6fb64c69`，versionCode 40，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **343 个，0 失败**（零代码改动，与 v1.0.38 基线一致）。GitHub Release v1.0.39 已发布并 API 回读校验。

主线全分辨率复查发现 `dancer_sumo_32.webp` 姿三四郎右肩后方（画面左侧、肩线外）仍有两截指尖状残桩——正是 v1.0.37 报告的那只「肩后多余手」的指尖部分。它与人物本体 alpha 相连（不是独立组件），所以 v1.0.37 的盒切与 v1.0.38 的独立连通域清零都漏了它。查实：指尖带指甲（y≈297、x≈113–117 有深褐色指甲带），在轮廓上形成两级台阶（y288–291 凸出至 x=124、y292–307 凸出至 x=110–112）。

修法：以平滑肩背弧线为准绳切除——弧线锚点取自轮廓干净段（上方 y≤287 的 x=133、下方 y≥308 的手臂轮廓），PCHIP 单调插值穿过污染带，1px AA 斜坡收边；指根处的暗影/海军蓝缘饰仅在 y300–307 按色规则清除。母版 PNG 与出货 WebP 同步修（母版还顺带补清 v1.0.38 只清了 WebP 的 67 px 碎片与微点——母版此前仍带着）。

**轮廓外 alpha 普查**（手术带 y288–312，以上述弧线为轮廓、留 1px AA 余量）：修复前 **120 px**（严格口径 142）→ 修复后 **0**（严格口径亦 0）。全图 alpha 连通域 = 1（仅人物本体）；画布 468×600 与 alpha 包围盒（x 84–388、y 141–586）不变；轮廓变动仅限 y288–307，y308 以下与原图逐行一致。修复后海军蓝/洋红双底全图 + 肩部放大目检：肩线平滑、无指尖、无指甲、无台阶。APK 内抽出该资源（res/dd.webp）复验，与源文件逐像素一致。

- 设备侧待用户验：姿三四郎跳到 _32 帧时右肩后方干净（暂停在该帧最易看）。

## v1.0.40 — 紧急修复：恢复真裸重试 + 处理器输出恒 16-bit + 诊断行补错误码（2026-11-16）

APK：`MySing-1.0.40.apk`，29,007,383 字节，SHA-256 `a5811a7f0fce49ba7095107fe907858cacc70ec77daa64c8ad2afc706120ed39`，versionCode 41，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **345 个，0 失败**（343 基线 +2 新增 float 转换测试；结构钉点 2 个与处理器 2 个为口径改写、计数不变，逐条注明于各测试类注释）。

### 回归归因（用户真机全灭）

用户装 v1.0.39 后报告「伴舞不动了，也不播放歌，泡泡会动」，按要求拍回后台诊断行，主线亲读坐实：**状态 IDLE · 0/276s · playing=false · sink=plain-failed**——两次构建尝试全失败、FallbackStateMachine 落 FAILED。证据链：v1.0.36 及以前一直能播，但用户历次截图（Youngblood、《心太软》等）sink 都是 **plain-retry**——即自定义 sink 构建（KaraokeRenderersFactory + DefaultAudioSink.Builder + 处理器链）在他设备上长期首试失败、一直靠真正裸构建的 plain-retry 兜底；v1.0.38 为让伴奏在备用通道也生效，把重试改成同一工厂构建（「全工程只剩一个构建点」），救生艇被拆、两试同死。这是 .38 的回归，与 .39 的素材改动无关（.39 零代码）。

### 修法一：恢复真裸重试（双路语义回到 v1.0.37 及以前的形态）

`PlayerController.buildPlayer(customSink: Boolean)` 恢复分发：尝试 1 = `KaraokeRenderersFactory` 带处理器工厂构建；尝试 2 = stock `DefaultRenderersFactory` 裸构建（无自定义 sink、无处理器）。`load()` 新歌回尝试 1、`handleError()` 首错重建为裸构建重试；`FallbackStateMachine` 类注释与 sinkLabel 口径随之恢复（custom / plain-retry / plain-failed，状态机逻辑本身未变）。`accompanimentUnavailable` 改为并集判定：`wantAccompaniment && (当前路径 != CUSTOM || (processor.configured && !processor.isActive()))`——裸通道上伴奏不可用重新算真降级，与处理器格式拒绝的实测口径并列；诊断行文案按原因分实话：custom 路径显示「此歌格式不支持消音」、其余路径显示「当前通道无消音」。`setAccompaniment` 在裸通道上不再置处理器 enabled（链里根本没有它），`isAccompaniment` 恢复「CUSTOM 路径且处理器开」的口径。结构钉点测试改写：钉死「尝试 1 工厂带处理器 + 尝试 2 裸构建」两条各自的形态、Builder 调用点恰 2 处、.38 的单构建措辞不得回潮。

### 修法二：处理器输出恒定 16-bit

`VocalCancelAudioProcessor.onConfigure` 声明的输出格式恒为 PCM_16BIT（采样率/声道随输入）；float 输入在 `queueInput` 内先逐样本转 16-bit（×32767 四舍五入、钳位），再走与原生 16-bit 完全相同的短域 (L−R)/2（多声道仍只消前两声道、其余转换直通）；disabled 时 float 也只转换不消音——本链永远不给 sink 喂 float（.38 前 float 是 NOT_SET 旁路、由 sink 自家链转格式，那条路是通的；.38 让 float 穿过本链是新增变量，.40 把它从链里彻底拿掉）。16-bit 路径逐字未动；保命 try/catch 保留（float 路径异常时发同格式静音，不发错格式字节）。处理器测试改写/新增：float configure 声明 16-bit 输出、disabled float 逐样本转换定值、enabled float 转换后消音定值（与 16-bit 路径同值）、float 多声道额外声道只转换不消音。

### 修法三：诊断行补错误码

`diagnosticsText` 在 `controller.lastError` 非空时把其全文附在行尾（「 · 错误 ERROR_CODE_…: …」，该字段本身以错误码名打头）——下次任何播放故障，一张后台截图就能定性，不必再靠 sink 标签推断。

- 设备侧待用户验：装上随便点歌应立即能播（大概率仍走 plain-retry 兜底，与 .36 时代一致）；若某首歌能走通 custom，伴奏对 float 歌同样生效；万一再遇不播，后台诊断行尾部的错误码就是定性依据，拍回来即可。

## v1.0.41 — 伴舞「左右闪」修复：帧骨盆锚点对齐 + 运行时减摇（2026-11-16）

APK：`MySing-1.0.41.apk`，30,336,531 字节，SHA-256 `62df376be7ff56a8d1dc01046ee93e9e89398657a47cbd3aeaf96bca167525fc`，versionCode 42，共享 keystore 证书 SHA-256 `1336ee0a…`（apksigner 验过）。单元测试 **345 个，0 失败**（与 v1.0.40 基线同数：本版无新增测试，DanceMotion 钉值 2 处按新摇摆幅度改写、逐条注明）。体积比 .40 涨 1.3 MB：524 张重编码帧按 q95 保存（原帧压缩更狠），换帧面干净。APK 内抽 `dancer_modern_woman_65` 复验，与对齐后源文件逐字节一致；aapt2 资源表伴舞条目 572 不变。

### 一、诊断：包围盒很稳，身体质量每步在跳

用户报告：「伴舞一直在左右闪，不是动作连贯问题，看得眼睛痛」（.40 播放已恢复，本版只治此视觉问题）。离线沿 `DancerPoseOrder` 真实舞步链复测（`dancers/measure_centroid.py`，与主线同口径：alpha 包围盒、躯干带 = bbox 纵向 25–60%、骨盆带 = 55–75%，质心以画布宽 % 计，相邻步 Δ）——主线数字复现无误：图的包围盒中心相邻 Δ 均值仅 0.11–0.80%（瓦力 1.98%），但躯干带质心 Δ 均值 modern **4.94%**（99 步中 68 步 >2%，最大 19.3%）、ancient 3.09%、sumo 2.44%、ancient_girl 2.16%、baby 1.94%、robot 3.12%。一拍一帧地播，眼睛看到的不是姿势在变，而是整个人每拍横向瞬移；运行时 DanceMotion 又在每帧之上叠整身摇摆旋转，雪上加霜。两手都治：帧锚点对齐（治瞬移）+ 运行时减摇（治叠加摇摆）。

### 二、锚点定义：骨盆带质心相对画布中心的偏移

舞台的渲染几何决定锚点必须相对画布中心量：播放页把每帧按固定布局高度绘制（宽度随帧自身宽高比），置于 `BottomCenter` 对齐的伴舞区内（MainActivity 舞台区代码）；全部画布同高 600 px、缩放一致，故源图点 x 的屏幕横坐标 = 区中心 + (x − W/2) × 同一缩放。画布现状：原始 60 帧每人共用一张画布（modern 455 / sumo 468 / ancient_girl 300 / baby 225；ancient 与 robot 各有两张：307+417、421+438），而 v1.0.33 的 40 张补间帧各自保留了生成时的独立画布宽——只有相对中心的锚点对混宽帧集是诚实的（同宽帧上它与绝对坐标对齐等价）。锚点取骨盆带（bbox 纵向 55–75%）alpha 质心——身体的真枢轴。

### 三、帧对齐执行（`dancers/align_frames_v1041.py`）

每帧横向平移 `shift = round((全链锚点中位数 − 本帧锚点) × 0.65)`：只补 65% 偏差，留 35% 天然 lateral 律动，不把舞跳成站桩。平移被本帧透明边距钳制——不裁身体、不改画布；被钳帧逐帧记录在 `dancers/alignment_v1041_log.json`：modern 25 / ancient 19 / sumo 16 / ancient_girl 5 / baby 3 / robot 0（多为补间帧：生成时人物贴边、边距仅 12 px，想要的位移最大 +88 px 只能落 +12 px，这部分残余偏差是边距物理上限，不硬来）。实际平移帧 524/560，其余 36 帧位移为 0、文件逐字节不动；重编码 WebP q95/method4。哪吒先查实：跌倒是运行时变换（`DanceMotion.BabyFall` 旋转 84°+下沉，全在运行时），他的 100 帧全是直立姿势（包围盒无一宽扁），照常对齐、无豁免；瓦力只对齐位置，步进硬切本色不动。静止图/邀请图是单张无步进，不动。同一整数位移同步镜像进源文件：`dancers/poses/frames_png` 母版 333 张 + `dancers/inbetween/` 补间源 191 张（映射按 integrate_v1033.py 的分配逻辑重建并与 inbetween_orders.json 交叉校验），画布不符者跳过并记录——本轮 0 跳过，未来重整合不会把闪烁带回来。

### 四、复测（双口径，如实并列）

口径一（主线原法，质心以各帧自身画布宽 % 计，相邻步 Δ 均值）：

| 舞者 | 躯干带 before → after | 骨盆带 before → after |
|---|---|---|
| modern (Amy) | 4.94% → 3.87% | 6.41% → 3.81% |
| ancient （秀颖） | 3.09% → 3.40% | 4.25% → 2.20% |
| sumo （姿三四郎） | 2.44% → 2.04% | 3.13% → 2.03% |
| ancient_girl （汉娜） | 2.16% → 2.27% | 3.30% → 1.33% |
| baby （哪吒） | 1.94% → 1.86% | 2.93% → 1.18% |
| robot （瓦力） | 3.12% → 3.16% | 3.89% → 1.36% |

口径二（屏幕真值：质心相对画布中心偏移，源图 px，相邻步 Δ 均值；`dancers/measure_anchor.py`）：骨盆锚点 modern 21.36 → 12.23 px（−43%）、ancient 15.29 → 7.72（−50%）、sumo 11.02 → 6.70（−39%）、ancient_girl 8.12 → 3.20（−61%）、baby 6.16 → 2.41（−61%）、robot 16.92 → 5.92（−65%——零钳制时恰好等于 0.65 补偿的理论残余 0.35 倍，反证管线无误）；modern 骨盆最大单步 101.9 → 69.9 px（残余主要来自被边距钳住的补间帧）。躯干锚点 Δ 降幅小（modern 17.93 → 15.13 px，ancient/ancient_girl/robot 近平）：躯干带相对骨盆的再分布是姿势本身（抬臂、侧倾的质量转移），骨盆钉住后它读作绕定点的舞动而非整人瞬移——「闪」的成分是整体平移，已由骨盆/包围盒锚点口径的大幅下降坐实消除；口径一的 % 数对窄画布补间帧有归一化放大，仅作对照存档，不作结论依据。

### 五、运行时减摇（DanceMotion，人类摇摆 ×≈0.45）

整身摇摆旋转幅度统一下调，与 v1.0.36 tempoFactor 的叠乘关系不变：modern 2.0° → **0.9°**、ancient 3.5° → **1.6°**、sumo 摇摆（原 ±4.5°，全场最大）4.5° → **2.0°**、哪吒跳舞相位 wobble 5.0/1.8 → 2.25/0.8。弹跳与节拍重音不是摇摆，不动。豁免口径不变：哪吒跌倒三相位（定时笑点）、瓦力步进 ±5°（本色）。钉值测试随改：96 BPM 定点旋转钉 modern −1.175571 → −0.529007、ancient −2.057248 → −0.940456、sumo −4.279793 → −1.902113（弹跳/缩放/下沉钉值不变，继续守 taper 平坦区）；sumo 范围测试上限 4.51 → 2.01、双向越界阈值 ±3 → ±1.5。

### 六、手机选人动画卡片

六张卡全部按对齐后的帧重采样（`dancers/make_anim.py` 原口径：100 步链取 0/10/…/90、瓦力 60 步链取 0/6/…/54，280 ms/帧、高 420、q80）：modern 138,976 / ancient 152,406 / sumo 120,568 / ancient_girl 86,466 / baby 57,030 / robot 168,034 B，合计 723,480 B。

### 七、过程注记

首轮对齐脚本的文件枚举用了字典序排序（_100 排到 _10 与 _11 之间），帧号标注与源文件镜像全盘错位——是源画布交叉校验的大面积不符（60+ 处 skip）当场暴露了它；出货帧本身因「每帧按自身骨盆计算位移」未受影响，但仍按纪律全量 git 还原后，以数字序 + 中心相对锚点重跑（顺带修正了绝对坐标锚点对混宽画布不诚实的问题），本章数字全部出自重跑结果。主线目检：modern 舞步链连续 10 步按渲染口径（等高、居中、底对齐）拼条对比——对齐后躯干在格内横向位置明显收稳，帧面无裁切、无残损。

- 设备侧待用户验：随便放一首舞曲看 Amy 跳舞——整个人应不再每拍左右瞬移，剩下的应只是姿势本身的舞动；快歌（~140 BPM）再看一眼减摇+taper 叠加后的观感；哪吒跌倒、瓦力步进应与以前一样。

## v1.0.42 — 肢体完整性专项全检（第二轮）+ 渲染层左右闪真因 + 二维码自愈（2026-11-16）

本版三件事：① 用户在 .38–.40 上又看到断手断脚残留（未指名），对六位伴舞做第二轮肢体专项复查；② 用户装 .41 后回报左右闪仍在（「不是自然律动，是整个身体左右闪」），主线定位到渲染层真因；③ .41 截图右上角二维码消失，查清是 IP 取值时机问题，一并修掉。

### 一、渲染层左右闪真因（与 .41 图内对齐互补，本条是「整个身体闪」的主因）

.41 的骨盆锚点对齐治的是**图内**锚点漂移；本条在**渲染层**：`DancerFrame` 的 Image 只设 `.height(heightDp)`，宽度随每帧自身宽高比（实测 0.195–0.78，差 4 倍）；`DancerImage` 内层 Box 未设 contentAlignment（默认 TopStart）、尺寸随最大子项。交叉淡化期间（每拍 35–50%）前后两帧左对齐叠放，窄帧相对其单独显示位置横移 (宽帧−窄帧)/2，淡化结束又弹回正中——整人每拍横跳+回弹，与图内容无关，图内对齐治不到。修法：内层 Box 改**固定宽** = heightDp × `SpotlightGeom.FRAME_ASPECT`（468/600，舞台几何其余部分早已假设的名义宽），contentAlignment = BottomCenter，两层 DancerFrame 在同一固定矩形内居中（最宽帧宽高比恰 = 0.78，不溢出）。连带：光池尺寸从此恒定；v1.0.32 的 stepOverflow 守卫名实相符；掌声排/仪式排的 Row 不再因帧宽变化互相推挤。结构测试 `DancerImageStructureTest`（2 例）钉死固定宽来源 + BottomCenter + 两层同矩形。

### 二、二维码消失修复（轮询自愈 + 占位卡）

病灶：`CornerQrCard` 的显示条件是 `remember(app.state.version) { Qr.lanIpAddress() }` 非 null——IP 只在队列 version 变化时重取，纯随机播放期长时间不刷新；开 App 时 WiFi 未连上、或中途断过再连上，二维码就一直缺席（IP 变更还会打出过期地址）。修法：根布局改用 `rememberLanIpAddress()`——首值同步取，其后 LaunchedEffect 每 5 秒慢轮询（`LanIpPoll.INTERVAL_MS`），纯状态机 `LanIpTracker` 只在值变化时发布新状态、位图只在 (ip, port) 变化时重组（`Qr.lanIpAddress()` 本身不动，不新开线程）。无地址时在原角位显示同几何的弱化占位卡 `CornerQrPlaceholderCard`（文案 TvKey.CORNER_QR_WAITING：ZH「等待网络…」/ EN "Waiting for network…"，下方保留扫码说明；无点击/无焦点）；三处二维码预留边距（Admin/Browse/Player）改无条件恒定，地址恢复时不发生布局跳动。测试 `LanIpTrackerTest` 8 例钉死占位↔二维码切换/同值不报变化/换址替换/掉线回占位/5 秒节拍。

### 三、肢体完整性专项全检（第二轮）

范围：六位伴舞全部舞步帧（人类各 100、瓦力 60，含补间帧与历次重画帧）+ 各自静止图与邀请图，共 **572 张**。方法：逐张全分辨率目检 + 肢体末端放大复核（程序只作辅助——相扑 _32 教训：残桩贴轮廓生长，连通域/alpha 普查抓不到）；六名检查员的 FLAG 全部由主线逐张亲验裁决，修复后自验板再逐张终验。处理口径：异物残留能擦则擦；真断肢一律**回源图重抠**（零漂移，本轮重画 0 帧）；重抠帧全部套 v1.0.41 骨盆锚点口径定标对齐（逐帧记录在 `dancers/audit142/`）。结论汇总（查出 = 处理完毕，踢出舞步链 **0**、重画 **0**）：

| 伴舞 | 查出 | 重抠 | 擦除/局部修复 | 说明 |
|---|---|---|---|---|
| Amy (modern_woman) | 12 | 5（04/24/33/41/invite） | 7（42/39 擦除；27/31/50/still 源图贴回；37 alpha 恢复） | 04 根因：补丁版发表把前臂涂掉，未打补丁的原版 A 表里手完整 |
| 秀颖 (ancient_woman) | 16 | 8（05/13/47/52/61/invite + 查源后 51/53） | 8（44/46/56/29/36/74/03/still） | 61 第三只手为隔纱透出的邻座手，手术剔除+纱纹理补回；其头后黑块经二次返修（双底验净，共删 1168px）才清净；90 销账（纱褶阴影） |
| 姿三四郎 (sumo) | 11 | 7（31/41/42/48/54/58/59） | 4（27/50/53/56） | 58/59 的「断手」查源坐实为**邻人之手**，单人重抠即排除；58 斜方肌补形经二次返修做谐波融合、53 右腿轮廓经二次返修按源图重建（逐行跳变 21px→1px）；30 销账 |
| 汉娜 (ancient_girl) | 3 | 2（46/invite） | 1（51 白裤透明裂缝以源图真色恢复） | 46 举手原被管线测地线划给邻座，双种子切回 |
| 哪吒 (baby) | 2 | 0 | 2（28 悬空粉指尖残片+暗渍擦除；invite 左脚灰三角擦除） | — |
| 瓦力 (robot) | 21 | 16（08/17/18/21/22/31/32/34/35/37/38/39/44/47/49/invite） | 5（07/23/33/45 残块擦除；23 上臂半透明段 alpha 剪影重建） | **38 整头缺失、47 整头+一臂缺失、39 面甲缺失**——git 考古证明 38 自 v1.0.37 起即无头（历轮全检漏网）；32 查源裁定源图有手指（出货「光滑圆顶」是抠图丢失），已恢复；06/10/12/14 天线变体销账 |

合计处理 **65 项**（重抠 38 / 擦除·局部 27 / 重画 0 / 踢帧 0）。口径偏离两处，均已论证并记录：秀颖 05 补回左手后原画布 307 装不下，按其既有标准宽画布扩至 417（渲染按高定尺寸，上屏大小不变）；sumo 重抠帧未用一刀切中位数高，改用原流水线尺度导出的逐帧自然高（与原出货高 ≤1px 吻合，姿势自然高本就在 373–510 间浮动）。另发现补间源映射一处对不上：出货 61 的真源是 `inbetween/ancient/after_0.webp`（非对齐脚本重建映射所指的 after_1）、74 源为 after_36——修复已镜像到真源文件，日后从补间源重整合时须先修映射表。

收尾全量普查（`dancers/audit142/qa_census.py`，572 资产连通域+触边）：真缺陷 1 处——瓦力 18 重抠后左上空域残留幽灵碎屑 2749px（海军蓝上不可见），已清除（出货+母版），复跑单连通域；其余标记逐个看图销账（瓦力 16 面甲分离、秀颖步摇流苏、轻纱在握点 alpha 谷分段、发丝尖、裙侧飘带尖、73 处构图触边——逐帧目检已覆盖「边缘切肢」项，无一被切）。

手机选人动画卡片：`make_anim.py` 整跑重采样；按采样口径核对——命中变更帧的只有秀颖卡（采样到 13/29/53/74）与瓦力卡（采样到 23），故仅此两张字节变化，其余四张采样帧未涉及、本就无需变。

### 四、测试与包

- 单测 **355**（345 基线 + DancerImageStructureTest 2 + LanIpTrackerTest 8），0 失败；舞步链未动，DancerPoseOrder/DancerInbetweenOrder 钉值不变。
- APK：`MySing-1.0.42.apk`，30,438,422 B，SHA-256 `313f66537965ec2663b1afd1d4fc6ca9d613ba3ada3f3f55707e9d727c438b31`，versionCode 43，签名证书 SHA-256 `1336ee0a…`（共享 keystore）。

- 设备侧待用户验：① 随便放一首舞曲看伴舞——整个身体不应再每拍左右横跳（渲染层主因已除，与 .41 的图内对齐叠加生效）；② 断开/重连 WiFi 或关着 WiFi 开 App 再开 WiFi——右上角应先显示「等待网络…」占位卡、联网后数秒内自动变回二维码；③ 伴舞抽查：瓦力第 38 步有头了、秀颖纱弧无第三只手、相扑抬腿帧脚完整。

## v1.0.43 — 伴舞大小归一（2026-11-16）

用户验收 .42 后：「就是有时会变大变小，但可以接受」——主线说明成因（渲染按画布高定尺寸，各帧人物占画布高度的比例不一，是生成尺度方差）后用户说「那就做吧」。本版只归一帧内容尺度；节拍重音的 scale 脉冲与前奏向前一步的变大是设计，未动。工具：`dancers/measure_size_v1043.py`（测量）+ `dancers/normalize_size_v1043.py`（归一），逐帧记录在 `dancers/normalize_v1043_log.json`。

### 方法

- **量**：身高比例 = alpha bbox 高 / 画布高（alpha≥8，与 .41 同阈值；画布高恒 600）。沿 DancerPoseOrder 全链出每位的中位数 / IQR / 相邻步 |Δ| 均值与最大（含环绕边）。
- **归一（带护栏）**：只处理比例落在中位数 ±25% 带内的帧——带外是真姿势高度（深蹲、鞠躬等），一律不动。带内帧按 `s = (中位数/比例)^0.65` 缩放内容（与 .41 同哲学：对数空间补 65%、不拉满），缩放锚点 =（该帧 .41 对齐后的骨盆锚点 x、脚底基线 y = bbox 底边），预乘 alpha 的 LANCZOS 缩放；放大被画布透明边距钳制（bbox 外扩 1px 算抗锯齿 fringe），画布尺寸不变，被钳帧逐帧入日志。缩放后 bbox 高不变（<0.5px）的帧留字节不动（noop）。
- **静止图/邀请图**同口径归一到舞链中位数（它们原本比中位数高 4–14%，是邀请→起舞切换处尺寸跳变的来源），12 张全部缩放、差距收窄约 65%。
- **镜像源母版**（防日后重整合把抖动带回）：PNG 母版（1–60 帧 + 邀请母版）直改；补间源不用 .41 脚本的重建映射（.42 已证其对 ancient 61/74 是错的），改按预乘缩略图 RMS **内容匹配**（接受的配对距离 ≤0.0076，近乎像素级一致），含糊的跳过并记录。共镜像 501 个源文件、0 跳过；匹配被拒 13 帧（秀颖 80/86/87/88、哪吒 61/62/68/69/70/72/77/80/81——多为缩略图近乎相同导致无法唯一判定，其出货帧本身已归一，只是源镜像保守跳过）。

### 结果（before → after，同口径复测）

| 伴舞 | 中位数 | IQR | 相邻Δ 均值 | 相邻Δ 最大 | 比例范围 | 静止 / 邀请 |
|---|---|---|---|---|---|---|
| Amy | 0.806 → 0.806 | 0.084 → **0.031** | 0.024 → 0.019 | 0.163 → 0.150 | [0.55,0.98] → [0.55,0.86] | 0.898→0.838 / 0.902→0.840 |
| 秀颖 | 0.765 → 0.765 | 0.088 → **0.037** | 0.022 → 0.015 | 0.092 → 0.107 | [0.60,0.88] → [0.60,0.81] | 0.843→0.790 / 0.850→0.792 |
| 姿三四郎 | 0.731 → 0.730 | 0.055 → **0.022** | 0.021 → 0.010 | 0.092 → 0.035 | [0.63,0.85] → [0.67,0.77] | 0.833→0.765 / 0.833→0.765 |
| 汉娜 | 0.490 → 0.490 | 0.048 → **0.017** | 0.012 → 0.006 | 0.067 → 0.057 | [0.40,0.57] → [0.41,0.52] | 0.508→0.498 / 0.515→0.498 |
| 哪吒 | 0.408 → 0.408 | 0.030 → **0.010** | 0.008 → 0.004 | 0.040 → 0.017 | [0.32,0.45] → [0.38,0.42] | 0.437→0.420 / 0.437→0.418 |
| 瓦力 | 0.815 → 0.815 | 0.070 → **0.025** | 0.046 → 0.021 | 0.212 → 0.212 | [0.60,0.96] → [0.60,0.86] | 0.877→0.835 / 0.877→0.837 |

处理计数：出货舞步帧缩放 **508**、noop 48、带外不动 4（Amy 9/20/77 = 0.547/0.592/0.597，瓦力 9 = 0.603，均为带外真姿势）、放大被边距钳制 35（Amy 16 / 秀颖 8 / 相扑 5 / 汉娜 3 / 哪吒 3 / 瓦力 0，逐帧在日志）。

如实说明三处未尽：① Amy 相邻Δ均值只降 21%——她的钳制帧最多（16）且最大步差与带外真姿势帧相邻，按护栏不硬拉；② 秀颖相邻Δ最大单步 0.092→0.107 微升——某步两侧一帧被钳、一帧足额，差动钳制所致（均值仍降 32%）；③ 瓦力最大单步 0.212 不变——该步紧邻带外不动的 9 号深蹲帧且另一侧恰为中位数附近的 noop 帧，属护栏预期。三者都是「真姿势不拉平」口径的直接后果，不是归一失效：六人 IQR 一致收窄约六成，与 0.65 补偿的理论残余（35%）吻合。

.41 对齐完好性复核：与 git HEAD 逐帧比骨盆锚点（相对画布中心），漂移 mean ≤0.31px、max ≤1.96px（缩放绕骨盆锚点构造性保持，残余来自缩放后骨盆带行重定义）。拼条目检：每位舞者取归一前步差最烈的连续 10 步窗口、按渲染口径（同高、居中、底对齐、海军蓝底）拼图，主线逐张亲看——无拉伸变形、无裁切、肢体完整、人物大小肉眼一致。

手机选人动画卡片：`make_anim.py` 六张全跑重采样，合计 720,298 B。

### 测试与包

- 单测 **355**（与 .42 基线同数：本版无代码改动，纯资产管线），0 失败；舞步链未动。
- APK：`MySing-1.0.43.apk`，31,152,751 B，SHA-256 `afa9e01d5992d121caf3c7358aed1c2f52b62859973617f98ef5931e8a3f6150`，versionCode 44，签名证书 SHA-256 `1336ee0a…`（共享 keystore）；包内伴舞资源条目 572 不变。

- 设备侧待用户验：放舞曲看 Amy / 瓦力连跳——人物大小应明显稳住，不再忽大忽小；深蹲、鞠躬等姿势仍保持其真实高度（护栏内不动）；邀请→起舞切换处不应再有尺寸跳变。

## v1.0.44 — 去人声 B 方案（分频消音）+ 点唱默认伴奏 + logo 白色光晕（2026-11-16）

用户拍板：去人声先用 B 方案（改进实时算法），A 方案（AI 分轨预制伴奏）延后不取消。另两条同日新规一并入版：点唱默认开伴奏、两个品牌 logo 向外发白光。

### 一、分频消音（B 方案本體）

旧实现的开启路径是全频带 (L−R)/2：居中人声压下去的同时，同样居中的低频（贝斯、底鼓）陪葬，伴奏发空发薄——这是「效果不明显」的一大半原因（另一半是 .40 已修的通道接线）。

新法（`VocalCancelAudioProcessor.cancelPair`，只动 enabled 的消音路径）：每声道一阶低通（分频点 180 Hz，系数 α = 1 − exp(−2π·180/采样率)，onConfigure 时按采样率算）拆低/高频段：

- 输出 = 低频段 (LP(L)+LP(R))/2 单声道和（双声道同送，低频完整保留）+ 高频段 (H(L)−H(R))/2（H = x − LP(x)，(L−R)/2 本体原样）。
- LP + H = x 逐声道恒等，所以全声像内容（单侧声道）输出仍恰为经典 x/2，逐样本成立（钉值 ±1 LSB）。
- 低通状态逐声道跨 buffer 保持，onConfigure / onFlush / onReset 清零（seek 不拖尾）。
- 不动的口径：输出恒 PCM_16BIT、mono/非 PCM 返回 NOT_SET、float 先转 16-bit 再处理、多声道只处理前两声道、disabled 逐字直通、保命 try/catch、双路构建形态（裸重试无处理器 + 如实降级）。

单测钉值（与算法同构的离散仿真值，Kotlin 实测同值域通过）：居中 60 Hz 正弦开启消音——旧算法输出能量 = 0，新算法保留 **90.0%**；带 2% 声道不平衡时新/旧保留能量比 ≈ **8,800×**（钉 ≥1,000×）；居中 1 kHz 正弦残余：旧 0、新 **3.14%**（≈ −15 dB，钉为「旧值 + 5% 输入能量」的绝对容差内——一阶裙边的价码，明写不藏）；跨 buffer 接缝逐样本全等（状态连续性钉死）；flush/reset 后输出与全新处理器逐样本全等。旧逐样本钉值测试 3 个因分频口径改写（单帧值由低通瞬态决定，如 (1000,200) 首帧 400→405；DC 恒定输入按设计归入低频保留），逐条理由写在测试类头注。

离线自测（《心太软》60–120 s 一段，ffmpeg 解码 + 同口径数值实现，附带参考、耳朵为准）：

| 指标 | 旧（全频带） | 新（分频） |
|---|---|---|
| 低频段（<180 Hz）能量保留 | **2.8%** | **81.8%** |
| 人声段（中置 300–3000 Hz）能量残余 | 8.9% | 17.0% |

低频救回来了：保留率约 29 倍，伴奏不再发空。人声残余如实记：由 8.9% 升至 17.0%——仍压掉约 5/6，但比旧法多漏约 8 个百分点，漏的是 180 Hz 一阶低通裙边在人声基频段（200–500 Hz）的穿透；这是规格（一阶、180 Hz）落地的固有代价，换的是低频的完整回归。若用户听感上觉得人声漏多了，后手是 A 方案（预制分轨），不在本版加码。

### 二、点唱默认伴奏（新规，单列）

规则（用户定）：**随机放的歌保持原唱，点唱的歌默认去人声**。落法：新歌开始（`startSong` → `PlayerController.load`）时按条目来源设默认——`EntrySource.ORDERED`（手机点歌、电视点歌、「现在唱」跳转、仪式锁定的下一首，全是 ORDERED）→ `wantAccompaniment=true`，经 `setAccompaniment` 同步处理器 enabled 与控制条「原唱/伴奏」芯片显示（芯片读的 Activity 状态在 startSong 里同源赋值）；`EntrySource.FILLER` → false。与变调同口径按歌重置；手动切换仍即时生效、只管本首。裸重试通道上的点唱歌：默认照设（wantAccompaniment=true），处理器不在链，`accompanimentUnavailable` 如实提示的口径不变。纯策略 `AccompanimentPolicy.defaultOnStart`（queue 包，与 FillerPolicy 同门），测试钉死 ORDERED→true / FILLER→false / null→false。

### 三、品牌 logo 白色光晕（新规，单列）

横幅左上角两个 logo（中文「一起来唱卡拉OK」图 / 英文 MySing 图）都要在深蓝底上带一圈向外扩散的白光，须贴图形轮廓、不要矩形背光板。落法（新 composable `BrandLogo`，全仓 logo 只有播放页横幅这一处绘制点——浏览页横幅无 logo 图，此一处即全覆盖）：同图以 `ColorFilter.tint(White)` 取白色剪影（alpha 即字形轮廓），垫底画 12 方向小偏移环（半径 3.0dp / 紧凑 2.2dp，每份 alpha 0.16）+ 一份居中放大 1.07× 的剪影（alpha 0.35）作光晕芯，叠印累积成向外 bloom；原图最后全 alpha 盖回，字形不糊。偏移与缩放全在绘制层（offset / graphicsLayer），盒子测量与旧版逐字相同（高 36/46dp、宽按图宽高比），光晕溢出不挤语言芯片。未用 `Modifier.blur`：API 31 以下静默失效，而 Shield 是 API 30——偏移叠印在所有版本上都真发光。

### 测试与包

- 单测 **361**（355 基线 + 处理器净增 5 + 策略 1），0 失败。处理器测试 9 → 14：原位改写 3（多声道、float 等价、float 多声道）、移除 1（旧逐样本钉值，由稳态钉值取代，理由见测试头注）、新增 6（低频保留、低频比值、1 kHz 残余、声像恒等、接缝连续、flush/reset 状态清零），净增 5。
- APK：`MySing-1.0.44.apk`，31,169,141 B，SHA-256 `41fd53eb6d2d567a658c7da77114d83bc68b79a9a07312d1d6d2d23a2df7e882`，versionCode 45，签名证书 SHA-256 `1336ee0a…`（共享 keystore）。

- 设备侧待用户验：① 点一首歌（走主通道的）听伴奏——低频应明显回来、不再发空，人声压制与旧法同级；随机歌应保持原唱、点唱歌应一上来就是伴奏（芯片显示「伴奏」），手动切回原唱只管本首；② 两个 logo 在深蓝底上应有贴轮廓的白光晕，旁边的中文/EN 芯片位置不动；③ 走裸重试的歌点唱时仍会如实提示伴奏不可用（默认状态照设、通道没处理器）。

## v1.0.45 — 仪式画面：下一首卡片上移 + 六人放大并站拢（2026-11-16）

用户原话两条，配套入版：「唱完6人出来邀请时，将歌曲文件名提高，6人再放大」；随后补一条：「六人可以靠近一些，现在分得太开」。纯布局改动：仪式时序（Ceremony.kt 的 8.4s 五段）、锁定/中断规则、keeper 独邀的常规尺寸与位置、光池逻辑，一律不动。

### 一、下一首卡片上移

`CeremonyStage` 的大卡片（32sp 金字，内容、字号、倒计时不变）从舞台正中（Alignment.Center）改到上方居中：`Alignment.TopCenter` + 顶边距 = 屏高 × 12%（舞台盒自身起于顶部横幅行之下，故卡片在屏幕上落在横幅之下的上方带）。仪式全程（鼓掌 / 齐邀 / 退出 / 独邀各段）同一个位置，不跳位。动机是配套：六人放大后，原在正中的卡片会被人群挤到，先让上去。

### 二、六人放大：高度公式（SpotlightGeom.sixRowHeightDp）

身高 = min（屏高 × 0.46， 屏宽 × 0.98 / 整排跨度系数）。旧口径 0.32（紧凑 0.36）废止。两种宽高比下实际生效（dp 口径，公式钉值测试覆盖）：

| 屏幕 | 生效身高 | 说明 |
|---|---|---|
| 16:9 电视 1920×1080 | **0.46 × 屏高**（496.8dp） | 收紧 pitch 后宽度上限不再卡住，目标值直接生效；整排跨度 ≈1878dp ≈ 屏宽 97.8% |
| 16:9 960×540 | **0.46 × 屏高**（248.4dp） | 同上，跨度 ≈ 屏宽 97.8% |
| 用户手机宽屏 ≈869×411 | **0.46 × 屏高**（189.1dp） | 目标值生效，跨度 ≈715dp ≈ 屏宽 82% |
| 4:3 1024×768（宽度卡住的例子） | 0.346 × 屏高（265.5dp） | 宽度上限生效：0.98 × 1024 / 3.78 |

队列为空时的独立掌声排（PlayerScreen 的 showCelebration）与仪式排共用同一公式、同一 SixInRow——用户眼里同一个「六人出来」的场面，一个尺寸、一套站位。

### 三、六人站拢：框体重叠（pitch 0.60）

成因：每人的画框宽 = 身高 × 0.78（FRAME_ASPECT），但人物本体在框内居中、两侧是透明边距；旧排法框挨框（spacedBy 6dp），中心距 = 0.78×身高 + 6dp，视觉上人站得很开。

改法：新增共享组合 `SixInRow`（仪式排与掌声排两处同口径）——固定跨度的盒内以显式 offset 定位，相邻中心距（pitch）= **0.60 × 身高**：透明边距互相叠、人物本体靠近，合影式站位；手臂张开的姿势允许轻微交叠（按用户口径，是预期效果）。整排跨度 = （0.78 + 5 × 0.60）× 身高 = **3.78 × 身高**，放大公式已按此重算（见上）。光池挂在每人自己的框上、随人放大，逻辑未动；重叠后相邻光池交融成一片舞台光，属预期。

### 四、交付与待验

- 单测 **366**（361 基线 + 5：pitch/跨度钉值、16:9 与宽屏手机的目标值生效、4:3 宽度上限、不超预算不变量），0 失败。
- APK：`MySing-1.0.45.apk`，31,169,135 B，SHA-256 `ce4e854f507c0253e0ff2e3d0e9fe3f22374065f31c817ddf5ea9ffb42b27e64`，versionCode 46，签名证书 SHA-256 `1336ee0a…`（共享 keystore）。
- 设备侧待用户验：唱完一首看仪式——卡片应在上方不再被人群挤、六人明显变大且站得拢；无下一首时的掌声排同尺寸同站位；keeper 独邀的位置与大小应与从前一致（交接无跳）。

## v1.0.46 — 电视端语音搜索（2026-11-16）

用户原话：「电视点歌里面可不可以加个语音搜索」→ 确认「好，加语音搜索」。范围只做这一件：浏览页搜索 + 语音输入；播放舞台、歌词安全带、仪式、队列规则一律不动。

### 一、先说清的事实：浏览页原本没有文本搜索框

电视浏览页此前只有筛选芯片（歌曲/歌手、语言、性别、歌手），文本+拼音搜索只存在于手机点歌页（`Catalog.search` / `PinyinInitials.matches`）。语音结果要有地方落、还要能改错字，所以本版先在浏览页补上搜索框本体，再把语音接到它上面——不是另起一套搜索：过滤走的仍是既有 `PinyinInitials.matches`（歌曲）与新增同规则的 `matchesText`（歌手名，子串/拼音首字母两条规则与歌曲侧逐字一致）。

### 二、搜索行（浏览页内，chips 行之下）

- 搜索框（占行内弹性宽）：显示当前查询词，空时显示提示「🔍 搜索歌名或歌手」；点/OK 打开原生 EditText 对话框编辑（与改名编辑器同款，Shield 出 Leanback 键盘），预填当前词、全选，存空即清空。行尾查询非空时多一颗「✕ 清除」芯片。
- 查询作用于当前列表之上：歌曲列表在 special（收藏/最近/全部+语言/性别/歌手芯片）的结果上再过一遍匹配；歌手列表按 `matchesText` 过名。
- 语音结果（或对话框保存）生效时：切到歌曲列表、清掉收藏/最近 special（语言/性别/歌手芯片保留），焦点自动落到结果第一行，遥控器直接接手选歌。

### 三、语音输入（系统 SpeechRecognizer）

- 入口：搜索框旁 🎤 语音按钮，遥控器可聚焦、OK 启动；紧凑布局同尺寸口径（12sp/小内距），行在浏览页既有的右侧二维码预留区之内，不挤既有元素。
- 识别：`SpeechRecognizer` + `ACTION_RECOGNIZE_SPEECH`（FREE_FORM），语言跟随 App 当前 UI 语言（中文→zh-CN，英文→en-US，纯函数 `VoiceSearch.languageTag` 钉值）；取第一候选填入搜索框状态（`VoiceSearch.firstCandidate` / `queryAfterResult`），走既有过滤；识别文字保持可编辑。
- 监听浮层：双语「正在听…说出歌名或歌手」对话框，Back 即取消（dismiss = cancel 识别）；出错/没听清给一句双语短提示，焦点还给 🎤 按钮。
- 权限：`RECORD_AUDIO` 运行时权限，首次点按钮先弹双语说明（只听搜索、不录唱歌），点「继续」才走系统申请；被拒后按钮只显示「没有麦克风权限，用打字搜索吧」，不再反复弹系统框。Manifest 本版只新增 `RECORD_AUDIO` 一个权限，外加 Android 11+ 包可见性 `<queries>`（RecognitionService，否则 `isRecognitionAvailable` 在有服务的设备上也会误报 false）。
- 可用性守卫：`isRecognitionAvailable` 为 false 时按钮置灰，行下常驻双语「本设备没有语音服务」，点击再提示一次，绝不崩溃（启动路径内重复检查一次）。判定逻辑在纯对象 `VoiceSearch`（buttonState/buttonEnabled），与 Android API 分离。
- 口径不变：本 App 的麦克风与唱歌话筒系统完全无关——话筒走独立功放，App 从不处理唱歌音频、不打分；此麦克风只听搜索命令。识别走设备自带语音服务，需要 WiFi 在线。

### 四、权限核对（如实记录）

本版 merged manifest 权限：INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE、RECORD_AUDIO（+ androidx 自动生成的 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION）。其中 **RECORD_AUDIO 是本版唯一新增**；INTERNET 是自 v1.0.0 起就有的存量权限——手机点歌的局域网 HTTP 服务靠它监听端口，没有它手机扫码点歌就断了，本版未动它。语音识别本身不经 App 的网络：走系统识别服务，App 侧没有为语音加任何网络权限。

### 五、交付与待验

- 单测 **372**（366 基线 + 6：VoiceSearch 5 个——语言映射、第一候选取舍、结果填入语义、可用性状态；PinyinInitials.matchesText 1 个），0 失败。SpeechRecognizer 本体是设备侧能力，未伪造测试。
- APK：`MySing-1.0.46.apk`，31,169,229 B，SHA-256 `ea24bf792d3b8186ec7c8b056ada126891ce7a28bdeb9178656e9dc279831249`，versionCode 47，签名证书 SHA-256 `1336ee0a…`（共享 keystore）。
- 设备侧待用户验：① 浏览页点 🎤，首次允许麦克风权限，说「刘德华 忘情水」类歌名/歌手——识别词应落进搜索框、列表立刻过滤、焦点在第一行；认错字时点搜索框改字应能改；② 英文界面下说英文歌名走 en-US；③ 拒绝权限后按钮只给提示不再弹框；④ 无语音服务的设备上按钮置灰有提示（他的 Note10+ 有三星语音服务，预计走可用路径）。

## v1.0.47 — 自动辨认伴奏文件并配对（2026-11-16）

用户原话：「下一版做自动辩认有伴奏文件的歌曲」。背景：独立的分轨 App 在原歌同文件夹生成「原名（伴奏）.mp3」（全角括号、时长与原歌严格一致、人声已分离干净）。本版让 MySing 自动认这份文件。仪式时序、伴舞、队列规则、语音搜索、Plan B 处理器本身一律不动。

### 一、扫描配对（library/AccompanimentPairing + LibraryScanner）

- 规则纯逻辑拆在 `AccompanimentPairing`：stem 以「（伴奏）」或半角「(伴奏)」结尾才算伴奏文件，只剥一层后缀；裸后缀（无原名）不算；标题只是以「伴奏」二字结尾、没有括号的，是普通歌曲，永不误判。
- 扫描时伴奏文件不进歌曲列表，而是按（分类文件夹， 原名 stem）配给同文件夹的原歌 → `Song.accompanimentRef` / `hasAccompanimentFile`。分类文件夹不同不配对；找不到原歌的孤儿伴奏静默忽略（不进任何列表、不报错）；伴奏自带的 .lrc 既不建歌也不抢原歌的歌词配对。
- 同名冲突（全角、半角各一份都在）：按文件名 plain 顺序取第一份（半角名排在全角前），每次重扫结果一致，与文件列举顺序无关。
- 扫描是整库重建：往硬盘里新增伴奏文件后，后台对账/手动重新扫描即自动配上，不需要重选文件夹。歌库缓存升到 v2 格式（每行第七字段存伴奏引用），v1 缓存按既有规矩整体作废、由紧随其后的全量扫描重建——冷启动先出缓存目录（带标记），扫描落地后配对补齐。

### 二、列表呈现

- 电视浏览列表：有伴奏文件的歌曲名后带小标记「 · 伴奏」（英文界面 "Acc."，新 TvKey.ACCOMP_BADGE，走既有双语表）。
- 手机点歌页：/api/songs 每首多带 `acc` 布尔，页面在歌名旁渲染既有 .badge 样式的小标记（中文「伴奏」/ 英文 "Acc."，页面字典新键 accBadge）。
- 伴奏文件本身永不出现在任何列表（按歌名/歌手/中文/英文/收藏/最近），永不被随机填充选中（填充只从扫描目录里挑，而它根本不是目录条目）；最近唱过记的仍是原歌。

### 三、播放切换（audio/PlaybackSourcePolicy + PlayerController）

- 选择策略拆成纯函数 `PlaybackSourcePolicy.choose(hasFile, accompanimentOn)`：关 → 原文件；开 + 有文件 → 伴奏文件（已去人声，绝不再过消音处理器）；开 + 无文件 → 原文件 + 实时分频消音（Plan B，现状）。
- v1.0.44 的默认规则顺延：点唱默认伴奏开 → 有文件就直接以伴奏文件起播；随机填充仍放原唱（原文件，哪怕伴奏文件就在旁边）；手动切换只对当前歌生效不变。
- 切换实现：`PlayerController.load` 多收一个 accompanimentUri；`setAccompaniment` 在文件/原唱之间切换时按当前进度换播媒体源（同一 LRC、同一进度、同一变调；位置在新文件时长已知时钳制在其内；暂停状态保持）。**源选择与处理器通道完全解耦**：文件伴奏不依赖 VocalCancelAudioProcessor，所以走 plain-retry 裸通道的歌也能用文件伴奏；连报错重试也保留当前文件（伴奏文件在裸通道上照常播）。`accompanimentUnavailable` 相应修正：正在播伴奏文件的歌永不报「不可用」；无文件时的如实提示逻辑不变。
- 诊断行（后台页）对有伴奏文件的当前歌追加「· 有伴奏文件」，正在播文件时再加「（正在播）」，方便设备侧核对配对是否被认出。

### 四、测试（399 = 372 基线 + 27，0 失败）

- `AccompanimentPairingTest` 7 个：全角/半角剥离、无括号不误判、裸后缀、只剥一层、后缀必须在末尾。
- `LibraryScannerTest` +9（共 14）：全部用真实中文文件名走真实扫描路径——全角配对且不建歌、半角配对、孤儿丢弃、无伴奏为 null、跨文件夹不配对、同名冲突与列举顺序无关、伴奏侧 lrc 不干扰、裸词标题是普通歌、重扫捡起后加的伴奏。
- `PlaybackSourcePolicyTest` 6 个：hasFile × 开/关 × 点唱/填充全矩阵（含「填充有文件也放原唱」「手动开优先文件不过处理器」）。
- `LibraryCacheTest` +2（共 8）：配对往返保真、v1 缓存整体作废。
- `KaraokeHttpServerSocketTest` +1（共 16）：真实 socket 上 /api/songs 带 acc 标志（中文歌 true / 英文歌 false），且列表仍只有两首原歌。
- `PhonePageTest` +1（共 19）：页面双语 accBadge 与按 s.acc 渲染的钉值。
- `FillerPickerTest` +1（共 12）：扫描后的目录喂填充选择器，永远只挑到原歌。
- 非 ASCII 口径：配对/扫描/缓存用真实中文文件名与引用；acc 标志经真实 socket 传输验证；设备侧（Shield/Note10+ 上 SAF 真实目录的配对与换播听感）仍待用户验。

### 五、交付与待验

- APK：`MySing-1.0.47.apk`，31,185,612 B，SHA-256 `91b1c043f04ad2cead6a398df09f0aaef6a3d13d7edacdd9e04266cf1d73c702`，versionCode 48，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 往一首歌旁边放「歌名（伴奏）.mp3」，重扫后电视列表和手机页该歌应带「伴奏」标记、歌单总数不变；② 点唱该歌应直接以伴奏起唱，切回原唱进度不跳；③ 随机填充唱到该歌时仍是原唱；④ 没有伴奏文件的歌行为与从前一致（实时消音）。

## v1.0.48 — 六人排大收紧 + 面板避让 + 卡片再上移（2026-11-16）

起因是用户在 v1.0.47 上拍的实况截图（手机横屏、仪式邀请段）：① 瓦力被右侧「已点歌单」队列面板盖住一半——六人排横跨到了面板预留带下面；② 用户原话「完全可以再靠近很多」——每人的实际身形远窄于 0.78 画框，0.60 的 pitch 仍然太宽。随后追定两条：③ 下一首卡片再上移；④ 六人再放大。本版五件事：收紧 pitch、整排避让面板、卡片上移、身高目标提高、瓦力个人小偏移收尾。

### 一、实测数据（六人排实际用的帧，alpha > 8 包围盒）

六人排鼓掌段画的是每人的站立原图（resId），邀请段画 invite 图；全部画布高 600 px，身形宽度以画布高为单位：

| 伴舞 | 站立图画布 | 站立 bbox x | 身形宽（站立/邀请） |
|---|---|---|---|
| Amy | 199×600 | [7, 192] | 0.308 / 0.362 |
| 秀颖 | 380×600 | [9, 366] | 0.595 / 0.317 |
| 姿三四郎 | 305×600 | [16, 288] | 0.453 / 0.485 |
| 汉娜 | 190×600 | [5, 185] | 0.300 / 0.245 |
| 哪吒 | 178×600 | [4, 174] | 0.283 / 0.202 |
| 瓦力 | 243×600 | [7, 237] | 0.383 / 0.355 |

六人的人物在各自画布内都基本居中（中心偏差 ≤ 0.01 身高；Amy 左右透明 7px/7px、瓦力 7px/6px）。相邻两人「身形边缘刚接触」所需的中心距（两帧取大）：Amy–秀颖 0.452、秀颖–相扑 0.524、相扑–汉娜 0.377、汉娜–哪吒 0.292、哪吒–瓦力 0.333。绑定对是秀颖的站立图——汉服大袖张开，包围盒宽 0.595。

### 二、整排收紧：pitch 0.60 → 0.48，跨度 3.78 → 3.18

统一 pitch 定为 **0.48 × 身高**（`SIX_ROW_PITCH_FRACTION`，跨度公式随之为 0.78 + 5×0.48 = 3.18）。此时五对相邻身形的边缘间隙：+0.028 / **−0.044** / +0.103 / +0.188 / +0.147——只有最宽的一对（秀颖袖口对相扑侧身）包围盒交叠 0.044，属用户已认可的「手臂张开姿势轻微交叠」；其余都留正缝。仪式排与独立掌声排共用 `SixInRow`，同口径生效。

### 三、面板避让：整排只许待在预留带左界以左

- 预留带宽沿用 LyricsBand 的既有口径并抽成单一出处 `LyricsBand.reserveWidthDp(屏宽)` = 0.19 × 屏宽 + 12dp（面板宽 + 一条缝），恒定预留、与面板是否显示无关。
- 高度公式（`SpotlightGeom.sixRowHeightDp(屏宽, 屏高, 预留带宽)`）= min（0.58 × 屏高， （屏宽 − 预留带宽） / 3.18）。
- 位置公式（新增 `sixRowLeftDp`）：整排在「屏宽 − 预留带宽」的可用宽度内居中。于是宽度封顶时右界恰好落在预留带左界上、永不越界——「整排右界 ≤ 预留带左界」由构造保证，不靠收紧碰巧，单测以不变量形式钉住（8 种屏幕尺寸全验）。仪式排、独立掌声排两处同改。

### 四、卡片再上移 + 身高目标 0.58

- 仪式下一首大卡片顶边距：屏高 × 12% → **× 5%**（贴近横幅行下方），五段同一位置不跳位，内容/字号/倒计时不变。
- 身高目标 0.46 → **0.58**，实际生效取「目标 / 收紧跨度 / 面板避让」三者最小：
  - **16:9 电视（1920×1080）**：485.3 dp ≈ **屏高 0.449**——面板避让封顶（可用宽 1543.2 / 3.18）。比 v1.0.45 的 0.46 略低 0.011，但六人站得近得多（pitch −20%）且整排完整避开面板，无人再被盖。
  - **用户手机宽屏（≈869×411 dp）**：217.6 dp ≈ **屏高 0.529**——同样宽度封顶，较 0.46 明显放大。
  - 0.58 目标本身只在超宽屏（如 2560×1080）生效。

### 五、瓦力边距对称验收：三种手段都用了

收紧 + 避让居中之后复核两端视觉边距（自可用区边缘起算）：Amy 视觉左边距 0.2358 × 身高、瓦力视觉右边距 0.1975 × 身高——**仍差 0.038**。原因：两端槽位的视觉边距只取决于各自画布宽高比在 0.78 固定槽位里的居中深度（Amy 图窄 0.332、沉得深；瓦力图宽 0.405、沉得浅），与 pitch 无关；居中又把两边等量抬升，所以收紧和避让都消不掉这个差。故按原验收标准保留瓦力个人小偏移：`SixInRow` 内仅瓦力的槽位左移 0.038 × 身高（`SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION` / `sixRowRobotShiftDp`，阈值 8–128 下实测差 0.035–0.038，稳健），移后其视觉右边距 0.2355 ≈ Amy 的 0.2358，对称达成。他的光池在同一槽位内、随他一起移；其他五人位置、他的独舞/邀请位置均未动。哪吒–瓦力间隙移后仍有 +0.109，不挤。

### 六、测试（403 = 399 基线 + 4，0 失败）

- `SpotlightGeomTest` 六人排一节重写：pitch/跨度钉值（0.48 / 3.18）；缝隙依据钉值（五对半宽和，只有一对交叠且 ≤ 0.05、其余 ≥ +0.02）；电视/手机/4:3 三个场景的生效身高钉值（485.3 / 217.6 / 257.1）；超宽屏目标生效时左界居中钉值（34.8）；「永不伸到面板下」不变量（8 种尺寸：身高 ≤ 0.58、跨度 ≤ 可用宽、右界 ≤ 预留带左界）。
- 瓦力偏移 2 个：偏移量 = 实测边距差（0.1975 + 0.038 ≈ 0.2358）、随身高线性缩放且远小于 pitch。
- LyricsBand 的 reserveWidthDp 抽取为纯重构，compute 行为不变，既有 LyricsBandTest 不动即过。

### 七、交付与待验

- APK：`MySing-1.0.48.apk`，31,185,618 B，SHA-256 `109e7d102ea538f9ea1bcbdb0b68a7d71780d6506085b6ae419bf0936c416f4a`，versionCode 49，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 仪式和唱完掌声时六人明显更近、更大，瓦力完整露出、不再被点歌面板盖住；② 下一首卡片贴近横幅下方；③ 秀颖与相扑之间袖口轻微交叠观感可否接受。

## v1.0.50 — 邀请动画（含弯腰）+ 五人排（Amy 抱汉娜）+ 鼓掌动画（2026-11-16）

> 说明：v1.0.49 按用户指令中途停掉、**从未发布**（无 tag、无 Release、商店与 GitHub 上都不存在这个版本）；其已完成的半成品（InviteCycle 骨架、Dancers 的 RowFigure/HOLD_COMBO、Spotlight 的五人几何、已转好的 57 张 webp）经逐项审查后直接折叠进本版，与弯腰帧一并交付。本版 versionCode 50（已发布的 v1.0.48 为 49，顺延一位）。

用户三句话定的三件事：「汉娜能不能让 Amy 抱着」（现实中汉娜是 Amy 的女儿）、「邀请可不可以做成动画」、「邀请时他们能弯腰吗」；外加「鼓掌也做成动画，一人两帧就可以了」。全部素材先画图、逐帧全分辨率 QA、经用户看图批准后才集成（站立/邀请 6 帧/鼓掌 2 帧/弯腰 2 帧，组合另有一套）。

### 一、邀请动画：手势 + 弯腰的完整帧链（新 InviteCycle）

- 每位伴舞一条 8 帧链：邀请手势 6 帧 → 半弯 bow_0 → 弯到位 bow_1。播放序为链式往返 `0,1,2,3,4,5,6,7,7,6,5,4,3,2,1`（CHAIN 把弯到位帧重复一次，往返后它连占 3 步 = 到位多停一拍），全周期 16 步 × 250ms = 4.0s，与被它取代的旧整身鞠躬周期（INVITE_PERIOD_MS）相同。
- 帧选择是调用方时钟的纯函数（暂停冻结）：solo 前奏邀请走播放位置、仪式群邀与 keeper 独邀走段内时钟。仪式两个邀请段都是 2000ms，250ms/步恰好 8 步——手势正好走到弯腰顶点结束，不会半路断在抬手处。
- 人类帧间沿用 PoseCycle 的 crossfade 口径（步内前 35% 淡入）；**瓦力全程硬切**（blendFor 返回 null，与跳舞/尾奏的豁免一致）。帧本身承载手势与弯腰，整身姿态改走中性 NEUTRAL_POSE——旧的单帧 + 倾斜鞠躬（DancerActivity.invitePose / Ceremony.invitePose）退出舞台（函数保留、仍有单测钉住，不再被舞台调用）。邀请时向前变大的布局放大（v1.0.32）不变；无前奏直接跳舞等既有规则不变。
- 三处邀请全覆盖：歌曲前奏 solo、仪式第②段群邀（排内每位各播自己的链，组合播 hold 链）、仪式第④段 keeper 独邀（EXITING 段 keeper 以链的静止帧淡入，SOLO_INVITE 段播完整链）。仪式 EXITING 段排内定格在手势完成帧（链的第 5 帧）随淡出化去，与旧版「定格邀请帧」等价。

### 二、五人排：Amy 抱着汉娜（SixInRow → CelebrationRow）

- 排的构成改为五位：[Amy 抱汉娜组合][秀颖][相扑][哪吒][瓦力]（Dancers.ROW_FIGURES；组合不是可选伴舞、没有自己的 Dancer 记录，只存在于排内呈现；点歌页选伴舞仍是六位，汉娜 solo 跳舞/邀请一切照旧）。
- 几何按五位重新推导（SpotlightGeom.ROW_*）：pitch 按实测身形宽定 **0.52 × 身高**，跨度 = 0.78 + 4×0.52 = **2.86**。实测口径同 v1.0.48（alpha>8 包围盒、按同时态取最坏值），本版把弯腰帧也纳入同时态复测——四对相邻的「刚接触」中心距：组合→秀颖 0.553（邀请第 3 帧绑定）/ 秀颖→相扑 0.521（站立帧）/ 相扑→哪吒 0.382 / 哪吒→瓦力 0.333；弯腰各态均不绑定任何一对。pitch 0.52 下边缘间隙 −0.033 / −0.001 / +0.138 / +0.187：只有最宽一对包围盒轻微交叠 0.033（秀颖袖尖搭组合边缘，用户已认可的轻微交叠），第二对恰好相切。
- 面板避让与居中公式不变（高度 = min(0.58×屏高, (屏宽−预留带)/2.86)，整排在可用宽内居中，右界 ≤ 预留带左界由构造保证）。实际生效：**16:9 电视 1920×1080 → 539.6 dp ≈ 屏高 0.500**（宽度封顶；比六人排的 0.449 明显放大——少一个身位买回了尺寸）；**用户手机宽屏 ≈869×411 dp → 238.4 dp = 0.58 目标本身**（宽度上限 241.9 不再绑定，六人排时为 0.529）；4:3 1024×768 → 285.8 ≈ 0.372（宽度封顶）。
- 瓦力个人偏移**退役归零**：新首位是组合的整幅 468 画布，实测两端视觉边距——组合左边距 0.2000 × 身高、瓦力右边距 0.1975 × 身高——只差 0.0025（约 1dp），构图自身对称；偏移机制保留在原处（ROW_ROBOT_LEFT_SHIFT_FRACTION = 0），日后美术再失衡可复用。

### 三、鼓掌动画：两帧真拍手（新 ClapCycle）

- 每位两帧（张开 _clap_0 / 拍合 _clap_1；组合为汉娜在怀里张开 ↔ 拍合），375ms/帧交替（用户定的 350–400ms 区间中值，全周期 750ms、约每分钟 80 下）。仪式鼓掌段与独立掌声排（无下一首的送别）同口径。
- 身体走中性姿态、只有手在动（图就是这样画的）；旧的静帧 + 弹跳姿态（DanceMotion.clapPose，300ms 周期）退出舞台——其周期会与帧交替互相漂移（函数保留、单测不动）。

### 四、素材入包

- 新增 71 张 webp（六位单人各 10 张：邀请 6 + 弯腰 2 + 鼓掌 2 = 60；组合 11 张：站立 1 + 邀请 6 + 弯腰 2 + 鼓掌 2；与 APK 内 webp 条目增量 +71 恰好对上）：.49 已转好的 57 张逐张与源 PNG 比对核验后复用（序号映射 invite_01=第 0 帧起、clap_open=张开帧，均实测对位；有损 quality≈90 级），弯腰 14 张本版按同口径补转（PIL quality=92）。原始 PNG 只留在 dancers/ 目录，不进 APK（aapt 资源表核对：amy_hannah 11 项、bow 14 项、invite 42 项、clap 14 项齐全）。
- APK：33,309,460 B（v1.0.48 为 31,185,618 B，+2,123,842 B ≈ +2.0MB，全是新动画帧）。

### 五、测试（409 = 403 基线 − 9 + 8 + 7，0 失败）

- 新增 InviteCycleTest（7 个）：链形状钉值（CHAIN=[0..7,7]、250ms、全周期 = INVITE_PERIOD_MS）；帧序全程（出手势→半弯→弯到位连占 3 步→往返回收→4s 回到静止）；仪式 2s 段恰好到达弯腰顶点；blend 在链邻居间淡化、弯腰顶点 previous==current 无幻影淡化；瓦力 blendFor 恒 null、人类恒非 null；ClapCycle 375ms 交替钉值；素材完整性（六位伴舞各 6+2 帧链、五人排构成与顺序、组合无 Dancer 记录、点歌名单仍是六位）。
- SpotlightGeomTest 排一节重写（8 个）：五人 pitch/跨度钉值（0.52 / 2.86）；四对间隙依据钉值（只许最宽一对交叠 ≤0.04、第二对相切 ±0.005、其余 ≥+0.10）；电视/手机/4:3/超宽四场景生效身高钉值（539.6≈0.500 / 238.4=0.580 / 285.8≈0.372 / 目标生效时 left≈135.1）；「永不伸到面板下」不变量（8 种尺寸）；两端边距对称钉值（0.2000 vs 0.1975、差 ≤0.003、偏移恒 0）。
- DancerImageStructureTest（v1.0.42 的固定矩形钉值）原样通过：DancerImage 仍是一个固定宽 Box、三处 DancerFrame 调用，新增的 framesOverride/labelOverride 只是换了帧表与标签来源，矩形与双层淡化结构未动。

### 六、交付与待验

- APK：`MySing-1.0.50.apk`，33,309,460 B，SHA-256 `c4d7828ff83adcc1fff0eb687ff51e378e987f67920c41ed21bcb1068b0489cf`，versionCode 50，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 前奏邀请是完整动画——伸手做「请」再弯腰致意，瓦力是一段段的机械感；② 仪式群邀/keeper 独邀同样弯腰，2 秒段恰好弯到位；③ 六人排变五人排，Amy 抱着汉娜站第一位，鼓掌是真拍手（手张开/拍合交替）；④ 电视上五人约屏高一半、手机上到 0.58，瓦力不被面板挡、两边留白对称；⑤ 汉娜被点中 solo 时仍是她自己跳、自己邀请。

## v1.0.51 — 已点歌单面板瘦身 + 后奏鼓掌 + 电视点歌选伴舞（2026-11-16）

用户一句话定的三件事：「正在唱和下一首别出现在已点歌单里，唱完3秒，如果后奏没结束，就让全部人出来鼓掌，电视点歌也可以选伴舞」。三项都是存量机制上的改法，无新素材、无新权限。

### 一、电视「已点歌单」面板只列下一首之后的歌（QueuePanelModel 重写）

- 面板行模型改为只吃等待列表：正在唱的那首（原 ▶ 行）和队列第一首（下一首）都不再出现——正在唱有歌词上方的「正在唱」行、下一首有横幅/仪式大卡片在报，面板只管还排在下一首后面的歌。行序号在面板内从 1 重新编，但每行携带的 waitingIndex 仍是队列真索引，遥控三动作（现在唱/下一首唱/删除）逻辑与落点不变。
- 可见性改为「等待列表 ≥2 首才出现」：只剩下一首或一首不剩时面板整体不显示，不留只剩标题的空壳（旧的「暂无排队」页脚随之退役，字符串保留在表中未删）。面板消失时一并清掉它的焦点簿记（panelFocused/actionIndex），免得残留标志吞掉舞台的 Back。
- 焦点落点随之修正：控制条方向上进入面板第一行（取行模型的首行 waitingIndex，不再写死 0）；「下一首唱」执行后被提的那首成为下一首、自动离开面板，焦点落到面板新的第一行（无行则回控制条）；删除后剩余不足 2 首时焦点回控制条，否则落在滑入原位的行。
- **手机点歌页完全不动**：它的队列来自 PlayQueue 本身（不经 QueuePanelModel），仍显示完整队列、仍能看到自己排第几并删除；点歌回执「前面还有 X 首」的计数口径也不动。

### 二、唱完 3 秒、后奏未结束：全员出来鼓掌（新 OutroClap）

- 触发判定拆成纯函数（ui/OutroClap.kt）：点唱的歌（ORDERED，填充不适用）、有 LRC、最后一句唱词结束时刻（沿用 DancerActivity.lastLyricEndMs 的现行口径：增强 LRC 的行尾结束标记，没有时用最后一句开始时刻）+3000ms 仍**严格早于**歌曲总时长 → 在该时刻舞台换成五人排全员鼓掌，陪后奏播完。恰好 3 秒后奏（+3s 与结束时刻重合）不触发；无 LRC、无时长、后奏更短都不触发；「不唱了」的歌走退出流程、条目被替换，天然到不了这一段。
- 计时只认**播放位置**不用墙钟：暂停时 positionMs 冻住，这 3 秒和鼓掌帧（帧时钟同样走 positionMs − 触发点）一起冻住，恢复播放才继续。
- 画面与歌结束后的流程共用同一个 CelebrationRow、同一几何（SpotlightGeom 的行高/左界/面板避让）：歌曲真正结束时，有下一首走仪式（第①段鼓掌即此排的延续）、无下一首走掌声送别，两种接管都不闪烁、不跳位、不重进场；区别只是鼓掌时钟从位置切回掌声墙钟，帧相位可能翻一帧（张开/拍合交替本身即如此）。原 solo 伴舞在全员出来时退场（条件与掌声送别同口径），排里有他本人，不出现两次。
- 此阶段**只有画面没有掌声录音**：掌声录音仍归歌曲结束后的仪式/送别，不和后奏音乐打架（playApplause 只在 onSongEndedNaturally 里调用，本段不碰）。

### 三、电视点歌也能选伴舞（新 TvDancerPickerDialog）

- 浏览页点中一首歌不再直接下单：先弹一步到位的伴舞选择（六位站立帧小图 + 名字卡片横排），焦点默认落在「电视」这个点歌人上次用的伴舞上（名字金色标出），OK 选定即点歌，Back/点外取消、不点歌；只是路过浏览不弹。选完的歌按现有规则出所选伴舞（舞台按点歌人查 Prefs 的既有路径）。
- 存储与手机页共用同一份 Prefs.dancerFor/setDancer（键 = 点歌人名）：电视写的是自己点歌人名（当前 UI 语言下的 电视/TV，与订单署名同名同源）那一条，手机上每个人的选择互不覆盖；手机页的选人逻辑与界面一行未动。
- 名字走既有 dancerDisplayName 解析：房主在后台改过的名、当前 UI 语言都即时生效；新增字符串仅两条（DANCER_PICK_TITLE / DANCER_PICK_HINT），中英双表齐全（StringTablesTest parity 钉住）。

### 四、测试（416 = 409 基线 − 7 + 6 + 8，0 失败）

- QueuePanelModelTest 按新契约重写（6 个）：下一首无行、其余从 1 重编且 waitingIndex 保持真索引；只有一首等待时无行且面板隐藏；空等待隐藏；恰好两首等待是面板出现的下限；canPromote/canPlayNow 规则原样钉住（旧契约的 7 个测试——▶ 行、填充行标记、只有正在唱也显示等——随行为退役删除）。
- 新增 OutroClapTest（8 个）：长后奏触发点 = 末句结束 +3000ms；恰好 3 秒不触发、3 秒多一毫秒触发；短后奏/歌词越界不触发；无 LRC、无时长（0/−1）不触发；活动窗 [触发点, 总时长) 两端边界；同一位置重复求值结果恒定（暂停不走字的构造性钉值）；DELAY_MS = 3000 钉值。
- 改动 3 无新增纯逻辑可测（预选即 Dancers.byKey 的既有回退，已有覆盖）；双语 parity 由 StringTablesTest 自动覆盖新键。

### 五、交付与待验

- APK：`MySing-1.0.51.apk`，33,309,456 B（与 v1.0.50 的 33,309,460 B 仅差 4 B——纯代码改动、无新素材），SHA-256 `276bf8b242141c318d62a6410576b9afe32b15308193e6d1c1f0f9a3758a0460`，versionCode 51，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 点两首以上的歌，看电视面板只剩下一首之后的歌、序号从 1 编，手机页队列照旧完整；② 唱一首后奏长的歌：最后唱词完 3 秒后五人出来鼓掌（无掌声声）、歌一结束仪式/送别无缝接上，暂停时鼓掌和倒计时一起冻住；③ 在电视浏览页点歌：先弹伴舞选择、上次选的人已被预选（金名+焦点），OK 点歌、Back 不点歌，唱起来出的是刚选的伴舞。

## v1.0.52 — 伴舞跟歌绑定：点歌那一刻定格，之后改选只对下一首生效（2026-11-16）

用户原话：「选好一首歌和舞伴后，这首歌的舞伴就不要再改变，然后再选下一首歌和舞伴，那么这个舞伴就配这首歌就能再变了」。一条规则变更：伴舞的绑定对象从「点歌人」改成「这首歌」。无新素材、无新权限。

### 一、旧机制（退役）

伴舞按点歌人名字存在 Prefs（dancerFor/setDancer），舞台、仪式 keeper、诊断行都在播放时按 `entry.orderedBy` 实时回查这份存储。后果正是用户这次否掉的两点：点歌人中途改选，正在唱的歌台上伴舞跟着换（KaraokeApp 的 dancerSetter 还专门 bump 一次状态去触发这次实时换人）；同一人早先点的歌轮到唱时，出的也是他最新选的那位，而不是点那首歌时选的那位。

### 二、新机制：QueueEntry.dancerKey 快照

- `QueueEntry` 新增 `dancerKey: String?`（不可变 val），`PlayQueue.add` / `KaraokeState.order` 新增同名可选参数：点歌那一刻把当时选中的伴舞冻进条目，条目一生（排队、正在唱、仪式 keeper）只读自己的快照。没带选择的条目（随机填充、无线索的点歌）快照为 null，读取时按既有 `Dancers.byKey` 回退默认 Amy——填充行为与 v1.0.51 完全一致。
- 三处读取全部改读快照：舞台 solo 伴舞、仪式 keeper（`Dancers.byKey(next.dancerKey)`）、播放器诊断行的舞者名。`MainActivity` 里不再有按点歌人查伴舞的播放路径。
- 中途换人随之取消：`KaraokeApp` 的 dancerSetter 去掉 `state.bump()`（那次 bump 的唯一目的就是触发台上实时换人），改选只写存储。两套语义不并存——旧回查代码与注释一并改写，没有留下任何按人解析的后门。

### 三、快照从哪来：点歌通道三处

- **手机页**：点歌请求体带上页面当时正选中的伴舞（`who:myDancer`）。服务端 `/api/order` 定格顺序：请求带了合法 `who` → 用它，并顺手刷新该名字的「上次选择」存储；没带或不合法（旧版页面）→ 回退到存储里该名字的上次选择；再没有就是 null（默认 Amy）。
- **电视点歌**：v1.0.51 的一步选择器选定后，`orderFromTv(song, dancer.key)` 把所选直接传进订单快照；`setDancer(电视, …)` 仍照写，但只作下次打开选择器时的预选记忆。
- **存储的定位变更**：Prefs 的按人存储从「绑定来源」降为「上次用的那位」预选记忆——手机页卡片的初始选中、电视选择器的焦点预选、手机间经 `/api/queue` 的 `dancers` 字段同步，都仍走它；播放侧再无一处读它。手机页与电视的选择 UI 形态一概未动。

### 四、文案

- 手机页伴舞提示（静态 HTML 与中英词典同步改）：旧文案承诺中途可换（「唱到你的歌时想换人，点一下电视上立刻换」/ "Tap anytime during your song to switch — the TV updates right away"）已删除；新文案中文「点歌时选的伴舞就配这首歌，定了就不变；想换一位，下一首歌再选。」英文 "The dancer you pick is locked to the song when you order it — it won't change afterwards. Pick a different one for your next song."
- 英文标题 "Pick your dancer" 改中性的 "Pick a dancer"（中文标题「选伴舞」本就中性，不动）。电视选择器的标题/提示（「选择伴舞」/「选好按 OK 就点这首歌」）本来就是跟歌措辞，不动。中英 parity 由既有测试覆盖。

### 五、测试（421 = 416 基线 + 5 新增，0 失败；无删除，2 个既有测试就地更新）

- 新增 PlayQueueTest（2 个）：快照冻入条目——先以相扑点 A、改选机器人再点 B，A 当前条目是相扑、B 是机器人，A 唱完 B 上台仍是机器人；无选择点歌的条目快照为 null。
- 新增 KaraokeStateTest（1 个）：状态层同场景钉值——两次点歌各带当时的选择，第二条不回写第一条，推进后上台条目仍带自己的快照。
- 新增 KaraokeHttpServerSocketTest（1 个）：真 socket + 真（内存）存储的全链路——先 /api/dancer 选相扑、无 `who` 点歌快照到相扑；中途改选机器人，已排条目纹丝不动；下一首点歌快照到机器人；另一人带 `who=baby` 点歌，快照为 baby 且其「上次选择」被刷新；`who` 不合法时回退存储选择。全程中文点歌人名，走真实 UTF-8 请求体。
- 新增 PhonePageTest（1 个）：新提示文案双语在页、旧「立刻换 / updates right away」措辞已绝迹。就地更新 2 个：`requestsCarryThePageLanguage` 的点歌请求体钉值加上 `who:myDancer`；`pageCarriesBothLanguagesOfText` 的英文标题钉值改 "Pick a dancer"。
- 旧语义没有专门的单测可删：中途换人当年活在 Activity/App 的组合代码里（按人回查 + bump），不在纯逻辑层；本次以新快照测试替代其契约，并在本节明示替换关系。

### 六、交付与待验

- APK：`MySing-1.0.52.apk`，33,309,458 B（与 v1.0.51 的 33,309,456 B 仅差 2 B——纯代码改动、无新素材），SHA-256 `45cf839ab701358f20223bd0f1b81996f75726f1b8675f3fdd81cdbd4d24763d`，versionCode 52，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 手机先选甲点一首 A，再改选乙点 B：A 唱起来出甲、B 出乙；② A 唱到一半在手机上改选丙，台上仍是甲（不再实时换人）；③ 电视点歌选一位，唱起来就是那位；下一首电视点歌时选择器仍预选上次那位；④ 没人点歌时的随机填充仍是 Amy。

## v1.0.53 — 哪吒起身加过渡；尾奏三段时序对齐：定格 2 秒 + 喘气 3 秒，全员 +5 秒出来（2026-11-16）

两项动作细节修正，都是用户在真机上看出来的：哪吒摔倒后一下弹回直立太突兀；后奏的节奏要分清「先定格、再喘气、然后全员才出来」。

### 一、哪吒起身：单段弹起 → 三段过渡（DanceMotion.BabyFall）

- **被取代的旧口径**：起身是单独一段 0.4 秒——二次曲线回正 + 反向过冲摆动（荡到 −14°）+ 放大弹跳（scale 到 1.09），读起来就是「啪一下弹回来」。
- **新口径**：起身拆成三段顺序衔接，总时长 0.90 秒（`RISE_TOTAL_SECONDS`），仍是纯变换纯函数（播放位置驱动、暂停冻结）：
  1. **撑起（PUSH，0.25 秒）**：像用手撑地爬起，倾角从躺平的 84° 平滑（smoothstep）收到半起的 37.5°（用户给的 35–40° 区间取中点，`RISE_HALF_DEG`）；
  2. **站直（STAND，0.35 秒）**：从 37.5° 平滑收到 0°，无过冲；
  3. **站稳回摆（SETTLE，0.30 秒）**：直立后一个 ±2° 量级的小回摆（`SETTLE_SWAY_DEG`，带 1−0.5v 衰减：一摆出去、荡回来时已更小），一下即止，结束恰好回正，然后接回跳舞。
- 细节：两段运动都以 smoothstep 缓动，段与段交界处速度为零，不顿挫；scale 全程 1.0（旧弹跳取消）；下沉量（sink）随倾角成比例回收（躺平 0.38 → 直立 0），起身过程连续离地。新增纯函数面：`RiseStage` 枚举 + `riseStage(progress)` / `riseStageAt(beats, bpm)`，供测试钉段界。
- **不变**：倒下 0.3 秒、躺 1 拍、每 12–20 拍伪随机的触发节奏、不出现的场合（鼓掌/静止/邀请/OUTRO_HOLD 期间从不开始摔倒）。

### 二、尾奏三段时序：定格保留，喘气定为 3 秒，全员触发点 +3s → +5s（OutroClap）

- 用户原话：「歌唱完3秒喘气，全部人才出来」。本版把尾奏明确成三段顺序衔接（点唱的歌）：
  1. 最后一句唱词结束 → **定格最后一个舞姿 2 秒**——v1.0.22 的 OUTRO_HOLD **原样保留**（本版一度拟取消定格，用户改口后作罢；`DancerActivity` 的相位机、`outroHoldPose`、0.4 秒融回站立帧及其全部测试口径一概未动）；
  2. 然后转站立 **喘气 3 秒**（STILL + 呼吸，新增常量 `OutroClap.BREATH_MS = 3000`）——用户说的「3 秒」就是这一段喘气；
  3. 喘气满 3 秒、即最后一句结束 **+5 秒** 时，若后奏还没结束 → 五人排出来鼓掌陪后奏。
- 因此 `OutroClap.DELAY_MS` 由 v1.0.51 的 3,000 改为 `DancerActivity.OUTRO_HOLD_MS + BREATH_MS` = 5,000：.51 的 +3s 是从唱词结束直接起算（那时喘气段还没有自己的定时），本版把定格与喘气分开计数，触发点对齐到 +5s。
- **其余口径全不变**：触发仍须严格早于歌曲总时长（后奏恰好 5 秒不触发）；计时只走播放位置、暂停冻结；3–5 秒的短后奏由结束流程（仪式/送别）接管，不重复进场；随机填充仍无全员鼓掌；后奏鼓掌段仍只有画面、没有掌声录音。MainActivity 侧仅注释口径同步（3+ s → 定格+喘气共 5s），渲染与接管逻辑未动。

### 三、测试（428 = 421 基线 + 净增 7，0 失败）

- BabyFallTest：删除旧测试 `risingOvershootsAndDancingResumes`（其断言的过冲正是被取代的行为），新增 6 个钉新口径——三段时长相加 = 总时长 0.90 秒且周期长度按新起身时长核算（120 BPM 下起身 = 1.8 拍）；段界进度点（0.25/0.90、0.60/0.90 两侧）分属 PUSH/STAND/SETTLE；三段按序出现且之后恢复跳舞；撑起/站直交界处倾角 = 37.5°（±1°）且 sink 按比例约半；全程无过冲（倾角 ≥ −2.01°）、scale 恒 1.0、无 bounce；回摆一峰一谷都在 ±2° 内、谷比峰浅（衰减）、段末回正（< 0.1°）。
- OutroClapTest：既有触发类测试全部按 +5s 改写（长后奏触发点 185,000；恰好 5 秒不触发；5 秒多一线触发；活动窗改到 185,000 起），并新增 2 个——旧 +3s 触发点不再触发（3–5 秒后奏不触发、+3s 时刻 isActive 为假）；`delay is the hold plus the breathing` 钉住 DELAY = OUTRO_HOLD_MS + BREATH_MS = 5,000；`the three tail stages run hold, breathing, then the row` 把三段与 `DancerActivity.phase` 对钉：+0–2s 是 OUTRO_HOLD（定格舞姿）、+2–5s 是 STILL（站立呼吸）、+5s 起 OutroClap 才 active。
- DancerActivityTest 未动——定格口径保留的直接证据。

### 四、交付与待验

- APK：`MySing-1.0.53.apk`，33,309,481 B（与 v1.0.52 的 33,309,458 B 仅差 23 B——纯代码改动、无新素材），SHA-256 `28812e14b07cc452131b313599415d5b32525691594a839a35bbd1f9fbc00158`，versionCode 53，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：① 盯哪吒跳舞：摔倒后先撑起到半起、再站直、最后小晃一下站稳，不再一下弹回；② 唱一首后奏长的点唱歌：唱完先定格最后一个舞姿 2 秒 → 站着喘气 3 秒 → 五个人才出来鼓掌陪后奏播完；③ 后奏短于 5 秒的歌：全员不出来，结束流程照常；④ 随机填充的歌唱完仍是 solo 定格+喘气，没有全员鼓掌。

## v1.0.54 — 哪吒起身加「蹲下」一段：半撑 → 蹲下 → 起身 → 站稳（2026-11-16）

只改哪吒起身动作的中间一段。用户看过 v1.0.53 的三段起身后说：「哪吒半撑，蹲下，再起身比较自然」——半撑之后直接站直还是不自然，中间要先蹲下再起身。

### 一、起身三段 → 四段（DanceMotion.BabyFall）

- **相对 .53 的变化**：本版在 .53 三段（撑起 → 站直 → 站稳回摆）的基础上，于半撑与站直之间插入一段「蹲下」；起身总时长由 0.90 秒变 1.20 秒（`RISE_TOTAL_SECONDS`），四段顺序衔接：
  1. **半撑（PUSH，0.25 秒，沿用 .53）**：手撑地半起，倾角 84° → 37.5°（`RISE_HALF_DEG`），smoothstep 缓动；
  2. **蹲下（CROUCH，0.30 秒，新增）**：倾角 37.5° → 0°（身子回正）的同时身体下蹲压缩——`Pose` 新增 Y 向挤压字段 `scaleY`（默认 1.0，仅本段使用），绕底部中心锚点（渲染层 transformOrigin 0.5/0.95）压到 `CROUCH_SCALE_Y = 0.84`，脚不离地；下沉量（sink）保持半撑末端的值（按 37.5° 比例的约 0.17）不回收——视觉上是「收腿蹲住」，不是站直；
  3. **起身（STAND，0.35 秒）**：从蹲姿伸直——scaleY 0.84 → 1.0、沉降随之回 0，倾角保持 0°；
  4. **站稳回摆（SETTLE，0.30 秒，沿用 .53）**：±2° 衰减一摆、段末回正。
- 细节：各段衔接全走 smoothstep（段界速度为零、不顿挫）；全程统一 scale（scaleX）不变、无过冲倾角（≥ −2.01° 的红线继续成立，回摆段口径同 .53）；仍是播放位置驱动的纯函数、暂停冻结。`RiseStage` 枚举新增 `CROUCH`，`riseStage(progress)` / `riseStageAt(beats, bpm)` 段界随之更新。
- 消费侧接线：渲染层 `DancerFrame` 的 `scaleY = pose.scale * pose.scaleY`（其余伴舞 scaleY 恒 1.0，画面零变化）；`DancerActivity.outroHoldPose` 的融回补上 scaleY 一项（万一哪吒在起身途中被最后一句结束「冻住」，解压与其它分量同口径融回，不会跳）。
- **不变**：倒下 0.3 秒、躺 1 拍、每 12–20 拍伪随机触发节奏、不摔场合（鼓掌/静止/邀请/OUTRO_HOLD）；尾奏三段时序（定格 2 秒 + 喘气 3 秒 + 全员 +5 秒）及其它全部功能未动。

### 二、测试（429 = 428 基线 + 净增 1，0 失败）

- BabyFallTest 就地更新 4 个：四段时长相加 = 总时长 1.20 秒且周期长度按新起身时长核算（120 BPM 下起身 = 2.4 拍）；段界进度点两侧分属 PUSH/CROUCH/STAND/SETTLE；四段按序出现且之后恢复跳舞；回摆测试的段起点按 push+crouch+stand = 0.90 秒（1.8 拍）重算。原「撑起/站直交界」测试更名为「撑起/蹲下交界」，断言不变（交界两侧仍是 37.5° 半撑、sink 约半）。
- 新增 1 个 `crouchBottomsOutFoldedAndTheStandRestoresFullHeight`：蹲下/起身交界两侧倾角 ≈ 0°、scaleY ≈ 0.84（全起身过程的最深挤压，扫描全程取最小值钉住）、sink 仍保持半撑值（是蹲住、不是站直）；起身段末 scaleY 回 1.0、sink 回 0；全程 scaleY 不超过 1.0（不拉伸过头）。

### 三、交付与待验

- APK：`MySing-1.0.54.apk`，33,309,472 B（与 v1.0.53 的 33,309,481 B 仅差 −9 B——纯代码改动、无新素材），SHA-256 `7e831a15548de3ab4fadd141d180de43d9056abf8bc23d4b1ca4502bebfc58c5`，versionCode 54，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：盯哪吒跳舞等他摔倒——半撑起来后先蹲下（个子明显矮一截、脚不离地），再伸直站起，最后小晃站稳；整段比 .53 多 0.3 秒，看是否如他所说更自然。

## v1.0.55 — 哪吒摔倒→起身改完整帧序小动画：11 帧全绘制、变换退役（2026-11-16）

用户看 v1.0.54 后先指出压扁蹲「还是没有半蹲这个动作」，随后把整段定性为「动作不补全、一闪一闪不自然」——几张关键帧 + 变换硬换读作闪图。本版按他的最终口径重做：整条链是一支**完整帧序列小动画**，11 帧全由绘制帧承载，每帧一步 0.13 秒、帧间只留步长前 30%（约 39 毫秒）的短 crossfade；摔倒的旋转变换、起身的 PUSH/STAND 变换段、scaleY 压扁蹲全部退役，位姿全程中性（唯一保留的变换是落稳时站立帧上的 ±2° 衰减回摆——点缀，不是姿势承载）。素材经历三轮追加才定稿：先有半撑/蹲两张真帧，再补真躺帧（用户：「倒地图不像」），再补翻身帧 + 倾倒帧，最后在撑起前插入备选帧补上全链最大的一跳；发布一直等到 11 帧全部到位才收尾。

### 一、11 帧链与时序（DanceMotion.BabyFall 帧序播放器）

- **最终链**（链槽位 0–10）：站立（摔倒开始瞬间冻住的那张舞姿帧）→ 倾倒 `nezha_lean` → 翻过来 `nezha_flip` → 躺 `nezha_lying`（停 1 拍）→ 翻身撑起前 `nezha_prepush` → 备撑 `nezha_prepush_alt`（俯卧双肘撑起，补「侧躺翘腿 → 正面蹲」这一跳）→ 半撑 `nezha_crouch_ground` → 撑起中 `nezha_pushmid` → 蹲 `nezha_crouch_knees` → 起身中 `nezha_risemid` → 站立 + 站稳回摆。
- **每帧时长与总时长**：每步 `STEP_SECONDS = 0.13` 秒（用户定的 110–150 毫秒量级正中），每步的入场淡化占步长前 `STEP_FADE_FRACTION = 0.30`（约 39 毫秒，沿用 PoseCycle.Blend 的形状——帧够密时读作动画而不是闪图）；摔倒 3 步 = **0.39 秒**；躺 = **1 拍**（随歌速，躺帧的入场淡化同样按 39 毫秒以秒计时，挂在 DOWN 相位开头）；起身 6 步 = 0.78 秒 + 站立步（回摆窗）0.30 秒 = **1.08 秒**。整支小动画（不含躺的 1 拍）共 1.47 秒。
- **播放器实现**：纯函数 `stepBlend(frames, beforeFirst, elapsed, span)` 按已播放秒数选步并给出 Blend 对（上一步帧 → 本步帧 + 淡化进度），`fallChainBlend` / `riseChainBlend` / `chainBlend`（DOWN 分支）三处共用同一口径；仍是播放位置驱动、暂停冻结，与 PoseCycle/InviteCycle 同一模式。`chainBlend` 签名加 `bpm`（只为 DOWN 的秒计时淡化）。链槽位常量 CHAIN_FROM/LEAN/FLIP/LYING/PREPUSH/PREPUSH_ALT/HALF_PUSH/PUSHMID/CROUCH/RISEMID/STAND 共 11 个，`Dancer.fallChainFrames` 挂 9 张绘制帧（槽位 1–9）。
- **退役清单**：`Pose.scaleY` 字段及渲染层、尾奏定格融回里的全部 scaleY 逻辑；起身变换段 `RiseStage`/`riseStage`/`riseStageAt` 与 RISE_PUSH/CROUCH/STAND/SETTLE 时长常量（由 STEP 口径取代，SETTLE 只剩回摆窗 SETTLE_SECONDS）；摔倒旋转（FALLING 位姿改中性）及其 sink 曲线、DOWN 的旋转躺姿；本版草稿期一度试过的「倾倒旋转 + 形态渐变 + 脚底锚点 0.98」方案随帧序定稿一并撤销——渲染锚点回到 0.95 的全场口径，脚着地由倾倒帧的画本身保证（画里脚钉地）。
- **尾奏定格防护（等价处理）**：融回不再需要融 scaleY，改为融**帧**——若最后一句结束正好冻住起身/摔倒途中，定格显示冻住那一刻的链帧（在同一 fallStart 下求值），STILL 喘气窗内再把这张冻链帧融回站立帧（链内的 CHAIN_STAND 槽位），与 .54 融 scaleY 同思路，不跳帧。
- **不变**：触发节奏（每 12–20 拍伪随机）、不摔场合（鼓掌/静止/邀请/OUTRO_HOLD）、只有哪吒摔、其他伴舞零影响；尾奏三段时序、伴舞跟歌绑定、队列面板、仪式、伴奏配对、语音搜索、五人排与邀请帧链全部未动。

### 二、素材实测（9 张绘制帧，全 468×600 RGBA，转 webp q92 进包、alpha 包围盒逐帧保真）

| 帧 | 图形范围（列×行 px） | 像高 | 备注 |
|---|---|---|---|
| lean 倾倒 | 208×228（行 362–590） | — | 脚钉地在画里 |
| flip 翻 | 243×184（行 407–591） | — | 蜷身侧翻中间态 |
| lying 躺 | 256×144（行 446–590） | — | 身体下缘贴基线，DOWN 位姿可完全中性 |
| prepush 撑前 | 230×162（行 428–590） | — | 侧躺翘腿 |
| prepush_alt 备撑 | 230×162（行 428–590） | — | 与撑前同几何，俯卧双肘撑起 |
| crouch_ground 半撑 | 125×190（行 399–589） | 站立 0.74 | 手撑地、臀沉低 |
| pushmid 撑中 | 126×210（行 380–590） | — | 半撑与蹲之间 |
| crouch_knees 蹲 | 93×192（行 397–589） | 站立 0.75 | 手按膝深蹲 |
| risemid 起中 | 101×224（行 366–590） | — | 蹲与站立之间 |

- 9 帧 alpha 下缘落在 589–591 行，与既有舞姿帧的基线带（584–590）一致，无需任何位移补偿。APK 内 webp 总数 645（.54）→ 654，正好 +9；PNG 数 34 不变——原始 PNG 一张未进包。

### 三、测试（430 = 429 基线 + 净增 1，0 失败）

- 删除 5 个钉已退役变换的旧测试：`riseStagesAddUpToTheTotalRise`、`riseStageBoundariesFollowTheStageDurations`、`riseStagesRunInOrderAndDancingResumes`、撑/蹲交界倾角测试、`crouchBottomsOutFoldedAndTheStandRestoresFullHeight`（scaleY 断言随字段退役）；`downPoseIsFlatOnTheFloor` 更名改写为 `downPoseIsNeutralForTheDrawnLyingFrame`（DOWN 位姿全中性）。
- 新增 6 个帧序测试：`sequenceTimingAddsUp`（步长在 110–150 毫秒带内；摔 0.39 秒、起身 1.08 秒、周期长度按新时长核算）；`fallSequenceStepsThroughLeanAndFlip`（主导帧顺序 站立→倾倒→翻 + 两处半淡化点位）；`lyingFrameFadesInOverTheFlipThenLiesAlone`（DOWN 开头 39 毫秒内躺帧淡入、拍中段独立成帧、DANCING 无链）；`riseSequenceStepsThroughAllSixFramesInOrder`（躺→撑前→备撑→半撑→撑中→蹲→起中→站立全序扫描）；`sequencePosesAreNeutralExceptTheSettleSway`（摔/躺/起身六步全程位姿严格中性）；`chainBlendAtWalksTheWholeSequenceByPosition`（位置域全程走查：390 毫秒摔、500 毫秒躺、1080 毫秒起身的关键淡化点与链起点锚定、结束后回跳舞）。
- 就地更新：`settleSwayIsOneSmallDampedSwing`（回摆起点按六步 0.78 秒 = 1.56 拍重算）、`riseHasNoSnapAndNoScalePop`（旋转红线收紧为 ±回摆角——起身不再有任何大倾角）、`babyFallPhasesAreExemptFromTheTempoTaper`（150 BPM 下摔/起身仍按 0.39/1.08 秒折算拍数，豁免口径由「峰值倾角」改为「秒计时长」）。

### 四、交付与待验

- APK：`MySing-1.0.55.apk`，33,440,424 B（比 v1.0.54 的 33,309,472 B 大 130,952 B——9 张新绘制帧 webp），SHA-256 `97dea7054ec9f9e2d01b956032e84cb795e75f20122dc969dc6c061ee55447b6`，versionCode 55，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：盯哪吒跳舞等他摔一次——整段应是一支连贯小动画：站着→倾倒（脚不离地）→翻身→躺平停一拍→侧身撑前→俯卧肘撑→手撑地半撑→撑起中→蹲→起身中→站直小晃站稳；不再有旋转硬倒、不再有压扁蹲、不再一闪一闪跳帧；暂停时动画应冻在当前帧。

## v1.0.56 — 手机上排队中的歌开唱前可换伴舞 + 删除升降调（2026-11-16）

两件都是用户当晚的指令。其一：v1.0.52「伴舞跟歌绑定」放宽——点自己排队中的歌可以弹出选伴舞、开唱之前都可以换，队列里每首歌后面标「（伴舞：××）」。其二：升/降调他实测「似乎没用」，明确说删，整条功能连根删除。

### 一、排队中改伴舞（开唱前可换，开唱即冻结）

- **模型**：`QueueEntry.dancerKey` 由 val 改 var，唯一写路径是 `PlayQueue.changeWaitingDancer`——只搜等待列表。条目一旦成为 current（开唱，或被 `advance()` 推进 current 的仪式锁定瞬间——仪式锁定的下一首在队列里已经是 current、不在等待列表），就不可能被改到；请求若正中 current 本人，回报 `LOCKED`。结果四态：`CHANGED` / `NOT_YOURS`（歌在队列里、不是你的名）/ `LOCKED` / `NOT_FOUND`。
- **寻址与竞态**：身份是（songId，点歌人）二元组；手机传来的 index 只是同一人重复点同一首歌时的消歧提示，不是身份——队列在打开弹窗期间被删歌/切歌移位，仍按身份找到条目（有单测钉住移位场景）；被删、被「现在唱」切走等一律以服务端校验结果为准。
- **服务端**：新接口 `POST /api/queue-dancer` {name,index,id,who,lang}：成功 200 +「伴舞换好了」；别人的歌 403「只能换自己点的歌的伴舞」；已开唱/仪式锁定 409「已经开唱，不能换了」；已不在队列 404「这首歌已经不在队列里了」；名字空/伴舞键非法 400（沿用 DANCER_INVALID）。新文案 4 条进 ServerStrings 双语表（parity 测试钉住）。`/api/queue` 的 current 与 waiting 条目都带 `dancer`（null 快照按舞台同口径解析为默认 Amy），waiting 另带 `id` 供寻址。
- **手机页**：队列每行（含正在唱行）歌名后追加「（伴舞：××）」/「(Dancer: ××)」，名字走 CUSTOM_DANCER_NAMES 优先、页面语言字典兜底，与选伴舞卡片同名源。我点的等待行可点（光标提示），弹出换伴舞弹窗：六张卡（`data-pick`，与点歌选择器的 `data-who` 分开，互不触发）、预选该条目当前伴舞、取消键；选定即 POST，服务端回执（成功或拒绝原因）短暂显示在消息行并刷新队列。别人的行不弹窗。删歌按钮补了 stopPropagation，不再误触行点击。选伴舞区提示文案双语同步改版：「点歌时选的伴舞就配这首歌；开唱之前，点你排队里的歌可以换一位。」旧「定了就不变」措辞退役。
- **读取点天然跟随**：仪式 keeper 在仪式开始时读 `entry.dancerKey`、诊断行同理——等待期改完的值就是开唱时的值，无额外接线。电视点歌的选伴舞（点歌时一步）不变；电视面板不加改伴舞。
- **状态层**：`KaraokeState.changeWaitingDancer` 转发队列校验，仅 CHANGED 时 bump 版本 + 回调，TV 侧随之重读。

### 二、删除升降调（按用户指令删除的功能）

- 删除清单：控制条三键「降调 −」「调 %d」「升调 ＋」（MainActivity，连 `semitoneLabel` 状态与 startSong 里的每歌复位）；`PlayerController.shiftPitch` 与 `semitones` 状态，及全部 PlaybackParameters 应用点（load 的复位、错误重试的重新应用、换源时的重新应用——变调没了之后播放参数恒为默认，新建播放器天然默认，换源/重试不再触碰）；`AudioMath.semitoneFactor`；`TvStrings` 的 PITCH_DOWN/PITCH_LABEL/PITCH_UP 三键（中英两表同删，parity 测试证明）；FillerPicker 注释里对 pitch 的引用。控制条其余按钮顺序不变、三键位置不留空位，焦点链随之收拢。
- 测试删改：AudioMathTest 的 `semitone factors` 删除；StringTablesTest 里 PITCH_LABEL 的双语 spot check 删除（两处都在文件里留了一行退役注记）。

### 三、测试（438 = 430 基线 + 净增 8，0 失败）

- PlayQueueTest +6：等待中改成功且推进到 current 后读到新值；开唱后（advance 后）改被拒 LOCKED 且值不变；改别人的歌 NOT_YOURS；歌已删 NOT_FOUND；队列移位后按身份仍改中；同歌同人重复点时按 index 消歧只改被点中的那条。
- KaraokeStateTest +1：状态层改成功 + 版本 bump，advance 后同请求变 LOCKED、开唱值保持改后的值。
- KaraokeHttpServerSocketTest +1：真 socket + 真中文名全链路——点歌带 who 快照 → /api/queue 带 dancer/id → 本人改成功（回执「伴舞换好了」）→ 换名字改被拒 403（英文原因按请求语言）→ 非法 who 400 → advance 后改被拒 409「已经开唱，不能换了」→ 不存在的歌 404。
- PhonePageTest +1 新增、1 改写：队列行伴舞标记/弹窗/data-pick/接口接线与删歌 stopPropagation 钉住；提示文案测试改名改断言为新版措辞（旧「定了就不变」必须绝迹）。

### 四、交付与待验

- APK：`MySing-1.0.56.apk`，33,456,793 B（比 v1.0.55 的 33,440,424 B 大 16,369 B——纯代码与页面改动、无新素材），SHA-256 `ca3a8227bbcd27e69d12d790c5ab9ef3f8668274c0fc08a7131fb25e2806a944`，versionCode 56，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：手机连点两首 → 点自己排队中的那首 → 弹窗换一位伴舞 → 行尾「（伴舞：××）」随之变、开唱时出的就是新伴舞；歌开唱后再点改 → 提示「已经开唱，不能换了」；点别人的歌不弹窗；电视控制条上降调/调/升调三键已消失、其余按钮照常。

## v1.0.57 — 舞台装扮：光束 + 地板反光 + 星光（2026-11-16）

用户看效果图（`stage-mock/stage_mock_v1.png`）批准的全套：顶部光束、地板与倒影、星光，星光按他随后补的一句「慢慢地闪亮」定调。纯舞台外观，不动任何播放/队列/仪式逻辑。

### 一、顶部光束（播放舞台最底层）

- 根层新增 `StageBeamBackdrop`，仅 `Screen.PLAYER` 时在泡泡层之下合成——字面意义的舞台最底层，泡泡/歌词/伴舞/面板全在它之上。静态 Canvas，无状态无时钟。
- 几何在纯模型 `StageBeams`（按效果图量出）：左右两角各一道，顶部窄口（0.07–0.19 宽）向下扇开（0.21–0.44 宽）落到 0.86 高；右束是左束的镜像。画法：多边形横向渐变（边缘透空、芯部暖白峰值 alpha 仅 0.16）+ 同路径裁剪的背景色纵向熔接（到底部熔掉 55%）——四边皆软，不用模糊（Shield API 30 无 RenderEffect，与品牌 logo 光晕同理）。

### 二、地板与倒影（每位伴舞脚下）

- **地板带**：舞台底部一条横带（深蓝提亮一档 + 顶部 2dp 高光线），深度 = 当前站在台上者的倒影深度。独唱时用独唱地板、五人排出场（庆祝/后奏鼓掌/整个仪式）用排地板——仪式全程一层地板，keeper 的倒影按排深度裁切，交接中途不换地板。带子是舞台第一个子节点，聚光池/倒影/人物都画在它之上；歌词带、面板、控制条一律未动。
- **倒影本体**：`DancerImage` 的图层栈只定义一次、画两次——本体照旧（v1.0.42 定宽画框原样保留，聚光池仍骑在画框上），第二画是同一栈在脚下节点的翻转：`StageFloor.reflectionScaleY/OriginFraction` 钉死的变换（脚精确落在基线、头沉到深度处，纵向压到 35%），再罩一层离屏 DstIn 渐隐（基线处 alpha 0.55 向下化为 0）。同一 painter 同一 pose 同一 blend——跳舞/邀请/鼓掌/哪吒帧序的当前帧天然被镜像，不新增任何解码；瓦力同样有倒影；仪式淡出时倒影随本体 alpha 一起淡。
- **尺寸代价（明示）**：倒影需要竖向空间，独唱的设计比例（0.80/0.72，邀请步进 0.95/0.88）从此量「地板之上的空间」——`StageFloor.restingHeightDp` 解 h = f×(H − 0.35h)：静止高度由舞台的 0.80 变 0.625（步进 0.742），但步进的增长比例（×1.1875）与所有相对关系不变，地板深度按静止高度定死、步进时只向上长、地板线不动。仪式 keeper 同公式，与独唱交接仍是同一人同一线。五人排几何（pitch 0.52 / span 2.86 / 高度公式）一字未动，只是容器向下长出一条地板深度、整排抬高到地板线上。

### 三、星光（与泡泡同层，全屏共用）

- 新 `Stars` 纯模型 + `StarBackground`，根层紧贴泡泡层之后合成，四屏共用同一片天。64 颗（紧凑 44），暖白小点（半径 0.7–1.7dp，带一圈淡晕）。
- 慢闪是钉死的契约：每颗独立相位、周期 3.5–9 秒的正弦明暗，`MIN_PERIOD_SEC = 3.5` 是防频闪下限（测试钉住常量本身 ≥ 3 秒、光滑度 100ms 内亮度变化 ≤ 峰值 10%、最暗仍留 30% 峰值不全灭）。与泡泡同一面墙钟驱动——暂停不冻天（泡泡的既有行为），不进播放状态机。

### 四、测试（459 = 438 基线 + 净增 21，0 失败）

- StageDressingTest +12：倒影 35% 与渐隐止点；静止高度恒等式（h = f×(H−depth)，400 区实测 250+87.5）与紧凑档同律；地板线在步进全程不动（heightAtFraction 与 resting 自洽）；步进后人物+地板仍装得下且增长比例不变；翻转变换的端点映射（脚→基线、头→深度，数值验算）；光束扇形/镜像/触顶达地与峰值亮度上限（≤0.20，保歌词可读）；层级顺序源钉（根层光束<泡泡<星光且光束仅 PLAYER、地板带在仪式分支之前、仪式/庆祝用排地板、独唱与 keeper 的倒影深度覆盖接线、五人排容器长出地板）。
- StarsTest +8：确定性与数量克制；全字段范围；周期下限硬钉（≥3s）；永不全灭、永不超过峰值；100ms 光滑度（防闪）；整周期呼吸幅度；全场散布与相位独立。
- DancerImageStructureTest 改写 1、新增 1（净 +1）：v1.0.42 的「一个定宽画框、双层同框」不变量保留，旧「恰一个 Box」钉随新形状退役——新钉：图层栈只定义一次（3 处 DancerFrame）、恰画两次（本体+倒影）、三盒（外壳/画框/倒影节点）、翻转与渐隐的接线与绘制顺序。

### 五、交付与待验

- APK：`MySing-1.0.57.apk`，33,456,780 B（比 v1.0.56 的 33,456,793 B 小 13 B——纯代码改动、无新素材），SHA-256 `9b34c6bfb07a52cbc5f77d06999135eb2cecd5437b73a6022a9984b204cfb6d0`，versionCode 57，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：舞台顶部两道光束是否够柔和、歌词是否照样清楚；独唱伴舞脚下倒影是否跟得上跳舞/邀请/哪吒倒地帧序、深浅是否合眼；五人排每人脚下都有倒影且整排站在地板线上；仪式 keeper 独邀时地板不跳；星光闪得够不够慢；独唱伴舞比以前小一号（0.80→0.625 的舞台占比，为倒影让出的空间）是否可接受——若嫌小，杠杆是减小倒影深度比例或整体比例再议。

## v1.0.58 — 平板播放器加固：降级粘住 + 第三档 BARE + 诊断保留 cause（2026-11-16）

病因来自平板 v1.0.55 的诊断行：custom 档起歌抛 `ERROR_CODE_FAILED_RUNTIME_CHECK` 后自愈到 plain-retry，但 `load()` 每首新歌都先回 custom 试错——这类设备上每首歌开头必先错一次。嫌疑排序（诊断已定）：①custom 的 AudioSink float 输出/AudioTrack 层；②两档共用的会话级 LoudnessEnhancer；③处理器本体（大体已自赦）。且 handleError 只存错误码名+消息首行，cause 链被丢弃——顶层消息只有一句没内容的 "Unexpected runtime error"，真正的根因无从看起。本版按诊断施工，不动 Plan B 处理器逻辑、不动 UI 读取方式、不动 .57 舞台装扮。

### 一、降级粘住（本次运行内）

- `FallbackStateMachine` 新增会话级 `startPath`（粘性地板）：`onError()` 每向下走一档，地板同步下移；`onNewLoad()` 改为回到地板而不是回 CUSTOM，并返回本首的起步档。健康设备整场留在 custom；平板第一首在 custom 错一次降到 plain 后，后续每首直接从 plain 起步，开头不再先错一次。
- FAILED 不是一种构建：BARE 也错后地板仍停在 BARE，下一首从最后一个真实构建起步，不再爬回已证明不行的两档重错一遍。
- `PlayerController` 记住活实例的构建档（`builtPath`），`load()` 只在起步档与活构建不同才重建；诊断行照旧读 `sinkLabel`，现在能看出当前档位（custom / plain-retry / bare / bare-failed）。

### 二、第三档「真全裸 BARE」

- 梯子变为 CUSTOM → PLAIN → BARE → FAILED。PLAIN 的构成一字未动（仍是 v1.0.40 的 stock `DefaultRenderersFactory` 裸构建——多样性教训钉在结构测试里）。
- BARE 的构成（结构测试钉死）：`BareRenderersFactory`——stock 渲染器工厂，唯一偏离是 sink 的 float 输出强制关（`.setEnableFloatOutput(false)`），不挂任何音频处理器；`applyGain` 在 BARE 档整体跳过 LoudnessEnhancer（load、错误重试、异步补测三个挂载点都过这一个函数，一处检查全档生效），增益只用 `player.volume` 做衰减半边（音量推子推不出增益，BARE 上增益段直接不施加）。
- PLAIN 再错时按第一错同样的姿势处理：重建为 BARE、seek 回出错位置续播；只有 BARE 也错才进 FAILED、错误留在屏上。`ErrorDisplayPolicy` 同步扩展：BARE 与 PLAIN 同为恢复档，确认在播且位置前进后横幅自动收起并锁定。

### 三、诊断保留 cause 链

- 新增纯工具 `PlaybackErrorText`：错误码名 + 顶层消息首行 + 逐级 cause（类名 + 消息首行，以 ` <- caused by ` 拼接），总长硬上限 480 字符（超出截断加省略号），cause 深度上限 8 且防自引用环。`handleError` 改走它——下次平板再出错，诊断行和错误横幅里能直接读到根因，不再只有一句 "Unexpected runtime error"。

### 四、测试（473 = 459 基线 + 净增 14，0 失败）

- FallbackStateMachineTest 改写并扩到 6：三段迁移全程（CUSTOM→PLAIN→BARE→FAILED 与各档 sink 标签含 bare / bare-failed）；降级粘住（错一次后 onNewLoad 停在 plain、再错停在 bare）；FAILED 后下一首从 BARE 起步且再错即 FAILED；健康运行永不离开 custom。
- PlaybackErrorTextTest +6：无 cause 时与旧格式逐字一致；多级 cause 顺序拼接；每条消息只取首行；消息缺失时退化为类名；总长硬上限；cause 成环不死循环。
- PlayerControllerStructureTest 改写并扩到 6：三档构成各就各位（custom 带处理器工厂 / plain 逐字保持 v1.0.40 裸构建 / bare 走 BareRenderersFactory）且恰三个 `ExoPlayer.Builder(` 调用点；BARE 工厂段内钉死 float 关、无 `setAudioProcessors`、不见 vocalProcessor；applyGain 的 BARE 分支排在任何 enhancer 构造之前；粘性接线（load 读 onNewLoad 返回值、按 builtPath 决定重建、错误处理 rebuildPlayer(fallback.path)、旧的 customSink 回爬字符串绝迹）；handleError 必须经 PlaybackErrorText 渲染。
- ErrorDisplayPolicyTest +1：BARE 恢复与 plain 同律（缓冲时显示、在播未推进仍显示、推进后收起并锁定、暂停不复活）。

### 五、交付与待验

- APK：`MySing-1.0.58.apk`，33,456,795 B（比 v1.0.57 的 33,456,780 B 大 15 B——纯代码改动、无新素材），SHA-256 `6adfca229439d6bd0800116f6965dd6ef04dd8970b007f4f58f6505abb9edf7f`，versionCode 58，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验（平板）：连点几首歌——第一首若在 custom 出错降级，此后每首开头不再先错一次（后台诊断行 sink 档位稳定停在 plain-retry 或 bare、不再每首跳回 custom）；歌能完整唱完；若再出错，诊断行错误文本带 cause 链，拍下来即知根因。MediaPlayer 第四档按计划留作后手（BARE 也死时再议），本版未实现。

## v1.0.59 — 舞台回位：地板去掉、独唱放大、五人排回底边、星光加大加多+随拍呼吸+左多右少；手机点歌页改版（2026-11-17）

用户在 v1.0.57 装扮上逐条点名回位（23:55–00:00）：地板那条白线不要、淡带也不要；独唱伴舞向上移放大；一齐鼓掌/一齐邀请的五人排再向下移、和以前一样；星光再大些、多些。随后追加两条（呼吸、分布）。手机点歌页按 23:37–23:38 亲口规格（已复述确认）改版。未动：.58 播放器三档梯子与诊断 cause 链、改伴舞规则与接口、伴奏配对、语音搜索、仪式/后奏时序、哪吒帧序、邀请帧链、光束层。

### 一、地板整块去掉

- `StageFloorBand` 组合函数及其常量（高光线色、淡带上下色）整体删除，MainActivity 舞台层不再画任何地板形状；倒影保留，仍以各人物脚底基线为锚、向下渐隐（DancerImage 内的镜像栈不变）。

### 二、独唱伴舞上移放大

- 高度律回到 .57 之前：`StageFloor.figureHeightDp(zone, fraction) = fraction × zone`——静止 0.80（紧凑 0.72）、邀请步进 0.95（紧凑 0.88）量的都是整个舞台区（.57 的「解税」公式 restingHeightDp/heightAtFractionDp 退役）。步进增长比 0.95/0.80、0.88/0.72 原样，由 figureHeightDp 直接钉死。
- 倒影让位：新纯函数 `soloReflectionDepthDp(zone, figure) = min(0.35 × figure, zone − figure)`，随人物当前高度走——静止时整个人 + 被压浅的倒影恰好填满舞台区；步进长高时倒影平滑压浅，人物一分不压；退化情形（人物 ≥ 区高）倒影为 0、永不为负。仪式 keeper 用同一对公式（同高、同倒影深），交接不跳。

### 三、五人排下移回原位

- CelebrationRow 恢复 .57 之前的单层 Box：容器高 = 人物高，人物 BottomStart 贴舞台底边，光池照旧溢出下沿；.57 为地板带长的那一段深度删除。三处共用点（庆祝/后奏行、仪式三段行分支）给 DancerImage 传新参数 `reflectionOverflow = true`：包装层只量人物高，倒影节点画出下沿之外、不裁剪（与光池同机制）——排回到底边，倒影跟着排走。

### 四、星光：加大加多 + 随拍呼吸 + 左多右少

- 数量 64→108（紧凑 44→72），半径 0.7–1.7dp → 0.9–2.6dp。逐颗慢闪一字未动：周期 3.5–9s、`MIN_PERIOD_SEC = 3.5` 钉子不松、最暗 30% 底光、正弦律动。
- 随拍呼吸（追加条 a）：新纯函数 `Stars.breathMultiplier(positionMs, bpm)` = 1 + 0.18 × (raised-cosine)²，相位 0 恰在拍点（峰值）、半拍回到 1，逐拍重复、跨拍连续。StarBackground 把每颗星的 halo 与星点 alpha 同乘这个系数——全场统一一层起伏，不是逐颗闪。接线在根层：仅当播放屏且有在播歌曲时把 positionMs + currentBpm 递入，其余屏/空闲为 null → 系数恒 1。呼吸跟播放时钟走：暂停时 positionMs 冻结、呼吸随之冻结，逐颗慢闪（墙钟）照旧。
- 分布左多右少（追加条 b）：`generate()` 按三区发牌——左区 [0, 0.36) 占 55%、歌词区 [0.36, 0.80) 占 15%、右区 [0.80, 1) 占 30%；左界即 `LyricsBand.DANCER_FRACTION`（同一套歌词带几何）。按单位宽度算，歌词区密度不足左区的四分之一。

### 五、手机点歌页改版

- 版面顺序：已点歌单 → 选伴舞 → 搜索栏 → 筛选按钮 → 歌曲列表（选伴舞块整块上移，内容与文案不变）。
- 组合筛选即出歌：页面只取一次全量歌单（loadCatalog），列表改由本地纯谓词 `filterSongs`（FILTER-BEGIN/END 标记段）渲染——语言 × 性别 × 模式（全部/收藏/最近）× 搜索词同时生效，点亮组合（如中文+男歌手）下方直接列出全部符合歌曲；旧的每次点按回服务端取数与 300 首上限（`slice(0,300)`）删除（中文男一类就有约 380 首，旧上限会截断）。收藏 = 与当前语言/性别求交；最近唱过保持唱过的顺序再求交。
- 高亮说真话：新增 `paintChips()` 按当前 lang/gender/mode 统一重绘全部按钮（含收藏/最近）；收藏/最近改为开关（再点一次回全部）；在搜索框打字不再偷偷把 mode 重置为 all。
- 搜索联想保持不变：SUGGEST 段与 suggestFor 一字未动，tools/check_suggest.js 仍全过；新增 tools/check_filter.js 把 FILTER 段从出厂源码逐字抽出，用真实中文夹具（张信哲/周杰伦/凤凰传奇/王菲/Adele + 400 首中文男无截断）跑通 13 项。

### 六、测试（480 = 473 基线 + 净增 7，0 失败）

- StageDressingTest（11）：退役 .57 的解税/地板带三钉；新钉 figureHeightDp 全量程（0.80×400=320）、倒影让位三点（静止压浅恰填满/紧凑全额/步进只剩 20）+ 全包络 figure+倒影 ≤ 区高、步进增长比、地板带绝迹（源码级）、solo 与 keeper 共用让位公式恰两处调用、行四处 reflectionOverflow、行容器回到纯人物高。
- StarsTest（10）：数量/半径新下限；分布钉（左 ≥ 半数、歌词区 ≤ 五分之一、密度比、zoneCounts 精确发牌 55/15/30、左界 = DANCER_FRACTION）；呼吸三钉（幅度上限 0.18 且全 BPM/全程不越界、峰值恰在拍点且半拍回落 + 跨拍连续、纯函数暂停冻结 + 无歌恒 1）。MIN_PERIOD_SEC 钉保留。
- PhonePageTest（20）：新钉版面顺序（歌单 < 伴舞 < 搜索 < 筛选 < 列表）、FILTER 标记与谓词签名 + 本地渲染 + 无 300 上限 + 无旧取数 URL、高亮真话（paintChips / 收藏最近开关 / 打字不重置 mode）。双语文案 parity 钉（StringTablesTest 与既有 parity 测试）全过，文案零改动。

### 七、交付与待验

- APK：`MySing-1.0.59.apk`，33,456,792 B（比 v1.0.58 的 33,456,795 B 小 3 B——纯代码改动、无新素材），SHA-256 `724c52c0e175c17924f665bea37e12fe5a2eaa60b576b054bc6258a1ec8414fc`，versionCode 59，签名证书 SHA-256 `1336ee0a…`（共享 keystore，apksigner 实测）。
- 设备侧待用户验：①舞台底部无白线无淡带、倒影还在；②独唱伴舞明显变大、上移，邀请步进长高时无跳变；③鼓掌/邀请五人排贴回舞台底边、倒影跟着；④星星更大更多、左侧密歌词后稀，唱歌时全场星光随拍轻轻一亮一落、暂停时呼吸停住；⑤手机页新顺序，点中文+男歌手直接出全部中文男歌曲，收藏/最近与语言性别组合生效，输首字联想照旧。
