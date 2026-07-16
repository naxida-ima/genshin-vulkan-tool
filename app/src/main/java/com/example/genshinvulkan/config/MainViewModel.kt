package com.example.genshinvulkan.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.genshinvulkan.permission.PermissionHelper
import com.example.genshinvulkan.permission.PermissionType
import com.example.genshinvulkan.permission.ShellResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    // 权限
    val permissionType: PermissionType = PermissionType.NONE,
    val shizukuAvailable: Boolean = false,
    val rootAvailable: Boolean = false,

    // 原神信息
    val genshinInfo: GenshinInfo? = null,
    val genshinDetected: Boolean = false,
    val detectingGenshin: Boolean = true,

    // Vulkan 状态
    val vulkanStatus: VulkanStatus = VulkanStatus(),
    val detectingStatus: Boolean = false,

    // 操作状态
    val isOperating: Boolean = false,
    val lastMessage: String = "",
    val lastSuccess: Boolean = true,

    // 显示细节
    val showDetails: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshPermission()
        detectGenshinIfReady()
    }

    fun refreshPermission() {
        PermissionHelper.init()
        _uiState.update {
            it.copy(
                permissionType = PermissionHelper.permissionType,
                shizukuAvailable = PermissionHelper.isShizukuAvailable(),
                rootAvailable = PermissionHelper.isRootAvailable()
            )
        }
    }

    fun requestShizukuPermission(context: android.content.Context) {
        PermissionHelper.requestShizukuPermission(context)
        // 权限结果由 listener 回调后通过 refreshPermission 更新
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            refreshPermission()
            detectGenshinIfReady()
        }
    }

    /**
     * 在权限就绪后检测原神并刷新状态
     */
    fun detectGenshinIfReady() {
        if (PermissionHelper.hasPermission) {
            viewModelScope.launch {
                _uiState.update { it.copy(detectingGenshin = true) }

                val gi = ConfigManager.detectGenshin()
                _uiState.update {
                    it.copy(
                        genshinInfo = gi,
                        genshinDetected = gi != null,
                        detectingGenshin = false,
                        lastMessage = if (gi == null) "未检测到原神安装" else "已检测到 ${gi.label} ${gi.versionName}"
                    )
                }

                // 检测到原神后刷新 Vulkan 状态
                gi?.let { refreshVulkanStatus(it.dataPath) }
            }
        }
    }

    /**
     * 刷新 Vulkan 启用状态
     */
    fun refreshVulkanStatus(genshinPath: String? = null) {
        val path = genshinPath ?: _uiState.value.genshinInfo?.dataPath ?: return
        if (!PermissionHelper.hasPermission) return

        viewModelScope.launch {
            _uiState.update { it.copy(detectingStatus = true) }
            val status = ConfigManager.detectStatus(path)
            _uiState.update { it.copy(vulkanStatus = status, detectingStatus = false) }
        }
    }

    /**
     * 切换 Vulkan 开关
     */
    fun toggleVulkan() {
        val state = _uiState.value
        val gi = state.genshinInfo ?: return
        val currentEnabled = state.vulkanStatus.enabled

        viewModelScope.launch {
            _uiState.update { it.copy(isOperating = true) }

            val result: ShellResult = if (currentEnabled) {
                ConfigManager.disableVulkan(gi.dataPath)
            } else {
                ConfigManager.enableVulkan(gi.dataPath, ConfigMethod.AUTO)
            }

            if (result.isSuccess) {
                refreshVulkanStatus(gi.dataPath)
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        lastSuccess = true,
                        lastMessage = if (currentEnabled) "已切换至 OpenGL ✓" else "已切换至 Vulkan ✓\n重启原神生效"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        lastSuccess = false,
                        lastMessage = "操作失败: ${result.output}"
                    )
                }
            }
        }
    }

    /**
     * 清理着色器缓存
     */
    fun clearShaderCache() {
        val gi = _uiState.value.genshinInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperating = true) }
            val result = ConfigManager.clearShaderCache(gi.dataPath)
            _uiState.update {
                it.copy(
                    isOperating = false,
                    lastSuccess = result.isSuccess,
                    lastMessage = if (result.isSuccess) "着色器缓存已清理 ✓" else "清理失败: ${result.output}"
                )
            }
            if (result.isSuccess) refreshVulkanStatus(gi.dataPath)
        }
    }

    /**
     * DEX 编译优化
     */
    fun optimizeDex() {
        val gi = _uiState.value.genshinInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperating = true) }
            val result = ConfigManager.optimizeDex(gi.packageName)
            _uiState.update {
                it.copy(
                    isOperating = false,
                    lastSuccess = result.isSuccess,
                    lastMessage = if (result.isSuccess) "DEX 编译优化完成 ✓" else "优化失败: ${result.output}"
                )
            }
        }
    }

    fun toggleDetails() {
        _uiState.update { it.copy(showDetails = !it.showDetails) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(lastMessage = "") }
    }
}
