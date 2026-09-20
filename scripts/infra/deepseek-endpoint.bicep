@description('Name of the Cognitive Services Account (AI Hub or AI Project)')
param accountName string

@description('Name of the deployment endpoint')
param deploymentName string = 'ep-eundunhealth-deepseekv3'

resource cognitiveServicesAccount 'Microsoft.CognitiveServices/accounts@2024-04-01-preview' existing = {
  name: accountName
}

resource deepseekDeployment 'Microsoft.CognitiveServices/accounts/deployments@2024-04-01-preview' = {
  parent: cognitiveServicesAccount
  name: deploymentName
  sku: {
    name: 'GlobalStandard'
    capacity: 1
  }
  properties: {
    model: {
      format: 'DeepSeek'
      name: 'DeepSeek-V3.2'
      version: '1'
    }
  }
}
