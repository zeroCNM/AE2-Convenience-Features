#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
# GTNHGradle 1.0.43 reads VERSION, not -Pversion, for its version override.
export VERSION="${VERSION:-$(cat VERSION)}"
if [ "$#" -eq 0 ]; then
    set -- build
fi
exec bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
    -Dorg.gradle.internal.http.connectionTimeout=15000 \
    -Dorg.gradle.internal.http.socketTimeout=15000 "$@"
