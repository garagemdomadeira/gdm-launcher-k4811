# SPEC · music

Leia antes o [`AGENTS.md`](../AGENTS.md) e o [`common/SPEC.md`](../common/SPEC.md).

| | |
|---|---|
| applicationId | `com.nwd.android.music.ui` (por cima do original) |
| Pacote Java | `com.k4811.android.music.ui` |
| Application | `MusicApp` (SuiteApp + `PlayerHandoff.init`) |
| Activity | `MusicActivity` (singleTask, `nosensor`) + alias `com.nwd.android.music.ui.home_horizontalActivity` |

## Responsabilidade

Player local de MP3 (memória interna, pendrive, cartão SD). Continua tocando com a tela fechada. É o único dono do estado do MP3: o launcher só recebe broadcasts e manda comandos.

## Componentes

| Componente | Papel | Recebe |
|---|---|---|
| `music/MusicPlaybackService` | MediaPlayer, lista, foco de áudio, `claimSource` (fonte 0 + `ACTION_APP_IN_OUT` app 2) | actions `…music.ui.PAUSE`, `…REMOTE` (extra `cmd`), `…RESUME` |
| `music/MusicClient` | liga a tela ao serviço (bind local) | — |
| `music/PauseReceiver` | pausa **síncrona** (`MusicPlaybackService.pauseRunning`) | `com.k4811.action.PAUSE_PLAYERS` (ignora o próprio `from`) |
| `music/RemoteReceiver` | comandos e pedido de estado do launcher | `com.k4811.action.MUSIC_COMMAND` (extra `only_if_active` = só se já estiver tocando), `MUSIC_QUERY` |
| `music/BackgroundPlayReceiver` | retomar ao ligar o carro | `com.nwd.ACTION_BACKGROUND_MEDIA_PLAY` com `extra_app_id` = 2 |
| `music/MusicLibrary` | músicas da pasta escolhida e subpastas, agrupadas por pasta | — |
| `music/MusicConfig` | últimas configurações em `K4811/mp3player/mp3player.config.json`: pasta (padrão `/sdcard/music`), última faixa e posição, aleatório, repetir | — |
| `ui/FolderPicker` | escolha de pasta | — |

## Contrato com o launcher

O serviço envia `com.k4811.action.MUSIC_STATE` a cada mudança (troca de faixa e play/pause na hora; posição no máximo 1×/s), com `has_track`, `title`, `artist`, `album`, `playing`, `position_ms`, `duration_ms`, `media_id`, `path`. Ver `common/music/MusicRemote`.

## Regras

- Antes de tocar: `PlayerHandoff.beforeMp3` (pausa a música BT).
- A pausa pedida por outro app precisa soltar o foco de áudio **antes** de a fonte sair do Android, senão o sistema da NWD mata este app.
- Estado do player em `MusicConfig` (mesmo esquema do `radio.config.json`: lê da memória, relê se o arquivo mudar, grava em segundo plano, não sobrescreve arquivo com erro). Na primeira vez importa os SharedPreferences antigos (`music_prefs`, `k4811_music`).
- A lista só rola até a faixa atual quando a música troca ou começa a tocar; só a pasta da faixa atual fica aberta (as outras recolhem ao tocar no nome).

## Lacunas

Botões do widget de música da NWD (`com.nwd.action.music.on_widget_button_click`) e abrir arquivo de áudio (`VIEW audio/*`).
