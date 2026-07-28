# Handoff — what needs your hands

The production baseline and the file-explorer dashboard are both implemented and
committed. What follows is only the work that cannot be done for you: it needs a Windows
machine, a human tapping the system file picker, or a decision that is yours.

Reference environment is **Windows**. Commands below are `cmd`/PowerShell from the
project root.

---

## 1. One-time setup

`local.properties` is gitignored, so it does not arrive with a clone. Create it with your
own SDK path, escaped the way a `.properties` file needs:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Android Studio writes this for you the first time you open the project. Nothing else is
needed — no keystore, no environment variables.

---

## 2. Automated checks to re-run on Windows

These all passed here (via the WSL mirror, `tools/wbuild.sh`), but they have never run on
a native Windows path. That is the point of re-running them.

```bat
gradlew.bat clean assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat lintDebug
gradlew.bat assembleRelease
gradlew.bat pixelApi34DebugAndroidTest
```

Expected results:

| Command | Expect |
|:--|:--|
| `testDebugUnitTest` | **82 tests, 0 failures** across 8 classes |
| `pixelApi34DebugAndroidTest` | **73 tests, 0 failures** (boots its own emulator — no device needed) |
| `lintDebug` | **0 errors, exactly 2 warnings**: `OldTargetApi` and `ObsoleteSdkInt` |
| `assembleRelease` | succeeds, unsigned APK ≈ **1.7 MB** |

One more device is defined but has never run here, because its system image needs a
licence accepted and that is not mine to accept:

```bat
sdkmanager.bat --licenses
gradlew.bat pixelApi30DebugAndroidTest
```

`pixelApi30` is the only coverage of the pre-Android-13 language path — above API 33 the
platform handles per-app language itself, so API 34 never touches the code that wraps the
app's own context. Worth running once.

Both lint warnings are deliberate and explained in `CLAUDE.md`. **Any third warning is a
regression** — that is the check, not the count itself.

Reports:

```
app\build\reports\tests\testDebugUnitTest\index.html
app\build\reports\androidTests\managedDevice\debug\index.html
app\build\reports\lint-results-debug.txt
```

---

## 3. Manual validation — needs a human

The system file picker cannot be driven by an instrumented test, so every path that
starts with "open a file" is unverified by CI and has to be walked by hand.

### Build the fixture first

In **Notepad** (not VS Code — Notepad is what produces the CRLF + BOM combination this is
testing), save the following as `fixture.md` with encoding **UTF-8 with BOM**:

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

Install and launch:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### The checklist

| # | Do this | Expect |
|:--|:--|:--|
| 1 | Tap **+** → **Open file**, pick `fixture.md` | Front matter renders as **nothing** — no bogus "title: Fixture" heading above "Heading one" |
| 2 | Look at the first heading | "Heading one" is styled as a heading. If the BOM leaked through it would render as plain text |
| 3 | Scroll to the image | It loads (needs network). A broken URL should show a failure notice, not a crash |
| 4 | Check the table and the code block | 3 columns with left/center/right alignment; `kotlin` label above `fun main() = Unit` |
| 5 | Tap the link | Opens in a browser |
| 6 | Switch to source, type something, hit undo, then redo | Undo/redo buttons appear **only** in source mode; both work |
| 7 | Scroll the preview down, toggle to source and back | Preview is still where you left it |
| 8 | Rotate the device in both modes | Scroll position and text survive |
| 9 | Save, then reopen `fixture.md` in Notepad | Still CRLF, still BOM. File size should have grown by exactly the characters you typed |
| 10 | Type an edit, press HOME, then `adb shell am force-stop com.mdview`, relaunch | The edit is **back**, title shows a `•`, snackbar says "Restored unsaved changes" with a **Discard** action |
| 11 | Tap that Discard | Buffer reverts to what is on disk, `•` clears |
| 12 | Open a `.md` from a file manager ("Open with" → MdView) | The document opens directly — **not** the dashboard with the file loading invisibly behind it. Back out to the dashboard and tap the same file in Files again: it must come back, with your unsaved edits intact |
| 13 | Open a large non-Markdown file (a `.jpg` renamed to `.md`, or anything > 2 MB) | A readable error message, not a class name like `java.io.IOException`, and you stay on the dashboard rather than landing in a blank editor |

### Dashboard and settings

