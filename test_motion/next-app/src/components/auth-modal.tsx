import { useEffect, useRef, useState, type FormEvent } from "react"
import { gsap } from "gsap"

import {
  loginUser,
  registerUser,
  type AuthResponse,
} from "@/components/auth-api"

type AuthMode = "login" | "register"

export function AuthModal({
  mode,
  onClose,
  onConfirm,
  onSwitchMode,
}: {
  mode: AuthMode
  onClose: () => void
  onConfirm: (session: AuthResponse) => void
  onSwitchMode: (mode: AuthMode) => void
}) {
  const modalRef = useRef<HTMLDivElement>(null)
  const contentRef = useRef<HTMLDivElement>(null)
  const [account, setAccount] = useState("")
  const [error, setError] = useState("")
  const [name, setName] = useState("")
  const [password, setPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    setError("")
  }, [mode])

  useEffect(() => {
    if (!modalRef.current || !contentRef.current) return

    const tl = gsap.timeline()
    tl.set(modalRef.current, { autoAlpha: 1 })
      .fromTo(
        modalRef.current,
        { backdropFilter: "blur(0px)", backgroundColor: "rgba(0,0,0,0)" },
        {
          backdropFilter: "blur(8px)",
          backgroundColor: "rgba(0,0,0,0.8)",
          duration: 0.4,
          ease: "power2.out",
        }
      )
      .fromTo(
        contentRef.current,
        { y: 50, autoAlpha: 0 },
        { y: 0, autoAlpha: 1, duration: 0.5, ease: "ease-secondary" },
        "<0.1"
      )

    return () => {
      tl.kill()
    }
  }, [])

  const handleClose = () => {
    if (!modalRef.current || !contentRef.current || submitting) return

    const tl = gsap.timeline({
      onComplete: onClose,
    })
    tl.to(contentRef.current, {
      y: 20,
      autoAlpha: 0,
      duration: 0.3,
      ease: "power2.in",
    }).to(
      modalRef.current,
      {
        backdropFilter: "blur(0px)",
        backgroundColor: "rgba(0,0,0,0)",
        duration: 0.3,
        ease: "power2.in",
      },
      "<"
    )
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError("")
    setSubmitting(true)

    try {
      const response =
        mode === "login"
          ? await loginUser({
              identifier: account.trim(),
              password,
            })
          : await registerUser({
              email: account.trim(),
              password,
              username: name.trim(),
            })

      setSubmitting(false)
      onConfirm(response)
    } catch (requestError) {
      setSubmitting(false)
      setError(
        requestError instanceof Error
          ? requestError.message
          : "请求失败，请稍后重试"
      )
    }
  }

  return (
    <div
      ref={modalRef}
      className="fixed inset-0 z-[3000] flex items-center justify-center p-4"
      style={{ visibility: "hidden" }}
    >
      <button
        aria-label="关闭登录弹窗"
        className="absolute inset-0 cursor-pointer"
        type="button"
        onClick={handleClose}
      />

      <div
        ref={contentRef}
        className="relative w-full max-w-md bg-[#070707] p-8 text-[#eaeaea] md:p-12"
        style={{ border: "1px solid #262626" }}
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          className="absolute top-0 left-0 text-[#eaeaea]"
          aria-hidden="true"
        >
          <rect width="14" height="1" fill="currentColor" />
          <rect
            width="14"
            height="1"
            transform="matrix(0 -1 1 0 0 14)"
            fill="currentColor"
          />
        </svg>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          className="absolute top-0 right-0 text-[#eaeaea]"
          aria-hidden="true"
        >
          <rect
            width="14"
            height="1"
            transform="matrix(0 1 -1 0 14 0)"
            fill="currentColor"
          />
          <rect width="14" height="1" fill="currentColor" />
        </svg>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          className="absolute bottom-0 left-0 text-[#eaeaea]"
          aria-hidden="true"
        >
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
        </svg>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          className="absolute right-0 bottom-0 text-[#eaeaea]"
          aria-hidden="true"
        >
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
        </svg>

        <button
          className="absolute top-4 right-4 text-[0.625rem] tracking-wider text-[#515151] uppercase transition-colors hover:text-[#eaeaea] disabled:opacity-40"
          disabled={submitting}
          style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
          type="button"
          onClick={handleClose}
        >
          [ CLOSE ]
        </button>

        <h2 className="mb-10 text-4xl font-normal tracking-tight lowercase md:text-5xl">
          {mode === "login" ? "登录" : "注册"}
        </h2>

        <form className="flex flex-col gap-6" onSubmit={handleSubmit}>
          {mode === "register" && (
            <div className="flex flex-col gap-2">
              <label
                className="text-[0.625rem] tracking-wider text-[#515151] uppercase"
                htmlFor="auth-username"
                style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
              >
                用户名 // [REQUIRED]
              </label>
              <input
                id="auth-username"
                className="w-full rounded-none border-b border-[#262626] bg-transparent py-2 text-[#eaeaea] transition-colors focus:border-[#eaeaea] focus:outline-none"
                placeholder="your name"
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </div>
          )}

          <div className="flex flex-col gap-2">
            <label
              className="text-[0.625rem] tracking-wider text-[#515151] uppercase"
              htmlFor="auth-account"
              style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
            >
              {mode === "login"
                ? "账号 / 邮箱 / 手机号 // [REQUIRED]"
                : "邮箱 // [REQUIRED]"}
            </label>
            <input
              id="auth-account"
              className="w-full rounded-none border-b border-[#262626] bg-transparent py-2 text-[#eaeaea] transition-colors focus:border-[#eaeaea] focus:outline-none"
              placeholder={mode === "login" ? "username / email / phone" : "hello@example.com"}
              type={mode === "login" ? "text" : "email"}
              value={account}
              onChange={(event) => setAccount(event.target.value)}
            />
          </div>

          <div className="flex flex-col gap-2">
            <label
              className="text-[0.625rem] tracking-wider text-[#515151] uppercase"
              htmlFor="auth-password"
              style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
            >
              密码 // [REQUIRED]
            </label>
            <input
              id="auth-password"
              className="w-full rounded-none border-b border-[#262626] bg-transparent py-2 text-[#eaeaea] transition-colors focus:border-[#eaeaea] focus:outline-none"
              placeholder="••••••••"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error && (
            <div className="border border-[#5a1f1f] bg-[#240909] px-3 py-2 text-[0.75rem] leading-5 text-[#ffb4b4]">
              {error}
            </div>
          )}

          <button
            className="mt-6 w-full border border-[#262626] bg-transparent py-4 text-[0.75rem] text-[#eaeaea] uppercase transition-colors hover:bg-[#eaeaea] hover:text-[#070707] disabled:cursor-not-allowed disabled:opacity-55"
            disabled={submitting}
            style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
            type="submit"
          >
            [ {submitting ? "处理中..." : mode === "login" ? "确认登录" : "创建账户"} ]
          </button>
        </form>

        <div className="mt-8 flex flex-col items-start justify-between gap-4 border-t border-[#262626] pt-6 md:flex-row md:items-center">
          <span
            className="text-[0.625rem] tracking-wider text-[#515151] uppercase"
            style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
          >
            {mode === "login" ? "还没有账号?" : "已经有账号了?"}
          </span>
          <button
            className="text-[0.625rem] tracking-wider text-[#eaeaea] uppercase transition-colors hover:text-[#ffb700] disabled:opacity-40"
            disabled={submitting}
            style={{ fontFamily: "'Rand Mono', Arial, sans-serif" }}
            type="button"
            onClick={() => onSwitchMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "注册新账号 ->" : "直接登录 ->"}
          </button>
        </div>
      </div>
    </div>
  )
}
