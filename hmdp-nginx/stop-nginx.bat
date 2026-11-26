@echo off
setlocal
pushd "%~dp0" || exit /b 1
set "HMDP_NGINX_EXE=%CD%\nginx.exe"
powershell.exe -NoProfile -NonInteractive -Command "$nginxId = 0; if (!(Test-Path -LiteralPath 'logs\nginx.pid')) { exit 1 }; if (![int]::TryParse((Get-Content -LiteralPath 'logs\nginx.pid' -Raw).Trim(), [ref]$nginxId)) { exit 1 }; $process = Get-Process -Id $nginxId -ErrorAction SilentlyContinue; if (!$process -or $process.Path -ne $env:HMDP_NGINX_EXE) { exit 1 }"
if errorlevel 1 (
    echo The project nginx process could not be verified.
    popd
    exit /b 1
)
nginx.exe -s quit
set "rc=%errorlevel%"
popd
exit /b %rc%
