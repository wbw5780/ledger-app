# 构建结果

**状态：全部交付物完成，构建成功。**（当前最新为第五轮 v1.4 黑白配色，见文末「第五轮 黑白配色」）

| # | 交付物 | 结果 |
|---|---|---|
| 1 | `./gradlew :app:assembleDebug` → `/root/ledger-app/ledger-debug.apk` | BUILD SUCCESSFUL，18224492 字节（第二轮重建） |
| 2 | `./gradlew :app:assembleRelease`（isMinifyEnabled=false，`app/release.keystore` 自签）→ `/root/ledger-app/ledger.apk`（**最终交付**） | BUILD SUCCESSFUL，11030458 字节（第二轮重建），apksigner v2 校验通过 |
| 3 | aapt dump badging 验证两个 APK → `/root/ledger-app/apk-verify.txt` | package 与 application-label 均读出 |
| 4 | 源码包 `/root/ledger-app-source.zip` | 已生成（排除 .gradle、build 目录） |
| 5 | `/root/ledger-app/README.md` | 安装方法、微信/支付宝导出路径、已知限制 |
| 6 | `/root/ledger-app/RESULT.md` | 本文件 |

附加：`./gradlew :app:testDebugUnitTest` 7 个单元测试全部通过（微信 UTF-8 BOM、支付宝 GBK、UTF-16LE、支付宝新版无前导行、乱码不崩溃、金额格式/解析、曲线补零与抽样）。

## 构建环境

- OpenJDK 17（`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`）
- Android SDK `/opt/android-sdk`：platform-tools、platforms;android-35、build-tools;35.0.0
- Gradle wrapper 8.12，AGP 8.7.3，Kotlin 2.0.21（compose plugin），KSP 2.0.21-1.0.28，Compose BOM 2024.10.01，Room 2.6.1
- gradle.properties 按要求限制内存（-Xmx2048m，workers.max=2，kotlin daemon 1536m），使用 `--no-daemon` 构建

## aapt 验证输出摘要

```
===== ledger-debug.apk (9086105 bytes) =====
package: name='com.ledger.app' versionCode='1' versionName='1.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sdkVersion:'26'
targetSdkVersion:'35'
uses-permission: name='android.permission.QUERY_ALL_PACKAGES'
application-label:'账本'
===== ledger.apk (6723329 bytes) =====
package: name='com.ledger.app' versionCode='1' versionName='1.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sdkVersion:'26'
targetSdkVersion:'35'
uses-permission: name='android.permission.QUERY_ALL_PACKAGES'
application-label:'账本'
===== apksigner verify ledger.apk =====
Verified using v2 scheme (APK Signature Scheme v2): true
Signer #1 certificate DN: CN=Ledger, OU=Personal, O=Ledger, L=Local, C=CN
```

## 功能实现概要

- 项目管理：新建/编辑/删除（确认弹窗，Room 外键 CASCADE + 事务内显式删除），多关键词（默认=项目名），图标可选本机已安装应用（PackageManager + QUERY_ALL_PACKAGES，搜索过滤）或上传图片；图标转 PNG 存 `filesDir/icons/`，BitmapFactory + LruCache 加载（无 Coil）
- 记录：手动记一笔（金额/收支/日期时间/对方/备注/所属项目），可编辑删除；金额以分(Long)存储；(source, source_id) 唯一索引去重
- CSV 导入：编码自动识别（BOM→严格 UTF-8→GBK），扫描定位表头，按列名取值，微信/支付宝两种格式及支付宝新版列名，尾部统计行跳过不崩；预览页按关键词默认勾选、已选笔数/金额合计、全选/全不选；导入后提示「新增 N 条，跳过重复 M 条」
- 统计图表：首页总盈亏卡片（绿↑/红↓ + 总收入/总支出）、总累计曲线、项目卡片列表；项目详情含盈亏、累计曲线、导入/记一笔按钮、按月倒序分组流水（月小计）；Canvas 手绘平滑曲线，补零日、前缀和、极值抽样至 ≤200 点，零轴虚线，零轴上下绿/红分色及渐变填充，首末日期与最大/最小值刻度，空状态提示
- 全中文界面，深色主题跟随系统（Android 12+ 动态取色），无任何网络权限/请求

## 已知限制

- 未在真机/模拟器上运行（按要求以编译 + 单元测试 + aapt 验证为准），UI 交互细节未经实机检验
- 仅支持 CSV/TXT 账单（xlsx 需先另存为 CSV）；退款不会自动冲抵原支出
- 账单时间按手机时区解析；无云备份，卸载即清除数据
- debug 与 release 签名不同，不能互相覆盖安装

## 本轮新增 PDF 支持

