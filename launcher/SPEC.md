# SPEC · launcher

Leia antes o [`AGENTS.md`](../AGENTS.md) e o [`common/SPEC.md`](../common/SPEC.md).

| | |
|---|---|
| applicationId | `com.garagemdomadeira.nwd.launcher` (ao lado do `com.android.launcher` original) |
| Pacote Java | `com.k4811.launcher` |
| Application | `LauncherApp` (SuiteApp + `PlayerHandoff.init` + `NwdSettings.init`) |
| Orientação | Home `nosensor` (tem layout vertical via grade); Apps `sensorLandscape` |

## Responsabilidade

Tela inicial e gaveta de apps. **Não toca nada**: rádio, MP3, BT, telefone, EQ e ventoinha são apps separados. O launcher só mostra estado e manda comandos.

## Componentes

| Componente | Tipo | Intents |
|---|---|---|
| `HomeActivity` | Activity (singleTask, clearTaskOnLaunch) | `MAIN` + `HOME` + `DEFAULT` + `LAUNCHER` |
| `AppsActivity` | Activity (exportada) | `com.k4811.launcher.action.APPS` |
| `overlay/VolumeOverlayService` | Service (exportado, só a prévia em modo dev) | aviso de volume por cima dos apps (SettingService tipo 14); `com.k4811.launcher.VOLUME_PREVIEW` |
| `overlay/EdgeMenuService` | Service | menu Voltar \| Vol− Vol+ \| Início ao deslizar de uma borda (`McuKeys`) |
| `media/MediaKeyReceiver` | Receiver (exportado) | teclas do painel/volante `com.nwd.action.ACTION_KEY_VALUE` |
| `media/MediaKeyService` | Service | despacha avançar/recuar para a fonte ativa |

## Tela inicial

- Grade de 6 áreas (`HomeGrid`), com o módulo de cada área vindo de `LauncherConfig.homeLayout`. `HomeModuleViews` posiciona e ajusta cada módulo ao tamanho da área.
- Módulos: Tocando agora, Relógio, Volume (`VolumeClient`/`MuteClient`), Ventoinha (lê `mcu_smart_fan_switch`), Bluetooth (`BtClient`), Imagem (`HomeImage`, sem moldura), Velocidade e Dados do GPS (`GpsClient`, só com a tela aberta).
- Barra superior: dia/noite (`NwdSettings.headlightsOn`), voltímetro (`com.nwd.action.ACTION_QUERY_CAN_BATTERY_VOLTAGE` → `ACTION_CAN_BATTERY_VOLTAGE`, extra `extra_voltage_value` = volts × 10), indicador da fonte.
- Apagar a tela: `com.nwd.action.ACTION_SET_BACKLIGHT_SWITCH` (`extra_backlight_switch`); `screen_off_after` liga isso por inatividade.
- Recria a tela no `onResume` se idioma, tema ou fonte mudaram (`lookKey`).
- Se o `launcher.json` não existia ao abrir (na partida o sdcard monta depois do launcher), espera o arquivo aparecer (`ACTION_MEDIA_MOUNTED` e conferência a cada 2 s por 2 min) e recria a tela; a pergunta de launcher padrão espera isso.
- Pergunta para virar o launcher padrão (`persist.nwd.launcher.default`) se `ask_default_launcher`.
- Segurar a área vazia abre as Configurações do Android (saída de emergência).

## "Tocando agora" (`home/HomeSession`)

| Fonte | Estado | Comandos |
|---|---|---|
| Rádio | `RadioClient` (só observando) | memória ±, liga/desliga via `PlayerHandoff.switchToRadio` |
| MP3 | `com.k4811.action.MUSIC_STATE` (e `com.android.music.metachanged/playstatechanged` do player da NWD, com `MusicBean`) | `MusicRemote.send` (`MUSIC_COMMAND`) |
| BT | `BtClient.query` + `com.bt.ACTION_BT_MUSIC_PLAY/PAUSE`, `ACTION_AVRCP_MUSIC_ID3` | `BtClient.playControl` |
| Chamada | `com.bt.ACTION_BT_*_CALL/NUMBER/NAME` | `BtClient.answer/hangup` |

A fonte vem de `mcu_current_source`. Tocar no cartão abre o app da fonte (`DockNavigator.navigate`, sem Splash).

## Botões

Dock → apps (ver common). Engrenagem → `Apps.SETTINGS`. Bloco da ventoinha → `Apps.FAN`. Bloco do Bluetooth e cartão de chamada → `Apps.PHONE`.

## Gaveta (`AppsActivity` + `home/AppCatalog`)

Todos os apps numa grade de 5 colunas que rola para baixo até o último (sem páginas), com entrada em cascata, ícone que afunda ao toque e o app abrindo com zoom a partir do ícone. Esconde teclados, o próprio launcher e apps cujo hardware não existe (bits de `Settings.System factory_feature`).

## Por cima dos apps (`overlay/`)

Começam no `LauncherApp`. Janelas `TYPE_SYSTEM_ALERT` (`SYSTEM_ALERT_WINDOW`, concedida na instalação), escala própria pelo tamanho real da tela.

- **Aviso de volume:** pílula no alto (alto-falante, barra na cor do tema, número) quando o volume muda; some em ~2 s; não pega toques. Ignora os valores dos primeiros 2 s (volume atual). Opção `show_volume_overlay`.
- **Menu da borda:** área de 30 de profundidade no meio da borda (380 de largura embaixo, 300 de altura nas laterais) com um puxador visível; deslizar 24 para dentro abre a pílula (deitada embaixo, em pé nas laterais). Voltar e Vol± = teclas falsas da MCU (`common/system/McuKeys`); Início = intent HOME. Segurar Vol± repete. Opção `edge_menu_side` (0 desligado, 1 embaixo, 2 esquerda, 3 direita), conferida a cada 3 s. Embaixo, a barra da NWD (`com.nwd.statusbarbottom`) fica por cima e pega o gesto em apps de fora.

## Teclas de mídia do painel/volante (`media/`)

O kernel da NWD entrega as teclas por `com.nwd.action.ACTION_KEY_VALUE` (`extra_key_value` byte). **Vol± e mudo o kernel executa sozinho** (global, qualquer app); **avançar (5) e recuar (6) ele só repassa.** O launcher é o único que escuta.

- `MediaKeyReceiver` (manifest, exportado): pega 5/6 e chama `MediaKeyService`. Receiver de manifest, então funciona com o launcher em segundo plano.
- `MediaKeyService` lê a fonte atual (`mcu_current_source`) e despacha para quem está tocando:
  - **rádio** (4): `RadioClient.presetStep(next)` — próxima/anterior estação salva da banda, ciclando, pulando vazias;
  - **Android** (0): `BtClient.query` — se a música BT está tocando, `playControl` 2/3 (AVRCP próxima/anterior); senão `MusicRemote.send(cmd, onlyIfActive=true)`, que o MP3 só atende se estiver tocando;
  - outra fonte, ou nada tocando: não faz nada.
- Mantém os clientes de rádio/BT conectados por 60 s para apertos seguidos responderem na hora; solta depois de ocioso. A frio, o rádio espera ~0,7 s depois de conectar antes do primeiro passo (estação e memórias precisam chegar). **Não testado na central.**

## Regras

- Não grave configuração: o launcher só lê `launcher.json`.
- Comando para outra fonte sempre pela API do app dela (MusicRemote, BtClient, RadioClient + PlayerHandoff).

## Lacunas

- Não substitui o launcher original: os serviços dele (janela de volume, barra lateral, botão flutuante, clima) continuam no `com.android.launcher`.
