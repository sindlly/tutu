#!/usr/bin/env bash
#
# 加载项目根目录的 .env 到环境变量。被 run-demo.sh / eval.sh 共用。
#
# 刻意手动解析而不是 source .env：避免执行文件里的任意 shell 代码，
# 也避免密钥含特殊字符（$、空格、引号）时被 shell 展开出错。

load_dotenv() {
  local root="${1:-.}"
  local file="$root/.env"
  [ -f "$file" ] || return 0

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
      ''|\#*) continue ;;
    esac
    local key="${line%%=*}"
    local value="${line#*=}"
    # 去掉首尾空白与包裹的引号
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    value="${value%\"}"
    value="${value#\"}"
    # 外部已显式 export 的变量优先于 .env（与 docker compose env_file 语义一致）。
    # 场景：Docker 栈的 MySQL 映射在 3307，跑 pipeline-test 时要覆盖 DB_URL：
    #   DB_URL=jdbc:mysql://127.0.0.1:3307/... ./scripts/pipeline-test.sh
    if [ -n "${!key:-}" ]; then
      continue
    fi
    export "$key=$value"
  done < "$file"
}

# 确保打包产物存在，不存在则编译
ensure_jar() {
  local jar="$1"
  if [ ! -f "$jar" ]; then
    echo "首次运行，正在编译..."
    mvn -q -B -DskipTests package
  fi
}

# 找 mysql 客户端。用户自装的 MySQL 通常不在 PATH 上。
find_mysql() {
  if [ -n "${MYSQL_BIN:-}" ] && [ -x "${MYSQL_BIN}" ]; then
    echo "$MYSQL_BIN"; return 0
  fi
  if command -v mysql >/dev/null 2>&1; then
    command -v mysql; return 0
  fi
  for candidate in "$HOME"/mysql/mysql-*/bin/mysql /opt/homebrew/opt/mysql/bin/mysql; do
    if [ -x "$candidate" ]; then echo "$candidate"; return 0; fi
  done
  return 1
}

# 从 JDBC URL 拆出 host / port / 库名，避免再维护一份重复配置
# 用法：parse_db_url 之后可使用 DBHOST / DBPORT / DBNAME
parse_db_url() {
  local url="${DB_URL#jdbc:mysql://}"
  local hostport="${url%%/*}"
  local rest="${url#*/}"
  DBNAME="${rest%%\?*}"
  DBHOST="${hostport%%:*}"
  DBPORT="${hostport##*:}"
}

# 执行一段 SQL（结果直接打到 stdout）
run_sql() {
  local sql="$1"
  local mysql_bin
  mysql_bin="$(find_mysql)" || {
    echo "错误：找不到 mysql 客户端，可用 MYSQL_BIN=/path/to/mysql 指定" >&2
    return 2
  }
  parse_db_url
  MYSQL_PWD="$DB_PASSWORD" "$mysql_bin" -h "$DBHOST" -P "$DBPORT" -u "$DB_USERNAME" \
    "$DBNAME" --batch --skip-column-names -e "$sql"
}
