@echo off
call "%~dp0..\..\run.bat" --vfs "%~dp0..\fixtures\vfs\minimal" --script "%~dp0..\fixtures\stage3-success.txt"
exit /b %ERRORLEVEL%

