import React from 'react';

interface LogoProps {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  showText?: boolean;
  animated?: boolean;
  className?: string;
  textClassName?: string;
}

export const Logo: React.FC<LogoProps> = ({
  size = 'md',
  showText = true,
  animated = true,
  className = '',
  textClassName = '',
}) => {
  const sizeMap = {
    sm: 'h-6 w-6',
    md: 'h-8 w-8',
    lg: 'h-10 w-10',
    xl: 'h-16 w-16',
  };

  const textSizeMap = {
    sm: 'text-base',
    md: 'text-lg',
    lg: 'text-xl',
    xl: 'text-3xl',
  };

  return (
    <div className={`flex items-center gap-2.5 font-mono font-bold tracking-tight text-white select-none ${className}`}>
      <div className={`relative flex items-center justify-center ${animated ? 'group' : ''}`}>
        <img
          src="/DevFlowLogo.webp"
          alt="DevFlow Logo"
          className={`${sizeMap[size]} object-contain drop-shadow-sm transition-all duration-300 ${
            animated ? 'group-hover:scale-110 group-hover:rotate-3 animate-float' : ''
          }`}
        />
      </div>
      {showText && (
        <span className={`font-mono font-bold ${textSizeMap[size]} ${textClassName}`}>
          Dev<span className="text-[#02ffcc] transition-colors duration-300">Flow</span>
        </span>
      )}
    </div>
  );
};
