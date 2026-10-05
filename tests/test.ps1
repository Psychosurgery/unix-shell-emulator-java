$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build\tests'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Filter '*.java' |
    ForEach-Object { $_.FullName })
$tests = @(Get-ChildItem -LiteralPath (Join-Path $root 'tests') -Filter '*.java' |
    ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -d $build @sources @tests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $build ShellTest
exit $LASTEXITCODE

