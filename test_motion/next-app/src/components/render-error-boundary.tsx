import { Component, type ErrorInfo, type ReactNode } from "react"

type RenderErrorBoundaryProps = {
  children: ReactNode
}

type RenderErrorBoundaryState = {
  error: Error | null
}

export class RenderErrorBoundary extends Component<
  RenderErrorBoundaryProps,
  RenderErrorBoundaryState
> {
  state: RenderErrorBoundaryState = {
    error: null,
  }

  static getDerivedStateFromError(error: Error): RenderErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Desktop render failed", error, errorInfo)
  }

  render() {
    if (this.state.error) {
      return (
        <main className="flex min-h-screen items-center justify-center bg-[#0c0d12] px-6 text-white">
          <div className="max-w-xl rounded-2xl border border-white/10 bg-white/8 p-6 shadow-2xl backdrop-blur">
            <h1 className="text-xl font-semibold">Desktop failed to render</h1>
            <p className="mt-3 text-sm leading-6 text-white/72">
              {this.state.error.message}
            </p>
          </div>
        </main>
      )
    }

    return this.props.children
  }
}
