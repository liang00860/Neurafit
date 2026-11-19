import { useEffect, useMemo, useState, type ReactNode } from "react"
import {
  AlertTriangle,
  Cloud,
  CloudLightning,
  CloudMoon,
  CloudRain,
  CloudSnow,
  CloudSun,
  Droplets,
  Eye,
  Gauge,
  GripVertical,
  LocateFixed,
  Map,
  Minus,
  Moon,
  Plus,
  Search,
  Settings,
  Sun,
  Sunrise,
  Sunset,
  Thermometer,
  Trash2,
  Umbrella,
  Wind,
  X,
  type LucideIcon,
} from "lucide-react"

import { apiRequest } from "@/lib/api-client"
import { cn } from "@/lib/utils"

type TemperatureUnit = "c" | "f"
type WindUnit = "bft" | "kmh" | "mph"
type PressureUnit = "hPa" | "inHg"
type PrecipitationUnit = "in" | "mm"
type VisibilityUnit = "km" | "mi"
type MapOverlay = "air" | "precipitation" | "temperature" | "wind"
type SettingsTab = "general" | "notifications"
type WeatherKind =
  | "clear"
  | "cloudy"
  | "fog"
  | "night"
  | "nightPartly"
  | "partly"
  | "rain"
  | "snow"
  | "storm"
  | "windy"

type HourForecast = {
  hour: string
  icon: WeatherKind
  precipitation?: number
  tempF: number
}

type DayForecast = {
  day: string
  icon: WeatherKind
  lowF: number
  precipitation?: number
  highF: number
}

type WeatherDetails = {
  airQuality: string
  airQualityScore: number
  averageHighF: number
  averageLowF: number
  feelsLikeF: number
  humidity: number
  moonPhase: string
  precipitationIn: number
  pressureInHg: number
  sunrise: string
  sunset: string
  uvIndex: number
  visibilityMiles: number
  windMph: number
}

type WeatherAlert = {
  area: string
  description: string
  timeRange: string
  title: string
}

type RemoteWeatherAlertResponse = {
  alert: WeatherAlert | null
  provider: string | null
}

type NotificationPreview = {
  buttons: string[]
  message: string
  subtitle: string
  title: string
}

type LocationWeather = {
  condition: string
  details: WeatherDetails
  heroGlow: string
  heroGradient: string
  highF: number
  hourly: HourForecast[]
  icon: WeatherKind
  id: string
  isCurrentLocation?: boolean
  lowF: number
  mapLabel: string
  name: string
  notification: NotificationPreview
  summary: string
  tempF: number
  tenDay: DayForecast[]
  alert?: WeatherAlert
}

type LocationSeed = {
  alert?: WeatherAlert
  condition: string
  current?: boolean
  details: Partial<WeatherDetails>
  heroGlow: string
  heroGradient: string
  highF: number
  icon: WeatherKind
  id: string
  lowF: number
  mapLabel: string
  name: string
  notification: NotificationPreview
  rainyHours?: number[]
  summary: string
  tempF: number
}

const CURRENT_LOCATION_ID = "my-location"

const weekdayNames = [
  "今日",
  "周二",
  "周三",
  "周四",
  "周五",
  "周六",
  "周日",
  "周一",
  "周二",
  "周三",
]

const hourNames = [
  "现在",
  "下午1时",
  "下午2时",
  "下午3时",
  "下午4时",
  "下午5时",
  "下午6时",
  "下午7时",
  "下午8时",
  "下午9时",
  "下午10时",
  "下午11时",
  "凌晨0时",
  "凌晨1时",
  "凌晨2时",
  "凌晨3时",
  "凌晨4时",
  "凌晨5时",
  "早上6时",
  "早上7时",
  "早上8时",
  "早上9时",
  "早上10时",
  "早上11时",
]

const savedLocationSeeds: LocationSeed[] = [
  {
    current: true,
    condition: "晴间多云",
    details: {
      airQuality: "良好",
      airQualityScore: 37,
      averageHighF: 73,
      averageLowF: 56,
      feelsLikeF: 74,
      humidity: 61,
      moonPhase: "娥眉月",
      precipitationIn: 0.04,
      pressureInHg: 29.98,
      sunrise: "06:02",
      sunset: "20:21",
      uvIndex: 6,
      visibilityMiles: 9.8,
      windMph: 9,
    },
    heroGlow: "rgba(255, 255, 255, 0.3)",
    heroGradient:
      "linear-gradient(135deg, rgba(119,193,255,0.88) 0%, rgba(108,171,246,0.7) 38%, rgba(70,114,220,0.78) 100%)",
    highF: 78,
    icon: "partly",
    id: "my-location",
    lowF: 61,
    mapLabel: "我的位置",
    name: "我的位置",
    notification: {
      buttons: ["开启", "稍后"],
      message:
        "你当前位置附近预计将在 43 分钟后出现小雨。",
      subtitle: "时间敏感",
      title: "即将开始下雨",
    },
    rainyHours: [15, 16, 17],
    summary: "今晚温暖，伴有轻微海洋层云。",
    tempF: 72,
  },
  {
    condition: "晴间多云",
    details: {
      airQuality: "优",
      airQualityScore: 21,
      averageHighF: 75,
      averageLowF: 58,
      feelsLikeF: 73,
      humidity: 58,
      moonPhase: "娥眉月",
      precipitationIn: 0.02,
      pressureInHg: 30.01,
      sunrise: "06:03",
      sunset: "20:22",
      uvIndex: 7,
      visibilityMiles: 10,
      windMph: 8,
    },
    heroGlow: "rgba(255, 248, 222, 0.32)",
    heroGradient:
      "linear-gradient(140deg, rgba(123,199,255,0.88) 0%, rgba(134,194,255,0.74) 30%, rgba(93,140,235,0.82) 100%)",
    highF: 78,
    icon: "partly",
    id: "cupertino",
    lowF: 61,
    mapLabel: "库比蒂诺",
    name: "库比蒂诺",
    notification: {
      buttons: ["查看", "忽略"],
      message: "局部多云天气将持续到深夜，整体平稳。",
      subtitle: "天气",
      title: "今夜天气平稳",
    },
    summary: "日落前间晴，伴有凉爽微风。",
    tempF: 72,
  },
  {
    condition: "雷暴",
    details: {
      airQuality: "中等",
      airQualityScore: 59,
      averageHighF: 81,
      averageLowF: 67,
      feelsLikeF: 84,
      humidity: 79,
      moonPhase: "娥眉月",
      precipitationIn: 0.41,
      pressureInHg: 29.82,
      sunrise: "05:31",
      sunset: "20:27",
      uvIndex: 4,
      visibilityMiles: 6.2,
      windMph: 16,
    },
    heroGlow: "rgba(197, 211, 255, 0.26)",
    heroGradient:
      "linear-gradient(145deg, rgba(83,101,149,0.9) 0%, rgba(61,76,118,0.82) 42%, rgba(34,42,69,0.92) 100%)",
    highF: 85,
    icon: "storm",
    id: "new-york",
    lowF: 70,
    mapLabel: "纽约",
    name: "纽约",
    notification: {
      buttons: ["查看预警", "关闭"],
      message: "下午 4 点后强对流可能带来阵风和雷电。",
      subtitle: "时间敏感",
      title: "强雷暴警报",
    },
    rainyHours: [2, 3, 4, 5, 6, 7],
    summary: "午后晚些时候雷暴发展，空气闷热。",
    tempF: 79,
    alert: {
      area: "纽约县",
      description:
        "雷暴可能带来强降雨、云对地闪电，以及局地最高 50 英里/小时的阵风。",
      timeRange: "持续至晚上7:00",
      title: "强雷暴警报",
    },
  },
  {
    condition: "大部晴朗",
    details: {
      airQuality: "良好",
      airQualityScore: 33,
      averageHighF: 79,
      averageLowF: 67,
      feelsLikeF: 83,
      humidity: 54,
      moonPhase: "娥眉月",
      precipitationIn: 0,
      pressureInHg: 29.93,
      sunrise: "05:13",
      sunset: "19:53",
      uvIndex: 8,
      visibilityMiles: 8.6,
      windMph: 11,
    },
    heroGlow: "rgba(255, 239, 201, 0.34)",
    heroGradient:
      "linear-gradient(140deg, rgba(137,190,255,0.88) 0%, rgba(120,173,255,0.72) 45%, rgba(78,118,225,0.84) 100%)",
    highF: 86,
    icon: "clear",
    id: "seoul",
    lowF: 69,
    mapLabel: "首尔",
    name: "首尔",
    notification: {
      buttons: ["打开", "忽略"],
      message: "紫外线强度将在下午 3 点前维持较高水平。",
      subtitle: "天气",
      title: "紫外线极强",
    },
    summary: "天气干燥，午间阳光强烈，伴有明亮薄雾。",
    tempF: 81,
  },
  {
    condition: "晴朗",
    details: {
      airQuality: "良好",
      airQualityScore: 28,
      averageHighF: 83,
      averageLowF: 71,
      feelsLikeF: 86,
      humidity: 60,
      moonPhase: "娥眉月",
      precipitationIn: 0,
      pressureInHg: 29.88,
      sunrise: "04:27",
      sunset: "18:59",
      uvIndex: 9,
      visibilityMiles: 8.9,
      windMph: 7,
    },
    heroGlow: "rgba(255, 227, 170, 0.35)",
    heroGradient:
      "linear-gradient(140deg, rgba(120,201,255,0.92) 0%, rgba(111,182,255,0.78) 36%, rgba(84,128,233,0.88) 100%)",
    highF: 88,
    icon: "clear",
    id: "tokyo",
    lowF: 73,
    mapLabel: "东京",
    name: "东京",
    notification: {
      buttons: ["查看", "静音"],
      message: "晴朗天气将持续整夜，风力平稳。",
      subtitle: "天气",
      title: "美好的夜晚即将到来",
    },
    summary: "城市范围内阳光明媚，夏日暖意充足。",
    tempF: 84,
  },
  {
    condition: "霾",
    details: {
      airQuality: "中等",
      airQualityScore: 66,
      averageHighF: 84,
      averageLowF: 71,
      feelsLikeF: 87,
      humidity: 63,
      moonPhase: "娥眉月",
      precipitationIn: 0.01,
      pressureInHg: 29.86,
      sunrise: "04:51",
      sunset: "18:57",
      uvIndex: 7,
      visibilityMiles: 5.4,
      windMph: 8,
    },
    heroGlow: "rgba(255, 225, 196, 0.28)",
    heroGradient:
      "linear-gradient(145deg, rgba(188,171,157,0.84) 0%, rgba(164,155,164,0.72) 45%, rgba(102,126,171,0.8) 100%)",
    highF: 89,
    icon: "fog",
    id: "shanghai",
    lowF: 74,
    mapLabel: "上海",
    name: "上海",
    notification: {
      buttons: ["打开", "忽略"],
      message: "受霾影响，能见度在下午晚些时候前有所下降。",
      subtitle: "天气",
      title: "能见度降低",
    },
    summary: "空气湿润，持续有霾，阳光较弱。",
    tempF: 83,
  },
  {
    condition: "小雨",
    details: {
      airQuality: "良好",
      airQualityScore: 24,
      averageHighF: 71,
      averageLowF: 55,
      feelsLikeF: 65,
      humidity: 84,
      moonPhase: "娥眉月",
      precipitationIn: 0.28,
      pressureInHg: 29.74,
      sunrise: "04:45",
      sunset: "21:20",
      uvIndex: 2,
      visibilityMiles: 7.8,
      windMph: 13,
    },
    heroGlow: "rgba(210, 223, 255, 0.22)",
    heroGradient:
      "linear-gradient(150deg, rgba(133,155,190,0.88) 0%, rgba(111,130,172,0.76) 38%, rgba(71,86,124,0.88) 100%)",
    highF: 68,
    icon: "rain",
    id: "london",
    lowF: 55,
    mapLabel: "伦敦",
    name: "伦敦",
    notification: {
      buttons: ["查看", "忽略"],
      message: "持续降雨将在接下来一小时内逐渐减弱。",
      subtitle: "时间敏感",
      title: "降水即将停止",
    },
    rainyHours: [0, 1, 2, 3, 4, 5, 6],
    summary: "全天阵雨不断，气温偏凉。",
    tempF: 63,
  },
]

