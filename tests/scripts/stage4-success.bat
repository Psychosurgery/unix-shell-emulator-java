@echo off
call "%~dp0..\..\run.bat" --vfs "%~dp0..\fixtures\vfs\deep" --script "%~dp0..\fixtures\stage4-success.txt"
exit /b %ERRORLEVEL%

