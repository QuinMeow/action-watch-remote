# 构建与验证记录

日期：2026-10-16 UTC

## 通过
- JDK17 纯JVM协议/状态机检查：32/32
- 官方 Android35 android.jar 直接Java编译
- Gradle8.9 + Android Gradle Plugin8.7.3：assembleDebug
- Android lintDebug：0错误（警告详情见 LINT-RESULTS.txt）
- 最终调试 APK 签名验证，Android API26–35包信息检查
- XML格式检查；独立源码审查后修复扫描回调、撤权清理、无障碍、状态重置及系统栏布局

## 不代表通过
- 未运行Android模拟器或实际屏幕UI，没有设备截图
- 未安装到OPPO Watch 4 Pro
- 未连接Action5 Pro，未验证配对身份、真实BLE写入、相机启停
- 未测试设备休眠/断连/温度/满卡/电量/厂商权限管理

当前云环境没有模拟器、KVM或已连接ADB设备。APK可构建与签名有效并不保证手表允许安装，也不证明相机兼容。

## 构建环境
- AWS Corretto JDK17（官方发行，SHA256验证）
- 官方 Gradle8.9，Wrapper固定发行包SHA256
- Google官方SDK Platform35、Build Tools34
- Android SDK条款在用户明确同意后接受：https://developer.android.com/studio#terms

源码复现：设置本地JAVA_HOME、ANDROID_HOME，在项目目录执行 `./test.sh` 和 `./gradlew :app:assembleDebug :app:lintDebug`。首次需网络下载工具/依赖及适用许可接受。不包含云机器配置、SDK、缓存、local.properties或调试密钥。

APK为开发用调试签名，不是Google Play发行包；密钥未附在交付包。它不含网络权限或后台服务。Gradle Wrapper自身是附许可的官方第三方构建组件。
