@echo off
call "%~dp0..\..\run.bat" --script "%~dp0..\fixtures\stage2-success.txt"
exit /b %ERRORLEVEL%

