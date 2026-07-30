import React, { useState } from 'react';
import { createUrl } from '../api/urls';
import type { CreateUrlPayload, URLItem } from '../api/urls';

interface CreateURLBarProps {
  onCreated: (url: URLItem) => void;
}

const EXPIRY_OPTIONS = [
  { label: 'No expiry', value: undefined },
  { label: '1 day', value: 1 },
  { label: '7 days', value: 7 },
  { label: '30 days', value: 30 },
  { label: '90 days', value: 90 },
  { label: '365 days', value: 365 },
];

const CreateURLBar: React.FC<CreateURLBarProps> = ({ onCreated }) => {
  const [url, setUrl] = useState('');
  const [expiresInDays, setExpiresInDays] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmed = url.trim();
    if (!trimmed) return;

    // Basic client-side URL check
    try {
      new window.URL(trimmed);
    } catch {
      setError('Please enter a valid URL (e.g. https://example.com)');
      return;
    }

    setLoading(true);
    try {
      const payload: CreateUrlPayload = { originalUrl: trimmed };
      if (expiresInDays !== undefined) {
        payload.expiresInDays = expiresInDays;
      }
      const created = await createUrl(payload);
      onCreated(created);
      setUrl('');
    } catch (err: any) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Failed to shorten URL.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="create-url-bar">
      <form onSubmit={handleSubmit} className="create-url-form">
        <input
          type="text"
          value={url}
          onChange={e => setUrl(e.target.value)}
          placeholder="Paste your long URL here..."
          className="form-input create-url-input"
          disabled={loading}
        />
        <select
          value={expiresInDays ?? ''}
          onChange={e => setExpiresInDays(e.target.value ? Number(e.target.value) : undefined)}
          className="form-input create-url-select"
          disabled={loading}
        >
          {EXPIRY_OPTIONS.map(opt => (
            <option key={opt.label} value={opt.value ?? ''}>
              {opt.label}
            </option>
          ))}
        </select>
        <button type="submit" className="btn-primary create-url-btn" disabled={loading}>
          {loading ? 'Shortening...' : 'Shorten'}
        </button>
      </form>
      {error && <p className="create-url-error">{error}</p>}
    </div>
  );
};

export default CreateURLBar;
