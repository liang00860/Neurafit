import { useEffect, useRef, useState } from "react"
import { gsap } from "gsap"
import { CustomEase } from "gsap/CustomEase"
import { ScrambleTextPlugin } from "gsap/ScrambleTextPlugin"
import { ScrollTrigger } from "gsap/ScrollTrigger"
import { SplitText } from "gsap/SplitText"
import Lenis from "@studio-freight/lenis"
import { HERO_LOGO_SVG, HERO_STROKE_SVG } from "./cwm-original-hero-svg"
import { useResponsiveScale } from "@/hooks/use-responsive-scale"

import type { AuthResponse, AuthUser } from "@/components/auth-api"
import { AuthModal } from "@/components/auth-modal"
import { MacosDesktopExperience } from "@/components/macos-desktop-experience"
import { RenderErrorBoundary } from "@/components/render-error-boundary"

declare module "react" {
  interface HTMLAttributes<T> extends React.AriaAttributes, React.DOMAttributes<T> {
    "loader-blink"?: string
    "loader-div"?: string
    "loader-percent"?: string
    "loader-text"?: string
    heromono?: string
    navbtn?: string
  }

  interface SVGProps<T> extends React.SVGAttributes<T>, React.ClassAttributes<T> {
    barcodeline?: string
    bracket?: string
  }
}

gsap.registerPlugin(ScrollTrigger, SplitText, CustomEase, ScrambleTextPlugin)

const AUTH_STORAGE_KEY = "neurafit.authUser"
const AUTH_TOKEN_STORAGE_KEY = "neurafit.authToken"
const DEBUG_AUTH_USER: AuthUser = {
  avatarUrl: null,
  createdAt: "2026-06-05T00:00:00.000Z",
  email: "debug@neurafit.local",
  id: -1,
  lastLoginAt: "2026-06-05T00:00:00.000Z",
  phone: null,
  status: 1,
  username: "debug-motion",
}

function readStoredAuthUser(): AuthUser | null {
  if (typeof window === "undefined") {
    return null
  }

  try {
    if (new URLSearchParams(window.location.search).get("debugDesktop") === "1") {
      return DEBUG_AUTH_USER
    }
    const storedUser = window.localStorage.getItem(AUTH_STORAGE_KEY)
    return storedUser ? (JSON.parse(storedUser) as AuthUser) : null
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

function storeAuthSession(session: AuthResponse) {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session.user))
  window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, session.token)
}

function clearStoredAuthUser() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
  window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
}

const MENU_LINKS = [
  { action: "login", label: "登录", className: "u-text-style-h3" },
  {
    href: "#design-dev",
    label: "Design + Dev",
    className: "u-text-style-h3",
    children: [
      { href: "#grids-layouts", label: "2.1  Grids & layouts" },
      { href: "#typography", label: "2.2  typography" },
      { href: "#colors", label: "2.3  color" },
      { href: "#motion", label: "2.4  motion" },
    ],
  },
  { action: "register", label: "注册", className: "u-text-style-h3" },
]

function createCustomEases() {
  CustomEase.create("ease-primary", "0.87,0,0.13,1")
  CustomEase.create("ease-secondary", "0.31,0.75,0.22,1")
  CustomEase.create("ease-fade", "0.76,0,0.24,1")
  CustomEase.create("ease-preloader", ".64,.04,.42,.99")
  CustomEase.create("ease-transition", "0.16,1,0.35,1")
}

function initLenis() {
  const lenis = new Lenis({ duration: 0.7, infinite: false })

  lenis.on("scroll", ScrollTrigger.update)
  gsap.ticker.add((time) => {
    lenis.raf(time * 1000)
  })
  gsap.ticker.lagSmoothing(0)

  return () => lenis.destroy()
}

