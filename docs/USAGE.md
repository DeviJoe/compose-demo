## Использование
### Настройки
При попытке зайти в web-интерфейс впервые, браузер спросит логин и пароль,
который указывался в env-файле.  
При необходимости можно настроить дополнительные параметры по кнопке с шестеренками в верхнем
правом углу экрана.

<img alt="Скриншот настроек" width="400" src="../screenshots/Screenshot_Settings.png"/>

### Создание сервисов
Сначала нужно создать сервисы, находящиеся в игре. Если не сделать этого, то никакие стримы не будут сохраняться! 
Для этого вызывается диалоговое окно по нажатию кнопки `+` в навбаре,
где можно указать название и порт сервиса, а также дополнительные опции.

<img alt="Скриншот окна создания сервиса" src="../screenshots/Screenshot_Service.png" width="600"/>

#### Параметры сервиса:
1. Имя
2. Порт (если сервис использует несколько портов, нужно создать по сервису на каждый порт)
3. Chunked transfer encoding: автоматически раскодировать [подобные](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Transfer-Encoding#chunked_encoding) http пакеты
4. Urldecode: автоматически проводить urldecode пакетов. Стоит включать по умолчанию в http сервисах.
5. Merge adjacent packets: автоматически склеивать соседние пакеты в одном направлении. Стоит включать по умолчанию в небинарных сервисах.
6. Inflate WebSockets: автоматически разархивировать [сжатые](https://www.rfc-editor.org/rfc/rfc7692) websocket-пакеты.
7. Decrypt TLS: автоматически расшифровывать TLS-трафик (HTTPS).
Работает только с типами шифрования TLS_RSA_WITH_AES_*, и при наличии приватного ключа, который использовался в сертификате сервера (как Wireshark).

### Создание паттернов
Для удобного отлова эксплоитов в приложении существует система паттернов.  
Чтобы создать паттерн, нужно открыть выпадающее меню `Patterns` и нажать кнопку `+`,
затем указать параметры паттерна и сохранить.

Важно: паттерн начнет действовать только на стримы, захваченные после его создания. Но можно использовать Lookback, чтобы проанализировать и стримы в прошлом.

<img alt="Скриншот окна создания паттерна" src="../screenshots/Screenshot_Pattern.png" width="400"/>

#### Параметры паттерна:
1. Имя: оно отображается в списке на стримах, которые содержат этот паттерн.
2. Паттерн: само содержимое паттерна. Может быть строкой, регулярным выражением или hex-строкой в зависимости от типа паттерна.
3. Действие паттерна
   1. Highlight подсветит найденный паттерн. Пример: поиск флагов.
   2. Ignore удалит стрим, содержащий этот паттерн. 
   Пример: вы запатчили сервис от определенной уязвимости и больше не хотите видеть определенный эксплоит в трафике. Можно добавить этот эксплоит как паттерн с типом IGNORE, и он больше не будет сохраняться.
4. Цвет: этим цветом будут подсвечиваться паттерны с типом Highlight
5. Метод поиска: подстрока, регулярное выражение, бинарная подстрока
6. Направление поиска: везде, только в запросах, только в ответах
7. Сервис: искать в трафике всех сервисов или в каком-то конкретном

### Начало игры
В режиме LIVE система начнет автоматически захватывать стримы и отображать их в сайдбаре.
В режиме FILE для начала обработки файла нужно нажать соответствующую кнопку в сайдбаре.
При нажатии на стрим в главном окне выводится список пакетов;
между бинарным и текстовым представлением можно переключиться по кнопке в сайдбаре.

### Обзор навбара
![Скриншот навбара](../screenshots/Screenshot_Navbar.png)

1. Заголовок
2. Счетчик SPM - Streams Per Minute, стримов в минуту
3. Счетчик PPS - Packets Per Stream, среднее количество пакетов в стриме
4. Кнопка открытия списка паттернов
5. Список сервисов. В каждом сервисе:
   1. Название
   2. Порт
   3. Счетчик SPM сервиса - позволяет определить наиболее популярные сервисы
   4. Кнопка редактирования сервиса
6. Кнопка добавления нового сервиса
7. Кнопка открытия настроек


### Обзор сайдбара
![Скриншот сайдбара](../screenshots/Screenshot_Sidebar.png)

В левой панели Packmate находятся стримы выбранного сервиса. 
Отображается номер стрима, протокол, TTL, сервис, время, хэш User-Agent (для http сервисов) и найденные паттерны.

Совет: иногда на CTF забывают перезаписать TTL пакетов внутри сети. В таком случае по TTL можно отличить запросы от чекеров и от других команд.

Совет #&#8203;2: по User-Agent можно отличать запросы из разных источников. К примеру, можно предположить, что на скриншоте выше запросы 4 и 5 пришли из разных источников.

Совет #&#8203;3: нажимайте на звездочку, чтобы добавить интересный стрим в избранное. Этот стрим будет выделен в списке, и появится в списке избранных стримов.

#### Управление просмотром

<img alt="Панель управления" src="../screenshots/Screenshot_Sidebar_header.png" width="400"/>

1. Пауза: Остановить/возобновить показ новых стримов на экране. Не останавливает перехват стримов и показ другим пользователям! Полезно, если стримы летят слишком быстро.
2. Избранные: показать только стримы, отмеченные как избранные
3. Переключить текстовый/hexdump вид
4. Начать анализ: появляется только при запуске в режиме `FILE`
5. Промотать список стримов до самого нового

### Обзор меню паттернов
![Список паттернов](../screenshots/Screenshot_Patterns.png)

1. Кнопка добавления паттерна
2. Выбор всех стримов (убрать фильтрацию по паттерну)
3. Список паттернов. В каждой строчке:
   1. Описание паттерна
   2. Кнопка Lookback - позволяет применить паттерн к стримам, обработанным до создания паттерна.
   3. Пауза - паттерн нельзя удалить, но можно поставить на паузу. После этого он не будет применяться к новым стримам.

Совет: создавайте отдельные паттерны для входящих и исходящих флагов. Так легче отличать чекер, кладущий флаги, от эксплоитов.

Совет #&#8203;2: используйте Lookback для исследования найденных эксплоитов.

Пример: вы обнаружили, что сервис только что отдал флаг пользователю `abc123` без видимых причин.
Можно предположить, что атакующая команда создала этого пользователя и подготовила эксплоит в другом стриме.
Но в игре слишком много трафика, чтобы найти этот стрим вручную.
Тогда можно создать `SUBSTRING` паттерн со значением `abc123` и активировать Lookback на несколько минут назад. 
После этого со включенным фильтром по паттерну будут отображаться только стримы, где упоминался этот пользователь.

### Горячие клавиши
Для быстрой навигации по стримам можно использовать следующие горячие клавиши:
* `Ctrl+Up` -- переместиться на один стрим выше
* `Ctrl+Down` -- переместиться на один стрим ниже
* `Ctrl+Home` -- перейти на последний стрим
* `Ctrl+End` -- перейти на первый стрим