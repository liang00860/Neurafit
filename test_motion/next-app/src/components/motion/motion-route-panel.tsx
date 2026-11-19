import { Navigation2, Route } from "lucide-react"

type MotionRoutePanelProps = {
  direction: string
  distance: string
  destination: string
}

export function MotionRoutePanel({
  direction,
  distance,
  destination,
}: MotionRoutePanelProps) {
  const showMeta = Boolean(distance.trim() || direction.trim())

  return (
    <div className="flex w-[338px] items-center gap-[18px] rounded-[32px] border border-white/16 bg-[#22231f]/62 px-[24px] py-[20px] text-white shadow-[0_28px_68px_rgba(0,0,0,0.26)] backdrop-blur-[30px]">
      <div className="flex size-[68px] shrink-0 items-center justify-center rounded-[24px] bg-white/[0.08] text-white/92">
        <Route size={34} strokeWidth={1.6} />
      </div>
      <div className="min-w-0">
        <div className="text-[21px] leading-none font-semibold tracking-[-0.035em]">
          {destination}
        </div>
        {showMeta && (
          <div className="mt-[13px] flex items-center gap-[10px] text-[15px] leading-none font-medium text-white/88">
            <Navigation2 size={16} strokeWidth={2} />
            {distance.trim() ? <span>{distance}</span> : null}
            {distance.trim() && direction.trim() ? (
              <span className="h-[4px] w-[4px] rounded-full bg-white/45" />
            ) : null}
            {direction.trim() ? <span>{direction}</span> : null}
          </div>
        )}
      </div>
    </div>
  )
}
