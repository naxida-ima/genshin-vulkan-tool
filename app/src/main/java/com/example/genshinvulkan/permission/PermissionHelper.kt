package com.example.genshinvulkan.permission

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shell 执行结果
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val output: String get() = stdout.ifBlank { stderr }
}

/**
 * 权限类型
 */
enum class PermissionType {
    NONE,       // 无任何权限
    SHIZUKU,    // Shizuku 已授权
    ROOT        // Root 可用
}

/**
 * 统一权限管理：Shizuku + Root 双通道
 */
object PermissionHelper {

    private var currentType = PermissionType.NONE
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == Shizuku.OnRequestPermissionResultListener.REQUEST_PERMISSION_RESULT_GRANTED) {
            currentType = PermissionType.SHIZUKU
        }
    }

    val permissionType: PermissionType get() = currentType
    val hasPermission: Boolean get() = currentType != PermissionType.NONE

    /**
     * 初始化：检测可用权限，优先级 Shizuku > Root
     */
    fun init() {
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        currentType = when {
            isShizukuAvailable() -> {
                if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    PermissionType.SHIZUKU
                } else {
                    PermissionType.NONE  // 需要手动请求
                }
            }
            isRootAvailable() -> PermissionType.ROOT
            else -> PermissionType.NONE
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (_: Exception) { false }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            p.waitFor()
            p.exitValue() == 0
        } catch (_: Exception) { false }
    }

    /**
     * 请求 Shizuku 权限
     */
    fun requestShizukuPermission(context: Context) {
        if (isShizukuAvailable() && Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        }
    }

    /**
     * 统一的 Shell 执行入口
     * 优先级：Shizuku > Root > 普通（可能失败）
     */
    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        when (currentType) {
            PermissionType.SHIZUKU -> execViaShizuku(cmd)
            PermissionType.ROOT -> execViaRoot(cmd)
            PermissionType.NONE -> execNormal(cmd)
        }
    }

    /**
     * 批量执行多条命令
     */
    suspend fun execMulti(vararg cmds: String): List<ShellResult> = withContext(Dispatchers.IO) {
        cmds.map { exec(it) }
    }

    /**
     * 执行脚本（多条命令拼接），提高效率
     */
    suspend fun execScript(cmds: List<String>): ShellResult = withContext(Dispatchers.IO) {
        val script = cmds.joinToString("\n")
        when (currentType) {
            PermissionType.SHIZUKU -> execViaShizuku(script)
            PermissionType.ROOT -> execViaRoot(script)
            PermissionType.NONE -> execNormal(script)
        }
    }

    // ─── 各通道实现 ───

    private fun execViaShizuku(cmd: String): ShellResult {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val outReader = BufferedReader(InputStreamReader(process.inputStream))
        val errReader = BufferedReader(InputStreamReader(process.errorStream))

        val outThread = Thread { outReader.forEachLine { stdout.appendLine(it) } }
        val errThread = Thread { errReader.forEachLine { stderr.appendLine(it) } }
        outThread.start()
        errThread.start()

        val exitCode = process.waitFor()
        outThread.join(2000)
        errThread.join(2000)

        return ShellResult(exitCode, stdout.toString().trim(), stderr.toString().trim())
    }

    private fun execViaRoot(cmd: String): ShellResult {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
        val exitCode = process.waitFor()
        return ShellResult(exitCode, stdout, stderr)
    }

    private fun execNormal(cmd: String): ShellResult {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
        val exitCode = process.waitFor()
        return ShellResult(exitCode, stdout, stderr)
    }
}
