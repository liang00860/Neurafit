from __future__ import annotations

import csv
import json
import math
import random
import zipfile
from dataclasses import dataclass
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from huggingface_hub import hf_hub_download
from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python.vision import PoseLandmarker, PoseLandmarkerOptions, RunningMode


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "datasets" / "hf_polar"
ZIP_PATH = DATASET_DIR / "cropped.zip"
MODEL_PATH = ROOT / "app" / "src" / "main" / "assets" / "pose_landmarker_lite.task"
FEATURE_CSV = DATASET_DIR / "neurafit_polar_pose_features.csv"
REPORT_PATH = ROOT / "reports" / "hf_polar_self_evolution_report.md"
STATE_PATH = ROOT / "reports" / "hf_polar_self_evolution_state.json"

POLAR_REPO = "sayantan47/POLAR-Posture-Level-Action-Recognition-Dataset"
RANDOM_SEED = 20260530
SAMPLES_PER_CLASS = 220
MIN_VISIBILITY = 0.45

LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28


@dataclass(frozen=True)
class PoseSample:
    sample_id: str
    split: str
    label: str
    left_knee_angle: float | None
    right_knee_angle: float | None
    avg_knee_angle: float | None
    min_leg_visibility: float


@dataclass(frozen=True)
class ThresholdSet:
    top_knee_angle: float
    start_knee_angle: float
    bottom_knee_angle: float


def ensure_dataset() -> Path:
    DATASET_DIR.mkdir(parents=True, exist_ok=True)
    if ZIP_PATH.exists():
        return ZIP_PATH
    return Path(
        hf_hub_download(
            repo_id=POLAR_REPO,
            repo_type="dataset",
            filename="cropped.zip",
            local_dir=DATASET_DIR,
        )
    )


def angle(first: tuple[float, float], center: tuple[float, float], third: tuple[float, float]) -> float | None:
    ax, ay = first
    bx, by = center
    cx, cy = third
    v1 = (ax - bx, ay - by)
    v2 = (cx - bx, cy - by)
    n1 = math.hypot(v1[0], v1[1])
    n2 = math.hypot(v2[0], v2[1])
    if n1 == 0.0 or n2 == 0.0:
        return None
    value = (v1[0] * v2[0] + v1[1] * v2[1]) / (n1 * n2)
    value = max(-1.0, min(1.0, value))
    return math.degrees(math.acos(value))


def select_images(zip_path: Path) -> list[str]:
    random.seed(RANDOM_SEED)
    with zipfile.ZipFile(zip_path) as archive:
        all_names = [
            name
            for name in archive.namelist()
            if name.lower().endswith(".jpg")
            and ("/standing/" in name or "/squatting/" in name)
            and (name.startswith("train/") or name.startswith("val/") or name.startswith("test/"))
        ]

    by_label: dict[str, list[str]] = {"standing": [], "squatting": []}
    for name in all_names:
        if "/standing/" in name:
            by_label["standing"].append(name)
        elif "/squatting/" in name:
            by_label["squatting"].append(name)

    selected: list[str] = []
    for names in by_label.values():
        random.shuffle(names)
        selected.extend(names[:SAMPLES_PER_CLASS])
    random.shuffle(selected)
    return selected


def decode_image(raw: bytes) -> np.ndarray | None:
    encoded = np.frombuffer(raw, dtype=np.uint8)
    bgr = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
    if bgr is None:
        return None
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)


def extract_sample(name: str, image: np.ndarray, landmarker: PoseLandmarker) -> PoseSample | None:
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=image)
    result = landmarker.detect(mp_image)
    landmarks = result.pose_landmarks[0] if result.pose_landmarks else None
    if not landmarks:
        return None

    def point(index: int) -> tuple[float, float]:
        landmark = landmarks[index]
        return landmark.x, landmark.y

    def visibility(index: int) -> float:
        return float(landmarks[index].visibility)

    left_visibility = min(visibility(LEFT_HIP), visibility(LEFT_KNEE), visibility(LEFT_ANKLE))
    right_visibility = min(visibility(RIGHT_HIP), visibility(RIGHT_KNEE), visibility(RIGHT_ANKLE))
    left_knee = None
    right_knee = None
    if left_visibility >= MIN_VISIBILITY:
        left_knee = angle(point(LEFT_HIP), point(LEFT_KNEE), point(LEFT_ANKLE))
    if right_visibility >= MIN_VISIBILITY:
        right_knee = angle(point(RIGHT_HIP), point(RIGHT_KNEE), point(RIGHT_ANKLE))

    available_angles = [value for value in [left_knee, right_knee] if value is not None]
    avg_knee = sum(available_angles) / len(available_angles) if available_angles else None
    parts = name.split("/")
    split = parts[0]
    label = parts[1]
    return PoseSample(
        sample_id=name,
        split=split,
        label=label,
        left_knee_angle=left_knee,
        right_knee_angle=right_knee,
        avg_knee_angle=avg_knee,
        min_leg_visibility=max(left_visibility, right_visibility),
    )


