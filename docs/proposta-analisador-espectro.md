# Proposta: módulo analisador de espectro (barras) na tela inicial

> **Status:** proposta, só para discussão. Nada implementado. Nenhum comportamento foi testado na central.
> **Envolve:** `launcher` (módulo e View), `common` (grade da tela inicial e cliente do Visualizer), `car-setting` (posicionar o módulo e, talvez, a permissão).

## Objetivo

Um módulo opcional na grade da tela inicial que desenha um **analisador de espectro em barras**, reagindo em tempo real ao áudio que está tocando (como as "barrinhas" de um equalizador visual).

## Tem API para isso? Sim: `android.media.audiofx.Visualizer`

O Android traz a API nativa **`Visualizer`** (desde a API 9), que "escuta" a saída de áudio e entrega:

- **`getWaveForm(byte[])`** — forma de onda (osciloscópio).
- **`getFft(byte[])`** — **FFT**: é daqui que saem as **barras** (energia por faixa de frequência).

Sem biblioteca nenhuma — respeita a regra do projeto (Java puro, só o SDK).

### Fluxo

1. `Visualizer v = new Visualizer(sessionId);`
2. `v.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);` (máximo, normalmente 1024)
3. `v.setDataCaptureListener(listener, Visualizer.getMaxCaptureRate() / 2, false /*waveform*/, true /*fft*/);`
4. `v.setEnabled(true);` ao mostrar; `false` ao esconder; `release()` ao destruir.
5. No `onFftDataCapture(byte[] fft, …)`: para cada par `(re, im)`, magnitude `≈ 10·log10(re² + im²)`; agrupar os bins em **N barras** (escala **logarítmica** na frequência), normalizar em dB e desenhar, com **decaimento** (queda suave) e **pico** (capinha que desce devagar).

### `sessionId`

- **`0` (mix global de saída):** vê **todo** o áudio que sai pelo Android. É o que serve para um analisador genérico.
- **Sessão específica:** só um player — não serve aqui (o launcher não toca nada).

## 🔎 Já existe precedente nesta central (engenharia reversa)

Procurei na `ROM/` e nos apps de fábrica decompilados (`radio apk/`):

- Os **docs da ROM** não mencionam espectro/visualizador.
- **Mas o app de rádio de fábrica** (`com.nwd.radio`) **traz uma classe pronta**: `com.android.utils.ui.spectrum.SpectrumView` (`radio apk/com.nwd.radio/decomp-apk-studio/sources/com/android/utils/ui/spectrum/SpectrumView.java`). Ela faz **exatamente** o que queremos:
  - `new Visualizer(0)` (mix global), `setCaptureSize(getCaptureSizeRange()[1])`, FFT a `getMaxCaptureRate()/2`;
  - `setEnabled(true)` em `onAttachedToWindow`, `false` em `onDetachedFromWindow`;
  - no `onDraw`, altura da barra `= altura − 10·log10(re²+im²)·6`, com **dois decaimentos**: um rápido para a barra (passo 4) e um lento para a **capinha de pico** (passo 8), desenhada como um traço de 4 px no topo.

### O que esse precedente diz (e o que não diz)

- **Diz:** o `Visualizer` era o caminho pretendido nessas unidades e temos um **molde de renderização** pronto para portar (a receita de magnitude, decaimento e pico acima).
- **Não diz que funciona de ponta a ponta:** a `SpectrumView` **não está referenciada em nenhum layout nem tela** do app de rádio (é código morto, provavelmente de um template `com.android.utils`), e o **manifesto do rádio não declara `RECORD_AUDIO`**. Portanto não há prova de que ela capturava áudio de fato — nem de qual áudio. Continua sendo necessário **testar na central**.

## Os poréns (o que precisa ser validado no aparelho)

