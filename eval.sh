#!/usr/bin/env bash
#
# 准确率评测：拿人工标注集去量模型，而不是靠"这次判错了几道题"猜。
#
# 用法：
#   ./eval.sh --export                      # 从 out/ 下最新一次批改结果导出标注草稿
#   ./eval.sh --export out/xxx/01-yyy.json  # 指定结果文件导出
#   ./eval.sh                               # 用 labels/sample-labels.csv 评测 samples/
#   ./eval.sh labels/hw1.csv                # 指定标注集
#   ./eval.sh labels/hw1.csv --repeat 5     # 覆盖重复次数（默认 3，单次结果噪声很大）
#   ./eval.sh labels/hw1.csv --demo.images=samples/a.jpg
#
set -euo pipefail

cd "$(dirname "$0")"
source scripts/load-env.sh

JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"

# ---------------------------------------------------------------- 导出标注草稿
if [ "${1:-}" = "--export" ]; then
  TARGET="${2:-}"
  if [ -z "$TARGET" ]; then
    TARGET="$(ls -t out/*/*.json 2>/dev/null | head -1 || true)"
    if [ -z "$TARGET" ]; then
      echo "错误：out/ 下找不到批改结果，先跑一次 ./run-demo.sh" >&2
      exit 2
    fi
  fi
  ensure_jar "$JAR"
  exec java -jar "$JAR" --demo.eval-export="$TARGET"
fi

# ---------------------------------------------------------------- 评测
load_dotenv .
if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "错误：未设置 DEEPSEEK_API_KEY。请在 .env 中填写，或 export DEEPSEEK_API_KEY=sk-xxx" >&2
  exit 2
fi

LABELS="labels/sample-labels.csv"
ARGS=()

# 第一个非选项参数视为标注集路径
if [ "$#" -gt 0 ] && [ "${1#-}" = "$1" ]; then
  LABELS="$1"
  shift
fi

if [ ! -f "$LABELS" ]; then
  echo "错误：标注集不存在：$LABELS" >&2
  echo "可以先用 ./eval.sh --export 生成草稿再核对" >&2
  exit 2
fi

ensure_jar "$JAR"

# 把 --repeat N 简写翻译成 --demo.eval-repeat=N，方便使用
has_target=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --repeat)
      ARGS+=("--demo.eval-repeat=${2:?--repeat 需要一个数字}")
      shift 2
      ;;
    --repeat=*)
      ARGS+=("--demo.eval-repeat=${1#*=}")
      shift
      ;;
    --demo.dir=*|--demo.images=*)
      has_target=1
      ARGS+=("$1")
      shift
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

# 没指定图片就默认 samples/ 目录
if [ "$has_target" -eq 0 ]; then
  ARGS=(--demo.dir=samples ${ARGS[@]+"${ARGS[@]}"})
fi

exec java -jar "$JAR" --demo.eval-labels="$LABELS" ${ARGS[@]+"${ARGS[@]}"}
