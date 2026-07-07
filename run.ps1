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
        .\run.ps1 up        (Docker: whole stack - agent + n8n, one command)
        .\run.ps1 down      (Docker: stop the whole stack)

    The LLM API key for real neural-network calls is read from a .env file
    in the project root (see .env.example) - no need to set environment
    variables by hand every time.
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'analyze', 'generate', 'serve', 'up', 'down', 'help')]
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
    'up' {
        $docker = Get-Command docker -ErrorAction SilentlyContinue
        if (-not $docker) {
            Write-Host "Docker was not found. Install Docker Desktop first:" -ForegroundColor Red
            Write-Host "    https://www.docker.com/products/docker-desktop/" -ForegroundColor Yellow
            exit 1
        }

        try {
            docker version --format '{{.Server.Version}}' 2>&1 | Out-Null
        } catch { }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Docker was found but does not seem to be running." -ForegroundColor Red
            Write-Host "Please start Docker Desktop and wait for it to finish initializing," -ForegroundColor Yellow
            Write-Host "then run '.\run.ps1 up' again." -ForegroundColor Yellow
            exit 1
        }

        # Make sure the host-side output folder exists BEFORE the container
        # mounts it. If this folder gets deleted while the agent container is
        # already running (its volume mounted), Docker Desktop on Windows can
        # leave the mount in a broken state inside the container, causing
        # confusing request-parsing errors on /generate-tests. Recreating it
        # here, before (re)starting the container, avoids that entirely.
        New-Item -ItemType Directory -Force -Path (Join-Path $ProjectRoot 'build\agent-output') | Out-Null

        Write-Host "Building and starting the full stack (agent + n8n)..." -ForegroundColor Cyan
        Write-Host "First run may take a few minutes (downloading base images and" -ForegroundColor DarkGray
        Write-Host "Gradle dependencies inside the build container)." -ForegroundColor DarkGray
        docker compose up --build -d
        if ($LASTEXITCODE -ne 0) {
            Write-Host "docker compose up failed - see the output above for details." -ForegroundColor Red
            exit 1
        }

        Write-Host ""
        Write-Host "Waiting for the agent's REST API to become ready..." -ForegroundColor Cyan
        $ready = $false
        $deadline = (Get-Date).AddSeconds(60)
        while ((Get-Date) -lt $deadline) {
            try {
                $response = Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get -TimeoutSec 3
                if ($response.status -eq "ok") {
                    $ready = $true
                    break
                }
            } catch {
                Start-Sleep -Seconds 2
            }
        }

        Write-Host ""
        if ($ready) {
            Write-Host "Everything is ready! Open your browser at:" -ForegroundColor Green
            Write-Host "  Agent API health check: http://localhost:8080/health" -ForegroundColor Green
            Write-Host "  n8n (visual interface): http://localhost:5678" -ForegroundColor Green
        } else {
            Write-Host "The agent did not respond within 60 seconds." -ForegroundColor Yellow
            Write-Host "It might still be starting up - check logs with:" -ForegroundColor Yellow
            Write-Host "  docker compose logs -f agent" -ForegroundColor Yellow
            Write-Host "n8n should still be reachable at: http://localhost:5678" -ForegroundColor Yellow
        }
    }
    'down' {
        docker compose down
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
        Write-Host "  .\run.ps1 serve                                    - REST API on port 8080 (local JDK, no Docker)"
        Write-Host "  .\run.ps1 serve -Port 9090                         - REST API on a different port"
        Write-Host ""
        Write-Host "  .\run.ps1 up                                       - ONE COMMAND: build + start agent + n8n in Docker"
        Write-Host "                                                       (requires Docker Desktop)"
        Write-Host "  .\run.ps1 down                                     - stop the whole Docker stack"
        Write-Host ""
        Write-Host "Recommended for a full demo: .\run.ps1 up" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "To enable real neural-network calls, copy .env.example to .env" -ForegroundColor DarkGray
        Write-Host "and fill in your LLM_API_KEY. Without a key, only heuristics are used." -ForegroundColor DarkGray
        Write-Host ""
    }
}
