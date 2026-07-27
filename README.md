# MdView

A minimal Markdown reader for Android. Two modes, nothing else:

- **Preview** — the document rendered as formatted, readable text.
- **Source** — a plain-text editor for the raw Markdown, saved back to the same file.

No cloud sync, no export, no formatting toolbar. Images are not downloaded; they
appear as an `[image: alt text]` placeholder.

## How it works

Files are opened through the Storage Access Framework, so the app declares **no
permissions at all** — the file you pick in the system picker is the grant. It also
registers for `ACTION_VIEW`, so `.md` files can be opened straight from a file
manager.

Markdown is parsed by [commonmark-java](https://github.com/commonmark/commonmark-java)
with the GitHub tables, strikethrough and autolink extensions, then rendered into
native Jetpack Compose composables — no WebView and no `TextView` interop.

| Area | Where |
|:--|:--|
| Parsing | `app/src/main/java/com/mdview/markdown/MarkdownParser.kt` |
| Block rendering | `.../markdown/MarkdownRenderer.kt` |
| Inline rendering | `.../markdown/InlineRenderer.kt` |
| File I/O (SAF) | `.../data/DocumentRepository.kt` |
| State | `.../MainViewModel.kt` |
| Screens | `.../ui/` |

## Building

Open the project in Android Studio and press Run — nothing special is needed.

### Building from WSL

The repository lives on the WSL filesystem, but the JDK and Android SDK are on the
Windows side. Gradle cannot build in place: as a Windows process it sees the project
through a `\\wsl.localhost\...` UNC path, and its file hasher dies there with *"The
function is incorrect"*. `cmd.exe` also refuses to `cd` into a UNC path, which rules
out `gradlew.bat`.

`tools/wbuild.sh` works around both. It mirrors the sources to a native Windows
directory with `rsync`, then invokes `java.exe` from Android Studio's bundled JBR
directly. The mirror is disposable; this repository stays the source of truth.

```sh
tools/wbuild.sh assembleDebug        # APK -> build-outputs/apk/debug/
tools/wbuild.sh testDebugUnitTest    # parser and inline-renderer tests
tools/wbuild.sh lintDebug
tools/wbuild.sh pixelApi34DebugAndroidTest   # boots a managed emulator, no device needed
```

Set `MDVIEW_BUILD_DIR` to move the mirror. `local.properties` must point `sdk.dir`
at the SDK using a **Windows** path, because Gradle runs as a Windows process.

## Versions

AGP 9.3.1 (which supplies Kotlin itself — the standalone `kotlin-android` plugin is
rejected), Gradle 9.6.1, Compose BOM 2026.06.01, `compileSdk` 37, `minSdk` 26,
`targetSdk` 36.