def build_features(zip_path: Path) -> list[PoseSample]:
    selected = select_images(zip_path)
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"MediaPipe model not found: {MODEL_PATH}")
    options = PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL_PATH)),
        running_mode=RunningMode.IMAGE,
        num_poses=1,
        min_pose_detection_confidence=0.35,
        min_pose_presence_confidence=0.35,
        min_tracking_confidence=0.35,
    )
    samples: list[PoseSample] = []
    with zipfile.ZipFile(zip_path) as archive:
        with PoseLandmarker.create_from_options(options) as landmarker:
            for name in selected:
                image = decode_image(archive.read(name))
                if image is None:
                    continue
                sample = extract_sample(name, image, landmarker)
                if sample and sample.avg_knee_angle is not None:
                    samples.append(sample)
    return samples


def write_features(samples: list[PoseSample]) -> None:
    FEATURE_CSV.parent.mkdir(parents=True, exist_ok=True)
    with FEATURE_CSV.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(
            file,
            fieldnames=[
                "sampleId",
                "split",
                "label",
                "leftKneeAngle",
                "rightKneeAngle",
                "avgKneeAngle",
                "minLegVisibility",
            ],
        )
        writer.writeheader()
        for sample in samples:
            writer.writerow(
                {
                    "sampleId": sample.sample_id,
                    "split": sample.split,
                    "label": sample.label,
                    "leftKneeAngle": format_optional(sample.left_knee_angle),
                    "rightKneeAngle": format_optional(sample.right_knee_angle),
                    "avgKneeAngle": format_optional(sample.avg_knee_angle),
                    "minLegVisibility": f"{sample.min_leg_visibility:.5f}",
                }
            )


def format_optional(value: float | None) -> str:
    return "" if value is None else f"{value:.5f}"


def classify(sample: PoseSample, thresholds: ThresholdSet) -> str:
    assert sample.avg_knee_angle is not None
    if sample.avg_knee_angle >= thresholds.top_knee_angle:
        return "standing"
    if sample.avg_knee_angle <= thresholds.bottom_knee_angle:
        return "squatting"
    return "transition"


def evaluate(samples: list[PoseSample], thresholds: ThresholdSet) -> dict:
    confusion = {
        "standing": {"standing": 0, "squatting": 0, "transition": 0},
        "squatting": {"standing": 0, "squatting": 0, "transition": 0},
    }
    exact = 0
    covered = 0
    for sample in samples:
        prediction = classify(sample, thresholds)
        confusion[sample.label][prediction] += 1
        if prediction != "transition":
            covered += 1
        if prediction == sample.label:
            exact += 1

    total = max(1, len(samples))
    return {
        "accuracy": exact / total,
        "coverage": covered / total,
        "exact": exact,
        "total": len(samples),
        "confusion": confusion,
    }


def optimize(samples: list[PoseSample]) -> tuple[ThresholdSet, dict, ThresholdSet, dict]:
    baseline = ThresholdSet(top_knee_angle=155.0, start_knee_angle=150.0, bottom_knee_angle=130.0)
    baseline_eval = evaluate(samples, baseline)
    best = baseline
    best_eval = baseline_eval

    for top in range(145, 171):
        for bottom in range(95, 141):
            if bottom >= top - 10:
                continue
            candidate = ThresholdSet(
                top_knee_angle=float(top),
                start_knee_angle=float(max(bottom + 10, min(150, top - 5))),
                bottom_knee_angle=float(bottom),
            )
            result = evaluate(samples, candidate)
            score = result["accuracy"] * 0.8 + result["coverage"] * 0.2
            best_score = best_eval["accuracy"] * 0.8 + best_eval["coverage"] * 0.2
            if score > best_score:
                best = candidate
                best_eval = result
    return baseline, baseline_eval, best, best_eval


def optimize_production_candidate(
    samples: list[PoseSample],
    baseline: ThresholdSet,
) -> tuple[ThresholdSet, dict]:
    best = baseline
    best_eval = evaluate(samples, baseline)
    for top in range(150, 166):
        candidate = ThresholdSet(
            top_knee_angle=float(top),
            start_knee_angle=baseline.start_knee_angle,
            bottom_knee_angle=baseline.bottom_knee_angle,
        )
        result = evaluate(samples, candidate)
        score = result["accuracy"] * 0.8 + result["coverage"] * 0.2
        best_score = best_eval["accuracy"] * 0.8 + best_eval["coverage"] * 0.2
        if score > best_score:
            best = candidate
            best_eval = result
    return best, best_eval


