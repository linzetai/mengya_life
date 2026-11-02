package com.mengyalife.app.data

/**
 * 演示数据集中管理。
 *
 * 本轮不接数据库、不接网络，所有内容都是**写死的演示数据**，
 * 界面上必须通过 [NOTICE] 之类的标记明确告诉用户「这不是真实记录」。
 *
 * 设计取向：围绕「人生方向 / 长期目标 / 成长趋势」这类长期议题，
 * 刻意不把短期消费（例如换手机、买装备）设计成主线。
 */
object DemoData {

    /** 所有页面统一的演示数据声明。 */
    const val NOTICE = "演示数据 · 非真实记录"

    /** 设置页中关于数据来源的统一说明。 */
    const val DATA_SCOPE_NOTE =
        "当前版本不读取、不上传任何真实数据；页面内容均为内置演示数据。"

    // -----------------------------------------------------------------------
    // 总览
    // -----------------------------------------------------------------------

    val lifeDirectionTitle = "把健康、专业深度和关系，都做成可以长期复利的事"

    val lifeDirectionStatement =
        "未来十年，我希望在身体状态稳定的前提下，把工程能力沉淀成可复用的方法论，" +
            "同时留出稳定的时间给家人和自己的长期学习。比起短期的成绩，我更在意" +
            "十年后这些事情还在持续变好。"

    val lifePillars = listOf("健康基线", "专业深度", "长期学习", "关系与陪伴")

    data class Insight(val title: String, val body: String)

    val insights = listOf(
        Insight(
            title = "健康维度在稳步回升",
            body = "最近 8 周「健康」维度的完成度从 48 上升到 72，主要来自规律运动和睡眠记录的稳定。",
        ),
        Insight(
            title = "职业阶段目标节奏偏紧",
            body = "「形成可迁移的方法」阶段的 2 项短期目标已连续两周滞后，建议下调本周的量化要求。",
        ),
        Insight(
            title = "日记里反复出现的关键词",
            body = "近 30 天的高频词是「节奏」「睡眠」「复盘」，可以作为下一阶段目标的输入。",
        ),
    )

    // -----------------------------------------------------------------------
    // 目标
    // -----------------------------------------------------------------------

    enum class GoalDimension(val label: String, val testTag: String) {
        ALL("全部", "goal_filter_all"),
        HEALTH("健康", "goal_filter_health"),
        CAREER("职业", "goal_filter_career"),
        WEALTH("财富", "goal_filter_wealth"),
        LIFE("生活", "goal_filter_life"),
    }

    data class StageGoal(
        val title: String,
        val progress: Float,
        val shortGoals: List<String>,
    )

    data class LongGoal(
        val dimension: GoalDimension,
        val title: String,
        val horizon: String,
        val summary: String,
        val progress: Float,
        val stages: List<StageGoal>,
    )

    val goals = listOf(
        LongGoal(
            dimension = GoalDimension.HEALTH,
            title = "把身体状态维持在可持续的基准线上",
            horizon = "长期 · 10 年",
            summary = "不追求短期极限，而是让运动、睡眠、饮食形成可以持续十年的默认习惯。",
            progress = 0.45f,
            stages = listOf(
                StageGoal(
                    title = "阶段：建立稳定的运动习惯",
                    progress = 0.70f,
                    shortGoals = listOf("每周完成三次有氧或力量训练", "每周一次较长距离的步行"),
                ),
                StageGoal(
                    title = "阶段：睡眠与精力基线",
                    progress = 0.40f,
                    shortGoals = listOf("固定入睡时间段", "每日记录一次精力状态"),
                ),
            ),
        ),
        LongGoal(
            dimension = GoalDimension.CAREER,
            title = "把工程能力沉淀为可复用的方法论",
            horizon = "长期 · 8 年",
            summary = "从「能完成需求」走向「能定义问题、沉淀方法、带人一起做对」。",
            progress = 0.35f,
            stages = listOf(
                StageGoal(
                    title = "阶段：深耕一个技术方向",
                    progress = 0.60f,
                    shortGoals = listOf("每季度输出一篇技术复盘", "完成一次系统性学习计划"),
                ),
                StageGoal(
                    title = "阶段：形成可迁移的方法",
                    progress = 0.30f,
                    shortGoals = listOf("整理个人知识库索引", "把一次项目经验写成可复用手册"),
                ),
            ),
        ),
        LongGoal(
            dimension = GoalDimension.WEALTH,
            title = "建立与人生阶段匹配的财务安全垫",
            horizon = "长期 · 10 年",
            summary = "目标是抗风险能力，而不是收益竞赛；先保证底线，再谈配置。",
            progress = 0.50f,
            stages = listOf(
                StageGoal(
                    title = "阶段：应急储备",
                    progress = 0.80f,
                    shortGoals = listOf("储备覆盖六个月必要支出"),
                ),
                StageGoal(
                    title = "阶段：长期配置计划",
                    progress = 0.30f,
                    shortGoals = listOf("制定年度储蓄与配置比例", "每半年复盘一次风险敞口"),
                ),
            ),
        ),
        LongGoal(
            dimension = GoalDimension.LIFE,
            title = "保持与家人和朋友的稳定连接",
            horizon = "长期 · 持续",
            summary = "把陪伴写进日程，而不是等有空的时候再说。",
            progress = 0.55f,
            stages = listOf(
                StageGoal(
                    title = "阶段：固定的陪伴时间",
                    progress = 0.60f,
                    shortGoals = listOf("每周一次不被打扰的家庭时间"),
                ),
                StageGoal(
                    title = "阶段：持续学习与兴趣",
                    progress = 0.40f,
                    shortGoals = listOf("每月读完一本非技术书"),
                ),
            ),
        ),
    )

