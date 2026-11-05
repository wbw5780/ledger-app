# 账本（Ledger）— 安卓原生记账 App

按「项目」（一般是游戏，如 噜噜王国、海域）分别记账，支持导入微信账单（PDF / CSV）和支付宝账单（CSV）、手动记一笔，查看每个项目的总盈亏、累计盈亏曲线，以及全部项目合计的总曲线。账目数据完全保存在本机、不上传；唯一的联网行为是检查/下载更新（GitHub Releases）。

- 源码仓库：<https://github.com/wbw5780/ledger-app>，安装包下载：<https://github.com/wbw5780/ledger-app/releases/latest>

- 技术栈：Kotlin + Jetpack Compose (Material 3，自定义主题与组件)，单 Activity 自导航 + 底部 Tab；Room (SQLite)；图表为 Compose Canvas 手绘（无第三方图表库、无 Coil）；PDF 文本提取用 [pdfbox-android](https://github.com/TomRoush/PdfBox-Android) 2.0.27.0（Apache 2.0）
- minSdk 26（Android 8.0+），targetSdk / compileSdk 35；当前版本 1.3（versionCode 4）
- 包名：`com.ledger.app`，应用名：账本

## 安装（侧载）

1. 把 `ledger.apk`（release 版，已签名）传到手机：数据线复制、微信/QQ「文件传输助手」、网盘等都可以。
   - 也可以用电脑 adb：`adb install -r ledger.apk`
2. 在手机文件管理器中点击 `ledger.apk` 安装；首次安装时系统会提示「允许安装未知来源应用」，按提示为对应的文件管理器/微信开启即可。
3. 部分手机（如 MIUI / ColorOS）安装时会有「安全检测」，选择「继续安装」即可。
4. `ledger-debug.apk` 是调试版，与 release 版签名不同，二者不能互相覆盖安装（需先卸载）。

## 界面与使用

底部导航 3 个 Tab：

- **总览**：总盈亏卡（净额大数字 + 涨跌箭头 + 全部项目累计曲线）＋「我的项目」卡片列表（图标 + 名称 + 净盈亏）。
- **明细**：全部项目流水合集，按月倒序分组（月份标题行右侧为当月小计）；顶部 chips 可按项目筛选。
- **我的**：「新建项目」「记一笔」按钮、微信/支付宝账单导出步骤、当前版本与「检查更新」。

1. 新建项目：「总览」页「我的项目」标题右侧的「＋」，或「我的」页的「新建项目」大按钮。填写名称（必填），可添加多个「账单匹配关键词」（不填默认为项目名）。图标可「选择已装 App」（自动填入应用名称和图标）或「上传图片」。
2. 点项目卡片进入详情页（这里才显示收入/支出明细）→「导入账单」→ 选择账单文件（微信的 pdf 或 csv、支付宝的 csv；按文件内容自动识别格式，不看扩展名）→ 预览页中命中关键词（交易对方或商品名包含任一关键词）的记录会默认勾选，可全选/全不选/按关键词重新勾选 → 底部「导入到「项目」」，完成后提示「新增 N 条，跳过重复 M 条」。重复导入同一账单不会产生重复记录（按 来源+交易单号 去重）；同一时间段的微信 PDF 和 CSV 交易单号相同，先后各导入一次也不会重复。
3. 「总览」「明细」页右上角的笔形按钮、项目页「记一笔」都可手动记账；点击任一流水可编辑或删除。
4. 删除项目（项目页右上角垃圾桶）会弹确认，并一并删除该项目下的所有记录。

视觉：自定义翡翠绿品牌配色（亮/暗两套，跟随系统深色模式，不使用 Android 12+ 动态取色），收入绿 / 支出红，暖金点缀；edge-to-edge 透明状态栏/导航栏；自绘线性图标；启动图标为自适应图标（深绿底 + 白色账本与上升折线，含 Android 13 主题图标 monochrome 层），Android 12+ 启动画面自动使用该图标。

## 微信账单导出

微信 → 我 → 服务 → 钱包 → 账单 → 右上角「常见问题」→「下载账单」→ 用途选择「用于个人对账」→ 选择时间范围 → 填写接收邮箱。

稍后邮箱会收到账单文件，两种都支持：

- **新版本：PDF 文件**（如 `微信支付账单(20240301-20240331).pdf`）——直接把 PDF 传到手机，在 App 中导入即可。
- **旧版本：zip 压缩包**（解压密码在微信「微信支付」服务通知里），解压后得到 `微信支付账单(起始日期-终止日期).csv`，传到手机后导入。

> 如果 PDF 本身设置了打开密码，或者是扫描图片版（无文字层），App 会提示无法读取；直接选中 zip 会提示先解压。xlsx 请先用 WPS/Excel「另存为 CSV」。

## 支付宝账单导出

支付宝 → 我的 → 账单 → 右上角「…」→「开具交易流水明细」→ 用途选择「用于个人对账」→ 选择时间范围 → 填写邮箱。
邮箱收到 zip（解压密码在支付宝消息中），解压后得到 `alipay_record_xxxx.csv`，传到手机后在 App 中导入。

## 解析规则说明

- 格式按文件头魔数识别（不看扩展名）：`%PDF-` 开头 → PDF 解析；`PK` 开头（zip/xlsx）→ 提示先解压/另存；其余按文本 CSV 处理。
- 编码自动识别：BOM（UTF-8 / UTF-16LE / UTF-16BE）→ 严格 UTF-8 校验 → 失败按 GBK 解码。
- 表头不假设在第一行：逐行扫描，直到出现「收/支」且含「交易时间」/「交易号」/「交易创建时间」的行；之前的说明行与之后的尾部统计行自动跳过，不会崩溃。
- 按列名取值（不依赖固定列下标），兼容支付宝新旧版本列的增减。
- 微信：收/支 为「收入」「支出」以外（如 `/`）的行跳过；金额可带 `¥`；去重单号用「交易单号」。
- 支付宝：「不计收支」跳过；只导入交易状态含「成功」的行（交易成功/还款成功/退款成功等）；去重单号用「交易号」（新版为「交易订单号」）。
- 金额以「分」为单位用 Long 存储，无浮点误差。

### 微信 PDF 解析（防御式）

- 用 `PDDocument` + `PDFTextStripper`（按坐标排序）逐页提取文本；没有任何交易时间的页（封面「微信支付账单」、说明页）整页跳过。
- 以 `yyyy-MM-dd HH:mm:ss` 作为一条记录的起点（兼容日期、时间折成两行），收集到下一条时间为止。
- 记录内：方向 = 单独的「收入」/「支出」；金额 = `¥?\d+\.\d{2}`；交易单号 = ≥20 位纯数字（窄列折成两行的单号会自动拼接）→ 作为去重键，与 CSV 的「交易单号」完全一致。
- 交易类型 / 交易对方 / 商品 / 当前状态 按表头列序尽力填入（识别不到表头时按 交易时间、交易类型、交易对方、商品、收/支、金额、支付方式、当前状态、交易单号、商户单号、备注 的默认顺序），兼容"每格一行"和"整行一条"两种提取结果，以及「交易单号」在「交易时间」之前的列序。
- 页眉页脚（「微信支付」「账单明细」「第 X 页 共 Y 页」等）、重复表头丢弃；收/支为「/」「其他」或缺金额的记录整条跳过，任何异常只提示不崩溃。
- 解析结果与 CSV 进入同一个导入预览页，关键词自动勾选、去重逻辑不变。

## 从源码构建

```bash
# 需要 JDK 17、Android SDK（platforms;android-35、build-tools;35.0.0）
echo "sdk.dir=/opt/android-sdk" > local.properties
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:testDebugUnitTest   # CSV/PDF 解析器、金额、曲线单元测试（PDF 端到端测试需系统有 CJK TrueType 字体，否则自动跳过）
```

release 签名使用 `app/release.keystore`（别名 `ledger`，密码 `ledger123`，写在 `app/build.gradle.kts`），仅用于个人侧载。

**签名 keystore 保存在构建服务器**（`/root/ledger-app/app/release.keystore`），不提交到公开仓库（已在 `.gitignore` 中排除）。从 GitHub 克隆的源码没有 keystore：`assembleDebug` 可正常构建；`assembleRelease` 会产出未签名的 APK，且无法覆盖安装官方发布版（签名不同）。

## 自动更新

1.3 起 App 内置更新，**更新源只有 GitHub Releases**（仓库 `wbw5780/ledger-app`，公开仓库，匿名访问，不带任何 token），不使用任何镜像。

原理：
1. 启动进入主界面 2 秒后静默请求一次 `https://api.github.com/repos/wbw5780/ledger-app/releases/latest`（`Accept: application/vnd.github+json`，HttpURLConnection，超时 10s）。release 的 `tag_name` 是数字 versionCode，比本机 `longVersionCode` 大才弹窗「发现新版本 v<版本名>」，正文为 release notes；静默检查的任何失败都不提示。
2. 「我的」页「检查更新」可手动检查，状态依次显示为 检查中 / 已是最新 / 新版 vX / 检查失败点击重试；手动检查失败会 toast「检查更新失败，请检查网络（需能访问 GitHub）」。
3. 「立即更新」：直接下载资产 `ledger.apk` 的 `browser_download_url`（github.com 会 302 到 release-assets.githubusercontent.com，全程 HTTPS），流式写到 `cacheDir/updates/ledger.apk`，弹窗显示进度条、百分比、已下载/总 MB，可「取消」（终止下载并删除临时文件）。
4. 下载完成后校验文件大小必须等于 asset size（不符就删除并提示「下载不完整，请重试」），再检查包名和 versionCode 与 release 一致，然后通过 FileProvider（`com.ledger.app.fileprovider`）content URI 打开系统安装器。Android 8+ 首次安装需要允许「安装未知应用」：App 会先弹说明，点「去设置」跳到本应用的开关页，返回后自动重新拉起安装。
5. 新版用同一个 `app/release.keystore` 签名，所以可以直接覆盖安装，数据不会丢。

发布（构建服务器）：`/root/publish-ledger.sh "更新说明"`：用 aapt 读出 versionCode/versionName，删除同 tag 的旧 release，创建 `tag=versionCode`、`name=v<versionName>` 的 release，上传 `ledger.apk`，最后匿名请求 `releases/latest` 自检。脚本从 `~/.git-credentials` 读 token，token 只通过 stdin 交给 curl，不会出现在命令行参数或输出里。

## 已知限制

- **自动更新依赖直连 GitHub**：国内网络访问 api.github.com / github.com / release-assets.githubusercontent.com 经常很慢或失败，可能需要代理/VPN。连不上时只有手动「检查更新」会提示失败，App 其他功能不受影响。也可以到 Releases 页面手动下载 APK 安装。
- GitHub API 匿名请求每个 IP 每小时限 60 次，正常使用（启动查一次）不会触到上限。
- 公开仓库里没有签名 keystore（只保存在构建服务器上）。别人用仓库源码构建出的 APK 签名不同，无法覆盖安装发布版。
- 1.2 及更早版本没有更新模块，从 1.2 升到 1.3 需要手动安装一次；之后的版本可以在 App 内更新。

- 支持 微信 PDF/CSV、支付宝 CSV；xlsx 需先另存为 CSV；加密 PDF、扫描图片版 PDF 不支持。
- PDF 解析基于版式推断，微信若大幅改版可能出现对方/商品填错列（金额、方向、时间、交易单号最可靠）；导入前请在预览页核对。
- 引入 pdfbox-android（含其传递依赖 BouncyCastle）后 release APK 由约 6.7MB 增至约 11MB。
- 账单中的「退款」按原始 收/支 方向导入，不会自动与原支出冲抵；微信中收/支为 `/` 的行（如零钱提现、转入零钱通）会被跳过。
- 账单时间按手机当前时区解析（微信/支付宝导出均为北京时间，国内使用无影响）。
- 少数缺失交易单号的行会用「时间+金额+对方+商品」生成哈希作为去重键。
- 无云同步/备份功能，卸载 App 会清除全部数据；数据库在 `/data/data/com.ledger.app/databases/ledger.db`。
- 「从已安装 App 选择」依赖 `QUERY_ALL_PACKAGES` 权限；如果上架应用商店会受政策限制，个人侧载不受影响。
- 未在真机/模拟器上运行测试，以编译通过、单元测试（CSV/PDF 解析、真实 PDF 端到端提取、金额、曲线序列）和 aapt 验证为准；1.2 版界面重做同样没有真机截图核对，个别间距/字号可能需要按实机效果微调。1.3 的更新流程中，服务器端的 API、下载链路已实际验证；App 端的弹窗、安装器跳转和权限页往返都没有真机验证。
