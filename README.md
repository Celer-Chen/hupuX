# 虎扑 X

一个基于 **Kotlin + Jetpack Compose** 的虎扑第三方 Android 客户端。界面简洁、无广告、无推送骚扰。

<table>
  <tr>
    <td><img src="doc/screenshots/home.jpg" width="260" alt="首页：今日比分横条 + 推荐流"></td>
    <td><img src="doc/screenshots/discover.jpg" width="260" alt="发现：关注的专区 + 专区网格"></td>
    <td><img src="doc/screenshots/score.jpg" width="260" alt="评分：赛程与球员评分"></td>
  </tr>
  <tr>
    <td align="center">首页</td>
    <td align="center">发现</td>
    <td align="center">评分</td>
  </tr>
</table>

---

## 功能

> 主要浏览列表（首页三个 Tab、发现、专区、评分、话题、个人列表、消息）均支持下拉刷新。

### 首页
- **推荐**：从虎扑首页聚合热门帖子，顶部图片轮播展示图文内容
- **热榜**：展示虎扑热榜话题（按热度排名），点击话题进入其帖子列表，再点帖子进入详情
- **关注**：汇总所有已关注专区的最新动态
- 右上角：搜索 / 设置入口

### 发现
- 浏览全部专区分类（篮球、足球、综合等）
- **5 列网格排布**：共 243 个专区，网格比列表节省约 5 倍纵向空间；每格 logo 右上角有可点击的关注徽标（红底「+」/ 灰底「✓」）
- 进入专区查看帖子列表，支持加载更多
- **关注专区（本地保存，无需登录）**：点专区 logo 右上角的「+」/「✓」徽标即可关注或取关；首页「关注」Tab 会聚合这些专区的最新帖子
- **发帖（需要登录）**：进入任意专区后显示发帖入口，填写标题和正文即可发布；支持图片 / 视频上传

### 评分
- 首页顶部「今日比分」横条：覆盖西甲/德甲/意甲/法甲/中超等全部联赛（抓虎扑首页内联数据，仅展示不可点）
- 评分页进来**自动定位到今天**的比赛（赛程横跨整季，不定位要滑几个月），当天日期标红
- 评分页 12 个分区看赛程：NBA / CBA / WNBA / CUBA / 英超 / 世界杯 / 网球 / LOL / LPL / LCK / 王者荣耀 / 绝地求生，含双方比分、系列赛大比分、开赛时间
- 点进某场比赛看**全场评分榜**：球员评分、评分人数、评论数，以及得分/篮板/助攻/出场时间等技术统计
- 点进某位球员看**详情页**：平均分、参与人数、技术统计，以及评分区评论
- **打分（需要登录）**：五星制（一星 2 分），可修改也可取消
- **评论（需要登录）**：发评论、回复他人、点亮；评论按时间游标翻页，滑到底自动加载
- 浏览无需登录，只有打分和评论需要

### 搜索
- 内嵌虎扑移动端搜索页（WebView），输入关键词搜索帖子；点结果直接进入原生帖子详情页
- 入口在**首页 / 发现页顶栏的搜索图标**（不占用底部导航位）

### 帖子详情
- 正文完整显示图片（含虎扑自定义 `<center class="hupu-img">` 格式）与视频，使用 WebView 渲染正文，支持 HTML5 `<video>`
- 评论列表：用户头像、楼主标识、引用回复、点赞数、评论内嵌图片
- **盖楼贴分页**：多页帖子默认按页浏览，支持上一页 / 下一页 / 跳页，也可切回连续滚动加载
- **评论排序**：正序 / 倒序 / 最热（按点亮数）
- 点击「X 条回复」展开子回复面板，支持**无限递归嵌套展开**
- **收藏 / 推荐 / 回复 / 点赞（需要登录）**：登录后自动切换桌面版 API 解锁；回复支持插图
- 图片点击全屏查看（双指缩放 / GIF），长按保存至相册

### 我的（需要登录）
- 头像、昵称、等级、地区、注册时长、统计数据
- **我的发帖 / 回帖 / 推荐 / 收藏**：分页加载
- **消息中心**：提到我的 / 评论 / 亮了·推荐，三个分类

### 登录
- WebView 登录（登录成功自动提取 Cookie）
- 设置中直接粘贴从浏览器复制的 Cookie 字符串（进入设置会回显当前 Cookie）

### 设置 → 外观
- **主题**：跟随系统 / 浅色 / 深色
- **纯黑背景**：深色下可开启，AMOLED 屏省电防烧屏
- **显示字号**：小 / 标准 / 大 / 特大四挡，只放大文字不放大图标间距

