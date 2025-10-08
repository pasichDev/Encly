package com.pasich.encly.utils

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch

/**
 * Класс для управления обновлениями приложения
 */
class AppUpdateHelper(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        const val UPDATE_REQUEST_CODE = 500
        private const val TAG = "AppUpdateHelper"
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    
    private val installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                Log.d(TAG, "Обновление загружено, готово к установке")
                appUpdateManager.completeUpdate()
            }
            InstallStatus.INSTALLED -> {
                Log.d(TAG, "Обновление установлено успешно")
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "Ошибка установки обновления: ${state.installErrorCode()}")
            }
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytesToDownload = state.totalBytesToDownload()
                val progress = if (totalBytesToDownload > 0) {
                    (bytesDownloaded * 100 / totalBytesToDownload).toInt()
                } else 0
                Log.d(TAG, "Загрузка обновления: $progress%")
            }
            else -> {
                Log.d(TAG, "Статус установки: ${state.installStatus()}")
            }
        }
    }

    init {
        appUpdateManager.registerListener(installStateUpdatedListener)
    }

    /**
     * Проверка наличия обновлений и их установка
     */
    fun checkForUpdates() {
        lifecycleOwner.lifecycleScope.launch {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        Log.d(TAG, "Доступно обновление: ${appUpdateInfo.availableVersionCode()}")
                        
                        // Приоритизируем обязательное обновление
                        if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            startImmediateUpdate(appUpdateInfo)
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            startFlexibleUpdate(appUpdateInfo)
                        }
                    }
                    UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                        Log.d(TAG, "Обновления отсутствуют")
                    }
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        Log.d(TAG, "Процесс обновления уже запущен")
                        // Продолжаем обязательное обновление, если оно было прервано
                        startImmediateUpdate(appUpdateInfo)
                    }
                    else -> {
                        Log.d(TAG, "Статус проверки обновлений: ${appUpdateInfo.updateAvailability()}")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Ошибка при проверке обновлений", e)
            }
        }
    }

    /**
     * Запуск обязательного обновления
     */
    private fun startImmediateUpdate(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Ошибка при запуске обязательного обновления", e)
        }
    }

    /**
     * Запуск гибкого обновления
     */
    private fun startFlexibleUpdate(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Ошибка при запуске гибкого обновления", e)
        }
    }

    /**
     * Проверка состояния обновления при возобновлении активности
     */
    fun checkUpdateStatus() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            // Если обновление уже загружено, предлагаем установить его
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
            
            // Если обязательное обновление было прервано, продолжаем его
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate(appUpdateInfo)
            }
        }
    }

    /**
     * Обработка результата запроса на обновление
     * Вызывать в onActivityResult
     */
    fun processUpdateResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Обновление было отклонено пользователем")
            
            // Если пользователь отклонил обновление, проверяем, было ли оно обязательным
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    // Если обязательное обновление доступно, но отклонено - запускаем его снова
                    startImmediateUpdate(appUpdateInfo)
                }
            }
        }
    }

    /**
     * Освобождение ресурсов
     * Вызывать в onDestroy
     */
    fun cleanup() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }
}
