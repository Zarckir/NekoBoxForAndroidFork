#!/bin/bash

set -e

chmod -R 777 .build 2>/dev/null || true
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Install gomobile
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ]; then
    rm -rf gomobile
    git clone https://github.com/MatsuriDayo/gomobile.git
    pushd gomobile
	git checkout origin/master2
    go install -v ./cmd/gomobile
    go install -v ./cmd/gobind
    popd
    rm -rf gomobile
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
fi

GOBIND="$GOPATH/bin/gobind-matsuri" "$GOPATH/bin/gomobile-matsuri" init