const addableLocationSeeds: LocationSeed[] = [
  {
    condition: "大部晴朗",
    details: {
      airQuality: "良好",
      airQualityScore: 29,
      averageHighF: 77,
      averageLowF: 58,
      feelsLikeF: 74,
      humidity: 49,
      moonPhase: "娥眉月",
      precipitationIn: 0,
      pressureInHg: 30.12,
      sunrise: "05:54",
      sunset: "21:38",
      uvIndex: 7,
      visibilityMiles: 10,
      windMph: 10,
    },
    heroGlow: "rgba(255, 241, 213, 0.32)",
    heroGradient:
      "linear-gradient(140deg, rgba(124,193,255,0.9) 0%, rgba(106,179,255,0.78) 45%, rgba(69,123,230,0.84) 100%)",
    highF: 76,
    icon: "clear",
    id: "paris",
    lowF: 58,
    mapLabel: "巴黎",
    name: "巴黎",
    notification: {
      buttons: ["打开", "忽略"],
      message: "宜人的阳光将持续到傍晚。",
      subtitle: "天气",
      title: "晴空",
    },
    summary: "天气干爽明亮，午后有轻柔微风。",
    tempF: 73,
  },
  {
    condition: "阵雨",
    details: {
      airQuality: "良好",
      airQualityScore: 26,
      averageHighF: 88,
      averageLowF: 79,
      feelsLikeF: 92,
      humidity: 81,
      moonPhase: "娥眉月",
      precipitationIn: 0.33,
      pressureInHg: 29.8,
      sunrise: "06:58",
      sunset: "19:15",
      uvIndex: 4,
      visibilityMiles: 8.4,
      windMph: 9,
    },
    heroGlow: "rgba(200, 230, 255, 0.26)",
    heroGradient:
      "linear-gradient(150deg, rgba(78,137,184,0.9) 0%, rgba(81,121,167,0.8) 40%, rgba(58,85,124,0.9) 100%)",
    highF: 90,
    icon: "rain",
    id: "singapore",
    lowF: 79,
    mapLabel: "新加坡",
    name: "新加坡",
    notification: {
      buttons: ["打开", "忽略"],
      message: "晚上 6 点后降雨可能短时增强。",
      subtitle: "时间敏感",
      title: "即将开始下雨",
    },
    rainyHours: [3, 4, 5, 12, 13],
    summary: "空气潮湿，稍后将有热带阵雨经过。",
    tempF: 86,
  },
  {
    condition: "有风",
    details: {
      airQuality: "优",
      airQualityScore: 19,
      averageHighF: 66,
      averageLowF: 50,
      feelsLikeF: 61,
      humidity: 57,
      moonPhase: "娥眉月",
      precipitationIn: 0,
      pressureInHg: 30.16,
      sunrise: "06:56",
      sunset: "17:04",
      uvIndex: 5,
      visibilityMiles: 10,
      windMph: 21,
    },
    heroGlow: "rgba(222, 236, 255, 0.24)",
    heroGradient:
      "linear-gradient(145deg, rgba(115,170,219,0.9) 0%, rgba(111,152,201,0.82) 45%, rgba(69,105,154,0.88) 100%)",
    highF: 67,
    icon: "windy",
    id: "sydney",
    lowF: 51,
    mapLabel: "悉尼",
    name: "悉尼",
    notification: {
      buttons: ["打开", "忽略"],
      message: "港湾区域的多风天气将持续到今晚。",
      subtitle: "天气",
      title: "今晚风力较大",
    },
    summary: "天气晴朗但风势较强，沿海空气清爽。",
    tempF: 62,
  },
  {
    condition: "多云",
    details: {
      airQuality: "良好",
      airQualityScore: 34,
      averageHighF: 70,
      averageLowF: 53,
      feelsLikeF: 68,
      humidity: 62,
      moonPhase: "娥眉月",
      precipitationIn: 0.07,
      pressureInHg: 30.04,
      sunrise: "04:47",
      sunset: "21:27",
      uvIndex: 3,
      visibilityMiles: 9.3,
      windMph: 8,
    },
    heroGlow: "rgba(227, 233, 249, 0.2)",
    heroGradient:
      "linear-gradient(145deg, rgba(163,179,208,0.82) 0%, rgba(145,158,188,0.76) 46%, rgba(97,117,155,0.84) 100%)",
    highF: 69,
    icon: "cloudy",
    id: "berlin",
    lowF: 54,
    mapLabel: "柏林",
    name: "柏林",
    notification: {
      buttons: ["打开", "忽略"],
      message:
        "傍晚前以多云为主，夜间有零星阵雨。",
      subtitle: "天气",
      title: "云量增多",
    },
    summary: "层云覆盖下，较凉空气逐渐形成。",
    tempF: 66,
  },
]

const defaultNotificationSettings = {
  "my-location": true,
  cupertino: true,
  london: true,
  "new-york": true,
  seoul: false,
  shanghai: false,
  tokyo: false,
}

const overlayThemes: Record<MapOverlay, string> = {
  air: "linear-gradient(180deg, rgba(238,241,245,0.98) 0%, rgba(224,230,238,0.94) 34%, rgba(207,222,241,0.92) 100%)",
  precipitation:
    "linear-gradient(180deg, rgba(215,224,237,0.98) 0%, rgba(197,210,229,0.96) 36%, rgba(175,192,216,0.94) 100%)",
  temperature:
    "linear-gradient(180deg, rgba(238,240,244,0.98) 0%, rgba(226,231,239,0.96) 30%, rgba(204,213,227,0.94) 66%, rgba(184,198,219,0.92) 100%)",
  wind: "linear-gradient(180deg, rgba(234,237,242,0.98) 0%, rgba(216,224,235,0.96) 42%, rgba(194,205,220,0.94) 100%)",
}

const overlayLabels: Record<MapOverlay, string> = {
  air: "空气质量",
  precipitation: "降水",
  temperature: "气温",
  wind: "风力",
}

const mapPins = [
  { id: "cupertino", left: "44%", top: "52%" },
  { id: "new-york", left: "77%", top: "42%" },
  { id: "london", left: "50%", top: "33%" },
  { id: "seoul", left: "88%", top: "39%" },
  { id: "tokyo", left: "92%", top: "43%" },
  { id: "shanghai", left: "84%", top: "47%" },
]

function average(values: number[]) {
  if (values.length === 0) {
    return 0
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function hPaToInHg(valueHpa: number) {
  return valueHpa / 33.8639
}

function metersToMiles(valueMeters: number) {
  return valueMeters / 1609.344
}

function formatClockTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value))
}

function formatForecastHour(value: string, index: number) {
  if (index === 0) {
    return "现在"
  }
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "numeric",
    hour12: false,
  }).format(new Date(value))
}

function formatForecastDay(value: string, index: number) {
  if (index === 0) {
    return "今日"
  }
  return new Intl.DateTimeFormat("zh-CN", {
    weekday: "short",
  }).format(new Date(value))
}

function findTimelineStartIndex(times: string[], currentTime: string | undefined) {
  if (times.length === 0 || !currentTime) {
    return 0
  }

  const exactIndex = times.findIndex((time) => time === currentTime)
  if (exactIndex >= 0) {
    return exactIndex
  }

  const currentTimestamp = new Date(currentTime).getTime()
  const nextIndex = times.findIndex(
    (time) => new Date(time).getTime() >= currentTimestamp
  )

  return nextIndex >= 0 ? nextIndex : 0
}

function airQualityLabel(score: number) {
  if (score <= 50) return "良好"
  if (score <= 100) return "中等"
  if (score <= 150) return "敏感人群不健康"
  if (score <= 200) return "不健康"
  if (score <= 300) return "非常不健康"
  return "危险"
}

function moonPhaseLabel(phase: number | undefined) {
  if (phase === undefined || Number.isNaN(phase)) {
    return "娥眉月"
  }
  if (phase < 0.03 || phase >= 0.97) return "新月"
  if (phase < 0.22) return "娥眉月"
  if (phase < 0.28) return "上弦月"
  if (phase < 0.47) return "盈凸月"
  if (phase < 0.53) return "满月"
  if (phase < 0.72) return "亏凸月"
  if (phase < 0.78) return "下弦月"
  return "残月"
}

function weatherKindFromCode(code: number, isDay = 1): WeatherKind {
  if (code === 0) {
    return isDay ? "clear" : "night"
  }
  if (code === 1 || code === 2) {
    return isDay ? "partly" : "nightPartly"
  }
  if (code === 3) {
    return "cloudy"
  }
  if (code === 45 || code === 48) {
    return "fog"
  }
  if ([51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)) {
    return "rain"
  }
  if ([71, 73, 75, 77, 85, 86].includes(code)) {
    return "snow"
  }
  if ([95, 96, 99].includes(code)) {
    return "storm"
  }
  return "windy"
}

function weatherLabelFromCode(code: number, isDay = 1) {
  if (code === 0) {
    return isDay ? "晴朗" : "晴夜"
  }
  if (code === 1) return isDay ? "大致晴朗" : "夜间晴朗"
  if (code === 2) return isDay ? "晴间多云" : "夜间多云"
  if (code === 3) return "多云"
  if (code === 45 || code === 48) return "有雾"
  if ([51, 53, 55, 56, 57].includes(code)) return "毛毛雨"
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return "降雨"
  if ([71, 73, 75, 77, 85, 86].includes(code)) return "降雪"
  if ([95, 96, 99].includes(code)) return "雷暴"
  return "有风"
}

function heroThemeForKind(kind: WeatherKind) {
  switch (kind) {
    case "storm":
      return {
        heroGlow: "rgba(197, 211, 255, 0.26)",
        heroGradient:
          "linear-gradient(145deg, rgba(83,101,149,0.9) 0%, rgba(61,76,118,0.82) 42%, rgba(34,42,69,0.92) 100%)",
      }
    case "rain":
      return {
        heroGlow: "rgba(210, 223, 255, 0.22)",
        heroGradient:
          "linear-gradient(150deg, rgba(133,155,190,0.88) 0%, rgba(111,130,172,0.76) 38%, rgba(71,86,124,0.88) 100%)",
      }
    case "cloudy":
    case "fog":
      return {
        heroGlow: "rgba(227, 233, 249, 0.2)",
        heroGradient:
          "linear-gradient(145deg, rgba(163,179,208,0.82) 0%, rgba(145,158,188,0.76) 46%, rgba(97,117,155,0.84) 100%)",
      }
    case "night":
    case "nightPartly":
      return {
        heroGlow: "rgba(187, 201, 255, 0.22)",
        heroGradient:
          "linear-gradient(145deg, rgba(73,96,144,0.88) 0%, rgba(55,73,119,0.82) 42%, rgba(30,39,70,0.9) 100%)",
      }
    default:
      return {
        heroGlow: "rgba(255, 241, 213, 0.32)",
        heroGradient:
          "linear-gradient(140deg, rgba(124,193,255,0.9) 0%, rgba(106,179,255,0.78) 45%, rgba(69,123,230,0.84) 100%)",
      }
  }
}

