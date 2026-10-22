# 架构

## 分层

```text
UI（MainActivity）
  ├── PreviewView / ImageView
  ├── 模式、阈值、进度、按钮
  └── 会话历史、保存

编排
  ├── CameraX 拍照
  ├── captureExecutor（解码/旋转）
  ├── inferExecutor（TFLite）
  └── 结果历史

基础设施
  ├── AppConfig
  ├── AppLogger
  ├── MetricsLogger
  ├── ModelInfo
  └── CrashHandler
```

## 数据流

```text
CameraX ImageCapture
  → JPEG（ImageProxy）
  → 读 bytes + close
  → BitmapFactory 解码
  → 旋转校正
  → 单 ROI / 整图切窗
  → TFLite FP16
  → logits [1,1024,1024,2]
  → sigmoid(z0-z1)
  → 阈值 / 概率累加
  → 掩膜叠加
  → 会话历史 / PNG
```

## 两种模式

### 单 ROI

```text
中心裁剪 1024×1024 原生像素
  → 一次推理
  → 1024² 概率图
```

### 整图滑窗

```text
1024 窗口 / 512 步长
  → N 个 tile
  → 概率累加 + 重叠计数
  → 逐像素平均
  → 整图掩膜
```

## 线程

| 线程 | 职责 |
|---|---|
| 主线程 | UI、CameraX 绑定、按钮、阈值 |
| captureExecutor | 拍照回调、JPEG 解码、旋转 |
| inferExecutor | 单 tile / 滑窗推理 |
| TFLite 内部线程 | 4 个 CPU 线程 |

规则：

```text
Interpreter 非线程安全 → 推理串行
View 只能主线程改 → runOnUiThread
ImageProxy 必须 close
大 Bitmap 及时 recycle
```

## 生命周期

```text
onCreate
  ├── AppLogger.init
  ├── CrashHandler.install
  ├── 加载 AppConfig
  ├── 加载模型 + ModelInfo
  ├── 初始化 CameraX
  └── 申请相机权限

onDestroy
  ├── shutdown executors
  ├── close interpreter
  └── close metrics logger
```

CameraX 通过 `bindToLifecycle` 自动管理相机开关。

## 状态机

```text
IDLE
  │ 拍照
  ▼
CAPTURING
  │ 解码 + 入队
  ▼
IDLE ──► QUEUED ──► INFERRING ──► RESULT
  ▲                                  │
  └──────────── 返回取景 ────────────┘
```

## 内存

- 输入 `ByteBuffer.allocateDirect(1024*1024*3*4)` ≈ 12 MB
- 输出 `ByteBuffer.allocateDirect(1024*1024*2*4)` ≈ 8 MB
- tile Bitmap 1024² ARGB ≈ 4 MB
- 整图概率图 4000×3000 float ≈ 48 MB
- 重叠计数 byte ≈ 12 MB
- `largeHeap=true`

## 可观测性

```text
files/logs/app.log        运行日志
files/logs/crash_*.txt    崩溃日志
files/metrics.csv         每次检测的指标
files/results/*.png       保存的结果
```

模型 SHA256 在启动时后台计算，显示在界面底部。
