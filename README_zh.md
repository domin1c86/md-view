# MdView

面向 Android 的极简 Markdown 阅读与编辑器。

打开应用先看到的是**文档面板**：以卡片列出你此前打开过的文档，附带标题和正文开头几行。底部有三个标签页：

- **最近** — 打开过的全部文档，按时间倒序，上方是你的文件夹。
- **收藏** — 你加过星的那些。
- **我的** — 主题、皮肤、语言、阅读字号与图片加载。

点击卡片进入文档本身，仍是两种模式：

- **预览（Preview）** — 将文档渲染为排版后的可读文本。
- **源码（Source）** — 编辑原始 Markdown 的纯文本编辑器，保存回同一文件。

无云同步、无导出、无格式化工具栏、无语法高亮。

## 工作原理

文件通过 Storage Access Framework 打开，因此应用不需要存储权限 — 你在系统选择器里选中的文件即授权。应用还注册了 `ACTION_VIEW`，可从文件管理器直接打开 `.md` 文件。

面板列的是**你打开过的文档**，而不是文件浏览器：选择器一次只授权一个文件，本来也没什么可浏览的。给文档加星还会让它免于 50 条上限的淘汰，移除卡片则会把对应的访问授权交还系统。

**文件夹只在应用内部分组。**「最近」列表上方那排标签，是 MdView 记在自己索引里的标记 —— 新建一个不会创建任何目录，把文档移进去也不会移动文件。所有文件都仍留在你当初打开它们的位置。点击标签只看归入其中的文档，再点一次（或按返回）即可看全部，长按可重命名或删除。删除文件夹绝不会删除文档，它们只是不再被归类。已归类的文档同样留在「最近」里，就像加星的那样，并出于同样的理由免于 50 条上限的淘汰。

打开某个文件夹时，**+** 会在打开所选文档的同时，把它归入该文件夹。

