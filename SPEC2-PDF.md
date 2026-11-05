# 补充任务：支持微信导出的 PDF 账单

上一轮已完成账本 App 的 CSV 导入。本轮在其代码基础上增加：**微信新版「个人对账」导出的是 PDF 文件**（邮箱接收，如 `微信支付账单(xxxx).pdf`），导入功能必须同样支持 PDF。所有改动完成后重新构建 release APK 并更新交付物。

## 具体要求

1. **依赖**：引入 `com.tom-roush:pdfbox-android:2.0.27.0`（Maven Central 上唯一的常规 Android PDFBox 移植，Apache 2.0），在 Application.onCreate 里 `PDFBoxResourceLoader.init(applicationContext)`。只用它做文本提取，不要引 iText/其他重库。

2. **文件路由按魔数判断，不看扩展名**：读文件头几个字节——`%PDF-` 开头走 PDF 解析；含 BOM/`，`/可解码文本走原 CSV 流程（UTF-8/GBK/UTF-16LE 自动识别逻辑保留）。文件选择器同时接受 .pdf/.csv/.txt 及 application/pdf、text/*、application/octet-stream。

3. **PDF 文本提取**：用 `PDDocument` + `PDFTextStripper` 逐页取文本合并。注意先跳过封面/说明页（含「微信支付账单」「明细列表」等标题页），逐行扫描。

4. **解析策略（防御式）**：PDF 里每笔交易是一行表格，提取出的文本通常按列序每格一行，顺序大致为：交易时间、交易类型、交易对方、商品、收/支、金额(元)、支付方式、当前状态、交易单号、商户单号、备注。实现要点：
   - 以正则 `^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$` 的行作为一条记录的起点，收集到下一条时间行/页眉为止
   - 记录内：方向 = 单独成行的 `收入` 或 `支出`；金额 = `^¥?\d+\.\d{2}$`；交易单号 = 纯数字且长度≥20 的行 → source_id；其余行按出现顺序填 交易类型/对方/商品（尽力而为，允许空）
   - 页眉页脚噪声（含「微信支付」「第 X 页」「共 Y 页」「账单明细」等）直接丢弃；解析不出方向的块整体丢弃，不许崩
   - 复用现有 `ImportRow` 数据结构，产出与 CSV 相同的结果进入同一个导入预览页（关键词自动勾选、去重逻辑不变；**PDF 与 CSV 的交易单号相同，跨格式去重天然生效**）

5. **README 更新**：微信导出说明改为——微信 我→服务→钱包→账单→右上角常见问题→下载账单→用于个人对账，邮箱收到文件（新版本为 PDF，旧版本 zip 内为 CSV），两种都支持；支付宝仍为 CSV。

6. **回归**：确保原 CSV 导入不受影响；`./gradlew :app:assembleRelease` 成功后更新 `/root/ledger-app/ledger.apk`（自签 keystore 沿用 app/release.keystore），debug 版同步更新为 `/root/ledger-app/ledger-debug.apk`，重跑 aapt 验证更新 apk-verify.txt，源码重新打包 `/root/ledger-app-source.zip`，并在 RESULT.md 追加「本轮新增 PDF 支持」小节。
