# 模型卡片

## 文件

```text
android/app/src/main/assets/best_fold1_1024_fp16.tflite
```

| 项目 | 内容 |
|---|---|
| 大小 | 约 43 MB |
| 结构 | YOLOv11-U-Net 语义分割学生 |
| 参数量 | 约 22.35 M |
| 输入 | `[1, 1024, 1024, 3]` float32，NHWC，0–1 |
| 输出 | `[1, 1024, 1024, 2]` float32 logits |
| 格式 | FP16 权重，float32 计算 |
| 训练输入 | 1024×1024 patch，stride 800 |
| 推理窗口 | 1024×1024，stride 512 |

SHA256：

```text
a53dfb7147c4851085a837ee929f97ddfaa4f172bbb013a151d86b4679cede57
```

见 [SHA256SUMS](SHA256SUMS)。

## 来源

该检查点属于论文中的 S3 学生模型（阶段 B 适配教师 + 特征蒸馏 + 注意力蒸馏，λ_attn=0.2）的 fold-1 版本。它是研究原型权重，不等同于论文 5 折实验中的均值模型；使用前请核对论文与实际检查点。

训练数据：

- 自建古建筑木构件裂缝数据集：718 张，crack/component/ignore 三值标注；
- 公开 Bridge Crack Dataset：10677 张无标注图像，仅用于阶段 A 自监督续训。

数据与论文的对应关系见大论文第二章。

## 转换链路

```text
PyTorch best.pt
  → ONNX 1024×1024, opset 17
  → onnxsim
  → onnx2tf
  → TFLite FP16
  → app/src/main/assets/best_fold1_1024_fp16.tflite
```

参考命令（训练仓库路径需按实际修改）：

```bash
python export_onnx.py \
  --ckpt best.pt \
  --out best_fold1_1024.onnx \
  --imgsz 1024 --check

onnxsim best_fold1_1024.onnx best_fold1_1024_sim.onnx
onnx2tf -i best_fold1_1024_sim.onnx -o tflite_out -osd -coion
python make_tflite_int8.py \
  --saved-model tflite_out \
  --calib-npy calib_1024.npy \
  --out best_fold1_1024_fp16.tflite
```

## 精度说明

本仓库不声称该检查点是 SOTA。论文中的 5 折蒸馏学生结果为：

```text
S0:   0.5429 ± 0.0180
Ours: 0.6145 ± 0.0119
```

但仓库内是 fold-1 检查点，干净评测应使用 fold-1 折外测试集，不能用与训练集重叠的图像评估。具体数字以论文和评测脚本为准。

## 许可

- 模型权重仅用于研究与评估；
- Bridge Crack Dataset 为 CC BY 4.0；
- 自建数据集的使用请联系作者；
- 代码为 Apache-2.0。
