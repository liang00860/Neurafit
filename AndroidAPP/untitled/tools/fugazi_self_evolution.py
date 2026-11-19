import csv
import math
import sqlite3
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATASET_DB = ROOT / "datasets" / "fugazi" / "bodytrip.db"
OUT_CSV = ROOT / "datasets" / "fugazi" / "neurafit_features.csv"
OUT_REPORT = ROOT / "reports" / "self_evolution_report.md"


def angle(first, center, third):
    ax, ay = first
    bx, by = center
    cx, cy = third
    v1 = (ax - bx, ay - by)
    v2 = (cx - bx, cy - by)
    n1 = math.hypot(*v1)
    n2 = math.hypot(*v2)
    if n1 == 0 or n2 == 0:
        return None
    value = (v1[0] * v2[0] + v1[1] * v2[1]) / (n1 * n2)
    value = max(-1.0, min(1.0, value))
    return math.degrees(math.acos(value))


def distance(first, second):
    return math.hypot(first[0] - second[0], first[1] - second[1])


def torso_tilt(shoulder_center, hip_center):
    dx = shoulder_center[0] - hip_center[0]
    dy = shoulder_center[1] - hip_center[1]
    return math.degrees(math.atan2(abs(dx), abs(dy)))


def load_squat_samples():
    connection = sqlite3.connect(DATASET_DB)
    cursor = connection.cursor()
    exercise_ids = [
        row[0]
        for row in cursor.execute("select id from exercise where name = 'Squats'").fetchall()
    ]
    samples = []

    for exercise_id in exercise_ids:
        rows = cursor.execute(
            "select id, percentage from percentage where exercise_id = ?",
            (exercise_id,),
        ).fetchall()
        for percentage_id, percentage in rows:
            landmarks = {
                landmark_id: (x, y)
                for landmark_id, x, y in cursor.execute(
                    "select landmark_id, x, y from landmark where percentage_id = ?",
                    (percentage_id,),
                ).fetchall()
            }
            required = [11, 12, 23, 24, 25, 26, 27, 28]
            if not all(index in landmarks for index in required):
                continue

            left_knee = angle(landmarks[23], landmarks[25], landmarks[27])
            right_knee = angle(landmarks[24], landmarks[26], landmarks[28])
            if left_knee is None or right_knee is None:
                continue

            shoulder_center = (
                (landmarks[11][0] + landmarks[12][0]) / 2,
                (landmarks[11][1] + landmarks[12][1]) / 2,
            )
            hip_center = (
                (landmarks[23][0] + landmarks[24][0]) / 2,
                (landmarks[23][1] + landmarks[24][1]) / 2,
            )
            knee_distance = distance(landmarks[25], landmarks[26])
            tilt = torso_tilt(shoulder_center, hip_center)
            avg_knee = (left_knee + right_knee) / 2

            # FUGAZI labels ROM percentage, not form errors. We convert it into a weak
            # supervised depth label: bottom frames are correct depth; mid-depth frames
            # are treated as "too shallow" samples for threshold calibration.
            if percentage >= 0.90 and avg_knee <= 135:
                expected_error = "NONE"
            elif 0.55 <= percentage <= 0.82 and avg_knee >= 118:
                expected_error = "SQUAT_TOO_SHALLOW"
            else:
                continue

            samples.append(
                {
                    "sampleId": f"fugazi-squat-{percentage_id}",
                    "actionName": "SQUAT",
                    "expectedCount": "1",
                    "expectedErrors": expected_error,
                    "leftKneeAngle": f"{left_knee:.4f}",
                    "rightKneeAngle": f"{right_knee:.4f}",
                    "torsoTiltAngle": f"{tilt:.4f}",
                    "leftRightKneeDistance": f"{knee_distance:.4f}",
                    "leftElbowAngle": "",
                    "rightElbowAngle": "",
                    "bodyLineAngle": "",
                    "bodyDescent": "",
                    "hipOffsetFromBodyLine": "",
                    "repDurationMs": "",
                }
            )

    connection.close()
    return samples


def predict_squat(sample, threshold):
    avg_knee = (
        float(sample["leftKneeAngle"]) + float(sample["rightKneeAngle"])
    ) / 2
    torso = float(sample["torsoTiltAngle"])
    knee_distance = float(sample["leftRightKneeDistance"])
    errors = []
    if avg_knee > threshold["maxKneeAngleAtBottom"]:
        errors.append("SQUAT_TOO_SHALLOW")
    if torso > threshold["maxBackLeanAngle"]:
        errors.append("BACK_LEAN_TOO_MUCH")
    if knee_distance < threshold["minKneeDistance"]:
        errors.append("KNEE_VALGUS")
    return errors or ["NONE"]


def evaluate(samples, threshold):
    exact = 0
    counters = defaultdict(lambda: {"tp": 0, "fp": 0, "fn": 0})
    rows = []
    for sample in samples:
        expected = set(sample["expectedErrors"].split("|"))
        predicted = set(predict_squat(sample, threshold))
        if expected == predicted:
            exact += 1
        for error in (expected | predicted) - {"NONE"}:
            if error in expected and error in predicted:
                counters[error]["tp"] += 1
            elif error not in expected and error in predicted:
                counters[error]["fp"] += 1
            elif error in expected and error not in predicted:
                counters[error]["fn"] += 1
        rows.append((sample["sampleId"], expected, predicted))
    return {
        "accuracy": exact / max(1, len(samples)),
        "exact": exact,
        "total": len(samples),
        "metrics": counters,
        "rows": rows,
    }