async function fetchCurrentLocationAlert(
  latitude: number,
  longitude: number,
  countryCode: string | null,
  locationName: string
) {
  try {
    const searchParams = new URLSearchParams({
      latitude: latitude.toString(),
      longitude: longitude.toString(),
      name: locationName,
    })
    if (countryCode) {
      searchParams.set("countryCode", countryCode)
    }

    const response = await apiRequest<RemoteWeatherAlertResponse>(
      `/weather/alerts/current?${searchParams.toString()}`
    )
    return response.alert ?? undefined
  } catch {
    return undefined
  }
}

async function fetchCurrentLocationWeather(
  latitude: number,
  longitude: number
): Promise<LocationWeather> {
  const forecastUrl = new URL("https://api.open-meteo.com/v1/forecast")
  forecastUrl.searchParams.set("latitude", latitude.toString())
  forecastUrl.searchParams.set("longitude", longitude.toString())
  forecastUrl.searchParams.set("timezone", "auto")
  forecastUrl.searchParams.set("forecast_days", "10")
  forecastUrl.searchParams.set("temperature_unit", "fahrenheit")
  forecastUrl.searchParams.set("wind_speed_unit", "mph")
  forecastUrl.searchParams.set("precipitation_unit", "inch")
  forecastUrl.searchParams.set(
    "current",
    [
      "temperature_2m",
      "apparent_temperature",
      "relative_humidity_2m",
      "is_day",
      "weather_code",
      "wind_speed_10m",
      "surface_pressure",
      "visibility",
    ].join(",")
  )
  forecastUrl.searchParams.set(
    "hourly",
    ["temperature_2m", "weather_code", "precipitation_probability", "is_day"].join(",")
  )
  forecastUrl.searchParams.set(
    "daily",
    [
      "weather_code",
      "temperature_2m_max",
      "temperature_2m_min",
      "precipitation_sum",
      "precipitation_probability_max",
      "sunrise",
      "sunset",
      "uv_index_max",
      "moon_phase",
    ].join(",")
  )

  const reverseUrl = new URL("https://geocoding-api.open-meteo.com/v1/reverse")
  reverseUrl.searchParams.set("latitude", latitude.toString())
  reverseUrl.searchParams.set("longitude", longitude.toString())
  reverseUrl.searchParams.set("language", "zh")
  reverseUrl.searchParams.set("count", "1")

  const airQualityUrl = new URL(
    "https://air-quality-api.open-meteo.com/v1/air-quality"
  )
  airQualityUrl.searchParams.set("latitude", latitude.toString())
  airQualityUrl.searchParams.set("longitude", longitude.toString())
  airQualityUrl.searchParams.set("timezone", "auto")
  airQualityUrl.searchParams.set("current", "us_aqi")

  const [forecastResponse, reverseResponse, airQualityResponse] =
    await Promise.all([
      fetch(forecastUrl.toString()),
      fetch(reverseUrl.toString()),
      fetch(airQualityUrl.toString()),
    ])

  if (!forecastResponse.ok) {
    throw new Error("forecast-request-failed")
  }

  const forecastData = await forecastResponse.json()
  const reverseData = reverseResponse.ok ? await reverseResponse.json() : {}
  const airQualityData = airQualityResponse.ok ? await airQualityResponse.json() : {}

  const current = forecastData.current ?? {}
  const hourly = forecastData.hourly ?? {}
  const daily = forecastData.daily ?? {}

  const locationResult = reverseData.results?.[0]
  const locationName =
    locationResult?.city ??
    locationResult?.town ??
    locationResult?.village ??
    locationResult?.name ??
    "当前位置"
  const countryCode =
    typeof locationResult?.country_code === "string"
      ? locationResult.country_code.toLowerCase()
      : typeof locationResult?.countryCode === "string"
        ? locationResult.countryCode.toLowerCase()
        : null
  const liveAlert = await fetchCurrentLocationAlert(
    latitude,
    longitude,
    countryCode,
    locationName
  )

  const currentCode = Number(current.weather_code ?? 0)
  const currentIsDay = Number(current.is_day ?? 1)
  const currentKind = weatherKindFromCode(currentCode, currentIsDay)
  const currentCondition = weatherLabelFromCode(currentCode, currentIsDay)
  const theme = heroThemeForKind(currentKind)
  const hourlyTimes: string[] = hourly.time ?? []
  const currentTime = typeof current.time === "string" ? current.time : undefined
  const timelineStartIndex = findTimelineStartIndex(hourlyTimes, currentTime)
  const nextHourPrecipitation = (hourly.precipitation_probability ?? [])
    .slice(timelineStartIndex, timelineStartIndex + 6)
    .map((value: number) => Number(value ?? 0))
  const maxNextHourPrecipitation = Math.max(0, ...nextHourPrecipitation)
  const airQualityScore = Math.round(Number(airQualityData.current?.us_aqi ?? 0))

  const hourlyForecast: HourForecast[] = hourlyTimes
    .slice(timelineStartIndex, timelineStartIndex + 24)
    .map((time: string, index: number) => ({
      hour: formatForecastHour(time, index),
      icon: weatherKindFromCode(
        Number(hourly.weather_code?.[timelineStartIndex + index] ?? currentCode),
        Number(hourly.is_day?.[timelineStartIndex + index] ?? currentIsDay)
      ),
      precipitation:
        Number(hourly.precipitation_probability?.[timelineStartIndex + index] ?? 0) > 0
          ? Math.round(
              Number(
                hourly.precipitation_probability?.[timelineStartIndex + index] ?? 0
              )
            )
          : undefined,
      tempF: Math.round(
        Number(
          hourly.temperature_2m?.[timelineStartIndex + index] ??
            current.temperature_2m ??
            0
        )
      ),
    }))

  const tenDayForecast: DayForecast[] = (daily.time ?? [])
    .slice(0, 10)
    .map((time: string, index: number) => ({
      day: formatForecastDay(time, index),
      icon: weatherKindFromCode(Number(daily.weather_code?.[index] ?? currentCode)),
      lowF: Math.round(Number(daily.temperature_2m_min?.[index] ?? 0)),
      precipitation:
        Number(daily.precipitation_probability_max?.[index] ?? 0) > 0
          ? Math.round(Number(daily.precipitation_probability_max?.[index] ?? 0))
          : undefined,
      highF: Math.round(Number(daily.temperature_2m_max?.[index] ?? 0)),
    }))

  const averageHigh = average(
    (daily.temperature_2m_max ?? []).slice(0, 7).map((value: number) => Number(value ?? 0))
  )
  const averageLow = average(
    (daily.temperature_2m_min ?? []).slice(0, 7).map((value: number) => Number(value ?? 0))
  )

  return {
    id: CURRENT_LOCATION_ID,
    isCurrentLocation: true,
    name: locationName,
    mapLabel: locationName,
    condition: currentCondition,
    details: {
      airQuality: airQualityLabel(airQualityScore),
      airQualityScore,
      averageHighF: Math.round(averageHigh),
      averageLowF: Math.round(averageLow),
      feelsLikeF: Math.round(Number(current.apparent_temperature ?? current.temperature_2m ?? 0)),
      humidity: Math.round(Number(current.relative_humidity_2m ?? 0)),
      moonPhase: moonPhaseLabel(Number(daily.moon_phase?.[0])),
      precipitationIn: Number(daily.precipitation_sum?.[0] ?? 0),
      pressureInHg: hPaToInHg(Number(current.surface_pressure ?? 1013.25)),
      sunrise: daily.sunrise?.[0] ? formatClockTime(daily.sunrise[0]) : "--:--",
      sunset: daily.sunset?.[0] ? formatClockTime(daily.sunset[0]) : "--:--",
      uvIndex: Math.round(Number(daily.uv_index_max?.[0] ?? 0)),
      visibilityMiles: metersToMiles(Number(current.visibility ?? 0)),
      windMph: Math.round(Number(current.wind_speed_10m ?? 0)),
    },
    heroGlow: theme.heroGlow,
    heroGradient: theme.heroGradient,
    highF: Math.round(Number(daily.temperature_2m_max?.[0] ?? current.temperature_2m ?? 0)),
    hourly: hourlyForecast,
    icon: currentKind,
    lowF: Math.round(Number(daily.temperature_2m_min?.[0] ?? current.temperature_2m ?? 0)),
    notification: liveAlert
      ? {
          buttons: ["查看预警", "稍后"],
          message: liveAlert.description,
          subtitle: "时间敏感",
          title: liveAlert.title,
        }
      : maxNextHourPrecipitation >= 40
      ? {
          buttons: ["查看", "稍后"],
          message: `${locationName} 接下来一小时内降水概率较高，请留意天气变化。`,
          subtitle: "时间敏感",
          title: "即将出现降水",
        }
      : {
          buttons: ["打开", "稍后"],
          message: `${locationName} 当前${currentCondition}，实时天气数据已同步。`,
          subtitle: "天气",
          title: "当前位置天气已更新",
        },
    summary:
      liveAlert
        ? `${locationName} 当前有官方恶劣天气预警，请及时查看详情并注意防范。`
        : maxNextHourPrecipitation >= 40
        ? `${locationName} 接下来几小时有较高降水概率，建议提前准备。`
        : `${locationName} 当前${currentCondition}，实时天气数据已同步。`,
    tempF: Math.round(Number(current.temperature_2m ?? 0)),
    tenDay: tenDayForecast,
    alert: liveAlert,
  }
}

function toCelsius(valueF: number) {
  return Math.round(((valueF - 32) * 5) / 9)
}

function toKilometersPerHour(valueMph: number) {
  return Math.round(valueMph * 1.609)
}

function toBeaufort(valueMph: number) {
  const thresholds = [1, 4, 8, 13, 19, 25, 32, 39, 47, 55, 64, 73]
  return thresholds.findIndex((threshold) => valueMph < threshold)
}

function formatTemperature(valueF: number, unit: TemperatureUnit) {
  return `${unit === "f" ? Math.round(valueF) : toCelsius(valueF)}\u00B0`
}

function formatWind(valueMph: number, unit: WindUnit) {
  if (unit === "kmh") {
    return `${toKilometersPerHour(valueMph)} km/h`
  }
  if (unit === "bft") {
    return `${toBeaufort(valueMph)}级`
  }
  return `${Math.round(valueMph)} 英里/小时`
}

function finiteOrZero(value: number) {
  return Number.isFinite(value) ? value : 0
}

function formatPressure(valueInHg: number, unit: PressureUnit) {
  const safeValueInHg = finiteOrZero(valueInHg)
  if (unit === "hPa") {
    return `${Math.round(safeValueInHg * 33.8639)} hPa`
  }
  return `${safeValueInHg.toFixed(2)} 英寸汞柱`
}

function formatVisibility(valueMiles: number, unit: VisibilityUnit) {
  const safeValueMiles = finiteOrZero(valueMiles)
  if (unit === "km") {
    return `${(safeValueMiles * 1.609).toFixed(1)} km`
  }
  return `${safeValueMiles.toFixed(1)} 英里`
}

function formatPrecipitation(valueInches: number, unit: PrecipitationUnit) {
  const safeValueInches = finiteOrZero(valueInches)
  if (unit === "mm") {
    return `${Math.round(safeValueInches * 25.4)} mm`
  }
  return `${safeValueInches.toFixed(2)} 英寸`
}

function formatCurrentTime(date: Date) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date)
}

function iconForWeather(kind: WeatherKind): LucideIcon {
  switch (kind) {
    case "clear":
      return Sun
    case "partly":
      return CloudSun
    case "cloudy":
      return Cloud
    case "rain":
      return CloudRain
    case "storm":
      return CloudLightning
    case "snow":
      return CloudSnow
    case "night":
      return Moon
    case "nightPartly":
      return CloudMoon
    case "windy":
      return Wind
    case "fog":
      return Cloud
  }
}

