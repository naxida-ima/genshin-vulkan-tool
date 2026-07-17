package com.example.genshinvulkan.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.genshinvulkan.config.AppUiState
import com.example.genshinvulkan.config.GuardianState
import com.example.genshinvulkan.config.MainViewModel
import com.example.genshinvulkan.config.DiagnoseManager.DiagnosePhase
import com.example.genshinvulkan.permission.PermissionType
import com.example.genshinvulkan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 背景动画
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "bgShift"
    )

    GenshinVulkanTheme {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val topInset = WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 小图标
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🍃", fontSize = 18.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "纳西妲·引擎切换",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "OpenGL → Vulkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── 动画渐变背景 ──
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        BgGradientStart,
                        BgGradientEnd,
                        BgGradientStart.copy(alpha = 0.6f)
                    ),
                    startY = bgShift * 2000f,
                    endY = (bgShift * 2000f) + 2000f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgBrush)
                )

                // ── 主内容滚动区 ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))

                    // ═══════ 权限状态 ═══════
                    PermissionChip(state, viewModel, context)

                    Spacer(Modifier.height(12.dp))

                    // ═══════ 原神检测 ═══════
                    GenshinChip(state)

                    if (state.genshinDetected) {
                        Spacer(Modifier.height(20.dp))

                        // ═══════ 核心指示器（大圆） ═══════
                        VulkanHeroIndicator(state)

                        Spacer(Modifier.height(24.dp))

                        // ═══════ 切换按钮 ═══════
                        ToggleSwitch(state, viewModel, context)

                        // ═══════ 防还原守护 ═══════
                        GuardianCard(state, viewModel, context)

                        // ═══════ 诊断模式 ═══════
                        DiagnoseCard(state, viewModel, context)

                        // ═══════ 消息 ═══════
                        AnimatedVisibility(
                            visible = state.lastMessage.isNotBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            MessageToast(state, viewModel)
                        }

                        Spacer(Modifier.height(20.dp))

                        // ═══════ 信息卡片 ═══════
                        InfoCard(state, viewModel)

                        Spacer(Modifier.height(12.dp))

                        // ═══════ 性能工具 ═══════
                        ToolsCard(state, viewModel)
                    }

                    // 底部留白
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// 权限 Chip
// ═══════════════════════════════════════════

@Composable
private fun PermissionChip(
    state: AppUiState,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    val (bgColor, icon, title, subtitle) = when {
        state.permissionType == PermissionType.SHIZUKU -> listOf(
            SuccessGreen.copy(alpha = 0.12f),
            Icons.Outlined.VerifiedUser,
            "Shizuku 已授权",
            "高权限模式运行中"
        )
        state.permissionType == PermissionType.ROOT -> listOf(
            WarningOrange.copy(alpha = 0.12f),
            Icons.Outlined.Terminal,
            "Root 权限可用",
            "超级用户模式"
        )
        state.shizukuAvailable -> listOf(
            Accent.copy(alpha = 0.12f),
            Icons.Outlined.LockOpen,
            "Shizuku 可用",
            "点击授权即可使用"
        )
        else -> listOf(
            DangerRed.copy(alpha = 0.1f),
            Icons.Outlined.Lock,
            "需要权限",
            "请安装 Shizuku 或 Root"
        )
    }

    @Suppress("UNCHECKED_CAST")
    val cast = bgColor as Color
    val castIcon = icon as androidx.compose.ui.graphics.vector.ImageVector
    val castTitle = title as String
    val castSub = subtitle as String

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cast,
        onClick = {
            if (state.shizukuAvailable && state.permissionType == PermissionType.NONE) {
                viewModel.requestShizukuPermission(context)
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                castIcon, contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when {
                    state.permissionType != PermissionType.NONE -> SuccessGreen
                    state.shizukuAvailable -> Accent
                    else -> DangerRed
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                castTitle,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            Text(
                castSub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════
// 原神检测 Chip
// ═══════════════════════════════════════════

@Composable
private fun GenshinChip(state: AppUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (state.genshinDetected)
            SuccessGreen.copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.detectingGenshin) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在检测原神...", style = MaterialTheme.typography.bodySmall)
            } else if (state.genshinDetected) {
                Icon(Icons.Outlined.SportsEsports, null, Modifier.size(18.dp),
                    tint = SuccessGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.genshinInfo!!.label}  v${state.genshinInfo!!.versionName}",
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp),
                    tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text("未检测到原神", style = MaterialTheme.typography.bodySmall,
                    color = DangerRed)
            }
        }
    }
}

// ═══════════════════════════════════════════
// 核心 Vulkan 状态指示器(大圆 + 光环)
// ═══════════════════════════════════════════

