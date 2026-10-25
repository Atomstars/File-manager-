@echo off
setlocal
cd /d "%~dp0"
echo Building the Windows portable Findspace.exe. Internet and Python are required for this one-time build.
where py >nul 2>nul
if %errorlevel% equ 0 (
    py -3 -m venv .build-env
) else (
    python -m venv .build-env
)
if errorlevel 1 goto failed
.build-env\Scripts\python.exe -m pip install "pyinstaller==6.22.3"
if errorlevel 1 goto failed
.build-env\Scripts\python.exe -m unittest discover -s tests -v
if errorlevel 1 goto failed
.build-env\Scripts\python.exe -m PyInstaller --noconfirm --clean --onefile --windowed --name Findspace --icon findspace.ico --add-data "findspace.ico;." Findspace.pyw
if errorlevel 1 goto failed
echo.
echo SUCCESS: dist\Findspace.exe
echo Copy that single file to your desktop. Python is not required to run the built EXE.
echo Source mode remains available through Launch_Findspace.vbs.
pause
exit /b 0
:failed
echo.
echo Build failed. Check the messages above. No executable is ready unless the build succeeded.
pause
exit /b 1
