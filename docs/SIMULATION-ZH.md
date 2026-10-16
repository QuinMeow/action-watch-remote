# 本机模拟与调试

## 不需要手表/相机的JVM模拟
准备JDK17后在源码目录执行：

```
./test.sh
```

它运行真实生产Protocol、RemoteSession、GpsData/GpsSourceRules代码，对接自写SimulatedCamera。人工坐标只存在于测试和显式本机模拟。无需网络、BLE或实际定位。

覆盖：配对、模式确认、拒绝、仅ACK不改变状态、错误SEQ、超时、录像中禁止切模式、GPS10Hz发送限速（真实样本仅1Hz）、背压丢弃、陈旧GPS、断连、垂直速度10Hz采样、日期跨年/闰日/月底、GGA校验和/质量/坐标/时间匹配。

## APK内本机模拟
1. 不连接真实相机，点击“连接模拟设备”
2. 顶部必须持续出现“模拟设备”
3. 点击“模式：…”，观察先等待、后状态确认；模拟录像按钮同样要求状态确认
4. 点击“开启模拟GPS”：使用固定人工测试位置，每秒一个样本，最多每秒发送10帧至假相机；不会申请真实定位或发送BLE
5. 打开“模拟测试”：下一条控制可拒绝、只回ACK、丢包或错SEQ；可暂停相机状态/人工GPS样本
6. 暂停GPS样本超过2秒应停止增加GPS帧；暂停状态后控制禁用、最终断连
7. 退出或断开会清掉模拟状态。要测试真实相机，必须退出模拟再手动扫描连接；不会将模拟坐标转发到真实BLE

本机模拟不证明真实硬件兼容，假相机也不能证明真实固件接受相同指令。

## Android系统模拟器
官方Android SDK Emulator可运行普通Android11/API30 AOSP镜像，不需要GMS。小屏目标378×496。无KVM时使用CPU软件翻译，较慢。真实执行结果、截图和限制见BUILD-RESULTS.md及模拟器报告（若存在）。

模拟器注入的mock Location不能冒充真实GPS：真实模式会拒绝mock标记；演示GPS应使用应用的显式假设备通道。Android模拟器并不替代OPPO蓝牙栈、硬件GNSS、相机配对或素材写入测试。

## 官方SDK工具边界
DJI官方Osmo-GPS-Controller-Demo的test/frame_check检查帧结构和CRC；test/connect_cmd_frame_builder构造配对报文；test_gps.c是硬件BLE回放示例。它们不是完整相机软件仿真环境。官方工具校验通过只说明帧可解析/CRC一致，不证明摄像机执行或GPS入片。

## 本次真实Android模拟器截图
下列图片由实际运行0.2 APK的Android11/API30模拟器截取，不是设计稿或HTML页面。画面里的相机/GPS是应用明确标记的本机假设备。

- [主界面与模拟连接](screenshots/final-240/02-ready.png)
- [模式切换已收到状态确认](screenshots/final-240/06-mode-confirmed.png)
- [模拟GPS：1Hz样本、10Hz发送上限](screenshots/final-240/07-gps-sending.png)


## 小屏与触控验收配置
主验收为378×496像素、density240，约252×331dp；另用density320（约189×248dp）做更紧凑的压力验证。两者都不是已确认的OPPO实际系统density，设备到位后需读取 `adb shell wm density` 复核。

所有常规按钮设定最小48dp，录像主按钮64dp。内容左右及上方18dp、底部24dp内边距，并叠加系统状态栏/导航栏insets；使用ScrollView使下面的断开/测试入口滚动可达；离线首屏直接显示连接方式，连接后显示控制。触控几何以按钮完整滚入视口后的uiautomator bounds为准，半截滚动项不当作完整点击目标。

当前Android模拟器是矩形视口，没有复制OPPO面板的实际圆角、触控边缘或厂商系统手势。验证包含预留安全边距和边界/滚动检查；物理圆角仍需真机确认。最终视频/截图及通过项见EMULATOR-REPORT.md。

界面文案采用短状态与直接操作提示：模拟设备、定位有效、等待定位、模式、开启/关闭GPS。兼容与协议验证边界保留在说明文档，不堆在主界面。

## 操作视频
- [完整240dpi屏幕录制，8分56秒](recordings/action-watch-remote-240dpi.mp4)
- [关键操作剪辑，约1分43秒](recordings/action-watch-remote-highlights-edited.mp4)：删除等待时段，各片段保持原速度；剪辑区间见同目录 highlights-edit-list.json
