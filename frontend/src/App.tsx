import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { WorkspaceProvider } from './context/WorkspaceContext';
import { AuthModalProvider } from './context/AuthModalContext';
import { NotificationProvider } from './context/NotificationContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthModal } from './components/AuthModal';

import { LandingPage } from './pages/LandingPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { OAuthCallbackPage } from './pages/OAuthCallbackPage';
import { AcceptInvitationPage } from './pages/AcceptInvitationPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { SelectWorkspacePage } from './pages/SelectWorkspacePage';
import { WorkspaceDashboardPage } from './pages/WorkspaceDashboardPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { ProjectDetailPage } from './pages/ProjectDetailPage';
import { ProjectSettingsPage } from './pages/ProjectSettingsPage';
import { MembersPage } from './pages/MembersPage';
import { BillingPage } from './pages/BillingPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { ProfilePage } from './pages/ProfilePage';
import { InboxPage } from './pages/InboxPage';
import { WorkspaceSettingsPage } from './pages/WorkspaceSettingsPage';

/**
 * Animasi masuk untuk setiap navigasi.
 *
 * <p>key={pathname} memaksa React membuang subtree lama dan memasang yang baru saat
 * rute berubah, jadi animasinya benar-benar jalan ulang tiap pindah halaman —
 * tanpa itu elemennya cuma di-update di tempat dan tidak ada yang bergerak.
 * Satu pembungkus di sini menggantikan menempelkan kelas ke lima belas halaman
 * satu per satu, dan halaman baru ikut dapat tanpa diingat-ingat.
 *
 * <p>Animasinya opacity saja. Transform apa pun di sini — termasuk translateY(0)
 * di akhir keyframe — menjadikan pembungkus ini containing block bagi setiap
 * keturunan `position: fixed`, sehingga bilah nav mobile dan seluruh modal halaman
 * diposisikan relatif ke sini, bukan ke viewport.
 *
 * <p>Gerakannya sendiri dimatikan oleh blok prefers-reduced-motion di index.css.
 */
function RouteTransition({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation();
  return (
    <div key={pathname} className="animate-page-in">
      {children}
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <WorkspaceProvider>
        <AuthModalProvider>
          <NotificationProvider>
            <BrowserRouter>
              <AuthModal />
              <RouteTransition>
              <Routes>
                {/* Public Routes */}
                <Route path="/" element={<LandingPage />} />
                <Route path="/verify-email" element={<VerifyEmailPage />} />
                <Route path="/oauth2/callback" element={<OAuthCallbackPage />} />
                <Route path="/accept-invitation" element={<AcceptInvitationPage />} />

                {/* Backward compatibility redirects for legacy /login and /register */}
                <Route path="/login" element={<Navigate to="/" replace />} />
                <Route path="/register" element={<Navigate to="/" replace />} />

                {/* Authenticated Workspace User Routes */}
                <Route element={<ProtectedRoute />}>
                  <Route path="/onboarding" element={<OnboardingPage />} />
                  <Route path="/select-workspace" element={<SelectWorkspacePage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/inbox" element={<InboxPage />} />

                  <Route path="/w/:slug/dashboard" element={<WorkspaceDashboardPage />} />
                  <Route path="/w/:slug/projects" element={<ProjectsPage />} />
                  <Route path="/w/:slug/projects/:projectId" element={<ProjectDetailPage />} />
                  <Route path="/w/:slug/projects/:projectId/settings" element={<ProjectSettingsPage />} />
                  <Route path="/w/:slug/members" element={<MembersPage />} />
                  <Route path="/w/:slug/billing" element={<BillingPage />} />
                  <Route path="/w/:slug/settings" element={<WorkspaceSettingsPage />} />

                  <Route path="/dashboard" element={<Navigate to="/select-workspace" replace />} />
                </Route>

                {/* Admin Routes (Requires SUPER_ADMIN) */}
                <Route element={<ProtectedRoute requireAdmin />}>
                  <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
                  <Route path="/admin/users" element={<AdminUsersPage />} />
                </Route>

                {/* Fallback */}
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
              </RouteTransition>
            </BrowserRouter>
          </NotificationProvider>
        </AuthModalProvider>
      </WorkspaceProvider>
    </AuthProvider>
  );
}

export default App;
