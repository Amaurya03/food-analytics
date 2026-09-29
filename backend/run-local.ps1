# Stop on error
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFilePath = Join-Path $scriptDir "..\.env"

# Ensure valid JAVA_HOME is configured for the Maven wrapper
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $binDir = Split-Path -Parent $javaCmd.Source
        $javaHomeCandidate = Split-Path -Parent $binDir
        if (Test-Path $javaHomeCandidate) {
            [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHomeCandidate, "Process")
        }
    }
}

# Read ../.env file if present and set variables for the current process
if (Test-Path $envFilePath) {
    Write-Host "Loading environment variables from $envFilePath..." -ForegroundColor Cyan
    Get-Content $envFilePath | ForEach-Object {
        $line = $_.Trim()
        # Skip empty lines and comment lines
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split "=", 2
            if ($parts.Count -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                # Remove surrounding quotes if present
                if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                    $val = $val.Substring(1, $val.Length - 2)
                }
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
} else {
    Write-Warning ".env file not found at $envFilePath. Ensure environment variables are set in your environment."
}

Write-Host "Starting Spring Boot application via Maven wrapper..." -ForegroundColor Green
Set-Location $scriptDir
& "$scriptDir\mvnw.cmd" spring-boot:run
