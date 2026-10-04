package com.unfallenmore.quickjokes.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unfallenmore.quickjokes.ui.MainViewModel
import com.unfallenmore.quickjokes.ui.achievements.AchievementsScreen
import com.unfallenmore.quickjokes.ui.history.HistoryScreen
import com.unfallenmore.quickjokes.ui.history.SessionDetailsScreen
import com.unfallenmore.quickjokes.ui.home.HomeScreen
import com.unfallenmore.quickjokes.ui.onboarding.OnboardingScreen
import com.unfallenmore.quickjokes.ui.results.ResultsScreen
import com.unfallenmore.quickjokes.ui.session.SessionScreen
import com.unfallenmore.quickjokes.ui.settings.SettingsScreen
import com.unfallenmore.quickjokes.ui.setup.SessionSetupScreen
import com.unfallenmore.quickjokes.ui.shop.ShopScreen
import com.unfallenmore.quickjokes.ui.stats.StatsScreen
import com.unfallenmore.quickjokes.ui.topics.MyTopicsScreen
import com.unfallenmore.quickjokes.ui.topics.TopicEditorScreen
import com.unfallenmore.quickjokes.ui.topics.TopicsScreen

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Главная", Icons.Default.Home),
    BottomItem(Routes.HISTORY, "История", Icons.AutoMirrored.Filled.List),
    BottomItem(Routes.STATS, "Статистика", Icons.Default.BarChart),
    BottomItem(Routes.ACHIEVEMENTS, "Награды", Icons.Default.EmojiEvents),
)

/** Корневой экран: сплэш → онбординг/главный экран, Scaffold + NavHost. */
@Composable
fun QuickJokesAppRoot() {
    val mainViewModel: MainViewModel = hiltViewModel()
    val onboardingDone by mainViewModel.onboardingDone.collectAsStateWithLifecycle()

    // Пока не решили, куда стартовать — не строим граф навигации
    if (onboardingDone == null) {
        StartupSplash()
        return
    }

    // Стартовый маршрут фиксируется один раз: завершение онбординга
    // не должно пересоздавать граф.
    val startRoute = remember {
        if (onboardingDone == true) Routes.HOME else Routes.ONBOARDING
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        // Системные inset'ы (статус-бар, жестовая зона) обрабатывает каждый экран
        // своим Scaffold'ом — здесь они продублировались бы лишним отступом сверху.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onStart = {
                        mainViewModel.completeOnboarding()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onStartClick = { navController.navigate(Routes.TOPICS) },
                    onShopClick = { navController.navigate(Routes.SHOP) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onAchievementsClick = { navController.navigate(Routes.ACHIEVEMENTS) },
                )
            }

            composable(Routes.TOPICS) {
                TopicsScreen(
                    onBack = { navController.popBackStack() },
                    onTopicClick = { topic ->
                        navController.navigate(Routes.sessionSetup(topic.id))
                    },
                    onAddClick = { navController.navigate(Routes.MY_TOPICS) },
                )
            }

            composable(
                route = Routes.TOPIC_EDITOR,
                arguments = listOf(
                    // LongType не поддерживает nullable: отсутствие аргумента
                    // кодируем сантинелом -1 (= «новая тема»)
                    navArgument("topicId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                TopicEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.MY_TOPICS) {
                MyTopicsScreen(
                    onBack = { navController.popBackStack() },
                    onCreateClick = { navController.navigate(Routes.topicEditor()) },
                    onEditClick = { topicId ->
                        navController.navigate(Routes.topicEditor(topicId))
                    },
                )
            }

            composable(Routes.SHOP) {
                ShopScreen(
                    onBack = { navController.popBackStack() },
                    onAddTopic = { navController.navigate(Routes.topicEditor()) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.SESSION_SETUP,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType }),
            ) { entry ->
                val topicId = entry.arguments?.getLong("topicId") ?: return@composable
                SessionSetupScreen(
                    onBack = { navController.popBackStack() },
                    onStartClick = { durationSec, difficulty, inputMode ->
                        navController.navigate(
                            Routes.session(topicId, durationSec, difficulty, inputMode),
                        ) {
                            popUpTo(Routes.TOPICS) { inclusive = false }
                        }
                    },
                )
            }

            composable(
                route = Routes.SESSION,
                arguments = listOf(
                    navArgument("topicId") { type = NavType.LongType },
                    navArgument("durationSec") {
                        type = NavType.IntType
                        defaultValue = 60
                    },
                    navArgument("difficulty") {
                        type = NavType.StringType
                        defaultValue = "MEDIUM"
                    },
                    navArgument("inputMode") {
                        type = NavType.StringType
                        defaultValue = "TEXT"
                    },
                ),
            ) {
                SessionScreen(
                    onFinished = { sessionId ->
                        navController.navigate(Routes.results(sessionId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }

            composable(
                route = Routes.RESULTS,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                ResultsScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(Routes.sessionDetails(sessionId))
                    },
                )
            }

            composable(
                route = Routes.SESSION_DETAILS,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                SessionDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(Routes.ACHIEVEMENTS) {
                AchievementsScreen()
            }
        }
    }
}

/** Краткий сплэш, пока загружается профиль и решается стартовый маршрут. */
@Composable
private fun StartupSplash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "QuickJokes",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
