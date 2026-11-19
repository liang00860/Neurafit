package com.neurafit.app.ui

import com.neurafit.app.analysis.WorkoutActions

enum class AppTab(val label: String, val icon: FitIcon) {
    Dashboard("Dashboard", FitIcon.Home),
    Training("Training", FitIcon.Dumbbell),
    Nutrition("Nutrition", FitIcon.Crown),
    Insights("Insights", FitIcon.Calendar),
}

data class ExerciseCard(
    val title: String,
    val label: String,
    val level: String,
    val highlighted: Boolean,
    val imageUrl: String,
    val actionName: String,
)

data class TrainingHomeCard(
    val title: String,
    val label: String,
    val level: String,
    val imageUrl: String,
    val actionName: String,
    val accent: TrainingCardAccent,
)

enum class TrainingCardAccent {
    Lime,
    Orange,
}

data class PlanItem(
    val day: String,
    val title: String,
    val state: PlanState = PlanState.Upcoming,
    val detail: String = "",
)

enum class PlanState {
    Done,
    Today,
    Upcoming,
}

object ImageUrls {
    const val Avatar =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuAVPAdj-tGpEcGrlPW0Wf_Eh8kP8d33sCkPJhxsY9KFznK-NwTHtbKvjCWAgutxQzmwruepQn-42nIEpYFuwLYxNfBbnrZltf_kIjak98hb-hYVBVs0Ky4uGffryVFcwlus_sySj3y6p_vlaaejm7rgqF9kqVRrHYCQ8RdnfeJCeNyRETpOKhie9dSdjAXWdx9sIph6kyXn3dQPVDGHgVFHXDhxo09Gy2Z4MuVxxT-7yyCuC5nEzOpylzi0A1DntkpkvVjtEJUtMts"
    const val DashboardSquat =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuDyT8kP1iVdW7zHBPsk0upKwrZk7vKG9w1boAo_UNYkhyirR0freihIoVTu-D4X_A9dUXspp3XLh8kmR8XNbjRALSiUIurpfJxFLRCK8HhPJvAIn4bD897cPmjliUH50KkCFUljaGXxbP83puHvJF4Y0t-zvjPOXGrgVIQR2X_IKBmmbE9qdywDB5N_FoNp2do-u7r9iXT7HXXdao1RFs5FF11xMkCsdDPo4D2QwXR_yfnwgD7X1fHyLJKB5oUiQMm07wEZI0Tprm0"
    const val Squat =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuCM12YUY4APFcU7C_g5E-x2YJKHMwjdUrLtpR_G92m-JnL2qGzY1QMeZR3VDEmqMku0GjxKlQ9WdQjECVmEM9tTNY1W_ls8hYDzFEqxsHftKwUgZktrD17ba6fOpIXyDIKmK-Qsn6O-mnjw_TG4j4dv-jLLqV5KxnHBDdk-nIuOYT8y78w58WeS_ZIN_8CBOCkwrv0uipJfPSqUirsAwPjPalWnibwXi6j0qDvKB8H2Jt0XUOvK40qiitYKAn-0cRV2SaiYuXGaMs8"
    const val Lunge =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuBfypoyjhob_SBuTKxRPnrLYZ_YHVwzLsjTvF5V6IjhAjzTtIh1BnGKg0IBcU3gTBrMaNuLghMdFGk7YY1vCAZg3GHSMOa36E7UpH9apgk4g5_WUrj9R-p_L6FYiYZjL6SEiKD_JtkFa53hZ0M9EaQBtzF1txGTQRHHmGFQwBvS2kMAIbOWiXEFELt2snsHZkZd-9IWXJ9Iq2zw7LvT5--yBv6y_dDBvmImXT_Hyxl2N5CzOKm3VeJkuINHEq218cTUEsXQ2Ox-H3g"
    const val Plank =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuDvBb0lx9Pc-LYDS1E4jJ0Adse-my7P-sT6HF2MJnePRUMNzTfqQR9ipTnRJTpi2u93PszYJuwFUIglMXYd7uyCTeYQ_ETjrEv7pv6QQ5ulIlTkciKumtVQ4gxNoG15MLEuGnevoW48dcwNAhj9FuesdvX6uPQYPQXYJLh3NbGUFk3M0lHZjNtQESj6CUK7yIIMfAG_8LJqnP3vM5O0_eeQYDoyZ8Nzkbo3mAbmAcXSn_nG15jQIsHL-9k0kVsInb-NLQMMnG0g0S0"
    const val Salmon =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuBruJHiMCJKDSHki-zBUXKWbb6wxEZx5A4xgIbIrW14_b5KoKfd8xHkSrSkzbl7ZFSZR3piQgteP3aBecl5y3GWnjLa5wGLZdT536-FfGQ7yTb0MQAcIiW4W0jkyM4D0DvxfchVDogxHIVnSu5yVMatmtZiROckV6Ud1W5Q4bUKLlW5ra9e03Gt4ljtUMnHJi7I8SijlowvnoOgDRwb8rkbajlfFW9dtNZzFZwy1PzeNyLNwRxC1nNFDichBOyftIpYxaI0jXa7mfE"
    const val Avocado =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuAqxOabLJ5fjpRBsqAB9acwZYymjB5CvoE5TRjBAssDCClYFSEGTigy0ne9NM1P0Ot3ZetA3pqFo07hvDwwPUWCq40vHOPfmVE7_tkFH4_rqt7Yy7ltUXV4lRkiFsFp5NbX6SWs3CYc0xyXoDO0qUueRC4wsRwW52jHMpocw_JVmvjdWLLUwtDphJ5MMCOfXg6E_v2qGzZwcjboPWLE7GgwE5vdA-dM6YaGjsrEH_YyMEwiQGEoiDQh4iLZC7PsfskpxQb_UJYXKHw"
    const val Smoothie =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuCUcbo9Z5JYAr4VYTzYW6B2wE6i4Jo7Q5VGN9XHVCSueH3fwYYeZtNMZZi_hj0WpnVAlnFMGpeiVf_KMR64QCsR0HzFUrfezeXCHUttmW70tJkRQOwRIvW86V-F4UKb1VcVEbsb4fQVyYmr0bHEIkD7z1_5_iZGPP-xR1ntp7oSElPObgA20kuZ2OGcQt6vpTatBA4Zp89omJ4VhXUVNrlSFJCY1ecuqqDMc-gosV_E3pSj7dedNEAQhEPv10BcDZ1lRZx5m1-0U3s"
    const val Abstract =
        "https://lh3.googleusercontent.com/aida-public/AB6AXuCiF2oVt2W9dnArHJt58nfR8DFrfNdZwsFAZPYnJXQZve8xK5o9i8zXsiF3WQv9rfKgO4TTcjd1eC6SJe39L2KEdCnvCGYKYOysTPtoWW-deb37w13vj3uOGYINxPatuYLb_luCFwIEeL-hx8SyRa5w7b82hGhqpEvyPdarerMUA7-QHnK3xlNpP2c-8DeSdDEbsarYCnhNxb0sUixLn7Rokkj4URiA_fiUSyaNoQ0z4H5v_58twUEaoArgzIJngP49kwpWKeuvLJw"
}

