package com.pasich.encly.presentation.designsystem

/** Whether the command key of shortcuts is Meta (⌘, on macOS) rather than Ctrl (everywhere else). */
internal expect fun platformUsesCommandKey(): Boolean
