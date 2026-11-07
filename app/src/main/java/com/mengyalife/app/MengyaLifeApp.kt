package com.mengyalife.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.advisor.AdvisorViewModel
import com.mengyalife.app.ui.components.MorrowBottomBar
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.ui.goals.GoalsViewModel
import com.mengyalife.app.ui.growth.GrowthMemoryScreen
import com.mengyalife.app.ui.growth.GrowthMemoryViewModel
import com.mengyalife.app.ui.insight.InsightUiState
import com.mengyalife.app.ui.insight.InsightViewModel
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.DiaryUiState
import com.mengyalife.app.ui.journal.JournalViewModel
import com.mengyalife.app.ui.screens.AdvisorScreen
import com.mengyalife.app.ui.screens.DecisionScreen
import com.mengyalife.app.ui.screens.DiaryScreen
import com.mengyalife.app.ui.screens.ExploreScreen
import com.mengyalife.app.ui.screens.GoalOverlays
import com.mengyalife.app.ui.screens.LifePanoramaScreen
import com.mengyalife.app.ui.screens.SettingsScreen
import com.mengyalife.app.ui.screens.TodayScreen
import com.mengyalife.app.ui.screens.TrajectoryScreen
import com.mengyalife.app.ui.settings.AiSettingsViewModel
import com.mengyalife.app.ui.settings.ExportViewModel
import com.mengyalife.app.ui.settings.RestoreMaintenanceScreen
import com.mengyalife.app.ui.settings.RestoreResultHost
import com.mengyalife.app.ui.settings.ThemeViewModel
import com.mengyalife.app.ui.theme.MengyaLifeTheme
import com.mengyalife.app.ui.theme.ThemeMode
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.weekly.WeeklyReviewScreen
import com.mengyalife.app.ui.weekly.WeeklyReviewViewModel
import com.mengyalife.app.data.weekly.WeeklyReviewRouter

/**
 * App 根组件：只负责**装配**。
 *
 * 导航状态（四个一级页 + 二级页栈）留在这里；数据与业务仍在各自
 * ViewModel / Repository。首页不主动触发高成本 AI 调用。
 */
