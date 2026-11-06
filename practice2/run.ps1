$ErrorActionPreference = 'Stop'
$build = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $build @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $build Main @args
exit $LASTEXITCODE
