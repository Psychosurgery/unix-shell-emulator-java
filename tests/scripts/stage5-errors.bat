@echo off
for %%F in (missing cycle root arity multi parent) do (
    call "%~dp0..\..\run.bat" --vfs "%~dp0..\fixtures\vfs\deep" --script "%~dp0..\fixtures\stage5-error-%%F.txt"
    if not errorlevel 1 exit /b 1
)
exit /b 0