function buildHourly(seed: LocationSeed): HourForecast[] {
  return hourNames.map((hour, index) => {
    const goesNight = index >= 8 && index <= 18
    const rainy = seed.rainyHours?.includes(index)
    const icon = rainy
      ? seed.icon === "storm"
        ? "storm"
        : "rain"
      : goesNight && (seed.icon === "clear" || seed.icon === "partly")
        ? seed.icon === "clear"
          ? "night"
          : "nightPartly"
        : seed.icon
    const rollingTemp =
      seed.tempF +
      Math.round(Math.sin(index / 3) * 2.8) -
      (index >= 10 ? Math.round((index - 9) * 0.5) : 0)

    return {
      hour,
      icon,
      precipitation: rainy ? Math.min(95, 25 + index * 4) : undefined,
      tempF: rollingTemp,
    }
  })
}

function buildTenDay(seed: LocationSeed): DayForecast[] {
  return weekdayNames.map((day, index) => {
    const high =
      seed.highF + Math.round(Math.sin(index * 0.6) * 3) - (index > 6 ? 1 : 0)
    const low = seed.lowF + Math.round(Math.cos(index * 0.5) * 2)
    const rainy = seed.rainyHours?.length && index % 3 === 1
    const icon = rainy
      ? seed.icon === "storm"
        ? "storm"
        : "rain"
      : index === 0
        ? seed.icon
        : index % 4 === 0 && seed.icon === "clear"
          ? "partly"
          : index % 5 === 0
            ? "cloudy"
            : seed.icon

    return {
      day,
      icon,
      lowF: low,
      precipitation: rainy ? 30 + index * 4 : undefined,
      highF: high,
    }
  })
}

function toLocationWeather(seed: LocationSeed): LocationWeather {
  return {
    condition: seed.condition,
    details: {
      airQuality: seed.details.airQuality ?? "良好",
      airQualityScore: seed.details.airQualityScore ?? 34,
      averageHighF: seed.details.averageHighF ?? seed.highF - 4,
      averageLowF: seed.details.averageLowF ?? seed.lowF - 3,
      feelsLikeF: seed.details.feelsLikeF ?? seed.tempF,
      humidity: seed.details.humidity ?? 62,
      moonPhase: seed.details.moonPhase ?? "娥眉月",
      precipitationIn: seed.details.precipitationIn ?? 0.05,
      pressureInHg: seed.details.pressureInHg ?? 29.94,
      sunrise: seed.details.sunrise ?? "06:04",
      sunset: seed.details.sunset ?? "20:20",
      uvIndex: seed.details.uvIndex ?? 6,
      visibilityMiles: seed.details.visibilityMiles ?? 9.4,
      windMph: seed.details.windMph ?? 9,
    },
    heroGlow: seed.heroGlow,
    heroGradient: seed.heroGradient,
    highF: seed.highF,
    hourly: buildHourly(seed),
    icon: seed.icon,
    id: seed.id,
    isCurrentLocation: seed.current,
    lowF: seed.lowF,
    mapLabel: seed.mapLabel,
    name: seed.name,
    notification: seed.notification,
    summary: seed.summary,
    tempF: seed.tempF,
    tenDay: buildTenDay(seed),
    alert: seed.alert,
  }
}

function WeatherSymbol({
  className,
  kind,
  size = 24,
  strokeWidth = 1.9,
}: {
  className?: string
  kind: WeatherKind
  size?: number
  strokeWidth?: number
}) {
  const Icon = iconForWeather(kind)
  return <Icon className={className} size={size} strokeWidth={strokeWidth} />
}

function GlassCard({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <section
      className={cn(
        "rounded-[26px] border border-black/[0.05] bg-white/58 shadow-[0_18px_42px_rgba(18,27,43,0.08)] backdrop-blur-[30px]",
        className
      )}
    >
      {children}
    </section>
  )
}

function DetailCard({
  caption,
  icon: Icon,
  title,
  value,
}: {
  caption: string
  icon: LucideIcon
  title: string
  value: string
}) {
  return (
    <GlassCard className="p-[16px]">
      <div className="flex items-center gap-[9px] text-[11px] font-semibold tracking-[0.08em] text-[#6e7581] uppercase">
        <span className="flex size-[28px] items-center justify-center rounded-full border border-black/[0.04] bg-white/80 text-[#6b7280]">
          <Icon size={14} />
        </span>
        {title}
      </div>
      <div className="mt-[14px] text-[28px] font-semibold tracking-[-0.045em] text-[#111316]">
        {value}
      </div>
      <p className="mt-[10px] text-[12px] leading-[18px] text-[#6b7280]">
        {caption}
      </p>
    </GlassCard>
  )
}

