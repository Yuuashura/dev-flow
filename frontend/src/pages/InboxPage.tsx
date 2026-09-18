import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Bell, Mail, CreditCard, Users, CheckCheck, Trash2,
  Loader2, AlertCircle, Inbox, ChevronLeft, ArrowLeft, ChevronRight,
  CheckCircle2, XCircle, ArrowRight, RefreshCw, AlertTriangle } from 'lucide-react';
import { Navbar } from '../components/Navbar';
import { api } from '../services/api';
import { apiError } from '../services/apiError';
import { useWorkspace } from '../context/WorkspaceContext';
import { useNotifications } from '../context/NotificationContext';
import type { InboxNotification, NotificationPage } from '../types';

type Tab = 'ALL' | 'UNREAD' | 'INVITATION' | 'BILLING' | 'SYSTEM';

const TAB_CONFIG: { id: Tab; label: string; icon: React.ReactNode }[] = [
  { id: 'ALL', label: 'Semua', icon: <Inbox className="h-3.5 w-3.5" /> },
  { id: 'UNREAD', label: 'Belum Dibaca', icon: <Bell className="h-3.5 w-3.5" /> },
  { id: 'INVITATION', label: 'Undangan', icon: <Users className="h-3.5 w-3.5" /> },
  { id: 'BILLING', label: 'Pembayaran', icon: <CreditCard className="h-3.5 w-3.5" /> },
  { id: 'SYSTEM', label: 'Sistem', icon: <Mail className="h-3.5 w-3.5" /> },
];

const TYPE_COLORS: Record<string, string> = {
  INVITATION: 'bg-violet-100 text-violet-700',
  BILLING: 'bg-emerald-100 text-emerald-700',
  SYSTEM: 'bg-blue-100 text-blue-700',
  COLLABORATION: 'bg-amber-100 text-amber-700',
};

const TYPE_ICONS: Record<string, React.ReactNode> = {
  INVITATION: <Users className="h-4 w-4" />,
  BILLING: <CreditCard className="h-4 w-4" />,
  SYSTEM: <Bell className="h-4 w-4" />,
  COLLABORATION: <Mail className="h-4 w-4" />,
};

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Baru saja';
  if (mins < 60) return `${mins} menit lalu`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} jam lalu`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} hari lalu`;
  return new Date(dateStr).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' });
}

