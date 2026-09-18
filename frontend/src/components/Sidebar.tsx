import React, { useEffect, useRef, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useWorkspace } from '../context/WorkspaceContext';
import { useAuth } from '../context/AuthContext';
import type { Project } from '../types';
import { api } from '../services/api';
import { CreateProjectModal } from './CreateProjectModal';
import {
  LayoutDashboard, FolderKanban, Users, ChevronUp, ChevronLeft, ChevronRight, Plus,
  UserRound, CreditCard, ShieldCheck, Info, LogOut, Settings,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { currentWorkspace } = useWorkspace();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const slug = currentWorkspace?.slug || 'default';
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Sidebar yang bisa diciutkan. Preferensi disimpan agar tidak kembali lebar
  // setiap kali halaman dimuat ulang.
  const [collapsed, setCollapsed] = useState(() =>
    localStorage.getItem('devflow.sidebarCollapsed') === 'true'
  );
  const [projects, setProjects] = useState<Project[]>([]);
  const [showAddModal, setShowAddModal] = useState(false);

  useEffect(() => {
    localStorage.setItem('devflow.sidebarCollapsed', String(collapsed));
  }, [collapsed]);

  useEffect(() => {
    if (!currentWorkspace) return;
    api
      .get<Project[]>(`/projects?workspaceId=${currentWorkspace.id}`)
      .then((res) => setProjects(res.data))
      .catch(() => setProjects([]));
  }, [currentWorkspace]);

  const isClient = currentWorkspace?.userRole === 'CLIENT';

  // Popover harus tertutup saat klik di luar atau tekan Escape, jika tidak ia
  // menggantung di atas konten dan menghalangi klik berikutnya.
  useEffect(() => {
    if (!menuOpen) return;
    const onPointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  const navItems = [
    { label: 'Dashboard', path: `/w/${slug}/dashboard`, icon: LayoutDashboard },
    { label: 'Proyek', path: `/w/${slug}/projects`, icon: FolderKanban },
    { label: 'Anggota Tim', path: `/w/${slug}/members`, icon: Users },
    ...(currentWorkspace?.userRole === 'OWNER'
      ? [{ label: 'Pengaturan Workspace', path: `/w/${slug}/settings`, icon: Settings }]
      : []),
  ];

  // Billing tetap di halaman workspace yang sudah ada (493 baris, berfungsi) —
  // hanya pintu masuknya yang dipindah ke menu profil.
  const profileMenu = [
    { label: 'Profil Saya', to: '/profile', icon: UserRound },
    { label: 'Billing & Paket', to: `/w/${slug}/billing`, icon: CreditCard },
    { label: 'Keamanan', to: '/profile?tab=security', icon: ShieldCheck },
    { label: 'Tentang', to: '/profile?tab=about', icon: Info },
  ];

  const initials = (user?.fullName || '?')
    .split(' ')
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join('')
    .toUpperCase();

  const handleLogout = async () => {
    setMenuOpen(false);
    await logout();
    navigate('/');
  };

  // Di bawah lg, <aside> di bawah ini disembunyikan sepenuhnya sementara Navbar
  // hanya menawarkan Inbox dan Profil — jadi Dashboard, Proyek, Anggota Tim dan
  // Pengaturan tidak bisa dijangkau sama sekali dari ponsel. Bilah bawah ini
  // memakai definisi navItems yang sama persis, jadi keduanya tidak bisa berbeda.
  const mobileNav = (
    <nav
      aria-label="Navigasi utama"
      className="fixed inset-x-0 bottom-0 z-40 flex border-t border-[#d9d9dd] bg-[#faf9f7]/95 backdrop-blur lg:hidden"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `tap ring-focus flex min-w-0 flex-1 flex-col items-center justify-center gap-1 px-1 py-2 text-[10px] font-semibold transition-colors ${
                isActive ? 'text-[#003c33]' : 'text-[#75758a] hover:text-[#17171c]'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Icon className={`h-5 w-5 shrink-0 ${isActive ? 'text-[#003c33]' : ''}`} />
                <span className="w-full truncate text-center leading-tight">{item.label}</span>
              </>
            )}
          </NavLink>
        );
      })}
      <NavLink
        to={`/w/${slug}/billing`}
        className={({ isActive }) =>
          `tap ring-focus flex min-w-0 flex-1 flex-col items-center justify-center gap-1 px-1 py-2 text-[10px] font-semibold transition-colors ${
            isActive ? 'text-[#003c33]' : 'text-[#75758a] hover:text-[#17171c]'
          }`
        }
      >
        <CreditCard className="h-5 w-5 shrink-0" />
        <span className="w-full truncate text-center leading-tight">Billing</span>
      </NavLink>
    </nav>
  );

  return (
    <>
    {mobileNav}
    <aside
      className={`sticky top-16 hidden h-[calc(100vh-4rem)] shrink-0 self-start flex-col justify-between overflow-y-auto border-r border-[#d9d9dd] bg-[#eeece7]/40 p-4 transition-[width] duration-200 lg:flex ${
        collapsed ? 'w-[72px]' : 'w-64'
      }`}
    >
      <div>
        <div className={`flex items-center ${collapsed ? 'justify-center' : 'justify-between px-3'}`}>
          {!collapsed && (
            <h2 className="text-[10px] font-mono font-bold uppercase tracking-wider text-[#75758a]">Navigation</h2>
          )}
          <button
            type="button"
            onClick={() => setCollapsed((value) => !value)}
            title={collapsed ? 'Perlebar sidebar' : 'Perkecil sidebar'}
            aria-label={collapsed ? 'Perlebar sidebar' : 'Perkecil sidebar'}
            className="press tap ring-focus rounded-lg p-1.5 text-[#75758a] hover:bg-white hover:text-[#17171c]"
          >
            {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </button>
        </div>

        <nav className="mt-2 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                title={collapsed ? item.label : undefined}
                className={({ isActive }) =>
                  `flex items-center rounded-full text-xs font-semibold transition ${
                    collapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-4 py-2.5'
                  } ${
                    isActive
                      ? 'bg-[#17171c] text-white shadow-sm'
                      : 'text-[#616161] hover:bg-white hover:text-[#17171c]'
                  }`
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                {!collapsed && <span className="truncate">{item.label}</span>}
              </NavLink>
            );
          })}
        </nav>

        <div className="mt-6">
          <div className={`flex items-center ${collapsed ? 'justify-center' : 'justify-between px-3'}`}>
            {!collapsed && (
              <h2 className="text-[10px] font-mono font-bold uppercase tracking-wider text-[#75758a]">Proyek</h2>
            )}
            {!isClient && (
              <button
                type="button"
                onClick={() => setShowAddModal(true)}
                title="Proyek baru"
                aria-label="Proyek baru"
                className="press tap ring-focus rounded-lg p-1.5 text-[#75758a] hover:bg-white hover:text-[#17171c]"
              >
                <Plus className="h-4 w-4" />
              </button>
            )}
          </div>

          <nav className="mt-2 space-y-1">
            {projects.length === 0 && !collapsed && (
              <p className="px-4 py-2 text-[11px] text-[#75758a]">Belum ada proyek.</p>
            )}
            {projects.map((project) => (
              <NavLink
                key={project.id}
                to={`/w/${slug}/projects/${project.id}`}
                title={collapsed ? project.name : undefined}
                className={({ isActive }) =>
                  `flex items-center rounded-full text-xs font-semibold transition ${
                    collapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-4 py-2.5'
                  } ${
                    isActive
                      ? 'bg-[#17171c] text-white shadow-sm'
                      : 'text-[#616161] hover:bg-white hover:text-[#17171c]'
                  }`
                }
              >
                <FolderKanban className="h-4 w-4 shrink-0" />
                {!collapsed && <span className="truncate">{project.name}</span>}
              </NavLink>
            ))}
          </nav>
        </div>
      </div>

      <div className="space-y-3">
        {!collapsed && (
          <div className="rounded-2xl border border-[#d9d9dd] bg-white p-3.5 shadow-xs">
            <p className="text-[10px] font-mono uppercase text-[#75758a]">Workspace Status</p>
            <div className="mt-1 flex items-center justify-between gap-2">
              <span className="truncate text-xs font-bold text-[#17171c]">{currentWorkspace?.name || 'Workspace'}</span>
              <span className="inline-flex shrink-0 items-center rounded-full border border-[#003c33]/10 bg-[#edfce9] px-2 py-0.5 text-[10px] font-bold text-[#003c33]">
                {currentWorkspace?.status || 'ACTIVE'}
              </span>
            </div>
          </div>
        )}

        <div ref={menuRef} className="relative border-t border-[#d9d9dd] pt-3">
          {menuOpen && (
            <div
              role="menu"
              className="animate-modal-in absolute bottom-full right-0 left-0 mb-2 origin-bottom overflow-hidden rounded-2xl border border-[#d9d9dd] bg-white p-1.5 shadow-lg"
            >
              {profileMenu.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    role="menuitem"
                    onClick={() => setMenuOpen(false)}
                    className="ring-focus flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-xs font-semibold text-[#616161] transition hover:bg-[#eeece7] hover:text-[#17171c]"
                  >
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </NavLink>
                );
              })}
              <div className="my-1.5 border-t border-[#d9d9dd]" />
              <button
                type="button"
                role="menuitem"
                onClick={handleLogout}
                className="press ring-focus flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-xs font-semibold text-rose-600 hover:bg-rose-50"
              >
                <LogOut className="h-4 w-4" />
                Keluar
              </button>
            </div>
          )}

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            title={collapsed ? user?.fullName || 'Pengguna' : undefined}
            className={`flex w-full items-center rounded-2xl border p-2.5 text-left transition ${
              collapsed ? 'justify-center' : 'gap-2.5'
            } ${menuOpen ? 'border-[#17171c]/15 bg-white shadow-xs' : 'border-transparent hover:bg-white'}`}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-[#17171c] text-[11px] font-bold text-[#edfce9]">
              {user?.avatarUrl
                ? <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                : initials}
            </span>
            {!collapsed && (
              <>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs font-bold text-[#17171c]">{user?.fullName || 'Pengguna'}</span>
                  <span className="block truncate text-[10px] text-[#75758a]">{user?.email}</span>
                </span>
                <ChevronUp className={`h-4 w-4 shrink-0 text-[#75758a] transition ${menuOpen ? '' : 'rotate-180'}`} />
              </>
            )}
          </button>
        </div>
      </div>

      {showAddModal && (
        <CreateProjectModal
          workspaceId={currentWorkspace?.id}
          onClose={() => setShowAddModal(false)}
          onCreated={(project) => {
            setProjects((previous) => [...previous, project]);
            setShowAddModal(false);
            navigate(`/w/${slug}/projects/${project.id}`);
          }}
        />
      )}
    </aside>
    </>
  );
};
