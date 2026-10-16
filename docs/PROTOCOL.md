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