function initCursorCoordinates(root: HTMLElement) {
  const x = root.querySelector<HTMLElement>("[data-coordinates-x]")
  const y = root.querySelector<HTMLElement>("[data-coordinates-y]")

  if (!x || !y) {
    return () => undefined
  }

  const handleMove = (event: MouseEvent) => {
    x.textContent = String(Math.round(event.pageX))
    y.textContent = String(Math.round(event.pageY))
  }

  document.addEventListener("mousemove", handleMove)

  return () => document.removeEventListener("mousemove", handleMove)
}

function initMenu(root: HTMLElement) {
  const buttons = root.querySelectorAll<HTMLElement>("[navbtn]")
  const menu = root.querySelector<HTMLElement>(".nav_menu")
  const navText = root.querySelectorAll<HTMLElement>("[data-nav-text]")
  const navBarcodeLines = root.querySelectorAll(".nav_barcode [barcodeline]")
  const sharpText = root.querySelectorAll<HTMLElement>(
    ".nav_open_text, .nav_close_text, .menu_heading_link, [data-nav-text]"
  )
  const pageMain = root.querySelector<HTMLElement>(".page_main")

  if (!menu || !buttons.length) {
    return () => undefined
  }

  const keepMenuTextSharp = () => {
    gsap.set(sharpText, {
      opacity: 1,
      filter: "none",
      transform: "none",
      willChange: "auto",
    })
  }

  const labels = Array.from(navText).map((node) => node.textContent ?? "")
  const timeline = gsap.timeline({
    paused: true,
    onStart: keepMenuTextSharp,
    onComplete: keepMenuTextSharp,
    onReverseComplete: () => {
      gsap.set(menu, { display: "none", height: 0, opacity: 0 })
      gsap.set(pageMain, { opacity: 1 })
      keepMenuTextSharp()
    },
  })

  timeline
    .set(menu, {
      display: "flex",
      height: "auto",
      filter: "none",
      transform: "none",
      willChange: "height, opacity",
    })
    .set(sharpText, {
      opacity: 1,
      filter: "none",
      transform: "none",
      willChange: "auto",
    })
    .set(navBarcodeLines, { yPercent: 0 }, 0)
    .fromTo(
      menu,
      { opacity: 0, height: 0 },
      {
        opacity: 1,
        height: "calc((var(--safe-vh) * 100) - (var(--site--margin) * 2))",
        delay: 0.3,
        duration: 0.8,
        ease: "ease-secondary",
      },
      0
    )
    .to(
      navText,
      {
        stagger: 0.1,
        duration: 0.8,
        scrambleText: (index: number) => ({
          text: labels[index],
          chars: "-_x0$9",
          tweenLength: true,
        }),
      } as unknown as gsap.TweenVars,
      0
    )
    .to(
      pageMain,
      {
        delay: 0.2,
        opacity: 0.6,
        duration: 0.6,
        ease: "ease-secondary",
      },
      0
    )

  let isOpen = false
  document.body.dataset.navigationStatus = "is-closed"

  const toggle = (event: Event) => {
    event.preventDefault()
    isOpen = !isOpen
    document.body.dataset.navigationStatus = isOpen ? "is-open" : "is-closed"

    if (isOpen) {
      timeline.play()
    } else {
      timeline.reverse()
    }
  }

  buttons.forEach((button) => button.addEventListener("click", toggle))

  return () => {
    buttons.forEach((button) => button.removeEventListener("click", toggle))
    timeline.kill()
  }
}

