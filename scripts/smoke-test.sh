#!/usr/bin/env bash
#
# API 冒烟测试：起服务 → 健康检查 → 登录 → 上传 → 私有读地址 → 越权校验。
# 每次改动后跑一遍，比手工点页面快得多，也能挡住"改了 A 弄坏 B"。
#
# 用法：
#   ./scripts/smoke-test.sh              # 服务没起就自动起，跑完留着
#   STOP_AFTER=1 ./scripts/smoke-test.sh # 跑完停掉自己起的服务
#
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/load-env.sh
load_dotenv .

BASE="${BASE_URL:-http://127.0.0.1:8080}"
JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"
LOG=/tmp/homework-app.log

ensure_jar "$JAR"

STARTED=0
if ! curl -sf "$BASE/api/v1/health" >/dev/null 2>&1; then
  echo "服务未启动，正在拉起（存储实现由 .env 的 STORAGE_PROVIDER 决定）..."
  nohup java -jar "$JAR" > "$LOG" 2>&1 &
  STARTED=1
  for _ in $(seq 1 40); do
    sleep 2
    curl -sf "$BASE/api/v1/health" >/dev/null 2>&1 && break
  done
fi

stop_started() {
  if [ "$STARTED" = "1" ] && [ "${STOP_AFTER:-0}" = "1" ]; then
    echo "停止本次启动的服务"
    pkill -f "homework-grader-0.0.1-SNAPSHOT.jar" || true
  fi
}
trap stop_started EXIT

# 取响应里的字段，路径用点号分隔，例如 json_get data.token；取不到返回空串而不是报错
json_get() {
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print(''); sys.exit(0)
for k in '$1'.split('.'):
    if isinstance(d, dict):
        d = d.get(k)
    elif isinstance(d, list):
        try:
            d = d[int(k)]
        except (ValueError, IndexError):
            d = None
    else:
        d = None
    if d is None:
        break
print(d if d is not None else '')
"
}

pass() { echo "  [OK]   $1"; }
fail() { echo "  [FAIL] $1"; FAILED=1; }
FAILED=0

echo
echo "==================== 1. 健康检查 ===================="
HEALTH=$(curl -s "$BASE/api/v1/health")
echo "$HEALTH" | python3 -m json.tool
CODES=$(echo "$HEALTH" | json_get code)
[ "$CODES" = "0" ] && pass "健康检查返回 code=0" || fail "健康检查异常"

STORAGE=$(echo "$HEALTH" | json_get data.storage)
if [ "$STORAGE" = "unavailable" ]; then
  fail "对象存储未配置，上传链路无法验证。请检查 .env 的 STORAGE_PROVIDER 与对应凭证"
else
  pass "对象存储就绪：$STORAGE"
fi
[ "$(echo "$HEALTH" | json_get data.deepSeekConfigured)" = "True" ] \
  && pass "DeepSeek 密钥已配置" || fail "DeepSeek 密钥未配置，批改功能不可用"

echo
echo "==================== 2. 未登录应被拒绝 ===================="
NOAUTH_CODE=$(curl -s "$BASE/api/v1/files/presign?fileId=1" | json_get code)
[ "$NOAUTH_CODE" = "401" ] && pass "无 token 返回 401" || fail "无 token 竟然返回 $NOAUTH_CODE"

echo
echo "==================== 3. 登录（用户 A） ===================="
LOGIN_A=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" \
  -H 'Content-Type: application/json' -d '{"devIdentity":"alice","grade":3,"studentName":"大宝"}')
echo "$LOGIN_A" | python3 -m json.tool
TOKEN_A=$(echo "$LOGIN_A" | json_get data.token)
STUDENT_A=$(echo "$LOGIN_A" | json_get data.student.id)
[ -n "$TOKEN_A" ] && pass "拿到 token" || fail "登录未返回 token"

echo
echo "==================== 3b. 真实微信登录链路 ===================="
if [ "$(echo "$HEALTH" | json_get data.wxConfigured)" = "True" ]; then
  # 故意用无效 code 打真实微信接口：微信会返回 errcode=40029，
  # 能收到这个错误码就说明 AppID/Secret 有效、HTTP 调用与错误码映射都通了
  WX=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" \
    -H 'Content-Type: application/json' -d '{"code":"invalid-code-for-smoke-test"}')
  echo "$WX" | python3 -m json.tool
  WX_CODE=$(echo "$WX" | json_get code)
  WX_MSG=$(echo "$WX" | json_get msg)
  if [ "$WX_CODE" = "400" ]; then
    pass "真实微信链路已接通：无效 code 被正确拒绝"
  elif [ "$WX_CODE" = "500" ]; then
    fail "微信调用失败，AppID/Secret 可能不正确（$WX_MSG）"
  else
    fail "微信登录返回异常 $WX_CODE：$WX_MSG"
  fi
