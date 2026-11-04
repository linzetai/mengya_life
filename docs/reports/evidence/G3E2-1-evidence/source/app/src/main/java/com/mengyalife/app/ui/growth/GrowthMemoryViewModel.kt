package com.mengyalife.app.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.growth.GrowthMemoryRepository
import com.mengyalife.app.data.growth.MemoryDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GrowthMemoryViewModel(
    private val repository: GrowthMemoryRepository,
) : ViewModel() {

    private data class Local(
        val selected: MemoryDetail? = null,
        val error: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<GrowthMemoryUiState> = combine(
        repository.observeAll(),
        local,
    ) { memories, localState ->
        GrowthMemoryUiState(
            loading = false,
            memories = memories,
            selected = localState.selected,
            error = localState.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GrowthMemoryUiState(),
    )

    fun onAction(action: GrowthMemoryAction) {
        when (action) {
            GrowthMemoryAction.Refresh -> viewModelScope.launch {
                local.value.selected?.let { detail ->
                    local.update { it.copy(selected = repository.loadDetail(detail.memory.id)) }
                }
            }
            is GrowthMemoryAction.Open -> viewModelScope.launch {
                val detail = repository.loadDetail(action.id)
                local.update { it.copy(selected = detail, error = if (detail == null) "记忆不存在" else null) }
            }
            GrowthMemoryAction.CloseDetail -> local.update { it.copy(selected = null) }
            is GrowthMemoryAction.Correct -> viewModelScope.launch {
                val ok = repository.correct(action.id, action.content)
                if (!ok) {
                    local.update { it.copy(error = "纠正失败：内容为空或记忆不可编辑") }
                } else {
                    local.update { it.copy(selected = repository.loadDetail(action.id)) }
                }
            }
            is GrowthMemoryAction.Delete -> viewModelScope.launch {
                val ok = repository.delete(action.id)
                local.update {
                    if (ok) it.copy(selected = null) else it.copy(error = "删除失败：记忆不可删除")
                }
            }
            GrowthMemoryAction.ConsumeError -> local.update { it.copy(error = null) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                GrowthMemoryViewModel(app.container.growthMemoryRepository)
            }
        }
    }
}
