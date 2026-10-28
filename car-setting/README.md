# GDM K4811 App suite · Configurações

Configurações da central e do launcher, com o visual padrão do GDM K4811 App suite. Fica **ao lado** do app de ajustes original da NWD (**Config. Veículo**, `com.android.car.setting`), sem substituí-lo. Também recebe **as configurações do launcher**: a engrenagem do [`launcher`](../launcher/README.md) abre este app.

> Especificação técnica (para quem vai mexer no código, inclusive agentes de IA): [`SPEC.md`](SPEC.md).

| | |
|---|---|
| Package | `com.nwd.car.setting` |
| Por que não `com.android.car.setting` | o original usa `sharedUserId com.android.settings`, que exige a **chave de plataforma**. Por isso este app instala ao lado dele |
| Versão | 2.0.7 (versionCode 20270007, maior que o do original) |
| App original | `radio apk/com.android.car.setting` (análise de onde cada opção grava) |
| Linguagem | **Java puro**, Views clássicas e layouts XML, **sem nenhuma biblioteca** |
| minSdk / targetSdk | **17 / 19**, os mesmos do original |
| Tamanho do APK | ~850 KB (release; a maior parte são as 6 fontes) |

## Painéis

As opções ficam em painéis escolhidos numa coluna lateral. Telas horizontais e verticais (`nosensor`).

| Painel | O que tem | Onde grava |
|---|---|---|
| **Principais** | idioma (8), dia/noite e voltímetro da tela inicial, apagar a tela após | `launcher.json` |
| **Customizar** | cor do tema (14), fonte (6), **Customizar tela inicial** (mapa das 6 áreas; tocar no módulo e depois na área; "Restaurar padrão"; escolher a imagem do módulo Imagem e a **imagem de fundo** da tela inicial, ambas da pasta `K4811/`), **Customizar dock** (até 5 ícones, Z-Link e o nome dele) | `launcher.json` |
| **Assistências** | câmera de ré (som na ré, espelhar), app de navegação e abrir ao ligar | `SettingService` da NWD / `Settings.System navigation_*` |
| **Sistema** | launcher padrão ("Usar o K4811"), esconder a barra inferior da NWD (só em centrais que têm), bipe, volume ao ligar, lembrar volume, brilho, dia/noite pelo farol, protetor de tela, "Sobre a central" | `persist.nwd.launcher.default`, `launcher.json`, `SettingService` |
| **Áudio** (escondido até ser confirmado numa central) | volume por fonte | `AudioGains` |
| **Avançado** (só no modo desenvolvedor, com senha) | região do rádio, freio de mão, ligar com qualquer tecla, props de áudio/microfone, menus da NWD, câmera 360, props do sistema | props e `SettingService` |

Ao mudar idioma, tema ou fonte, o app se recria na hora. O launcher se recria ao voltar para ele, e os outros apps aplicam na próxima vez que abrem.

Das páginas do original, não estão aqui: lista branca, voz, luz de 3 cores, atualização, restaurar fábrica e informação do dispositivo (IMEI/ICCID). Elas continuam no Config. Veículo original.

**Volume por fonte:** os valores ficam no próprio app (`AudioGains`) e são reaplicados quando ele abre (`SettingApp`). Enquanto o painel Áudio estiver escondido, isso não tem efeito.

## Diferenças do original

- O Config. Veículo original continua instalado. O SDK de janelas da NWD ainda abre o original (`com.android.car.setting.home_horizontalActivity`) quando falta o app de navegação.
- O extra `ref0` (abrir direto numa página, como `setNaviPath`) não é tratado.
- Não tem o diálogo de escolha de launcher do original (`ZtxzActivity`).
- **Fora por enquanto:** lista branca, restaurar fábrica e atualização do sistema (precisam da chave de plataforma, como o original).

## Configurações do launcher

Este app é o **único dono** de `/sdcard/nwd-configs/launcher.json` (ver [`common`](../common/README.md)). Ao abrir, ele cria o arquivo com os padrões, se ainda não existir. O launcher e os apps só leem.

```
car-setting/src/main/
├── AndroidManifest.xml          SettingsActivity (singleTask, launcher), HomeSettings, DockSettings, Advanced, DevProps
├── java/com/k4811/car/setting/
│   ├── SettingsActivity.java    painéis (coluna lateral)
│   ├── HomeSettingsActivity.java  mapa da tela inicial, módulos e imagem
│   ├── DockSettingsActivity.java  ícones do dock e nome do Z-Link
│   ├── AdvancedActivity.java    opções de fábrica (senha)
│   ├── DevPropsActivity.java    props do sistema, com filtro e exportar
│   ├── SettingApp.java          crashes, idioma, SettingService e launcher.json
│   ├── home/AppCatalog.java     lista de apps (escolha do app de navegação)
│   ├── audio/AudioPanel.java    painel Áudio (volume por fonte)
│   ├── system/PlatformCompat.java
│   ├── ui/                      OptionList, PropRows, InputDialog, HomeMapView, ThemeSwatches
└── res/layout/                  activity_settings, activity_dev_props, dev_prop_row, dialog_input, option_button
```

## Build e instalação

```bash
./gradlew :car-setting:assembleRelease
adb install -r car-setting/build/outputs/apk/release/car-setting-release.apk
```

Status: compila (debug e release). Ainda não testado na central.
