package com.unfallenmore.quickjokes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние первого запуска: пока null — решаем, куда стартовать
 * (онбординг или главный экран).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _onboardingDone = MutableStateFlow<Boolean?>(null)

    /** null = ещё не загружено. */
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            _onboardingDone.value = profile.onboardingDone
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            profileRepository.setOnboardingDone()
            _onboardingDone.value = true
        }
    }
}
