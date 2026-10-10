#!/usr/bin/env bash
#
# 启动本地 MinIO（S3 兼容对象存储）。凭证从 .env 读取，不用 MinIO 默认的 minioadmin。
#
# 用法：
#   ./scripts/start-minio.sh          # 前台运行，Ctrl-C 停止
#   nohup ./scripts/start-minio.sh > /tmp/minio.log 2>&1 &   # 后台运行
#
# 控制台：http://127.0.0.1:9001
#
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/load-env.sh
load_dotenv .

# 应用侧叫 ACCESS_KEY/SECRET_KEY，MinIO 服务端叫 ROOT_USER/ROOT_PASSWORD，值是同一对
: "${MINIO_ACCESS_KEY:?请在 .env 中设置 MINIO_ACCESS_KEY}"
: "${MINIO_SECRET_KEY:?请在 .env 中设置 MINIO_SECRET_KEY}"

COMMAND="${MINIO_BIN:-minio}"
if ! command -v "$COMMAND" >/dev/null 2>&1; then
  echo "错误：找不到 $COMMAND，请先执行 brew install minio" >&2
  exit 2
fi

if lsof -nP -iTCP:9000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "MinIO 已在 9000 端口运行，无需重复启动"
  exit 0
fi

DATA_DIR="${MINIO_DATA_DIR:-./data/minio}"
mkdir -p "$DATA_DIR"

echo "启动 MinIO"
echo "  数据目录：$DATA_DIR"
echo "  S3 端点： http://127.0.0.1:9000"
echo "  控制台： http://127.0.0.1:9001"
echo "  访问密钥：$MINIO_ACCESS_KEY"

# MinIO 还会读 MINIO_ACCESS_KEY/SECRET_KEY 这两个已废弃的别名，
# 同时存在会打印 deprecation 警告，这里先摘掉再显式传 ROOT_USER/ROOT_PASSWORD
ACCESS_KEY="$MINIO_ACCESS_KEY"
SECRET_KEY="$MINIO_SECRET_KEY"
unset MINIO_ACCESS_KEY MINIO_SECRET_KEY

MINIO_ROOT_USER="$ACCESS_KEY" \
MINIO_ROOT_PASSWORD="$SECRET_KEY" \
  exec "$COMMAND" server "$DATA_DIR" --address ":9000" --console-address ":9001"
