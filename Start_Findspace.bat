@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
  py -3 app.py
) else (
  where python >nul 2>nul
  if errorlevel 1 (
    echo Python 3.10 or later is required. Install Python from python.org and enable Add Python to PATH.
  ) else (
    python app.py
  )
)
pause
