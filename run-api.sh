#!/usr/bin/env bash
#
# 启动 API 服务（常驻）。开发和联调用这个。
#
# 用法：
#   ./run-api.sh                                  # 默认 8080
#   SERVER_PORT=9090 ./run-api.sh                 # 换端口
#   STORAGE_LOCAL_FALLBACK=true ./run-api.sh      # COS 凭证没配好时用本地磁盘兜底
#
set -euo pipefail

cd "$(dirname "$0")"
source scripts/load-env.sh
load_dotenv .

if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "警告：未设置 DEEPSEEK_API_KEY，批改功能将不可用" >&2
fi
if [ -z "${DB_URL:-}" ]; then
  echo "错误：未设置 DB_URL。请复制 .env.example 为 .env 并填写数据库连接信息" >&2
  exit 2
fi

JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"
ensure_jar "$JAR"

echo "启动 API 服务：http://127.0.0.1:${SERVER_PORT:-8080}"
echo "配置自检：curl -s http://127.0.0.1:${SERVER_PORT:-8080}/api/v1/health | python3 -m json.tool"
exec java -jar "$JAR" "$@"
