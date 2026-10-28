$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'read-app-setting.ps1')
foreach ($ghnKey in @('GHN_TOKEN', 'GHN_SHOP_ID')) {
    $ghnProcessValue = [Environment]::GetEnvironmentVariable($ghnKey, 'Process')
    $ghnUserValue = [Environment]::GetEnvironmentVariable($ghnKey, 'User')
    $ghnMachineValue = [Environment]::GetEnvironmentVariable($ghnKey, 'Machine')
    [pscustomobject]@{
        Name = $ghnKey
        ApplicationConfigured = -not [string]::IsNullOrWhiteSpace((Get-AppSetting $ghnKey))
        CurrentProcessConfigured = -not [string]::IsNullOrWhiteSpace($ghnProcessValue)
        UserConfigured = -not [string]::IsNullOrWhiteSpace($ghnUserValue)
        MachineConfigured = -not [string]::IsNullOrWhiteSpace($ghnMachineValue)
    }
}
Write-Output 'Restart NetBeans and Tomcat after saving variables. No token values are displayed.'
