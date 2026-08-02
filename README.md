# MdView

A minimal Markdown reader and editor for Android.

The app opens on a **dashboard** of documents you have opened before, shown as cards with
their heading and a couple of lines of the text. Three tabs along the bottom:

- **Recent** — everything you have opened, newest first.
- **Favourites** — the ones you starred.
- **Mine** — theme, skins, language, reading text size and image loading.

Tap a card and you get the document itself, in one of two modes:

- **Preview** — the document rendered as formatted, readable text.
- **Source** — a plain-text editor for the raw Markdown, saved back to the same file.

No cloud sync, no export, no formatting toolbar, no syntax highlighting.

## How it works

Files are opened through the Storage Access Framework, so the app needs no storage
permission — the file you pick in the system picker is the grant. It also registers for
`ACTION_VIEW`, so `.md` files can be opened straight from a file manager.

The dashboard is a list of *documents you have opened*, not a file browser: the picker
grants access to one file at a time, so there is nothing to browse. Starring a document
also exempts it from the 50-entry cap, and removing a card hands its access grant back.

Markdown is parsed by [commonmark-java](https://github.com/commonmark/commonmark-java)
with the GitHub tables, strikethrough and autolink extensions plus YAML front matter,
then rendered into native Jetpack Compose composables — no WebView and no `TextView`
interop.

**Unsaved edits are autosaved to app-private storage.** Android kills backgrounded
processes without warning, so the buffer is written to a draft half a second after you
stop typing, and again when the app goes to the background. Reopen the document and the
draft comes back, with a **Discard** button in case you did not want it.

**Files are written back the way they arrived.** A byte-order mark is stripped for
editing and restored on save, and CRLF line endings survive a round trip, so a document
authored in Notepad still looks untouched to the tools that made it.

| Area | Where |
|:--|:--|
| Parsing | `app/src/main/java/com/mdview/markdown/MarkdownParser.kt` |
| Block rendering | `.../markdown/MarkdownRenderer.kt` |
| Inline rendering | `.../markdown/InlineRenderer.kt` |
| Encoding and line endings | `.../markdown/DocumentCodec.kt` |
| Card headings and excerpts | `.../markdown/DocumentSummary.kt` |
| File I/O (SAF) | `.../data/DocumentRepository.kt` |
| Autosaved drafts | `.../data/DraftStore.kt` |
| Recents and favourites | `.../data/LibraryStore.kt` |
| Preferences | `.../data/SettingsStore.kt` |
| In-app language | `.../data/AppLocale.kt` |
| State and navigation | `.../MainViewModel.kt` |
| Colour, shape and type tokens | `.../ui/theme/Skin.kt` |
| Easing curves and durations | `.../ui/theme/Motion.kt` |
| Menus, dialogs and the message bar | `.../ui/SkinOverlays.kt` |
| Dashboard | `.../ui/dashboard/` |
| Document screen | `.../ui/` |

### Settings

The **Mine** tab carries these preferences, applied the moment you tap them:

| Setting | Notes |
|:--|:--|
| Theme | System / Light / Dark, independent of what the device is doing |
| Light skin, Dark skin | Which skin to use in each mode. See below |
| Colours from your wallpaper | Material You. Overrides both skins; off by default, and hidden below Android 12 where it does nothing |
| Language | System / English / 简体中文. On Android 13+ this is the same setting the system exposes under Apps → Language |
| Text size | Scales the document, the editor and code blocks, not the app's own controls |
| Load images from the web | Never / unmetered networks only / always |

### Skins

A skin is the whole palette: not just the accent, but the code-block background, the
blockquote bar, table rules, links and the four surface layers the app is built from.
Eight ship with the app — Paper, Cobalt, Sepia and Solarized Light for light mode; Ink,
Midnight, Nord and Solarized Dark for dark — and each is held to WCAG AA contrast by an
automated test.

You choose one skin for light mode and one for dark, so *Follow the system* flips between
your two choices rather than between two fixed themes.

You can also **import your own** as a JSON file, through Mine → Import a skin…
[`docs/SKINS.md`](docs/SKINS.md) is the full specification: every colour token and what
draws it, the typography and shape rules, a worked example, and the validation limits.

### Images

Images are fetched with [Coil](https://coil-kt.github.io/coil/), which is why the app
declares `INTERNET`. Whether a *remote* image loads depends on the setting above;
`data:`, `content:` and `file:` images always render, since they never leave the device.

**Images stored beside the document** — `./img/diagram.png`, `../assets/logo.png` — need one
extra step, because opening a document through the picker grants access to that one file and
not to the folder around it. The first such image offers to fix that: tap it, choose the
folder the document lives in, and every image under that folder resolves from then on. A
path starting with `/` is measured from the granted folder's root, the convention Hugo,
Jekyll and MkDocs use.

The grant is remembered, covers every document beneath it, and is listed under
**Mine → Image folders** with a way to hand it back. Two limits are worth knowing:

- Only an image on a line of its own becomes a real image. One sitting inside a sentence
  stays an `[image: alt]` placeholder.
- Cloud providers are not supported for relative paths. Google Drive and the like give
  documents opaque ids with no folder structure to measure a path against, so those images
  say so rather than guessing.

## Building

Open the project in Android Studio and press Run — nothing else is needed.

From a terminal (PowerShell or `cmd`) in the project root:

```bat
gradlew.bat assembleDebug          :: APK -> app\build\outputs\apk\debug\
gradlew.bat testDebugUnitTest      :: JVM tests: parser, inline renderer, codec, drafts
gradlew.bat lintDebug
gradlew.bat assembleRelease        :: minified, unsigned
gradlew.bat pixelApi34DebugAndroidTest   :: boots a managed emulator, no device needed
gradlew.bat pixelApi30DebugAndroidTest   :: the pre-Android-13 language path
```

`pixelApi30` needs its system-image licence accepted once (`sdkmanager.bat --licenses`).
It exists because Android 13+ hands per-app language to the platform, so API 34 never
exercises the older code path where the app wraps its own context.

A single test class or method:

```bat
gradlew.bat testDebugUnitTest --tests "com.mdview.markdown.DocumentCodecTest"
gradlew.bat testDebugUnitTest --tests "*.DocumentCodecTest.byteOrderMarkIsStripped"
gradlew.bat pixelApi34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mdview.MainViewModelTest
```

Where things land:

| Output | Path |
|:--|:--|
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Release APK | `app\build\outputs\apk\release\app-release-unsigned.apk` |
| Unit test report | `app\build\reports\tests\testDebugUnitTest\index.html` |
| Instrumented report | `app\build\reports\androidTests\managedDevice\debug\index.html` |
| Lint report | `app\build\reports\lint-results-debug.html` |

`local.properties` is gitignored and must point `sdk.dir` at the SDK, escaped the way a
properties file needs:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Install on a running emulator or a device with USB debugging on:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Two lint warnings are expected and deliberate: `OldTargetApi`, because `targetSdk` stays
at 36 while `compileSdk` is forced to 37 by the androidx versions in use; and
`ObsoleteSdkInt` on `mipmap-anydpi-v26`, whose `-v26` qualifier cannot be dropped without
AAPT2 losing the launcher icons.

### Release builds

`assembleRelease` runs R8 with resource shrinking and produces an **unsigned** APK — no
keystore is checked in. To install one for testing, sign it with the local debug key:

```bat
apksigner sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android ^
  --ks-key-alias androiddebugkey --key-pass pass:android ^
  --out app-release-signed.apk ^
  app\build\outputs\apk\release\app-release-unsigned.apk
```

`apksigner` lives in `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\`.

### Appendix: building from WSL

Only relevant when the repository sits on the WSL filesystem while the JDK and SDK are on
the Windows side. Gradle cannot build in place there: as a Windows process it sees the
project through a `\\wsl.localhost\...` UNC path, and its file hasher fails with *"The
function is incorrect"*. `cmd.exe` also refuses to `cd` into a UNC path, which rules out
`gradlew.bat`.

`tools/wbuild.sh` works around both. It mirrors the sources to a native Windows directory
with `rsync`, then invokes `java.exe` from Android Studio's bundled JBR directly. The
mirror is disposable; this repository stays the source of truth.

```sh
tools/wbuild.sh assembleDebug
tools/wbuild.sh testDebugUnitTest
```

`MDVIEW_BUILD_DIR` moves the mirror. Build outputs are copied back to `build-outputs/`,
but reports are not — those stay in the mirror, at the Windows path Gradle prints. Both
`build-outputs/` and `MDVIEW_BUILD_DIR` are WSL-only concerns and mean nothing when
building on Windows.

## Versions

AGP 9.3.1 (which supplies Kotlin itself — the standalone `kotlin-android` plugin is
rejected), Gradle 9.6.1, Compose BOM 2026.06.01, commonmark 0.29.0, Coil 3.5.0,
`compileSdk` 37, `minSdk` 26, `targetSdk` 36.

No navigation library, no DataStore, no Room, no serialisation library: navigation is a
`when` over two destinations, and both stores are plain files with hand-written codecs.
