# Native Libraries

此目錄包含編譯後的動態函式庫，用於 Panama FFI 整合。

## 檔案說明

- `libfubon_c.dylib` - C wrapper library (由 `scripts/build-native.sh` 編譯)
- `libfubon.dylib` - Fubon C++ SDK (從 submodule 複製)

## 建置

執行以下命令編譯 native libraries：

```bash
bash scripts/build-native.sh
```

建置腳本會：
1. 使用 CMake 編譯 C wrapper
2. 複製 `libfubon_c.dylib` 到此目錄
3. 複製 `libfubon.dylib` 到此目錄
4. 自動修正 dylib 依賴路徑

## 依賴關係

```
libfubon_c.dylib
  └─> @loader_path/libfubon.dylib  (相對路徑)
```

使用 `@loader_path` 確保 `libfubon_c.dylib` 能正確找到同目錄的 `libfubon.dylib`。

## 檢查依賴

```bash
# 檢查 libfubon_c.dylib 的依賴
otool -L native/lib/libfubon_c.dylib

# 應該看到：
#   @loader_path/libfubon.dylib
```

## 注意事項

- 這些檔案會被 `.gitignore` 忽略（因為是編譯產物）
- 建構 uberjar 時會自動包含這些檔案
- 執行應用程式時需要設定 `java.library.path` 指向此目錄
