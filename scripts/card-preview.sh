#!/usr/bin/env bash
#
# 错题本自检 + 卡片预览：
#   1. 塞一条固定的示例错题（不调 AI、不花钱）
#   2. 走一遍错题本接口：列表 / 详情 / 复习打卡 / 统计 / 重新生成卡片
#   3. 把卡片图下载到 out/card-preview.png，迭代排版时直接看效果
#
# 用法：
#   ./scripts/card-preview.sh
#   open out/card-preview.png
#
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/load-env.sh
load_dotenv .

BASE="${BASE_URL:-http://127.0.0.1:8080}"
JAR="target/homework-grader-0.0.1-SNAPSHOT.jar"
LOG=/tmp/homework-app.log
DEV_IDENTITY="cardpreview"
OUT="out/card-preview.png"

ensure_jar "$JAR"

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

echo "准备示例错题数据..."
# 用固定的 biz_no，重复运行不会产生重复数据
run_sql "
INSERT INTO t_user (open_id, nickname, role, quota_daily, status, deleted)
VALUES ('dev-openid-local-001:$DEV_IDENTITY', '卡片预览', 1, 999, 1, 0)
ON DUPLICATE KEY UPDATE nickname = nickname;
SET @uid = (SELECT id FROM t_user WHERE open_id = 'dev-openid-local-001:$DEV_IDENTITY');

INSERT INTO t_student (user_id, name, grade, sort_no, deleted)
SELECT @uid, '预览孩子', 3, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_student WHERE user_id = @uid LIMIT 1);
SET @sid = (SELECT id FROM t_student WHERE user_id = @uid ORDER BY id LIMIT 1);

INSERT INTO t_homework_submit (biz_no, user_id, student_id, subject, grade, status, image_count, total_count, right_count, wrong_count, score)
VALUES ('card-preview', @uid, @sid, 'math', 3, 3, 1, 1, 0, 1, 0)
ON DUPLICATE KEY UPDATE biz_no = biz_no;
SET @subid = (SELECT id FROM t_homework_submit WHERE biz_no = 'card-preview');

INSERT INTO t_question (submit_id, seq, question_no, question_type, stem, student_answer, correct_answer, is_correct, confidence, wrong_reason, analysis, knowledge_points, difficulty, page_seq, needs_check)
SELECT @subid, 1, '口算15', 5, '158+42+168=', '268', '368', 0, 0.500,
       '百位漏加进位，结果少了 100',
       '158+42=200，200+168=368。可用估算检验：160+40+170=370，268 明显偏小。',
       '[\"三位数连加\", \"进位加法\"]', 3, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_question WHERE submit_id = @subid LIMIT 1);
SET @qid = (SELECT id FROM t_question WHERE submit_id = @subid ORDER BY id LIMIT 1);

INSERT INTO t_mistake (user_id, student_id, question_id, submit_id, subject, grade, question_type, stem_snapshot, answer_snapshot, knowledge_key, mastery_level, review_count, right_streak, next_review_at, status)
SELECT @uid, @sid, @qid, @subid, 'math', 3, 5, '158+42+168=', '268', '三位数连加', 1, 1, 1, NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_mistake WHERE question_id = @qid LIMIT 1);
"

FAILED=0
pass() { echo "  [OK]   $1"; }
fail() { echo "  [FAIL] $1"; FAILED=1; }

echo
echo "==================== 1. 登录 ===================="
TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" -H 'Content-Type: application/json' \
  -d "{\"devIdentity\":\"$DEV_IDENTITY\",\"grade\":3}" | json_get data.token)
[ -n "$TOKEN" ] && pass "登录成功" || { fail "登录失败"; exit 1; }

echo
echo "==================== 2. 错题列表 ===================="
LIST=$(curl -s "$BASE/api/v1/mistakes?size=5" -H "Authorization: Bearer $TOKEN")
echo "$LIST" | python3 -m json.tool | head -18
MISTAKE_ID=$(echo "$LIST" | json_get data.records.0.id)
[ -n "$MISTAKE_ID" ] && pass "列表返回错题 mistakeId=$MISTAKE_ID" || fail "列表为空"

