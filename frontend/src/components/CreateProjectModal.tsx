import React, { useState } from 'react';
import { X, Loader2 } from 'lucide-react';
import { api } from '../services/api';
import type { Project } from '../types';
import { apiError } from '../services/apiError';

interface Props {
  workspaceId?: string;
  onClose: () => void;
  onCreated: (project: Project) => void;
}

export const CreateProjectModal: React.FC<Props> = ({ workspaceId, onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [startDate, setStartDate] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!workspaceId) return;

    if (!name.trim()) {
      setError('Nama proyek wajib diisi.');
      return;
    }
    if (name.trim().length > 255) {
      setError('Nama proyek maksimal 255 karakter.');
      return;
    }
    if (startDate && targetDate && startDate > targetDate) {
      setError('Tanggal mulai harus sebelum atau sama dengan target selesai.');
      return;
    }

    setSaving(true);
    setError('');

    try {
      const res = await api.post<Project>('/projects', {
        workspaceId,
        name,
        description,
        startDate: startDate || null,
        targetDate: targetDate || null,
        clientVisible: true,
      });
      onCreated(res.data);
    } catch (err) {
      const message = apiError(err, '');
      if (message.includes('PLAN_LIMIT_REACHED')) {
        setError('Anda telah mencapai batas proyek untuk paket Free. Upgrade ke Premium untuk membuat proyek tanpa batas.');
      } else {
        // Pesan server ditampilkan apa adanya. Sebelumnya semua error selain plan-limit
        // dipukul rata jadi "coba lagi", jadi pesan validasi seperti "target sebelum
        // tanggal mulai" tidak pernah sampai ke user dan mereka mencoba ulang terus.
        setError(message || 'Gagal membuat proyek. Silakan coba lagi.');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-3 backdrop-blur-xs sm:p-4">
      <div className="animate-modal-in max-h-[calc(100dvh-1.5rem)] w-full max-w-md overflow-y-auto rounded-2xl border border-black/[0.08] bg-white p-4 shadow-xl sm:max-h-[calc(100dvh-2rem)] sm:p-6">
        <div className="flex items-center justify-between border-b border-black/[0.06] pb-4">
          <h3 className="font-mono text-lg font-bold text-[#17171c]">Buat Proyek Baru</h3>
          <button onClick={onClose} className="text-[#75758a] transition hover:text-[#17171c]">
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="block text-xs font-medium text-[#17171c]">Nama Proyek</label>
            <input
              type="text"
              required
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="E-Commerce Mobile App"
              className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3.5 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-[#17171c]">Deskripsi</label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Deskripsi singkat mengenai proyek ini..."
              className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3.5 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="block text-xs font-medium text-[#17171c]">Tanggal Mulai</label>
              <input
                type="date"
                value={startDate}
                max={targetDate || undefined}
                onChange={(e) => { setStartDate(e.target.value); setError(""); }}
                className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2 text-xs text-[#17171c] outline-none focus:border-[#003c33]/50"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-[#17171c]">Target Selesai</label>
              <input
                type="date"
                value={targetDate}
                min={startDate || undefined}
                onChange={(e) => { setTargetDate(e.target.value); setError(""); }}
                className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2 text-xs text-[#17171c] outline-none focus:border-[#003c33]/50"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={saving}
            className="press mt-4 w-full rounded-xl bg-[#17171c] py-3 text-sm font-semibold text-[#eeece7] hover:bg-black disabled:opacity-50"
          >
            {saving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Simpan Proyek'}
          </button>

          {error && (
            <div className="mt-3 rounded-xl border border-rose-500/20 bg-rose-50 p-3 text-xs text-rose-700">
              {error}
            </div>
          )}
        </form>
      </div>
    </div>
  );
};
