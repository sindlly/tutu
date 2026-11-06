# 云服务器部署方案

> 目标：把「拍照批改作业」后端四件套（MySQL / Redis / MinIO / API）部署到一台云服务器，
> 对外统一入口为 `http://www.zycquet.com`，nginx 用 `/tutu` 路径反向代理 API。

## 一、整体架构

```
小程序 / H5 客户端
   │
   ├─ 业务接口(上传/批改/任务/积分) ──→ http://www.zycquet.com/tutu/... ──nginx──→ 127.0.0.1:8080 (api 容器)
   │
   └─ 图片预签名 URL(展示作业照/错题卡) ──→ http://www.zycquet.com:9000 ────────→  minio 容器
                                                          (S3 签名含 Host，必须直连，见下文)

docker compose 内部网络：
  api ──→ mysql:3306 / redis:6379 / minio:9000   (容器内用服务名互访，不占宿主机端口)
```

**两个关键设计点：**

1. **API 走 `/tutu` 前缀，nginx 负责剥掉前缀**。后端本身没有 context-path，
   接口实际路径是 `/api/v1/...`，所以 `http://www.zycquet.com/tutu/api/v1/health` 会转发成 `/api/v1/health`。
2. **MinIO 不能挂在 `/tutu/xxx` 子路径下**。S3 预签名把**路径**也计入签名，
   nginx 剥前缀后 MinIO 校验的路径与签名不一致会直接 403。所以图片读取用「同域名不同端口」直通（方案 A），
   将来上 https 时再切独立子域名（方案 B）。

## 二、服务器准备

```bash
# 1. 安装 Docker + compose 插件（官方源，Ubuntu/Debian 为例）
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker

# 2. 安全组 / 防火墙开放端口
#    80    → nginx（对外入口，必须）
#    9000  → MinIO 预签名 URL 直通（必须，见方案 A）
#    3306 / 6379 / 8080 → 不要对公网开放！
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
# 预签名对外地址：Host 参与签名，必须是「客户端可达」的地址（方案 A：同域名 9000 端口）
MINIO_PRESIGN_ENDPOINT=http://www.zycquet.com:9000

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

**安全加固（强烈建议直接改文件）**：MySQL/Redis/API 只给本机 nginx 用，
端口映射都绑到 `127.0.0.1`，公网完全摸不到；只有 MinIO 需要对公网开放：

```yaml
  mysql:
    ports:
      - "127.0.0.1:3306:3306"      # 改：只绑本机（远程运维走 SSH 隧道）
  redis:
    ports:
      - "127.0.0.1:6379:6379"      # 改：只绑本机
  minio:
    ports:
      - "9000:9000"                # 保持：预签名要公网可达
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

`/etc/nginx/conf.d/tutu.conf`（或加进现有 server 块）：

```nginx
server {
    listen 80;
    server_name www.zycquet.com;

    client_max_body_size 25m;      # 上传接口上限 20MB，留点余量

    # 业务 API：/tutu/xxx → 127.0.0.1:8080/xxx（注意 proxy_pass 结尾的 / 会剥掉 /tutu 前缀）
    location /tutu/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 200s;   # 批改接口最长 180s，别让 nginx 先掐断
    }

    # MinIO 预签名直通（方案 A）：只转发不重写路径，S3 签名才对得上
    location / {
        proxy_pass http://127.0.0.1:9000;
        proxy_set_header Host $http_host;   # 必须原样传 Host，参与签名
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 七、验证

```bash
# 1. 健康检查（应返回 JSON，code=0）
curl http://www.zycquet.com/tutu/api/v1/health

# 2. MinIO 直通
curl -I http://www.zycquet.com:9000/minio/health/live   # 200

# 3. 小程序联调（API 地址是编译期注入的，不用改源码）
#    - 线上模式编译：npm run build:weapp:online   （或 dev:weapp:online 带监听）
#      → API_BASE = http://www.zycquet.com/tutu
#    - 本地模式（默认）：npm run dev:weapp → API_BASE = http://127.0.0.1:8080
#    - 开发者工具勾选「不校验合法域名」即可用 http 联调
#    - 真机预览同样需要打开调试模式（右上角胶囊 → 开发调试）才能走 http
```

**上线前必须做（微信硬性要求）**：
- 小程序正式版 request/downloadFile 合法域名**只接受 https**。
  需要给 `www.zycquet.com` 配 TLS 证书（Let's Encrypt 免费即可），
  并把 MinIO 切到独立子域名（方案 B）：`files.zycquet.com` 也配证书，
  `.env` 里 `MINIO_PRESIGN_ENDPOINT=https://files.zycquet.com`，
  nginx 对两个域名各自 `proxy_pass`（仍然不能重写路径）。
- 在微信公众平台 → 开发管理 → 服务器域名，添加 `https://www.zycquet.com`（request/uploadFile）
  和 `https://files.zycquet.com`（downloadFile）。

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
4. 上 https（方案 B）后再提审小程序
5. 后续补：微信内容安全审核（imgSecCheck/msgSecCheck）、用户数据删除入口（未成年人合规）
