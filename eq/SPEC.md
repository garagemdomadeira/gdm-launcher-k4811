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

## Atraso por alto-falante (aba Canais = aba King do original)

O King do DspAdjust3 é só o atraso de FL, FR, RL, RR, centro e sub (`0x0B`, 0–200 décimos de ms). Na aba Canais: toque no valor = +0,5 ms (depois de 20 ms volta a zero), toque longo = −0,5 ms (`EqModel.DELAY_MAX`/`DELAY_STEP`).

## Aba Graves (`BassPanel`, só no DSP ARM)

A aba Bass do DspAdjust3 original, com os mesmos quadros (tipo áudio `0x0D`):

- **Divisor de frequência** por área (0 FL/FR, 1 RL/RR, 2 centro, 3 subwoofer): `0x0D` (`DspFrames.highPass`/`lowPass`: área, 1 = passa-alta / 0 = passa-baixa, Hz em 2 bytes, inclinação Q em 2 bytes). Frequência pela tabela `EqModel.PF_HZ` (20 Hz a 20 kHz, a `frequenceToReal` original), Q de 0 a 30. Padrão: passa-alta 20 Hz; passa-baixa 20 kHz na frente e atrás, 160 Hz no centro e no sub; Q 10.
- **Reforço de graves (DBB)** por área (0 FL/FR, 1 RL/RR, 2 sub/centro): `0x1B` (área, Hz em 2 bytes, 20, 0, intensidade 0–20). Frequências 40, 60, 100, 150, 200, 250, 300, 400 Hz.
- **Som 3D**: `0x1C` (liga/desliga, 44, 1).
- Tudo gravado em `eq` (`crossovers`, `bass_boosts`, `round_3d`) e enviado também no quadro completo `0xF2` (`DspFrames.otherAll(state)`, como o `sendF2Data` original) na reaplicação e no boot.

## Aba Meus EQs (`SavedEqPanel`, só no DSP ARM)

Os EQs personalizados do DspAdjust3 original, nos mesmos arquivos (`eq/CustomEqFiles`), para os dois apps enxergarem os mesmos EQs:

- **Salvar:** a curva atual vai para `<config>/app/eq_customize/<nome>`, um Properties com `key_7604_frequence`, `key_7735_gain` (ganhos no valor do quadro, o `mEqGainOriginal`) e `key_7735_qvalue`, 32 números separados por vírgula, leitura e escrita para todos (o `chmod 777` original). Nome só com letras, números, `_` e `-` (sugestão `EQ_<data>_<hora>`); se já existir, o botão vira "Substituir?" e o segundo toque grava.
- **Usar:** lê o arquivo e manda o EQ inteiro (`0xF1`), como o `send32EQModeData(nome)`; vira a curva Personalizada e `persist.sys.dsp.eq` recebe o nome do arquivo, como no original. O nome em uso fica em `eq` (`custom_name`, etiqueta "Em uso") até mexer na curva ou trocar de predefinição.
- **Apagar:** pede um segundo toque ("Apagar?").
- **Pen drive:** exportar copia para `<pen drive>/EQ_Setup/` (o primeiro volume removível montado, como o `Utils.uDiskName` original, em `Storages.removable`); importar copia da primeira `EQ_Setup` com arquivos só os que são EQ de verdade (32 frequências e ganhos) e com nome aceito. Gravar no pen drive no Android 10 exige app de sistema (`WRITE_MEDIA_STORAGE`): instalado à parte, exportar avisa que não deu.
- Sem a pasta da NWD (emulador, app instalado à parte) usa `files/eq_customize` do próprio app.

## Componentes

| Componente | Papel |
|---|---|
| `eq/EqController` | estado, detecção, envio, `reapplyAll`, `reapplySaved` (boot) |
| `eq/CustomEqFiles` | EQs salvos: eq_customize, pen drive (`EQ_Setup`), leitura pelo `ArmDsp.readPresetFile(File)` |
| `eq/EqStore` | SharedPreferences `eq`: backend, predefinição, loudness, fader, balanço, bandas MCU, ganhos/frequências/Q do ARM, `apply_on_boot`, `influence`, `arm_simple` |
| `eq/EqBootReceiver` | `BOOT_COMPLETED`, `com.nwd.action.ACTION_MCU_STATE_CHANGE` (`extra_mcu_state` 0→1, último valor gravado em `eq_boot`), `com.nwd.action.ACTION_REINIT_DSP_DATA` |
| `eq/EqBootService` | também `com.nwd.ACTION_DSP_SERVICE`; `reapplySaved` e para |
| `ui/SitePadView`, `ui/VerticalSlider` | controles (o slider avisa o início do arrasto, para a Influência) |

Sem a barra de baixo da suíte (Início · Equalizador · Apps): sai pelo botão de voltar. Tela do EQ: Loudness, Influência, Reset e Aplicar ao ligar em cima (com `ButtonIcon`), sliders de altura fixa (250) para sobrar área de arrastar as bandas de lado, predefinições embaixo. **Influência** (padrão ligada): mexer numa banda leva as 2 vizinhas de cada lado junto, 50% e 25% da mudança, contadas dos valores do início do arrasto (`EqController.setMcuBands`/`setArmBands` enviam só as bandas que mudaram).

**Bandas por caminho:** MCU = 10 bandas (`EQ10_*`). DSP ARM = 32 bandas paramétricas (freq/Q/ganho por banda).

**DSP: modo simples (padrão) × avançado.** No DSP o EQ abre com uma vista de **7 bandas** (chip **Avançado** desligado; `arm_simple`, padrão ligado). As 7 ficam nos índices `EqModel.EQ7_ANCHORS = {5,9,13,17,21,25,29}` do grid de 32 (frequências mais próximas de 60/150/400/1k/2.5k/6k/15k → 63/160/400/1k/2.5k/6.3k/16k; o rótulo é a freq real de `armFrequencies[ancora]`). Mexer numa âncora chama `EqController.setArmAnchor`, que lê as 7 âncoras do `armGainsDb` atual, aplica o novo valor e **interpola linearmente** (`EqModel.interp7to32`) os 32 ganhos (abaixo da 1ª / acima da última âncora repete a ponta); depois reaproveita `setArmBands` (envia só as bandas que mudaram). A fonte de verdade continua sendo `armGainsDb[32]`; o modo simples é uma vista. O chip **Avançado** (`setArmSimple`) mostra as 32 bandas (comportamento antigo). A **Influência** fica oculta no modo simples (a interpolação já suaviza) e o chip **Avançado** só aparece no DSP.

## Reaplicar no boot/ACC

Carrega o estado salvo **sem** ler a MCU por cima; espera conhecer o caminho de áudio e (na MCU) conectar; aguarda 3 s; reaplica; solta a conexão. Timeout 20 s. No ARM reaplica sempre; na MCU só com `apply_on_boot`. No ARM manda também o quadro completo `0xF2` (canais, crossover das 4 áreas, fader/balanço, reforço de graves e 3D), como o `sendF2Data` do DspAdjust3 original: crossover, graves e 3D neutros (áreas 1-2 de 20 Hz a 20 kHz, 3-4 de 20 a 160 Hz). Sem ele o DSP fica com o crossover de quando liga e o som sai baixo e abafado. Também grava, pelo canal da NWD (`SystemProps.requestSet`), `sys.nwd.dsp.type=3` na reaplicação e `persist.sys.dsp.eq=<predefinição>` sempre que a predefinição muda, como o original. O app precisa ter sido aberto uma vez depois de instalado (senão o Android não entrega `BOOT_COMPLETED`).
