@echo off
call "%~dp0..\..\run.bat" --vfs "%~dp0..\fixtures\vfs\missing"
exit /b %ERRORLEVEL%