| # | Do this | Expect |
|:--|:--|:--|
| 14 | Launch the app fresh | The **Recent** tab, empty, with a bottom bar of Recent / Favourites / Mine |
| 15 | Open `fixture.md`, then press back | A card headed **Fixture** (from the front matter, not the filename) with an excerpt and "fixture.md · just now" |
| 16 | Open a second document, then back | Two cards, most recent first |
| 17 | Tap the star on one, switch to **Favourites** | Only that document is listed; it is still in Recent too |
| 18 | Force-stop and relaunch | Both cards and the star survived |
| 19 | Long-press a card → **Remove from the list** | The card goes; the other stays |
| 20 | Open a document, type without saving, press back | A blue **Unsaved draft** card at the top of Recent. Tap it — your text is there |
| 21 | **Mine** → Theme → Dark, on a device set to Light | The whole app goes dark. Watch for a **white flash** on cold start, and check the status-bar icons stay legible |
| 22 | **Mine** → Text size → Large | The document and editor grow; the top bar and bottom tabs do **not** |
| 23 | **Mine** → Load images from the web → Never, then open `fixture.md` | "Image not loaded" where the photo was |
| 24 | **Mine** → Language → 简体中文 | Everything switches, **including the "just now" / "2 小时前" timestamps**, and you stay on the Mine tab rather than being bounced to Recent |
| 25 | With Chinese active, open a document you had edited but not saved | The unsaved text is still there — the language change recreates the Activity, and that must not cost you anything |
| 26 | Android 13+: Settings → Apps → MdView → Language | MdView is listed, with English and 简体中文. Change it there and the app agrees |

Items 10 and 20 matter most. Both are the data-loss guarantee: unsaved work has to
survive a kill *and* survive walking away to the dashboard. If either loses text, stop and
tell me.

---

## 4. Release APK — a decision you own

`assembleRelease` produces an **unsigned** APK at
`app\build\outputs\apk\release\app-release-unsigned.apk`. That was the agreed call: no
keystore is generated and none is committed.

Consequence: that APK **will not install**. To smoke-test it, sign with your local debug
key:

```bat
apksigner sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android ^
  --ks-key-alias androiddebugkey --key-pass pass:android ^
  --out app-release-signed.apk ^
  app\build\outputs\apk\release\app-release-unsigned.apk

adb install -r app-release-signed.apk
```

`apksigner` lives in `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\`.

Worth doing at least once even though the debug build passes: the release path runs R8,
and the Markdown parser discovers commonmark extension classes reflectively. Keep rules
for those are in `app\proguard-rules.pro`. Walk items 1–5 and 14–17 of the checklist on the
signed release build; if a node type silently vanishes from the preview, or the dashboard
comes up empty after opening something, a keep rule is missing.

If you later want a build for distribution, generating the release keystore is yours to
do — it must not end up in this repository.

---

## 5. Two properties you should know you now have

Both follow from the "full image support" decision, and neither is a defect:

- **The app requests `INTERNET`** and now also `ACCESS_NETWORK_STATE`. Opening an
  untrusted `.md` can contact whatever servers its images point at — ordinary
  tracking-pixel behaviour. **Mine → Load images from the web** gates this now; the
  default is still Always, so change it if that matters to you.
- **Relative image paths do not resolve.** `![](./img/a.png)` cannot load, because picking
  a file through the picker grants access to that one file and not the folder around it.
  Only `https://`, `content://`, `file://` and `data:` work. Fixing this means moving to
  `OpenDocumentTree` and reworking the whole open flow.

---

## 6. Deferred — confirm before anyone builds it

Cut from scope deliberately, listed so nothing here reads as an oversight:

TalkBack heading semantics · tooltips on the icon-only top-bar actions · monochrome
launcher icon · task-list (`- [ ]`) rendering · link-scheme restriction · sibling-file
image resolution · search within the document list · sorting the list by anything other
than recency · CI.

Delivered since this list was first written: backup and data-extraction rules, the
remote-image privacy gate, and localisation (English + 简体中文).

`CLAUDE.md` records the standing scope boundary: preview and source editing plus the
dashboard and settings, with no cloud sync, export, formatting toolbar, syntax
highlighting, or file browsing beyond what the picker grants. Additions there are new
scope to agree on, not gaps to fill.
