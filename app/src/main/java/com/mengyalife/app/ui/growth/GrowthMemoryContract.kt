package com.mengyalife.app.ui.growth

import com.mengyalife.app.data.growth.MemoryDetail
import com.mengyalife.app.domain.growth.GrowthMemory

data class GrowthMemoryUiState(
    val loading: Boolean = true,
    val memories: List<GrowthMemory> = emptyList(),
    val selected: MemoryDetail? = null,
    val error: String? = null,
)

sealed interface GrowthMemoryAction {
    data object Refresh : GrowthMemoryAction
    data class Open(val id: String) : GrowthMemoryAction
    data object CloseDetail : GrowthMemoryAction
    data class Correct(val id: String, val content: String) : GrowthMemoryAction
    data class Delete(val id: String) : GrowthMemoryAction
    data object ConsumeError : GrowthMemoryAction
}
