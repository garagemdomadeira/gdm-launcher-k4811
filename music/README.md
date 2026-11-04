# GDM K4811 App suite · Música

Substituto do player de música local (MP3 do USB, SD e memória interna) da central NWD, com o **mesmo package** do original (`com.nwd.android.music.ui`), com o visual padrão do GDM K4811 App suite.

> Especificação técnica (para quem vai mexer no código, inclusive agentes de IA): [`SPEC.md`](SPEC.md).

| | |
|---|---|
| Package | `com.nwd.android.music.ui` (o mesmo do original) |
| Versão | 2.0.7 (versionCode 20270007, maior que o do original) |
| App original | `radio apk/com.nwd.android.music.ui` |

**Dock e configurações:** usa o mesmo dock do launcher e segue as configurações feitas no [`car-setting`](../car-setting/README.md) (idioma, tema, fonte, ícones do dock). Ver [`../common`](../common/README.md).

## Código

| | |
|---|---|
| Linguagem | **Java puro**, Views clássicas e layouts XML, **sem nenhuma biblioteca** |
| minSdk / targetSdk | **17 / 19**, os mesmos do original |
| Tamanho do APK | ~740 KB (release; a maior parte são as 6 fontes) |

```
music/src/main/
├── AndroidManifest.xml          MusicActivity (singleTask, launcher, nosensor), MusicPlaybackService,
│                                PauseReceiver, RemoteReceiver
├── java/com/k4811/android/music/ui/
│   ├── MusicActivity.java       faixa, capa, progresso, lista por pasta, aleatório, repetir, Abrir pasta
│   ├── MusicApp.java            SuiteApp + Bluetooth conectado
│   ├── ui/FolderPicker.java     escolha da pasta (memória interna, pendrive ou cartão SD)
│   └── music/
│       ├── MusicPlaybackService.java  MediaPlayer, continua tocando com a tela fechada
│       ├── MusicClient.java           liga a tela ao serviço
│       ├── MusicLibrary.java          músicas da pasta escolhida e subpastas, agrupadas por pasta
│       ├── MusicConfig.java           últimas configurações (K4811/mp3player/mp3player.config.json)
│       ├── PauseReceiver.java         pausa quando outro app da suíte vira a fonte
│       ├── RemoteReceiver.java        comandos e pedido de estado do launcher
│       └── MusicState, MusicTrack
├── res/layout/                  activity_music, music_row, option_button
└── res/layout-port/             activity_music: música em cima, lista embaixo (centrais verticais)
```

## Lista de músicas

O player abre por padrão a pasta `music` do sdcard (`/sdcard/music`); toque em **Abrir pasta** para escolher outra (memória interna, pendrive ou cartão SD). A lista só rola até a faixa atual quando a música troca ou começa a tocar, então dá para navegar por ela enquanto toca. Só a pasta da música atual fica aberta; tocar no nome de uma pasta abre ou recolhe (isso não vai para o arquivo de configuração). A lista mostra só as músicas dessa pasta e das subpastas, agrupadas por pasta, com recuo para cada subpasta. Anterior e próxima seguem essa mesma ordem.

## Últimas configurações

A pasta, a última faixa e onde ela parou, o aleatório e o repetir ficam em `K4811/mp3player/mp3player.config.json` (na memória interna da NWD ou, como reserva, no sdcard do Android, como o `radio.config.json` do rádio). Ao abrir, o player volta para essa faixa e posição. O arquivo pode ser editado à mão; se ele não abrir como JSON, o player não o sobrescreve até ser corrigido.

```json
{
  "version": 1,
  "folder": "/storage/sdcard0/music",
  "last": { "path": "/storage/sdcard0/music/Rock/song.mp3", "position": 83000 },
  "shuffle": false,
  "repeat": "all"
}
```

`repeat` aceita `all`, `one` ou `off`; `position` é em milissegundos.

## Fonte de áudio

- Ao tocar, o serviço avisa a central que a música é a fonte (`ACTION_REQUEST_CHANGE_SOURCE` fonte 0 e `ACTION_APP_IN_OUT` app 2, como o original). Antes, chama `PlayerHandoff.beforeMp3`, que pausa a música BT.
- Quando outro app da suíte envia `com.k4811.action.PAUSE_PLAYERS` (o rádio ao ligar, a música BT ao tocar), o `PauseReceiver` pausa o player na hora e solta o foco de áudio. Isso precisa acontecer antes de a fonte sair do Android: se o player ainda segurar o foco, o sistema da NWD força a parada do app.
- Perder o foco de áudio também pausa.

## "O que está tocando" no launcher (`MusicRemote` do common)

- A cada mudança, o serviço envia o broadcast `com.k4811.action.MUSIC_STATE` com faixa, artista, álbum, play/pause, posição, duração, `media_id` e caminho. Troca de faixa e play/pause saem na hora; só a posição, no máximo 1×/s.
- O `RemoteReceiver` atende `com.k4811.action.MUSIC_QUERY` (reenvia o estado, se o player estiver rodando) e `com.k4811.action.MUSIC_COMMAND` (`cmd`: 0 tocar/pausar, 1 próxima, 2 anterior). O comando inicia o player se preciso e carrega a lista antes de tocar. Com o extra `only_if_active` (teclas do volante, pelo launcher), o comando só vale se o player já estiver tocando — se nada estiver tocando, é ignorado, para não começar a tocar MP3 por cima do rádio ou do BT.

## Diferenças do original

Activity com o nome do original (`activity-alias`), para o sistema e outros apps continuarem abrindo este app pelo mesmo componente.

**Retomar ao ligar o carro:** quando o carro liga com o MP3 como última fonte, o sistema da NWD manda `com.nwd.ACTION_BACKGROUND_MEDIA_PLAY` com `extra_app_id` = 2. O `BackgroundPlayReceiver` inicia o serviço com `ACTION_RESUME`, que carrega a lista da pasta escolhida e toca a última faixa, sem abrir a tela. O original abria a tela invisível para isso. Ainda não testado na central.

**O que o original tinha e este app ainda não tem:** `SLMusicWidegtButtonReveiver` (botões do widget de música da NWD) e abrir arquivo de áudio (`VIEW audio/*`).

## Build e instalação

```bash
./gradlew :music:assembleRelease
adb install -r music/build/outputs/apk/release/music-release.apk
```

Status: compila (debug e release). Ainda não testado na central.
