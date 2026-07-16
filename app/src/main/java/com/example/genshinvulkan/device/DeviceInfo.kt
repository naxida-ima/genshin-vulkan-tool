package com.example.genshinvulkan.device

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 设备信息检测：GPU型号、设备型号等
 */
object DeviceInfo {

    /** 获取设备型号（如 Xiaomi 23013RK75C）*/
    val model: String get() = Build.MODEL

    /** 获取设备品牌 */
    val brand: String get() = Build.BRAND

    /** 获取 Android 版本 */
    val androidVersion: String get() = Build.VERSION.RELEASE

    /** 获取芯片平台（从 /proc/cpuinfo 或 ro.board.platform）*/
    val chipset: String by lazy { detectChipset() }

    /** 获取 GPU 渲染器名称（如 Adreno (TM) 830）*/
    val gpuRenderer: String by lazy { detectGpuRenderer() }

    /** 获取 GPU 供应商 */
    val gpuVendor: String by lazy { detectGpuVendor() }

    /**
     * 判断芯片类型
     */
    fun detectChipType(): ChipType {
        val gpu = gpuRenderer.lowercase()
        return when {
            gpu.contains("adreno") -> ChipType.SNAPDRAGON
            gpu.contains("mali") -> ChipType.MEDIATEK
            gpu.contains("powervr") -> ChipType.POWERVR
            gpu.contains("xclipse") || gpu.contains("exynos") -> ChipType.EXYNOS
            else -> ChipType.UNKNOWN
        }
    }

    /**
     * 获取 Vulkan 白名单中需要填入的 GPU 型号字符串
     * 例如："Adreno (TM) 830", "Mali-G710"
     */
    fun getGpuListString(): String {
        return when (detectChipType()) {
            ChipType.SNAPDRAGON -> {
                // 从 full gpu renderer string 提取 Adreno 部分
                val gpu = gpuRenderer
                val regex = Regex("Adreno\\s*\\(TM\\)\\s*(\\d+)")
                regex.find(gpu)?.value?.trim() ?: gpu
            }
            ChipType.MEDIATEK -> {
                val gpu = gpuRenderer
                val regex = Regex("Mali-[A-Z]?\\d+")
                regex.find(gpu)?.value ?: gpu
            }
            else -> gpuRenderer
        }
    }

    /**
     * 检测 GPU 渲染器名称
     * 尝试多个来源：egl, sysfs, prop
     */
    private fun detectGpuRenderer(): String {
        // 方法1: 通过 /proc 下的 gl strings（无需 root）
        try {
            val files = arrayOf(
                "/sys/class/kgsl/kgsl-3d0/gpubusy",  // 仅作为存在检测
            )
        } catch (_: Exception) {}

        // 方法2: 通过 getprop（需要 root/shizuku 才能读某些属性）
        // 方法3: 通过 /proc/cpuinfo 的 Hardware 行 + 推断
        // 方法4: ro.hardware.chipname 属性

        // 最佳方法：直接读取 ro.hardware.vulkan 或使用 EGL
        // 这里返回默认值，实际运行时通过 Shell 获取
        return tryGetProp("ro.gfx.driver.1")
            ?: tryGetProp("ro.hardware.egl")
            ?: "Unknown GPU"
    }

    private fun detectGpuVendor(): String {
        return tryGetProp("ro.gpu.vendor")
            ?: when (detectChipType()) {
                ChipType.SNAPDRAGON -> "Qualcomm"
                ChipType.MEDIATEK -> "ARM"
                ChipType.EXYNOS -> "Samsung"
                ChipType.POWERVR -> "Imagination"
                else -> "Unknown"
            }
    }

    private fun detectChipset(): String {
        return tryGetProp("ro.board.platform")
            ?: tryGetProp("ro.chipname")
            ?: tryGetProp("ro.hardware.chipname")
            ?: Build.HARDWARE
    }

    private fun tryGetProp(prop: String): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", prop))
            BufferedReader(InputStreamReader(p.inputStream)).use { it.readLine()?.trim()?.ifEmpty { null } }
        } catch (_: Exception) { null }
    }
}

enum class ChipType { SNAPDRAGON, MEDIATEK, EXYNOS, POWERVR, UNKNOWN }
