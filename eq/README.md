# GDM K4811 App suite · Equalizador

Substituto do app **Áudio / DSP** da central NWD (`com.nwd.audioset`), com o visual padrão do GDM K4811 App suite.

> Especificação técnica (para quem vai mexer no código, inclusive agentes de IA): [`SPEC.md`](SPEC.md).

| | |
|---|---|
| Package | `com.nwd.audioset` (o mesmo do original) |
| Versão | 2.0.7 (versionCode 20270007, maior que o do original) |
| App original | `radio apk/com.nwd.audioset` |
| Linguagem | **Java puro**, Views clássicas e layouts XML, **sem nenhuma biblioteca** |
| minSdk / targetSdk | **17 / 19**, os mesmos dos originais |
| Tamanho do APK | ~710–740 KB (release; a maior parte são as 6 fontes) |

## Código

```
eq/src/main/
├── AndroidManifest.xml          EqActivity (singleTask, launcher)
├── java/com/k4811/audioset/
│   ├── EqActivity.java          bandas, predefinições, fader/balanço (site pad)
│   ├── eq/
│   │   ├── EqController.java    escolhe o caminho: MCU (SettingService) ou DSP ARM
│   │   ├── SettingClient.java   SettingService da NWD (AIDL do common)
│   │   ├── ArmDsp.java,         DspFrames.java DSP ARM (AK7604/AK7735/DTS) e arquivos de predefinição
│   │   └── EqModel.java,        EqStore.java
│   └── ui/                      SitePadView, VerticalSlider
└── res/layout/activity_eq.xml
```

É o item **EQ** do dock. A tela de Som do car-setting também abre este app. Quando ele está instalado, o `com.nwd.audioset` original some da gaveta do launcher.

**Dock e configurações:** usa o mesmo dock do launcher e segue as configurações feitas no [`car-setting`](../car-setting/README.md) (idioma, tema, fonte, ícones do dock). Ver [`../common`](../common/README.md).

## Tela do equalizador

- Em cima, **Loudness**, **Influência**, **Reset** e **Aplicar ao ligar**, cada um com ícone. No DSP aparece também o chip **Avançado**. Embaixo, as predefinições (Padrão, Pop, Rock…).
- **Quantas bandas:** no caminho MCU são **10**. No **DSP** o equalizador abre no **modo simples, com 7 bandas** (60, 150, 400 Hz, 1, 2.5, 6 e 15 kHz — na verdade as frequências do DSP mais próximas: 63/160/400/1k/2.5k/6.3k/16k). Ao mexer numa delas, as frequências intermediárias do DSP seguem por uma **rampa (interpolação linear)** entre as duas bandas vizinhas — no meio do caminho dá a média das duas. O chip **Avançado** troca para a vista completa de **32 bandas**. O ajuste é o mesmo por baixo: o modo simples só controla as 32 bandas em 7 pontos.
- Os sliders das bandas têm altura fixa (250dp), menor que a coluna. O valor em cima e a frequência embaixo ocupam o resto, e é ali que se arrasta para rolar as bandas de lado (no modo avançado, as 32).
- **Influência** (ligada por padrão, gravada com o resto do som): ao mexer numa banda, as 2 vizinhas de cada lado acompanham, as mais próximas com 50% da mudança e as seguintes com 25%. A conta parte dos valores de quando o dedo encostou no slider, então arrastar para cima e para baixo não acumula erro de arredondamento. No **modo simples** do DSP a Influência fica oculta, porque a interpolação das 7 bandas já suaviza a curva.

## Diferenças do original

Activity com o nome do original (`activity-alias`), para o sistema e outros apps continuarem abrindo este app pelo mesmo componente.

**Som reaplicado ao ligar o carro** (papel do `DspServices` e do `StartReceiver` do original):

- O `EqBootReceiver` recebe `BOOT_COMPLETED`, `com.nwd.action.ACTION_MCU_STATE_CHANGE` com `extra_mcu_state` indo de 0 para 1 (o carro saiu do ACC) e `com.nwd.action.ACTION_REINIT_DSP_DATA`. O último estado da MCU fica gravado, porque o processo pode morrer entre os dois avisos.
- O `EqBootService` (que também atende `com.nwd.ACTION_DSP_SERVICE`) carrega o som salvo e chama `EqController.reapplySaved`. Ele espera saber o caminho de áudio e, na MCU, conectar no SettingService; depois aguarda 3 s, como o original, reaplica e para. Se em 20 s não conseguir, desiste.
- **DSP ARM:** reaplica sempre, porque ele volta ao padrão a cada partida.
- **MCU:** só reaplica com **"Aplicar ao ligar"** ligado na tela do EQ. A MCU guarda os valores dela.
- O app de EQ precisa ter sido aberto uma vez depois de instalado: o Android não entrega o `BOOT_COMPLETED` para um app que nunca rodou.
- Ainda não testado na central.

## Build e instalação

```bash
./gradlew :eq:assembleRelease
adb install -r eq/build/outputs/apk/release/eq-release.apk
```

Status: compila (debug e release). Ainda não testado na central.
