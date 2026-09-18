import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { apiError } from '../services/apiError';
import { Skeleton } from '../components/Skeleton';
import type { OrderHistoryPage, OrderRecord, OwnerPlan, Plan, PaymentStatus } from '../types';
import { Check, Sparkles, Loader2, AlertCircle, X, ShieldCheck, CreditCard, ArrowRight, History, ExternalLink, RefreshCw } from 'lucide-react';

const getErrorMessage = (error: unknown) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    return (error as { response?: { data?: { error?: { message?: string } } } }).response?.data?.error?.message;
  }
  return undefined;
};

export const BillingPage: React.FC = () => {
  useResolveWorkspace();
  const { currentWorkspace } = useWorkspace();
  const { user } = useAuth();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [planCode, setPlanCode] = useState('FREE');
  const [ownerPlan, setOwnerPlan] = useState<OwnerPlan | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [checkoutForm, setCheckoutForm] = useState({
    customerName: user?.fullName || '',
    customerEmail: user?.email || '',
    customerPhone: user?.phoneNumber || '',
    companyName: user?.companyName || '',
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [searchParams, setSearchParams] = useSearchParams();
  const payment = searchParams.get('payment');
  const externalId = searchParams.get('external_id');
  const [history, setHistory] = useState<OrderRecord[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  // Dibedakan dari "riwayat memang kosong": tanpa ini gagal-muat tampil sebagai
  // "belum ada transaksi".
  const [historyError, setHistoryError] = useState('');
  const [historyPage, setHistoryPage] = useState<OrderHistoryPage | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | ''>('');
  const pageSize = 8;

  const fetchData = async () => {
    try {
      const pRes = await api.get('/billing/plans');
      setPlans(pRes.data);

      const ownerRes = await api.get('/billing/owner/plan');
      setPlanCode(ownerRes.data.planCode);
      setOwnerPlan(ownerRes.data);
    } catch (err) {
      // Dulu hanya di-log. Komponen tetap di state default-nya, jadi kegagalan muat
      // dirender sebagai fakta: paket tampil FREE dan user bisa membeli ulang paket
      // yang sebenarnya sudah aktif.
      console.error('Failed to fetch billing data:', err);
      setError(apiError(err, 'Gagal memuat data paket. Muat ulang halaman untuk melihat status terbaru.'));
    }
  };

  const fetchHistory = async (page = pageIndex, status = statusFilter) => {
    setHistoryLoading(true);
    setHistoryError('');
    try {
      const params: Record<string, string | number> = { page, size: pageSize };
      if (status) params.status = status;
      const res = await api.get('/billing/payments', { params });
      setHistory(res.data.content);
      setHistoryPage(res.data);
    } catch (err) {
      console.error('Failed to fetch order history:', err);
      setHistory([]);
      setHistoryError(apiError(err, 'Gagal memuat riwayat pembayaran.'));
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    // Load plan data and order history when the user changes workspace.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchData();
    void fetchHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentWorkspace]);

  useEffect(() => {
    if (!payment || !externalId) return;

    if (payment === 'failed') {
      return;
    }

    let attempts = 0;
    const checkPayment = async () => {
      try {
        const res = await api.get(`/billing/payments/${externalId}`);
        if (res.data.status === 'PAID') {
          setMessage('Pembayaran berhasil. Paket Premium Anda sudah aktif.');
          await fetchData();
          setSearchParams({}, { replace: true });
          return;
        }
        attempts += 1;
        if (attempts < 8) window.setTimeout(checkPayment, 1500);
        else setMessage('Pembayaran sedang dikonfirmasi. Muat ulang halaman ini beberapa saat lagi.');
      } catch {
        setError('Status pembayaran belum dapat dikonfirmasi. Silakan muat ulang halaman.');
      }
    };
    void checkPayment();
  }, [payment, externalId, setSearchParams]);

  const handleUpgrade = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    setMessage('');
    try {
      if (!currentWorkspace) throw new Error('Workspace belum dipilih');
      const res = await api.post('/billing/owner/checkout', {
        workspaceSlug: currentWorkspace.slug,
        ...checkoutForm,
      });
      window.location.assign(res.data.invoiceUrl);
    } catch (err: unknown) {
      setError(getErrorMessage(err) || 'Gagal memproses upgrade.');
    } finally {
      setLoading(false);
    }
  };

  const planEndLabel = ownerPlan?.planEnd
    ? new Intl.DateTimeFormat('id-ID', { dateStyle: 'long' }).format(new Date(ownerPlan.planEnd))
    : null;

  const changeStatusFilter = (status: PaymentStatus | '') => {
    setStatusFilter(status);
    setPageIndex(0);
    void fetchHistory(0, status);
  };

  const changePage = (page: number) => {
    setPageIndex(page);
    void fetchHistory(page);
  };

  const statusMeta: Record<PaymentStatus, { label: string; className: string }> = {
    PAID: { label: 'Dibayar', className: 'bg-[#edfce9] text-[#003c33] border-[#003c33]/10' },
    PENDING: { label: 'Menunggu', className: 'bg-amber-50 text-amber-700 border-amber-200/60' },
    EXPIRED: { label: 'Kedaluwarsa', className: 'bg-slate-100 text-slate-600 border-slate-200' },
    FAILED: { label: 'Gagal', className: 'bg-rose-50 text-rose-700 border-rose-200/60' },
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 max-w-5xl flex-1 overflow-y-auto p-4 sm:p-8 pb-24 lg:pb-0">
          <div>
            <h1 className="text-2xl font-mono font-bold text-[#17171c]">Billing & Langganan</h1>
            <p className="text-xs text-[#75758a] mt-0.5">Pilih paket langganan yang sesuai untuk kebutuhan tim Anda.</p>
          </div>

          {/* Current Plan Banner */}
          <div className="mt-6 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
            <div className="flex items-center justify-between flex-wrap gap-4">
              <div>
                <span className="font-mono text-xs font-semibold uppercase tracking-wider text-[#003c33]">Paket Saat Ini</span>
                <h3 className="mt-1 font-mono text-2xl font-bold text-[#17171c]">
                  {planCode === 'PRO' ? 'Premium' : 'Free'}
                </h3>
                <p className="mt-1 text-xs text-[#75758a]">
                  {planCode === 'PRO'
                    ? `Semua fitur tanpa batas aktif${planEndLabel ? ` sampai ${planEndLabel}` : ''}.`
                    : 'Terbatas 3 workspace dan 3 proyek per workspace. Upgrade untuk tanpa batas.'}
                </p>
              </div>
              <span className={`rounded-full px-4 py-1.5 text-xs font-bold border ${
                planCode === 'PRO'
                  ? 'bg-[#003c33] text-[#eeece7] border-[#003c33]'
                  : 'bg-[#eeece7] text-[#17171c] border-black/10'
              }`}>
                {planCode === 'PRO' ? 'PREMIUM' : 'FREE'}
              </span>
            </div>
          </div>

          {message && (
            <div className="mt-4 flex items-center gap-3 rounded-xl border border-emerald-500/20 bg-emerald-50 p-4 text-sm text-emerald-700">
              <Check className="h-5 w-5 shrink-0" /> <span>{message}</span>
            </div>
          )}

          {error && (
            <div className="mt-4 flex items-center gap-3 rounded-xl border border-rose-500/20 bg-rose-50 p-4 text-sm text-rose-700">
              <AlertCircle className="h-5 w-5 shrink-0" /> <span>{error}</span>
            </div>
          )}

          {payment === 'failed' && !error && (
            <div className="mt-4 flex items-center gap-3 rounded-xl border border-rose-500/20 bg-rose-50 p-4 text-sm text-rose-700">
              <AlertCircle className="h-5 w-5 shrink-0" />
              <span>Pembayaran dibatalkan atau invoice telah kedaluwarsa. Paket Anda tidak berubah.</span>
            </div>
          )}

          {/* Plans Grid */}
          <div className="stagger mt-10 grid gap-8 md:grid-cols-2">
            {plans.map((plan) => {
              const isCurrent = plan.code === planCode;
              return (
                <div
                  key={plan.id}
                  className={`flex flex-col justify-between rounded-2xl border p-8 relative overflow-hidden ${
                    plan.code === 'PRO' && planCode !== 'PRO'
                      ? 'border-[#003c33]/40 bg-[#faf9f7] shadow-[0_2px_20px_rgba(0,60,51,0.1)]'
                      : 'border-black/[0.07] bg-[#faf9f7] shadow-[0_2px_10px_rgba(0,0,0,0.02)]'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <h3 className="font-mono text-2xl font-bold text-[#17171c]">{plan.name}</h3>
                      {plan.code === 'PRO' && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-[#003c33] px-3 py-1 text-xs font-bold text-[#eeece7]">
                          <Sparkles className="h-3 w-3" /> Recommended
                        </span>
                      )}
                      {isCurrent && (
                        <span className="inline-flex items-center rounded-full bg-[#edfce9] px-3 py-1 text-xs font-bold text-[#003c33] border border-[#003c33]/10">
                          Aktif
                        </span>
                      )}
                    </div>
                    <p className="mt-2 text-sm text-[#75758a]">{plan.description}</p>

                    <div className="mt-6">
                      <span className="font-mono text-3xl font-bold text-[#17171c] sm:text-4xl">
                        Rp {plan.priceAmount.toLocaleString('id-ID')}
                      </span>
                      <span className="text-sm text-[#75758a]"> / bulan</span>
                    </div>

                    <ul className="mt-8 space-y-3 text-sm text-[#616161]">
                      <li className="flex items-center gap-2">
                        <Check className="h-4 w-4 text-[#003c33]" />
                        {plan.code === 'PRO' ? 'Workspace tanpa batas' : 'Maksimal 3 workspace'}
                      </li>
                      <li className="flex items-center gap-2">
                        <Check className="h-4 w-4 text-[#003c33]" />
                        {plan.code === 'PRO' ? 'Proyek tanpa batas per workspace' : 'Maksimal 3 proyek per workspace'}
                      </li>
                      <li className="flex items-center gap-2">
                        <Check className="h-4 w-4 text-[#003c33]" />
                        {plan.code === 'PRO' ? 'Anggota & GitHub tanpa batas' : '5 anggota & 1 repo GitHub'}
                      </li>
                      <li className="flex items-center gap-2">
                        <Check className="h-4 w-4 text-[#003c33]" />
                        {plan.code === 'PRO' ? 'Milestone tanpa batas' : '3 milestone per proyek'}
                      </li>
                    </ul>
                  </div>

                  {isCurrent && (
                    <div className="mt-8 w-full rounded-full border border-black/10 bg-[#eeece7]/60 py-3.5 text-center text-sm font-semibold text-[#616161]">
                      {plan.code === 'PRO' && planEndLabel ? `Aktif sampai ${planEndLabel}` : 'Paket Terpasang'}
                    </div>
                  )}
                  {plan.code === 'FREE' && !isCurrent && (
                    <div className="mt-8 w-full rounded-full border border-black/10 py-3.5 text-center text-sm font-semibold text-[#75758a]">
                      Paket gratis
                    </div>
                  )}
                  {plan.code === 'PRO' && planCode === 'FREE' && (
                    <button
                      onClick={() => setCheckoutOpen(true)}
                      disabled={loading}
                      className="press ring-focus mt-8 w-full rounded-full bg-[#17171c] py-3.5 text-sm font-semibold text-[#eeece7] transition hover:bg-black disabled:opacity-50 cursor-pointer"
                    >
                      Upgrade ke Premium
                    </button>
                  )}
                </div>
              );
            })}
          </div>

          {/* Order History */}
          <div className="mt-10 rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-8">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="rounded-xl bg-[#17171c] p-2.5 text-white"><History className="h-5 w-5" /></div>
                <div>
                  <h2 className="text-lg font-bold">Riwayat Pembelian</h2>
                  <p className="text-xs text-[#75758a]">Daftar invoice langganan Anda.</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                {(['', 'PAID', 'PENDING', 'EXPIRED', 'FAILED'] as (PaymentStatus | '')[]).map((status) => (
                  <button
                    key={status || 'ALL'}
                    onClick={() => changeStatusFilter(status)}
                    className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                      statusFilter === status
                        ? 'border-[#17171c] bg-[#17171c] text-white'
                        : 'border-black/10 bg-white text-[#616161] hover:bg-[#eeece7]'
                    }`}
                  >
                    {status === '' ? 'Semua' : statusMeta[status].label}
                  </button>
                ))}
              </div>
            </div>

            {historyLoading ? (
              <div className="mt-5">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="flex items-center justify-between border-b border-black/[0.05] py-4">
                    <div className="space-y-2 flex-1">
                      <Skeleton className="h-4 w-40" />
                      <Skeleton className="h-3 w-56" />
                    </div>
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-4 w-16 text-right" />
                    <Skeleton className="h-6 w-20 rounded-full" />
                    <Skeleton className="h-6 w-16 rounded-full" />
                  </div>
                ))}
              </div>
            ) : historyError ? (
              // Gagal memuat bukan "belum ada transaksi". Tanpa cabang ini keduanya
              // terlihat sama, dan user menyimpulkan pembayarannya tidak tercatat.
              <div role="alert" className="animate-toast-in rounded-2xl border border-rose-500/20 bg-rose-50 px-6 py-10 text-center">
                <AlertCircle className="mx-auto h-8 w-8 text-rose-400" />
                <p className="mt-3 text-sm font-semibold text-rose-800">{historyError}</p>
                <button
                  type="button"
                  onClick={() => fetchHistory(pageIndex, statusFilter)}
                  className="press mt-4 inline-flex items-center gap-1.5 rounded-full border border-rose-300 bg-white px-4 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-100"
                >
                  <RefreshCw className="h-3.5 w-3.5" /> Coba lagi
                </button>
              </div>
            ) : history.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-black/10 bg-white px-6 py-14 text-center">
                <CreditCard className="mx-auto h-8 w-8 text-[#d9d9dd]" />
                <p className="mt-3 text-sm font-semibold text-[#17171c]">Belum ada transaksi</p>
                <p className="mt-1 text-xs text-[#75758a]">
                  Setelah melakukan pembayaran langganan, invoice akan muncul di sini.
                </p>
              </div>
            ) : (
              <div className="mt-5 overflow-x-auto">
                <table className="w-full min-w-[640px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-black/[0.08] text-left text-[11px] font-bold uppercase tracking-wider text-[#75758a]">
                      <th className="pb-3 pr-4">Paket</th>
                      <th className="pb-3 pr-4">Tanggal</th>
                      <th className="pb-3 pr-4">Metode</th>
                      <th className="pb-3 pr-4 text-right">Total</th>
                      <th className="pb-3 pr-4">Status</th>
                      <th className="pb-3 text-right">Invoice</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((record) => {
                      const meta = statusMeta[record.status] ?? statusMeta.PENDING;
                      return (
                        <tr key={record.id} className="border-b border-black/[0.05] last:border-0">
                          <td className="py-4 pr-4">
                            <p className="font-semibold text-[#17171c]">{record.planName}</p>
                            <p className="mt-0.5 font-mono text-[11px] text-[#75758a]">
                              {record.externalId.slice(0, 26)}...
                            </p>
                          </td>
                          <td className="py-4 pr-4 text-[#616161]">
                            {new Intl.DateTimeFormat('id-ID', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(record.createdAt))}
                          </td>
                          <td className="py-4 pr-4 text-[#616161]">
                            {record.status === 'PAID'
                              ? [record.paymentMethod, record.paymentChannel].filter(Boolean).join(' · ') || 'Xendit'
                              : '-'}
                          </td>
                          <td className="py-4 pr-4 text-right font-semibold text-[#17171c]">
                            Rp {record.amount.toLocaleString('id-ID')}
                          </td>
                          <td className="py-4 pr-4">
                            <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-bold ${meta.className}`}>
                              {meta.label}
                            </span>
                          </td>
                          <td className="py-4 text-right">
                            {record.invoiceUrl ? (
                              <a
                                href={record.invoiceUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-1 rounded-full border border-black/10 bg-white px-3 py-1.5 text-xs font-semibold text-[#1863dc] transition hover:bg-[#eeece7]"
                              >
                                Lihat <ExternalLink className="h-3 w-3" />
                              </a>
                            ) : (
                              <span className="text-xs text-[#75758a]">-</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>

                {historyPage && historyPage.totalPages > 1 && (
                  <div className="mt-5 flex items-center justify-between">
                    <p className="text-xs text-[#75758a]">
                      Halaman {historyPage.number + 1} dari {historyPage.totalPages}
                    </p>
                    <div className="flex gap-2">
                      <button
                        onClick={() => changePage(historyPage.number - 1)}
                        disabled={historyPage.first}
                        className="press ring-focus rounded-full border border-black/10 bg-white px-4 py-2 text-xs font-semibold text-[#17171c] transition hover:bg-[#eeece7] disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        Sebelumnya
                      </button>
                      <button
                        onClick={() => changePage(historyPage.number + 1)}
                        disabled={historyPage.last}
                        className="press ring-focus rounded-full border border-black/10 bg-white px-4 py-2 text-xs font-semibold text-[#17171c] transition hover:bg-[#eeece7] disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        Berikutnya
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </main>
      </div>

      {checkoutOpen && (
        <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-3 backdrop-blur-sm sm:p-4" onMouseDown={() => !loading && setCheckoutOpen(false)}>
          <div className="max-h-[calc(100dvh-1.5rem)] w-full max-w-3xl overflow-y-auto rounded-3xl bg-[#faf9f7] shadow-2xl" onMouseDown={(event) => event.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-black/[0.08] px-4 py-4 sm:px-8 sm:py-5">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#003c33]">Upgrade paket</p>
                <h2 className="mt-1 text-xl font-bold">Konfirmasi langganan Premium</h2>
              </div>
              <button type="button" onClick={() => setCheckoutOpen(false)} disabled={loading} aria-label="Tutup" className="rounded-full p-2 text-[#616161] transition hover:bg-black/5 disabled:opacity-50"><X className="h-5 w-5" /></button>
            </div>

            <form onSubmit={handleUpgrade} className="grid md:grid-cols-[1fr_300px]">
              <div className="space-y-5 p-6 sm:p-8">
                <div>
                  <h3 className="text-sm font-bold">Informasi pelanggan</h3>
                  <p className="mt-1 text-xs text-[#75758a]">Digunakan untuk identitas pada invoice Xendit.</p>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <CheckoutField label="Nama lengkap" value={checkoutForm.customerName} required onChange={(value) => setCheckoutForm({ ...checkoutForm, customerName: value })} />
                  <CheckoutField label="Email" type="email" value={checkoutForm.customerEmail} required onChange={(value) => setCheckoutForm({ ...checkoutForm, customerEmail: value })} />
                  <CheckoutField label="Nomor telepon" value={checkoutForm.customerPhone} placeholder="08xxxxxxxxxx" onChange={(value) => setCheckoutForm({ ...checkoutForm, customerPhone: value })} />
                  <CheckoutField label="Perusahaan" value={checkoutForm.companyName} placeholder="Opsional" onChange={(value) => setCheckoutForm({ ...checkoutForm, companyName: value })} />
                </div>
                <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-black/[0.08] bg-white p-4">
                  <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} className="mt-0.5 h-4 w-4 accent-[#003c33]" />
                  <span className="text-xs leading-5 text-[#616161]">Saya memahami paket berlaku selama 30 hari dan tidak diperpanjang otomatis. Pembayaran diproses secara aman oleh Xendit.</span>
                </label>
              </div>

              <aside className="bg-[#17171c] p-6 text-white sm:p-8">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#003c33] text-[#edfce9]"><Sparkles className="h-5 w-5" /></div>
                <h3 className="mt-5 text-lg font-bold">DevFlow Premium</h3>
                <p className="mt-1 text-xs text-slate-400">Akses semua fitur selama 30 hari.</p>
                <div className="mt-6 space-y-3 border-y border-white/10 py-5 text-xs text-slate-300">
                  <div className="flex items-center gap-2"><Check className="h-4 w-4 text-emerald-400" /> Workspace dan proyek tanpa batas</div>
                  <div className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-emerald-400" /> Pembayaran aman via Xendit</div>
                  <div className="flex items-center gap-2"><CreditCard className="h-4 w-4 text-emerald-400" /> Tidak diperpanjang otomatis</div>
                </div>
                <div className="mt-5 flex items-end justify-between">
                  <span className="text-sm text-slate-400">Total</span>
                  <div className="text-right"><strong className="text-2xl">Rp50.000</strong><p className="text-[11px] text-slate-500">untuk 30 hari</p></div>
                </div>
                <button type="submit" disabled={loading || !agreed} className="press ring-focus mt-6 inline-flex w-full items-center justify-center gap-2 rounded-full bg-white px-4 py-3.5 text-sm font-bold text-[#17171c] transition hover:bg-[#edfce9] disabled:cursor-not-allowed disabled:opacity-45">
                  {loading ? <><Loader2 className="h-4 w-4 animate-spin" /> Membuat invoice...</> : <>Lanjutkan Pembayaran <ArrowRight className="h-4 w-4" /></>}
                </button>
              </aside>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

const CheckoutField = ({ label, value, onChange, type = 'text', placeholder, required = false }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
}) => (
  <label>
    <span className="mb-2 block text-xs font-semibold text-[#3f3f46]">{label}{required && <span className="text-rose-600"> *</span>}</span>
    <input type={type} value={value} required={required} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} className="w-full rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm outline-none transition placeholder:text-[#75758a] focus:border-[#003c33] focus:ring-2 focus:ring-[#003c33]/10" />
  </label>
);
