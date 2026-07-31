import React from 'react';
import { Link } from 'react-router-dom';
import { deleteUrl } from '../api/urls';
import type { URLItem } from '../api/urls';

interface URLTableProps {
  urls: URLItem[];
  onDelete: (shortCode: string) => void;
  onCopy: (text: string) => void;
  onQR: (shortCode: string) => void;
}

const formatDate = (iso: string): string => {
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

const truncateUrl = (url: string, max: number = 50): string => {
  if (url.length <= max) return url;
  return url.slice(0, max) + '…';
};

const URLTable: React.FC<URLTableProps> = ({ urls, onDelete, onCopy, onQR }) => {

  const handleDelete = async (shortCode: string) => {
    if (!window.confirm('Delete this short URL? This cannot be undone.')) return;
    try {
      await deleteUrl(shortCode);
      onDelete(shortCode);
    } catch (err) {
      console.error('Failed to delete URL', err);
    }
  };

  return (
    <div className="url-table-wrapper">
      <table className="url-table">
        <thead>
          <tr>
            <th>Original URL</th>
            <th>Short URL</th>
            <th>Clicks</th>
            <th>Created</th>
            <th>Expires</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {urls.map(url => (
            <tr key={url.id}>
              <td title={url.originalUrl} className="url-cell">
                {truncateUrl(url.originalUrl)}
              </td>
              <td className="short-url-cell">
                <Link to={`/stats/${url.shortCode}`}><code>{url.shortCode}</code></Link>
              </td>
              <td className="clicks-cell">{url.clickCount}</td>
              <td className="date-cell">{formatDate(url.createdAt)}</td>
              <td className="date-cell">
                {url.expiresAt ? formatDate(url.expiresAt) : '—'}
              </td>
              <td className="actions-cell">
                <button
                  className="btn-icon"
                  onClick={() => onCopy(url.shortUrl)}
                  title="Copy short URL"
                >
                  📋
                </button>
                <button
                  className="btn-icon"
                  onClick={() => onQR(url.shortCode)}
                  title="Show QR code"
                >
                  ⬜
                </button>
                <button
                  className="btn-icon btn-icon-danger"
                  onClick={() => handleDelete(url.shortCode)}
                  title="Delete"
                >
                  🗑
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default URLTable;
