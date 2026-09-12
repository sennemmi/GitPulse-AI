# GitPulse AI 技术情报分析系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-green.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-orange.svg)](https://spring.io/projects/spring-ai)

## 项目简介

🚀 GitPulse AI: A high-performance Multi-Agent system for automated GitHub technical intelligence. Built with Java 21, Spring AI, and MCP. 基于 Java 21 和多智能体协同的 GitHub 仓库深度评估系统，支持异步任务处理、结构化情报生成和 Markdown 导出。

项目提供两种运行模式：默认的演示模式不依赖 GitHub、模型或图片服务即可验证完整任务链路；真实模式通过 GitHub API、ModelScope 和可选的 GitHub MCP 获取实时数据。

## 核心功能

- **智能仓库分析**：自动分析 GitHub 仓库的技术栈、架构和代码质量
- **多代理协作**：采用 IntentAgent、ResearchAgent、ReportAgent 多代理架构
- **技术报告生成**：基于 AI 生成详细的技术分析报告
- **开源技术雷达**：维护仓库 Watchlist，按团队评估模板生成可解释评分、证据快照和横向比较
- **文生图支持**：集成 ModelScope 魔搭社区 API，支持生成技术架构图
- **MCP 协议支持**：通过 GitHub MCP Server 实现与 GitHub 的深度集成
- **异步消息处理**：基于 RocketMQ 实现任务异步处理
- **缓存优化**：使用 Redis 缓存分析结果，提升响应速度
- **流量控制**：集成 Sentinel 实现限流和熔断保护

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.11 | 核心框架 |
| Spring AI | 1.1.2 | AI 模型集成 |
| Java | 21 | 编程语言 (支持虚拟线程) |
| MySQL | 8.x | 数据持久化 |
| Redis | 7.x | 缓存与分布式锁 |
| RocketMQ | 5.x | 消息队列 |
| Sentinel | 2023.0.1.0 | 流量控制 |
| MCP Client | - | Model Context Protocol |
| ModelScope | - | 文生图 API |

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户请求层                            │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                    AgentController                          │
│                  (REST API 接口层)                           │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
│ IntentAgent  │ │ResearchAgent│ │ ReportAgent │
│  (意图识别)   │ │ (技术研究)  │ │ (报告生成)  │
└───────┬──────┘ └──────┬──────┘ └──────┬──────┘
        │               │               │
        └───────────────┼───────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                  GitHub MCP Server                          │
│              (Docker 容器化运行)                             │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
│  GitHub API  │ │ ModelScope  │ │   MySQL     │
│  (仓库数据)   │ │  (文生图)   │ │  (数据存储)  │
└──────────────┘ └─────────────┘ └─────────────┘
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- RocketMQ 5.0+
- Docker Engine + Docker Compose (用于运行 MySQL、Redis 和 RocketMQ)
- WSL2 + Ubuntu 24.04（WSL 运行方式）

### 1. 克隆项目

```bash
git clone https://github.com/sennemmi/GitPulse-AI.git
cd GitPulse-AI
```

### 2. 配置环境变量

项目使用环境变量管理敏感配置，请创建 `.env` 文件或设置以下环境变量：

```bash
# MySQL 配置
export MYSQL_PASSWORD=your_mysql_password

# GitHub Token
export GITHUB_TOKEN=your_github_personal_access_token

# ModelScope API Key
export MODELSCOPE_API_KEY=your_modelscope_api_key

# 可选：代理配置
export PROXY_HOST=127.0.0.1
export PROXY_PORT=7890
```

**Windows PowerShell:**
```powershell
$env:MYSQL_PASSWORD="your_mysql_password"
$env:GITHUB_TOKEN="your_github_personal_access_token"
$env:MODELSCOPE_API_KEY="your_modelscope_api_key"
```

### 3. 启动依赖服务

```bash
# 使用脚本启动并等待 MySQL、Redis、RocketMQ 健康
bash start-deps.sh

# Windows PowerShell/CMD
start-deps.bat
```