**状态：完成。** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 全部 BUILD SUCCESSFUL；交付物已全部更新（`ledger.apk`、`ledger-debug.apk`、`apk-verify.txt`、`/root/ledger-app-source.zip`、README）。

| 项 | 结果 |
|---|---|
| 版本 | versionCode 1→2，versionName 1.0→1.1；release 仍用 `app/release.keystore` 签名，证书 SHA-256 `df55d71c…8fbbe` 与上一版相同，可直接覆盖安装 |
| `ledger.apk`（release） | 11030458 字节（原 6723329），apksigner v2 校验通过 |
| `ledger-debug.apk` | 18224492 字节（原 9086105） |
| 单元测试 | 16 个全部通过、0 跳过（原 7 个 CSV/金额/曲线测试不变且全部通过 + 新增 9 个 PDF 测试） |
| apk-verify.txt | aapt badging（两个 APK）+ apksigner + 本轮检查：Manifest 中 `android:name="com.ledger.app.LedgerApp"`、pdfbox 资源（glyphlist / UniGB cmap / LiberationSans）已打包、dex 中存在 PDDocument / PDFTextStripper / PDFBoxResourceLoader / BillImporter / PdfBillParser |

### 改动

- **依赖**：`com.tom-roush:pdfbox-android:2.0.27.0`（仅用于文本提取）。它的传递依赖 BouncyCastle 1.72 保留（加密 PDF 的安全处理器会用到，去掉的话可能在运行时因缺类崩溃），但通过 packaging 排除了 BouncyCastle 的后量子算法参数表 `org/bouncycastle/pqc/**`（约 8MB 未压缩，这里用不到），release 体积从 15.2MB 降到 11.0MB。没有引入 iText 或其他库。
- **`LedgerApp`**（新增 Application，已在 Manifest 注册）：在 `onCreate` 中调用 `PDFBoxResourceLoader.init(applicationContext)`。
- **`csv/BillImporter`**（新增统一入口）：按魔数路由，不看扩展名。`%PDF-` 开头（允许前导 BOM 或空白）→ PDF；`PK\x03\x04`（zip/xlsx）→ 提示先解压；其余 → 原 `BillParser.parse`（BOM / 严格 UTF-8 / GBK / UTF-16 识别逻辑一行未改）。PDF 路径上所有 Throwable 都会被捕获并转成错误提示。
- **`csv/PdfTextExtractor`**：`PDDocument.load` + `PDFTextStripper`（sortByPosition）逐页提取；加密 PDF 给出明确提示。
- **`csv/PdfBillParser`**（纯 Kotlin，可单测）：跳过没有交易时间的页（封面/说明页）；每行按空白切词并分类为锚点（时间 / 收入·支出 / 金额 / ≥20 位单号）或文本，因此"每格一行"和"整行一条"两种提取结果都能处理；以时间作为记录起点（兼容日期和时间折成两行）；按表头列序把文本填入 交易类型/对方/商品/状态（找不到表头时用 SPEC 给的默认列序；也兼容「交易单号」在「交易时间」之前的列序）；窄列折行的商品名会合并，折行的长单号会拼接；页眉页脚、重复表头丢弃；没有方向或金额的记录整条丢弃并计入跳过数。产出 `ImportRow(source="wechat", sourceId=交易单号)`，与 CSV 完全一致，所以跨格式去重靠现有 (source, source_id) 唯一索引直接生效。
- **UI**：文件选择器 MIME 从 `*/*` 改为 `application/pdf`、`text/*`、`application/octet-stream`，并额外加了 `text/csv`、`text/comma-separated-values`、`application/csv`、`application/vnd.ms-excel`（部分文件管理器会把 csv 标成这些类型，不加会导致原来能选的 CSV 变成灰色无法选择）；去掉了原来"非 .csv/.txt 扩展名直接拒绝"的检查。预览页对 PDF 显示「PDF 账单，跳过不计收支/无法识别 N 笔」，其余流程（关键词自动勾选、导入、去重提示）不变。
- **README**：微信导出说明改为「PDF（新版）/ zip 内 CSV（旧版）两种都支持」，新增 PDF 解析规则和已知限制；支付宝仍为 CSV。

### 新增测试（`PdfBillParserTest`）