function initLoader(root: HTMLElement) {
  const loader = root.querySelector<HTMLElement>(".loader_wrap")
  const loaderPercentWrap = root.querySelector<HTMLElement>("[loader-div]")
  const loaderText = root.querySelectorAll<HTMLElement>("[loader-text]")
  const loaderTextOriginal = Array.from(loaderText).map(
    (node) => node.textContent ?? ""
  )
  const heroMono = root.querySelectorAll<HTMLElement>("[heromono]")
  const heroMonoOriginal = Array.from(heroMono).map(
    (node) => node.textContent ?? ""
  )
  const barcodeLines = root.querySelectorAll("[barcodeline]")
  const heroLogoPaths = root.querySelectorAll(".hero-logo-path")
  const brackets = root.querySelectorAll("[bracket]")
  const loaderBlinkChars = SplitText.create("[loader-blink]", {
    type: "chars",
  }).chars
  const counter = { value: 0 }

  if (!loader || !loaderPercentWrap) {
    return () => undefined
  }

  document.body.classList.add("cwm-scroll-lock")

  gsap.set(loader, { display: "block", autoAlpha: 1 })
  gsap.set(".loader-mid-wrap, .loader_percentt, .loader_text", { autoAlpha: 0 })
  gsap.set(loaderText, { autoAlpha: 1 })
  gsap.set(heroLogoPaths, { opacity: 0 })
  gsap.set(brackets, { opacity: 0 })
  gsap.set(barcodeLines, { yPercent: -101 })
  gsap.set(heroMono, { opacity: 0 })
  gsap.set(loaderBlinkChars, { opacity: 0 })

  const blinkIn = () => {
    gsap.to(".loader-mid-wrap, .loader_percentt, .loader_text", {
      autoAlpha: 1,
      duration: 0.5,
    })

    const charTimeline = gsap.timeline()
    loaderBlinkChars.forEach((char) => {
      const charBlink = gsap.timeline()
      charBlink
        .to(char, { delay: 0.4, opacity: 1, duration: 0.04 })
        .to(char, { delay: 0.02, opacity: 0, duration: 0.03 })
        .to(char, { opacity: 1, duration: 0.04 })
        .to(char, { opacity: 1, duration: 0.05, ease: "power4.out" })
      charTimeline.add(charBlink, Math.random() * 0.3)
    })
  }

  gsap.fromTo(
    loaderPercentWrap,
    { autoAlpha: 0 },
    {
      autoAlpha: 1,
      duration: 0.3,
      ease: "steps(2)",
    }
  )

  const heroReveal = gsap.timeline({ delay: 3.5 })
  heroReveal
    .call(
      () => {
        const pathTimeline = gsap.timeline()
        heroLogoPaths.forEach((path) => {
          const pathBlink = gsap.timeline()
          pathBlink
            .to(path, { delay: 0.25, opacity: 1, duration: 0.05 })
            .to(path, { delay: 0.02, opacity: 0, duration: 0.04 })
            .to(path, { delay: 0.1, opacity: 1, duration: 0.06 })
            .to(path, { delay: 0.2, opacity: 1, duration: 0.07 })
          pathTimeline.add(pathBlink, Math.random() * 0.5)
        })

        brackets.forEach((bracket) => {
          const bracketBlink = gsap.timeline()
          bracketBlink
            .to(bracket, { delay: 0.25, opacity: 1, duration: 0.03 })
            .to(bracket, { delay: 0.02, opacity: 0, duration: 0.04 })
            .to(bracket, { delay: 0.1, opacity: 1, duration: 0.02 })
            .to(bracket, { delay: 0.01, opacity: 1, duration: 0.04 })
          pathTimeline.add(bracketBlink, Math.random() * 0.5)
        })
      },
      undefined,
      0
    )
    .to(
      heroMono,
      {
        duration: 0.00001,
        scrambleText: {
          chars: " ",
          text: " ",
          speed: 0.6,
          tweenLength: false,
          rightToLeft: false,
        },
      },
      0
    )
    .to(
      heroMono,
      {
        duration: 0.5,
        stagger: 0.1,
        opacity: 1,
        scrambleText: (index: number) => ({
          chars: " ",
          text: heroMonoOriginal[index],
          speed: 0.5,
          tweenLength: false,
          rightToLeft: false,
        }),
      } as unknown as gsap.TweenVars,
      0
    )
    .to(
      barcodeLines,
      {
        delay: 0.3,
        yPercent: 0,
        duration: 0.8,
        stagger: 0.06,
        ease: "ease-preloader",
      },
      0
    )

  const loaderTimeline = gsap.timeline({
    onComplete: () => {
      document.body.classList.remove("cwm-scroll-lock")
    },
  })

  loaderTimeline
    .call(blinkIn)
    .to(
      counter,
      {
        delay: 1.2,
        onUpdate: () => {
          const el = root.querySelector("[loader-percent]");
          if (el) {
            el.textContent = String(Math.round(counter.value));
          }
        },
        ease: "ease-preloader",
        value: 100,
        duration: 2,
      },
      0
    )
    .to(
      loaderPercentWrap,
      {
        delay: 1.2,
        duration: 1.8,
        ease: "ease-preloader",
        height: "100%",
      },
      0
    )
    .to(
      loaderText,
      {
        duration: 0.00001,
        scrambleText: {
          chars: " ",
          text: " ",
          speed: 0.6,
          tweenLength: false,
          rightToLeft: false,
        },
      },
      0
    )
    .to(
      loaderText,
      {
        delay: 0.6,
        duration: 0.2,
        stagger: 0.14,
        scrambleText: (index: number) => ({
          chars: " ",
          text: loaderTextOriginal[index],
          speed: 0.5,
          tweenLength: false,
          rightToLeft: false,
        }),
      } as unknown as gsap.TweenVars,
      0
    )
    .to(
      ".loader-dial",
      {
        duration: 0.4,
        xPercent: 100,
        stagger: 0.07,
        ease: "ease-preloader",
      },
      3
    )
    .to(
      loaderText,
      {
        delay: 3,
        duration: 0.1,
        stagger: 0.03,
        scrambleText: {
          chars: " ",
          text: " ",
          speed: 0.5,
          tweenLength: false,
          rightToLeft: true,
        },
      },
      0
    )
    .to(
      loaderBlinkChars,
      {
        stagger: { amount: 0.1, from: "random" },
        duration: 0.4,
        ease: "steps(4)",
        opacity: 0,
      },
      "<.2"
    )
    .to(
      loader,
      {
        autoAlpha: 0,
        duration: 0.3,
        ease: "steps(7)",
      },
      "<.4"
    )
    .set(loader, { display: "none" })

  return () => {
    document.body.classList.remove("cwm-scroll-lock")
    loaderTimeline.kill()
    heroReveal.kill()
  }
}

