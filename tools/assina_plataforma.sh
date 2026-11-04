#!/usr/bin/env bash
# Assina o GDM Atualizações (updater) com a chave de PLATAFORMA da ROM: ele usa sharedUserId android.uid.system
# para instalar apps sem a tela do Android e gravar a ROM no /cache. Só funciona em ROM assinada com essa mesma chave.
#
# uso: tools/assina_plataforma.sh [apk de entrada] [apk de saída]
#   padrão: updater/build/outputs/apk/release/updater-release.apk -> updater/build/GDM-Atualizacoes.apk
# Chave: $PLATFORM_KEYS (padrão ~/Downloads/K4811_mod/platform_keys, com platform.pk8 e platform.x509.pem).
set -euo pipefail
cd "$(dirname "$0")/.."
IN="${1:-updater/build/outputs/apk/release/updater-release.apk}"
OUT="${2:-updater/build/GDM-Atualizacoes.apk}"
KEYS="${PLATFORM_KEYS:-$HOME/Downloads/K4811_mod/platform_keys}"
BT=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/ | tail -1)
"${BT}apksigner" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" \
    --v1-signing-enabled true --v2-signing-enabled true --out "$OUT" "$IN"
"${BT}apksigner" verify --print-certs "$OUT" | head -2
echo "$OUT"
