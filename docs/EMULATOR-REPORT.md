# Android Emulator 实际运行验收记录

日期：2026-10-16 UTC。实际启动 Android 系统模拟器并安装 APK；本报告不包含实体手表或相机测试。

## APK 身份

- 包名：`dev.actionwatch.remote`；versionCode 2，versionName `0.2-prototype`
- 最终 APK SHA-256：`bf6f822d5f987e31ec5b3e5f5ce13a5d96b3ba5409c6d16646681fdb376e153d`
- 验收结束时读取模拟器内已安装 `base.apk` 并计算 SHA-256，与交付 APK 一致
- 最终运行后读取 Android crash buffer 为空；未观察到该应用崩溃。这不替代长期稳定性测试

## 环境与覆盖边界

Google 官方 Android Emulator 37.2.12，AOSP Android 11 / API 30，x86_64，无 Google Play Services。云端 Linux 没有可用 KVM；CPU 使用 `-accel off` 软件翻译，GPU 使用 SwiftShader，2 个虚拟 CPU，1536MB RAM。

实际虚拟屏幕为 **378×496 像素**。物理 density **240dpi**，另以 `wm density 320` 做紧凑布局压力测试。OPPO Watch 4 Pro 的实际运行 density 尚未获真机确认。保留 Android 状态栏，配置硬件主按键，因此没有底部导航栏。这里只验证矩形屏幕中的边距、触控尺寸及滚动可达，未模拟 OPPO 物理圆角、手势、省电策略或蓝牙芯片。

所有相机和 GPS 演示使用应用内“模拟设备”通道。没有连接实体相机；未验证真实 BLE 配对、真实 GPS/NMEA、相机落盘、镜头录制、OPPO 安装兼容性，以及 API 31+ 的蓝牙运行时权限分支。界面中的 GPS 帧数是模拟发送计数，不是相机收包或保存证明。

## 最终 APK 已执行的交互

| 项目 | 结果与证据 |
|---|---|
| 240dpi 离线入口 | 扫描、连接模拟设备首屏可见；`final-240/01-start.png` |
| 模拟连接与录制 | 连接成功，开始/停止录制按钮状态变化；`02-ready.png`、`03-recording.png` |
| 六种模式列表 | 上下滚动可到达全部六项；选择低光视频后主界面确认；`04-mode-menu.png` 至 `06-mode-confirmed.png`。未逐个切换全部六项 |
| 模拟 GPS | 开启后定位有效、1Hz 采样、10Hz 发送上限及帧数增长；`07-gps-sending.png`、`08-bottom.png` |
| 断开与重连 | 断开后返回连接入口；重连后普通录像、GPS 关闭；`09-disconnected.png`、`10-reconnected-bottom.png` |
| 退出与弹窗清理 | 打开模拟测试菜单后按 Home，再返回应用：菜单消失，连接已断开；`11-test-menu.png`、`12-return-menu-closed.png` |
| 定位权限拒绝 | 扫描触发 Android 原生定位权限请求，滚动找到拒绝并点击；应用显示可再次授权提示，没有崩溃；`13-permission-dialog.png` 至 `15-permission-denied.png` |
| 320dpi 首屏及控制区 | 连接入口可见；录制、模式、GPS 可滚入视口完整显示；`final-320/01-start.png` 至 `03-controls-scrolled.png` |
| 320dpi 模式列表 | 滚至末项，低光视频完整可见并可选中；`04-mode-menu.png` 至 `06-low-light-confirmed.png` |
| 320dpi GPS 与底部 | GPS 计数从截图 07 的 99 帧增长到截图 08 的 303 帧；测试、断开按钮和底部两行提示均可完整滚入视口；`07-gps-bottom.png`、`08-bottom-complete.png` |

