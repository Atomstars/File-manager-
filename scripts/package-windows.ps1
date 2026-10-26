param([string]$Destination = '')
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
Push-Location $repository
try {
    & "$repository\mvnw.cmd" --batch-mode verify
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed. No package was created.' }
    $inputDirectory = Join-Path $repository ('target\package-input-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $inputDirectory | Out-Null
    Copy-Item -LiteralPath "$repository\target\findspace.jar" -Destination $inputDirectory
    Copy-Item -LiteralPath "$repository\target\lib" -Destination "$inputDirectory\lib" -Recurse
    if (-not $Destination) { $Destination = Join-Path $repository 'target\desktop' }
    $Destination = [IO.Path]::GetFullPath($Destination)
    if (Test-Path -LiteralPath (Join-Path $Destination 'Findspace')) {
        throw "An app already exists in $Destination. Choose a new -Destination to preserve it."
    }
    $javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command jpackage).Source }
    & "$javaBin\jpackage.exe" --type app-image --name Findspace --app-version 2.0.0 --vendor Atomstars `
        --description 'Find your files. Understand your space.' --input $inputDirectory --main-jar findspace.jar `
        --main-class io.github.atomstars.findspace.bootstrap.Findspace --dest $Destination `
        --icon "$repository\findspace.ico" --java-options '--enable-native-access=ALL-UNNAMED' `
        --add-modules java.base,java.desktop,java.sql,java.logging,java.naming,jdk.unsupported,jdk.crypto.ec
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
    Write-Output "Built: $Destination\Findspace\Findspace.exe"
    Write-Output 'Distribute the entire Findspace folder; its bundled runtime removes the need to install Java.'
} finally { Pop-Location }
