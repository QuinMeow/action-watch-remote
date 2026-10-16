# 协议实现说明与可核查来源

查阅日期：2026-10-16。以下是自行撰写的实现摘要，非官方文档副本。

官方来源：
- 帧格式：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol.md
- 配对、录像、状态：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md
- GATT：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/ble/ble.c
- CRC参数：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/tree/main/utils/crc
- 握手流程：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/logic/connect_logic.c
- 示例控制：https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/logic/command_logic.c

帧以 AA 开始，不是旧 DUML 55。小端序；帧总长10位，版本6位。头CRC16用反射多项式A001/初值3AA3/无终值异或；尾CRC32用反射多项式EDB88320/初值3AA3/无终值异或。CRC16覆盖前10字节，CRC32覆盖除自身之外整帧。编码与校验采用自行实现的逐位算法，未复制官方查表代码。

测试固定向量来自官方 protocol_data_segment.md 的模式切换示例：总长27、SEQ5、CRC16 EE57、CRC32 D0E1FAF4。只用来验证帧/CRC，不意味着应用已提供模式切换功能。

服务FFF0；FFF4订阅通知；FFF5无响应写。应用通过CCCD启用通知，尝试MTU185，若不支持则使用默认MTU23并按MTU-3分片。写入排队、等Android写完成回调再发下一块，有3.5秒写超时。不能把这个回调当作相机执行成功。

0019：首次强制verify_mode1，显示四位随机码。相机verify_mode2/verify_data0才允许；非0立即断开。应答用相同SEQ和本机应用ID。配对总等待45秒。Android不能取得真实本机MAC，所以身份字段使用本地随机标识是实验性选择。

1D05：push_mode3/push_freq20；1D02最少38字节才更新界面。状态3只在已知视频模式才标成录像中，照片状态3不混同录像。未知/照片/直播模式不允许录像按钮。3.5秒没有新状态即禁用按钮，随后8秒未恢复断开。

1D03：开始0，停止1；相机原始device_id四字节用于控制。命令SEQ、CmdSet、CmdID和响应标志均要匹配ACK。ACK成功只表示应答；后续状态才确认录制/停止。6秒超时则显示结果未知并等待新状态，**不重发或反向补偿**，避免错误启停。

序号16位回绕；接收重组有长度上限、头校验、尾校验、2秒残帧超时。只有前台连接，没有自动重连、后台任务、GPS位置读取或网络数据传输。

## 0.2：模式与GPS

1D04：9字节载荷，4字节握手相机ID原样、目标mode、4个0保留字段。六个已知模式在界面可选；录像/预录/睡眠或状态不新鲜时禁用切换。只有1D02实际mode匹配才确认；SEQ匹配的ACK成功仍等待状态，拒绝/6秒超时不虚报成功。官方示例的硬编码device_id和非0reserved不一致仍需硬件核验，不以QS模拟替代直接切换。

0017：48字节GPS载荷、CmdType0，无执行/素材保存ACK。整数LE、速度float32 LE。发送日期和时间从原始测量UTC换算完整UTC+8，跨年/月/闰日进位；官方示例直接hour+8的问题见 https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/issues/23 ，本选择不是相机固件语义保证。

GPS最大10Hz发送，真实采样受设备控制。重复新鲜样本保持测量时间和数值，不插值。以elapsedRealtime计量，超过2秒停止；控制命令pending/队列忙时丢弃本次GPS槽，不补发、不积压。睡眠、断开和离开应用停止。

真实GPS仅GPS_PROVIDER，拒绝Android mock位置。必须有校验有效的GPGGA/GNGGA、有效定位质量1–5、真实卫星数与MSL海拔，以及Android位置、速度、方向（速度非0时）和精度字段。NMEA与Location的UTC epoch时间/日内时间在50ms内且坐标一致，NMEA接收单调年龄<=2秒；两回调均尝试匹配，不依赖先后顺序。GGA无效立即暂停；不以0/NaN代替缺失数据。

MSL高度来自GGA，不使用Android椭球高冒充；垂直速度用至少200ms的MSL高度差分，10Hz样本之间保持最近<=1秒的导数估计，数据间隔>5秒重置。该估算有噪声，Android垂直精度只作为接收机估计误差，不保证涵盖MSL/大地水准面误差。

官方 Android API：
- https://developer.android.com/reference/android/location/Location
- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/reference/android/location/OnNmeaMessageListener

纯本机模拟对接自写SimulatedCamera，所有GPS为明确的人工fixture；真实连接分支不能启用假相机/假GPS，模式切换需先断开。官方帧工具只校验格式/CRC，不能证明真实BLE或相机执行。
