# Proposta: controles de mídia do Spotify (e afins) no launcher

> **Status:** proposta, só para discussão. Nada implementado. Nenhum comportamento foi testado na central.
> **Envolve:** `launcher` (principal), `common` (cliente de mídia e contrato), `car-setting` (ativar e dar a permissão).

## Objetivo

Mostrar o que o **Spotify** está tocando na tela inicial (faixa, artista, capa) e **controlar** (tocar/pausar, próxima, anterior) pelo launcher — pela tela inicial e pelas **teclas de mídia do volante** que já roteamos (`MediaKeyService`). O mesmo mecanismo serve para qualquer app de áudio do Android (YouTube Music, Deezer, podcasts…), então a proposta é, na prática, **"controles de mídia de apps do Android"**, com o Spotify como caso principal.

## O que NÃO vamos usar (e por quê)

- **Spotify App Remote SDK / Web API:** exige biblioteca externa e login OAuth. Fere a regra 1 (Java puro, sem bibliotecas) e some com a simplicidade. **Descartado.**
- **`android.media.RemoteController` (API 19):** funciona via notification listener, mas está **deprecado** desde a API 21 e é mais limitado. Usamos o sucessor. **Descartado.**
- **Só disparar tecla de mídia** (`AudioManager.dispatchMediaKeyEvent` com `KEYCODE_MEDIA_*`): controla quem tem o foco de áudio, mas **não lê** faixa/estado/capa. Fica como **plano B** de controle, não como solução.

## Abordagem proposta: MediaSession (tudo AOSP, sem lib)

O Android expõe o que está tocando através de **`MediaSession`**. Um app pode ler e controlar essas sessões com:

1. **`NotificationListenerService`** — um serviço no launcher que o usuário habilita uma vez em **Acesso a notificações**. É o que destrava o acesso às sessões de mídia para um app comum (sem precisar da chave de plataforma).
2. **`MediaSessionManager.getActiveSessions(ComponentName)`** — passando o componente do nosso listener, devolve a lista de **`MediaController`** ativos. API 21+ (o aparelho é Android 10 / API 29; guardamos com `Build.VERSION.SDK_INT >= 21`).
3. Para cada `MediaController`:
   - `getPackageName()` → de quem é a sessão (ex.: `com.spotify.music`).
   - `getMetadata()` → **`MediaMetadata`**: título, artista, álbum, duração e **capa** (bitmap ou URI).
   - `getPlaybackState()` → **`PlaybackState`**: tocando/pausado, posição, e **quais ações** a sessão aceita (próxima, anterior…).
   - `registerCallback(...)` → avisa quando faixa ou estado mudam (sem polling).
   - `getTransportControls()` → **`play()` / `pause() / skipToNext() / skipToPrevious()`**.

Ou seja: a mesma família de APIs que um Android Auto usa, só que lida por um listener de notificações.

### Por que funciona com targetSdk 19

`minSdk 17 / targetSdk 19` não impede chamar APIs mais novas: o `compileSdk 36` conhece as classes e o aparelho (API 29) as tem. É só **guardar por versão** (`SDK_INT >= 21`) e, abaixo disso, não oferecer o recurso. O `NotificationListenerService` e a tela de **Acesso a notificações** independem do targetSdk.

## Permissão e ativação (o ponto sensível)

Ler sessões de mídia exige **uma** destas:

