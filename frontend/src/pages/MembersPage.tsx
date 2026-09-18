import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { Skeleton } from '../components/Skeleton';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api } from '../services/api';
import { apiError } from '../services/apiError';
import type { Invitation } from '../types';
import { UserPlus, Mail, CheckCircle2, AlertCircle, Copy, Check, Clock, Loader2 } from 'lucide-react';

export const MembersPage: React.FC = () => {
  useResolveWorkspace();
  const { currentWorkspace } = useWorkspace();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('DEVELOPER');
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingInvites, setLoadingInvites] = useState(true);
  // Dibedakan dari "memang belum ada undangan": gagal-muat dulu dirender sebagai
  // empty state, jadi OWNER menyimpulkan undangannya tidak terkirim.
  const [invitesError, setInvitesError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [copiedToken, setCopiedToken] = useState<string | null>(null);

  const fetchInvitations = async () => {
    if (!currentWorkspace) return;
    setInvitesError('');
    try {
      const res = await api.get(`/workspaces/${currentWorkspace.id}/invitations`);
      setInvitations(res.data);
    } catch (err) {
      console.error('Failed to fetch invitations:', err);
      setInvitesError(apiError(err, 'Gagal memuat daftar undangan.'));
    } finally {
      setLoadingInvites(false);
    }
  };

  useEffect(() => {
    fetchInvitations();
  }, [currentWorkspace]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentWorkspace) return;
    setLoading(true);
    setMessage('');
    setError('');

    try {
      await api.post(`/workspaces/${currentWorkspace.id}/invitations`, { email, role });
      setMessage(`Undangan telah dikirim ke ${email}. Link undangan dan email verifikasi siap diproses.`);
      setEmail('');
      fetchInvitations();
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal mengirim undangan.'));
    } finally {
      setLoading(false);
    }
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
        <main className="min-w-0 max-w-4xl flex-1 overflow-y-auto p-4 sm:p-8 pb-24 lg:pb-0">
          <div>
            <h1 className="text-2xl font-mono font-bold text-[#17171c]">Anggota Tim & Undangan</h1>
            <p className="text-xs text-[#75758a] mt-0.5">Kelola anggota workspace dan undang kolaborator baru.</p>
          </div>

          {/* Form Undang Member */}
          <div className="mt-8 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
            <h3 className="text-lg font-bold text-[#17171c] flex items-center gap-2">
              <UserPlus className="h-5 w-5 text-[#003c33]" /> Undang Anggota Baru
            </h3>

            {message && (
              <div className="mt-4 flex items-center gap-3 rounded-xl border border-emerald-500/20 bg-emerald-50 p-4 text-sm text-emerald-700">
                <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" />
                <span>{message}</span>
              </div>
            )}

            {error && (
              <div className="mt-4 flex items-center gap-3 rounded-xl border border-rose-500/20 bg-rose-50 p-4 text-sm text-rose-700">
                <AlertCircle className="h-5 w-5 shrink-0 text-rose-500" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleInvite} className="mt-6 flex flex-col sm:flex-row items-center gap-4">
              <div className="relative w-full flex-1">
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="developer@example.com"
                  className="w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-4 py-3 pl-11 text-sm text-[#17171c] placeholder-[#75758a] outline-none focus:border-[#003c33]/50"
                />
                <Mail className="absolute left-3.5 top-3.5 h-4 w-4 text-[#75758a]" />
              </div>

              <select
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="w-full sm:w-44 rounded-xl border border-black/10 bg-[#eeece7]/50 px-4 py-3 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
              >
                <option value="DEVELOPER">Developer</option>
                <option value="CLIENT">Client</option>
                <option value="OWNER">Owner</option>
              </select>

              <button
                type="submit"
                disabled={loading}
                className="press ring-focus w-full sm:w-auto rounded-full bg-[#17171c] px-6 py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black disabled:opacity-50 cursor-pointer"
              >
                {loading ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Mengirim...</span> : 'Kirim Undangan'}
              </button>
            </form>
          </div>

          {/* List Undangan Pending */}
          <div className="mt-10">
            <h2 className="text-lg font-mono font-bold text-[#17171c]">Daftar Undangan Sent</h2>
            <div className="stagger mt-4 space-y-3">
              {loadingInvites ? (
                Array.from({ length: 2 }).map((_, i) => (
                  <div key={i} className="flex items-center justify-between rounded-2xl border border-black/[0.07] bg-white p-4 shadow-xs">
                    <div className="space-y-2 flex-1">
                      <Skeleton className="h-4 w-52" />
                      <Skeleton className="h-3 w-32" />
                    </div>
                    <Skeleton className="h-8 w-36 rounded-full" />
                  </div>
                ))
              ) : invitesError ? (
                <div role="alert" className="animate-toast-in rounded-xl border border-rose-500/20 bg-rose-50 px-4 py-6 text-center text-sm text-rose-700">
                  {invitesError}
                  <button type="button" onClick={fetchInvitations}
                    className="press mt-3 block w-full rounded-full border border-rose-300 bg-white px-4 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-100">
                    Coba lagi
                  </button>
                </div>
              ) : invitations.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-black/10 p-4 sm:p-6 text-center text-xs text-[#75758a]">
                  Belum ada undangan dikirim di workspace ini.
                </div>
              ) : (
                invitations.map((inv) => (
                  <div
                    key={inv.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-2xl border border-black/[0.07] bg-white p-4 shadow-xs"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm text-[#17171c]">{inv.email}</span>
                        <span className="rounded-full bg-[#edfce9] px-2.5 py-0.5 text-[10px] font-mono font-bold text-[#003c33]">
                          {inv.role}
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5 text-xs text-[#75758a] mt-1">
                        <Clock className="h-3.5 w-3.5" />
                        <span>Status: <strong className="text-[#17171c]">{inv.status}</strong></span>
                      </div>
                    </div>

                    <button
                      onClick={() => copyLink(inv.token)}
                      className="inline-flex items-center justify-center gap-2 rounded-full border border-black/10 bg-[#eeece7]/60 px-4 py-2 text-xs font-semibold text-[#17171c] hover:bg-[#eeece7] transition cursor-pointer"
                    >
                      {copiedToken === inv.token ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-600" /> Link Tersalin!
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" /> Salin Link Undangan
                        </>
                      )}
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};
