import { useState } from "react"

import MinimalistHeroDemo from "@/components/demo"
import { FeatureShowcase } from "@/components/feature-showcase"
import Footer from "@/components/Footer"
import { useResponsiveScale } from "@/hooks/use-responsive-scale"
import { MacosDesktopExperience } from "@/components/macos-desktop-experience"
import { RenderErrorBoundary } from "@/components/render-error-boundary"
import { BentoGridShowcase } from "@/components/ui/bento-grid-showcase"
import { ContainerScroll } from "@/components/ui/container-scroll-animation"
import { TestimonialSlider } from "@/components/ui/testimonial-slider"
import { ZoomParallax } from "@/components/ui/zoom-parallax"

const PARALLAX_IMAGES = [
  {
    src: "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&q=80&w=3506&ixlib=rb-4.0.3",
    alt: "Nature 1",
  },
  {
    src: "https://images.unsplash.com/photo-1501854140801-50d01698950b?auto=format&fit=crop&q=80&w=3450&ixlib=rb-4.0.3",
    alt: "Nature 2",
  },
  {
    src: "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?auto=format&fit=crop&q=80&w=3540&ixlib=rb-4.0.3",
    alt: "Nature 3",
  },
  {
    src: "https://images.unsplash.com/photo-1433086966358-54859d0ed716?auto=format&fit=crop&q=80&w=3450&ixlib=rb-4.0.3",
    alt: "Nature 4",
  },
  {
    src: "https://images.unsplash.com/photo-1465146344425-f00d5f5c8f07?auto=format&fit=crop&q=80&w=3552&ixlib=rb-4.0.3",
    alt: "Nature 5",
  },
  {
    src: "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?auto=format&fit=crop&q=80&w=3540&ixlib=rb-4.0.3",
    alt: "Nature 6",
  },
  {
    src: "https://images.unsplash.com/photo-1426604966848-d7adac402bff?auto=format&fit=crop&q=80&w=3540&ixlib=rb-4.0.3",
    alt: "Nature 7",
  },
]

const TESTIMONIALS = [
  {
    id: 1,
    name: "Sarah Johnson",
    affiliation: "Creative Director, Studio X",
    quote:
      "The animations and interactions built into this framework have completely transformed how we deliver projects to our clients. It's incredibly smooth.",
    imageSrc:
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=800",
    thumbnailSrc:
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200",
  },
  {
    id: 2,
    name: "Michael Chen",
    affiliation: "Lead Developer, TechFlow",
    quote:
      "I've never worked with a UI library that feels this polished out of the box. The attention to detail in the micro-interactions is outstanding.",
    imageSrc:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=800",
    thumbnailSrc:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=200",
  },
  {
    id: 3,
    name: "Emma Williams",
    affiliation: "Product Designer, Innovate",
    quote:
      "Finally, a set of components that bridge the gap between design and engineering. The bento grid alone saved us weeks of custom development.",
    imageSrc:
      "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=800",
    thumbnailSrc:
      "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=200",
  },
  {
    id: 4,
    name: "David Smith",
    affiliation: "Founder, Startup Hub",
    quote:
      "The visual impact of the scroll animations helped us increase our landing page conversion rate by 40%. Highly recommended.",
    imageSrc:
      "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=800",
    thumbnailSrc:
      "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=200",
  },
]

export function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)

  useResponsiveScale()

  if (isAuthenticated) {
    return (
      <RenderErrorBoundary>
        <MacosDesktopExperience />
      </RenderErrorBoundary>
    )
  }

  return (
    <div className="flex min-h-screen flex-col overflow-x-clip">
      <MinimalistHeroDemo onLoginSuccess={() => setIsAuthenticated(true)} />
      <ZoomParallax images={PARALLAX_IMAGES} />
      <ContainerScroll
        titleComponent={
          <>
            <h1 className="text-[clamp(2rem,calc(3vw*var(--font-scale)),3.75rem)] font-semibold text-black dark:text-white">
              Unleash the power of <br />
              <span className="mt-1 block text-[clamp(2.75rem,calc(6vw*var(--font-scale)),6rem)] leading-none font-bold">
                Scroll Animations
              </span>
            </h1>
          </>
        }
      >
        <div className="hidden">
          <img
            src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&q=80&w=3540&ixlib=rb-4.0.3"
            alt="hero"
            height={720}
            width={1400}
            className="mx-auto h-full rounded-2xl object-cover object-left-top"
            draggable={false}
          />
        </div>
      </ContainerScroll>
      <FeatureShowcase
        title="Showcase Your App"
        description="A beautiful layout to highlight the key features of your mobile or web application. Features an interactive accordion and tabbed media."
        tabs={[
          {
            value: "tab-1",
            label: "Mockup",
            src: "https://images.unsplash.com/photo-1611162617474-5b21e879e113?auto=format&fit=crop&q=80&w=1000&ixlib=rb-4.0.3",
          },
          {
            value: "tab-2",
            label: "Screen",
            src: "https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&q=80&w=1000&ixlib=rb-4.0.3",
          },
          {
            value: "tab-3",
            label: "Abstract",
            src: "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&q=80&w=1000&ixlib=rb-4.0.3",
          },
        ]}
      />
      <div className="screen-section">
        <h2 className="mb-[calc(2.5rem*var(--space-scale))] text-center text-[clamp(1.875rem,calc(3vw*var(--font-scale)),3.25rem)] font-bold">
          Bento Grid Features
        </h2>
        <BentoGridShowcase
          integration={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Integration Slot (Span 2 rows)
            </div>
          }
          trackers={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Trackers Slot
            </div>
          }
          statistic={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Statistic Slot
            </div>
          }
          focus={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Focus Slot
            </div>
          }
          productivity={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Productivity Slot
            </div>
          }
          shortcuts={
            <div className="adaptive-rounded flex h-full w-full flex-col items-center justify-center border border-neutral-200 bg-neutral-100 p-[var(--component-pad)] text-center text-[clamp(0.875rem,calc(1rem*var(--font-scale)),1.125rem)] text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900">
              Shortcuts Slot (Span 2 cols)
            </div>
          }
        />
      </div>
      <TestimonialSlider reviews={TESTIMONIALS} />
      <Footer
        leftLinks={[
          { href: "#", label: "Home" },
          { href: "#", label: "About" },
          { href: "#", label: "Features" },
        ]}
        rightLinks={[
          { href: "#", label: "Privacy Policy" },
          { href: "#", label: "Terms of Service" },
        ]}
        copyrightText="© 2026 Your Company. All rights reserved."
      />
    </div>
  )
}

export default App