1. 每格一行版式：封面页、表头、页眉页脚、`/` 方向行跳过、折行商品名合并
2. 整行一条版式（含「第1页/共1页」页脚）
3. 交易单号在交易时间之前的列序，日期/时间分行，单号折行
4. 没有数据、缺方向或金额：返回错误或跳过，不崩溃
5. 魔数路由：.csv 扩展名但内容是 PDF → 走 PDF；.pdf 扩展名但内容是 GBK CSV → 走 CSV；zip → 提示；提取器抛异常 → 返回错误
6. 跨格式去重键：同一笔交易的 CSV 与 PDF 结果在 source、sourceId、金额、时间、方向上完全一致
7. **真实 PDF 端到端（桌面版 PDFBox）**：测试中用 Apache PDFBox 2.0.27（`testImplementation`，不打进 APK）和系统 CJK 字体生成一份「封面页 + 横版表格明细页」的 PDF，提取后解析
8. **真实 PDF 端到端（pdfbox-android）**：同一份 PDF 交给 App 实际使用的 `PdfTextExtractor`（pdfbox-android）。做法是把 AAR 里的 assets 解出来放到测试 classpath——未初始化时 pdfbox-android 会回退到从 classpath 加载资源；同时开启 `unitTests.isReturnDefaultValues`。结果解析出 2 笔，交易对方、商品、状态、单号都正确，并且和 CSV 的去重键一致
9. 以 `%PDF-` 开头但内容损坏的文件经真实提取器处理：返回错误信息，不崩溃

### 本轮已知限制

- 手头没有真实的微信 PDF 样本。解析规则按 SPEC 描述的列序以及常见变体（整行一条、单号在前、折行）编写，并用合成 PDF 验证；微信实际版式如有差异，金额、方向、时间、交易单号这几个锚点最可靠，对方/商品可能填错列，需要在预览页核对。
- 不支持带打开密码的 PDF（会提示）和扫描图片版 PDF（没有文字层，会提示"未找到交易记录"）。
- 仍未在真机或模拟器上运行；`PDFBoxResourceLoader.init` 在真机上的效果只能通过代码和 Manifest 检查确认（JVM 测试走的是 classpath 回退路径）。
- 在整行一条版式下，如果某个单元格在同一行内折行，折出来的部分可能被归到上一笔记录的备注里（不影响金额和去重）。


## 第三轮 UI 重做

**状态：完成。** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 全部 BUILD SUCCESSFUL；交付物已全部更新（`ledger.apk`、`ledger-debug.apk`、`apk-verify.txt`、README、`/root/ledger-app-source.zip`）。

| 项 | 结果 |
|---|---|
| 版本 | versionCode 2→3，versionName 1.1→1.2；release 仍用 `app/release.keystore` 签名，证书 SHA-256 `df55d71c…8fbbe` 不变，可直接覆盖安装 1.1 |
| `ledger.apk`（release） | 11083274 字节，apksigner v2 校验通过 |
| `ledger-debug.apk` | 14408933 字节 |
| 单元测试 | 16 个全部通过、0 跳过（7 个 CSV/金额/曲线 + 9 个 PDF，含两条真实 PDF 端到端），解析/导入/曲线逻辑代码本轮未改动 |
| apk-verify.txt | 两个 APK 的 aapt badging + apksigner + 第二轮 PDF 回归检查 + 本轮检查：icon/roundIcon 指向 adaptive-icon（background/foreground/monochrome 三层），旧 `drawable/ic_launcher` 已移除，Theme.Ledger 四套配置（默认/night/v31/night-v31）的背景色、透明系统栏、splash 背景与图标，品牌色资源，新增 UI 类在 dex 中，源码中动态取色调用为 0；末尾附单元测试结果 |

### 改动

**主题（`ui/Theme.kt`）**
- 删掉了 `dynamicLightColorScheme` / `dynamicDarkColorScheme` 分支，亮/暗两套 colorScheme 每个槽位都显式赋值（包括 secondary/tertiary/surfaceContainer*/outline/inverse 等，`surfaceTint` 设为透明），默认紫色不会再从任何组件里冒出来。
  - 亮色：primary `#0E7C5A`，背景 `#F6F7F4`，卡片 `#FFFFFF`
  - 暗色：primary `#3DBE8C`，背景 `#101413`，卡片 `#1A201E`
- 新增 `LedgerPalette`（通过 CompositionLocal 提供）：收入 `#2E9E6B` / 支出 `#E25B5B`（暗色下调亮为 `#4CC48A` / `#FF7A7A`），暖金 `#D9A441`，以及卡片、描边、tonal 底、导航栏底色。`MoneyColors` 改为从这里取色，所有金额只用收入色、支出色或主文字色。
- 圆角体系：10/12/16/20/28dp。字体层级加粗了标题；金额统一用 `AmountStyle`（`fontFeatureSettings = "tnum"` + SemiBold）。

