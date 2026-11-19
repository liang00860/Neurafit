from __future__ import annotations

import csv
import io
import json
import math
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "datasets" / "rehab24_6"
JOINT_ZIP = DATASET_DIR / "3d_joints.zip"
SEGMENTATION_CSV = DATASET_DIR / "Segmentation.csv"
FEATURE_CSV = DATASET_DIR / "neurafit_squat_pushup_features.csv"
REPORT_PATH = ROOT / "reports" / "rehab24_squat_pushup_iteration_report.md"
STATE_PATH = ROOT / "reports" / "rehab24_squat_pushup_iteration_state.json"

EXERCISE_TO_ACTION = {
    3: "PUSH_UP",
    6: "SQUAT",
}

JOINT = {
    "hips": 0,
    "neck": 3,
    "left_shoulder": 6,
    "left_elbow": 7,
    "left_wrist": 8,
    "right_shoulder": 11,
    "right_elbow": 12,
    "right_wrist": 13,
    "left_hip": 16,
    "left_knee": 17,
    "left_ankle": 18,
    "left_toe": 19,
    "right_hip": 21,
    "right_knee": 22,
    "right_ankle": 23,
    "right_toe": 24,
}

BASELINE = {
    "SQUAT": {
        "maxKneeAngleAtBottom": 110.0,
        "maxBackLeanAngle": 35.0,
    },
    "PUSH_UP": {
        "maxElbowAngleAtBottom": 105.0,
        "minBodyLineAngle": 160.0,
        "minDescent": 0.06,
    },
}


@dataclass(frozen=True)
class RehabFeature:
    sample_id: str
    action_name: str
    exercise_id: int
    video_id: str
    person_id: int
    repetition_number: int
    correctness: int
    correct_label: str
    first_frame: int
    last_frame: int
    frame_count: int
    duration_ms: float
    orientation: str
    exercise_subtype: str
    min_avg_knee_angle: float | None
    min_avg_elbow_angle: float | None
    max_back_lean_angle: float | None
    min_body_line_angle: float | None
    max_body_descent: float | None
    min_knee_distance_ratio: float | None
    motion_smoothness: float


def angle(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> np.ndarray:
    v1 = a - b
    v2 = c - b
    n1 = np.linalg.norm(v1, axis=1)
    n2 = np.linalg.norm(v2, axis=1)
    denom = n1 * n2
    result = np.full(a.shape[0], np.nan, dtype=float)
    valid = denom > 1e-8
    dot = np.sum(v1[valid] * v2[valid], axis=1) / denom[valid]
    dot = np.clip(dot, -1.0, 1.0)
    result[valid] = np.degrees(np.arccos(dot))
    return result


def joint(frames: np.ndarray, name: str) -> np.ndarray:
    return frames[:, JOINT[name], :3]


def avg_series(left: np.ndarray, right: np.ndarray) -> np.ndarray:
    return np.nanmean(np.vstack([left, right]), axis=0)


def min_or_none(values: np.ndarray) -> float | None:
    clean = values[np.isfinite(values)]
    return None if clean.size == 0 else float(np.min(clean))


def max_or_none(values: np.ndarray) -> float | None:
    clean = values[np.isfinite(values)]
    return None if clean.size == 0 else float(np.max(clean))


def torso_tilt(frames: np.ndarray) -> np.ndarray:
    vector = joint(frames, "neck") - joint(frames, "hips")
    horizontal = np.linalg.norm(vector[:, [0, 2]], axis=1)
    vertical = np.abs(vector[:, 1])
    return np.degrees(np.arctan2(horizontal, np.maximum(vertical, 1e-8)))


def body_line_angle(frames: np.ndarray) -> np.ndarray:
    shoulder = (joint(frames, "left_shoulder") + joint(frames, "right_shoulder")) / 2.0
    hips = joint(frames, "hips")
    ankle = (joint(frames, "left_ankle") + joint(frames, "right_ankle")) / 2.0
    return angle(shoulder, hips, ankle)


def body_descent(frames: np.ndarray) -> float | None:
    shoulder = (joint(frames, "left_shoulder") + joint(frames, "right_shoulder")) / 2.0
    vertical = shoulder[:, 1]
    clean = vertical[np.isfinite(vertical)]
    if clean.size == 0:
        return None
    return float(np.max(clean) - np.min(clean))


def knee_distance_ratio(frames: np.ndarray) -> np.ndarray:
    knee_distance = np.linalg.norm(joint(frames, "left_knee") - joint(frames, "right_knee"), axis=1)
    hip_distance = np.linalg.norm(joint(frames, "left_hip") - joint(frames, "right_hip"), axis=1)
    return knee_distance / np.maximum(hip_distance, 1e-8)


def smoothness(frames: np.ndarray, action_name: str) -> float:
    if action_name == "SQUAT":
        point = joint(frames, "hips")
    else:
        point = (joint(frames, "left_shoulder") + joint(frames, "right_shoulder")) / 2.0
    if len(point) < 3:
        return 0.0
    return float(np.nanmean(np.linalg.norm(np.diff(np.diff(point, axis=0), axis=0), axis=1)))


def read_segmentation() -> list[dict]:
    rows: list[dict] = []
    with SEGMENTATION_CSV.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file, delimiter=";")
        for row in reader:
            exercise_id = int(row["exercise_id"])
            if exercise_id in EXERCISE_TO_ACTION:
                rows.append(row)
    return rows


