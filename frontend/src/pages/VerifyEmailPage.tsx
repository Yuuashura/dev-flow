import React, { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api } from '../services/api';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { apiError } from '../services/apiError';

export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<'success' | 'error'>('error');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setStatus('error');
      setMessage('Token verifikasi tidak ditemukan');
      return;
    }

    const verify = async () => {
      try {
        const res = await api.get(`/auth/verify-email?token=${token}`);
        setStatus('success');
        setMessage(res.data.message || 'Email berhasil diverifikasi!');
      } catch (err) {
        setStatus('error');
        setMessage(apiError(err, 'Token verifikasi kadaluarsa atau tidak valid.'));
      } finally {
        setLoading(false);
      }
    };

    verify();
  }, [token]);

  return (
    <div className="flex min-h-screen flex-col justify-center items-center bg-[#eeece7] px-6 py-12 text-[#17171c]">
      <div className="w-full max-w-md rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-5 sm:p-8 text-center shadow-[0_2px_10px_rgba(0,0,0,0.03)] animate-scale-in">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#003c33] text-[#edfce9] shadow-sm logo-pulse">
          <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="h-8 w-8 object-contain logo-float" />
        </div>
        {loading ? (
          <div className="py-8">
            <Loader2 className="mx-auto h-12 w-12 animate-spin text-[#003c33]" />
            <p className="mt-4 text-sm text-[#75758a]">Memverifikasi email Anda...</p>
          </div>
        ) : status === 'success' ? (
          <div className="py-4">
            <CheckCircle2 className="mx-auto h-16 w-16 text-[#003c33]" />
            <h3 className="mt-4 font-mono text-2xl font-bold text-[#17171c]">Verifikasi Berhasil!</h3>
            <p className="mt-2 text-sm text-[#75758a]">{message}</p>
            <Link
              to="/"
              className="press ring-focus mt-6 inline-block rounded-full bg-[#17171c] px-6 py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black"
            >
              Masuk Akun
            </Link>
          </div>
        ) : (
          <div className="py-4">
            <XCircle className="mx-auto h-16 w-16 text-rose-500" />
            <h3 className="mt-4 font-mono text-2xl font-bold text-[#17171c]">Verifikasi Gagal</h3>
            <p className="mt-2 text-sm text-[#75758a]">{message}</p>
            <Link
              to="/"
              className="press ring-focus mt-6 inline-block rounded-full border border-black/[0.12] bg-white px-6 py-3 text-sm font-semibold text-[#17171c] hover:bg-[#eeece7]/60"
            >
              Kembali ke Beranda
            </Link>
          </div>
        )}
      </div>
    </div>
  );
};