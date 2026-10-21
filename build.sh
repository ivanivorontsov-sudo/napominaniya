#!/usr/bin/env bash
# Сборка и подпись: ./build.sh  (пароль берётся из keystore-info.txt)
set -euo pipefail
cd "$(dirname "$0")"
. /opt/tools/env.sh
gradle --no-daemon :app:testReleaseUnitTest :app:assembleRelease
PASS=$(grep '^Пароль хранилища:' keystore-info.txt | awk '{print $3}')
zipalign -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/napom-aligned.apk
apksigner sign --ks release.keystore --ks-key-alias napominaniya --ks-pass pass:"$PASS" --key-pass pass:"$PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --min-sdk-version 26 \
  --out Napominaniya.apk /tmp/napom-aligned.apk
apksigner verify --min-sdk-version 21 --verbose Napominaniya.apk | head -4
