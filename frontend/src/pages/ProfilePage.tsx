import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Check, Loader2, MapPin, ShieldCheck, UserRound, Info,
  Monitor, Trash2, KeyRound,
} from 'lucide-react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { ImageUpload } from '../components/ImageUpload';

interface Session {
  id: string;
  userAgent?: string;
  ipAddress?: string;
  createdAt: string;
  lastUsedAt?: string;
  expiresAt: string;
  current: boolean;
}

const getErrorMessage = (error: unknown) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { error?: { message?: string }; message?: string } } }).response;
    return response?.data?.error?.message || response?.data?.message;
  }
  return undefined;
};

const TABS = [
  { id: 'profile', label: 'Data diri', icon: UserRound },
  { id: 'security', label: 'Keamanan', icon: ShieldCheck },
  { id: 'about', label: 'Tentang', icon: Info },
] as const;

type TabId = (typeof TABS)[number]['id'];

const formatDate = (value?: string) =>
  value
    ? new Intl.DateTimeFormat('id-ID', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '-';

export const ProfilePage: React.FC = () => {
  const { user, refetchUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeTab: TabId = TABS.some((tab) => tab.id === tabParam) ? (tabParam as TabId) : 'profile';

  const [form, setForm] = useState({
    fullName: user?.fullName || '',
    phoneNumber: user?.phoneNumber || '',
    jobTitle: user?.jobTitle || '',
    companyName: user?.companyName || '',
    city: user?.city || '',
    bio: user?.bio || '',
    avatarUrl: user?.avatarUrl || '',
  });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const updateField = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage('');
    setError('');
    try {
      await api.patch('/auth/me', form);
      await refetchUser();
      setMessage('Profil berhasil diperbarui.');
    } catch (err: unknown) {
      setError(getErrorMessage(err) || 'Profil gagal disimpan.');
    } finally {
      setSaving(false);
    }
  };

  const initials = (user?.fullName || '?')
    .split(' ')
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join('')
    .toUpperCase();

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 p-4 sm:p-8 pb-24 lg:pb-0">
          <div className="mx-auto max-w-5xl">
            <div className="flex items-center gap-4">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-[#17171c] text-lg font-bold text-[#edfce9]">
                {user?.avatarUrl
                  ? <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                  : initials}
              </div>
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-bold">{user?.fullName}</h1>
                <p className="truncate text-sm text-[#75758a]">{user?.email}</p>
              </div>
            </div>

            <div className="mt-6 flex items-center gap-1 overflow-x-auto rounded-2xl border border-[#d9d9dd] bg-white p-1.5 shadow-xs">
              {TABS.map((tab) => {
                const Icon = tab.icon;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setSearchParams(tab.id === 'profile' ? {} : { tab: tab.id })}
                    className={`flex items-center gap-1.5 rounded-xl px-3.5 py-2 text-xs font-semibold transition ${
                      activeTab === tab.id
                        ? 'bg-[#17171c] text-white shadow-sm'
                        : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {tab.label}
                  </button>
                );
              })}
            </div>

            <div className="mt-6">
              {activeTab === 'profile' && (
                <section className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-8">
                  <div className="flex items-start gap-3 border-b border-black/[0.07] pb-6">
                    <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><UserRound className="h-5 w-5" /></div>
                    <div>
                      <h2 className="text-xl font-bold">Data diri</h2>
                      <p className="mt-1 text-sm text-[#616161]">Lengkapi informasi yang digunakan pada akun dan kolaborasi tim.</p>
                    </div>
                  </div>

                  {message && <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"><Check className="h-4 w-4" />{message}</div>}
                  {error && <div className="mt-5 rounded-xl border border-rose-500/20 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}

                  <form onSubmit={handleSubmit} className="mt-6 grid gap-5 sm:grid-cols-2">
                    <Field label="Nama lengkap" name="fullName" value={form.fullName} onChange={updateField} required />
                    <Field label="Nomor telepon" name="phoneNumber" value={form.phoneNumber} onChange={updateField} placeholder="08xxxxxxxxxx" />
                    <Field label="Jabatan" name="jobTitle" value={form.jobTitle} onChange={updateField} placeholder="Product Designer" />
                    <Field label="Perusahaan" name="companyName" value={form.companyName} onChange={updateField} placeholder="Nama perusahaan" />
                    <Field label="Kota" name="city" value={form.city} onChange={updateField} placeholder="Jakarta" />
                    <div className="sm:col-span-2">
                      <ImageUpload
                        kind="AVATAR"
                        label="Foto profil"
                        value={form.avatarUrl || null}
                        onChange={(path) => setForm((f) => ({ ...f, avatarUrl: path ?? '' }))}
                      />
                    </div>
                    <label className="sm:col-span-2">
                      <span className="mb-2 block text-xs font-semibold text-[#3f3f46]">Tentang saya</span>
                      <textarea name="bio" value={form.bio} onChange={updateField} rows={4} maxLength={1000} placeholder="Ceritakan singkat peran dan keahlian Anda" className="w-full resize-y rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm outline-none transition focus:border-[#003c33] focus:ring-2 focus:ring-[#003c33]/10" />
                    </label>
                    <div className="sm:col-span-2 flex justify-end border-t border-black/[0.07] pt-5">
                      <button type="submit" disabled={saving} className="press ring-focus inline-flex min-w-36 items-center justify-center gap-2 rounded-full bg-[#17171c] px-5 py-3 text-sm font-semibold text-white transition hover:bg-black focus:outline-none focus:ring-2 focus:ring-[#003c33]/30 disabled:opacity-60">
                        {saving && <Loader2 className="h-4 w-4 animate-spin" />}{saving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Simpan profil'}
                      </button>
                    </div>
                  </form>
                </section>
              )}

              {activeTab === 'security' && <SecurityTab />}

              {activeTab === 'about' && (
                <section className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-8">
                  <div className="flex items-start gap-3 border-b border-black/[0.07] pb-6">
                    <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><Info className="h-5 w-5" /></div>
                    <div>
                      <h2 className="text-xl font-bold">Tentang saya</h2>
                      <p className="mt-1 text-sm text-[#616161]">Ringkasan akun Anda di DevFlow.</p>
                    </div>
                  </div>
                  <dl className="mt-6 grid gap-4 sm:grid-cols-2">
                    <Row label="Nama lengkap" value={user?.fullName} />
                    <Row label="Email" value={user?.email} />
                    <Row label="Jabatan" value={user?.jobTitle} />
                    <Row label="Perusahaan" value={user?.companyName} />
                    <Row
                      label="Kota"
                      value={user?.city}
                      icon={<MapPin className="h-3.5 w-3.5 text-[#75758a]" />}
                    />
                    <Row
                      label="Status email"
                      value={user?.emailVerified ? 'Terverifikasi' : 'Belum terverifikasi'}
                      icon={<ShieldCheck className={`h-3.5 w-3.5 ${user?.emailVerified ? 'text-emerald-600' : 'text-amber-600'}`} />}
                    />
                    <Row label="Metode masuk" value={user?.identities?.join(', ') || 'Email & password'} />
                    <Row label="Status akun" value={user?.status} />
                    <div className="sm:col-span-2">
                      <dt className="text-xs font-semibold text-[#3f3f46]">Bio</dt>
                      <dd className="mt-1.5 whitespace-pre-line rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm text-[#616161]">
                        {user?.bio || 'Belum ada bio.'}
                      </dd>
                    </div>
                  </dl>
                </section>
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

const Field = ({ label, name, value, onChange, placeholder, required = false, type = 'text' }: {
  label: string;
  name: string;
  value: string;
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  required?: boolean;
  type?: string;
}) => (
  <label>
    <span className="mb-2 block text-xs font-semibold text-[#3f3f46]">{label}{required && <span className="text-rose-600"> *</span>}</span>
    <input name={name} type={type} value={value} onChange={onChange} required={required} placeholder={placeholder} className="w-full rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm outline-none transition placeholder:text-[#75758a] focus:border-[#003c33] focus:ring-2 focus:ring-[#003c33]/10" />
  </label>
);

const Row = ({ label, value, icon }: { label: string; value?: string | null; icon?: React.ReactNode }) => (
  <div>
    <dt className="text-xs font-semibold text-[#3f3f46]">{label}</dt>
    <dd className="mt-1.5 flex items-center gap-1.5 rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm text-[#616161]">
      {icon}
      {value || 'Belum diisi'}
    </dd>
  </div>
);

const SecurityTab: React.FC = () => {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const [sessions, setSessions] = useState<Session[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState('');
  const [revoking, setRevoking] = useState<string | null>(null);
  const [forceLogout, setForceLogout] = useState(false);
  const [sessionsReload, setSessionsReload] = useState(0);

  useEffect(() => {
    void (async () => {
      try {
        setSessionsError('');
        const res = await api.get<Session[]>('/auth/sessions', {
          headers: { 'X-Refresh-Token': localStorage.getItem('refreshToken') || '' },
        });
        setSessions(res.data);
      } catch (err: unknown) {
        setSessionsError(getErrorMessage(err) || 'Gagal memuat daftar sesi.');
      } finally {
        setSessionsLoading(false);
      }
    })();
  }, [sessionsReload]);

  // React compiler melarang penulisan window.location langsung di handler,
  // jadi logout paksa setelah mencabut sesi sendiri ditunda ke effect ini.
  useEffect(() => {
    if (forceLogout) {
      window.location.href = '/';
    }
  }, [forceLogout]);

  const handleChangePassword = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage('');
    setError('');
    try {
      await api.post('/auth/change-password', {
        ...form,
        currentRefreshToken: localStorage.getItem('refreshToken') || '',
      });
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setMessage('Password berhasil diubah. Perangkat lain telah dikeluarkan.');
      setSessionsReload((n) => n + 1);
    } catch (err: unknown) {
      setError(getErrorMessage(err) || 'Password gagal diubah.');
    } finally {
      setSaving(false);
    }
  };

  const handleRevoke = async (session: Session) => {
    setRevoking(session.id);
    setSessionsError('');
    try {
      await api.delete(`/auth/sessions/${session.id}`);
      // Mencabut sesi sendiri berarti keluar dari perangkat ini: token yang
      // tersimpan sudah mati, jadi jangan biarkan UI seolah masih login.
      if (session.current) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        setForceLogout(true);
        return;
      }
      setSessions((current) => current.filter((item) => item.id !== session.id));
    } catch (err: unknown) {
      setSessionsError(getErrorMessage(err) || 'Sesi gagal dicabut.');
    } finally {
      setRevoking(null);
    }
  };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-8">
        <div className="flex items-start gap-3 border-b border-black/[0.07] pb-6">
          <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><KeyRound className="h-5 w-5" /></div>
          <div>
            <h2 className="text-xl font-bold">Ganti password</h2>
            <p className="mt-1 text-sm text-[#616161]">Minimal 8 karakter. Semua perangkat lain akan dikeluarkan setelah diganti.</p>
          </div>
        </div>

        {message && <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"><Check className="h-4 w-4" />{message}</div>}
        {error && <div className="mt-5 rounded-xl border border-rose-500/20 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}

        <form onSubmit={handleChangePassword} className="mt-6 grid gap-5 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Field
              label="Password saat ini" name="currentPassword" type="password" required
              value={form.currentPassword}
              onChange={(e) => setForm((c) => ({ ...c, currentPassword: e.target.value }))}
            />
          </div>
          <Field
            label="Password baru" name="newPassword" type="password" required
            value={form.newPassword}
            onChange={(e) => setForm((c) => ({ ...c, newPassword: e.target.value }))}
          />
          <Field
            label="Konfirmasi password baru" name="confirmPassword" type="password" required
            value={form.confirmPassword}
            onChange={(e) => setForm((c) => ({ ...c, confirmPassword: e.target.value }))}
          />
          <div className="sm:col-span-2 flex justify-end border-t border-black/[0.07] pt-5">
            <button type="submit" disabled={saving} className="press ring-focus inline-flex min-w-36 items-center justify-center gap-2 rounded-full bg-[#17171c] px-5 py-3 text-sm font-semibold text-white transition hover:bg-black disabled:opacity-60">
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}{saving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Ganti password'}
            </button>
          </div>
        </form>
      </section>

      <section className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-6 shadow-[0_2px_10px_rgba(0,0,0,0.02)] sm:p-8">
        <div className="flex items-start gap-3 border-b border-black/[0.07] pb-6">
          <div className="rounded-xl bg-[#edfce9] p-2.5 text-[#003c33]"><Monitor className="h-5 w-5" /></div>
          <div>
            <h2 className="text-xl font-bold">Perangkat aktif</h2>
            <p className="mt-1 text-sm text-[#616161]">Sesi yang sedang masuk ke akun Anda. Cabut yang tidak Anda kenali.</p>
          </div>
        </div>

        {sessionsError && <div className="mt-5 rounded-xl border border-rose-500/20 bg-rose-50 px-4 py-3 text-sm text-rose-700">{sessionsError}</div>}

        {sessionsLoading ? (
          <div className="mt-6 flex items-center gap-2 text-sm text-[#75758a]"><Loader2 className="h-4 w-4 animate-spin" /> Memuat sesi...</div>
        ) : sessions.length === 0 ? (
          <p className="mt-6 text-sm text-[#75758a]">Tidak ada sesi aktif.</p>
        ) : (
          <ul className="mt-6 space-y-3">
            {sessions.map((session) => (
              <li key={session.id} className="flex items-center justify-between gap-4 rounded-xl border border-[#d9d9dd] bg-white px-4 py-3.5">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-sm font-semibold text-[#17171c]">
                      {session.userAgent || 'Perangkat tidak dikenal'}
                    </span>
                    {session.current && (
                      <span className="shrink-0 rounded-full border border-[#003c33]/10 bg-[#edfce9] px-2 py-0.5 text-[10px] font-bold text-[#003c33]">
                        PERANGKAT INI
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-[#75758a]">
                    {session.ipAddress || 'IP tidak tercatat'} &middot; Terakhir dipakai {formatDate(session.lastUsedAt || session.createdAt)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleRevoke(session)}
                  disabled={revoking === session.id}
                  className="press ring-focus inline-flex shrink-0 items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-rose-600 transition hover:bg-rose-50 disabled:opacity-60"
                >
                  {revoking === session.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                  Cabut
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
};
