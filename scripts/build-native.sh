#!/bin/bash
set -e

echo "🔨 編譯 C wrapper..."

# 進入 C wrapper 目錄
cd native/fubon-sdk-ffi/c_wrapper
rm -rf build
mkdir build
cd build

# 使用 CMake 編譯
cmake ..
make

echo "📦 複製 libraries 到 native/lib..."
cd ../../../..  # 回到 stock-dash 根目錄

# 複製產物
cp native/fubon-sdk-ffi/c_wrapper/build/libfubon_c.dylib native/lib/
cp native/fubon-sdk-ffi/fubon-cpp-sdk/bindings/libfubon.dylib native/lib/

echo "🔧 修正 dylib 依賴路徑..."
# 修正 libfubon_c.dylib 對 libfubon.dylib 的依賴路徑
install_name_tool -change \
  "/builds/fugle/brokerage-service-development/fubon-securities/sdk-core/uniffi_wraper/target/aarch64-apple-darwin/release/deps/libfubon.dylib" \
  "@loader_path/libfubon.dylib" \
  native/lib/libfubon_c.dylib

echo "✓ Native library 編譯完成"
ls -lh native/lib/
