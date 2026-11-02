package com.mengyalife.app

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.components.AppTopBar
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.DiaryUiState
import com.mengyalife.app.ui.journal.JournalViewModel
import com.mengyalife.app.ui.screens.AdvisorScreen
import com.mengyalife.app.ui.screens.DiaryScreen
import com.mengyalife.app.ui.screens.GoalsScreen
import com.mengyalife.app.ui.screens.OverviewScreen
import com.mengyalife.app.ui.screens.SettingsScreen
import com.mengyalife.app.ui.screens.TrendsScreen
import com.mengyalife.app.ui.settings.ThemeViewModel
import com.mengyalife.app.ui.theme.MengyaLifeTheme
import com.mengyalife.app.ui.theme.ThemeMode

/**
 * App 根组件：只负责**装配**。
 *
 * Gate 2A 起的分工：
 * - 导航状态（当前一级页 / 是否在设置页）留在这里；
 * - 日记的业务状态与动作交给 [JournalViewModel]；
 * - 主题偏好的读写交给 [ThemeViewModel]（DataStore）；
 * - 各页面纯渲染 —— 这里不再出现任何数据库或时间戳逻辑。
 */
@Composable
fun MengyaLifeApp() {
    // ---- 主题：来自 DataStore，读盘期间先按「跟随系统」渲染 ----
    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
    val persistedThemeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val themeMode: ThemeMode = persistedThemeMode ?: ThemeMode.SYSTEM

    // ---- 日记：来自 Room，状态由 ViewModel 持有 ----
    val journalViewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)
    val diaryState by journalViewModel.uiState.collectAsStateWithLifecycle()

    // ---- 导航状态 ----
    var currentTab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // ---- 页面滚动位置（上提，进设置返回不丢）----
    val overviewListState = rememberLazyListState()
    val goalsListState = rememberLazyListState()
    val trendsListState = rememberLazyListState()
    val diaryListState = rememberLazyListState()
    val advisorListState = rememberLazyListState()

    // ---- 仍是纯演示、尚未入库的页面状态 ----
    var goalDimension by rememberSaveable { mutableStateOf(DemoData.GoalDimension.ALL) }
    val doneShortGoals = remember { mutableStateListOf<String>() }

    var advisorInput by rememberSaveable { mutableStateOf("") }
    var nextMessageId by remember { mutableLongStateOf(1000L) }
    val advisorMessages = remember {
        mutableStateListOf<DemoData.ChatMessage>().apply { addAll(DemoData.advisorMessages) }
    }

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val versionName = remember(context) { readVersionName(context) }

    MengyaLifeTheme(darkTheme = darkTheme) {
        // 系统返回键：在设置页时先关闭设置，回到原来的一级页面。
        BackHandler(enabled = showSettings) { showSettings = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            if (showSettings) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeViewModel.setThemeMode(it) },
                    onBack = { showSettings = false },
                    versionName = versionName,
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
                    goalDimension = goalDimension,
                    onGoalDimensionChange = { goalDimension = it },
                    doneShortGoals = doneShortGoals,
                    onToggleShortGoal = { text ->
                        if (text in doneShortGoals) doneShortGoals.remove(text) else doneShortGoals.add(text)
                    },
                    diaryState = diaryState,
                    onDiaryAction = journalViewModel::onAction,
                    advisorInput = advisorInput,
                    onAdvisorInputChange = { advisorInput = it },
                    advisorMessages = advisorMessages,
                    onSendAdvisorMessage = {
                        val text = advisorInput.trim()
                        if (text.isNotEmpty()) {
                            advisorMessages.add(
                                DemoData.ChatMessage(
                                    id = nextMessageId++,
                                    fromUser = true,
                                    text = text,
                                ),
                            )
                            advisorMessages.add(
                                DemoData.ChatMessage(
                                    id = nextMessageId++,
                                    fromUser = false,
                                    text = "（演示）已收到。当前版本没有接入任何模型服务，" +
                                        "这条提示只用于说明发送链路可用，不代表真实 AI 回答。",
                                    system = true,
                                ),
                            )
                            advisorInput = ""
                        }
                    },
                    onUseAdvisorSuggestion = { suggestion -> advisorInput = suggestion },
                )
            }
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
    goalDimension: DemoData.GoalDimension,
    onGoalDimensionChange: (DemoData.GoalDimension) -> Unit,
    doneShortGoals: List<String>,
    onToggleShortGoal: (String) -> Unit,
    diaryState: DiaryUiState,
    onDiaryAction: (DiaryAction) -> Unit,
    advisorInput: String,
    onAdvisorInputChange: (String) -> Unit,
    advisorMessages: List<DemoData.ChatMessage>,
    onSendAdvisorMessage: () -> Unit,
    onUseAdvisorSuggestion: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 五个一级页面共用同一个顶栏：右侧统一是齿轮设置按钮。
            AppTopBar(
                title = currentTab.label,
                onSettingsClick = onOpenSettings,
            )
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
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
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
                    onOpenAdvisor = { onTabChange(AppTab.ADVISOR) },
                )

                AppTab.GOALS -> GoalsScreen(
                    listState = goalsListState,
                    selectedDimension = goalDimension,
                    onDimensionChange = onGoalDimensionChange,
                    doneShortGoals = doneShortGoals.toSet(),
                    onToggleShortGoal = onToggleShortGoal,
                )

                AppTab.TRENDS -> TrendsScreen(listState = trendsListState)

                AppTab.DIARY -> DiaryScreen(
                    listState = diaryListState,
                    state = diaryState,
                    onAction = onDiaryAction,
                )

                AppTab.ADVISOR -> AdvisorScreen(
                    listState = advisorListState,
                    input = advisorInput,
                    onInputChange = onAdvisorInputChange,
                    messages = advisorMessages,
                    onSend = onSendAdvisorMessage,
                    onUseSuggestion = onUseAdvisorSuggestion,
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
