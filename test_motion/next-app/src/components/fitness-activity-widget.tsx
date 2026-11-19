import { useEffect, useState } from "react"
import { Clock3, Hourglass, MoreHorizontal, Star } from "lucide-react"

import {
  fetchTrainingPlanActivity,
  type TrainingPlanActivity,
} from "@/components/training-plan-api"

const defaultActivityDates = [
  { date: "17", day: "日", selected: true },
  { date: "18", day: "一" },
  { date: "19", day: "二" },
  { date: "20", day: "三" },
  { date: "21", day: "四" },
  { date: "22", day: "五" },
  { date: "23", day: "六" },
]

const defaultActivityRows = [
  {
    icon: Star,
    label: "次数",
    value: "第10次",
  },
  {
    icon: Clock3,
    label: "今日训练",
    value: "31m 04s",
  },
  {
    icon: Hourglass,
    label: "总时长",
    value: "120m 45s",
  },
]

const rowIcons = [Star, Clock3, Hourglass]

type FitnessActivityWidgetProps = {
  className?: string
}

export function FitnessActivityWidget({
  className = "",
}: FitnessActivityWidgetProps) {
  const [activity, setActivity] = useState<TrainingPlanActivity>(null)

  useEffect(() => {
    let ignore = false

    function loadActivity() {
      fetchTrainingPlanActivity()
        .then((nextActivity) => {
          if (!ignore) {
            setActivity(nextActivity)
          }
        })
        .catch(() => {
          if (!ignore) {
            setActivity(null)
          }
        })
    }

    loadActivity()
    window.addEventListener("focus", loadActivity)
    const interval = window.setInterval(loadActivity, 5_000)

    return () => {
      ignore = true
      window.removeEventListener("focus", loadActivity)
      window.clearInterval(interval)
    }
  }, [])

  const activityDates = activity?.dates?.length
    ? activity.dates
    : defaultActivityDates
  const activityRows = activity?.rows?.length
    ? activity.rows.map((row, index) => ({
        ...row,
        icon: rowIcons[index] ?? Star,
      }))
    : defaultActivityRows

  return (
    <section
      className={`w-[420px] rounded-[34px] bg-white px-[31px] pt-[28px] pb-[27px] text-[#111111] shadow-[0_24px_60px_rgba(0,0,0,0.16)] ${className}`}
      aria-label="每日活动健身面板"
    >
      <div className="flex items-start justify-between gap-[20px]">
        <h2 className="text-[48px] leading-[1.08] font-medium tracking-[-0.04em] text-[#101010]">
          Daily activity
        </h2>
        <MoreHorizontal
          aria-hidden="true"
          className="mt-[5px] text-[#0b0b0b]"
          size={28}
          strokeWidth={3}
        />
      </div>

      <div className="mt-[54px] grid grid-cols-7 text-center">
        {activityDates.map((item, index) => (
          <div
            key={item.date}
            className={`relative flex min-w-0 flex-col items-center ${
              index > 0
                ? "before:absolute before:top-[17px] before:left-0 before:h-[35px] before:w-px before:bg-[#ececec]"
                : ""
            }`}
          >
            <span
              className={`flex size-[38px] items-center justify-center rounded-full text-[17px] leading-none tracking-[-0.02em] ${
                item.selected
                  ? "bg-[#101010] font-medium text-white"
                  : "font-normal text-[#303030]"
              }`}
            >
              {item.date}
            </span>
            <span className="mt-[9px] text-[16px] leading-none font-normal text-[#6f6f6f]">
              {item.day}
            </span>
          </div>
        ))}
      </div>

      <div className="mt-[38px]">
        {activityRows.map((row, index) => {
          const Icon = row.icon

          return (
            <div
              key={row.label}
              className={`flex items-center justify-between gap-[18px] py-[15px] ${
                index > 0 ? "border-t border-[#ececec]" : ""
              }`}
            >
              <div className="inline-flex h-[30px] items-center gap-[8px] rounded-full border border-[#151515] px-[10px] text-[16px] leading-none font-normal tracking-[-0.03em] text-[#121212]">
                <Icon size={15} strokeWidth={1.8} aria-hidden="true" />
                <span>{row.value}</span>
              </div>
              <div className="text-right text-[18px] leading-none font-normal tracking-[-0.025em] text-[#171717]">
                {row.label}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
