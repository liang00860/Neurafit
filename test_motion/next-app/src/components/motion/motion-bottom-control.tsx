import { Pause, Play } from "lucide-react"

type MotionBottomControlProps = {
  isActive: boolean
  onPause: () => void
  onResume: () => void
}

export function MotionBottomControl({
  isActive,
  onPause,
  onResume,
}: MotionBottomControlProps) {
  return (
    <button
      className="absolute bottom-[28px] left-1/2 z-[7] flex size-[76px] -translate-x-1/2 items-center justify-center rounded-full border border-white/30 bg-white/[0.24] text-white shadow-[0_26px_70px_rgba(0,0,0,0.28)] backdrop-blur-[30px] transition-transform duration-200 hover:scale-[1.04] active:scale-95"
      data-window-no-drag
      type="button"
      aria-label={isActive ? "Pause session" : "Start demo session"}
      onClick={isActive ? onPause : onResume}
    >
      {isActive ? (
        <Pause size={26} fill="currentColor" strokeWidth={1.8} />
      ) : (
        <Play
          className="ml-[3px]"
          size={27}
          fill="currentColor"
          strokeWidth={1.8}
        />
      )}
    </button>
  )
}
