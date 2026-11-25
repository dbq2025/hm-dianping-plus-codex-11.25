@echo off
rem Start nginx for hm-dianping frontend (must run from ASCII path, Chinese path is NOT supported by nginx for Windows)
cd /d E:\hmdp-nginx
tasklist /FI "IMAGENAME eq nginx.exe" | find /I "nginx.exe" >nul
if %errorlevel%==0 (
    echo nginx is already running. http://localhost:8080/index.html
    pause
    exit /b 0
)
nginx.exe -p E:\hmdp-nginx\
echo nginx started. Open http://localhost:8080/index.html
pause
