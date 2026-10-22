# 开发与调试

## 环境

```text
JDK 17
Android SDK 34
Android 7.0（API 24）及以上
```

`android/local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 构建

```bash
cd android
./gradlew assembleDebug
```

APK：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 安装

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## 日志

```bash
adb logcat -s CrackEdge
```

文件日志：

```text
/sdcard/Android/data/com.gzt.crackedge/files/logs/app.log
```

拉取：

```bash
adb pull /sdcard/Android/data/com.gzt.crackedge/files/logs/app.log
```

## 指标

```text
/sdcard/Android/data/com.gzt.crackedge/files/metrics.csv
```

字段见 [BENCHMARK.md](BENCHMARK.md)。

## 崩溃

```text
/sdcard/Android/data/com.gzt.crackedge/files/logs/crash_*.txt
```

包含时间、线程、设备、Android 版本和堆栈。

## 常见问题

### 相机黑屏

- 检查 CAMERA 权限；
- 检查是否有其他 App 占用相机；
- 查看 `adb logcat -s CrackEdge` 的 `camera start failed`。

### 拍照后一直“正在拍摄”

- 查看日志中的 `capture decode failed` / `capture error`；
- 确认 `ImageProxy.close()` 被调用；
- 确认 `captureExecutor` 没有被长时间任务占满。

### 检测报形状错误

确认模型输入是 `[1,1024,1024,3]`，输出是 `[1,1024,1024,2]`，并且输出使用 direct `ByteBuffer`，不要用一维 `float[]`。

### 整图模式内存不足

- 使用单 ROI；
- 降低拍照分辨率；
- 检查 `largeHeap` 是否生效；
- 查看 `metrics.csv` 的 `native_heap_mb` / `avail_mem_mb`。

## 发布

```bash
./gradlew assembleDebug
```

CI 使用 `.github/workflows/android.yml` 自动构建 debug APK。

正式发布需要：

```text
release keystore
signingConfig
versionCode/versionName
CHANGELOG
GitHub Release
```

keystore 不要提交到仓库，使用 GitHub Secrets。
