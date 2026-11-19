from __future__ import annotations

import csv
import json
import math
import re
import statistics
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
ZIP_PATH = ROOT / "datasets" / "intellirehabds" / "SkeletonData.zip"
FEATURE_CSV = ROOT / "datasets" / "intellirehabds" / "neurafit_sequence_features.csv"
REPORT_PATH = ROOT / "reports" / "intellirehab_self_evolution_report.md"
STATE_PATH = ROOT / "reports" / "intellirehab_self_evolution_state.json"

GESTURE_NAMES = {
    0: "Elbow Flexion Left",
    1: "Elbow Flexion Right",
    2: "Shoulder Flexion Left",
    3: "Shoulder Flexion Right",
    4: "Shoulder Abduction Left",
    5: "Shoulder Abduction Right",
    6: "Shoulder Forward Elevation",
    7: "Side Tap Left",
    8: "Side Tap Right",
}

JOINTS = {
    "spine_base": 0,
    "spine_mid": 1,
    "neck": 2,
    "head": 3,
    "left_shoulder": 4,
    "left_elbow": 5,
    "left_wrist": 6,
    "left_hand": 7,
    "right_shoulder": 8,
    "right_elbow": 9,
    "right_wrist": 10,
    "right_hand": 11,
    "left_hip": 12,
    "left_knee": 13,
    "left_ankle": 14,
    "left_foot": 15,
    "right_hip": 16,
    "right_knee": 17,
    "right_ankle": 18,
    "right_foot": 19,
    "spine_shoulder": 20,
    "left_hand_tip": 21,
    "left_thumb": 22,
    "right_hand_tip": 23,
    "right_thumb": 24,
}


@dataclass(frozen=True)
class SequenceFeature:
    sample_id: str
    subject_id: int
    date_id: int
    gesture_label: int
    gesture_name: str
    repetition_number: int
    correct_label: int
    correctness: str
    position: str
    frame_count: int
    duration_proxy: float
    primary_angle_min: float
    primary_angle_max: float
    primary_angle_range: float
    primary_angle_mean: float
    primary_angle_std: float
    primary_motion_range: float
    symmetry_delta: float
    smoothness: float
    torso_sway_range: float


def parse_filename(path: str) -> tuple[int, int, int, int, int, str] | None:
    name = Path(path).stem
    match = re.match(r"^(\d+)_(\d+)_(\d+)_(\d+)_(\d+)_(.+)$", name)
    if not match:
        return None
    subject_id, date_id, gesture_label, repetition, correct_label, position = match.groups()
    return int(subject_id), int(date_id), int(gesture_label), int(repetition), int(correct_label), position.lower()


