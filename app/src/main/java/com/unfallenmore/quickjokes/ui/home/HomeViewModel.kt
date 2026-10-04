package com.unfallenmore.quickjokes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.repository.AchievementEvaluator
import com.unfallenmore.quickjokes.data.repository.DailyBonusResult
import com.unfallenmore.quickjokes.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Прогресс одного достижения для блока «Ближайшие достижения». */
data class AchievementProgressUi(
    val achievement: Achievement,
    val fraction: Float,
)

data class HomeUiState(
    val loading: Boolean = true,
    val coins: Int = 0,
    val streak: Int = 0,
    val totalJokes: Int = 0,
    val onboardingDone: Boolean = false,
    /** true — бонус за сегодня ещё не получен. */
    val bonusAvailable: Boolean = false,
    val bonusMessage: String? = null,
    val nextAchievements: List<AchievementProgressUi> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao,
) : ViewModel() {

    private val bonusMessage = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        profileRepository.observeProfile(),
        achievementDao.observeCatalog(),
        profileRepository.observeUnlockedAchievements(),
        bonusMessage,
    ) { profile, catalog, unlocked, message ->
        // Факты пересчитываются при каждом изменении профиля — дёшево (SQL-агрегаты)
        val facts = achievementEvaluator.collectFacts()
        val nearest = catalog
            .filter { it.code !in unlocked }
            .map {
                AchievementProgressUi(
                    achievement = it,
                    fraction = achievementEvaluator.progressFraction(it.type, facts, it.threshold),
                )
            }
            .sortedByDescending { it.fraction }
            .take(3)

        HomeUiState(
            loading = false,
            coins = profile.coins,
            streak = profile.streak,
            totalJokes = profile.totalJokes,
            onboardingDone = profile.onboardingDone,
            bonusAvailable = profile.lastLoginDate != todayIso(),
            bonusMessage = message,
            nextAchievements = nearest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Инициировать показ онбординга (кнопка «Как играть»). */
    fun onboardingShown() {
        viewModelScope.launch { profileRepository.setOnboardingDone() }
    }

    fun claimDailyBonus() {
        viewModelScope.launch {
            when (val result = profileRepository.claimDailyBonus()) {
                is DailyBonusResult.AlreadyClaimed -> {
                    _events.tryEmit("Бонус за сегодня уже получен")
                }

                is DailyBonusResult.Claimed -> {
                    bonusMessage.value =
                        "+${result.coins} монет • серия ${result.streak} дн."
                    _events.tryEmit(
                        "Ежедневный бонус: +${result.coins} монет, серия ${result.streak} дней",
                    )
                }
            }
        }
    }

    fun clearBonusMessage() {
        bonusMessage.value = null
    }

    private fun todayIso(): String =
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
}
