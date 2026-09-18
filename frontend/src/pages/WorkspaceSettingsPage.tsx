import React, { useState } from 'react';
import { Settings2, Check, Loader2 } from 'lucide-react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api } from '../services/api';
import { ImageUpload } from '../components/ImageUpload';
import type { Workspace } from '../types';

const errorMessage = (error: unknown) =>
  (error as { response?: { data?: { error?: { message?: string }; message?: string } } })
    .response?.data?.error?.message
  || (error as { response?: { data?: { message?: string } } }).response?.data?.message
  || 'Gagal menyimpan pengaturan workspace.';

export const WorkspaceSettingsPage: React.FC = () => {
  useResolveWorkspace();
  const { currentWorkspace, setCurrentWorkspace, refreshWorkspaces, loadingWorkspaces } = useWorkspace();
  const [saving, setSaving] = useState(false);
  // Form ini memakai FormData, tapi logo diunggah lewat XHR terpisah — jadi
  // path-nya perlu state sendiri, tidak bisa dibaca dari elemen form.
  const [logoUrl, setLogoUrl] = useState<string | null>(currentWorkspace?.logoUrl ?? null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentWorkspace || currentWorkspace.userRole !== 'OWNER') return;
    const form = new FormData(event.currentTarget as HTMLFormElement);
    setSaving(true);
    setMessage('');
    setError('');
    try {
      const response = await api.put<Workspace>(`/workspaces/${currentWorkspace.id}/details`, {
        name: form.get('name'),
        logoUrl: logoUrl,
        businessType: form.get('businessType'),
        timezone: form.get('timezone'),
      });
      setCurrentWorkspace(response.data);
      await refreshWorkspaces();
      setMessage('Pengaturan workspace berhasil disimpan.');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const inputClass = 'mt-1.5 w-full rounded-xl border border-black/10 bg-white px-4 py-3 text-sm text-[#17171c] outline-none transition focus:border-[#003c33]/60 focus:ring-2 focus:ring-[#003c33]/10';

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 p-4 sm:p-8 pb-24 lg:pb-0">
          <div className="mx-auto max-w-3xl">
            <div className="flex items-center gap-3">
              <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><Settings2 className="h-5 w-5" /></div>
              <div>
                <h1 className="text-2xl font-mono font-bold">Pengaturan Workspace</h1>
                <p className="mt-0.5 text-xs text-[#616161]">Perbarui identitas dan zona waktu workspace.</p>
              </div>
            </div>

            {!loadingWorkspaces && currentWorkspace?.userRole !== 'OWNER' ? (
              <div className="mt-8 rounded-2xl border border-black/10 bg-[#faf9f7] p-4 sm:p-6">
                <h2 className="font-bold">Akses khusus owner</h2>
                <p className="mt-1 text-sm text-[#616161]">Hanya owner yang dapat mengubah pengaturan workspace.</p>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="mt-8 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-5 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-6">
                <div className="grid gap-5 sm:grid-cols-2">
                  <label className="text-xs font-semibold sm:col-span-2">
                    Nama workspace
                    <input name="name" defaultValue={currentWorkspace?.name} required maxLength={255} className={inputClass} />
                  </label>
                  <div className="sm:col-span-2">
                    <ImageUpload
                      kind="WORKSPACE_LOGO"
                      rounded="xl"
                      label="Logo workspace"
                      value={logoUrl}
                      onChange={setLogoUrl}
                      disabled={currentWorkspace?.userRole !== 'OWNER'}
                    />
                  </div>
                  <label className="text-xs font-semibold">
                    Jenis bisnis
                    <input name="businessType" maxLength={50} defaultValue={currentWorkspace?.businessType} placeholder="Software, agensi, retail" className={inputClass} />
                  </label>
                  <label className="text-xs font-semibold">
                    Zona waktu
                    <input name="timezone" defaultValue={currentWorkspace?.timezone} placeholder="Asia/Jakarta" className={inputClass} />
                  </label>
                </div>

                {error && <div role="alert" className="mt-5 rounded-xl border border-rose-500/20 bg-rose-50 p-3 text-sm text-rose-700">{error}</div>}
                {message && (
                  <div role="status" className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 p-3 text-sm text-emerald-700">
                    <Check className="h-4 w-4" /> {message}
                  </div>
                )}

                <div className="mt-6 flex justify-end">
                  <button type="submit" disabled={saving || !currentWorkspace} className="press ring-focus rounded-full bg-[#17171c] px-6 py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black focus:outline-none focus:ring-2 focus:ring-[#17171c]/30 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50">
                    {saving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Simpan Perubahan'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </main>
      </div>
    </div>
  );
};
