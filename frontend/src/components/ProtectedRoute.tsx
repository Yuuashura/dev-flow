import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  requireAdmin?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ requireAdmin = false }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-screen w-screen flex-col items-center justify-center gap-4 bg-[#eeece7] text-[#17171c]">
        <img
          src="/DevFlowLogo.webp"
          alt="DevFlow Logo"
          className="h-12 w-12 object-contain logo-float"
        />
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#003c33] border-t-transparent"></div>
        <p className="text-xs font-mono text-[#75758a]">Memuat DevFlow...</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/" replace />;
  }

  if (requireAdmin && user.globalRole !== 'SUPER_ADMIN') {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
};