else
  echo "  [SKIP] 未配置 AppID，跳过真实微信链路验证"
fi

echo
echo "==================== 4. 上传作业图片 ===================="
IMAGE=$(ls samples/*.jpg samples/*.jpeg samples/*.png 2>/dev/null | head -1 || true)
if [ -z "$IMAGE" ]; then
  fail "samples/ 下没有测试图片，跳过上传链路"
else
  UPLOAD=$(curl -s -X POST "$BASE/api/v1/files/upload" \
    -H "Authorization: Bearer $TOKEN_A" \
    -F "file=@$IMAGE" -F "bizType=1")
  echo "$UPLOAD" | python3 -m json.tool
  FILE_ID=$(echo "$UPLOAD" | json_get data.fileId)
  [ -n "$FILE_ID" ] && pass "上传成功 fileId=$FILE_ID" || fail "上传失败"

  echo
  echo "==================== 5. 取私有读地址 ===================="
  PRESIGN=$(curl -s "$BASE/api/v1/files/presign?fileId=$FILE_ID" -H "Authorization: Bearer $TOKEN_A")
  echo "$PRESIGN" | python3 -m json.tool
  URL=$(echo "$PRESIGN" | json_get data.url)
  [ -n "$URL" ] && pass "拿到预签名地址" || fail "未拿到预签名地址"

  echo
  echo "==================== 5b. 预签名地址能真正取回图片 ===================="
  CONTENT_TYPE=$(curl -s -o /tmp/smoke-downloaded -w '%{content_type}' "$URL")
  DL_SIZE=$(wc -c < /tmp/smoke-downloaded | tr -d ' ')
  echo "  下载结果：content-type=$CONTENT_TYPE size=${DL_SIZE}字节"
  case "$CONTENT_TYPE" in
    image/*) pass "预签名地址返回图片（$CONTENT_TYPE，$DL_SIZE 字节）" ;;
    *)       fail "预签名地址未返回图片：$CONTENT_TYPE" ;;
  esac

  echo
  echo "==================== 6. 越权校验（用户 B 读 A 的文件） ===================="
  LOGIN_B=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" \
    -H 'Content-Type: application/json' -d '{"devIdentity":"bob","grade":4}')
  TOKEN_B=$(echo "$LOGIN_B" | json_get data.token)
  STUDENT_B=$(echo "$LOGIN_B" | json_get data.student.id)
  [ "$STUDENT_A" != "$STUDENT_B" ] && pass "两个账号确实是不同用户" || fail "开发模式未区分用户，越权用例无效"

  CROSS=$(curl -s "$BASE/api/v1/files/presign?fileId=$FILE_ID" -H "Authorization: Bearer $TOKEN_B")
  echo "$CROSS" | python3 -m json.tool
  CROSS_CODE=$(echo "$CROSS" | json_get code)
  [ "$CROSS_CODE" = "403" ] && pass "跨用户读取被拒绝（403）" || fail "越权未被拦住，返回 $CROSS_CODE"

  echo
  echo "==================== 7. 非法文件类型应被拒绝 ===================="
  echo "this is definitely not an image" > /tmp/not-an-image.txt
  BAD=$(curl -s -X POST "$BASE/api/v1/files/upload" \
    -H "Authorization: Bearer $TOKEN_A" \
    -F "file=@/tmp/not-an-image.txt" -F "bizType=1")
  echo "$BAD" | python3 -m json.tool
  BAD_CODE=$(echo "$BAD" | json_get code)
  [ "$BAD_CODE" = "400" ] && pass "非图片文件被拒绝（400）" || fail "非图片文件未被拦住，返回 $BAD_CODE"

  echo
  echo "==================== 8. 客户端不得指定服务端图片类型 ===================="
  BIZ=$(curl -s -X POST "$BASE/api/v1/files/upload" \
    -H "Authorization: Bearer $TOKEN_A" \
    -F "file=@$IMAGE" -F "bizType=3")
  BIZ_CODE=$(echo "$BIZ" | json_get code)
  [ "$BIZ_CODE" = "400" ] && pass "bizType=3（错题卡片）被拒绝" || fail "服务端图片类型被客户端指定，返回 $BIZ_CODE"
fi

echo
echo "==================== 9. 退出登录后 token 失效 ===================="
curl -s -X POST "$BASE/api/v1/auth/logout" -H "Authorization: Bearer $TOKEN_A" > /dev/null
AFTER_CODE=$(curl -s "$BASE/api/v1/files/presign?fileId=1" -H "Authorization: Bearer $TOKEN_A" | json_get code)
[ "$AFTER_CODE" = "401" ] && pass "退出后 token 已吊销（401）" || fail "退出后 token 仍可用，返回 $AFTER_CODE"

echo
if [ "$FAILED" = "0" ]; then
  echo "==================== 全部通过 ===================="
else
  echo "==================== 存在失败项 ===================="
fi
exit "$FAILED"
