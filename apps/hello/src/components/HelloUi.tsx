import { ReactNode } from "react";
import { cn } from "../lib/utils";

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center rounded-[24px] border border-[var(--hello-border)] bg-[var(--hello-panel)] px-6 py-10 text-center shadow-[var(--hello-shadow-soft)] backdrop-blur-xl",
        className,
      )}
    >
      <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-[20px] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]">
        {icon}
      </div>
      <h3 className="text-base font-semibold text-[var(--hello-text)]">{title}</h3>
      <p className="mt-2 max-w-sm text-sm leading-6 text-[var(--hello-text-muted)]">
        {description}
      </p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

export function FilterChip({
  active,
  label,
  onClick,
  count,
}: {
  active?: boolean;
  label: string;
  onClick?: () => void;
  count?: number;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition",
        active
          ? "border-transparent bg-[var(--hello-accent)] text-white shadow-[0_10px_24px_rgba(15,143,120,0.24)]"
          : "hello-pill hover:border-[var(--hello-border-strong)] hover:text-[var(--hello-text)]",
      )}
    >
      <span>{label}</span>
      {typeof count === "number" ? (
        <span
          className={cn(
            "rounded-full px-1.5 py-0.5 text-[10px]",
            active ? "bg-white/18 text-white" : "bg-black/5 text-[var(--hello-text-muted)] dark:bg-white/10",
          )}
        >
          {count}
        </span>
      ) : null}
    </button>
  );
}

export function SkeletonBlock({ className }: { className?: string }) {
  return <div className={cn("hello-skeleton rounded-2xl", className)} />;
}
