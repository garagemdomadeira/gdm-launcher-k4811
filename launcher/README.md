# GDM K4811 App suite · Launcher

Launcher leve para a central NWD, com o visual padrão do GDM K4811 App suite, mas **sem as funções de rádio, MP3, Bluetooth, telefone e EQ**. Cada botão só abre o app daquela função.

> Especificação técnica (para quem vai mexer no código, inclusive agentes de IA): [`SPEC.md`](SPEC.md).

| | |
|---|---|
| Package | `com.garagemdomadeira.nwd.launcher` (instala ao lado do `com.android.launcher` original) |
| Versão | 2.0.7 (versionCode 20270007) |
| App original | `radio apk/com.android.launcher` |
| Base visual | tema, fonte, dock e ícones do [`common`](../common/README.md) |
| Dock e configurações | [`../common`](../common/README.md) |

## O que ele faz

- **Tela inicial em módulos:** uma grade invisível de 6 áreas (**A** a **F**: uma alta à esquerda, uma grande e uma larga no centro, três pequenas à direita). Cada módulo vai em qualquer área ou fica escondido: **Tocando agora**, **Relógio**, **Controle de volume**, **Ventoinha**, **Bluetooth**, **Imagem**, **Velocidade (GPS)** e **Dados do GPS**. Cada um se ajusta ao tamanho da área. A posição de cada módulo vem do `launcher.json` (`home_layout`) e é escolhida no car-setting, em **Customizar → Customizar tela inicial**.
- **Barra superior:** dia/noite (lua = farol aceso), voltímetro (CAN) e o que está tocando (Rádio, MP3 ou BT).
- **Imagem:** uma foto da pasta `K4811/` na memória interna (subpastas também, menos `K4811/radio`).
- **Imagem de fundo:** opcional, escolhida no car-setting (mesma pasta `K4811/` do módulo Imagem). Preenche a tela inteira atrás dos módulos (recortada para caber); sem escolha, fica a cor do tema. Dá para remover a qualquer momento.
- **GPS:** velocidade em km/h e latitude, longitude, altitude e direção. Funcionam sem internet e só ligam o GPS com a tela inicial aberta.
- **Apagar a tela após** 5, 10 ou 30 s sem tocar na tela inicial (ou nunca).
- **Centrais verticais (768×1024):** as colunas da grade viram linhas. A em cima; B e C no meio; D, E e F embaixo.
- Gaveta de apps (`AppsActivity`). Os apps do GDM K4811 App suite abrem a gaveta pela action `com.k4811.launcher.action.APPS`.
- O bloco da ventoinha abre o [`fan`](../fan/README.md) (`com.nwd.cpumonitor`).
- **A engrenagem abre o app de configurações** [`car-setting`](../car-setting/README.md) (`com.nwd.car.setting`). É lá que ficam o launcher padrão, o dock, o idioma e as opções da tela inicial. O launcher só lê `/sdcard/nwd-configs/launcher.json`.
- Quando o idioma, o tema ou a fonte mudam nas configurações, a tela inicial se recria sozinha ao voltar (`onResume`).

## Para onde cada botão leva

| Botão | Abre |
|---|---|
| Rádio | `com.nwd.radio` ([`radio`](../radio/README.md)) |
| MP3 | `com.nwd.android.music.ui` ([`music`](../music/README.md)) |
| Bluetooth | `com.nwd.bt.music` ([`bt-music`](../bt-music/README.md)) |
| Telefone | `com.nwd.android.phone` ([`phone`](../phone/README.md)) |
| EQ | `com.nwd.audioset` ([`eq`](../eq/README.md)) |
| Bloco da ventoinha | `com.nwd.cpumonitor` ([`fan`](../fan/README.md)) |
| AUX / 360 / Vídeo / Z-Link | apps da central (`ExternalApps`) |
| Bloco do Bluetooth | `com.nwd.android.phone` (pareamento) |
| Engrenagem | car-setting (`com.nwd.car.setting`) |

Os botões abrem os apps pelo package. Os apps de função da suíte usam o mesmo package dos originais da NWD, então o dock abre o que estiver instalado: o nosso ou o original.

Rádio, MP3, música BT, telefone, EQ e ventoinha são apps separados: o launcher não tem player nem tela dessas funções.

## "O que está tocando" e chamada

O módulo "Tocando agora" **só manda comandos**. Com o rádio tocando, ele mostra a imagem da estação (a mesma da tela do rádio). Durante uma chamada, o cartão da chamada ocupa a área dele. Quem toca é sempre o app da fonte, e tocar no cartão abre esse app.

