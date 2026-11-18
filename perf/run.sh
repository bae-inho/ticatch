#!/usr/bin/env bash
# 측정 한 번: DB를 측정 직전 상태로 되돌리고, k6 시나리오를 돌리고, 정합성을 확인한다.
# 사용법: perf/run.sh <시나리오>   예: perf/run.sh rush
set -euo pipefail

scenario="${1:?시나리오 이름을 주세요 (warmup, rush, hot, steady)}"
cd "$(dirname "$0")/.."
mkdir -p perf/results

# Git Bash(Windows)에서는 Docker에 넘길 경로를 C:/... 형태로 바꿔야 한다.
perf_dir="$(cd perf && (pwd -W 2>/dev/null || pwd))"

psql() {
    docker compose exec -T postgres psql -U ticatch -d ticatch_perf "$@"
}

psql -q -v ON_ERROR_STOP=1 < perf/sql/reset.sql
# 시나리오에 필요한 데이터가 따로 있으면 넣는다. 예: cancel → perf/sql/cancel.sql
if [ -f "perf/sql/${scenario}.sql" ]; then
    psql -q -v ON_ERROR_STOP=1 -v reservations="${RESERVATIONS:-1000}" < "perf/sql/${scenario}.sql"
fi

waits_file="$(mktemp)"
psql -q < perf/sql/waits.sql 2> "${waits_file}" &
sampler=$!

MSYS_NO_PATHCONV=1 docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    -v "${perf_dir}:/perf" \
    -e BASE_URL="${BASE_URL:-http://host.docker.internal:8080}" \
    -e RESERVATIONS="${RESERVATIONS:-1000}" \
    -e BACKGROUND_SECONDS="${BACKGROUND_SECONDS:-20}" \
    grafana/k6:1.3.0 run --quiet "/perf/${scenario}.js"

psql -q -c "SELECT pg_cancel_backend(pid) FROM pg_stat_activity WHERE application_name = 'perf-waits'" > /dev/null
wait "${sampler}" || true

if [ "${scenario}" != "warmup" ]; then
    # 앱 연결이 하나라도 일하던 표본만 센다(로그인 같은 준비 단계도 포함된다).
    sed -n 's/.*sample busy=\([0-9]*\) lock=\([0-9]*\).*/\1 \2/p' "${waits_file}" | awk '
        $1 > 0 { n++; busy += $1; lock += $2; if ($1 > maxBusy) maxBusy = $1; if ($2 > maxLock) maxLock = $2 }
        END { if (n) printf "  DB 연결  표본=%d 평균 사용=%.1f 최대 사용=%d 평균 잠금 대기=%.1f 최대 잠금 대기=%d\n", n, busy / n, maxBusy, lock / n, maxLock }'
    psql < perf/sql/check.sql
fi
rm -f "${waits_file}"
