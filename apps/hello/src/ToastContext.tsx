import { createContext, ReactNode, useContext, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, LoaderCircle, X, XCircle } from "lucide-react";
import { cn } from "./lib/utils";

type ToastTone = "success" | "error" | "warning" | "loading";

type ToastInput = {
  title: string;
  description?: string;
  tone?: ToastTone;
  durationMs?: number;
};

type ToastItem = ToastInput & {
  id: string;
  tone: ToastTone;
};

type ToastContextValue = {
  pushToast: (toast: ToastInput) => string;
  dismissToast: (id: string) => void;
};

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const toneMap: Record<ToastTone, { icon: typeof CheckCircle2; shell: string; iconClass: string }> = {
  success: {
    icon: CheckCircle2,
    shell: "border-emerald-200/80 bg-white/95 text-slate-900 dark:border-emerald-500/20 dark:bg-slate-900/95 dark:text-slate-50",
    iconClass: "text-emerald-500",
  },
  error: {
    icon: XCircle,
    shell: "border-rose-200/80 bg-white/95 text-slate-900 dark:border-rose-500/20 dark:bg-slate-900/95 dark:text-slate-50",
    iconClass: "text-rose-500",
  },
  warning: {
    icon: AlertTriangle,
    shell: "border-amber-200/80 bg-white/95 text-slate-900 dark:border-amber-500/20 dark:bg-slate-900/95 dark:text-slate-50",
    iconClass: "text-amber-500",
  },
  loading: {
    icon: LoaderCircle,
    shell: "border-sky-200/80 bg-white/95 text-slate-900 dark:border-sky-500/20 dark:bg-slate-900/95 dark:text-slate-50",
    iconClass: "text-sky-500",
  },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const pushToast = (toast: ToastInput) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const next: ToastItem = {
      id,
      tone: toast.tone || "success",
      durationMs: toast.durationMs ?? (toast.tone === "loading" ? 2200 : 3600),
      title: toast.title,
      description: toast.description,
    };
    setToasts((prev) => [...prev, next]);
    window.setTimeout(() => dismissToast(id), next.durationMs);
    return id;
  };

  const value = useMemo(() => ({ pushToast, dismissToast }), []);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-3 z-[120] flex flex-col items-center gap-3 px-3 sm:top-4 sm:right-4 sm:left-auto sm:items-end">
        {toasts.map((toast) => {
          const tone = toneMap[toast.tone];
          const Icon = tone.icon;
          return (
            <div
              key={toast.id}
              className={cn(
                "pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-[20px] border px-4 py-3 shadow-2xl backdrop-blur-xl sm:min-w-[320px]",
                tone.shell,
              )}
            >
              <Icon className={cn("mt-0.5 h-5 w-5 shrink-0", tone.iconClass, toast.tone === "loading" && "animate-spin")} />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold">{toast.title}</p>
                {toast.description ? (
                  <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-300">
                    {toast.description}
                  </p>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => dismissToast(toast.id)}
                className="rounded-full p-1 text-slate-400 transition hover:bg-black/5 hover:text-slate-700 dark:hover:bg-white/10 dark:hover:text-slate-100"
                aria-label="Dismiss toast"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}
