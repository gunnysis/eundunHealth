<#
GitHub Actions의 AZURE_CREDENTIALS secret 등록 / 갱신 — **긴급 폴백 전용 (2026-07-03~)**.

2026-07-02 OIDC 연합 전환(PR #141/#142) 후 워크플로 Azure 로그인은 federated
credential(`github-main`) 기반이며, 상시 AZURE_CREDENTIALS secret 은 2026-07-03
완전 제거됨(GitHub secret + Entra 앱 비밀번호). 본 스크립트는 다음 경우에만 실행:
  - OIDC 장애 시 임시 복귀 — 워크플로 azure/login 을 creds: 방식으로 revert 한 뒤
    본 스크립트로 SP credential 재생성 + secret 재등록 (기존 secret 불요, 매 실행 reset)
  - SP 역할(AcrPush 등) 재부여/점검
상세: docs/ops/monitoring-and-cost.md §6.7.

backend.yml의 Build, Scan & Deploy job이 (구 방식에서) azure/login@v2로 사용하는
service principal JSON을 생성/패치하고 GitHub repository secret으로 등록한다.

참조 인시던트: docs/ops/incident-log.md INC-2026-05-25-17.

전제:
  - Azure CLI (az) 로그인 완료 — `az account show`가 정상 응답
  - GitHub CLI (gh) 설치. 인증은 자동 (local.properties:GITHUB_CLASSIC_TOKEN 사용)
  - 호출자가 대상 RG에 RBAC 부여 권한 보유

기본 사용:
  pwsh -File scripts\register-azure-credentials.ps1

만료 갱신 시:
  pwsh -File scripts\register-azure-credentials.ps1 -Verify

매개변수:
  -ResourceGroup    : Container App / PG가 속한 RG (기본 apps)
  -AcrName          : ACR 이름 (기본 eundunhealthacr)
  -Repo             : GitHub repo owner/name (기본 gunnysis/eundunHealth)
  -SpName           : Service principal 표시 이름 (기본 eundunhealth-github-deploy)
  -Verify           : 등록 후 빈 commit + push로 deploy job 강제 트리거
  -LocalProperties  : GH 토큰 추출 대상 파일 (기본 local.properties)

주의: 매 실행이 SP secret을 reset한다. 이전에 등록된 AZURE_CREDENTIALS는 무효화됨.
#>

[CmdletBinding()]
param(
    [string] $ResourceGroup   = "apps",
    [string] $AcrName         = "eundunhealthacr",
    [string] $Repo            = "gunnysis/eundunHealth",
    [string] $SpName          = "eundunhealth-github-deploy",
    [switch] $Verify,
    [string] $LocalProperties = "$PSScriptRoot\..\local.properties"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

function Write-Step($n, $msg) {
    Write-Host ""
    Write-Host "==> Step $n — $msg" -ForegroundColor Cyan
}

# ----------------------------------------------------------------------
# 사전 점검
# ----------------------------------------------------------------------
Write-Step 0 "사전 점검"

foreach ($cli in @('az', 'gh', 'git')) {
    if (-not (Get-Command $cli -ErrorAction SilentlyContinue)) {
        throw "$cli CLI가 PATH에 없음. 설치 후 다시 실행."
    }
}

try {
    $account = az account show -o json 2>$null | ConvertFrom-Json
} catch {
    throw "az account show 실패. 먼저 'az login' 실행."
}
$subId    = $account.id
$tenantId = $account.tenantId
Write-Host "  Subscription : $($account.name) ($subId)"
Write-Host "  Tenant       : $tenantId"

if (-not (Test-Path $LocalProperties)) {
    throw "local.properties를 찾을 수 없음: $LocalProperties"
}
$ghTokenLine = Select-String -Path $LocalProperties -Pattern "^GITHUB_CLASSIC_TOKEN="
if (-not $ghTokenLine) {
    throw "local.properties에 GITHUB_CLASSIC_TOKEN= 항목이 없음."
}
$ghToken = $ghTokenLine.Line.Split("=", 2)[1].Trim()
if ([string]::IsNullOrWhiteSpace($ghToken)) {
    throw "GITHUB_CLASSIC_TOKEN 값이 비어있음."
}

$tmpFile = Join-Path $env:TEMP ("azure-sp-{0}.json" -f (Get-Date -Format yyyyMMddHHmmss))

try {
    # --------------------------------------------------------------------
    # Step 1 — SP 생성/패치, 결과 JSON을 임시 파일로 (콘솔 출력 없음)
    # --------------------------------------------------------------------
    Write-Step 1 "Service principal 생성/패치 ($SpName)"
    $scope = "/subscriptions/$subId/resourceGroups/$ResourceGroup"
    az ad sp create-for-rbac `
        --name $SpName `
        --role Contributor `
        --scopes $scope `
        --json-auth `
        | Out-File -FilePath $tmpFile -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "az ad sp create-for-rbac 실패 (exit $LASTEXITCODE)" }
    Write-Host "  JSON saved: $tmpFile (Step 4에서 삭제됨)"

    # --------------------------------------------------------------------
    # Step 2 — ACR push 권한 (RG Contributor가 이미 포괄하나 명시적 부여)
    # --------------------------------------------------------------------
    Write-Step 2 "ACR push 권한 부여"
    $clientId = (Get-Content $tmpFile -Raw | ConvertFrom-Json).clientId
    $acrId = az acr show --name $AcrName --query id -o tsv
    if ($LASTEXITCODE -ne 0) { throw "az acr show 실패. ACR 이름 확인: $AcrName" }

    # AcrPush 부여. 이미 존재하면 'already exists' 에러를 무시.
    $assignOut = az role assignment create --assignee $clientId --scope $acrId --role AcrPush 2>&1
    if ($LASTEXITCODE -ne 0 -and ($assignOut -notmatch 'already exists|RoleAssignmentExists')) {
        throw "AcrPush 권한 부여 실패: $assignOut"
    }
    Write-Host "  AcrPush role on $AcrName"

    # --------------------------------------------------------------------
    # Step 3 — GitHub repo secret 등록
    # --------------------------------------------------------------------
    Write-Step 3 "GitHub secret AZURE_CREDENTIALS 등록"
    # gh 옛 버전(<2.40)은 --body-file 미지원. stdin pipe가 호환성 + 보안 둘 다 best:
    #   - 평문 인자(-b "...")를 피해 PowerShell history / 프로세스 인자에 secret 미노출.
    #   - 모든 gh 버전에서 동작.
    $env:GH_TOKEN = $ghToken
    try {
        Get-Content $tmpFile -Raw | gh secret set AZURE_CREDENTIALS --repo $Repo
        if ($LASTEXITCODE -ne 0) { throw "gh secret set 실패 (exit $LASTEXITCODE)" }
    } finally {
        Remove-Item Env:\GH_TOKEN -ErrorAction SilentlyContinue
    }
    Write-Host "  Secret registered to $Repo"

    # --------------------------------------------------------------------
    # Step 4 — 임시 파일 삭제 (finally에서도 보장되나 명시 호출)
    # --------------------------------------------------------------------
    Write-Step 4 "임시 자격 파일 삭제"
    Remove-Item $tmpFile -Force
    $tmpFile = $null
    Write-Host "  Removed."

    # --------------------------------------------------------------------
    # Step 5 — (선택) 빈 commit + push로 deploy job 강제 트리거
    # --------------------------------------------------------------------
    if ($Verify) {
        Write-Step 5 "검증 — 빈 commit + push로 deploy job 강제 트리거"
        $repoRoot = Resolve-Path "$PSScriptRoot\.."
        Push-Location $repoRoot
        try {
            $branch = git rev-parse --abbrev-ref HEAD
            if ($branch -ne 'main') {
                Write-Warning "현재 branch가 main이 아님 ($branch). 검증은 main에서만 의미 있음."
            }
            git commit --allow-empty -m "ci: AZURE_CREDENTIALS 검증 트리거"
            if ($LASTEXITCODE -ne 0) { throw "git commit 실패 (exit $LASTEXITCODE)" }
            git push origin $branch
            if ($LASTEXITCODE -ne 0) { throw "git push 실패 (exit $LASTEXITCODE)" }
            Write-Host ""
            Write-Host "Push 완료. GitHub Actions에서 backend.yml 실행 확인:"
            Write-Host "  gh run watch --repo $Repo"
        } finally {
            Pop-Location
        }
    } else {
        Write-Host ""
        Write-Host "검증 트리거를 같이 돌리려면 -Verify 옵션을 추가하세요." -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "완료." -ForegroundColor Green
}
finally {
    if ($tmpFile -and (Test-Path $tmpFile)) {
        Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue
        Write-Host "  (cleanup) $tmpFile 삭제" -ForegroundColor DarkGray
    }
}
