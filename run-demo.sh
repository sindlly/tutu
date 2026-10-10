#!/usr/bin/env bash
#
# 一条命令跑批改验证：自动加载 .env 里的密钥、必要时编译、然后执行批改。
#
# 用法：
#   ./run-demo.sh                                # 批改 samples/ 目录，完整批改模式
#   ./run-demo.sh --demo.mode=ocr                # 只识别不判分
#   ./run-demo.sh --demo.images=a.jpg,b.jpg      # 指定图片
#   ./run-demo.sh --deepseek.thinking.enabled=true
#
# 要做准确率评测请用 ./eval.sh
#
set -euo pipefail

cd "$(dirname "$0")"
source scripts/load-env.sh
load_dotenv .

if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "错误：未设置 DEEPSEEK_API_KEY。请在 .env 中填写，或 export DEEPSEEK_API_KEY=sk-xxx" >&2
  exit 2
fi

JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"
ensure_jar "$JAR"

ARGS=("$@")

# 只要没显式指定图片，就默认批改 samples/ 目录。
# 否则像 `./run-demo.sh --demo.mode=ocr` 这种只带修饰参数的情况会因为找不到图片而挂起当服务跑。
has_target=0
for arg in ${ARGS[@]+"${ARGS[@]}"}; do
  case "$arg" in
    --demo.dir=*|--demo.images=*) has_target=1 ;;
  esac
done
if [ "$has_target" -eq 0 ]; then
  ARGS=("--demo.dir=samples" ${ARGS[@]+"${ARGS[@]}"})
fi

exec java -jar "$JAR" "${ARGS[@]}"
