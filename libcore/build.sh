#!/bin/bash

if [ -f ./env_java.sh ]; then
  source ./env_java.sh
fi
source ../buildScript/init/env_ndk.sh

BUILD=".build"

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

mkdir -p "$BUILD"
CACHE_DIR=$(cd "$BUILD" && pwd)

export GOBIND="$GOPATH/bin/gobind-matsuri"
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 21 -cache "$CACHE_DIR" -trimpath -ldflags='-s -w' -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api' . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
