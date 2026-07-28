# Writing a skin for MdView

A **skin** is a JSON file that tells MdView what colour to draw everything, how round its
corners are, and how its type is weighted. You can write one in a text editor, import it
through **Mine → Import a skin…**, and it appears in the picker beside the built-in ones.

A skin controls colour, corner radius and type weight. It does **not** control layout,
icons, animation, the launcher icon, or which controls exist. It cannot ship a font file
or reference a URL — see [Typography](#typography).

---

## The shortest skin that works

Every value is optional except `id`, `name`, and a `base` to inherit from. This is a
complete, valid skin:

```json
{
  "id": "warmer-ink",
  "name": "Warmer Ink",
  "base": "ink",
  "colors": {
    "accent": "#E8A33D",
    "link": "#F0B860"
  }
}
```

Everything not mentioned comes from `ink`. Start here, override one thing at a time, and
only reach for the full file below when you are replacing a whole palette.

---

## The surface ladder

Four of the colour tokens are not independent — they are rungs, ordered from the layer
furthest back to the one nearest the reader. Getting the *order* right matters more than
getting any single value right.

```
  surfaceRaised   menus, dialogs, the pressed state of a card
  surface         cards, list rows, the navigation bar and rail
  canvas          the window itself, behind everything, including the top bar
  surfaceSunken   code blocks, the editor, recessed fills
```

On a dark skin each rung is lighter than the one below it; on a light skin each is
darker. Two rungs may share a value — `Paper` uses pure white for both `surface` and
`surfaceRaised` — but a skin that inverts them will look wrong on every screen at once.

The top bar deliberately sits on `canvas`, not `surface`, so cards read as lifted off the
background rather than merging into it.

---

## Colour tokens

All 23, with the component that draws each one.

### Surfaces

| Token | Role |
|---|---|
| `canvas` | The window background. **Also painted behind the very first frame**, before Compose draws, so a wrong value here shows as a flash on cold start. |
| `surface` | Cards, the navigation bar, the navigation rail, the table body. |
| `surfaceRaised` | Dropdown menus, dialogs. The only rung allowed to look elevated. |
| `surfaceSunken` | The editor background, blocked-image placeholders, recessed fills. |

### Accent

| Token | Role |
|---|---|
| `accent` | The one colour carrying the skin's identity: the FAB, the editor cursor, the selected tab icon, radio buttons, switches, section headings, the import row. |
| `onAccent` | Text and icons drawn **on top of** an `accent` fill. Must contrast with `accent`, not with the background. |
| `accentSubtle` | A wash of the accent: the selected-tab pill, the unsaved-draft card, the pressed state behind a link, the empty-state icon halo. |

### Text

| Token | Role |
|---|---|
| `textPrimary` | Body copy, headings, card titles. |
| `textSecondary` | Card excerpts, list markers, quoted text, setting descriptions. |
| `textMuted` | The quietest readable text: timestamps, the `filename · 2 hours ago` line, code-fence language chips, captions. |

### Lines

| Token | Role |
|---|---|
| `border` | Hairlines around cards, code blocks and skin swatches. |
| `divider` | Rules between settings rows, under H1/H2 headings, and the `---` thematic break. |

### Document

| Token | Role |
|---|---|
| `link` | Link text. |
| `linkPressed` | Link text while the touch is down. The background behind it comes from `accentSubtle`. |
| `code` | Monospace text, in both fenced blocks and inline spans. |
| `codeBackground` | The fill behind fenced blocks and inline code spans. |
| `quoteBar` | The vertical rule down the left of a blockquote. |
| `quoteText` | Text inside a blockquote. |
| `tableHeader` | The fill behind a table's header row. |
| `tableBorder` | Table rules, between rows and between columns, plus the table's outer hairline. |

### Status

| Token | Role |
|---|---|
| `danger` | The unsaved-changes bullet in the title bar, card notices (*Read-only*, *No longer available*), import errors. |
| `success` | Confirmations. |
| `selection` | Text selection. |

---

## Typography

Type is expressed as **multipliers, never as absolute sizes**. Every size in MdView is in
`sp`, so it already compounds with the reader's accessibility font scale, and the
in-app **Text size** setting multiplies again on top. A skin that could pin body text to
11 sp would silently defeat both, so it cannot.

```json
"type": {
  "bodyScale": 1.0,
  "monoScale": 0.9,
  "headingWeight": 700,
  "tracking": 0.0
}
```

| Key | Range | Meaning |
|---|---|---|
| `bodyScale` | 0.85 – 1.3 | Scales body copy. Values outside the range are clamped, not rejected. |
| `monoScale` | 0.7 – 1.2 | Size of monospace text relative to body text. Applies to code blocks, inline code **and the source editor**. |
| `headingWeight` | 100 – 900 | Font weight for headings and table headers. Snapped to the nearest hundred. |
| `tracking` | −0.05 – 0.1 | Extra letter spacing for body text, in `em`. Negative tightens. |

**There is no font-family key.** MdView ships no font resources and has no font
dependency; body text is the platform default and code is the platform monospace. A skin
cannot reference a font file or a URL.

Headings always use tighter letter spacing than Material's default — that is a
system-wide decision, not a per-skin one.

---

## Shape

Corner radii in dp, clamped to 0–48.

```json
"shape": { "small": 8, "medium": 16, "large": 20 }
```

| Key | Applies to |
|---|---|
| `small` | The blockquote bar, inline fills, the import-error notice. |
| `medium` | Cards, code blocks, tables, images, skin swatches. |
| `large` | Dialogs, menus, sheets. |

Setting all three to a small number gives a squared-off, WPS-like feel; raising them
gives the softer look of `Paper`.

---

## Component guidelines

- **Cards** are `surface` with a 1 dp `border` hairline and no shadow. On a dark skin a
  shadow is invisible, which is exactly why the surface ladder and the hairline exist.
  Make `border` distinguishable from both `canvas` and `surface`.
- **The navigation bar and rail** use `surface`, with `accentSubtle` behind the selected
  item and `accent` for its icon. Only one of the two is on screen at a time; the rail
  appears once the window is at least 640 dp wide.
- **Code blocks** are `codeBackground` with a `border` hairline. They scroll sideways
  rather than wrapping, so `code` must stay readable against `codeBackground` at small
  sizes.
- **Tables** sit on `surface` with a `tableBorder` outline; the header row is
  `tableHeader`. Keep `tableBorder` visible against `tableHeader` or the header stops
  reading as a separate row.
- **Blockquotes** are a 3 dp `quoteBar` with `quoteText` beside it. Do not make `quoteBar`
  so quiet that it disappears — on Discord's own palette the equivalent grey scores
  1.57:1 against the chat background and stops working as a structural cue.
- **Links** are underlined as well as coloured, so colour is never the only signal.

---

## The complete file

Every key MdView reads. Use this as a reference, not as a starting point — a skin that
sets `base` and overrides ten tokens is easier to maintain and survives future releases
better.

```json
{
  "schema": 1,
  "id": "solar-flare",
  "name": "Solar Flare",
  "author": "you@example.com",
  "dark": true,
  "base": "ink",

  "colors": {
    "canvas":        "#0E0F13",
    "surface":       "#15171C",
    "surfaceRaised": "#1C1F26",
    "surfaceSunken": "#090A0C",

    "accent":        "#C79BFF",
    "onAccent":      "#1A0B26",
    "accentSubtle":  "#2A1F3D",

    "textPrimary":   "#E9E7F0",
    "textSecondary": "#B4B0C2",
    "textMuted":     "#8B8799",

    "border":        "#282B33",
    "divider":       "#1E212A",

    "link":          "#C79BFF",
    "linkPressed":   "#DCC2FF",

    "code":          "#E4A8CE",
    "codeBackground": "#171A20",

    "quoteBar":      "#C79BFF",
    "quoteText":     "#B4B0C2",

    "tableHeader":   "#171A20",
    "tableBorder":   "#343B47",

    "danger":        "#FF8A80",
    "success":       "#43C08A",
    "selection":     "#3B2A55"
  },

  "shape": { "small": 8, "medium": 16, "large": 20 },

  "type": {
    "bodyScale": 1.0,
    "monoScale": 0.9,
    "headingWeight": 700,
    "tracking": 0.0
  }
}
```

### Top-level keys

| Key | Required | Notes |
|---|---|---|
| `id` | yes | `[a-z0-9][a-z0-9-]{0,63}`. Becomes a filename. Uppercase is folded to lowercase; anything outside the charset is refused. Cannot be the id of a built-in. |
| `name` | yes | What the picker shows. Trimmed, and truncated at 48 characters. |
| `author` | no | Not displayed anywhere yet; kept so a file can carry its provenance. |
| `dark` | no | Whether this is a dark skin, which decides which of the two pickers it appears in. Inherited from `base` when absent. |
| `base` | no | The built-in to inherit every unspecified token from. Defaults to `ink` when `dark` is true, `paper` otherwise. |
| `schema` | no | Currently `1`. A higher number still parses. |

Built-in ids available as a `base`: `paper`, `cobalt`, `sepia`, `solarized-light`,
`ink`, `midnight`, `nord`, `solarized-dark`.

### Colour formats

`#RGB`, `#RRGGBB` and `#AARRGGBB`, in either case. The `#` is required. `#F0A` expands to
`#FF00AA`. Alpha is accepted but think twice: a translucent token composites against
whatever is behind it, which makes its contrast unpredictable.

---

## What happens when something is wrong

MdView follows one rule throughout: **a bad value should cost you that value, not the
whole file.**

| Situation | Result |
|---|---|
| Unknown top-level key | Ignored. Dropped when the skin is stored. |
| Unknown token inside `colors`, `shape` or `type` | Ignored. |
| Malformed colour (`"#GGG"`, `"red"`, `42`) | That one token falls back to `base`. Its siblings still apply. |
| Number out of range | Clamped to the documented range. |
| `schema` higher than 1 | Parsed anyway. |
| Arrays anywhere | Parsed and discarded, so a future schema will not break this build. |
| A byte order mark at the start | Stripped. Windows editors add these. |
| Missing or unusable `id` | **Refused** — *"That skin's id is missing or unusable"*. |
| Missing `name` | **Refused** — *"That skin has no name"*. |
| Unknown `base` | **Refused** — *"That skin is built on one this app does not have"*. |
| `id` matches a built-in | **Refused** — *"A built-in skin already uses that id"*. |
| Not JSON — trailing commas, single quotes, unquoted keys, comments, `NaN` | **Refused** — *"That file is not a skin this app can read"*. |

Re-importing a skin whose `id` you already have **replaces** it, which is what you want
while iterating on your own file.

### Limits

| Limit | Value |
|---|---|
| File size | 64 KiB |
| Nesting depth | 8 |
| Values in the file | 1024 |
| Any single string | 256 characters |
| `id` length | 64 |
| `name` length | 48 |
| Imported skins kept | 24 |

---

## Contrast

The built-in skins are held to these floors **in an automated test**, measured against
all four surface rungs rather than just one. Hold your own skin to the same bar:

| Foreground | Against | Minimum |
|---|---|---|
| `textPrimary`, `textSecondary`, `quoteText`, `link`, `linkPressed` | every surface rung | 4.5:1 |
| `textMuted`, `danger`, `success` | every surface rung | 3:1 |
| `accent` as text | `canvas` and `surface` | 3:1 |
| `onAccent` | `accent` | 4.5:1 |
| `code` | `codeBackground` | 4.5:1 |
| `border` | `canvas` and `surface` | 1.2:1 |

### Your favourite palette probably does not pass

Three of the shipped skins deviate from their canonical values, because those palettes
were designed for terminals, where nothing has to pass WCAG. This is normal, and worth
knowing before you port one:

- **Nord.** Aurora red `#BF616A` scores **2.46:1** against Nord's own surface. MdView
  ships `#D9848D` instead. Frost `#8FBCBB` as a link drops to 4.14:1 on the raised rung,
  so links use `#A3CFCE`.
- **Solarized.** Blue `#268BD2` under white text is **3.68:1**. MdView uses `#186FA8` for
  the light skin, and `#3AA0E0` over a dark `onAccent` for the dark one.
- **Discord.** Blurple `#5865F2` is only 3.37:1 against its own surface, so it is never
  used as *text* — only as a fill with `onAccent` on top, where it measures 4.61:1.

---

## Installing and removing

Import through **Mine → Import a skin…** and pick the `.json` file. Providers frequently
mislabel `.json`, so the picker's filter is deliberately wide and the file itself is what
gets validated.

An imported skin is stored in the app's private storage, re-encoded in full — with no
`base` — so retuning a built-in in a later release can never silently change how your
skin looks.

Remove one with the **×** on its swatch. Built-in skins have no × at all. Removing the
skin you are currently using falls back to Paper or Ink.

Skins are **not** included in device backup, for the same reason drafts are not: the app's
private storage is restored to a different app instance where the stored URI grants no
longer mean anything.

---

## One thing that is not skinnable

**Material You.** When *Colours from your wallpaper* is on, the wallpaper wins and both
skin pickers are dimmed — including the document tokens, which are rebuilt from the
wallpaper scheme so that code blocks and tables follow it too. Turn it off to choose a
skin. It is off by default for exactly this reason.
