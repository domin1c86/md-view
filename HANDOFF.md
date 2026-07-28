# Handoff — what needs your hands

Everything in the production-baseline plan is implemented and committed (`4a6175a`,
`901f62b`). What follows is only the work that cannot be done for you: it needs a
Windows machine, a human tapping the system file picker, or a decision that is yours.

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
| `testDebugUnitTest` | **38 tests, 0 failures** across 4 classes |
| `pixelApi34DebugAndroidTest` | **25 tests, 0 failures** (boots its own emulator — no device needed) |
| `lintDebug` | **0 errors, exactly 2 warnings**: `OldTargetApi` and `ObsoleteSdkInt` |
| `assembleRelease` | succeeds, unsigned APK ≈ **1.6 MB** |

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
| 1 | Open `fixture.md` through the app's Open action | Front matter renders as **nothing** — no bogus "title: Fixture" heading above "Heading one" |
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
| 12 | Open a `.md` from a file manager ("Open with" → MdView) | Document loads directly, no empty state |
| 13 | Open a large non-Markdown file (a `.jpg` renamed to `.md`, or anything > 2 MB) | A readable error message, not a class name like `java.io.IOException` |

Item 10 is the one that matters most — it is the data-loss fix, and it is the reason this
round of work existed.

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
for those are in `app\proguard-rules.pro`. Walk items 1–5 of the checklist on the signed
release build; if a node type silently vanishes from the preview, a keep rule is missing.

If you later want a build for distribution, generating the release keystore is yours to
do — it must not end up in this repository.

---

## 5. Two properties you should know you now have

Both follow from the "full image support" decision, and neither is a defect:

- **The app requests `INTERNET`.** Opening an untrusted `.md` will silently contact
  whatever servers its images point at — ordinary tracking-pixel behaviour. A "load
  remote images?" prompt could gate this, but it is not built.
- **Relative image paths do not resolve.** `![](./img/a.png)` cannot load, because picking
  a file through the picker grants access to that one file and not the folder around it.
  Only `https://`, `content://`, `file://` and `data:` work. Fixing this means moving to
  `OpenDocumentTree` and reworking the whole open flow.

---

## 6. Deferred — confirm before anyone builds it

Cut from scope deliberately, listed so nothing here reads as an oversight:

TalkBack heading semantics · tooltips on the icon-only top-bar actions · monochrome
launcher icon · backup and data-extraction rules · task-list (`- [ ]`) rendering ·
link-scheme restriction · localization beyond English · a remote-image privacy gate ·
sibling-file image resolution · CI.

`CLAUDE.md` also records the standing scope boundary: no cloud sync, export, formatting
toolbar, syntax highlighting or recents list. Additions there are new scope to agree on,
not gaps to fill.
