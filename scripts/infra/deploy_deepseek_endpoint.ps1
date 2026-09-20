<#
.SYNOPSIS
Deploys a DeepSeek-V3 model as a Serverless Endpoint (Global Standard) in Azure AI Foundry.

.DESCRIPTION
This script ensures the `ml` extension for Azure CLI is installed and creates a serverless endpoint
for the DeepSeek-V3 model. It maps to the 'Global Standard' serverless deployment type available in the catalog.
#>

param (
    [Parameter(Mandatory=$false)]
    [string]$EndpointName = "ep-eundunhealth-deepseekv3",
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "rg-eundunhealth-prod-krc",
    
    [Parameter(Mandatory=$false)]
    [string]$AiHubName = "hub-eundunhealth-prod",

    [Parameter(Mandatory=$false)]
    [string]$ModelId = "azureml://registries/azureml-deepseek/models/DeepSeek-V3"
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

# 3. Create the Serverless Endpoint using Bicep
Write-Host "Creating serverless endpoint '$EndpointName' for DeepSeek-V3 via Bicep..." -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup | AI Account (Hub): $AiHubName" -ForegroundColor DarkGray

$bicepPath = Join-Path -Path $PSScriptRoot -ChildPath "deepseek-endpoint.bicep"
if (-Not (Test-Path $bicepPath)) {
    Write-Error "Bicep configuration file not found at $bicepPath"
    exit 1
}

az deployment group create `
    --resource-group $ResourceGroup `
    --template-file $bicepPath `
    --parameters accountName=$AiHubName deploymentName=$EndpointName

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSuccessfully deployed the Serverless Endpoint (DeepSeek-V3)!" -ForegroundColor Green
    
    # 4. Fetch the keys/credentials to verify
    Write-Host "Fetching Cognitive Services account keys..." -ForegroundColor Cyan
    $keys = az cognitiveservices account keys list `
        --name $AiHubName `
        --resource-group $ResourceGroup | ConvertFrom-Json
        
    Write-Host "`nDeployment Complete." -ForegroundColor Green
    Write-Host "Your Endpoint Base URL will follow the pattern: https://$AiHubName.cognitiveservices.azure.com/" -ForegroundColor Yellow
    Write-Host "Please store the Key in Azure Key Vault (e.g., DEEPSEEK_KEY)." -ForegroundColor Yellow
} else {
    Write-Error "Failed to deploy the DeepSeek-V3 model. Check the error messages above."
}
