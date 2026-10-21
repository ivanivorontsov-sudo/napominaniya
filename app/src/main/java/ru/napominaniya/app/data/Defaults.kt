package ru.napominaniya.app.data

object Defaults {
    private const val H = 60L
    private const val D = 1440L

    val categories = listOf(
        Category(name = "Работа", color = 0xFF1E88E5.toInt()),
        Category(name = "Здоровье", color = 0xFFE53935.toInt()),
        Category(name = "Дом", color = 0xFF43A047.toInt()),
        Category(name = "Личное", color = 0xFF8E24AA.toInt()),
        Category(name = "Финансы", color = 0xFFFFB300.toInt()),
        Category(name = "Учёба", color = 0xFF00897B.toInt()),
        Category(name = "Поездки", color = 0xFF00ACC1.toInt())
    )

    fun profiles() = listOf(
        Profile(name = "Работа", emoji = "💼", color = 0xFF1E88E5.toInt(), priority = Priority.NORMAL, preReminders = listOf(15),
            description = "Обычные рабочие задачи: всплывающее уведомление, предупреждение за 15 минут",
            alert = AlertSettings(importance = Importance.HIGH, vibration = VibrationKind.NORMAL, snoozeMinutes = 10)),
        Profile(name = "Лекарства", emoji = "💊", color = 0xFFE53935.toInt(), priority = Priority.HIGH,
            description = "Нельзя смахнуть, повтор каждые 10 минут до подтверждения, пульсирующая вибрация",
            alert = AlertSettings(importance = Importance.HIGH, vibration = VibrationKind.HEARTBEAT, persistent = true,
                nagEveryMinutes = 10, nagMaxTimes = 6, snoozeMinutes = 15, maxSnoozes = 3, ledColor = 0xFF00FF00.toInt(), ignoreQuietHours = true)),
        Profile(name = "Сон", emoji = "😴", color = 0xFF5E35B1.toInt(), priority = Priority.HIGH,
            description = "Будильник: полный экран, плавное нарастание 60 с, пример для отключения, звонит даже в беззвучном",
            alert = AlertSettings(ringMode = true, gradualSeconds = 60, volume = 85, challenge = Challenge.MATH, mathDifficulty = 2,
                silentBehavior = SilentBehavior.FORCE_SOUND, ignoreQuietHours = true, snoozeMinutes = 9, maxSnoozes = 3, autoDismissMinutes = 15)),
        Profile(name = "Встреча", emoji = "🤝", color = 0xFF00897B.toInt(), priority = Priority.HIGH, preReminders = listOf(60, 15),
            description = "Предупреждения за час и за 15 минут, короткая вибрация",
            alert = AlertSettings(importance = Importance.HIGH, vibration = VibrationKind.SHORT, snoozeMinutes = 5)),
        Profile(name = "Критично", emoji = "🚨", color = 0xFFD50000.toInt(), priority = Priority.CRITICAL,
            description = "Полноэкранный звонок, повтор каждые 5 минут, игнорирует тихие часы и беззвучный режим",
            alert = AlertSettings(importance = Importance.URGENT, ringMode = true, persistent = true, nagEveryMinutes = 5, nagMaxTimes = 10,
                vibration = VibrationKind.SOS, silentBehavior = SilentBehavior.FORCE_SOUND, ignoreQuietHours = true, volume = 100, ledColor = 0xFFFF0000.toInt())),
        Profile(name = "Тихо", emoji = "🤫", color = 0xFF78909C.toInt(), priority = Priority.LOW,
            description = "Без звука и вибрации, просто запись в шторке",
            alert = AlertSettings(importance = Importance.LOW, vibration = VibrationKind.NONE, soundUri = "")),
        Profile(name = "Спорт и вода", emoji = "💧", color = 0xFF039BE5.toInt(), priority = Priority.NORMAL,
            description = "Короткое уведомление, само исчезает через 30 минут",
            alert = AlertSettings(importance = Importance.DEFAULT, vibration = VibrationKind.SHORT, autoDismissMinutes = 30))
    )

