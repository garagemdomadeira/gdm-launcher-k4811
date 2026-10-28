# SPEC · car-setting

Leia antes o [`AGENTS.md`](../AGENTS.md) e o [`common/SPEC.md`](../common/SPEC.md).

| | |
|---|---|
| applicationId | `com.nwd.car.setting` (ao lado do `com.android.car.setting` original) |
| Pacote Java | `com.k4811.car.setting` |
| Application | `SettingApp` (SuiteApp + `NwdSettings.init` + `LauncherConfig.ensureExists` + `AudioGains.reapply`) |
| Orientação | todas as telas `nosensor` |

## Responsabilidade

**Único app que grava o `/sdcard/nwd-configs/launcher.json`.** Também ajusta a central pelo SettingService e por props. Aberto pela engrenagem do launcher.

## Telas

| Activity | Conteúdo |
|---|---|
| `SettingsActivity` | painéis numa coluna lateral: Principais (idioma, dia/noite, voltímetro, apagar a tela), Customizar (tema, fonte, atalhos para as duas telas abaixo), Assistências (câmera de ré, navegação), Sistema (launcher padrão, barra da NWD, bipe, volume ao ligar, brilho, "Sobre"), Áudio (escondido), Avançado (só modo desenvolvedor, com senha) |
| `HomeSettingsActivity` | mapa da tela inicial (`HomeMapView`): tocar no módulo e depois na área; escolher a imagem do módulo Imagem (`home_image`) e a imagem de fundo (`home_bg_image`), ambas da pasta `K4811/`; o fundo tem opção "Remover" (`HomeImage.clearBg`) |
| `DockSettingsActivity` | ícones do dock (até 5) e nome do Z-Link |
| `AdvancedActivity` | região do rádio, freio de mão, ligar com qualquer tecla, props de áudio/microfone, menus da NWD, câmera 360 |
| `DevPropsActivity` | lista e edita props do sistema, exporta para Download/ |

## Onde cada opção grava

| Destino | Como |
|---|---|
| `launcher.json` | `LauncherConfig.set…` |
| MCU (brilho, volume inicial, bipe, ré, região do rádio…) | `NwdSettings` (AIDL `SettingFeature`) e `Settings.System mcu_*` |
| Props (`persist.*`) | `SystemProps.requestSet` + `PropRows` (confere em 1,5 s e mostra o comando adb se não aplicar) |
| App de navegação | `Settings.System navigation_packagename/classname/appname` |

UI das listas: `ui/OptionList` (linhas de toggle, escolha, stepper, ação), `ui/PropRows`, `ui/InputDialog`, `ui/ThemeSwatches`.

## Regras

- Ao mudar idioma, tema ou fonte, `restartScreens()` recria só este app; o launcher se recria sozinho ao voltar.
- O botão "Usar o K4811" grava `persist.nwd.launcher.default = Apps.LAUNCHER`.
- Toda opção nova: string nos 8 idiomas e, se for compartilhada, chave em `LauncherConfig` (ver common).

## Lacunas (fora por enquanto)

Lista branca, restaurar fábrica, atualização do sistema, voz, luz de 3 cores, informação do dispositivo, extra `ref0`. Exigem a chave de plataforma ou não foram portados; continuam no Config. Veículo original.
