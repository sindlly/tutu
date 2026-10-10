#!/usr/bin/env bash
#
# 清空本地开发数据（表结构由 Flyway 保留，不动）。
# 典型场景：想从干净状态跑一遍冒烟测试，或者切换了存储实现要丢掉旧的文件记录。
#
# 这是破坏性操作，必须显式确认：
#   CONFIRM=yes ./scripts/reset-dev-data.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/load-env.sh
load_dotenv .

if [ "${CONFIRM:-}" != "yes" ]; then
  echo "这会清空本地开发库里的全部业务数据（表结构保留）。" >&2
  echo "确认请执行：CONFIRM=yes ./scripts/reset-dev-data.sh" >&2
  exit 2
fi

: "${DB_URL:?未设置 DB_URL}"

run_sql "
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE t_mistake_review;
TRUNCATE t_mistake;
TRUNCATE t_question;
TRUNCATE t_submit_image;
TRUNCATE t_homework_submit;
TRUNCATE t_ai_call_log;
TRUNCATE t_user_usage;
TRUNCATE t_file;
TRUNCATE t_student;
TRUNCATE t_user;
SET FOREIGN_KEY_CHECKS = 1;
"

echo "完成。表结构仍在，下次启动会用 Flyway 校验版本。"
