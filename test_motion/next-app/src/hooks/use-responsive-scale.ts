import { useEffect } from "react"

type ScreenTier = "compact" | "tablet" | "desktop" | "wide"

type ViewportSnapshot = {
  width: number
  height: number
  screenWidth: number
  screenHeight: number
  dpr: number
  viewportZoom: number
}

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max)

const getViewportSnapshot = (): ViewportSnapshot => {
  const visualViewport = window.visualViewport

  return {
    width: Math.max(1, visualViewport?.width ?? window.innerWidth),
    height: Math.max(1, visualViewport?.height ?? window.innerHeight),
    screenWidth: window.screen?.width ?? window.innerWidth,
    screenHeight: window.screen?.height ?? window.innerHeight,
    dpr: window.devicePixelRatio || 1,
    viewportZoom: visualViewport?.scale ?? 1,
  }
}

const getScreenTier = (width: number): ScreenTier => {
  if (width < 640) return "compact"
  if (width < 1024) return "tablet"
  if (width >= 1680) return "wide"
  return "desktop"
}

const getDesignBase = (width: number, height: number) => {
  const compactLandscape = width < 1024 && height < 560

  if (width < 640) {
    return {
      width: 390,
      height: 844,
      min: compactLandscape ? 0.74 : 0.82,
      max: 1.08,
    }
  }

  if (width < 1024) {
    return {
      width: 768,
      height: 1024,
      min: compactLandscape ? 0.74 : 0.84,
      max: 1.1,
    }
  }

  return {
    width: 1440,
    height: 900,
    min: height < 720 ? 0.78 : 0.82,
    max: 1.18,
  }
}

const calculateScale = ({ width, height }: ViewportSnapshot) => {
  const base = getDesignBase(width, height)
  const widthRatio = width / base.width
  const heightRatio = height / base.height
  const rawScale = Math.min(widthRatio, heightRatio * 1.08)
  const scale = clamp(rawScale, base.min, base.max)

  return {
    scale,
    fontScale: clamp(scale, width < 640 ? 0.9 : 0.86, 1.12),
    spaceScale: clamp(scale, width < 640 ? 0.86 : 0.8, 1.16),
    componentScale: clamp(scale, width < 640 ? 0.84 : 0.78, 1.2),
    sectionScale: clamp(scale, width < 640 ? 0.82 : 0.8, 1.14),
  }
}

export function useResponsiveScale() {
  useEffect(() => {
    let frame = 0
    const root = document.documentElement

    const updateScale = () => {
      frame = 0
      const snapshot = getViewportSnapshot()
      const tier = getScreenTier(snapshot.width)
      const orientation =
        snapshot.width >= snapshot.height ? "landscape" : "portrait"
      const { scale, fontScale, spaceScale, componentScale, sectionScale } =
        calculateScale(snapshot)

      root.dataset.screenTier = tier
      root.dataset.screenOrientation = orientation
      root.style.setProperty(
        "--viewport-width",
        `${snapshot.width.toFixed(2)}px`
      )
      root.style.setProperty(
        "--viewport-height",
        `${snapshot.height.toFixed(2)}px`
      )
      root.style.setProperty(
        "--safe-vh",
        `${(snapshot.height / 100).toFixed(4)}px`
      )
      root.style.setProperty("--screen-width", `${snapshot.screenWidth}px`)
      root.style.setProperty("--screen-height", `${snapshot.screenHeight}px`)
      root.style.setProperty("--device-pixel-ratio", snapshot.dpr.toFixed(3))
      root.style.setProperty(
        "--viewport-zoom",
        snapshot.viewportZoom.toFixed(3)
      )
      root.style.setProperty("--screen-scale", scale.toFixed(4))
      root.style.setProperty("--font-scale", fontScale.toFixed(4))
      root.style.setProperty("--space-scale", spaceScale.toFixed(4))
      root.style.setProperty("--component-scale", componentScale.toFixed(4))
      root.style.setProperty("--section-scale", sectionScale.toFixed(4))
    }

    const scheduleUpdate = () => {
      if (frame) return
      frame = window.requestAnimationFrame(updateScale)
    }

    updateScale()
    window.addEventListener("resize", scheduleUpdate)
    window.addEventListener("orientationchange", scheduleUpdate)
    window.visualViewport?.addEventListener("resize", scheduleUpdate)
    window.visualViewport?.addEventListener("scroll", scheduleUpdate)

    return () => {
      if (frame) window.cancelAnimationFrame(frame)
      window.removeEventListener("resize", scheduleUpdate)
      window.removeEventListener("orientationchange", scheduleUpdate)
      window.visualViewport?.removeEventListener("resize", scheduleUpdate)
      window.visualViewport?.removeEventListener("scroll", scheduleUpdate)
    }
  }, [])
}
