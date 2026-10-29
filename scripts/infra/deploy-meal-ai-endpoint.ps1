<#
.SYNOPSIS
Deploys the meal plan AI model endpoint (gpt-5.4-mini, GlobalStandard) in Azure AI Foundry.

.DESCRIPTION
Provisions or updates the GlobalStandard model deployment (default: ep-eundunhealth-meal-ai / OpenAI gpt-5.4-mini 2026-03-17, 100K TPM) on the Azure AI Services Hub (hub-eundunhealth-prod) using scripts/infra/meal-ai-endpoint.bicep.
#>

param (
    [Parameter(Mandatory=$false)]
    [string]$EndpointName = "ep-eundunhealth-meal-ai",

    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "rg-eundunhealth-prod-krc",

    [Parameter(Mandatory=$false)]
    [string]$AiHubName = "hub-eundunhealth-prod",

    [Parameter(Mandatory=$false)]
    [string]$ModelFormat = "OpenAI",

    [Parameter(Mandatory=$false)]
    [string]$ModelName = "gpt-5.4-mini",

    [Parameter(Mandatory=$false)]
    [string]$ModelVersion = "2026-03-17",

    [Parameter(Mandatory=$false)]
    [int]$SkuCapacity = 100
)

# 1. Check if az is installed
if (!(Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Error "Azure CLI (az) is not installed. Please install it first."
    exit 1
}

# 2. Check if logged into Azure
Write-Host "Verifying Azure login status..." -ForegroundColor Cyan
$account = az account show 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($account)) {
    Write-Error "Not logged into Azure. Please run 'az login' first."
    exit 1
}

# 3. Deploy the Meal AI Endpoint using Bicep
Write-Host "Deploying meal AI endpoint '$EndpointName' ($ModelFormat / $ModelName $ModelVersion, capacity=$SkuCapacity) via Bicep..." -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup | AI Account (Hub): $AiHubName" -ForegroundColor DarkGray

$bicepPath = Join-Path -Path $PSScriptRoot -ChildPath "meal-ai-endpoint.bicep"
if (-Not (Test-Path $bicepPath)) {
    Write-Error "Bicep configuration file not found at $bicepPath"
    exit 1
}

az deployment group create `
    --resource-group $ResourceGroup `
    --template-file $bicepPath `
    --parameters accountName=$AiHubName deploymentName=$EndpointName modelFormat=$ModelFormat modelName=$ModelName modelVersion=$ModelVersion skuCapacity=$SkuCapacity

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSuccessfully deployed the Meal AI Endpoint ($EndpointName -> $ModelName)!" -ForegroundColor Green
    az cognitiveservices account deployment show `
        --resource-group $ResourceGroup `
        --name $AiHubName `
        --deployment-name $EndpointName `
        -o table
} else {
    Write-Error "Failed to deploy the Meal AI model endpoint. Check the error messages above."
    exit 1
}
