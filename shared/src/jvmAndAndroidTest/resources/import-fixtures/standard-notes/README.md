Real "Download backup" (Preferences → Backups) from the Standard Notes web app 3.202.9, used
offline without an account, synthetic notes only. Without an account every note is Plain Text
(Super is a paid feature).

- "Groceries": `- [x] Milk`, `- [ ] Eggs`, `- [ ] Хліб 🍞` as text; tag home.
- "Meeting notes": Markdown-style text (H1, H2, bullets, numbered, quote, rule); tags work, important.
- "Ідеї 💡": two paragraphs.
- "Pinned reminder": pinned (`appData["org.standardnotes.sn"].pinned`).
- "Archived recipe": archived (`appData["org.standardnotes.sn"].archived`).
- "Old draft": in the trash (`content.trashed`).

Offline, `updated_at` is 1970-01-01; the edit time is `appData["org.standardnotes.sn"].client_updated_at`.
The ZIP also holds `Items/<type>/…` plain copies and an `SN|UserPreferences` item.
