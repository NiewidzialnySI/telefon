# Audyt Telefon — 2026-10-30 (noc)

Kod źródłowy był już na GitHub (`657a46d`). Lokalnie dopisano **backup v2** + ten raport.

## 1. Backup (Google Drive) — PRIORYTET

### Było (v1)
- Tylko `SharedPreferences` (skan XML w `shared_prefs`)
- `ed.apply()` przy przywracaniu (niesynchroniczne)
- Brak plików: klucz ADB, nagrania, transkrypcje

### Po poprawce (v2 + ochrona pustki) — w **v1.3** na GitHub
- Prefs jak wcześniej
- Klucz ADB (`filesDir/adb/adbkey` + `.crt`) → base64
- Nagrania WAV/M4A do ~40 MB łącznie (meta zawsze)
- Transkrypcje tekstowe
- `zawiera` / `pomija` w JSON
- Przywracanie: `commit()` + zapis plików
- **Nie uploaduje pustego backupu** na Drive
- **Restore** wybiera najlepszą niepustą z ostatnich 8 kopii; odmawia gdy lokalne dane są bogatsze
- Puste sekcje prefs nie czyszczą lokalnych danych

### Nadal celowo poza backupem
| Dane | Powód |
|------|--------|
| SMS / MMS / CallLog / Kontakty | Providery systemowe, nie sandbox aplikacji |
| Model Whisper (~1,5 GB) | Za duży; pobierany ponownie |
| Cache logo firm | Odtwarzalne z sieci |

### Ryzyka / TODO
- Brak szyfrowania JSON na Drive (dane wrażliwe: notatki, spam, opinie)
- Token OAuth w pamięci procesu (OK krótkoterminowo)
- Po przywróceniu motywu warto zrestartować aplikację (MotywStan czyta prefs przy starcie)
- Limit 40 MB nagrań — większe archiwum osobno (zip) = przyszła iteracja

---

## 2. Nagrywanie rozmów (ADB / shell / hybryda)

### Stan
- Własny daemon `ShellAdbMain` + Shizuku zapas
- Drabinka: hybryda MIC+DN → dual mic/up+dn → stereo → mono…
- Soft-switch przy jednostronnym audio; `vc_mono` nie jest preferowany
- Republish bindera co 2 s; `adb_wifi` tylko gdy daemon martwy

### Ryzyka
- Na Realme/ColorOS MIC w trakcie rozmowy (słuchawka) bywa wyciszony przez OEM — wtedy dalej tylko „oni”
- Keepalive AudioTrack może kolidować z MIC (wyłączany przy hybrydzie)
- Cache zdolności v4 — OK po resecie

### TODO
- Opcja „głośnik na czas nagrywania” / dual path MIC+speaker
- Logi `Lrms/Rrms` w UI diagnostyki ustawień

---

## 3. Połączenia / InCall / powiadomienia

### Stan
- `TelefonInCallService`, ekran rozmowy, dymki, notatki, spam swipe
- Chrome przychodzące, reputacja numeru (lokalne opinie)

### Ryzyka
- Zależność od bycia domyślnym dialerem
- Overlay / full-screen intent na OEM

---

## 4. SMS / MMS

### Stan
- Domyslna SMS, planowane, drafty, ukryte wątki, MMS download retry

### Backup
- Preferencje SMS (ukryte, drafty, planowane) **są** w prefs
- Treść wiadomości **nie** — system Telephony

---

## 5. Kontakty / spam / reputacja

### Stan
- Ulubione, pseudonimy, `BazaSpamu`, `OpinieLokalne` → prefs (w backupie)

### Ryzyka
- Kontakty systemowe poza backupem (oczekiwane)

---

## 6. Aktualizacje + uprawnienia onboarding

### Stan
- `AktualizacjaAplikacji` + unknown sources
- PermissionsScreen: telefon, kontakty, SMS, BT, alarmy, a11y, overlay…
- Po a11y: auto-powrót do aplikacji (`AdbParowanieDostepnosc.wrocDoAplikacji`)

### Nadal ręczne
- `WRITE_SECURE_SETTINGS` (ADB grant)
- Shizuku (opcjonalny zapas)

---

## 7. Motywy / ikony / UI

### Stan
- `MotywStan` / palety / Fallout assets / aliasy launchera
- Prefs `motyw_prefs` → w backupie

### Ryzyka
- Po restore bez restartu UI może trzymać stary motyw w pamięci

---

## Checklist na jutro (ręcznie)

1. Backup → Drive → wyczyść dane aplikacji → przywróć → sprawdź motyw, spam, ulubione, ADB pair, 1 nagranie
2. Rozmowa testowa: log `próba hybryda` / `dual OK mic+dn`, odsłuch obu stron
3. Onboarding a11y: po przełączniku auto-powrót
4. Wyłączenie laptopa po audycie — na życzenie użytkownika
