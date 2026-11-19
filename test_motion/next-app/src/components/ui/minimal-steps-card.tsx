import { useEffect, useMemo, type ReactNode } from "react"
import { motion, useSpring, useTransform, type Variants } from "framer-motion"

import { cn } from "@/lib/utils"

interface BarData {
  value: number
}

export interface MinimalStepsCardProps {
  className?: string
  data: BarData[]
  icon: ReactNode
  stepsUnit?: string
  subtitle: string
  title: string
  totalSteps: number
}

export function MinimalStepsCard({
  title,
  subtitle,
  totalSteps,
  stepsUnit = "步",
  icon,
  data = [],
  className,
}: MinimalStepsCardProps) {
  const maxValue = useMemo(
    () => Math.max(...data.map((item) => item.value), 1),
    [data]
  )

  const animatedSteps = useSpring(0, {
    damping: 15,
    mass: 0.8,
    stiffness: 75,
  })
  const displaySteps = useTransform(animatedSteps, (value) =>
    new Intl.NumberFormat("en-US").format(Math.round(value))
  )

  useEffect(() => {
    animatedSteps.set(totalSteps)
  }, [animatedSteps, totalSteps])

  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.03,
      },
    },
  }

  const barVariants: Variants = {
    hidden: { height: "0%" },
    visible: (customHeight: string) => ({
      height: customHeight,
      transition: {
        damping: 12,
        stiffness: 100,
        type: "spring",
      },
    }),
  }

  return (
    <div
      className={cn(
        "flex w-full max-w-md flex-col rounded-[34px] border border-black/[0.06] bg-white p-[24px] text-[#101010] shadow-[0_24px_60px_rgba(0,0,0,0.14)]",
        className
      )}
      role="figure"
      aria-label={`${title}，${subtitle}：${totalSteps}${stepsUnit}`}
    >
      <header className="flex items-start justify-between gap-[20px]">
        <div>
          <h2 className="text-[24px] leading-none font-medium tracking-[-0.035em] text-[#111111]">
            {title}
          </h2>
          <p className="mt-[8px] text-[14px] leading-none font-normal text-[#777777]">
            {subtitle}
          </p>
        </div>
        <div className="flex size-[42px] items-center justify-center rounded-full bg-[#f3f3f3] text-[#111111]">
          {icon}
        </div>
      </header>

      <div className="my-[28px]" aria-live="polite">
        <motion.span className="text-[48px] leading-none font-semibold tracking-[-0.055em] text-[#101010]">
          {displaySteps}
        </motion.span>
        <span className="ml-[8px] text-[18px] leading-none font-normal tracking-[-0.02em] text-[#777777]">
          {stepsUnit}
        </span>
      </div>

      <motion.div
        className="mt-auto flex h-[128px] w-full items-end gap-[5px]"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
        aria-label="每日步数柱状图"
      >
        {data.map((item, index) => {
          const barHeight = `${(item.value / maxValue) * 100}%`

          return (
            <div
              key={`${item.value}-${index}`}
              className="relative h-full w-full rounded-t-[5px]"
              aria-label={`第${index + 1}天：${item.value}${stepsUnit}`}
            >
              <div className="absolute bottom-0 h-full w-full rounded-t-[5px] bg-[#f1f1f1]" />
              <motion.div
                className="absolute bottom-0 w-full rounded-t-[5px] bg-[#101010]"
                variants={barVariants}
                custom={barHeight}
              />
            </div>
          )
        })}
      </motion.div>
    </div>
  )
}