def load_joint_file(archive: zipfile.ZipFile, exercise_id: int, video_id: str) -> np.ndarray:
    name = f"Ex{exercise_id}/{video_id}-30fps.npy"
    return np.load(io.BytesIO(archive.read(name)))


def extract_features() -> list[RehabFeature]:
    rows = read_segmentation()
    features: list[RehabFeature] = []
    cache: dict[tuple[int, str], np.ndarray] = {}
    with zipfile.ZipFile(JOINT_ZIP) as archive:
        for row in rows:
            exercise_id = int(row["exercise_id"])
            action_name = EXERCISE_TO_ACTION[exercise_id]
            video_id = row["video_id"]
            key = (exercise_id, video_id)
            if key not in cache:
                cache[key] = load_joint_file(archive, exercise_id, video_id)
            all_frames = cache[key]
            first_frame = int(row["first_frame"])
            last_frame = int(row["last_frame"])
            start = max(0, first_frame)
            end = min(all_frames.shape[0], last_frame + 1)
            frames = all_frames[start:end]
            if len(frames) < 3:
                continue

            left_knee = angle(joint(frames, "left_hip"), joint(frames, "left_knee"), joint(frames, "left_ankle"))
            right_knee = angle(joint(frames, "right_hip"), joint(frames, "right_knee"), joint(frames, "right_ankle"))
            left_elbow = angle(joint(frames, "left_shoulder"), joint(frames, "left_elbow"), joint(frames, "left_wrist"))
            right_elbow = angle(joint(frames, "right_shoulder"), joint(frames, "right_elbow"), joint(frames, "right_wrist"))
            avg_knee = avg_series(left_knee, right_knee)
            avg_elbow = avg_series(left_elbow, right_elbow)

            sample_id = f"{video_id}_rep{row['repetition_number']}_ex{exercise_id}"
            correctness = int(row["correctness"])
            features.append(
                RehabFeature(
                    sample_id=sample_id,
                    action_name=action_name,
                    exercise_id=exercise_id,
                    video_id=video_id,
                    person_id=int(row["person_id"]),
                    repetition_number=int(row["repetition_number"]),
                    correctness=correctness,
                    correct_label="correct" if correctness == 1 else "incorrect",
                    first_frame=first_frame,
                    last_frame=last_frame,
                    frame_count=len(frames),
                    duration_ms=len(frames) / 30.0 * 1000.0,
                    orientation=row["cam17_orientation"],
                    exercise_subtype=row["exercise_subtype"],
                    min_avg_knee_angle=min_or_none(avg_knee),
                    min_avg_elbow_angle=min_or_none(avg_elbow),
                    max_back_lean_angle=max_or_none(torso_tilt(frames)),
                    min_body_line_angle=min_or_none(body_line_angle(frames)),
                    max_body_descent=body_descent(frames),
                    min_knee_distance_ratio=min_or_none(knee_distance_ratio(frames)),
                    motion_smoothness=smoothness(frames, action_name),
                )
            )
    return features


def write_features(features: list[RehabFeature]) -> None:
    FEATURE_CSV.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(RehabFeature.__dataclass_fields__.keys())
    with FEATURE_CSV.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for feature in features:
            writer.writerow({key: serialize(value) for key, value in feature.__dict__.items()})


def serialize(value) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:.6f}"
    return str(value)


def predict(feature: RehabFeature, thresholds: dict) -> int:
    if feature.action_name == "SQUAT":
        too_shallow = (
            feature.min_avg_knee_angle is not None
            and feature.min_avg_knee_angle > thresholds["maxKneeAngleAtBottom"]
        )
        back_lean = (
            feature.max_back_lean_angle is not None
            and feature.max_back_lean_angle > thresholds["maxBackLeanAngle"]
        )
        return 0 if (too_shallow or back_lean) else 1

    too_shallow = (
        feature.min_avg_elbow_angle is not None
        and feature.min_avg_elbow_angle > thresholds["maxElbowAngleAtBottom"]
    )
    bad_line = (
        feature.min_body_line_angle is not None
        and feature.min_body_line_angle < thresholds["minBodyLineAngle"]
    )
    low_descent = (
        feature.max_body_descent is not None
        and feature.max_body_descent < thresholds["minDescent"]
    )
    return 0 if (too_shallow or bad_line or low_descent) else 1


