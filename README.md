# Hanime ATV

非官方 **Android TV** 客户端，内容来自 [hanime1.me](https://hanime1.me)。界面为遥控器（D-pad）操作设计，支持网页账号登录与 Google 登录，使用 Media3 ExoPlayer 播放。

> ⚠️ 本项目仅供个人学习与技术研究，请遵守当地法律法规，所有内容版权归原站点及作者所有。

## 功能

- 首页：精选大图 + 最新上架等多个栏目（与站点首页同步的 SSR 内容解析）
- 搜索：遥控器屏幕键盘输入，支持分页加载、标签/作者快捷搜索
- 详情：封面、简介、标签、作者、相关推荐、清晰度选择
- 播放：Media3 ExoPlayer，硬解优先，清晰度切换、±10s 快进快退、倍速、断点续播
- 账号：邮箱密码登录（原生表单，遥控器可输入）、Google 登录（内置 WebView）、Cookie 粘贴登录
- 收藏 / 观看历史：登录后同步站点收藏，本地记录观看进度
- 适配电视：横屏、无触屏、Leanback 启动入口、自动隐藏系统栏

## 构建

### 本地

需要 JDK 17 与 Android SDK（compileSdk 35）。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions

推送到 `main` 分支或手动触发 `Build APK` workflow，会自动：

1. 编译 debug / release 两个包；
2. 上传 artifact；
3. 创建以 `build-<run_number>` 为 tag 的 Release，可直接下载 APK 安装。

> release 包使用 debug 签名以便直接安装分发，请勿用于正式商店发布。

## 安装

```bash
adb install -r HanimeATV-release.apk
# 或
adb connect <电视IP>:5555 && adb install -r HanimeATV-release.apk
```

也可以把 APK 拷到 U 盘，使用电视上的文件管理器安装。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）+ 自研 TV 焦点组件
- Media3 ExoPlayer
- OkHttp + Jsoup（站点 HTML 解析）
- Coil（图片）
- 单 Activity + Compose 状态导航

## 登录说明

- **邮箱登录**：直接在 App 内输入账号密码，走站点 `/login` 表单。
- **Google 登录**：内置 WebView 打开站点 `/auth/google`，在电视上使用遥控器/外接键盘完成 Google 授权。
- **Cookie 登录**：在电脑浏览器登录后复制 Cookie，粘贴到 App 内（适合电视上输入不便时使用）。

登录态通过 `CookieManager` 同步到 OkHttp，App 重启后保持。
