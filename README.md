# 🚗 Garagem Launcher

> A custom home screen for **NWD** Android car head units (1280×720), built with one goal: **an amazing in-car experience**. ✨

🌐 **Website:** [garagemdomadeira.com](https://garagemdomadeira.com) · 📺 **YouTube:** [Garagem do Madeira](https://www.youtube.com/@garagemdomadeira)

---

## 🇺🇸 English

**Garagem Launcher** is an open-source launcher that replaces the stock home screen of NWD head units. It puts everything you use while driving (radio, music, Bluetooth, phone, equalizer and car settings) into a clean, minimalist interface that is fast and easy to use at a glance.

The project is part of **Garagem do Madeira**, a space about cars, DIY and technology. You can follow the work on the website [garagemdomadeira.com](https://garagemdomadeira.com) and on the YouTube channel [@garagemdomadeira](https://www.youtube.com/@garagemdomadeira). 🔧

🎯 **The focus is the experience:** big touch targets, readable at a glance, smooth animations, nothing that distracts you from the road, and nothing that changes your audio unless you ask for it.

## 🇧🇷 Português

O **Garagem Launcher** é um launcher de código aberto que substitui a tela inicial original das centrais multimídia NWD. Ele reúne tudo o que você usa ao dirigir (rádio, música, Bluetooth, telefone, equalizador e ajustes do carro) numa interface limpa e minimalista, rápida e fácil de entender com uma olhada.

O projeto faz parte da **Garagem do Madeira**, um espaço sobre carros, faça-você-mesmo e tecnologia. Acompanhe o trabalho no site [garagemdomadeira.com](https://garagemdomadeira.com) e no canal do YouTube [@garagemdomadeira](https://www.youtube.com/@garagemdomadeira). 🔧

🎯 **O foco é a experiência:** botões grandes, leitura rápida, animações suaves, nada que tire a sua atenção da estrada e nada que mexa no seu áudio sem você pedir.

## 🇪🇸 Español

**Garagem Launcher** es un launcher de código abierto que reemplaza la pantalla de inicio original de las radios multimedia NWD. Reúne todo lo que usas al conducir (radio, música, Bluetooth, teléfono, ecualizador y ajustes del coche) en una interfaz limpia y minimalista, rápida y fácil de entender de un vistazo.

El proyecto forma parte de **Garagem do Madeira**, un espacio sobre coches, hazlo-tú-mismo y tecnología. Sigue el trabajo en el sitio [garagemdomadeira.com](https://garagemdomadeira.com) y en el canal de YouTube [@garagemdomadeira](https://www.youtube.com/@garagemdomadeira). 🔧

🎯 **El foco es la experiencia:** botones grandes, lectura rápida, animaciones suaves, nada que te distraiga de la carretera y nada que cambie tu audio sin que lo pidas.

---

## ✨ Features

| | Screen | What it does |
|---|---|---|
| 🏠 | **Home** | Clock and date, volume and mute, "now playing" (radio, USB/SD or Bluetooth), fan and Bluetooth status, and a customizable dock |
| 📻 | **Radio** | FM/AM with RDS (station name, RadioText, PTY, TA/AF), 12 presets per band, tuning dial and numeric keypad |
| 🎵 | **Music** | USB/SD music player with album art and track list |
| 🎧 | **Bluetooth music** | A2DP/AVRCP playback controls and track info from your phone |
| 📞 | **Phone** | Bluetooth dialer, contacts, call history and in-call screen |
| 🎚️ | **Equalizer** | 10-band EQ with presets, loudness and sound field (fader/balance) |
| 🌀 | **Fan** | Cooling fan preferences |
| 📱 | **Apps** | Paged grid of installed apps |
| ⚙️ | **Settings** | Three panels in a side column: **Main** (language, font, theme color, dock, utilities), **Assists** (reverse camera, navigation) and **System** (default launcher, beep and volume, display, "About the unit") |

🔇 Opening the home screen **never** turns the radio on or interrupts what's playing.

## 🌍 Languages

The app ships in 8 languages, selectable in **Settings → Language** (each shown with a reference flag), independent of the system language:

| | Language | Strings |
|---|---|---|
| 🇺🇸 | English (default) | `app/src/main/res/values/strings*.xml` |
| 🇧🇷 | Portuguese (Brazil) | `app/src/main/res/values-pt/strings*.xml` |
| 🇪🇸 | Spanish | `app/src/main/res/values-es/strings*.xml` |
| 🇫🇷 | French | `app/src/main/res/values-fr/strings*.xml` |
| 🇷🇺 | Russian | `app/src/main/res/values-ru/strings*.xml` |
| 🇯🇵 | Japanese | `app/src/main/res/values-ja/strings*.xml` |
| 🇨🇳 | Mandarin (Simplified Chinese) | `app/src/main/res/values-zh/strings*.xml` |
| 🇮🇳 | Hindi | `app/src/main/res/values-hi/strings*.xml` |

Strings are split by area (`strings_home.xml`, `strings_radio.xml`, `strings_settings.xml`…), each name prefixed with its area (`home_`, `radio_`, `settings_`…).

➕ **Adding a language** (e.g. German):

1. Copy `res/values-pt/` to `res/values-de/` and translate the texts.
2. In `home/LauncherPrefs.java`, add the tag to `LANGUAGES` (`"de"`) and the language's own name, with its flag, to `LANGUAGE_NAMES` (`"🇩🇪 Deutsch"`).
3. If you need a regional variant (like `pt` → `pt-BR` or `zh` → `zh-CN`), adjust `Ui.applyLanguage`.

Contributions with new translations are very welcome! 🙌

## 🎨 Theme colors

In **Settings → Appearance → Theme color** you pick one of 14 themes, shown as color swatches:

- **Columns:** grayscale, blue, violet, red, orange, yellow and green.
- **Top row:** the color as the accent on a black background. Grayscale on black is the default.
- **Bottom row:** the same accent with the background in a dark shade of the theme color.

Every theme color is derived from the chosen hue in OKLCH (perceptual lightness, chroma and hue), so all themes keep the same lightness per token: dark backgrounds and readable text in any color. Error, warning and success colors never change.

- Tokens and derivation: `ui/Palette.java`
- Themed versions of the XML drawables and color selectors: `ui/ThemeDrawables.java`
- Applying the theme to every inflated layout: `ui/ThemeInflater.java`

## 🔤 Fonts

**Exo 2** is the default. In **Settings → Appearance → Font** you can switch to **Montserrat**, **Rajdhani**, **Outfit**, **Inter** or **Public Sans**.

Each font ships as three static weights in `app/src/main/assets/fonts/` (`<Font>-400.ttf`, `-500.ttf`, `-600.ttf`), subset to Latin to keep the APK small. To add one, drop the three files and its OFL license there, then add its file prefix to `FONTS` and its display name to `FONT_NAMES` in `home/LauncherPrefs.java`.

## 📦 Build and install

Requires the Android SDK (its path goes in `local.properties`, `sdk.dir=...`).

```bash
./gradlew assembleRelease
```

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

```bash
adb shell cmd package set-home-activity com.garagemdomadeira.nwd.launcher/.HomeActivity
```

The release build is signed with the debug key so it can be installed directly on the head unit. 💾 **Without adb:** copy the APK to a USB drive, install it, press Home and choose **Garagem** → **Always**.

### ↩️ Going back to the stock launcher

```bash
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

```bash
adb shell cmd package set-home-activity <package>/<stock launcher activity>
```

As a last resort, uninstalling Garagem brings the stock launcher back:

```bash
adb uninstall com.garagemdomadeira.nwd.launcher
```

🆘 **Emergency exit:** long-press the empty area of the home screen to open Android Settings.

## 🛠️ Developer mode

Debug and factory features are hidden by default. They show up when the file `LAUNCHER_DEV_MODE.txt` exists at the root of the internal storage (`/sdcard/LAUNCHER_DEV_MODE.txt`). The file's content doesn't matter, and it's checked every time a screen opens.

```bash
adb shell touch /sdcard/LAUNCHER_DEV_MODE.txt
```

```bash
adb shell rm /sdcard/LAUNCHER_DEV_MODE.txt
```

| Screen | What developer mode shows |
|---|---|
| 📻 Radio | Diagnostics (long-press the frequency number) |
| 🎚️ Equalizer | **Diag.** tab and the MCU/DSP badge |
| ⚙️ Settings | "Last app error" panel, technical "About the unit" lines (NWD platform, SoC, DSP, services, cameras) and the password-protected **Advanced** entry (head unit menus, system props, factory audio, CarPlay…) |

The check lives in `system/DevMode.java`.

### 🧪 Demo mode

To preview the radio layout without a head unit (emulator at 1280×720, 160 dpi):

```bash
adb shell am start -n com.garagemdomadeira.nwd.launcher/.RadioActivity --ez demo true
```

## 🧩 Technical notes

| | |
|---|---|
| 📦 Package | `com.garagemdomadeira.nwd.launcher` |
| ☕ Language | Pure Java, classic Android Views, **no external libraries** |
| 🤖 minSdk / targetSdk | 17 / 19 |
| 🖥️ Resolution | Designed for 1280×720 |
| 🔤 Fonts | [Exo 2](https://github.com/googlefonts/Exo-2.0) (default), [Montserrat](https://github.com/JulietaUla/Montserrat), [Rajdhani](https://fonts.google.com/specimen/Rajdhani), [Outfit](https://github.com/Outfitio/Outfit-Fonts), [Inter](https://github.com/rsms/inter) and [Public Sans](https://github.com/uswds/public-sans), all under the SIL Open Font License (`app/src/main/assets/fonts/OFL-*.txt`) |

- No AndroidX, AppCompat or Compose: only Android Views and custom Views drawn on `Canvas`.
- Icons are drawn in code (`ui/IconView`), since vector drawables only exist from Android 5.
- Fixed 1280×720 scale (`Ui.applyDesignDensity`): 1 dp = 1 design pixel, whatever density the unit reports.
- `android.max_aspect = 2.4` in the manifest avoids black bars on Android 10.
- With `targetSdk 19`, Android 10 shows a one-time "built for an older version of Android" notice. Just tap OK.

## 🗂️ Project structure

```
app/src/main/
├── AndroidManifest.xml
├── aidl/                        # system service interfaces (radio, settings, Bluetooth)
├── assets/fonts/                # fonts (400/500/600, Latin subset) + OFL licenses
├── java/com/garagemdomadeira/nwd/launcher/
│   ├── *Activity.java           # one Activity per screen
│   ├── bt/                      # Bluetooth client (phone and music)
│   ├── eq/                      # equalizer and DSP
│   ├── home/                    # home state, volume, mute, app catalog, preferences
│   ├── music/                   # USB/SD library and playback
│   ├── radio/                   # radio client, bands, presets
│   ├── system/                  # system props, head unit settings, external apps, crash log, dev mode
│   └── ui/                      # Ui (colors/fonts/scale), custom Views, dialogs
└── res/
```

## ⚠️ Disclaimer

This is an independent, community project. It is **not affiliated with or endorsed by NWD** or any head unit manufacturer. Use it at your own risk: it talks to the head unit's system services, and factory options (developer mode) can change how your unit behaves.

---

<p align="center">Made with ❤️ and ☕ at <a href="https://garagemdomadeira.com">Garagem do Madeira</a> 🚗</p>
