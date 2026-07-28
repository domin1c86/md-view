# 交接 — 需要你亲自动手的部分

生产基线和文档面板都已实现并提交。接下来只有替你完成不了的事：需要 Windows 机器、需要人去点系统文件选择器，或需要你本人拍板的决定。

参考环境是 **Windows**。以下命令均在项目根目录下用 `cmd`/PowerShell 执行。

---

## 1. 一次性设置

`local.properties` 已被 gitignore，克隆仓库时不会带上。请按 `.properties` 文件要求转义，用你自己的 SDK 路径创建它：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

第一次用 Android Studio 打开项目时会自动写好。除此之外什么都不用配 — 不需要 keystore，也不需要环境变量。

---

## 2. 在 Windows 上重跑自动化检查

这些检查在这边都已通过（经 WSL 镜像、`tools/wbuild.sh`），但从未在原生 Windows 路径上跑过。重跑正是为了这一点。

```bat
.\gradlew.bat clean assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
.\gradlew.bat pixelApi34DebugAndroidTest
```

预期结果：

| 命令 | 预期 |
|:--|:--|
| `testDebugUnitTest` | 8 个类共 **82 个测试，0 失败** |
| `pixelApi34DebugAndroidTest` | **73 个测试，0 失败**（会自行启动托管模拟器 — 不需要真机） |
| `lintDebug` | **0 错误，恰好 2 条警告**：`OldTargetApi` 与 `ObsoleteSdkInt` |
| `assembleRelease` | 成功，未签名 APK 约 **1.7 MB** |

还定义了一台设备，但这边从未跑过 —— 它的系统镜像需要接受许可协议，而那不该由我替你接受：

```bat
sdkmanager.bat --licenses
gradlew.bat pixelApi30DebugAndroidTest
```

`pixelApi30` 是 Android 13 以前那条语言路径的唯一覆盖 —— API 33 以上由系统自己处理应用内语言，所以 API 34 根本不会走到应用自行包装 Context 的那段代码。值得跑一次。

两条 lint 警告是刻意保留的，说明见 `CLAUDE.md`。**再多出第三条警告就是回归** — 检查点是这一点，而不是警告条数本身。

报告路径：

```
app\build\reports\tests\testDebugUnitTest\index.html
app\build\reports\androidTests\managedDevice\debug\index.html
app\build\reports\lint-results-debug.txt
```

---

## 3. 手工验证 — 需要真人

系统文件选择器无法由仪器化测试驱动，因此所有以「打开文件」开头的路径都未经 CI 验证，必须亲手走一遍。

### 先准备测试文件

用 **记事本**（不要用 VS Code — 记事本才会产出本项要测的 CRLF + BOM 组合），将下列内容保存为 `fixture.md`，编码选 **UTF-8 with BOM**：

````markdown
---
title: Fixture
tags: [round, trip]
---

# Heading one

Some **bold**, *italic*, ~~struck~~ and `code`.

- first
- second
  - nested

1. one
2. two

> Quoted line.

```kotlin
fun main() = Unit
```

| Left | Center | Right |
|:-----|:------:|------:|
| a    | b      | c     |

![remote image](https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Example.jpg/320px-Example.jpg)

