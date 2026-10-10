#!/usr/bin/env bash
#
# 批改流水线端到端测试：上传 → 创建任务 → 轮询 → 取结果 → 校验落库。
#
# ⚠️ 会真实调用 DeepSeek。一页作业约 1~2 分钱，跑几次可以忽略，但不要放进 CI。
#    免费的自检请用 ./scripts/smoke-test.sh
#
# 用法：
#   ./scripts/pipeline-test.sh
#   RESET=1 ./scripts/pipeline-test.sh    # 先清空开发数据再跑，便于看清落库结果
#
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/load-env.sh
load_dotenv .

BASE="${BASE_URL:-http://127.0.0.1:8080}"
JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"
LOG=/tmp/homework-app.log
POLL_TIMEOUT=180

ensure_jar "$JAR"

if [ "${RESET:-0}" = "1" ]; then
  echo "先清空开发数据..."
  CONFIRM=yes ./scripts/reset-dev-data.sh
fi

STARTED=0
if ! curl -sf "$BASE/api/v1/health" >/dev/null 2>&1; then
  echo "服务未启动，正在拉起..."
  nohup java -jar "$JAR" > "$LOG" 2>&1 &
  STARTED=1
  for _ in $(seq 1 40); do
    sleep 2
    curl -sf "$BASE/api/v1/health" >/dev/null 2>&1 && break
  done
fi

cleanup() {
  if [ "$STARTED" = "1" ] && [ "${STOP_AFTER:-0}" = "1" ]; then
    pkill -f "homework-grader-0.0.1-SNAPSHOT.jar" || true
  fi
}
trap cleanup EXIT

FAILED=0
pass() { echo "  [OK]   $1"; }
fail() { echo "  [FAIL] $1"; FAILED=1; }

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

