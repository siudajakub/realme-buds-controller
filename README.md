# Realme Buds Controller

Prosta aplikacja Android do testowania sterowania Realme Buds Air 5 Pro przez kanał OPO/RFCOMM widoczny w Androidzie i Gadgetbridge.

## Co działa w tej wersji

- łączenie ze sparowanymi `realme Buds Air 5 Pro` przez Bluetooth Classic RFCOMM
- zapis do kanału OPO `0000079A-D102-11E1-9B23-00025B00A5A5`
- inicjalizacja firmware/konfiguracja/bateria w stylu Gadgetbridge
- przyciski trybu hałasu:
  - ANC
  - Przezroczystość
  - Normalny
- odczyty testowe:
  - bateria
  - info
  - EQ
- pole ręcznego pakietu HEX do dalszego testowania
- diagnostyka reklam BLE jako osobny tryb pomocniczy
- log ramek TX/RX w aplikacji

Firmware update celowo nie jest obsługiwany.

## Budowanie

```bash
cd /Users/j/vibe/dane/realme-buds-controller
./gradlew :app:assembleDebug
```

APK debug powstaje tutaj:

```text
/Users/j/vibe/dane/realme-buds-controller/app/build/outputs/apk/debug/app-debug.apk
```

## Instalacja przez ADB

```bash
cd /Users/j/vibe/dane/realme-buds-controller
/Users/j/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Użycie

1. Sparuj słuchawki z telefonem w ustawieniach Bluetooth.
2. Upewnij się, że słuchawki są połączone audio z telefonem.
3. Uruchom aplikację i nadaj uprawnienia Bluetooth.
4. Naciśnij `Połącz`.
5. Po statusie połączenia RFCOMM użyj przycisków `ANC`, `Przezroczystość` albo `Normalny`.

Jeśli słuchawki nie reagują, najważniejszy jest log ramek TX/RX w aplikacji oraz porównanie z logiem HCI z Realme Link.
