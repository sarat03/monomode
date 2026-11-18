# Plan: a books folder instead of All files access

Line numbers are against the 2026-11-17 tree, which has uncommitted edits to ModePage, MainActivity and strings.xml.

## Uses today
- **Permission.** Manifest :26. `isExternalStorageManager` is read at Reader.kt:352, HomeActivity.kt:172 and MainActivity.kt:99 (`allFiles`, used only at ModePage.kt:162).
- **`File` paths.**
  - Reader.kt: `loadCover` :171, `findBooks` :365-370, folder names :389, BookDetails :613-655, `Reading` :801.
  - Pdf.kt:81 and Catalog.kt:23.
  - HomeActivity `bookFrom` :264-272.
- **KOReader.**
  - KoReader.kt and its test.
  - ModePage `ReadingApp` :125, :146-167.
  - `Modes.reader` and ModesTest:77-82.
  - HomeActivity: `reader`, `book`, `cover`, `Reader()`, `open(Book)` and StrictMode LAX :167.
  - Manifest `file` scheme :64, Profile.kt:36 and SECURITY.md:34.

Once KOReader is removed, `grep -rnE "MANAGE_EXTERNAL|isExternalStorageManager|KOREADER|koreader" app engine` must come back empty. All files access has no other use.

## Approach: copy-on-open, engine parsers unchanged
`ZipFile` (in `Epub` and `Cbz`) needs a seekable file. A `ZipInputStream` engine would read local headers instead of the central directory, which would undo the parsing hardening just merged.
- **The copy.** Stream the book on IO into `cacheDir/book.<ext>` with a new engine `InputStream.copyCapped(to)`. Only one copy is kept, and startup deletes it along with the old `opened-*` files.
- **Cap.** `whole()` and the per-entry `readCapped` (`MAX_BOOK_BYTES`) still guard memory. The copy gets its own disk cap, `MAX_COPY_BYTES` = 512 MB, so big CBZ files still open.
- **PDF is not copied.** `PdfRenderer` takes `openFileDescriptor(uri, "r")` directly.

## Listing, covers, keys
- **Walk.** One `buildChildDocumentsUriUsingTree` query per folder (projection: id, name, MIME type, size, modified). No DocumentFile: it is not a dependency, and it makes one IPC per property. Depth, hidden-folder and `BOOK_TYPES` rules stay in engine behind a fake-able seam (ADR 0006).
- **Metadata cache** in the engine, keyed by key, size and mtime. Only new or changed books are temp-copied and parsed. Covers are saved to `filesDir/covers/`, so library rows never open a book.
- **Shelf keys** are derived from the docId:
  - `primary:rel` becomes `<getExternalStorageDirectory()>/rel` and `<UUID>:rel` becomes `/storage/<UUID>/rel`. These are today's keys exactly, so places, marks and collections carry over with no migration.
  - Other providers use `authority/docId`.
  - An "Open with" URI that has no tree uses the URI string.
- `BookInfo` gains `uri`, `name`, `folder` and `size`. `Reading` takes a key and a file.

## UX
- **Picking the folder.** `ACTION_OPEN_DOCUMENT_TREE` is started from HomeActivity only: from the library's empty state and from a "Books folder" row in `ReaderSettings` (Reader.kt:298). Starting it from MainActivity would trigger `onStop`, which ends the session (ADR 0005).
- **Grant.** Take a persistable read+write grant (write is needed for `deleteDocument`) and release the old one on change.
- **Empty-state text.** "Android 11 and later cannot use the storage root or Download itself; choose or create a folder such as Books." It is also shown when the folder or the grant is gone.
- **Errors.** Catch `ActivityNotFoundException`. Protect both DocumentsUI packages in `lists/protected.txt`.
- **"Open with"** takes `content://` only and shows "Opening…" while it copies.

## Tests
- **Engine units:** `copyCapped`, the listing rules, the key derivation and the metadata cache.
- **HomeTest:** the empty state, plus an accessibility check.
- **mm36 (API 36) only:**
  - Upgrade from the old APK, keeping a place, a mark and a collection.
  - Check the Documents-folder docId prefix: `home:` needs mapping.
  - Try "Open with" from Files, the Download root refused, a deleted folder, and a book over the cap.

## Risks
- The docId format is internal to the provider, though stable since API 19.
- The first scan of a large library is slow; show progress.
- The grant is lost if the folder moves.
- The picker result must return to HomeActivity while it is the home app.

## Steps (one commit each, checks from CONTRIBUTING)
1. Remove KOReader: everything listed above, plus its strings.
2. Engine: `copyCapped`, `MAX_COPY_BYTES`, the listing rules, the key derivation and the metadata cache, with tests. Delete `findBooks`.
3. Library: the picker, the grant, the walk, the cover cache, the `BookInfo` fields, Catalog `folder`, the empty state and the Settings row.
4. Opening: copy-on-open, PDF by descriptor, `deleteDocument`, `bookFrom`, cache cleanup.
5. Manifest: drop the permission and run the grep. Add the DocumentsUI packages to protected.txt.
6. Docs, then `graphify update .` and the mm36 pass:
   - ADR 0010: SAF folder, copy-on-open, docId keys, no KOReader.
   - features.md: the reader and modes sections.
   - CONTEXT.md: **Books folder**.
   - SECURITY.md, PLAN.md, both handoffs.
