import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { marketPriceService } from '../services/api';

/**
 * Admin monitoring view for the automatic mandi price import.
 *
 * <p>Manual entry was removed: the scheduled AGMARKNET importer is the only writer, so an
 * administrator can inspect what is stored and when it was last refreshed but cannot invent a
 * "reference" price that would be shown to farmers as official data.
 */
const AdminMarketPrices = () => {
  const [prices, setPrices] = useState([]);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [rows, syncStatus] = await Promise.all([
        marketPriceService.getPrices(),
        marketPriceService.getStatus(),
      ]);
      setPrices(Array.isArray(rows) ? rows : []);
      setStatus(syncStatus || null);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load mandi prices.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const formatDate = (value) => {
    if (!value) return '—';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleDateString('en-IN');
  };

  const formatDateTime = (value) => {
    if (!value) return '—';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleString('en-IN');
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-widest text-lime-400">
            Administration
          </p>
          <h1 className="mt-2 text-3xl font-black text-white">Mandi Price Import</h1>
          <p className="mt-2 max-w-3xl text-sm text-slate-400">
            Prices are fetched automatically from the AGMARKNET dataset on data.gov.in. Manual
            reference-price entry has been removed; this page is read-only monitoring.
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-800 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </button>
      </div>

      {error && (
        <div
          role="alert"
          className="flex items-center gap-3 rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-300"
        >
          <AlertCircle className="h-5 w-5 shrink-0" /> {error}
        </div>
      )}

      <section className="grid gap-4 rounded-2xl border border-slate-800 bg-slate-900 p-5 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Import enabled</p>
          <p className={`mt-1 text-lg font-bold ${status?.enabled ? 'text-lime-400' : 'text-amber-300'}`}>
            {status ? (status.enabled ? 'Yes' : 'No API key') : 'Checking…'}
          </p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Last result</p>
          <p className="mt-1 text-sm font-semibold text-slate-200">
            {status?.lastSyncStatus || 'Checking…'}
          </p>
          <p className="mt-1 text-xs text-slate-500">{formatDateTime(status?.lastSyncAt)}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Stored records</p>
          <p className="mt-1 text-lg font-bold text-white">{status?.importedRecords ?? '—'}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Newest reporting date</p>
          <p className="mt-1 text-sm font-semibold text-slate-200">
            {formatDate(status?.newestReportingDate)}
          </p>
        </div>
      </section>

      {status?.lastSyncMessage && (
        <p className="rounded-xl border border-slate-800 bg-slate-900 p-4 text-sm text-slate-400">
          {status.lastSyncMessage}
        </p>
      )}

      <section className="rounded-2xl border border-slate-800 bg-slate-900 p-5">
        <h2 className="mb-4 text-lg font-bold text-white">Imported quotations</h2>
        {loading ? (
          <p className="text-sm text-slate-400">Loading mandi prices…</p>
        ) : prices.length === 0 ? (
          <p className="text-sm text-slate-500">
            No official records are stored yet. Set <code className="text-lime-300">MANDI_API_KEY</code>{' '}
            on the backend and enable <code className="text-lime-300">MANDI_INITIAL_SYNC=true</code>, or
            wait for the next scheduled import.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="pb-3">Commodity</th>
                  <th className="pb-3">Mandi</th>
                  <th className="pb-3">State / District</th>
                  <th className="pb-3 text-right">Modal</th>
                  <th className="pb-3">Reported on</th>
                  <th className="pb-3">Fetched at</th>
                </tr>
              </thead>
              <tbody>
                {prices.slice(0, 100).map((price) => (
                  <tr
                    key={price.id}
                    className="border-t border-slate-800 text-slate-300 transition hover:bg-slate-800/40"
                  >
                    <td className="py-3 font-semibold text-slate-100">
                      {price.commodity || price.cropName}
                    </td>
                    <td className="py-3">{price.marketName}</td>
                    <td className="py-3 text-slate-400">
                      {price.state} / {price.district}
                    </td>
                    <td className="py-3 text-right font-bold text-lime-400">
                      ₹{Number(price.modalPrice ?? price.pricePerUnit).toLocaleString('en-IN', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}
                    </td>
                    <td className="py-3">
                      {formatDate(price.reportingDate)}
                      {price.stale && (
                        <span className="ml-2 rounded-md border border-amber-500/40 px-2 py-0.5 text-[11px] font-bold uppercase text-amber-300">
                          Stale
                        </span>
                      )}
                    </td>
                    <td className="py-3 text-xs text-slate-400">{formatDateTime(price.fetchedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {prices.length > 100 && (
              <p className="mt-3 text-xs text-slate-500">
                Showing the first 100 of {prices.length} stored quotations.
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  );
};

export default AdminMarketPrices;
