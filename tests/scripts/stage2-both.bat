@echo off
call "%~dp0..\..\run.bat" --vfs "%~dp0..\..\src" --script "%~dp0..\fixtures\stage2-success.txt"
exit /b %ERRORLEVEL%

