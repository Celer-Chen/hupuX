# 安全待办

> 本文记录已识别、但暂未处理的安全问题。处理时间由维护者决定。

## 1. 登录 Cookie 明文存储（待处理）

**现状**

- `app/src/main/java/com/hupux/data/local/CookiePreferences.kt` 将 `manual_cookie`、`webview_cookie` 明文写入 `SharedPreferences("hupu_prefs", MODE_PRIVATE)`。
- `app/src/main/AndroidManifest.xml` 中 `android:allowBackup="true"`，系统备份可能包含该 SharedPreferences 文件。
- 设置页的“手动 Cookie”输入框以明文展示，输入过程也可能被输入法/截屏记录。
- Cookie 同时会被注入 WebView 的 `CookieManager`。

**风险**

- 虎扑 Cookie 等同于账号登录凭证；root 设备、调试备份、备份提取工具或同签名应用可能读取到明文。
- 用户丢失手机后，备份恢复/取证工具可还原登录态。

**建议修复方案**

1. 使用 AndroidX Security Crypto 的 `EncryptedSharedPreferences`（AES256-GCM，密钥存 Android Keystore）保存 Cookie。
2. 关闭 `android:allowBackup`，或通过 `backup_rules.xml` / `data_extraction_rules.xml` 排除 `hupu_prefs`。
3. 清除登录时同时清理 SharedPreferences 和 `CookieManager` 中的虎扑 Cookie。
4. 设置页手动 Cookie 输入框改为密码态，提供“显示/隐藏”切换。
5. 可选：升级旧版本时迁移旧明文数据，或直接安全清除并要求重新登录。

**验收标准**

- 备份提取或反编译应用数据目录无法得到明文 Cookie；
- WebView 登录、手动 Cookie 登录、自动登录、清除登录行为保持不变；
- 旧版本升级后不会因旧明文数据残留而继续泄露。

**涉及文件**

- `app/src/main/java/com/hupux/data/local/CookiePreferences.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/hupux/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/hupux/ui/profile/LoginWebViewScreen.kt`

**状态**：待处理（未排期，2026-10-23 记录）
