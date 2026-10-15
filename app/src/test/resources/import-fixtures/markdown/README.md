Generic Markdown/TXT input. `obsidian-vault/` is a folder as Obsidian keeps it on disk (Obsidian
has no export format; a vault is plain files): YAML frontmatter with tags as a list and as a flow
sequence, an `aliases` key, `- [ ]` tasks, a nested bullet, a callout (`> [!note]`), wiki links
`[[…]]` and `[[…|alias]]`, an inline `#tag`, a `.obsidian/` settings folder and an attachment that
must be skipped, plus a Markor-style `.txt` note. Joplin's Markdown exports in `../joplin/md` and
`../joplin/md_frontmatter` are real generic-Markdown inputs too.
