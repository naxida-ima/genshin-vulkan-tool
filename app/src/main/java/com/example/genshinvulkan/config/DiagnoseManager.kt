package com.example.genshinvulkan.config

import com.example.genshinvulkan.device.DeviceInfo
import com.example.genshinvulkan.permission.PermissionHelper

/**
 * 诊断模式 —— 把 PC 端 verify 脚本搬进 App，用 Shizuku（或 Root / 普通）shell 在手机上直接跑，
 * 不需要 PC、也不需要 root（Shizuku 优先通道）。
 *
 * 逆向结论（6.7.0 官方 APK）：
 *   - vulkan_gpu_list_config.txt 是 GPU 白名单的 PersistentFile 副本，权威源在 APK 内 assets，
 *     首行 version=7、整文件 422 字节。游戏启动时比对 version 与 fileSize，不符则用 APK 内源重建（= 被还原）。
 *   - vulkan_gpu_list_config_engine.txt（6.0+）是运行时持久配置，首行 "2"，不在 APK 内。
 *   - 因此：若写入后白名单 version≠7 或 size≠422，就会触发 "Invalid version provided" + "fileSize not equal"
 *     → 游戏用 APK 权威源重建 → 这正是「写明文文件被还原」的代码级根因。
 */
object DiagnoseManager {

    // 参考基线：6.7.0 官方 APK 白名单样本（首行版本号、整文件字节数）。
    // 不同版本可能变化，仅作「健康」参考，不作为绝对标准。
    private const val REF_WHITELIST_VERSION = 7
    private const val REF_WHITELIST_SIZE = 422

    private const val GPU_LIST_FILE = "vulkan_gpu_list_config.txt"
    private const val GPU_ENGINE_FILE = "vulkan_gpu_list_config_engine.txt"

    /** 诊断阶段（状态机，供 UI 驱动） */
    enum class DiagnosePhase { Idle, Running, Done, NoPermission, Error }

    /** 单个配置文件诊断 */
    data class FileDiag(
        val name: String,
        val path: String,
        val exists: Boolean,
        val size: Int = -1,
        val firstLine: String = "",      // 白名单=version 数字；engine="2"
        val gpuLines: List<String> = emptyList(),
        val containsDeviceGpu: Boolean = false,
        val raw: String = ""
    )

    /** 诊断总结果 */
    data class DiagnoseResult(
        val deviceGpu: String,
        val whiteList: FileDiag?,
        val engineCfg: FileDiag?,
        val risk: Boolean,               // 是否处于「会被游戏还原」状态
        val verdict: String,             // 人话结论
        val logcatHints: String = ""     // 关键日志摘要（尽力而为）
    )

    /**
     * 执行完整诊断。
     * @param genshinPath 形如 /storage/emulated/0/Android/data/com.miHoYo.Yuanshen/files
     */
    suspend fun diagnose(genshinPath: String): DiagnoseResult {
        val deviceGpu = DeviceInfo.getGpuListString()

        // 1) 定位配置文件（Shizuku shell 即可访问，无需 root）
        val find = PermissionHelper.exec(
            "find \"$genshinPath\" -maxdepth 1 -name \"*vulkan_gpu_list_config*\" 2>/dev/null"
        )
        val found = find.stdout.lines().map { it.trim() }.filter { it.isNotBlank() }

        val whiteList = found.firstOrNull { it.endsWith(GPU_LIST_FILE) }?.let { readConfig(it) }
        val engineCfg = found.firstOrNull { it.endsWith(GPU_ENGINE_FILE) }?.let { readConfig(it) }

        // 2) 判定风险：白名单被改坏（version / size 偏离参考基线）
        val risk = whiteList?.let { wl ->
            wl.exists && (wl.firstLine != REF_WHITELIST_VERSION.toString() || wl.size != REF_WHITELIST_SIZE)
        } ?: false

        // 3) 日志抓取（尽力而为，失败不影响主诊断）
        val logcatHints = tryCaptureLogcat()

        // 4) 生成人话结论
        val verdict = buildVerdict(whiteList, engineCfg, deviceGpu, risk)

        return DiagnoseResult(
            deviceGpu = deviceGpu,
            whiteList = whiteList,
            engineCfg = engineCfg,
            risk = risk,
            verdict = verdict,
            logcatHints = logcatHints
        )
    }

