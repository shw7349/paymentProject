#!/usr/bin/env bash
#
# 같은 상품(productId)에 동시 재고 차감 요청을 쏘아 각 동시성 방어 전략이 어떻게 동작/오작동하는지
# 관찰하는 부하 스크립트.
#
# 사용법:
#   scripts/concurrency-load.sh [strategy] [concurrency] [initial_stock] [product_id] [base_url ...]
#
# 예)
#   # 단일 인스턴스, 방어 없음 → oversell 관찰
#   scripts/concurrency-load.sh none 50 30
#
#   # 조건부 UPDATE → 정확히 initial 만큼만 성공
#   scripts/concurrency-load.sh conditional-update 50 30
#
#   # 다중 인스턴스(8080, 8081)에 synchronized 전략 → 프로세스 락이 뚫려 oversell 재현
#   scripts/concurrency-load.sh synchronized 50 30 order-1 http://localhost:8080 http://localhost:8081
#
# 전략: none | synchronized | for-update | redis-setnx | conditional-update
set -euo pipefail

STRATEGY="${1:-none}"
CONCURRENCY="${2:-50}"
INITIAL_STOCK="${3:-30}"
PRODUCT_ID="${4:-load-$(date +%s)}"
shift $(( $# < 4 ? $# : 4 )) || true
BASE_URLS=("$@")
if [ "${#BASE_URLS[@]}" -eq 0 ]; then
  BASE_URLS=("http://localhost:8080")
fi

RESET_URL="${BASE_URLS[0]}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "== 동시성 실험 =="
echo "strategy      : $STRATEGY"
echo "concurrency   : $CONCURRENCY"
echo "initial stock : $INITIAL_STOCK"
echo "product_id    : $PRODUCT_ID"
echo "instances     : ${BASE_URLS[*]}"
echo

# 1) 재고 초기화
curl -s -X POST "$RESET_URL/api/concurrency/reset?productId=$PRODUCT_ID&quantity=$INITIAL_STOCK" > /dev/null
echo "재고를 $INITIAL_STOCK 로 초기화했습니다."

# 2) 동시 차감 요청 — 각 요청의 HTTP 상태코드만 파일에 기록
echo "동시 요청 $CONCURRENCY 건 발사..."
START_TS=$(date +%s.%N)
for i in $(seq 1 "$CONCURRENCY"); do
  url="${BASE_URLS[$(( (i - 1) % ${#BASE_URLS[@]} ))]}"
  (
    code=$(curl -s -o /dev/null -w '%{http_code}' \
      -X POST "$url/api/concurrency/$STRATEGY/decrement?productId=$PRODUCT_ID")
    echo "$code" >> "$TMP_DIR/codes"
  ) &
done
wait
END_TS=$(date +%s.%N)

# 3) 결과 집계
SUCCESS=$(grep -c '^200$' "$TMP_DIR/codes" || true)
SOLD_OUT=$(grep -c '^409$' "$TMP_DIR/codes" || true)
LOCK_FAILED=$(grep -c '^429$' "$TMP_DIR/codes" || true)
OTHER=$(grep -cvE '^(200|409|429)$' "$TMP_DIR/codes" || true)

LEFT=$(curl -s "$RESET_URL/api/concurrency/stock?productId=$PRODUCT_ID" \
  | sed -E 's/.*"quantity":([0-9-]+).*/\1/')
ELAPSED=$(awk "BEGIN {printf \"%.2f\", $END_TS - $START_TS}")

# 실제로 줄어든 재고 = initial - left. 성공 응답 수와 다르면 lost update(oversell)가 있었다는 뜻.
ACTUAL_DECREASED=$(( INITIAL_STOCK - LEFT ))

echo
echo "== 결과 =="
printf '성공(200)        : %s\n' "$SUCCESS"
printf '재고소진(409)    : %s\n' "$SOLD_OUT"
printf '락실패(429)      : %s\n' "$LOCK_FAILED"
printf '기타 상태코드    : %s\n' "$OTHER"
printf '남은 재고        : %s\n' "$LEFT"
printf '실제 차감량      : %s (= %s - %s)\n' "$ACTUAL_DECREASED" "$INITIAL_STOCK" "$LEFT"
printf '소요 시간        : %ss\n' "$ELAPSED"
echo

if [ "$LEFT" -lt 0 ]; then
  echo "❌ OVERSELL: 재고가 음수($LEFT)입니다. 방어가 뚫렸습니다."
elif [ "$SUCCESS" -ne "$ACTUAL_DECREASED" ]; then
  echo "❌ LOST UPDATE: 성공 응답($SUCCESS) ≠ 실제 차감량($ACTUAL_DECREASED). 갱신 손실이 있었습니다."
elif [ "$ACTUAL_DECREASED" -gt "$INITIAL_STOCK" ]; then
  echo "❌ OVERSELL: 초기 재고보다 많이 팔렸습니다."
else
  echo "✅ 정합성 OK: 성공 수 = 실제 차감량 = $SUCCESS, 재고 음수 없음."
fi