### 4. 在 WSL 中运行项目

```bash
# 确保 JAVA_HOME 指向 JDK 21
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

# 打包（当前 WSL 演示环境已验证）
./mvnw -DskipTests package

# 使用 WSL 配置启动，端口为 18080
java -jar target/AgentsProj-0.0.1-SNAPSHOT.jar --spring.profiles.active=wsl
```

WSL 配置默认使用 `app.demo-mode=true`、关闭 MCP 和代理，因此可以在没有外部 API Key 的情况下验证任务链路。真实运行时设置 `app.demo-mode=false`，并提供 `GITHUB_TOKEN`、`MODELSCOPE_API_KEY` 及所需的 MCP 配置。

## API 接口

### 获取幂等 Token

```http
GET /api/agent/token
```

### 提交异步任务

```http
POST /api/agent/chat
Content-Type: application/json
Idempotency-Token: <token>

{
  "message": "分析 https://github.com/sennemmi/GitPulse-AI"
}
```

接口返回 `taskId`。支持的自然语言示例包括：

- `分析 https://github.com/owner/repository`
- `查看 GitHub 热榜`
- `analyze and publish https://github.com/owner/repository`
- `export 一段项目介绍`

### 查询任务结果

```http
GET /api/agent/task/{taskId}
```

任务状态通常依次为 `PENDING`、`INTENT_RECOGNIZED`、`DATA_FETCHING`、`AI_ANALYZING`、`REPORT_GENERATING`、`SUCCESS` 或 `FAILED`。

启用发布/导出意图时，Markdown 文件默认写入 `exports/`。

### 技术雷达 API

技术雷达用于把一次性仓库分析变成可持续的仓库评估。演示模式使用明确标记的 `demo://` 证据；真实模式会从 GitHub API 采集仓库元数据、README、根目录文件、贡献者数量和最近 commit。

#### 创建 Watchlist 项目

```http
POST /api/radar/watchlist
Content-Type: application/json

{
  "repoName": "owner/repository",
  "displayName": "候选框架 A",
  "criteria": {
    "maintenance": 30,
    "community": 25,
    "productionFit": 20,
    "documentation": 15,
    "license": 10
  }
}
```

不传 `criteria` 时使用默认评估模板。当前评分规则是可解释的确定性规则，不把任意模型输出当作事实分数。

```http
GET  /api/radar/watchlist
PUT  /api/radar/watchlist/{watchId}
POST /api/radar/watchlist/{watchId}/scan
GET  /api/radar/watchlist/{watchId}/snapshots
DELETE /api/radar/watchlist/{watchId}
POST /api/radar/scan-all
```

#### 横向比较

```http
POST /api/radar/compare
Content-Type: application/json

{
  "watchIds": [1, 2, 3]
}
```

比较接口返回各仓库最近一次快照的总分、分项得分、原始指标、证据和采集时间。定时扫描默认关闭，可通过 `RADAR_SCHEDULE_ENABLED=true` 开启，并用 `RADAR_SCAN_CRON` 配置 Cron 表达式。

## 配置说明

### application.yaml 配置项

| 配置项 | 说明 | 环境变量 |
|--------|------|----------|
| `spring.datasource.password` | MySQL 密码 | `MYSQL_PASSWORD` |
| `spring.ai.openai.api-key` | ModelScope API Key | `MODELSCOPE_API_KEY` |
| `github.token` | GitHub Personal Access Token | `GITHUB_TOKEN` |
| `spring.ai.mcp.client.stdio.connections.github-mcp.env.GITHUB_PERSONAL_ACCESS_TOKEN` | MCP GitHub Token | `GITHUB_TOKEN` |
| `app.demo-mode` | 是否使用本地确定性演示数据 | `APP_DEMO_MODE` |
| `spring.ai.mcp.client.enabled` | 是否启用 GitHub MCP 客户端 | `MCP_ENABLED` |
| `proxy.enabled` | 是否启用代理 | `PROXY_ENABLED` |
| `proxy.host` | 代理服务器地址 | `PROXY_HOST` |
| `proxy.port` | 代理服务器端口 | `PROXY_PORT` |

