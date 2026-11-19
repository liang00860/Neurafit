import { useState } from "react"
import { MinimalistHero } from "@/components/minimalist-hero" // Adjusted import path
import { LoginPage } from "@/components/login-page"
import { AnimatePresence } from "framer-motion"

interface MinimalistHeroDemoProps {
  onLoginSuccess?: () => void
}

const MinimalistHeroDemo = ({ onLoginSuccess }: MinimalistHeroDemoProps) => {
  const [showLogin, setShowLogin] = useState(false)

  const navLinks = [
    { label: "HOME", href: "#" },
    { label: "PRODUCT", href: "#" },
    { label: "STORE", href: "#" },
    { label: "ABOUT US", href: "#" },
    {
      label: "LOGIN",
      href: "#",
      onClick: (e: React.MouseEvent) => {
        e.preventDefault()
        setShowLogin(true)
      },
    },
  ]

  return (
    <>
      <MinimalistHero
        logoText="mnmlst."
        navLinks={navLinks}
        mainText="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum ultrices, justo vel tempus."
        readMoreLink="#"
        imageSrc="https://ik.imagekit.io/fpxbgsota/image%2013.png?updatedAt=1753531863793"
        imageAlt="A portrait of a person in a black turtleneck, in profile."
        overlayText={{
          part1: "less is",
          part2: "more.",
        }}
      />
      <AnimatePresence>
        {showLogin && (
          <LoginPage
            onClose={() => setShowLogin(false)}
            onLoginSuccess={onLoginSuccess}
          />
        )}
      </AnimatePresence>
    </>
  )
}

export default MinimalistHeroDemo
