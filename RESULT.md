# 构建结果

**状态：全部交付物完成，构建成功。**（当前最新为第四轮 v1.3 自动更新，见文末「第四轮 应用内自动更新」）

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
