import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { SkeletonTableRow } from '../components/Skeleton';
import { api } from '../services/api';
import { getErrMsg } from '../services/apiError';
import type { User, UserStatus } from '../types';
import { Shield, AlertTriangle, Loader2 } from 'lucide-react';

export const AdminUsersPage: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Suspend mengunci seseorang keluar dari platform. Dulu jalan dari satu klik,
  // tanpa konfirmasi dan tanpa mengunci tombol selama request terbang — klik ganda
  // yang tidak sengaja mengirim dua permintaan.
  const [confirmAction, setConfirmAction] = useState<{ user: User; next: UserStatus } | null>(null);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const fetchUsers = async () => {
    try {
      const res = await api.get('/admin/users?page=0&size=50');
      setUsers(res.data.content || []);
      setError(null);
    } catch (err: unknown) {
      setError(getErrMsg(err) || 'Gagal memuat daftar user.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleUpdateStatus = async (userId: string, newStatus: UserStatus) => {
    setUpdatingId(userId);
    try {
      await api.patch(`/admin/users/${userId}/status?status=${newStatus}`);
      setError(null);
      setConfirmAction(null);
      await fetchUsers();
    } catch (err: unknown) {
      // Tanpa ini kegagalan tidak terlihat sama sekali: toggle hanya diam tidak berubah.
      setError(getErrMsg(err) || 'Gagal mengubah status user.');
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="mx-auto max-w-7xl px-8 py-10">
        <div className="flex items-center justify-between border-b border-black/[0.07] pb-6">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-[#003c33] px-3 py-1 text-xs font-bold text-[#eeece7]">
              <Shield className="h-4 w-4" /> ADMIN USER MANAGEMENT
            </div>
            <h1 className="mt-3 font-mono text-3xl font-bold tracking-tight text-[#17171c]">Daftar Semua Pengguna Platform</h1>
          </div>
        </div>

        {error && (
          <div role="alert" className="mt-6 rounded-xl border border-rose-500/20 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </div>
        )}

        <div className="mt-8 overflow-hidden rounded-2xl border border-black/[0.07] bg-[#faf9f7] shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
          {/* Pembungkus scroll: sebelumnya hanya overflow-hidden, jadi kolom yang
              tidak muat di layar sempit terpotong dan tidak bisa dijangkau sama sekali. */}
          <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-[#eeece7]/80 text-xs uppercase text-[#75758a] border-b border-black/[0.07]">
              <tr>
                <th className="px-6 py-4">Nama Lengkap</th>
                <th className="px-6 py-4">Email</th>
                <th className="px-6 py-4">Role Global</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Aksi Status</th>
              </tr>
            </thead>
<tbody className="divide-y divide-black/[0.06]">
              {loading
                ? Array.from({ length: 5 }).map((_, i) => <SkeletonTableRow key={i} columns={5} />)
                : users.map((u) => (
                  <tr key={u.id} className="hover:bg-white">
                    <td className="px-6 py-4 font-semibold text-[#17171c]">{u.fullName}</td>
                    <td className="px-6 py-4 text-[#75758a]">{u.email}</td>
                    <td className="px-6 py-4">
                      {u.globalRole ? (
                        <span className="rounded-full bg-[#003c33] px-2 py-0.5 text-xs font-bold text-[#eeece7]">
                          {u.globalRole}
                        </span>
                      ) : (
                        <span className="text-xs text-[#75758a]">USER</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${
                          u.status === 'ACTIVE'
                            ? 'bg-[#edfce9] text-[#003c33] border border-[#003c33]/10'
                            : 'bg-rose-50 text-rose-600 border border-rose-200'
                        }`}
                      >
                        {u.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      {u.status === 'ACTIVE' ? (
                        <button
                          disabled={updatingId === u.id}
                          onClick={() => setConfirmAction({ user: u, next: 'SUSPENDED' })}
                          className="press rounded-lg border border-rose-300 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-600 hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {updatingId === u.id ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Suspend'}
                        </button>
                      ) : (
                        <button
                          disabled={updatingId === u.id}
                          onClick={() => handleUpdateStatus(u.id, 'ACTIVE')}
                          className="press rounded-lg border border-[#003c33]/30 bg-[#edfce9] px-3 py-1 text-xs font-semibold text-[#003c33] hover:bg-[#d9f5d1] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {updatingId === u.id ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Aktifkan'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
          </div>
        </div>
      </div>

      {confirmAction && (
        <div
          className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-[#17171c]/55 p-4 backdrop-blur-xs"
          onClick={() => updatingId === null && setConfirmAction(null)}
        >
          <div
            role="alertdialog"
            aria-modal="true"
            className="animate-modal-in w-full max-w-md rounded-2xl border border-black/10 bg-white p-4 sm:p-6 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start gap-3">
              <div className="rounded-xl bg-rose-50 p-2.5 text-rose-600"><AlertTriangle className="h-5 w-5" /></div>
              <div className="min-w-0">
                <h3 className="text-base font-bold text-[#17171c]">Suspend pengguna ini?</h3>
                <p className="mt-1 break-words text-sm text-[#75758a]">
                  <strong className="text-[#17171c]">{confirmAction.user.email}</strong> akan langsung kehilangan
                  akses ke seluruh workspace-nya. Anda bisa mengaktifkannya kembali kapan saja.
                </p>
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                disabled={updatingId !== null}
                onClick={() => setConfirmAction(null)}
                className="press rounded-full border border-[#d9d9dd] bg-white px-4 py-2.5 text-sm font-semibold text-[#3f3f46] hover:bg-[#eeece7] disabled:opacity-50"
              >
                Batal
              </button>
              <button
                type="button"
                disabled={updatingId !== null}
                onClick={() => handleUpdateStatus(confirmAction.user.id, confirmAction.next)}
                className="press rounded-full bg-rose-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {updatingId !== null ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Memproses...</span> : 'Ya, suspend'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
