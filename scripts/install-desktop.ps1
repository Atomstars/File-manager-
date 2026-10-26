param(
    [string]$PackageDirectory = '',
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'Programs\Findspace'),
    [string]$DesktopDirectory = [Environment]::GetFolderPath('Desktop')
)
$ErrorActionPreference = 'Stop'
if (-not $PackageDirectory) {
    $PackageDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'target\desktop\Findspace'
}
$PackageDirectory = [IO.Path]::GetFullPath($PackageDirectory)
foreach ($required in @('Findspace.exe', 'app\findspace.jar', 'runtime\bin\java.dll')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PackageDirectory $required))) {
        throw "Incomplete package: $required is missing. Run package-windows.ps1 first."
    }
}
if (-not (Test-Path -LiteralPath $DesktopDirectory -PathType Container)) {
    throw 'The desktop directory must already exist.'
}
$versionDirectory = Join-Path $InstallRoot ('2.0.0-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
New-Item -ItemType Directory -Path $versionDirectory | Out-Null
Copy-Item -LiteralPath $PackageDirectory -Destination (Join-Path $versionDirectory 'Findspace') -Recurse
$executable = Join-Path $versionDirectory 'Findspace\Findspace.exe'
$shortcutPath = Join-Path $DesktopDirectory 'Findspace.lnk'
if (Test-Path -LiteralPath $shortcutPath) {
    Copy-Item -LiteralPath $shortcutPath -Destination (Join-Path $versionDirectory 'previous-desktop-shortcut.lnk')
}
$shell = New-Object -ComObject WScript.Shell
try {
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $executable
    $shortcut.WorkingDirectory = Split-Path -Parent $executable
    $shortcut.IconLocation = "$executable,0"
    $shortcut.Description = 'Findspace — your files, folders and apps around one desktop orb'
    $shortcut.Save()
    $verified = $shell.CreateShortcut($shortcutPath)
    if ($verified.TargetPath -ne $executable) { throw 'Desktop shortcut verification failed.' }
} finally {
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) | Out-Null
}
Write-Output "Installed: $executable"
Write-Output "Desktop shortcut: $shortcutPath"
Write-Output 'Previous installations and preferences are preserved. Double-click Findspace to start.'
