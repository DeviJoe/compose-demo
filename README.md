<div align="center">

# Packmate
</div>

### [[EN](README_EN.md) | RU]
Утилита перехвата и анализа трафика для CTF.

#### Фичи:
* Поддерживает перехват живого трафика и обработку pcap файлов
* Поддерживает текстовые и бинарные сервисы
* Умеет отображать совпадения паттернов в пакетах цветом
  * Подстрока
  * Регулярное выражение
  * Бинарная подстрока
* Умеет сохранять стримы в избранное и отображать только избранные стримы
* Работает с несколькими сервисами на разных портах, может отображать стримы для конкретных сервисов и паттернов
* Поддерживает навигацию по стримам с помощью горячих клавиш
* Позволяет копировать содержимое пакета в нужном формате
* Конкатенирует смежные пакеты
* Автоматически проводит urldecode
* Разархивирует GZIP в HTTP на лету
* Разархивирует сжатые WebSockets

![Скриншот главного окна](screenshots/Screenshot.png)
## Клонирование
Поскольку этот репозиторий содержит фронтенд как git submodule, его необходимо клонировать так:
```bash
git clone --recurse-submodules https://gitlab.com/packmate/Packmate.git

# Или, на старых версиях git
git clone --recursive https://gitlab.com/packmate/Packmate.git
```

Если репозиторий уже был склонирован без подмодулей, необходимо выполнить:
```bash
git pull  # Забираем свежую версию мастер-репы из gitlab
git submodule update --init --recursive
```

## Подготовка
В этом ПО используется Docker и docker-compose. В образ `packmate-app` пробрасывается 
сетевой интерфейс хоста, его название указывается переменной окружения (об этом ниже).

`packmate-db` настроен на прослушивание порта 65001 с локальным IP.  
Файлы БД сохраняются в ./docker/postgres_data, поэтому для обнуления базы нужно удалить эту папку.

### Настройка
Программа берет основные настройки из переменных окружения, поэтому для удобства
можно создать env-файл.  
Он должен называться `.env` и лежать в корневой директории проекта.

В файле необходимо прописать:
```bash
# Локальный IP сервера на указанном интерфейсе или в pcap файле
PACKMATE_LOCAL_IP=192.168.1.124
# Имя пользователя для web-авторизации
PACKMATE_WEB_LOGIN=SomeUser
# Пароль для web-авторизации
PACKMATE_WEB_PASSWORD=SomeSecurePassword
```

Если мы перехватываем трафик сервера (лучший вариант, если есть возможность):
```bash
# Режим работы - перехват
PACKMATE_MODE=LIVE
# Интерфейс, на котором производится перехват трафика
PACKMATE_INTERFACE=wlan0
```
Если мы анализируем pcap дамп:
```bash
# Режим работы - анализ файла
PACKMATE_MODE=FILE
# Путь до файла от корня проекта
PACKMATE_PCAP_FILE=dump.pcap
```

### Запуск
После указания нужных настроек в env-файле, можно запустить приложение:
```bash
sudo docker-compose up --build -d
```

При успешном запуске Packmate будет видно с любого хоста на порту `65000`.

### Начало работы
При попытке зайти в web-интерфейс впервые, браузер спросит логин и пароль,
который указывался в env-файле.  
После успешного входа необходимо открыть настройки кликом по шестеренкам в правом
верхнем углу, затем ввести логин и пароль API, указанный при входе.

![Скриншот настроек](screenshots/Screenshot_Settings.png)

Все настройки сохраняются в local storage и теряются только при смене IP-адреса или порта сервера.

## Использование
Сначала нужно создать сервисы, находящиеся в игре.  
Для этого вызывается диалоговое окно по нажатию кнопки `+` в навбаре,
где можно указать название и порт сервиса, а также дополнительные опции.

Для удобного отлова флагов в приложении существует система паттернов.  
Чтобы создать паттерн, нужно открыть выпадающее меню `Patterns` и нажать кнопку `+`,
затем указать нужный тип поиска, сам паттерн, цвет подсветки в тексте и прочее.

В режиме LIVE система начнет автоматически захватывать стримы и отображать их в сайдбаре.
В режиме FILE для начала обработки файла нужно нажать соответствующую кнопку в сайдбаре. 
При нажатии на стрим в главном контейнере выводится список пакетов;
между бинарным и текстовым представлением можно переключиться по кнопке в сайдбаре.

### Горячие клавиши
Для быстрой навигации по стримам можно использовать следующие горячие клавиши:
* `Ctrl+Up` -- переместиться на один стрим выше
* `Ctrl+Down` -- переместиться на один стрим ниже
* `Ctrl+Home` -- перейти на последний стрим
* `Ctrl+End` -- перейти на первый стрим

<div align="right">

*desu~*
</div>
