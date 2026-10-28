# GHN staging only. Default: preview. -Create: create/reuse the shop and save GHN_SHOP_ID.
param([switch]$Create)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

. (Join-Path $PSScriptRoot 'read-app-setting.ps1')
$ghnToken = Get-AppSetting 'GHN_TOKEN'
$ghnExistingShop = Get-AppSetting 'GHN_SHOP_ID'
if ([string]::IsNullOrWhiteSpace($ghnToken) -or $ghnExistingShop -notmatch '^[1-9][0-9]{0,9}$') {
    throw 'Chua co GHN_TOKEN/GHN_SHOP_ID. Luu Token test va Shop ID hien tai tren may nay truoc.'
}
if ($ghnToken -match '[\r\n]' -or $ghnToken -eq 'TOKEN_TEST_CUA_BAN') {
    throw 'GHN_TOKEN khong hop le. Dung Token API that tu developer.ghn.dev.'
}
$ghnHandler = [Net.Http.HttpClientHandler]::new()
$ghnHandler.AllowAutoRedirect = $false
$ghnHttp = [Net.Http.HttpClient]::new($ghnHandler)
$ghnHttp.Timeout = [TimeSpan]::FromSeconds([int](Get-AppSetting 'GHN_REQUEST_TIMEOUT_SECONDS'))
$ghnBaseUrl = Get-AppSetting 'GHN_API_BASE_URL'
$ghnBaseUri = [Uri]$ghnBaseUrl
if ($ghnBaseUri.Scheme -ne 'https' -or $ghnBaseUri.Host -ne 'dev-online-gateway.ghn.vn') {
    throw 'GHN_API_BASE_URL must reference the staging HTTPS host.'
}

function Invoke-Ghn([string]$Path, $Body = $null) {
    $method = if ($null -eq $Body) { [Net.Http.HttpMethod]::Get } else { [Net.Http.HttpMethod]::Post }
    $request = [Net.Http.HttpRequestMessage]::new($method, ($ghnBaseUrl.TrimEnd('/') + '/' + $Path))
    $response = $null
    try {
        $request.Headers.Add('Token', $ghnToken)
        $request.Headers.Add('ShopId', $ghnExistingShop)
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 10 -Compress
            $request.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
        }
        try { $response = $ghnHttp.SendAsync($request).GetAwaiter().GetResult() }
        catch {
            if ($null -ne $Body) { throw 'Chua ro ket qua tao shop do mat ket noi. Kiem tra Quan ly cua hang GHN truoc khi chay lai; script khong tu dong gui lai.' }
            throw 'Khong ket noi duoc GHN test. Kiem tra mang roi thu lai.'
        }
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $result = $null
        try { $result = $raw | ConvertFrom-Json } catch { }
        if ([int]$response.StatusCode -ne 200 -or $result.code -ne 200) {
            # Never print raw HTTP responses, headers or token values.
            $code = [string]$result.code_message
            if ($code -notmatch '^[A-Za-z0-9_-]{1,80}$') { $code = 'UNSPECIFIED' }
            throw ('GHN tu choi buoc ' + $Path.Split('?')[0] + ': HTTP ' + [int]$response.StatusCode + ', ma ' + $code + '. Doi chieu tai lieu GHN; Token van duoc giu kin.')
        }
        return $result.data
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}

