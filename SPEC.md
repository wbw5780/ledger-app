# 任务：开发一个安卓原生「账本」App 并打出可安装的 APK

你要在这台服务器上从零开发一个安卓应用并完成构建。本文件是完整需求。**成功标准：`/root/ledger-app/ledger.apk` 存在、构建成功、可安装。** 全程无人交互（权限已跳过），遇到问题自行排查修复，直到构建成功。

## 背景

用户想在手机上记账：给每个"项目"（一般是游戏，比如 噜噜王国、海域）单独记账，收入/支出来自微信、支付宝账单的 CSV 导入，也支持手动记一笔。要能看每个项目的总盈亏、累计盈亏曲线，以及所有项目加在一起的总曲线。

## 技术栈（固定，不要换）

- Kotlin + Jetpack Compose（Material 3），单 Activity 多页面（自导航即可，不必引 navigation 库），单模块 `:app`
- 数据库 Room（本地 SQLite），不联网、不要任何网络请求功能
- minSdk 26，targetSdk 35，compileSdk 35
- 图表**不要引入第三方图表库**，用 Compose Canvas 手绘折线图
- 界面全部中文

## 功能需求

### 1. 项目管理
- 新建/编辑/删除项目：名称必填；可选设多个「账单匹配关键词」（用于导入账单时自动勾选，默认关键词=项目名）
- 项目图标两种来源（新建项目页两个入口）：
  a. **从本机已安装 App 选择**：列出手机上所有已安装应用（图标+名称，支持搜索过滤），点选后自动取该应用的名字和图标填入表单。需要 `QUERY_ALL_PACKAGES` 权限（清单声明即可，个人侧载应用不需要动态申请），用 PackageManager 实现
  b. **自己上传图片**：系统文件选择器选图片 + 手动输入名字
- 图标保存方式：转成 PNG 存 `filesDir/icons/`，数据库存文件名；列表里用 Coil 加载？——**不要用 Coil**，自己写 BitmapFactory + remember 即可

### 2. 记录（流水）
- 手动记一笔：金额、收入/支出、日期时间（默认现在）、备注、所属项目
- 记录可编辑、可删除
- 每条记录字段：id、项目id、类型(收入/支出)、金额(分，存 Long 避免浮点误差)、时间戳、对方/商品名、备注、来源(manual/wechat/alipay)、source_id(外部单号，用于去重)

### 3. 微信/支付宝账单 CSV 导入（核心功能，解析要稳）
入口在项目详情页：「导入账单」。用系统文件选择器（ACTION_OPEN_DOCUMENT，mime 用 `*/*` 再按扩展名 .csv/.txt 过滤，因为文件管理器经常给 octet-stream）。

解析要求（两种格式都要支持，注意**编码自动识别**）：
- 编码：依次尝试——BOM 判断 UTF-8/UTF-16LE；否则字节严格 UTF-8 校验，失败则按 GBK 解
- 找表头行：不假设在第一行，逐行扫描直到出现已知列名组合（微信含「交易时间」「收/支」；支付宝含「交易号」或「交易创建时间」「收/支」）
- 尾部的统计说明行（含「微信支付」「本数据由」等非数据行）自然解析失败就跳过，不许崩

**微信格式**（导出文件名类似 微信支付账单(xxx).csv，列：交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注）：
- 时间 `2024-03-01 12:33:11`；金额可能带 `¥` 前缀；收/支列：`收入`/`支出`，其他值（如 `/`）跳过
- source_id 用「交易单号」

**支付宝格式**（导出文件名类似 alipay_record_xxx.csv，典型列：交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注,资金状态；新版本列略有增减、可能没有前导说明行，按表头名取列，别按固定下标）：
- 金额是纯数字；收/支列：`收入`/`支出`，`不计收支` 跳过
- 只导入交易状态含「成功」的行（交易成功/还款成功/退款成功等），其他（交易关闭/等待付款）跳过
- source_id 用「交易号」

**导入流程**：选文件 → 解析 → 预览页（列表带勾选框，默认勾选命中本项目关键词（对方+商品名 contains 任一关键词）的行，其余不勾；顶部显示 已选笔数/金额合计，支持全选/全不选）→ 确认入库。
**去重**：数据库对 (来源, source_id) 建唯一索引，冲突跳过；导入完成弹结果（新增 N 条，跳过重复 M 条）。