echo
echo "==================== 0. 预检 DeepSeek 密钥 ===================="
# 先花一次 1 token 的调用确认密钥有效。否则整条流程跑完才失败，排查成本高得多
KEY_CHECK=$(curl -s -X POST https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${DEEPSEEK_API_KEY:-}" \
  -d '{"model":"deepseek-flash","messages":[{"role":"user","content":"ping"}],"max_tokens":1}')
if echo "$KEY_CHECK" | grep -qi "invalid\|authentication"; then
  fail "DeepSeek 密钥无效：$(echo "$KEY_CHECK" | head -c 200)"
  echo "  请到 https://platform.deepseek.com/api_keys 重新生成，并更新 .env 的 DEEPSEEK_API_KEY"
  exit 2
fi
pass "DeepSeek 密钥有效"

SUFFIX="$(date +%s)"
USER_CODE="pipeline-$SUFFIX"
BIZ_NO="biz-$SUFFIX"

echo
echo "==================== 1. 登录并上传作业照片 ===================="
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" \
  -H 'Content-Type: application/json' \
  -d "{\"devIdentity\":\"$USER_CODE\",\"grade\":3,\"studentName\":\"测试孩子\"}")
TOKEN=$(echo "$LOGIN" | json_get data.token)
STUDENT_ID=$(echo "$LOGIN" | json_get data.student.id)
[ -n "$TOKEN" ] && pass "登录成功，studentId=$STUDENT_ID" || fail "登录失败"

IMAGE=$(ls samples/*.jpg samples/*.jpeg samples/*.png 2>/dev/null | head -1 || true)
if [ -z "$IMAGE" ]; then
  fail "samples/ 下没有测试图片"
  exit 1
fi

FILE_ID=$(curl -s -X POST "$BASE/api/v1/files/upload" \
  -H "Authorization: Bearer $TOKEN" -F "file=@$IMAGE" -F "bizType=1" | json_get data.fileId)
[ -n "$FILE_ID" ] && pass "上传成功 fileId=$FILE_ID（$IMAGE）" || fail "上传失败"

echo
echo "==================== 2. 创建批改任务 ===================="
CREATE_BODY="{\"bizNo\":\"$BIZ_NO\",\"studentId\":$STUDENT_ID,\"subject\":\"math\",\"grade\":3,\"fileIds\":[$FILE_ID]}"
CREATE=$(curl -s -X POST "$BASE/api/v1/homework/submits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$CREATE_BODY")
echo "$CREATE" | python3 -m json.tool
SUBMIT_ID=$(echo "$CREATE" | json_get data.submitId)
[ -n "$SUBMIT_ID" ] && pass "任务已创建 submitId=$SUBMIT_ID" || fail "创建任务失败"

echo
echo "==================== 3. 幂等：同一 bizNo 再提交 ===================="
REPEAT=$(curl -s -X POST "$BASE/api/v1/homework/submits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$CREATE_BODY")
REPEAT_ID=$(echo "$REPEAT" | json_get data.submitId)
[ "$REPEAT_ID" = "$SUBMIT_ID" ] && pass "返回同一任务，未重复创建（submitId=$REPEAT_ID）" \
  || fail "幂等失效，重复创建了 submitId=$REPEAT_ID"

echo
echo "==================== 4. 额度已扣一次 ===================="
QUOTA=$(curl -s "$BASE/api/v1/quota/today" -H "Authorization: Bearer $TOKEN")
echo "$QUOTA" | python3 -m json.tool
USED=$(echo "$QUOTA" | json_get data.used)
[ "$USED" = "1" ] && pass "重复提交只扣了一次额度" || fail "额度异常，used=$USED（期望 1）"

echo
echo "==================== 5. 轮询直到批改结束 ===================="
ELAPSED=0
STATUS=""
while [ "$ELAPSED" -lt "$POLL_TIMEOUT" ]; do
  STATUS_JSON=$(curl -s "$BASE/api/v1/homework/submits/$SUBMIT_ID" -H "Authorization: Bearer $TOKEN")
  STATUS=$(echo "$STATUS_JSON" | json_get data.status)
  FINISHED=$(echo "$STATUS_JSON" | json_get data.finished)
  echo "  ${ELAPSED}s 状态=$STATUS ($(echo "$STATUS_JSON" | json_get data.statusText))"
  if [ "$FINISHED" = "True" ]; then
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done

echo
echo "==================== 6. 取批改结果 ===================="
DETAIL=$(curl -s "$BASE/api/v1/homework/submits/$SUBMIT_ID/result" -H "Authorization: Bearer $TOKEN")
echo "$DETAIL" | python3 -c "
import sys, json
d = json.load(sys.stdin).get('data') or {}
s = d.get('submit') or {}
qs = d.get('questions') or []
print(f\"  状态：{s.get('statusText')}　题目 {s.get('totalCount')} 道，对 {s.get('rightCount')}，错 {s.get('wrongCount')}，得分 {s.get('score')}\")
print(f\"  原图 {len(d.get('images') or [])} 张\")
wrong = [q for q in qs if not q.get('correct')]
check = [q for q in qs if q.get('needsCheck')]
print(f\"  判错 {len(wrong)} 道，建议核对 {len(check)} 道\")
for q in qs[:5]:
    flag = '错' if not q.get('correct') else '对'
    bbox = '有' if q.get('bbox') else '无'
    print(f\"    [{flag}] {q.get('questionNo')} {q.get('stem')} → 学生:{q.get('studentAnswer')} 正确:{q.get('correctAnswer')} 置信度:{q.get('confidence')} 坐标:{bbox}\")
if len(qs) > 5:
    print(f'    ...（共 {len(qs)} 道）')
"

if [ "$STATUS" = "3" ]; then
  pass "批改成功结束"
else
  fail "批改未成功结束，最终状态=$STATUS"
  echo "$STATUS_JSON" | python3 -m json.tool
fi

echo
echo "==================== 7. 落库校验 ===================="
Q_COUNT=$(run_sql "SELECT COUNT(*) FROM t_question WHERE submit_id=$SUBMIT_ID;")
M_COUNT=$(run_sql "SELECT COUNT(*) FROM t_mistake WHERE submit_id=$SUBMIT_ID;")
LOG_COUNT=$(run_sql "SELECT COUNT(*) FROM t_ai_call_log WHERE submit_id=$SUBMIT_ID;")
SUB_ROW=$(run_sql "SELECT CONCAT(status,'|',IFNULL(total_count,0),'|',IFNULL(wrong_count,0),'|',IFNULL(cost,0)) FROM t_homework_submit WHERE id=$SUBMIT_ID;")

echo "  t_question 行数：$Q_COUNT"
echo "  t_mistake  行数：$M_COUNT"
echo "  t_ai_call_log 行数：$LOG_COUNT"
echo "  任务行 status|题目数|错题数|成本：$SUB_ROW"

[ "$Q_COUNT" -gt 0 ] && pass "题目已落库" || fail "题目没有落库"
[ "$LOG_COUNT" = "1" ] && pass "AI 调用日志已记录" || fail "AI 调用日志异常：$LOG_COUNT"

WRONG_IN_DB=$(echo "$SUB_ROW" | cut -d'|' -f3)
[ "$M_COUNT" = "$WRONG_IN_DB" ] && pass "错题数与错题本条目一致（$M_COUNT）" \
  || fail "错题本条目数($M_COUNT)与任务统计($WRONG_IN_DB)不符"

echo
echo "==================== 7b. 下载错题素材供人工核对 ===================="
MISTAKE_ID=$(run_sql "SELECT id FROM t_mistake WHERE submit_id=$SUBMIT_ID ORDER BY id LIMIT 1;" || true)
if [ -n "${MISTAKE_ID:-}" ]; then
  MISTAKE_DETAIL=$(curl -s "$BASE/api/v1/mistakes/$MISTAKE_ID" -H "Authorization: Bearer $TOKEN")
  CARD_URL=$(echo "$MISTAKE_DETAIL" | json_get data.mistake.cardImageUrl)
  CROP_URL=$(echo "$MISTAKE_DETAIL" | json_get data.cropImageUrl)
  mkdir -p out
  if [ -n "$CARD_URL" ]; then
    curl -s -o out/pipeline-mistake-card.png "$CARD_URL"
    pass "卡片已下载：out/pipeline-mistake-card.png（$(wc -c < out/pipeline-mistake-card.png | tr -d ' ') 字节）"
  else
    fail "错题没有卡片图"
  fi
  if [ -n "$CROP_URL" ]; then
    curl -s -o out/pipeline-mistake-crop.jpg "$CROP_URL"
    pass "裁剪已下载：out/pipeline-mistake-crop.jpg（$(wc -c < out/pipeline-mistake-crop.jpg | tr -d ' ') 字节）← 需人工核对裁得准不准"
  else
    # 没有裁剪图是允许的（OCR 没匹配上、或 OCR 不可用都会跳过），但要在报告里说清楚
    echo "  [WARN] 该错题没有原图裁剪 —— 只有拿到可靠坐标（OCR 行坐标匹配 / 手动框选）才会裁，"
  fi
else
  echo "  [SKIP] 本次批改没有错题，跳过素材下载"
fi

echo
echo "==================== 8. 越权：用户 B 读 A 的任务 ===================="
TOKEN_B=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" \
  -H 'Content-Type: application/json' -d "{\"devIdentity\":\"intruder-$SUFFIX\",\"grade\":3}" | json_get data.token)
CROSS=$(curl -s "$BASE/api/v1/homework/submits/$SUBMIT_ID/result" -H "Authorization: Bearer $TOKEN_B" | json_get code)
[ "$CROSS" = "403" ] && pass "跨用户读取任务被拒绝（403）" || fail "越权未被拦住，返回 $CROSS"

echo
if [ "$FAILED" = "0" ]; then
  echo "==================== 流水线全部通过 ===================="
else
  echo "==================== 存在失败项 ===================="
fi
exit "$FAILED"
