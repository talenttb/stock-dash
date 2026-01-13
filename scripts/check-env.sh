#!/bin/bash
# 檢查 Panama FFI 整合所需的環境

echo "🔍 檢查環境需求..."
echo ""

# 檢查 Java 版本
echo "1. 檢查 Java 版本"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    java -version 2>&1 | head -3
    if [ "$JAVA_VERSION" -ge 25 ]; then
        echo "✓ Java 版本正確 (>= 25)"
    else
        echo "✗ Java 版本過低，需要 Java 25"
        exit 1
    fi
else
    echo "✗ 找不到 Java"
    exit 1
fi
echo ""

# 檢查 jextract
echo "2. 檢查 jextract"
if [ -n "$JEXTRACT_HOME" ]; then
    echo "JEXTRACT_HOME=$JEXTRACT_HOME"
    if [ -f "$JEXTRACT_HOME/bin/jextract" ]; then
        $JEXTRACT_HOME/bin/jextract --version 2>&1 | head -2
        echo "✓ jextract 可用"
    else
        echo "✗ 找不到 $JEXTRACT_HOME/bin/jextract"
        exit 1
    fi
else
    echo "✗ JEXTRACT_HOME 環境變數未設定"
    echo "請執行：export JEXTRACT_HOME=/opt/jextract-25"
    exit 1
fi
echo ""

# 檢查 CMake
echo "3. 檢查 CMake"
if command -v cmake &> /dev/null; then
    cmake --version | head -1
    echo "✓ CMake 可用"
else
    echo "✗ 找不到 CMake"
    exit 1
fi
echo ""

# 檢查 C++ 編譯器
echo "4. 檢查 C++ 編譯器"
if command -v c++ &> /dev/null; then
    c++ --version | head -1
    echo "✓ C++ 編譯器可用"
else
    echo "✗ 找不到 C++ 編譯器"
    exit 1
fi
echo ""

# 檢查 Clojure CLI
echo "5. 檢查 Clojure CLI"
if command -v clj &> /dev/null; then
    clj --version 2>&1
    echo "✓ Clojure CLI 可用"
else
    echo "✗ 找不到 Clojure CLI"
    exit 1
fi
echo ""

# 檢查 git submodule
echo "6. 檢查 git submodule"
if [ -d "native/fubon-sdk-ffi/.git" ]; then
    echo "✓ fubon-sdk-ffi submodule 已初始化"
else
    echo "⚠ fubon-sdk-ffi submodule 未初始化"
    echo "請執行：git submodule update --init --recursive"
fi
echo ""

# 檢查 native libraries
echo "7. 檢查 native libraries"
if [ -f "native/lib/libfubon_c.dylib" ] && [ -f "native/lib/libfubon.dylib" ]; then
    echo "✓ Native libraries 已編譯"
    ls -lh native/lib/*.dylib
else
    echo "⚠ Native libraries 未編譯"
    echo "請執行：bash scripts/build-native.sh"
fi
echo ""

# 檢查 Java bindings
echo "8. 檢查 Java bindings"
if [ -d "native/generated/com/fubon/ffi" ]; then
    FILE_COUNT=$(find native/generated/com/fubon/ffi -name "*.java" | wc -l)
    echo "✓ Java bindings 已生成 ($FILE_COUNT 個檔案)"
else
    echo "⚠ Java bindings 未生成"
    echo "請執行：bash scripts/generate-bindings.sh"
fi
echo ""

# 檢查編譯的 class 檔案
echo "9. 檢查編譯的 class 檔案"
if [ -d "target/classes/com/fubon/ffi" ]; then
    CLASS_COUNT=$(find target/classes/com/fubon/ffi -name "*.class" | wc -l)
    echo "✓ Java classes 已編譯 ($CLASS_COUNT 個檔案)"
else
    echo "⚠ Java classes 未編譯"
    echo "請執行：clj -T:build compile-java"
fi
echo ""

echo "─────────────────────────────────────"
echo "環境檢查完成！"
echo ""
echo "如果看到 ⚠ 警告，請依照提示執行對應的命令。"
