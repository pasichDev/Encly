A real Google Takeout export of Google Keep (October 2026), reduced to the five synthetic notes
labelled `encly-test`. Their ZIP records (local header, deflated data, data descriptor, extra
fields; flags 0x808) are byte for byte as Takeout wrote them; only the central directory was
rebuilt for the smaller set, and `Labels.txt` (which lists every label of the account) was
replaced by one listing the two test labels. The reader was also run over the full original
export: all 540 entries read with matching CRCs.

- "Encly test · Ідеї 💡": two paragraphs.
- "Encly test · Groceries": checklist; Takeout lists checked items last: Eggs, Хліб 🍞, Milk (checked);
  labels encly-home, encly-test.
- "Encly test · Pinned reminder": isPinned.
- "Encly test · Archived recipe": isArchived.
- "Encly test · Old draft": isTrashed.

Each note has a `.json` (the source of truth) and an `.html` copy; timestamps are microseconds.
