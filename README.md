# Hanime ATV

非官方 **Android TV** 客户端，内容来自 [hanime1.me](https://hanime1.me)。界面为遥控器（D-pad）操作设计，支持**手机扫码登录**（含 Google 登录引导）、分类/标签/排序筛选、Media3 ExoPlayer 播放，并支持**基于 GitHub Releases 的应用内更新**。

> ⚠️ 本项目仅供个人学习与技术研究，请遵守当地法律法规，所有内容版权归原站点及作者所有。

## 1.1.x 更新要点

- **1.1.2 修复**：分类页/标签页没数据——站点 `?genre=`、`?tags[]=` 的结果页用的是另一套卡片结构（`div.search-videos` + `.home-rows-videos-title`），旧解析器只认 `.video-item-container`，已补上回退解析并加了解析回归测试。
- **1.1.2 修复**：Google 登录不再自动在电视 WebView 里打开（Google 会拒绝 WebView，容易看到 404/403 错误页）；现在会在界面上显示真实的 HTTP 错误，并给出两条可用路径——手机扫码完成 Google 登录 + **一键同步书签**，或 Cookie 导入。
- **修复国产电视（小米/Redmi 等）界面被放大、错位**：不少电视把 1080p 面板上报成 480dpi，逻辑尺寸只有 640×360dp，导致选项框巨大、多出边框、竖排错位、字体和进度条过大。现在统一按 Android TV 的 960×540dp 逻辑尺寸归一化，并在设置里提供 80%~120% 界面缩放。
- **登录重做，新增手机扫码登录**：电视显示二维码 → 手机扫码打开局域网登录页 → 手机键盘输入账号密码（或跳转手机浏览器完成 Google 授权 / 同步电脑端 Cookie）→ 电视自动同步登录态，无需在电视上打字。
- **新增分类页**：影片类型（裏番/泡麵番/Motion Anime/3DCG/…）、内容标签（多选，按人物/角色/情境/剧情/体位分组）、9 种排序方式。
- **详情页标签可点击筛选**：点任意 tag 直接进入按该标签过滤的列表。
- **播放器界面瘦身**：进度条更细、控制条与字号整体缩小，不再霸屏。
- **应用内更新**：设置 → 版本与更新（或启动时静默检查）从 GitHub Releases 拉取新版本，下载后拉起系统安装器。
- **新图标与 TV banner**：自适应图标 + 各密度 PNG + 启动器 banner。

## 功能

- 首页：精选大图 + 最新上架等多个栏目（与站点首页同步的 SSR 内容解析）
- 分类：类型 / 标签 / 排序 / 关键词组合筛选，分页加载
- 搜索：遥控器屏幕键盘（支持大小写与符号）、搜索历史、热门类型与标签快捷入口
- 详情：封面、简介、可点击标签、作者、相关推荐、清晰度列表
- 播放：Media3 ExoPlayer，硬解优先，清晰度切换、±10s 快进快退、倍速、断点续播
- 账号：扫码手机登录（推荐）、邮箱密码登录、Cookie 导入、电视内 WebView Google 登录（备选）
- 收藏 / 观看历史：登录后同步站点收藏，本地记录观看进度
- 适配电视：横屏、无触屏、Leanback 启动入口、自动隐藏系统栏、密度归一化
- 应用内更新：GitHub Release 版本检查、下载、安装

## 登录说明

站点 hanime1.me 的账号体系是 Cookie 会话，App 无法拿到 Google 的 ID Token，所以：

1. **手机扫码登录（推荐）**
   打开「登录」页会自动启动局域网配对服务（端口 8765 起），电视显示二维码。
   手机与电视连同一个 Wi-Fi，扫码后：
   - 直接在手机上输入站点账号密码，电视端自动登录；
   - **Google 登录**：点「在手机浏览器中打开 Google 登录」完成授权 → 回到扫码页展开「一键同步书签」→ 复制书签代码保存成浏览器书签 → 在 hanime1.me 任意页面点一下该书签，登录态立刻同步到电视（站点的 `hanime1_session` 不是 HttpOnly，所以可以这样迁移；电脑浏览器同样适用）；
   - 也支持把电脑浏览器里已登录的 Cookie 粘贴到手机页面同步。
   配对页只在局域网内可访问，带一次性 6 位配对码，登录成功或 5 分钟无操作后服务自动关闭。

2. **邮箱登录**：电视屏幕键盘现已支持大小写切换与符号页，可输入完整密码；也会显示站点返回的错误原因。

3. **Cookie 导入**：在电脑浏览器登录 hanime1.me → F12 → Network → 任意请求 → 复制请求头 Cookie 值 → 电视上粘贴（或通过手机扫码页同步）。

4. **Google 登录（电视内 WebView，仅作备选）**：Google 基本都会以“浏览器不安全”拒绝 WebView 授权；界面会显示真实 HTTP 状态，方便判断是 Google 拒绝（403）还是站点/网络问题（404 等）。请优先使用上面的手机扫码路径。

## 应用内更新

- 版本号统一放在根目录 `version.properties`（`versionCode` / `versionName`）。
- CI 每次构建会以 `v<versionName>` 作为 tag 发布 Release（例如 `v1.1.0`），并上传 release/debug 两个 APK。
- App 启动时（6 小时内最多一次）与「设置 → 版本与更新」会请求 GitHub `releases/latest`，比较版本号，下载 `*-release.apk` 后调用系统安装器。
- 首次安装需要允许「安装未知来源应用」；系统会弹出授权页，允许后返回再点安装即可。
- 数据库/文件权限使用 `FileProvider`，安装包下载在应用缓存目录。
- **签名**：仓库内 `keystore/hanime-atv.jks` + `keystore.properties` 是固定的自用签名（公开仓库含私钥，仅用于自用分发，切勿用于正式商店）。之前 `build-*` 版本由 CI 临时 debug 签名，与本版本签名不一致，**从旧版本升级需要先卸载一次**；之后再更新即可覆盖安装。

## 构建

### 本地

需要 JDK 17 与 Android SDK（compileSdk 35）。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions

推送到 `main` 分支或手动触发 `Build APK` workflow，会自动：

1. 读取 `version.properties` 的版本号；
2. 编译 debug / release 两个包；
3. 上传 artifact；
4. 创建/更新 `v<versionName>` 的 Release（APK 命名 `HanimeATV-v<version>-release.apk`），可直接下载安装。

> release 包使用 debug 签名以便直接安装分发，请勿用于正式商店发布。

## 安装

```bash
adb install -r HanimeATV-v1.1.0-release.apk
# 或
adb connect <电视IP>:5555 && adb install -r HanimeATV-v1.1.0-release.apk
```

也可以把 APK 拷到 U 盘，使用电视上的文件管理器安装。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）+ 自研 TV 焦点组件
- 画面缩放：全局 `Density` 归一化（960×540dp）
- Media3 ExoPlayer
- OkHttp + Jsoup（站点 HTML 解析）
- Coil（图片）
- NanoHTTPD（手机扫码登录的局域网页面）+ ZXing（二维码）
- 单 Activity + Compose 状态导航

## 已知限制

- 站点有 Cloudflare 防护，部分网络环境（尤其机房/VPN 出口）会直接 403；家庭宽带一般正常。
- 电视内置 WebView 的 Google 授权可能被 Google 拒绝，请优先使用手机扫码登录。
- 扫码登录页面走局域网明文 HTTP，仅建议在家用网络使用；配对码一次性有效，登录完成即关闭服务。
- release 包为 debug 签名，用于自用分发。

## 免责声明

本项目为非官方客户端，仅用于个人学习与技术研究，不对内容来源的合法性负责。所有内容版权归 hanime1.me 及原作者所有。请遵守当地法律法规，合理使用。
