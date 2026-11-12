# Oavi

Android 17 ve sonrası için, tek elle kullanılmak üzere tasarlanmış bir web tarayıcı.
Galaxy A55'in Exynos 1480 çipini tanır ve ona göre ayarlanır; diğer tüm telefonlarda
standart profille çalışır.

- **Motor:** GeckoView 160 (Firefox'un motoru), `arm64-v8a`
- **Arayüz:** Kotlin + Jetpack Compose, kendi tasarım sistemi
- **En düşük sürüm:** Android 17 (API 37)
- **Diller:** Türkçe, İngilizce

## APK'yı indirme

Her push'ta GitHub Actions uygulamayı derler.

1. Depoda **Actions** sekmesini aç, en üstteki başarılı **Build** çalışmasına gir.
2. Sayfanın altındaki **Artifacts** bölümünden `oavi-apk` dosyasını indir.
3. Zip içinden `release/app-release.apk` dosyasını telefona kur.

`debug/app-debug.apk` aynı uygulamanın hata ayıklama sürümüdür: daha büyük ve daha yavaştır,
yalnızca sorun incelemek için kullanılır. Hız için her zaman **release** sürümünü kur.

## Arayüz: Ada

Oavi'de üst çubuk yoktur. Tek kontrol, ekranın altında başparmağın durduğu yerde yüzen
**ada**dır. Ada başka çubuklarla yer değiştirmez; kendisi şekil değiştirir.

| Hareket | Sonuç |
|---|---|
| Adaya dokun | Adres/arama alanına dönüşür; mevcut adres seçili gelir |
| Adada sola/sağa kaydır | Sonraki/önceki sekme |
| Adada yukarı kaydır | Sekme ızgarası |
| Adada aşağı kaydır | Ada küçük bir hapa küçülür (okurken yol açar) |
| Sayfayı aşağı kaydır | Ada kendiliğinden küçülür, yukarı kaydırınca geri gelir |
| Sekme düğmesine uzun bas | Yeni sekme |
| Sistem geri hareketi | Sayfada geri; paneller geri hareketini izleyerek küçülür |
| Sekme kartını yana kaydır | Sekmeyi kapat ("Geri al" ile döner) |
| Sekme kartına uzun bas | Grupla, arşivle, diğerlerini kapat |

### Tasarımın dayandığı ilkeler

| İlke | Oavi'de karşılığı |
|---|---|
| Fitts yasası, başparmak bölgesi | Tüm birincil eylemler ekranın altında; sorular ve paneller alttan gelir |
| Hick yasası | Menüde altı gündelik eylem görünür, seyrek olanlar "Daha fazla" altında |
| Kas hafızası | Ada hep aynı yerde; hareketler her ekranda aynı anlama gelir |
| Doherty eşiği | Her dokunuş anında yay hareketiyle karşılık verir; yükleme adanın içinde dolar |
| Zeigarnik etkisi | Yeni sekmede "Kaldığın yerden devam et" |
| Kayıp kaçınma | "Emin misin?" yerine "Geri al": sekme kapatma, geçmiş silme, yer imi kaldırma |
| Jakob yasası | Geri hareketi, uzun basma menüleri ve paylaşım alışıldığı gibi çalışır |
| Bilişsel yük | Okurken ada küçülür; site rengi arayüze yansır, bağlam kaybolmaz |

Hareketlerin tamamı yay fiziği kullanır: yarıda kesilebilir ve her zaman yerine oturur.
Renk ve saydamlık dışında süreli animasyon yoktur.

## Özellikler

- **Reklam ve izleyici engelleme:** Motorun izleme koruması (Sıkı) ve yerleşik bir reklam ağı
  listesi (157 ağ). Site bazında tek dokunuşla kapatılabilir; engellenen sayısı gösterilir.
- **Gizli sekmeler:** Diske hiçbir şey yazılmaz, ekran görüntüsü alınamaz, istenirse parmak
  izi ya da ekran kilidiyle kilitlenir.
- **Okuma görünümü:** Makaleyi sadeleştirir; yazı boyutu ve kâğıt rengi ayarlanır
  (Mozilla Readability).
- **Karanlık sayfalar:** Kendi karanlık teması olmayan siteleri karartır.
- **Sekme grupları ve arşiv:** Renkli gruplar; 14 gün dokunulmayan sekmeler arşive taşınır.
  Uygulama kapanıp açıldığında sekmeler ve sayfa geçmişleri geri gelir.
- **Diğer:** Geçmiş, yer imleri, indirme, sayfada bul, masaüstü sitesi, paylaşım, site
  izinleri (konum, kamera, mikrofon), varsayılan tarayıcı olma.

### Android 17'ye özgü olanlar

- **Handoff:** Okunan sayfa, kullanıcının diğer cihazındaki tarayıcıda sürdürülebilir.
- **Yerel ağ izni** (`ACCESS_LOCAL_NETWORK`): modem/yazıcı arayüzlerine erişim için bildirilir.
- **Gelişmiş Koruma Modu:** Telefonda açıksa Oavi en sıkı ayarlarına geçer (yalnızca HTTPS,
  sıkı izleme koruması).
- **Tetikleyici tabanlı profilleme:** Hata ayıklama sürümü soğuk açılış ve anomali izlerini
  toplar (`ProfilingManager`).
- Öngörülü geri hareketi, kenardan kenara çizim, tematik simge.

## Exynos 1480 profili

Uygulama açılışta çipi okur (`Build.SOC_MODEL` = `s5e8845`) ve bir performans profili seçer.
Bir uygulama çekirdeğe ya da GPU sürücüsüne müdahale edemez; yapabildiği şeyler şunlardır ve
Oavi bunların hepsini yapar:

| Ayar | Exynos 1480 (8 GB) | Standart |
|---|---|---|
| İçerik süreci sayısı | 4 (dört A78 çekirdeği için) | Motor varsayılanı |
| Bellekte canlı tutulan sekme | 8 | RAM'e göre 4–8 |
| Bellek içi önbellek | 128 MB | Motor varsayılanı |
| Anında geri/ileri için saklanan sayfa | 6 | Motor varsayılanı |
| Yedek içerik süreci (yeni sekme için sıcak) | Açık | Kapalı |
| Önceden açılan bağlantı | 10 | Motor varsayılanı |
| 120 Hz ekran modu | İstenir | İstenir |
| ADPF yük desteği | Açık | Kapalı |

**ADPF yük desteği:** Sayfa yüklenirken ve hareket sırasında Android'in performans ipucu
oturumu üzerinden zamanlayıcıya haber verilir; arayüz ve motor iş parçacıkları hızlı
çekirdeklere alınır, iş bitince bırakılır.

Diğer Exynos çipleri yük desteğini alır ama motor ayarları varsayılanda kalır. 5 GB'ın
altındaki cihazlar bellek tasarrufu profiline geçer. Tüm değerler tek dosyadadır:
[`PerformanceProfile.kt`](core/model/src/main/kotlin/com/oavi/browser/model/PerformanceProfile.kt).

Release sürümü ayrıca R8 ile küçültülür ve uygulama kodu için bir Baseline Profile taşır;
bu sayede kurulumdan sonraki ilk açılışlar da derlenmiş kodla başlar.

## Proje yapısı

```
app/                 Application, MainActivity, kök bileşen
core/model/          Saf Kotlin: adres/arama çözümleme, sekme kuralları, performans profilleri (+ testler)
core/device/         Çip algılama, 120 Hz, ADPF, Gelişmiş Koruma, profilleme
core/data/           SQLite (sekme, geçmiş, yer imi), DataStore (ayarlar), önizleme ve indirme
core/engine/         GeckoView: çalışma zamanı, sekme denetleyicisi, istemler, izinler
                     assets/extensions/oavi/  reklam engelleme, okuma görünümü, karanlık sayfa
core/ui/             Tasarım sistemi: renk, tipografi, hareket, ada camı, paneller, simgeler
feature/browser/     Sayfa + ada, adres alanı, menü, yeni sekme sayfası, sayfa istemleri
feature/tabs/        Sekme ızgarası, gruplar, arşiv
feature/privacy/     Koruma paneli, gizli sekme kilidi
feature/reader/      Okuma görünümü ayarları
feature/onboarding/  İlk açılış
feature/settings/    Ayarlar, geçmiş ve yer imleri
tools/               Metin ve simge üreteçleri, emülatör duman testi
```

Arayüz metinleri `tools/strings.py` içinde tek yerde durur; iki dil birlikte yazılır:

```
python3 tools/strings.py core/ui/src/main/res      # values/ ve values-tr/ dosyalarını üretir
python3 tools/icons.py core/ui/src/main/res/drawable
```

## Derleme

Android Studio ile ya da komut satırından (JDK 17+ ve Android SDK gerekir):

```
./gradlew :core:model:test      # birim testleri
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Derlemeler `keystore/oavi-sideload.keystore` ile imzalanır; böylece her yeni derleme
öncekinin üzerine kurulur. **Bu anahtar depoda açık durur ve yalnızca kişisel kurulum
içindir.** Uygulamayı başkalarına dağıtacaksan kendi anahtarını üret ve şu ortam
değişkenleriyle ver: `OAVI_KEYSTORE_FILE`, `OAVI_KEYSTORE_PASSWORD`, `OAVI_KEY_ALIAS`,
`OAVI_KEY_PASSWORD`.

## Galaxy A55'te denerken bakılacaklar

Hız ve akıcılık yalnızca gerçek cihazda ölçülebilir. Release sürümünü kurduktan sonra:

- [ ] Ayarlar → Performans: "Bu telefonun Exynos 1480 çipine göre ayarlı" yazıyor mu?
- [ ] Soğuk açılış: simgeye dokunuştan adanın görünmesine kadar geçen süre
- [ ] Uzun bir sayfada kaydırma 120 Hz'de akıcı mı? (Geliştirici seçenekleri → Yenileme hızını göster)
- [ ] Adada sola/sağa kaydırarak sekme değiştirme ve sekme ızgarasını açma akıcı mı?
- [ ] Ağır bir haber sitesinde reklamlar engelleniyor, koruma panelinde sayı artıyor mu?
- [ ] Gizli sekme kilidi parmak iziyle açılıyor mu?
- [ ] Uygulamayı kapatıp açınca sekmeler kaldığı yerden geliyor mu?

## Bilinen sınırlar

- İndirmeler uygulama açıkken sürer; uygulama kapatılırsa yarıda kalır.
- Sekmeler arası sürükle-bırak yok; gruplama uzun basma menüsünden yapılır.
- Site simgeleri (favicon) yerine sitenin baş harfi gösterilir.
- Parola kaydetme, eşitleme ve uzantı mağazası yok.
- Reklam listesi yerleşiktir; kendiliğinden güncellenmez.

## Lisanslar

- GeckoView — Mozilla Public License 2.0
- Readability (`core/engine/src/main/assets/extensions/oavi/Readability*.js`) — Apache License 2.0