function useCreativeWebManualReplica() {
  const rootRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const root = rootRef.current

    if (!root) {
      return
    }

    createCustomEases()

    const cleanups = [
      initLenis(),
      initCursorCoordinates(root),
      initMenu(root),
      initLoader(root),
    ]

    return () => {
      cleanups.forEach((cleanup) => cleanup())
      ScrollTrigger.getAll().forEach((trigger) => trigger.kill())
      gsap.globalTimeline.clear()
    }
  }, [])

  return rootRef
}

function BarcodeSvg({ className = "hero_barcode" }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="100%"
      viewBox="0 0 333 109"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M0 109V0H5.54237V109H0ZM11.0847 109V0H16.6271V109H11.0847ZM22.1695 109V0H38.7966V109H22.1695ZM44.339 109V0H60.9661V109H44.339ZM77.5932 109V0H83.1356V109H77.5932Z"
        fill="currentColor"
        barcodeline=""
      />
      <path
        d="M83.2222 109V0H99.8493V109H83.2222ZM105.392 109V0H110.934V109H105.392ZM116.476 109V0H122.019V109H116.476ZM138.646 109V0H155.273V109H138.646ZM160.815 109V0H166.358V109H160.815Z"
        fill="currentColor"
        barcodeline=""
      />
      <path
        d="M166.444 109V0H183.072V109H166.444ZM188.614 109V0H194.156V109H188.614ZM199.699 109V0H216.326V109H199.699ZM232.953 109V0H238.495V109H232.953ZM244.038 109V0H249.58V109H244.038Z"
        fill="currentColor"
        barcodeline=""
      />
      <path
        d="M249.667 109V0H255.209V109H249.667ZM271.836 109V0H288.463V109H271.836ZM294.006 109V0H299.548V109H294.006ZM305.09 109V0H321.717V109H305.09ZM327.26 109V0H332.802V109H327.26Z"
        fill="currentColor"
        barcodeline=""
      />
    </svg>
  )
}

