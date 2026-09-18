import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api } from '../services/api';
import { apiError } from '../services/apiError';
import type { Invitation, WorkspaceMember, Project } from '../types';
import { UserPlus, Mail, Copy, Check, ArrowLeft, Users, Settings2, FolderPen, Loader2 } from 'lucide-react';

export const ProjectSettingsPage: React.FC = () => {
  useResolveWorkspace();
  const { projectId } = useParams<{ projectId: string }>();
  const { currentWorkspace } = useWorkspace();
  const slug = currentWorkspace?.slug || 'default';

  const [project, setProject] = useState<Project | null>(null);
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [loading, setLoading] = useState(true);

  const [email, setEmail] = useState('');
  const [role, setRole] = useState('DEVELOPER');
  const [inviting, setInviting] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [copiedToken, setCopiedToken] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [startDate, setStartDate] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [savingDetails, setSavingDetails] = useState(false);
  // Peran yang ada hanya OWNER, DEVELOPER, CLIENT. Perbandingan dengan 'ADMIN'
  // di sini tidak pernah bisa benar — dibuang supaya aturannya terbaca apa adanya:
  // mengelola proyek adalah hak OWNER, sama seperti yang ditegakkan server.
  const canManage = currentWorkspace?.userRole === 'OWNER';

  const fetchAll = async () => {
    if (!projectId) return;
    try {
      const [p, m] = await Promise.all([
        api.get<Project>(`/projects/${projectId}`),
        api.get<WorkspaceMember[]>(`/workspaces/projects/${projectId}/members`),
      ]);
      setProject(p.data);
      setName(p.data.name);
      setDescription(p.data.description ?? '');
      setStartDate(p.data.startDate ?? '');
      setTargetDate(p.data.targetDate ?? '');
      setMembers(m.data);
      try {
        const i = await api.get<Invitation[]>(`/workspaces/projects/${projectId}/invitations`);
        setInvitations(i.data);
      } catch {
        setInvitations([]);
      }
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal memuat pengaturan proyek.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    void fetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectId) return;
    setInviting(true);
    setMessage('');
    setError('');
    try {
      await api.post(`/workspaces/projects/${projectId}/invitations`, { email, role });
      setMessage(`Undangan dikirim ke ${email}.`);
      setEmail('');
      fetchAll();
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal mengirim undangan.'));
    } finally {
      setInviting(false);
    }
  };

  const handleSaveDetails = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!projectId) return;

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

    setSavingDetails(true); setMessage(''); setError('');
    try {
      const response = await api.put<Project>(`/projects/${projectId}/details`, {
        name, description, startDate: startDate || null, targetDate: targetDate || null,
      });
      setProject(response.data);
      setMessage('Detail proyek berhasil disimpan.');
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal menyimpan detail proyek.'));
    } finally { setSavingDetails(false); }
  };

  const copyLink = (token: string) => {
    const link = `${window.location.origin}/accept-invitation?token=${token}`;
    navigator.clipboard.writeText(link);
    setCopiedToken(token);
    setTimeout(() => setCopiedToken(null), 2000);
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 p-4 sm:p-8 pb-24 lg:pb-0">
          <div className="mx-auto max-w-4xl">
            <Link
              to={`/w/${slug}/projects/${projectId}`}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#75758a] hover:text-[#17171c]"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Kembali ke proyek
            </Link>

            <div className="mt-3 flex items-center gap-3">
              <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><Settings2 className="h-5 w-5" /></div>
              <div>
                <h1 className="break-words text-xl font-mono font-bold sm:text-2xl">Pengaturan Proyek</h1>
                <p className="mt-0.5 text-xs text-[#75758a]">
                  {loading ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memuat...</span> : project?.name}
                </p>
              </div>
            </div>

            {error && (
              <div className="mt-5 rounded-xl border border-rose-500/20 bg-rose-50 p-3 text-sm text-rose-700">{error}</div>
            )}
            {message && (
              <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 p-3 text-sm text-emerald-700">
                <Check className="h-4 w-4" /> {message}
              </div>
            )}

            {canManage && (
              <section className="mt-8 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
                <h3 className="flex items-center gap-2 text-lg font-bold"><FolderPen className="h-5 w-5 text-[#003c33]" /> Detail Proyek</h3>
                <p className="mt-1 text-xs text-[#616161]">Nama dan jadwal ini dipakai untuk bar waktu pada Ikhtisar proyek.</p>
                <form onSubmit={handleSaveDetails} className="mt-5 grid gap-4 sm:grid-cols-2">
                  <label className="text-xs font-semibold sm:col-span-2">Nama proyek<input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none focus:border-[#003c33]/50" /></label>
                  <label className="text-xs font-semibold sm:col-span-2">Deskripsi<textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none focus:border-[#003c33]/50" /></label>
                  <label className="text-xs font-semibold">Tanggal mulai<input type="date" value={startDate} max={targetDate || undefined} onChange={(e) => { setStartDate(e.target.value); setError(''); }} className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none transition focus:border-[#003c33]/50" /></label>
                  <label className="text-xs font-semibold">Target selesai<input type="date" min={startDate || undefined} value={targetDate} onChange={(e) => { setTargetDate(e.target.value); setError(''); }} className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none transition focus:border-[#003c33]/50" /></label>
                  <div className="sm:col-span-2 flex justify-end"><button type="submit" disabled={savingDetails} className="press ring-focus rounded-full bg-[#17171c] px-6 py-3 text-sm font-semibold text-[#eeece7] hover:bg-black disabled:opacity-50">{savingDetails ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Simpan Detail'}</button></div>
                </form>
              </section>
            )}

            {/* Undang ke proyek */}
            {canManage && (
            <section className="mt-8 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
              <h3 className="flex items-center gap-2 text-lg font-bold">
                <UserPlus className="h-5 w-5 text-[#003c33]" /> Undang ke Proyek Ini
              </h3>
              <p className="mt-1 text-xs text-[#616161]">
                Orang yang diundang hanya mendapat akses ke proyek ini, bukan ke seluruh workspace.
              </p>

              <form onSubmit={handleInvite} className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center">
                <div className="relative flex-1">
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="developer@example.com"
                    className="w-full rounded-xl border border-black/10 bg-white px-4 py-3 pl-11 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
                  />
                  <Mail className="absolute left-3.5 top-3.5 h-4 w-4 text-[#75758a]" />
                </div>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className="w-full sm:w-44 rounded-xl border border-black/10 bg-white px-4 py-3 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
                >
                  <option value="DEVELOPER">Developer</option>
                  <option value="CLIENT">Client</option>
                  <option value="OWNER">Owner</option>
                </select>
                <button
                  type="submit"
                  disabled={inviting}
                  className="press ring-focus w-full sm:w-auto rounded-full bg-[#17171c] px-6 py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black disabled:opacity-50"
                >
                  {inviting ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Mengirim...</span> : 'Kirim Undangan'}
                </button>
              </form>
            </section>
            )}

            {/* Anggota proyek */}
            <section className="mt-8">
              <h2 className="flex items-center gap-2 font-mono text-lg font-bold">
                <Users className="h-5 w-5 text-[#003c33]" /> Anggota ({members.length})
              </h2>
              <div className="mt-4 space-y-2.5">
                {loading ? (
                  <p className="text-sm text-[#75758a]">Memuat anggota...</p>
                ) : members.length === 0 ? (
                  <p className="rounded-2xl border border-dashed border-black/10 p-4 sm:p-6 text-center text-xs text-[#75758a]">
                    Belum ada anggota di proyek ini.
                  </p>
                ) : (
                  members.map((m) => (
                    <div key={m.userId} className="flex items-center justify-between gap-3 rounded-xl border border-black/[0.06] bg-white px-4 py-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#17171c] text-[11px] font-bold text-[#edfce9]">
                          {(m.fullName || '?').split(' ').slice(0, 2).map((p) => p.charAt(0)).join('').toUpperCase()}
                        </span>
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-[#17171c]">{m.fullName}</p>
                          <p className="truncate text-xs text-[#75758a]">{m.email}</p>
                        </div>
                      </div>
                      <span className="shrink-0 rounded-full bg-[#edfce9] px-2.5 py-0.5 text-[10px] font-mono font-bold text-[#003c33]">
                        {m.role}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </section>

            {/* Undangan pending */}
            {canManage && (
            <section className="mt-8">
              <h2 className="font-mono text-lg font-bold">Undangan Terkirim</h2>
              <div className="mt-4 space-y-2.5">
                {loading ? (
                  <p className="text-sm text-[#75758a]">Memuat undangan...</p>
                ) : invitations.length === 0 ? (
                  <p className="rounded-2xl border border-dashed border-black/10 p-4 sm:p-6 text-center text-xs text-[#75758a]">
                    Belum ada undangan untuk proyek ini.
                  </p>
                ) : (
                  invitations.map((inv) => (
                    <div key={inv.id} className="flex flex-col gap-3 rounded-xl border border-black/[0.06] bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="text-sm font-semibold text-[#17171c]">{inv.email}</p>
                        <p className="mt-0.5 text-xs text-[#75758a]">
                          Peran: <span className="font-bold">{inv.role}</span> · Status: {inv.status}
                        </p>
                      </div>
                      <button
                        onClick={() => copyLink(inv.token)}
                        className="inline-flex items-center gap-2 rounded-full border border-black/10 bg-[#eeece7]/60 px-4 py-2 text-xs font-semibold text-[#17171c] hover:bg-[#eeece7]"
                      >
                        {copiedToken === inv.token ? (
                          <><Check className="h-3.5 w-3.5 text-emerald-600" /> Link Tersalin!</>
                        ) : (
                          <><Copy className="h-3.5 w-3.5" /> Salin Link Undangan</>
                        )}
                      </button>
                    </div>
                  ))
                )}
              </div>
            </section>
            )}
          </div>
        </main>
      </div>
    </div>
  );
};

