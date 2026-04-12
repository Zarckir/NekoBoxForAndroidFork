#!/bin/bash

source buildScript/init/env_ndk.sh

if [[ "$OSTYPE" =~ ^darwin ]]; then
  export SRC_ROOT=$PWD
else
  export SRC_ROOT=$(realpath .)
fi

PREBUILT_ROOT=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt

if [[ "$OSTYPE" =~ ^darwin ]]; then
  if [ -d "$PREBUILT_ROOT/darwin-arm64/bin" ]; then
    DEPS=$PREBUILT_ROOT/darwin-arm64/bin
  else
    DEPS=$PREBUILT_ROOT/darwin-x86_64/bin
  fi
else
  DEPS=$PREBUILT_ROOT/linux-x86_64/bin
fi

if [ ! -d "$DEPS" ]; then
  echo "Error: NDK toolchain not found at $DEPS"
  exit 1
fi

export ANDROID_ARM_CC=$DEPS/armv7a-linux-androideabi21-clang
export ANDROID_ARM_CXX=$DEPS/armv7a-linux-androideabi21-clang++
export ANDROID_ARM_CC_21=$DEPS/armv7a-linux-androideabi21-clang
export ANDROID_ARM_CXX_21=$DEPS/armv7a-linux-androideabi21-clang++
export ANDROID_ARM_STRIP=$DEPS/arm-linux-androideabi-strip

export ANDROID_ARM64_CC=$DEPS/aarch64-linux-android21-clang
export ANDROID_ARM64_CXX=$DEPS/aarch64-linux-android21-clang++
export ANDROID_ARM64_STRIP=$DEPS/aarch64-linux-android-strip

export ANDROID_X86_CC=$DEPS/i686-linux-android21-clang
export ANDROID_X86_CXX=$DEPS/i686-linux-android21-clang++
export ANDROID_X86_CC_21=$DEPS/i686-linux-android21-clang
export ANDROID_X86_CXX_21=$DEPS/i686-linux-android21-clang++
export ANDROID_X86_STRIP=$DEPS/i686-linux-android-strip

export ANDROID_X86_64_CC=$DEPS/x86_64-linux-android21-clang
export ANDROID_X86_64_CXX=$DEPS/x86_64-linux-android21-clang++
export ANDROID_X86_64_STRIP=$DEPS/x86_64-linux-android-strip
