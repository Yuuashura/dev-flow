import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useWorkspace } from '../context/WorkspaceContext';
import { api } from '../services/api';
import { getErrMsg } from '../services/apiError';
import { Building2, Globe, AlertCircle } from 'lucide-react';

export const OnboardingPage: React.FC = () => {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [businessType, setBusinessType] = useState('SOFTWARE_HOUSE');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { setCurrentWorkspace, refreshWorkspaces } = useWorkspace();
  const navigate = useNavigate();

  const handleNameChange = (val: string) => {
    setName(val);
    setSlug(val.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await api.post('/workspaces', {
        name,
        slug,
        businessType,
        timezone: 'Asia/Jakarta',
      });
      await refreshWorkspaces();
      setCurrentWorkspace(res.data);
      navigate(`/w/${res.data.slug}/dashboard`);
    } catch (err: unknown) {
      const msg = getErrMsg(err) ?? '';
      // Selain plan-limit, pesan server ditampilkan apa adanya. Fallback lama
      // ("Pastikan slug unik") menegaskan satu sebab tertentu yang biasanya salah —
      // timezone tidak dikenal, nama terlalu panjang, atau slug berformat salah
      // semuanya muncul sebagai saran yang menyesatkan.
      setError(
        msg.includes('PLAN_LIMIT_REACHED')
          ? 'Anda telah mencapai batas workspace untuk paket Free. Upgrade ke Premium untuk membuat workspace tanpa batas.'
          : msg || 'Gagal membuat workspace. Coba lagi.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-[#eeece7] px-6 py-12 text-[#17171c]">
      <div className="w-full max-w-lg rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-5 sm:p-8 shadow-[0_2px_10px_rgba(0,0,0,0.03)]">
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#003c33] shadow-sm logo-pulse">
            <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="h-8 w-8 object-contain logo-float" />
          </div>
          <h2 className="mt-4 font-mono text-2xl font-bold tracking-tight text-[#17171c]">Buat Workspace Anda</h2>
          <p className="mt-1 text-sm text-[#75758a]">Workspace adalah pusat kolaborasi tim, proyek, dan klien Anda.</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-3 rounded-xl border border-rose-500/20 bg-rose-50 p-4 text-sm text-rose-700">
            <AlertCircle className="h-5 w-5 shrink-0 text-rose-500" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-8 space-y-5">
          <div>
            <label className="block text-sm font-medium text-[#17171c]">Nama Workspace</label>
            <div className="relative mt-2">
              <input
                type="text"
                required
                value={name}
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="Software House Studio"
                className="w-full rounded-xl border border-black/10 bg-white/60 px-4 py-3 pl-11 text-sm text-[#17171c] placeholder-[#75758a] outline-none focus:border-[#003c33]/50 focus:ring-1 focus:ring-[#003c33]/30"
              />
              <Building2 className="absolute left-3.5 top-3.5 h-4 w-4 text-[#75758a]" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#17171c]">URL Slug (Unik)</label>
            <div className="relative mt-2">
              <input
                type="text"
                required
                value={slug}
                onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                placeholder="software-house-studio"
                className="w-full rounded-xl border border-black/10 bg-white/60 px-4 py-3 pl-24 text-sm text-[#17171c] placeholder-[#75758a] outline-none focus:border-[#003c33]/50 focus:ring-1 focus:ring-[#003c33]/30"
              />
              <span className="absolute left-4 top-3 text-sm text-[#75758a]">saas.app/w/</span>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#17171c]">Jenis Bisnis</label>
            <div className="relative mt-2">
              <select
                value={businessType}
                onChange={(e) => setBusinessType(e.target.value)}
                className="w-full appearance-none rounded-xl border border-black/10 bg-white/60 px-4 py-3 pl-11 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50 focus:ring-1 focus:ring-[#003c33]/30"
              >
                <option value="SOFTWARE_HOUSE">Software House / Agency</option>
                <option value="STARTUP">Startup</option>
                <option value="FREELANCER">Freelancer</option>
              </select>
              <Globe className="absolute left-3.5 top-3.5 h-4 w-4 text-[#75758a]" />
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-4">
                <svg className="h-4 w-4 text-[#75758a]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="press ring-focus mt-2 w-full rounded-xl bg-[#17171c] py-3.5 text-sm font-semibold text-[#eeece7] transition hover:bg-black disabled:opacity-50"
          >
            {loading ? 'Membuat Workspace...' : 'Buat Workspace \u2192'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-[#75758a]">
          Anda dapat mengubah detail ini nanti di pengaturan workspace.
        </p>
      </div>
    </div>
  );
};
