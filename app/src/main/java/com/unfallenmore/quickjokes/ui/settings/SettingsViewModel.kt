package com.unfallenmore.quickjokes.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.backup.BackupRepository
import com.unfallenmore.quickjokes.data.repository.ReminderSettings
import com.unfallenmore.quickjokes.data.repository.SettingsRepository
import com.unfallenmore.quickjokes.data.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsEvent {
    data object ProgressReset : SettingsEvent
    data object DataExported : SettingsEvent
    data object DataImported : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val reminderSettings: StateFlow<ReminderSettings> = settingsRepository.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var resetInProgress = false
    private var backupInProgress = false

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch {
            runCatching { settingsRepository.setThemeMode(mode) }
                .onFailure { _events.tryEmit(SettingsEvent.Error("Не удалось сохранить тему")) }
        }
    }

    // --- Напоминания ---

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setReminderEnabled(enabled) }
                .onFailure { _events.tryEmit(SettingsEvent.Error("Не удалось изменить напоминание")) }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setReminderTime(hour, minute) }
                .onFailure { _events.tryEmit(SettingsEvent.Error("Не удалось сохранить время")) }
        }
    }

    // --- Экспорт / импорт ---

    fun exportData(uri: Uri) {
        if (backupInProgress) return
        backupInProgress = true
        viewModelScope.launch {
            runCatching { backupRepository.exportTo(uri) }
                .onSuccess { _events.tryEmit(SettingsEvent.DataExported) }
                .onFailure {
                    _events.tryEmit(SettingsEvent.Error("Экспорт не удался: ${it.message}"))
                }
            backupInProgress = false
        }
    }

    fun importData(uri: Uri) {
        if (backupInProgress) return
        backupInProgress = true
        viewModelScope.launch {
            runCatching { backupRepository.importFrom(uri) }
                .onSuccess { _events.tryEmit(SettingsEvent.DataImported) }
                .onFailure {
                    _events.tryEmit(SettingsEvent.Error("Импорт не удался: ${it.message}"))
                }
            backupInProgress = false
        }
    }

    fun resetProgress() {
        if (resetInProgress) return
        resetInProgress = true
        viewModelScope.launch {
            runCatching { settingsRepository.resetProgress() }
                .onSuccess { _events.tryEmit(SettingsEvent.ProgressReset) }
                .onFailure {
                    _events.tryEmit(SettingsEvent.Error(it.message ?: "Сбой при сбросе"))
                }
            resetInProgress = false
        }
    }
}
