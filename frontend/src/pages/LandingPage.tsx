import React from 'react';
import { useAuthModal } from '../context/AuthModalContext';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { Sparkles, ArrowRight, Zap, GitBranch, CreditCard } from 'lucide-react';

export const LandingPage: React.FC = () => {
  const { openAuthModal } = useAuthModal();
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-[#eeece7] text-[#17171c]">
      {/* Announcement Bar */}
      <div className="flex min-h-9 h-auto items-center justify-center gap-2 bg-[#000000] px-3 py-2 text-center text-[11px] font-mono font-medium leading-snug text-white sm:px-4 sm:text-[12px]">
        <Sparkles className="h-3.5 w-3.5 shrink-0 text-[#ff7759]" />
        <span className="min-w-0 [overflow-wrap:anywhere]">
          DevFlow SaaS 2026 Release<span className="hidden sm:inline"> — </span>
          <span className="block sm:inline">Platform Manajemen Proyek Multi-Tenant &amp; Client Portal</span>
        </span>
      </div>

      {/* Hero Section */}
      <section className="relative overflow-hidden px-4 pt-12 pb-16 sm:px-6 sm:pt-20 sm:pb-28">
        <div className="mx-auto max-w-5xl text-center">
          <div className="animate-fade-in-up inline-flex max-w-full flex-wrap items-center justify-center gap-2 rounded-full border border-[#d9d9dd] bg-white px-3 py-2 text-center text-[11px] font-mono font-semibold leading-snug text-[#003c33] shadow-xs logo-pulse sm:gap-3 sm:px-4 sm:text-xs">
            <img src="/DevFlowLogo.webp" alt="DevFlow Logo" className="logo-float h-5 w-5 shrink-0 object-contain" />
            <span className="break-words">Cohere-Inspired Editorial Enterprise Interface</span>
          </div>

          <h1 className="animate-fade-in-up delay-2 mt-7 text-4xl font-normal leading-[1.05] tracking-tight text-[#17171c] sm:mt-8 sm:text-6xl md:text-7xl font-mono">
            Enterprise Client Portal <br className="hidden sm:block" />
            <span className="break-words italic font-sans font-bold text-[#003c33]">
              &amp; Multi-Tenant Workspaces
            </span>
          </h1>

          <p className="animate-fade-in-up delay-3 mx-auto mt-5 max-w-2xl text-sm leading-relaxed text-[#616161] sm:mt-6 sm:text-base">
            Menghubungkan Owner, Developer, dan Client dalam satu ekosistem real-time. Terintegrasi dengan Google OAuth, GitHub Webhooks, Milestone Approval, dan Multi-Tenant Billing.
          </p>

          <div className="animate-fade-in-up delay-4 mt-8 flex flex-col items-stretch gap-3 sm:mt-10 sm:flex-row sm:items-center sm:justify-center sm:gap-4">
            {user ? (
              <Link
                to="/select-workspace"
                className="press ring-focus inline-flex w-full items-center justify-center gap-2.5 rounded-full bg-[#17171c] px-5 py-3.5 text-sm font-semibold text-white shadow-md transition hover:bg-[#000000] hover:scale-105 sm:w-auto sm:px-7"
              >
                Buka Workspace Saya <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
              </Link>
            ) : (
              <>
                <button
                  onClick={() => openAuthModal('register')}
                   className="press ring-focus hover-lift inline-flex w-full items-center justify-center gap-2.5 rounded-full bg-[#17171c] px-5 py-3.5 text-sm font-semibold text-white shadow-md transition hover:bg-[#000000] cursor-pointer sm:w-auto sm:px-7"
                >
                  Mulai Sekarang Gratis <ArrowRight className="h-4 w-4" />
                </button>
                <button
                  onClick={() => openAuthModal('login')}
                   className="press ring-focus rounded-full border border-[#d9d9dd] bg-white px-5 py-3.5 text-sm font-semibold text-[#17171c] shadow-xs transition hover:bg-slate-50 hover:scale-105 cursor-pointer sm:px-7"
                >
                  Masuk Akun
                </button>
              </>
            )}
          </div>
        </div>
      </section>

      {/* Deep Green Product Feature Section */}
      <section className="bg-[#003c33] py-16 text-white sm:py-24">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="text-center animate-fade-in-up">
            <span className="font-mono text-xs uppercase tracking-widest text-[#edfce9]">Arsitektur Production-Grade</span>
            <h2 className="mt-3 text-3xl font-mono font-normal sm:text-4xl text-white">Fitur Utama DevFlow</h2>
            <p className="mt-4 text-sm text-[#edfce9]/80 max-w-xl mx-auto">Dirancang presisi untuk software house, agency, dan freelance developer.</p>
          </div>

          <div className="mt-10 grid gap-5 sm:mt-16 sm:grid-cols-2 sm:gap-8 lg:grid-cols-3">
            <div className="animate-fade-in-up delay-1 hover-lift rounded-2xl border border-white/10 bg-[#071829]/60 p-5 backdrop-blur-sm hover:border-[#02ffcc]/30 sm:p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#edfce9] text-[#003c33]">
                <Zap className="h-5 w-5" />
              </div>
              <h3 className="mt-5 text-lg font-mono font-normal leading-snug text-white sm:mt-6 sm:text-xl">Multi-Tenant Workspaces</h3>
              <p className="mt-2 text-xs text-[#edfce9]/70 leading-relaxed">Isolasi data antar workspace dengan skema role-based access control (Owner, Developer, Client).</p>
            </div>

            <div className="animate-fade-in-up delay-2 hover-lift rounded-2xl border border-white/10 bg-[#071829]/60 p-5 backdrop-blur-sm hover:border-[#02ffcc]/30 sm:p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#edfce9] text-[#003c33]">
                <GitBranch className="h-5 w-5" />
              </div>
              <h3 className="mt-5 text-lg font-mono font-normal leading-snug text-white sm:mt-6 sm:text-xl">Integrasi GitHub</h3>
              <p className="mt-2 text-xs text-[#edfce9]/70 leading-relaxed">Webhook sinkronisasi otomatis commit, PR, dan event repositori langsung ke board proyek.</p>
            </div>

            <div className="animate-fade-in-up delay-3 hover-lift rounded-2xl border border-white/10 bg-[#071829]/60 p-5 backdrop-blur-sm hover:border-[#02ffcc]/30 sm:p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#edfce9] text-[#003c33]">
                <CreditCard className="h-5 w-5" />
              </div>
              <h3 className="mt-5 text-lg font-mono font-normal leading-snug text-white sm:mt-6 sm:text-xl">Billing &amp; Subscriptions</h3>
              <p className="mt-2 text-xs text-[#edfce9]/70 leading-relaxed">Manajemen langganan Free &amp; Pro terhubung langsung dengan Payment Gateway (Xendit).</p>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-[#d9d9dd] bg-white py-12 text-center text-xs text-[#75758a]">
        <div className="mb-3 flex justify-center">
          <Logo size="sm" textClassName="text-[#17171c]" />
        </div>
        <p className="px-4 leading-relaxed">&copy; 2026 DevFlow SaaS Platform. All rights reserved.</p>
      </footer>
    </div>
  );
};
