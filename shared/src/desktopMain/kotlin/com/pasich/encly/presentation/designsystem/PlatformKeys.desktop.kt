package com.pasich.encly.presentation.designsystem

internal actual fun platformUsesCommandKey(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
