# Autoria e créditos

Este documento registra, de forma honesta, a autoria do código deste projeto.

## Projeto base — Marcelo Madeira

A suíte foi **criada por Marcelo Madeira** (projeto "Garagem Launcher", licença
GPLv3). O commit inicial e a estrutura de **todos os módulos** são dele:

- `common` (biblioteca compartilhada: dock, tema, fonte, idioma, clientes dos
  serviços da NWD)
- `launcher`, `radio`, `music`, `bt-music`, `phone`, `eq`, `fan`, `car-setting`

A modularização (do app monolítico para os módulos atuais) também é dele.

## Camada GDM/HTG — Herbert Guariente

Por cima da base do Madeira, esta linha de trabalho acrescenta a camada de
integração e personalização GDM/HTG:

- Reestruturação da tela inicial (home vertical v2)
- PIP (mapa/app em display virtual na home)
- Widget de tempo (Open-Meteo) e app de Clima (A/C pelo CAN)
- Disco de vinil girando no MP3 e no Bluetooth
- Ajustes de UI (números do teclado, barra superior, etc.)
- Integração com a ROM, CAN/MCU e chave de plataforma própria

## Ownership do código vivo (linhas .java/.xml, por módulo)

| Módulo       | Madeira | Herbert | % Madeira |
|--------------|--------:|--------:|----------:|
| common       |  11.945 |   4.335 |    73%    |
| launcher     |   3.511 |   6.713 |    34%    |
| radio        |     804 |      47 |    94%    |
| music (MP3)  |   2.031 |      58 |    97%    |
| bt-music     |     395 |      45 |    89%    |
| phone        |   1.109 |      25 |    97%    |
| eq           |   2.073 |   1.507 |    57%    |
| fan          |     457 |       0 |   100%    |
| car-setting  |   3.089 |     399 |    88%    |
| **Total**    | **25.414** | **13.129** | **65%** |

Resumo: os apps de mídia (rádio, MP3, Bluetooth, telefone, ventoinha,
configurações) são quase inteiramente do Madeira. A autoria concentrada de
Herbert está na home do launcher, no EQ e em toda a camada ROM/CAN/visual
que não aparece nesta contagem.

## Trazido dos merges recentes do upstream (Madeira)

- Dock arrastável (`DockConfig`/`dock.json`: arrastar da gaveta, reordenar, máx. 7)
- Ícones personalizados (`CustomIcons`)
- Analisador de espectro (barras) na home
- Controles de mídia de apps (Spotify, Deezer, YT Music) no launcher
- EQ modo simples de 7 bandas
- Imagem de fundo opcional na home
- Teclas de avançar/recuar do volante globais
- Reset geral das configurações
