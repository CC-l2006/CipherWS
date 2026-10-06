# docker/ 目录说明与 Nacos 容器部署

> 统计时间：2026-10-05
>
> 本文说明仓库根目录新增的 `docker/` 目录：它把 **Nacos（注册中心 + 配置中心）** 做成可离线导入的容器镜像，
> 供后端微服务（见 [微服务架构.md](./微服务架构.md)）注册与拉取配置使用。

## 一、目录内容

```
docker/
├── load-docker.sh              # 导入镜像：docker load -i ./*.tar
├── run.sh                      # 启动 Nacos 单机容器（standalone + derby 内嵌存储）
└── nacos-server:v3.3.0.tar     # Nacos 镜像的 OCI tar 包（约 588 MB；未入库，.gitignore 已忽略）
```

| 文件 | 作用 | 关键点 |
| --- | --- | --- |
| `load-docker.sh` | 把目录内所有 `*.tar` 镜像包导入本机 Docker | 本质是 `docker load -i ./*.tar`，导入后打印 `docker images` |
| `run.sh` | 以单机模式启动 Nacos | 设置 `MODE=standalone` 与鉴权密钥，映射 `8849/8848/9848` 三个端口（控制台宿主端口 8849） |
| `nacos-server:v3.3.0.tar` | 离线镜像包 | 镜像内 `RepoTags` 为 **`nacos/nacos-server:v3.3.0-RC`**（注意与文件名 `v3.3.0` 不完全一致）；**未纳入版本库**（`.gitignore` 忽略 `docker/*.tar`） |

## 二、为什么用「导入 tar」而不是 `docker pull`

- 目标环境（内网 / 云服务器）可能无法直连 Docker Hub，因此先在能联网的机器上 `docker save` 出镜像，再通过**仓库外渠道**（移动硬盘 / 对象存储）分发；
- `load-docker.sh` 只负责 `docker load`，对文件命名不敏感，导入后即可用镜像 tag 启动。

> ⚠️ **两点注意**
> 1. 文件名里的冒号 `:` 在 Linux/macOS 合法，但 **Windows 不允许文件名包含 `:`**。若要在 Windows 上克隆/解包，
>    建议把文件改名为 `nacos-server-v3.3.0.tar`（`load-docker.sh` 用的是通配符 `./*.tar`，不受影响）。
> 2. **tar 体积接近 600 MB，已从版本库移除并加入 `.gitignore`（规则 `docker/*.tar`）**。
>    文件仍保留在本地 `docker/` 目录，但 `git clone` 不会带上它，推送 GitHub 也不会（GitHub 单文件上限 100 MB）。
>    换机器时改用 `docker pull nacos/nacos-server:v3.3.0-RC` 拉取，或通过移动硬盘 / 对象存储单独传递该 tar，再执行 `bash load-docker.sh`。

## 三、镜像信息

`docker inspect` 得到的关键信息：

| 项 | 值 |
| --- | --- |
| 镜像 tag | `nacos/nacos-server:v3.3.0-RC` |
| 基础系统 | Ubuntu 26.04 |
| 内置 JDK | OpenJDK（镜像自带，无需宿主机装 JDK） |
| 默认工作目录 | `/home/nacos` |
| 默认 JVM | `-Xms1g -Xmx1g -Xmn512m` |
| 暴露端口 | `8080/tcp`、`8848/tcp`、`9080/tcp`、`9848/tcp`、`5353/tcp`、`5353/udp` |
| 时区 | `Asia/Shanghai` |

## 四、部署步骤

```bash
# 1. 进入 docker 目录
cd docker

# 2. 导入镜像（读取目录内所有 *.tar）
bash load-docker.sh

# 3. 启动 Nacos（单机模式）
bash run.sh

# 4. 查看容器状态
docker ps
```

启动成功后：

- **控制台**：`http://<服务器IP>:8849`（浏览器访问，会 302 跳转到 `/next/`）
- **客户端地址**：`<服务器IP>:8848`（微服务 `spring.cloud.nacos.server-addr` 填写这个）

## 五、端口规划

| 端口 | 协议 | 用途 | 是否对外 |
| --- | --- | --- | --- |
| `8849` | HTTP | Nacos 控制台（Web UI，宿主映射到容器内 8080） | 建议仅内网访问 |
| `8848` | HTTP / gRPC | **主端口**，客户端（微服务）连接地址 | 仅内网 |
| `9848` | gRPC | 客户端 gRPC 连接（2.x/3.x 客户端需要） | 仅内网 |
| `9849` | gRPC | 服务端间 gRPC（集群模式用，单机可不通） | 不暴露 |

