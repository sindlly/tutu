#!/usr/bin/env bash
#
# 一键启动整个项目：Docker 四件套（MySQL/Redis/MinIO/API）+ 小程序前端。
#
# 幂等：已在跑的服务自动跳过，重复执行无副作用。
#   ./start.sh           # 全量启动（后端 + 小程序编译 + H5）
#   ./start.sh --no-h5   # 不起 H5（只在微信开发者工具里调试时用）
#
# 日志位置：
#   后端   docker compose logs -f api   （容器日志）
#   小程序 /tmp/homework-weapp.log
#   H5     /tmp/homework-h5.log
#
# 写法说明：本脚本刻意用「全单引号 + 命令不嵌套」的保守风格。
# macOS 自带 bash 3.2 对双引号内嵌套 $( ) 的解析有坑，绕开最省心。
#
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"
MINIAPP="$ROOT/miniapp"
WEAPP_LOG=/tmp/homework-weapp.log
H5_LOG=/tmp/homework-h5.log
API_HEALTH=http://127.0.0.1:8080/api/v1/health
NO_H5=0
if [ "${1:-}" = '--no-h5' ]; then NO_H5=1; fi

step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
info() { echo "   $1"; }
ok()   { echo "   ✓ $1"; }

# ---------- 0. 前置检查 ----------
step '检查环境'
if [ ! -f .env ]; then
  echo '   缺少 .env（先 cp .env.example .env 并填密钥）'
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo '   未安装 docker：brew install colima docker docker-compose'
  exit 1
fi
ok '.env 与 docker 就绪'

# ---------- 1. Docker VM ----------
step 'Docker VM（colima）'
if docker info >/dev/null 2>&1; then
  ok 'VM 已在运行'
else
  info '启动 colima（首次约 1~2 分钟）...'
  colima start --cpu 4 --memory 8
  ok 'colima 已启动'
fi

# ---------- 2. 四件套容器 ----------
step '后端容器（MySQL / Redis / MinIO / API）'
if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  ok 'API 端口 8080 已有服务'
  info '确认是本项目容器...'
  RUNNING=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -c '^hw-api$' || true)
  if [ "$RUNNING" = 0 ]; then
    echo '   8080 被其他进程占用（非本项目容器），先处理它再重跑：'
    lsof -nP -iTCP:8080 -sTCP:LISTEN | tail -n +2 | head -3
    exit 1
  fi
fi
info 'docker compose up -d（已起的容器不动，缺镜像会自动构建）...'
DOCKER_BUILDKIT=1 docker compose up -d --build
ok '容器已就绪'

# ---------- 3. 等 API ----------
step '等待 API 就绪'
info "$API_HEALTH"
READY=0
i=0
while [ $i -lt 45 ]; do
  if curl -sf -m 3 "$API_HEALTH" >/dev/null 2>&1; then READY=1; break; fi
  sleep 2
  i=$((i + 1))
done
if [ "$READY" != 1 ]; then
  echo '   ✗ API 90 秒内未就绪，看日志：docker compose logs --tail 50 api'
  exit 1
fi
ok 'API 已就绪'

# ---------- 4. 小程序前端 ----------
step '小程序编译（dev:weapp watch）'

if [ ! -d "$MINIAPP/node_modules" ]; then
  info '首次运行，安装前端依赖...'
  (cd "$MINIAPP" && npm install)
fi

WEAPP_RUNNING=0
for pid in $(pgrep -f 'build:weapp -- --watch' 2>/dev/null); do
  PROC_CWD=$(lsof -p "$pid" 2>/dev/null | awk '$4=="cwd"{print $NF}')
  if [ "$PROC_CWD" = "$MINIAPP" ]; then WEAPP_RUNNING=1; fi
done

if [ "$WEAPP_RUNNING" = 1 ]; then
  ok 'weapp watch 已在运行'
else
  (cd "$MINIAPP" && nohup npm run dev:weapp > "$WEAPP_LOG" 2>&1 &)
  info '已后台启动，日志 /tmp/homework-weapp.log（首次编译约 40 秒）'
fi

# ---------- 5. H5 ----------
if [ "$NO_H5" = 0 ]; then
  step 'H5 调试页（dev:h5 watch，端口 10087）'
  if lsof -nP -iTCP:10087 -sTCP:LISTEN >/dev/null 2>&1; then
    ok '10087 已有服务在跑，跳过'
  else
    (cd "$MINIAPP" && nohup npm run dev:h5 -- --port 10087 > "$H5_LOG" 2>&1 &)
    info '已后台启动，日志 /tmp/homework-h5.log（约 30 秒后可用）'
  fi
fi

# ---------- 6. 汇总 ----------
step '完成'
echo ''
echo '   后端 API      http://127.0.0.1:8080'
echo '   H5 调试页     http://127.0.0.1:10087'
echo '   小程序产物    miniapp/dist/（微信开发者工具导入 miniapp/ 目录）'
echo '   数据          ./data/docker/（MySQL 3307 / MinIO 9002 / Redis 6380）'
echo ''
echo '   常用命令：'
echo '     docker compose logs -f api     # 后端日志'
echo '     ./scripts/smoke-test.sh        # 冒烟自检'
echo '     docker compose down            # 停后端（数据保留）'
echo '     colima stop                    # 关 Docker VM'
