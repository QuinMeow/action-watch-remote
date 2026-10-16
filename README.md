# Action 手表遥控 0.2 原型

为 OPPO Watch 4 Pro + DJI Osmo Action 5 Pro 编写的普通 Android BLE 遥控原型。**还没有真实手表/相机验证，不保证可安装、可配对、可控制或能向素材写入GPS。** 不用于重要拍摄或危险运动。

## 功能
- 前台BLE扫描与相机端确认配对，开始/停止录像
- 模式切换：普通录像、慢动作、延时摄影、拍照、运动延时、低光视频（相机可能拒绝不支持的模式）
- 只有相机状态推送确认后才显示录像/模式改变；ACK不冒充执行成功
- 真实GPS开关：按需请求精确定位，实时读取GPS_PROVIDER + GGA NMEA，不读缓存旧位置、不默认开启
- GPS向当前相机发送位置/时间/高度/速度等，不上传互联网；无完整定位/旧fix/休眠/断连时停止发送
- 10Hz是发送上限，不等于真实GPS采样达到10Hz；新鲜样本可重复发送，保持原始测量时间，无插值/假定位
- 明确隔离的本机模拟：自写假相机、人工GPS坐标、拒绝/错误SEQ/仅ACK/丢包/暂停状态与GPS的可控注入

不做Wi-Fi实时监看，不用账号、GMS、Wear OS、后台服务或网络权限。离开应用即停止GPS并断开，**不会自动停止相机正在进行的录像**。

## 三种验证不能混淆
1. JVM协议/状态机模拟：运行生产协议、会话、GPS编码，对接我们自写的假相机。不是DJI官方相机模拟器，也不是Android UI
2. Android模拟器：运行实际APK和界面（本次实际结果见 docs/BUILD-RESULTS.md）；没有模拟出真实相机BLE或手表GPS
3. 真机：OPPO手表、DJI相机、固件、真实GPS、Mimo素材GPS记录均待设备就位后验证

官方仓库有帧检查、连接帧构造及需ESP32/BLE的GPS回放示例，没有找到完整软件相机模拟器。我们生成的模式/GPS帧可用官方工具交叉校验，结果见 docs/OFFICIAL-FRAME-CHECK.txt。

## 关键限制
- Android普通APP不能读取真实本机蓝牙MAC，配对身份使用持久化随机标识，能否被相机接受待验证
- 官方示例device_id常量/字节序有不一致，本原型使用相机握手回传的原始四字节控制，需硬件验证
- GPS必须同时获得真实、非mock的Android定位和GPGGA/GNGGA NMEA。没有NMEA、速度或精度字段时宁可不发，不以0伪装缺失字段
- GGA提供MSL高度；Android椭球高没有冒充MSL。垂直速度由真实MSL高度差分估算，有噪声；Android垂直精度只作为接收机不确定性估计，非已验证的MSL误差界
- 日期时间采用完整UTC+8换算并跨日归一；官方样例直接hour+8存在跨日歧义，真实相机时间语义仍待核实
- 前台应用保持屏幕亮起，真实功耗尚未测试，并非已优化的长续航方案。普通APK能否在OPPO固件侧载、GATT分片/UI及所有功能均须真机测试

## 开发与复现
JDK17+：`./test.sh`。运行基础协议、GPS、模拟联调三组测试。
Android：minSdk26、target/compileSdk35、Gradle8.9、AGP8.7.3；设置JAVA_HOME和ANDROID_HOME，运行 `./gradlew :app:assembleDebug :app:lintDebug`。Wrapper校验值固定；SDK/完整Gradle/JDK/缓存/密钥不随源码交付。

- [安装与真机测试](docs/INSTALL-TEST-ZH.md)
- [本机模拟指南及视频](docs/SIMULATION-ZH.md)
- [Android小屏实际验收](docs/EMULATOR-REPORT.md)
- [协议选择与来源](docs/PROTOCOL.md)
- [许可边界](THIRD-PARTY-NOTICES.md)

自写内容MIT；官方协议资料及相关条款并未被重新许可。非DJI或OPPO官方产品。

- [Android 模拟器验收报告与录屏](docs/EMULATOR-REPORT.md)
