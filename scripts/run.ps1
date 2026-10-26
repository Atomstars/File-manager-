$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
Push-Location $repository
try {
    & "$repository\mvnw.cmd" --batch-mode package
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    & java --enable-native-access=ALL-UNNAMED -jar "$repository\target\findspace.jar" @args
} finally { Pop-Location }