function BracketSvg({ className }: { className: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="100%"
      viewBox="0 0 14 14"
      fill="none"
      bracket=""
      className={className}
      aria-hidden="true"
    >
      {className === "bracket-br" && (
        <>
          <rect
            width="14"
            height="1"
            transform="matrix(0 -1 -1 0 14 14)"
            fill="currentColor"
          />
          <rect
            width="14"
            height="1"
            transform="matrix(1 0 0 -1 0 14)"
            fill="currentColor"
          />
        </>
      )}
      {className === "bracket_-bl" && (
        <>
          <rect
            width="14"
            height="1"
            transform="matrix(1 0 0 -1 0 14)"
            fill="currentColor"
          />
          <rect
            width="14"
            height="1"
            transform="matrix(0 1 1 0 0 0)"
            fill="currentColor"
          />
        </>
      )}
      {className === "bracket-tr" && (
        <>
          <rect
            width="14"
            height="1"
            transform="matrix(0 1 -1 0 14 0)"
            fill="currentColor"
          />
          <rect width="14" height="1" fill="currentColor" />
        </>
      )}
      {className === "bracket-tl" && (
        <>
          <rect width="14" height="1" fill="currentColor" />
          <rect
            width="14"
            height="1"
            transform="matrix(0 -1 1 0 0 14)"
            fill="currentColor"
          />
        </>
      )}
    </svg>
  )
}

function HeroLogoSvg() {
  return (
    <div
      aria-hidden="true"
      className="hero_svg_source"
      dangerouslySetInnerHTML={{ __html: HERO_LOGO_SVG }}
    />
  )
}

function HeroStrokeSvg() {
  return (
    <div
      aria-hidden="true"
      className="hero_svg_source"
      dangerouslySetInnerHTML={{ __html: HERO_STROKE_SVG }}
    />
  )
}

