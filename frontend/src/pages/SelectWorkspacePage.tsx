import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useWorkspace } from '../context/WorkspaceContext';
import { Plus, ArrowRight, ArrowUpRight, AlertTriangle, X } from 'lucide-react';
import { Skeleton } from '../components/Skeleton';

export const SelectWorkspacePage: React.FC = () => {
  const { workspaces, setCurrentWorkspace, loadingWorkspaces } = useWorkspace();
  const navigate = useNavigate();
  // AuthModal menerima undangan yang tertunda saat login. Kalau gagal, dulu alurnya
  // tetap jalan tanpa jejak apa pun — user masuk dan tidak pernah tahu undangannya
  // tidak diterima. Pesannya dititipkan lewat sessionStorage dan ditampilkan di sini,
  // halaman pertama setelah login.
  const [inviteError, setInviteError] = useState<string | null>(
    () => sessionStorage.getItem('invitation_accept_error')
  );

  useEffect(() => {
    if (inviteError) sessionStorage.removeItem('invitation_accept_error');
  }, [inviteError]);

  useEffect(() => {
    if (!loadingWorkspaces && workspaces.length === 0) {
      navigate('/onboarding');
    }
  }, [workspaces, loadingWorkspaces, navigate]);

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c] px-[5%] pt-16 pb-24 lg:pt-24">
      {inviteError && (
        <div
          role="alert"
          className="animate-toast-in mb-8 flex items-start gap-2 rounded-xl border border-amber-500/30 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="min-w-0 flex-1">{inviteError}</span>
          <button
            type="button"
            onClick={() => setInviteError(null)}
            aria-label="Tutup"
            className="press ring-focus shrink-0 rounded-lg p-1 text-amber-700 hover:bg-amber-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Header */}
      <header className="mb-14 grid grid-cols-1 items-end gap-8 lg:grid-cols-2">
        <div>
          <div className="flex items-center gap-3">
            <span className="font-mono text-xs tracking-widest text-[#75758a]">MEMBER AREA</span>
            <span className="h-px w-12 bg-current" />
          </div>
          <h1 className="mt-6 font-mono text-3xl font-bold leading-[1.1] tracking-tight sm:text-5xl sm:leading-[1.05] md:text-6xl">
            Pilih <br /> Workspace
          </h1>
        </div>
        <p className="max-w-md font-mono text-xs leading-relaxed text-[#75758a] lg:justify-self-end lg:text-right">
          ← Pilih ruang kerja untuk melihat proyek, anggota, dan aktivitas tim Anda.
        </p>
      </header>

      {/* Grid */}
      <section className="grid grid-cols-1 gap-10 lg:grid-cols-[260px_1fr]">
        {/* Sidebar title */}
        <div>
          <h2 className="font-mono text-sm font-bold tracking-wide text-[#17171c]">
            Workspace Saya
          </h2>
          <div className="mt-3 h-px w-10 bg-[#17171c]" />
          <p className="mt-4 font-mono text-xs leading-relaxed text-[#75758a]">
            Setiap workspace memiliki anggota dan proyeknya sendiri.
          </p>
        </div>

        {/* Workspace list */}
        <div className="space-y-3">
          {loadingWorkspaces ? (
            Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="flex items-center justify-between rounded-2xl border border-black/[0.07] bg-[#faf9f7] px-6 py-5">
                <div className="flex items-center gap-5 flex-1">
                  <Skeleton className="h-11 w-11 rounded-xl" />
                  <div className="space-y-2 flex-1">
                    <Skeleton className="h-5 w-40" />
                    <Skeleton className="h-3 w-24" />
                  </div>
                </div>
                <Skeleton className="h-6 w-20 rounded-md" />
              </div>
            ))
          ) : (
            workspaces.map((ws) => (
            <button
              key={ws.id}
              onClick={() => {
                setCurrentWorkspace(ws);
                navigate(`/w/${ws.slug}/dashboard`);
              }}
              className="press ring-focus group flex w-full items-center justify-between rounded-2xl border border-black/[0.07] bg-[#faf9f7] px-6 py-5 text-left transition shadow-[0_2px_10px_rgba(0,0,0,0.02)] hover:border-[#17171c]/30 hover:bg-white"
            >
              <div className="flex items-center gap-5">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#003c33]/10 font-bold text-[#003c33]">
                  {ws.name.charAt(0).toUpperCase()}
                </div>
                <div>
                  <h3 className="font-semibold leading-tight text-[#17171c] transition group-hover:text-[#003c33]">
                    {ws.name}
                  </h3>
                  <p className="mt-0.5 text-xs font-mono text-[#75758a]">/{ws.slug}</p>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <span className="rounded-md border border-black/[0.06] bg-[#eeece7] px-2.5 py-1 text-[11px] font-mono font-semibold tracking-wider text-[#17171c]">
                  {ws.userRole}
                </span>
                <ArrowUpRight className="h-5 w-5 text-[#75758a] transition group-hover:text-[#17171c]" />
              </div>
            </button>
            ))
          )}

          {/* Full-width CTA for creating new */}
          <Link
            to="/onboarding"
            className="press ring-focus group mt-6 flex w-full items-center justify-between rounded-2xl bg-[#17171c] px-6 py-5 text-[#eeece7] transition hover:bg-[#241a16]"
          >
            <div className="flex items-center gap-4">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#003c33]">
                <Plus className="h-6 w-6 text-[#eeece7]" />
              </div>
              <div>
                <div className="font-semibold">Buat Workspace Baru</div>
                <div className="font-mono text-xs text-[#afa8a0]">Mulai ruang kolaborasi terpisah untuk tim atau klien lain.</div>
              </div>
            </div>
            <ArrowRight className="h-6 w-6 transition group-hover:translate-x-1" />
          </Link>
        </div>
      </section>
    </div>
  );
};
