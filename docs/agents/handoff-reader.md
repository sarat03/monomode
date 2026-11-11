# Handoff: MonoMode's built-in e-reader

Written 2026-11-11 at the end of Phase 4. Read this first, then `git log master..HEAD`.

## Where things stand

- **Branch:** `claude/kiosk-mode-wireless-debugging-4613ab`, six commits ahead of `master`, **not pushed**, no PR.
  It lives in the worktree `.claude/worktrees/kiosk-mode-wireless-debugging-4613ab`.
- **Working tree:** clean. 110 engine tests pass. `:app:assembleDebug` builds.
- **Done:** Phases 0 to 4 of the plan below. **Left:** Phase 5, and the review items at the end.

## What was built

E-reader mode no longer has a launcher. Its home screen (`HomeActivity`) *is* the reader's library, and a
book opens in place on the same screen. The mode's button says "Enter e-reader" and goes straight in.

| File | Holds |
|---|---|
| `app/.../Reader.kt` | `Library`, `Reading`, `ReaderSettings`, `BookDetails`, the page script `SCRIPT`, `css()` |
| `app/.../HomeActivity.kt` | `ReaderHome()` (library / book / settings), `applyScreen()`, volume keys, `bookFrom()` for "Open with" |
| `engine/.../Epub.kt` | EPUB parsing: title, author, series, description, spine, contents, cover, `search`, `note`, `progress`/`locate` |
| `engine/.../Shelf.kt` | Everything the reader remembers, behind `Shelf.Store` (SharedPreferences file `shelf` on the phone) |
| `engine/.../Catalog.kt` | `BookInfo`, `onList`, `groups`, `members` |
| `engine/.../Tap.kt` | `tapZone()` |
| `engine/.../KoReader.kt` | Reads KOReader's last book (used when KOReader is the chosen reading app) |

How the reading page works, because it is not obvious from the code alone:

- One chapter at a time is served to a `WebView` from the EPUB's zip under the made-up host `book.invalid`
  (`shouldInterceptRequest`). The network is blocked.
- **Pages are CSS columns.** `css()` makes each column plus its gap exactly one screen wide (two columns per
  screen above 840 CSS px). Turning a page is `scrollTo` by one screen width. Sizes are in px taken from the
  view, because `vw`/`vh` are not dependable in a WebView. A viewport `<meta>` pins the zoom; without it the
  WebView zooms a little more on every rotation.
- **Scripts:** the book's own are blocked by a `Content-Security-Policy: script-src 'none'` response header.
  The app's own `SCRIPT` (`window.mm`) is run from outside with `evaluateJavascript`, which that header does
  not stop. It does highlights, search positioning, link detection for taps, and the text for read aloud.
  Places are character offsets in `document.body.textContent`, the same text `Epub.search` reads.
- **Scroll mode** reuses the page model: a "page" is nine tenths of a screen.
- The page is padded with `systemBarsIgnoringVisibility`, so a panel that shows the status bar does not
  resize the page (a resize reloads the chapter).

## The plan and what is left

The user chose features from a ReadEra/KOReader comparison. Numbers are from that list.

| Phase | Features | State |
|---|---|---|
| 0 | Baseline commit | done `b4fd332` |
| 1 | Reader memory into `engine` with tests | done `0cca4dd` |
| 2 | Bookmarks (1), go to / back (2), text options (7), brightness (10), keep on + orientation (11), volume keys (12), status line (14) | done `5d325d3` |
| 3 | Search (3), highlights + notes (4), scroll mode (6), tap layouts (13), footnote popups (15), read aloud (16) | done `2d2b9b8` |
| 4 | Lists (17), cover grid (18), book details (19), authors/series/collections (20), folders (21), statistics (22), Open with (26) | done `9926c15` |
| **5** | **TXT, HTML, FB2, CBZ (24), then PDF (23)** | **not started** |

Left out on purpose by the user: dictionary lookup (5), own font files (8), extra themes (9), MOBI/DOCX/DJVU
(25), sync (27). No KOReader code is copied: MonoMode is MIT, KOReader is AGPL.

### Suggested shape for Phase 5

Not started and not agreed in detail, so treat as a proposal:

1. Pull an interface out of `Epub` (title, author, spine, `bytes`, `mediaType`, contents, cover, search…)
   and make `Epub` one implementation. `Reading` and `Library` only need that interface.
