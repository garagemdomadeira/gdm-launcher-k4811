# SPEC · common (biblioteca)

Leia antes o [`AGENTS.md`](../AGENTS.md) da raiz.

## Responsabilidade

Tudo que mais de um app usa: dock e navegação entre apps, tema/fonte/idioma/densidade, configuração compartilhada (`launcher.json`), "uma fonte por vez", barra flutuante da NWD, clientes dos serviços da NWD (rádio, Bluetooth, SettingService), grade da tela inicial, strings dos 8 idiomas, drawables, estilos e fontes. Não tem Activity.

## Pacotes

| Pacote | Classes | Para quê |
|---|---|---|
| `com.k4811.common` | `Apps`, `SuiteApp` | packages dos apps (do `BuildConfig`) e actions da suíte; Application base (CrashLog, idioma, BottomBar) |
| `config` | `NwdConfigs`, `LauncherConfig`, `DockConfig` | pasta `/sdcard/nwd-configs`, gravação atômica de JSON; `launcher.json` (só o car-setting grava); `dock.json` (layout do dock, só o launcher grava) |
| `ui` | `Ui`, `Palette`, `ThemeInflater`, `ThemeDrawables`, `DockView`, `DockNavigator`, `Splash`, `IconView`, `ButtonIcon`, `CustomIcons`, `Touch` | cores, fontes, idioma, densidade 1280×720; temas OKLCH; dock; navegação; ícones vetoriais; ícone à esquerda de botão de texto (MP3, EQ); ícones personalizados de `K4811/icons/`; feedback de toque |
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
| `dock_toggles` | array de ids | `radio music bt_music phone eq` | **legado** — só lido para montar o `dock.json` padrão na 1ª vez; o dock agora fica no `dock.json` (ver seção Dock) |
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

## `dock.json` (só o launcher grava)

Layout do dock, separado do `launcher.json` porque é editado pelo launcher (arrastar app da gaveta), não pelo car-setting — um escritor por arquivo, sem corrida. Chave `slots`: array ordenado de tokens (id de `DockView.Item` ou package de app). Sem arquivo, `DockConfig` monta o padrão a partir do `dock_toggles` (legado): `home` + itens ligados + `apps`. Edição (só o launcher): `insert` (adiciona/move um app numa posição, respeitando `MAX_ITEMS`), `move` (reordena) e `remove`; `isFull` avisa quando chegou no limite. **Limite `MAX_ITEMS` = 7 no total** (Início + até 5 no meio + Apps); dá para ter menos. Início e Apps ficam sempre nas pontas.

## Dock (`DockView.Item`)

O dock é uma **lista ordenada de slots** (`DockConfig`, em `dock.json`), não mais uma seleção fixa. Cada slot é um *token*: um id de `DockView.Item` (`home`, `radio`, `apps`…) com o comportamento embutido, ou um **package** de app comum (atalho que só abre). `DockView.refresh` lê os tokens e monta os slots; `Item.byId`/`Item.byPackage` resolvem cada um (um app da suíte arrastado — ex.: `com.nwd.radio` — vira o item inteligente `RADIO`). `isAvailable`/instalação escondem o que não existe. Itens: `HOME`, `RADIO`, `MUSIC`, `BT_MUSIC`, `PHONE`, `EQ`, `AUX`, `CAMERA_360`, `VIDEO`, `ZLINK`, `APPS`.

Início e Apps ficam sempre nas pontas e não são substituíveis (`DockConfig.isFixed`), para não trancar o usuário fora da tela inicial nem da gaveta. O usuário monta o dock arrastando (ver `launcher/SPEC.md`): da gaveta para o dock **adiciona** (até `MAX_ITEMS`); segurar um ícone do dock e arrastar **reordena**, ou **remove** se soltar fora do dock. A edição (`DockView.setEditor`) só é ligada pelo launcher (home e gaveta), que é quem grava o `dock.json`; nos outros apps o dock é só exibição/navegação. Não há mais customização do dock nas Configurações.

`DockNavigator.navigate` (itens embutidos): na tela inicial abre na hora; dentro dos apps, RADIO/MUSIC/PHONE/BT_MUSIC/EQ passam por um `Splash` de 1 s; o app atual faz `finish()` ao trocar, menos o launcher. Atalho de app comum: `DockNavigator.openApp` só abre o app.

**O dock não mostra texto, só o ícone.** Ícone de cada slot: 1º um arquivo em `K4811/icons` (ver abaixo); senão, o **ícone real do app** (`@mipmap/ic_launcher`, colorido) quando há `pkg` instalado; senão, o vetor do `IconView` (Início, Apps, 360 e Z-Link não têm `pkg` fixo). O estado ativo é marcado por opacidade (ícone colorido) ou cor (vetor) e pelo sublinhado.

## Ícones personalizados (`K4811/icons/`)

`CustomIcons` deixa o motorista trocar os ícones dos apps largando imagens em `K4811/icons/`, nas **mesmas raízes** do `HomeImage` (armazenamento interno da NWD por `ro.nwd.internal.path`, depois o do Android). Sem arquivo, tudo usa o ícone padrão — nada a configurar.

- `icons/<package>.<ext>` — vale na **gaveta** e no **dock** (ex.: `com.nwd.radio.png`, `com.waze.png`).
- `icons/dock_<id>.<ext>` — só para aquele **item do dock**, inclusive os sem app fixo (`dock_home`, `dock_apps`, `dock_cam360`, `dock_zlink`); tem prioridade sobre `<package>`.
- Extensões: `.png`, `.webp`, `.jpg`, `.jpeg` (as mesmas do `HomeImage`). ids do dock: ver `DockView.Item`.

Ordem: dock → `dock_<id>` → `<package>` → ícone real do app (`CustomIcons.appIcon`) → vetor; gaveta → `<package>` → ícone do sistema. `HomeImage.load` decodifica com `inSampleSize`; os bitmaps não são cacheados (o dock só recria quando a seleção muda; a gaveta recicla os seus a cada recarga).

## Regras

- Não chame `LauncherConfig.set…` fora do car-setting.
- `Ui.applyDesignDensity(activity)` antes do `setContentView` em toda Activity (aplica idioma e tema também).
- Nada de package de app escrito no código: use `Apps.*`.
- Strings novas nos 8 idiomas (`res/values*/strings_*.xml`).
- `BuildConfig.PKG_*` vem da tabela `packages` do `build.gradle.kts` da raiz.

## Lacunas

- `AudioGains` grava os volumes por fonte no SharedPreferences do app que chama (car-setting) e só reaplica quando ele abre; o painel Áudio está escondido.
