# 云服务器部署方案

> 目标：把「拍照批改作业」后端四件套（MySQL / Redis / MinIO / API）部署到一台云服务器，
> 对外统一入口为 `http://www.zycquet.com`，nginx 用 `/tutu` 路径反向代理 API。

## 一、整体架构

```
小程序 / H5 客户端
   │
   ├─ 业务接口(上传/批改/任务/积分) ──→ http://www.zycquet.com/tutu/... ────nginx──→ 127.0.0.1:8080 (api 容器)
   │
   └─ 图片预签名 URL(展示作业照/错题卡) ──→ http://www.zycquet.com/homework-grader/...
                                                       └──nginx 原样透传──→ 127.0.0.1:9000 (minio 容器)

docker compose 内部网络：
  api ──→ mysql:3306 / redis:6379 / minio:9000   (容器内用服务名互访，不占宿主机端口)

（开发环境保持现状：本机 MinIO 直连 + presign 地址 http://127.0.0.1:9002，不受本方案影响）
```

**两个关键设计点：**

1. **API 走 `/tutu` 前缀，nginx 负责剥掉前缀**。后端本身没有 context-path，
   接口实际路径是 `/api/v1/...`，所以 `http://www.zycquet.com/tutu/api/v1/health` 会转发成 `/api/v1/health`。
2. **MinIO 预签名走 `/homework-grader/`（桶名）前缀，nginx 原样透传、绝不重写路径**。
   MinIO SDK 生成的是路径风格 URL（`{presign-endpoint}/{桶}/{key}?X-Amz-...`），
   S3 签名把路径计入签名——只要 nginx 不剥前缀，MinIO 收到的路径与签名一致即可通过校验。
   因此 `.env` 里 `MINIO_PRESIGN_ENDPOINT=http://www.zycquet.com`（不带端口、不带路径），
   9000 端口不需要对公网开放。将来上 https 也只需把它改成 `https://www.zycquet.com`，不用换域名。

## 二、服务器准备

```bash
# 1. 安装 Docker + compose 插件（官方源，Ubuntu/Debian 为例）
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker

# 2. 安全组 / 防火墙开放端口
#    80    → nginx（对外入口，API 和图片预签名都走它，必须）
#    3306 / 6379 / 8080 / 9000 → 不要对公网开放！（图片走 nginx 80 代理，9000 只留本机）
```

## 三、上传代码

服务器上只需要这些文件（Docker 多阶段构建，服务器不需要 maven/JDK）：

```
Dockerfile  docker-compose.yml  pom.xml  src/  scripts/  .env(服务器上新建)
```

```bash
# 方式一：git
git clone <你的仓库地址> tutu && cd tutu/springboot

# 方式二：rsync（本地执行，排除本地调试产物）
rsync -av --exclude 'target/' --exclude 'out/' --exclude 'data/' \
      --exclude 'samples/' --exclude '.env' --exclude 'miniapp/node_modules/' \
      /Users/nashdeng/tutu/springboot/ user@服务器IP:/opt/tutu/springboot/
```

> `data/` 一定不要上传——里面是本机 MySQL/MinIO 的旧数据卷，传上去会把容器数据搞乱。

## 四、配置 `.env`（服务器上新建，权限 600）

```bash
cd /opt/tutu/springboot
cat > .env <<'EOF'
# ---- 数据库 ----
DB_USERNAME=homework_app
DB_PASSWORD=<一个强密码>

# ---- Redis（容器网络内互访，暂可无密码；见第八节加固建议）----

# ---- 存储：MinIO ----
STORAGE_PROVIDER=minio
MINIO_ACCESS_KEY=<自定一个 accessKey>
MINIO_SECRET_KEY=<自定一个强 secretKey>
MINIO_BUCKET=homework-grader
# 预签名对外地址：走 nginx 80 的桶名前缀路径（SDK 会拼成 http://www.zycquet.com/homework-grader/{key}）
MINIO_PRESIGN_ENDPOINT=http://www.zycquet.com

# ---- 批改模型（必填，否则提交批改会失败）----
DEEPSEEK_API_KEY=<你的 DeepSeek key>

# ---- 微信登录（生产必填）----
WX_MINIAPP_APPID=wx88168e0c2c6b3bf2
WX_MINIAPP_SECRET=<小程序 secret>
WX_DEV_LOGIN_ENABLED=false        # 生产必须 false，允许 devIdentity 登录的开关绝不能开

# ---- OCR 错题选框（可选，不填则不裁剪错题图）----
GRADING_CROP_ENABLED=true
COS_SECRET_ID=<腾讯云子账号 ID>
COS_SECRET_KEY=<腾讯云子账号 KEY>
OCR_SECRET_ID=<同上，或单独的 OCR 子账号>
OCR_SECRET_KEY=<同上>

# ---- 跨域：生产留空 ----
CORS_ALLOWED_ORIGINS=
EOF
chmod 600 .env
```

## 五、改 docker-compose.yml 的端口（服务器标准端口）

服务器上没有本机原生 MySQL/Redis 抢端口，改用标准端口起：

```bash
export MYSQL_PORT=3306 REDIS_PORT=6379 MINIO_PORT=9000 MINIO_CONSOLE_PORT=9001
```

