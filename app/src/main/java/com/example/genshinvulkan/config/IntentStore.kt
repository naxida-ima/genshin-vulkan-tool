package com.example.genshinvulkan.config

import android.content.Context
import java.io.File

/**
 * 记录用户对 Vulkan 的「意图」（vulkan / opengl）。
 *
 * 存放在本应用私有目录（/Android/data/com.example.genshinvulkan/files/），
 * 该目录：
 *   1. 不需要 Shizuku / Root 权限即可读写；
 *   2. 不受原神更新 / 资源校验影响（原神只动它自己的 files 目录）。
 *
 * 守护机制仅在意图为 "vulkan" 时才自动重写配置，
 * 避免用户在手动切回 OpenGL 后又被强行改回 Vulkan。
 */
object IntentStore {
    private const val FILE = "vulkan_intent.txt"

    fun read(ctx: Context): String? {
        return try {
            val f = File(ctx.getExternalFilesDir(null), FILE)
            if (f.exists()) f.readText().trim().ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    fun write(ctx: Context, intent: String) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: return
            File(dir, FILE).writeText(intent)
        } catch (_: Exception) {
            // 忽略：仅影响守护判断，不阻断手动操作
        }
    }

    fun clear(ctx: Context) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: return
            File(dir, FILE).delete()
        } catch (_: Exception) {
            // 忽略
        }
    }
}
