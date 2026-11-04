# AGENTS.md — guia para agentes de IA

Leia este arquivo inteiro antes de mexer no projeto. Depois leia o `SPEC.md` do módulo em que vai trabalhar. Os `README.md` são para pessoas; os `SPEC.md` são a especificação técnica de cada app.

## O que é

**GDM K4811 App suite**: um conjunto de apps Android que substituem os apps de fábrica de uma central multimídia automotiva **NWD** (tela 1280×720, Android 10, SoC chinês). Cada pasta da raiz é um app. Todos compartilham o módulo `common` (dock, tema, fonte, idioma, clientes dos serviços da NWD).

A engenharia reversa dos apps originais (APKs decompilados, protocolos) fica fora deste repositório. Os SPECs podem citar pastas no formato `radio apk/com.nwd.radio/…` como referência a esse material. Consulte-o antes de inventar um protocolo da NWD.

## Regras que não mudam

1. **Java puro, Views clássicas e layouts XML. Nenhuma biblioteca**, nem AndroidX, nem Kotlin, nem Compose. Só o SDK do Android e o `org.json` que vem nele.
2. **minSdk 17 / targetSdk 19** em todos os apps. É intencional: com target < 23 as permissões perigosas (armazenamento, `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, localização) são concedidas na instalação, e com target < 26/29 não há limite de serviço em segundo plano nem scoped storage. **Não suba o targetSdk.**
3. Java 8 (lambdas ok). compileSdk 36 só define as APIs conhecidas.
4. Código Java sempre em `com.k4811.*`. O `applicationId` de cada app vem da tabela `packages` no `build.gradle.kts` da raiz. **Nunca escreva um package de app no código**: use `com.k4811.common.Apps` (que lê o `BuildConfig` do common).
5. **Strings visíveis ficam no `common/src/main/res/values*/strings_*.xml`**, nos 8 idiomas (en padrão, pt, es, fr, ru, ja, zh, hi), com prefixo da área (`home_`, `radio_`, `media_`, `settings_`, `adv_`, `eq_`, `audio_`). Textos só da suíte vão em `strings_suite.xml`. Ao criar uma string, crie nos 8 idiomas.
6. O projeto não tem nenhuma menção a "madeira", "garagem" ou "gdm" no código, nos recursos ou na documentação. A **única** exceção é o package do launcher, `com.garagemdomadeira.nwd.launcher`, pedido pelo dono. Não reintroduza esses nomes.
7. Comentários: o código portado tem comentários em inglês, o código da suíte em português. Siga o idioma do arquivo que estiver editando. A documentação (READMEs, SPECs, mensagens de commit) é em português.
8. Não suba APK, chave ou senha. O `.gitignore` já cobre `*.apk`, `*.jks`, `keystore.properties`, `local.properties`.

## Mapa dos módulos

| Pasta | App | Package (applicationId) | Pacote Java | Na central |
|---|---|---|---|---|
| `common` | biblioteca | — | `com.k4811.common` | entra em todos |
| `launcher` | tela inicial e gaveta | `com.garagemdomadeira.nwd.launcher` | `com.k4811.launcher` | ao lado do `com.android.launcher` original |
| `car-setting` | configurações (engrenagem) | `com.nwd.car.setting` | `com.k4811.car.setting` | ao lado do `com.android.car.setting` original |
| `radio` | rádio FM/AM | `com.nwd.radio` | `com.k4811.radio` | por cima do original |
| `music` | MP3 (pasta local, USB, SD) | `com.nwd.android.music.ui` | `com.k4811.android.music.ui` | por cima do original |
| `bt-music` | música Bluetooth (A2DP/AVRCP) | `com.nwd.bt.music` | `com.k4811.bt.music` | por cima do original |
| `phone` | telefone Bluetooth (HFP) | `com.nwd.android.phone` | `com.k4811.android.phone` | por cima do original |
| `eq` | equalizador / DSP | `com.nwd.audioset` | `com.k4811.audioset` | por cima do original |
| `fan` | ventoinha | `com.nwd.cpumonitor` | `com.k4811.cpumonitor` | por cima do original |

"Por cima do original" só funciona se o APK for assinado com a **mesma chave** do original e tiver versionCode maior (20270000). Com outra chave, a instalação falha com `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. O car-setting não substitui o original porque ele usa `sharedUserId com.android.settings`, que exige a chave de plataforma.

## Build

```bash
./gradlew assembleRelease                 # os 8 apps
./gradlew :radio:assembleRelease          # um app
./gradlew :radio:compileReleaseJavaWithJavac   # só compilar (rápido, para checar erros)
./copy-apks.sh --build                    # gera e copia para /Volumes/madera/UpdateApk (pen drive)
```

- Saída: `<app>/build/outputs/apk/release/<app>-release.apk`.
- Assinatura: `keystore.properties` na raiz (fora do git; modelo em `keystore.properties.example`). Sem ele, o release sai com a chave de debug.
- `android.nonTransitiveRClass=false`: cada app enxerga os recursos do common pelo próprio `R`.
- R8 ligado no release, com `-dontobfuscate` e `-keep class com.nwd.** / com.bt.**` (AIDL e Parcelables dos serviços da NWD não podem ser renomeados). O launcher também mantém `com.acloud.newinterface.MusicBean`.

**Não há testes automatizados.** "Pronto" significa: `./gradlew assembleRelease` sem erro e, quando mexer em manifest ou package, conferir o APK:

```bash
BT=$(ls -d ~/Library/Android/sdk/build-tools/*/ | tail -1)
${BT}aapt dump badging radio/build/outputs/apk/release/radio-release.apk | head -3
${BT}aapt dump xmltree radio/build/outputs/apk/release/radio-release.apk AndroidManifest.xml
${BT}apksigner verify --print-certs radio/build/outputs/apk/release/radio-release.apk
```

Nada roda no emulador de forma útil: os serviços da NWD (rádio, BT, SettingService, MCU) só existem na central. O rádio tem um modo demo: `adb shell am start -n com.nwd.radio/com.k4811.radio.RadioActivity --ez demo true`. Diga sempre que o comportamento não foi testado na central.

## Configuração compartilhada: `/sdcard/nwd-configs/launcher.json`

Cada app tem seu package, então SharedPreferences não é compartilhável. As configurações que todos seguem ficam num JSON em disco:

- **Só o car-setting grava** (`LauncherConfig.set…`). O launcher e os apps só leem. Não chame `LauncherConfig.set…` fora do car-setting.
- Gravação atômica (`NwdConfigs.write`: `.tmp` + `renameTo`). Leitura com cache por `lastModified()`.
- Chaves (versão 2): `language`, `theme`, `dark_mode`, `font`, `dock_toggles`, `zlink_name`, `hide_bottom_bar`, `home_layout`, `home_image`, `home_bg_image`, `media_app_controls`, `show_voltmeter`, `show_day_night`, `screen_off_after`, `ask_default_launcher`. Detalhes em `common/SPEC.md`.
- Preferência que só um app usa fica no SharedPreferences dele (ex.: o EQ em `EqStore`). Rádio e MP3 guardam as delas em JSON editável em `K4811/<app>/` (`PresetStore`, `MusicConfig`).

## Contratos entre os apps (broadcasts da suíte)

| Action | Quem envia | Quem recebe | Extras |
|---|---|---|---|
| `com.k4811.action.PAUSE_PLAYERS` | `PlayerHandoff` (rádio, BT, launcher) | music `PauseReceiver` | `from` = package de quem pediu |
| `com.k4811.action.MUSIC_STATE` | music `MusicPlaybackService` | launcher `HomeSession` | `has_track`, `title`, `artist`, `album`, `playing`, `position_ms`, `duration_ms`, `media_id`, `path` |
| `com.k4811.action.MUSIC_QUERY` | launcher | music `RemoteReceiver` | — (responde com `MUSIC_STATE` se o player estiver rodando) |
| `com.k4811.action.MUSIC_COMMAND` | launcher | music `RemoteReceiver` | `cmd`: 0 tocar/pausar, 1 próxima, 2 anterior; `only_if_active` (bool) = só reage se já estiver tocando |
| `com.nwd.action.ACTION_KEY_VALUE` | kernel da NWD (painel/volante/controle) | launcher `MediaKeyReceiver` | `extra_key_value` (byte): 5 próxima, 6 anterior; `extra_key_type` 0 painel/1 controle/2 volante/3 toque |
| `com.k4811.launcher.action.APPS` | `DockNavigator` (item Apps) | launcher `AppsActivity` | — |
| `com.k4811.action.BOTTOM_BAR_QUERY` (ordenado) | `BottomBar` do app que parou | `BottomBar$Query` de todos | resultCode 1 = "tenho tela na frente" |

Com targetSdk 19, broadcast implícito ainda chega em receiver declarado no manifest, mesmo no Android 10. Se um dia o target subir, todos esses contratos quebram.

## Sistema da NWD (o que os apps usam)

- **Fonte de áudio atual:** `Settings.System mcu_current_source` (0 = Android, 4 = rádio, 7 = AUX, 8 = DVR) e o broadcast `com.nwd.action.ACTION_CHANGE_SOURCE`.
- **Pegar a fonte:** `com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE` (`extra_source_id`, byte) e `com.nwd.action.ACTION_APP_IN_OUT` (`extra_app_id`: 2 música, 8 rádio, 10 telefone, 15 música BT; `extra_app_operation` 1, `extra_app_event` 0). Constantes em `NwdConstants`.
- **Teclas do painel/volante:** o kernel entrega `com.nwd.action.ACTION_KEY_VALUE` (`extra_key_value` byte, `extra_key_type` 0 painel/1 controle/2 volante/3 toque). **Vol± e mudo o kernel executa sozinho** (por isso funcionam em qualquer app); **avançar (5)/recuar (6)/play (7) ele só repassa**, e cabe ao app da fonte ativa reagir. Só o launcher escuta (`MediaKeyReceiver` → `MediaKeyService`): despacha para a fonte que está tocando. Códigos em `NwdConstants.KEY_*`.
- **Serviços:** rádio (`com.nwd.radio.service.ACTION_RADIO_SERVICE`, AIDL `RadioFeature`), Bluetooth (`BtClient`, AIDL `com.bt.BTFeature`, broadcasts `com.bt.ACTION_*`), SettingService (`com.nwd.setting.service/.SettingService`, AIDL `SettingFeature`: volume, EQ pela MCU, brilho, bits do sistema). Os `.aidl` precisam manter a **ordem exata dos métodos** do original.
- **Props:** um app comum não faz `setprop persist.*`. Use `SystemProps.requestSet` (grava `Settings.System nwd_system_prop` e envia `com.nwd.action.ACTION_SET_SYSTEM_PROP`). Confira o resultado depois com `SystemProps.get`.
- **Barra flutuante da NWD:** `com.nwd.statusbarbottom.removeFloat` / `createFloat` com `pagename=home_horizontal`.

## Armadilhas conhecidas

- **Uma fonte por vez:** antes de uma fonte começar, as outras param (`PlayerHandoff.beforeRadio/beforeMp3/beforeBt/switchToRadio`). Se a fonte sai do Android enquanto o player do MP3 ainda segura o foco de áudio, **o sistema da NWD mata o app de música**. Por isso `switchToRadio` espera 0,6 s quando a fonte era Android e o `PauseReceiver` pausa de forma síncrona. Não remova essas esperas.
- **Densidade:** todo layout é desenhado em 1280×720 "dp". `Ui.applyDesignDensity(activity)` (antes do `setContentView`) faz 1 dp = 1 px desse desenho e também aplica idioma e tema. Toda Activity nova precisa chamar isso. Telas sem Activity (janela flutuante) calculam a própria escala (ver `phone/CallFloatService`).
- **Tema:** as cores vêm de `Ui.*` (não `final`) e o `ThemeInflater` troca cores literais do tema padrão nos layouts. Use `@color/...`, `@drawable/...` e `Ui.TEXT`, `Ui.SURFACE` etc., nunca uma cor nova solta, para o tema funcionar.
- **Idioma/tema/fonte mudam em outro app:** o launcher compara no `onResume` e chama `recreate()`. Os outros apps aplicam ao abrir (trocar de app pelo dock faz `finish()`).
- **Dock:** sempre por `DockNavigator.attachDock(activity, item)` logo depois do `setContentView`. Navegação entre apps por `DockNavigator.navigate`. No launcher abre na hora; dentro dos apps há um Splash de 1 s.
- **Barra da NWD entre processos:** o `BottomBar` de cada app só mostra a barra de volta depois de perguntar aos outros (ver contrato acima). Todo app precisa usar `com.k4811.common.SuiteApp` (ou uma subclasse) como `Application`.
- **Telas verticais (768×1024):** launcher, car-setting e MP3 têm `layout-port` e `screenOrientation="nosensor"`; o rádio tem um layout só, em coluna, que serve nas duas. Os outros são só horizontais (`sensorLandscape`).
- **Modo desenvolvedor:** diagnósticos, último erro e o menu Avançado só aparecem com `/sdcard/LAUNCHER_DEV_MODE.txt` (`DevMode.enabled()`).
- **Aliases:** os apps que substituem originais têm um `activity-alias` com o nome da Activity original (ex.: `com.nwd.radio.home_horizontalActivity`), porque o sistema e outros apps abrem por esse componente. Não remova.

## Como trabalhar

- Mudança que afeta mais de um app quase sempre vai no `common`. Verifique se o contrato entre apps (tabela acima) continua igual.
- Ao mudar um package ou um contrato, atualize `build.gradle.kts`, `Apps.java`, os manifests e os `SPEC.md`/`README.md` afetados.
- Antes de commitar, rode `./gradlew assembleRelease` e procure nomes proibidos: `grep -rIil "madeira\|garagem\|gdm" --exclude-dir=build --exclude-dir=.git .` (só pode aparecer `com.garagemdomadeira.nwd.launcher` no `build.gradle.kts` e na documentação dele).
- Commits em português, na `main`, só com os arquivos da tarefa. Pode haver trabalho em andamento de outra pessoa no repositório: confira `git status` e não inclua o que não é seu.

## Onde está cada coisa

| Assunto | Arquivo |
|---|---|
| Tabela de packages, assinatura | `build.gradle.kts` |
| Packages no código, actions da suíte | `common/.../Apps.java` |
| Configuração compartilhada | `common/.../config/LauncherConfig.java`, `NwdConfigs.java` |
| Dock e navegação | `common/.../ui/DockView.java`, `DockNavigator.java`, `Splash.java` |
| Tema, fonte, idioma, densidade | `common/.../ui/Ui.java`, `Palette.java`, `ThemeInflater.java`, `ThemeDrawables.java` |
| Uma fonte por vez | `common/.../audio/PlayerHandoff.java` |
| Rádio (cliente do serviço, favoritas) | `common/.../radio/RadioClient.java`, `PresetStore.java` |
| Bluetooth | `common/.../bt/BtClient.java` |
| SettingService (volume, EQ, brilho) | `common/.../system/NwdSettings.java`, `common/.../eq/SettingClient.java` |
| Tela inicial (grade de módulos) | `common/.../home/HomeGrid.java`, `launcher/.../home/HomeModuleViews.java` |
| Copiar APKs para o pen drive | `copy-apks.sh` |