**安全加固（强烈建议直接改文件）**：MySQL/Redis/API/MinIO 全部只给本机 nginx 用，
端口映射都绑到 `127.0.0.1`，公网只暴露 nginx 的 80：

```yaml
  mysql:
    ports:
      - "127.0.0.1:3306:3306"      # 改：只绑本机（远程运维走 SSH 隧道）
  redis:
    ports:
      - "127.0.0.1:6379:6379"      # 改：只绑本机
  minio:
    ports:
      - "127.0.0.1:9000:9000"      # 改：图片走 nginx 80 代理，9000 不对公网开放
      - "127.0.0.1:9001:9001"      # 改：控制台只绑本机
  api:
    ports:
      - "127.0.0.1:8080:8080"      # 改：只给 nginx 反代用
```

启动：

```bash
DOCKER_BUILDKIT=1 docker compose up -d --build
docker compose ps          # 四个容器都应为 healthy
docker compose logs -f api # 首次启动 Flyway 会自动建表（V1~V8 全量迁移）
```

> 首次构建要下 maven 依赖，约 5~10 分钟；之后改代码重建只需 1 分钟左右（BuildKit 缓存）。

## 六、nginx 配置

服务器上已有 nginx 在跑网站（`www.zycquet.com` 的 80 端口），**把下面两个 location 加进现有的那个 server 块**（别新建 server，会抢 `server_name`）：

```nginx
# ---- 加在现有 server { listen 80; server_name www.zycquet.com; ... } 里 ----

    client_max_body_size 25m;      # 上传接口上限 20MB，留点余量（加在 server 层）

    # 1) 业务 API：/tutu/xxx → 127.0.0.1:8080/xxx
    #    注意 proxy_pass 结尾的 / 会剥掉 /tutu 前缀
    location /tutu/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 200s;   # 批改接口最长 180s，别让 nginx 先掐断
    }

    # 2) MinIO 图片预签名：/homework-grader/{key} → 127.0.0.1:9000/homework-grader/{key}
    #    关键：proxy_pass 后面【不带路径、不带斜杠】，nginx 才会原样透传 URI；
    #    S3 签名包含路径，一旦重写（加 / 或改写）MinIO 校验必 403。
    #    前缀必须等于桶名（MINIO_BUCKET=homework-grader），换桶名要同步改这里。
    location /homework-grader/ {
        proxy_pass http://127.0.0.1:9000;
        proxy_set_header Host $http_host;   # Host 参与签名，必须原样透传，不能改成 $proxy_host
    }
```

改完执行 `sudo nginx -t && sudo systemctl reload nginx`。

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 七、验证

```bash
# 1. 健康检查（应返回 JSON，code=0）
curl http://www.zycquet.com/tutu/api/v1/health

# 2. MinIO 图片路径走 nginx（在小程序里打开一张作业照/错题卡，图片能显示即通）
#    后端返回的预签名地址应形如 http://www.zycquet.com/homework-grader/xxx?X-Amz-...
#    也可直接拿一条预签名 URL 来 curl：
curl -I 'http://www.zycquet.com/homework-grader/<key>?X-Amz-...'   # 200
#    （403 = 签名没对上，多半是 proxy_pass 写成了带 / 的形式或 Host 被改写）

# 3. 小程序联调（API 地址是编译期注入的，不用改源码）
#    - 线上模式编译：npm run build:weapp:online   （或 dev:weapp:online 带监听）
#      → API_BASE = http://www.zycquet.com/tutu
#    - 本地模式（默认）：npm run dev:weapp → API_BASE = http://127.0.0.1:8080
#    - 开发者工具勾选「不校验合法域名」即可用 http 联调
#    - 真机预览同样需要打开调试模式（右上角胶囊 → 开发调试）才能走 http
```

**上线前必须做（微信硬性要求）**：
- 小程序正式版 request/downloadFile 合法域名**只接受 https**。
  给 `www.zycquet.com` 配 TLS 证书（Let's Encrypt 免费即可），nginx 把上面两个 location 挪进 443 的
  server 块，`.env` 里 `MINIO_PRESIGN_ENDPOINT` 改成 `https://www.zycquet.com` 后
  `docker compose up -d api` 重建。图片和 API 同域名，**不需要**额外的 files 子域名。
- 在微信公众平台 → 开发管理 → 服务器域名，添加 `https://www.zycquet.com`
  （request/uploadFile/downloadFile 三类都勾上）。

## 八、日常运维

```bash
# 更新后端（改代码后）
DOCKER_BUILDKIT=1 docker compose up -d --build api

# 看日志
docker compose logs -f api

# 数据备份（MySQL 数据卷 + MinIO 数据卷）
tar czf /backup/tutu-$(date +%F).tgz data/docker/mysql data/docker/minio
# 建议配 crontab 每日备份并异地传输
```

**加固清单（按优先级）**：
1. `.env` 权限 600，永不提交 git（`.gitignore` 已排除）
2. MySQL/Redis/API 端口只绑 `127.0.0.1`（第五节已做）
3. Redis 加密码：`.env` 加 `REDIS_PASSWORD`，compose 里 redis 加 `command: redis-server --requirepass ${REDIS_PASSWORD}`
4. 上 https（见第七节）后再提审小程序
5. 后续补：微信内容安全审核（imgSecCheck/msgSecCheck）、用户数据删除入口（未成年人合规）
