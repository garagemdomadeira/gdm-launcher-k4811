# SPEC · common (biblioteca)

Leia antes o [`AGENTS.md`](../AGENTS.md) da raiz.

## Responsabilidade

Tudo que mais de um app usa: dock e navegação entre apps, tema/fonte/idioma/densidade, configuração compartilhada (`launcher.json`), "uma fonte por vez", barra flutuante da NWD, clientes dos serviços da NWD (rádio, Bluetooth, SettingService), grade da tela inicial, strings dos 8 idiomas, drawables, estilos e fontes. Não tem Activity.

## Pacotes

| Pacote | Classes | Para quê |
|---|---|---|
| `com.k4811.common` | `Apps`, `SuiteApp` | packages dos apps (do `BuildConfig`) e actions da suíte; Application base (CrashLog, idioma, BottomBar) |
| `config` | `NwdConfigs`, `LauncherConfig` | pasta `/sdcard/nwd-configs`, gravação atômica de JSON; leitura/gravação do `launcher.json` |
| `ui` | `Ui`, `Palette`, `ThemeInflater`, `ThemeDrawables`, `DockView`, `DockNavigator`, `Splash`, `IconView`, `ButtonIcon`, `Touch` | cores, fontes, idioma, densidade 1280×720; temas OKLCH; dock; navegação; ícones vetoriais; ícone à esquerda de botão de texto (MP3, EQ); feedback de toque |
| `audio` | `PlayerHandoff`, `AudioGains` | uma fonte por vez entre apps; volume por fonte (painel escondido) |
| `home` | `HomeGrid`, `HomeImage`, `GpsClient` | áreas A–F e módulos da tela inicial; imagem da pasta `K4811/`; GPS |
| `radio` | `RadioClient`, `RadioState`, `PresetStore`, `StationArt`, `Band`, `BandRange`, `Preset` | cliente do serviço de rádio da NWD; favoritas em `K4811/radio/radio.config.json`; arte das estações |
| `music` | `MusicRemote`, `AlbumArt` | contrato MP3 ↔ launcher; capa da faixa |
| `eq` | `EqModel`, `SettingClient` | modelo do EQ e cliente do SettingService (usados por eq, car-setting, AudioGains) |
| `system` | `ExternalApps`, `SystemProps`, `NwdConstants`, `NwdSettings`, `CrashLog`, `BottomBar`, `DevMode`, `McuKeys` | apps da central (AUX, 360, vídeo, Z-Link); props; constantes da NWD; SettingService; registro de crash; barra flutuante; modo desenvolvedor; teclas falsas da MCU (Voltar, Início, Vol±) |
| `bt` | `BtClient` | serviço de Bluetooth da NWD |
| `com.bt`, `com.nwd.radio.service.data` | Parcelables | precisam ter o mesmo nome e formato do original (keep no R8) |

AIDL em `src/main/aidl`: `com.bt.*`, `com.nwd.radio.service.*`, `com.nwd.setting.service.*`. A **ordem dos métodos** precisa ser a do original.

## Manifest

Declara `READ_EXTERNAL_STORAGE` e o receiver `com.k4811.common.system.BottomBar$Query` (action `com.k4811.action.BOTTOM_BAR_QUERY`), que entra no manifest de todos os apps.

## `launcher.json` (só o car-setting grava)

| Chave | Tipo | Padrão | Uso |
|---|---|---|---|
| `version` | int | 2 | formato |
| `language` | string | `en` | `pt en es fr ru ja zh hi` (`LauncherConfig.LANGUAGES`) |
| `theme` | int | -1 | `Palette.DEFAULT` (-1) ou 0..`Palette.THEMES`-1 |
| `dark_mode` | bool | true | fundo escuro; `false` inverte luminosidade dos neutros (fundo claro) |
| `font` | string | `Exo2` | `Exo2 Montserrat Rajdhani Outfit Inter PublicSans` (arquivos `assets/fonts/<id>-400/500/600.ttf`) |
| `dock_toggles` | array de ids | `radio music bt_music phone eq` | até 5 (`MAX_DOCK_TOGGLES`); ids de `DockView.Item` |
| `zlink_name` | int | 0 | índice em `ZLINK_NAMES` (Z-Link, CarPlay, Auto) |
| `hide_bottom_bar` | bool | false | esconder a barra da NWD |
| `home_layout` | string | `now_playing:left,clock:center_top,volume:center_bottom,fan:right_top,bluetooth:right_middle` | `módulo:área` separados por vírgula; ids de `HomeGrid.Module/Slot` |
| `home_image` | string | — | caminho da imagem do módulo Imagem |
| `home_bg_image` | string | — | caminho da imagem de fundo da tela inicial (ausente = cor do tema) |
| `media_app_controls` | bool | `false` | mostrar/controlar no launcher a mídia de apps do Android (Spotify…); precisa do Acesso a notificações |
| `show_voltmeter`, `show_day_night` | bool | true | barra superior da tela inicial |
| `screen_off_after` | int (s) | 0 | 0, 5, 10 ou 30 |
| `ask_default_launcher` | bool | true | perguntar na abertura do launcher |

Áreas (`HomeGrid.Slot`): `left`(A), `center_top`(B), `center_bottom`(C), `right_top`(D), `right_middle`(E), `right_bottom`(F). Módulos: `now_playing`, `clock`, `volume`, `fan`, `bluetooth`, `image`, `speed`, `gps`.

Para criar uma configuração nova: chave + getter/setter em `LauncherConfig`, tela no car-setting, leitura no app que usa, linha nesta tabela.

## Dock (`DockView.Item`)

`HOME`, `RADIO`, `MUSIC`, `BT_MUSIC`, `PHONE`, `EQ`, `AUX`, `CAMERA_360`, `VIDEO`, `ZLINK`, `APPS`. Início e Apps são fixos. Cada item tem `pkg` (o app que abre); `isAvailable` esconde itens cujo app/hardware não existe. `DockNavigator.navigate`: na tela inicial abre na hora; dentro dos apps, RADIO/MUSIC/PHONE/BT_MUSIC/EQ passam por um `Splash` de 1 s. O app atual faz `finish()` ao trocar, menos o launcher.

## Regras

- Não chame `LauncherConfig.set…` fora do car-setting.
- `Ui.applyDesignDensity(activity)` antes do `setContentView` em toda Activity (aplica idioma e tema também).
- Nada de package de app escrito no código: use `Apps.*`.
- Strings novas nos 8 idiomas (`res/values*/strings_*.xml`).
- `BuildConfig.PKG_*` vem da tabela `packages` do `build.gradle.kts` da raiz.

## Lacunas

- `AudioGains` grava os volumes por fonte no SharedPreferences do app que chama (car-setting) e só reaplica quando ele abre; o painel Áudio está escondido.
