import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { Skeleton, SkeletonList } from '../components/Skeleton';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api } from '../services/api';
import type { Project } from '../types';
import { FolderKanban, CheckCircle2, Clock, Users, Plus, ArrowRight, TrendingUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useCountUp } from '../hooks/useCountUp';

/** Badge status dulu selalu hijau apa pun statusnya, jadi proyek yang dibatalkan
 *  terbaca sama seperti yang selesai. */
const STATUS_STYLES: Record<string, string> = {
  PLANNING: 'bg-[#eeece7] text-[#3f3f46]',
  IN_PROGRESS: 'bg-blue-50 text-blue-700',
  ON_HOLD: 'bg-amber-50 text-amber-700',
  COMPLETED: 'bg-[#edfce9] text-[#003c33]',
  CANCELLED: 'bg-rose-50 text-rose-600',
};

/** Angka metrik yang menghitung naik. Dipisah jadi komponen karena hook tidak boleh
 *  dipanggil di dalam JSX bercabang. */
const Metric: React.FC<{ value: number }> = ({ value }) => (
  <p className="mt-3 font-mono text-3xl font-bold tabular-nums text-[#17171c]">{useCountUp(value)}</p>
);

export const WorkspaceDashboardPage: React.FC = () => {
  useResolveWorkspace();
  const { currentWorkspace } = useWorkspace();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!currentWorkspace) return;
    const fetchDashboardData = async () => {
      try {
        const res = await api.get(`/projects?workspaceId=${currentWorkspace.id}`);
        setProjects(res.data);
      } catch (err) {
        console.error('Failed to fetch projects:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchDashboardData();
  }, [currentWorkspace]);

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 overflow-y-auto p-4 sm:p-8 pb-24 lg:pb-0">
          {/* Header */}
          <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <h1 className="text-xl font-mono font-bold text-[#17171c] sm:text-2xl">Dashboard Workspace</h1>
              <p className="text-xs text-[#75758a] mt-0.5">Ringkasan statistik dan proyek aktif di {currentWorkspace?.name}.</p>
            </div>
            <Link
              to={`/w/${currentWorkspace?.slug}/projects`}
              className="press ring-focus inline-flex w-full items-center justify-center gap-2 rounded-full bg-[#17171c] px-5 py-2.5 text-xs font-semibold text-white shadow-sm transition hover:bg-black sm:w-auto"
            >
              <Plus className="h-4 w-4" /> Proyek Baru
            </Link>
          </div>

          {/* Stats Cards */}
          <div className="stagger mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <Skeleton className="h-3 w-20" />
                    <Skeleton className="h-8 w-8 rounded-full" />
                  </div>
                  <Skeleton className="mt-3 h-8 w-16" />
                </div>
              ))
            ) : (
              <>
                <div className="lift rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono font-bold text-[#75758a] uppercase">Total Proyek</span>
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#eeece7] text-[#17171c]">
                      <FolderKanban className="h-4 w-4" />
                    </div>
                  </div>
                  <Metric value={projects.length} />
                </div>

                <div className="lift rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono font-bold text-[#75758a] uppercase">Proyek Berjalan</span>
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-50 text-amber-700">
                      <Clock className="h-4 w-4" />
                    </div>
                  </div>
                  <Metric value={projects.filter((p) => p.status === 'IN_PROGRESS' || p.status === 'PLANNING').length} />
                </div>

                <div className="lift rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono font-bold text-[#75758a] uppercase">Selesai</span>
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#edfce9] text-[#003c33]">
                      <CheckCircle2 className="h-4 w-4" />
                    </div>
                  </div>
                  <Metric value={projects.filter((p) => p.status === 'COMPLETED').length} />
                </div>

                <div className="lift rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono font-bold text-[#75758a] uppercase">Role Anda</span>
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#003c33] text-[#edfce9]">
                      <Users className="h-4 w-4" />
                    </div>
                  </div>
                  <p className="mt-3 text-xl font-mono font-bold text-[#003c33]">{currentWorkspace?.userRole || 'MEMBER'}</p>
                </div>
              </>
            )}
          </div>

          {/* Denyut workspace: satu angka yang menjawab "sejauh apa kita" tanpa
              harus membaca tiap kartu proyek. */}
          {!loading && projects.length > 0 && (
            <section className="animate-fade-in-up mt-6 rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-xs sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#edfce9] text-[#003c33]">
                    <TrendingUp className="h-4.5 w-4.5" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-[#75758a]">Progres keseluruhan</p>
                    <p className="mt-0.5 text-[11px] text-[#93939f]">
                      Rata-rata dari {projects.length} proyek di workspace ini
                    </p>
                  </div>
                </div>
                <p className="font-mono text-3xl font-bold tabular-nums text-[#003c33]">
                  {Math.round(projects.reduce((n, p) => n + (p.progressPercent || 0), 0) / projects.length)}%
                </p>
              </div>
              <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-[#eeece7]">
                <div
                  className="animate-progress h-full rounded-full bg-gradient-to-r from-[#003c33] to-[#02ffcc] transition-[width] duration-700"
                  style={{ width: `${Math.round(projects.reduce((n, p) => n + (p.progressPercent || 0), 0) / projects.length)}%` }}
                />
              </div>
            </section>
          )}

          {/* Active Projects List */}
          <div className="mt-10">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-mono font-bold text-[#17171c]">Proyek Terbaru</h2>
              <Link
                to={`/w/${currentWorkspace?.slug}/projects`}
                className="text-xs font-semibold text-[#1863dc] hover:underline inline-flex items-center gap-1"
              >
                Lihat Semua <ArrowRight className="h-3 w-3" />
              </Link>
            </div>

            <div className="stagger mt-4 space-y-3">
              {loading ? (
                <SkeletonList count={3} />
              ) : projects.length === 0 ? (
                <div className="rounded-3xl border border-dashed border-[#d9d9dd] bg-white/50 p-5 sm:p-8 text-center text-xs text-[#75758a]">
                  Belum ada proyek di workspace ini. Klik "Proyek Baru" di atas untuk membuat proyek pertama Anda.
                </div>
              ) : (
                projects.slice(0, 5).map((project) => (
                  <Link
                    key={project.id}
                    to={`/w/${currentWorkspace?.slug}/projects/${project.id}`}
                    className="press ring-focus lift group flex flex-col items-start gap-3 rounded-2xl border border-[#d9d9dd] bg-white p-4 shadow-xs hover:border-[#17171c] sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0">
                      <h3 className="flex items-center gap-1.5 truncate text-sm font-bold text-[#17171c]">
                        {project.name}
                        <ArrowRight className="h-3.5 w-3.5 shrink-0 opacity-0 transition-all group-hover:translate-x-0.5 group-hover:opacity-100" />
                      </h3>
                      <p className="mt-0.5 line-clamp-1 text-xs text-[#75758a]">{project.description || 'Tidak ada deskripsi'}</p>
                    </div>

                    <div className="flex w-full flex-wrap items-center justify-between gap-3 sm:w-auto sm:justify-end sm:gap-6">
                      <span className={`shrink-0 rounded-full px-3 py-1 font-mono text-[10px] font-bold ${STATUS_STYLES[project.status] ?? STATUS_STYLES.PLANNING}`}>
                        {project.status}
                      </span>
                      <div className="w-full sm:w-32">
                        <div className="mb-1 flex justify-between font-mono text-[10px] text-[#75758a]">
                          <span>Progress</span>
                          <span className="font-bold tabular-nums text-[#003c33]">{project.progressPercent}%</span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#eeece7]">
                          <div
                            className={`animate-progress h-full rounded-full transition-[width] duration-700 ${
                              project.progressPercent >= 100 ? 'bg-emerald-500' : 'bg-[#003c33]'
                            }`}
                            style={{ width: `${project.progressPercent}%` }}
                          />
                        </div>
                      </div>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};