@Composable
private fun VulkanHeroIndicator(state: AppUiState) {
    val vulkanOn = state.vulkanStatus.enabled

    // 光环旋转动画
    val ringRotate = rememberInfiniteTransition(label = "ring")
    val ringAngle by ringRotate.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ringAngle"
    )

    // 脉冲动画（Vulkan 开启时）
    val pulseScale by ringRotate.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // 光环
        if (vulkanOn) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = ringAngle }) {
                val half = size.width / 2
                drawArc(
                    color = VulkanRedLight.copy(alpha = 0.3f),
                    startAngle = -30f, sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - 8.dp.toPx(),
                        size.height - 8.dp.toPx()
                    )
                )
                drawArc(
                    color = Accent.copy(alpha = 0.2f),
                    startAngle = 150f, sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - 4.dp.toPx(),
                        size.height - 4.dp.toPx()
                    )
                )
            }
        }

        // 中心圆
        Surface(
            modifier = Modifier
                .size(if (vulkanOn) (100.dp * pulseScale) else 100.dp)
                .shadow(
                    elevation = if (vulkanOn) 12.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (vulkanOn) VulkanRedLight else StatusOff,
                    spotColor = if (vulkanOn) VulkanRed else StatusOff
                ),
            shape = CircleShape,
            color = if (vulkanOn) VulkanRed else StatusOff
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.detectingStatus) {
                    CircularProgressIndicator(
                        Modifier.size(40.dp),
                        color = Color.White, strokeWidth = 3.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (vulkanOn) "VK" else "GL",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = if (vulkanOn) "Vulkan" else "OpenGL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 外圈虚线装饰
        if (vulkanOn) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -ringAngle * 0.5f }
            ) {
                val half = size.width / 2
                for (i in 0..11) {
                    val angle = Math.toRadians(i * 30.0 + 15)
                    val cx = half + (half - 10.dp.toPx()) * kotlin.math.cos(angle).toFloat()
                    val cy = half + (half - 10.dp.toPx()) * kotlin.math.sin(angle).toFloat()
                    drawCircle(
                        color = AccentLight.copy(alpha = 0.5f),
                        radius = 2.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // 状态标签
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (vulkanOn) VulkanRed.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (vulkanOn) Icons.Outlined.Bolt else Icons.Outlined.InvertColors,
                null, Modifier.size(16.dp),
                tint = if (vulkanOn) VulkanRed else TextSecondary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "当前引擎: ${if (vulkanOn) "Vulkan" else "OpenGL ES"}",
                style = MaterialTheme.typography.labelLarge,
                color = if (vulkanOn) VulkanRed else TextSecondary
            )
            if (vulkanOn) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        state.vulkanStatus.method.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// 切换按钮
// ═══════════════════════════════════════════

@Composable
private fun ToggleSwitch(
    state: AppUiState,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    val enabled = state.genshinDetected && state.permissionType != PermissionType.NONE
    val vulkanOn = state.vulkanStatus.enabled

    // 按钮动画
    val btnScale by animateFloatAsState(
        if (state.isOperating) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f), label = "btnScale"
    )

    Button(
        onClick = { viewModel.toggleVulkan(context) },
        enabled = enabled && !state.isOperating,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer { scaleX = btnScale; scaleY = btnScale },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (vulkanOn) OpenGLBlue else VulkanRed,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = TextSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        if (state.isOperating) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                color = Color.White, strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("执行中...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(
                if (vulkanOn) Icons.Outlined.RestartAlt else Icons.Outlined.FlashOn,
                null, Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (vulkanOn) "切换到 OpenGL" else "一键切换到 Vulkan",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ═══════════════════════════════════════════
// 消息 Toast
// ═══════════════════════════════════════════

@Composable
private fun MessageToast(state: AppUiState, viewModel: MainViewModel) {
    Spacer(Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (state.lastSuccess)
            SuccessGreen.copy(alpha = 0.1f)
        else DangerRed.copy(alpha = 0.1f),
        onClick = { viewModel.clearMessage() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (state.lastSuccess) Icons.Outlined.TaskAlt else Icons.Outlined.Cancel,
                null, Modifier.size(18.dp),
                tint = if (state.lastSuccess) SuccessGreen else DangerRed
            )
            Spacer(Modifier.width(8.dp))
            Text(
                state.lastMessage,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ═══════════════════════════════════════════
// 设备信息卡片
// ═══════════════════════════════════════════

@Composable
private fun InfoCard(state: AppUiState, viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "设备信息",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { viewModel.toggleDetails() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.showDetails) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (state.showDetails) Icons.Outlined.ExpandLess
                            else Icons.Outlined.ExpandMore,
                            null, Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 始终显示的三行关键信息
            Spacer(Modifier.height(12.dp))
            InfoRow("📱", "设备", "${android.os.Build.BRAND} ${android.os.Build.MODEL}")
            InfoRow("🖥️", "GPU", state.vulkanStatus.gpuConfigured.ifBlank {
                com.example.genshinvulkan.device.DeviceInfo.gpuRenderer
            })
            InfoRow("⚙️", "芯片", com.example.genshinvulkan.device.DeviceInfo.chipset)

            // 展开后的详情
            AnimatedVisibility(visible = state.showDetails) {
                Column {
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    InfoRow("📦", "Android", "${android.os.Build.VERSION.RELEASE} " +
                            "(API ${android.os.Build.VERSION.SDK_INT})")
                    InfoRow("🗂️", "配置路径", state.genshinInfo!!.dataPath
                        .replace("/storage/emulated/0", "内部存储")
                    )
                    if (state.vulkanStatus.shaderCacheExists) {
                        InfoRow("💾", "着色器缓存", state.vulkanStatus.shaderCacheSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

// ═══════════════════════════════════════════
// 性能工具卡片
// ═══════════════════════════════════════════

@Composable
private fun ToolsCard(state: AppUiState, viewModel: MainViewModel) {
    val enabled = state.genshinDetected && state.permissionType != PermissionType.NONE

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("性能优化", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(12.dp))

            // 工具按钮网格
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolButton(
                    icon = Icons.Outlined.CleaningServices,
                    label = "清理缓存",
                    subtitle = if (state.vulkanStatus.shaderCacheExists)
                        state.vulkanStatus.shaderCacheSize else "无缓存",
                    enabled = enabled && !state.isOperating && state.vulkanStatus.shaderCacheExists,
                    onClick = { viewModel.clearShaderCache() },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Outlined.Bolt,
                    label = "DEX优化",
                    subtitle = "speed 编译",
                    enabled = enabled && !state.isOperating,
                    onClick = { viewModel.optimizeDex() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolButton(
                    icon = Icons.Outlined.Refresh,
                    label = "刷新状态",
                    subtitle = "重新检测",
                    enabled = enabled && !state.isOperating,
                    onClick = {
                        viewModel.refreshVulkanStatus()
                        viewModel.detectGenshinIfReady()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Outlined.Info,
                    label = "关于",
                    subtitle = "v1.0.0",
                    enabled = true,
                    onClick = { /* TODO */ },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null, Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else TextSecondary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                       else TextSecondary.copy(alpha = 0.5f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 0.7f else 0.4f
                )
            )
        }
    }
}

// ═══════════════════════════════════════════
// 防还原守护卡片
// ═══════════════════════════════════════════

@Composable
private fun GuardianCard(
    state: AppUiState,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    if (!state.genshinDetected) return

    val containerColor = when (state.guardianState) {
        GuardianState.Repaired -> SuccessGreen.copy(alpha = 0.1f)
        GuardianState.Intact -> SuccessGreen.copy(alpha = 0.08f)
        GuardianState.Failed -> DangerRed.copy(alpha = 0.1f)
        GuardianState.NeedPermission -> WarningOrange.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }
    val accent = when (state.guardianState) {
        GuardianState.Repaired, GuardianState.Intact -> SuccessGreen
        GuardianState.Failed -> DangerRed
        GuardianState.NeedPermission -> WarningOrange
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (state.guardianState) {
        GuardianState.Failed -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Shield
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(6.dp))
                Text("防还原守护", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (state.guardianState == GuardianState.Repaired
                    || state.guardianState == GuardianState.Intact
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.18f)
                    ) {
                        Text(
                            "运行中",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                }
            }

            if (state.guardianMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.guardianMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolButton(
                    icon = Icons.Outlined.Autorenew,
                    label = "重新应用",
                    subtitle = "强制重写配置",
                    enabled = state.genshinDetected
                            && state.permissionType != PermissionType.NONE
                            && !state.isOperating,
                    onClick = { viewModel.reapplyVulkan(context) },
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    icon = Icons.Outlined.Refresh,
                    label = "守护检测",
                    subtitle = "检查是否被还原",
                    enabled = state.genshinDetected
                            && state.permissionType != PermissionType.NONE
                            && !state.isOperating,
                    onClick = { viewModel.guardVulkan(context) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DiagnoseCard(
    state: AppUiState,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    if (!state.genshinDetected) return

    val risk = state.diagnoseState == DiagnosePhase.Done && state.diagnoseResult?.risk == true
    val containerColor = if (risk)
        DangerRed.copy(alpha = 0.1f)
    else
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val accent = if (risk) DangerRed else Accent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, null, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(6.dp))
                Text("诊断模式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (state.diagnoseState == DiagnosePhase.Done) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (risk) DangerRed else SuccessGreen).copy(alpha = 0.18f)
                    ) {
                        Text(
                            if (risk) "有风险" else "健康",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (risk) DangerRed else SuccessGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { viewModel.runDiagnose(context) },
                enabled = state.diagnoseState != DiagnosePhase.Running
                        && state.permissionType != PermissionType.NONE,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (state.diagnoseState == DiagnosePhase.Running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("诊断中…")
                } else {
                    Icon(Icons.Outlined.BugReport, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("运行诊断（检测配置是否被还原）")
                }
            }

            when (state.diagnoseState) {
                DiagnosePhase.NoPermission -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "请先授权 Shizuku / Root 再运行诊断。",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
                DiagnosePhase.Error -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "诊断失败，请确认本应用已能访问原神文件目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
                DiagnosePhase.Done -> {
                    state.diagnoseResult?.let { r ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            r.verdict,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (r.logcatHints.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "关键日志：",
                                style = MaterialTheme.typography.labelSmall,
                                color = Accent
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    r.logcatHints,
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> { /* Idle: 仅显示按钮 */ }
            }
        }
    }
}
