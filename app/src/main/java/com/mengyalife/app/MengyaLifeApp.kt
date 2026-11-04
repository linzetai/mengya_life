package com.mengyalife.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.advisor.AdvisorViewModel
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.components.AppTopBar
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.ui.goals.GoalsViewModel
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.DiaryUiState
import com.mengyalife.app.ui.journal.JournalViewModel
import com.mengyalife.app.ui.screens.AdvisorScreen
import com.mengyalife.app.ui.screens.DiaryScreen
import com.mengyalife.app.ui.screens.GoalOverlays
import com.mengyalife.app.ui.screens.GoalsScreen
import com.mengyalife.app.ui.screens.OverviewScreen
import com.mengyalife.app.ui.screens.SettingsScreen
import com.mengyalife.app.ui.screens.TrendsScreen
import com.mengyalife.app.ui.growth.GrowthMemoryAction
import com.mengyalife.app.ui.growth.GrowthMemoryScreen
import com.mengyalife.app.ui.growth.GrowthMemoryViewModel
import com.mengyalife.app.ui.insight.InsightUiState
import com.mengyalife.app.ui.insight.InsightViewModel
import com.mengyalife.app.ui.weekly.WeeklyReviewScreen
import com.mengyalife.app.ui.weekly.WeeklyReviewViewModel
import com.mengyalife.app.data.weekly.WeeklyReviewRouter
import com.mengyalife.app.ui.settings.AiSettingsViewModel
import com.mengyalife.app.ui.settings.ExportViewModel
import com.mengyalife.app.ui.settings.ThemeViewModel
import com.mengyalife.app.ui.theme.MengyaLifeTheme
import com.mengyalife.app.ui.theme.ThemeMode

/**
 * App 根组件：只负责**装配**。
 *
 * - 导航状态（当前一级页 / 是否在设置页）留在这里；
 * - 日记 → [JournalViewModel]；目标 → [GoalsViewModel]；主题 → [ThemeViewModel]；
 * - 各页面纯渲染，根组件里不出现数据库、时间戳或校验逻辑。
 */
@Composable
fun MengyaLifeApp() {
    // ---- 主题 ----
    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
    val persistedThemeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val themeMode: ThemeMode = persistedThemeMode ?: ThemeMode.SYSTEM

    // ---- 日记 ----
    val journalViewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)
    val diaryState by journalViewModel.uiState.collectAsStateWithLifecycle()

    // ---- 目标 ----
    val goalsViewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)
    val goalsState by goalsViewModel.uiState.collectAsStateWithLifecycle()

    val insightViewModel: InsightViewModel = viewModel(factory = InsightViewModel.Factory)
    val insightState by insightViewModel.state.collectAsStateWithLifecycle()
    val weeklyViewModel: WeeklyReviewViewModel = viewModel(factory = WeeklyReviewViewModel.Factory)
    val weeklyState by weeklyViewModel.state.collectAsStateWithLifecycle()
    val weeklyEnabled by weeklyViewModel.enabled.collectAsStateWithLifecycle()
    val weeklyAiEnabled by weeklyViewModel.aiEnabled.collectAsStateWithLifecycle()
    val weeklyNotificationAsked by weeklyViewModel.notificationAsked.collectAsStateWithLifecycle()

    // ---- 数据导出（设置页）----
    val exportViewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()

    // ---- 导航状态 ----
    var currentTab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMemories by rememberSaveable { mutableStateOf(false) }
    var showWeeklyReview by rememberSaveable { mutableStateOf(false) }
    val memoryViewModel: GrowthMemoryViewModel = viewModel(factory = GrowthMemoryViewModel.Factory)
    val memoryState by memoryViewModel.uiState.collectAsStateWithLifecycle()

    // ---- 页面滚动位置（进设置返回不丢）----
    val overviewListState = rememberLazyListState()
    val goalsListState = rememberLazyListState()
    val trendsListState = rememberLazyListState()
    val diaryListState = rememberLazyListState()
    val advisorListState = rememberLazyListState()

    val weeklyOpenRequest by WeeklyReviewRouter.requests.collectAsStateWithLifecycle()
    LaunchedEffect(weeklyOpenRequest) {
        if (weeklyOpenRequest) {
            showWeeklyReview = true
            showSettings = false
            WeeklyReviewRouter.consume()
        }
    }
    LaunchedEffect(
        currentTab, showSettings, showMemories,
        goalsState.snapshot, diaryState.entries, memoryState,
    ) {
        if (currentTab == AppTab.OVERVIEW && !showSettings && !showMemories) {
            insightViewModel.refresh()
        }
    }

    // ---- AI 顾问（真实请求 + 本地持久化）----
    val advisorViewModel: AdvisorViewModel = viewModel(factory = AdvisorViewModel.Factory)
    val advisorState by advisorViewModel.uiState.collectAsStateWithLifecycle()

    // ---- AI 设置（BYOK 配置 / 测试连接 / 删除密钥）----
    val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = AiSettingsViewModel.Factory)
    val aiSettingsState by aiSettingsViewModel.state.collectAsStateWithLifecycle()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val versionName = remember(context) { readVersionName(context) }

    MengyaLifeTheme(darkTheme = darkTheme) {
        // 系统返回键：在设置页时先关闭设置，回到原来的一级页面。
        BackHandler(enabled = showSettings) { showSettings = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            if (showWeeklyReview) {
                BackHandler { showWeeklyReview = false }
                WeeklyReviewScreen(
                    state = weeklyState,
                    onBack = { showWeeklyReview = false },
                    onGenerate = weeklyViewModel::generateNow,
                    onGenerateAi = weeklyViewModel::generateAiNow,
                    onOpenGoal = { id ->
                        goalsViewModel.onAction(GoalAction.FocusGoal(id))
                        showWeeklyReview = false
                        showSettings = false
                        currentTab = AppTab.GOALS
                    },
                    onDiscuss = { text ->
                        showWeeklyReview = false
                        showSettings = false
                        currentTab = AppTab.ADVISOR
                        advisorViewModel.onAction(AdvisorAction.InputChange(text))
                    },
                )
            } else if (showMemories) {
                BackHandler { showMemories = false }
                GrowthMemoryScreen(
                    state = memoryState,
                    onAction = memoryViewModel::onAction,
                    onBack = { showMemories = false },
                )
            } else if (showSettings) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeViewModel.setThemeMode(it) },
                    onBack = { showSettings = false },
                    versionName = versionName,
                    exportState = exportState,
                    onExportRequested = exportViewModel::requestExport,
                    onExportConfirmed = exportViewModel::confirmExport,
                    onExportDestination = exportViewModel::onDestinationChosen,
                    onExportCancelled = exportViewModel::cancelExport,
                    onExportDestinationCancelled = exportViewModel::onDestinationCancelled,
                    onExportMessageConsumed = exportViewModel::consumeMessage,
                    aiState = aiSettingsState,
                    onAiAction = aiSettingsViewModel::onAction,
                    onOpenMemories = { showMemories = true },
                    weeklyEnabled = weeklyEnabled,
                    onWeeklyToggle = { enabled ->
                        if (enabled && !weeklyNotificationAsked && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            weeklyViewModel.markNotificationAsked()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        weeklyViewModel.setEnabled(enabled)
                    },
                    onOpenWeeklyReview = { showWeeklyReview = true },
                    weeklyAiEnabled = weeklyAiEnabled,
                    onWeeklyAiToggle = weeklyViewModel::setAiEnabled,
                )
            } else {
                MainShell(
                    currentTab = currentTab,
                    onTabChange = { currentTab = it },
                    onOpenSettings = { showSettings = true },
                    overviewListState = overviewListState,
                    goalsListState = goalsListState,
                    trendsListState = trendsListState,
                    diaryListState = diaryListState,
                    advisorListState = advisorListState,
                    diaryState = diaryState,
                    onDiaryAction = journalViewModel::onAction,
                    goalsState = goalsState,
                    onGoalAction = goalsViewModel::onAction,
                    onCreateGoalFromOverview = {
                        currentTab = AppTab.GOALS
                        goalsViewModel.onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null))
                    },
                    advisorState = advisorState,
                    onAdvisorAction = advisorViewModel::onAction,
                    insightState = insightState,
                    onGenerateInsight = insightViewModel::generate,
                )
            }

            // 目标的编辑 / 进展 / 历史 / 删除确认：跨页面可用
            GoalOverlays(state = goalsState, onAction = goalsViewModel::onAction)
        }
    }
}

