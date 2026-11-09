$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path build -Force | Out-Null
    $sources = @(Get-ChildItem src,tests -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac --release 17 -Xlint:all -encoding UTF-8 -d build @sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java '-Djava.awt.headless=true' -cp build Tests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
