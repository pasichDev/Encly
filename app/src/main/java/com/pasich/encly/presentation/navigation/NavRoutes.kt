package com.pasich.encly.presentation.navigation

enum class NavRoutes {
    HomeRoute,
    TrashRoute,
    ArchiveRoute,
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
    WipePinRoute,
    DuressPinRoute,
    LockRoute,
    BackupRoute,
    AppearanceRoute,
    LicensesRoute,
    ImportNotesRoute,
}

/** Query argument of [NavRoutes.TasksRoute]: open the new-task sheet on arrival. */
const val TASKS_ADD_ARG = "add"
