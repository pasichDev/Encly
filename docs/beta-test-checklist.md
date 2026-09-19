# Beta device test checklist

Run these scenarios on a physical Android device using a release APK. Record the app version,
Android version and result for each failure.

## Fresh vault

### PIN-only setup

Steps:

1. Install the release APK on a device with no existing Encly data.
2. Complete auto-managed onboarding and create a six-digit PIN.
3. Create a note and a task, then close and reopen the app.

Expected result:

- Setup completes only after the vault opens.
- The app starts at the lock screen after reopening.
- The note and task are present after unlocking.

### Recovery setup

Steps:

1. Start a fresh vault using the recovery-seed option.
2. Record the displayed twelve words offline; do not use clipboard or sharing.
3. Lock the app and unlock once with the recovery seed.

Expected result:

- The recovery seed unlocks the same local vault.
- An invalid word or wrong order does not unlock it.
- Cancelling returns to the lock screen without changing vault data.

## Locking and biometrics

### PIN lockout

Steps:

1. Enter an incorrect PIN five times.
2. Immediately enter the correct PIN.
3. Wait for the displayed lockout period and retry.

Expected result:

- The correct PIN is rejected during the lockout.
- Unlock succeeds only after the lockout has elapsed.

### Biometric lifecycle

Steps:

1. Enable biometrics from Security settings.
2. Cancel the prompt, then try again and authenticate.
3. Lock the app, unlock with biometrics, then remove or add a device fingerprint.
4. Return to Encly.

Expected result:

- Cancellation does not change the persisted toggle.
- A successful prompt unlocks the vault without a duplicate prompt or PIN flash.
- Enrollment changes invalidate the biometric slot safely; PIN or recovery remains usable.

### Background and process death

Steps:

1. Edit a note, leave the app with Home/app switch, then return.
2. Repeat while the keyboard is visible.
3. From Recents, force-stop Encly and reopen it.

Expected result:

- Pending editor changes persist or a visible save failure is shown.
- Protected content is not visible in Recents before unlock.
- Reopen always requires an unlock factor.

## Data operations

### Notes

Steps:

1. Create a note, edit it twice, use Done and system Back.
2. Duplicate it, move one copy to Trash, restore it, then permanently delete it.
3. Create an empty draft and discard it.

Expected result:

- Each save creates or updates exactly one note.
- Trash, restore and permanent delete affect the intended record only.
- Discarded drafts do not reappear.

### Tasks and tags

Steps:

1. Create, edit, complete and uncomplete a task.
2. Clear completed tasks.
3. Create, rename, reorder and delete tags; delete the currently selected tag.

Expected result:

- UI changes only after persistence succeeds.
- No task/tag operation silently reports success after a storage failure.
- Lists refresh without showing stale tag data.

## Privacy and platform checks

Steps:

1. Try screenshots and screen recording on the lock, note editor and recovery screens.
2. Inspect Android backup/device-transfer settings.
3. Type a note, task, tag, link and recovery seed; inspect keyboard suggestions.
4. Inspect logcat while creating and editing protected content.

Expected result:

- Screenshots, recording and recents previews are blocked.
- App data is excluded from backup and device transfer.
- Protected fields do not enable personalized learning or autofill.
- No protected plaintext, keys, PINs or recovery words reach logcat.

## Release install

Steps:

1. Install the generated release APK on a clean device.
2. Install the next release build over it after completing the above scenarios.

Expected result:

- Installation succeeds with R8 enabled.
- The upgrade path does not expose or silently replace an existing vault.
