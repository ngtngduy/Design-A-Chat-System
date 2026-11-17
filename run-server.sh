#!/bin/bash
set -e

# Tự động dùng JDK đi kèm Android Studio nếu máy chưa cài Java trên PATH
if ! command -v java >/dev/null 2>&1; then
  STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [ -d "$STUDIO_JBR" ]; then
    export JAVA_HOME="$STUDIO_JBR"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

DIST_BIN="./server/build/install/server/bin/server"

if [ ! -x "$DIST_BIN" ]; then
  if [ -x "./gradlew" ]; then
    echo "Đang đóng gói server lần đầu (:server:installDist)..."
    ./gradlew :server:installDist --quiet
  else
    echo "Chưa có $DIST_BIN."
    echo "Trong Android Studio: mở tab Gradle (mép phải) -> Tasks -> distribution -> installDist (chạy 1 lần)."
    exit 1
  fi
fi

echo "Khởi động Chat Server (độc lập, không giữ khóa Gradle)..."
exec "$DIST_BIN"
