# Realme Buds Controller

Aplikacja Android do sterowania `realme Buds Air 5 Pro` przez kanał OPO/RFCOMM (Bluetooth Classic), widoczny w Androidzie i Gadgetbridge.

## Co działa w tej wersji

Aplikacja ma trzy zakładki:

### 1. Słuchawki (panel)

- połączenie/rozłączenie ze sparowanymi `realme Buds Air 5 Pro` przez Bluetooth Classic RFCOMM (kanał OPO `0000079A-D102-11E1-9B23-00025B00A5A5`)
- bateria L / P / etui
- tryby kontroli hałasu: ANC / Przezroczystość / Normalny
- informacje o urządzeniu: nazwa, firmware, wykrycie UUID OPO, transport

### 2. Ustawienia

- konfiguracja gestów (strona / typ / akcja)
- wybór trybów dla kafelka ANC w Szybkich ustawieniach

### 3. Lab

- ręczne wysyłanie surowych pakietów HEX
- log ramek TX/RX
- eksport logu

### Integracja z systemem

- kafelek "Tryb ANC" w Szybkich ustawieniach Androida przełącza tryby na sparowanych słuchawkach
- wymaga Androida 12+ (minSdk 31)

Firmware update celowo nie jest obsługiwany.

## Budowanie

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :app:assembleDebug
```

APK debug powstaje tutaj:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Instalacja przez ADB

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testy

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest
```

## Użycie

1. Sparuj słuchawki z telefonem w ustawieniach Bluetooth.
2. Upewnij się, że słuchawki są połączone audio z telefonem.
3. Uruchom aplikację i nadaj uprawnienia Bluetooth.
4. Naciśnij `Połącz`.
5. Po statusie połączenia RFCOMM użyj trybów ANC / Przezroczystość / Normalny.

Jeśli słuchawki nie reagują, najważniejszy jest log ramek TX/RX w zakładce Lab oraz porównanie z logiem HCI z Realme Link.

## Znane ograniczenia

- Ramka ustawienia ANC (`rfcommSetAnc`) wysyła payload zaczynający się od `0x03`, podczas gdy zwalidowana ramka OPO-v1 używa `0x01` — do potwierdzenia na sprzęcie.
