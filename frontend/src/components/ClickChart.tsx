import React, { useMemo, useState } from 'react';
import type { ClickEvent } from '../api/urls';

interface ClickChartProps {
  clicks: ClickEvent[];
}

type Range = '7d' | '30d' | 'all';

const RANGE_LABELS: Record<Range, string> = {
  '7d': '7 days',
  '30d': '30 days',
  'all': 'All time',
};

const ClickChart: React.FC<ClickChartProps> = ({ clicks }) => {
  const [range, setRange] = useState<Range>('30d');

  const { buckets, maxCount } = useMemo(() => {
    const now = new Date();
    let daysBack: number;

    switch (range) {
      case '7d': daysBack = 7; break;
      case '30d': daysBack = 30; break;
      case 'all': daysBack = Math.max(30, clicks.length > 0
        ? Math.ceil((now.getTime() - new Date(clicks[clicks.length - 1].clickedAt).getTime()) / 86400000) + 1
        : 30); break;
    }

    const cutoff = new Date(now.getTime() - daysBack * 86400000);

    // Group clicks into daily buckets
    const countMap = new Map<string, number>();
    for (const click of clicks) {
      const d = new Date(click.clickedAt);
      if (d < cutoff) continue;
      const key = d.toISOString().slice(0, 10); // YYYY-MM-DD
      countMap.set(key, (countMap.get(key) || 0) + 1);
    }

    // Build array of daily buckets
    const result: { date: string; label: string; count: number }[] = [];
    for (let i = daysBack - 1; i >= 0; i--) {
      const d = new Date(now.getTime() - i * 86400000);
      const key = d.toISOString().slice(0, 10);
      const label = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
      result.push({ date: key, label, count: countMap.get(key) || 0 });
    }

    const max = Math.max(1, ...result.map(b => b.count));
    return { buckets: result, maxCount: max };
  }, [clicks, range]);

  return (
    <div className="click-chart">
      <div className="click-chart-header">
        <h3>Click Activity</h3>
        <div className="chart-range-tabs">
          {(Object.keys(RANGE_LABELS) as Range[]).map(r => (
            <button
              key={r}
              className={`chart-range-tab ${range === r ? 'active' : ''}`}
              onClick={() => setRange(r)}
            >
              {RANGE_LABELS[r]}
            </button>
          ))}
        </div>
      </div>
      <div className="chart-container">
        {buckets.length === 0 ? (
          <p className="chart-empty">No click data for this period.</p>
        ) : (
          <div className="chart-bars">
            {buckets.map(b => (
              <div key={b.date} className="chart-bar-col" title={`${b.label}: ${b.count} click${b.count !== 1 ? 's' : ''}`}>
                <div className="chart-bar-track">
                  <div
                    className="chart-bar-fill"
                    style={{ height: `${(b.count / maxCount) * 100}%` }}
                  />
                </div>
                {/* Show label every few bars to avoid overcrowding */}
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="chart-x-labels">
        {buckets.length > 0 && (
          <>
            <span>{buckets[0].label}</span>
            <span>{buckets[Math.floor(buckets.length / 2)]?.label}</span>
            <span>{buckets[buckets.length - 1].label}</span>
          </>
        )}
      </div>
    </div>
  );
};

export default ClickChart;
