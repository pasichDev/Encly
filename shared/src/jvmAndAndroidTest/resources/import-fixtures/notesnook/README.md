Real exports from the Notesnook web app (offline mode, no account), synthetic notes only:
Settings → Backup & export → "Export all notes" as Markdown (`notes-md.zip`), Markdown +
Frontmatter (`notes-md-frontmatter.zip`) and Plain text (`notes-txt.zip`), plus "Create backup now"
without attachments (`backup.nnbackupz`, which Encly must refuse with guidance).

- "Groceries": checklist Milk (checked), Eggs, "Хліб 🍞"; tag home.
- "Meeting notes" in notebook Work (folder `Work/`): H1, H2, bullets, numbered, quote, rule; tags work, important.
- "Ідеї 💡": two paragraphs.
- "Pinned reminder": `pinned: true`.
- "Archived recipe": in `_archive/`, `archived: true`.
- "Old draft" was moved to the trash; Notesnook does not export trashed notes.

Dates in the frontmatter follow the app's date-format setting (here `14-10-2026 10:48 PM`), not ISO.
List items are separated by blank lines. File names replace spaces with `-`.
