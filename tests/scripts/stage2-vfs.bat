@echo off
call "%~dp0..\..\run.bat" --vfs "%~dp0..\..\src"
exit /b %ERRORLEVEL%

