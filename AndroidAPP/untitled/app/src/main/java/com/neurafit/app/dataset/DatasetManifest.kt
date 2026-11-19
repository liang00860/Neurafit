package com.neurafit.app.dataset

data class DatasetManifest(
    val name: String,
    val version: String,
    val license: String,
    val sourceUrl: String,
    val files: List<DatasetFile>,
)

data class DatasetFile(
    val url: String,
    val fileName: String,
    val sha256: String? = null,
)

object PublicDatasetCatalog {
    val recommendedSources = listOf(
        DatasetSource(
            name = "MM-Fit",
            homepage = "https://mmfit.github.io/",
            note = "包含多模态健身动作数据，使用前应确认许可和下载条款。",
        ),
        DatasetSource(
            name = "InfiniteRep",
            homepage = "https://paperswithcode.com/dataset/infiniterep",
            note = "适合动作计数相关实验，建议先人工筛选深蹲和俯卧撑样本。",
        ),
        DatasetSource(
            name = "Qualcomm Exercise Video Dataset",
            homepage = "https://www.qualcomm.com/developer/software/qevd-dataset",
            note = "公开视频数据集，使用前应按官网要求申请或确认下载方式。",
        ),
    )
}

data class DatasetSource(
    val name: String,
    val homepage: String,
    val note: String,
)