@Composable
fun MengyaLifeApp() {
    // The restore guard must run before constructing any DB-backed ViewModel.
    val appContext = LocalContext.current.applicationContext as MengyaApplication
    val restoreGateActive by appContext.container.restoreCoordinator.gateActive.collectAsStateWithLifecycle()
    if (restoreGateActive) {
        MengyaLifeTheme(darkTheme = isSystemInDarkTheme()) { RestoreMaintenanceScreen() }
        return
    }

    // ---- 主题 ----
    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
    val persistedThemeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val themeMode: ThemeMode = persistedThemeMode ?: ThemeMode.SYSTEM

    // ---- 真实数据 ----
    val journalViewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)
    val diaryState by journalViewModel.uiState.collectAsStateWithLifecycle()

    val goalsViewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)
    val goalsState by goalsViewModel.uiState.collectAsStateWithLifecycle()

    val insightViewModel: InsightViewModel = viewModel(factory = InsightViewModel.Factory)
    val insightState by insightViewModel.state.collectAsStateWithLifecycle()

    val weeklyViewModel: WeeklyReviewViewModel = viewModel(factory = WeeklyReviewViewModel.Factory)
    val weeklyState by weeklyViewModel.state.collectAsStateWithLifecycle()
    val weeklyEnabled by weeklyViewModel.enabled.collectAsStateWithLifecycle()
    val weeklyAiEnabled by weeklyViewModel.aiEnabled.collectAsStateWithLifecycle()
    val weeklyNotificationAsked by weeklyViewModel.notificationAsked.collectAsStateWithLifecycle()

    val exportViewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()

    val advisorViewModel: AdvisorViewModel = viewModel(factory = AdvisorViewModel.Factory)
    val advisorState by advisorViewModel.uiState.collectAsStateWithLifecycle()

    val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = AiSettingsViewModel.Factory)
    val aiSettingsState by aiSettingsViewModel.state.collectAsStateWithLifecycle()

    val memoryViewModel: GrowthMemoryViewModel = viewModel(factory = GrowthMemoryViewModel.Factory)
    val memoryState by memoryViewModel.uiState.collectAsStateWithLifecycle()

    // ---- 导航状态 ----
    var currentTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMemories by rememberSaveable { mutableStateOf(false) }
    var showWeeklyReview by rememberSaveable { mutableStateOf(false) }
    var showPanorama by rememberSaveable { mutableStateOf(false) }
    var showExplore by rememberSaveable { mutableStateOf(false) }
    var showDecision by rememberSaveable { mutableStateOf(false) }

    // ---- 页面滚动位置（切换页面/设置返回不丢）----
    val todayListState = rememberLazyListState()
    val panoramaListState = rememberLazyListState()
    val trajectoryListState = rememberLazyListState()
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

    val mainVisible = !showSettings && !showMemories && !showWeeklyReview &&
        !showPanorama && !showExplore && !showDecision
    LaunchedEffect(
        currentTab, mainVisible,
        goalsState.snapshot, diaryState.entries, memoryState,
    ) {
        if (currentTab == AppTab.TODAY && mainVisible) {
            insightViewModel.refresh()
        }
    }

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val versionName = remember(context) { readVersionName(context) }

    val openPanorama: (Long?) -> Unit = { goalId ->
        if (goalId != null) goalsViewModel.onAction(GoalAction.FocusGoal(goalId))
        showPanorama = true
        showSettings = false
        showWeeklyReview = false
        showMemories = false
        showExplore = false
        showDecision = false
    }
    val discussWithAdvisor: (String) -> Unit = { text ->
        showExplore = false
        showDecision = false
        showPanorama = false
        showWeeklyReview = false
        showSettings = false
        currentTab = AppTab.ADVISOR
        advisorViewModel.onAction(AdvisorAction.InputChange(text))
    }

    MengyaLifeTheme(darkTheme = darkTheme) {
        BackHandler(enabled = showSettings) { showSettings = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            when {
                showWeeklyReview -> {
                    BackHandler { showWeeklyReview = false }
                    WeeklyReviewScreen(
                        state = weeklyState,
                        onBack = { showWeeklyReview = false },
                        onGenerate = weeklyViewModel::generateNow,
                        onGenerateAi = weeklyViewModel::generateAiNow,
                        onOpenGoal = { id -> openPanorama(id) },
                        onDiscuss = { text -> discussWithAdvisor(text) },
                        onOpenSettings = { showSettings = true },
                    )
                }

                showMemories -> {
                    BackHandler { showMemories = false }
                    GrowthMemoryScreen(
                        state = memoryState,
                        onAction = memoryViewModel::onAction,
                        onBack = { showMemories = false },
                    )
                }

                showSettings -> SettingsScreen(
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

                showExplore -> ExploreScreen(
                    onBack = { showExplore = false },
                    onOpenSettings = { showSettings = true },
                    onSaveDraft = { title, description, domain ->
                        showExplore = false
                        showPanorama = true
                        currentTab = AppTab.TODAY
                        goalsViewModel.onAction(
                            GoalAction.NewGoal(
                                level = GoalLevel.LONG_TERM,
                                parentId = null,
                                initialTitle = title,
                                initialDescription = description,
                                initialDomain = domain,
                            ),
                        )
                    },
                    onDiscussWithAdvisor = discussWithAdvisor,
                )

                showDecision -> DecisionScreen(
                    goals = goalsState.snapshot,
                    onBack = { showDecision = false },
                    onOpenSettings = { showSettings = true },
                    onDiscussWithAdvisor = discussWithAdvisor,
                )

                showPanorama -> {
                    BackHandler { showPanorama = false }
                    LifePanoramaScreen(
                    listState = panoramaListState,
                    state = goalsState,
                    onAction = goalsViewModel::onAction,
                    onBack = { showPanorama = false },
                    onOpenSettings = { showSettings = true },
                    onOpenExplore = { showExplore = true },
                    )
                }

                else -> MainShell(
                    currentTab = currentTab,
                    onTabChange = { currentTab = it },
                    onOpenSettings = { showSettings = true },
                    onOpenPanorama = { openPanorama(null) },
                    onOpenExplore = { showExplore = true },
                    onOpenDecision = { showDecision = true },
                    onOpenGoal = { id -> openPanorama(id) },
                    onCreateGoalFromToday = {
                        showPanorama = true
                        goalsViewModel.onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null))
                    },
                    todayListState = todayListState,
                    trajectoryListState = trajectoryListState,
                    diaryListState = diaryListState,
                    advisorListState = advisorListState,
                    diaryState = diaryState,
                    onDiaryAction = journalViewModel::onAction,
                    goalsState = goalsState,
                    onGoalAction = goalsViewModel::onAction,
                    weeklyState = weeklyState,
                    onOpenWeeklyReview = { showWeeklyReview = true },
                    advisorState = advisorState,
                    onAdvisorAction = advisorViewModel::onAction,
                    insightState = insightState,
                    onDiscussInsight = discussWithAdvisor,
                    onDraftInsight = { text ->
                        discussWithAdvisor(text)
                        advisorViewModel.onAction(AdvisorAction.RequestProposal)
                    },
                    onWriteDiary = {
                        currentTab = AppTab.DIARY
                        journalViewModel.onAction(DiaryAction.NewEntry)
                    },
                    onOpenDiaryEntry = { entry ->
                        currentTab = AppTab.DIARY
                        journalViewModel.onAction(DiaryAction.Open(entry))
                    },
                )
            }

            // 目标的编辑 / 进展 / 历史 / 删除：首页与全景页都可触发
            GoalOverlays(state = goalsState, onAction = goalsViewModel::onAction)
        }
    }
}

