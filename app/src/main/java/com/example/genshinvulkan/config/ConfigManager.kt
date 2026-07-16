package com.example.genshinvulkan.config

import com.example.genshinvulkan.device.DeviceInfo
import com.example.genshinvulkan.permission.PermissionHelper
import com.example.genshinvulkan.permission.ShellResult

/**
 * Vulkan 配置方法枚举
 */
enum class ConfigMethod(val label: String) {
    ENGINE("引擎配置 6.0+"),   // vulkan_gpu_list_config_engine.txt
    GPU_LIST("GPU白名单 5.2+"), // vulkan_gpu_list_config.txt
    LEGACY("旧版 JSON"),        // hardware_model_config.json
    AUTO("自动检测")
}

/**
 * 配置状态
 */
data class VulkanStatus(
    val enabled: Boolean = false,
    val method: ConfigMethod = ConfigMethod.AUTO,
    val gpuConfigured: String = "",
    val shaderCacheExists: Boolean = false,
    val shaderCacheSize: String = ""
)

/**
 * 原神版本信息
 */
data class GenshinInfo(
    val packageName: String = "",
    val versionName: String = "",
    val installed: Boolean = false,
    val dataPath: String = "",
    val label: String = ""
)

/**
 * 配置管理器 — 核心模块
 *
 * 原神配置文件路径（以国服为例）：
 *   /storage/emulated/0/Android/data/com.miHoYo.Yuanshen/files/
 *
 * 三种 Vulkan 配置方法：
 *   1. hardware_model_config.json     — 旧版（5.2 前），修改 vulkanFlag
 *   2. vulkan_gpu_list_config.txt     — 5.2+，写入 GPU 型号
 *   3. vulkan_gpu_list_config_engine.txt — 6.0+，首行 "2"，次行 GPU 型号
 */
object ConfigManager {

    // 支持的包名（按优先级）
    private val SUPPORTED_PACKAGES = listOf(
        "com.miHoYo.Yuanshen",        // 国服
        "com.miHoYo.GenshinImpact",   // 国际服
    )

    private const val FILES_SUFFIX = "/files"
    private const val JSON_FILE = "hardware_model_config.json"
    private const val GPU_LIST_FILE = "vulkan_gpu_list_config.txt"
    private const val GPU_ENGINE_FILE = "vulkan_gpu_list_config_engine.txt"
    private const val SHADER_CACHE_DIR = "UnityVulkanPSO"

    // ─── 原神检测 ───

    /**
     * 检测已安装的原神（通过检查 data 目录是否存在）
     */
    suspend fun detectGenshin(): GenshinInfo? {
        for (pkg in SUPPORTED_PACKAGES) {
            val dataPath = "/storage/emulated/0/Android/data/$pkg"
            val filesPath = "$dataPath$FILES_SUFFIX"

            // 检查目录是否存在
            val checkResult = PermissionHelper.exec("test -d \"$filesPath\" && echo 'EXISTS'")
            if (checkResult.stdout.contains("EXISTS")) {
                val label = when {
                    pkg.contains("GenshinImpact") -> "原神·国际服"
                    else -> "原神·国服"
                }
                // 尝试获取版本号
                val version = tryGetVersion(pkg)
                return GenshinInfo(
                    packageName = pkg,
                    versionName = version,
                    installed = true,
                    dataPath = filesPath,
                    label = label
                )
            }
        }
        return null
    }

    private suspend fun tryGetVersion(pkg: String): String {
        val result = PermissionHelper.exec("dumpsys package $pkg | grep versionName | head -1")
        val match = Regex("versionName=([\\d.]+)").find(result.stdout)
        return match?.groupValues?.get(1) ?: "未知"
    }

    // ─── 状态检测 ───

