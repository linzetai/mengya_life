package com.mengyalife.app.ui.candidates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.presentation.InformationSubject
import com.mengyalife.app.ui.presentation.InformationSubjectType
import com.mengyalife.app.ui.theme.isDarkTheme

/** Display-only nonbreaking joiner keeps a number next to its Chinese unit. */
private fun joinNumericUnitBreaks(text: String): String = Regex(
    "(\\d+(?:\\.\\d+)?)(分钟|小时|次|页|本|公里|米|公斤|千克|%)",
).replace(text) { match -> "${match.groupValues[1]}\u2060${match.groupValues[2]}" }

/** Keep only a complete, verbatim explicit preference phrase; never paraphrase the user's words. */
fun explicitPreferenceExcerpt(text: String): String? {
    val cues = listOf("我想先", "我希望", "我想保持", "希望先", "希望保持")
    val start = cues.map { cue -> text.lastIndexOf(cue).takeIf { it >= 0 } }.filterNotNull().maxOrNull() ?: return null
    val stop = text.indexOfAny(charArrayOf('。', '！', '？', '!', '?', '\n'), startIndex = start)
        .takeIf { it >= 0 } ?: text.length
    if (stop - start > 64) return null
    return text.substring(start, (stop + if (stop < text.length) 1 else 0).coerceAtMost(text.length))
}

/** Match the current conversation's real source/context IDs; never label it using today's unrelated plan. */
fun candidateConversationSubject(snapshot: CandidateSnapshot, state: AdvisorUiState): InformationSubject? {
    val message = state.messages.lastOrNull { it.role == AdvisorRole.ASSISTANT } ?: return null
    val ids = message.sources.mapNotNull { source -> when (source.type) {
        AdvisorSourceType.GOAL -> source.recordId.toLongOrNull()
        AdvisorSourceType.PROGRESS -> snapshot.context.checkins.firstOrNull { it.id.toString() == source.recordId }?.goalId
        else -> null
    } }.toSet()
    if (ids.isEmpty()) return null
    fun identity(subject: InformationSubject): Set<Long> = when {
        subject.details.isNotEmpty() -> subject.details.flatMap { identity(it) }.toSet()
        subject.type == InformationSubjectType.GOAL -> setOfNotNull(subject.stableId)
        subject.linkedGoal != null -> setOf(subject.linkedGoal.id)
        else -> emptySet()
    }
    return snapshot.observations.firstOrNull { identity(it.subject) == ids }?.subject
}

/** Only a user message explicitly scoped to the same stable goal may explain a preference. */
fun candidateUserContext(snapshot: CandidateSnapshot, state: AdvisorUiState): String? {
    fun goalIds(subject: InformationSubject): Set<Long> = when {
        subject.details.isNotEmpty() -> subject.details.flatMap { goalIds(it) }.toSet()
        subject.type == InformationSubjectType.GOAL -> setOfNotNull(subject.stableId)
        subject.linkedGoal != null -> setOf(subject.linkedGoal.id)
        else -> emptySet()
    }
    val plan = snapshot.plans.firstOrNull()
    val subject = plan?.subject ?: snapshot.observations.firstOrNull()?.subject ?: return null
    val ids = goalIds(subject)
    if (ids.isEmpty()) return null
    return state.messages.asReversed().firstNotNullOfOrNull { message ->
        if (message.role != AdvisorRole.USER || message.status != AdvisorMessageStatus.SENT) return@firstNotNullOfOrNull null
        val sameObject = message.contextGoalId in ids || message.sources.any { source -> when (source.type) {
            AdvisorSourceType.GOAL -> source.recordId.toLongOrNull() in ids
            AdvisorSourceType.PROGRESS -> snapshot.context.checkins.firstOrNull { it.id.toString() == source.recordId }?.goalId in ids
            else -> false
        } }
        if (sameObject) explicitPreferenceExcerpt(message.content) else null
    }
}

@Composable
fun CandidateConversationHeader(subject: InformationSubject?, onOpenGoal: (Long) -> Unit, onSettings: () -> Unit, keyboardOpen: Boolean) {
    val p = LocalProductColors.current
    val companion = LocalUiDesignCandidate.current == UiDesignCandidate.B_COMPANION
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("candidate_advisor_${LocalUiDesignCandidate.current.name.lowercase()}_${if (isDarkTheme()) "dark" else "light"}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CandidatePageHeader(if (companion) "对话" else "顾问", onSettings)
        if (subject != null) {
            val goalId = subject.linkedGoal?.id ?: subject.stableId.takeIf { subject.type == InformationSubjectType.GOAL }
            Column(
                Modifier.fillMaxWidth()
                    .then(if (goalId != null) Modifier.clickable { onOpenGoal(goalId) } else Modifier)
                    .defaultMinSize(minHeight = 48.dp)
                    .background(if (companion) p.nested else p.background, RoundedCornerShape(10.dp))
                    .padding(horizontal = if (companion) 12.dp else 0.dp, vertical = 8.dp),
            ) {
                Text((if (companion) "正在聊 · " else "关于 · ") + subject.displayName, style = MaterialTheme.typography.titleSmall, color = p.text)
                if (!keyboardOpen) subject.parentGoal?.currentName?.let {
                    Text("上级目标：$it", style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
            }
        } else if (!keyboardOpen) {
            Text("近况对话", style = MaterialTheme.typography.bodyMedium, color = p.secondary)
        }
    }
    if (!companion) HorizontalDivider(color = p.border)
}

@Composable
fun CandidateMessageBody(message: AdvisorMessage) {
    val p = LocalProductColors.current
    val user = message.role == AdvisorRole.USER
    val companion = LocalUiDesignCandidate.current == UiDesignCandidate.B_COMPANION
    val content = joinNumericUnitBreaks(message.content.removePrefix("[QA 设计样例]").trimStart())
    val tag = if (user) "advisor_user_message" else "advisor_assistant_message"
    if (!companion && user) {
        Surface(
            modifier = Modifier.widthIn(max = 312.dp).testTag(tag),
            shape = RoundedCornerShape(16.dp),
            color = p.accentSurface,
        ) {
            Text(content, style = MaterialTheme.typography.bodyLarge, color = p.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    } else {
        Column(
            Modifier.fillMaxWidth().testTag(tag)
                .then(if (companion && user) Modifier.background(p.nested, RoundedCornerShape(10.dp)).padding(12.dp) else Modifier.padding(vertical = 4.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (user) "你" else "MengYa", style = MaterialTheme.typography.labelMedium, color = p.secondary)
            Text(content, style = MaterialTheme.typography.bodyLarge, color = p.text)
            if (companion && !user) HorizontalDivider(Modifier.padding(top = 8.dp), color = p.border)
        }
    }
}
