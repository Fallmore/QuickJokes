package com.unfallenmore.quickjokes.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.repository.AchievementEvaluator
import com.unfallenmore.quickjokes.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AchievementTab(val label: String) {
    UNLOCKED("Полученные"),
    LOCKED("Закрытые"),
}

data class AchievementRowUi(
    val achievement: Achievement,
    val unlocked: Boolean,
    val fraction: Float,
)

data class AchievementsUiState(
    val loading: Boolean = true,
    val tab: AchievementTab = AchievementTab.UNLOCKED,
    val rows: List<AchievementRowUi> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    achievementDao: AchievementDao,
    private val profileRepository: ProfileRepository,
    private val evaluator: AchievementEvaluator,
) : ViewModel() {

    private val tab = MutableStateFlow(AchievementTab.UNLOCKED)

    val uiState: StateFlow<AchievementsUiState> = combine(
        achievementDao.observeCatalog(),
        profileRepository.observeUnlockedAchievements(),
        tab,
    ) { catalog, unlocked, activeTab ->
        // Агрегаты пересчитываются на каждое изменение — SQL COUNT(*) дёшев
        val facts = evaluator.collectFacts()
        val rows = catalog
            .map { def ->
                AchievementRowUi(
                    achievement = def,
                    unlocked = def.code in unlocked,
                    fraction = evaluator.progressFraction(def.type, facts, def.threshold),
                )
            }
            .filter { row ->
                if (activeTab == AchievementTab.UNLOCKED) row.unlocked else !row.unlocked
            }

        AchievementsUiState(
            loading = false,
            tab = activeTab,
            rows = rows,
            unlockedCount = unlocked.size,
            totalCount = catalog.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    fun onTabChange(value: AchievementTab) {
        tab.value = value
    }
}
