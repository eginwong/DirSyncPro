#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APP_VERSION="${1:-$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null | tr -d '\r' | tr -d '\n')}"
if [[ -z "$APP_VERSION" || "$APP_VERSION" == "null" ]]; then
  APP_VERSION="0.1"
fi

APP_VERSION="$(printf '%s' "$APP_VERSION" | tr -cd '0-9.' | sed -E 's/\.+/./g; s/^\.|\.$//g')"
if [[ -z "$APP_VERSION" ]]; then
  APP_VERSION="1.0"
fi
IFS='.' read -r -a APP_VERSION_PARTS <<< "$APP_VERSION"
if [[ ${#APP_VERSION_PARTS[@]} -gt 0 && ${APP_VERSION_PARTS[0]} == "0" ]]; then
  APP_VERSION_PARTS[0]="1"
fi
APP_VERSION="${APP_VERSION_PARTS[0]}"
for part in "${APP_VERSION_PARTS[@]:1}"; do
  if [[ -n "$part" ]]; then
    APP_VERSION="${APP_VERSION}.${part}"
  fi
done
if [[ ${#APP_VERSION_PARTS[@]} -gt 3 ]]; then
  APP_VERSION="${APP_VERSION_PARTS[0]}.${APP_VERSION_PARTS[1]}.${APP_VERSION_PARTS[$(( ${#APP_VERSION_PARTS[@]} - 1 ))]}"
fi
if [[ "$APP_VERSION" =~ ^0\.|^0$ ]]; then
  APP_VERSION="1${APP_VERSION#0}"
fi

mkdir -p dist/staging dist/mac
rm -rf dist/staging/*
rm -rf dist/mac/*

mvn -q test package

JAR_PATH="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | head -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  echo "No built JAR found in target/" >&2
  exit 1
fi
JAR_NAME="$(basename "$JAR_PATH")"

cp "$JAR_PATH" dist/staging/
cp -R target/lib dist/staging/
cp -R src/main/resources dist/staging/resources

jpackage \
  --type app-image \
  --input dist/staging \
  --name DirSyncPro \
  --main-jar "$JAR_NAME" \
  --main-class dirsyncpro.DirSyncPro \
  --dest dist/mac \
  --app-version "$APP_VERSION" \
  --vendor "DirSyncPro" \
  --icon src/main/resources/icons/DirSyncPro.png \
  --resource-dir src/main/resources \
  --java-options "-Xmx1g"
