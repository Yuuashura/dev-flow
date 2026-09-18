import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useWorkspace } from '../context/WorkspaceContext';

export const useResolveWorkspace = () => {
  const { slug } = useParams<{ slug: string }>();
  const { workspaces, currentWorkspace, setCurrentWorkspace } = useWorkspace();

  useEffect(() => {
    if (!slug || workspaces.length === 0) return;

    const targetWorkspace = workspaces.find((ws) => ws.slug === slug);
    if (targetWorkspace && targetWorkspace.id !== currentWorkspace?.id) {
      setCurrentWorkspace(targetWorkspace);
    }
  }, [slug, workspaces, currentWorkspace, setCurrentWorkspace]);
};
