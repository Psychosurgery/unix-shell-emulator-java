@echo off
for %%F in (ls cd cat option date whoami) do (
    call "%~dp0..\..\run.bat" --vfs "%~dp0..\fixtures\vfs\deep" --script "%~dp0..\fixtures\stage4-error-%%F.txt"
    if not errorlevel 1 exit /b 1
)
exit /b 0

