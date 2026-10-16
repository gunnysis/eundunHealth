[CmdletBinding()]
param(
    [string]$ApiBaseUrl = "https://oss.exercisedb.dev/api/v1",
    [string]$CatalogPath = "app/src/main/assets/korean_exercises.json",
    [ValidateRange(1, 5)]
    [int]$MaxAttempts = 3
)

$ErrorActionPreference = "Stop"
$catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json -AsHashtable
$queries = @(
    @{ Part = "chest"; Limit = 6 },
    @{ Part = "shoulders"; Limit = 6 },
    @{ Part = "back"; Limit = 6 },
    @{ Part = "upper arms"; Limit = 6 },
    @{ Part = "upper legs"; Limit = 6 },
    @{ Part = "lower legs"; Limit = 6 },
    @{ Part = "cardio"; Limit = 10 }
)

function Invoke-ExerciseDbQuery {
    param([string]$Uri, [int]$ExpectedCount)

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri $Uri -TimeoutSec 30
            if ($response.success -ne $true -or $null -eq $response.data) {
                throw "ExerciseDB returned an unsuccessful or malformed response."
            }
            $data = @($response.data)
            if ($data.Count -ne $ExpectedCount) {
                throw "ExerciseDB returned $($data.Count) items; expected $ExpectedCount."
            }
            return $data
        } catch {
            if ($attempt -eq $MaxAttempts) { throw }
            Start-Sleep -Seconds $attempt
        }
    }
}

$items = foreach ($query in $queries) {
    $part = [Uri]::EscapeDataString($query.Part)
    $uri = "$ApiBaseUrl/exercises?bodyParts=$part&limit=$($query.Limit)"
    Invoke-ExerciseDbQuery -Uri $uri -ExpectedCount $query.Limit
}

$sha256 = [Security.Cryptography.SHA256]::Create()
$live = @($items | Sort-Object exerciseId -Unique)
$mismatches = foreach ($item in $live) {
    $normalizedName = ($item.name.Trim() -replace '\s+', ' ')
    $normalizedInstructions = ($item.instructions | ForEach-Object {
        (($_ -replace '^Step:\s*\d+\s*', '').Trim() -replace '\s+', ' ')
    }) -join "`n"
    $normalized = $normalizedName + "`n" + $normalizedInstructions
    $bytes = [Text.Encoding]::UTF8.GetBytes($normalized)
    $fingerprint = [Convert]::ToHexString($sha256.ComputeHash($bytes)).ToLowerInvariant()
    $assetItem = $catalog.exercises[$item.exerciseId]
    if ($null -eq $assetItem) {
        "missing id: $($item.exerciseId) ($($item.name))"
    } elseif ($assetItem.sourceFingerprint -ne $fingerprint) {
        "changed source name or instructions: $($item.exerciseId) ($($item.name))"
    }
}

$staleIds = @($catalog.exercises.Keys | Where-Object { $_ -notin $live.exerciseId })
Write-Output "live=$($live.Count) asset=$($catalog.exercises.Count) mismatches=$(@($mismatches).Count) stale=$($staleIds.Count)"
$mismatches | ForEach-Object { Write-Error $_ -ErrorAction Continue }
$staleIds | ForEach-Object { Write-Error "current recommendation pool no longer contains: $_" -ErrorAction Continue }

if (@($mismatches).Count -gt 0 -or $staleIds.Count -gt 0) {
    exit 1
}
