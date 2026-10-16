# 0.2 构建与验证记录

日期：2026-10-16 UTC。候选APK versionCode2 / versionName0.2-prototype。

## 已通过
- JDK17聚合检查181/181：32基础协议、106GPS编码/校验、43假相机与状态机联调
- 独立复核者重跑同一聚合脚本181/181，模式确认、模拟隔离、GPS隐私/生命周期和背压审查无剩余阻断
- 发现并修复：10Hz样本导致高度差分永不成功；Location先于NMEA导致永远缺GPS。回调双向匹配、测量时间去重、200ms高度基准的实现已复核
- Android35编译、Gradle assembleDebug、lintDebug通过
- DJI官方frame_check工具交叉检查模式与GPS帧，SOF/长度/CRC16/CRC32均通过，见OFFICIAL-FRAME-CHECK.txt
- APK v2签名验证通过；与0.1相同签名证书，versionCode升为2
- Lint：0错误、2警告。1条API31蓝牙属性对低API忽略的预期提示；1条中文原型文本拼接的国际化提示。不是运行成功证明

## Android模拟器
实际Android11/API30 AOSP软件模拟器在无KVM的云环境运行最终APK，378×496像素。240dpi已跑通模拟连接、录像/停止、模式菜单滚动与切换、模拟GPS、断开重连、Home返回清理、定位权限拒绝。完整录屏约8分56秒，未加速或裁切；320dpi压力验证也通过模式列表末项、低光状态确认、GPS及断开入口滚动可达。独立视觉审查通过；普通按钮48dp、录像64dp。精确配置及几何记录见EMULATOR-REPORT.md。

## 未验证
- OPPO Watch 4 Pro安装、蓝牙栈、权限与真实屏幕
- Action5 Pro配对身份、device_id选择、实际模式/录像执行
- 手表是否提供完整GPS/GGA/速度精度、真实采样率与功耗
- 相机是否接收并写入GPS、UTC+8实际解释、Mimo素材呈现
- 真实睡眠/远距离/满卡/过热/低电量

JVM假相机不是Android UI，Android模拟器也不是手表/相机。仅源码/帧校验不能证明真实设备兼容。

## 工具
JDK17 AWS Corretto，Gradle8.9，AGP8.7.3，Google SDK Platform35 / Build Tools34。工具来自官方来源；Android SDK条款已由用户明确同意：https://developer.android.com/studio#terms

复现：设置JAVA_HOME/ANDROID_HOME，运行 `./test.sh` 和 `./gradlew :app:assembleDebug :app:lintDebug`。本次无GitHub发布动作由此工程任务执行；源码同步由主任务在最终验证之后管理。