## 获取 API Key

### GitHub Personal Access Token

1. 登录 GitHub 账号
2. 进入 Settings -> Developer settings -> Personal access tokens
3. 点击 "Generate new token (classic)"
4. 勾选以下权限：
   - `repo` - 访问仓库
   - `read:org` - 读取组织信息 (可选)
5. 生成并保存 Token

### ModelScope API Key

1. 访问 [ModelScope 魔搭社区](https://www.modelscope.cn/)
2. 注册并登录账号
3. 进入个人中心 -> API Key 管理
4. 创建新的 API Key

## 项目结构

```
AgentsProj/
├── src/main/java/cn/hhu/sen/agentsproj/
│   ├── agent/              # AI 代理实现
│   │   ├── IntentAgent.java      # 意图识别代理
│   │   ├── ResearchAgent.java    # 技术研究代理
│   │   └── ReportAgent.java      # 报告生成代理
│   ├── client/             # 外部 API 客户端
│   │   ├── GitHubApiClient.java
│   │   └── ModelScopeImageClient.java
│   ├── config/             # 配置类
│   ├── controller/         # REST API 控制器（含技术雷达 API）
│   ├── entity/             # 数据库实体（含雷达 Watchlist 和快照）
│   ├── exception/          # 异常处理
│   ├── model/              # 数据模型
│   ├── mq/                 # 消息队列消费者
│   ├── repository/         # 数据访问层
│   ├── service/            # 业务逻辑层
│   │   ├── RadarDataService.java       # 仓库事实采集
│   │   ├── RadarScoringService.java    # 可解释评分
│   │   └── RadarService.java           # Watchlist/快照/比较
│   ├── tools/              # AI 工具类
│   └── AgentsProjApplication.java
├── src/main/resources/
│   ├── prompts/            # AI Prompt 模板
│   ├── application.yaml    # 默认应用配置
│   ├── application-wsl.yaml # WSL 本地依赖配置
│   ├── schema.sql          # MySQL 初始化表结构
│   └── logback-spring.xml  # 日志配置
├── docker-compose.yml      # Docker 编排配置
├── start-deps.sh            # WSL/Linux 依赖启动脚本
├── Dockerfile               # 多阶段构建镜像
└── pom.xml                 # Maven 配置
```

## 开发指南

### 添加新的 AI Agent

1. 在 `agent` 包下创建新的 Agent 类
2. 继承 Spring AI 的 Agent 抽象类
3. 在 `prompts` 目录添加对应的 Prompt 模板
4. 在 `AgentController` 中注册新的端点

### 自定义工具

在 `tools` 包下添加新的工具类，使用 `@Tool` 注解标记方法：

```java
@Component
public class CustomTools {
    
    @Tool(name = "customTool", description = "工具描述")
    public String customTool(String param) {
        // 实现逻辑
    }
}
```

## 测试

```bash
# 运行所有测试
./mvnw test

# 运行基准测试
./mvnw test -Dtest=*BenchmarkTest

# 运行集成测试
./mvnw test -Dtest=FullWorkflowIntegrationTest
```

## 部署

### Docker 部署

```bash
# 构建镜像
docker build -t agents-proj .

# 运行容器
docker run -d \
  -e MYSQL_PASSWORD=xxx \
  -e GITHUB_TOKEN=xxx \
  -e MODELSCOPE_API_KEY=xxx \
  -p 8080:8080 \
  agents-proj
```

### 生产环境注意事项

1. **务必使用环境变量管理敏感配置**，不要硬编码在配置文件中
2. 启用 HTTPS
3. 配置适当的日志级别
4. 设置 JVM 参数优化性能
5. 配置健康检查端点

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 [MIT](LICENSE) 许可证开源。

## 联系方式

如有问题或建议，欢迎提交 Issue 或 Pull Request。

---

**注意**：本项目仅供学习和研究使用，请遵守相关 API 的使用条款和速率限制。
