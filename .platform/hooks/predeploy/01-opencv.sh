#!/bin/bash
# OpenCV 네이티브(.so) 설치 — dcm4che JPEG 디코딩용 (weasis-core-img가 로드)
# 인스턴스가 새로 뜰 때마다 실행되므로 수동 설치 불필요.
set -e
LIB_DIR=/var/app/lib
SO_URL="https://raw.githubusercontent.com/nroduit/mvn-repo/master/org/weasis/thirdparty/org/opencv/libopencv_java/4.11.0-dcm/libopencv_java-4.11.0-dcm-linux-x86-64.so"

mkdir -p "$LIB_DIR"
if [ ! -f "$LIB_DIR/libopencv_java.so" ]; then
  curl -fsSL -o "$LIB_DIR/libopencv_java.so" "$SO_URL"
fi
chmod 755 "$LIB_DIR/libopencv_java.so"
