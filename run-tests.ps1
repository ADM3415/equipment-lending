$ErrorActionPreference = "Stop"

$maven = "C:\tools\apache-maven-3.9.16\bin\mvn.cmd"

$tests = @(
    "LoanRestoreTest"
    "EmailTest"
    "DatabaseConnectionTest"
    "JdbcItemRepositoryTest"
    "JdbcStudentRepositoryTest"
    "JdbcStaffRepositoryTest"
    "JdbcTeacherRepositoryTest"
    "JdbcLoanRepositoryTest"
    "JdbcChargeRepositoryTest"
    "JdbcInvoiceRepositoryTest"
    "JdbcInvoiceDispatcherTest"
    "JdbcLoanRegistrationServiceTest"
)

Push-Location $PSScriptRoot

try {
    if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
        throw "Maven was not found at: $maven"
    }

    foreach ($name in @("DB_URL", "DB_USERNAME", "DB_PASSWORD")) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")

        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "$name is not configured in this PowerShell session."
        }
    }

    Write-Host "`nCompiling application and tests..." -ForegroundColor Cyan

    & $maven test-compile

    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed. No tests were run."
    }

    $passed = 0

    foreach ($test in $tests) {
        Write-Host "`nRunning $test..." -ForegroundColor Cyan

        & $maven org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
            "-Dexec.mainClass=org.example.equipmentlending.$test" `
            "-Dexec.classpathScope=test" `
            "-Dcom.mysql.cj.disableAbandonedConnectionCleanup=true"

        if ($LASTEXITCODE -ne 0) {
            throw "$test failed. Stopping the test suite."
        }

        $passed++
    }

    Write-Host "`nAll $passed test classes passed." -ForegroundColor Green
}
finally {
    Pop-Location
}