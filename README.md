# CipherWS

CipherWS 是一个前后端同仓的个人建站项目：前端为 Vue 3 + Vite 单页应用，后端为 Spring Cloud Alibaba 微服务。

> 统计时间：2026-10-05

## 一、根目录总览

```
CipherWS/
├── frontend/          # 前端工程（Vue 3 + Vite 单页应用）
├── server/            # 后端工程（Spring Cloud Alibaba 微服务，Maven 多模块）
├── docker/            # 容器化部署资产（Nacos 镜像与启停脚本）
├── docs/              # 项目文档（架构、接口、部署说明等）
├── .git/              # Git 版本库
├── .gitignore         # Git 忽略规则
└── README.md          # 项目简介（本文件）
```

| 名称 | 类型 | 职责 |
| --- | --- | --- |
| `frontend/` | 目录 | 前端主站，负责页面渲染与接口调用（构建产物 `dist/`） |
| `server/` | 目录 | 后端微服务聚合工程（父 POM + 4 个模块） |
| `docker/` | 目录 | Nacos 注册中心 / 配置中心的容器镜像与启停脚本 |
| `docs/` | 目录 | 项目文档集中存放处 |
| `.git/` | 目录 | Git 版本库元数据 |
| `.gitignore` | 文件 | 仓库忽略规则 |
| `README.md` | 文件 | 项目说明（本文件） |

## 二、后端模块（server/）

```
server/
├── pom.xml                    # 父工程：Spring Boot 3.5 + Spring Cloud 2025 + Spring Cloud Alibaba 2025
├── cipherws-common/           # 公共模块：Result / 实体 / OpenFeign 契约
├── cipherws-say-service/      # 业务服务（名言 + 音频），注册 Nacos
├── cipherws-portal-service/   # 门户聚合服务（BFF），OpenFeign 调用 say 服务
└── cipherws-gateway/          # API 网关，统一入口（:8080）
```

| 模块 | 端口 | 说明 |
| --- | --- | --- |
| `cipherws-gateway` | 8080 | 对外唯一入口，经 Nacos 服务发现路由（沿用原后端端口） |
| `cipherws-say-service` | 8081 | 原单体业务服务 |
| `cipherws-portal-service` | 8082 | 聚合服务，演示 OpenFeign |

> Nacos 控制台在容器内是 8080，为避开网关占用的 8080，`docker/run.sh` 已把它映射到宿主 **8849**。

详见 [docs/微服务架构.md](docs/微服务架构.md)。

## 三、快速开始

```bash
# 1) 启动 Nacos（注册中心 / 配置中心）
cd docker && bash load-docker.sh && bash run.sh

# 2) 构建并启动后端微服务
cd ../server && ./mvnw clean package
java -jar cipherws-say-service/target/cipherws-say-service-0.0.1-SNAPSHOT.jar
java -jar cipherws-portal-service/target/cipherws-portal-service-0.0.1-SNAPSHOT.jar
java -jar cipherws-gateway/target/cipherws-gateway-0.0.1-SNAPSHOT.jar

# 3) 前端开发
cd ../frontend && npm install && npm run dev
```

验证：`curl http://127.0.0.1:8080/say/saying?id=1`

## 四、文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/微服务架构.md](docs/微服务架构.md) | 后端微服务结构、Nacos、OpenFeign、运行方式 |
| [docs/Nacos容器部署.md](docs/Nacos容器部署.md) | `docker/` 目录说明与 Nacos 容器部署 |
| [docs/分层架构.md](docs/分层架构.md) | 自上而下的分层架构设计 |
| [docs/架构设计.md](docs/架构设计.md) | 总体拓扑、部署流程与决策记录 |
| [server/接口文档.md](server/接口文档.md) | 后端接口说明 |
