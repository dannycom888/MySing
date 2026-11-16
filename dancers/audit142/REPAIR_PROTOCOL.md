# v1.0.42 修复协议（主线亲验坐实后下发，修复代理必须逐条遵守）

## 总原则
1. **源图重抠优先**：缺陷帧的原始绘画都在分格源图里。先在源图里找到同一人物（姿势与当前缺陷帧一致），单人重抠恢复原图——零造型漂移。源图里该人物本身残缺（被邻人遮挡/生成时就画坏/画出画布）才走 media 单图重画（fix137 方式），且必须在回报中标明 regen。
2. **只修下发的帧**，不许动其他帧、其他舞者、任何代码/测试/舞步链。
3. 每帧修完必须自验：全帧检查板 + 缺陷部位放大 + 与舞步链前后步并排（舞步链顺序在 `app/src/main/java/com/dannycom888/shieldkaraoke/dancer/DancerPoseOrder.kt`，按帧下标查 tour 邻居）。没自验过关不许报修好。

## 源图在哪
- 帧 1–40 代：`dancers/poses/media-generation-poses-<dancer>-<a..h>-*.webp`（每张 5 人）
- 后续代：`dancers/poses/extra40/sheet_<key>_X<n>.png`、`dancers/poses/extra60/` 内分格图
- 静止/邀请图：母版在 `dancers/poses/frames_png/dancer_<key>.png` / `dancer_<key>_invite.png`（母版=当前出货图本身）；原始生成图在 `dancers/` 根目录的 `media-generation-*` 文件里找。
- 补间帧（61–100）源：`dancers/inbetween/<key>/after_*.webp`。
- 找图方法：缺陷帧的姿势就是路标——在该舞者的全部源图里逐张比对姿势，中的那个人物就是源。找不到就回报主线，不许猜。

## 重抠方法
1. 从源图裁出单人区域（尽量避开邻人重叠带），rembg u2net 抠图（环境参考 `dancers/` 下既有脚本；`dancers/poses/.venv-cutout` 若在可用，没有就按既有脚本的方式建）。
2. alpha 收紧：`a = clip((a-0.10)/0.75, 0, 1)`（compose137 口径）；去掉不属于本人物的连通块（邻人碎片）；本人物的 legit 分离小件（如独立的鞋尖若与主体断开但位置正确）保留判断要写进回报。
3. 合成（关键，防漂移）：
   - 画布 = 当前出货帧的**确切尺寸**（WxH 不变）。
   - 内容高 = 该舞者**健康帧内容高的中位数**（自己从该舞者未下发帧量取；缺陷帧本身缺头/缺腿时其高度作废，严禁用它定标）。
   - 基线：内容底边距画布底 12px（全项目统一口径）。
   - 水平：先按内容 bbox 居中放，再做骨盆锚点对齐（下条）。
4. **骨盆锚点对齐（v1.0.41 口径，必做）**：锚点 = 内容 bbox 纵向 55–75% 带内 alpha>16 像素的 alpha 加权质心 x 相对画布中心的偏移（px）。`shift = round((中位数 − 本帧锚点) × 0.65)`，正 shift 右移；shift 被本帧左右透明边距钳制（不许裁到身体）。各舞者中位数（px）：modern +0.8078、ancient −3.9015、sumo −1.4707、ancient_girl −2.0490、baby −2.8157、robot −0.3143。实现可直接复用 `dancers/align_frames_v1041.py` 的 pelvis_and_bbox / shift_image 函数。
5. 编码 WebP quality=95, method=4（v1.0.41 出货口径）；同步镜像母版：帧 ≤60 写 `dancers/poses/frames_png/dancer_<key>_<NN>.png`；静止/邀请图写对应 frames_png 母版 PNG。出货路径 `app/src/main/res/drawable-nodpi/dancer_<key>_<NN>.webp`（静止图是 .png、邀请图是 .webp，照现有文件名）。

## 擦除/局部修复方法（残留类）
- 在出货文件 + 母版上同步操作，像素级：独立碎片先验连通性（与主体不连通才整块删）；贴轮廓/在身体上的污迹按局部 alpha 手术（参考 v1.0.39 相扑 _32 的轮廓手术法：沿轮廓逐段处理，禁止大面积涂抹）。
- 身体上的半透明污斑若源图可查，以源图对应区域为准重抠该区域贴回；实在不行才局部 alpha 恢复（把被污染的半透明像素按周围健康像素的 alpha 分布恢复），回报中写明手法。
- 擦除类不动骨盆、不重排画布（原位修），不需要锚点位移；但若动了内容 bbox 左右极值，回报中注明。

## 禁止
- 不许改 DancerPoseOrder / 测试 / 版本号 / 任何代码。
- 不许 git commit / build / release（主线统一做）。
- 不许为了"修好"而换姿势、换造型、换服装配色；重画时 prompt 必须带该舞者的定版造型描述 + 同姿势要求，并附设计定版图作参考（`dancers/` 下 media-generation 设计图）。
- 天线（robot）：有旋钮/短柱底座的算完整变体，不许动 06/10/12/14。

## 回报格式（每帧一行 + 附件路径）
帧号 | 手法（recut/erase/regen）| 源图文件 | 锚点/位移（recut 必填）| 自验板路径（/tmp/fix142/<key>/…）| 备注（如实写未决问题）
汇总行：处理 N 帧，recut x / erase y / regen z，失败或存疑逐条列明——**失败就报失败，不许粉饰**。
