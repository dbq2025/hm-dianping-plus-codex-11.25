@echo off
setlocal
pushd "%~dp0" || exit /b 1
if not exist logs mkdir logs
if not exist temp mkdir temp
for %%F in (logs\nginx.pid) do if exist "%%F" if %%~zF GTR 0 (
    echo A project nginx PID file exists. Check the process before starting again.
    popd
    exit /b 1
)
nginx.exe -t
if errorlevel 1 (
    popd
    exit /b 1
)
start "" /b "%CD%\nginx.exe"
set "rc=%errorlevel%"
popd
exit /b %rc%
