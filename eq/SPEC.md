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
| `eq/EqStore` | SharedPreferences `eq`: backend, predefinição, loudness, fader, balanço, bandas MCU, ganhos/frequências/Q do ARM, `apply_on_boot`, `influence`, `arm_simple` |
| `eq/EqBootReceiver` | `BOOT_COMPLETED`, `com.nwd.action.ACTION_MCU_STATE_CHANGE` (`extra_mcu_state` 0→1, último valor gravado em `eq_boot`), `com.nwd.action.ACTION_REINIT_DSP_DATA` |
| `eq/EqBootService` | também `com.nwd.ACTION_DSP_SERVICE`; `reapplySaved` e para |
| `ui/SitePadView`, `ui/VerticalSlider` | controles (o slider avisa o início do arrasto, para a Influência) |

Tela do EQ: Loudness, Influência, Reset e Aplicar ao ligar em cima (com `ButtonIcon`), sliders de altura fixa (250) para sobrar área de arrastar as bandas de lado, predefinições embaixo. **Influência** (padrão ligada): mexer numa banda leva as 2 vizinhas de cada lado junto, 50% e 25% da mudança, contadas dos valores do início do arrasto (`EqController.setMcuBands`/`setArmBands` enviam só as bandas que mudaram).

**Bandas por caminho:** MCU = 10 bandas (`EQ10_*`). DSP ARM = 32 bandas paramétricas (freq/Q/ganho por banda).

**DSP: modo simples (padrão) × avançado.** No DSP o EQ abre com uma vista de **7 bandas** (chip **Avançado** desligado; `arm_simple`, padrão ligado). As 7 ficam nos índices `EqModel.EQ7_ANCHORS = {5,9,13,17,21,25,29}` do grid de 32 (frequências mais próximas de 60/150/400/1k/2.5k/6k/15k → 63/160/400/1k/2.5k/6.3k/16k; o rótulo é a freq real de `armFrequencies[ancora]`). Mexer numa âncora chama `EqController.setArmAnchor`, que lê as 7 âncoras do `armGainsDb` atual, aplica o novo valor e **interpola linearmente** (`EqModel.interp7to32`) os 32 ganhos (abaixo da 1ª / acima da última âncora repete a ponta); depois reaproveita `setArmBands` (envia só as bandas que mudaram). A fonte de verdade continua sendo `armGainsDb[32]`; o modo simples é uma vista. O chip **Avançado** (`setArmSimple`) mostra as 32 bandas (comportamento antigo). A **Influência** fica oculta no modo simples (a interpolação já suaviza) e o chip **Avançado** só aparece no DSP.

## Reaplicar no boot/ACC

Carrega o estado salvo **sem** ler a MCU por cima; espera conhecer o caminho de áudio e (na MCU) conectar; aguarda 3 s; reaplica; solta a conexão. Timeout 20 s. No ARM reaplica sempre; na MCU só com `apply_on_boot`. O app precisa ter sido aberto uma vez depois de instalado (senão o Android não entrega `BOOT_COMPLETED`).
