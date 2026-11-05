package com.mengyalife.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.backup.BackupArtifact
import com.mengyalife.app.data.backup.BackupException
import com.mengyalife.app.data.backup.BackupFormatV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val showPassword: Boolean = false,
    val preparing: Boolean = false,
    val writing: Boolean = false,
    val suggestedFileName: String? = null,
    val counts: Map<String, Int>? = null,
    val message: String? = null,
    val lastResult: String? = null,
) {
    val busy: Boolean get() = preparing || writing
}

/**
 * 完整加密备份 v2 的 UI 状态机。
 *
 * 口令只保存在内存：从确认到 SAF 选择完成之间暂存为 CharArray，随后立即清空。
 * 不写日志、不写 DataStore、不放进 Snackbar/错误信息。
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val exporter = (application as MengyaApplication).container.backupExporter
    private val writer = (application as MengyaApplication).container.backupFileWriter

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var pendingPassphrase: CharArray? = null
    private var pendingArtifact: BackupArtifact? = null

    fun requestBackup() {
        if (_state.value.busy) return
        _state.update { it.copy(showPassword = true, message = null, lastResult = null) }
    }

    fun cancelPassword() {
        clearSecrets()
        _state.update { it.copy(showPassword = false) }
    }

    /** @return 错误文案；null 表示已开始。 */
    fun startBackup(passphrase: String): String? {
        if (passphrase.length < MIN_PASSPHRASE_CHARS) {
            return "备份口令至少 $MIN_PASSPHRASE_CHARS 个字符"
        }
        if (_state.value.busy) return "正在准备备份，请稍候"
        val chars = passphrase.toCharArray()
        clearSecrets()
        pendingPassphrase = chars
        _state.update {
            it.copy(showPassword = false, preparing = true, message = null, lastResult = null, suggestedFileName = null)
        }
        viewModelScope.launch {
            try {
                val artifact = withContext(Dispatchers.IO) { exporter.build(chars) }
                pendingArtifact = artifact
                _state.update {
                    it.copy(
                        preparing = false,
                        suggestedFileName = BackupFormatV2.suggestedFileName(artifact.createdAt),
                        counts = artifact.counts.values,
                    )
                }
            } catch (e: BackupException) {
                clearSecrets()
                _state.update { it.copy(preparing = false, message = userMessage(e)) }
            } catch (e: Exception) {
                clearSecrets()
                _state.update { it.copy(preparing = false, message = "备份准备失败：${e.message ?: "未知错误"}") }
            }
        }
        return null
    }

    /** UI 已用 suggestedFileName 启动 CreateDocument；避免重复启动。 */
    fun onSaveDialogLaunched() {
        _state.update { it.copy(suggestedFileName = null) }
    }

    fun onDestinationChosen(uriString: String) {
        val artifact = pendingArtifact
        val passphrase = pendingPassphrase
        if (artifact == null || passphrase == null) {
            _state.update { it.copy(message = "备份已过期，请重新开始") }
            return
        }
        if (_state.value.writing) return
        _state.update { it.copy(writing = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                writer.commit(uriString, artifact, passphrase)
            }
            clearSecrets()
            val success = "完整备份已写入并校验：${artifact.counts.total()} 条记录（${artifact.sizeBytes} 字节）"
            val text = when {
                result.ok && result.tempCleanupFailed ->
                    "$success；但临时文件清理失败（内容已加密），请稍后在系统存储中清理"
                result.ok -> success
                else -> "备份失败：${result.message ?: result.errorCode ?: "未知错误"}"
            }
            _state.update {
                it.copy(writing = false, message = text, lastResult = text, counts = null)
            }
        }
    }

    fun onDestinationCancelled() {
        clearSecrets()
        _state.update {
            it.copy(writing = false, suggestedFileName = null, counts = null,
                message = "已取消备份，没有写入任何文件", lastResult = "已取消备份，没有写入任何文件")
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        clearSecrets()
        super.onCleared()
    }

    private fun clearSecrets() {
        pendingPassphrase?.fill('\u0000')
        pendingPassphrase = null
        pendingArtifact = null
    }

    private fun userMessage(e: BackupException): String = when (e.code) {
        "SETTINGS_CHANGED" -> "导出期间设置发生变化，请重试"
        "PAYLOAD_INVALID", "PAYLOAD_HASH" -> "备份数据校验失败，未写入文件"
        "PAYLOAD_TOO_LARGE", "FILE_TOO_LARGE" -> "数据超过备份大小限制，无法导出"
        "PASSPHRASE_EMPTY", "KDF_PARAMS" -> e.message ?: "备份口令或加密参数非法"
        else -> e.message ?: "备份失败（${e.code}）"
    }

    companion object {
        const val MIN_PASSPHRASE_CHARS = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                BackupViewModel(app)
            }
        }
    }
}
