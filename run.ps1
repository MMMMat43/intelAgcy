<#
.SYNOPSIS
    Simple entry point for IntelligentTestAgent.

.DESCRIPTION
    Hides environment details (finding a compatible JDK, gradlew flags,
    --args formatting) behind simple commands:

        .\run.ps1 build
        .\run.ps1 test
        .\run.ps1 analyze  -Source path\To\File.java
        .\run.ps1 generate -Source path\To\File.java [-Output path]
        .\run.ps1 serve    [-Port 8080]

    The LLM API key for real neural-network calls is read from a .env file
    in the project root (see .env.example) - no need to set environment
    variables by hand every time.
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'analyze', 'generate', 'serve', 'help')]
    [string]$Action = 'help',

    [string]$Source,
    [string]$Output,
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Test-JavaHomeCompatible {
    param([string]$CandidateHome)

    $javaExe = Join-Path $CandidateHome 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { return $false }

    # java -version writes to stderr by design. With $ErrorActionPreference
    # = 'Stop' (set globally in this script), PowerShell would otherwise
    # turn that stderr output into a terminating NativeCommandError even on
    # success, so it must be relaxed just for this native call.
    $previousEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $verOutput = & $javaExe -version 2>&1 | Out-String
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $previousEap
    }

    if ($verOutput -notmatch '"(\d+)') {
        return $false
    }

    # The Kotlin compiler version pinned in this project (2.0.21) only
    # reliably parses JDK major versions up to 21 - JDK 25/26 (including
    # JBRs bundled with newer JetBrains IDEs) make it crash internally.
    return ([int]$Matches[1] -le 21)
}

function Resolve-CompatibleJavaHome {
    # 1) Explicit override: .jdkhome file (not tracked by git), one line
    #    with the path to the JDK home directory. Trusted as-is, no version
    #    check, so you can force a specific JDK if auto-detection is wrong.
    $overrideFile = Join-Path $ProjectRoot '.jdkhome'
    if (Test-Path $overrideFile) {
        $override = (Get-Content $overrideFile -Raw).Trim()
        if ($override -and (Test-Path (Join-Path $override 'bin\java.exe'))) {
            return $override
        }
    }

    # 2) Current JAVA_HOME, if it is actually compatible.
    if ($env:JAVA_HOME -and (Test-JavaHomeCompatible $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    # 3) Look for a JBR bundled with a JetBrains IDE (PyCharm/IDEA/etc.),
    #    verifying each candidate's actual version - newer IDEs may ship
    #    JBRs based on JDK 25/26, which are just as incompatible as the
    #    system JDK, so every candidate must be checked individually.
    $jbrPatterns = @(
        "C:\Program Files\JetBrains\*\jbr",
        "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\*\ch-0\*\jbr"
    )
    foreach ($pattern in $jbrPatterns) {
        $candidates = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') }
        foreach ($candidate in $candidates) {
            if (Test-JavaHomeCompatible $candidate.FullName) {
                return $candidate.FullName
            }
        }
    }

    # 4) Look for a separately installed JDK 17/21.
    $jdkPatterns = @(
        "C:\Program Files\Java\jdk-17*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*"
    )
    foreach ($pattern in $jdkPatterns) {
        $candidates = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') }
        foreach ($candidate in $candidates) {
            if (Test-JavaHomeCompatible $candidate.FullName) {
                return $candidate.FullName
            }
        }
    }

    return $null
}

function Import-DotEnv {
    $envFile = Join-Path $ProjectRoot '.env'
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
                $parts = $line.Split('=', 2)
                $name = $parts[0].Trim()
                $value = $parts[1].Trim().Trim('"')
                if ($name -and $value) {
                    Set-Item -Path "Env:$name" -Value $value
                }
            }
        }
        Write-Host "Loaded .env" -ForegroundColor DarkGray
    }
}

$javaHome = Resolve-CompatibleJavaHome
if (-not $javaHome) {
    Write-Host "No compatible JDK (version 21 or lower) found on this machine." -ForegroundColor Red
    Write-Host "The Kotlin compiler used by this project cannot run on JDK 22+." -ForegroundColor Yellow
    Write-Host "Install a JDK 17 or 21 (e.g. Eclipse Temurin), or, if you already have" -ForegroundColor Yellow
    Write-Host "one bundled with a JetBrains IDE, create a '.jdkhome' file in the project" -ForegroundColor Yellow
    Write-Host "root containing a single line with its path, for example:" -ForegroundColor Yellow
    Write-Host "    C:\Program Files\JetBrains\PyCharm 2025.2.3\jbr" -ForegroundColor Yellow
    exit 1
}
$env:JAVA_HOME = $javaHome
Write-Host "JAVA_HOME: $javaHome" -ForegroundColor DarkGray

Import-DotEnv

$gradlew = Join-Path $ProjectRoot 'gradlew.bat'

switch ($Action) {
    'build' {
        & $gradlew build --no-daemon
    }
    'test' {
        & $gradlew test --no-daemon
    }
    'analyze' {
        if (-not $Source) {
            Write-Host "Usage: .\run.ps1 analyze -Source path\to\File.java" -ForegroundColor Red
            exit 1
        }
        & $gradlew run "--args=--source `"$Source`"" --no-daemon
    }
    'generate' {
        if (-not $Source) {
            Write-Host "Usage: .\run.ps1 generate -Source path\to\File.java" -ForegroundColor Red
            exit 1
        }
        $outDir = if ($Output) { $Output } else { "build/agent-output" }
        & $gradlew run "--args=--source `"$Source`" --generate-tests --output `"$outDir`"" --no-daemon
        Write-Host ""
        Write-Host "Artifacts saved to: $outDir" -ForegroundColor Green
    }
    'serve' {
        $env:PORT = "$Port"
        Write-Host "Starting REST API on port $Port (Ctrl+C to stop)..." -ForegroundColor Cyan
        & $gradlew runServer --no-daemon
    }
    default {
        Write-Host ""
        Write-Host "IntelligentTestAgent - quick start" -ForegroundColor Cyan
        Write-Host "===================================="
        Write-Host ""
        Write-Host "  .\run.ps1 build                                    - build the project"
        Write-Host "  .\run.ps1 test                                     - run tests"
        Write-Host "  .\run.ps1 analyze  -Source path\to\File.java        - analyze code"
        Write-Host "  .\run.ps1 generate -Source path\to\File.java        - generate tests"
        Write-Host "                                                       (artifacts in build\agent-output)"
        Write-Host "  .\run.ps1 generate -Source path\to\File.java -Output my_folder"
        Write-Host "                                                     - generate tests into your own folder"
        Write-Host "  .\run.ps1 serve                                    - REST API on port 8080 (for n8n)"
        Write-Host "  .\run.ps1 serve -Port 9090                         - REST API on a different port"
        Write-Host ""
        Write-Host "To enable real neural-network calls, copy .env.example to .env" -ForegroundColor DarkGray
        Write-Host "and fill in your LLM_API_KEY. Without a key, only heuristics are used." -ForegroundColor DarkGray
        Write-Host ""
    }
}
