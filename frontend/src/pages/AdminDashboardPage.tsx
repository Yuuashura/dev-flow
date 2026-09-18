import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { Skeleton } from '../components/Skeleton';
import { api } from '../services/api';
import { Shield, Users, Building2, Server } from 'lucide-react';
import { Link } from 'react-router-dom';

export const AdminDashboardPage: React.FC = () => {
  const [userCount, setUserCount] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAdminStats = async () => {
      try {
        const res = await api.get('/admin/users?page=0&size=1');
        setUserCount(res.data.totalElements || 0);
      } catch (err) {
        console.error('Failed to fetch admin stats:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchAdminStats();
  }, []);

  return (
    <div className="min-h-screen bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-8 sm:py-10">
        <div className="flex items-start justify-between border-b border-black/[0.07] pb-6">
          <div className="min-w-0">
            <div className="inline-flex items-center gap-2 rounded-full bg-[#003c33] px-3 py-1 text-xs font-bold text-[#eeece7]">
              <Shield className="h-4 w-4" /> SUPER ADMIN PORTAL
            </div>
            <h1 className="mt-3 break-words font-mono text-2xl font-bold tracking-tight text-[#17171c] sm:text-3xl">Platform System Health & Monitoring</h1>
            <p className="text-xs text-[#75758a] mt-1">Ringkasan status platform DevFlow SaaS secara global.</p>
          </div>
        </div>

        {/* Stats */}
        <div className="mt-8 grid gap-6 sm:grid-cols-3">
          <Link
            to="/admin/users"
            className="lift press ring-focus block rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 shadow-[0_2px_10px_rgba(0,0,0,0.02)] hover:border-[#17171c]/30 sm:p-6"
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#75758a] uppercase">Total Pengguna Registered</span>
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#003c33]/10 text-[#003c33]">
                <Users className="h-5 w-5" />
              </div>
            </div>
            {loading ? (
              <Skeleton className="mt-4 h-10 w-24" />
            ) : (
              <p className="mt-4 font-mono text-3xl font-bold text-[#17171c] sm:text-4xl">{userCount}</p>
            )}
            <p className="mt-2 text-xs text-[#1863dc] hover:underline">Kelola semua user →</p>
          </Link>

          <div className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#75758a] uppercase">Status System Services</span>
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#edfce9] text-[#003c33]">
                <Server className="h-5 w-5" />
              </div>
            </div>
            {/* Angka status sebelumnya di-hardcode ("7/7 Services Online") dan ditampilkan
                seolah data live. Belum ada endpoint health aggregation, jadi ditulis apa adanya. */}
            <p className="mt-4 text-xl font-bold text-[#75758a]">Belum tersedia</p>
            <p className="mt-2 text-xs text-[#75758a]">Butuh endpoint agregasi health per service.</p>
          </div>

          <div className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4 sm:p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#75758a] uppercase">Database Status</span>
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#eeece7] text-[#17171c]">
                <Building2 className="h-5 w-5" />
              </div>
            </div>
            {/* Jumlah tabel sebelumnya di-hardcode "18 Tables Active". Nama database juga
                tidak perlu diekspos ke UI. */}
            <p className="mt-4 text-xl font-bold text-[#17171c]">PostgreSQL</p>
            <p className="mt-2 text-xs text-[#75758a]">Detail skema tidak diekspos di UI.</p>
          </div>
        </div>
      </div>
    </div>
  );
};
