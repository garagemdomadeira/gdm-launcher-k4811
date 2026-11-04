# SPEC · eq

Leia antes o [`AGENTS.md`](../AGENTS.md) e o [`common/SPEC.md`](../common/SPEC.md).

| | |
|---|---|
| applicationId | `com.nwd.audioset` (por cima do original) |
| Pacote Java | `com.k4811.audioset` |
| Application | `com.k4811.common.SuiteApp` |
| Activity | `EqActivity` (singleTask, `sensorLandscape`) + alias `com.nwd.audioset.home_horizontalActivity` |
| Análise do original | `radio apk/com.nwd.audioset/README.md` |

## Responsabilidade

Equalizador, loudness, fader/balanço (site pad), canais (nível, mudo, fase, atraso). Dois caminhos de áudio, escolhidos por `EqController` (auto, forçar MCU ou forçar ARM):

- **MCU:** `SettingClient` (AIDL `SettingFeature`): `setAudioParam` (5 predefinição, 12 fader, 13 balanço, 17 loudness) e `setDspAudio` (bandas).
- **DSP ARM** (AK7604/AK7735/DTS): `eq/ArmDsp` + `eq/DspFrames` (frames binários), arquivos de predefinição em `<config>/app/…`.

## Componentes

| Componente | Papel |
|---|---|
| `eq/EqController` | estado, detecção, envio, `reapplyAll`, `reapplySaved` (boot) |
| `eq/EqStore` | SharedPreferences `eq`: backend, predefinição, loudness, fader, balanço, bandas MCU, ganhos/frequências/Q do ARM, `apply_on_boot`, `influence` |
| `eq/EqBootReceiver` | `BOOT_COMPLETED`, `com.nwd.action.ACTION_MCU_STATE_CHANGE` (`extra_mcu_state` 0→1, último valor gravado em `eq_boot`), `com.nwd.action.ACTION_REINIT_DSP_DATA` |
| `eq/EqBootService` | também `com.nwd.ACTION_DSP_SERVICE`; `reapplySaved` e para |
| `ui/SitePadView`, `ui/VerticalSlider` | controles (o slider avisa o início do arrasto, para a Influência) |

Tela do EQ: Loudness, Influência, Reset e Aplicar ao ligar em cima (com `ButtonIcon`), sliders de altura fixa (250) para sobrar área de arrastar as bandas de lado, predefinições embaixo. **Influência** (padrão ligada): mexer numa banda leva as 2 vizinhas de cada lado junto, 50% e 25% da mudança, contadas dos valores do início do arrasto (`EqController.setMcuBands`/`setArmBands` enviam só as bandas que mudaram).

## Reaplicar no boot/ACC

Carrega o estado salvo **sem** ler a MCU por cima; espera conhecer o caminho de áudio e (na MCU) conectar; aguarda 3 s; reaplica; solta a conexão. Timeout 20 s. No ARM reaplica sempre; na MCU só com `apply_on_boot`. No ARM manda também o quadro completo `0xF2` (canais, crossover das 4 áreas, fader/balanço, reforço de graves e 3D), como o `sendF2Data` do DspAdjust3 original: crossover, graves e 3D neutros (áreas 1-2 de 20 Hz a 20 kHz, 3-4 de 20 a 160 Hz). Sem ele o DSP fica com o crossover de quando liga e o som sai baixo e abafado. Também grava, pelo canal da NWD (`SystemProps.requestSet`), `sys.nwd.dsp.type=3` na reaplicação e `persist.sys.dsp.eq=<predefinição>` sempre que a predefinição muda, como o original. O app precisa ter sido aberto uma vez depois de instalado (senão o Android não entrega `BOOT_COMPLETED`).
