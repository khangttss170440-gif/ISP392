param(
    [Parameter(Mandatory = $true)][string]$TomcatHome,
    [string]$AntHome = $env:ANT_HOME
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskPreviousLocation = Get-Location
$taskKeys = @('MENSFORM_CONFIG_FILE', 'AUDIT_ENV', 'VNPAY_TMN_CODE', 'VNPAY_HASH_SECRET',
    'SHIPPING_FREE_THRESHOLD',
    'GHN_ESTIMATED_ITEM_WEIGHT_GRAMS', 'GHN_MAX_WEIGHT_GRAMS', 'OUTFIT_ROLE_TOP',
    'VNPAY_EXPIRY_MINUTES', 'VNPAY_ORDER_TYPE', 'APP_NAME', 'GHN_SYNC_ENABLED',
    'PRODUCT_IMAGE_MAX_FILE_BYTES')
$taskKeys += @('GHN_SHOP_ID', 'GHN_TOKEN', 'GHN_ESTIMATED_LENGTH_CM',
    'GHN_ESTIMATED_WIDTH_CM', 'GHN_ESTIMATED_HEIGHT_CM', 'GHN_SHOP_PAGE_SIZE')
$taskSavedEnvironment = @{}
foreach ($key in $taskKeys) {
    $taskSavedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    [Environment]::SetEnvironmentVariable($key, $null, 'Process')
}
try {
    Set-Location -LiteralPath $taskRoot
    [xml]$taskDeploymentContext = Get-Content -LiteralPath web/META-INF/context.xml -Raw
    $taskContextPath = $taskDeploymentContext.Context.GetAttribute('path')
    if ([string]::IsNullOrWhiteSpace($taskContextPath) -or -not $taskContextPath.StartsWith('/')) {
        throw 'NetBeans deployment requires a nonempty Context path in web/META-INF/context.xml.'
    }
    [Environment]::SetEnvironmentVariable('AUDIT_ENV', 'environment', 'Process')
    [Environment]::SetEnvironmentVariable('VNPAY_TMN_CODE', 'TEST0001', 'Process')
    [Environment]::SetEnvironmentVariable('VNPAY_HASH_SECRET', 'test-only-key', 'Process')
    [Environment]::SetEnvironmentVariable('GHN_SHOP_ID', '123', 'Process')
    [Environment]::SetEnvironmentVariable('GHN_TOKEN', 'test-only-token', 'Process')
    $taskClasses = Join-Path $taskRoot 'test/.scratch/config-classes'
    New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
    $taskSources = @(Get-ChildItem src/java -Recurse -Filter '*.java')
    $taskSources += @(Get-Item test/java/util/ConfigurationRegressionTest.java,
        test/java/dao/PaymentResultRegressionTest.java, test/java/util/RuntimeConfigurationIntegrationTest.java,
        test/java/service/CarrierConfigRegressionTest.java, test/java/util/ConfigDiscoveryRegressionTest.java,
        test/java/service/ProductSearchRegressionTest.java, test/java/service/CollectionSearchRegressionTest.java,
        test/java/controller/ChatbotSearchIntegrationTest.java)
    $taskArguments = $taskSources | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
    [IO.File]::WriteAllLines((Join-Path $taskRoot 'test/.scratch/config-sources.txt'),
        $taskArguments, [Text.UTF8Encoding]::new($false))
    $taskClasspath = "$TomcatHome/lib/*;$TomcatHome/bin/tomcat-juli.jar;$taskClasses"
    & javac -encoding UTF-8 -source 17 -target 17 -cp $taskClasspath -d $taskClasses '@test/.scratch/config-sources.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item -LiteralPath src/java/application.properties -Destination (Join-Path $taskClasses 'application.properties')
    foreach ($test in @('util.ConfigurationRegressionTest', 'util.ConfigDiscoveryRegressionTest', 'dao.PaymentResultRegressionTest',
            'service.CarrierConfigRegressionTest', 'service.ProductSearchRegressionTest', 'service.CollectionSearchRegressionTest',
            'controller.ChatbotSearchIntegrationTest', 'util.RuntimeConfigurationIntegrationTest')) {
        & java -cp $taskClasspath $test
        if ($LASTEXITCODE -ne 0) { throw "Regression check failed: $test" }
    }
    foreach ($asset in Get-ChildItem web/assets -Filter '*.js') {
        & node --check $asset.FullName
        if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed: $($asset.Name)" }
    }
    & node --test test/web/chatbot-timeout.spec.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Chatbot browser timeout regression checks failed.' }
    & node --test test/web/app-shell.spec.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Application navigation regression checks failed.' }
    foreach ($script in Get-ChildItem scripts -Filter '*.ps1') {
        $taskTokens = $null
        $taskParseErrors = $null
        [Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$taskTokens, [ref]$taskParseErrors) | Out-Null
        if ($taskParseErrors.Count -gt 0) { throw "PowerShell syntax check failed: $($script.Name)" }
    }
    if ($AntHome) {
        & java -cp "$taskClasspath;$AntHome/lib/*" org.apache.jasper.JspC -uriroot web `
            -d test/.scratch/jsp-classes -compile -classpath `
            "$taskClasses;$TomcatHome/lib/servlet-api.jar;$TomcatHome/lib/jsp-api.jar;$TomcatHome/lib/el-api.jar"
        if ($LASTEXITCODE -ne 0) { throw 'JSP compilation failed.' }
    }
    Write-Output 'Configuration, payment, Tomcat runtime and source syntax checks passed.'
} finally {
    Set-Location -LiteralPath $taskPreviousLocation
    foreach ($key in $taskKeys) {
        [Environment]::SetEnvironmentVariable($key, $taskSavedEnvironment[$key], 'Process')
    }
}