export const InboxPage: React.FC = () => {
  const navigate = useNavigate();
  const { currentWorkspace, refreshWorkspaces } = useWorkspace();
  const { refreshUnreadCount } = useNotifications();

  const [activeTab, setActiveTab] = useState<Tab>('ALL');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [accepting, setAccepting] = useState<string | null>(null);
  // Satu banner dipakai untuk sukses dan gagal, tapi dulu selalu dirender emerald
  // dengan ikon centang — undangan yang ditolak server terbaca sebagai konfirmasi.
  const [actionMsg, setActionMsg] = useState<{ text: string; tone: 'success' | 'error' } | null>(null);

  const flash = (text: string, tone: 'success' | 'error' = 'success') => {
    setActionMsg({ text, tone });
    setTimeout(() => setActionMsg(null), tone === 'error' ? 6000 : 3000);
  };

  const fetchNotifications = useCallback(async (tab: Tab, p: number) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, string> = { page: String(p), size: '15' };
      if (tab === 'UNREAD') params.status = 'UNREAD';
      else if (tab !== 'ALL') params.type = tab;
      const res = await api.get<NotificationPage>('/notifications', { params });
      setData(res.data);
    } catch {
      setError('Gagal memuat notifikasi. Coba lagi.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNotifications(activeTab, page);
  }, [activeTab, page, fetchNotifications]);

  const handleTabChange = (tab: Tab) => {
    setActiveTab(tab);
    setPage(0);
  };

  const markRead = async (id: string) => {
    try {
      await api.patch(`/notifications/${id}/read`);
      setData(prev => prev ? {
        ...prev,
        content: prev.content.map(n => n.id === id ? { ...n, status: 'READ' } : n)
      } : prev);
      refreshUnreadCount();
    } catch (err: unknown) {
      flash(apiError(err, 'Gagal menandai notifikasi dibaca.'), 'error');
    }
  };

  const archiveOne = async (id: string) => {
    try {
      await api.delete(`/notifications/${id}`);
      setData(prev => prev ? {
        ...prev,
        content: prev.content.filter(n => n.id !== id),
        totalElements: prev.totalElements - 1,
      } : prev);
    } catch (err: unknown) {
      flash(apiError(err, 'Gagal mengarsipkan notifikasi.'), 'error');
    }
  };

  const markAllRead = async () => {
    try {
      await api.patch('/notifications/read-all');
      setData(prev => prev ? {
        ...prev,
        content: prev.content.map(n => ({ ...n, status: 'READ' as const }))
      } : prev);
      refreshUnreadCount();
      flash('Semua notifikasi ditandai dibaca.');
    } catch (err: unknown) {
      flash(apiError(err, 'Gagal menandai semua notifikasi dibaca.'), 'error');
    }
  };

  const archiveAllRead = async () => {
    try {
      await api.delete('/notifications/read');
      fetchNotifications(activeTab, 0);
      setPage(0);
      flash('Notifikasi yang sudah dibaca diarsipkan.');
    } catch (err: unknown) {
      flash(apiError(err, 'Gagal mengarsipkan notifikasi yang sudah dibaca.'), 'error');
    }
  };

  const handleAcceptInvitation = async (notification: InboxNotification) => {
    const token = notification.metadata?.invitationToken;
    if (!token) return;
    setAccepting(notification.id);
    try {
      await api.post(`/invitations/${token}/accept`);
      await markRead(notification.id);
      refreshUnreadCount();

      // Tanpa ini, daftar workspace masih yang diambil saat login — jadi pesan
      // "Workspace ditambahkan" muncul lalu /select-workspace menampilkan daftar
      // lama. Untuk orang yang belum punya workspace sama sekali, daftar lama itu
      // kosong dan SelectWorkspacePage melemparnya ke /onboarding: diberi tahu
      // berhasil, lalu diminta membuat workspace sendiri.
      //
      // AcceptInvitationPage sudah memanggilnya sejak awal; jalur Inbox tidak.
      await refreshWorkspaces();

      flash('Undangan diterima! Workspace ditambahkan.');
      setTimeout(() => navigate('/select-workspace'), 1500);
    } catch (err: unknown) {
      flash(apiError(err, 'Gagal menerima undangan.'), 'error');
    } finally {
      setAccepting(null);
    }
  };

  const notifications = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="min-h-screen bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">

        <nav className="mb-5 flex items-center gap-1.5 text-xs text-slate-400">
          {currentWorkspace ? (
            <Link
              to={`/w/${currentWorkspace.slug}/dashboard`}
              className="press ring-focus inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 font-medium text-[#003c33] shadow-xs transition hover:bg-[#edfce9]"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Kembali ke {currentWorkspace.name}
            </Link>
          ) : (
            <Link
              to="/select-workspace"
              className="press ring-focus inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 font-medium text-[#003c33] shadow-xs transition hover:bg-[#edfce9]"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Kembali ke Daftar Workspace
            </Link>
          )}
          <span className="mx-1 text-slate-300">/</span>
          <span className="font-medium text-slate-500">Inbox</span>
        </nav>

        <div className="mb-6 flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Inbox</h1>
            <p className="mt-1 text-sm text-[#616161]">Undangan, notifikasi pembayaran, dan pesan sistem.</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchNotifications(activeTab, page)}
              className="press ring-focus flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 shadow-xs hover:bg-slate-50 transition"
              title="Perbarui"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={markAllRead}
              className="press ring-focus flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 shadow-xs hover:bg-slate-50 transition"
            >
              <CheckCheck className="h-3.5 w-3.5" />
              Baca Semua
            </button>
            <button
              onClick={archiveAllRead}
              className="press ring-focus flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 shadow-xs hover:bg-slate-50 transition"
            >
              <Trash2 className="h-3.5 w-3.5" />
              Arsipkan
            </button>
          </div>
        </div>

        {actionMsg && (
          <div
            role="status"
            aria-live="polite"
            className={`animate-toast-in mb-4 flex items-start gap-2 rounded-xl border px-4 py-3 text-sm font-medium ${
              actionMsg.tone === 'error'
                ? 'border-rose-500/20 bg-rose-50 text-rose-700'
                : 'border-emerald-500/20 bg-emerald-50 text-emerald-700'
            }`}
          >
            {actionMsg.tone === 'error'
              ? <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />}
            {actionMsg.text}
          </div>
        )}

        <div className="mb-4 flex items-center gap-1 overflow-x-auto rounded-2xl border border-[#d9d9dd] bg-white p-1.5 shadow-xs">
          {TAB_CONFIG.map(tab => (
            <button
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`flex shrink-0 items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-semibold transition ${
                activeTab === tab.id
                  ? 'bg-[#17171c] text-white shadow-sm'
                  : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>

        <div className="rounded-2xl border border-[#d9d9dd] bg-white shadow-xs overflow-hidden">
          {loading ? (
            <div className="stagger p-4 space-y-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-start gap-3.5 px-2 py-3">
                  <div className="skeleton skeleton-circle h-8 w-8 shrink-0" />
                  <div className="flex-1 space-y-2">
                    <div className="skeleton skeleton-text" style={{ width: '70%' }} />
                    <div className="skeleton skeleton-text" style={{ width: '40%', opacity: 0.7 }} />
                  </div>
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center py-20 gap-3">
              <AlertCircle className="h-10 w-10 text-rose-400" />
              <p className="text-sm text-[#75758a]">{error}</p>
              <button
                onClick={() => fetchNotifications(activeTab, page)}
                className="press ring-focus rounded-full bg-[#17171c] px-4 py-2 text-xs font-semibold text-white hover:bg-black transition"
              >
                Coba Lagi
              </button>
            </div>
          ) : notifications.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 gap-3">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
                <Inbox className="h-7 w-7" />
              </div>
              <p className="text-base font-semibold text-[#3f3f46]">Inbox kosong</p>
              <p className="text-sm text-[#75758a]">
                {activeTab === 'UNREAD' ? 'Tidak ada notifikasi yang belum dibaca.' : 'Tidak ada notifikasi di sini.'}
              </p>
            </div>
          ) : (
            <ul className="divide-y divide-[#f0f0f0]">
              {notifications.map(notification => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  accepting={accepting === notification.id}
                  onRead={markRead}
                  onArchive={archiveOne}
                  onAccept={handleAcceptInvitation}
                />
              ))}
            </ul>
          )}

          {!loading && !error && totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-[#f0f0f0] px-4 py-3">
              <span className="text-xs text-slate-400">
                Halaman {page + 1} dari {totalPages} · {data?.totalElements ?? 0} notifikasi
              </span>
              <div className="flex items-center gap-1">
                <button
                  disabled={page === 0}
                  onClick={() => setPage(p => p - 1)}
                  className="flex h-7 w-7 items-center justify-center rounded-lg border border-[#d9d9dd] bg-white text-slate-500 transition hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </button>
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                  className="flex h-7 w-7 items-center justify-center rounded-lg border border-[#d9d9dd] bg-white text-slate-500 transition hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

const NotificationItem: React.FC<{
  notification: InboxNotification;
  accepting: boolean;
  onRead: (id: string) => void;
  onArchive: (id: string) => void;
  onAccept: (n: InboxNotification) => void;
}> = ({ notification, accepting, onRead, onArchive, onAccept }) => {
  const isUnread = notification.status === 'UNREAD';
  const isInvitation = notification.type === 'INVITATION';
  const hasToken = !!notification.metadata?.invitationToken;

  return (
    <li
      className={`group flex gap-3.5 px-4 py-4 transition ${isUnread ? 'bg-[#f8fffe]' : 'bg-white'} hover:bg-slate-50`}
      onClick={() => { if (isUnread) onRead(notification.id); }}
    >
      <div className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-white ${
        notification.type === 'INVITATION' ? 'bg-violet-500' :
        notification.type === 'BILLING' ? 'bg-emerald-500' :
        notification.type === 'SYSTEM' ? 'bg-blue-500' : 'bg-amber-500'
      }`}>
        {TYPE_ICONS[notification.type] ?? <Bell className="h-4 w-4" />}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 flex-wrap">
            {isUnread && (
              <span className="h-2 w-2 shrink-0 rounded-full bg-[#02ffcc]" />
            )}
            <span className={`text-xs font-semibold ${isUnread ? 'text-[#17171c]' : 'text-slate-600'}`}>
              {notification.title}
            </span>
            <span className={`rounded-md px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
              TYPE_COLORS[notification.type] ?? 'bg-slate-100 text-slate-500'
            }`}>
              {notification.type}
            </span>
            {notification.priority === 'HIGH' && (
              <span className="rounded-md bg-rose-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-rose-600">
                Penting
              </span>
            )}
          </div>
          <span className="shrink-0 text-[11px] text-slate-400">{timeAgo(notification.createdAt)}</span>
        </div>

        {notification.content && (
          <p className="mt-1 text-xs leading-relaxed text-slate-500 line-clamp-2">{notification.content}</p>
        )}

        {isInvitation && hasToken && (
          <div className="mt-3 flex items-center gap-2">
            <button
              onClick={e => { e.stopPropagation(); onAccept(notification); }}
              disabled={accepting}
              className="press ring-focus flex items-center gap-1.5 rounded-full bg-[#003c33] px-3.5 py-1.5 text-[11px] font-bold text-white transition hover:bg-[#004d42] disabled:opacity-60 cursor-pointer"
            >
              {accepting ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <CheckCircle2 className="h-3.5 w-3.5" />
              )}
              {accepting ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Terima Undangan'}
            </button>
            <button
              onClick={e => { e.stopPropagation(); onArchive(notification.id); }}
              className="press ring-focus flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-[11px] font-medium text-slate-500 transition hover:bg-slate-50 cursor-pointer"
            >
              <XCircle className="h-3.5 w-3.5" />
              Tolak
            </button>
          </div>
        )}

        {!isInvitation && notification.actionUrl && (
          <a
            href={notification.actionUrl}
            onClick={e => { e.stopPropagation(); onRead(notification.id); }}
            className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-[#003c33] hover:underline"
          >
            Lihat Detail <ArrowRight className="h-3 w-3" />
          </a>
        )}
      </div>

      <button
        title="Arsipkan"
        onClick={e => { e.stopPropagation(); onArchive(notification.id); }}
        className="mt-0.5 shrink-0 rounded-lg p-1.5 text-slate-300 opacity-0 transition hover:bg-slate-100 hover:text-slate-500 group-hover:opacity-100"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </li>
  );
};