echo
echo "==================== 3. 错题详情 ===================="
DETAIL=$(curl -s "$BASE/api/v1/mistakes/$MISTAKE_ID" -H "Authorization: Bearer $TOKEN")
echo "$DETAIL" | python3 -c "
import sys, json
d = json.load(sys.stdin).get('data') or {}
m = d.get('mistake') or {}
print(f\"  题干：{m.get('stem')}　我的答案：{m.get('studentAnswer')}\")
print(f\"  正确答案：{d.get('correctAnswer')}　知识点：{d.get('knowledgePoints')}\")
print(f\"  掌握度：{m.get('masteryLevel')}　复习次数：{m.get('reviewCount')}\")
print(f\"  卡片图：{'有' if m.get('cardImageUrl') else '无'}　原图裁剪：{'有' if d.get('cropImageUrl') else '无'}\")
"
[ "$(echo "$DETAIL" | json_get data.correctAnswer)" = "368" ] \
  && pass "详情带回正确答案" || fail "详情缺少正确答案"

echo
echo "==================== 4. 复习打卡（答对升级） ===================="
BEFORE=$(echo "$DETAIL" | json_get data.mistake.masteryLevel)
AFTER_JSON=$(curl -s -X POST "$BASE/api/v1/mistakes/$MISTAKE_ID/review" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"correct":true}')
AFTER=$(echo "$AFTER_JSON" | json_get data.mistake.masteryLevel)
echo "  掌握度 $BEFORE → $AFTER，下次复习 $(echo "$AFTER_JSON" | json_get data.mistake.nextReviewAt)"
[ "$AFTER" -gt "$BEFORE" ] && pass "答对后掌握度提升，并排了下次复习" \
  || fail "掌握度没有变化（$BEFORE → $AFTER）"

echo
echo "==================== 5. 复习打卡（答错降级） ===================="
DOWN_JSON=$(curl -s -X POST "$BASE/api/v1/mistakes/$MISTAKE_ID/review" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"correct":false}')
DOWN=$(echo "$DOWN_JSON" | json_get data.mistake.masteryLevel)
echo "  掌握度 $AFTER → $DOWN"
[ "$DOWN" -lt "$AFTER" ] && pass "答错后掌握度下降" || fail "答错没有降级（$AFTER → $DOWN）"

echo
echo "==================== 6. 统计 ===================="
curl -s "$BASE/api/v1/mistakes/stats" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
TOTAL=$(curl -s "$BASE/api/v1/mistakes/stats" -H "Authorization: Bearer $TOKEN" | json_get data.total)
[ "$TOTAL" -gt 0 ] && pass "统计返回 total=$TOTAL" || fail "统计异常"

echo
echo "==================== 7. 生成并下载卡片 ===================="
CARD=$(curl -s -X POST "$BASE/api/v1/mistakes/$MISTAKE_ID/card" -H "Authorization: Bearer $TOKEN")
URL=$(echo "$CARD" | json_get data.cardImageUrl)
[ -n "$URL" ] || { fail "卡片生成失败：$CARD"; exit 0; }

mkdir -p out
curl -s -o "$OUT" "$URL"
SIZE=$(wc -c < "$OUT" | tr -d ' ')
CONTENT_TYPE=$(curl -s -o /dev/null -w '%{content_type}' "$URL")
echo "  下载：$CONTENT_TYPE，${SIZE} 字节"
case "$CONTENT_TYPE" in
  image/*) pass "卡片图可下载（$OUT）" ;;
  *)       fail "卡片地址未返回图片：$CONTENT_TYPE" ;;
esac

echo
echo "==================== 8. 越权：别的用户读这张错题 ===================="
OTHER_TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/wx-login" -H 'Content-Type: application/json' \
  -d '{"devIdentity":"someone-else"}' | json_get data.token)
CROSS=$(curl -s "$BASE/api/v1/mistakes/$MISTAKE_ID" -H "Authorization: Bearer $OTHER_TOKEN" | json_get code)
[ "$CROSS" = "403" ] && pass "跨用户读取错题被拒绝（403）" || fail "越权未被拦住，返回 $CROSS"

echo
if [ "$FAILED" = "0" ]; then
  echo "==================== 错题本自检全部通过 ===================="
  echo "卡片预览：open $OUT"
else
  echo "==================== 存在失败项 ===================="
fi
exit "$FAILED"