| Fonte | De onde vem o estado | ⏮ ⏯ ⏭ | Tocar no cartão |
|---|---|---|---|
| Rádio | serviço de rádio da NWD (`RadioClient` do common, só observando) | memória anterior/próxima, liga/desliga | radio |
| MP3 | broadcast `com.k4811.action.MUSIC_STATE` do music (`MusicRemote`) | broadcast `com.k4811.action.MUSIC_COMMAND` | music |
| Bluetooth | serviço de BT da NWD (`BtClient`) e broadcasts `com.bt.*` | `playControl` do serviço de BT | bt-music |
| App de mídia (Spotify, Deezer, YT Music…) | sessão de mídia do Android (`MediaSession`) | tocar/pausar, próxima, anterior | o próprio app |
| Chamada | broadcasts `com.bt.ACTION_BT_*_CALL` | atender / desligar pelo serviço de BT | phone |

Os **controles de mídia de apps** (Spotify e afins) são opcionais: ligue em **Configurações › Principais › Controles de mídia de apps** e conceda o **Acesso a notificações** quando a central pedir. Com isso, o launcher mostra e controla o que qualquer app de áudio está tocando — inclusive pelas teclas do volante. Sem a permissão, nada muda.

A fonte atual vem de `Settings.System mcu_current_source`. Na tela inicial, o dock sempre destaca o Início; o que está tocando aparece na barra superior. Ligar o rádio pelo módulo segue a regra de uma fonte por vez (`PlayerHandoff.switchToRadio`). Também continuam valendo os broadcasts do player original da NWD (`com.android.music.metachanged`).

O sistema NWD guarda o launcher padrão em `persist.nwd.launcher.default`, que vem de fábrica com `com.android.launcher` (o original). Na abertura, se a propriedade não for este app, ele pergunta se deve virar o padrão (`HomeActivity.checkDefaultLauncher`); o botão "Usar o K4811" também fica no car-setting.

## Aviso de volume

Quando o volume muda (volante, botão giratório ou tela), aparece no alto da tela, por cima de qualquer app, uma pílula com o alto-falante (as ondas acompanham o nível, X no zero), a barra na cor do tema e o número. Some sozinha depois de ~2 s e não pega toques.

- `VolumeOverlayService` começa junto com o launcher (`LauncherApp`) e escuta o SettingService da NWD, o mesmo do módulo de volume da tela inicial. Os valores dos primeiros 2 s são o volume atual, não uma mudança, e não mostram nada.
- Janela `TYPE_SYSTEM_ALERT` com `SYSTEM_ALERT_WINDOW`, concedida na instalação (targetSdk < 23), como a janela de chamada do telefone.
- Liga/desliga em car-setting › Tela inicial › **Mostrar volume na tela** (`show_volume_overlay` no `launcher.json`, ligado por padrão).
- Prévia sem a central (modo desenvolvedor): `adb shell am startservice -n com.garagemdomadeira.nwd.launcher/com.k4811.launcher.overlay.VolumeOverlayService -a com.k4811.launcher.VOLUME_PREVIEW --ei volume 25 --ei max 40`
- Ainda não testado na central: falta ver se o volume vindo do volante chega por esse aviso e se a NWD mostra o aviso dela junto.

## Menu da borda

Deslizar o dedo de uma borda da tela para dentro, em qualquer app, abre uma pílula com **Voltar | Vol− Vol+ | Início**. A borda é escolhida nas configurações: **embaixo** (menu deitado) ou **esquerda/direita** (menu em pé). Some depois de ~4,5 s sem uso, ao tocar fora ou depois de Voltar/Início. Segurar Vol± repete (depois de 0,8 s, a cada 0,1 s), como na barra da NWD.

