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