function Loader() {
  return (
    <div className="loader_wrap u-width-full">
      <div className="loader-mid-wrap">
        <div className="loader-midline" />
      </div>
      <p loader-blink="" className="loader_text">
        [<span className="text-span">status</span>:active]
      </p>
      <div className="loader-dials">
        <div className="loader-dial loader-dial-y" />
        <div className="loader-dial loader-dial-white" />
        <div className="loader-dial loader-dial-white" />
        <div className="loader-dial loader-dial-white" />
        <div className="loader-dial loader-dial-gray" />
        <div className="loader-dial loader-dial-gray" />
        <div className="loader-dial loader-dial-gray" />
        <div className="loader-dial" />
        <div className="loader-dial" />
        <div className="loader-dial" />
        <div className="loader-dial loader-dial-white" />
      </div>
      <div className="loader_percentt">
        <div loader-div="" className="loader-percent-wrap">
          <div className="loader-percent-contain">
            <h1 loader-blink="" loader-percent="" className="loader-percent">
              0
            </h1>
            <h1 loader-blink="" className="loader-percent loader-percent-icon">
              %
            </h1>
          </div>
        </div>
      </div>
      <div className="loader-details-wrap">
        <div className="loader-details-top">
          <div className="loader-details-blocks">
            <div data-pulse="" className="loader-block" />
            <div className="loader-block loader-block-stroke" />
          </div>
          <p loader-text="" className="u-mono">
            &copy;CWM &mdash; FW25
          </p>
          <div className="loader-details-text">
            <p loader-text="" className="loader-idk u-mono">
              prjct by
            </p>
            <div className="div-block-7">
              <p loader-text="" className="u-mono">
                huy + ivor
              </p>
              <p loader-text="" className="u-mono">
                [l] vn.us
              </p>
            </div>
          </div>
          <div className="loader-details-text">
            <p loader-text="" className="loader-idk u-mono">
              // site.loading
            </p>
            <div className="div-block-7">
              <p loader-text="" className="u-mono">
                [f] Scripts() {"{"}
              </p>
              <p loader-text="" className="u-mono loader-text-offset">
                initlenis();
              </p>
              <p loader-text="" className="u-mono loader-text-offset">
                initnav();
              </p>
              <p loader-text="" className="u-mono loader-text-offset">
                initloader();
              </p>
              <p loader-text="" className="u-mono">
                {"}"}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function Nav({ onAuthClick }: { onAuthClick: (mode: "login" | "register") => void }) {
  return (
    <div className="nav">
      <div className="nav_wrap">
        <div className="nav_open_btn">
          <a navbtn="" href="#" className="nav_open_text u-mono">
            Menu [<span className="nav_plus">+</span>]
          </a>
        </div>
        <div className="nav_menu u-height-full u-width-full">
          <div className="menu_links_contain">
            <div className="nav_menu_top">
              <a href="#home" className="nav_home">
                &copy;WM
              </a>
              <a navbtn="" href="#" className="nav_close_text u-mono">
                Close [<span className="nav_plus">x</span>]
              </a>
            </div>
            {MENU_LINKS.map((link) => (
              <div className="menu_link_wrap" key={link.label}>
                <a
                  data-nav-text=""
                  href={link.href || "#"}
                  onClick={(e) => {
                    if (link.action) {
                      e.preventDefault()
                      onAuthClick(link.action as "login" | "register")
                    }
                  }}
                  className={`menu_heading_link ${link.className ?? "u-mono"}`}
                >
                  {link.label}
                </a>
                {link.children && (
                  <div data-directional-hover="" className="menu_dd_links">
                    {link.children.map((child) => (
                      <a
                        data-nav-text=""
                        href={child.href}
                        className="menu_heading_link u-mono"
                        key={child.href}
                      >
                        {child.label}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
          <div className="menu_footer">
            <div className="hero_credits_wrap u-flex-vertical-nowrap u-gap-2 nav_credits u-justify-content-between u-align-items-end">
              <div className="hero_credits_text">
                <p heromono="" className="project-by u-mono mono-gray">
                  project by
                </p>
                <div className="hero_credits_links u-flex-horizontal-nowrap u-gap-2">
                  <a
                    heromono=""
                    href="https://bymonolog.com/"
                    target="_blank"
                    className="credit_link u-mono"
                  >
                    huy
                  </a>
                  <p heromono="" className="u-mono">
                    +
                  </p>
                  <a
                    heromono=""
                    href="https://www.ivorjian.com/"
                    target="_blank"
                    className="credit_link u-mono"
                  >
                    ivor
                  </a>
                </div>
              </div>
              <BarcodeSvg className="hero_barcode nav_barcode" />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function Hero() {
  return (
    <main className="page_main">
      <section id="home" className="hero_wrap u-width-full">
        <div className="section_overlay u-width-full">
          <div className="section_overlay_vertical u-height-full u-width-full u-flex-horizontal-nowrap u-justify-content-between">
            <div className="section_vert_line u-height-full" />
            <div className="section_vert_line u-height-full" />
          </div>
          <div className="section_overlay_horizontal u-width-full u-height-full u-flex-vertical-nowrap u-justify-content-between">
            <div className="section_hori_line" />
            <div className="section_hori_line" />
          </div>
        </div>
        <div className="hero_contain u-width-full u-height-full u-flex-vertical-nowrap u-justify-content-between">
          <div className="hero_logo_wrap u-width-full">
            <HeroStrokeSvg />
            <HeroLogoSvg />
            <div data-grid="" className="grid-bg" />
          </div>
          <div className="hero_bottom_wrap u-width-full u-flex-horizontal-nowrap u-justify-content-between u-align-items-end">
            <div className="hero_bl_wrap u-width-full u-flex-vertical-nowrap u-justify-content-between u-height-full">
              <div data-coordinates-wrap="" className="coordinates__inner">
                <p className="coordinates__p u-mono">
                  <span className="text-span-2">[X]</span>.
                  <span data-coordinates-x="" className="coordinates_x">
                    0
                  </span>
                  px
                </p>
                <p className="coordinates__p u-mono">
                  <span className="text-span-2">[Y]</span>.
                  <span data-coordinates-y="" className="coordinates_y">
                    0
                  </span>
                  px
                </p>
              </div>
              <div className="hero_credits_wrap u-flex-vertical-nowrap u-gap-2">
                <div className="hero_credits_text">
                  <p heromono="" className="project-by u-mono mono-gray">
                    project by
                  </p>
                  <div className="hero_credits_links u-flex-horizontal-nowrap u-gap-2">
                    <a
                      heromono=""
                      href="https://bymonolog.com/"
                      target="_blank"
                      className="credit_link u-mono"
                    >
                      huy
                    </a>
                    <p heromono="" className="credit_link u-mono">
                      +
                    </p>
                    <a
                      heromono=""
                      href="https://www.ivorjian.com/"
                      target="_blank"
                      className="credit_link u-mono"
                    >
                      ivor
                    </a>
                  </div>
                </div>
                <BarcodeSvg />
              </div>
            </div>
            <div className="hero_info_wrap u-flex-vertical-nowrap u-justify-content-between u-width-full">
              <div className="hero_info_icon u-width-full u-height-full u-flex-horizontal-nowrap u-align-items-center u-justify-content-center" />
              <BracketSvg className="bracket-br" />
              <BracketSvg className="bracket_-bl" />
              <BracketSvg className="bracket-tr" />
              <BracketSvg className="bracket-tl" />
              <p className="hero_info_head u-text-style-h4">
                The creative process of
                <br />
                crafting stand-out websites
              </p>
              <div className="hero_info_about u-width-full">
                <div className="hero_info_contain u-flex-horizontal-wrap u-justify-content-between u-align-items-start">
                  <div>
                    <p heromono="" className="u-mono">
                      active ingredients
                    </p>
                  </div>
                  <div className="hero_info_strokes_wrap u-flex-vertical-nowrap">
                    <div className="info_stroke">
                      <p heromono="" className="u-mono">
                        webflow
                      </p>
                    </div>
                    <div className="info_stroke info_no_stroke">
                      <p heromono="" className="u-mono">
                        gsap
                      </p>
                    </div>
                  </div>
                </div>
                <div className="hero_info_contain u-flex-horizontal-wrap u-justify-content-between u-align-items-start hero_info_nostroke">
                  <div>
                    <p heromono="" className="u-mono">
                      chapters
                    </p>
                  </div>
                  <div className="hero_info_strokes_wrap u-flex-vertical-nowrap">
                    <div className="info_stroke">
                      <p heromono="" className="u-mono">
                        01. 登录
                      </p>
                    </div>
                    <div className="info_stroke info_no_stroke">
                      <p heromono="" className="u-mono">
                        02. design + dev
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}

function CreativeWebManualReplica({
  onAuthConfirm,
}: {
  onAuthConfirm: (session: AuthResponse) => void
}) {
  const rootRef = useCreativeWebManualReplica()
  useResponsiveScale()
  const [authMode, setAuthMode] = useState<"login" | "register" | null>(null)

  return (
    <div ref={rootRef} className="creative-web-manual-replica">
      <Loader />
      <Nav onAuthClick={(mode) => setAuthMode(mode)} />
      <Hero />
      {authMode && (
        <AuthModal
          mode={authMode}
          onClose={() => setAuthMode(null)}
          onConfirm={onAuthConfirm}
          onSwitchMode={(mode) => setAuthMode(mode)}
        />
      )}
    </div>
  )
}

export function App() {
  const [authenticatedUser, setAuthenticatedUser] =
    useState<AuthUser | null>(readStoredAuthUser)

  if (authenticatedUser) {
    return (
      <RenderErrorBoundary>
        <MacosDesktopExperience
          onReturnToManual={() => {
            clearStoredAuthUser()
            setAuthenticatedUser(null)
          }}
        />
      </RenderErrorBoundary>
    )
  }

  return (
    <CreativeWebManualReplica
      onAuthConfirm={(session) => {
        storeAuthSession(session)
        setAuthenticatedUser(session.user)
      }}
    />
  )
}

export default App
