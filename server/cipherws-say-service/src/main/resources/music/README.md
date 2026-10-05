# music/ —— 音频资源目录（内容不入库）

**⚠️ 本目录下的音频文件已被 `.gitignore` 排除，不在版本库中。**

## 为什么

这里原有 14 个音频文件，合计 **约 131 MB**（最大单个 13.8 MB）。放进 Git 会让仓库
永久变重、clone 变慢，且 GitHub 对单文件有 100 MB 硬限制——一旦有文件超标，
push 会被直接拒绝，事后删掉也不会让历史变小。

因此音频改为**仓库外分发**（移动硬盘 / 网盘 / 对象存储），部署时放到磁盘目录，
由环境变量指过去。

## 现在怎么跑

后端读取音频的配置项是 `resource.path.audio.music`（见
`cipherws-say-service/src/main/resources/application.yml`）：

```yaml
resource:
  path:
    audio:
      music: ${AUDIO_MUSIC_PATH:classpath:music/}
```

所以有两种可用方式：

### 方式一：用 `deploy\run-services.ps1`（推荐）

该脚本启动服务时会**自动**把 `AUDIO_MUSIC_PATH` 指向
`<项目根>/deploy/music/`（若该目录存在）。你只需要把音频放进去：

```
deploy/
├── music/                          # 放音频，不入库
│   ├── HOYO-MiX - Da Capo.mp3
│   └── ...
└── run-services.ps1                # 启动时自动注入 AUDIO_MUSIC_PATH
```

### 方式二：手动指定任意磁盘目录

```powershell
$env:AUDIO_MUSIC_PATH = 'D:/music/'
# 注意：末尾的 "/" 要保留，Windows 路径用正斜杠
```

路径支持 `classpath:`、`file:` 前缀或普通目录；`AudioServiceImpl` 会在末尾补 `/`。

## 本目录下还保留什么

只保留本 `README.md`（`.gitignore` 里用 `music/*` + `!music/README.md` 精确放行）。
**不要**把音频文件加回这里再提交——需要入库的话请先确认是否接受 131 MB 的历史包袱。

## 相关文档

- [deploy/README.md](../../../../../../deploy/README.md) —— 本地部署与运行
- [docs/微服务架构.md](../../../../../../docs/微服务架构.md) —— 音频接口与配置说明