def counts(samples: list[PoseSample]) -> dict[str, int]:
    result = {"standing": 0, "squatting": 0}
    for sample in samples:
        result[sample.label] += 1
    return result


def angle_stats(samples: list[PoseSample], label: str) -> dict[str, float | None]:
    values = sorted(sample.avg_knee_angle for sample in samples if sample.label == label and sample.avg_knee_angle is not None)
    if not values:
        return {"min": None, "p25": None, "median": None, "p75": None, "max": None}
    return {
        "min": values[0],
        "p25": percentile(values, 0.25),
        "median": percentile(values, 0.50),
        "p75": percentile(values, 0.75),
        "max": values[-1],
    }


def percentile(values: list[float], ratio: float) -> float:
    index = (len(values) - 1) * ratio
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return values[int(index)]
    return values[lower] * (upper - index) + values[upper] * (index - lower)


def safe_to_apply(best: ThresholdSet, best_eval: dict, baseline_eval: dict) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    if best_eval["accuracy"] < baseline_eval["accuracy"] + 0.03:
        reasons.append("优化准确率提升不足 3 个百分点")
    if best_eval["coverage"] < 0.65:
        reasons.append("非 transition 覆盖率低于 65%")
    if not (120.0 <= best.bottom_knee_angle <= 135.0):
        reasons.append("bottomKneeAngle 超出当前 App 安全范围 120-135")
    if not (150.0 <= best.top_knee_angle <= 165.0):
        reasons.append("topKneeAngle 超出当前 App 安全范围 150-165")
    return not reasons, reasons


def safe_to_apply_production_candidate(
    candidate: ThresholdSet,
    candidate_eval: dict,
    baseline: ThresholdSet,
    baseline_eval: dict,
) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    if candidate.bottom_knee_angle != baseline.bottom_knee_angle:
        reasons.append("POLAR 不是深度纠错数据，不能自动修改 bottomKneeAngle")
    if candidate.start_knee_angle != baseline.start_knee_angle:
        reasons.append("POLAR 不是连续视频，不能自动修改 startKneeAngle")
    if candidate_eval["accuracy"] < baseline_eval["accuracy"] + 0.03:
        reasons.append("生产候选准确率提升不足 3 个百分点")
    if candidate_eval["coverage"] < baseline_eval["coverage"] + 0.02:
        reasons.append("生产候选覆盖率提升不足 2 个百分点")
    if not (150.0 <= candidate.top_knee_angle <= 165.0):
        reasons.append("topKneeAngle 超出当前 App 安全范围 150-165")
    return not reasons, reasons