    private fun s(off: Long, title: String, text: String = "", repeat: RepeatType = RepeatType.ONCE, p: Priority = Priority.NORMAL) =
        ScenarioStep(offsetMinutes = off, title = title, text = text, repeat = repeat, priority = p)

    private const val MED = "Это общая справочная информация по материалам медицинских лабораторий и клиник. Точные правила зависят от конкретного исследования — обязательно следуйте инструкциям вашей клиники и лечащего врача."
    private const val MEDS = "Приложение не заменяет консультацию врача. Схему приёма, дозировку и длительность курса определяет врач."

    fun scenarios(): List<ScenarioTemplate> = listOf(
        ScenarioTemplate(name = "Анализ крови натощак", emoji = "🩸", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время сдачи анализа", disclaimer = MED,
            description = "Голодание 8–12 ч, без алкоголя за сутки, без курения за час, только вода",
            steps = listOf(
                s(-2 * D, "Через 2 дня — анализ крови", "Начните исключать жирное, жареное, острое и алкоголь. Если принимаете лекарства — уточните у врача, нужно ли их отменять перед анализом."),
                s(-1 * D, "Завтра анализ крови", "Сегодня: никакого алкоголя (минимум 24 ч), жирной и жареной еды, тяжёлых нагрузок, бани и сауны. Подготовьте паспорт, полис ОМС/ДМС и направление.", p = Priority.HIGH),
                s(-14 * H, "Лёгкий ужин", "Поешьте легко и без жирного — примерно через 2 часа начнётся голодание."),
                s(-12 * H, "Больше не есть", "Начало голодания (8–12 ч). Можно только чистую негазированную воду. Сок, чай, кофе, сладкие напитки и жевательная резинка — нельзя.", p = Priority.HIGH),
                s(-8 * H, "Крайний срок: не есть", "Минимальное голодание — 8 часов. Дальше только вода."),
                s(-90, "Пора выходить", "Возьмите паспорт, полис, направление и бутылку воды. Лекарства утром — только по согласованию с врачом."),
                s(-60, "Не курить", "Воздержитесь от курения за 1 час до забора крови. Избегайте физических и эмоциональных нагрузок.", p = Priority.HIGH),
                s(-15, "Посидите спокойно", "Отдохните 10–15 минут перед процедурой."),
                s(0, "Время сдачи анализа", "Удачи!"),
                s(30, "Можно поесть", "Анализ сдан — можно позавтракать и принять отложенные лекарства (если врач разрешил).")
            )),
        ScenarioTemplate(name = "УЗИ брюшной полости", emoji = "🩻", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время УЗИ", disclaimer = MED, description = "Диета без газообразующих продуктов, натощак 6–8 ч",
            steps = listOf(
                s(-3 * D, "Диета перед УЗИ", "Исключите продукты, вызывающие газообразование: чёрный хлеб, бобовые, капусту, сырые овощи и фрукты, цельное молоко, газировку, сладости. Препараты (например, от вздутия) — только по назначению."),
                s(-1 * D, "Завтра УЗИ", "Лёгкий ужин не позднее 19–20 часов."),
                s(-8 * H, "Не есть", "УЗИ брюшной полости делают натощак (обычно 6–8 ч без еды). Не курите и не жуйте жвачку.", p = Priority.HIGH),
                s(-60, "Выходите", "Возьмите направление, результаты прошлых исследований, полотенце/пелёнку."),
                s(0, "УЗИ", "")
            )),
        ScenarioTemplate(name = "УЗИ малого таза / мочевого пузыря", emoji = "🚰", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время УЗИ", disclaimer = MED, description = "Наполненный мочевой пузырь к началу исследования",
            steps = listOf(
                s(-1 * D, "Завтра УЗИ", "Уточните в клинике, нужен ли наполненный мочевой пузырь."),
                s(-90, "Выпейте 1–1,5 л воды", "Выпейте негазированную воду за 1–1,5 часа до исследования и не мочитесь до УЗИ (если так указала клиника).", p = Priority.HIGH),
                s(0, "УЗИ", "")
            )),
        ScenarioTemplate(name = "Колоноскопия", emoji = "🏥", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время исследования", disclaimer = MED, description = "Бесшлаковая диета 3 дня, жидкая диета накануне, приём препарата для очищения",
            steps = listOf(
                s(-3 * D, "Бесшлаковая диета (3 дня)", "Исключите: овощи, фрукты, ягоды, зелень, орехи, семечки, бобовые, цельнозерновой хлеб, крупы с оболочкой. Можно: отварное нежирное мясо, рыбу, яйца, белый рис, макароны, белый хлеб, нежирные молочные продукты.", p = Priority.HIGH),
                s(-1 * D, "Завтра колоноскопия — жидкая диета", "С сегодняшнего дня (по инструкции, обычно после лёгкого завтрака) — только прозрачные жидкости: вода, бульон без гущи, чай без молока, сок без мякоти."),
                s(-15 * H, "Первая часть препарата", "Примите первую часть препарата для очищения кишечника строго по инструкции врача.", p = Priority.CRITICAL),
                s(-5 * H, "Вторая часть препарата", "Приём второй части обычно заканчивают за 3–4 часа до исследования.", p = Priority.CRITICAL),
                s(-3 * H, "Больше не пить", "Обычно за 2–4 часа до исследования (особенно под седацией) пить нельзя — уточните у врача.", p = Priority.HIGH),
                s(-90, "Выезжайте", "Паспорт, полис, направление, результаты прошлых исследований. При седации нужен сопровождающий — не садитесь за руль."),
                s(0, "Колоноскопия", "")
            )),
        ScenarioTemplate(name = "Плановая операция", emoji = "🩺", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время операции", disclaimer = MED, description = "Анализы, отмена препаратов, голодание перед анестезией",
            steps = listOf(
                s(-7 * D, "Через неделю операция", "Сдайте анализы по списку клиники. Согласуйте с врачом отмену антикоагулянтов, аспирина и других препаратов."),
                s(-3 * D, "Соберите вещи в больницу", "Паспорт, полис, направление, выписки и анализы, список лекарств, удобная одежда, тапочки, средства гигиены, зарядка."),
                s(-1 * D, "Завтра операция", "Гигиенический душ, снимите лак и украшения. Лёгкий ужин.", p = Priority.HIGH),
                s(-8 * H, "Не есть", "Твёрдая пища обычно запрещена за 6–8 часов до анестезии.", p = Priority.CRITICAL),
                s(-3 * H, "Выезжайте в больницу", "Проверьте документы и вещи."),
                s(-2 * H, "Больше не пить", "Прозрачные жидкости обычно разрешены не позднее чем за 2 часа до анестезии — уточните у анестезиолога.", p = Priority.CRITICAL),
                s(0, "Операция", "Всё будет хорошо!")
            )),
        ScenarioTemplate(name = "Приём у врача", emoji = "👨‍⚕️", group = "Здоровье и анализы", builtIn = true,
            eventLabel = "Время приёма", description = "Документы, вопросы врачу, выход из дома",
            steps = listOf(
                s(-1 * D, "Завтра приём у врача", "Подготовьте паспорт, полис, результаты анализов, выписки и список вопросов к врачу."),
                s(-60, "Выходите к врачу", ""),
                s(0, "Приём у врача", "")
            )),

        // ---- Питание и голодание ----
        ScenarioTemplate(name = "Интервальное голодание 16:8", emoji = "🍽️", group = "Питание", builtIn = true,
            eventLabel = "Начало окна питания", description = "8 часов можно есть, 16 — нельзя. Повторяется каждый день",
            disclaimer = "При хронических заболеваниях, диабете, беременности посоветуйтесь с врачом перед голоданием.",
            steps = listOf(
                s(0, "Окно питания открыто", "Можно есть. Начните с полноценного приёма пищи.", RepeatType.DAILY),
                s(7 * H + 30, "Через 30 минут — конец окна питания", "Успейте поесть.", RepeatType.DAILY),
                s(8 * H, "Не есть до завтра", "Окно питания закрыто. Можно воду, чай и кофе без сахара.", RepeatType.DAILY, Priority.HIGH)
            )),
        ScenarioTemplate(name = "Интервальное голодание 18:6", emoji = "⏳", group = "Питание", builtIn = true,
            eventLabel = "Начало окна питания", description = "6 часов можно есть, 18 — нельзя",
            disclaimer = "При хронических заболеваниях, диабете, беременности посоветуйтесь с врачом перед голоданием.",
            steps = listOf(
                s(0, "Окно питания открыто", "Можно есть.", RepeatType.DAILY),
                s(5 * H + 30, "Через 30 минут — конец окна питания", "", RepeatType.DAILY),
                s(6 * H, "Не есть до завтра", "Окно питания закрыто. Только вода, чай и кофе без сахара.", RepeatType.DAILY, Priority.HIGH)
            )),
        ScenarioTemplate(name = "Питьевой режим", emoji = "💧", group = "Питание", builtIn = true,
            eventLabel = "Время подъёма", description = "Стакан воды каждые 2 часа в течение дня",
            steps = listOf(30L, 150L, 270L, 390L, 510L, 630L, 750L).mapIndexed { i, m ->
                s(m, "Выпейте стакан воды", "Стакан №${i + 1} из 7 (≈250 мл).", RepeatType.DAILY)
            }),

        // ---- Лекарства ----
        ScenarioTemplate(name = "Лекарство 2 раза в день", emoji = "💊", group = "Лекарства", builtIn = true,
            eventLabel = "Время утреннего приёма", disclaimer = MEDS, description = "Утром и вечером (каждые 12 ч) после еды",
            steps = listOf(
                s(0, "Утренний приём лекарства", "После еды, запить водой.", RepeatType.DAILY, Priority.HIGH),
                s(12 * H, "Вечерний приём лекарства", "После еды, запить водой.", RepeatType.DAILY, Priority.HIGH)
            )),
        ScenarioTemplate(name = "Лекарство каждые 8 часов", emoji = "💉", group = "Лекарства", builtIn = true,
            eventLabel = "Время первого приёма", disclaimer = MEDS, description = "Например, антибиотик — строго через равные промежутки",
            steps = listOf(
                s(0, "Приём лекарства (1 из 3)", "", RepeatType.DAILY, Priority.HIGH),
                s(8 * H, "Приём лекарства (2 из 3)", "", RepeatType.DAILY, Priority.HIGH),
                s(16 * H, "Приём лекарства (3 из 3)", "", RepeatType.DAILY, Priority.HIGH)
            )),
        ScenarioTemplate(name = "За 30 минут до еды, 3 раза в день", emoji = "🥄", group = "Лекарства", builtIn = true,
            eventLabel = "Время завтрака", disclaimer = MEDS, description = "Приём перед завтраком, обедом и ужином",
            steps = listOf(
                s(-30, "Лекарство — за 30 минут до завтрака", "", RepeatType.DAILY, Priority.HIGH),
                s(5 * H + 30, "Лекарство — за 30 минут до обеда", "", RepeatType.DAILY, Priority.HIGH),
                s(11 * H + 30, "Лекарство — за 30 минут до ужина", "", RepeatType.DAILY, Priority.HIGH)
            )),

        // ---- Поездки ----
        ScenarioTemplate(name = "Авиаперелёт", emoji = "✈️", group = "Поездки", builtIn = true,
            eventLabel = "Время вылета", description = "Документы, онлайн-регистрация, выезд в аэропорт, посадка",
            steps = listOf(
                s(-7 * D, "Проверьте документы", "Паспорт/загранпаспорт (срок действия), визы, страховка, брони."),
                s(-2 * D, "Соберите вещи", "Проверьте нормы багажа и ручной клади. Жидкости до 100 мл, пауэрбанк — только в ручную кладь."),
                s(-1 * D, "Онлайн-регистрация", "Обычно открывается за 24–30 часов до вылета. Сохраните посадочный.", p = Priority.HIGH),
                s(-5 * H, "Проверьте статус рейса", "Нет ли задержки или смены терминала."),
                s(-4 * H, "Выезжайте в аэропорт", "Рекомендуется быть в аэропорту за 3 ч до международного и за 2 ч до внутреннего рейса.", p = Priority.CRITICAL),
                s(-2 * H, "Регистрация и сдача багажа", "Стойки обычно закрываются за 40–60 минут до вылета."),
                s(-40, "Идите к выходу на посадку", "Посадка заканчивается за 15–20 минут до вылета.", p = Priority.HIGH),
                s(0, "Вылет", "Хорошего полёта!")
            )),
        ScenarioTemplate(name = "Поезд", emoji = "🚆", group = "Поездки", builtIn = true,
            eventLabel = "Время отправления", description = "Билеты, выезд на вокзал, посадка",
            steps = listOf(
                s(-1 * D, "Завтра поезд", "Проверьте билет, документы и соберите вещи."),
                s(-2 * H, "Выезжайте на вокзал", "", p = Priority.HIGH),
                s(-30, "Посадка", "Найдите свой вагон."),
                s(0, "Отправление", "")
            )),

        // ---- Работа и учёба ----
        ScenarioTemplate(name = "Встреча", emoji = "🤝", group = "Работа и учёба", builtIn = true,
            eventLabel = "Время встречи", description = "Подготовка накануне, выход, начало",
            steps = listOf(
                s(-1 * D, "Завтра встреча", "Подготовьте материалы и повестку."),
                s(-60, "Через час встреча", "Проверьте дорогу или ссылку на видеозвонок."),
                s(-15, "Выходите / подключайтесь", "", p = Priority.HIGH),
                s(0, "Встреча началась", "")
            )),
        ScenarioTemplate(name = "Экзамен", emoji = "🎓", group = "Работа и учёба", builtIn = true,
            eventLabel = "Начало экзамена", description = "План повторения, сборы накануне, сон, выход",
            steps = listOf(
                s(-7 * D, "Неделя до экзамена", "Составьте план повторения тем по дням."),
                s(-1 * D, "Завтра экзамен", "Паспорт, пропуск, ручки, калькулятор (если разрешён). Проверьте адрес и аудиторию."),
                s(-10 * H, "Пора спать", "Выспитесь — это важнее ночного повторения."),
                s(-2 * H, "Завтрак и выход", "Выходите с запасом времени.", p = Priority.HIGH),
                s(-20, "Будьте на месте", ""),
                s(0, "Экзамен. Удачи!", "")
            )),
        ScenarioTemplate(name = "Помодоро (4 цикла)", emoji = "🍅", group = "Работа и учёба", builtIn = true,
            eventLabel = "Начало работы", description = "25 минут работы / 5 минут отдыха, в конце длинный перерыв",
            steps = listOf(
                s(0, "Помидор 1: работаем 25 минут"), s(25, "Перерыв 5 минут"),
                s(30, "Помидор 2: работаем"), s(55, "Перерыв 5 минут"),
                s(60, "Помидор 3: работаем"), s(85, "Перерыв 5 минут"),
                s(90, "Помидор 4: работаем"), s(115, "Длинный перерыв 15–30 минут")
            )),
        ScenarioTemplate(name = "Разминка при сидячей работе", emoji = "🧘", group = "Работа и учёба", builtIn = true,
            eventLabel = "Начало рабочего дня", description = "Каждый час — встать, размяться, дать отдых глазам",
            steps = (1..8).map { s(it * H, "Разомнитесь", "Встаньте на 3–5 минут, пройдитесь, посмотрите вдаль (отдых для глаз).", RepeatType.DAILY) }),

        // ---- Финансы и дом ----
        ScenarioTemplate(name = "Оплата счетов", emoji = "💳", group = "Финансы и дом", builtIn = true,
            eventLabel = "Крайний срок оплаты", description = "Ежемесячно: за 3 дня, за день и в день оплаты",
            steps = listOf(
                s(-3 * D, "Через 3 дня — оплата", "ЖКХ, интернет, связь, кредит — проверьте, что оплатить.", RepeatType.MONTHLY),
                s(-1 * D, "Завтра крайний срок оплаты", "", RepeatType.MONTHLY, Priority.HIGH),
                s(0, "Сегодня последний день оплаты!", "", RepeatType.MONTHLY, Priority.CRITICAL)
            )),
        ScenarioTemplate(name = "Окончание документа / страховки", emoji = "📄", group = "Финансы и дом", builtIn = true,
            eventLabel = "Дата окончания", description = "ОСАГО, паспорт, виза, подписка — напоминания за месяц, неделю и день",
            steps = listOf(
                s(-30 * D, "Через месяц заканчивается срок действия", "Запланируйте продление."),
                s(-7 * D, "Через неделю заканчивается срок действия", "", p = Priority.HIGH),
                s(-1 * D, "Завтра заканчивается срок действия!", "", p = Priority.CRITICAL),
                s(0, "Срок действия истёк сегодня", "")
            )),
        ScenarioTemplate(name = "Обработка питомца от клещей", emoji = "🐾", group = "Финансы и дом", builtIn = true,
            eventLabel = "Дата обработки", description = "Ежемесячная обработка от блох и клещей",
            steps = listOf(
                s(-1 * D, "Завтра обработка питомца", "Проверьте, есть ли средство.", RepeatType.MONTHLY),
                s(0, "Обработайте питомца от блох и клещей", "", RepeatType.MONTHLY)
            )),
        ScenarioTemplate(name = "Замена контактных линз (2 недели)", emoji = "👁️", group = "Финансы и дом", builtIn = true,
            eventLabel = "Когда надели новые линзы", description = "Напоминание заменить линзы через 14 дней",
            steps = listOf(
                s(13 * D, "Завтра заменить линзы", "Проверьте запас."),
                s(14 * D, "Замените контактные линзы", "", p = Priority.HIGH)
            )),

        // ---- Личное ----
        ScenarioTemplate(name = "День рождения", emoji = "🎂", group = "Личное", builtIn = true,
            eventLabel = "Дата дня рождения", description = "Ежегодно: за 2 недели, 3 дня, накануне и в день",
            steps = listOf(
                s(-14 * D, "Через 2 недели день рождения", "Подумайте о подарке.", RepeatType.YEARLY),
                s(-3 * D, "Купите подарок", "", RepeatType.YEARLY),
                s(-1 * D, "Завтра день рождения", "", RepeatType.YEARLY, Priority.HIGH),
                s(0, "Сегодня день рождения! 🎉", "Не забудьте поздравить.", RepeatType.YEARLY, Priority.HIGH)
            )),
        ScenarioTemplate(name = "Режим сна", emoji = "🌙", group = "Личное", builtIn = true,
            eventLabel = "Время подъёма", description = "Напоминание отложить телефон и лечь вовремя (8 ч сна)",
            steps = listOf(
                s(-9 * H, "Через час — спать", "Отложите телефон, приглушите свет.", RepeatType.DAILY),
                s(-8 * H, "Пора спать", "Чтобы выспаться (8 ч) к подъёму.", RepeatType.DAILY, Priority.HIGH)
            ))
    )
}
