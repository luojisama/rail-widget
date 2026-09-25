# 铁路行程助手 (RailWidget)

基于 12306 官方票务邮件与短信智能解析的 Android 桌面小部件应用，专为经常乘坐高铁路网出行的旅客设计。

---

## 核心特性

- **三种密度桌面小部件 (MIUI 12+ / HyperOS 深度适配)**：
  - **2×2（磁贴模式）**：紧凑呈现车次号、检票口高亮标签、出发时间与席位。
  - **4×2 / 2×4（横幅卡片）**：全景行程横卡，显示始发到站、出发/到达时刻、运行状态（候车中/正在检票/停止检票）及检票口。
  - **4×4（全景看板）**：包含完整途经停靠站时间轴、发车倒计时、多车票切换及右上角内置 **⟳ 一键刷新同步** 按钮。
- **双通道智能同步**：
  - **Cloud Mail 自建邮箱通道**：原生对接基于 Cloudflare Workers 的 Serverless 开源 Cloud Mail（通过 REST API 交互并缓存 JWT 鉴权）。
  - **通用 IMAP 通道**：标准 JavaMail/TLS 协议栈，全面支持 QQ 邮箱、163 邮箱、Gmail、Outlook 等。
  - **短信广播与快速导入**：自动拦截来自 12306 的购票短信，同时支持在 APP 内一键粘贴识别任意格式的 12306 通知。
- **12306 官方公开接口联动**：
  - 自动通过 `search.12306.cn` 与 `kyfw.12306.cn/otn/czxx/queryByTrainNo` 查询官方公开列车时刻表。
  - 自动补全精确到站时间（如 C315 普洱站 18:38 到）、停靠时长、沿途经停站列表。
- **免手搓 UI**：
  - 全量采用 Google 官方 **Material 3** 现成规范组件（`ElevatedCard`、`ListItem`、`FilterChip`、`TopAppBar`、`ExtendedFloatingActionButton`），视觉质感高级原生。
- **本地历史存储**：
  - 本地 SQLite 持久化保存所有车票行程，支持查看历史、归档与删除。

---

## 工程构建

```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Debug APK
./gradlew assembleDebug
```
产物输出路径：`app/build/outputs/apk/debug/app-debug.apk`。