@Composable
private fun MainShell(
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPanorama: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenDecision: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onCreateGoalFromToday: () -> Unit,
    todayListState: LazyListState,
    trajectoryListState: LazyListState,
    diaryListState: LazyListState,
    advisorListState: LazyListState,
    diaryState: DiaryUiState,
    onDiaryAction: (DiaryAction) -> Unit,
    goalsState: GoalsUiState,
    onGoalAction: (GoalAction) -> Unit,
    weeklyState: com.mengyalife.app.ui.weekly.WeeklyReviewUiState,
    onOpenWeeklyReview: () -> Unit,
    advisorState: AdvisorUiState,
    onAdvisorAction: (AdvisorAction) -> Unit,
    insightState: InsightUiState,
    onDiscussInsight: (String) -> Unit,
    onDraftInsight: (String) -> Unit,
    onWriteDiary: () -> Unit,
    onOpenDiaryEntry: (com.mengyalife.app.data.local.JournalEntryEntity) -> Unit,
) {
    val colors = MorrowTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(goalsState.message) {
        val message = goalsState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onGoalAction(GoalAction.ConsumeMessage)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.canvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MorrowBottomBar(currentTab = currentTab, onTabChange = onTabChange)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (currentTab) {
                AppTab.TODAY -> TodayScreen(
                    listState = todayListState,
                    goals = goalsState.snapshot,
                    entries = diaryState.entries,
                    insights = insightState,
                    onOpenSettings = onOpenSettings,
                    onOpenPanorama = onOpenPanorama,
                    onOpenGoal = onOpenGoal,
                    onCreateGoal = onCreateGoalFromToday,
                    onOpenExplore = onOpenExplore,
                    onWriteDiary = onWriteDiary,
                    onOpenDecision = onOpenDecision,
                    onOpenTrends = { onTabChange(AppTab.PATH) },
                    onDiscussInsight = onDiscussInsight,
                    onDraftInsight = onDraftInsight,
                )

                AppTab.PATH -> TrajectoryScreen(
                    listState = trajectoryListState,
                    trends = goalsState.trends,
                    goals = goalsState.snapshot,
                    entries = diaryState.entries,
                    weekly = weeklyState,
                    onOpenSettings = onOpenSettings,
                    onOpenWeeklyReview = onOpenWeeklyReview,
                    onOpenGoal = onOpenGoal,
                    onOpenPanorama = onOpenPanorama,
                    onWriteDiary = onWriteDiary,
                    onOpenDiaryEntry = onOpenDiaryEntry,
                )

                AppTab.DIARY -> DiaryScreen(
                    listState = diaryListState,
                    state = diaryState,
                    onAction = onDiaryAction,
                    onOpenSettings = onOpenSettings,
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
    RestoreResultHost()
}

/**
 * 版本号从**实际安装的包信息**读取，而不是写死一个假版本。
 */
@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "未知"
