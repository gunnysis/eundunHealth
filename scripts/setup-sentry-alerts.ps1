#!/usr/bin/env pwsh
# Sentry Alert 자동 설정 스크립트 — idempotent (재실행 안전)
# 실행: pwsh scripts/setup-sentry-alerts.ps1 [-DryRun]
#
# 필요 조건:
#   - SENTRY_AUTH_TOKEN 환경변수 설정 (User Settings > Auth Tokens)
#   - 필요 스코프: project:write, org:read, member:read
#
# ─── 버그 이력 / 재발방지 주석 (2026-06-16) ───────────────────────────────
# B1  $org(API 응답) vs $ORG(슬러그) — PowerShell 변수명 대소문자 무시(case-insensitive)로
#     $ORG가 응답 객체로 덮어씌워져 URI가 망가짐. 수정: 모든 상수는 $script: 명시 + 응답은
#     별도 이름($orgInfo)으로 받음. 함수 내에서 외부 변수는 반드시 $script: 접두어 사용.
# B2  environment="production" → Sentry는 첫 이벤트 수신 전 해당 환경을 404로 거부.
#     수정: environment 생략. 출시 후 Sentry UI > Alerts > Edit > "Filter by Environment" 추가.
# B3  interval="30m" → Sentry API 거부. 유효값: 1m / 5m / 10m / 1h / 4h / 24h / 1w.
#     수정: "1h" 사용.
# B4  dataset="transactions" → Sentry가 spans(events_analytics_platform)로 마이그레이션 완료.
#     수정: dataset="events_analytics_platform", query="is_transaction:true", span.duration.
# B5  targetType="team" → 팀 ID(targetIdentifier) 필수 → 솔로 프로젝트 부적합 → 404.
#     수정: targetType="user" + targetIdentifier=Sentry 유저ID.
#     유저ID 조회: GET /api/0/organizations/{org}/members/ → .user.id (멤버 .id 아님!)

