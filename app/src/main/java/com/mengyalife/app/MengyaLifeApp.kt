package com.mengyalife.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.produceState
import com.mengyalife.app.ui.candidates.*
import com.mengyalife.app.ui.presentation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorTestBus
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
import com.mengyalife.app.ui.screens.TodayProductScreen
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    val requestedCandidate by DesignPreview.mode.collectAsStateWithLifecycle()
    val designCandidate = if (BuildConfig.QA_BUILD && BuildConfig.APPLICATION_ID.endsWith(".qa0254")) {
        requestedCandidate
    } else UiDesignCandidate.NONE

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

    val careViewModel: com.mengyalife.app.ui.care.CareViewModel =
        viewModel(factory = com.mengyalife.app.ui.care.CareViewModel.Factory)
    val careState by careViewModel.state.collectAsStateWithLifecycle()


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
    // 今天页的近期小计划：只在 ACTIVE 且时间锚定在当下时出现。
    val planRepository = appContext.container.smallPlanRepository
    val planDay = com.mengyalife.app.ui.components.rememberToday()
    val allPlansState by remember(planRepository) { planRepository.observeAll().map { it to true } }
        .collectAsStateWithLifecycle(initialValue = emptyList<com.mengyalife.app.data.local.plan.SmallPlanEntity>() to false)
    val allPlans = allPlansState.first
    val allPlansReady = allPlansState.second
    val planProgressFlow = remember(planRepository) { planRepository.observeProgressCounts().map { it to true } }
    val planProgressState by planProgressFlow.collectAsStateWithLifecycle(initialValue = emptyMap<Long, Int>() to false)
    val planProgressCounts = planProgressState.first
    val planProgressCountsReady = planProgressState.second
    val weeklyProgressFlow = remember(planRepository, planDay) {
        val currentZone = java.time.ZoneId.systemDefault()
        val start = planDay.with(java.time.DayOfWeek.MONDAY).atStartOfDay(currentZone).toInstant().toEpochMilli()
        planRepository.observeProgressBetween(start, System.currentTimeMillis()).map { it to true }
    }
    val weeklyProgressState by weeklyProgressFlow.collectAsStateWithLifecycle(
        initialValue = emptyList<com.mengyalife.app.data.local.plan.PlanProgressEntity>() to false,
    )
    val weeklyPlanProgress = weeklyProgressState.first
    val weeklyPlanProgressReady = weeklyProgressState.second
    val homeCheckinState by remember(appContext.container) {
        appContext.container.databaseHandle.goalDao().observeAllCheckins().map { it to true }
    }.collectAsStateWithLifecycle(initialValue = emptyList<com.mengyalife.app.data.local.goal.GoalCheckinEntity>() to false)
    val homeGoalCheckins = homeCheckinState.first
    val homeGoalCheckinsReady = homeCheckinState.second
    val todayPlans = androidx.compose.runtime.remember(allPlans, planProgressCounts, planDay) {
        val now = System.currentTimeMillis()
        allPlans
            .filter {
                it.status == "ACTIVE" &&
                    (it.timeScope.isNullOrBlank() ||
                        com.mengyalife.app.domain.goals.PlanTime.isCurrent(it.timeScope, it.createdAt, now))
            }
            .sortedByDescending { it.updatedAt }
            .take(3)
            .map {
                com.mengyalife.app.ui.screens.PlanTodayItem(
                    planId = it.id,
                    title = it.title,
                    timeScope = it.timeScope,
                    progressCount = planProgressCounts[it.id] ?: 0,
                )
            }
    }
    val trajectoryPlans = androidx.compose.runtime.remember(allPlans, planProgressCounts) {
        allPlans.sortedByDescending { it.updatedAt }.map {
            com.mengyalife.app.ui.screens.PlanTrajectoryItem(
                id = it.id,
                title = it.title,
                status = it.status,
                timeScope = it.timeScope,
                createdAt = it.createdAt,
                progressCount = planProgressCounts[it.id] ?: 0,
            )
        }
    }



    // 顾问保存日记后，跳到日记页同一篇（不复制数据）。
    LaunchedEffect(advisorState.recordAction?.openDiaryEntryId) {
        val entryId = advisorState.recordAction?.openDiaryEntryId ?: return@LaunchedEffect
        currentTab = AppTab.DIARY
        showPanorama = false
        showExplore = false
        showDecision = false
        showSettings = false
        showWeeklyReview = false
        journalViewModel.onAction(DiaryAction.OpenById(entryId))
        advisorViewModel.onAction(AdvisorAction.DismissRecordAction)
    }

    LaunchedEffect(currentTab) {
        if (currentTab == AppTab.TODAY) careViewModel.refresh()
    }

    val careSettings = remember { com.mengyalife.app.data.care.CareSettings(appContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCareCard = androidx.compose.runtime.rememberUpdatedState(careState.card)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (currentCareCard.value != null && careSettings.notificationsEnabled) {
                    careViewModel.notifyIfNeeded(appContext)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        com.mengyalife.app.data.care.CareOpenBus.pending.collect { id ->
            if (id == null) return@collect
            currentTab = AppTab.ADVISOR
            showPanorama = false
            showExplore = false
            showDecision = false
            showSettings = false
            showWeeklyReview = false
            careViewModel.onNotificationOpen(id)
            com.mengyalife.app.data.care.CareOpenBus.clear()
        }
    }
    LaunchedEffect(careState.openAdvisorText) {
        val text = careState.openAdvisorText ?: return@LaunchedEffect
        currentTab = AppTab.ADVISOR
        showPanorama = false
        showExplore = false
        showDecision = false
        showSettings = false
        showWeeklyReview = false
        if (text.isNotBlank()) {
            advisorViewModel.onAction(AdvisorAction.InputChange(text))
        }
        careViewModel.consumeOpenAdvisor()
    }

    // ---- 页面滚动位置（切换页面/设置返回不丢）----
    val todayListState = rememberLazyListState()
    val panoramaListState = rememberLazyListState()
    val trajectoryListState = rememberLazyListState()
    val diaryListState = rememberLazyListState()
    val advisorListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!BuildConfig.QA_BUILD) return@LaunchedEffect
        AdvisorTestBus.prompts.collect { prompt ->
            if (prompt == null) return@collect
            AdvisorTestBus.clear()
            currentTab = AppTab.ADVISOR
            showSettings = false
            showMemories = false
            showWeeklyReview = false
            showPanorama = false
            showExplore = false
            showDecision = false
            advisorViewModel.onAction(AdvisorAction.InputChange(prompt))
            // 等配置与授权从 DataStore 读入后再发送；QA 注入只用于真机验收。
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                advisorViewModel.uiState.first { it.configured && it.consentValid }
            }
            advisorViewModel.onAction(AdvisorAction.Send)
        }
    }

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
    DisposableEffect(lifecycleOwner, currentTab, mainVisible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentTab == AppTab.TODAY && mainVisible) {
                insightViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    LaunchedEffect(advisorState.recordAction?.openGoalId) {
        val goalId = advisorState.recordAction?.openGoalId ?: return@LaunchedEffect
        openPanorama(goalId)
        advisorViewModel.onAction(AdvisorAction.DismissRecordAction)
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

    DesignCandidateTheme(candidate = designCandidate, dark = darkTheme) {
        BackHandler(enabled = showSettings) { showSettings = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MorrowTheme.colors.canvas)
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
                    candidate = designCandidate,
                    allPlans = allPlans,
                    allPlansReady = allPlansReady,
                    progressCounts = planProgressCounts,
                    weeklyPlanProgress = weeklyPlanProgress,
                    weeklyPlanProgressReady = weeklyPlanProgressReady,
                    planProgressCountsReady = planProgressCountsReady,
                    onRefreshCare = { careViewModel.refresh() },
                    currentTab = currentTab,
                    onTabChange = { currentTab = it },
                    onOpenSettings = { showSettings = true },
                    onOpenPanorama = { openPanorama(null) },
                    onOpenExplore = { showExplore = true },
                    onOpenDecision = { showDecision = true },
                    onOpenGoal = { id -> openPanorama(id) },
                    todayPlans = todayPlans,
                    allGoalCheckins = homeGoalCheckins,
                    allGoalCheckinsReady = homeGoalCheckinsReady,
                    trajectoryPlans = trajectoryPlans,
                    careCard = careState.card,
                    onCareEngage = careViewModel::engage,
                    onCareIgnore = careViewModel::ignore,
                    onCareSilence = careViewModel::silence,
                    onOpenPlan = { title ->
                        currentTab = AppTab.ADVISOR
                        showPanorama = false
                        showExplore = false
                        showDecision = false
                        showSettings = false
                        showWeeklyReview = false
                        advisorViewModel.onAction(AdvisorAction.InputChange("聊聊这个计划：$title"))
                    },
                    onCreateGoalFromToday = {
                        showPanorama = true
                        goalsViewModel.onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null))
                    },
                    onRecordGoalFromToday = { id ->
                        findGoalInTree(goalsState.snapshot.roots, id)?.let { goalsViewModel.onAction(GoalAction.OpenCheckin(it)) }
                            ?: openPanorama(id)
                    },
                    onOpenRecentCheckinFromToday = { checkin ->
                        findGoalInTree(goalsState.snapshot.roots, checkin.goalId)?.let { goalsViewModel.onAction(GoalAction.OpenHistory(it)) }
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
                    onGenerateInsight = insightViewModel::generate,
                    onDiscussInsight = { reference ->
                        showExplore = false
                        showDecision = false
                        showPanorama = false
                        showWeeklyReview = false
                        showSettings = false
                        currentTab = AppTab.ADVISOR
                        advisorViewModel.onAction(AdvisorAction.DiscussInsight(reference))
                    },
                    onTalkToAdvisor = discussWithAdvisor,
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
    candidate: UiDesignCandidate,
    allPlans: List<com.mengyalife.app.data.local.plan.SmallPlanEntity>,
    allPlansReady: Boolean,
    progressCounts: Map<Long, Int>,
    weeklyPlanProgress: List<com.mengyalife.app.data.local.plan.PlanProgressEntity>,
    weeklyPlanProgressReady: Boolean,
    planProgressCountsReady: Boolean,
    onRefreshCare: () -> Unit,
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPanorama: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenDecision: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    todayPlans: List<com.mengyalife.app.ui.screens.PlanTodayItem>,
    allGoalCheckins: List<com.mengyalife.app.data.local.goal.GoalCheckinEntity>,
    allGoalCheckinsReady: Boolean,
    onRecordGoalFromToday: (Long) -> Unit,
    onOpenRecentCheckinFromToday: (com.mengyalife.app.data.local.goal.GoalCheckinEntity) -> Unit,
    trajectoryPlans: List<com.mengyalife.app.ui.screens.PlanTrajectoryItem>,
    onOpenPlan: (String) -> Unit,
    careCard: com.mengyalife.app.ui.care.CareCard?,
    onCareEngage: () -> Unit,
    onCareIgnore: () -> Unit,
    onCareSilence: () -> Unit,
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
    onGenerateInsight: () -> Unit,
    onDiscussInsight: (com.mengyalife.app.domain.insight.InsightReference) -> Unit,
    onTalkToAdvisor: (String) -> Unit,
    onWriteDiary: () -> Unit,
    onOpenDiaryEntry: (com.mengyalife.app.data.local.JournalEntryEntity) -> Unit,
) {
    val colors = MorrowTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val container = (LocalContext.current.applicationContext as MengyaApplication).container
    val preparation by DesignPreview.preparation.collectAsStateWithLifecycle()
    val checkinFlow = remember(container) { container.databaseHandle.goalDao().observeAllCheckins() }
    val candidateCheckins by checkinFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var candidateError by remember { mutableStateOf<String?>(null) }
    var lastCandidateSnapshot by remember { mutableStateOf<CandidateSnapshot?>(null) }
    var displayedCandidateReport by remember { mutableStateOf<com.mengyalife.app.domain.insight.InsightReport?>(null) }
    val presentationDay = com.mengyalife.app.ui.components.rememberToday()
    val candidateSnapshot by produceState<CandidateSnapshot?>(
        initialValue = null,
        candidate, goalsState.snapshot, allPlans, progressCounts, candidateCheckins,
        insightState.report, insightState.currentVersion, advisorState.consent, careCard, preparation, presentationDay,
    ) {
        if (candidate == UiDesignCandidate.NONE) { value = null; return@produceState }
        val previous = lastCandidateSnapshot
        value = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                loadCandidateSnapshot(container, goalsState.snapshot, candidateCheckins, allPlans, progressCounts,
                    careCard, displayedCandidateReport ?: insightState.report, previous)
            }
            value = loaded
            lastCandidateSnapshot = loaded
            displayedCandidateReport = loaded.report
            candidateError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            candidateError = "当前观察暂不可用，请重新核对记录"
            android.util.Log.w("UiDesignQA", "presentation snapshot unavailable", error)
        }
    }
    val localContext = remember(goalsState.snapshot, candidateCheckins, allPlans, presentationDay) {
        InformationContext(goalsState.snapshot, candidateCheckins, allPlans, InformationBinding(), now = System.currentTimeMillis())
    }
    val preview = candidateSnapshot ?: CandidateSnapshot(
        localContext, null, emptyList(),
        (if (candidate == UiDesignCandidate.NONE) emptyList() else allPlans.filter { it.status == "ACTIVE" }).sortedByDescending { it.updatedAt }.map {
            InformationResolver.resolve(InformationInput.Plan(it.id, null), localContext)
        }, null,
    )
    var previewPlanId by remember { mutableStateOf<Long?>(null) }
    var previewSubjects by remember { mutableStateOf<List<InformationSubject>?>(null) }
    var previewEvidence by remember { mutableStateOf<InformationPresentation?>(null) }
    val navigatePreview: (InformationNextAction) -> Unit = { action ->
        when (action) {
            is InformationNextAction.OpenGoal -> onOpenGoal(action.goalId)
            is InformationNextAction.OpenPlan -> previewPlanId = action.planId
            is InformationNextAction.OpenSubjects -> previewSubjects = action.subjects
            is InformationNextAction.DiscussInsight -> onDiscussInsight(action.reference)
            is InformationNextAction.OpenCareConversation -> {
                if (careCard?.opportunityId == action.opportunityId && careCard.decisionId == action.decisionId) onCareEngage()
                else onRefreshCare()
            }
            is InformationNextAction.Reevaluate -> if (action.opportunityId != null) onRefreshCare() else onGenerateInsight()
            InformationNextAction.None -> Unit
        }
    }
    val openRecord: (com.mengyalife.app.data.local.goal.GoalCheckinEntity) -> Unit = { record ->
        fun find(nodes: List<com.mengyalife.app.data.repository.GoalNode>): com.mengyalife.app.data.local.goal.GoalEntity? =
            nodes.firstNotNullOfOrNull { node -> if (node.goal.id == record.goalId) node.goal else find(node.children) }
        find(goalsState.snapshot.roots)?.let { onGoalAction(GoalAction.OpenHistory(it)) }
    }

    LaunchedEffect(goalsState.message) {
        val message = goalsState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onGoalAction(GoalAction.ConsumeMessage)
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.canvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = if (imeVisible) {
            WindowInsets.systemBars.exclude(WindowInsets.navigationBars)
        } else {
            WindowInsets.systemBars
        },
        bottomBar = {
            if (!imeVisible) {
                MorrowBottomBar(currentTab = currentTab, onTabChange = onTabChange)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(if (candidate != UiDesignCandidate.NONE) Modifier.testTag(
                    if (candidateSnapshot != null && preparation == null) "candidate_ready" else "candidate_loading",
                ) else Modifier),
        ) {
            when (currentTab) {
                AppTab.TODAY -> if (candidate != UiDesignCandidate.NONE) CandidateTodayScreen(
                    snapshot = preview,
                    listState = todayListState,
                    onAction = navigatePreview,
                    onOpenGoals = onOpenPanorama,
                    onWriteDiary = onWriteDiary,
                    onTalk = { onTalkToAdvisor("想聊聊最近的近况。") },
                    relatedUserContext = candidateUserContext(preview, advisorState),
                    onReevaluate = onGenerateInsight,
                    onOpenEvidence = { previewEvidence = it },
                    onSettings = onOpenSettings,
                    preparation = preparation ?: candidateError,
                ) else TodayProductScreen(
                    listState = todayListState,
                    goals = goalsState.snapshot,
                    goalsReady = !goalsState.loading,
                    plans = allPlans,
                    plansReady = allPlansReady,
                    weeklyPlanProgress = weeklyPlanProgress,
                    weeklyPlanProgressReady = weeklyPlanProgressReady,
                    checkins = allGoalCheckins,
                    checkinsReady = allGoalCheckinsReady,
                    onOpenGoal = onOpenGoal,
                    onOpenGoals = onOpenPanorama,
                    onCreateGoal = onCreateGoalFromToday,
                    onRecordGoal = onRecordGoalFromToday,
                    onOpenRecord = onOpenRecentCheckinFromToday,
                    onTalk = { text -> onTalkToAdvisor(text) },
                )

                AppTab.PATH -> if (candidate != UiDesignCandidate.NONE) CandidateTrajectoryScreen(
                    snapshot = preview,
                    listState = trajectoryListState,
                    onOpenGoal = onOpenGoal,
                    onOpenRecord = openRecord,
                    onOpenGoals = onOpenPanorama,
                    onSettings = onOpenSettings,
                ) else TrajectoryScreen(
                    listState = trajectoryListState,
                    trends = goalsState.trends,
                    goals = goalsState.snapshot,
                    entries = diaryState.entries,
                    weekly = weeklyState,
                    plans = trajectoryPlans,
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
                    candidateSubject = if (candidate != UiDesignCandidate.NONE) candidateConversationSubject(preview, advisorState) else null,
                    onOpenSubjectGoal = onOpenGoal,
                    onOpenPlanById = if (candidate != UiDesignCandidate.NONE) ({ id -> previewPlanId = id }) else null,
                    onOpenPlans = {
                        // Match the trajectory's own “行动状态” anchor, not a generic tab jump.
                        trajectoryListState.requestScrollToItem(3)
                        onTabChange(AppTab.PATH)
                    },
                    onReevaluateInsight = { onTabChange(AppTab.TODAY); onGenerateInsight() },
                    goalTitles = remember(goalsState.snapshot) {
                        buildMap {
                            fun visit(nodes: List<com.mengyalife.app.data.repository.GoalNode>) {
                                nodes.forEach { node -> put(node.goal.id, node.goal.title); visit(node.children) }
                            }
                            visit(goalsState.snapshot.roots)
                        }
                    },
                )
            }
        }
    }
    if (candidate != UiDesignCandidate.NONE) {
        previewPlanId?.let { id ->
            allPlans.firstOrNull { it.id == id }?.let { plan ->
                CandidatePlanDialog(plan, preview, preview.verifiedProgressCounts?.let { it[id] ?: 0 },
                    onDismiss = { previewPlanId = null }, onOpenGoal = onOpenGoal)
            }
        }
        previewSubjects?.let { subjects ->
            CandidateSubjectsDialog(subjects, onDismiss = { previewSubjects = null }, onOpenGoal = onOpenGoal)
        }
        previewEvidence?.let { info ->
            CandidateEvidenceDialog(info, preview, onDismiss = { previewEvidence = null }, onOpenGoal = onOpenGoal)
        }
    }
    RestoreResultHost()
}

private fun findGoalInTree(
    roots: List<com.mengyalife.app.data.repository.GoalNode>,
    id: Long,
): com.mengyalife.app.data.local.goal.GoalEntity? = roots.firstNotNullOfOrNull { node ->
    if (node.goal.id == id) node.goal else findGoalInTree(node.children, id)
}

/**
 * 版本号从**实际安装的包信息**读取，而不是写死一个假版本。
 */
@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "未知"