1. **O que o `Visualizer(0)` enxerga.** Ele vê o **mix de saída do Android**. Nesta unidade, **rádio FM e AUX** são roteados pelo **MCU** (fora do Android), então podem **não** aparecer. Deve reagir bem a **Spotify, MP3, YouTube Music** (fonte Android); **BT (A2DP) é incerto**. *(O precedente no app de rádio sugere que alguém imaginou mostrar espectro no rádio — mas, sendo código morto, não confirma que o áudio do rádio chega ao mix do Android.)*
2. **Permissão `RECORD_AUDIO`.** O `Visualizer` na sessão 0 captura áudio e exige `RECORD_AUDIO` (+ `MODIFY_AUDIO_SETTINGS`, que já temos). Com `targetSdk 19` ela é **concedida na instalação**; no Android 10 pode haver *appops* — a confirmar. (O rádio de fábrica é app de sistema/plataforma e não precisava declará-la.)
3. **Um global por vez.** Só um app pode ter o `Visualizer(0)` ativo. Conflito improvável, mas possível.
4. **Suporte da ROM.** Como a classe de fábrica usa a API, o framework quase certamente a tem; ainda assim, `new Visualizer(0)` pode falhar — tratar com `try/catch` e esconder o módulo se não der.
5. **Custo de CPU.** Rodar **só com a tela inicial visível** (como o módulo de GPS já faz), senão gasta à toa.

## Como entraria no projeto

- Novo módulo `SPECTRUM` em `HomeGrid.Module`, posicionável na grade pelo car-setting (como Relógio, Imagem, etc.).
- `SpectrumView` (View custom, Java puro + `Canvas`), portando a receita do precedente, mas:
  - agrupando em **N barras** (ex.: 16–24) em **escala log**, em vez de uma linha por bin;
  - usando as **cores do tema** (`Ui.ACCENT`/`Ui.TEXT…`) e barras arredondadas, no visual da suíte;
  - com **decaimento + pico** como no original.
- Um cliente/estado em `common` (ex.: `common/media/SpectrumSource`) que liga/desliga o `Visualizer`, trata permissão e erro, e entrega as barras já agrupadas (a View fica "burra").
- `RECORD_AUDIO` no manifesto do launcher; string do módulo nos 8 idiomas.
- Dado o risco dos poréns nº 1 e 2, começaria **atrás do modo desenvolvedor** (ou de um toggle desligado) até validar no carro.

## Decisões em aberto (precisa da sua escolha)

1. **Quantas barras e estilo:** poucas e grossas (ex.: 16, cara de "EQ") ou muitas e finas (estilo do precedente)? Com ou sem a capinha de pico?
2. **De onde vem o áudio que importa:** só confirmar que Spotify/MP3 aparecem, ou investir em tentar captar rádio/BT (pode não ser possível pelo caminho do MCU)?
3. **Entrada no projeto:** já como módulo normal da grade, ou primeiro **atrás do modo desenvolvedor** para o teste de viabilidade?
4. **Teste mínimo antes da UI:** vale um beta "sonda" que só loga se `new Visualizer(0)` abre e se chega FFT com som tocando (rádio vs. Spotify), para decidir o escopo com dado real?

## Esboço de arquivos (quando for implementar)

```
common/src/main/java/com/k4811/common/media/SpectrumSource.java   liga o Visualizer, agrupa em N barras, trata permissão/erro
launcher/src/main/java/com/k4811/launcher/home/SpectrumView.java   desenha as barras (Canvas), decaimento + pico, cores do tema
launcher/.../home/HomeGrid.java + HomeModuleViews.java             novo módulo SPECTRUM na grade
launcher/src/main/AndroidManifest.xml                              permissão RECORD_AUDIO
car-setting/.../HomeSettingsActivity.java                          posicionar o módulo (já é automático pelo mapa)
common/src/main/res/values*/strings_*.xml                          nome do módulo nos 8 idiomas
```

Referência de renderização: `radio apk/com.nwd.radio/.../com/android/utils/ui/spectrum/SpectrumView.java` (app de fábrica).

Nenhuma biblioteca nova; tudo com o SDK do Android. **Não testado na central.**
