import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { SkeletonCard } from '../components/Skeleton';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api } from '../services/api';
import { getErrMsg } from '../services/apiError';
import type { Project } from '../types';
import { FolderKanban, Plus, X } from 'lucide-react';
import { Link } from 'react-router-dom';

export const ProjectsPage: React.FC = () => {
  useResolveWorkspace();
  const { currentWorkspace } = useWorkspace();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [formError, setFormError] = useState('');

  // Form state
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [startDate, setStartDate] = useState('');
  const [targetDate, setTargetDate] = useState('');

  const fetchProjects = async () => {
    if (!currentWorkspace) return;
    try {
      const res = await api.get(`/projects?workspaceId=${currentWorkspace.id}`);
      setProjects(res.data);
    } catch (err) {
      console.error('Failed to fetch projects:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();
  }, [currentWorkspace]);

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentWorkspace) return;

    // Halaman ini punya form buat-proyek sendiri, terpisah dari CreateProjectModal.
    // Aturannya dulu hanya ditegakkan di modal itu, jadi jalur ini menerima target
    // sebelum tanggal mulai — persis pola "cuma satu jalur yang cek" yang jadi alasan
    // validasi dipusatkan di server.
    if (!name.trim()) {
      setFormError('Nama proyek wajib diisi.');
      return;
    }
    if (name.trim().length > 255) {
      setFormError('Nama proyek maksimal 255 karakter.');
      return;
    }
    if (startDate && targetDate && startDate > targetDate) {
      setFormError('Tanggal mulai harus sebelum atau sama dengan target selesai.');
      return;
    }
    setFormError('');

    try {
      await api.post('/projects', {
        workspaceId: currentWorkspace.id,
        name,
        description,
        startDate: startDate || null,
        targetDate: targetDate || null,
        clientVisible: true,
      });
      setShowModal(false);
      setFormError('');
      setName('');
      setDescription('');
      fetchProjects();
    } catch (err) {
      const msg = getErrMsg(err) ?? '';
      // Pesan server ditampilkan apa adanya. Fallback lama memukul rata semua
      // kegagalan jadi "coba lagi", jadi alasan validasi tidak pernah sampai ke user.
      setFormError(
        msg.includes('PLAN_LIMIT_REACHED')
          ? 'Anda telah mencapai batas proyek untuk paket Free. Upgrade ke Premium untuk membuat proyek tanpa batas.'
          : msg || 'Gagal membuat proyek. Silakan coba lagi.'
      );
      console.error('Failed to create project:', err);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 overflow-y-auto p-4 sm:p-8 pb-24 lg:pb-0">
          <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl font-mono font-bold text-[#17171c]">Daftar Proyek</h1>
              <p className="text-xs text-[#75758a] mt-0.5">Semua proyek dalam workspace {currentWorkspace?.name}.</p>
            </div>
            <button
              onClick={() => setShowModal(true)}
              className="press ring-focus inline-flex items-center gap-2 rounded-full bg-[#17171c] px-4 py-2.5 text-sm font-semibold text-[#eeece7] transition hover:bg-black"
            >
              <Plus className="h-4 w-4" /> Proyek Baru
            </button>
          </div>

          <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {loading ? (
              <>
                {Array.from({ length: 3 }).map((_, i) => (
                  <SkeletonCard key={i} />
                ))}
              </>
            ) : projects.length === 0 ? (
              <div className="col-span-full rounded-2xl border border-dashed border-[#75758a]/40 p-12 text-center text-[#75758a]">
                Belum ada proyek. Klik "Proyek Baru" untuk membuat proyek pertama Anda.
              </div>
            ) : (
              projects.map((p) => (
                <Link
                  key={p.id}
                  to={`/w/${currentWorkspace?.slug}/projects/${p.id}`}
                  className="press ring-focus hover-lift flex flex-col justify-between rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 transition shadow-[0_2px_10px_rgba(0,0,0,0.02)] hover:border-[#17171c]/30 hover:bg-white group"
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <span className="inline-flex items-center rounded-full bg-[#edfce9] px-2.5 py-1 text-xs font-semibold text-[#003c33] border border-[#003c33]/10">
                        {p.status}
                      </span>
                      <FolderKanban className="h-5 w-5 text-[#75758a] group-hover:text-[#17171c] transition" />
                    </div>

                    <h3 className="mt-4 text-lg font-bold text-[#17171c] group-hover:text-[#003c33] transition">{p.name}</h3>
                    <p className="mt-1 text-xs text-[#75758a] line-clamp-2">{p.description || 'Tidak ada deskripsi'}</p>
                  </div>

                  <div className="mt-6 border-t border-black/[0.06] pt-4">
                    <div className="flex items-center justify-between text-xs text-[#75758a] mb-1">
                      <span>Progres</span>
                      <span className="font-semibold text-[#17171c]">{p.progressPercent}%</span>
                    </div>
                    <div className="h-2 w-full rounded-full bg-[#eeece7] overflow-hidden">
                      <div
                        className="h-full bg-[#003c33]"
                        style={{ width: `${p.progressPercent}%` }}
                      ></div>
                    </div>
                  </div>
                </Link>
              ))
            )}
          </div>
        </main>
      </div>

      {/* Modal Create Project */}
      {showModal && (
        <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-3 backdrop-blur-xs sm:p-4">
            <div className="max-h-[calc(100dvh-1.5rem)] w-full max-w-md overflow-y-auto rounded-2xl border border-black/[0.08] bg-white p-4 shadow-xl sm:max-h-[calc(100dvh-2rem)] sm:p-6">
            <div className="flex items-center justify-between border-b border-black/[0.06] pb-4">
              <h3 className="font-mono text-lg font-bold text-[#17171c]">Buat Proyek Baru</h3>
              <button onClick={() => setShowModal(false)} className="text-[#75758a] hover:text-[#17171c]">
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleCreateProject} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-[#17171c]">Nama Proyek</label>
                <input
                  type="text"
                  required
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
                    onChange={(e) => { setStartDate(e.target.value); setFormError(''); }}
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2 text-xs text-[#17171c] outline-none focus:border-[#003c33]/50"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#17171c]">Target Selesai</label>
                  <input
                    type="date"
                    value={targetDate}
                    min={startDate || undefined}
                    onChange={(e) => { setTargetDate(e.target.value); setFormError(''); }}
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2 text-xs text-[#17171c] outline-none focus:border-[#003c33]/50"
                  />
                </div>
              </div>

              <button
                type="submit"
                className="press ring-focus mt-4 w-full rounded-xl bg-[#17171c] py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black"
              >
                Simpan Proyek
              </button>

              {formError && (
                <div className="mt-3 rounded-xl border border-rose-500/20 bg-rose-50 p-3 text-xs text-rose-700">
                  {formError}
                </div>
              )}
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
