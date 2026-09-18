import React, { useRef, useState } from 'react';
import { Upload, X, Loader2, AlertTriangle } from 'lucide-react';
import { api } from '../services/api';
import { apiError } from '../services/apiError';

/** Cermin batas server (MediaService.MAX_UPLOAD_BYTES). Server tetap penentu —
 *  ini hanya supaya user tidak menunggu unggahan 10 MB baru ditolak. */
const MAX_BYTES = 2 * 1024 * 1024;

/** Server mengenali tipe dari byte, bukan dari ini. Daftar ini cuma menyaring
 *  dialog pemilih berkas dan memberi pesan lebih cepat. */
const ACCEPT = 'image/png,image/jpeg';

interface Props {
  kind: 'AVATAR' | 'WORKSPACE_LOGO';
  /** Path media saat ini, mis. /api/v1/media/{uuid}. */
  value?: string | null;
  onChange: (mediaPath: string | null) => void;
  label: string;
  hint?: string;
  disabled?: boolean;
  /** Avatar bundar, logo persegi membulat. */
  rounded?: 'full' | 'xl';
}

export const ImageUpload: React.FC<Props> = ({
  kind, value, onChange, label, hint, disabled = false, rounded = 'full',
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const pick = async (file: File) => {
    setError('');

    if (file.size > MAX_BYTES) {
      setError(`Ukuran gambar maksimal ${MAX_BYTES / 1024 / 1024} MB.`);
      return;
    }

    setUploading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      // Content-Type sengaja tidak diset: browser harus menulis sendiri boundary
      // multipart-nya, dan menimpanya membuat request tidak bisa di-parse server.
      const res = await api.post<{ url: string }>(`/media?kind=${kind}`, form);
      onChange(res.data.url);
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal mengunggah gambar.'));
    } finally {
      setUploading(false);
      // Direset supaya memilih berkas yang sama dua kali tetap memicu change.
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const shape = rounded === 'full' ? 'rounded-full' : 'rounded-2xl';

  return (
    <div>
      <span className="mb-2 block text-xs font-semibold text-[#3f3f46]">{label}</span>

      <div className="flex items-center gap-3">
        <div className={`flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden border border-[#d9d9dd] bg-[#eeece7] ${shape}`}>
          {value ? (
            <img src={value} alt="" className="h-full w-full object-cover" />
          ) : (
            <Upload className="h-5 w-5 text-[#93939f]" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={disabled || uploading}
              onClick={() => inputRef.current?.click()}
              className="press ring-focus tap inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3.5 py-2 text-xs font-semibold text-[#17171c] hover:bg-[#faf9f7] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {uploading
                ? <><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Mengunggah...</>
                : <><Upload className="h-3.5 w-3.5" /> {value ? 'Ganti gambar' : 'Pilih gambar'}</>}
            </button>

            {value && !uploading && (
              <button
                type="button"
                disabled={disabled}
                onClick={() => { onChange(null); setError(''); }}
                className="press ring-focus tap inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"
              >
                <X className="h-3.5 w-3.5" /> Hapus
              </button>
            )}
          </div>

          <p className="mt-1.5 text-[11px] leading-relaxed text-[#93939f]">
            {hint ?? 'PNG atau JPG, maksimal 2 MB. Gambar diperkecil dan disimpan ulang di server.'}
          </p>
        </div>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void pick(file);
        }}
      />

      {error && (
        <p role="alert" className="animate-toast-in animate-shake mt-2 flex items-start gap-1.5 rounded-lg border border-rose-500/20 bg-rose-50 px-3 py-2 text-[11px] font-medium text-rose-700">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {error}
        </p>
      )}
    </div>
  );
};