**组件（`ui/Common.kt`、新增 `ui/LedgerIcons.kt`）**
- `LedgerCard`：圆角 20dp。亮色用极淡的绿灰阴影，暗色用 1dp 白色 8% 描边加明度差分层，不用默认 elevation。内边距 16–20dp，卡片间距 12dp，页面左右留白 16dp。
- `AmountText`：¥ 按数字字号的 0.68 倍显示，加千分位，金额过长时按比例缩小字号，避免撑出卡片。
- `ProjectIcon` / `BitmapOrLetter`：图标统一放进圆角 14dp 的方形容器（列表 50dp、详情 56dp、编辑页 76dp）。容器底色取图标平均色的淡色；没有图标时用品牌 tonal 底加首字。
- 顶栏：一级页面用 `LargeHeader`（30sp 粗体大标题，透明背景）；二级页面用 `SubTopBar`（圆形返回按钮加标题，透明背景）。原来的 `TopAppBar` 全部去掉。
- `LedgerBottomBar`：自定义底部导航，高 78dp。选中项是 64×36dp 的药丸形指示器，品牌绿底、反色图标，文字也变品牌绿，颜色切换带动画。导航栏底色一直延伸到系统导航栏下方。
- 自绘线性图标（24 网格、圆头描边）：总览/明细/我的、＋、笔、返回、编辑、删除、导入、日历、时钟等，没有引入 material-icons-extended。
- 其他：`PillChip`（药丸筛选）、`GoldBadge`（暖金徽标）、`TrendBadge`（↑↓ 圆形徽标）、`PrimaryButton` / `TonalButton`（圆角 16dp、高 54dp、无阴影）、`GroupedRow`（分组列表：首行上圆角、末行下圆角，中间用细分隔线）、`ledgerFieldColors`（输入框描边和底色）。

**页面**
- **一级页面改为 3 个 Tab**（`MainScreen`，替换原 `HomeScreen`）。原悬浮 FAB 已移除。当前 Tab 状态提升到 `MainActivity`，从二级页面返回时保持不变；在「明细」/「我的」页按返回键会先回到「总览」。
  - **总览**：大标题「账本」，副标题为日期，右上角是「记一笔」快捷按钮。下面依次是总盈亏卡（标签「总盈亏」、36sp 净额、↑↓ 徽标，累计曲线放在卡片内）和「我的项目」区块（标题旁的暖金徽标显示项目数，右侧有＋按钮用于新建项目）。项目卡片只显示图标、名称、净盈亏和箭头。
  - **明细**：全部项目的流水按月倒序分组，月份行用标题样式，当月小计显示在同一行右侧。顶部有一行项目筛选 chips（全部/各项目）。选「全部」时，每行左侧显示项目图标，副标题是项目名；选某个项目时，左侧显示来源徽标，副标题是来源。点任意一条进入编辑/删除。
  - **我的**：深绿渐变品牌卡（项目数和流水数），撑满宽度的「新建项目」主按钮（58dp），「记一笔」次按钮，「账单导出」三步说明卡（用暖金序号），底部是版本号（从 PackageManager 读取）。
- **项目详情**：去掉了「共 N 笔记录」小字。页面依次是盈亏主卡（含曲线）、收入/支出两张小统计卡（只在详情页出现）、「导入账单」/「记一笔」按钮，以及按月分组的流水。
- **记一笔/编辑记录**：支出/收入改为药丸分段切换（选中段用对应的收支色）。金额是一个 40sp 的大号等宽数字输入，带 ¥ 前缀，按收支着色。日期/时间改为 tonal 胶囊按钮。导入来源由原来的一行小字改为暖金徽标。
- **新建/编辑项目**：图标卡（预览，以及「选择已装 App」「上传图片」两个按钮）、名称和关键词卡（关键词改成可点击删除的 tonal 胶囊），关键词说明小字改成了输入框占位文字。应用选择弹窗去掉了包名小字（搜索仍然可以匹配包名），改为分组列表。
- **导入预览**：顶部汇总卡显示文件名、「微信/支付宝 PDF/CSV」徽标、「共 N 笔 · 跳过 M」、已选净额大数字和收入/支出两块。勾选状态用自定义圆形勾选标记，未勾选行的金额变灰。底部是撑满宽度的「导入到「项目」（N）」按钮。去掉了编码说明、关键词列表这些小字。
- **曲线（`ui/Chart.kt`）**：线宽 2.5→3dp。渐变更明显：零轴上方是绿色 42% 渐变到透明，下方是红色渐变到透明。去掉了左轴和上下边框实线，只保留淡色零轴虚线。刻度文字 10.5sp、淡灰、等宽数字。末端点加了暖金光晕。空状态文字缩短为「暂无数据」。