param(
    [switch]$DryRun  # 실제 변경 없이 실행 계획만 출력
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ═══════════════════════════════════════════════════════════════════════════
# 설정 상수 — 이 블록만 수정하면 됨
# ═══════════════════════════════════════════════════════════════════════════
$script:ORG_SLUG        = "gunnys"
$script:SENTRY_USER_ID  = "4265580"    # Sentry 유저 ID (멤버 ID 아님!)
                                        # 갱신: GET /api/0/organizations/$ORG_SLUG/members/ → .user.id
$script:ANDROID_PROJECT = "eundunhealth"
$script:BACKEND_PROJECT = "eundunhealth-backend"
$script:BASE_URL        = "https://sentry.io/api/0"

# ── 토큰 확인 ──────────────────────────────────────────────────────────────
$TOKEN = $env:SENTRY_AUTH_TOKEN
if (-not $TOKEN) {
    Write-Error "SENTRY_AUTH_TOKEN 환경변수가 설정되지 않았습니다."
    exit 1
}
$script:HEADERS = @{
    Authorization  = "Bearer $TOKEN"
    "Content-Type" = "application/json"
}

if ($DryRun) {
    Write-Host "`n[DRY RUN] 실제 변경 없음 — 실행 계획만 출력합니다." -ForegroundColor Yellow
}

# ── 조직 연결 확인 ─────────────────────────────────────────────────────────
Write-Host "`n🔍 Sentry 연결 확인 중..." -ForegroundColor Cyan
try {
    $orgInfo = Invoke-RestMethod `
        -Uri "$script:BASE_URL/organizations/$script:ORG_SLUG/" `
        -Headers $script:HEADERS -Method GET
    Write-Host "✅ 조직: $($orgInfo.name) (slug: $($orgInfo.slug))"
} catch {
    Write-Error "조직 연결 실패 (토큰·슬러그 확인): $_"
    exit 1
}

# ═══════════════════════════════════════════════════════════════════════════
# 공통 헬퍼
# ═══════════════════════════════════════════════════════════════════════════

# Sentry API 에러 응답에서 사람이 읽을 수 있는 메시지를 추출
function Get-SentryErrorDetail {
    param([System.Management.Automation.ErrorRecord]$Err)
    $rawBody = $null
    if ($null -ne $Err.ErrorDetails -and $null -ne $Err.ErrorDetails.Message) {
        $rawBody = $Err.ErrorDetails.Message
    } elseif ($null -ne $Err.Exception.Response) {
        try {
            $rawBody = ([System.IO.StreamReader]::new(
                $Err.Exception.Response.GetResponseStream()
            )).ReadToEnd()
        } catch {}
    }
    if ($null -eq $rawBody) { return $Err.Exception.Message }
    try {
        $parsed = $rawBody | ConvertFrom-Json
        if ($null -ne $parsed.name)   { return ($parsed.name   -join ', ') }
        if ($null -ne $parsed.detail) { return $parsed.detail }
    } catch {}
    return $rawBody
}

# ═══════════════════════════════════════════════════════════════════════════
# Issue Alert (project-level rules)
# ═══════════════════════════════════════════════════════════════════════════
# 주의사항:
#   - environment 미지정: B2 참조 (출시 후 UI에서 추가)
#   - interval 유효값: 1m / 5m / 10m / 1h / 4h / 24h / 1w (B3 참조)
#   - idempotent: GET으로 기존 룰 목록 조회 후 동일 이름 존재 시 skip

function New-IssueAlert {
    param(
        [string]$ProjectSlug,
        [string]$Name,
        [array]$Conditions,
        [int]$FrequencyMinutes
    )

    # 기존 룰 조회 — 동일 이름 존재 시 skip (에러 응답 의존 방식보다 안전)
    try {
        $existing = Invoke-RestMethod `
            -Uri "$script:BASE_URL/projects/$script:ORG_SLUG/$ProjectSlug/rules/" `
            -Headers $script:HEADERS -Method GET
        if ($existing | Where-Object { $_.name -eq $Name }) {
            Write-Host "  ✅ 이미 존재: $Name"
            return
        }
    } catch {
        Write-Host "  ⚠️  기존 룰 조회 실패 — 생성 시도: $(Get-SentryErrorDetail $_)" -ForegroundColor Yellow
    }

    if ($DryRun) {
        Write-Host "  [DRY] 생성 예정: $Name"
        return
    }

    $body = @{
        name        = $Name
        actionMatch = "any"
        filterMatch = "all"
        conditions  = $Conditions
        filters     = @()
        actions     = @(@{
            id         = "sentry.mail.actions.NotifyEmailAction"
            targetType = "IssueOwners"
        })
        frequency   = $FrequencyMinutes
        owner       = $null
    } | ConvertTo-Json -Depth 10

    try {
        $result = Invoke-RestMethod `
            -Uri "$script:BASE_URL/projects/$script:ORG_SLUG/$ProjectSlug/rules/" `
            -Headers $script:HEADERS -Method POST -Body $body
        Write-Host "  ✅ 생성됨: $Name (id: $($result.id))"
    } catch {
        Write-Host "  ❌ 실패: $Name — $(Get-SentryErrorDetail $_)" -ForegroundColor Red
    }
}

# ═══════════════════════════════════════════════════════════════════════════
# Metric Alert (organization-level alert-rules)
# ═══════════════════════════════════════════════════════════════════════════
# 주의사항:
#   - dataset="events_analytics_platform": B4 참조 ("transactions" deprecated)
#   - query="is_transaction:true": spans 중 트랜잭션만 필터 (기존 동작 유지)
#   - targetType="user": B5 참조 (솔로 프로젝트 — 팀 없음)
#   - $script:SENTRY_USER_ID: 멤버 ID가 아닌 유저 ID (B5 참조)
#   - thresholdType: 0=ABOVE(초과 시 발동) / 1=BELOW

function New-MetricAlert {
    param(
        [string]$Name,
        [string]$ProjectSlug,
        [string]$Aggregate,
        [int]$TimeWindow,
        [double]$WarnThreshold,
        [double]$CritThreshold,
        [double]$ResolveThreshold,
        [string]$Query = "is_transaction:true"
    )

    # 기존 룰 조회 — 동일 이름 존재 시 skip
    try {
        $existing = Invoke-RestMethod `
            -Uri "$script:BASE_URL/organizations/$script:ORG_SLUG/alert-rules/" `
            -Headers $script:HEADERS -Method GET
        if ($existing | Where-Object { $_.name -eq $Name }) {
            Write-Host "  ✅ 이미 존재: $Name"
            return
        }
    } catch {
        Write-Host "  ⚠️  기존 룰 조회 실패 — 생성 시도: $(Get-SentryErrorDetail $_)" -ForegroundColor Yellow
    }

    if ($DryRun) {
        Write-Host "  [DRY] 생성 예정: $Name"
        return
    }

    $emailAction = @{
        type             = "email"
        targetType       = "user"
        targetIdentifier = $script:SENTRY_USER_ID
    }
    $body = @{
        name             = $Name
        dataset          = "events_analytics_platform"
        aggregate        = $Aggregate
        query            = $Query
        timeWindow       = $TimeWindow
        thresholdType    = 0
        resolveThreshold = $ResolveThreshold
        projects         = @($ProjectSlug)
        triggers         = @(
            @{ label = "critical"; alertThreshold = $CritThreshold; actions = @($emailAction) }
            @{ label = "warning";  alertThreshold = $WarnThreshold;  actions = @($emailAction) }
        )
        owner = $null
    } | ConvertTo-Json -Depth 10

    try {
        $result = Invoke-RestMethod `
            -Uri "$script:BASE_URL/organizations/$script:ORG_SLUG/alert-rules/" `
            -Headers $script:HEADERS -Method POST -Body $body
        Write-Host "  ✅ 생성됨: $Name (id: $($result.id))"
    } catch {
        Write-Host "  ❌ 실패: $Name — $(Get-SentryErrorDetail $_)" -ForegroundColor Red
    }
}

# ── Performance threshold ──────────────────────────────────────────────────
function Set-PerformanceThreshold {
    param([string]$ProjectSlug, [int]$ThresholdMs)
    if ($DryRun) {
        Write-Host "  [DRY] Performance threshold: ${ThresholdMs}ms → $ProjectSlug"
        return
    }
    $body = @{ performance = @{ apdexThreshold = $ThresholdMs } } | ConvertTo-Json -Depth 5
    try {
        Invoke-RestMethod `
            -Uri "$script:BASE_URL/projects/$script:ORG_SLUG/$ProjectSlug/" `
            -Headers $script:HEADERS -Method PUT -Body $body | Out-Null
        Write-Host "  ✅ Performance threshold: ${ThresholdMs}ms → $ProjectSlug"
    } catch {
        Write-Host "  ⚠️  Performance threshold 설정 실패 (UI에서 수동 설정 필요): $ProjectSlug" -ForegroundColor Yellow
    }
}

# ═══════════════════════════════════════════════════════════════════════════
# 알림 룰 정의
# ═══════════════════════════════════════════════════════════════════════════

Write-Host "`n📱 Android Issue Alerts ($script:ANDROID_PROJECT) ..." -ForegroundColor Cyan

New-IssueAlert -ProjectSlug $script:ANDROID_PROJECT `
    -Name "[Android] 신규 이슈 즉시 알림" `
    -Conditions @(@{ id = "sentry.rules.conditions.first_seen_event.FirstSeenEventCondition" }) `
    -FrequencyMinutes 60

New-IssueAlert -ProjectSlug $script:ANDROID_PROJECT `
    -Name "[Android] 회귀 알림" `
    -Conditions @(@{ id = "sentry.rules.conditions.regression_event.RegressionEventCondition" }) `
    -FrequencyMinutes 5

New-IssueAlert -ProjectSlug $script:ANDROID_PROJECT `
    -Name "[Android] 빈도 급증 (3회/1시간)" `
    -Conditions @(@{
        id       = "sentry.rules.conditions.event_frequency.EventFrequencyCondition"
        value    = 3
        interval = "1h"   # 유효값: 1m/5m/10m/1h/4h/24h/1w — "30m" 등 비표준 거부(B3)
    }) `
    -FrequencyMinutes 240

Write-Host "`n🖥️  Backend Issue Alerts ($script:BACKEND_PROJECT) ..." -ForegroundColor Cyan

New-IssueAlert -ProjectSlug $script:BACKEND_PROJECT `
    -Name "[Backend] 신규 이슈 즉시 알림" `
    -Conditions @(@{ id = "sentry.rules.conditions.first_seen_event.FirstSeenEventCondition" }) `
    -FrequencyMinutes 60

New-IssueAlert -ProjectSlug $script:BACKEND_PROJECT `
    -Name "[Backend] 회귀 알림" `
    -Conditions @(@{ id = "sentry.rules.conditions.regression_event.RegressionEventCondition" }) `
    -FrequencyMinutes 5

New-IssueAlert -ProjectSlug $script:BACKEND_PROJECT `
    -Name "[Backend] 빈도 급증 (10회/60분)" `
    -Conditions @(@{
        id       = "sentry.rules.conditions.event_frequency.EventFrequencyCondition"
        value    = 10
        interval = "1h"
    }) `
    -FrequencyMinutes 240

Write-Host "`n📊 Backend Metric Alerts ..." -ForegroundColor Cyan
# ESTIMATE-ONLY: 출시 2주 후 실측 데이터로 임계값 재조정 필요

New-MetricAlert `
    -Name "[Backend] p95 응답시간" `
    -ProjectSlug $script:BACKEND_PROJECT `
    -Aggregate "p95(span.duration)" `
    -TimeWindow 5 `
    -WarnThreshold 2000 -CritThreshold 5000 -ResolveThreshold 1500

New-MetricAlert `
    -Name "[Backend] 에러율 스파이크" `
    -ProjectSlug $script:BACKEND_PROJECT `
    -Aggregate "failure_rate()" `
    -TimeWindow 10 `
    -WarnThreshold 0.01 -CritThreshold 0.05 -ResolveThreshold 0.005

Write-Host "`n⚡ Performance threshold 설정 ..." -ForegroundColor Cyan
Set-PerformanceThreshold -ProjectSlug $script:ANDROID_PROJECT -ThresholdMs 2000
Set-PerformanceThreshold -ProjectSlug $script:BACKEND_PROJECT -ThresholdMs 1000

# ═══════════════════════════════════════════════════════════════════════════
Write-Host "`n✅ 완료! Sentry UI 확인: https://sentry.io/organizations/$script:ORG_SLUG/alerts/" -ForegroundColor Green
Write-Host @"

📋 남은 수동 작업 (UI 직접 설정):
   sentry.io → 우측 상단 아이콘 → User Settings → Notifications
   ✅ Weekly Reports → ON
   ✅ Deploy Notifications → ON
   ✅ Workflow: Regressions → ON
   ✅ Workflow: Issue Resolved → ON

⚠️  출시 후: Alerts > 각 룰 Edit > "Filter by Environment: production" 추가 권장 (B2)
⚠️  Rule F (Android p95 Metric Alert) → DAU 100+ 후 수동 추가
⚠️  임계값은 ESTIMATE-ONLY — 출시 2주 후 실측 기반 재조정 필요
"@