[A link](https://example.com)
````

安装并启动：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 检查清单

| # | 操作 | 预期 |
|:--|:--|:--|
| 1 | 通过应用的「打开」操作打开 `fixture.md` | Front matter **完全不渲染** — 「Heading one」上方不会出现虚假的「title: Fixture」标题 |
| 2 | 看第一个标题 | 「Heading one」按标题样式显示。若 BOM 泄漏进来，会变成普通纯文本 |
| 3 | 滚到图片 | 能加载（需要网络）。坏 URL 应显示失败提示，而不是崩溃 |
| 4 | 检查表格与代码块 | 三列，左/中/右对齐；`fun main() = Unit` 上方有 `kotlin` 标签 |
| 5 | 点链接 | 在浏览器中打开 |
| 6 | 切到源码模式，输入内容，点撤销再重做 | 撤销/重做按钮**仅**在源码模式出现；两者都可用 |
| 7 | 预览滚到下方，切到源码再切回 | 预览仍停在刚才的位置 |
| 8 | 两种模式下都旋转设备 | 滚动位置与文本都保留 |
| 9 | 保存，再用记事本重新打开 `fixture.md` | 仍是 CRLF，仍有 BOM。文件大小应恰好增加你输入的那些字符 |
| 10 | 输入一处修改，按 HOME，再执行 `adb shell am force-stop com.mdview`，重新打开应用 | 修改**还在**，标题出现 `•`，snackbar 显示「Restored unsaved changes」，并带有 **Discard** 操作 |
| 11 | 点 Discard | 缓冲区恢复为磁盘内容，`•` 消失 |
| 12 | 从文件管理器打开 `.md`（「打开方式」→ MdView） | 文档直接加载，不出现空状态 |
| 13 | 打开一个大的非 Markdown 文件（把 `.jpg` 改名为 `.md`，或任意 > 2 MB 的文件） | 出现可读的错误信息，而不是 `java.io.IOException` 这类类名 |

### 文档面板与设置

| # | 操作 | 预期 |
|:--|:--|:--|
| 14 | 全新启动应用 | 停在**最近**标签页，列表为空，底部是 最近／收藏／我的 |
| 15 | 打开 `fixture.md`，然后按返回 | 出现一张标题为 **Fixture** 的卡片（取自 front matter，而非文件名），带摘要和「fixture.md · 刚刚」 |
| 16 | 再打开一个文档，然后返回 | 两张卡片，最近打开的在上 |
| 17 | 给其中一张加星，切到**收藏** | 只列出那一个；它在「最近」里也还在 |
| 18 | force-stop 后重新启动 | 两张卡片和星标都还在 |
| 19 | 长按卡片 → **从列表中移除** | 该卡片消失，另一张保留 |
| 20 | 打开文档，输入但不保存，按返回 | 「最近」顶部出现蓝色的**未保存的草稿**卡片。点它 —— 文字还在 |
| 21 | 在浅色系统上选 **我的 → 主题 → 深色** | 整个应用变深色。注意冷启动时有没有**白闪**，以及状态栏图标是否仍看得清 |
| 22 | **我的 → 字号 → 大** | 文档和编辑器变大；顶栏和底部标签**不变** |
| 23 | **我的 → 加载网络图片 → 从不**，再打开 `fixture.md` | 原本是图片的位置显示「未加载图片」 |
| 24 | **我的 → 语言 → English** | 全部切换，**包括「刚刚」／「2 小时前」这类时间**，并且仍停在「我的」标签页，而不是被弹回「最近」 |
| 25 | 切换语言后，打开一个改过但没保存的文档 | 未保存的内容还在 —— 切语言会重建 Activity，这不该让你丢任何东西 |
| 26 | Android 13+：设置 → 应用 → MdView → 语言 | 列表里有 MdView，可选 English 和简体中文。在那里改，应用应当跟着变 |

### 皮肤与自适应外壳

| # | 操作 | 预期 |
|:--|:--|:--|
| 27 | **我的** → 滚动到**浅色皮肤**／**深色皮肤** | 两个画廊共八个缩略图，各自以本皮肤的配色预览，当前使用的那个带勾 |
| 28 | 浅色选 **Cobalt**、深色选 **Midnight**，再在主题里切换浅色／深色 | 整个应用都变，*包括代码块、表格、引用竖线和链接* —— 而不只是栏和按钮。这一处最容易出问题 |
| 29 | 在 Midnight 下冷启动（`adb shell am force-stop com.mdview` 后重开） | 第一帧之前**没有浅色闪烁**。窗口背景现在取自当前皮肤，此处原先是两个写死的常量 |
| 30 | 打开**从壁纸取色**（Android 12+） | 两个皮肤画廊变暗，并给出说明文字。文档（含代码块）跟随壁纸，而不是之前的皮肤 |
| 31 | 再关掉它 | 画廊恢复可用，你选的皮肤回来了 |
| 32 | **我的 → 导入皮肤…**，选一个有效的 `.json`（照 `docs/SKINS_zh.md` 写一个） | 它出现在对应画廊中，可选中，并带一个 **×** 用于移除 |
| 33 | 分别导入一个被截断的文件、一个 5 MB 的文件，以及 `"id"` 为 `"../../x"` 的文件 | 导入行下方给出三条*不同的*可读提示，且当前使用的皮肤不受影响 |
| 34 | 选中一个导入的皮肤，再用它的 **×** 移除 | 回退到 Paper 或 Ink；不留下任何半应用状态 |
| 35 | 导入一个 `"id"` 为 `"midnight"` 的文件 | 被拒绝 —— 内置皮肤不可被顶替 |
| 36 | 把窗口宽度拉到 640 dp 以上：平板、展开的折叠屏，或 `adb shell wm size 1600x1000` | 三个标签从底栏移到左侧**导航栏**，**+** 位于其顶部。切换前后选中的标签保持不变 |
| 37 | 改回手机宽度（`adb shell wm size reset`） | 底栏回来；屏幕上始终只有一组标签 |
| 38 | **我的 → 字号 → 大**，然后打开含代码块的文档并切到源码 | 代码块**和编辑器**都变大。它们此前完全无视这项设置 |
| 39 | 切到 English 再回到「我的」 | 皮肤区、导入行以及任何错误提示都已翻译 |

第 10 项最重要 — 这是防数据丢失的修复，也是本轮工作存在的原因。

---

## 4. Release APK — 由你拍板

`assembleRelease` 会产出**未签名** APK，路径为
`app\build\outputs\apk\release\app-release-unsigned.apk`。这是事先约定的做法：不生成 keystore，也不提交进仓库。

后果：该 APK **无法安装**。若要冒烟测试，用本地 debug 密钥签名：

```bat
apksigner sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android ^
  --ks-key-alias androiddebugkey --key-pass pass:android ^
  --out app-release-signed.apk ^
  app\build\outputs\apk\release\app-release-unsigned.apk

adb install -r app-release-signed.apk
```

`apksigner` 位于 `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\`。

即便 debug 构建已通过，也至少做一次：release 路径会跑 R8，而 Markdown 解析器通过反射发现 commonmark 扩展类。相关 keep 规则在 `app\proguard-rules.pro`。请在签名后的 release 构建上走完检查清单第 1–5 项；若某种节点类型在预览中静默消失，说明缺了 keep 规则。

若以后要做分发用构建，生成 release keystore 由你自己完成 — 绝不能放进本仓库。

---

## 5. 你该知道现已具备的两个特性

两者都来自「完整图片支持」的决定，都不是缺陷：

- **应用会申请 `INTERNET`。** 打开不受信任的 `.md` 时，会静默访问其中图片所指向的服务器 — 普通的跟踪像素行为。「是否加载远程图片？」提示可以拦住这一点，但尚未实现。
- **相对图片路径无法解析。** `![](./img/a.png)` 加载不了，因为通过选择器打开文件只会授予该文件的访问权，而不是周围文件夹。只有 `https://`、`content://`、`file://` 和 `data:` 可用。要修这个，得改用 `OpenDocumentTree` 并重做整个打开流程。

---

## 6. 已推迟 — 任何人动手前请先确认

刻意从范围内砍掉，列在这里以免被当成疏漏：

TalkBack 标题语义 · 仅图标顶栏操作的 tooltip · 单色启动器图标 · backup 与 data-extraction 规则 · 任务列表（`- [ ]`）渲染 · 链接协议限制 · 英语以外的本地化 · 远程图片隐私门控 · 同目录相对路径图片解析 · CI。

`CLAUDE.md` 也记录了现行范围边界：无云同步、无导出、无格式化工具栏、无语法高亮、无最近文件列表。往那边加东西属于需重新约定的新范围，不是待补的缺口。
