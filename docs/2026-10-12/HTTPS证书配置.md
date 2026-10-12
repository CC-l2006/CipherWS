# HTTPS / SSL 证书配置

> 落地日期：2026-10-12
> 证书来源：已签发好的 nginx 格式证书（`.pem` + `.key`）
> 覆盖范围：**`cipherws.icu` + `www.cipherws.icu`**（不含 `train.cipherws.icu`）

## 一、关键前提：证书的覆盖范围

SSL 证书按**域名**签发，**子域名不会自动被主域名证书覆盖**。所以：

| 站点 | 域名 | 本证书是否覆盖 | 结论 |
| --- | --- | --- | --- |
| 主站 | `cipherws.icu` | ✅ | 可以上 HTTPS |
| 主站 | `www.cipherws.icu` | ✅ | 可以上 HTTPS（但还缺一条 A 记录） |
| 云间列车 | `train.cipherws.icu` | ❌ | **仍是 HTTP**，需要单独签一张 |

强行用主域名证书去配 `train.cipherws.icu`，浏览器会报
`NET::ERR_CERT_COMMON_NAME_INVALID`（证书域名不匹配）—— 这不是配置错误，
是证书本身不含那个域名。阿里云每个账号每年有 20 张免费 DV 额度，再签一张即可。

## 二、配置步骤

### 1. 上传证书并收紧权限

```bash
# 服务器上先建目录（700：只有 root 能进）
sudo mkdir -p /etc/nginx/ssl
sudo chmod 700 /etc/nginx/ssl
```

```bash
# 在你本地机器上执行（文件名换成实际的）
scp cipherws.icu.pem root@47.104.253.198:/tmp/
scp cipherws.icu.key root@47.104.253.198:/tmp/
```

```bash
# 回到服务器归位
sudo mv /tmp/cipherws.icu.pem /etc/nginx/ssl/cipherws.icu.pem
sudo mv /tmp/cipherws.icu.key /etc/nginx/ssl/cipherws.icu.key
sudo chmod 600 /etc/nginx/ssl/cipherws.icu.pem /etc/nginx/ssl/cipherws.icu.key
sudo chown root:root /etc/nginx/ssl/cipherws.icu.pem /etc/nginx/ssl/cipherws.icu.key
sudo ls -l /etc/nginx/ssl/
```

权限必须是 `600` + 属主 root。nginx 以 root 身份启动、读完证书再降权运行；
权限放宽等于把私钥暴露给机器上所有用户，等于证书作废。

文件名**必须**是 `cipherws.icu.pem` / `cipherws.icu.key`，因为
`deploy/nginx/cipherws.conf` 里写死了这两个路径。

### 2. 校验证书本身（**别跳过这步**）

```bash
# 覆盖哪些域名、有效期
sudo openssl x509 -in /etc/nginx/ssl/cipherws.icu.pem -noout -subject -dates -ext subjectAltName

# 证书与私钥是否配对 —— 两条命令输出的 md5 必须一致
sudo openssl x509 -noout -pubkey -in /etc/nginx/ssl/cipherws.icu.pem | openssl md5
sudo openssl pkey  -noout -pubout -in /etc/nginx/ssl/cipherws.icu.key | openssl md5
```

为什么必须做：`nginx -t` **只检查文件能不能读**，不检查证书内容。

| 问题 | `nginx -t` 能发现吗 | 后果 |
| --- | --- | --- |
| 证书路径写错 / 文件不存在 | ✅ 能，会拒绝加载 | 配置不生效，现有服务不受影响 |
| 证书与私钥不配对 | ❌ 不能 | TLS 握手失败，浏览器报错 |
| 证书不含访问的域名 | ❌ 不能 | nginx 正常跑，浏览器报域名不匹配 |
| 证书已过期 | ❌ 不能 | 浏览器拦截，站点等于不可用 |

所以第 2 步的 `subjectAltName` 和 md5 比对不能省。

### 3. 放行 443

阿里云控制台 → 安全组 → 入方向：

| 协议 | 端口 | 授权对象 |
| --- | --- | --- |
| 自定义 TCP | **443/443** | 0.0.0.0/0 |

> 本机安全组里已有一条「所有 TCP 1/65535 允许」，理论上已包含 443；
> 但如果 HTTPS 起不来而 nginx 确实在监听，就补一条显式的 443 规则排除干扰。

### 4. 应用配置

```bash
cd /opt/cipherws
git pull
./deploy/update-frontend.sh
```

脚本会先备份 `/etc/nginx/conf.d/cipherws.conf`，覆盖新配置，跑 `nginx -t`
校验，**失败自动回滚**再退出。所以配置写错不会把线上弄挂。