**系统层**
- 状态栏和导航栏：`enableEdgeToEdge(SystemBarStyle.auto(透明, 透明))` 让两者都透明，图标明暗跟随系统深色模式。API 29+ 关闭了 `isNavigationBarContrastEnforced`，三键导航下系统不会再叠半透明遮罩。主题 XML 的 `windowBackground` 改为 `#F6F7F4` / `#101413`，冷启动时不会闪黑条或白条。
- **启动图标**：删掉了旧的 `drawable/ic_launcher.xml`，新增 `mipmap-anydpi-v26/ic_launcher(.xml|_round.xml)` 自适应图标，分三层：
  - 背景：深翡翠绿斜向渐变（`#139069`→`#0A5C43`），右上角有一块淡光。
  - 前景：白色账本，带书脊、两条收支横线、绿色上升折线和暖金端点。所有图形都在 66dp 安全区内（外框角点到中心约 30.8 个单位，安全区半径 33）。
  - monochrome：同样的造型改为线框，用于 Android 13+ 主题图标。
- **Splash**：新增 `values-v31` / `values-night-v31` 主题，`windowSplashScreenBackground` 设为对应的主题背景色，`windowSplashScreenAnimatedIcon` 设为 `@mipmap/ic_launcher`。Android 12+ 启动画面显示新图标，背景与 App 首屏一致。

### 未改动（防回归）
- `csv/`、`data/`、`util/`（BillParser、PdfBillParser、BillImporter、PdfTextExtractor、Room 实体/DAO、Money、Series）一行未改。文件选择 MIME 列表、大小上限、魔数路由、关键词匹配 `matchesKeywords`、导入事务与去重提示、项目删除事务、记录保存/删除的逻辑，都是原样搬进新布局。
- 16 个单元测试在改动后的代码上全部重新执行并通过。

### 本轮已知限制
- 环境里没有模拟器和真机，界面没有截图核对。判断依据只有编译通过、代码走查和 aapt 资源检查，实机上个别间距、字号或阴影深浅可能还需要微调。
- 图标容器的淡色取的是图标像素的平均色，对颜色很杂的图标效果偏灰（不影响使用）。
- 已从 `app/build.gradle.kts` 删掉直接依赖 `material-icons-core`，代码里也不再 import 它；但 material3 本身会把它作为传递依赖带进来（比如下拉框箭头 `ExposedDropdownMenuDefaults.TrailingIcon` 就用到它），所以 APK 体积没有变化。


## 第四轮 应用内自动更新

**状态：完成。** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 全部 BUILD SUCCESSFUL。仓库已推送，Release v1.3 已实际发布，服务器上匿名完整下载已验证。更新源只有 GitHub Releases，没有任何镜像。

| 项 | 结果 |
|---|---|
| GitHub 仓库 | <https://github.com/wbw5780/ledger-app>（public），分支 `main`。`.gitignore` 排除 `.gradle/`、`.kotlin/`、`build/`、`app/build/`、`local.properties`、`*.apk`、`*.keystore`/`*.jks`、`app/release.keystore`。已核对：仓库 `app/` 下只有 `build.gradle.kts`、`schemas`、`src`，没有 keystore |
| 版本 | versionCode 3→4，versionName 1.2→1.3。release 仍用 `app/release.keystore` 签名，证书 SHA-256 `df55d71c…8fbbe` 不变，可直接覆盖安装 1.2 |
| `ledger.apk`（release） | 11116658 字节，SHA-256 `ddb33d4f…8cbfdb`，apksigner v2 校验通过 |
| `ledger-debug.apk` | 14206860 字节 |
| 单元测试 | 22 个全部通过、0 跳过（原 16 个 + 新增 `UpdateCheckerTest` 6 个） |
| 发布脚本 | `/root/publish-ledger.sh`（chmod +x，仓库中另有一份副本 `scripts/publish-ledger.sh`），共实际执行 4 次，均成功。从第 2 次起每次都先删除同 tag 的旧 release 和 tag，所以「可重复发布」这条路径也跑过了 |
| Release | `releases/latest`：`tag_name=4`、`name=v1.3`、body=「新增应用内自动更新：自动检查 GitHub Releases，应用内下载并引导安装」，资产 `ledger.apk` 11116658 字节 |
| 匿名下载 | 不带 token：`browser_download_url` 先 302 到 release-assets.githubusercontent.com，再返回 200，完整下载 11116658 字节，SHA-256 与本地 `ledger.apk` 一致。另外用 JDK 的 HttpURLConnection 按 App 相同的写法请求了一遍：API 返回 200，下载时自动跟随跨主机 302，最终 200，读到的字节数等于 asset size |
| token 安全 | 脚本把 `Authorization` 头通过 stdin 交给 `curl -K -`，不会出现在命令行参数、`ps` 或输出里。已扫描 4 次发布日志、脚本、git 全部历史和工作目录，都没有出现 token |
| apk-verify.txt | 两个 APK 的 aapt badging、apksigner、第二/三轮记录，以及本轮检查：versionCode 4/1.3；INTERNET + REQUEST_INSTALL_PACKAGES 权限；cleartext 出现 0 次；FileProvider（authority `com.ledger.app.fileprovider`）；`file_paths.xml` 内容；更新模块的类都在 dex 中；没有 OkHttp；关键字符串常量；GitHub 匿名核验；单元测试结果 |
| 源码包 | `/root/ledger-app-source.zip` 已重打，内容与 git 仓库跟踪的文件一致（不含 keystore、构建产物和 APK） |

