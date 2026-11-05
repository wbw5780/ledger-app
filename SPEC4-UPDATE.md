# 第四轮任务：应用内自动更新（更新源 = GitHub Releases，只此一个）

给这个侧载 App 加自动更新：App 内检查 GitHub Releases、有新版弹窗（更新日志=release notes）、应用内下载 APK（进度条）→ 大小校验 → 拉起系统安装器。**更新源只有 GitHub，不做任何镜像兜底。**

服务器 `~/.git-credentials` 里的 GitHub 账号 `wbw5780` 的 PAT 已验证有 `repo` scope（gist, repo, workflow），用它建仓库和发 release。**脚本里读 token 时不要把 token 打印到日志/输出里。**

## 1. 服务器：建仓库 + 推源码

1. 用 API 建公开仓库 `wbw5780/ledger-app`（public——App 端要免 token 匿名访问 Releases API 和下载资产）：
   `curl -u wbw5780:$TOKEN -d '{"name":"ledger-app","public":true ...}' https://api.github.com/user/repos`（参数按 API 文档写对即可）
2. `/root/ledger-app` 里 `git init` + 提交全部源码并推送到 main。`.gitignore` 必须排除：`.gradle/`、`build/`、`app/build/`、`local.properties`、`*.apk`、以及 **`app/release.keystore`（签名私钥不入公开仓库，只留在服务器上，README 里注明一句"签名 keystore 保存在构建服务器"）**
3. 写清初次提交信息（如 "账本App：项目记账+微信/支付宝账单导入(PDF/CSV)+统计曲线+自动更新"）

## 2. 服务器：发布脚本 `/root/publish-ledger.sh`（chmod +x）

用途：把 /root/ledger-app/ledger.apk 发布为新版本。步骤：
1. `/opt/android-sdk/build-tools/35.0.0/aapt dump badging` 取 versionCode / versionName
2. 若已存在 `tag_name == versionCode` 的 release 先删掉（DELETE release + DELETE refs/tags/<tag>），保证可重复发布
3. 创建 release：`tag_name=<versionCode>`、`name=v<versionName>`、`body=$1`（changelog，脚本参数，给默认值）
4. 上传 `ledger.apk` 资产（asset name 就叫 `ledger.apk`，Content-Type: application/vnd.android-package-archive）
5. 自检：`curl -s https://api.github.com/repos/wbw5780/ledger-app/releases/latest` 确认 tag/asset/size 正确并打印结果（**不打印 token**）

## 3. App 端（versionCode 4，versionName "1.3"）

### 清单与权限
- 加 `INTERNET`、`REQUEST_INSTALL_PACKAGES` 权限；**全 HTTPS，不需要 usesCleartextTraffic**
- 注册 FileProvider（`androidx.core.content.FileProvider`，authority `${applicationId}.fileprovider`），`res/xml/file_paths.xml` 含 `<cache-path name="update" path="updates/"/>`

### 更新模块（新文件 UpdateChecker.kt，放 ui/ 或 util/）
- 常量：`REPO_API_LATEST = "https://api.github.com/repos/wbw5780/ledger-app/releases/latest"`，资产名 `ledger.apk`
- 请求带 `Accept: application/vnd.github+json`，HttpURLConnection（连接/读取超时 10s，**不引入 OkHttp**），协程执行
- 解析 JSON（org.json 即可，别加依赖）：`tag_name`（数字字符串=versionCode）、`name`（展示名）、`body`（changelog）、`assets[]` 里 name==`ledger.apk` 的 `browser_download_url` 和 `size`
- 与本地 `PackageManager.getPackageInfo(...).longVersionCode` 比较，tag 数字更大才提示
- 下载：GET browser_download_url（https 跨主机 302，HttpURLConnection 会自动跟随；不要带任何 Authorization 头），流式写 `cacheDir/updates/ledger.apk`，进度回调（百分比、已下载/总 MB）；完成后校验文件大小==asset size，不符则删除文件并提示「下载不完整，请重试」
- 安装：FileProvider content URI + `Intent(ACTION_VIEW)` type `application/vnd.android-package-archive` + `FLAG_GRANT_READ_URI_PERMISSION`
- Android 8+ 首次：`canRequestPackageInstalls()` 为 false 时弹说明，按钮跳 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`（带本应用 package uri），返回后重试
- 一切网络错误：启动静默检查时无声吞掉；手动检查时 toast「检查更新失败，请检查网络（需能访问 GitHub）」

### UI 接入（风格沿用第三轮组件）
- 启动进入主界面后延迟 2s 静默检查一次，仅当有新版本才弹窗
- 更新弹窗：标题「发现新版本 v<versionName>」+ changelog 正文 +「以后再说」/「立即更新」
- 下载弹窗：进度条 + 百分比 + MB + 「取消」（取消即终止下载并删临时文件）；完成自动拉安装器
- 「我的」页：版本行保留，加「检查更新」入口（检查中/已是最新/发现新版/失败可重试 四态）

## 4. 交付物与验收

- versionCode 4 / versionName 1.3；签名沿用 `app/release.keystore`（**必须同一个**）；`testDebugUnitTest`、`assembleDebug`、`assembleRelease` 全过
- 更新 ledger.apk / ledger-debug.apk / apk-verify.txt / README（自动更新原理+已知限制：国内直连 GitHub 可能不通需代理、公开仓库不含 keystore）/ RESULT.md（第四轮小节）/ 重打 /root/ledger-app-source.zip（同样排除 keystore）
- 仓库推送成功、发布脚本跑通：**实际执行一次** `/root/publish-ledger.sh "新增应用内自动更新：自动检查 GitHub Releases，应用内下载并引导安装"`，releases/latest 返回 v1.3 且资产 ledger.apk 可匿名 curl 下载（服务器上验证完整下载无 4xx/5xx）
- 顺序建议：先服务器建仓推码 → 再写 App 更新模块 → 构建出包 → 发布 → 核验
