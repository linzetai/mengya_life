package com.mengyalife.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreFileInfo
import com.mengyalife.app.data.restore.RestorePreview
import com.mengyalife.app.data.restore.RestoreRunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RestoreUiState(
    val fileName: String? = null,
    val fileSize: Long = 0,
    val showPassword: Boolean = false,
    val busy: Boolean = false,
    val preview: RestorePreview? = null,
    val error: String? = null,
    val executing: Boolean = false,
    val successMessage: String? = null,
)

/**
 * 恢复流程状态：选择文件 → 口令校验 → 预览 → 执行。
 *
 * 没有 execute/commit 方法；不持有 DAO/DataStore 写依赖。
 * 口令只在 confirm 调用栈和 CharArray 中存在，I/O 完成后清空。
 */
class RestoreViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MengyaApplication).container
    private val fileSource = container.restoreFileSource
    private val reader = container.restoreReader
    private val validator = container.restoreValidator
    private val planner = container.restorePlanner

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    private var selectedUri: String? = null
    private var passphrase: CharArray? = null
    private var backupBytes: ByteArray? = null

    fun onFileChosen(uri: String) {
        if (_state.value.busy) return
        clearSecrets()
        selectedUri = uri
        _state.update { it.copy(busy = true, error = null, preview = null, fileName = null) }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { fileSource.inspect(uri) }
                _state.update {
                    it.copy(
                        busy = false,
                        fileName = info.name,
                        fileSize = info.sizeBytes,
                        showPassword = true,
                    )
                }
            } catch (e: RestoreException) {
                _state.update { it.copy(busy = false, error = userMessage(e)) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "无法读取备份文件") }
            }
        }
    }

    fun cancelPassword() {
        clearSecrets()
        _state.update { it.copy(showPassword = false, fileName = null, fileSize = 0, error = null) }
    }

    /** @return 错误文案；null 表示已开始。 */
    fun confirmPassword(password: String): String? {
        if (password.isBlank()) return "请输入备份口令"
        val uri = selectedUri ?: return "请先选择备份文件"
        if (_state.value.busy) return "正在校验，请稍候"
        val chars = password.toCharArray()
        clearSecrets()
        passphrase = chars
        _state.update { it.copy(showPassword = false, busy = true, error = null, preview = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val info = fileSource.inspect(uri)
                    val bytes = fileSource.read(uri)
                    reader.validateFileSize(bytes.size.toLong())
                    val opened = reader.open(bytes, chars)
                    val validation = validator.validate(opened.payload)
                    backupBytes = bytes
                    planner.preview(opened, validation, RestoreFileInfo(info.name, bytes.size.toLong()))
                }
                _state.update { it.copy(busy = false, preview = result, fileName = result.let { p -> state.value.fileName }) }
            } catch (e: RestoreException) {
                clearSecrets()
                _state.update { it.copy(busy = false, error = userMessage(e)) }
            } catch (e: Exception) {
                clearSecrets()
                _state.update { it.copy(busy = false, error = "备份校验失败：${e.message ?: "未知错误"}") }
            }
        }
        return null
    }

    fun dismissPreview() {
        clearSecrets()
        selectedUri = null
        _state.update { it.copy(preview = null, fileName = null, fileSize = 0, error = null, successMessage = null) }
    }

    /** 用户已勾选确认后由 UI 调用；没有第二套隐藏入口。 */
    fun executeConfirmed() {
        val preview = _state.value.preview ?: return
        val bytes = backupBytes ?: return
        val pass = passphrase ?: return
        if (!preview.canExecute || _state.value.executing) return
        _state.update { it.copy(executing = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.restoreCoordinator.execute(bytes, pass, preview.payloadSha256, preview.backupId)
            }
            clearSecrets()
            _state.update {
                if (result.status == RestoreRunStatus.SUCCESS) {
                    RestoreResultBus.success(result.message)
                    it.copy(executing = false, preview = null, successMessage = result.message)
                } else {
                    it.copy(executing = false, error = result.message)
                }
            }
        }
    }

    fun consumeError() { _state.update { it.copy(error = null) } }

    fun consumeSuccess() { _state.update { it.copy(successMessage = null) } }

    override fun onCleared() {
        clearSecrets()
        super.onCleared()
    }

    private fun clearSecrets() {
        passphrase?.fill('\u0000')
        passphrase = null
        backupBytes = null
    }

    private fun userMessage(e: RestoreException): String = when (e.code) {
        "V1_UNSUPPORTED" -> "这是旧版 v1 导出：只能识别，不支持恢复"
        "DECRYPT_FAILED" -> "口令错误或备份文件已损坏"
        "FILE_TOO_LARGE" -> "备份文件超过 64 MiB，已拒绝"
        "SOURCE_PACKAGE", "SOURCE_QA" -> "备份来源与此应用/构建不匹配，已拒绝"
        "FUTURE_SCHEMA" -> "备份数据库版本高于当前应用，请先升级应用"
        "PAYLOAD_HASH", "PAYLOAD_INVALID" -> "备份完整性/结构校验失败"
        else -> e.message ?: "备份校验失败（${e.code}）"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                RestoreViewModel(app)
            }
        }
    }
}
