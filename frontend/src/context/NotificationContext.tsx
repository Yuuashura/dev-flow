import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

interface NotificationContextValue {
  unreadCount: number;
  refreshUnreadCount: () => void;
  decrementUnread: (by?: number) => void;
  resetUnread: () => void;
}

const NotificationContext = createContext<NotificationContextValue>({
  unreadCount: 0,
  refreshUnreadCount: () => {},
  decrementUnread: () => {},
  resetUnread: () => {},
});

export const useNotifications = () => useContext(NotificationContext);

const POLL_INTERVAL_MS = 30_000;

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchCount = useCallback(async () => {
    if (!user) return;
    try {
      const res = await api.get<{ count: number }>('/notifications/unread-count');
      setUnreadCount(res.data.count ?? 0);
    } catch {
      // keep existing count on transient failure
    }
  }, [user]);

  useEffect(() => {
    if (!user) {
      setUnreadCount(0);
      return;
    }
    fetchCount();
    timerRef.current = setInterval(fetchCount, POLL_INTERVAL_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [user, fetchCount]);

  const refreshUnreadCount = useCallback(() => { fetchCount(); }, [fetchCount]);
  const decrementUnread = useCallback((by = 1) => setUnreadCount(c => Math.max(0, c - by)), []);
  const resetUnread = useCallback(() => setUnreadCount(0), []);

  return (
    <NotificationContext.Provider value={{ unreadCount, refreshUnreadCount, decrementUnread, resetUnread }}>
      {children}
    </NotificationContext.Provider>
  );
};
