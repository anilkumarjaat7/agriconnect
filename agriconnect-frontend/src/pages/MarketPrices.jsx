import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle, CalendarDays, MapPin, RefreshCw, Search, TrendingUp } from 'lucide-react';
import { marketPriceService } from '../services/api';
import { AuthContext } from '../context/AuthContext';

/**
 * Official mandi prices from the AGMARKNET dataset published on data.gov.in.
 *
 * <p>These are reference rates for a commodity in a particular mandi, not the price of any one
 * seller's listing. A seller's own asking price is stored on their crop listing and is never
 * changed by anything on this screen.
 *
 * <p>Farmers see records for the state on their registered profile. Buyers and admins can filter
 * any state. Every row shows its reporting date and source, and an explicit "Stale" marker is
 * shown when the reported date is older than the server's freshness window.
 */
const MarketPrices = () => {
  const { user } = React.useContext(AuthContext);
  const isFarmer = String(user?.role || '').replace('ROLE_', '') === 'FARMER';

  const [prices, setPrices] = useState([]);
  const [states, setStates] = useState([]);
  const [status, setStatus] = useState(null);
  const [search, setSearch] = useState('');
  const [state, setState] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadPrices = useCallback(async (term, selectedState) => {
    setLoading(true);
    setError('');
    try {
      const data = isFarmer
        ? await marketPriceService.getMyPrices(term)
        : await marketPriceService.getPrices(term, selectedState);
      setPrices(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load mandi prices.');
    } finally {
      setLoading(false);
    }
  }, [isFarmer]);

  const loadMeta = useCallback(async () => {
    try {
      const [availableStates, syncStatus] = await Promise.all([
        marketPriceService.getStates(),
        marketPriceService.getStatus(),
      ]);
      setStates(Array.isArray(availableStates) ? availableStates : []);
      setStatus(syncStatus || null);
    } catch (err) {
      // Metadata is supplementary: never hide the price table because of it.
      console.error('Could not load mandi price metadata', err);
    }
  }, []);

  useEffect(() => {
    loadPrices('', '');
    loadMeta();
  }, [loadPrices, loadMeta]);

  const handleSubmit = (e) => {
    e.preventDefault();
    loadPrices(search.trim(), state);
  };

  const clearFilters = () => {
    setSearch('');
    setState('');
    loadPrices('', '');
  };

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

  const money = (value) =>
    value == null
      ? '—'
      : Number(value).toLocaleString('en-IN', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });

  const lastSyncLabel = () => {
    if (!status) return 'Checking…';
    if (!status.enabled) return 'Automatic import is not configured';
    if (status.lastSyncStatus === 'NEVER_RUN') return 'No import has run yet';
    return `${status.lastSyncStatus} · ${formatDateTime(status.lastSyncAt)}`;
  };

  return (
    <div className="space-y-8 selection:bg-lime-500 selection:text-slate-900">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-widest text-lime-400">
            AGMARKNET · data.gov.in
          </p>
          <h1 className="mt-2 text-3xl font-black text-white">
            {isFarmer ? 'Mandi Prices in Your State' : 'State-wise Mandi Prices'}
          </h1>
          <p className="mt-2 max-w-3xl text-sm text-slate-400">
            {isFarmer
              ? `Showing official quotations for ${user?.state || 'your registered state'}. Update your state in Profile to change this.`
              : 'Official minimum, maximum and modal prices reported by mandis across India.'}{' '}
            These are market references only — they never change a seller&apos;s own listing price.
          </p>
        </div>
        <button
          onClick={() => {
            loadPrices(search, state);
            loadMeta();
          }}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-800 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </button>
      </div>

      <section className="grid gap-4 rounded-2xl border border-slate-800 bg-slate-900 p-5 sm:grid-cols-3">
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Data source</p>
          <p className="mt-1 text-sm font-semibold text-slate-200">
            {status?.source || 'AGMARKNET / data.gov.in'}
          </p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Newest reporting date</p>
          <p className="mt-1 flex items-center gap-2 text-sm font-semibold text-slate-200">
            <CalendarDays className="h-4 w-4 text-lime-400" />
            {formatDate(status?.newestReportingDate)}
          </p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Last import</p>
          <p className="mt-1 text-sm font-semibold text-slate-200">{lastSyncLabel()}</p>
        </div>
      </section>

      {status?.lastSyncStatus === 'FAILED' && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-300"
        >
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="font-semibold">The last scheduled import failed.</p>
            <p className="mt-1 text-amber-200/80">
              {status.lastSyncMessage || 'No details supplied.'} The prices below are the most recent
              records already stored; check their reporting dates before using them.
            </p>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[240px] flex-1">
          <Search
            className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500"
            aria-hidden="true"
          />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter by commodity, mandi, district or state"
            aria-label="Filter mandi prices"
            className="w-full rounded-xl border border-slate-800 bg-slate-950 py-2.5 pl-9 pr-4 text-sm text-slate-100 placeholder:text-slate-600 focus:border-lime-500 focus:outline-none focus:ring-2 focus:ring-lime-500/40"
          />
        </div>
        {!isFarmer && (
          <select
            value={state}
            onChange={(e) => setState(e.target.value)}
            aria-label="Filter by state"
            className="rounded-xl border border-slate-800 bg-slate-950 px-4 py-2.5 text-sm text-slate-100 focus:border-lime-500 focus:outline-none focus:ring-2 focus:ring-lime-500/40"
          >
            <option value="">All states</option>
            {states.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        )}
        <button
          type="submit"
          className="rounded-xl bg-lime-600 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-lime-500"
        >
          Search
        </button>
        {(search || state) && (
          <button
            type="button"
            onClick={clearFilters}
            className="rounded-xl border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-300 transition hover:bg-slate-800"
          >
            Clear
          </button>
        )}
      </form>

      {error && (
        <div
          role="alert"
          className="flex items-center gap-3 rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-300"
        >
          <AlertCircle className="h-5 w-5 shrink-0" /> {error}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-slate-400">Loading mandi prices…</p>
      ) : (
        <section className="rounded-2xl border border-slate-800 bg-slate-900 p-5">
          <h2 className="mb-4 flex items-center gap-2 text-lg font-bold text-white">
            <TrendingUp className="h-5 w-5 text-lime-400" />
            {prices.length} {prices.length === 1 ? 'quotation' : 'quotations'}
          </h2>

          {prices.length === 0 ? (
            <p className="text-sm text-slate-500">
              {status && !status.enabled
                ? 'The automatic import is not configured yet. No official prices are available — no placeholder prices are shown.'
                : isFarmer && !user?.state
                  ? 'Add your state in your Profile to see mandi prices for your region.'
                  : 'No official quotations have been imported yet. Prices appear here after the first successful data.gov.in import.'}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[980px] text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="pb-3">Commodity</th>
                    <th className="pb-3">Mandi / District</th>
                    <th className="pb-3">State</th>
                    <th className="pb-3 text-right">Min</th>
                    <th className="pb-3 text-right">Max</th>
                    <th className="pb-3 text-right">Modal</th>
                    <th className="pb-3">Reported on</th>
                    <th className="pb-3">Last updated</th>
                  </tr>
                </thead>
                <tbody>
                  {prices.map((price) => (
                    <tr
                      key={price.id}
                      className="border-t border-slate-800 text-slate-300 transition hover:bg-slate-800/40"
                    >
                      <td className="py-3">
                        <p className="font-semibold text-slate-100">{price.commodity || price.cropName}</p>
                        {(price.variety || price.grade) && (
                          <p className="text-xs text-slate-500">
                            {[price.variety, price.grade].filter(Boolean).join(' · ')}
                          </p>
                        )}
                      </td>
                      <td className="py-3">
                        <p className="flex items-center gap-1.5 font-medium text-slate-200">
                          <MapPin className="h-3.5 w-3.5 text-lime-400" aria-hidden="true" />
                          {price.marketName}
                        </p>
                        <p className="text-xs text-slate-500">{price.district}</p>
                      </td>
                      <td className="py-3 text-slate-400">{price.state}</td>
                      <td className="py-3 text-right">₹{money(price.minPrice)}</td>
                      <td className="py-3 text-right">₹{money(price.maxPrice)}</td>
                      <td className="py-3 text-right font-bold text-lime-400">
                        ₹{money(price.modalPrice ?? price.pricePerUnit)}
                      </td>
                      <td className="py-3">
                        <p>{formatDate(price.reportingDate)}</p>
                        {price.stale ? (
                          <span className="mt-1 inline-block rounded-md border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide text-amber-300">
                            Stale
                          </span>
                        ) : (
                          <span className="mt-1 inline-block rounded-md border border-lime-500/40 bg-lime-500/10 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide text-lime-300">
                            Current reporting date
                          </span>
                        )}
                      </td>
                      <td className="py-3 text-xs text-slate-400">
                        <p>{formatDateTime(price.fetchedAt || price.updatedAt)}</p>
                        <p className="truncate text-slate-600" title={price.source}>
                          {price.source || 'Official feed'}
                        </p>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
};

export default MarketPrices;
