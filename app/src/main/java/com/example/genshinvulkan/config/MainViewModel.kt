package com.example.genshinvulkan.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.genshinvulkan.permission.PermissionHelper
import com.example.genshinvulkan.permission.PermissionType
import android.content.Context
import com.example.genshinvulkan.permission.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GuardianState {
    None,           // 未启用守护
    Intact,         // 配置完好
    Repaired,       // 已自动修复
    Failed,         // 修复失败
    NeedPermission  // 等待授权
}

data class AppUiState(
    // 权限
    val permissionType: PermissionType = PermissionType.NONE,
    val shizukuAvailable: Boolean = false,
    val rootAvailable: Boolean = false,

    // 原神信息
    val genshinInfo: GenshinInfo? = null,
    val genshinDetected: Boolean = false,
    val detectingGenshin: Boolean = false,

    // Vulkan 状态
    val vulkanStatus: VulkanStatus = VulkanStatus(),
    val detectingStatus: Boolean = false,

    // 操作状态
    val isOperating: Boolean = false,
    val lastMessage: String = "",
    val lastSuccess: Boolean = true,

    // 显示细节
    val showDetails: Boolean = false,

    // 防还原守护
    val guardianState: GuardianState = GuardianState.None,
    val guardianMessage: String = "",

    // 诊断模式
    val diagnoseState: DiagnoseManager.DiagnosePhase = DiagnoseManager.DiagnosePhase.Idle,
    val diagnoseResult: DiagnoseManager.DiagnoseResult? = null
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshPermission()
        detectGenshinIfReady()
    }

    /**
     * 刷新权限状态。
     * 整个检测（含 Runtime.exec）在 IO 线程执行，绝不阻塞主线程，
     * 否则 isRootAvailable 的 exec+waitFor 会冻结 UI（“没反应了”）。
     */
    fun refreshPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            PermissionHelper.init()
            _uiState.update {
                it.copy(
                    permissionType = PermissionHelper.permissionType,
                    shizukuAvailable = PermissionHelper.isShizukuAvailable(),
                    rootAvailable = PermissionHelper.isRootAvailable()
                )
            }
        }
    }

    fun requestShizukuPermission(context: android.content.Context) {
        PermissionHelper.requestShizukuPermission(context)
        // 权限结果由 listener 回调后通过 refreshPermission 更新
        viewModelScope.launch(Dispatchers.IO) {
            delay(800)
            refreshPermission()
            detectGenshinIfReady()
        }
    }

    /**
     * 检测原神并刷新状态。
     * 关键：无条件执行，不依赖权限——没权限时走普通 shell 检测，
     * 跑完必定把 detectingGenshin 置回 false，避免 UI 永久卡在“检测中”。
     */
    fun detectGenshinIfReady() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(detectingGenshin = true) }

            val gi = ConfigManager.detectGenshin()
            _uiState.update {
                it.copy(
                    genshinInfo = gi,
                    genshinDetected = gi != null,
                    detectingGenshin = false,
                    lastMessage = if (gi == null) {
                        if (PermissionHelper.hasPermission) {
                            "未检测到原神安装"
                        } else {
                            "未检测到原神 · 请先授权 Shizuku 或 Root"
                        }
                    } else {
                        "已检测到 ${gi.label} ${gi.versionName}"
                    }
                )
            }

            // 检测到原神后刷新 Vulkan 状态
            gi?.let { refreshVulkanStatus(it.dataPath) }
        }
    }

    /**
     * 刷新 Vulkan 启用状态
     */
    fun refreshVulkanStatus(genshinPath: String? = null) {
        val path = genshinPath ?: _uiState.value.genshinInfo?.dataPath ?: return
        if (!PermissionHelper.hasPermission) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(detectingStatus = true) }
            val status = ConfigManager.detectStatus(path)
            _uiState.update { it.copy(vulkanStatus = status, detectingStatus = false) }
        }
    }

    /**
     * 切换 Vulkan 开关
     */
    fun toggleVulkan(context: android.content.Context) {
        val state = _uiState.value
        val gi = state.genshinInfo ?: return
        val currentEnabled = state.vulkanStatus.enabled

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isOperating = true) }

            val result: ShellResult = if (currentEnabled) {
                IntentStore.write(context, "opengl")
                ConfigManager.disableVulkan(gi.dataPath)
            } else {
                IntentStore.write(context, "vulkan")
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
     * 防还原守护：仅在用户意图为 Vulkan 时，
     * 检测配置是否被游戏（更新 / 资源校验）还原，是则自动重写。
     * 由 MainActivity.onResume 在每次回到 App 时调用。
     */
    fun guardVulkan(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val intent = IntentStore.read(context)
            if (intent != "vulkan") {
                _uiState.update { it.copy(guardianState = GuardianState.None, guardianMessage = "") }
                return@launch
            }
            if (!PermissionHelper.hasPermission) {
                _uiState.update {
                    it.copy(
                        guardianState = GuardianState.NeedPermission,
                        guardianMessage = "守护待命：请先授权 Shizuku / Root 后再自动修复"
                    )
                }
                return@launch
            }
            // 确保已检测到原神（onResume 中检测可能尚未完成）
            var gi = _uiState.value.genshinInfo
            if (gi == null) {
                gi = ConfigManager.detectGenshin()
                if (gi != null) {
                    _uiState.update { s -> s.copy(genshinInfo = gi, genshinDetected = true) }
                }
            }
            if (gi == null) {
                _uiState.update {
                    it.copy(guardianState = GuardianState.None, guardianMessage = "守护：未检测到原神")
                }
                return@launch
            }

            val r = ConfigManager.ensureVulkan(gi.dataPath)
            val (gState, gMsg) = when (r) {
                is GuardResult.Intact -> GuardianState.Intact to "守护：Vulkan 配置完好 ✓"
                is GuardResult.Repaired -> GuardianState.Repaired to "守护：检测到配置被还原，已自动重新写入 ✓"
                is GuardResult.NoPermission -> GuardianState.NeedPermission to "守护：需要权限才能修复"
                is GuardResult.NotTarget -> GuardianState.None to ""
                is GuardResult.Failed -> GuardianState.Failed to "守护：修复失败 ${r.msg}"
            }
            _uiState.update { it.copy(guardianState = gState, guardianMessage = gMsg) }
            if (r is GuardResult.Repaired || r is GuardResult.Intact) {
                refreshVulkanStatus(gi.dataPath)
            }
        }
    }

    /**
     * 手动「重新应用」：强制重写 Vulkan 配置，并标记为意图 vulkan。
     */
    fun reapplyVulkan(context: android.content.Context) {
        val gi = _uiState.value.genshinInfo ?: return
        if (!PermissionHelper.hasPermission) {
            _uiState.update {
                it.copy(
                    lastSuccess = false,
                    lastMessage = "请先授权 Shizuku / Root",
                    guardianState = GuardianState.NeedPermission,
                    guardianMessage = "需要权限才能重新应用"
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isOperating = true) }
            IntentStore.write(context, "vulkan")
            val r = ConfigManager.enableVulkan(gi.dataPath, ConfigMethod.AUTO)
            _uiState.update { it.copy(isOperating = false) }
            if (r.isSuccess) {
                refreshVulkanStatus(gi.dataPath)
                _uiState.update {
                    it.copy(
                        lastSuccess = true,
                        lastMessage = "已重新应用 Vulkan 配置 ✓\n重启原神生效",
                        guardianState = GuardianState.Repaired,
                        guardianMessage = "已重新应用 Vulkan 配置 ✓"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        lastSuccess = false,
                        lastMessage = "重新应用失败: ${r.output}",
                        guardianState = GuardianState.Failed,
                        guardianMessage = "重新应用失败: ${r.output}"
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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

    /**
     * 诊断模式：在手机上用 Shizuku 跑配置校验（PC verify 脚本的 App 内版本）。
     * 不需要 PC、不需要 root（Shizuku 优先通道）。
     */
    fun runDiagnose(context: android.content.Context) {
        val gi = _uiState.value.genshinInfo
        if (gi == null) {
            _uiState.update {
                it.copy(
                    diagnoseState = DiagnoseManager.DiagnosePhase.Error,
                    diagnoseResult = null,
                    lastMessage = "未检测到原神，无法诊断"
                )
            }
            return
        }
        if (!PermissionHelper.hasPermission) {
            _uiState.update {
                it.copy(
                    diagnoseState = DiagnoseManager.DiagnosePhase.NoPermission,
                    diagnoseResult = null,
                    lastMessage = "请先授权 Shizuku / Root 再运行诊断"
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(diagnoseState = DiagnoseManager.DiagnosePhase.Running, diagnoseResult = null)
            }
            try {
                val result = DiagnoseManager.diagnose(gi.dataPath)
                _uiState.update {
                    it.copy(diagnoseState = DiagnoseManager.DiagnosePhase.Done, diagnoseResult = result)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(diagnoseState = DiagnoseManager.DiagnosePhase.Error, diagnoseResult = null)
                }
            }
        }
    }
}