    private suspend fun readConfig(path: String): FileDiag {
        val cat = PermissionHelper.exec("cat \"$path\" 2>/dev/null")
        val sizeRes = PermissionHelper.exec("wc -c < \"$path\" 2>/dev/null")
        val raw = cat.stdout
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val firstLine = lines.firstOrNull() ?: ""
        val deviceGpu = DeviceInfo.getGpuListString()
        val contains = lines.any { it.equals(deviceGpu, ignoreCase = true) }
            || (deviceGpu.isNotBlank() && lines.any {
                it.contains(deviceGpu.substringBefore(" "), ignoreCase = true)
            })
        return FileDiag(
            name = path.substringAfterLast("/"),
            path = path,
            exists = cat.isSuccess && (raw.isNotBlank() || (sizeRes.stdout.trim().toIntOrNull() ?: 0) > 0),
            size = sizeRes.stdout.trim().toIntOrNull() ?: -1,
            firstLine = firstLine,
            gpuLines = lines.drop(1),
            containsDeviceGpu = contains,
            raw = raw
        )
    }

    /**
     * 抓关键日志（确认比对方向 / 还原触发点）。
     * Shizuku shell 以 shell uid 运行，通常可读 logcat；普通权限 App 拿不到 READ_LOGS，但走 Shizuku 不受此限。
     * 失败则返回空串，绝不影响主诊断。
     */
    private suspend fun tryCaptureLogcat(): String {
        val keywords = listOf(
            "VulkanGpuConfig", "vulkan_gpu_list_config",
            "Invalid version", "fileSize", "Vulkan Swapchain",
            "SetVulkanDeviceConfig", "Vulkan not support"
        ).joinToString("|")
        val res = PermissionHelper.exec(
            "logcat -d -t 800 2>/dev/null | grep -Ei \"$keywords\" | tail -n 20"
        )
        return res.stdout.trim()
    }

    private fun buildVerdict(
        wl: FileDiag?, engine: FileDiag?, deviceGpu: String, risk: Boolean
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            "本机 GPU：${
                if (deviceGpu.isBlank() || deviceGpu == "Unknown GPU") "⚠️ 识别失败（Unknown GPU）" else deviceGpu
            }"
        )

        if (wl == null && engine == null) {
            sb.appendLine("未找到任何 vulkan_gpu_list_config 文件，原神可能尚未生成配置或路径未授权。")
            return sb.toString().trimEnd()
        }

        wl?.let {
            sb.appendLine()
            sb.appendLine("【白名单 vulkan_gpu_list_config.txt】")
            if (!it.exists) {
                sb.appendLine("  · 不存在（游戏尚未写入 / 已被清）")
            } else {
                sb.appendLine("  · 首行 version = ${it.firstLine.ifBlank { "（空）" }}（参考基线 7）")
                sb.appendLine("  · 文件大小 = ${it.size} 字节（参考基线 422）")
                sb.appendLine("  · GPU 条目数 = ${it.gpuLines.size}")
                sb.appendLine("  · 是否含本机 GPU = ${if (it.containsDeviceGpu) "是 ✓" else "否 ✗"}")
                if (it.firstLine != REF_WHITELIST_VERSION.toString() || it.size != REF_WHITELIST_SIZE) {
                    sb.appendLine("  ⚠️ version 或 size 偏离参考基线 → 游戏启动时会用 APK 内权威源重建（= 被还原）")
                } else {
                    sb.appendLine("  ✓ version / size 与参考基线一致，结构未被破坏")
                }
            }
        }

        engine?.let {
            sb.appendLine()
            sb.appendLine("【引擎配置 vulkan_gpu_list_config_engine.txt（6.0+）】")
            if (!it.exists) {
                sb.appendLine("  · 不存在（首次启动后由游戏生成）")
            } else {
                sb.appendLine("  · 首行 = ${it.firstLine.ifBlank { "（空）" }}（应为 2）")
                sb.appendLine("  · 是否含本机 GPU = ${if (it.containsDeviceGpu) "是 ✓" else "否 ✗"}")
            }
        }

        sb.appendLine()
        if (risk) {
            sb.appendLine("📌 结论：当前白名单配置处于「会被游戏自动还原」状态。")
            sb.appendLine("   修复方向（策略 A）：原地等长替换 GPU 型号，保持 version=7 与总字节数 422 不变，")
            sb.appendLine("   让 version / size 校验通过，从而绕过还原。")
        } else {
            sb.appendLine("📌 结论：当前配置文件结构健康，未被篡改，游戏不会还原。")
        }

        return sb.toString().trimEnd()
    }
}
