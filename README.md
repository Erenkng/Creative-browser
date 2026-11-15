# Oavi

Android 17 ve sonrası için, tek elle kullanılmak üzere tasarlanmış bir web tarayıcı.
Galaxy A55'in Exynos 1480 çipini tanır ve ona göre ayarlanır; diğer tüm telefonlarda
standart profille çalışır.

- **Motor:** GeckoView 160 (Firefox'un motoru), `arm64-v8a`
- **Arayüz:** Kotlin + Jetpack Compose, kendi tasarım sistemi
- **En düşük sürüm:** Android 17 (API 37)
- **Diller:** Türkçe, İngilizce (telefon dilini izler; Ayarlar → Uygulamalar → Oavi → Dil
  bölümünden ayrıca seçilebilir)

## APK'yı indirme

Her push'ta GitHub Actions uygulamayı derler.

1. Depoda **Actions** sekmesini aç, en üstteki başarılı **Build** çalışmasına gir.
2. Sayfanın altındaki **Artifacts** bölümünden `oavi-apk` dosyasını indir.
3. Zip içinden `release/app-release.apk` dosyasını telefona kur.

`debug/app-debug.apk` aynı uygulamanın hata ayıklama sürümüdür: daha büyük ve daha yavaştır,
yalnızca sorun incelemek için kullanılır. Hız için her zaman **release** sürümünü kur.

## Düzenler

Ayarlar → Düzen'den beş düzenden biri seçilir; her kart düzenin küçük bir çizimini gösterir.
Hepsi aynı hareketleri tanır (çubukta yana kaydır: sekme değiştir) ve **Solak kullanım**
açıkken en çok kullanılan düğmeler sol başparmağın altına geçer.

| Düzen | Neye benzer | Oavi'nin farkı |
|---|---|---|
| **Ada** (varsayılan) | Oavi'nin kendi düzeni | Altta yüzen tek kapsül; okurken küçük bir hapa döner |
| **Klasik** | Chrome gibi adres çubuğu üstte | Koruma kalkanı adresin içinde, okuma görünümü ve yenile tek dokunuşta; çubuğu yana kaydırarak sekme değiştirme, aşağı çekerek tüm sekmeler; yeni sekmede büyük arama kutusu |
| **Sekme şeridi** | Vivaldi gibi üstte açık sekmelerin şeridi, altta araç çubuğu | Seçili sekme vurgu rengiyle işaretlenir; ortadaki arama düğmesi başparmağın ilk değdiği yerde; araç çubuğu okurken saklanır |
| **Alt çubuk** | Brave gibi adres ve düğmeler altta | Kalkan bu sayfada engellenenleri rozetle sayar; yeni sekmede bu hafta ve toplam engellenenler; okurken ince bir şeride iner |
| **Odak** | Zen gibi sayfa yumuşak bir zemin üzerinde yuvarlak bir kart | Altta küçük bir tutamak; adres ekranın ortasında yüzen bir komut çubuğunda açılır; sekmeler gruplarıyla soldan açılan dikey panelde, en çok gezilen siteler en üstte |

## Arayüz: Ada

Ada düzeninde üst çubuk yoktur. Tek kontrol, ekranın altında başparmağın durduğu yerde yüzen
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

Sayfaların kendi alt şeritleri (çerez bildirimleri, sohbet kutuları, "Kabul et" düğmeleri)
adanın arkasında kalmaz: motora adanın sayfanın altından ne kadarını kapladığı bildirilir, bu
şeritler adanın üstünde durur ve ada okuma hapına küçüldükçe aynı yay hareketiyle aşağı iner.

İkinci sekme açıldığında yeni sekme sayfasında, adanın hemen üstünde bir kez kısa bir ipucu
çıkar (yana kaydır: sekme değiştir, yukarı: tüm sekmeler, aşağı: küçült). İlk yana kaydırmada
ya da "Anladım" denince bir daha görünmez.

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
- **Diğer:** Geçmiş, yer imleri, indirme, dosya yükleme, sayfada bul, masaüstü sitesi,
  paylaşım, site izinleri (konum, kamera, mikrofon), varsayılan tarayıcı olma.

### 0.2.0 ile gelenler

- **Beş düzen ve solak kullanım** (yukarıda).
- **Vurgu rengi:** Turkuaz, mavi, mor, gül, kehribar.
- **Sayfa çevirisi** cihazın üzerinde (motorun çeviri modelleriyle); başka dildeki sayfada
  adanın üstünde sakin bir öneri çıkar.
- **Site simgeleri** yeni sekmede, geçmişte, yer imlerinde ve sekme kartlarında.
- **Sesle arama**, **panodan yapıştır ve git**, **arama kısayolları**: `!w istanbul`
  Wikipedia'da, `!yt` YouTube'da, `!harita` haritada, `!tr` çeviride arar (sona da yazılabilir:
  `istanbul !w`); "!" yazınca kısayollar listelenir.
- **Seçili metinde** Kopyala / Paylaş / Ara araç çubuğu (ve diğer uygulamaların metin eylemleri).
- **Sesli okuma:** Sayfa ya da makale telefonun kendi sesiyle okunur; duraklat, devam et.
- **PDF olarak kaydet**, **yazdır**, **QR kodu** (sayfayı başka cihazda açmak için).
- **Çerez uyarılarını reddet:** Yaygın onay araçlarında ve Türkçe/İngilizce/Almanca/Fransızca
  "Reddet" düğmelerinde otomatik reddeder; çerezle ilgisi olmayan düğmelere dokunmaz.
- **Bağlantıları temizle:** `utm_*`, `fbclid`, `gclid` gibi izleme ekleri açılan adreslerden
  ve kopyalanan/paylaşılan bağlantılardan silinir.
- **Güvenli DNS:** Cloudflare, Quad9 ya da AdGuard üzerinden DNS over HTTPS (ulaşılamazsa
  ağın kendi DNS'ine düşer).
- **Veri tasarrufu:** Sayfalar görselsiz yüklenir.
- **Gece karanlık sayfalar:** 20:00–07:00 arası sayfalar kendiliğinden koyulaşır.
- **Arşiv süresi:** Kullanılmayan sekmeler 1 gün, 1 hafta, 2 hafta sonra ya da hiç.
- **Yazı boyutu** (telefonu izle ya da %90–150), **her sitede yakınlaştırma**, **ana ekran
  kısayolları** (yeni sekme / gizli sekme), tam ekran videoda **resim içinde resim**.

### Android 17'ye özgü olanlar

- **Handoff:** Okunan sayfa, kullanıcının diğer cihazındaki tarayıcıda sürdürülebilir.
- **Yerel ağ izni** (`ACCESS_LOCAL_NETWORK`): Android 17'de ev ağındaki cihazlara (modem,
  yazıcı, NAS) bağlanmak ayrı izin ister; izin yoksa bağlantı uzun bir zaman aşımıyla düşer.
  Oavi, `192.168.x.x` ya da `yazici.local` gibi bir adrese gidilirken önce kısaca nedenini
  açıklar, sonra sistem iznini ister. İzin verilmezse beklemeden ne yapılacağını anlatan bir
  sayfa gösterir.
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
| Bellek içi önbellek | 64 MB (Android 17'nin uygulama bellek sınırı gözetilerek) | Motor varsayılanı |
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

## Testler ve CI

Her push'ta GitHub Actions şunları çalıştırır:

| Adım | Ne doğrular |
|---|---|
| Birim testleri (`core/model`) | Adres/arama ayrımı, sekme kuralları, arşiv, öneri sıralaması, çip algılama ve performans profilleri, indirme adları, site rengi, hata sayfası |
| Uzantı testleri (`tools/extension-test`) | Reklam engelleme kuralları, site bazında kapatma, okuma görünümü (içeriğin arındırılması dahil), karanlık sayfa — gerçek bir DOM üzerinde |
| Debug ve release derlemesi | Uygulamanın tamamı Android 17 SDK'sı, Compose ve GeckoView'e karşı derlenir; release R8 ile küçültülür |
| Lint | Android'in statik denetimleri |
| Emülatörde duman testi | **Release** sürümü Android 17 emülatörüne kurulur; ilk açılış, arama motoru seçimi, başka uygulamadan gelen bağlantıyla gerçek bir sayfa, menü, sekme ızgarası, adres alanı, koruma paneli, ayarlar, yerel ağ izni akışı ve gizli sekme tek tek denenir. Altta sabit şeridi olan bir test sayfasında şeridin adanın üstünde durduğu ve ada küçülünce aşağı indiği ekran görüntüsünden ölçülür. Ardından telefon karanlık temaya, Oavi Türkçeye alınır; ada, menü ve gizli sekme Türkçe denetlenir, sayfanın tema değişiminden sonra görünmeye devam ettiği ve adayı kaydırıp önceki sekmeye dönünce çizildiği ölçülür. Çökme olmadığı ve cihaz profilinin motora ulaştığı da kontrol edilir |

Duman testi her adımda ekran görüntüsü alır; Actions çalışmasının **Artifacts** bölümünde
`smoke-screenshots` olarak durur.

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
- Sayfanın en sonundaki içerik, ada tam boyutundayken adanın arkasında kalabilir; aşağı
  kaydırınca ada küçüldüğü için genellikle görünür hâle gelir.

## Lisanslar

- GeckoView — Mozilla Public License 2.0
- Readability (`core/engine/src/main/assets/extensions/oavi/Readability*.js`) — Apache License 2.0