def angle(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> np.ndarray:
    v1 = a - b
    v2 = c - b
    n1 = np.linalg.norm(v1, axis=1)
    n2 = np.linalg.norm(v2, axis=1)
    denom = n1 * n2
    result = np.full(shape=(a.shape[0],), fill_value=np.nan, dtype=float)
    valid = denom > 1e-8
    dot = np.sum(v1[valid] * v2[valid], axis=1) / denom[valid]
    dot = np.clip(dot, -1.0, 1.0)
    result[valid] = np.degrees(np.arccos(dot))
    return result


def joint(frames: np.ndarray, name: str) -> np.ndarray:
    return frames[:, JOINTS[name], :]


def nan_stat(values: np.ndarray, fn, default: float = 0.0) -> float:
    clean = values[np.isfinite(values)]
    if clean.size == 0:
        return default
    return float(fn(clean))


def range_of(values: np.ndarray) -> float:
    return nan_stat(values, np.nanmax) - nan_stat(values, np.nanmin)


def movement_range(points: np.ndarray) -> float:
    if len(points) < 2:
        return 0.0
    center = np.nanmean(points, axis=0)
    distances = np.linalg.norm(points - center, axis=1)
    return range_of(distances)


def trajectory_smoothness(points: np.ndarray) -> float:
    if len(points) < 3:
        return 0.0
    velocity = np.diff(points, axis=0)
    acceleration = np.diff(velocity, axis=0)
    return float(np.nanmean(np.linalg.norm(acceleration, axis=1)))


def read_sequence(raw: bytes) -> np.ndarray | None:
    lines = raw.decode("utf-8", errors="ignore").splitlines()
    rows: list[list[float]] = []
    for line in lines:
        if not line.strip():
            continue
        try:
            values = [float(part) for part in line.split(",") if part.strip()]
        except ValueError:
            continue
        if len(values) != 75:
            continue
        rows.append(values)
    if len(rows) < 3:
        return None
    return np.asarray(rows, dtype=float).reshape(len(rows), 25, 3)


def primary_series(frames: np.ndarray, gesture_label: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    left_elbow = angle(joint(frames, "left_shoulder"), joint(frames, "left_elbow"), joint(frames, "left_wrist"))
    right_elbow = angle(joint(frames, "right_shoulder"), joint(frames, "right_elbow"), joint(frames, "right_wrist"))
    left_shoulder = angle(joint(frames, "left_hip"), joint(frames, "left_shoulder"), joint(frames, "left_elbow"))
    right_shoulder = angle(joint(frames, "right_hip"), joint(frames, "right_shoulder"), joint(frames, "right_elbow"))
    left_hip = angle(joint(frames, "spine_base"), joint(frames, "left_hip"), joint(frames, "left_knee"))
    right_hip = angle(joint(frames, "spine_base"), joint(frames, "right_hip"), joint(frames, "right_knee"))

    if gesture_label == 0:
        return left_elbow, joint(frames, "left_wrist"), right_elbow
    if gesture_label == 1:
        return right_elbow, joint(frames, "right_wrist"), left_elbow
    if gesture_label in (2, 4):
        return left_shoulder, joint(frames, "left_wrist"), right_shoulder
    if gesture_label in (3, 5):
        return right_shoulder, joint(frames, "right_wrist"), left_shoulder
    if gesture_label == 6:
        return (left_shoulder + right_shoulder) / 2.0, (joint(frames, "left_wrist") + joint(frames, "right_wrist")) / 2.0, np.abs(left_shoulder - right_shoulder)
    if gesture_label == 7:
        return left_hip, joint(frames, "left_ankle"), right_hip
    if gesture_label == 8:
        return right_hip, joint(frames, "right_ankle"), left_hip
    return left_elbow, joint(frames, "left_wrist"), right_elbow


def extract_feature(path: str, frames: np.ndarray) -> SequenceFeature | None:
    meta = parse_filename(path)
    if meta is None:
        return None
    subject_id, date_id, gesture_label, repetition, correct_label, position = meta
    if gesture_label not in GESTURE_NAMES or correct_label not in (1, 2):
        return None

    primary_angle, primary_points, opposite_signal = primary_series(frames, gesture_label)
    spine = joint(frames, "spine_base")
    torso_sway = range_of(spine[:, 0]) + range_of(spine[:, 2])
    primary_range = range_of(primary_angle)
    primary_motion = movement_range(primary_points)
    opposite_range = range_of(opposite_signal)
    smoothness = trajectory_smoothness(primary_points)
    symmetry_delta = abs(primary_range - opposite_range)

    return SequenceFeature(
        sample_id=Path(path).stem,
        subject_id=subject_id,
        date_id=date_id,
        gesture_label=gesture_label,
        gesture_name=GESTURE_NAMES[gesture_label],
        repetition_number=repetition,
        correct_label=correct_label,
        correctness="correct" if correct_label == 1 else "incorrect",
        position=position,
        frame_count=len(frames),
        duration_proxy=float(len(frames)),
        primary_angle_min=nan_stat(primary_angle, np.nanmin),
        primary_angle_max=nan_stat(primary_angle, np.nanmax),
        primary_angle_range=primary_range,
        primary_angle_mean=nan_stat(primary_angle, np.nanmean),
        primary_angle_std=nan_stat(primary_angle, np.nanstd),
        primary_motion_range=primary_motion,
        symmetry_delta=symmetry_delta,
        smoothness=smoothness,
        torso_sway_range=torso_sway,
    )


def load_features() -> list[SequenceFeature]:
    if not ZIP_PATH.exists():
        raise FileNotFoundError(f"Missing dataset archive: {ZIP_PATH}")
    features: list[SequenceFeature] = []
    with zipfile.ZipFile(ZIP_PATH) as archive:
        names = [
            name
            for name in archive.namelist()
            if name.startswith("SkeletonData/Simplified/")
            and name.lower().endswith(".txt")
        ]
        for name in names:
            frames = read_sequence(archive.read(name))
            if frames is None:
                continue
            feature = extract_feature(name, frames)
            if feature:
                features.append(feature)
    return features


def write_features(features: list[SequenceFeature]) -> None:
    FEATURE_CSV.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(SequenceFeature.__dataclass_fields__.keys())
    with FEATURE_CSV.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for feature in features:
            writer.writerow(feature.__dict__)


def feature_vector(feature: SequenceFeature) -> np.ndarray:
    return np.asarray(
        [
            feature.frame_count,
            feature.primary_angle_min,
            feature.primary_angle_max,
            feature.primary_angle_range,
            feature.primary_angle_mean,
            feature.primary_angle_std,
            feature.primary_motion_range,
            feature.symmetry_delta,
            feature.smoothness,
            feature.torso_sway_range,
        ],
        dtype=float,
    )


def split_by_subject(features: list[SequenceFeature]) -> tuple[list[SequenceFeature], list[SequenceFeature]]:
    train: list[SequenceFeature] = []
    test: list[SequenceFeature] = []
    for feature in features:
        if feature.subject_id % 5 == 0:
            test.append(feature)
        else:
            train.append(feature)
    return train, test


def normalize(train_vectors: np.ndarray, vectors: np.ndarray) -> np.ndarray:
    mean = train_vectors.mean(axis=0)
    std = train_vectors.std(axis=0)
    std[std < 1e-8] = 1.0
    return (vectors - mean) / std


def nearest_centroid_eval(features: list[SequenceFeature]) -> dict:
    train, test = split_by_subject(features)
    train_by_gesture: dict[int, list[SequenceFeature]] = defaultdict(list)
    test_by_gesture: dict[int, list[SequenceFeature]] = defaultdict(list)
    for feature in train:
        train_by_gesture[feature.gesture_label].append(feature)
    for feature in test:
        test_by_gesture[feature.gesture_label].append(feature)

    summary: dict[str, dict] = {}
    total_correct = 0
    total_count = 0

    for gesture_label in sorted(GESTURE_NAMES):
        train_items = train_by_gesture[gesture_label]
        test_items = test_by_gesture[gesture_label]
        if not train_items or not test_items:
            continue

        train_vectors = np.vstack([feature_vector(item) for item in train_items])
        train_norm = normalize(train_vectors, train_vectors)
        centroids: dict[int, np.ndarray] = {}
        for correct_label in (1, 2):
            label_vectors = train_norm[[item.correct_label == correct_label for item in train_items]]
            if label_vectors.size:
                centroids[correct_label] = label_vectors.mean(axis=0)

        if len(centroids) < 2:
            continue

        test_vectors = np.vstack([feature_vector(item) for item in test_items])
        test_norm = normalize(train_vectors, test_vectors)

        exact = 0
        confusion = {
            "correct": {"correct": 0, "incorrect": 0},
            "incorrect": {"correct": 0, "incorrect": 0},
        }
        for item, vector in zip(test_items, test_norm):
            distances = {
                label: float(np.linalg.norm(vector - centroid))
                for label, centroid in centroids.items()
            }
            prediction = min(distances, key=distances.get)
            if prediction == item.correct_label:
                exact += 1
            expected_key = "correct" if item.correct_label == 1 else "incorrect"
            predicted_key = "correct" if prediction == 1 else "incorrect"
            confusion[expected_key][predicted_key] += 1

        total_correct += exact
        total_count += len(test_items)
        summary[str(gesture_label)] = {
            "gestureName": GESTURE_NAMES[gesture_label],
            "accuracy": exact / max(1, len(test_items)),
            "exact": exact,
            "total": len(test_items),
            "confusion": confusion,
        }

    return {
        "split": "subject_id % 5 == 0 as test",
        "accuracy": total_correct / max(1, total_count),
        "exact": total_correct,
        "total": total_count,
        "byGesture": summary,
    }


def dataset_stats(features: list[SequenceFeature]) -> dict:
    by_gesture = Counter(feature.gesture_label for feature in features)
    by_correctness = Counter(feature.correctness for feature in features)
    subjects = sorted({feature.subject_id for feature in features})
    repetitions = [feature.repetition_number for feature in features]
    frame_counts = [feature.frame_count for feature in features]
    return {
        "sampleCount": len(features),
        "subjectCount": len(subjects),
        "gestureCounts": {str(key): value for key, value in sorted(by_gesture.items())},
        "correctnessCounts": dict(by_correctness),
        "minRepetition": min(repetitions),
        "maxRepetition": max(repetitions),
        "medianFrameCount": statistics.median(frame_counts),
        "minFrameCount": min(frame_counts),
        "maxFrameCount": max(frame_counts),
    }


def write_state(features: list[SequenceFeature], evaluation: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    state = {
        "source": "IntelliRehabDS / Zenodo 4610859 + GitHub nsadawi/intellirehabds",
        "featureCsv": str(FEATURE_CSV.relative_to(ROOT)),
        "datasetStats": dataset_stats(features),
        "evaluation": evaluation,
        "acceptedForProduction": False,
        "reason": "Dataset actions are rehabilitation gestures rather than NeuraFit squat/push-up; use as correction-label benchmark, not direct production threshold update.",
    }
    STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def write_report(features: list[SequenceFeature], evaluation: dict) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    stats = dataset_stats(features)
    lines = [
        "# NeuraFit IntelliRehabDS 自我迭代报告",
        "",
        "## 数据集结论",
        "",
        "- 数据集：IntelliRehabDS - A dataset of physical rehabilitation movements",
        "- 来源：Zenodo record 4610859；辅助代码仓库 `nsadawi/intellirehabds`",
        "- 标签条件：包含动作类别、重复编号、正确/错误标签、逐帧 3D 骨架时间序列。",
        "- 标注质量：数据集说明中写明正确性标注由两名标注者独立完成，适合作为动作纠错模块的外部验证集。",
        "- 适配边界：动作是康复动作，不是深蹲/俯卧撑，因此本轮不直接写入生产阈值。",
        "",
        "## 数据统计",
        "",
        f"- 有效序列数：{stats['sampleCount']}",
        f"- 受试者数：{stats['subjectCount']}",
        f"- 正确样本：{stats['correctnessCounts'].get('correct', 0)}",
        f"- 错误样本：{stats['correctnessCounts'].get('incorrect', 0)}",
        f"- 重复编号范围：{stats['minRepetition']} - {stats['maxRepetition']}",
        f"- 帧数范围：{stats['minFrameCount']} - {stats['maxFrameCount']}，中位数 {stats['medianFrameCount']}",
        f"- 特征 CSV：`{FEATURE_CSV.relative_to(ROOT)}`",
        "",
        "## 动作类别样本数",
        "",
        "| 编号 | 动作 | 样本数 |",
        "|---:|---|---:|",
    ]
    for gesture_label, count in stats["gestureCounts"].items():
        lines.append(f"| {gesture_label} | {GESTURE_NAMES[int(gesture_label)]} | {count} |")

    lines.extend(
        [
            "",
            "## 离线正确性评估",
            "",
            "- 方法：按受试者划分训练/测试集，使用序列统计特征和每类动作的 nearest-centroid 分类器评估正确/错误可分性。",
            "- 目的：验证该数据是否能支撑“纠错模型/规则库”的自我迭代，不作为最终算法精度宣称。",
            "",
            f"- 总体准确率：{evaluation['accuracy']:.3f} ({evaluation['exact']}/{evaluation['total']})",
            "",
            "| 动作 | 准确率 | 命中数 |",
            "|---|---:|---:|",
        ]
    )
    for item in evaluation["byGesture"].values():
        lines.append(f"| {item['gestureName']} | {item['accuracy']:.3f} | {item['exact']}/{item['total']} |")

    lines.extend(
        [
            "",
            "## 可用于 NeuraFit 的下一步",
            "",
            "- 将该数据集作为“纠错标签数据源”接入离线评估管线。",
            "- 抽象出与动作无关的序列质量特征，例如运动幅度、左右对称性、平滑度、躯干摆动。",
            "- 后续若找到深蹲/俯卧撑专用数据，可复用同一套导入、特征提取和回归测试流程。",
            "- 当前不自动更新深蹲/俯卧撑阈值，避免康复动作标签误导健身动作规则。",
        ]
    )
    REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    features = load_features()
    write_features(features)
    evaluation = nearest_centroid_eval(features)
    write_state(features, evaluation)
    write_report(features, evaluation)
    print(f"features={len(features)}")
    print(f"feature_csv={FEATURE_CSV}")
    print(f"report={REPORT_PATH}")
    print(f"accuracy={evaluation['accuracy']:.3f} ({evaluation['exact']}/{evaluation['total']})")


if __name__ == "__main__":
    main()