def evaluate(features: list[RehabFeature], thresholds: dict) -> dict:
    exact = 0
    confusion = {
        "correct": {"correct": 0, "incorrect": 0},
        "incorrect": {"correct": 0, "incorrect": 0},
    }
    for feature in features:
        expected = feature.correctness
        prediction = predict(feature, thresholds)
        if prediction == expected:
            exact += 1
        expected_key = "correct" if expected == 1 else "incorrect"
        predicted_key = "correct" if prediction == 1 else "incorrect"
        confusion[expected_key][predicted_key] += 1
    return {
        "accuracy": exact / max(1, len(features)),
        "exact": exact,
        "total": len(features),
        "confusion": confusion,
    }


def balanced_score(result: dict) -> float:
    confusion = result["confusion"]
    correct_recall = confusion["correct"]["correct"] / max(1, sum(confusion["correct"].values()))
    incorrect_recall = confusion["incorrect"]["incorrect"] / max(1, sum(confusion["incorrect"].values()))
    return (correct_recall + incorrect_recall) / 2.0


def search_best_thresholds(action_features: list[RehabFeature]) -> dict:
    action_name = action_features[0].action_name
    best = dict(BASELINE[action_name])
    best_train_eval = evaluate(action_features, best)

    if action_name == "SQUAT":
        for knee in range(85, 141, 2):
            for back in range(25, 61, 2):
                candidate = {
                    "maxKneeAngleAtBottom": float(knee),
                    "maxBackLeanAngle": float(back),
                }
                result = evaluate(action_features, candidate)
                if balanced_score(result) > balanced_score(best_train_eval):
                    best = candidate
                    best_train_eval = result
    else:
        for elbow in range(75, 136, 3):
            for body_line in range(130, 176, 3):
                for descent_step in range(2, 26, 2):
                    candidate = {
                        "maxElbowAngleAtBottom": float(elbow),
                        "minBodyLineAngle": float(body_line),
                        "minDescent": descent_step / 100.0,
                    }
                    result = evaluate(action_features, candidate)
                    if balanced_score(result) > balanced_score(best_train_eval):
                        best = candidate
                        best_train_eval = result

    return best


def empty_eval() -> dict:
    return {
        "accuracy": 0.0,
        "exact": 0,
        "total": 0,
        "confusion": {
            "correct": {"correct": 0, "incorrect": 0},
            "incorrect": {"correct": 0, "incorrect": 0},
        },
    }


def merge_eval(target: dict, source: dict) -> None:
    target["exact"] += source["exact"]
    target["total"] += source["total"]
    for expected in ("correct", "incorrect"):
        for predicted in ("correct", "incorrect"):
            target["confusion"][expected][predicted] += source["confusion"][expected][predicted]


def finalize_eval(result: dict) -> dict:
    result["accuracy"] = result["exact"] / max(1, result["total"])
    return result


def cross_validate(action_features: list[RehabFeature]) -> tuple[dict, dict, dict, dict, list[dict]]:
    action_name = action_features[0].action_name
    baseline = BASELINE[action_name]
    baseline_cv = empty_eval()
    candidate_cv = empty_eval()
    fold_thresholds: list[dict] = []

    for person_id in sorted({feature.person_id for feature in action_features}):
        train = [feature for feature in action_features if feature.person_id != person_id]
        test = [feature for feature in action_features if feature.person_id == person_id]
        if not train or not test:
            continue
        candidate = search_best_thresholds(train)
        fold_thresholds.append({"personId": person_id, "thresholds": candidate})
        merge_eval(baseline_cv, evaluate(test, baseline))
        merge_eval(candidate_cv, evaluate(test, candidate))

    final_candidate = search_best_thresholds(action_features)
    return baseline, finalize_eval(baseline_cv), final_candidate, finalize_eval(candidate_cv), fold_thresholds


def stats(features: list[RehabFeature]) -> dict:
    by_action = Counter(feature.action_name for feature in features)
    by_label = Counter(feature.correct_label for feature in features)
    by_action_label: dict[str, dict[str, int]] = defaultdict(lambda: {"correct": 0, "incorrect": 0})
    for feature in features:
        by_action_label[feature.action_name][feature.correct_label] += 1
    return {
        "sampleCount": len(features),
        "byAction": dict(by_action),
        "byCorrectLabel": dict(by_label),
        "byActionLabel": dict(by_action_label),
    }


