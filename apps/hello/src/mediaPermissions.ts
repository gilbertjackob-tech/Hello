const FRIENDLY_MEDIA_ERRORS: Record<string, string> = {
  NotAllowedError: "Camera/microphone permission was denied. Allow it from browser site settings.",
  NotFoundError: "No camera or microphone device found.",
  NotReadableError: "Camera/microphone is already being used by another app.",
  OverconstrainedError: "The selected camera or microphone constraints cannot be satisfied.",
  SecurityError: "Camera/microphone requires HTTPS or a trusted local origin.",
  NotSupportedError: "Camera/microphone capture is not supported by this browser.",
};

function makeMediaError(name: string, message: string) {
  if (typeof DOMException !== "undefined") {
    return new DOMException(message, name);
  }

  const error = new Error(message);
  error.name = name;
  return error;
}

export function describeMediaAccessError(error: unknown, fallback = "Could not access camera/microphone.") {
  const name =
    error && typeof error === "object" && "name" in error && typeof (error as Error).name === "string"
      ? (error as Error).name
      : "MediaError";
  const message =
    error && typeof error === "object" && "message" in error && typeof (error as Error).message === "string"
      ? (error as Error).message
      : "";
  const friendly = FRIENDLY_MEDIA_ERRORS[name] || fallback;
  return `${friendly} (${name}${message ? `: ${message}` : ""})`;
}

export function getMediaCaptureReadinessError() {
  if (!window.isSecureContext) {
    return makeMediaError("SecurityError", "Camera/microphone requires HTTPS or a trusted local origin.");
  }

  if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== "function") {
    return makeMediaError("NotSupportedError", "navigator.mediaDevices.getUserMedia is unavailable.");
  }

  return null;
}

export async function requestUserMediaWithDiagnostics(constraints: MediaStreamConstraints) {
  const readinessError = getMediaCaptureReadinessError();
  if (readinessError) {
    console.error("[HELLO_MEDIA_PRELIGHT_FAILED]", readinessError.name, readinessError.message);
    throw readinessError;
  }

  try {
    return await navigator.mediaDevices.getUserMedia(constraints);
  } catch (error) {
    console.error("[HELLO_GET_USER_MEDIA_FAILED]", error);
    throw error;
  }
}

export async function testCameraMicrophoneAccess() {
  const stream = await requestUserMediaWithDiagnostics({
    audio: true,
    video: true,
  });
  stream.getTracks().forEach((track) => track.stop());
  return "Camera/mic permission OK";
}
