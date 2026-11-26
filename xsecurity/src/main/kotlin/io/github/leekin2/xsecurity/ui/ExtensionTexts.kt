package io.github.leekin2.xsecurity.ui

import io.github.leekin2.xsecurity.Features as F

/** Descriptions distinguish master settings from active sessions and granted permissions. */
/** Key of the "choose apps" row under the background clipboard switch. */
const val PICK_CLIPBOARD = "clipboard_pick_apps"
const val OPEN_EXTEND_UNLOCK = "open_extend_unlock"
const val OPEN_KITS = "open_kits"
const val PICK_CHARGE_LIMIT = "pick_charge_limit"
const val PICK_AOD_TIMEOUT = "pick_aod_timeout"
const val PICK_AOD_CUTOFF = "pick_aod_cutoff"
const val PICK_AOD_BURNIN = "pick_aod_burnin"
const val PICK_AOD_GLOW = "pick_aod_glow"
const val PICK_AOD_POSITION = "pick_aod_position"
const val PICK_AOD_PERF = "pick_aod_perf"
const val PICK_AOD_STATE = "pick_aod_state"
const val PICK_AOD_SONG = "pick_aod_song"
const val EXTEND_UNLOCK_FAILED = "extend_unlock_failed"

internal fun extensionLines(language: AppLanguage): Map<String, Line> = when (language) {
    AppLanguage.TR -> mapOf(
        OPEN_KITS to Line("Kitleri düzenle", "Kit ekleyin, kaldırın, sıralayın; AOD'de ve bildirim varken nasıl görüneceğini seçin"),
        F.SIM_MENUS to Line("SIM ayarları: VoLTE ve Wi-Fi araması menüleri", "SIM kartı ayarlarında Xiaomi’nin kendi “VoLTE kullan” anahtarını ve Wi-Fi araması ağ tercihini (Wi-Fi tercih edilir) gösterir; yeniden başlatınca kaybolmaz. LSPosed’de Telefon (com.android.phone) kapsamını seçip telefonu yeniden başlatın"),
        F.IMS_ICONS to Line("Global VoLTE / VoNR simgeleri", "Çin ROM’da durum çubuğunda global ROM’daki gibi orijinal Vo LTE / Vo NR simgesini gösterir. Simge yalnızca gerçek IMS kaydı varken görünür. LSPosed’de Sistem Arayüzü kapsamını seçip Sistem Arayüzünü yeniden başlatın"),
        F.CLIPBOARD_WHITELIST to Line("Arka planda pano okuma (beyaz liste)", "Yalnızca seçtiğiniz uygulamalar arka planda panoyu okuyabilir; diğerleri normal kuralda kalır. System Framework kapsamı ve yeniden başlatma gerekir"),
        PICK_CLIPBOARD to Line("Uygulamaları seç", "Panoyu arka planda okuyabilecek uygulamalar"),
        PICK_CHARGE_LIMIT to Line("Şarjı durdur", "Pil koruması ve akıllı şarj bu seviyede durur"),
        PICK_AOD_TIMEOUT to Line("Gizleme süresi", ""),
        PICK_AOD_CUTOFF to Line("Bu seviyenin altında", ""),
        PICK_AOD_BURNIN to Line("Kaydırma aralığı", ""),
        PICK_AOD_GLOW to Line("Işık rengi", ""),
        PICK_AOD_POSITION to Line("Alttan uzaklık", ""),
        PICK_AOD_PERF to Line("Performans", ""),
        PICK_AOD_STATE to Line("Ekran durumu", ""),
        PICK_AOD_SONG to Line("Stil", ""),
        F.APPLIST_UPLOAD_OFF to Line("Yüklü uygulama listesi yüklenmesini engelle", "Güvenlik bileşeni (Guard Provider) yüklü uygulama listenizi Xiaomi sunucusuna otomatik göndermez"),
        F.POWER_SAVE_REFRESH to Line("Pil tasarrufunda yenileme hızını koru", "Pil tasarrufunun 60 Hz sınırını kaldırır; seçtiğiniz hız ve termal sınırlar korunur. System Framework kapsamı ve yeniden başlatma gerekir; pil tüketimi artabilir"),
        F.POWER_SAVE_BRIGHTNESS to Line("Pil tasarrufunda parlaklığı koru", "Pil tasarrufunun parlaklık azaltmasını engeller. System Framework kapsamı ve yeniden başlatma gerekir; sıcaklık koruması değişmez"),
        F.GAME_KEEP_ENABLED to Line("Oyun Turbo anahtarını açık tut", "Oyun Turbo'nun ana aç/kapat ayarı açık kalır. Oyun oturumunu sürekli açık tutmaz ve oyun içi özel ayarları kilitlemez"),
        F.NET_APP_CONTROLS to Line("Uygulama ağ kontrollerini genişlet", "Güvenlik → Veri kullanımı ekranında uygun sistem uygulamalarına Wi-Fi ve arka plan ağ kontrolleri ekler. Kurallarınızı buradan siz seçersiniz"),
        F.ENHANCED_KEYBOARD to Line("Gelişmiş klavye araç çubuğu", "Xiaomi'nin klavye alt çubuğunu (pano, sık kullanılanlar, klavye değiştirici) Gboard dahil tüm klavyelere açar; alt boşluk hatasını düzeltir. LSPosed'da klavye uygulamanızı (ör. Gboard) işaretleyip klavyeyi yeniden başlatın"),
        F.PANEL_GLASS_BLUR to Line("Oyun ve video araç kutusu cam bulanıklığı", "Oyun Turbo ve Video araç kutusu panellerinde düz arka plan yerine buzlu cam bulanıklığı uygular"),
        F.DAILY_DATA_LIMIT to Line("Uygulama başına günlük mobil veri kotası", "Uygulama bilgisi ekranına günlük mobil veri sınırı (MB, bildir veya gece yarısına kadar engelle) ve gelişmiş günlük veri hesabı ekler"),
        F.SCREENSHOT_MAX_QUALITY to Line("Ekran görüntüsü maksimum kalite (%100)", "Ham ve düzenlenmiş ekran görüntülerini bulanık JPEG sıkıştırması olmadan %100 kalitede kaydeder. LSPosed'da Ekran Görüntüsü (com.miui.screenshot) kapsamını işaretleyin"),
        F.SCREENSHOT_NO_CROP_LIMIT to Line("Ekran görüntüsü kırpma sınırını kaldır", "Ekran görüntüsü kırpma kutusundaki minimum boyut sınırını kaldırır. LSPosed'da Ekran Görüntüsü (com.miui.screenshot) kapsamını işaretleyin"),
    )
    AppLanguage.EN -> mapOf(
        OPEN_KITS to Line("Edit kits", "Add, remove and order the kits; choose how they show on the AOD and with notifications"),
        F.SIM_MENUS to Line("SIM settings: VoLTE and Wi-Fi calling menus", "Shows Xiaomi’s own “Use VoLTE” switch and the Wi-Fi calling network preference on the SIM card settings page, and keeps them after a restart. Select Phone (com.android.phone) scope in LSPosed and reboot"),
        F.IMS_ICONS to Line("Global VoLTE / VoNR icons", "Shows the global ROM’s original Vo LTE / Vo NR status bar icon on a China ROM. It appears only while IMS is really registered. Select System UI scope in LSPosed and restart System UI"),
        F.CLIPBOARD_WHITELIST to Line("Background clipboard read (allow list)", "Only the apps you pick can read the clipboard in the background; all others keep the normal rule. Requires System Framework scope and reboot"),
        PICK_CLIPBOARD to Line("Choose apps", "Apps that may read the clipboard in the background"),
        PICK_CHARGE_LIMIT to Line("Stop charging at", "Battery protection and smart charging stop at this level"),
        PICK_AOD_TIMEOUT to Line("Hide after", ""),
        PICK_AOD_CUTOFF to Line("Below", ""),
        PICK_AOD_BURNIN to Line("Move every", ""),
        PICK_AOD_GLOW to Line("Glow colour", ""),
        PICK_AOD_POSITION to Line("Distance from the bottom", ""),
        PICK_AOD_PERF to Line("Performance", ""),
        PICK_AOD_STATE to Line("Screen state", ""),
        PICK_AOD_SONG to Line("Style", ""),
        F.APPLIST_UPLOAD_OFF to Line("Block installed app list upload", "The security component (Guard Provider) no longer sends your installed app list to Xiaomi automatically"),
        F.POWER_SAVE_REFRESH to Line("Keep refresh rate in Battery Saver", "Removes Battery Saver's 60 Hz cap; respects your selected rate and thermal limits. Requires System Framework scope and reboot; may use more battery"),
        F.POWER_SAVE_BRIGHTNESS to Line("Keep brightness in Battery Saver", "Prevents Battery Saver dimming. Requires System Framework scope and reboot; thermal protection is unchanged"),
        F.GAME_KEEP_ENABLED to Line("Keep Game Turbo enabled", "Keeps the master switch on. Does not force an active game session or lock individual game settings"),
        F.NET_APP_CONTROLS to Line("Extend per-app network controls", "Adds Wi-Fi and background network controls for eligible system apps in Security → Data usage. You choose the rules there"),
        F.ENHANCED_KEYBOARD to Line("Enhanced keyboard toolbar", "Unlocks Xiaomi's bottom keyboard bar (clipboard, phrases, keyboard switcher) for Gboard and other keyboards, with bottom-inset fix. Tick your keyboard app (e.g. Gboard) in LSPosed and restart the keyboard"),
        F.PANEL_GLASS_BLUR to Line("Game & video toolbox glass blur", "Applies frosted glass blur to Game Turbo and Video Toolbox panels instead of an opaque background"),
        F.DAILY_DATA_LIMIT to Line("Per-app daily mobile data limit", "Adds a daily mobile data limit (MB, notify or restrict until midnight) to App info plus enhanced daily data calculation"),
        F.SCREENSHOT_MAX_QUALITY to Line("Maximum screenshot quality (100%)", "Saves raw and edited screenshots at 100% quality without muddy JPEG compression. Tick Screenshot (com.miui.screenshot) in LSPosed"),
        F.SCREENSHOT_NO_CROP_LIMIT to Line("Remove screenshot crop limit", "Removes the minimum size limit when cropping screenshots. Tick Screenshot (com.miui.screenshot) in LSPosed"),
    )
    AppLanguage.RU -> mapOf(
        OPEN_KITS to Line("Изменить виджеты", "Добавляйте, удаляйте и упорядочивайте виджеты; настройте показ на AOD и при уведомлениях"),
        F.SIM_MENUS to Line("Настройки SIM: меню VoLTE и Wi-Fi звонков", "Показывает в настройках SIM собственный переключатель Xiaomi «Использовать VoLTE» и выбор сети для Wi-Fi звонков; сохраняются после перезагрузки. Выберите Телефон (com.android.phone) в LSPosed и перезагрузите"),
        F.IMS_ICONS to Line("Значки VoLTE / VoNR как в Global", "Показывает в строке состояния оригинальный значок Vo LTE / Vo NR глобальной прошивки на китайской. Значок виден только при реальной регистрации IMS. Выберите System UI в LSPosed и перезапустите System UI"),
        F.CLIPBOARD_WHITELIST to Line("Чтение буфера обмена в фоне (список)", "Только выбранные вами приложения могут читать буфер обмена в фоне; остальные работают по обычному правилу. Нужны System Framework и перезагрузка"),
        PICK_CLIPBOARD to Line("Выбрать приложения", "Приложения, которым разрешено читать буфер обмена в фоне"),
        PICK_CHARGE_LIMIT to Line("Остановить зарядку на", "Защита батареи и умная зарядка останавливаются на этом уровне"),
        PICK_AOD_TIMEOUT to Line("Скрыть через", ""),
        PICK_AOD_CUTOFF to Line("Ниже", ""),
        PICK_AOD_BURNIN to Line("Сдвиг каждые", ""),
        PICK_AOD_GLOW to Line("Цвет подсветки", ""),
        PICK_AOD_POSITION to Line("Отступ снизу", ""),
        PICK_AOD_PERF to Line("Производительность", ""),
        PICK_AOD_STATE to Line("Режим экрана", ""),
        PICK_AOD_SONG to Line("Стиль", ""),
        F.APPLIST_UPLOAD_OFF to Line("Не отправлять список установленных приложений", "Компонент безопасности (Guard Provider) больше не отправляет список ваших приложений на сервер Xiaomi"),
        F.POWER_SAVE_REFRESH to Line("Сохранять частоту при экономии заряда", "Убирает предел 60 Гц режима экономии, сохраняя выбранную частоту и тепловые ограничения. Нужны System Framework и перезагрузка; расход может вырасти"),
        F.POWER_SAVE_BRIGHTNESS to Line("Сохранять яркость при экономии заряда", "Убирает затемнение режима экономии. Нужны System Framework и перезагрузка; тепловая защита сохраняется"),
        F.GAME_KEEP_ENABLED to Line("Не отключать Game Turbo", "Главный переключатель остаётся включённым. Игровой сеанс не удерживается постоянно; настройки отдельных игр не блокируются"),
        F.NET_APP_CONTROLS to Line("Расширить сетевые настройки приложений", "Добавляет Wi-Fi и фоновую сеть для подходящих системных приложений в Безопасность → Трафик. Правила выбираете вы"),
        F.ENHANCED_KEYBOARD to Line("Расширенная панель клавиатуры", "Открывает нижнюю панель Xiaomi (буфер, фразы, переключение клавиатур) для Gboard и других клавиатур, исправляет отступ снизу. Выберите клавиатуру (напр. Gboard) в LSPosed и перезапустите её"),
        F.PANEL_GLASS_BLUR to Line("Стеклянное размытие панелей игр и видео", "Применяет матовое размытие стекла к панелям Game Turbo и видеоинструментов"),
        F.DAILY_DATA_LIMIT to Line("Дневной лимит мобильных данных на приложение", "Добавляет дневной лимит (МБ, уведомление или блокировка до полуночи) в сведения о приложении и улучшенный подсчёт трафика"),
        F.SCREENSHOT_MAX_QUALITY to Line("Максимальное качество скриншотов (100%)", "Сохраняет обычные и отредактированные скриншоты в качестве 100% без размытия JPEG. Отметьте Скриншот (com.miui.screenshot) в LSPosed"),
        F.SCREENSHOT_NO_CROP_LIMIT to Line("Снять лимит обрезки скриншотов", "Убирает минимальный размер области при обрезке скриншотов. Отметьте Скриншот (com.miui.screenshot) в LSPosed"),
    )
    AppLanguage.ES -> mapOf(
        OPEN_KITS to Line("Editar kits", "Añade, quita y ordena los kits; elige cómo se ven en AOD y con notificaciones"),
        F.SIM_MENUS to Line("Ajustes SIM: menús de VoLTE y llamadas Wi-Fi", "Muestra en los ajustes de la SIM el interruptor «Usar VoLTE» de Xiaomi y la preferencia de red de llamadas Wi-Fi, y se mantienen tras reiniciar. Selecciona Teléfono (com.android.phone) en LSPosed y reinicia"),
        F.IMS_ICONS to Line("Iconos VoLTE / VoNR globales", "Muestra en una ROM china el icono original Vo LTE / Vo NR de la ROM global. Solo aparece con un registro IMS real. Selecciona System UI en LSPosed y reinicia System UI"),
        F.CLIPBOARD_WHITELIST to Line("Leer portapapeles en segundo plano (lista)", "Solo las apps que elijas pueden leer el portapapeles en segundo plano; las demás siguen la regla normal. Requiere System Framework y reinicio"),
        PICK_CLIPBOARD to Line("Elegir apps", "Apps que pueden leer el portapapeles en segundo plano"),
        PICK_CHARGE_LIMIT to Line("Detener la carga en", "La protección de batería y la carga inteligente se detienen en este nivel"),
        PICK_AOD_TIMEOUT to Line("Ocultar tras", ""),
        PICK_AOD_CUTOFF to Line("Por debajo de", ""),
        PICK_AOD_BURNIN to Line("Mover cada", ""),
        PICK_AOD_GLOW to Line("Color del brillo", ""),
        PICK_AOD_POSITION to Line("Distancia desde abajo", ""),
        PICK_AOD_PERF to Line("Rendimiento", ""),
        PICK_AOD_STATE to Line("Estado de pantalla", ""),
        PICK_AOD_SONG to Line("Estilo", ""),
        F.APPLIST_UPLOAD_OFF to Line("Impedir la subida de la lista de apps", "El componente de seguridad (Guard Provider) ya no envía automáticamente tu lista de apps a Xiaomi"),
        F.POWER_SAVE_REFRESH to Line("Mantener frecuencia con ahorro de batería", "Quita el límite de 60 Hz del ahorro; respeta la frecuencia elegida y los límites térmicos. Requiere System Framework y reinicio; puede gastar más batería"),
        F.POWER_SAVE_BRIGHTNESS to Line("Mantener brillo con ahorro de batería", "Evita la reducción de brillo del ahorro. Requiere System Framework y reinicio; conserva la protección térmica"),
        F.GAME_KEEP_ENABLED to Line("Mantener Game Turbo activado", "Mantiene encendido el interruptor principal. No fuerza una sesión de juego activa ni bloquea ajustes de cada juego"),
        F.NET_APP_CONTROLS to Line("Ampliar controles de red por aplicación", "Añade Wi-Fi y red en segundo plano para apps del sistema compatibles en Seguridad → Uso de datos. Tú eliges las reglas"),
        F.ENHANCED_KEYBOARD to Line("Barra de teclado mejorada", "Desbloquea la barra inferior de teclado de Xiaomi (portapapeles, frases, selector) para Gboard y otros teclados y corrige el margen inferior. Marca tu teclado (p. ej. Gboard) en LSPosed y reinicia el teclado"),
        F.PANEL_GLASS_BLUR to Line("Desenfoque de cristal en cajas de juego y vídeo", "Aplica desenfoque de cristal esmerilado a los paneles de Game Turbo y vídeo"),
        F.DAILY_DATA_LIMIT to Line("Límite diario de datos móviles por app", "Añade límite diario (MB, notificar o bloquear hasta medianoche) en Info de la app y cálculo mejorado de datos"),
        F.SCREENSHOT_MAX_QUALITY to Line("Calidad máxima de capturas (100%)", "Guarda capturas originales y editadas al 100% sin compresión JPEG borrosa. Marca Captura de pantalla (com.miui.screenshot) en LSPosed"),
        F.SCREENSHOT_NO_CROP_LIMIT to Line("Quitar límite de recorte en capturas", "Elimina el tamaño mínimo al recortar capturas de pantalla. Marca Captura de pantalla (com.miui.screenshot) en LSPosed"),
    )
    AppLanguage.ZH -> mapOf(
        OPEN_KITS to Line("编辑小组件", "添加、移除和排序小组件；选择息屏和有通知时的显示方式"),
        F.SIM_MENUS to Line("SIM 卡设置：VoLTE 与 WLAN 通话菜单", "在 SIM 卡设置中显示小米原生的“使用 VoLTE”开关和 WLAN 通话网络偏好，重启后保留。需在 LSPosed 勾选电话（com.android.phone）并重启"),
        F.IMS_ICONS to Line("国际版 VoLTE / VoNR 图标", "在国行系统的状态栏显示国际版原生 Vo LTE / Vo NR 图标，仅在 IMS 实际注册时显示。需在 LSPosed 勾选系统界面并重启系统界面"),
        F.CLIPBOARD_WHITELIST to Line("后台读取剪贴板（白名单）", "仅你选择的应用可在后台读取剪贴板，其他应用保持默认规则。需勾选 System Framework 并重启"),
        PICK_CLIPBOARD to Line("选择应用", "允许在后台读取剪贴板的应用"),
        PICK_CHARGE_LIMIT to Line("停止充电于", "电池保护和智能充电在此电量停止"),
        PICK_AOD_TIMEOUT to Line("隐藏时间", ""),
        PICK_AOD_CUTOFF to Line("低于", ""),
        PICK_AOD_BURNIN to Line("移动间隔", ""),
        PICK_AOD_GLOW to Line("光效颜色", ""),
        PICK_AOD_POSITION to Line("距底部距离", ""),
        PICK_AOD_PERF to Line("性能", ""),
        PICK_AOD_STATE to Line("屏幕状态", ""),
        PICK_AOD_SONG to Line("样式", ""),
        F.APPLIST_UPLOAD_OFF to Line("阻止上传已安装应用列表", "安全组件（Guard Provider）不再自动把你的已安装应用列表发送给小米服务器"),
        F.POWER_SAVE_REFRESH to Line("省电模式保持刷新率", "移除省电模式的 60 Hz 限制，保留所选刷新率及温控限制。需勾选 System Framework 并重启；可能增加耗电"),
        F.POWER_SAVE_BRIGHTNESS to Line("省电模式保持亮度", "阻止省电模式调暗屏幕。需勾选 System Framework 并重启；不改变温控保护"),
        F.GAME_KEEP_ENABLED to Line("保持游戏加速总开关开启", "保持总开关开启，不强制持续游戏会话，不锁定各游戏的设置"),
        F.NET_APP_CONTROLS to Line("扩展应用联网控制", "在手机管家→流量使用中为符合条件的系统应用提供 Wi-Fi 和后台联网控制，由你选择规则"),
        F.ENHANCED_KEYBOARD to Line("增强键盘底部工具栏", "为 Gboard 及其他第三方输入法解锁小米全面屏键盘底部栏（剪贴板、常用语、切换输入法）并修复底部间距。需在 LSPosed 勾选输入法（如 Gboard）并重启输入法"),
        F.PANEL_GLASS_BLUR to Line("游戏与视频工具箱玻璃模糊", "为游戏加速与视频工具箱面板启用磨砂玻璃模糊背景"),
        F.DAILY_DATA_LIMIT to Line("应用每日移动流量限额", "在应用信息中添加每日移动流量上限（MB，提醒或限制至午夜）及增强日流量统计"),
        F.SCREENSHOT_MAX_QUALITY to Line("截屏最高画质（100%）", "以 100% 画质保存原始与编辑后的截屏，避免模糊 JPEG 压缩。请在 LSPosed 中勾选截屏（com.miui.screenshot）"),
        F.SCREENSHOT_NO_CROP_LIMIT to Line("移除截屏裁剪最小限制", "取消截屏裁剪时的最小尺寸限制。请在 LSPosed 中勾选截屏（com.miui.screenshot）"),
    )
}

internal fun aodRestart(language: AppLanguage): String = when (language) {
    AppLanguage.TR -> "Sistem Arayüzü ve AOD'yi yeniden başlat"
    AppLanguage.EN -> "Restart System UI and AOD"
    AppLanguage.RU -> "Перезапустить System UI и AOD"
    AppLanguage.ES -> "Reiniciar System UI y AOD"
    AppLanguage.ZH -> "重启系统界面和息屏显示"
}

internal fun aodRestartSummary(language: AppLanguage): String = when (language) {
    AppLanguage.TR -> "Sistem Arayüzü ve Her zaman açık ekranı yeniden başlatır; ekran kilitlenir (root gerekir)"
    AppLanguage.EN -> "Restarts System UI and Always-on display; the screen will lock (root required)"
    AppLanguage.RU -> "Перезапускает System UI и Always-on display; экран заблокируется (нужен root)"
    AppLanguage.ES -> "Reinicia System UI y Pantalla siempre activa; la pantalla se bloqueará (requiere root)"
    AppLanguage.ZH -> "重启系统界面和息屏显示；屏幕会锁定（需要 root）"
}
