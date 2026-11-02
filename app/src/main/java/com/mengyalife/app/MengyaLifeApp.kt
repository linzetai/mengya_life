package com.mengyalife.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.navigation.AppTab
import com.mengyalife.app.ui.components.AppTopBar
import com.mengyalife.app.ui.screens.AdvisorScreen
import com.mengyalife.app.ui.screens.DiaryScreen
import com.mengyalife.app.ui.screens.GoalsScreen
import com.mengyalife.app.ui.screens.OverviewScreen
import com.mengyalife.app.ui.screens.SettingsScreen
import com.mengyalife.app.ui.screens.TrendsScreen
import com.mengyalife.app.ui.theme.MengyaLifeTheme
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * App 根组件：**导航状态、主题状态、页面状态都在这里集中管理**。
 *
 * 结构选择说明：
 * - 设置页是二级页面，用独立的 `showSettings` 状态切换整棵子树，
 *   因此进入设置时底部导航栏自然不存在（不是「盖一层」），
 *   系统返回键由 [BackHandler] 处理为「先关设置」。
 * - 五个一级页面的滚动位置、输入内容、勾选状态全部**上提**到这里，
 *   所以「进设置 → 返回」不会丢失原页面的 UI 状态。
 */
@Composable
fun MengyaLifeApp() {
    // ---- 主题状态（仅内存，本轮不持久化）----
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }

    // ---- 导航状态 ----
    var currentTab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // ---- 各一级页面的 UI 状态（上提，避免切页/进设置后被销毁）----
    val overviewListState = rememberLazyListState()
    val goalsListState = rememberLazyListState()
    val trendsListState = rememberLazyListState()
    val diaryListState = rememberLazyListState()
    val advisorListState = rememberLazyListState()

    var goalDimension by rememberSaveable { mutableStateOf(DemoData.GoalDimension.ALL) }
    val doneShortGoals = remember { mutableStateListOf<String>() }

    var diaryDraft by rememberSaveable { mutableStateOf("") }
    var diaryEditorOpen by rememberSaveable { mutableStateOf(false) }
    var nextDiaryId by remember { mutableLongStateOf(1000L) }
    val diaryEntries = remember {
        mutableStateListOf<DemoData.DiaryEntry>().apply { addAll(DemoData.diaryEntries) }
    }

    var advisorInput by rememberSaveable { mutableStateOf("") }
    var nextMessageId by remember { mutableLongStateOf(1000L) }
    val advisorMessages = remember {
        mutableStateListOf<DemoData.ChatMessage>().apply { addAll(DemoData.advisorMessages) }
    }

    val scope = rememberCoroutineScope()

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
                    onThemeModeChange = { themeMode = it },
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
                    diaryDraft = diaryDraft,
                    onDiaryDraftChange = { diaryDraft = it },
                    diaryEditorOpen = diaryEditorOpen,
                    onToggleDiaryEditor = { diaryEditorOpen = !diaryEditorOpen },
                    diaryEntries = diaryEntries,
                    onSaveDiary = {
                        val text = diaryDraft.trim()
                        if (text.isNotEmpty()) {
                            diaryEntries.add(
                                index = 0,
                                element = DemoData.DiaryEntry(
                                    id = nextDiaryId++,
                                    date = "今天",
                                    title = "今天的记录",
                                    excerpt = text,
                                    tags = listOf("本次运行"),
                                    isNew = true,
                                ),
                            )
                            diaryDraft = ""
                            diaryEditorOpen = false
                            scope.launch {
                                diaryListState.animateScrollToItem(0)
                            }
                        }
                    },
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
                            // 滚动由 AdvisorScreen 内部的 LaunchedEffect 负责
                        }
                    },
                    onUseAdvisorSuggestion = { suggestion ->
                        advisorInput = suggestion
                    },
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
    overviewListState: androidx.compose.foundation.lazy.LazyListState,
    goalsListState: androidx.compose.foundation.lazy.LazyListState,
    trendsListState: androidx.compose.foundation.lazy.LazyListState,
    diaryListState: androidx.compose.foundation.lazy.LazyListState,
    advisorListState: androidx.compose.foundation.lazy.LazyListState,
    goalDimension: DemoData.GoalDimension,
    onGoalDimensionChange: (DemoData.GoalDimension) -> Unit,
    doneShortGoals: List<String>,
    onToggleShortGoal: (String) -> Unit,
    diaryDraft: String,
    onDiaryDraftChange: (String) -> Unit,
    diaryEditorOpen: Boolean,
    onToggleDiaryEditor: () -> Unit,
    diaryEntries: List<DemoData.DiaryEntry>,
    onSaveDiary: () -> Unit,
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
                    draft = diaryDraft,
                    onDraftChange = onDiaryDraftChange,
                    entries = diaryEntries,
                    editorOpen = diaryEditorOpen,
                    onToggleEditor = onToggleDiaryEditor,
                    onSave = onSaveDiary,
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
