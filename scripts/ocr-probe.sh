#!/usr/bin/env bash
#
# OCR 探活：拿一张作业图打一次腾讯云 OCR，看认出了哪些行、坐标是多少。
# 用来确认"行坐标拿得准不准"，比跑一次完整批改快得多，也不花模型的钱。
#
# 用法：
#   ./scripts/ocr-probe.sh                       # 用 samples/ 下第一张图
#   ./scripts/ocr-probe.sh samples/xxx.jpg       # 指定图片
#
set -euo pipefail

cd "$(dirname "$0")/.."

IMAGE="${1:-}"
if [ -z "$IMAGE" ]; then
  IMAGE=$(ls samples/*.jpg samples/*.jpeg samples/*.png 2>/dev/null | head -1)
fi
if [ -z "$IMAGE" ] || [ ! -f "$IMAGE" ]; then
  echo "错误：找不到作业图片。把图片放到 samples/ 下，或直接指定路径" >&2
  exit 2
fi

BASE="${BASE_URL:-http://127.0.0.1:8080}"

echo "识别：$IMAGE"
echo
curl -s -X POST "$BASE/api/demo/ocr" -F "file=@$IMAGE" \
  | python3 -c '
import json, sys

payload = json.load(sys.stdin)
if payload.get("code") != 0:
    print("失败：", payload.get("msg"))
    sys.exit(1)

data = payload["data"]
print("provider =", data["provider"], " 图片 =", f"{data[\"width\"]}x{data[\"height\"]}")
lines = data["lines"]
print("识别行数 =", len(lines))
print()
print(f"{'y':>7} {'h':>7}  {'x':>6} {'w':>6}  文本")
for line in lines:
    print(f"{line[\"y\"]:7.3f} {line[\"h\"]:7.3f}  {line[\"x\"]:6.3f} {line[\"w\"]:6.3f}  {line[\"text\"]}")
'
