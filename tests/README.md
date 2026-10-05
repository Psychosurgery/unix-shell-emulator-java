# Проверки

Запустите `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\test.ps1`.
Проверки не требуют сторонних библиотек: они компилируют исходники и
`ShellTest.java`, затем проверяют парсер, VFS, команды и остановку сценария.
