import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useWorkspace } from '../context/WorkspaceContext';
import { useAuthModal } from '../context/AuthModalContext';
import { useNotifications } from '../context/NotificationContext';
import { Logo } from './Logo';
import { LogOut, Shield, ChevronDown, Plus, LayoutGrid, Bell, UserRound } from 'lucide-react';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const { workspaces, currentWorkspace, setCurrentWorkspace } = useWorkspace();
  const { openAuthModal } = useAuthModal();
  const { unreadCount } = useNotifications();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [wsDropdownOpen, setWsDropdownOpen] = useState(false);
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  return (
    <header className="sticky top-0 z-40 flex h-16 w-full items-center justify-between gap-2 border-b border-[#d9d9dd] bg-[#17171c] px-3 text-white shadow-sm sm:px-6">
      <div className="flex min-w-0 flex-1 items-center gap-2 sm:gap-6">
        <Link to="/" className="ring-focus group flex shrink-0 items-center gap-2.5">
          {/* Wordmark disembunyikan di bawah sm: 76px-nya yang membuat switcher
              workspace bertabrakan dengan lonceng di 320px. Marka gambarnya tetap. */}
          <Logo size="sm" textClassName="hidden text-white sm:inline" />
        </Link>

        {user && (
          <div className="relative min-w-0 flex-1 sm:flex-none">
            <button
              onClick={() => setWsDropdownOpen(!wsDropdownOpen)}
              className="press tap ring-focus flex w-full min-w-0 max-w-[150px] items-center gap-1.5 rounded-full border border-slate-700 bg-slate-800/80 px-2.5 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 sm:w-auto sm:max-w-none sm:gap-2 sm:px-3.5"
            >
              <LayoutGrid className="h-3.5 w-3.5 text-emerald-400" />
              <span className="truncate">{currentWorkspace ? currentWorkspace.name : 'Pilih Workspace'}</span>
              <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
            </button>

            {wsDropdownOpen && (
              <div className="animate-modal-in absolute left-0 z-50 mt-2 w-[min(16rem,calc(100vw-1.5rem))] origin-top-left rounded-2xl border border-[#d9d9dd] bg-white p-2 text-slate-900 shadow-xl">
                <div className="px-3 py-2 text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400">
                  Workspaces
                </div>
                {workspaces.map((ws) => (
                  <button
                    key={ws.id}
                    onClick={() => {
                      setCurrentWorkspace(ws);
                      setWsDropdownOpen(false);
                      navigate(`/w/${ws.slug}/dashboard`);
                    }}
                    className={`flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-xs font-medium transition ${
                      currentWorkspace?.id === ws.id
                        ? 'bg-[#edfce9] text-[#003c33] font-bold'
                        : 'text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    <span>{ws.name}</span>
                    <span className="text-[10px] font-mono text-slate-400 uppercase">{ws.userRole}</span>
                  </button>
                ))}
                <div className="my-1 border-t border-slate-100"></div>
                <Link
                  to="/onboarding"
                  onClick={() => setWsDropdownOpen(false)}
                  className="ring-focus flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-[#1863dc] transition hover:bg-slate-50"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Buat Workspace Baru</span>
                </Link>
              </div>
            )}
          </div>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-1.5 sm:gap-3">
        {user ? (
          <>
            <Link
              to="/inbox"
              className="press tap ring-focus relative flex h-9 w-9 items-center justify-center rounded-full border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white"
              title="Inbox"
            >
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-[#02ffcc] text-[9px] font-bold text-[#17171c]">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </Link>

            <div className="relative">
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                className="press tap ring-focus flex items-center gap-2.5 rounded-full border border-slate-700 bg-slate-800 p-1 pr-3 text-xs font-medium text-slate-200 hover:bg-slate-700"
              >
                <div className="flex h-7 w-7 items-center justify-center rounded-full bg-[#003c33] text-[11px] font-bold text-[#edfce9]">
                  {user.fullName.charAt(0).toUpperCase()}
                </div>
                <span className="hidden max-w-28 truncate sm:inline">{user.fullName}</span>
              </button>

              {dropdownOpen && (
                <div className="animate-modal-in absolute right-0 z-50 mt-2 w-56 origin-top-right rounded-2xl border border-[#d9d9dd] bg-white p-2 text-slate-900 shadow-xl">
                  <div className="border-b border-slate-100 px-3 py-2.5">
                    <p className="text-xs font-bold text-slate-900">{user.fullName}</p>
                    <p className="text-[11px] text-slate-500 truncate">{user.email}</p>
                    {user.globalRole === 'SUPER_ADMIN' && (
                      <span className="mt-1.5 inline-flex items-center gap-1 rounded-md bg-[#003c33] px-2 py-0.5 text-[10px] font-bold text-[#edfce9]">
                        <Shield className="h-3 w-3" /> SUPER ADMIN
                      </span>
                    )}
                  </div>

                  {user.globalRole === 'SUPER_ADMIN' && (
                    <Link
                      to="/admin/dashboard"
                      onClick={() => setDropdownOpen(false)}
                      className="ring-focus mt-1 flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-[#003c33] hover:bg-[#edfce9]"
                    >
                      <Shield className="h-4 w-4" />
                      <span>Admin Portal</span>
                    </Link>
                  )}

                  <Link
                    to="/inbox"
                    onClick={() => setDropdownOpen(false)}
                    className="ring-focus mt-1 flex items-center justify-between gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                  >
                    <div className="flex items-center gap-2">
                      <Bell className="h-4 w-4" />
                      <span>Inbox</span>
                    </div>
                    {unreadCount > 0 && (
                      <span className="flex h-4 w-4 items-center justify-center rounded-full bg-[#02ffcc] text-[9px] font-bold text-[#17171c]">
                        {unreadCount > 9 ? '9+' : unreadCount}
                      </span>
                    )}
                  </Link>

                  <Link
                    to="/profile"
                    onClick={() => setDropdownOpen(false)}
                    className="ring-focus mt-1 flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
                  >
                    <UserRound className="h-4 w-4" />
                    <span>Profil Saya</span>
                  </Link>

                  <button
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold text-[#ff7759] transition hover:bg-red-50 mt-1"
                  >
                    <LogOut className="h-4 w-4" />
                    <span>Keluar</span>
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex items-center gap-3">
            <button
              onClick={() => openAuthModal('login')}
              className="text-xs font-semibold text-slate-300 hover:text-white transition cursor-pointer"
            >
              Masuk
            </button>
            <button
              onClick={() => openAuthModal('register')}
              className="press ring-focus rounded-full bg-white px-4 py-2 text-xs font-bold text-[#17171c] shadow-sm transition hover:bg-slate-100 cursor-pointer"
            >
              Daftar Gratis
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