也可手工来：

```bash
sudo cp /opt/cipherws/deploy/nginx/cipherws.conf /etc/nginx/conf.d/cipherws.conf
sudo rm -f /etc/nginx/sites-enabled/default     # 挪走自带站点，免得抢 80
sudo nginx -t && sudo systemctl reload nginx
```

### 5. 验证

```bash
# 本机自测：绕开安全组与 DNS，直接确认 nginx 与证书没问题
curl -I -k https://127.0.0.1/ -H 'Host: cipherws.icu'

# 证书在监听吗
sudo ss -tlnp | grep -E ':(80|443)\b'

# 外网访问（在你自己电脑上跑）
curl -I https://cipherws.icu/
```

本机通、外网不通 → 问题在安全组或 DNS；
本机都不通 → 问题在 nginx 或证书。

浏览器打开 `https://cipherws.icu/` 应该看到锁头图标。点开证书能看到
颁发者、有效期、以及「使用者备用名称」里列出的两个域名。

### 6. 回滚

配置在 Git 里，所以回滚很干净：

```bash
cd /opt/cipherws
git log --oneline -- deploy/nginx/cipherws.conf    # 找到上一个版本
git checkout <上一个提交> -- deploy/nginx/cipherws.conf
sudo cp deploy/nginx/cipherws.conf /etc/nginx/conf.d/cipherws.conf
sudo nginx -t && sudo systemctl reload nginx
```

或者直接用脚本留下的备份（脚本每次同步前都会 `mktemp` 备份，但那是临时文件，
进程结束就没了 —— 所以长期回滚还是靠 Git）。

## 三、当前配置形态

| 站点 | :80 | :443 |
| --- | --- | --- |
| `cipherws.icu` / `www` | ACME 挑战目录 + **301 跳转 HTTPS** | 全部内容（页面 + `/say/` + `/audio/`） |
| `train.cipherws.icu` | 托管 `train/dist`（HTTP 正常服务） | 未启用（等它自己的证书） |

**80 块里特意保留了 `/.well-known/acme-challenge/`**，放在跳转之前。
将来若改用 Let's Encrypt 自动续期（`certbot --webroot /var/www/certbot`），
续期时验证请求不会被 301 掉。用之前记得 `sudo mkdir -p /var/www/certbot`。

## 四、前端不需要重新构建

接口地址是空值（同源相对路径 `/say/...`、`/audio/...`），切到 HTTPS 后
浏览器会自动用 `https://` 请求，**不需要改 `.env.production`、也不用重新构建**。

而 Link 页那个「云间列车」按钮指向的是 `http://train.cipherws.icu`，
列车站以后上了 HTTPS，记得把它改成 `https://`（改 `Link.vue` 后重新构建）。
现在不改也能用 —— 跨协议跳转不算混合内容，浏览器不会拦。

## 五、续期（重要）

阿里云免费 DV 证书**有效期很短（当前 3 个月）且不会自动续期**：

1. 到期前在控制台重新签发
2. 重新下载，替换 `/etc/nginx/ssl/` 下那两个文件
3. `sudo nginx -t && sudo systemctl reload nginx`

**证书一过期，浏览器会直接拦截，站点等于不可用** —— 务必设日历提醒。

想彻底免维护可改用 Let's Encrypt：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d cipherws.icu -d www.cipherws.icu
```

90 天一续、全自动。配置里已经预留了 ACME 挑战目录。

## 六、待办清单

| # | 事项 | 说明 |
| --- | --- | --- |
| 1 | 给 `www.cipherws.icu` 加 A 记录 | 证书和 `server_name` 都写了它，但 DNS 里查不到（实测解析失败），所以 `https://www.cipherws.icu` 暂时打不开。加 `www A 47.104.253.198` 即可，不用改 nginx |
| 2 | 给 `train.cipherws.icu` 单独签证书 | 签好后照 `deploy/nginx/cipherws.conf` 里「列车站 HTTPS」那段注释的步骤做 |
| 3 | 考虑开启 HSTS | 配置里写好了但**注释着**。原因见下 |
| 4 | 证书续期提醒 | 见第五节 |

### 为什么 HSTS 先别开

HSTS（`Strict-Transport-Security`）会让浏览器记住「这个域名只能用 HTTPS」，
在 `max-age` 到期前**无法回退到 HTTP**。而阿里云免费证书要**手工续期**，
万一忘了续期、证书过期，老访客会被浏览器直接挡在门外，连 HTTP 兜底都没有。

建议：确认 HTTPS 稳定跑一段时间、并且设好了续期提醒之后，再打开
`deploy/nginx/cipherws.conf` 里那行 `add_header Strict-Transport-Security`。
