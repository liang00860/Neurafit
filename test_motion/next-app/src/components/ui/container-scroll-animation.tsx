"use client"
import React, { useRef } from "react"
import { useScroll, useTransform, motion, MotionValue } from "framer-motion"

export const ContainerScroll = ({
  titleComponent,
  children,
}: {
  titleComponent: string | React.ReactNode
  children: React.ReactNode
}) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const { scrollYProgress } = useScroll({
    target: containerRef,
  })
  const [isMobile, setIsMobile] = React.useState(false)

  React.useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth <= 768)
    }
    checkMobile()
    window.addEventListener("resize", checkMobile)
    return () => {
      window.removeEventListener("resize", checkMobile)
    }
  }, [])

  const scaleDimensions = () => {
    return isMobile ? [0.7, 0.9] : [1.05, 1]
  }

  const rotate = useTransform(scrollYProgress, [0, 1], [20, 0])
  const scale = useTransform(scrollYProgress, [0, 1], scaleDimensions())
  const translate = useTransform(scrollYProgress, [0, 1], [0, -100])

  return (
    <div
      className="relative flex h-[calc(60rem*var(--section-scale))] min-h-[calc(var(--safe-vh)*100)] items-center justify-center px-[var(--page-padding)] py-[var(--section-y)] md:h-[calc(80rem*var(--section-scale))]"
      ref={containerRef}
    >
      <div
        className="relative w-full py-[calc(2.5rem*var(--space-scale))] md:py-[calc(10rem*var(--space-scale))]"
        style={{
          perspective: "1000px",
        }}
      >
        <Header translate={translate} titleComponent={titleComponent} />
        <Card rotate={rotate} translate={translate} scale={scale}>
          {children}
        </Card>
      </div>
    </div>
  )
}

export const Header = ({
  translate,
  titleComponent,
}: {
  translate: MotionValue<number>
  titleComponent: React.ReactNode
}) => {
  return (
    <motion.div
      style={{
        translateY: translate,
      }}
      className="mx-auto max-w-[min(64rem,var(--screen-container))] text-center"
    >
      {titleComponent}
    </motion.div>
  )
}

import { AnimatedCards } from "@/components/ui/animated-cards"

export const Card = ({
  rotate,
  scale,
  children,
}: {
  rotate: MotionValue<number>
  scale: MotionValue<number>
  translate: MotionValue<number>
  children?: React.ReactNode
}) => {
  return (
    <motion.div
      style={{
        rotateX: rotate,
        scale,
      }}
      className="adaptive-rounded adaptive-shadow mx-auto -mt-[calc(3rem*var(--space-scale))] h-[clamp(24rem,calc(42vw*var(--component-scale)),40rem)] w-full max-w-[min(64rem,var(--screen-container))] border-[clamp(2px,calc(4px*var(--component-scale)),4px)] border-[#6C6C6C] bg-[#222222] p-[clamp(0.5rem,calc(1.5rem*var(--space-scale)),1.5rem)]"
    >
      <div className="h-full w-full overflow-hidden rounded-[clamp(0.75rem,calc(1rem*var(--component-scale)),1rem)] bg-gray-100 md:p-[calc(1rem*var(--space-scale))] dark:bg-zinc-900">
        <AnimatedCards
          title="Scroll Animations"
          description="Explore our new scroll animation features."
          image={{
            step1light1:
              "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&q=80&w=800",
            step1light2:
              "https://images.unsplash.com/photo-1501854140801-50d01698950b?auto=format&fit=crop&q=80&w=800",
            step2light1:
              "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?auto=format&fit=crop&q=80&w=800",
            step2light2:
              "https://images.unsplash.com/photo-1433086966358-54859d0ed716?auto=format&fit=crop&q=80&w=800",
            step3light:
              "https://images.unsplash.com/photo-1465146344425-f00d5f5c8f07?auto=format&fit=crop&q=80&w=800",
            step4light:
              "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&q=80&w=800",
            alt: "Features Demo",
          }}
        />
        {children}
      </div>
    </motion.div>
  )
}
