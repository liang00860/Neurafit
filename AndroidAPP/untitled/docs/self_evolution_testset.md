# NeuraFit 测试集自我优化说明

本模块用于本科毕设实验中的“自学习闭环”验证。系统不会直接从互联网随机抓取视频，也不会在线训练 MediaPipe 模型，而是使用受控测试集完成规则库评估与阈值优化。

## 推荐流程

1. 收集公开许可或自采的深蹲、俯卧撑视频。
2. 使用现有 App 或脚本提取 MediaPipe 关键点和关节角度。
3. 人工标注每个样本的真实错误类型。
4. 生成 `sample_motion_features.csv` 同格式的特征级测试集。
5. 调用 `SelfEvolutionRunner.runFromCsv(csvText)` 批量评估。
6. 查看生成的 Markdown 报告，记录准确率、各错误类型 Precision/Recall/F1 和推荐阈值。
7. 通过回归测试后，再将推荐阈值写入默认阈值或个性化阈值表。

## CSV 字段

| 字段 | 说明 |
|---|---|
| sampleId | 样本编号 |
| actionName | `SQUAT` 或 `PUSH_UP` |
| expectedCount | 人工标注次数，可选 |
| expectedErrors | 人工标注错误，多个错误用 `|` 分隔 |
| leftKneeAngle/rightKneeAngle | 深蹲膝关节角度 |
| torsoTiltAngle | 深蹲背部前倾角 |
| leftRightKneeDistance | 深蹲左右膝距离 |
| leftElbowAngle/rightElbowAngle | 俯卧撑肘关节角度 |
| bodyLineAngle | 肩-髋-踝身体直线程度 |
| bodyDescent | 俯卧撑身体下降幅度 |
| hipOffsetFromBodyLine | 髋部相对身体直线偏移，正值表示下塌，负值表示过高 |
| repDurationMs | 单次动作耗时，低于阈值视为动作过快 |

## 示例文件

示例测试集位于：

`app/src/main/assets/evaluation/sample_motion_features.csv`

## 已接入的外部数据集

### FUGAZI

- 来源：`https://github.com/aris-konstantinidis/fugazi`
- 本地文件：
  - `datasets/fugazi/bodytrip.db`
  - `datasets/fugazi/test-data.csv`
  - `datasets/fugazi/neurafit_features.csv`
- 说明：该数据集提供 MediaPipe landmark、动作标签和 ROM percentage。它没有专家纠错标签，因此当前转换脚本将 ROM percentage 映射为深蹲深度弱监督标签，用于验证阈值搜索流程。
- 执行脚本：`python tools/fugazi_self_evolution.py`
- 生成报告：`reports/self_evolution_report.md`

### REHAB24-6

- 来源：`https://zenodo.org/records/13305826`
- 本地文件：
  - `datasets/rehab24_6/Segmentation.csv`
  - `datasets/rehab24_6/Segmentation.txt`
  - `datasets/rehab24_6/joints_names.txt`
- 说明：该数据集包含重复段分割、正确性标签、深蹲和俯卧撑动作，许可为 CC BY-NC 4.0，适合毕业设计实验。由于 `2d_joints.zip` 文件较大，当前只下载了标注文件；网络稳定时可继续下载骨架序列并转换为 NeuraFit 特征 CSV。

## 论文表述建议

本文中的“自学习”主要体现在规则阈值的自动评估与优化，而非对底层姿态估计模型进行在线训练。系统基于人工标注测试集对动作纠错规则进行评估，通过网格搜索寻找更合适的阈值组合，并在回归验证通过后更新规则库或用户个性化阈值。该方法具有较好的可解释性和移动端可落地性。
