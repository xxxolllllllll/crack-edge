# 模型卡片

## 文件

```text
android/app/src/main/assets/yolo11n_fold0_1024_fp16.tflite
```

| 项目 | 内容 |
|---|---|
| 大小 | 约 10 MB（10,188 KB） |
| 结构 | YOLOv11n-U-Net 语义分割学生 |
| 参数量 | 约 5.18 M |
| 输入 | `[1, 1024, 1024, 3]` float32，NHWC，0–1 |
| 输出 | `[1, 1024, 1024, 2]` float32 logits |
| 格式 | FP16 权重，float32 计算 |
| 训练输入 | 1024×1024 patch，stride 800 |
| 推理窗口 | 1024×1024，stride 512 |

SHA256：

```text
0f7f98942bd08aab5d7ea6778c1a3ce78d21da996124afb922e6c4b522a32275
```

见 [SHA256SUMS](SHA256SUMS)。

## 来源

该检查点属于论文中的 YOLOv11n-U-Net 蒸馏学生（阶段 B 适配教师 + 特征蒸馏 + 裂缝区域注意力蒸馏，λ_feat=1.0、λ_attn=0.2）的 **fold-0** 版本。训练时使用 fold-1～fold-4，fold-0 仅作为折外测试集。

训练数据：

- 自建古建筑木构件裂缝数据集：718 张，crack/component/ignore 三值标注；
- 公开 Bridge Crack Dataset：10677 张无标注图像，仅用于阶段 A 自监督续训。

数据与论文的对应关系见大论文第二章。

## 精度

fold-0 折外测试集（1024 窗口、512 步长、阈值 0.5）：

```text
mIoU      0.6138
F1        0.7607
Precision 0.7233
Recall    0.8020
```

5 折交叉验证：

```text
YOLOv11n: 0.6047 ± 0.0105
YOLOv11m: 0.6145 ± 0.0119
S0 (m):   0.5429 ± 0.0180
```

n 学生与 m 学生的 5 折差异不显著（p=0.192），但参数量减少 76.8%。该模型不是 SOTA：CrackMamba（0.6835）与 SCSegamba（0.6572）在 fold-0 上更高，本仓库定位为可复现的轻量蒸馏基线。

## 转换链路

```text
PyTorch best.pt (fold-0, YOLOv11n-U-Net)
  → ONNX 1024×1024, opset 17
  → onnx2tf
  → TFLite FP16
  → app/src/main/assets/yolo11n_fold0_1024_fp16.tflite
```

参考命令（训练仓库路径需按实际修改）：

```bash
python3 deploy/export_onnx.py \
  --ckpt runs/kfold/fold0/yolov11nKD/yolo11n_KD/best.pt \
  --out deploy/models/yolo11n_fold0_1024.onnx \
  --imgsz 1024 --check

onnx2tf -i deploy/models/yolo11n_fold0_1024.onnx \
  -o deploy/tflite/yolo11n_fold0_1024 -osd -coion
```

输出文件为 `yolo11n_fold0_1024_float16.tflite`。

FP16 转换一致性抽检（同一随机输入）：

```text
ONNX vs TFLite max_abs = 1.71e-3
argmax agreement      = 100.000%
sigmoid probability Δ = 9.2e-6
```

## 许可

- 模型权重仅用于研究与评估；
- Bridge Crack Dataset 为 CC BY 4.0；
- 自建数据集的使用请联系作者；
- 代码为 Apache-2.0。
