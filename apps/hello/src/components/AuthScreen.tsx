import React, { useState, useEffect } from "react";
import { User } from "../types";
import { fetchCloudUserQuestion, loginCloudUser, registerCloudUser } from "../api";

interface AuthScreenProps {
  onAuthSuccess: (user: User) => void;
}

type AuthMode = "login" | "register";

export function AuthScreen({ onAuthSuccess }: AuthScreenProps) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [name, setName] = useState("");
  const [securityQuestion, setSecurityQuestion] = useState(
    "What was the name of your first pet?",
  );
  const [securityAnswer, setSecurityAnswer] = useState("");
  const [fetchedQuestion, setFetchedQuestion] = useState("");
  const [step, setStep] = useState<"username" | "question">("username");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const commonQuestions = [
    "What was the name of your first pet?",
    "In what city where you born?",
    "What is your mother's maiden name?",
    "What was the name of your first school?",
    "What is your favorite book?",
  ];

  useEffect(() => {
    // If we have a saved username in localStorage, pre-fill it
    const savedName = localStorage.getItem("whatsclone_last_username");
    if (savedName) {
      setName(savedName);
    }
  }, []);

  const handleFetchQuestion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setError("");
    setLoading(true);

    try {
      const question = await fetchCloudUserQuestion(name);
      setFetchedQuestion(question);
      setStep("question");
      localStorage.setItem("whatsclone_last_username", name);
    } catch (err: any) {
      setError(err.message);
      if (err.message === "User not found" || err.message === "User needs registration") {
        setMode("register");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const data = await loginCloudUser({ name, securityAnswer });
      onAuthSuccess(data);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const data = await registerCloudUser({ name, securityQuestion, securityAnswer });
      localStorage.setItem("whatsclone_last_username", name.trim());
      onAuthSuccess(data);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="hello-app-ambient flex w-full min-h-screen items-center justify-center bg-[var(--hello-bg)] p-4 text-[var(--hello-text)]">
      <div className="hello-panel-strong max-w-sm w-full overflow-hidden rounded-[var(--hello-radius-xl)] p-8">
        <h2 className="text-2xl font-bold text-center text-[var(--hello-text)] mb-6">
          {mode === "login" ? "Welcome Back" : "Create Account"}
        </h2>

        {error && (
          <div className="p-3 mb-4 text-sm text-[var(--hello-danger)] bg-red-500/10 rounded-[var(--hello-radius-sm)]">
            {error}
          </div>
        )}

        {mode === "login" ? (
          step === "username" ? (
            <form
              onSubmit={handleFetchQuestion}
              className="flex flex-col gap-4"
            >
              <input
                type="text"
                placeholder="Username"
                className="hello-input w-full px-4 py-3 placeholder:text-[var(--hello-text-muted)]"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-[var(--hello-accent)] hover:bg-[var(--hello-accent-strong)] text-white font-medium py-3 rounded-[var(--hello-radius-md)] transition-colors mt-2"
              >
                {loading ? "Checking..." : "Continue"}
              </button>
            </form>
          ) : (
            <form onSubmit={handleLogin} className="flex flex-col gap-4">
              <div className="text-sm font-medium text-[var(--hello-text-muted)] mb-2">
                Security Question: <br />
                <span className="text-lg text-[var(--hello-text)]">
                  {fetchedQuestion}
                </span>
              </div>
              <input
                type="text"
                placeholder="Your Answer"
                className="hello-input w-full px-4 py-3 placeholder:text-[var(--hello-text-muted)]"
                value={securityAnswer}
                onChange={(e) => setSecurityAnswer(e.target.value)}
                required
              />
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-[var(--hello-accent)] hover:bg-[var(--hello-accent-strong)] text-white font-medium py-3 rounded-[var(--hello-radius-md)] transition-colors mt-2"
              >
                {loading ? "Signing in..." : "Sign In"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setStep("username");
                  setSecurityAnswer("");
                  setError("");
                }}
                className="w-full text-[var(--hello-text-muted)] hover:text-[var(--hello-text)] text-sm py-2"
              >
                Back
              </button>
            </form>
          )
        ) : (
          <form onSubmit={handleRegister} className="flex flex-col gap-4">
            <input
              type="text"
              placeholder="Choose a Username"
              className="hello-input w-full px-4 py-3 placeholder:text-[var(--hello-text-muted)]"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />

            <div className="flex flex-col gap-1">
              <label className="text-sm text-[var(--hello-text-muted)] font-medium">
                Security Question
              </label>
              <select
                value={securityQuestion}
                onChange={(e) => setSecurityQuestion(e.target.value)}
                className="hello-input w-full px-4 py-3"
              >
                {commonQuestions.map((q) => (
                  <option key={q} value={q}>
                    {q}
                  </option>
                ))}
              </select>
            </div>

            <input
              type="text"
              placeholder="Your Answer (This will be your password)"
              className="hello-input w-full px-4 py-3 placeholder:text-[var(--hello-text-muted)]"
              value={securityAnswer}
              onChange={(e) => setSecurityAnswer(e.target.value)}
              required
            />

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-[var(--hello-accent)] hover:bg-[var(--hello-accent-strong)] text-white font-medium py-3 rounded-[var(--hello-radius-md)] transition-colors mt-2"
            >
              {loading ? "Creating..." : "Sign Up"}
            </button>
          </form>
        )}

        <div className="mt-6 text-center text-sm text-[var(--hello-text-muted)] flex flex-col gap-2">
          {mode === "login" ? (
            <div>
              Don't have an account?{" "}
              <button
                type="button"
                onClick={() => {
                  setMode("register");
                  setError("");
                }}
                className="text-[var(--hello-accent)] hover:underline"
              >
                Sign up
              </button>
            </div>
          ) : (
            <div>
              Already have an account?{" "}
              <button
                type="button"
                onClick={() => {
                  setMode("login");
                  setStep("username");
                  setError("");
                }}
                className="text-[var(--hello-accent)] hover:underline"
              >
                Sign in
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