def optimize(samples):
    baseline = {
        "maxKneeAngleAtBottom": 110.0,
        "maxBackLeanAngle": 35.0,
        "minKneeDistance": 0.08,
    }
    best = baseline
    best_eval = evaluate(samples, baseline)
    for knee in range(100, 131, 2):
        # FUGAZI stores normalized landmark coordinates from a different capture
        # setup than the phone preview. We therefore let the offline report search
        # a wider back-angle range and treat the result as dataset calibration,
        # not as an automatic production update.
        for back in range(30, 91, 5):
            for dist_step in range(2, 13):
                threshold = {
                    "maxKneeAngleAtBottom": float(knee),
                    "maxBackLeanAngle": float(back),
                    "minKneeDistance": dist_step / 100,
                }
                result = evaluate(samples, threshold)
                if result["accuracy"] > best_eval["accuracy"]:
                    best = threshold
                    best_eval = result
    return baseline, evaluate(samples, baseline), best, best_eval


def write_csv(samples):
    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    header = [
        "sampleId",
        "actionName",
        "expectedCount",
        "expectedErrors",
        "leftKneeAngle",
        "rightKneeAngle",
        "torsoTiltAngle",
        "leftRightKneeDistance",
        "leftElbowAngle",
        "rightElbowAngle",
        "bodyLineAngle",
        "bodyDescent",
        "hipOffsetFromBodyLine",
        "repDurationMs",
    ]
    with OUT_CSV.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=header)
        writer.writeheader()
        writer.writerows(samples)


def metric_line(name, metric):
    tp = metric["tp"]
    fp = metric["fp"]
    fn = metric["fn"]
    precision = tp / max(1, tp + fp)
    recall = tp / max(1, tp + fn)
    f1 = 0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
    return f"| {name} | {precision:.3f} | {recall:.3f} | {f1:.3f} |"


def write_report(samples, baseline, baseline_eval, best, best_eval):
    OUT_REPORT.parent.mkdir(parents=True, exist_ok=True)
    mismatches = [
        row for row in best_eval["rows"] if row[1] != row[2]
    ][:20]
    lines = [
        "# NeuraFit 外部数据集自我评估报告",
        "",
        "## 数据来源",
        "",
        "- 数据集：FUGAZI GitHub repository 的 MediaPipe landmark 数据库",
        "- 用途：动作与 ROM 标注数据，用于规则阈值弱监督校准",
        "- 注意：该数据集没有专家纠错标签；本报告使用 ROM percentage 构造深蹲深度弱标签，不作为最终医学或训练结论。",
        "",
        "## 样本转换",
        "",
        f"- 转换后样本数：{len(samples)}",
        f"- 输出 CSV：`{OUT_CSV.relative_to(ROOT)}`",
        "- 标签规则：ROM 接近底部且膝角较小时标为 `NONE`；中等 ROM 且膝角较大时标为 `SQUAT_TOO_SHALLOW`。",
        "",
        "## 阈值搜索结果",
        "",
        f"- 基线准确率：{baseline_eval['accuracy']:.3f} ({baseline_eval['exact']}/{baseline_eval['total']})",
        f"- 优化后准确率：{best_eval['accuracy']:.3f} ({best_eval['exact']}/{best_eval['total']})",
        "",
        "### 基线阈值",
        "",
        f"- maxKneeAngleAtBottom: {baseline['maxKneeAngleAtBottom']}",
        f"- maxBackLeanAngle: {baseline['maxBackLeanAngle']}",
        f"- minKneeDistance: {baseline['minKneeDistance']}",
        "",
        "### 推荐阈值",
        "",
        f"- maxKneeAngleAtBottom: {best['maxKneeAngleAtBottom']}",
        f"- maxBackLeanAngle: {best['maxBackLeanAngle']}",
        f"- minKneeDistance: {best['minKneeDistance']}",
        "",
        "## 错误类型指标",
        "",
        "| 错误类型 | Precision | Recall | F1 |",
        "|---|---:|---:|---:|",
    ]
    for name, metric in sorted(best_eval["metrics"].items()):
        lines.append(metric_line(name, metric))
    lines += [
        "",
        "## 误判样本示例",
        "",
        "| 样本 ID | 标注 | 预测 |",
        "|---|---|---|",
    ]
    if not mismatches:
        lines.append("| 无 | 无 | 无 |")
    else:
        for sample_id, expected, predicted in mismatches:
            lines.append(
                f"| {sample_id} | {'|'.join(sorted(expected))} | {'|'.join(sorted(predicted))} |"
            )
    OUT_REPORT.write_text("\n".join(lines), encoding="utf-8")


def main():
    samples = load_squat_samples()
    write_csv(samples)
    baseline, baseline_eval, best, best_eval = optimize(samples)
    write_report(samples, baseline, baseline_eval, best, best_eval)
    print(f"samples={len(samples)}")
    print(f"csv={OUT_CSV}")
    print(f"report={OUT_REPORT}")
    print(f"baseline_accuracy={baseline_eval['accuracy']:.3f}")
    print(f"optimized_accuracy={best_eval['accuracy']:.3f}")
    print(f"recommended={best}")


if __name__ == "__main__":
    main()