240dpi 已执行断开/重连和退出清理；320dpi 验证布局及底部按钮可达，未重复所有生命周期动作。最终候选没有逐项重跑全部故障注入组合、GPS 暂停后过期停发或单独点击 GPS 关闭按钮；这些不能由本次截图和录像宣称通过。代码侧 181 项 JVM 回归结果由构建报告另行记录，不等同于真实硬件验证。

## 触控尺寸与安全边距

使用 Android UIAutomator 只读获取实际 Accessibility 控件 bounds，原始 XML 位于 `docs/ui-geometry/`。高度以控件完整滚入视口为准，滚动裁切后的可见片段不能当作完整触控尺寸。按钮背景的可见色块比触控 bounds 小，以下测量使用真实控件 bounds。

| 完整可见控件 | 240dpi | 320dpi |
|---|---:|---:|
| 连接模拟设备、扫描 | 72px = 48dp | 96px = 48dp |
| 开始/停止录像 | 96px = 64dp | 128px = 64dp |
| 模式、GPS | 72px = 48dp | 96px = 48dp |
| 模拟测试、断开连接 | 72px = 48dp | 96px = 48dp |
| 320dpi 低光视频列表项 | — | 96px = 48dp |

示例完整 bounds：240dpi 录制 `[27,235][351,331]`，模式 `[27,331][351,403]`；320dpi 录制 `[36,57][342,185]`，模式 `[36,185][342,281]`，GPS `[36,281][342,377]`；320dpi 滚到底的测试 `[36,168][342,264]`、断开 `[36,264][342,360]`。

左右安全边距分别为 27px（240dpi）和 36px（320dpi），即 18dp。顶部结合系统状态栏保留间距，底部提示可完整滚入，底部保留 24dp 设计间距。小屏需要滚动是预期行为；真实手表圆角区域尚需真机确认。

## 录像与截图

以下媒体已单独交付。此次 GitHub 源码提交不包含截图或录像文件；下列文件名是验收资产标识，不是仓库内下载路径。

截图为官方 emulator console 对真实 Android 屏幕截取的 PNG。录像由同一 console 录制 WebM，再本地转为 H.264 MP4；保持 **378×496**，不裁切、不缩放、片段内不变速。录制目标 10fps，无音频；软件模拟的速度、等待和视频帧率不能用于证明真机性能。

- `recordings/action-watch-remote-240dpi.mp4`：完整连续录屏 **536.000 秒（8分56秒）**，包含操作间的等待
- `recordings/action-watch-remote-highlights-edited.mp4`：**关键操作剪辑 102.901 秒（约1分43秒）**，删除等待，保留每段原速度；不是连续实时演示。剪辑点见 `highlights-edit-list.json`
- `recordings/action-watch-remote-320dpi-part1.mp4`：独立连续录屏 **360.000 秒（6分钟）**，录制达到上限后停止
- `recordings/action-watch-remote-320dpi-part2.mp4`：后续独立连续录屏 **78.100 秒（1分18.1秒）**，覆盖低光选择、模拟 GPS 计数增长和底部滚动。两段之间有未录制间隔，不将其伪称为一次连续录屏
- 最终截图仅使用 `screenshots/final-240/` 的 15 张和 `screenshots/final-320/` 的 8 张；项目其他旧截图不属于这个 APK 的最终验收证据，不应混入交付包

## 环境异常

首次 AVD 启动、尚未安装应用时，Android 系统 DeviceStorageMonitorService 曾空指针崩溃，系统重启服务后完成启动；System UI 曾出现无响应弹窗，选择等待后恢复。等待后续构建期间模拟器进程收到 SIGKILL，shell 退出码 137；没有证据确认原因，不认定为应用崩溃或确定的 OOM。随后原 AVD 成功重启，完成本报告的最终 APK 运行。验收结束时 boot_completed=1，ADB 可用。

官方工具参考：[启动参数](https://developer.android.com/studio/run/emulator-commandline)、[模拟器加速说明](https://developer.android.com/studio/run/emulator-acceleration)。