### 改动

- **`util/UpdateChecker.kt`**（新增）：
  - 常量 `REPO_API_LATEST`，资产名 `ledger.apk`
  - 请求方式：HttpURLConnection，连接和读取超时都是 10s；请求头 `Accept: application/vnd.github+json`，另带 `User-Agent`（GitHub API 要求）；在 `Dispatchers.IO` 协程里执行，不带任何 Authorization
  - `parseRelease` 用 org.json 解析出 `tag_name`（数字）、`name`、`body`，以及 `assets[]` 里 `ledger.apk` 的 url 和 size
  - `isNewer`：tag 严格大于本机 `longVersionCode` 才算有新版
  - `download`：流式写入 `cacheDir/updates/ledger.apk`，约每 1% 回调一次进度。HttpURLConnection 会自动跟随 302，另外也手动处理 3xx，并拒绝非 HTTPS 地址。每读一块检查一次协程是否被取消。下载完要求文件大小等于 asset size，否则删除文件并抛 `IncompleteDownloadException`。取消或出错时都会删除文件
  - 安装前用 `getPackageArchiveInfo` 再核对一次包名和 versionCode
  - 安装：FileProvider content URI + `ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`
  - Android 8+ 用 `canRequestPackageInstalls()` 判断安装权限，并提供跳转 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`（带 `package:` uri）的 Intent
- **`ui/UpdateUi.kt`**（新增）：
  - `UpdateController` 状态机：检查状态有 Idle / 检查中 / 已是最新 / 发现新版 / 失败；当前弹窗有 新版提示 / 下载中 / 需要授权
  - 静默检查吞掉所有错误；手动检查失败时 toast「检查更新失败，请检查网络（需能访问 GitHub）」
  - 下载弹窗：进度条 + 百分比 + 「x / y MB」 + 「取消」（终止协程并删除临时文件），不能点外部或按返回键关闭；下载完成后自动拉起安装器
  - 没有安装权限时先弹说明，点「去设置」跳到权限页；从设置页返回（onResume）后自动重试安装，仍未授权就再弹说明
  - 新版提示弹窗：标题「发现新版本 v1.x」，显示安装包大小徽标和 changelog（可滚动），按钮「以后再说」/「立即更新」
- **接入**：
  - `MainActivity` 通过 `LocalUpdater` 提供 controller；进入主界面后 `delay(2s)` 静默检查一次（每个进程只查一次），只有发现新版才弹窗；用 `LifecycleResumeEffect` 处理从设置页返回
  - 「我的」页在导出说明卡下面新增分组卡：「当前版本 v1.3」和「检查更新」两行。「检查更新」行右侧按状态显示：检查中（转圈）/ 已是最新 / 暖金徽标「新版 vX」（点击再次打开更新弹窗）/ 红字「检查失败，点击重试」
  - 底部原有的版本号文字保留不动
  - 品牌卡上的「离线」徽标改成「本地」，因为 App 现在会联网检查更新，但账目数据仍然只存在本机
  - 新增自绘的 Download 图标
- **Manifest**：新增 `INTERNET`、`REQUEST_INSTALL_PACKAGES` 权限；注册 FileProvider，`res/xml/file_paths.xml` 为 `<cache-path name="update" path="updates/"/>`。没有设置 usesCleartextTraffic，请求全部走 HTTPS
- **构建**：
  - 签名改成条件式：`app/release.keystore` 存在时才签名，这样从公开仓库克隆下来的源码也能构建。服务器上 keystore 存在，签名不受影响
  - 新增 `testImplementation("org.json:json")`，只用于单测：android.jar 里的 org.json 是桩实现，测 `parseRelease` 需要真实实现。App 运行时用系统自带的 org.json，没有给 APK 增加依赖
- **README**：新增「自动更新」一节（原理和发布流程）；在已知限制里补充：国内直连 GitHub 可能不通、需要代理；公开仓库不含 keystore；匿名 API 每小时限 60 次；1.2 升到 1.3 需要手动安装一次。另外注明「签名 keystore 保存在构建服务器」

### 与 SPEC 不同的地方

- **APK MIME 类型**：SPEC 写的是 `application/vnd.android-package-archive`（连字符），正确的类型是 `application/vnd.android.package-archive`（点号）。系统安装器按后者匹配，如果照 SPEC 写，`ACTION_VIEW` 会找不到安装器。第一次发布后，GitHub 回显的 content_type 让我发现了这个问题。App 的 Intent type 和上传资产的 Content-Type 都已改成正确值，单测里也锁定了这个常量，之后重新构建并重新发布
- **重定向主机**：GitHub 资产现在 302 到 `release-assets.githubusercontent.com`，不是 SPEC 说的 objects.githubusercontent.com。这对 App 没有影响，跨主机 https→https 重定向会被自动跟随（已验证）
- **建仓参数**：GitHub API 没有 `public` 参数，实际用的是 `"private": false`

### 本轮已知限制

- 仍然没有真机或模拟器。服务器端的 API、302 和完整下载已实际验证，App 端走的是同一套 HttpURLConnection 写法；但弹窗交互、系统安装器拉起、「安装未知应用」设置页往返，以及国产 ROM 自带安装器的行为，都没有在真机上确认
- 国内直连 GitHub 不稳定，可能需要代理。按要求没有做镜像兜底，连不上时静默检查不打扰用户，只有手动检查会提示
- 已装 1.2 的设备没有更新模块，需要手动安装一次 1.3，之后才能在 App 内更新
- 每次 release 构建的 APK 不是逐字节可复现的。本轮最后一次发布用的是最终源码（提交 `23f666c`，tag 4 指向它）构建的包，Release、`ledger.apk` 和 apk-verify.txt 里记录的 SHA-256 一致

## 第五轮 黑白配色

**状态：完成。** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 全部 BUILD SUCCESSFUL。提交 `114880b`「UI: 黑白配色主题（v1.4，versionCode 5）」已推送到 `main`；`/root/publish-ledger.sh` 执行成功，releases/latest 指向 tag 5。这一轮只换配色，没有改功能逻辑。

| 项 | 结果 |
|---|---|
| 版本 | versionCode 4→5，versionName 1.3→1.4。仍用 `app/release.keystore` 签名，证书 SHA-256 `df55d71c…8fbbe` 不变，1.3 可以在 App 内直接更新覆盖安装 |
| `ledger.apk`（release） | 11100322 字节，SHA-256 `b8ba4c73…6d792f`，apksigner v2 校验通过 |
| `ledger-debug.apk` | 14189179 字节 |
| 单元测试 | 22 个全部通过、0 跳过（BillParserTest 7 + PdfBillParserTest 9 + UpdateCheckerTest 6），测试代码一行没动 |
| Release | `releases/latest`：`tag_name=5`、`name=v1.4`、body=「全新黑白配色：去掉绿色与金色，仅保留支出/负数红色语义色」，资产 `ledger.apk` 11100322 字节，content_type `application/vnd.android.package-archive`。发布脚本自检通过；另外匿名完整下载了一次：302 跳到 release-assets.githubusercontent.com 后返回 200，字节数和 SHA-256 都与本地一致 |
| token 安全 | 已扫描发布日志、apk-verify、README、RESULT、脚本、工作目录和 git 全部历史，都没有出现 token |
| apk-verify.txt | 重新生成：两个 APK 的 badging、apksigner，以及本轮检查：versionCode 5/1.4；签名证书；颜色资源（bg_light `#F4F4F4`、bg_dark `#0B0B0B`、brand_ink `#111111`、brand_paper `#F5F5F5`，`brand_emerald` 出现 0 次）；Theme.Ledger 四套配置的 windowBackground、colorPrimary 和 Splash 背景；启动图标三层 drawable 及内联渐变的实际颜色（`#1A1A1A`→`#0A0A0A`、前景全白）；源码中旧色值残留（无）；源码里全部颜色字面量的去重清单；dex 关键类和自动更新字符串回归；GitHub 匿名核验；单元测试结果。前几轮的历史核验记录可以在 git 历史中查到 |
| 源码包 | `/root/ledger-app-source.zip` 已用 `git archive HEAD` 重打（只含仓库跟踪的文件，不含 keystore、构建产物和 APK） |

