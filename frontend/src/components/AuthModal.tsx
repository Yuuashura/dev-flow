import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthModal } from '../context/AuthModalContext';
import { useAuth } from '../context/AuthContext';
import { api, oauthApi } from '../services/api';
import { apiError, errStatus } from '../services/apiError';
import { X, Mail, Lock, User as UserIcon, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import type { User } from '../types';

export const AuthModal: React.FC = () => {
  const { isOpen, mode, closeAuthModal, switchMode } = useAuthModal();
  const { login } = useAuth();
  const navigate = useNavigate();

  // Form states
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [githubLoading, setGithubLoading] = useState(false);

  // Reset states when modal opens/closes or mode changes
  useEffect(() => {
    setError('');
    setSuccessMsg('');
  }, [mode, isOpen]);

  // Dideklarasikan sebelum effect yang memakainya: effect di bawah menangkap
  // binding ini, dan referensi yang dibaca sebelum deklarasinya tidak ikut
  // ter-update saat nilainya berubah.
  const handlePostAuthRedirect = async (user: User) => {
    closeAuthModal();
    const pendingToken = sessionStorage.getItem('pending_invitation_token');
    if (pendingToken) {
      try {
        await api.post(`/invitations/${pendingToken}/accept`);
        sessionStorage.removeItem('pending_invitation_token');
      } catch (err) {
        // Kegagalan dulu ditelan diam-diam dan alurnya tetap jalan, jadi user masuk
        // tanpa pernah tahu undangannya tidak diterima. Token juga hanya dihapus saat
        // sukses, jadi token yang permanen tidak berlaku (kedaluwarsa, dicabut, salah
        // akun) dicoba ulang setiap kali login.
        const status = errStatus(err);
        const permanent = status !== null && status >= 400 && status < 500;
        if (permanent) {
          sessionStorage.removeItem('pending_invitation_token');
        }
        sessionStorage.setItem(
          'invitation_accept_error',
          apiError(err, 'Undangan tidak dapat diterima otomatis. Buka kembali link undangannya.')
        );
      }
    }
    if (user.globalRole === 'SUPER_ADMIN') {
      navigate('/admin/dashboard');
    } else {
      navigate('/select-workspace');
    }
  };

  // Listen for OAuth message from popup window (Google / GitHub)
  useEffect(() => {
    const handleOAuthMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === 'OAUTH_SUCCESS') {
        const { accessToken, refreshToken, user } = event.data.payload;
        login(accessToken, refreshToken, user);
        handlePostAuthRedirect(user);
      } else if (event.data?.type === 'OAUTH_ERROR') {
        setError(event.data.error || 'Login OAuth gagal.');
        setGoogleLoading(false);
        setGithubLoading(false);
      }
    };

    window.addEventListener('message', handleOAuthMessage);
    return () => window.removeEventListener('message', handleOAuthMessage);
  }, [login, closeAuthModal, navigate]);

  if (!isOpen) return null;

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await api.post('/auth/login', { email, password });
      const { accessToken, refreshToken, user } = res.data;
      login(accessToken, refreshToken, user);
      handlePostAuthRedirect(user);
    } catch (err) {
      setError(apiError(err, 'Login gagal. Periksa email & password.'));
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Konfirmasi password tidak cocok');
      return;
    }

    setLoading(true);
    try {
      await api.post('/auth/register', {
        fullName,
        email,
        password,
        confirmPassword,
      });
      setSuccessMsg(`Email verifikasi telah dikirim ke ${email}. Silakan cek inbox Anda.`);
    } catch (err) {
      setError(apiError(err, 'Registrasi gagal. Coba lagi.'));
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setError('');
    setGoogleLoading(true);
    try {
      sessionStorage.setItem('oauth_provider', 'google');
      const res = await oauthApi.get('/google/authorize');
      const { authUrl } = res.data;
      openPopup(authUrl, 'Google OAuth Login');
    } catch {
      setError('Gagal menghubungkan ke Google.');
      setGoogleLoading(false);
    }
  };

  const handleGitHubLogin = async () => {
    setError('');
    setGithubLoading(true);
    try {
      sessionStorage.setItem('oauth_provider', 'github');
      const res = await oauthApi.get('/github/authorize');
      const { authUrl } = res.data;
      openPopup(authUrl, 'GitHub OAuth Login');
    } catch {
      setError('Gagal menghubungkan ke GitHub.');
      setGithubLoading(false);
    }
  };

  const openPopup = (url: string, title: string) => {
    const width = 500;
    const height = 650;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;

    const popup = window.open(
      url,
      title,
      `width=${width},height=${height},top=${top},left=${left},scrollbars=yes,status=yes`
    );

    if (!popup) return;
    const checkClosed = window.setInterval(() => {
      if (popup.closed) {
        window.clearInterval(checkClosed);
        setGoogleLoading(false);
        setGithubLoading(false);
      }
    }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 backdrop-blur-xs animate-in fade-in duration-200 sm:p-4">
      <div
        className="relative max-h-[calc(100dvh-1.5rem)] w-full max-w-md overflow-y-auto rounded-3xl border border-[#d9d9dd] bg-white p-5 shadow-2xl transition-all animate-scale-in sm:max-h-[calc(100dvh-2rem)] sm:p-8"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={closeAuthModal}
          className="absolute right-5 top-5 flex h-8 w-8 items-center justify-center rounded-full bg-[#eeece7] text-[#17171c] hover:bg-[#d9d9dd] transition cursor-pointer"
        >
          <X className="h-4 w-4" />
        </button>

        {/* Modal Header */}
        <div className="text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#003c33] text-[#edfce9] logo-pulse">
            <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="h-7 w-7 object-contain logo-float" />
          </div>
          <h2 className="mt-4 text-2xl font-mono font-bold text-[#17171c]">
            {mode === 'login' ? 'Masuk ke DevFlow' : 'Daftar Akun DevFlow'}
          </h2>
          <p className="mt-1 text-xs text-[#75758a]">
            {mode === 'login'
              ? 'Selamat datang kembali! Pilih metode masuk Anda.'
              : 'Gabung platform DevFlow SaaS hari ini.'}
          </p>
        </div>

        {/* Mode Switcher Tabs */}
        <div className="mt-6 flex rounded-full bg-[#eeece7] p-1 text-xs font-semibold">
          <button
            onClick={() => switchMode('login')}
            className={`flex-1 rounded-full py-2 transition cursor-pointer ${
              mode === 'login' ? 'bg-[#17171c] text-white shadow-xs' : 'text-[#616161] hover:text-[#17171c]'
            }`}
          >
            Masuk
          </button>
          <button
            onClick={() => switchMode('register')}
            className={`flex-1 rounded-full py-2 transition cursor-pointer ${
              mode === 'register' ? 'bg-[#17171c] text-white shadow-xs' : 'text-[#616161] hover:text-[#17171c]'
            }`}
          >
            Daftar
          </button>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mt-4 flex items-center gap-2.5 rounded-2xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-700">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Success Verification Notice (for manual register) */}
        {successMsg ? (
          <div className="mt-6 py-4 text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-[#003c33]" />
            <h3 className="mt-3 text-lg font-mono font-bold text-[#17171c]">Verifikasi Email Dikirim</h3>
            <p className="mt-2 text-xs text-[#616161] leading-relaxed">{successMsg}</p>
            <button
              onClick={() => switchMode('login')}
              className="press ring-focus mt-5 w-full rounded-full bg-[#17171c] py-3 text-xs font-semibold text-white shadow-sm hover:bg-black transition cursor-pointer"
            >
              Sudah Verifikasi? Masuk Sekarang
            </button>
          </div>
        ) : (
          <>
            {/* OAuth Buttons (Google & GitHub) */}
            <div className="mt-6 space-y-2.5">
              <button
                onClick={handleGoogleLogin}
                disabled={googleLoading || githubLoading}
                className="press ring-focus flex w-full items-center justify-center gap-3 rounded-full border border-[#d9d9dd] bg-white py-2.5 text-xs font-bold text-[#17171c] shadow-xs hover:bg-[#eeece7]/50 disabled:opacity-50 transition cursor-pointer"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                <span>{googleLoading ? 'Menghubungkan Google...' : 'Lanjutkan dengan Google'}</span>
              </button>

              <button
                onClick={handleGitHubLogin}
                disabled={googleLoading || githubLoading}
                className="press ring-focus flex w-full items-center justify-center gap-3 rounded-full bg-[#17171c] py-2.5 text-xs font-bold text-white shadow-xs hover:bg-black disabled:opacity-50 transition cursor-pointer"
              >
                <svg className="h-4 w-4 fill-current text-white" viewBox="0 0 24 24">
                  <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
                </svg>
                <span>{githubLoading ? 'Menghubungkan GitHub...' : 'Lanjutkan dengan GitHub'}</span>
              </button>
            </div>

            {/* Divider */}
            <div className="my-4 flex items-center gap-3">
              <div className="h-px flex-1 bg-[#d9d9dd]" />
              <span className="text-[10px] font-mono text-[#75758a] uppercase">atau email</span>
              <div className="h-px flex-1 bg-[#d9d9dd]" />
            </div>

            {/* Forms */}
            {mode === 'login' ? (
              <form onSubmit={handleLoginSubmit} className="space-y-3">
                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Alamat Email</label>
                  <div className="relative mt-1">
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="andi@example.com"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2.5 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <Mail className="absolute left-3.5 top-3 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Password</label>
                  <div className="relative mt-1">
                    <input
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2.5 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <Lock className="absolute left-3.5 top-3 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="press ring-focus w-full rounded-full bg-[#17171c] py-3 text-xs font-semibold text-white shadow-sm hover:bg-black disabled:opacity-50 transition mt-2 cursor-pointer"
                >
                  {loading ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Masuk Akun'}
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegisterSubmit} className="space-y-2.5">
                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Nama Lengkap</label>
                  <div className="relative mt-1">
                    <input
                      type="text"
                      required
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Andi Pratama"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <UserIcon className="absolute left-3.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Alamat Email</label>
                  <div className="relative mt-1">
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="andi@example.com"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <Mail className="absolute left-3.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Password</label>
                  <div className="relative mt-1">
                    <input
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="Minimal 8 karakter"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <Lock className="absolute left-3.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-mono font-bold uppercase text-[#75758a]">Konfirmasi Password</label>
                  <div className="relative mt-1">
                    <input
                      type="password"
                      required
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Ulangi password"
                      className="w-full rounded-2xl border border-[#d9d9dd] bg-[#eeece7]/30 px-4 py-2 pl-10 text-xs font-medium text-[#17171c] placeholder-slate-400 outline-none focus:border-[#17171c] focus:bg-white transition"
                    />
                    <Lock className="absolute left-3.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="press ring-focus w-full rounded-full bg-[#17171c] py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-black disabled:opacity-50 transition mt-2 cursor-pointer"
                >
                  {loading ? 'Mendaftarkan...' : 'Daftar Sekarang'}
                </button>
              </form>
            )}
          </>
        )}
      </div>
    </div>
  );
};