2. TXT and HTML: a one-chapter book whose single entry is the file wrapped in minimal XHTML.
3. FB2: XML to chapters (one per `<section>`), cover from the base64 `<binary>`.
4. CBZ: a zip of images; one "chapter" per image, served as a page with the image.
5. PDF last, and separately: Android's `PdfRenderer`, pages as bitmaps. It does not fit the WebView page
   model, so it likely needs its own small page view. Highlights, search and text options will not apply.
6. `findEpubs` and the manifest's "Open with" filter must learn the new types.

## How the work is done here

The user asked for this explicitly; keep to it.

- **Test-first, at agreed seams.** Engine unit tests are the main seam (`./gradlew :engine:test`). Each cycle:
  write the failing test, run it, write the code, run it. Format readers go through one interface with small
  generated files. The reading page (WebView) and PDF are checked by scripted emulator runs, not unit tests.
- **Smallest change that fully works.** No speculative options. Shortcuts get a `shortcut:` comment.
- **All screen text in `strings.xml`.**
- **One commit per phase**, on this branch. Do not push or open a PR unless asked.
- After code changes run `graphify update .`.
- Replies to the user: plain English, lead with what was found, say what was not verified.

## Building and testing

```bash
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :engine:test :app:assembleDebug
```

`:app:lintDebug` fails on 8 errors that predate this work (7 `StringFormatMatches` in `MainActivity.kt`, and
`QUERY_ALL_PACKAGES`). Compare only the lines for files you touched.

**Emulator.** AVD `tab34` (tablet, 2560x1600). KOReader v2026.07.1 is installed on it. Test books are on it:
`/sdcard/Download/Voyage.epub` (3 chapters, contents, an in-text link, a footnote, a blocked script, the word
"albatross" once), `/sdcard/Books/Test Book.epub`, `/sdcard/Books/Calm Seas.epub`.

- **Never run the connected (instrumented) tests on the user's phone: they wipe app data.** Ask first.
- The mode page needs a paired Wireless-debugging session, which the emulator does not give. To see the
  E-reader home without it, fake the mode: `adb shell run-as io.github.sarat03.monomode touch files/mode-E_READER.txt`,
  then `adb shell am start -n io.github.sarat03.monomode/.HomeActivity`. Remove the file afterwards.
- `tools/reader-ui.sh` has the helpers used for the scripted runs: `ui` (texts on screen), `tap "<text>"`,
  `vis` (which paragraphs of the test book are on the page), `top`.
- The emulator is left with MonoMode as its home screen and grayscale on, from an older real mode test whose
  restore record is gone. Screenshots do not show the grayscale. Turn it off for colour checks with
  `adb shell settings put secure accessibility_display_daltonizer_enabled 0`, and put it back.
- All files access is already granted to MonoMode on the emulator.

**Tool quirks in this setup.**

- The context-mode hook cuts long Bash output. Keep output short, or use `ctx_execute`.
- `ctx_execute` runs in the **main checkout**, not this worktree. `cd` to the worktree inside it, or use
  absolute paths, or it reads stale files.
- Images can be read only when small (about 10 KB): `sips -s format jpeg -s formatOptions 12 -Z 430 in.png --out out.jpg`.

## Not verified, and known rough edges

Tell the user about these rather than assume they are fine.

- **No run on a real phone** (single column, portrait) and **no real-world EPUBs**. Everything was tested
  on the tablet emulator with generated books.
- The **"Enter e-reader" button** and the mode page's reading-app picker were never exercised (needs a session).
- **Read aloud:** the speech engine started and finished pages and the pages turned; the sound was not heard.
- **Highlight colour** on the page was not confirmed by eye. **Hyphenation**, **brightness** and
  **orientation lock** were switched on without error but their effect was not measured.
- Twice in the very first scroll-based version a page turn landed in the wrong place. The paging code was
  rewritten since and it has not recurred, but the cause was never found.
- A link to *any* short passage (under 800 characters, not a heading) opens as a footnote popup. "Go there"
  covers a wrong guess.
- "Have read" is never set automatically. Deleting a book keeps what the reader remembers about it.
- The library rescans the storage on every return; fine for hundreds of books, not measured beyond four.
- The controls above the book list (search, sort, "Show as") are three rows tall. The look has not been
  reviewed by the user since Phase 2.
- With "Own home screen" switched off in the mode, the library is reachable only through "Enter e-reader".
- **MonoMode now declares All files access** (`MANAGE_EXTERNAL_STORAGE`). The manifest comment and ADR 0002
  call `QUERY_ALL_PACKAGES` "the one broad permission"; that wording is out of date and the ADR is not updated.
  Google Play restricts this permission.
