#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./mvnw --batch-mode package
exec java --enable-native-access=ALL-UNNAMED -jar target/findspace.jar "$@"
