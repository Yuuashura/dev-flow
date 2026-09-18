import React from 'react';

interface SkeletonProps {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = '' }) => (
  <div className={`skeleton ${className}`} />
);

export const SkeletonText: React.FC<{ lines?: number; className?: string }> = ({ lines = 1, className = '' }) => (
  <div className={`space-y-2 ${className}`}>
    {Array.from({ length: lines }).map((_, i) => (
      <div
        key={i}
        className="skeleton skeleton-text"
        style={{ width: i === lines - 1 && lines > 1 ? '70%' : '100%' }}
      />
    ))}
  </div>
);

export const SkeletonCard: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`rounded-2xl border border-black/[0.07] bg-white p-6 shadow-xs ${className}`}>
    <div className="flex items-center justify-between">
      <Skeleton className="h-6 w-24 rounded-full" />
      <Skeleton className="h-5 w-5 rounded-md" />
    </div>
    <Skeleton className="mt-4 h-6 w-3/4" />
    <Skeleton className="mt-2 h-4 w-full" />
    <Skeleton className="mt-1 h-4 w-2/3" />
    <div className="mt-6 border-t border-black/[0.06] pt-4">
      <div className="flex justify-between mb-1">
        <Skeleton className="h-3 w-16" />
        <Skeleton className="h-3 w-8" />
      </div>
      <Skeleton className="h-2 w-full rounded-full" />
    </div>
  </div>
);

export const SkeletonList: React.FC<{ count?: number; className?: string }> = ({ count = 3, className = '' }) => (
  <div className={`space-y-3 ${className}`}>
    {Array.from({ length: count }).map((_, i) => (
      <div key={i} className="flex items-center justify-between rounded-2xl border border-[#d9d9dd] bg-white p-4 shadow-xs">
        <div className="space-y-2 flex-1">
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-3 w-64" />
        </div>
        <div className="flex items-center gap-4">
          <Skeleton className="h-6 w-16 rounded-md" />
          <Skeleton className="h-2 w-24 rounded-full" />
        </div>
      </div>
    ))}
  </div>
);

export const SkeletonTaskBoard: React.FC = () => (
  <div className="grid gap-6 md:grid-cols-4">
    {['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'].map((col) => (
      <div key={col} className="rounded-2xl border border-black/[0.07] bg-[#faf9f7] p-4">
        <div className="flex items-center justify-between border-b border-black/[0.06] pb-3">
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-5 w-6 rounded-full" />
        </div>
        <div className="mt-4 space-y-3">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-black/[0.06] bg-white p-4 shadow-sm space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-full" />
              <div className="mt-3 pt-3 border-t border-black/[0.06] flex justify-between">
                <Skeleton className="h-3 w-12" />
                <Skeleton className="h-3 w-10" />
              </div>
            </div>
          ))}
        </div>
      </div>
    ))}
  </div>
);

export const SkeletonTableRow: React.FC<{ columns?: number }> = ({ columns = 5 }) => (
  <tr className="border-b border-[#f0f0f0]">
    {Array.from({ length: columns }).map((_, i) => (
      <td key={i} className="px-4 py-3.5">
        <Skeleton className="h-4 w-full" />
      </td>
    ))}
  </tr>
);
