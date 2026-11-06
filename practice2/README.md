# Практическая 2, вариант 7

Java 17. Без библиотек. PyPI JSON API, рекурсивный DFS.

`run.bat --package requests --version 2.32.3 --mode render`

`--package`, `--version` обязательны. `--repository` — `https://pypi.org/pypi`.
`--mode config|direct|graph|order|render`, по умолчанию `graph`.
`--filter` убирает пакет с подстрокой и его ветку. При цикле порядка загрузки нет.
`--output out/graph` — D2 и PNG. `--show false` отключает окно. PNG рисует Java2D.
`--python-version 3.13.0`, `--platform win32|linux|darwin` (по умолчанию текущая ОС).

Тестовый граф: `run.bat --package A --version 1 --test true --repository tests/fixtures/diamond.json`.
Формат: `{"A":{"1":["B==1"]},"B":{"1":[]}}`. Имена — заглавная латиница.

Проверки: `powershell -File test.ps1`. Примеры и сравнения — [examples](examples/README.md).
Сравнение онлайн после сборки: `python tests/compare.py requests 2.32.3` (также `flask 3.1.0`, `rich 13.9.4`).
Зависимости: максимальные стабильные версии без yanked. Конфликты — ошибка, без перебора назад.
URL-зависимости и нестандартные записи версий не поддержаны.
