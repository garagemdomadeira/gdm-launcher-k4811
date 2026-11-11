# GDM Atualizações (`updater`)

Atualiza os apps da suíte e a ROM pelas releases públicas do GitHub
(`guarienteherbert-ux/gdm-k4811-ota`). Nada é instalado sem a pessoa tocar em **Confirmar update**.

## Como funciona

- **Agenda** (`CheckReceiver`): no boot e quando o próprio app é atualizado, um alarme por dia (o primeiro
  10 min depois de ligar). A cada alarme, `CheckService` baixa o `ota.json` do GitHub se já faz 7 dias desde
  a última procura; senão usa o último guardado.
- **Lista** (`Ota`): `releases/latest/download/ota.json` + `ota.json.sig` (RSA/SHA-256). A chave pública
  está em `Ota.PUBLIC_KEY`; a privada em `K4811_mod/ota_keys/ota.key.pem` (fora do git). Sem assinatura
  válida, nada é usado: o recovery aceita a chave de teste do AOSP, que é pública.
- **O que é novo**: apps **instalados** com `versionCode` maior (o launcher perto do fim, este app por
  último); ROM do canal desta central com `codigo` maior. Canal e número vêm de `k4811_ota_canal` e
  `k4811_ota_rom` no `UpdateConfig.ini` (cópia em `/data/nwdappconfig/app`, senão `/system/config/app`).
  ROM sem essas linhas: só apps.
- **Aviso** (`Prompt`): janela flutuante no alto da tela, com **Agora não** (pergunta de novo amanhã) e
  **Confirmar update** (abre `UpdatesActivity` e instala os apps).
- **Apps** (`Installer.installApp`): baixa, confere SHA-256 e a assinatura (igual à instalada) e instala
  por sessão do `PackageInstaller`. Como sistema, sem tela; fora da ROM, com a confirmação do Android.
- **ROM** (`Installer.installRom`): pede uma segunda confirmação (avisa se a internet é do celular). Só
  segue se a ROM publicada tiver a mesma chave de plataforma da instalada (`plataforma` no `ota.json` e a
  marca `META-INF/com/k4811/platform.sha256` no zip); com outra chave apagaria os dados, então manda usar o
  pen drive. Baixa direto em `/cache/update.zip` (continua de onde parou), confere SHA-256 e manda o
  `MASTER_CLEAR` como o atualizador da NWD, com `recovery=/cache/update.zip` e `extra_wipe_data=false`
  (o `MasterClearReceiver` da ROM chama `RecoverySystem.rebootWipeDataAndUpdateOS` sem apagar).

## Assinatura e instalação

`sharedUserId="android.uid.system"`: o APK precisa da chave de **plataforma** da ROM, não a da suíte.

```bash
./gradlew :updater:assembleRelease && tools/assina_plataforma.sh   # -> updater/build/GDM-Atualizacoes.apk
./gradlew :updater:assembleRelease -PsemSistema                    # emulador: sem sharedUserId
```

Só instala em ROM assinada com a nossa chave de plataforma (as v16+ e as da comunidade).

## Publicar

```bash
tools/publica_ota.py --apps launcher.apk eq.apk updater/build/GDM-Atualizacoes.apk \
    --notas com.garagemdomadeira.nwd.launcher="o que mudou" [--rom gdm:4:4:rom.zip --notas-rom "..."] [--simular]
```

O `versionCode` de cada app tem de subir. A ROM precisa trazer o GDM Atualizações, as linhas
`k4811_ota_canal` / `k4811_ota_rom` no `UpdateConfig.ini` e a marca da chave de plataforma. Canal: `gdm`
(horizontal). Limite do GitHub: 2 GB por arquivo; o `/cache` da central tem 1,7 GB.

## Não testado na central

Testado no emulador: procura, assinatura, aviso, download e instalação de app (com a confirmação do
Android). A instalação sem tela como sistema e a gravação da ROM pelo `MASTER_CLEAR` sem apagar ainda
precisam ser testadas na central.