    /**
     * 检测当前 Vulkan 状态
     */
    suspend fun detectStatus(genshinPath: String): VulkanStatus {
        val gpuStr = DeviceInfo.getGpuListString()

        // 检查 engine 文件（6.0+）
        val engineContent = readFile("$genshinPath/$GPU_ENGINE_FILE")
        if (engineContent.isSuccess && engineContent.stdout.isNotBlank()) {
            val lines = engineContent.stdout.lines().map { it.trim() }
            val hasGpu = lines.any { it.equals(gpuStr, ignoreCase = true) } ||
                          lines.any { it.contains(gpuStr.substringBefore(" "), ignoreCase = true) }
            if (hasGpu) {
                return buildVulkanStatus(true, ConfigMethod.ENGINE, gpuStr, genshinPath)
            }
        }

        // 检查 GPU list 文件（5.2+）
        val listContent = readFile("$genshinPath/$GPU_LIST_FILE")
        if (listContent.isSuccess && listContent.stdout.isNotBlank()) {
            val hasGpu = listContent.stdout.contains(gpuStr, ignoreCase = true) ||
                         listContent.stdout.contains(gpuStr.substringBefore(" "), ignoreCase = true)
            if (hasGpu) {
                return buildVulkanStatus(true, ConfigMethod.GPU_LIST, gpuStr, genshinPath)
            }
        }

        // 检查 JSON（旧版）
        val jsonContent = readFile("$genshinPath/$JSON_FILE")
        if (jsonContent.isSuccess) {
            if (jsonContent.stdout.contains("\"vulkanFlag\": 1") ||
                jsonContent.stdout.contains("\"vulkanFlag\":1")) {
                return buildVulkanStatus(true, ConfigMethod.LEGACY, gpuStr, genshinPath)
            }
        }

        return buildVulkanStatus(false, ConfigMethod.AUTO, gpuStr, genshinPath)
    }

    private suspend fun buildVulkanStatus(
        enabled: Boolean,
        method: ConfigMethod,
        gpu: String,
        path: String
    ): VulkanStatus {
        val cacheExists = PermissionHelper
            .exec("test -d \"$path/$SHADER_CACHE_DIR\" && echo 'YES'")
            .stdout.contains("YES")
        val cacheSize = if (cacheExists) {
            PermissionHelper
                .exec("du -sh \"$path/$SHADER_CACHE_DIR\" 2>/dev/null | cut -f1")
                .stdout.ifBlank { "?" }
        } else ""

        return VulkanStatus(enabled, method, gpu, cacheExists, cacheSize)
    }

    // ─── 核心：启用 Vulkan ───

    /**
     * 启用 Vulkan（自动选最优方法）
     */
    suspend fun enableVulkan(genshinPath: String, method: ConfigMethod = ConfigMethod.AUTO): ShellResult {
        val gpuStr = DeviceInfo.getGpuListString()
        val deviceModel = DeviceInfo.model

        val resolvedMethod = when (method) {
            ConfigMethod.AUTO -> detectBestMethod(genshinPath)
            else -> method
        }

        return when (resolvedMethod) {
            ConfigMethod.ENGINE -> writeEngineConfig(genshinPath, gpuStr)
            ConfigMethod.GPU_LIST -> writeGpuListConfig(genshinPath, gpuStr)
            ConfigMethod.LEGACY -> writeLegacyConfig(genshinPath, deviceModel)
            ConfigMethod.AUTO -> ShellResult(-1, "", "无法确定配置方法")
        }
    }

    /**
     * 禁用 Vulkan（还原配置）
     */
    suspend fun disableVulkan(genshinPath: String): ShellResult {
        // 清空/删除所有 Vulkan 配置文件
        val cmds = listOf(
            "rm -f \"$genshinPath/$GPU_LIST_FILE\"",
            "rm -f \"$genshinPath/$GPU_ENGINE_FILE\""
        )
        val result = PermissionHelper.execScript(cmds)

        // 对于 JSON，恢复 vulkanFlag=0
        val jsonResult = readFile("$genshinPath/$JSON_FILE")
        if (jsonResult.isSuccess && jsonResult.stdout.contains("vulkanFlag")) {
            val restored = jsonResult.stdout
                .replace("\"vulkanFlag\": 1", "\"vulkanFlag\": 0")
                .replace("\"vulkanFlag\":1", "\"vulkanFlag\":0")
            writeFile("$genshinPath/$JSON_FILE", restored)
        }

        return result
    }

    // ─── 配置写入方法 ───

    /**
     * 6.0+ 引擎配置文件
     * 格式：
     *   2
     *   Adreno (TM) 830
     */
    private suspend fun writeEngineConfig(genshinPath: String, gpuStr: String): ShellResult {
        val content = "2\n$gpuStr\n"
        return writeFile("$genshinPath/$GPU_ENGINE_FILE", content)
    }

    /**
     * 5.2+ GPU 白名单
     * 格式：一行 GPU 型号
     */
    private suspend fun writeGpuListConfig(genshinPath: String, gpuStr: String): ShellResult {
        return writeFile("$genshinPath/$GPU_LIST_FILE", gpuStr)
    }