def write_state(
    samples: list[PoseSample],
    baseline: ThresholdSet,
    baseline_eval: dict,
    global_best: ThresholdSet,
    global_best_eval: dict,
    production_candidate: ThresholdSet,
    production_candidate_eval: dict,
    accepted: bool,
    rejected_reasons: list[str],
) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    state = {
        "source": POLAR_REPO,
        "featureCsv": str(FEATURE_CSV.relative_to(ROOT)),
        "sampleCounts": counts(samples),
        "baselineThresholds": baseline.__dict__,
        "baselineEval": baseline_eval,
        "globalCandidateThresholds": global_best.__dict__,
        "globalCandidateEval": global_best_eval,
        "productionCandidateThresholds": production_candidate.__dict__,
        "productionCandidateEval": production_candidate_eval,
        "acceptedForProduction": accepted,
        "rejectedReasons": rejected_reasons,
    }
    STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def write_report(
    samples: list[PoseSample],
    baseline: ThresholdSet,
    baseline_eval: dict,
    global_best: ThresholdSet,
    global_best_eval: dict,
    production_candidate: ThresholdSet,
    production_candidate_eval: dict,
    accepted: bool,
    rejected_reasons: list[str],
) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    standing_stats = angle_stats(samples, "standing")
    squat_stats = angle_stats(samples, "squatting")
    lines = [
        "# NeuraFit Hugging Face 数据集离线迭代报告",
        "",
        "## 数据集来源",
        "",
        f"- Hugging Face 数据集：`{POLAR_REPO}`",
        "- 使用文件：`cropped.zip`",
        "- 使用标签：`standing`、`squatting`",
        "- 迭代目标：用真实人体姿态图片校准深蹲阶段判断中的膝盖角度阈值。",
        "- 限制说明：POLAR 是姿态级动作分类数据，不含专家纠错标签、重复次数标签和时间序列，因此不能直接训练纠错规则或计数状态机。",
        "",
        "## 样本处理",
        "",
        f"- 抽样上限：每类 {SAMPLES_PER_CLASS} 张图像",
        f"- MediaPipe 成功提取有效腿部关键点样本：{len(samples)}",
        f"- standing 样本：{counts(samples)['standing']}",
        f"- squatting 样本：{counts(samples)['squatting']}",
        f"- 特征 CSV：`{FEATURE_CSV.relative_to(ROOT)}`",
        "",
        "## 平均膝盖角度分布",
        "",
        "| 类别 | min | p25 | median | p75 | max |",
        "|---|---:|---:|---:|---:|---:|",
        f"| standing | {fmt_stat(standing_stats['min'])} | {fmt_stat(standing_stats['p25'])} | {fmt_stat(standing_stats['median'])} | {fmt_stat(standing_stats['p75'])} | {fmt_stat(standing_stats['max'])} |",
        f"| squatting | {fmt_stat(squat_stats['min'])} | {fmt_stat(squat_stats['p25'])} | {fmt_stat(squat_stats['median'])} | {fmt_stat(squat_stats['p75'])} | {fmt_stat(squat_stats['max'])} |",
        "",
        "## 阈值搜索结果",
        "",
        "| 方案 | topKneeAngle | startKneeAngle | bottomKneeAngle | 准确率 | 覆盖率 | 命中数 |",
        "|---|---:|---:|---:|---:|---:|---:|",
        threshold_row("当前 App 基线", baseline, baseline_eval),
        threshold_row("全局离线候选", global_best, global_best_eval),
        threshold_row("生产安全候选", production_candidate, production_candidate_eval),
        "",
        "## 混淆矩阵（生产安全候选）",
        "",
        "| 真实标签 | 预测 standing | 预测 squatting | transition |",
        "|---|---:|---:|---:|",
        confusion_row("standing", production_candidate_eval),
        confusion_row("squatting", production_candidate_eval),
        "",
        "## 生产应用决策",
        "",
        f"- 是否自动写入生产代码：{'是' if accepted else '否'}",
        f"- 生产候选更新：`topKneeAngle {baseline.top_knee_angle:.1f} -> {production_candidate.top_knee_angle:.1f}`，`startKneeAngle` 和 `bottomKneeAngle` 保持不变。",
    ]
    if rejected_reasons:
        lines.append("- 拒绝原因：")
        lines.extend(f"  - {reason}" for reason in rejected_reasons)
    else:
        lines.append("- 候选阈值通过安全门限，已作为保守生产更新同步到 App；后续应继续用连续视频数据复核计数稳定性。")
    lines.extend(
        [
            "",
            "## 结论",
            "",
            "本次迭代完成了 Hugging Face 数据源筛选、真实图片抽样、MediaPipe 骨架提取、膝盖角度特征生成和阈值搜索。",
            "由于该数据集不是连续训练视频，也没有纠错标签，本次结果只作为深蹲阶段判断的外部参考，不直接替代 App 中已有状态机和个性化阈值。",
        ]
    )
    REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def fmt_stat(value: float | None) -> str:
    return "-" if value is None else f"{value:.1f}"


def threshold_row(name: str, thresholds: ThresholdSet, result: dict) -> str:
    return (
        f"| {name} | {thresholds.top_knee_angle:.1f} | {thresholds.start_knee_angle:.1f} | "
        f"{thresholds.bottom_knee_angle:.1f} | {result['accuracy']:.3f} | "
        f"{result['coverage']:.3f} | {result['exact']}/{result['total']} |"
    )


def confusion_row(label: str, result: dict) -> str:
    row = result["confusion"][label]
    return f"| {label} | {row['standing']} | {row['squatting']} | {row['transition']} |"


def main() -> None:
    zip_path = ensure_dataset()
    samples = build_features(zip_path)
    write_features(samples)
    baseline, baseline_eval, global_best, global_best_eval = optimize(samples)
    production_candidate, production_candidate_eval = optimize_production_candidate(samples, baseline)
    accepted, rejected_reasons = safe_to_apply_production_candidate(
        production_candidate,
        production_candidate_eval,
        baseline,
        baseline_eval,
    )
    write_state(
        samples,
        baseline,
        baseline_eval,
        global_best,
        global_best_eval,
        production_candidate,
        production_candidate_eval,
        accepted,
        rejected_reasons,
    )
    write_report(
        samples,
        baseline,
        baseline_eval,
        global_best,
        global_best_eval,
        production_candidate,
        production_candidate_eval,
        accepted,
        rejected_reasons,
    )
    print(f"samples={len(samples)}")
    print(f"feature_csv={FEATURE_CSV}")
    print(f"report={REPORT_PATH}")
    print(f"accepted_for_production={accepted}")
    if rejected_reasons:
        print("rejected_reasons=" + "; ".join(rejected_reasons))


if __name__ == "__main__":
    main()
