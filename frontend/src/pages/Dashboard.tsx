import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getMyUrls } from '../api/urls';
import type { URLItem } from '../api/urls';
import CreateURLBar from '../components/CreateURLBar';
import URLTable from '../components/URLTable';
import QRModal from '../components/QRModal';
import Toast from '../components/Toast';

const Dashboard: React.FC = () => {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();

  const [urls, setUrls] = useState<URLItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [qrCode, setQrCode] = useState<string | null>(null); // shortCode for QR modal

  // Auth guard
  useEffect(() => {
    if (!authLoading && !user) {
      navigate('/login');
    }
  }, [user, authLoading, navigate]);

  // Fetch URLs
  useEffect(() => {
    if (!user) return;
    const fetchUrls = async () => {
      try {
        const data = await getMyUrls();
        setUrls(data);
      } catch {
        setError('Failed to load your URLs.');
      } finally {
        setLoading(false);
      }
    };
    fetchUrls();
  }, [user]);

  const handleCreated = (newUrl: URLItem) => {
    setUrls(prev => [newUrl, ...prev]);
    setToast('URL shortened!');
  };

  const handleDelete = (shortCode: string) => {
    setUrls(prev => prev.filter(u => u.shortCode !== shortCode));
    setToast('URL deleted.');
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setToast('Copied to clipboard!');
    } catch {
      setToast('Failed to copy.');
    }
  };

  if (authLoading || (!user && !authLoading)) {
    return null; // Wait for auth check
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h1>Your Links</h1>
        <p>Create, manage, and track your shortened URLs.</p>
      </div>

      <CreateURLBar onCreated={handleCreated} />

      {loading && (
        <div className="dashboard-loading">
          <div className="skeleton-row" />
          <div className="skeleton-row" />
          <div className="skeleton-row" />
        </div>
      )}

      {error && <p className="dashboard-error">{error}</p>}

      {!loading && !error && urls.length === 0 && (
        <div className="dashboard-empty">
          <p className="empty-icon">🔗</p>
          <h2>No links yet</h2>
          <p>Paste a URL above to create your first short link.</p>
        </div>
      )}

      {!loading && urls.length > 0 && (
        <URLTable
          urls={urls}
          onDelete={handleDelete}
          onCopy={handleCopy}
          onQR={setQrCode}
        />
      )}

      {qrCode && (
        <QRModal shortCode={qrCode} onClose={() => setQrCode(null)} />
      )}

      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </div>
  );
};

export default Dashboard;
