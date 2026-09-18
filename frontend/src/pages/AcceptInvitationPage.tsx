import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useAuthModal } from '../context/AuthModalContext';
import { useWorkspace } from '../context/WorkspaceContext';
import type { Invitation } from '../types';
import { Loader2, AlertCircle, CheckCircle2, ArrowRight } from 'lucide-react';
import { apiError } from '../services/apiError';

export const AcceptInvitationPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const { user } = useAuth();
  const { openAuthModal } = useAuthModal();
  const { refreshWorkspaces } = useWorkspace();

  const [invitation, setInvitation] = useState<Invitation | null>(null);
  const [loading, setLoading] = useState(true);
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!token) {
      setError('Token undangan tidak ditemukan.');
      setLoading(false);
      return;
    }

    // Save token in sessionStorage in case user registers/logins
    sessionStorage.setItem('pending_invitation_token', token);

    const checkToken = async () => {
      try {
        const res = await api.get(`/invitations/${token}`);
        setInvitation(res.data);
      } catch (err) {
        setError(apiError(err, 'Undangan tidak valid atau telah kadaluarsa.'));
      } finally {
        setLoading(false);
      }
    };

    checkToken();
  }, [token]);

  // Dideklarasikan sebelum effect yang memanggilnya: referensi yang dibaca
  // sebelum deklarasinya tidak ikut ter-update saat nilainya berubah.
  const handleAccept = async () => {
    if (!token) return;
    setAccepting(true);
    setError('');
    try {
      await api.post(`/invitations/${token}/accept`);
      sessionStorage.removeItem('pending_invitation_token');
      setSuccess(true);
      await refreshWorkspaces();
      setTimeout(() => {
        navigate('/select-workspace');
      }, 1500);
    } catch (err) {
      setError(apiError(err, 'Gagal menerima undangan. Coba lagi.'));
    } finally {
      setAccepting(false);
    }
  };

  // Auto-accept sekali saja saat user sudah login.
  //
  // Penjaga lama membaca success/accepting/error padahal dep array-nya hanya
  // [user, token, invitation] — ketiganya nilai basi dari render pertama, jadi
  // penjaga itu tidak benar-benar menjaga apa pun dan undangan bisa diterima dua
  // kali. Ref tidak ikut siklus render, jadi penjaganya selalu nilai terkini.
  const autoAccepted = useRef(false);
  useEffect(() => {
    if (autoAccepted.current || !user || !token || !invitation) return;
    autoAccepted.current = true;
    void handleAccept();
  }, [user, token, invitation]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-[#eeece7] px-6 py-12 text-[#17171c]">
      <div className="w-full max-w-md rounded-3xl border border-black/[0.07] bg-[#faf9f7] p-5 sm:p-8 text-center shadow-xl animate-scale-in">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#003c33] text-[#edfce9] shadow-sm logo-pulse">
          <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="h-8 w-8 object-contain logo-float" />
        </div>

        {loading ? (
          <div className="py-8">
            <Loader2 className="mx-auto h-8 w-8 animate-spin text-[#003c33]" />
            <p className="mt-3 text-xs font-mono text-[#75758a]">Memeriksa status undangan...</p>
          </div>
        ) : error ? (
          <div className="py-6">
            <AlertCircle className="mx-auto h-12 w-12 text-rose-500" />
            <h3 className="mt-4 font-mono text-xl font-bold text-[#17171c]">Undangan Tidak Valid</h3>
            <p className="mt-2 text-xs text-[#75758a] leading-relaxed">{error}</p>
            <button
              onClick={() => navigate('/')}
              className="press ring-focus mt-6 w-full rounded-full bg-[#17171c] py-3 text-xs font-semibold text-white shadow-sm hover:bg-black transition cursor-pointer"
            >
              Kembali ke Beranda
            </button>
          </div>
        ) : success ? (
          <div className="py-6">
            <CheckCircle2 className="mx-auto h-12 w-12 text-[#003c33]" />
            <h3 className="mt-4 font-mono text-xl font-bold text-[#17171c]">Undangan Diterima!</h3>
            <p className="mt-2 text-xs text-[#75758a]">
              Selamat! Anda telah bergabung ke workspace. Mengalihkan ke dashboard...
            </p>
          </div>
        ) : (
          <div className="mt-6">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#edfce9] px-3 py-1 text-[11px] font-mono font-bold text-[#003c33] border border-[#003c33]/10">
              UNDANGAN KOLABORASI
            </span>
            <h2 className="mt-4 font-mono text-2xl font-bold text-[#17171c]">
              Bergabung ke Workspace
            </h2>
            <p className="mt-2 text-xs text-[#616161] leading-relaxed">
              Anda diundang bergabung sebagai <span className="font-bold text-[#003c33]">{invitation?.role}</span> untuk email{' '}
              <span className="font-bold text-[#17171c]">{invitation?.email}</span>.
            </p>

            {user ? (
              <div className="mt-6">
                <button
                  onClick={handleAccept}
                  disabled={accepting}
                  className="press ring-focus w-full rounded-full bg-[#17171c] py-3.5 text-xs font-semibold text-white shadow-sm hover:bg-black disabled:opacity-50 transition cursor-pointer flex items-center justify-center gap-2"
                >
                  {accepting ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Terima Undangan Sekarang'} <ArrowRight className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <div className="mt-6 space-y-3">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-left text-xs text-amber-800">
                  <p className="font-semibold mb-1">Akun Diperlukan</p>
                  <p>
                    Anda belum masuk. Silakan mendaftar akun baru atau masuk dengan email <span className="font-bold">{invitation?.email}</span> terlebih dahulu.
                  </p>
                </div>

                <button
                  onClick={() => openAuthModal('register')}
                  className="press ring-focus w-full rounded-full bg-[#17171c] py-3.5 text-xs font-semibold text-white shadow-sm hover:bg-black transition cursor-pointer"
                >
                  Daftar Akun Baru
                </button>

                <button
                  onClick={() => openAuthModal('login')}
                  className="press ring-focus w-full rounded-full border border-[#d9d9dd] bg-white py-3.5 text-xs font-semibold text-[#17171c] shadow-xs hover:bg-slate-50 transition cursor-pointer"
                >
                  Sudah Punya Akun? Masuk
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
