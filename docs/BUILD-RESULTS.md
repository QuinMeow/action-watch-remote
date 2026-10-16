# 0.3 构建与验证记录

日期：2026-10-16 UTC。候选APK versionCode3 / versionName0.3-prototype。

## 已通过
- JDK17聚合检查199/199：32基础协议、106GPS编码/校验、43假相机与状态机联调、18Debug门控
- 独立复核者重跑同一聚合脚本199/199，模式确认、模拟隔离、GPS隐私/生命周期和背压审查无剩余阻断
- 发现并修复：10Hz样本导致高度差分永不成功；Location先于NMEA导致永远缺GPS。回调双向匹配、测量时间去重、200ms高度基准的实现已复核
- Android35编译、Gradle assembleDebug、lintDebug通过
- DJI官方frame_check工具交叉检查模式与GPS帧，SOF/长度/CRC16/CRC32均通过，见OFFICIAL-FRAME-CHECK.txt
- APK v2签名验证通过；与0.2相同签名证书，versionCode升为3
- Lint：0错误、5警告。1条API31蓝牙属性对低API忽略的预期提示；3条中文原型文本拼接的国际化提示；1条官方齿轮矢量路径较长的性能提示（保留官方几何）。不是运行成功证明

## Android模拟器

0.3实际界面验收见 [ui-v03/REPORT.md](ui-v03/REPORT.md)。0.2历史截图与结论不替代本次新版验证。

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