### 4. 统计与图表
- **总览页**（首页）：
  - 顶部「总盈亏」大卡片：所有项目 收入合计-支出合计（正数绿色↑，负数红色↓，同时显示总收入/总支出）
  - **总累计曲线**：全部记录按日累计净额（收入-支出累加）折线图，X 轴日期（起止标注），Y 轴金额；画零轴基线，零轴上下区域/线条用绿红两色区分；无数据时显示空状态提示
  - 项目卡片列表：图标+名称+净盈亏（+收入/支出小字），点进项目详情
  - 右下角浮动按钮：新建项目；顶栏按钮：记一笔
- **项目详情页**：项目名+图标、本项目总盈亏（收入/支出）、本项目累计曲线（同上样式）、按钮（导入账单/记一笔）、流水列表按月倒序分组（月头显示当月小计）
- 曲线图实现要点（Canvas）：对每日净额做前缀和得到序列；点数很多时抽样/取极值压缩到 ~200 点内；空隙日补 0 净额保持连续；坐标轴画少量刻度（首末日期、Y 最大最小值）；线条用 cubic 平滑或折线均可，但必须好看

### 5. 其他
- 删除项目时弹确认（项目下记录一并删除，Room 外键 CASCADE）
- 金额展示统一 ¥xx.xx
- 支持深色主题跟随系统

## 构建环境（本机限制，务必遵守）

服务器：4 核 / 3.8G 内存 / 磁盘剩约 15G。已装 openjdk 25（**对 AGP 太新**），需要：
1. `apt-get update && apt-get install -y openjdk-17-jdk-headless unzip wget`，构建时 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
2. Android SDK 装到 `/opt/android-sdk`：下载 cmdline-tools（https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip 或更新版），`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`，`yes | sdkmanager --licenses`
3. 项目 `/root/ledger-app/local.properties` 写 `sdk.dir=/opt/android-sdk`
4. Gradle 用 wrapper，distributionUrl 指向 gradle-8.12-bin.zip
5. `gradle.properties` 必须限制内存：
   ```
   org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
   org.gradle.parallel=false
   org.gradle.workers.max=2
   kotlin.daemon.jvmargs=-Xmx1536m
   android.useAndroidX=true
   ```
6. 版本组合：AGP 8.7.x + Kotlin 2.0.x + Compose BOM 2024.10.x（用 compose plugin `org.jetbrains.kotlin.plugin.compose`），Room 2.6.1 + ksp

## 交付物（必须全部完成）

1. `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**，APK 复制为 `/root/ledger-app/ledger-debug.apk`
2. release 版（`isMinifyEnabled=false`，生成一个本地自签 keystore 签名，放 `app/release.keystore`，密码写进 gradle 文件即可）→ `/root/ledger-app/ledger.apk`（**最终交付文件**）
3. 用 build-tools 的 aapt 验证两个 APK：`aapt dump badging` 能读出 package 和 application-label，并把输出存到 `/root/ledger-app/apk-verify.txt`
4. 打包源码：`cd /root && zip -r ledger-app-source.zip ledger-app -x "ledger-app/.gradle/*" "ledger-app/app/build/*" "ledger-app/build/*"`
5. 写 `/root/ledger-app/README.md`：安装方法（传手机 sideload）、微信账单导出路径（微信 我→服务→钱包→账单→右上角常见问题→下载账单，用于个人对账，邮箱收取 zip 解压得 csv）、支付宝导出路径（支付宝 我的→账单→右上角…→开具交易流水明细）、以及已知限制
6. 写 `/root/ledger-app/RESULT.md`：构建结果、APK 路径、验证输出摘要、已知限制

## 自检要求

构建失败必须迭代修复直到成功（内存不足就调小堆/关 daemon：`./gradlew --no-daemon`，Kotlin 编译挂就再降 Xmx）。最终检查交付物 1-6 全部存在才算完成。不要用模拟器测试（本机没有 KVM 也不用装），以编译通过 + aapt 验证为准，代码正确性靠认真写。

现在开始。先建好 `/root/ledger-app` 目录结构，再装环境、写代码、构建，最后按顺序核验交付物并写 RESULT.md。
