# Keep this secret separate from the GHN API token. Only the shared webhook secret is copied.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'read-app-setting.ps1')
$ghnSecret = Get-AppSetting 'GHN_WEBHOOK_SECRET'
if ([string]::IsNullOrWhiteSpace($ghnSecret) -or $ghnSecret.Length -lt 32) {
    $ghnSecret = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    [Environment]::SetEnvironmentVariable('GHN_WEBHOOK_SECRET',$ghnSecret,'User')
}
$env:GHN_WEBHOOK_SECRET = $ghnSecret
try {
    Set-Clipboard -Value $ghnSecret
    Write-Host 'Da luu GHN_WEBHOOK_SECRET va copy gia tri vao clipboard.'
    Write-Host 'Trong GHN -> Header tuy chinh: Key = X-GHN-Webhook-Secret; Value = nhan Ctrl+V.'
} catch {
    Write-Host 'Da luu secret; clipboard chua san sang. De copy lai, mo PowerShell binh thuong va chay lai script nay.'
}
Write-Host 'Giu Content-Type: application/json. Dong/mo lai NetBeans va Tomcat sau khi luu cau hinh.'
Write-Host 'Chi dien URL webhook khi ngrok dang online. Secret khong duoc in ra man hinh hoac luu vao source.'
