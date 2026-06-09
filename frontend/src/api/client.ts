const DEFAULT_API_BASE_URL = "http://localhost:8080";

export type ApiError = {
  message: string;
  status?: number;
};

export async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.json() as Promise<T>;
}

export async function postJson<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    // Undefined bodies are omitted so empty POST actions do not send the JSON string "undefined".
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.json() as Promise<T>;
}

export async function patchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method: "PATCH",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.json() as Promise<T>;
}

export async function uploadForm<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
    body: formData,
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.json() as Promise<T>;
}

export function apiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    // Spring and FastAPI use slightly different error fields, so normalize both shapes here.
    const body = (await response.json()) as { message?: string; error?: string };
    return {
      message: body.message || body.error || `Request failed with status ${response.status}`,
      status: response.status,
    };
  } catch {
    return {
      message: `Request failed with status ${response.status}`,
      status: response.status,
    };
  }
}

export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unexpected dashboard error.";
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null && "message" in error;
}
