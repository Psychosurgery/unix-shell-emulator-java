# Практическая 2, вариант 7

Java 17+. Без библиотек.

`run.bat --package requests --version 2.32.3`

Опции: `--package`, `--version`, `--repository`, `--test true|false`, `--filter`.
По умолчанию репозиторий — `https://pypi.org/pypi`, фильтр пустой.
В тестовом режиме нужен файл и имя из заглавных латинских букв.

Проверка: `powershell -File test.ps1`.

Зависимости — `requires_dist` указанной версии через PyPI JSON API.
`--mode config` выводит настройки, `--mode direct` — прямые зависимости.

По умолчанию — граф, рекурсивный DFS. Фильтр убирает пакет вместе с его веткой.
Тест: `run.bat --package A --version 1 --test true --repository tests/fixtures/cycle.json`.
Формат файла: `{"A":{"1":["B==1"]},"B":{"1":[]}}`.
Маркеры: CPython 3.13.0, текущая ОС. Можно задать `--python-version` и `--platform win32|linux|darwin`.
Выбирается максимальная стабильная версия без yanked. Конфликт версий — ошибка, без перебора назад.
URL-зависимости и нестандартные записи версий не поддержаны.