@Composable
private fun MainShell(
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    onOpenSettings: () -> Unit,
    overviewListState: LazyListState,
    goalsListState: LazyListState,
    trendsListState: LazyListState,
    diaryListState: LazyListState,
    advisorListState: LazyListState,
    diaryState: DiaryUiState,
    onDiaryAction: (DiaryAction) -> Unit,
    goalsState: GoalsUiState,
    onGoalAction: (GoalAction) -> Unit,
    onCreateGoalFromOverview: () -> Unit,
    advisorState: AdvisorUiState,
    onAdvisorAction: (AdvisorAction) -> Unit,
    insightState: InsightUiState,
    onGenerateInsight: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // 目标操作的结果提示（删除影响、状态更新、失败原因）
    LaunchedEffect(goalsState.message) {
        val message = goalsState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onGoalAction(GoalAction.ConsumeMessage)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppTopBar(title = currentTab.label, onSettingsClick = onOpenSettings)
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { onTabChange(tab) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(text = tab.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag(tab.testTag),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (currentTab) {
                AppTab.OVERVIEW -> OverviewScreen(
                    listState = overviewListState,
                    goals = goalsState.snapshot,
                    onOpenGoals = { onTabChange(AppTab.GOALS) },
                    onCreateGoal = onCreateGoalFromOverview,
                    onOpenAdvisor = { onTabChange(AppTab.ADVISOR) },
                    insights = insightState,
                    onGenerateInsight = onGenerateInsight,
                    onOpenTrends = { onTabChange(AppTab.TRENDS) },
                    onOpenInsightGoal = { id ->
                        onGoalAction(GoalAction.FocusGoal(id))
                        onTabChange(AppTab.GOALS)
                    },
                    onDiscussInsight = { text ->
                        onTabChange(AppTab.ADVISOR)
                        onAdvisorAction(AdvisorAction.InputChange(text))
                    },
                    onDraftInsight = { text ->
                        onTabChange(AppTab.ADVISOR)
                        onAdvisorAction(AdvisorAction.InputChange(text))
                        onAdvisorAction(AdvisorAction.RequestProposal)
                    },
                )

                AppTab.GOALS -> GoalsScreen(
                    listState = goalsListState,
                    state = goalsState,
                    onAction = onGoalAction,
                )

                AppTab.TRENDS -> TrendsScreen(
                    listState = trendsListState,
                    trends = goalsState.trends,
                )

                AppTab.DIARY -> DiaryScreen(
                    listState = diaryListState,
                    state = diaryState,
                    onAction = onDiaryAction,
                )

                AppTab.ADVISOR -> AdvisorScreen(
                    listState = advisorListState,
                    state = advisorState,
                    onAction = onAdvisorAction,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

/**
 * 版本号从**实际安装的包信息**读取，而不是写死一个假版本。
 */
@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "未知"