function Select-Location([string]$Title, $Rows, [string]$NameField) {
    $choices = @($Rows | Sort-Object -Property $NameField)
    if ($choices.Count -eq 0) { throw ('GHN khong tra danh muc ' + $Title) }
    Write-Host ''
    Write-Host $Title
    for ($i = 0; $i -lt $choices.Count; $i++) { Write-Host ('{0,3}. {1}' -f ($i + 1), $choices[$i].$NameField) }
    while ($true) {
        $answer = Read-Host 'Nhap so thu tu'
        $selected = 0
        if ([int]::TryParse($answer, [ref]$selected) -and $selected -ge 1 -and $selected -le $choices.Count) { return $choices[$selected - 1] }
        Write-Host 'Chon so trong danh sach.'
    }
}
function Read-Required([string]$Prompt, [string]$Default, [int]$MaxLength) {
    while ($true) {
        $value = Read-Host ($Prompt + $(if ($Default) { ' [' + $Default + ']' } else { '' }))
        if ([string]::IsNullOrWhiteSpace($value)) { $value = $Default }
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Trim().Length -le $MaxLength -and $value -notmatch '[\r\n]') { return $value.Trim() }
        Write-Host ('Can nhap gia tri, toi da ' + $MaxLength + ' ky tu.')
    }
}
try {
    # Read the complete shop list so a rerun can reuse a matching shop.
    $shops = @()
    $offset = 0
    $pageSize = [int](Get-AppSetting 'GHN_SHOP_PAGE_SIZE')
    $maxPages = [int](Get-AppSetting 'GHN_SHOP_MAX_PAGES')
    if ($pageSize -lt 1 -or $pageSize -gt 100 -or $maxPages -lt 1 -or $maxPages -gt 1000) { throw 'Invalid GHN pagination settings.' }
    for ($page = 0; $page -lt $maxPages; $page++) {
        $pageData = Invoke-Ghn ('v2/shop/all?offset=' + $offset + '&limit=' + $pageSize)
        $pageShops = @($pageData.shops | Where-Object { $null -ne $_ })
        $shops += $pageShops
        if ($pageShops.Count -lt $pageSize) { break }
        $next = [int]$pageData.last_offset
        if ($next -le $offset -or $page -eq $maxPages - 1) { throw 'Chua doc du danh sach shop de kiem tra trung. Dung lai va kiem tra tren GHN.' }
        $offset = $next
    }
    $existing = @($shops | Where-Object { [string]$_._id -eq $ghnExistingShop })
    if ($existing.Count -ne 1 -or [string]$existing[0].status -ne '1') {
        throw 'Shop ID hien tai phai thuoc Token va dang hoat dong de goi API tao shop. Kiem tra tai khoan GHN test.'
    }
    Write-Host 'TAO DIEM LAY HANG GHN TEST - khong tao van don, khong goi shipper.'
    $shopName = Read-Required 'Ten cua hang' ((Get-AppSetting 'APP_NAME') + ' Test') 100
    $shopPhone = Read-Required 'SDT cua hang' ([string]$existing[0].phone) 20
    $shopPhone = $shopPhone -replace '[ .()-]', ''
    if ($shopPhone.StartsWith('+84')) { $shopPhone = '0' + $shopPhone.Substring(3) }
    if ($shopPhone -notmatch '^0[0-9]{9,10}$') { throw 'SDT cua hang khong hop le.' }

    $province = Select-Location 'Chon tinh/thanh NOI LAY HANG' (Invoke-Ghn 'master-data/province') 'ProvinceName'
    $district = Select-Location 'Chon quan/huyen NOI LAY HANG' (Invoke-Ghn ('master-data/district?province_id=' + $province.ProvinceID)) 'DistrictName'
    $ward = Select-Location 'Chon phuong/xa NOI LAY HANG' (Invoke-Ghn ('master-data/ward?district_id=' + $district.DistrictID)) 'WardName'
    $street = Read-Required 'So nha, ten duong NOI LAY HANG' '' 500
    $address = $street + ', ' + $ward.WardName + ', ' + $district.DistrictName + ', ' + $province.ProvinceName
    if ($address.Length -gt 1024 -or [int]$district.DistrictID -le 0 -or [string]::IsNullOrWhiteSpace([string]$ward.WardCode)) { throw 'Dia chi lay hang khong hop le.' }
    $body = [ordered]@{ name = $shopName; phone = $shopPhone; address = $address; ward_code = [string]$ward.WardCode; district_id = [int]$district.DistrictID }
    Write-Host ''
    Write-Host ('Ten shop: ' + $shopName)
    Write-Host ('Dien thoai: ' + $shopPhone)
    Write-Host ('Noi lay hang: ' + $address)
    Write-Host ('Ma quan: ' + $body.district_id + '; ma phuong: ' + $body.ward_code)
    if (-not $Create) {
        Write-Host 'CHE DO XEM TRUOC: chua tao shop/chua thay cau hinh. Chay lai voi -Create de tao shop test.'
        return
    }
    $duplicate = @($shops | Where-Object {
        [string]$_.name -eq $shopName -and [string]$_.phone -eq $shopPhone -and [string]$_.address -eq $address -and
        [string]$_.district_id -eq [string]$district.DistrictID -and [string]$_.ward_code -eq [string]$ward.WardCode -and [string]$_.status -eq '1'
    })
    if ($duplicate.Count -gt 0) {
        $newId = [string]$duplicate[0]._id
        Write-Host 'Da co shop trung thong tin: dung lai shop, khong tao them.'
    } else {
        $created = Invoke-Ghn 'v2/shop/register' $body
        $newId = [string]$created.shop_id
    }
    if ($newId -notmatch '^[1-9][0-9]{0,9}$') { throw 'GHN khong tra Shop ID hop le. Kiem tra danh sach shop tren GHN truoc khi chay lai.' }
    Write-Host ('Shop ID test: ' + $newId)
    [Environment]::SetEnvironmentVariable('GHN_SHOP_ID', $newId, 'User')
    $env:GHN_SHOP_ID = $newId
    Write-Host 'Da luu GHN_SHOP_ID cho tai khoan Windows nay. Dong/mo lai NetBeans va Tomcat, sau do bam Tinh phi GHN.'
    Write-Host 'Cac don da luu bang Shop ID cu van thuoc shop cu. Khong doi ShopID cua don da co van don.'
} finally {
    $ghnHttp.Dispose()
}
