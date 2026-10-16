# 许可与来源告知

本交付中的 Java 应用、测试和说明均为本次新写，适用根目录 MIT LICENSE。没有复制 PANDAJSR/OSMO-Remote 或其他无明确许可证项目的源码，没有捆绑 DJI 官方 C 实现、其 CRC 表、原始文档、图像或固件。

协议字段与校验算法参数研究自 DJI 官方公开仓库：
https://github.com/dji-sdk/Osmo-GPS-Controller-Demo

DJI EULA 原文：https://developer.dji.com/policies/eula/

上游 LICENSE 同时涉及文档 EULA、Demo EULA、MIT；部分源文件也有额外权利声明。请查看原文：
https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/LICENSE

本项目的 MIT 只覆盖我们有权许可的自写内容，**不承诺 DJI 协议/API 条款不适用，也不作商业使用、再分发或协议权利的法律保证**。公开发布或商用前应向 DJI 确认相关协议与条款边界。测试中一条短十六进制帧作为官方固定互操作向量使用，其出处见 PROTOCOL.md。

Android 平台及构建工具、Gradle、Android Gradle Plugin、JDK 有各自许可。它们不是本项目 MIT 许可对象。构建使用 Google 官方 SDK（本次用户已同意其工具条款），Gradle 官方发行版、AWS Corretto JDK17；源码包不包含SDK、JDK或完整Gradle发行版；仅附官方Gradle8.9 Wrapper及其脚本，采用Apache-2.0，原许可与NOTICE保存在licenses/。Wrapper未改动，下载分发包的SHA256已固定。应用不引用第三方 Android UI 或 BLE 库。

DJI、Osmo、OPPO 等名称和商标归各自权利人所有；本原型没有官方合作、认证或支持承诺。

## Material Icons（0.3起）

设置、定位、箭头、录制及停止图标来自Google官方Material Icons，Apache-2.0许可。由SVG机械转换为Android VectorDrawable，几何路径未改，填充色改为运行时tint。逐文件来源见licenses/Material-Icons-SOURCES.md，完整许可见licenses/Material-Icons-LICENSE.txt。这些资源不适用本项目MIT许可。
