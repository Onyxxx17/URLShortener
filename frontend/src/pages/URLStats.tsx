import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getURLStats } from '../api/urls';
import type { StatsResponse } from '../api/urls';
import ClickChart from '../components/ClickChart';

const formatDate = (iso: string): string => {
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' });
};

// Parse user-agent string into a simple browser name
const parseBrowser = (ua: string | null): string => {
  if (!ua) return 'Unknown';
  if (ua.includes('Firefox')) return 'Firefox';
  if (ua.includes('Edg/')) return 'Edge';
  if (ua.includes('Chrome') && !ua.includes('Edg/')) return 'Chrome';
  if (ua.includes('Safari') && !ua.includes('Chrome')) return 'Safari';
  if (ua.includes('Opera') || ua.includes('OPR/')) return 'Opera';
  return 'Other';
};

const URLStats: React.FC = () => {
  const { shortCode } = useParams<{ shortCode: string }>();
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [needsLogin, setNeedsLogin] = useState(false);

  useEffect(() => {
    if (!shortCode) return;
    const fetchStats = async () => {
      try {
        const data = await getURLStats(shortCode);
        setStats(data);
      } catch (err: any) {
      
        if (err.response?.status === 401) {
          setNeedsLogin(true);
          setError('Please log in to view these stats.');
        } else {
          setError(err.response?.data?.message || 'Failed to load stats for this URL.');
        }
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, [shortCode]);

  if (loading) {
    return (
      <div className="stats-page">
        <div className="stats-loading">
          <div className="skeleton-row" style={{ height: 80 }} />
          <div className="skeleton-row" style={{ height: 200 }} />
          <div className="skeleton-row" style={{ height: 120 }} />
        </div>
      </div>
    );
  }

  if (error || !stats) {
    return (
      <div className="stats-page">
        <div className="stats-error-container">
          <p className="dashboard-error">{error || 'URL not found.'}</p>
          {needsLogin ? (
            <Link to="/login" className="btn-secondary">Log in</Link>
          ) : (
            <Link to="/dashboard" className="btn-secondary">← Back to Dashboard</Link>
          )}
        </div>
      </div>
    );
  }

  // Compute top referers
  const refererCounts = new Map<string, number>();
  const browserCounts = new Map<string, number>();
  for (const click of stats.recentClicks) {
    const ref = click.referer || 'Direct';
    refererCounts.set(ref, (refererCounts.get(ref) || 0) + 1);
    const browser = parseBrowser(click.userAgent);
    browserCounts.set(browser, (browserCounts.get(browser) || 0) + 1);
  }

  const topReferers = [...refererCounts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5);

  const topBrowsers = [...browserCounts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5);

  return (
    <div className="stats-page fade-in">
      <Link to="/dashboard" className="stats-back-link">← Back to Dashboard</Link>

      {/* Header */}
      <div className="stats-header card">
        <div className="stats-header-left">
          <h1><code>{stats.shortCode}</code></h1>
          <p className="stats-original-url" title={stats.originalUrl}>
            {stats.originalUrl.length > 80 ? stats.originalUrl.slice(0, 80) + '…' : stats.originalUrl}
          </p>
          <div className="stats-meta">
            <span>Created {formatDate(stats.createdAt)}</span>
            {stats.expiresAt && (
              <span className={stats.isExpired ? 'stats-expired' : ''}>
                {stats.isExpired ? 'Expired' : `Expires ${formatDate(stats.expiresAt)}`}
              </span>
            )}
            {stats.lastAccessedAt && (
              <span>Last clicked {formatDate(stats.lastAccessedAt)}</span>
            )}
          </div>
        </div>
        <div className="stats-header-right">
          <div className="stats-big-number">
            <span className="big-number">{stats.clickCount.toLocaleString()}</span>
            <span className="big-label">total clicks</span>
          </div>
        </div>
      </div>

      {/* Click Chart */}
      <ClickChart clicks={stats.recentClicks} />

      {/* Breakdown Cards */}
      <div className="stats-breakdown">
        <div className="breakdown-card card">
          <h3>Top Referers</h3>
          {topReferers.length === 0 ? (
            <p className="breakdown-empty">No referer data yet.</p>
          ) : (
            <ul className="breakdown-list">
              {topReferers.map(([ref, count]) => (
                <li key={ref}>
                  <span className="breakdown-label" title={ref}>
                    {ref.length > 40 ? ref.slice(0, 40) + '…' : ref}
                  </span>
                  <span className="breakdown-count">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="breakdown-card card">
          <h3>Browsers</h3>
          {topBrowsers.length === 0 ? (
            <p className="breakdown-empty">No browser data yet.</p>
          ) : (
            <ul className="breakdown-list">
              {topBrowsers.map(([browser, count]) => (
                <li key={browser}>
                  <span className="breakdown-label">{browser}</span>
                  <span className="breakdown-count">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};

export default URLStats;
