# XApps for HyperOS

**XGoogle, XSecurity and XNotify in one app and one root module, for the Xiaomi HyperOS China ROM.**

[Türkçe aşağıda](#türkçe)

## What is inside

| Part | What it does |
|---|---|
| **XGoogle** | Google Assistant / Gemini on the power key, Circle to Search, Hey Google, Lens, live translate, Wallet, Android Auto, GMS China restriction removed and more |
| **XSecurity** | Security app unlocks, Game Turbo extras, battery health, VoLTE / VoNR icons and system tweaks |
| **XNotify** | Phone notifications on the Xiaomi tablet, and Xiaomi's reply box for every app (the former XReply) |

Each part can be turned on or off on the XApps home screen. A part that is off is not loaded at all, so it costs nothing. Each part keeps its own settings screen.

## New in 1.0.5

- **Video sound with screen off:** a new XSecurity switch, off by default. Shows "Play video sound with screen off" in the video toolbox on Global ROMs and on devices where Xiaomi hid it, when the system supports it.
- **Extend Unlock (Smart Lock):** the lock screen now takes the trusted state straight from Android. If you use Smart Lock, please tell us whether a trusted device or place keeps the phone unlocked.
- **Clock seconds:** no wrong minute for a moment when the minute changes, and no refresh while the screen is off.
- Fixes for moving from the old apps, for removing the module and for the experimental Google map. See the [changelog](CHANGELOG.md).

## Earlier

- **1.0.4:** seconds on the status bar clock, Extend Unlock (Smart Lock), experimental Google map in Find Device. All off by default.
- **1.0.2:** Find Device fix (see below), Google Lens button in the camera, hide the home screen icon, remove the high volume warning, short notification vibration.

## Find Device (Xiaomi Find Device fix)

Find your device with its street address, usually in 5-10 seconds, from **[i.mi.com/find](https://i.mi.com/find)** or your other Xiaomi devices. Works worldwide, including where stock Find Device shows "Unknown location". The switch is at the very top of XSecurity and is on by default.

- **Real address on the server:** the device's report now carries its street address, so the website and your other devices show it even while the app is closed.
- **Right location:** correct on devices without GPS too, such as tablets.
- **Right place on the map:** device icons and the blue dot no longer drift.
- **Alarm works:** remote commands work in every system language.
- **Route:** opens Google Maps directly. Without Google Maps, it asks which map app to open.
- **Google map (experimental, separate switch, off by default):** the map inside Find Device shows Google's map, without Baidu's labels and logo. It uses an unofficial Google address; if Google can't be reached, Baidu's map comes back.

The map inside the app is still Xiaomi's Baidu map. A device needs internet to send a new location, and Find Device must be on with your Xiaomi account. The address comes from the phone's geocoding service and goes to Xiaomi with the location it already reports. A tablet without GPS uses Wi-Fi location, so 10-30 m of drift is normal.

## Requirements

- Xiaomi HyperOS China ROM, Android 15 or newer
- Root: KernelSU / SukiSU (Magisk and APatch supported, untested)
- LSPosed (API 102)
- On newer KernelSU builds a mount module such as Hybrid Mount, for XGoogle's system parts
- For XGoogle: the Google app from the Play Store

## Installation

1. Download **XApps-x.y.zip** from the [latest release](https://github.com/Leekin2/XApps/releases/latest).
2. Install it as a module in KernelSU / Magisk / APatch.
3. Reboot.

That is all. The module installs the XApps app, enables it in LSPosed with its whole scope, and moves your settings over from the separate XGoogle, XSecurity and XNotify modules, which it then uninstalls.

Only the APK installed? Enable XApps in LSPosed, tick the recommended apps, then install the root module from XApps → XGoogle.

The tablet needs XApps too for XNotify.

## Updating

Install the new zip over the old one (KernelSU shows updates itself), then reboot.

## Removing

Remove the **XApps** module in your root manager and reboot. The app goes with it, and the power key and navigation handle get their previous functions back.

## License

AGPL-3.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Not affiliated with Xiaomi or Google.

---

## Türkçe

**XGoogle, XSecurity ve XNotify tek uygulama ve tek root modülünde, Xiaomi HyperOS Çin ROM'u için.**

| Bölüm | Ne yapar |
|---|---|
| **XGoogle** | Güç tuşunda Google Asistan / Gemini, Daire Çizerek Arama, Hey Google, Lens, canlı çeviri, Wallet, Android Auto, GMS Çin kısıtlamasının kaldırılması ve dahası |
| **XSecurity** | Güvenlik uygulaması kilitleri, Oyun Turbo ekstraları, pil sağlığı, VoLTE / VoNR simgeleri ve sistem ayarları |
| **XNotify** | Telefon bildirimleri Xiaomi tablette ve her uygulamaya Xiaomi yanıt kutusu (eski XReply) |

Her bölüm XApps ana ekranından açılıp kapanır. Kapalı bölüm hiç yüklenmez. Her bölümün kendi ayar ekranı vardır.

### 1.0.5'te yeni

- **Ekran kapalıyken video sesi:** XSecurity'de yeni bir anahtar, varsayılan kapalı. Video araç kutusundaki "Ekran kapalıyken video sesi" kısayolunu Global ROM'da ve Xiaomi'nin gizlediği cihazlarda da gösterir; sistem desteklemiyorsa eklenmez.
- **Genişletilmiş Kilit Açma (Smart Lock):** kilit ekranı güvenilir durumu artık doğrudan Android'den alıyor. Smart Lock kullanıyorsanız, güvenilir cihaz veya yerde telefonun kilitsiz kalıp kalmadığını bize bildirin.
- **Saatte saniye:** dakika değişirken bir an yanlış dakika görünmez, ekran kapalıyken saat yenilenmez.
- Eski uygulamalardan geçiş, modülü kaldırma ve deneysel Google haritası için düzeltmeler. Ayrıntılar [değişiklik notunda](CHANGELOG.md).

### Önceki sürümler

- **1.0.4:** durum çubuğu saatinde saniye, Genişletilmiş Kilit Açma (Smart Lock), Cihazı Bul'da deneysel Google haritası. Hepsi varsayılan kapalı.
- **1.0.2:** Cihazı Bul düzeltmesi (aşağıda), kamerada Google Lens düğmesi, ana ekran simgesini gizleme, yüksek ses uyarısını kaldırma, kısa bildirim titreşimi.

### Cihazı Bul (Xiaomi Cihazı Bul düzeltmesi)

Cihazınızı **[i.mi.com/find](https://i.mi.com/find)** adresinden veya diğer Xiaomi cihazlarınızdan genelde 5-10 saniyede, açık adresiyle bulun. Dünyanın her yerinde çalışır, normal Cihazı Bul'un "Bilinmeyen konum" gösterdiği yerlerde de. Anahtar XSecurity'nin en üstünde ve varsayılan olarak açık.

- **Sunucuda gerçek adres:** cihazın raporu artık açık adresi de taşıyor. Uygulama kapalıyken bile site ve diğer cihazlarınız adresi gösterir.
- **Doğru konum:** tablet gibi GPS'i olmayan cihazlarda da konum doğru çıkar.
- **Haritada doğru yer:** cihaz simgeleri ve mavi nokta artık kaymaz.
- **Alarm çalışır:** uzaktan komutlar her sistem dilinde çalışır.
- **Rota:** doğrudan Google Haritalar'ı açar. Google Haritalar yoksa hangi harita uygulamasının açılacağını sorar.
- **Google haritası (deneysel, ayrı anahtar, varsayılan kapalı):** Cihazı Bul'un içindeki harita, Baidu'nun yazıları ve logosu olmadan Google haritasını gösterir. Resmî olmayan bir Google adresi kullanır; Google'a ulaşılamazsa Baidu haritası geri gelir.

Uygulamanın içindeki harita yine Xiaomi'nin Baidu haritasıdır. Yeni konum göndermek için cihazda internet gerekir, Cihazı Bul da Xiaomi hesabınızla açık olmalıdır. Adres telefonun adres hizmetinden alınır ve zaten bildirilen konumla birlikte Xiaomi'ye gider. GPS'i olmayan tablet Wi-Fi konumu kullanır, 10-30 metre sapma normaldir.

### Gereksinimler

- Xiaomi HyperOS Çin ROM'u, Android 15 veya üstü
- Root: KernelSU / SukiSU (Magisk ve APatch destekli, denenmedi)
- LSPosed (API 102)
- Yeni KernelSU sürümlerinde XGoogle'ın sistem parçaları için Hybrid Mount gibi bir mount modülü
- XGoogle için Play Store'dan Google uygulaması

### Kurulum

1. [Son sürümden](https://github.com/Leekin2/XApps/releases/latest) **XApps-x.y.zip** dosyasını indirin.
2. KernelSU / Magisk / APatch'te modül olarak kurun.
3. Yeniden başlatın.

Bu kadar. Modül XApps uygulamasını kurar, LSPosed'da kapsamıyla birlikte etkinleştirir, ayrı XGoogle, XSecurity ve XNotify modüllerinizin ayarlarını taşır ve onları kaldırır.

Sadece APK mı kurdunuz? XApps'i LSPosed'da etkinleştirip önerilen uygulamaları işaretleyin, sonra root modülünü XApps → XGoogle'dan kurun.

XNotify için tablete de XApps kurulmalıdır.

### Güncelleme

Yeni zip'i eskisinin üzerine kurun (KernelSU güncellemeyi kendisi gösterir) ve yeniden başlatın.

### Kaldırma

Root yöneticisinde **XApps** modülünü kaldırıp yeniden başlatın. Uygulama da kaldırılır; güç tuşu ve gezinme çubuğu eski işlevlerine döner.

### Lisans

AGPL-3.0, bkz. [LICENSE](LICENSE) ve [NOTICE](NOTICE). Xiaomi veya Google ile bağlantısı yoktur.
