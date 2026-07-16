# 纳西妲·原神渲染引擎切换工具 🍃

[![Build APK](https://github.com/naxida-ima/genshin-vulkan-tool/actions/workflows/build.yml/badge.svg)](https://github.com/naxida-ima/genshin-vulkan-tool/actions/workflows/build.yml)

一键切换原神的渲染引擎 —— 从 OpenGL 到 Vulkan，提升帧率稳定性、降低功耗。

## 功能一览

| 功能 | 说明 |
|------|------|
| 🔄 **一键切换 Vulkan/OpenGL** | 自动检测设备 GPU，选择最优配置方法 |
| 📱 **设备自动检测** | GPU 型号、芯片平台、设备型号全自动识别 |
| 🎮 **原神版本感知** | 自动适配 5.2 / 6.0 / 新版三种配置方法 |
| 🧹 **着色器缓存清理** | 一键清理 UnityVulkanPSO 缓存 |
| ⚡ **DEX 编译优化** | 通过 speed 模式优化原神 DEX |
| 🛡️ **Shizuku + Root 双通道** | 无需 root 也能用（Shizuku）|

## 为什么选择 Vulkan？

- Vulkan 对多线程利用更好，功耗优化更优
- 帧率更稳定，减少掉帧和卡顿
- 尤其对骁龙 8 系、天玑 9000+ 芯片效果明显

## 权限方案

| 方案 | 要求 | 推荐度 |
|------|------|--------|
| **Shizuku** | 安装 Shizuku App，无需 Root | ⭐⭐⭐ 首选 |
| **Root** | 设备已 Root | ⭐⭐ 备选 |

## 安全声明

- ✅ 只修改米哈游官方预留的本地配置文件
- ✅ 不触碰游戏二进制、内存、反作弊系统
- ✅ 着色器缓存清理 / DEX 优化均为 Android 标准操作
- ✅ 不会导致封号

## 构建

```bash
# 1. 用 Android Studio 打开项目目录
# 2. Sync Gradle
# 3. Build → Build APK
```

或在命令行：

```bash
./gradlew assembleRelease
# APK 输出：app/build/outputs/apk/release/app-release.apk
```

## 项目结构

```
genshin-vulkan-tool/
├── app/src/main/java/com/example/genshinvulkan/
│   ├── MainActivity.kt              # 入口 Activity
│   ├── permission/
│   │   └── PermissionHelper.kt      # Shizuku + Root 统一权限
│   ├── device/
│   │   └── DeviceInfo.kt            # GPU/设备/芯片检测
│   ├── config/
│   │   ├── ConfigManager.kt         # 配置读写核心
│   │   └── MainViewModel.kt         # 状态管理
│   └── ui/
│       ├── MainScreen.kt            # Material 3 UI
│       └── theme/Theme.kt           # 纳西妲绿主题
└── app/build.gradle.kts             # 依赖配置
```

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Shizuku API 13.1.5
- Gson JSON 解析
- Shizuku shell 进程 / Root su 执行
