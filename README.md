# Action 手表遥控 0.1 原型

为 OPPO Watch 4 Pro + DJI Osmo Action 5 Pro 编写的普通 Android BLE 遥控原型。**没有真实手表或相机测试，不保证可安装、可配对或可录像。** 可审查源码，并用空白测试片验证后再考虑使用；不要用在重要拍摄、危险运动或不能失误的场景。

## 当前功能
- 前台扫描 BLE 设备，由用户选择；校验 FFF0/FFF4/FFF5 服务
- R SDK 0xAA 帧、CRC16/CRC32、分片重组、损坏包恢复
- 相机端确认配对码；拒绝后断开，不自动反复配对
- 相机状态订阅及电量、模式、时长显示
- 大按钮开始/停止录像；重复点击锁定；只有新鲜相机状态确认才显示录像中
- 超时/断连/权限撤回反馈。离开应用即断开，**不会自动停止相机录像**

没有实时画面、模式切换、GPS发送、唤醒、后台自动录像、云上传、账号或联网权限。不依赖 Wear OS 或 Google Play Services，无原生 ABI 依赖。Android minSdk 26，target/compileSdk 35。

## 最重要的未验证项
1. Android 普通应用不能读取本机真实蓝牙 MAC。本原型把安装时生成并保存在本机的随机标识放入配对身份字段。这不是已证实的 DJI 兼容方案，**可能使配对失败**；不会读取/伪装为其他设备的身份。卸载会丢失标识。
2. 官方示例对 device_id 的常量/字节序存在不一致。本原型保留相机主动 0019 消息的四个原始字节用于控制，**这属于需硬件核实的实现选择，不是文档保证**。
3. FFF5 无响应写的回调、MTU、分片和配对流程必须在 OPPO 系统与具体相机固件上验证。
4. 未测试屏幕渲染、系统安装限制、手表省电策略和相机休眠。

## 使用与开发
- 安装和真机核对：[docs/INSTALL-TEST-ZH.md](docs/INSTALL-TEST-ZH.md)
- 协议选择和来源：[docs/PROTOCOL.md](docs/PROTOCOL.md)
- 许可边界：[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)
- 测试记录：[docs/JVM-TEST-RESULTS.txt](docs/JVM-TEST-RESULTS.txt)

JDK17+ 执行 `./test.sh`，无需 Android SDK。Android 项目用 Gradle8.9、AGP8.7.3、Android SDK35；安装构建工具前须接受工具自身许可。设置 ANDROID_HOME 后运行 `./gradlew :app:assembleDebug :app:lintDebug`。本项目不提交SDK、完整Gradle发行版或私人调试密钥；附官方Gradle Wrapper与校验值。

自写源代码采用 MIT；DJI 协议资料、品牌和相关条款不因此被重新许可。本项目非 DJI 或 OPPO 官方产品。

## 范围说明
按本次需求，不做 Wi-Fi 实时监看，以免增加手表耗电。官方 R SDK 另有模式切换和 GPS 数据推送能力，但本版未实现；不能从“能读取状态”推断任意画质、ISO 或帧率参数可写。后续功能需要单独确定需求并验证。
