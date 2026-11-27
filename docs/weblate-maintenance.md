# Weblate maintenance

(Maintainers.) How translations from [Hosted Weblate](https://hosted.weblate.org/projects/encly/)
reach `main`. The project has four components, *App*, *Security*, *Handoff* and *Import*, one per
file (`strings.xml`, `strings_security.xml`, `strings_handoff.xml`, `strings_import.xml` under
`core/resources/src/commonMain/strings/`). Weblate collects the changes on its
`weblate-translations` branch and opens a pull request into `main`; it never pushes to `main`.
Weblate pulls `main` on every push through the GitHub webhook. Translator-facing rules and the
security wording check are in [CONTRIBUTING.md](../CONTRIBUTING.md#translations).

## Merge a Weblate pull request

1. Open the pull request from `weblate-translations`. Wait for the *Android CI* checks; merge only
   when all of them pass. A pull request that changes only `.xml` files runs the full CI.
2. Open the diff. Only `values-<lang>/strings*.xml` files should change, and inside them only the
   text of translated strings. Reject or send back the pull request if you see:
   - entries moved, reordered or reformatted instead of changed;
   - a different escaping (`\'`, `\"`, `&amp;`, `&lt;`) on lines whose text did not change;
   - `translatable="false"` added, removed or an entry with it translated;
   - changes in `values/` (English): the source text changes only in this repository;
   - a new `values-<lang>/` folder: a language also needs the other steps of *Adding a language* in
     CONTRIBUTING.md before its pull request can pass CI.
3. For every changed security string, do the check under *Security wording* in CONTRIBUTING.md.
4. Merge. Weblate learns about the merge through the webhook and starts the next pull request from
   the new `main`.

## Resolve a conflict between `main` and `weblate-translations`

A conflict appears when `main` changed the same lines of a translation file that Weblate also
changed, for example a string edited in Git while a Weblate pull request was open.

1. Prefer to avoid it: merge the open Weblate pull request before changing the same strings.
2. Lock the component on Weblate (*Manage → Repository maintenance → Lock*) so nobody adds changes
   while you work, then *Commit* and *Push* any pending changes so nothing is lost.
3. Merge the Weblate pull request first if it is clean, or resolve it by merging `main` into
   `weblate-translations` locally:
   ```
   git fetch origin
   git switch weblate-translations
   git merge origin/main
   ```
   Fix the conflicts in the `.xml` files, keeping the English-side change from `main` and the
   translated text from Weblate, run the CI checks, push, and merge the pull request.
4. If the translations on Weblate are not worth keeping (they are only unmerged edits), use the
   reset below instead.
5. Unlock the component.

## Reset Weblate to the repository state

Use it when Weblate's branch and `main` have diverged and Weblate's unmerged changes can be
dropped. It discards the changes on Weblate that are not in the repository, so first make sure
nothing in them is still wanted.

1. Merge or close the open Weblate pull request.
2. On Weblate, open each component (*App*, *Security*, *Handoff*, *Import*), then
   *Manage → Repository maintenance → Reset*. Weblate re-reads the branch from the repository and
   drops its own unmerged commits.
3. Check that the component shows no pending changes and that its statistics match the repository.
