# 铁行卡片 (RailCard)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/luojisama/rail-widget?color=005ac1)](https://github.com/luojisama/rail-widget/releases)

基于 12306 官方票务邮件与短信智能解析的现代 Android 桌面行程小部件应用，专为经常乘坐高铁路网出行的旅客设计。

---

## 核心特性

- **多尺寸桌面小部件（完美自适应 4 列与 5 列网格）**：
  - **车票磁贴 (2×2)**：紧凑呈现车次号、检票口高亮标签、出发时间与席位。
  - **行程横卡 (4×2 / 5×2 / 2×5)**：通栏车票卡片，显示始发到站、出发/到达时刻、运行状态（候车中/正在检票/停止检票）及检票口，采用 Material 3 票面视觉质感。
  - **全景看板 (4×4 / 5×4 / 4×5)**：包含规整途经停靠时刻表、发车倒计时及内置 **⟳ 一键刷新同步** 按钮。
- **Android App Shortcuts（快捷方式长按呼出）**：
  - 在手机桌面长按「铁行卡片」应用图标，无需进入系统小部件中心，直接在弹出菜单中一键添加小部件或同步短信。
- **三阶段行程分类管理**：
  - **未出行**：即将出发车次置顶，突出检票口高亮卡片与发车时刻；
  - **在途中**：列车发车后自动切换为运行中状态，展示在途行驶进度；
  - **已结束**：行程到站后自动整理归档至历史记录。
- **双通道智能同步与短信读取**：
  - **系统短信读取**：一键读取本地 12306 购票短信，智能补全出发站、时刻与电子客票详情；
  - **Cloud Mail 自建邮箱通道**：原生对接基于 Cloudflare Workers 的 Serverless 开源 Cloud Mail（通过 REST API 交互并缓存 JWT 鉴权）；
  - **通用 IMAP 通道**：标准 JavaMail/TLS 协议栈，全面支持 QQ 邮箱、163 邮箱、Gmail、Outlook 等。
- **12306 官方时刻表联动**：
  - 自动通过 `search.12306.cn` 与 `kyfw.12306.cn` 查询列车时刻表，自动补全精准到站时间、经停站与检票口。
- **在线检查更新**：
  - 自动获取 GitHub 最新 Release，支持国内多镜像源极速下载与一键安装。

---

## 开源协议

本项目采用 [Apache License 2.0](LICENSE) 协议开源。
