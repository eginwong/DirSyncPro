#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p dist/staging dist/mac
rm -rf dist/staging/*
rm -rf dist/mac/*

mvn -q test package

cp target/dirSyncPro-0.1.jar dist/staging/
cp -R target/lib dist/staging/
cp -R src/main/resources dist/staging/resources

jpackage \
  --type app-image \
  --input dist/staging \
  --name DirSyncPro \
  --main-jar dirSyncPro-0.1.jar \
  --main-class dirsyncpro.DirSyncPro \
  --dest dist/mac \
  --app-version 1.0 \
  --vendor "DirSyncPro" \
  --icon src/main/resources/icons/DirSyncPro.png \
  --resource-dir src/main/resources \
  --java-options "-Xmx512m"
