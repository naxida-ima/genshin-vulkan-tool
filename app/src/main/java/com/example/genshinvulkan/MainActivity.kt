package com.example.genshinvulkan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.genshinvulkan.config.MainViewModel
import com.example.genshinvulkan.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: MainViewModel = viewModel()

        setContent {
            MainScreen(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台返回时刷新权限状态
        val vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        vm.refreshPermission()
        vm.detectGenshinIfReady()
    }
}
