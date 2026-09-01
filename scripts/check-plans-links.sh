#!/usr/bin/env bash
# docs/plans/ 페어 참조 링크 가드
#
# 왜 필요한가 (근본 원인):
#   docs/plans/ 는 hybrid 구조다 — 활성 작업은 페어 파일, 완료 작업은 topic ledger entry.
#   컨벤션(`docs/plans/README.md` 워크플로 3항)은 머지 후 "ledger entry 추가 + 페어 git rm"
#   까지만 정한다. **다른 문서가 그 페어를 참조하고 있어도 아무도 고치지 않는다.**
#   그래서 이관할 때마다 링크가 조용히 죽는다. 본 가드 도입 시점 실측(2026-09-01):
#   10개 파일에 **26건**이 이미 끊겨 있었다 — CHANGELOG 11 · CLAUDE.md 3 ·
#   operations-snapshot 3 · incident-log 2 · migration-runbook 2 · 기타 5.
#   죽은 링크는 "그 근거를 읽을 수 없다" 는 뜻이라 문서의 추적성이 통째로 무너진다.
#
# 무엇을 검사하는가:
#   추적되는 모든 .md 안의 `docs/plans/....md` 경로가 실제로 존재하는지.
#   존재하지 않으면 파일명의 날짜로 ledger 후보를 찾아 함께 알려준다.
#
# 예외 규칙 — **같은 줄에 리다이렉트가 있으면 통과**:
#   죽은 페어 참조라도 그 줄이 실재하는 `docs/plans/logs/<topic>.md` 를 함께 가리키면
#   위반이 아니다. 참조에는 두 종류가 있고 처리가 다르기 때문이다.
#     · 길잡이 참조("설계 문서: …") → 경로를 ledger 로 **교체**한다.
#     · 이력 기록(CHANGELOG 의 "Added `…-design.md`") → 그 릴리스가 실제로 그 파일을
#       추가한 것이 사실이므로 **교체하면 이력이 왜곡된다.** 원문을 두고 리다이렉트를
#       덧붙인다.
#   두 경우 모두 "죽은 참조 옆에는 반드시 갈 곳이 적혀 있다" 는 하나의 불변식으로 수렴한다.
#
# 무엇을 검사하지 *않는가* (의도적 제외):
#   - `docs/plans/logs/**` — ledger entry 는 자기가 흡수한 페어를 **출처(provenance)로**
#     인용한다. 그 파일은 정의상 삭제된 것이므로 dead link 가 정상이다. 여기까지 막으면
#     ledger 가 출처를 못 적는다.
#   - `docs/plans/_templates/**` — 템플릿의 예시 경로.
#   - `docs/plans/_staging/**` 로의 참조 — gitignored scratch 폴더라 애초에 참조하면 안 된다.
#     (참조가 있으면 그것 자체가 오류이므로 일반 규칙대로 걸린다.)
#
# 사용:
#   bash scripts/check-plans-links.sh          # 검사 (위반 시 exit 1)
#   bash scripts/check-plans-links.sh --list   # 위반만 나열, exit 0 (조사용)
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

LIST_ONLY=0
[ "${1:-}" = "--list" ] && LIST_ONLY=1

LEDGERS="docs/plans/logs"
violations=0

# 추적 대상 .md (ledger·템플릿 제외)
while IFS= read -r file; do
  case "$file" in
    docs/plans/logs/*|docs/plans/_templates/*) continue ;;
  esac
  [ -f "$file" ] || continue

  # 한 줄에 여러 참조가 있을 수 있으므로 -o 로 전부 뽑는다
  while IFS= read -r ref; do
    [ -n "$ref" ] || continue

    # `{a,b}` 축약형은 여러 파일을 가리키는 한 표기다(`…-{design,plan}.md`,
    # `logs/{android,backend}.md`). 이 저장소가 가장 많이 쓰는 형태인데 예전 정규식이
    # `{`,`,`,`}` 를 문자 클래스에 넣지 않아 **통째로 보이지 않았다** — 그 사각지대에
    # 죽은 참조가 숨어 있었다(2026-09-02 발견, 실측 7건).
    # 판정은 확장한 경로 **전부**를 본다: 하나라도 없으면 그 표기는 깨진 것이다.
    if case "$ref" in *"{"*"}"*) true ;; *) false ;; esac; then
      prefix="${ref%%\{*}"
      rest="${ref#*\{}"
      alts="${rest%%\}*}"
      suffix="${rest#*\}}"
      all_present=1
      old_ifs=$IFS
      IFS=','
      for alt in $alts; do
        [ -f "${prefix}${alt}${suffix}" ] || all_present=0
      done
      IFS=$old_ifs
      [ "$all_present" -eq 1 ] && continue
    else
      [ -f "$ref" ] && continue
    fi

    line=$(grep -n -F -- "$ref" "$file" | head -1 | cut -d: -f1)

    # 예외: 같은 줄이 실재하는 ledger 를 함께 가리키면 리다이렉트가 있는 것으로 본다.
    # 상대형(`logs/android.md`)도 받는다 — 문장 안에서는 그렇게 쓰는 것이 자연스럽고,
    # 절대형만 받으면 리다이렉트를 제대로 달아 둔 줄이 위반으로 잡힌다(2026-09-02 실측 2건).
    if [ -n "$line" ]; then
      redirect=$(sed -n "${line}p" "$file" \
        | grep -oE '(docs/plans/)?logs/[a-z-]+\.md' | head -1)
      if [ -n "$redirect" ]; then
        case "$redirect" in
          docs/plans/*) redirect_path="$redirect" ;;
          *)            redirect_path="docs/plans/$redirect" ;;
        esac
        [ -f "$redirect_path" ] && continue
      fi
    fi

    violations=$((violations + 1))
    base=$(basename "$ref")
    date=$(printf '%s' "$base" | grep -oE '^[0-9]{4}-[0-9]{2}-[0-9]{2}' || true)

    hint=""
    if [ -n "$date" ]; then
      # 날짜가 등장하는 ledger 를 후보로 제시 (사람이 최종 판단)
      cands=$(grep -l -- "$date" "$LEDGERS"/*.md 2>/dev/null | sed 's|.*/||' | tr '\n' ' ')
      [ -n "$cands" ] && hint="  → ledger 후보: $cands"
    fi

    echo "$file:${line:-?}: 존재하지 않는 페어 참조 '$ref'$hint"
    # `{design,plan}` 축약형을 함께 뽑도록 `{`,`,`,`}` 를 문자 클래스에 포함한다.
  done < <(grep -oE 'docs/plans/[A-Za-z0-9._/{},-]+\.md' -- "$file" 2>/dev/null | sort -u)
done < <(git ls-files '*.md')

if [ "$violations" -eq 0 ]; then
  echo "OK: docs/plans/ 페어 참조 링크 정상 (끊긴 참조 0건)"
  exit 0
fi

echo
echo "끊긴 참조 ${violations}건."
echo "페어를 ledger 로 이관했다면 참조도 함께 리다이렉트해야 한다:"
echo "  docs/plans/<날짜>-<주제>-design.md  →  docs/plans/logs/<topic>.md (<날짜> entry)"
echo "컨벤션: docs/plans/README.md 워크플로 4항(참조 리다이렉트)."

[ "$LIST_ONLY" -eq 1 ] && exit 0
exit 1
