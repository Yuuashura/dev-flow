import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { oauthApi } from '../services/api';

export const OAuthCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const provider = sessionStorage.getItem('oauth_provider') || 'github';
  const connectMode = sessionStorage.getItem('oauth_connect') === '1';
  const [status, setStatus] = useState(() => `Menghubungkan akun ${provider === 'github' ? 'GitHub' : 'Google'}...`);
  const [errorMsg, setErrorMsg] = useState('');
  const processed = useRef(false);

  useEffect(() => {
    if (processed.current) return;
    processed.current = true;

    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      const desc = searchParams.get('error_description') || 'Proses login dibatalkan atau ditolak oleh penyedia.';
      fail(desc);
      return;
    }

    if (!code || !state) {
      fail('Parameter OAuth tidak lengkap. Silakan coba lagi.');
      return;
    }

    const endpoint = connectMode ? `/github/connect/callback` : `/${provider}/callback`;

    oauthApi
      .post(endpoint, { code, state })
      .then((res) => succeed(res.data))
      .catch((err: unknown) => fail(extractMessage(err) || `Login ${provider === 'github' ? 'GitHub' : 'Google'} gagal. Silakan coba lagi.`));

    function extractMessage(err: unknown): string | null {
      if (typeof err === 'object' && err !== null && 'response' in err) {
        const response = (err as { response?: { data?: { error?: { message?: string } } } }).response;
        return response?.data?.error?.message ?? null;
      }
      return null;
    }

    function succeed(payload: unknown) {
      if (connectMode && window.opener) {
        window.opener.postMessage({ type: 'GH_CONNECT_SUCCESS', ...(payload as object) }, window.location.origin);
        window.close();
        return;
      }
      if (window.opener) {
        window.opener.postMessage({ type: 'OAUTH_SUCCESS', payload }, window.location.origin);
        window.close();
      } else {
        setStatus('Login berhasil. Anda dapat menutup halaman ini.');
      }
    }

    function fail(msg: string) {
      setErrorMsg(msg);
      setStatus('');
      if (window.opener) {
        const type = connectMode ? 'GH_CONNECT_ERROR' : 'OAUTH_ERROR';
        window.opener.postMessage({ type, error: msg }, window.location.origin);
        window.setTimeout(() => window.close(), 3000);
      }
    }
  }, [provider, connectMode, searchParams]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#eeece7] p-6 text-center text-[#17171c]">
      <div className="animate-scale-in rounded-3xl border border-[#d9d9dd] bg-white p-5 sm:p-8 shadow-lg max-w-sm w-full">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#003c33] text-[#edfce9] logo-pulse">
          <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="h-8 w-8 object-contain logo-float" />
        </div>
        {errorMsg
          ? <p className="mt-2 text-sm text-rose-600">{errorMsg}</p>
          : <p className="mt-2 text-xs text-[#75758a]">{status}</p>}
        {errorMsg && window.opener && (
          <p className="mt-3 text-xs text-[#75758a]">Halaman ini akan ditutup otomatis...</p>
        )}
        {errorMsg && !window.opener && (
          <button
            onClick={() => window.close()}
            className="mt-5 rounded-full border border-black/10 px-5 py-2 text-xs font-semibold transition hover:bg-[#eeece7]"
          >
            Tutup
          </button>
        )}
      </div>
    </div>
  );
};
