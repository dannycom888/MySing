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