### 配色

| | 浅色 | 深色 |
|---|---|---|
| 页面背景 / 卡片 | `#F4F4F4` / `#FFFFFF`（极淡纯黑阴影） | `#0B0B0B` / `#171717` + 1dp `#262626` 描边 |
| 主文字 / 次级文字 | `#111111` / `#8A8A8A` | `#F5F5F5` / `#9A9A9A` |
| primary（主按钮、选中 chip、导航药丸、勾选标记） | `#111111` 底白字 | `#F5F5F5` 底黑字 |
| tonal（次按钮、未选中 chip、来源徽标） | `#EAEAEA` 底黑字 | `#232323` 底白字 |
| 分隔线 | `#E5E5E5` | `#262626` |
| 支出 / 负盈亏（唯一的彩色） | `#E5484D` | `#F07178` |
| 收入 / 正盈亏 | 主文字色 + 「+」号 | 主文字色 + 「+」号 |

### 改动

- **`ui/Theme.kt`**：`Brand` 中去掉 Emerald/Gold，改为 `Ink #111111`、`Paper #F5F5F5` 和两种支出红。亮/暗两套 `colorScheme` 全部槽位重新赋为灰阶，`error` 用支出红，所以删除按钮等也是这个红色。`LedgerPalette` 去掉 `gold`，新增 `badge/onBadge`（中性徽标）、`iconBg`（项目图标容器灰底 `#EDEDED`/`#232323`）、`onIncome`（以收入色为底时上面的文字色）；`income` 改为主文字色。
- **`ui/Chart.kt`**：线条 3dp；零轴以上为主文字色，填充是主文字色 18%→透明的渐变，看起来是灰色；零轴以下为红色，填充 25%→透明；零轴仍是淡灰虚线；末端光晕从暖金改为 `onSurfaceVariant` 30% 的灰色。
- **`ui/Common.kt`**：`GoldBadge` 改名为 `CountBadge`，用中性灰底（调用处 ImportPreview/UpdateUi/RecordEdit 同步改名）；项目图标容器不再从图标取平均色，统一用 `iconBg`，因此删掉了 `averageColor`；`PillChip` 未选中时为 tonal 灰底、主文字色、无描边，选中时为主色底；卡片和底栏阴影去掉原来的绿色偏色（`0x..0E3B2C` 改为纯黑低透明度）；底部导航沿用 primary/onPrimary，所以选中状态在浅色下是黑药丸白图标，深色下是白药丸黑图标，未选中为灰色。
- **`ui/HomeScreen.kt`**：「我的」页品牌卡的渐变改为 `#1A1A1A`→`#0A0A0A`；深色下改为 `#2A2A2A`→`#1A1A1A` 加描边，避免和 `#0B0B0B` 背景混在一起。「本地」徽标改为白底黑字；引导步骤的序号改为主色底（浅色黑底白字，深色白底黑字）。
- **`ui/RecordEditScreen.kt`**：收支切换中，「收入」选中时底色是主文字色，文字改用 `onIncome`（否则深色下会出现白底白字）；「支出」选中仍是红底白字。
- **资源**：`colors.xml` 中 `brand_emerald*` 换成 `brand_ink`/`brand_paper`，`bg_light`/`bg_dark` 改为 `#F4F4F4`/`#0B0B0B`。四套 `themes.xml` 的 windowBackground、colorPrimary/Accent、Splash 背景（v31 / night-v31）同步修改。
- **启动图标**：背景为 `#1A1A1A`→`#0A0A0A` 斜向渐变，右上角加一层极淡白光；前景是白色线框账本（与 monochrome 层几何形状相同）、白色上升折线和白色实心端点，两条横线为 70% 白；monochrome 层也补上了端点。
- **README**：版本号和视觉描述已更新。
- **全局残留检查**：在 `app/src` 中 grep 了 `0E7C5A、3DBE8C、2E9E6B、D9A441、E6B75A、139069、0A5C43、F6F7F4、101413、E25B5B、FF7A7A、4CC48A、8A6111、3A2A00、0E3B2C、DCF1E7、E3F2EA、7FDDB2、7FD6AE、1A201E、brand_emerald、Gold/Emerald`，结果为 0。源码里剩下的颜色字面量只有灰阶和红色系（支出红及其 error 容器色），完整清单见 apk-verify.txt。

