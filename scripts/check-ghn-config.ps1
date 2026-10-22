$ErrorActionPreference = 'Stop'
foreach ($ghnKey in @('GHN_TOKEN', 'GHN_SHOP_ID')) {
    $ghnProcessValue = [Environment]::GetEnvironmentVariable($ghnKey, 'Process')
    $ghnUserValue = [Environment]::GetEnvironmentVariable($ghnKey, 'User')
    $ghnMachineValue = [Environment]::GetEnvironmentVariable($ghnKey, 'Machine')
    [pscustomobject]@{
        Name = $ghnKey
        CurrentProcessConfigured = -not [string]::IsNullOrWhiteSpace($ghnProcessValue)
        UserConfigured = -not [string]::IsNullOrWhiteSpace($ghnUserValue)
        MachineConfigured = -not [string]::IsNullOrWhiteSpace($ghnMachineValue)
    }
}
Write-Output 'Restart NetBeans and Tomcat after saving variables. No token values are displayed.'