Markdown 由 [commonmark-java](https://github.com/commonmark/commonmark-java) 解析，并启用 GitHub 表格、删除线、自动链接扩展以及 YAML front matter，再渲染为原生 Jetpack Compose 组件 — 不用 WebView，也不做 `TextView` 互操作。

**未保存的编辑会自动写入应用私有存储。** Android 会不经警告杀掉后台进程，因此停止输入半秒后会把缓冲区写入草稿，应用进入后台时再写一次。重新打开文档时草稿会恢复，并提供 **Discard（丢弃）** 按钮，以防你并不想保留。

**文件按原样写回。** 编辑时剥离字节顺序标记（BOM），保存时恢复；CRLF 换行在往返后仍保留，因此记事本写出来的文档，对当初的工具来说看起来仍未被改动。

| 区域 | 位置 |
|:--|:--|
| 解析 | `app/src/main/java/com/mdview/markdown/MarkdownParser.kt` |
| 块级渲染 | `.../markdown/MarkdownRenderer.kt` |
| 行内渲染 | `.../markdown/InlineRenderer.kt` |
| 编码与换行 | `.../markdown/DocumentCodec.kt` |
| 卡片标题与摘要 | `.../markdown/DocumentSummary.kt` |
| 文件 I/O（SAF） | `.../data/DocumentRepository.kt` |
| 自动保存草稿 | `.../data/DraftStore.kt` |
| 最近、收藏与文件夹 | `.../data/LibraryStore.kt` |
| 偏好设置 | `.../data/SettingsStore.kt` |
| 应用内语言 | `.../data/AppLocale.kt` |
| 状态与导航 | `.../MainViewModel.kt` |
| 颜色、圆角与字体令牌 | `.../ui/theme/Skin.kt` |
| 缓动曲线与时长 | `.../ui/theme/Motion.kt` |
| 菜单、对话框与消息条 | `.../ui/SkinOverlays.kt` |
| 文档面板 | `.../ui/dashboard/` |
| 文档界面 | `.../ui/` |

### 设置

**我的**标签页有以下偏好，点选即刻生效：

| 设置 | 说明 |
|:--|:--|
| 主题 | 跟随系统／浅色／深色，与设备当前设置无关 |
| 浅色皮肤、深色皮肤 | 两种模式各自使用哪个皮肤，见下文 |
| 从壁纸取色 | Material You。会覆盖两个皮肤；默认关闭，Android 12 以下会隐藏，因为那里本就无效 |
| 语言 | 跟随系统／English／简体中文。Android 13+ 上这与系统「应用 → 语言」里的是同一项设置 |
| 字号 | 只放大文档、编辑器与代码块，不影响应用自身控件 |
| 加载网络图片 | 从不／仅在不计流量的网络下／始终 |

### 皮肤

皮肤是整套配色：不只是强调色，还包括代码块底色、引用竖线、表格线、链接，以及构成整个界面的四个表面层级。应用内置八套 —— 浅色的 Paper、Cobalt、Sepia、Solarized Light，深色的 Ink、Midnight、Nord、Solarized Dark —— 每一套的对比度都由自动化测试守住 WCAG AA。

你为浅色模式和深色模式各选一个皮肤，因此*跟随系统*切换的是你选定的这两个，而不是两套固定主题。

你也可以**导入自己的皮肤**（JSON 文件），入口在 我的 → 导入皮肤…。
[`docs/SKINS_zh.md`](docs/SKINS_zh.md) 是完整规范：每个颜色令牌及其绘制者、排版与形状规则、
完整示例，以及各项校验限制。

### 图片

图片通过 [Coil](https://coil-kt.github.io/coil/) 拉取，因此应用声明了 `INTERNET`。**远程**图片是否加载取决于上面那项设置；`data:`、`content:`、`file:` 图片始终渲染，因为它们本来就不出设备。

**与文档放在一起的图片** —— `./img/diagram.png`、`../assets/logo.png` —— 需要多一步，
因为通过选择器打开文档只授予该文件的访问权，而不是周围文件夹。第一张这样的图片会主动提出解决：
点按它，选择一个存放图片的文件夹。**图片所在的文件夹、或它上面的任意一层，都可以** —— 授权
`img/` 就够了，授权文档自身所在的文件夹同样可以。只有以 `/` 开头的路径是例外：它从所授予文件夹的
根目录起算，这正是 Hugo、Jekyll 与 MkDocs 采用的约定，因此它需要一个包含该文档的文件夹。

该授权会被记住，对其中的所有文档都有效，并列在 **我的 → 图片文件夹** 中，可随时收回。
另有两点限制值得知道：

- 只有独占一行的图片才会渲染为真正的图片。嵌在句子里的仍显示为 `[image: alt]` 占位符。
- 相对路径不支持云端提供方。Google Drive 之类给文档的是不透明 id，没有可供路径衡量的目录结构，
  因此这类图片会如实说明，而不是去猜。

## 构建

用 Android Studio 打开项目并按 Run 即可 — 无需其他步骤。

在项目根目录的终端（PowerShell 或 `cmd`）中：

```bat
gradlew.bat assembleDebug          :: APK -> app\build\outputs\apk\debug\
gradlew.bat testDebugUnitTest      :: JVM tests: parser, inline renderer, codec, drafts
gradlew.bat lintDebug
gradlew.bat assembleRelease        :: minified, unsigned
gradlew.bat pixelApi34DebugAndroidTest   :: boots a managed emulator, no device needed
```

单个测试类或方法：

```bat
gradlew.bat testDebugUnitTest --tests "com.mdview.markdown.DocumentCodecTest"
gradlew.bat testDebugUnitTest --tests "*.DocumentCodecTest.byteOrderMarkIsStripped"
gradlew.bat pixelApi34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mdview.MainViewModelTest
```

产物位置：

| 输出 | 路径 |
|:--|:--|
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Release APK | `app\build\outputs\apk\release\app-release-unsigned.apk` |
| 单元测试报告 | `app\build\reports\tests\testDebugUnitTest\index.html` |
| 仪器化测试报告 | `app\build\reports\androidTests\managedDevice\debug\index.html` |
| Lint 报告 | `app\build\reports\lint-results-debug.html` |

`local.properties` 已被 gitignore，必须把 `sdk.dir` 指到 SDK，并按 properties 文件要求转义：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

安装到正在运行的模拟器，或已开启 USB 调试的真机：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

有两条 lint 警告是预期且刻意的：`OldTargetApi`，因为 `targetSdk` 保持为 36，而所用的 androidx 版本迫使 `compileSdk` 为 37；以及 `mipmap-anydpi-v26` 上的 `ObsoleteSdkInt`，其 `-v26` 限定符若去掉，AAPT2 会丢失启动器图标。

### Release 构建

`assembleRelease` 会跑带资源压缩的 R8，并产出**未签名** APK — 仓库中未提交 keystore。若要安装做测试，用本地 debug 密钥签名：

```bat
apksigner sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android ^
  --ks-key-alias androiddebugkey --key-pass pass:android ^
  --out app-release-signed.apk ^
  app\build\outputs\apk\release\app-release-unsigned.apk
```

`apksigner` 位于 `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\`。

### 附录：从 WSL 构建

仅当仓库在 WSL 文件系统上，而 JDK 与 SDK 在 Windows 一侧时才相关。Gradle 无法就地构建：作为 Windows 进程，它通过 `\\wsl.localhost\...` UNC 路径看到项目，文件哈希会失败并报 *"The function is incorrect"*。`cmd.exe` 也无法 `cd` 进 UNC 路径，因此用不了 `gradlew.bat`。

`tools/wbuild.sh` 绕过了这两点。它用 `rsync` 把源码镜像到原生 Windows 目录，再直接调用 Android Studio 自带 JBR 里的 `java.exe`。镜像可随时丢弃；本仓库仍是唯一真相来源。

```sh
tools/wbuild.sh assembleDebug
tools/wbuild.sh testDebugUnitTest
```

`MDVIEW_BUILD_DIR` 可改镜像位置。构建产物会复制回 `build-outputs/`，但报告不会 — 报告留在镜像里，路径即 Gradle 打印的 Windows 路径。`build-outputs/` 与 `MDVIEW_BUILD_DIR` 都只与 WSL 有关，在 Windows 上构建时无意义。

## 版本

AGP 9.3.1（自身提供 Kotlin — 独立的 `kotlin-android` 插件会被拒绝）、Gradle 9.6.1、Compose BOM 2026.06.01、commonmark 0.29.0、Coil 3.5.0、`compileSdk` 37、`minSdk` 26、`targetSdk` 36。

没有引入导航库、DataStore、Room 或序列化库：导航就是在两个目标之间的一个 `when`，两个存储都是手写编解码器加普通文件。
