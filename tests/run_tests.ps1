$ErrorActionPreference = 'Stop'
$heroRoot = Split-Path $PSScriptRoot -Parent
$heroClasses = Join-Path $heroRoot 'build/classes'
New-Item -ItemType Directory -Force $heroClasses | Out-Null
$heroSources = @(Get-ChildItem (Join-Path $heroRoot 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $heroClasses $heroSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$heroPassed = 0
$heroFailed = 0
$heroCases = @(Get-ChildItem (Join-Path $heroRoot 'examples/*.hero')) + @(Get-ChildItem (Join-Path $PSScriptRoot 'cases/*.hero'))
foreach ($heroCase in $heroCases) {
    $heroStart = New-Object System.Diagnostics.ProcessStartInfo
    $heroStart.FileName = 'java'
    $heroStart.Arguments = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "' + $heroClasses + '" heroscript.compiler.Main run "' + $heroCase.Name + '"'
    $heroStart.WorkingDirectory = $heroCase.DirectoryName
    $heroStart.UseShellExecute = $false
    $heroStart.CreateNoWindow = $true
    $heroStart.RedirectStandardOutput = $true
    $heroStart.RedirectStandardError = $true
    $heroStart.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $heroStart.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $heroProcess = [System.Diagnostics.Process]::Start($heroStart)
    $heroStdout = $heroProcess.StandardOutput.ReadToEndAsync()
    $heroStderr = $heroProcess.StandardError.ReadToEndAsync()
    $heroProcess.WaitForExit()
    $heroOutput = $heroStdout.Result.Replace("`r`n", "`n").TrimEnd("`n")
    $heroErrors = $heroStderr.Result
    $heroActual = $heroOutput + "`n[exit " + $heroProcess.ExitCode + ']'
    $heroExpectedPath = Join-Path $PSScriptRoot ('expected/' + $heroCase.BaseName + '.out')
    if (Test-Path $heroExpectedPath) {
        $heroExpected = [IO.File]::ReadAllText($heroExpectedPath).Replace("`r`n", "`n").TrimEnd("`n")
    } else { $heroExpected = $null }
    if ($heroActual -ceq $heroExpected -and $heroErrors.Length -eq 0) {
        $heroPassed++
        Write-Output "OK $($heroCase.BaseName)"
    } else {
        $heroFailed++
        Write-Output "FALHOU $($heroCase.BaseName)"
        Write-Output $heroActual
        if ($heroErrors) { Write-Output $heroErrors }
    }
    $heroProcess.Dispose()
}
Write-Output "$heroPassed passaram; $heroFailed falharam"
if ($heroFailed -gt 0) { exit 1 }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'FeatureTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'InvestigatorTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'SupervisionTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'FlashTravelTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'DeadpoolActionTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'BatplanTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'HulkSmashTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'SpiderSenseTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'ValidationTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java '-Dfile.encoding=UTF-8' -cp $heroClasses (Join-Path $PSScriptRoot 'RevisionFixTests.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location (Split-Path $PSScriptRoot -Parent)
try { & java '-Dfile.encoding=UTF-8' -cp build/classes tests/RunnerServerTests.java } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (Get-Command node -ErrorAction SilentlyContinue) {
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & node tests/spiderSense-editor.mjs
        if ($LASTEXITCODE -eq 0) { & node tests/playground-server.mjs }
    } finally { Pop-Location }
}
exit $LASTEXITCODE
