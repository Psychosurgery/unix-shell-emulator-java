$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Filter '*.java' |
    ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -d $build @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $build Main @args
exit $LASTEXITCODE

