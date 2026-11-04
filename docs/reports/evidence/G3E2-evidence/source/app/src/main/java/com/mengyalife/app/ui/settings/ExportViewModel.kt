package com.mengyalife.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.export.ExportJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「导出我的数据」的界面状态。
 *
 * 流程：
 * 1. [requestExport] 在数据库事务里取一致快照（此时还没碰文件系统）；
 * 2. 界面展示敏感信息提醒与条数，用户点「选择保存位置」；
 * 3. [confirmExport] 返回建议文件名，界面用 SAF（CreateDocument）让用户选位置；
 * 4. [onDestinationChosen] 写入 + **回读校验**，失败绝不谎报成功；
 * 5. 全程只写用户选中的那个文件：不上传、不自动分享、不需要存储权限。
 */
data class ExportUiState(
    val preparing: Boolean = false,
    val bundle: ExportJson.Bundle? = null,
    val awaitingDestination: Boolean = false,
    val exporting: Boolean = false,
    val message: String? = null,
) {
    /** 是否应该弹出「文件可能包含敏感信息」的确认框。 */
    val showWarning: Boolean
        get() = bundle != null && !awaitingDestination && !exporting
}

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MengyaApplication).container.dataExportRepository
    private val resolver = application.contentResolver

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /** 读取一致快照，准备弹确认框。 */
    fun requestExport() {
        val current = _state.value
        if (current.preparing || current.exporting || current.bundle != null) return

        _state.update { it.copy(preparing = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.buildBundle() }
                .onSuccess { bundle ->
                    _state.update {
                        it.copy(preparing = false, bundle = bundle, awaitingDestination = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            preparing = false,
                            bundle = null,
                            message = "读取数据失败：${error.message ?: "未知错误"}",
                        )
                    }
                }
        }
    }

    /** 用户确认「继续导出」：返回建议文件名，由界面启动系统保存对话框。 */
    fun confirmExport(): String? {
        val bundle = _state.value.bundle ?: return null
        _state.update { it.copy(awaitingDestination = true) }
        return ExportJson.suggestedFileName(bundle.exportedAt)
    }

    /** 用户取消导出提醒框。 */
    fun cancelExport() {
        _state.update { it.copy(bundle = null, awaitingDestination = false) }
    }

    /** 用户在系统文件选择器里取消了保存。 */
    fun onDestinationCancelled() {
        _state.update {
            it.copy(
                bundle = null,
                awaitingDestination = false,
                exporting = false,
                message = "已取消导出，没有写入任何文件",
            )
        }
    }

    /** 用户选定保存位置：写文件 → 回读 → 与快照逐项比对。 */
    fun onDestinationChosen(uri: Uri) {
        val bundle = _state.value.bundle ?: return
        if (_state.value.exporting) return

        _state.update { it.copy(exporting = true, awaitingDestination = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.encode(bundle)
                    writeText(uri, json)
                    val readBack = readText(uri)
                    val verification = repository.verify(readBack, bundle)
                    json.length to verification
                }
            }

            result.onSuccess { (length, verification) ->
                if (verification.ok) {
                    val counts = bundle.counts
                    _state.update {
                        it.copy(
                            bundle = null,
                            awaitingDestination = false,
                            exporting = false,
                            message = "导出完成并已校验：日记 ${counts.journalEntries} 条 · " +
                                "目标 ${counts.goals} 个 · 进展 ${counts.goalCheckins} 条" +
                                "（${length} 字符）",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            bundle = null,
                            awaitingDestination = false,
                            exporting = false,
                            message = "导出文件校验失败：${verification.problems.firstOrNull() ?: "结构不完整"}",
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        bundle = null,
                        awaitingDestination = false,
                        exporting = false,
                        message = "导出失败：${error.message ?: "无法写入所选位置"}",
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun writeText(uri: Uri, text: String) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: error("无法打开所选位置写入")
        stream.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    private fun readText(uri: Uri): String {
        val stream = resolver.openInputStream(uri)
            ?: error("无法回读刚写入的文件")
        return stream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                ExportViewModel(app)
            }
        }
    }
}
