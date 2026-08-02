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
| `testDebugUnitTest` | **239 tests, 0 failures** |
| `pixelApi34DebugAndroidTest` | **132 tests, 0 failures** (boots its own emulator — no device needed) |
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

![beside the document](./images/pic.png)

![from the folder root](/images/pic.png)

![outside the tree](../../../pic.png)

[A link](https://example.com)
````

Put a real PNG at `images/pic.png` next to `fixture.md` — items 41–43 need it to exist.

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

### Skins and the adaptive shell

| # | Do this | Expect |
|:--|:--|:--|
| 27 | **Mine** → scroll to **Light skin** / **Dark skin** | Eight swatches across the two galleries, each previewed in its own colours, with a tick on the active one |
| 28 | Pick **Cobalt** for light and **Midnight** for dark, then flip Theme between Light and Dark | The whole app changes, *including code blocks, tables, blockquote bars and links* — not just the bars and buttons. That split is the thing most likely to be wrong |
| 29 | Cold-start the app under Midnight (`adb shell am force-stop com.mdview`, relaunch) | **No pale flash before the first frame.** The window background now comes from the active skin; this is what used to be two hardcoded constants |
| 30 | Turn on **Colours from your wallpaper** (Android 12+) | Both skin galleries dim, with a caption explaining why. The document — code blocks included — follows the wallpaper, not the old skin |
| 31 | Turn it off again | The galleries come back live and your chosen skins return |
| 32 | **Mine** → **Import a skin…**, pick a valid `.json` (write one from `docs/SKINS.md`) | It appears in the right gallery, selectable, with a **×** to remove it |
| 33 | Import a truncated file, a 5 MB file, and one whose `"id"` is `"../../x"` | Three *different* readable messages inline under the import row, and the skin you were using stays active |
| 34 | Select an imported skin, then remove it with its **×** | Falls back to Paper or Ink; nothing is left half-applied |
| 35 | Import a file whose `"id"` is `"midnight"` | Refused — a built-in cannot be shadowed |
| 36 | Widen the window past 640 dp: a tablet, an unfolded foldable, or `adb shell wm size 1600x1000` | The three tabs move from the bottom bar to a **rail** on the left, with the **+** at its top. The selected tab survives the switch |
| 37 | Go back to a phone width (`adb shell wm size reset`) | The bottom bar returns; still exactly one set of tabs on screen |
| 38 | **Mine** → Text size → Large, then open a document with a code block and switch to source | Code blocks **and the editor** grow. They used to ignore this setting entirely |
| 39 | Switch to 简体中文 and revisit Mine | The skin section, the import row and any error message are all translated |
| 40 | Open `fixture.md` and scroll to `./images/pic.png` | A notice offering folder access, **not** a plain "unavailable". A document with no local images must show no such notice anywhere |
| 41 | Tap it | The system folder picker opens — ideally already at `fixture.md`'s own folder. `EXTRA_INITIAL_URI` is a hint DocumentsUI may ignore, so check rather than assume. Choose that folder |
| 42 | Look at the two resolvable images | Both `./images/pic.png` **and** `/images/pic.png` now render, with no reopen and no second prompt |
| 43 | Look at `../../../pic.png` | "Image unavailable". It must never show a picture from outside the granted folder |
| 44 | Open a second document in a *sibling* folder under the same tree | Its images load with **no** second grant |
| 45 | Force-stop the app, then reboot the device, then reopen `fixture.md` | Images still load. The grant is persisted, not per-session |
| 46 | **Mine** → **Image folders** | The folder is listed by name. Tap ✕; images stop loading and the offer comes back |
| 47 | Grant a folder that does *not* contain the document | The distinct "does not contain this document" notice — not the same invitation again |
| 48 | Open a document from Google Drive that uses a relative image, and grant a Drive folder | The "wrong folder" notice. Never a spinner that never resolves, and never a crash |

### Motion and overlays

No test can assert how something feels, and two of these cover behaviour the platform used
to provide and no longer does.

| # | Do this | Expect |
|---|---|---|
| 49 | Open any menu, and the unsaved-changes dialog, on a **light** skin and then on **Ink** | Neither casts a shadow. On Ink the hairline border is the only thing separating them from the surface behind — if it is missing they will look like floating text |
| 50 | Edit without saving, then **⋮ → New document** | The dialog fades *and scales* in, and does the same on the way out rather than vanishing. No white or grey flash of a platform dialog window behind it |
| 51 | With that dialog open, tap the dimmed area outside it. Then reopen it and use the back gesture | Both dismiss it. The scrim's tap is hand-written — `dismissOnClickOutside` cannot fire now that the dialog window fills the screen — so this is the one that would silently regress |
| 52 | Tap a document card and press back **while it is still animating in** | You land on the dashboard, once. Not two screens back, not out of the app. Both screens are briefly composed together and only the arriving one may answer |
| 53 | Developer options → **Animator duration scale: off**, then move around the app | Everything is instant and nothing hangs or half-draws. Then set **10x** and check nothing breaks or double-fires. Compose applies this scale itself, so this is verifying it, not implementing it |
| 54 | Turn on TalkBack, open the dialog, then save a document to get the snackbar | The dialog is announced as a dialog; the snackbar with its action stays long enough to reach. That timeout is the reason `SnackbarHost` was kept rather than hand-rolled |
| 55 | Rotate with the dialog open; switch language with a menu open | Neither leaves a stranded overlay or an untranslated one |

### Folders

Folders are an in-app label and **nothing else**. Item 58 is the one that proves it, and
it is the whole point of the feature — if a directory appears anywhere, stop.

| # | Do this | Expect |
|---|---|---|
| 56 | On **Recent**, tap **+ New folder**, name it `Work` | The chip appears and is selected: the list below is empty, saying so |
| 57 | With `Work` open, tap **+** → **Open file**, pick a document | It opens for reading, as it does anywhere else. Press back: it is in `Work` |
| 58 | Open a file manager and look at that document's folder, at internal storage's root, and at `Android/data/com.mdview` | **No `Work` directory anywhere**, and the document has not moved. This is the constraint the whole feature turns on |
| 59 | Back out to the full list, long-press another card → **Move to folder…** → `Work` → **Move** | It joins `Work` |
| 60 | Look at Recent with no chip selected | **Both filed documents are still listed.** Recent is a log of what you opened, not a bucket that filing empties |
| 61 | Tap the `Work` chip, then tap it again | Filters, then unfilters. The back gesture does the same, and only then leaves the app |
| 62 | Long-press the `Work` chip → **Rename**, call it `Projects` | The chip renames and still holds both documents — the id is what they reference, not the name |
| 63 | Try to make a second folder called `projects` | Refused inline, in the dialog, saying a folder already has that name. The dialog stays open with your text |
| 64 | Press **Create** with the field empty | Refused with a reason. Nothing is created and the dialog stays |
| 65 | Long-press the chip → **Delete folder** → **Delete** | The chip goes; **both documents are still in Recent**, and nothing on the device was deleted |
| 66 | Make a folder, file a document, then `adb shell am force-stop com.mdview` and relaunch | The chip and its contents survived |
| 67 | Switch to 简体中文 and repeat 56 and 65 | The chip, both dialogs and the "nothing is deleted from your device" wording are all translated |
| 68 | Widen past 640 dp (`adb shell wm size 1600x1000`) | The strip is still above the cards, with the tabs on the rail. Reset with `adb shell wm size reset` |

Items 10 and 20 matter most. Both are the data-loss guarantee: unsaved work has to
survive a kill *and* survive walking away to the dashboard. If either loses text, stop and
tell me. Item 58 is the one to fail the folder feature on.

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
- **A folder grant is transitive, and permanent until revoked.** Relative image paths do
  resolve now, but only after the user grants the document's folder through the notice on
  the first unresolved image. That grant then covers *every* document and *every* image
  beneath that folder, for good — not just the document that prompted it. That is the
  point, since one grant serves a whole notes tree, but it is more access than the single
  file the picker hands over. **Mine → Image folders** lists what is held and takes it
  back. At most ten are kept, because Android caps how many URI grants an app may persist
  and the recents list already spends part of that budget.

---

## 6. Deferred — confirm before anyone builds it

Cut from scope deliberately, listed so nothing here reads as an oversight:

TalkBack heading semantics · tooltips on the icon-only top-bar actions · monochrome
launcher icon · task-list (`- [ ]`) rendering · link-scheme restriction · relative images
on cloud providers · inline (mid-sentence) image rendering · search within the document
list · sorting the list by anything other than recency · CI.

Delivered since this list was first written: backup and data-extraction rules, the
remote-image privacy gate, localisation (English + 简体中文), the skin system, and
sibling-file image resolution.

`CLAUDE.md` records the standing scope boundary: preview and source editing plus the
dashboard and settings, with no cloud sync, export, formatting toolbar, syntax
highlighting, or file browsing beyond what the picker grants. Additions there are new
scope to agree on, not gaps to fill.