> run.sh 映射的是 `8849`（控制台）、`8848`、`9848` 三个端口，单机（standalone）演示足够。
> 微服务客户端会打印 `server main port = 8848`，即注册/发现走的是 **8848**。
>
> **控制台为什么是 8849 而不是 8080**：网关 `cipherws-gateway` 沿用了前端的 `8080` 端口，
> 为避免冲突，`run.sh` 把容器内控制台的 8080 映射到宿主 **8849**（容器内端口不变）。

## 六、环境变量

`run.sh` 里设置的变量：

| 变量 | 值（示例） | 说明 |
| --- | --- | --- |
| `MODE` | `standalone` | 单机模式（内嵌 Derby 数据库，仅适合开发/演示） |
| `NACOS_AUTH_TOKEN` | `N2FjM2Y4YjEtZGM3OS00ZjI1LThhYjYtMTIzNDU2Nzg5MGFi` | 鉴权 Token，**必须是 Base64 字符串**（该值是 `7ac3f8b1-...` 的 Base64） |
| `NACOS_AUTH_IDENTITY_KEY` | `nacos_identity_key_9f8e7d6c` | 服务间身份校验的 key |
| `NACOS_AUTH_IDENTITY_VALUE` | `nacos_identity_value_3a2b1c0d9e8f7a6b` | 服务间身份校验的 value |

> 镜像启动脚本会校验 `NACOS_AUTH_TOKEN`：缺省会直接报
> `env NACOS_AUTH_TOKEN must be set with Base64 String.` 并退出。

## 七、鉴权与登录（Nacos 3.x 重要变化）

Nacos 3.x 默认开启鉴权，需要注意：

1. **控制台首次使用要初始化管理员密码**：打开 `http://<IP>:8849`，按提示创建 `admin` 用户密码；
   在此之前用默认账号直连会返回 `User not found! Please check user exist or password is right!`。
2. **微服务客户端要带上用户名/密码**：
   ```yaml
   spring:
     cloud:
       nacos:
         server-addr: 127.0.0.1:8848
         username: ${NACOS_USERNAME:nacos}
         password: ${NACOS_PASSWORD:nacos}
   ```
   账号密码要和控制台里初始化的一致，否则注册会失败（`register failed ... 401`）。
3. **临时关闭鉴权（仅本地调试）**：可用 `-e NACOS_AUTH_ENABLE=false`，但镜像仍要求提供 `NACOS_AUTH_TOKEN`。

## 八、验证方式

导入并启动后，可以用下面的方式确认 Nacos 正常：

```bash
# 控制台可达（返回 302 即正常）
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8849/

# 容器在运行
docker ps --filter name=nacos --format '{{.Names}} {{.Status}} {{.Ports}}'
```

微服务注册成功的日志特征（见 [微服务架构.md](./微服务架构.md)）：

```
[REGISTER-SERVICE] public registering service cipherws-say-service with instance Instance{ip='...', port=8081, ...}
[REGISTER-SERVICE] public registering service cipherws-gateway     with instance Instance{ip='...', port=8080, ...}
```

## 九、常用运维命令

```bash
# 查看日志
docker logs -f nacos-standalone-derby

# 停止 / 启动 / 删除
docker stop nacos-standalone-derby
docker start nacos-standalone-derby
docker rm -f nacos-standalone-derby
```

## 十、生产环境建议

`run.sh` 使用的是 **standalone + 内嵌 Derby**，数据不落外部库、不支持集群，**只适合开发与演示**。正式环境建议：

- 用 `MODE=cluster` 部署至少 3 个节点，前置负载均衡；
- 外部化存储（MySQL）与鉴权密钥（用环境变量或密钥管理注入，不要硬编码进脚本）；
- 收紧端口暴露：`8848`/`9848` 仅内网可达，控制台 `8849` 加访问控制或只经跳板机访问；
- 给镜像打固定 tag 并纳入发布流程，避免随意 `latest`。

---

相关文档：[微服务架构.md](./微服务架构.md) · [分层架构.md](./分层架构.md) · [架构设计.md](./架构设计.md)