def write_state(features: list[RehabFeature], results: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    state = {
        "source": "REHAB24-6 / Zenodo 13305826",
        "featureCsv": str(FEATURE_CSV.relative_to(ROOT)),
        "stats": stats(features),
        "results": results,
        "acceptedForProduction": False,
        "reason": "Binary correctness labels are suitable for offline iteration, but error-type labels are not available; threshold changes should be manually reviewed.",
    }
    STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def write_report(features: list[RehabFeature], results: dict) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    dataset_stats = stats(features)
    lines = [
        "# NeuraFit REHAB24-6 深蹲/俯卧撑迭代报告",
        "",
        "## 数据集结论",
        "",
        "- 数据集：REHAB24-6: A multi-modal dataset of physical rehabilitation exercises",
        "- 动作覆盖：Ex3 = Push-ups，Ex6 = Squats",
        "- 标签覆盖：repetition_number、first_frame、last_frame、correctness、3D skeleton time series",
        "- 适配价值：这是当前找到的最贴近 NeuraFit 的同域外部数据集，可以用于深蹲/俯卧撑计数分段和二分类纠错验证。",
        "- 限制：只有二值正确性标签，没有细分到 `SQUAT_TOO_SHALLOW`、`BACK_LEAN_TOO_MUCH` 等错误类型。",
        "",
        "## 数据统计",
        "",
        f"- 有效样本数：{dataset_stats['sampleCount']}",
        f"- PUSH_UP 样本：{dataset_stats['byAction'].get('PUSH_UP', 0)}",
        f"- SQUAT 样本：{dataset_stats['byAction'].get('SQUAT', 0)}",
        f"- 正确样本：{dataset_stats['byCorrectLabel'].get('correct', 0)}",
        f"- 错误样本：{dataset_stats['byCorrectLabel'].get('incorrect', 0)}",
        f"- 特征 CSV：`{FEATURE_CSV.relative_to(ROOT)}`",
        "",
        "## 阈值评估",
        "",
        "- 评估方式：leave-one-person-out。每一折留出 1 名受试者作为测试集，其余受试者用于搜索候选阈值，最后汇总所有测试折。",
        "",
        "| 动作 | 方案 | 准确率 | 命中数 | 阈值 |",
        "|---|---|---:|---:|---|",
    ]
    for action_name in ["SQUAT", "PUSH_UP"]:
        item = results[action_name]
        lines.append(result_row(action_name, "当前 App 基线", item["baselineThresholds"], item["baselineEval"]))
        lines.append(result_row(action_name, "训练集搜索候选", item["candidateThresholds"], item["candidateEval"]))

    lines.extend(
        [
            "",
            "## 生产应用决策",
            "",
            "- 是否自动写入 App 阈值：否",
            "- 原因：REHAB24-6 的标签是二值正确/错误，缺少错误类型标签；直接替换生产规则容易把某类未覆盖错误误认为阈值问题。",
            "- 建议：将本数据集纳入离线回归测试，后续把 NeuraFit 的规则库输出与 `correctness` 做二分类一致性验证。",
        ]
    )
    REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def result_row(action_name: str, label: str, thresholds: dict, result: dict) -> str:
    threshold_text = ", ".join(f"{key}={value}" for key, value in thresholds.items())
    return f"| {action_name} | {label} | {result['accuracy']:.3f} | {result['exact']}/{result['total']} | `{threshold_text}` |"


def main() -> None:
    features = extract_features()
    write_features(features)
    results = {}
    for action_name in ["SQUAT", "PUSH_UP"]:
        action_features = [feature for feature in features if feature.action_name == action_name]
        baseline, baseline_eval, candidate, candidate_eval, fold_thresholds = cross_validate(action_features)
        results[action_name] = {
            "baselineThresholds": baseline,
            "baselineEval": baseline_eval,
            "candidateThresholds": candidate,
            "candidateEval": candidate_eval,
            "foldThresholds": fold_thresholds,
        }
    write_state(features, results)
    write_report(features, results)
    print(f"features={len(features)}")
    print(f"feature_csv={FEATURE_CSV}")
    print(f"report={REPORT_PATH}")
    for action_name, result in results.items():
        print(
            action_name,
            f"baseline={result['baselineEval']['accuracy']:.3f}",
            f"candidate={result['candidateEval']['accuracy']:.3f}",
        )


if __name__ == "__main__":
    main()