### 设置 → 关于
- **检查更新**：对比 GitHub 最新 release 的版本号，有新版可一键跳转下载页
  （走 release 的 atom feed 而非 GitHub API——后者对未认证请求限流到每 IP 每小时 60 次）

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| 界面 | Jetpack Compose + Material 3、Navigation Compose、Coil 3、Haze 毛玻璃 |
| 网络 | **Ktor Client**（OkHttp 引擎） |
| JSON 解析 | **kotlinx.serialization** |
| HTML 解析 | **Ksoup**、Jsoup |
| 本地数据库 | SQLDelight（Android driver） |
| 依赖注入 | Koin |
| 时间 | kotlinx-datetime |

### 数据来源

| 接口类型 | 说明 |
|---|---|
| 移动端 SSR（`m.hupu.com`） | 首页、专区、帖子详情，解析 `__NEXT_DATA__` JSON；**未登录默认走此接口** |
| 桌面端 SSR / REST（`bbs.hupu.com`） | **登录后自动切换**：评论列表、子回复、收藏、点赞、推荐、回复、发帖 |
| 桌面端 REST（`my.hupu.com/pcmapi/`） | 个人资料、我的发帖/回帖/推荐、消息中心（需 Cookie） |
| 虎扑图床 + 阿里云 OSS | 发帖 / 回复插图与视频上传（STS 临时凭证直传，OSS V1 手动签名） |

详细 API 文档见 [`doc/api/`](doc/api/README.md)（赛程与评分见 [`match-score.md`](doc/api/match-score.md)）；界面设计规范（颜色 Token / 层级 / 导航 / 网格）见 [`doc/ui-design.md`](doc/ui-design.md)。

---

## 项目结构

```
hupuX/
├── app/                        # Android 应用：UI、ViewModel、DI、平台能力
│   └── src/main/java/com/hupux/
│       ├── data/               # Cookie 存储、图片/视频上传、SQLite 回调
│       ├── di/                 # Koin 依赖装配
│       ├── ui/                 # 首页 / 发现 / 评分 / 帖子 / 我的 / 设置
│       └── MainActivity.kt
├── shared/                     # Android library：数据层与虎扑接口解析
│   └── src/main/
│       ├── java/com/hupux/data/
│       │   ├── model/          # 数据模型
│       │   ├── repository/     # 数据仓库层
│       │   └── scraper/        # HupuScraper（移动端）、HupuDesktopScraper（桌面版接口）
│       └── sqldelight/         # FollowedZones 表定义
└── doc/api/                    # 接口文档
```

---

## 环境要求

| 项 | 要求 |
|---|---|
| Android | Android Studio（Ladybug 或更新版本）+ JDK 17 |
| SDK | minSdk 26（Android 8.0）/ targetSdk 35 |

---

## 构建与运行

```bash
git clone https://github.com/Celer-Chen/hupuX.git
cd hupuX

# 构建 Debug 包（APK 位于 app/build/outputs/apk/debug/）
./gradlew :app:assembleDebug

# 构建 Release 包（APK 位于 app/build/outputs/apk/release/）
./gradlew :app:assembleRelease
```

Android Studio 直接打开项目根目录即可运行。

---

## 发布流程（CI）

推送到 `main` 后由 GitHub Actions 全自动完成，**不需要手动改版本号**：

1. 纯文档改动（`**.md` / `doc/**` / 仓库配图）**不触发** CI；
2. 触发后先算出新版本号（默认 patch +1，如本次推送自己改了 `appVersion` 则以手动值为准）；
3. 用该版本号构建 Android Release APK；
4. 构建成功后把版本号写回 `gradle.properties`，并发布 GitHub Release `v<版本号>`。
   构建失败则仓库保持不变，不会留下没有 release 的版本号。

想发 minor/major 版本时，手动把 `gradle.properties` 的 `appVersion` 改成目标版本再推即可
（`appVersionCode` 忘了改也没关系，CI 会自动 +1，保证 Android 能覆盖安装）。

> ⚠️ 提交信息里**不要出现方括号包起来的 skip-ci 指令**（`skip ci` / `ci skip` 那两种写法）。
> GitHub 见到就会跳过整条流水线，于是既不构建也不发版——连"解释这个机制"时顺手写进提交信息
> 都会中招。CI 自己回写版本号的那条提交是故意带它的，用来防止递归触发。

---

## 免责声明

本项目为个人学习用途，数据来源于虎扑（hupu.com）公开页面。请勿用于任何商业目的。

安全相关待办（如 Cookie 加密存储）见 [`doc/security-todo.md`](doc/security-todo.md)。
