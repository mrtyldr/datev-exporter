[CmdletBinding()]
param(
    [string]$Fixture,
    [switch]$Wait
)

$ErrorActionPreference = 'Stop'
$checkerVersion = '2.2.3.0'
$checkerUrl = 'https://developer.datev.de/assets/Datev_Format_Pruefprogramm_2_2_3_0_76439824cb.zip'
$checkerSha256 = '216f2028bfb35b4d3abcde9b4a9ab12873ceff9b9c88ca94294e45a5c056a6dc'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Fixture)) {
    $Fixture = Join-Path $repositoryRoot 'build/datev-checker/fixture/EXTF_Buchungsstapel.csv'
}

$fixturePath = [System.IO.Path]::GetFullPath($Fixture)
if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
    throw "Fixture does not exist: $fixturePath. Run ./gradlew generateDatevCheckerFixture first."
}

if (-not [string]::IsNullOrWhiteSpace($env:DATEV_CHECKER_EXE)) {
    $checkerExe = [System.IO.Path]::GetFullPath($env:DATEV_CHECKER_EXE)
    if (-not (Test-Path -LiteralPath $checkerExe -PathType Leaf)) {
        throw "DATEV_CHECKER_EXE does not point to a file: $checkerExe"
    }
} else {
    $checkerRoot = Join-Path $repositoryRoot "build/datev-checker/manual/$checkerVersion"
    $archive = Join-Path $checkerRoot 'datev-checker.zip'
    $extracted = Join-Path $checkerRoot 'extracted'
    $checkerExe = Join-Path $extracted 'DatevFormatPruefprogramm/DatevFormatPruefProgramm.exe'

    New-Item -ItemType Directory -Force -Path $checkerRoot | Out-Null
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        Write-Host "Downloading official DATEV checker $checkerVersion ..."
        Invoke-WebRequest -Uri $checkerUrl -OutFile $archive -UseBasicParsing -TimeoutSec 30
    }

    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $checkerSha256) {
        Remove-Item -LiteralPath $archive -Force
        throw "DATEV checker checksum mismatch. Expected $checkerSha256, got $actualSha256."
    }

    if (-not (Test-Path -LiteralPath $checkerExe -PathType Leaf)) {
        if (Test-Path -LiteralPath $extracted) {
            Remove-Item -LiteralPath $extracted -Recurse -Force
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $extracted
    }
}

Write-Warning 'The official DATEV checker is a Windows GUI application without a documented headless validation result or exit-code contract.'
Write-Warning 'This launcher only opens the fixture. Inspect the displayed report manually; a successful process launch is not a compatibility pass.'

$argument = '-o:"{0}"' -f $fixturePath
$process = Start-Process -FilePath $checkerExe -ArgumentList $argument -PassThru
Write-Host "Opened $fixturePath in DATEV checker (PID $($process.Id))."

if ($Wait) {
    Write-Host 'Waiting for the checker window to be closed manually ...'
    $process.WaitForExit()
    Write-Host "Checker process exited with code $($process.ExitCode). This code is not interpreted as a validation result."
}
