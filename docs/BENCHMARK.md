# 基准测试

## metrics.csv 字段

```text
time              时间
device            厂商 + 型号
android           Android 版本
abi               arm64-v8a / armeabi-v7a / ...
model             模型资产名
model_sha8        模型 SHA256 前 8 位
format            FP16 / FP32 / INT8
mode              ROI / FULL
w, h              拍摄图像尺寸
tiles             窗口数（ROI=1）
decode_ms         JPEG 解码 + 旋转
infer_ms          推理（含输入填充）
post_ms           后处理 + 掩膜
total_ms          decode + detect
crack_pct         裂缝像素占比
threshold         阈值
java_heap_mb      Java 堆
native_heap_mb    native 堆
avail_mem_mb      系统可用内存
```

## 采集

1. 安装 APK；
2. 拍 3 张单 ROI；
3. 拍 1 张整图滑窗；
4. 拉取：

```bash
adb pull /sdcard/Android/data/com.gzt.crackedge/files/metrics.csv
```

## 示例汇总

在论文/报告里建议报告：

```text
单 ROI：推理均值 / P50 / P95 / 总时延
整图：tile 数 / 推理总时延 / 总时延
内存：java / native / avail
设备：型号、SoC、Android 版本
模型：asset、sha8、格式
```

## 注意

- 不同 SoC、系统版本、拍照分辨率差异很大；
- 整图模式耗时随图像面积增长；
- 不要在充电/高温状态下只测一次，需记录是否降频；
- 结果只对当前模型和当前设备有效。
