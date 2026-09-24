package com.pasich.encly.presentation.navigation

enum class NavRoutes {
    HomeRoute,
    TrashRoute,
    SettingsRoute,
    EditNoteRoute,
    EditTagRoute,
    AboutRoute,
    SupportRoute,
    FaqRoute,
    TasksRoute,
    SecuritySettingsRoute,
    OnboardingRoute,
    LossDataRoute,
    LegacyVaultRoute,
    PinCodeConfig,
    LockRoute,
    BackupRoute,
    AppearanceRoute,
    LicensesRoute,
}

/** Query argument of [NavRoutes.TasksRoute]: open the new-task sheet on arrival. */
const val TASKS_ADD_ARG = "add"