    /**
     * 旧版 JSON 配置
     * 修改 hardware_model_config.json，将 vulkanFlag 改为 1
     */
    private suspend fun writeLegacyConfig(genshinPath: String, deviceModel: String): ShellResult {
        val existing = readFile("$genshinPath/$JSON_FILE")
        val json = if (existing.isSuccess && existing.stdout.isNotBlank()) {
            existing.stdout
        } else {
            // 创建默认 JSON
            """{"configs":[{"hardwareModel":"$deviceModel","vulkanFlag":1}]}"""
        }

        // 查找是否已有本机配置
        val hasDeviceEntry = json.contains("\"hardwareModel\": \"$deviceModel\"") ||
                             json.contains("\"hardwareModel\":\"$deviceModel\"")

        val modified = if (hasDeviceEntry) {
            // 修改已有的 vulkanFlag
            json.replace("\"vulkanFlag\": 0", "\"vulkanFlag\": 1")
                .replace("\"vulkanFlag\":0", "\"vulkanFlag\":1")
        } else {
            // 在第一组配置前插入
            if (json.contains("\"configs\": [")) {
                val insert = """
    {"hardwareModel":"$deviceModel","littleCoreCount":0,"bigCoreCount":8,"littleCoreMask":0,"bigCoreMask":200,"vulkanFlag":1,"openglFlag":0,"vulkanSupport":1,"openglSupport":0,"TextureFormats":"ASTC","isVariableMaxFPS":1,"unityQualityGraphics":"01","GpuUsageNodeMask":"00000001h","JobsUtility.JobWorkerMaximumCount":8},
""".trimEnd()
                json.replace("\"configs\": [", "\"configs\": [\n$insert")
            } else {
                """{"configs":[{"hardwareModel":"$deviceModel","vulkanFlag":1}]}"""
            }
        }

        return writeFile("$genshinPath/$JSON_FILE", modified)
    }

    // ─── 文件操作辅助 ───

    private suspend fun readFile(path: String): ShellResult {
        return PermissionHelper.exec("cat \"$path\" 2>/dev/null")
    }

    /**
     * 写入文件 — 安全方法：
     * 1. 将内容用 base64 编码后通过管道写入目标文件
     * 2. 避免 echo 导致的引号和特殊字符逃逸问题
     */
    private suspend fun writeFile(path: String, content: String): ShellResult {
        val b64 = android.util.Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        return PermissionHelper.exec(
            "mkdir -p \"$(dirname \"$path\")\" 2>/dev/null; " +
            "chmod 755 \"$(dirname \"$path\")\" 2>/dev/null; " +
            "echo '$b64' | base64 -d > \"$path\""
        )
    }

    // ─── 辅助功能 ───

    /**
     * 清理着色器缓存（UnityVulkanPSO + cache 目录）
     * 安全项：只清理缓存，不影响账号/进度
     */
    suspend fun clearShaderCache(genshinPath: String): ShellResult {
        return PermissionHelper.execScript(listOf(
            "rm -rf \"$genshinPath/$SHADER_CACHE_DIR\"",
            "echo 'Cache cleared'"
        ))
    }

    /**
     * 检测最佳配置方法
     */
    private suspend fun detectBestMethod(genshinPath: String): ConfigMethod {
        // 优先检测 engine 文件是否存在（说明游戏支持 6.0+ 方法）
        val engineExists = PermissionHelper
            .exec("test -f \"$genshinPath/$GPU_ENGINE_FILE\" && echo 'YES' || echo 'NO'")
            .stdout.contains("YES")

        if (engineExists) return ConfigMethod.ENGINE

        // 其次检查 GPU list 是否存在
        val listExists = PermissionHelper
            .exec("test -f \"$genshinPath/$GPU_LIST_FILE\" && echo 'YES' || echo 'NO'")
            .stdout.contains("YES")

        if (listExists) return ConfigMethod.GPU_LIST

        // 最后回退到 JSON 方法
        return ConfigMethod.LEGACY
    }

    /**
     * DEX 编译优化（通过 cmd package compile）
     * 安全项：使用官方 speed 编译模式，不触碰游戏逻辑
     */
    suspend fun optimizeDex(packageName: String): ShellResult {
        return PermissionHelper.exec("cmd package compile -m speed -f $packageName")
    }

    /**
     * 获取着色器缓存大小（人类可读）
     */
    suspend fun getCacheSize(genshinPath: String): String {
        val result = PermissionHelper.exec("du -sh \"$genshinPath/$SHADER_CACHE_DIR\" 2>/dev/null | cut -f1")
        return result.stdout.ifBlank { "0" }
    }
}