object TrainingImageUrls {
    const val UpperBody = ImageUrls.Plank
    const val Squat = ImageUrls.Squat
    const val PushUp = ImageUrls.Plank
}

val TrainingAvatars = listOf(
    ImageUrls.Avatar,
    ImageUrls.Squat,
    ImageUrls.Plank,
)

val TrainingHomeCards = listOf(
    TrainingHomeCard(
        title = "深蹲",
        label = "下肢",
        level = "初级",
        imageUrl = TrainingImageUrls.Squat,
        actionName = WorkoutActions.SQUAT,
        accent = TrainingCardAccent.Lime,
    ),
    TrainingHomeCard(
        title = "俯卧撑",
        label = "上肢",
        level = "初级",
        imageUrl = TrainingImageUrls.PushUp,
        actionName = WorkoutActions.PUSH_UP,
        accent = TrainingCardAccent.Orange,
    ),
    TrainingHomeCard(
        title = "引体向上",
        label = "背部",
        level = "中级",
        imageUrl = ImageUrls.Plank,
        actionName = "pull_up",
        accent = TrainingCardAccent.Lime,
    ),
    TrainingHomeCard(
        title = "平板支撑",
        label = "核心",
        level = "初级",
        imageUrl = ImageUrls.Plank,
        actionName = "plank",
        accent = TrainingCardAccent.Orange,
    ),
    TrainingHomeCard(
        title = "箭步蹲",
        label = "下肢",
        level = "初级",
        imageUrl = ImageUrls.Lunge,
        actionName = "lunge",
        accent = TrainingCardAccent.Lime,
    ),
    TrainingHomeCard(
        title = "开合跳",
        label = "有氧",
        level = "初级",
        imageUrl = ImageUrls.Squat,
        actionName = "jumping_jack",
        accent = TrainingCardAccent.Orange,
    ),
)

val Exercises = listOf(
    ExerciseCard("Squat", "LEGS - COMPOUND", "BEGINNER", true, ImageUrls.Squat, WorkoutActions.SQUAT),
    ExerciseCard("Push-up", "CHEST - BODYWEIGHT", "BEGINNER", true, ImageUrls.Plank, WorkoutActions.PUSH_UP),
    ExerciseCard("Lunge", "LEGS - UNILATERAL", "INTERMEDIATE", false, ImageUrls.Lunge, WorkoutActions.SQUAT),
    ExerciseCard("Plank", "CORE - ISOMETRIC", "BEGINNER", false, ImageUrls.Plank, WorkoutActions.PUSH_UP),
)

val WeeklyPlan = listOf(
    PlanItem("MON", "Lower Body Power", PlanState.Done),
    PlanItem("TUE", "Upper Body Hypertrophy", PlanState.Today, "6 Movements - 45 Min"),
    PlanItem("WED", "Active Recovery"),
    PlanItem("THU", "Full Body Strength"),
)
