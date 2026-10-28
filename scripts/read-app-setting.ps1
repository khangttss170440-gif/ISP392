# Read the same public defaults/external file as Java. Never print configuration values.
$script:ApplicationConfigRoot = Split-Path -Parent $PSScriptRoot
function Get-AppSetting([string]$Key) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Key, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value.Trim() }
    }
    $external = ''
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $external = [Environment]::GetEnvironmentVariable('MENSFORM_CONFIG_FILE', $scope)
        if (-not [string]::IsNullOrWhiteSpace($external)) { break }
    }
    if ([string]::IsNullOrWhiteSpace($external)) {
        $external = Join-Path $script:ApplicationConfigRoot 'config/application.local.properties'
    } elseif (-not (Test-Path -LiteralPath $external -PathType Leaf)) {
        throw 'MENSFORM_CONFIG_FILE does not reference a readable file.'
    }
    foreach ($file in @($external, (Join-Path $script:ApplicationConfigRoot 'src/java/application.properties'))) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        $candidate = ''
        foreach ($line in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
            if ($line -match '^\s*([^#!\s=:]+)\s*[=:]\s*(.*)$' -and $Matches[1] -ceq $Key) {
                $candidate = $Matches[2].Trim()
            }
        }
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return $candidate.Replace('\:', ':').Replace('\=', '=').Replace('\\', '\')
        }
    }
    return ''
}
