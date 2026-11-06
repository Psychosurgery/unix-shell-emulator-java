# Проверки

`stage1..4.txt` — параметры, ошибки, тестовые графы и порядок загрузки.
Сравнение: pip 24.3.1, pipdeptree 2.30.0, Python 3.13.1, Windows, 06.11.2026.

| Пакет | Узлы | Рёбра | Версии и связи |
| --- | ---: | ---: | --- |
| requests 2.32.3 | 5 | 4 | совпали |
| flask 3.1.0 | 7 | 7 | совпали |
| rich 13.9.4 | 4 | 3 | совпали |

`*.d2`, `*.png` — наши графы; `*-pipdeptree.txt` — эталонные деревья.
В дереве Flask MarkupSafe повторяется, в графе это один общий узел.
Независимые зависимости requests и flask переставлены: топологический порядок не единственный.
Порядок rich совпал. Везде зависимости раньше пакета. Результаты — `*-order.txt`, `*-comparison.txt`.
При цикле выводим ошибку: строгого порядка нет. pip допускает установку участников цикла.

[PyPI API](https://dev.pypi.org/api-reference/json/), [порядок pip](https://pip.pypa.io/en/stable/cli/pip_install/#installation-order), [pipdeptree](https://pipdeptree.readthedocs.io/).
