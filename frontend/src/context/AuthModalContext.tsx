import React, { createContext, useContext, useState } from 'react';

type ModalMode = 'login' | 'register';

interface AuthModalContextType {
  isOpen: boolean;
  mode: ModalMode;
  openAuthModal: (mode?: ModalMode) => void;
  closeAuthModal: () => void;
  switchMode: (mode: ModalMode) => void;
}

const AuthModalContext = createContext<AuthModalContextType | undefined>(undefined);

export const AuthModalProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [mode, setMode] = useState<ModalMode>('login');

  const openAuthModal = (initialMode: ModalMode = 'login') => {
    setMode(initialMode);
    setIsOpen(true);
  };

  const closeAuthModal = () => {
    setIsOpen(false);
  };

  const switchMode = (newMode: ModalMode) => {
    setMode(newMode);
  };

  return (
    <AuthModalContext.Provider value={{ isOpen, mode, openAuthModal, closeAuthModal, switchMode }}>
      {children}
    </AuthModalContext.Provider>
  );
};

export const useAuthModal = () => {
  const context = useContext(AuthModalContext);
  if (!context) {
    throw new Error('useAuthModal must be used within an AuthModalProvider');
  }
  return context;
};
