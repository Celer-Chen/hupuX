package com.hupux.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.BuildConfig
import com.hupux.data.GITHUB_RELEASES_URL
import com.hupux.data.UpdateChecker
import com.hupux.data.local.CookiePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 检查更新的 UI 状态 */
data class UpdateUiState(
    val checking: Boolean = false,
    /** 检查结果文案；null 表示还没查过 */
    val message: String? = null,
    val hasUpdate: Boolean = false,
    val releaseUrl: String = GITHUB_RELEASES_URL
)

class SettingsViewModel constructor(
    private val cookiePrefs: CookiePreferences,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** 对比 GitHub 最新 release 的 tag 与当前版本 */
    fun checkUpdate() {
        if (_updateState.value.checking) return
        _updateState.value = UpdateUiState(checking = true)
        viewModelScope.launch {
            val r = updateChecker.check(BuildConfig.VERSION_NAME)
            _updateState.value = when {
                r.error != null -> UpdateUiState(message = "检查失败：${r.error}")
                r.hasUpdate     -> UpdateUiState(
                    message = "有新版本 ${r.latest}", hasUpdate = true, releaseUrl = r.releaseUrl)
                else            -> UpdateUiState(message = "已更新至最新版本")
            }
        }
    }

    private val _input = MutableStateFlow(cookiePrefs.manualCookie)
    val input: StateFlow<String> = _input.asStateFlow()

    private val _saved = MutableStateFlow(cookiePrefs.manualCookie.isNotEmpty())
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun updateInput(value: String) {
        _input.value = value
        _saved.value = false
    }

    fun save() {
        cookiePrefs.manualCookie = _input.value
        _saved.value = _input.value.isNotEmpty()
    }

    fun clearAll() {
        _input.value = ""
        cookiePrefs.clearAll()
        _saved.value = false
    }

    val statusText: String get() = when {
        cookiePrefs.manualCookie.isNotEmpty()  -> "已使用手动 Cookie"
        cookiePrefs.webviewCookie.isNotEmpty() -> "已使用 WebView Cookie"
        else                                    -> "未登录"
    }
}