### 与 SPEC 不同 / 需要说明的地方

- **曲线零轴以下的线条用红色**：SPEC 写的是「线条=主文字色」，但同时规定负盈亏用红色，而且零轴以下的填充也是红色。所以这里零轴以上的线条用主文字色，零轴以下的线条用红色，和填充、净额数字的颜色一致。如果希望整条线都是黑/白，只需把 `Chart.kt` 中下半部分的 `drawPath(line, neg, …)` 改成 `pos`。
- **深色品牌卡**：SPEC 对这张卡没有规定。如果照搬浅色的黑渐变，深色下会和背景融在一起，所以改用 `#2A2A2A`→`#1A1A1A` 加描边。
- **APK 中残留一个绿色值**：androidx.core 库自带 `color/call_notification_answer_color #1D873B`（通话通知样式用），App 的 UI 没有引用它，所以没有处理。

### 本轮已知限制

- 和前几轮一样，没有在真机或模拟器上看过实际效果，以编译、单元测试和 aapt 资源核验为准。灰阶的层次（例如 `#F4F4F4` 背景与白卡片、`#0B0B0B` 与 `#171717`）是按 SPEC 取值的，实机观感可能还需要微调。
- Material3 内置组件（DatePicker、TimePicker、AlertDialog、进度条等）的颜色来自 colorScheme，已全部换成灰阶，但同样没有截图核对。
