import React, { useState } from "react";
import {
  describeMediaAccessError,
  requestUserMediaWithDiagnostics,
} from "../mediaPermissions";

export function PermissionsModal({ onDone }: { onDone: () => void }) {
  const [loading, setLoading] = useState(false);

  const requestPermissions = async () => {
    setLoading(true);

    try {
      if ("Notification" in window && Notification.permission !== "granted") {
        await Notification.requestPermission();
      }

      if (navigator.storage && navigator.storage.persist) {
        await navigator.storage.persist();
      }

      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        try {
          const stream = await requestUserMediaWithDiagnostics({
            video: true,
            audio: true,
          });
          stream.getTracks().forEach((track) => track.stop());
        } catch (error) {
          console.warn(
            "Camera/Mic denied or unavailable",
            describeMediaAccessError(error),
            error,
          );
        }
      }

      if ("geolocation" in navigator) {
        navigator.geolocation.getCurrentPosition(
          () => undefined,
          (error) => console.warn("Location denied or unavailable", error),
        );
      }
    } catch (error) {
      console.error("Error requesting permissions", error);
    } finally {
      setLoading(false);
      onDone();
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-md">
      <div className="hello-panel-strong flex w-full max-w-md flex-col items-center rounded-[28px] p-8 text-center animate-in fade-in zoom-in duration-300">
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-[20px] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]">
          <svg
            className="h-8 w-8"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
            />
          </svg>
        </div>

        <h2 className="mb-3 text-2xl font-semibold tracking-tight text-[var(--hello-text)]">
          Unlock the full Hello experience
        </h2>

        <p className="mb-6 text-sm leading-6 text-[var(--hello-text-muted)]">
          Hello uses device permissions for calls, voice notes, smart sharing, status, and notifications.
        </p>

        <div className="mb-7 grid w-full grid-cols-2 gap-3 text-left text-sm">
          {["Notifications", "Location", "Camera", "Storage", "Microphone", "Photos"].map(
            (item) => (
              <div
                key={item}
                className="rounded-2xl border border-[var(--hello-border)] bg-[var(--hello-panel)] px-3 py-3 text-[var(--hello-text)] shadow-[var(--hello-shadow-soft)]"
              >
                <span className="font-semibold">{item}</span>
              </div>
            ),
          )}
        </div>

        <div className="flex w-full flex-col space-y-3">
          <button
            onClick={requestPermissions}
            disabled={loading}
            className="w-full rounded-2xl bg-[var(--hello-accent)] py-3 text-sm font-semibold text-white transition hover:bg-[var(--hello-accent-strong)] disabled:opacity-50"
          >
            {loading ? "Requesting..." : "Allow All"}
          </button>

          <button
            onClick={onDone}
            disabled={loading}
            className="w-full rounded-2xl border border-[var(--hello-border)] py-3 text-sm font-semibold text-[var(--hello-text-muted)] transition hover:bg-black/5 dark:hover:bg-white/5"
          >
            Ask me later
          </button>
        </div>
      </div>
    </div>
  );
}
