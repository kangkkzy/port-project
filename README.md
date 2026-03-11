# SECS: 自动化集装箱码头离散事件仿真系统
**(Automated Container Terminal Discrete Event Simulation System)**

## 📖 项目简介
**SECS** 是一个专为自动化集装箱码头（ACT）设计的轻量级、高保真离散事件仿真引擎与可视化平台。系统基于交通运输工程与现代港口物流管理的核心业务流程，从零构建了包括岸桥（QC）、自动化轨道吊（ASC）、集卡（Truck）在内的数字化模型。

本项目不仅能提供真实作业流程的可视化动态推演，更为高级的**多智能体寻路算法（MAPF）**、**交通拥堵风险评估**以及**基于机器学习的调度决策**提供了一个解耦、可靠的测试沙箱（Sandbox）。

## ✨ 核心特性
- ⏱️ **双驱动仿真引擎**：采用时间步（Time-step）与事件队列（Event-Queue）双重驱动架构，支持高并发指令处理与状态机流转。
- 🚚 **高保真物理与运动学建模**：内置二维坐标系下的物理引擎，支持时间切片级的设备速度、加速度及轨迹段计算。
- 📡 **全双工实时同步**：基于 WebSocket 协议，实现后端仿真时钟与前端视图的毫秒级状态同步。
- 🗺️ **2D 数字孪生可视化**：前端采用 Vue 3 + Konva (Canvas) 技术，实现港口堆场、路网拓扑及设备运动轨迹的精细化渲染。
- 🔌 **算法无缝接入**：提供标准的 HTTP RESTful API 与外部算法扩展点，支持 Python 等外部语言轻松接入 MAPF 算法或 AI 调度模型。
- 🚧 **防冲突与交通管控**：内置碰撞雷达服务与电子围栏（Fence）机制，支持动态交通管制模拟。

## 🛠️ 技术栈
### 后端 (Backend)
- **语言/框架**: Java 17+ / Spring Boot 3
- **通信**: WebSocket, RESTful API
- **核心设计**: 策略模式（事件处理器）、面向对象状态机引擎、切面异常处理

### 前端 (Frontend)
- **框架**: Vue 3 (Composition API) + Vite
- **语言**: TypeScript
- **可视化**: Konva.js (vue-konva) 用于 2D Canvas 渲染
- **状态管理**: Pinia

## 📁 项目结构
```text
port-project/
├── src/main/java/              # 后端 Java 源码目录
│   ├── engine/                 # 核心：离散事件引擎、WebSocket 广播、日志
│   ├── service/                # 业务逻辑：物理计算、场景加载、外部算法对接
│   ├── controller/             # HTTP API 控制层
│   ├── model/                  # 实体类、DTO 及系统配置
│   └── common/                 # 全局配置、工具类、异常处理
├── src/main/resources/         # 后端配置文件与静态资源
│   ├── map-config.json         # 港口地图拓扑配置文件
│   └── scenarios/              # 仿真场景与作业指令初始化文件
├── frontend/                   # 前端 Vue 3 源码目录
│   ├── src/components/         # 仿真控制面板、地图渲染组件
│   ├── src/api/                # Axios 请求封装与 WebSocket 通信逻辑
│   ├── src/stores/             # 仿真状态全局管理
│   └── src/views/              # 页面级视图
└── pom.xml                     # Maven 依赖管理

## 🚀 快速启动
1. 环境准备
JDK 17 或更高版本

Maven 3.6+

Node.js 18+ 与 npm (或 pnpm)

2. 启动后端仿真引擎
进入项目根目录，通过 Maven 启动 Spring Boot 应用：

Bash
mvn clean install
mvn spring-boot:run
后端服务默认运行在 http://localhost:8080

3. 启动前端可视化系统
进入 frontend 目录，安装依赖并启动 Vite 开发服务器：

Bash
cd frontend
npm install
npm run dev
前端服务默认运行在 http://localhost:5173。打开浏览器访问该地址即可看到港口仿真界面。

## 🔌 API 与算法接入
本项目提供完善的 HTTP 接口用于控制引擎和下发指令，非常适合作为外部调度算法（如 Python 编写的强化学习模型）的运行环境。

加载地图: GET /api/map/config

加载场景: POST /api/scenario/load

引擎控制: POST /api/engine/start | pause | step

下发指令: POST /api/command/move | assignTask | fenceControl

详细的接口报文结构请参考项目内置的 Swagger 文档或 API 说明文档。

## 📈 未来规划
[ ] 集成高级 MAPF (Multi-Agent Path Finding) 算法作为默认的寻路策略。

[ ] 引入随机干扰机制（如设备临时故障），用于支持 Bow-tie 等交通风险评估模型。

[ ] 提供基于机器学习（如 KNN）的调度日志分析工具。
