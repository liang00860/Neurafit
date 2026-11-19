"use client"

import { useState, useEffect, useCallback } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { ArrowLeft, ArrowRight, Star } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

// Define the type for a single review
export type Review = {
  id: string | number
  name: string
  affiliation: string
  quote: string
  imageSrc: string
  thumbnailSrc: string
}

// Define the props for the slider component
interface TestimonialSliderProps {
  reviews: Review[]
  /** Optional class name for the container */
  className?: string
}

/**
 * A reusable, animated testimonial slider component.
 * It uses framer-motion for animations and is styled with
 * shadcn/ui theme variables.
 */
export const TestimonialSlider = ({
  reviews,
  className,
}: TestimonialSliderProps) => {
  const [currentIndex, setCurrentIndex] = useState(0)
  // 'direction' helps framer-motion understand slide direction (next vs. prev)
  const [direction, setDirection] = useState<"left" | "right">("right")

  const activeReview = reviews[currentIndex]

  const handleNext = useCallback(() => {
    setDirection("right")
    setCurrentIndex((prev) => (prev + 1) % reviews.length)
  }, [reviews.length])

  const handlePrev = () => {
    setDirection("left")
    setCurrentIndex((prev) => (prev - 1 + reviews.length) % reviews.length)
  }

  const handleThumbnailClick = (index: number) => {
    // Determine direction for animation
    setDirection(index > currentIndex ? "right" : "left")
    setCurrentIndex(index)
  }

  useEffect(() => {
    const timer = setInterval(() => {
      handleNext()
    }, 2000)

    return () => clearInterval(timer)
  }, [handleNext])

  // Get the next 3 reviews for the thumbnails, excluding the current one
  const thumbnailReviews = reviews
    .filter((_, i) => i !== currentIndex)
    .slice(0, 3)

  // Animation variants for the main image
  const imageVariants = {
    enter: (direction: "left" | "right") => ({
      y: direction === "right" ? "100%" : "-100%",
      opacity: 0,
    }),
    center: { y: 0, opacity: 1 },
    exit: (direction: "left" | "right") => ({
      y: direction === "right" ? "-100%" : "100%",
      opacity: 0,
    }),
  }

  // Animation variants for the text content
  const textVariants = {
    enter: (direction: "left" | "right") => ({
      x: direction === "right" ? 50 : -50,
      opacity: 0,
    }),
    center: { x: 0, opacity: 1 },
    exit: (direction: "left" | "right") => ({
      x: direction === "right" ? -50 : 50,
      opacity: 0,
    }),
  }

  return (
    <div
      className={cn(
        "screen-section relative min-h-[clamp(34rem,calc(40rem*var(--section-scale)),44rem)] w-full overflow-hidden bg-black text-white",
        className
      )}
    >
      <div className="screen-container grid h-full grid-cols-1 gap-[var(--component-gap)] md:grid-cols-12">
        {/* === Left Column: Meta and Thumbnails === */}
        <div className="order-2 flex flex-col justify-between md:order-1 md:col-span-3">
          <div className="flex flex-row justify-between space-x-4 md:flex-col md:justify-start md:space-y-4 md:space-x-0">
            {/* Pagination */}
            <span className="font-mono text-sm text-gray-400">
              {String(currentIndex + 1).padStart(2, "0")} /{" "}
              {String(reviews.length).padStart(2, "0")}
            </span>
            {/* Vertical "Reviews" Text */}
            <h2 className="hidden text-sm font-medium tracking-widest uppercase [writing-mode:vertical-rl] md:block md:rotate-180">
              Reviews
            </h2>
          </div>

          {/* Thumbnail Navigation */}
          <div className="mt-8 flex space-x-2 md:mt-0">
            {thumbnailReviews.map((review) => {
              // Find the original index to navigate to
              const originalIndex = reviews.findIndex((r) => r.id === review.id)
              return (
                <button
                  key={review.id}
                  onClick={() => handleThumbnailClick(originalIndex)}
                  className="h-[clamp(5rem,calc(6rem*var(--component-scale)),6.5rem)] w-[clamp(4rem,calc(5rem*var(--component-scale)),5.5rem)] overflow-hidden rounded-md opacity-70 transition-opacity duration-300 hover:opacity-100 focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background focus:outline-none"
                  aria-label={`View review from ${review.name}`}
                >
                  <img
                    src={review.thumbnailSrc}
                    alt={review.name}
                    className="h-full w-full object-cover"
                  />
                </button>
              )
            })}
          </div>
        </div>

        {/* === Center Column: Main Image === */}
        <div className="relative order-1 h-[clamp(20rem,calc(36rem*var(--component-scale)),34rem)] min-h-[clamp(20rem,calc(28rem*var(--component-scale)),32rem)] md:order-2 md:col-span-4">
          <AnimatePresence initial={false} custom={direction}>
            <motion.img
              key={currentIndex}
              src={activeReview.imageSrc}
              alt={activeReview.name}
              custom={direction}
              variants={imageVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ duration: 0.6, ease: [0.4, 0, 0.2, 1] }} // Cubic bezier for smooth ease
              className="absolute inset-0 h-full w-full rounded-lg object-cover"
            />
          </AnimatePresence>
        </div>

        {/* === Right Column: Text and Navigation === */}
        <div className="order-3 flex flex-col justify-between md:order-3 md:col-span-5 md:pl-[calc(2rem*var(--space-scale))]">
          {/* Text Content */}
          <div className="relative min-h-[clamp(12rem,calc(14rem*var(--component-scale)),16rem)] overflow-hidden pt-[calc(1rem*var(--space-scale))] md:pt-[calc(6rem*var(--space-scale))]">
            <AnimatePresence initial={false} custom={direction} mode="wait">
              <motion.div
                key={currentIndex}
                custom={direction}
                variants={textVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={{ duration: 0.6, ease: [0.4, 0, 0.2, 1] }}
              >
                <div className="mb-2 flex items-center space-x-1">
                  {[...Array(5)].map((_, i) => (
                    <Star
                      key={i}
                      className="h-4 w-4 fill-yellow-400 text-yellow-400"
                    />
                  ))}
                  <span className="ml-2 text-sm font-bold text-white">5.0</span>
                </div>
                <p className="text-sm font-medium text-gray-400">
                  {activeReview.affiliation}
                </p>
                <h3 className="mt-1 text-xl font-semibold text-white">
                  {activeReview.name}
                </h3>
                <blockquote className="mt-[calc(1.5rem*var(--space-scale))] text-[clamp(1.375rem,calc(2rem*var(--font-scale)),2.25rem)] leading-snug font-medium text-white">
                  "{activeReview.quote}"
                </blockquote>
              </motion.div>
            </AnimatePresence>
          </div>

          {/* Navigation Buttons */}
          <div className="mt-[calc(2rem*var(--space-scale))] flex items-center space-x-2 md:mt-0">
            <Button
              variant="outline"
              size="icon"
              className="size-[clamp(2.75rem,calc(3rem*var(--component-scale)),3.25rem)] rounded-full border-gray-600 bg-transparent text-white hover:bg-gray-800"
              onClick={handlePrev}
              aria-label="Previous review"
            >
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <Button
              variant="default"
              size="icon"
              className="size-[clamp(2.75rem,calc(3rem*var(--component-scale)),3.25rem)] rounded-full bg-white text-black hover:bg-gray-200"
              onClick={handleNext}
              aria-label="Next review"
            >
              <ArrowRight className="h-5 w-5" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