    // -----------------------------------------------------------------------
    // 趋势
    // -----------------------------------------------------------------------

    data class TrendSeries(val name: String, val values: List<Float>)

    val trendWeeks = listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W10", "W11", "W12")

    val trendSeries = listOf(
        TrendSeries("健康", listOf(48f, 52f, 50f, 58f, 61f, 59f, 64f, 68f, 66f, 70f, 71f, 72f)),
        TrendSeries("专业", listOf(55f, 57f, 58f, 56f, 60f, 62f, 58f, 57f, 61f, 59f, 58f, 58f)),
        TrendSeries("关系", listOf(60f, 58f, 62f, 63f, 61f, 58f, 60f, 62f, 61f, 60f, 62f, 61f)),
    )

    data class DimensionScore(val label: String, val score: Int, val delta: Int)

    val dimensionScores = listOf(
        DimensionScore("健康", 72, +8),
        DimensionScore("专业", 58, -2),
        DimensionScore("财务", 64, +3),
        DimensionScore("关系", 61, +1),
        DimensionScore("学习", 49, -4),
    )

    val trendSummary = listOf(
        "近 12 周，健康维度是唯一持续上升的维度，与运动习惯的建立时间点吻合。",
        "专业维度在第 5–8 周出现过一次下滑，对应项目集中交付期，属于可解释的波动。",
        "学习维度是当前最弱项（49），且近一个月下降 4，建议在下个阶段目标里单独设一条。",
        "关系维度基本持平，没有明显恶化，但也没有主动投入的痕迹。",
    )

    // -----------------------------------------------------------------------
    // 日记
    // -----------------------------------------------------------------------

    data class DiaryEntry(
        val id: Long,
        val date: String,
        val title: String,
        val excerpt: String,
        val tags: List<String>,
        val isNew: Boolean = false,
    )

    val diaryEntries = listOf(
        DiaryEntry(
            id = 1L,
            date = "10 月 31 日",
            title = "把节奏调慢了一档",
            excerpt = "这周主动推掉了一个并不重要的会，晚上多睡了一小时。第二天反而把最难的那段工作推进了。",
            tags = listOf("节奏", "睡眠"),
        ),
        DiaryEntry(
            id = 2L,
            date = "10 月 28 日",
            title = "复盘：为什么这段时间效率不稳",
            excerpt = "把最近两周的作息排了一遍，问题不在能力，而在于每天开始工作的时间不固定。先从固定起床时间开始。",
            tags = listOf("复盘", "习惯"),
        ),
        DiaryEntry(
            id = 3L,
            date = "10 月 24 日",
            title = "和家人吃饭的一晚",
            excerpt = "很久没有不看手机地吃完一顿饭。聊的都是小事，但感觉比拿下一个项目更踏实。",
            tags = listOf("关系", "陪伴"),
        ),
    )

    /** 「未来 AI 提取信息」区域的示例内容（明确标注为示例，不是真实结果）。 */
    val diaryAiExtractExample = listOf(
        "情绪倾向：平静、略疲惫",
        "提到的事项：睡眠、会议、家庭晚餐",
        "可提取的目标线索：固定起床时间",
    )

    // -----------------------------------------------------------------------
    // 顾问
    // -----------------------------------------------------------------------

    data class ChatMessage(
        val id: Long,
        val fromUser: Boolean,
        val text: String,
        val system: Boolean = false,
    )

    val advisorMessages = listOf(
        ChatMessage(
            id = 1L,
            fromUser = false,
            text = "这条消息用于展示对话样式，不代表真实 AI 输出。后续版本接入模型后，这里会是你和顾问的对话记录。",
            system = true,
        ),
        ChatMessage(
            id = 2L,
            fromUser = true,
            text = "最近职业维度的阶段目标有点推不动，我该怎么拆？",
        ),
        ChatMessage(
            id = 3L,
            fromUser = false,
            text = "（演示回复）真实回答需要接入模型。当前页面只验证输入、发送与消息列表的交互是否可用。",
            system = true,
        ),
    )

    val advisorSuggestions = listOf(
        "帮我把「形成可迁移的方法」拆成两周内可做的动作",
        "健康维度的上升，主要来自哪些习惯？",
        "这周我最该放弃的一件事是什么？",
    )
}