- `EdgeMenuService` começa junto com o launcher. Uma área de toque de 30 px de profundidade pega o gesto. Ela fica só no meio da borda: 380 px de largura embaixo, 300 px de altura nas laterais. Um traço discreto (o "puxador") mostra onde deslizar. **Se o puxador não aparece, o serviço não está rodando.** Toques nessa área, sem deslizar, não chegam ao app de baixo.
- A área é funda de propósito: muitas telas de central não registram o toque na última fileira de pixels, e o deslize começa uns 15–30 px para dentro.
- Embaixo, a barra da NWD (`com.nwd.statusbarbottom`, se não estiver escondida) fica por cima do nosso menu e pega o gesto em apps de fora. Nesse caso, use esquerda ou direita.
- Voltar e Vol± são teclas falsas da MCU (`common/system/McuKeys`): o broadcast `com.nwd.action.EMU_RECEIVER_MCU_PROTOCAL` com o quadro `F0 06 01 01 00 00 00 KK CS`, o mesmo caminho do volante e da barra da NWD. Sem a chave de plataforma, é o único jeito de fazer Voltar em outro app. Vol± por esse caminho também faz aparecer o aviso de volume.
- Início abre a tela inicial direto pelo Android (com a tecla da MCU como reserva).
- Escolha em car-setting › Principais › **Menu da borda**: Desligado, Embaixo, Esquerda ou Direita (`edge_menu_side` no `launcher.json`, 0 a 3; padrão Embaixo). O serviço confere a opção a cada 3 s, então vale na hora. O log `K4811EdgeMenu` diz em que borda e com que tamanho a área foi criada.
- No emulador só o Início funciona (não há MCU). Ainda não testado na central.

## Teclas de mídia do volante

As teclas de **volume e mudo** do volante o sistema da NWD trata sozinho, por isso funcionam em qualquer app. Já **avançar e recuar** o sistema só repassa como um broadcast (`com.nwd.action.ACTION_KEY_VALUE`), e cabe ao app da fonte ativa reagir — algo que a pele de fábrica fazia e a recompilação tinha perdido.

Agora o launcher é quem escuta (`media/MediaKeyReceiver` → `MediaKeyService`) e, qualquer que seja a tela aberta, manda o comando para o que está tocando:

- **Rádio:** próxima/anterior estação salva da banda, ciclando (pula memórias vazias).
- **MP3 e música Bluetooth:** próxima/anterior faixa.
- **Nada tocando:** não faz nada (não começa a tocar sozinho).

Mantém a conexão com os serviços de rádio/BT aquecida por 60 s para apertos seguidos responderem na hora. Ainda não testado na central.

## Diferenças do original

Este app **não substitui** o launcher original: tem package próprio e fica instalado ao lado dele. O original roda serviços do sistema que este app não tem. Enquanto o original continuar instalado, eles seguem disponíveis; se ele for removido, somem:

| Serviço do original | O que fazia |
|---|---|
| `com.launcher.FloatBar` (`com.nwd.volumeview.VolumeService`) | **janela de volume** ao girar o botão de volume |
| `NwdNaviBarService` | barra lateral deslizante (voltar, home, volume) |
| `SuspensionService` | botão flutuante (assistive touch) |
| `BootTipService`, `WeatherService`, `SkinChangFloatService`, papel de parede animado | dica no boot, clima, troca de tema |
| `LauncherProvider` (`com.android.launcher2.settings`) e receivers de atalho | atalhos da área de trabalho |

Também saem as telas extras que ele tinha na gaveta: tela apagada, caixa de ferramentas, aprender volante, reiniciar e protetor de tela. Detalhes em `radio apk/com.android.launcher/README.md`.

## Código

| | |
|---|---|
| Linguagem | **Java puro**, Views clássicas e layouts XML, **sem nenhuma biblioteca** |
| minSdk / targetSdk | **17 / 19**, os mesmos do original |
| Tamanho do APK | ~780 KB (release; a maior parte são as 6 fontes) |

```
launcher/src/main/
├── AndroidManifest.xml          HomeActivity (HOME, nosensor) e AppsActivity (action APPS); permissão de localização (GPS)
├── java/com/k4811/launcher/
│   ├── HomeActivity.java        tela inicial
│   ├── AppsActivity.java        gaveta
│   ├── LauncherApp.java         SuiteApp + Bluetooth e SettingService conectados
│   ├── home/                    HomeSession (fonte, MP3, BT, chamada), HomeModuleViews (módulos da grade),
│   │                            AppCatalog, VolumeClient, MuteClient
│   ├── overlay/                 VolumeOverlayService + VolumeOverlayView: aviso de volume por cima dos apps;
│   │                            EdgeMenuService: menu ao deslizar de uma borda
│   ├── media/                   MediaKeyReceiver + MediaKeyService: teclas de avançar/recuar do volante
│   └── (com/acloud/…/MusicBean) faixa do player da NWD
└── res/layout/                  activity_home, activity_apps, app_cell, dialog_confirm
```

## Build e instalação

```bash
./gradlew :launcher:assembleRelease
adb install -r launcher/build/outputs/apk/release/launcher-release.apk
```

Status: compila (debug e release). Ainda não testado na central.
