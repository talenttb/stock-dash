#!/bin/bash
set -e

if [ -z "$JEXTRACT_HOME" ]; then
  echo "❌ 錯誤：JEXTRACT_HOME 未設定"
  echo "請先執行：export JEXTRACT_HOME=/opt/jextract-25"
  exit 1
fi

echo "🔧 生成 Java bindings..."

HEADER_PATH=native/fubon-sdk-ffi/c_wrapper/include/fubon_c.h
OUTPUT_DIR=native/generated

# 清理舊檔案
rm -rf $OUTPUT_DIR
mkdir -p $OUTPUT_DIR

# 執行 jextract
$JEXTRACT_HOME/bin/jextract \
  --output $OUTPUT_DIR \
  -t com.fubon.ffi \
  -l fubon_c \
  --use-system-load-library \
  $HEADER_PATH

echo "✓ Java bindings 生成完成"
find $OUTPUT_DIR -name "*.java" | head -5
