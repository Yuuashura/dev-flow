/**
 * Satu pembaca error untuk seluruh aplikasi.
 *
 * Sebelumnya ada empat salinan terpisah (ProjectDetailPage, AdminUsersPage,
 * TaskDetailPanel, ProjectSettingsPage) dan semuanya hanya membaca
 * `error.message`. Backend menaruh alasan field-level di `error.details[]`, jadi
 * seluruh pesan validasi hilang di perjalanan dan user cuma melihat teks umum.
 *
 * Bentuk respons error dari semua service:
 *   { error: { code, message, details[], requestId, timestamp } }
 */

type ApiErrorBody = {
  error?: {
    code?: string;
    message?: string;
    details?: string[];
    requestId?: string;
  };
};

type AxiosLike = {
  response?: { status?: number; data?: ApiErrorBody };
  message?: string;
};

/** Pesan server, atau null kalau memang tidak ada. Pemanggil yang memutuskan fallback. */
export function getErrMsg(err: unknown): string | null {
  if (typeof err !== 'object' || err === null) return null;

  const body = (err as AxiosLike).response?.data?.error;
  if (body) {
    const message = body.message?.trim();
    const details = (body.details ?? []).map((d) => d.trim()).filter(Boolean);

    // "Invalid request parameters" adalah placeholder lama yang tidak memberi tahu
    // apa pun; kalau details ada, itu yang berguna.
    const messageIsPlaceholder =
      !message || message === 'Invalid request parameters' || message === 'An unexpected error occurred';

    if (messageIsPlaceholder && details.length > 0) return details.join('; ');
    if (message && details.length > 0 && !details.some((d) => message.includes(d))) {
      return `${message} (${details.join('; ')})`;
    }
    if (message) return message;
    if (details.length > 0) return details.join('; ');
  }

  // Network error / timeout: tidak ada respons sama sekali.
  if ((err as AxiosLike).response === undefined && (err as AxiosLike).message) {
    return 'Tidak dapat terhubung ke server. Periksa koneksi Anda.';
  }
  return null;
}

/** Pesan server, atau fallback bila server tidak memberi pesan yang berguna. */
export function apiError(err: unknown, fallback: string): string {
  return getErrMsg(err) ?? fallback;
}

/** requestId dari respons error — dipakai saat user perlu melaporkan kegagalan. */
export function errRequestId(err: unknown): string | null {
  if (typeof err !== 'object' || err === null) return null;
  return (err as AxiosLike).response?.data?.error?.requestId ?? null;
}

/** Status HTTP-nya, untuk pemanggil yang perlu membedakan 403 dari 404. */
export function errStatus(err: unknown): number | null {
  if (typeof err !== 'object' || err === null) return null;
  return (err as AxiosLike).response?.status ?? null;
}
