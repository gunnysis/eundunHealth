@description('Name of the Cognitive Services Account (AI Hub or AI Services)')
param accountName string = 'hub-eundunhealth-prod'

@description('Name of the deployment endpoint')
param deploymentName string = 'ep-eundunhealth-meal-ai'

@description('Model provider format (e.g. OpenAI)')
param modelFormat string = 'OpenAI'

@description('Model name in Azure AI Foundry catalog')
param modelName string = 'gpt-5.4-mini'

@description('Model version in Azure AI Foundry catalog')
param modelVersion string = '2026-03-17'

@description('GlobalStandard TPM capacity in thousands (100 = 100K TPM / 100 RPM, $0 idle cost)')
param skuCapacity int = 100

resource cognitiveServicesAccount 'Microsoft.CognitiveServices/accounts@2024-10-01' existing = {
  name: accountName
}

resource mealAiDeployment 'Microsoft.CognitiveServices/accounts/deployments@2024-10-01' = {
  parent: cognitiveServicesAccount
  name: deploymentName
  sku: {
    name: 'GlobalStandard'
    capacity: skuCapacity
  }
  properties: {
    model: {
      format: modelFormat
      name: modelName
      version: modelVersion
    }
  }
}