export function WeatherAppWindow({
  className = "",
  windowControls,
}: {
  className?: string
  windowControls?: ReactNode
}) {
  const [locations, setLocations] = useState<LocationWeather[]>(() =>
    savedLocationSeeds.map(toLocationWeather)
  )
  const [selectedLocationId, setSelectedLocationId] =
    useState(CURRENT_LOCATION_ID)
  const [searchQuery, setSearchQuery] = useState("")
  const [draggedId, setDraggedId] = useState<string | null>(null)
  const [mapExpanded, setMapExpanded] = useState(false)
  const [mapOverlay, setMapOverlay] = useState<MapOverlay>("precipitation")
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [settingsTab, setSettingsTab] = useState<SettingsTab>("general")
  const [temperatureUnit, setTemperatureUnit] = useState<TemperatureUnit>("c")
  const [windUnit, setWindUnit] = useState<WindUnit>("mph")
  const [pressureUnit, setPressureUnit] = useState<PressureUnit>("inHg")
  const [precipitationUnit, setPrecipitationUnit] =
    useState<PrecipitationUnit>("in")
  const [visibilityUnit, setVisibilityUnit] = useState<VisibilityUnit>("mi")
  const [locationServicesEnabled, setLocationServicesEnabled] = useState(true)
  const [severeAlertsEnabled, setSevereAlertsEnabled] = useState(true)
  const [nextHourRainEnabled, setNextHourRainEnabled] = useState(true)
  const [currentTime, setCurrentTime] = useState(() => new Date())
  const [notificationByLocation, setNotificationByLocation] = useState(
    defaultNotificationSettings
  )

  useEffect(() => {
    const timer = window.setInterval(() => {
      setCurrentTime(new Date())
    }, 30_000)

    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!locationServicesEnabled || !("geolocation" in navigator)) {
      return
    }

    let isCancelled = false

    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const currentLocationWeather = await fetchCurrentLocationWeather(
            coords.latitude,
            coords.longitude
          )

          if (isCancelled) {
            return
          }

          setLocations((currentLocations) =>
            currentLocations.map((location) =>
              location.id === CURRENT_LOCATION_ID
                ? currentLocationWeather
                : location
            )
          )
        } catch {
          if (isCancelled) {
            return
          }
        }
      },
      () => {
        if (!isCancelled) {
          setLocationServicesEnabled(false)
        }
      },
      {
        enableHighAccuracy: true,
        maximumAge: 300_000,
        timeout: 10_000,
      }
    )

    return () => {
      isCancelled = true
    }
  }, [locationServicesEnabled])

  const searchMatches = useMemo(() => {
    const normalized = searchQuery.trim().toLowerCase()
    if (!normalized) {
      return []
    }

    return addableLocationSeeds
      .map(toLocationWeather)
      .filter(
        (location) =>
          !locations.some(
            (savedLocation) => savedLocation.id === location.id
          ) && location.name.toLowerCase().includes(normalized)
      )
  }, [locations, searchQuery])

  const selectedLocation =
    locations.find((location) => location.id === selectedLocationId) ??
    locations[0]
  const currentLocation =
    locations.find((location) => location.id === CURRENT_LOCATION_ID) ??
    locations[0]
  const currentLocationLabel =
    locationServicesEnabled && currentLocation.isCurrentLocation
      ? currentLocation.mapLabel
      : "当前位置"

  const tenDayMin = Math.min(...selectedLocation.tenDay.map((day) => day.lowF))
  const tenDayMax = Math.max(...selectedLocation.tenDay.map((day) => day.highF))

  const weatherDetails = [
    {
      caption: `今日空气质量指数 AQI 为 ${selectedLocation.details.airQualityScore}，颗粒物累积较轻。`,
      icon: Wind,
      title: "空气质量",
      value: selectedLocation.details.airQuality,
    },
    {
      caption:
        "紫外线在午后前后达到峰值，外出建议适当防护。",
      icon: Sun,
      title: "紫外线指数",
      value: `${selectedLocation.details.uvIndex} / 10`,
    },
    {
      caption:
        "地面风力稳定，傍晚较平稳，晚些时候会有轻微变化。",
      icon: Wind,
      title: "风力",
      value: formatWind(selectedLocation.details.windMph, windUnit),
    },
    {
      caption: "相对湿度全天大部分时间较为舒适。",
      icon: Droplets,
      title: "湿度",
      value: `${selectedLocation.details.humidity}%`,
    },
    {
      caption:
        "综合湿度和风感后的人体体感温度。",
      icon: Thermometer,
      title: "体感温度",
      value: formatTemperature(
        selectedLocation.details.feelsLikeF,
        temperatureUnit
      ),
    },
    {
      caption:
        "远距离能见度清晰，适合观景和出行。",
      icon: Eye,
      title: "能见度",
      value: formatVisibility(
        selectedLocation.details.visibilityMiles,
        visibilityUnit
      ),
    },
    {
      caption:
        "气压整体较稳定，全天变化不大。",
      icon: Gauge,
      title: "气压",
      value: formatPressure(
        selectedLocation.details.pressureInHg,
        pressureUnit
      ),
    },
    {
      caption: "预计未来 24 小时的液态降水累计量。",
      icon: Umbrella,
      title: "降水",
      value: formatPrecipitation(
        selectedLocation.details.precipitationIn,
        precipitationUnit
      ),
    },
    {
      caption: "晨光将温暖地洒向城市，带来柔和的低角度光线。",
      icon: Sunrise,
      title: "日出",
      value: selectedLocation.details.sunrise,
    },
    {
      caption: "傍晚光线缓慢减弱，西侧天空视野清晰。",
      icon: Sunset,
      title: "日落",
      value: selectedLocation.details.sunset,
    },
    {
      caption: `本周常见气温为 ${formatTemperature(
        selectedLocation.details.averageHighF,
        temperatureUnit
      )} / ${formatTemperature(
        selectedLocation.details.averageLowF,
        temperatureUnit
      )}.`,
      icon: Thermometer,
      title: "平均值",
      value: `${formatTemperature(
        selectedLocation.details.averageHighF,
        temperatureUnit
      )} / ${formatTemperature(selectedLocation.details.averageLowF, temperatureUnit)}`,
    },
    {
      caption:
        "今夜月光较柔和，黄昏后可见细窄月缘。",
      icon: Moon,
      title: "月相",
      value: selectedLocation.details.moonPhase,
    },
  ]

  const notificationPreviews: NotificationPreview[] = [
    {
      buttons: ["查看", "忽略"],
      message:
        selectedLocation.alert?.description ??
        `${selectedLocation.name} 当前没有政府发布的恶劣天气预警。`,
      subtitle: selectedLocation.alert ? "时间敏感" : "天气",
      title: selectedLocation.alert?.title ?? "恶劣天气预警",
    },
    {
      buttons: ["开启", "稍后"],
      message: `${selectedLocation.name} 周边可能会在一小时内开始降雨。`,
      subtitle: "时间敏感",
      title: "即将开始下雨",
    },
    {
      buttons: ["查看", "静音"],
      message: `今晚稍后 ${selectedLocation.name} 可能因冷空气出现阵雪。`,
      subtitle: "时间敏感",
      title: "即将开始下雪",
    },
    {
      buttons: ["打开", "忽略"],
      message: `${selectedLocation.name} 接下来一小时可查看分钟级降水变化。`,
      subtitle: "天气",
      title: "未来一小时降水",
    },
    {
      buttons: ["查看", "忽略"],
      message: "阵雨将很快减弱，天气会逐步转好。",
      subtitle: "天气",
      title: "降水即将停止",
    },
    {
      buttons: ["打开", "稍后"],
      message: selectedLocation.notification.message,
      subtitle: "时间敏感",
      title: "天气时效通知",
    },
  ]

  const mapLocations = locations.filter((location) =>
    mapPins.some((pin) => pin.id === location.id)
  )

  const moveLocation = (targetId: string) => {
    if (!draggedId || draggedId === targetId) {
      return
    }

    setLocations((currentLocations) => {
      const draggedIndex = currentLocations.findIndex(
        (location) => location.id === draggedId
      )
      const targetIndex = currentLocations.findIndex(
        (location) => location.id === targetId
      )

      if (draggedIndex === -1 || targetIndex === -1) {
        return currentLocations
      }

      const reordered = [...currentLocations]
      const [draggedLocation] = reordered.splice(draggedIndex, 1)
      reordered.splice(targetIndex, 0, draggedLocation)
      return reordered
    })
    setDraggedId(null)
  }

  const deleteLocation = (locationId: string) => {
    setLocations((currentLocations) =>
      currentLocations.filter((location) => location.id !== locationId)
    )
    if (selectedLocationId === locationId) {
      setSelectedLocationId(CURRENT_LOCATION_ID)
    }
  }

  const addLocation = (location: LocationWeather) => {
    setLocations((currentLocations) => [...currentLocations, location])
    setSelectedLocationId(location.id)
    setSearchQuery("")
  }

  return (
    <section
      className={cn(
        "relative flex h-full w-full overflow-hidden rounded-[28px] border border-black/[0.06] bg-[#e9edf2] text-[#111316] shadow-[0_36px_96px_rgba(18,27,43,0.18)]",
        className
      )}
      style={{
        fontFamily:
          "SF Pro Display, SF Pro Text, Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
      }}
    >
      <div
        className="absolute inset-0"
        style={{
          background:
            "linear-gradient(180deg, rgba(244,246,249,0.98) 0%, rgba(233,237,243,0.96) 52%, rgba(228,233,240,0.94) 100%)",
        }}
      />
      <div
        className="absolute inset-0 opacity-100"
        style={{
          background:
            "radial-gradient(circle at 18% 12%, rgba(255,255,255,0.98) 0%, rgba(255,255,255,0.54) 18%, rgba(255,255,255,0) 44%), radial-gradient(circle at 78% 14%, rgba(212,223,239,0.55) 0%, rgba(212,223,239,0.12) 28%, rgba(255,255,255,0) 54%), linear-gradient(180deg, rgba(255,255,255,0.28) 0%, rgba(222,229,238,0.18) 100%)",
        }}
      />
      <div className="relative flex h-full w-full flex-col">
        <div
          className="flex h-[62px] items-center border-b border-black/[0.05] bg-white/56 px-[18px] backdrop-blur-[30px]"
          data-window-drag-handle
        >
          {windowControls}
          <div className="ml-[24px]">
            <div className="text-[19px] font-semibold tracking-[-0.035em] text-[#111316]">
              天气
            </div>
            <div className="mt-[1px] text-[12px] text-[#6b7280]">
              {selectedLocation.name} 天气预报
            </div>
          </div>
          <div
            className="ml-auto flex items-center gap-[10px]"
            data-window-no-drag
          >
            <button
              className="flex h-[32px] items-center gap-[7px] rounded-full border border-black/[0.05] bg-white/78 px-[12px] text-[12px] font-medium text-[#3f4752] shadow-[inset_0_1px_0_rgba(255,255,255,0.88)]"
              onClick={() => setSelectedLocationId(CURRENT_LOCATION_ID)}
            >
              <LocateFixed size={14} />
              {currentLocationLabel}
            </button>
            <div className="flex items-center gap-[8px] rounded-full border border-black/[0.05] bg-white/78 px-[12px] py-[7px] text-[12px] font-medium text-[#3f4752]">
              <WeatherSymbol
                className="text-[#6b7280]"
                kind={currentLocation.icon}
                size={16}
              />
              {formatTemperature(currentLocation.tempF, temperatureUnit)}
              <span className="text-[#7b8290]">{currentLocation.mapLabel}</span>
            </div>
            <button
              className="flex size-[32px] items-center justify-center rounded-full border border-black/[0.05] bg-white/78 text-[#4b5563] shadow-[inset_0_1px_0_rgba(255,255,255,0.9)]"
              onClick={() => setSettingsOpen((open) => !open)}
            >
              <Settings size={16} />
            </button>
          </div>
        </div>

        <div className="flex min-h-0 flex-1">
          <aside className="relative flex w-[320px] shrink-0 flex-col border-r border-black/[0.05] bg-[linear-gradient(180deg,rgba(245,247,250,0.84),rgba(237,240,245,0.7))] p-[18px] backdrop-blur-[32px]">
            <div className="relative" data-window-no-drag>
              <Search
                className="absolute top-1/2 left-[14px] -translate-y-1/2 text-[#8f96a3]"
                size={15}
              />
              <input
                className="h-[38px] w-full rounded-[12px] border border-black/[0.05] bg-white/82 px-[40px] text-[13px] text-[#1f2937] shadow-[inset_0_1px_0_rgba(255,255,255,0.92)] outline-none placeholder:text-[#8f96a3]"
                placeholder="搜索或添加城市"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
              />
            </div>

            {searchMatches.length > 0 && (
              <GlassCard className="mt-[10px] p-[8px]">
                <div className="mb-[4px] px-[8px] text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                  添加地点
                </div>
                {searchMatches.map((location) => (
                  <button
                    key={location.id}
                    className="flex w-full items-center gap-[10px] rounded-[14px] px-[10px] py-[9px] text-left transition-colors hover:bg-[#eef2f8]"
                    onClick={() => addLocation(location)}
                  >
                    <WeatherSymbol
                      className="text-[#6b7280]"
                      kind={location.icon}
                      size={18}
                    />
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] font-medium text-[#111316]">
                        {location.name}
                      </div>
                      <div className="truncate text-[11px] text-[#7b8290]">
                        {location.summary}
                      </div>
                    </div>
                    <span className="flex size-[24px] items-center justify-center rounded-full bg-[#0a84ff] text-white">
                      <Plus size={14} />
                    </span>
                  </button>
                ))}
              </GlassCard>
            )}

            <div className="mt-[16px] flex min-h-0 flex-1 flex-col gap-[8px] overflow-y-auto pr-[3px]">
              {locations.map((location) => {
                const selected = location.id === selectedLocation.id
                const locationCondition =
                  location.isCurrentLocation && !locationServicesEnabled
                    ? "定位服务已关闭"
                    : location.condition

                return (
                  <div
                    key={location.id}
                    className={cn(
                      "group flex items-center gap-[10px] rounded-[18px] border px-[12px] py-[11px] transition-all",
                      selected
                        ? "border-[#0a84ff]/18 bg-[#0a84ff]/14 shadow-[inset_0_1px_0_rgba(255,255,255,0.9)]"
                        : "border-transparent bg-white/48 hover:border-black/[0.04] hover:bg-white/78"
                    )}
                    draggable={!location.isCurrentLocation}
                    onClick={() => setSelectedLocationId(location.id)}
                    onDragOver={(event) => event.preventDefault()}
                    onDragStart={() => setDraggedId(location.id)}
                    onDrop={() => moveLocation(location.id)}
                  >
                    <button className="text-[#9aa1ad]">
                      <GripVertical size={14} />
                    </button>
                    <div className="flex size-[38px] items-center justify-center rounded-[14px] border border-black/[0.04] bg-white/84 text-[#6b7280] shadow-[inset_0_1px_0_rgba(255,255,255,0.96)]">
                      <WeatherSymbol kind={location.icon} size={20} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-[6px]">
                        <div className="truncate text-[14px] font-semibold text-[#111316]">
                          {location.name}
                        </div>
                        {location.isCurrentLocation && (
                          <span className="rounded-full bg-white/82 px-[6px] py-[2px] text-[10px] font-semibold tracking-[0.08em] text-[#0a84ff] uppercase">
                            实时
                          </span>
                        )}
                      </div>
                      <div className="truncate text-[12px] text-[#707784]">
                        {locationCondition}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-[18px] font-semibold tracking-[-0.03em] text-[#111316]">
                        {formatTemperature(location.tempF, temperatureUnit)}
                      </div>
                      <div className="text-[11px] text-[#7b8290]">
                        {formatTemperature(location.highF, temperatureUnit)} /{" "}
                        {formatTemperature(location.lowF, temperatureUnit)}
                      </div>
                    </div>
                    {!location.isCurrentLocation && (
                      <button
                        className="rounded-full p-[6px] text-[#9aa1ad] opacity-0 transition-opacity group-hover:opacity-100 hover:bg-white hover:text-[#d05151]"
                        onClick={(event) => {
                          event.stopPropagation()
                          deleteLocation(location.id)
                        }}
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>
                )
              })}
            </div>

            <GlassCard className="mt-[14px] p-[14px]">
              <div className="flex items-start gap-[10px]">
                <div className="flex size-[34px] items-center justify-center rounded-[12px] border border-black/[0.04] bg-white/84 text-[#6b7280]">
                  <LocateFixed size={17} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-semibold text-[#111316]">
                    定位服务
                  </div>
                  <p className="mt-[4px] text-[12px] leading-[18px] text-[#6b7280]">
                    使用当前位置获取实时降水、通知和地图居中。
                  </p>
                  <div className="mt-[10px] flex items-center justify-between rounded-[14px] border border-black/[0.04] bg-[#f5f6f8] px-[10px] py-[8px]">
                    <span className="text-[12px] font-medium text-[#4b5563]">
                      {locationServicesEnabled
                        ? `已启用${currentLocation.name ? ` · ${currentLocation.name}` : ""}`
                        : "需要权限"}
                    </span>
                    <button
                      className={cn(
                        "rounded-full px-[10px] py-[5px] text-[11px] font-semibold",
                        locationServicesEnabled
                          ? "bg-[#e7eef8] text-[#0a84ff]"
                          : "bg-[#0a84ff] text-white"
                      )}
                      onClick={() =>
                        setLocationServicesEnabled(
                          (currentValue) => !currentValue
                        )
                      }
                    >
                      {locationServicesEnabled ? "关闭" : "开启"}
                    </button>
                  </div>
                </div>
              </div>
            </GlassCard>
          </aside>

          <div className="relative min-h-0 flex-1 overflow-hidden">
            <div className="absolute inset-0 overflow-y-auto px-[22px] py-[20px]">
              <section 
                className="relative overflow-hidden rounded-[32px] border border-white/65 p-[28px] shadow-[0_20px_60px_rgba(33,42,56,0.10)] transition-all duration-700"
                style={{ background: selectedLocation.heroGradient }}
              >
                <div 
                  className="absolute top-[14%] left-[8%] h-[220px] w-[320px] rounded-full blur-[36px] transition-colors duration-700" 
                  style={{ backgroundColor: selectedLocation.heroGlow }}
                />
                <div 
                  className="absolute top-[-40px] right-[-80px] h-[240px] w-[380px] rounded-full blur-[42px] transition-colors duration-700" 
                  style={{ backgroundColor: selectedLocation.heroGlow }}
                />
                <div className="absolute inset-x-0 bottom-0 h-[42%] bg-[linear-gradient(180deg,rgba(255,255,255,0)_0%,rgba(255,255,255,0.22)_54%,rgba(255,255,255,0.46)_100%)]" />
                <div className="relative flex items-start justify-between gap-[24px]">
                  <div>
                    <div className="text-[18px] font-medium text-white/92">
                      {selectedLocation.name}
                    </div>
                    <div className="mt-[6px] text-[94px] leading-[0.9] font-semibold tracking-[-0.085em] text-white">
                      {formatTemperature(
                        selectedLocation.tempF,
                        temperatureUnit
                      )}
                    </div>
                    <div className="mt-[10px] text-[24px] font-medium text-white/92">
                      {selectedLocation.condition}
                    </div>
                    <div className="mt-[8px] flex items-center gap-[10px] text-[14px] font-medium text-white/80">
                      <span>
                        最高：{" "}
                        {formatTemperature(
                          selectedLocation.highF,
                          temperatureUnit
                        )}
                      </span>
                      <span>
                        最低：{" "}
                        {formatTemperature(
                          selectedLocation.lowF,
                          temperatureUnit
                        )}
                      </span>
                    </div>
                    <p className="mt-[18px] max-w-[460px] text-[14px] leading-[22px] text-white/82">
                      {selectedLocation.summary}
                    </p>
                  </div>
                  <div className="flex flex-col items-end">
                    <div className="rounded-[30px] border border-white/30 bg-white/18 p-[22px] shadow-[inset_0_1px_0_rgba(255,255,255,0.42)]">
                      <WeatherSymbol
                        className="text-white drop-shadow-[0_10px_18px_rgba(0,0,0,0.14)]"
                        kind={selectedLocation.icon}
                        size={120}
                        strokeWidth={1.55}
                      />
                    </div>
                    <div className="mt-[14px] rounded-full border border-white/30 bg-white/18 px-[12px] py-[6px] text-[11px] font-semibold tracking-[0.08em] text-white/78 uppercase">
                      大气状况
                    </div>
                  </div>
                </div>
              </section>

              <GlassCard className="mt-[18px] p-[18px]">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                      逐小时预报
                    </div>
                    <div className="mt-[4px] text-[22px] font-semibold tracking-[-0.03em] text-[#111316]">
                      未来24小时
                    </div>
                  </div>
                  <div className="rounded-full border border-black/[0.05] bg-[#f5f6f8] px-[10px] py-[6px] text-[11px] font-medium text-[#6b7280]">
                    横向滚动
                  </div>
                </div>
                <div className="mt-[16px] flex gap-[12px] overflow-x-auto pb-[4px]">
                  {selectedLocation.hourly.map((hour) => (
                    <div
                      key={`${selectedLocation.id}-${hour.hour}`}
                      className="min-w-[92px] rounded-[22px] border border-black/[0.05] bg-[#f8f9fb] px-[10px] py-[14px] text-center shadow-[inset_0_1px_0_rgba(255,255,255,0.95)]"
                    >
                      <div className="text-[12px] font-medium text-[#7b8290]">
                        {hour.hour}
                      </div>
                      <div className="mt-[10px] flex justify-center text-[#6b7280]">
                        <WeatherSymbol kind={hour.icon} size={24} />
                      </div>
                      <div className="mt-[12px] text-[16px] font-semibold text-[#111316]">
                        {formatTemperature(hour.tempF, temperatureUnit)}
                      </div>
                      <div className="mt-[5px] text-[11px] text-[#0a84ff]">
                        {hour.precipitation
                          ? `${hour.precipitation}%`
                          : "\u00a0"}
                      </div>
                    </div>
                  ))}
                </div>
              </GlassCard>

              <div className="mt-[18px] grid grid-cols-[minmax(0,1.05fr)_minmax(300px,0.68fr)] gap-[18px]">
                <GlassCard className="p-[18px]">
                  <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                    10日预报
                  </div>
                  <div className="mt-[14px]">
                    {selectedLocation.tenDay.map((day, index) => {
                      const rangeWidth =
                        ((day.highF - day.lowF) /
                          Math.max(1, tenDayMax - tenDayMin)) *
                        100
                      const offset =
                        ((day.lowF - tenDayMin) /
                          Math.max(1, tenDayMax - tenDayMin)) *
                        100

                      return (
                        <div
                          key={`${selectedLocation.id}-${day.day}-${index}`}
                          className={cn(
                            "grid grid-cols-[82px_56px_68px_1fr_56px_56px] items-center gap-[10px] py-[12px]",
                            index !== 0 && "border-t border-black/[0.05]"
                          )}
                        >
                          <div className="text-[13px] font-medium text-[#111316]">
                            {day.day}
                          </div>
                          <div className="flex justify-center text-[#6b7280]">
                            <WeatherSymbol kind={day.icon} size={21} />
                          </div>
                          <div className="text-[12px] text-[#0a84ff]">
                            {day.precipitation ? `${day.precipitation}%` : ""}
                          </div>
                          <div className="relative h-[6px] rounded-full bg-[#d9dee6]">
                            <div
                              className="absolute top-0 h-full rounded-full bg-[linear-gradient(90deg,#98a7ba,#cfd7e4)]"
                              style={{
                                left: `${offset}%`,
                                width: `${rangeWidth}%`,
                              }}
                            />
                          </div>
                          <div className="text-right text-[13px] font-medium text-[#7b8290]">
                            {formatTemperature(day.lowF, temperatureUnit)}
                          </div>
                          <div className="text-right text-[13px] font-semibold text-[#111316]">
                            {formatTemperature(day.highF, temperatureUnit)}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </GlassCard>

                <div className="flex flex-col gap-[18px]">
                  <GlassCard className="p-[18px]">
                    <div className="flex items-start gap-[12px]">
                      <div className="flex size-[38px] items-center justify-center rounded-[14px] border border-[#f0d7b0] bg-[#faf3e8] text-[#a26a1e]">
                        <AlertTriangle size={18} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                          恶劣天气预警
                        </div>
                        <div className="mt-[6px] text-[20px] font-semibold tracking-[-0.03em] text-[#111316]">
                          {selectedLocation.alert?.title ?? "当前无预警"}
                        </div>
                        <p className="mt-[8px] text-[13px] leading-[20px] text-[#6b7280]">
                          {selectedLocation.alert?.description ??
                            "当出现危险天气时，政府预警和未来一小时降水提醒会显示在这里。"}
                        </p>
                        <div className="mt-[10px] text-[12px] text-[#7b8290]">
                          {selectedLocation.alert
                            ? `${selectedLocation.alert.area} · ${selectedLocation.alert.timeRange}`
                            : `${selectedLocation.name} · 天气平稳时段`}
                        </div>
                        <button className="mt-[14px] rounded-full bg-[#0a84ff] px-[12px] py-[7px] text-[12px] font-semibold text-white shadow-[0_8px_18px_rgba(10,132,255,0.26)]">
                          查看完整预警
                        </button>
                      </div>
                    </div>
                  </GlassCard>

                  <GlassCard className="p-[18px]">
                    <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                      通知预览
                    </div>
                    <div className="mt-[12px] space-y-[10px]">
                      {notificationPreviews.map((preview) => (
                        <div
                          key={preview.title}
                          className="rounded-[22px] border border-black/[0.05] bg-white/76 p-[14px] shadow-[0_8px_24px_rgba(18,27,43,0.06)]"
                        >
                          <div className="flex items-start gap-[12px]">
                            <div className="flex size-[42px] shrink-0 items-center justify-center rounded-[16px] bg-[linear-gradient(180deg,#bcc6d6,#8d99ac)] text-white">
                              <WeatherSymbol
                                kind={selectedLocation.icon}
                                size={20}
                              />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center justify-between">
                                <div className="text-[13px] font-semibold text-[#111316]">
                                  天气
                                </div>
                                <div className="text-[11px] text-[#8f96a3]">
                                  {preview.subtitle}
                                </div>
                              </div>
                              <div className="mt-[3px] text-[15px] font-semibold text-[#111316]">
                                {preview.title}
                              </div>
                              <p className="mt-[5px] text-[12px] leading-[18px] text-[#6b7280]">
                                {preview.message}
                              </p>
                              <div className="mt-[10px] flex gap-[8px]">
                                {preview.buttons.map((buttonLabel, index) => (
                                  <button
                                    key={buttonLabel}
                                    className={cn(
                                      "rounded-full px-[10px] py-[5px] text-[11px] font-medium",
                                      index === 0
                                        ? "bg-[#edf1f7] text-[#0a84ff]"
                                        : "bg-white text-[#4b5563]"
                                    )}
                                  >
                                    {buttonLabel}
                                  </button>
                                ))}
                              </div>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </GlassCard>
                </div>
              </div>

              <div className="mt-[18px] grid grid-cols-4 gap-[18px]">
                {weatherDetails.map((detail) => (
                  <DetailCard
                    key={detail.title}
                    caption={detail.caption}
                    icon={detail.icon}
                    title={detail.title}
                    value={detail.value}
                  />
                ))}
              </div>

              <GlassCard className="mt-[18px] overflow-hidden">
                <div className="flex items-center justify-between border-b border-black/[0.05] px-[18px] py-[16px]">
                  <div>
                    <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                      交互天气地图
                    </div>
                    <div className="mt-[4px] text-[22px] font-semibold tracking-[-0.03em] text-[#111316]">
                      天气图层
                    </div>
                  </div>
                  <div
                    className="flex items-center gap-[8px]"
                    data-window-no-drag
                  >
                    {(
                      [
                        "precipitation",
                        "temperature",
                        "air",
                        "wind",
                      ] as MapOverlay[]
                    ).map((overlay) => (
                      <button
                        key={overlay}
                        className={cn(
                          "rounded-full px-[10px] py-[6px] text-[11px] font-semibold capitalize",
                          mapOverlay === overlay
                            ? "bg-[#0a84ff] text-white"
                            : "bg-[#f5f6f8] text-[#4b5563]"
                        )}
                        onClick={() => setMapOverlay(overlay)}
                      >
                        {overlayLabels[overlay]}
                      </button>
                    ))}
                    <button
                      className="rounded-full bg-[#f5f6f8] px-[10px] py-[6px] text-[11px] font-semibold text-[#4b5563]"
                      onClick={() => setMapExpanded(true)}
                    >
                      展开
                    </button>
                  </div>
                </div>
                <div className="p-[18px]">
                  <div
                    className="relative h-[300px] overflow-hidden rounded-[28px] border border-white/42 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)]"
                    style={{ backgroundImage: overlayThemes[mapOverlay] }}
                  >
                    <div className="absolute inset-0 bg-[radial-gradient(circle_at_40%_42%,rgba(255,255,255,0.36)_0%,rgba(255,255,255,0)_34%),radial-gradient(circle_at_72%_34%,rgba(255,255,255,0.32)_0%,rgba(255,255,255,0)_40%),linear-gradient(180deg,rgba(255,255,255,0.08),rgba(255,255,255,0.14))]" />
                    <div className="absolute inset-[12px] rounded-[24px] border border-white/35 opacity-55" />
                    <div className="absolute top-[18px] left-[18px] rounded-[20px] bg-white/78 px-[12px] py-[10px] backdrop-blur-[16px]">
                      <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                        图层
                      </div>
                      <div className="mt-[4px] text-[14px] font-semibold text-[#111316]">
                        {overlayLabels[mapOverlay]}
                      </div>
                    </div>
                    <div className="absolute top-[18px] right-[18px] flex flex-col gap-[8px]">
                      <button className="flex size-[36px] items-center justify-center rounded-full bg-white/78 text-[#4b5563]">
                        <Plus size={16} />
                      </button>
                      <button className="flex size-[36px] items-center justify-center rounded-full bg-white/78 text-[#4b5563]">
                        <Minus size={16} />
                      </button>
                      <button
                        className="flex size-[36px] items-center justify-center rounded-full bg-white/78 text-[#4b5563]"
                        onClick={() => setSelectedLocationId(CURRENT_LOCATION_ID)}
                      >
                        <LocateFixed size={16} />
                      </button>
                    </div>
                    {mapPins.map((pin) => {
                      const location = mapLocations.find(
                        (item) => item.id === pin.id
                      )
                      if (!location) {
                        return null
                      }

                      return (
                        <button
                          key={pin.id}
                          className={cn(
                            "absolute flex -translate-x-1/2 -translate-y-1/2 items-center gap-[6px] rounded-full border border-white/55 px-[10px] py-[7px] text-[12px] font-medium shadow-[0_14px_28px_rgba(47,66,109,0.16)] backdrop-blur-[18px]",
                            location.id === selectedLocation.id
                              ? "bg-[#0a84ff] text-white"
                              : "bg-white/82 text-[#4b5563]"
                          )}
                          style={{ left: pin.left, top: pin.top }}
                          onClick={() => setSelectedLocationId(location.id)}
                        >
                          <WeatherSymbol kind={location.icon} size={16} />
                          {location.mapLabel}
                        </button>
                      )
                    })}
                    <div className="absolute right-[18px] bottom-[18px] left-[18px] rounded-[24px] bg-white/78 px-[14px] py-[12px] backdrop-blur-[18px]">
                      <div className="flex items-center justify-between text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                        <span>预报时间线</span>
                        <span>未来6小时</span>
                      </div>
                      <div className="mt-[12px] flex items-end gap-[4px]">
                        {selectedLocation.hourly
                          .slice(0, 18)
                          .map((hour, index) => (
                            <div
                              key={`${hour.hour}-${index}`}
                              className="flex-1 text-center"
                            >
                              <div
                                className="mx-auto w-full rounded-full bg-[#a8b6c7]"
                                style={{
                                  height: `${hour.precipitation ? 12 + hour.precipitation * 0.5 : 10}px`,
                                }}
                              />
                            </div>
                          ))}
                      </div>
                    </div>
                  </div>
                  <div className="mt-[14px] flex items-center justify-between rounded-[20px] border border-black/[0.04] bg-[#f6f7f9] px-[14px] py-[12px]">
                    <div className="text-[12px] leading-[18px] text-[#6b7280]">
                      点击任意地图标记可查看天气、将地点添加到侧边栏，或在地图中打开。
                    </div>
                    <div className="flex gap-[8px]">
                      <button
                        className="rounded-full bg-white px-[12px] py-[6px] text-[11px] font-semibold text-[#4b5563]"
                        onClick={() =>
                          addLocation(
                            toLocationWeather(
                              addableLocationSeeds.find(
                                (item) => item.id === "paris"
                              ) ?? addableLocationSeeds[0]
                            )
                          )
                        }
                      >
                        添加地点
                      </button>
                      <button className="rounded-full bg-[#0a84ff] px-[12px] py-[6px] text-[11px] font-semibold text-white">
                        在地图中打开
                      </button>
                    </div>
                  </div>
                </div>
              </GlassCard>

              <div className="mt-[18px] grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)_minmax(0,0.8fr)] gap-[18px]">
                <GlassCard className="p-[18px]">
                  <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                    菜单栏状态
                  </div>
                  <div className="mt-[14px] rounded-[18px] border border-black/[0.05] bg-[#f7f8fa] px-[12px] py-[10px] shadow-[inset_0_1px_0_rgba(255,255,255,0.9)]">
                    <div className="mb-[10px] rounded-[10px] bg-[#e7ebf0] px-[10px] py-[6px]">
                      <div className="flex items-center justify-between text-[11px] font-medium text-[#4b5563]">
                        <span>天气</span>
                        <span>{formatCurrentTime(currentTime)}</span>
                      </div>
                    </div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-[8px] text-[13px] font-semibold text-[#111316]">
                        <WeatherSymbol
                          className="text-[#6b7280]"
                          kind={currentLocation.icon}
                          size={18}
                        />
                        {formatTemperature(currentLocation.tempF, temperatureUnit)}
                      </div>
                      <div className="text-[11px] text-[#7b8290]">
                        {currentLocation.mapLabel}
                      </div>
                    </div>
                    <div className="mt-[9px] text-[12px] leading-[18px] text-[#6b7280]">
                      紧凑的菜单栏天气状态，与当前位置实时联动。
                    </div>
                    <button className="mt-[12px] rounded-full bg-white px-[10px] py-[6px] text-[11px] font-medium text-[#4b5563]">
                      点击打开天气
                    </button>
                  </div>
                </GlassCard>

                <GlassCard className="p-[18px]">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                        天气小组件
                      </div>
                      <div className="mt-[4px] text-[22px] font-semibold tracking-[-0.03em] text-[#111316]">
                        桌面小组件预览
                      </div>
                    </div>
                  </div>
                  <div className="mt-[16px] grid grid-cols-[120px_1fr] gap-[12px]">
                    <div className="rounded-[24px] bg-[linear-gradient(180deg,#c3ccd8_0%,#9da9bb_100%)] p-[14px] text-white shadow-[0_12px_24px_rgba(80,90,110,0.14)]">
                      <div className="text-[12px] font-medium">小</div>
                      <div className="mt-[10px] flex items-center justify-between">
                        <WeatherSymbol kind={selectedLocation.icon} size={24} />
                        <div className="text-[26px] font-semibold tracking-[-0.05em]">
                          {formatTemperature(
                            selectedLocation.tempF,
                            temperatureUnit
                          )}
                        </div>
                      </div>
                      <div className="mt-[8px] text-[11px] text-white/78">
                        {selectedLocation.name}
                      </div>
                    </div>
                    <div className="rounded-[24px] bg-white/48 p-[14px] shadow-[inset_0_1px_0_rgba(255,255,255,0.72)]">
                      <div className="text-[12px] font-medium text-[#111316]">
                        中
                      </div>
                      <div className="mt-[10px] grid grid-cols-4 gap-[8px]">
                        {selectedLocation.hourly.slice(0, 4).map((hour) => (
                          <div
                            key={hour.hour}
                            className="rounded-[18px] bg-white/72 px-[8px] py-[10px] text-center"
                          >
                            <div className="text-[10px] text-[#7b8290]">
                              {hour.hour}
                            </div>
                            <div className="mt-[6px] flex justify-center text-[#6b7280]">
                              <WeatherSymbol kind={hour.icon} size={18} />
                            </div>
                            <div className="mt-[6px] text-[12px] font-semibold text-[#111316]">
                              {formatTemperature(hour.tempF, temperatureUnit)}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                    <div className="col-span-2 rounded-[26px] bg-white/48 p-[14px] shadow-[inset_0_1px_0_rgba(255,255,255,0.72)]">
                      <div className="text-[12px] font-medium text-[#111316]">
                        大
                      </div>
                      <div className="mt-[12px] grid grid-cols-[1.1fr_0.8fr_0.8fr_0.8fr] gap-[10px]">
                        <div className="rounded-[20px] bg-white/60 p-[12px]">
                          <div className="text-[11px] tracking-[0.08em] text-[#7b8290] uppercase">
                            10日
                          </div>
                          <div className="mt-[8px] space-y-[6px]">
                            {selectedLocation.tenDay.slice(0, 4).map((day) => (
                              <div
                                key={day.day}
                                className="flex items-center justify-between text-[12px]"
                              >
                                <span className="text-[#6b7280]">
                                  {day.day}
                                </span>
                                <WeatherSymbol
                                  className="text-[#6b7280]"
                                  kind={day.icon}
                                  size={16}
                                />
                                <span className="font-medium text-[#111316]">
                                  {formatTemperature(
                                    day.highF,
                                    temperatureUnit
                                  )}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                        <div className="rounded-[20px] bg-white/60 p-[12px]">
                          <div className="text-[11px] tracking-[0.08em] text-[#7b8290] uppercase">
                            空气质量
                          </div>
                          <div className="mt-[10px] text-[26px] font-semibold tracking-[-0.05em] text-[#111316]">
                            {selectedLocation.details.airQualityScore}
                          </div>
                          <div className="text-[12px] text-[#6b7280]">
                            {selectedLocation.details.airQuality}
                          </div>
                        </div>
                        <div className="rounded-[20px] bg-white/60 p-[12px]">
                          <div className="text-[11px] tracking-[0.08em] text-[#7b8290] uppercase">
                            当前
                          </div>
                          <div className="mt-[10px] text-[26px] font-semibold tracking-[-0.05em] text-[#111316]">
                            {formatTemperature(
                              selectedLocation.tempF,
                              temperatureUnit
                            )}
                          </div>
                          <div className="text-[12px] text-[#6b7280]">
                            {selectedLocation.condition}
                          </div>
                        </div>
                        <div className="rounded-[20px] bg-white/60 p-[12px]">
                          <div className="text-[11px] tracking-[0.08em] text-[#7b8290] uppercase">
                            地点
                          </div>
                          <div className="mt-[10px] text-[18px] font-semibold tracking-[-0.04em] text-[#111316]">
                            {selectedLocation.name}
                          </div>
                          <div className="text-[12px] text-[#6b7280]">
                            基于侧边栏当前选中的城市
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </GlassCard>

                <GlassCard className="p-[18px]">
                  <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                    设置与提醒
                  </div>
                  <div className="mt-[10px] space-y-[10px]">
                    <div className="flex items-center justify-between rounded-[16px] bg-white/54 px-[12px] py-[10px]">
                      <div>
                        <div className="text-[12px] font-medium text-[#111316]">
                          恶劣天气预警
                        </div>
                        <div className="text-[11px] text-[#6b7280]">
                          政府发布的通知与预警
                        </div>
                      </div>
                      <button
                        className={cn(
                          "rounded-full px-[10px] py-[5px] text-[11px] font-semibold",
                          severeAlertsEnabled
                            ? "bg-[#0a84ff] text-white"
                            : "bg-white text-[#4b5563]"
                        )}
                        onClick={() =>
                          setSevereAlertsEnabled(
                            (currentValue) => !currentValue
                          )
                        }
                      >
                        {severeAlertsEnabled ? "开" : "关"}
                      </button>
                    </div>
                    <div className="flex items-center justify-between rounded-[16px] bg-white/54 px-[12px] py-[10px]">
                      <div>
                        <div className="text-[12px] font-medium text-[#111316]">
                          未来一小时降水
                        </div>
                        <div className="text-[11px] text-[#6b7280]">
                          即将下雨、即将下雪、即将停止降水
                        </div>
                      </div>
                      <button
                        className={cn(
                          "rounded-full px-[10px] py-[5px] text-[11px] font-semibold",
                          nextHourRainEnabled
                            ? "bg-[#0a84ff] text-white"
                            : "bg-white text-[#4b5563]"
                        )}
                        onClick={() =>
                          setNextHourRainEnabled(
                            (currentValue) => !currentValue
                          )
                        }
                      >
                        {nextHourRainEnabled ? "开" : "关"}
                      </button>
                    </div>
                    {locations.slice(0, 4).map((location) => (
                      <div
                        key={location.id}
                        className="flex items-center justify-between rounded-[16px] bg-white/54 px-[12px] py-[10px]"
                      >
                        <div>
                          <div className="text-[12px] font-medium text-[#111316]">
                            {location.name}
                          </div>
                          <div className="text-[11px] text-[#6b7280]">
                            该地点的专属预警和天气状况
                          </div>
                        </div>
                        <button
                          className={cn(
                            "rounded-full px-[10px] py-[5px] text-[11px] font-semibold",
                            notificationByLocation[
                              location.id as keyof typeof notificationByLocation
                            ]
                              ? "bg-[#e7eef8] text-[#0a84ff]"
                              : "bg-white text-[#4b5563]"
                          )}
                          onClick={() =>
                            setNotificationByLocation((currentValue) => ({
                              ...currentValue,
                              [location.id]:
                                !currentValue[
                                  location.id as keyof typeof notificationByLocation
                                ],
                            }))
                          }
                        >
                          {notificationByLocation[
                            location.id as keyof typeof notificationByLocation
                          ]
                            ? "提醒中"
                            : "已静音"}
                        </button>
                      </div>
                    ))}
                  </div>
                </GlassCard>
              </div>
            </div>
          </div>
        </div>

        {settingsOpen && (
          <div
            className="absolute top-[74px] right-[22px] z-20 w-[368px] rounded-[28px] border border-black/[0.05] bg-white/84 p-[18px] shadow-[0_22px_54px_rgba(18,27,43,0.16)] backdrop-blur-[34px]"
            data-window-no-drag
          >
            <div className="flex items-center justify-between">
              <div>
                <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                    设置
                </div>
                <div className="mt-[4px] text-[22px] font-semibold tracking-[-0.03em] text-[#111316]">
                    天气偏好设置
                </div>
              </div>
              <button
                className="rounded-full bg-[#f5f6f8] p-[7px] text-[#4b5563]"
                onClick={() => setSettingsOpen(false)}
              >
                <X size={15} />
              </button>
            </div>
            <div className="mt-[14px] flex gap-[8px]">
              {(["general", "notifications"] as SettingsTab[]).map((tab) => (
                <button
                  key={tab === "general" ? "通用" : "通知"}
                  className={cn(
                    "rounded-full px-[12px] py-[6px] text-[12px] font-semibold capitalize",
                    settingsTab === tab
                      ? "bg-[#0a84ff] text-white"
                      : "bg-[#f5f6f8] text-[#4b5563]"
                  )}
                  onClick={() => setSettingsTab(tab)}
                >
                  {tab === "general" ? "通用" : "通知"}
                </button>
              ))}
            </div>
            {settingsTab === "general" ? (
              <div className="mt-[16px] space-y-[12px]">
                <SettingRow label="气温">
                  {(["f", "c"] as TemperatureUnit[]).map((unit) => (
                    <PillOption
                      key={unit}
                      active={temperatureUnit === unit}
                      label={unit.toUpperCase()}
                      onClick={() => setTemperatureUnit(unit)}
                    />
                  ))}
                </SettingRow>
                <SettingRow label="风力">
                  {(["mph", "kmh", "bft"] as WindUnit[]).map((unit) => (
                    <PillOption
                      key={unit}
                      active={windUnit === unit}
                      label={unit}
                      onClick={() => setWindUnit(unit)}
                    />
                  ))}
                </SettingRow>
                <SettingRow label="气压">
                  {(["inHg", "hPa"] as PressureUnit[]).map((unit) => (
                    <PillOption
                      key={unit}
                      active={pressureUnit === unit}
                      label={unit}
                      onClick={() => setPressureUnit(unit)}
                    />
                  ))}
                </SettingRow>
                <SettingRow label="降水">
                  {(["in", "mm"] as PrecipitationUnit[]).map((unit) => (
                    <PillOption
                      key={unit}
                      active={precipitationUnit === unit}
                      label={unit}
                      onClick={() => setPrecipitationUnit(unit)}
                    />
                  ))}
                </SettingRow>
                <SettingRow label="能见度">
                  {(["mi", "km"] as VisibilityUnit[]).map((unit) => (
                    <PillOption
                      key={unit}
                      active={visibilityUnit === unit}
                      label={unit}
                      onClick={() => setVisibilityUnit(unit)}
                    />
                  ))}
                </SettingRow>
                <div className="rounded-[18px] bg-[#f5f6f8] p-[12px] text-[12px] leading-[18px] text-[#6b7280]">
                  快捷键：<span className="font-medium">Command+K</span> 搜索，
                  <span className="font-medium">M</span> 展开地图，
                  <span className="font-medium">N</span> 打开通知。
                </div>
              </div>
            ) : (
              <div className="mt-[16px] space-y-[10px]">
                <ToggleRow
                  active={locationServicesEnabled}
                  description="允许使用精确当前位置天气。"
                  label="使用当前位置"
                  onToggle={() =>
                    setLocationServicesEnabled((currentValue) => !currentValue)
                  }
                />
                <ToggleRow
                  active={severeAlertsEnabled}
                  description="政府发布的恶劣天气预警。"
                  label="恶劣天气预警"
                  onToggle={() =>
                    setSevereAlertsEnabled((currentValue) => !currentValue)
                  }
                />
                <ToggleRow
                  active={nextHourRainEnabled}
                  description="即将下雨和即将下雪的通知。"
                  label="未来一小时降水"
                  onToggle={() =>
                    setNextHourRainEnabled((currentValue) => !currentValue)
                  }
                />
                {locations.slice(0, 5).map((location) => (
                  <ToggleRow
                    key={location.id}
                    active={
                      notificationByLocation[
                        location.id as keyof typeof notificationByLocation
                      ] ?? false
                    }
                    description={`${location.name} 的${location.condition}更新。`}
                    label={location.name}
                    onToggle={() =>
                      setNotificationByLocation((currentValue) => ({
                        ...currentValue,
                        [location.id]: !(
                          currentValue[
                            location.id as keyof typeof notificationByLocation
                          ] ?? false
                        ),
                      }))
                    }
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {mapExpanded && (
          <div
            className="absolute inset-0 z-30 flex items-center justify-center bg-[#cfd5de]/42 px-[50px] py-[40px] backdrop-blur-[16px]"
            data-window-no-drag
          >
            <div className="relative h-full max-h-[760px] w-full max-w-[1200px] rounded-[34px] border border-black/[0.05] bg-white/88 p-[22px] shadow-[0_28px_80px_rgba(18,27,43,0.18)] backdrop-blur-[34px]">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                    展开地图
                  </div>
                  <div className="mt-[4px] text-[28px] font-semibold tracking-[-0.03em] text-[#111316]">
                    {selectedLocation.name} 的天气图层
                  </div>
                </div>
                <div className="flex items-center gap-[8px]">
                  {(
                    [
                      "precipitation",
                      "temperature",
                      "air",
                      "wind",
                    ] as MapOverlay[]
                  ).map((overlay) => (
                    <button
                      key={overlay}
                      className={cn(
                        "rounded-full px-[12px] py-[7px] text-[12px] font-semibold capitalize",
                        mapOverlay === overlay
                          ? "bg-[#0a84ff] text-white"
                          : "bg-[#f5f6f8] text-[#4b5563]"
                      )}
                      onClick={() => setMapOverlay(overlay)}
                    >
                      {overlayLabels[overlay]}
                    </button>
                  ))}
                  <button
                    className="rounded-full bg-[#f5f6f8] p-[8px] text-[#4b5563]"
                    onClick={() => setMapExpanded(false)}
                  >
                    <X size={16} />
                  </button>
                </div>
              </div>
              <div
                className="relative mt-[18px] h-[calc(100%-146px)] overflow-hidden rounded-[30px] border border-white/52"
                style={{ backgroundImage: overlayThemes[mapOverlay] }}
              >
                <div className="absolute inset-0 bg-[radial-gradient(circle_at_35%_40%,rgba(255,255,255,0.34)_0%,rgba(255,255,255,0)_30%),radial-gradient(circle_at_72%_28%,rgba(255,255,255,0.28)_0%,rgba(255,255,255,0)_34%)]" />
                {mapPins.map((pin) => {
                  const location = mapLocations.find(
                    (item) => item.id === pin.id
                  )
                  if (!location) {
                    return null
                  }

                  return (
                    <button
                      key={`expanded-${pin.id}`}
                      className={cn(
                        "absolute flex -translate-x-1/2 -translate-y-1/2 items-center gap-[7px] rounded-full border border-white/55 px-[12px] py-[8px] text-[12px] font-medium shadow-[0_18px_30px_rgba(47,66,109,0.18)] backdrop-blur-[18px]",
                        location.id === selectedLocation.id
                          ? "bg-[#0a84ff] text-white"
                          : "bg-white/82 text-[#4b5563]"
                      )}
                      style={{ left: pin.left, top: pin.top }}
                      onClick={() => setSelectedLocationId(location.id)}
                    >
                      <WeatherSymbol kind={location.icon} size={16} />
                      {location.name}
                    </button>
                  )
                })}
                <div className="absolute right-[20px] bottom-[20px] left-[20px] rounded-[26px] bg-white/82 p-[16px] backdrop-blur-[20px]">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
                        当前选中标记
                      </div>
                      <div className="mt-[4px] text-[20px] font-semibold text-[#111316]">
                        {selectedLocation.name}
                      </div>
                      <div className="mt-[4px] text-[13px] text-[#6b7280]">
                        {selectedLocation.condition} ·{" "}
                        {formatTemperature(
                          selectedLocation.tempF,
                          temperatureUnit
                        )}
                      </div>
                    </div>
                    <div className="flex gap-[8px]">
                      <button
                        className="rounded-full bg-white px-[12px] py-[7px] text-[12px] font-semibold text-[#4b5563]"
                        onClick={() => {
                          const locationToAdd = addableLocationSeeds
                            .map(toLocationWeather)
                            .find((item) => item.id === selectedLocation.id)
                          if (locationToAdd) {
                            addLocation(locationToAdd)
                          }
                        }}
                      >
                        添加到已保存
                      </button>
                      <button className="rounded-full bg-[#0a84ff] px-[12px] py-[7px] text-[12px] font-semibold text-white">
                        在地图中打开
                      </button>
                    </div>
                  </div>
                </div>
                <div className="absolute top-[20px] right-[20px] flex flex-col gap-[8px]">
                  <button className="flex size-[38px] items-center justify-center rounded-full bg-white/84 text-[#4b5563]">
                    <Plus size={16} />
                  </button>
                  <button className="flex size-[38px] items-center justify-center rounded-full bg-white/84 text-[#4b5563]">
                    <Minus size={16} />
                  </button>
                  <button className="flex size-[38px] items-center justify-center rounded-full bg-white/84 text-[#4b5563]">
                    <Map size={16} />
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

function SettingRow({
  children,
  label,
}: {
  children: ReactNode
  label: string
}) {
  return (
    <div className="rounded-[18px] bg-[#f5f6f8] px-[12px] py-[10px]">
      <div className="text-[11px] font-semibold tracking-[0.08em] text-[#7b8290] uppercase">
        {label}
      </div>
      <div className="mt-[8px] flex flex-wrap gap-[8px]">{children}</div>
    </div>
  )
}

function PillOption({
  active,
  label,
  onClick,
}: {
  active: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button
      className={cn(
        "rounded-full px-[10px] py-[6px] text-[12px] font-semibold",
        active ? "bg-[#0a84ff] text-white" : "bg-white text-[#4b5563]"
      )}
      onClick={onClick}
    >
      {label}
    </button>
  )
}

function ToggleRow({
  active,
  description,
  label,
  onToggle,
}: {
  active: boolean
  description: string
  label: string
  onToggle: () => void
}) {
  return (
    <div className="flex items-center justify-between rounded-[18px] bg-[#f5f6f8] px-[12px] py-[10px]">
      <div className="pr-[10px]">
        <div className="text-[12px] font-medium text-[#111316]">{label}</div>
        <div className="text-[11px] leading-[17px] text-[#6b7280]">
          {description}
        </div>
      </div>
      <button
        className={cn(
          "rounded-full px-[10px] py-[5px] text-[11px] font-semibold",
          active ? "bg-[#0a84ff] text-white" : "bg-white text-[#4b5563]"
        )}
        onClick={onToggle}
      >
        {active ? "开" : "关"}
      </button>
    </div>
  )
}