- **`MEDIA_CONTENT_CONTROL`** — permissão de sistema, **não** concedível a app comum (precisaria da chave de plataforma; o launcher é `com.garagemdomadeira.nwd.launcher`, não plataforma). **Fora.**
- **Acesso a notificações** — o usuário liga em **Configurações do Android › Acesso a notificações** (ou `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). É o caminho viável.

Proposta de fluxo no **car-setting**:

- Um item novo em **Customizar tela inicial** (ou **Principais**): **"Controles de mídia de apps (Spotify)"**, com um botão **"Dar acesso"** que abre a tela de Acesso a notificações.
- O status (ligado/desligado) é lido de `NotificationManagerService` (lista de listeners habilitados) e mostrado na nota da linha.
- Uma chave no `launcher.json` (ex.: `media_app_controls`, só o car-setting grava) liga/desliga o recurso na tela inicial, mesmo com a permissão concedida.

> Sem a permissão, o recurso simplesmente não aparece; o resto do launcher não muda.

## O que muda no launcher

- **Novo `NotificationListenerService`** (ex.: `media/MediaNotificationListener`) declarado no manifest com `BIND_NOTIFICATION_LISTENER_SERVICE`. Ele existe para destravar o `getActiveSessions`; não precisa tratar notificação nenhuma.
- **Novo cliente em `common`** (ex.: `common/media/AppMediaClient`): acha a sessão "ativa" (preferir a que está `PLAYING`; empate → a mais recente), expõe um snapshot (pacote, título, artista, capa, tocando, posição/duração, ações suportadas) e os comandos (play/pause/next/prev). Fica no `common` porque é candidato a ser usado por mais de uma tela.
- **`HomeSession`**: hoje trata `RADIO`, `USB` (nosso MP3) e `BT`. Entra um `Kind` novo, ex.: `APP` (mídia de app Android), alimentado pelo `AppMediaClient` quando a fonte é **Android (0)** e há uma sessão de mídia de terceiro ativa.
- **Módulo "Tocando agora"**: passa a exibir também a mídia de app (capa + faixa + artista + ⏮⏯⏭). Tocar no cartão abre o app dono da sessão (`getPackageManager().getLaunchIntentForPackage(pacote)`).
- **Selo da fonte (barra superior)**: além de Rádio/MP3/BT, mostra o nome do app (ex.: "Spotify").
- **`MediaKeyService`** (teclas do volante): na fonte **Android**, a ordem de decisão passa a considerar a sessão de app. Decisão em aberto (ver abaixo): **Spotify tem prioridade sobre o MP3 da suíte?**

## Convivência com "uma fonte por vez"

O Spotify toca na **fonte Android (0)**, como o nosso app de MP3. A regra "uma fonte por vez" da suíte pausa os **players da suíte** (rádio/BT/MP3); o Spotify, sendo app de terceiro, **não** é pausado por nós — quem arbitra o áudio dentro do Android é o **foco de áudio** do sistema. Pontos a validar na central:

- Começar o **rádio/BT** tira a fonte do Android → o sistema da NWD normalmente **pausa/derruba** o áudio Android (logo, o Spotify também). Precisamos confirmar o comportamento real.
- Com dois players na fonte Android (nosso MP3 **e** Spotify), o `AppMediaClient` deve mostrar/controlar **o que está realmente tocando** (sessão `PLAYING`), não os dois.

## Capacidades e limites

**Dá para:** ler faixa/artista/álbum/duração/posição, capa, estado de reprodução; tocar/pausar, próxima, anterior; abrir o app. Genérico para qualquer app com `MediaSession` (Spotify, YT Music, Deezer, podcasts…).

**Limites:**
- **Curtir, playlists, busca, seek, volume do app** não fazem parte do controle padrão de transporte (alguns apps expõem ações customizadas via `PlaybackState.getCustomActions()`, mas é por app e frágil — fora do escopo).
- Depende do app **publicar** uma `MediaSession` correta (o Spotify publica).
- A capa pode vir como **URI** (precisa carregar em disco/stream, fora da thread principal, como já fazemos para capas).
- Exige a permissão manual de **Acesso a notificações** (não dá para conceder sozinho sem chave de plataforma).
- `SDK_INT >= 21` (ok no aparelho).

## Alternativa de baixo custo (plano B)

Se só o **controle** importasse (sem ler faixa/capa), dava para disparar `KEYCODE_MEDIA_NEXT/PREV/PLAY_PAUSE` por `AudioManager` para quem tem o foco — sem permissão nenhuma. Mas perde o "o que está tocando", que é metade do valor. Proposta: ir de MediaSession; cair no plano B só se a permissão não estiver concedida.

## Decisões em aberto (precisa da sua escolha)

1. **Prioridade na fonte Android:** quando Spotify e o MP3 da suíte podem tocar, quem manda nas teclas do volante e no cartão — **o que estiver `PLAYING`** (recomendado) ou sempre um deles?
2. **Como aparece:** reusar o módulo **"Tocando agora"** (recomendado, menos UI nova) ou um **módulo/linha separada** "Mídia do app"?
3. **Abrangência:** qualquer app com sessão de mídia (recomendado) ou **lista fixa** (só `com.spotify.music`)?
4. **Onde fica o ativar/permissão** no car-setting: dentro de **Customizar tela inicial** ou em **Principais**?

## Esboço de arquivos (quando for implementar)

```
common/src/main/java/com/k4811/common/media/AppMediaClient.java   sessões ativas, snapshot e comandos
launcher/src/main/java/com/k4811/launcher/media/MediaNotificationListener.java   destrava getActiveSessions
launcher/.../HomeActivity.java + home/HomeSession.java            novo Kind APP no "Tocando agora"
launcher/.../media/MediaKeyService.java                           roteia próxima/anterior para a sessão
car-setting/.../HomeSettingsActivity.java                         ativar + abrir Acesso a notificações
common/.../config/LauncherConfig.java                             chave media_app_controls
common/src/main/res/values*/strings_*.xml                         textos nos 8 idiomas
```

Nenhuma biblioteca nova; tudo com o SDK do Android. Strings nos 8 idiomas. Comentários em inglês no código portado / português no da suíte, como manda o `AGENTS.md`.
