package com.example.genshinvulkan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.genshinvulkan.config.MainViewModel
import com.example.genshinvulkan.permission.PermissionHelper
import com.example.genshinvulkan.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 整个 Activity 生命周期内只主动弹一次授权窗，避免反复骚扰
    private var autoRequestedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MainScreen(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()

        // 主动申请 Shizuku 授权：
        // 检测到 Shizuku 可用且尚未授权时，主动弹窗申请，
        // 这样本应用才会进入 Shizuku 的「可授权列表」，用户只需点允许。
        // 必须先 pingBinder 确认存活，否则直接 requestPermission 会闪退（Shizuku 官方提示）。
        if (!autoRequestedOnce
            && PermissionHelper.isShizukuAvailable()
            && !PermissionHelper.isGranted()
            && !PermissionHelper.shouldShowRationale()
        ) {
            autoRequestedOnce = true
            PermissionHelper.requestShizukuPermission(this)
        }

        // 从后台返回时刷新权限状态
        viewModel.refreshPermission()
        viewModel.detectGenshinIfReady()
    }
}
