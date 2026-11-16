const $ = (id) => document.getElementById(id);
const API = '';
const REQUEST_TIMEOUT_MS = 12000;

const OPENFREEMAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';
const OPENFREEMAP_ATTRIBUTION =
  '<a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> &copy; <a href="https://www.openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> Data from <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors';
const DEFAULT_OSRM_BASE_URL = 'https://router.project-osrm.org';
const DEFAULT_CENTER_LNGLAT = [88.3639, 22.5726]; // [lng, lat]
const AVERAGE_SPEED_KMH = 28;
const MARKER_ANIMATION_MS = 900;

let authHeader = sessionStorage.getItem('cityrideAuth') || '';
let currentUser = null;
let allStops = [];
let lastResults = [];
let selectedBus = null;
let activeBusCard = null;
let trackingTimer = null;
let trackingInFlight = false;
let driverWatchId = null, driverSendInFlight = false, pendingDriverFix = null;
let isLiveBrowserGpsActive = false;
let lastDriverGpsCoords = null;
let locationSocket = null, reconnectTimer = null, reconnectDelay = 1000;

const routeGeometryCache = new Map();

const liveState = {
  path: [],
  roadCoords: [],
  fromIndex: -1,
  toIndex: -1,
  autoFollowBus: true,
  lastBearing: 0,
  firstFixShown: false
};

const maps = {
  overview: null,
  overviewStopMarkers: [],
  live: null,
  liveLoaded: false,
  stopMarkers: [],
  busMarker: null,
  busMarkerEl: null,
  busMarkerLngLat: null,
  busAnimation: null,
  pendingRouteDraw: false
};

let activeAdminTab = 'buses';
let adminCache = {
  buses: [],
  drivers: [],
  routes: [],
  stops: [],
  'route-stops': [],
  trips: [],
  'bus-stop-history': [],
  admins: []
};

/* ==================== CORE API & UI HELPERS ==================== */

async function api(path, options = {}, authenticated = true) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs || REQUEST_TIMEOUT_MS);

  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (authenticated && authHeader) {
    headers.Authorization = authHeader;
  }

  let response;
  try {
    response = await fetch(API + path, {
      ...options,
      headers,
      signal: controller.signal
    });
  } catch (err) {
    clearTimeout(timer);
    if (err && err.name === 'AbortError') {
      throw new Error('Request timed out. Please check your connection or backend server.');
    }
    throw new Error('Network error: Unable to reach the server.');
  }
  clearTimeout(timer);

  if (response.status === 401) {
    if (authenticated && authHeader && path !== '/api/auth/me') {
      handleUnauthorizedSession();
    }
    throw new Error('Invalid credentials or session expired. Please sign in again.');
  }
  if (response.status === 403) {
    throw new Error('You do not have permission to perform this action.');
  }
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json')
    ? await response.json().catch(() => null)
    : await response.text().catch(() => '');

  if (!response.ok) {
    const message = (data && typeof data === 'object' && (data.message || data.error))
      || (typeof data === 'string' && data.trim())
      || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return data;
}

function setText(id, value) {
  const el = $(id);
  if (el) el.textContent = value;
}

function toast(message, duration = 3600) {
  const el = $('toast');
  if (!el) return;
  el.textContent = message;
  el.classList.add('show');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => el.classList.remove('show'), duration);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function sameName(a, b) {
  return String(a ?? '').trim().toLowerCase() === String(b ?? '').trim().toLowerCase();
}

function statusPill(status) {
  const s = String(status ?? '—').toUpperCase();
  const isGood = ['ACTIVE', 'RUNNING', 'AVAILABLE', 'COMPLETED'].includes(s);
  const isDanger = ['ABORTED', 'EMERGENCY ABORT', 'PASSED'].includes(s);
  const isWarn = ['MAINTENANCE', 'UPCOMING'].includes(s);
  const cls = isGood
    ? 'status'
    : isDanger
      ? 'status status-danger'
      : isWarn
        ? 'status status-warn'
        : 'status status-muted';
  return `<span class="${cls}">${escapeHtml(s)}</span>`;
}

function formatKm(km) {
  if (km == null || !Number.isFinite(Number(km))) return '—';
  const val = Number(km);
  if (val < 1) return `${Math.round(val * 1000)} m`;
  return `${val.toFixed(2)} km`;
}

function formatDuration(minutes) {
  if (minutes == null || !Number.isFinite(Number(minutes))) return '—';
  const rounded = Math.round(Number(minutes));
  if (rounded < 1) return '< 1 min';
  if (rounded < 60) return `${rounded} min`;
  const h = Math.floor(rounded / 60);
  const m = rounded % 60;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

function formatClock(dateStrOrObj) {
  if (!dateStrOrObj) return '';
  const d = dateStrOrObj instanceof Date ? dateStrOrObj : new Date(dateStrOrObj);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatTime24h(timeStr) {
  if (!timeStr) return '—';
  const parts = String(timeStr).trim().split(':');
  if (parts.length < 2) return String(timeStr);
  return `${parts[0].padStart(2, '0')}:${parts[1].slice(0, 2).padStart(2, '0')}`;
}

function formatDateTimeRecord(dateTimeStr, fallbackDate, fallbackTime) {
  if (dateTimeStr) {
    const d = new Date(dateTimeStr);
    if (!Number.isNaN(d.getTime())) {
      return `${d.toLocaleDateString()} · ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
    }
  }
  if (fallbackTime) {
    return `${fallbackDate || ''} · ${fallbackTime}`.replace(/^\s*·\s*/, '');
  }
  return '—';
}

function showLogin(show = true) {
  $('loginError').textContent = '';
  $('loginModal').classList.toggle('hidden', !show);
  if (show) $('username').focus();
}

function openActionModal(eyebrow, title, bodyHtml, onMount) {
  $('actionModalEyebrow').textContent = eyebrow || 'MANAGE RECORD';
  $('actionModalTitle').textContent = title || 'Edit';
  $('actionModalBody').innerHTML = bodyHtml;
  $('actionModal').classList.remove('hidden');
  if (typeof onMount === 'function') {
    onMount($('actionModalBody'));
  }
}

function closeActionModal() {
  $('actionModal').classList.add('hidden');
  $('actionModalBody').innerHTML = '';
}

function handleUnauthorizedSession() {
  stopLiveGps();
  authHeader = '';
  currentUser = null;
  sessionStorage.removeItem('cityrideAuth');
  $('portal').classList.add('hidden');
  $('portalNavLink').classList.add('hidden');
  $('loginOpen').classList.remove('hidden');
  $('logoutBtn').classList.add('hidden');
  $('adminPanel').classList.add('hidden');
  $('driverPanel').classList.add('hidden');
  $('overviewMapSection')?.classList.remove('hidden');
}

/* ==================== ROAD POLYLINE FETCH (OSRM) & MAPLIBRE MAPS ==================== */

async function fetchRoadGeometryForStops(routeId, stops) {
  const directCoords = (stops || []).map(s => [Number(s.longitude), Number(s.latitude)]);
  if (directCoords.length < 2) {
    return { coords: directCoords };
  }

  const cacheKey = `${routeId}:${directCoords.map(c => `${c[0].toFixed(5)},${c[1].toFixed(5)}`).join(';')}`;
  if (routeGeometryCache.has(cacheKey)) {
    return routeGeometryCache.get(cacheKey);
  }

  const coordString = directCoords.map(c => `${c[0]},${c[1]}`).join(';');
  const url = `${DEFAULT_OSRM_BASE_URL}/route/v1/driving/${coordString}?overview=full&geometries=geojson&steps=false`;

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 4500);
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
    clearTimeout(timeoutId);
    if (!response.ok) throw new Error(`OSRM HTTP ${response.status}`);
    const data = await response.json();
    const geoCoords = data?.routes?.[0]?.geometry?.coordinates;
    if (!Array.isArray(geoCoords) || geoCoords.length < 2) throw new Error('Invalid OSRM geometry');

    const result = {
      coords: geoCoords.map(([lng, lat]) => [Number(lng), Number(lat)])
    };
    routeGeometryCache.set(cacheKey, result);
    return result;
  } catch (_) {
    const fallback = { coords: directCoords };
    routeGeometryCache.set(cacheKey, fallback);
    return fallback;
  }
}

function clearMarkerList(list) {
  while (list.length > 0) {
    list.pop().remove();
  }
}

function buildLineFeature(coordsLngLat) {
  return {
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'LineString',
      coordinates: Array.isArray(coordsLngLat) ? coordsLngLat : []
    }
  };
}

function initOverviewMap() {
  const container = $('overviewMap');
  if (!container || typeof window.maplibregl === 'undefined') return;
  try {
    maps.overview = new maplibregl.Map({
      container,
      style: OPENFREEMAP_STYLE_URL,
      center: DEFAULT_CENTER_LNGLAT,
      zoom: 12,
      attributionControl: false
    });
    maps.overview.addControl(new maplibregl.NavigationControl(), 'top-right');
    maps.overview.addControl(new maplibregl.AttributionControl({
      compact: false,
      customAttribution: OPENFREEMAP_ATTRIBUTION
    }));
    maps.overview.on('load', drawOverviewStops);
  } catch (err) {
    console.warn('Overview map fallback:', err);
  }
}

function drawOverviewStops() {
  if (!maps.overview) return;
  clearMarkerList(maps.overviewStopMarkers);
  const bounds = new maplibregl.LngLatBounds();
  let count = 0;
  allStops.forEach(stop => {
    const lat = Number(stop.latitude);
    const lng = Number(stop.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
    const lngLat = [lng, lat];
    bounds.extend(lngLat);
    count++;

    const dot = document.createElement('div');
    dot.className = 'stop-map-pin';
    dot.style.width = '14px';
    dot.style.height = '14px';
    dot.title = `${stop.name} (#${stop.id})`;

    const popup = new maplibregl.Popup({ offset: 10 })
      .setHTML(`<strong>${escapeHtml(stop.name)}</strong> (Stop #${escapeHtml(stop.id)})`);

    const marker = new maplibregl.Marker({ element: dot })
      .setLngLat(lngLat)
      .setPopup(popup)
      .addTo(maps.overview);
    maps.overviewStopMarkers.push(marker);
  });
  if (count > 0) {
    maps.overview.fitBounds(bounds, { padding: 36, maxZoom: 14 });
  }
}

function ensureLiveMapGeoJsonLayers(map) {
  if (!map) return;
  if (!map.getSource('remaining-route')) {
    map.addSource('remaining-route', { type: 'geojson', data: buildLineFeature([]) });
  }
  if (!map.getLayer('remaining-route-layer')) {
    map.addLayer({
      id: 'remaining-route-layer',
      type: 'line',
      source: 'remaining-route',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#2563eb', 'line-width': 5, 'line-opacity': 0.92 }
    });
  }
  if (!map.getSource('travelled-route')) {
    map.addSource('travelled-route', { type: 'geojson', data: buildLineFeature([]) });
  }
  if (!map.getLayer('travelled-route-layer')) {
    map.addLayer({
      id: 'travelled-route-layer',
      type: 'line',
      source: 'travelled-route',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#10b981', 'line-width': 6, 'line-opacity': 0.95 }
    });
  }
}

function ensureLiveMap() {
  if (maps.live) return maps.live;
  const container = $('liveMap');
  if (!container || typeof window.maplibregl === 'undefined') return null;

  try {
    maps.live = new maplibregl.Map({
      container,
      style: OPENFREEMAP_STYLE_URL,
      center: DEFAULT_CENTER_LNGLAT,
      zoom: 13,
      attributionControl: false
    });
    maps.live.addControl(new maplibregl.NavigationControl(), 'top-right');
    maps.live.addControl(new maplibregl.AttributionControl({
      compact: false,
      customAttribution: OPENFREEMAP_ATTRIBUTION
    }));
    maps.live.on('load', () => {
      maps.liveLoaded = true;
      ensureLiveMapGeoJsonLayers(maps.live);
      if (maps.pendingRouteDraw) {
        maps.pendingRouteDraw = false;
        drawInitialRoadRouteOnMap();
      }
    });
  } catch (err) {
    console.error('Could not initialize live map:', err);
  }
  return maps.live;
}

function resetLiveMapLayers() {
  if (maps.busAnimation) {
    cancelAnimationFrame(maps.busAnimation);
    maps.busAnimation = null;
  }
  if (maps.busMarker) {
    maps.busMarker.remove();
    maps.busMarker = null;
    maps.busMarkerEl = null;
    maps.busMarkerLngLat = null;
  }
  clearMarkerList(maps.stopMarkers);
  if (maps.live && maps.liveLoaded) {
    maps.live.getSource('travelled-route')?.setData(buildLineFeature([]));
    maps.live.getSource('remaining-route')?.setData(buildLineFeature([]));
  }
}

function fitMapToCoordinates(map, coordsLngLat) {
  if (!map || !Array.isArray(coordsLngLat) || !coordsLngLat.length) return;
  map.resize();
  const bounds = new maplibregl.LngLatBounds();
  coordsLngLat.forEach(c => {
    if (Array.isArray(c) && Number.isFinite(c[0]) && Number.isFinite(c[1])) {
      bounds.extend(c);
    }
  });
  if (!bounds.isEmpty()) {
    map.fitBounds(bounds, { padding: 42, maxZoom: 15, duration: 600 });
  }
}

function drawStopMarkersOnLiveMap(passedCount) {
  if (!maps.live) return;
  clearMarkerList(maps.stopMarkers);
  liveState.path.forEach((stop, index) => {
    const isFrom = index === liveState.fromIndex;
    const isTo = index === liveState.toIndex;
    const isPassed = index < passedCount;

    const el = document.createElement('div');
    let cls = 'stop-map-pin';
    let label = String(index + 1);
    if (isPassed) {
      cls += ' stop-map-pin-passed';
      label = '✓';
    } else if (isFrom) {
      cls += ' stop-map-pin-boarding';
      label = 'B';
    } else if (isTo) {
      cls += ' stop-map-pin-dest';
      label = 'D';
    }
    el.className = cls;
    el.textContent = label;
    el.title = `#${index + 1} ${stop.name}`;

    const popup = new maplibregl.Popup({ offset: 12 }).setHTML(
      `<strong>#${index + 1} ${escapeHtml(stop.name)}</strong>`
    );

    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([Number(stop.longitude), Number(stop.latitude)])
      .setPopup(popup)
      .addTo(maps.live);
    maps.stopMarkers.push(marker);
  });
}

function drawInitialRoadRouteOnMap() {
  const map = maps.live;
  if (!map) return;
  if (!maps.liveLoaded) {
    maps.pendingRouteDraw = true;
    return;
  }
  resetLiveMapLayers();
  ensureLiveMapGeoJsonLayers(map);

  const coords = liveState.roadCoords.length > 1
    ? liveState.roadCoords
    : liveState.path.map(s => [Number(s.longitude), Number(s.latitude)]);

  if (coords.length > 1) {
    map.getSource('remaining-route')?.setData(buildLineFeature(coords));
    map.getSource('travelled-route')?.setData(buildLineFeature([]));
  }
  drawStopMarkersOnLiveMap(0);
  fitMapToCoordinates(map, coords);
}

function updateSplitRoadPolylines(busLngLat) {
  const map = maps.live;
  const coords = liveState.roadCoords;
  if (!coords.length || !map || !maps.liveLoaded) return;
  ensureLiveMapGeoJsonLayers(map);

  let closestIdx = 0;
  let minSqDist = Infinity;
  for (let i = 0; i < coords.length; i++) {
    const dx = coords[i][0] - busLngLat[0];
    const dy = coords[i][1] - busLngLat[1];
    const d2 = dx * dx + dy * dy;
    if (d2 < minSqDist) {
      minSqDist = d2;
      closestIdx = i;
    }
  }

  const travelledCoords = [...coords.slice(0, closestIdx + 1), busLngLat];
  const remainingCoords = [busLngLat, ...coords.slice(closestIdx + 1)];

  map.getSource('travelled-route')?.setData(buildLineFeature(travelledCoords));
  map.getSource('remaining-route')?.setData(buildLineFeature(remainingCoords));
}

function createBusMarkerElement(stopped) {
  const wrapper = document.createElement('div');
  wrapper.className = 'bus-live-wrapper';
  wrapper.innerHTML = `
    <div class="bus-live-halo${stopped ? ' stopped' : ''}"></div>
    <div class="bus-direction-ring">
      <div class="bus-direction-arrow"></div>
    </div>
    <div class="bus-pin${stopped ? ' bus-pin-stopped' : ''}">🚌</div>
  `;
  return wrapper;
}

function animateBusMarker(bus, stopped, reportedBearing) {
  const map = maps.live;
  if (!map) return;

  const targetLng = bus.longitude;
  const targetLat = bus.latitude;
  const targetBearing = Number.isFinite(reportedBearing) ? reportedBearing : liveState.lastBearing;
  liveState.lastBearing = targetBearing;

  if (!maps.busMarker) {
    maps.busMarkerEl = createBusMarkerElement(stopped);
    maps.busMarker = new maplibregl.Marker({
      element: maps.busMarkerEl,
      rotation: targetBearing,
      rotationAlignment: 'map'
    })
      .setLngLat([targetLng, targetLat])
      .addTo(map);
    maps.busMarkerLngLat = [targetLng, targetLat];
    return;
  }

  const halo = maps.busMarkerEl?.querySelector('.bus-live-halo');
  const pin = maps.busMarkerEl?.querySelector('.bus-pin');
  if (halo) halo.classList.toggle('stopped', Boolean(stopped));
  if (pin) pin.classList.toggle('bus-pin-stopped', Boolean(stopped));

  if (maps.busAnimation) {
    cancelAnimationFrame(maps.busAnimation);
    maps.busAnimation = null;
  }

  const startLngLat = maps.busMarkerLngLat || [targetLng, targetLat];
  if (Math.abs(startLngLat[0] - targetLng) < 1e-6 && Math.abs(startLngLat[1] - targetLat) < 1e-6) {
    maps.busMarker.setRotation(targetBearing);
    return;
  }

  const startedAt = performance.now();
  const step = now => {
    const progress = Math.min(1, (now - startedAt) / MARKER_ANIMATION_MS);
    const lng = startLngLat[0] + (targetLng - startLngLat[0]) * progress;
    const lat = startLngLat[1] + (targetLat - startLngLat[1]) * progress;
    maps.busMarkerLngLat = [lng, lat];
    maps.busMarker.setLngLat(maps.busMarkerLngLat);
    maps.busMarker.setRotation(targetBearing);
    maps.busAnimation = progress < 1 ? requestAnimationFrame(step) : null;
  };
  maps.busAnimation = requestAnimationFrame(step);
}

/* ==================== WEBSOCKET CONNECTION ==================== */

function connectLocationSocket() {
  if (typeof window.WebSocket === 'undefined') return;
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  try {
    locationSocket = new WebSocket(`${protocol}//${window.location.host}/ws/location`);
  } catch (_) {
    return;
  }

  locationSocket.addEventListener('open', () => {
    reconnectDelay = 1000;
  });

  locationSocket.addEventListener('message', event => {
    try {
      const location = JSON.parse(event.data);
      if (Number(location.id) === Number(selectedBus)) {
        refreshTracking(false);
      }
      if (currentUser?.role === 'ADMIN' && activeAdminTab === 'buses') {
        const idx = adminCache.buses.findIndex(b => Number(b.id) === Number(location.id));
        if (idx !== -1) {
          adminCache.buses[idx] = { ...adminCache.buses[idx], ...location };
        }
      }
    } catch (error) {
      console.error('Received an invalid bus location update.', error);
    }
  });

  locationSocket.addEventListener('close', () => {
    clearTimeout(reconnectTimer);
    reconnectTimer = setTimeout(connectLocationSocket, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 30000);
  });

  locationSocket.addEventListener('error', () => {
    try { locationSocket.close(); } catch (_) {}
  });
}

/* ==================== PASSENGER SEARCH & SIMPLIFIED LIVE TRACKING ==================== */

async function loadStops() {
  try {
    const stops = await api('/api/stops', {}, false);
    allStops = Array.isArray(stops) ? stops : [];
    const currentFrom = $('fromStop').value;
    const currentTo = $('toStop').value;
    ['fromStop', 'toStop'].forEach(id => {
      const select = $(id);
      select.innerHTML = '<option value="">Choose ' + (id === 'fromStop' ? 'starting' : 'destination') + ' stop</option>';
      allStops.forEach(stop => select.add(new Option(stop.name, stop.name)));
    });
    if (currentFrom && allStops.some(s => s.name === currentFrom)) $('fromStop').value = currentFrom;
    if (currentTo && allStops.some(s => s.name === currentTo)) $('toStop').value = currentTo;
    drawOverviewStops();
  } catch (_) {
    toast('Could not load stops. Check that the backend server is running.');
  }
}

$('swapStops').addEventListener('click', () => {
  const a = $('fromStop').value;
  $('fromStop').value = $('toStop').value;
  $('toStop').value = a;
});

$('searchForm').addEventListener('submit', async e => {
  e.preventDefault();
  const from = $('fromStop').value.trim();
  const to = $('toStop').value.trim();
  if (!from || !to) return toast('Please select both starting and destination stops.');
  if (from.toLowerCase() === to.toLowerCase()) return toast('Choose two different stops.');

  const submitBtn = $('searchSubmitBtn');
  submitBtn.disabled = true;
  submitBtn.innerHTML = 'Searching… <span>→</span>';

  closeLiveTracking();
  $('results').innerHTML = '<div class="empty-state">Searching available buses and route stoppages…</div>';
  $('routeStoppagesBanner').classList.add('hidden');

  try {
    const [busesRes, routeSearchRes] = await Promise.allSettled([
      api(`/api/passenger/buses?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, {}, false),
      api(`/api/route-stops/search?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, {}, false)
    ]);

    if (routeSearchRes.status === 'fulfilled' && Array.isArray(routeSearchRes.value?.stoppages) && routeSearchRes.value.stoppages.length) {
      const stopsHtml = routeSearchRes.value.stoppages
        .map((s, idx) => `<span class="stop-pill">${idx + 1}. ${escapeHtml(s)}</span>`)
        .join('<span class="muted">→</span>');
      $('routeStoppagesBanner').innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
          <strong style="font-size:13px;">Route Stoppages (${escapeHtml(routeSearchRes.value.from)} → ${escapeHtml(routeSearchRes.value.to)})</strong>
          <span class="muted">${routeSearchRes.value.stoppages.length} stops on journey</span>
        </div>
        <div class="stop-pill-row">${stopsHtml}</div>`;
      $('routeStoppagesBanner').classList.remove('hidden');
    }

    if (busesRes.status === 'fulfilled') {
      lastResults = Array.isArray(busesRes.value) ? busesRes.value : [];
      renderBusResults(lastResults);
      $('results')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
      throw busesRes.reason;
    }
  } catch (err) {
    lastResults = [];
    $('results').innerHTML = `<div class="empty-state"><strong>No matching running buses</strong><p>${escapeHtml(err.message)}</p></div>`;
    $('resultCount').textContent = '0 buses found';
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerHTML = 'Search Available Buses <span>→</span>';
  }
});

function renderBusResults(buses) {
  $('resultCount').textContent = `${buses.length} bus${buses.length === 1 ? '' : 'es'} found`;
  if (!buses.length) {
    $('results').innerHTML = '<div class="empty-state"><strong>No running buses on this route right now</strong><p>A bus appears here once a trip is started on a matching route.</p></div>';
    return;
  }
  const fromVal = $('fromStop').value;
  const toVal = $('toStop').value;

  // Point 1: Keep Bus Number and Route Name always visible even when Live Route Tracking is open
  $('results').innerHTML = buses.map(b => {
    const isRunning = String(b.status).toUpperCase() === 'RUNNING';
    const isSelected = Number(selectedBus) === Number(b.busId);
    const timeText = formatTime24h(b.departureTime);
    return `
    <article class="bus-card ${isSelected ? 'selected' : ''}" data-bus="${b.busId}">
      <div class="bus-top">
        <div class="bus-number">Bus ${escapeHtml(b.busNumber)}</div>
        <span class="status ${isRunning ? '' : 'status-warn'}">
          ${isRunning ? '<i class="live-dot"></i>RUNNING' : `🕒 ${escapeHtml(timeText)}`}
        </span>
      </div>
      <div class="bus-route">${escapeHtml(b.routename || `Route #${b.routeId}`)} (${escapeHtml(fromVal)} → ${escapeHtml(toVal)})</div>
      <button class="btn btn-primary btn-wide track-btn" type="button" data-bus="${b.busId}">
        🚌 ${isSelected ? 'Live Route Tracking Active' : 'Open Live Route Tracking'} <span>→</span>
      </button>
    </article>`;
  }).join('');

  document.querySelectorAll('.track-btn').forEach(btn => btn.addEventListener('click', e => {
    e.stopPropagation();
    selectBus(Number(btn.dataset.bus));
  }));
  document.querySelectorAll('.bus-card').forEach(card => card.addEventListener('click', () => selectBus(Number(card.dataset.bus))));
}

function showBanner(message, kind) {
  const banner = $('trip-banner');
  if (!banner) return;
  if (!message) {
    banner.classList.add('hidden');
    banner.textContent = '';
    return;
  }
  banner.textContent = message;
  banner.className = `trip-banner trip-banner-${kind || 'info'}`;
}

function closeLiveTracking() {
  selectedBus = null;
  activeBusCard = null;
  if (trackingTimer) clearInterval(trackingTimer);
  trackingTimer = null;
  resetLiveMapLayers();
  $('live-tracking-box')?.classList.add('hidden');
  if (lastResults.length) {
    renderBusResults(lastResults);
  }
}

async function selectBus(busId) {
  const bus = lastResults.find(b => Number(b.busId) === Number(busId));
  if (!bus) return;

  selectedBus = busId;
  activeBusCard = bus;
  liveState.firstFixShown = false;

  // Re-render bus cards so duplicate Bus Number & Route on the selected card is hidden while Live Tracking is open
  renderBusResults(lastResults);
  if (trackingTimer) clearInterval(trackingTimer);

  const fromName = $('fromStop').value || 'Boarding Stop';
  const toName = $('toStop').value || 'Destination';

  const box = $('live-tracking-box');
  box.classList.remove('hidden');

  setText('tracking-bus-title', `Bus ${bus.busNumber} · ${bus.routename || 'Route #' + bus.routeId}`);
  setText('track-speed-note', `Average Speed: ${AVERAGE_SPEED_KMH} km/h`);

  const map = ensureLiveMap();
  if (map) map.resize();

  box.scrollIntoView({ behavior: 'smooth', block: 'start' });

  try {
    const stops = await api(`/api/passenger/routes/${bus.routeId}/stops`, {}, false);
    const validStops = (Array.isArray(stops) ? stops : []).filter(
      s => Number.isFinite(Number(s.latitude)) && Number.isFinite(Number(s.longitude))
    );
    liveState.path = validStops;
    liveState.fromIndex = validStops.findIndex(s => sameName(s.name, fromName));
    if (liveState.fromIndex < 0) liveState.fromIndex = 0;

    liveState.toIndex = validStops.findIndex((s, idx) => sameName(s.name, toName) && idx > liveState.fromIndex);
    if (liveState.toIndex < 0) liveState.toIndex = Math.max(0, validStops.length - 1);

    const roadData = await fetchRoadGeometryForStops(bus.routeId, validStops);
    liveState.roadCoords = roadData.coords;

    if (map) {
      drawInitialRoadRouteOnMap();
    }
  } catch (err) {
    toast(`Could not load route stops: ${err.message}`);
  }

  await refreshTracking(false);
  trackingTimer = setInterval(() => refreshTracking(false), 5000);
}

function renderWimtTimelineFromBackend(stopsList, status, pct, updatedTimeText) {
  const container = $('wimt-timeline-list');
  const countEl = $('wimt-stops-count');
  setText('wimt-progress-pct', `${pct}%`);
  if ($('wimt-progress-fill')) $('wimt-progress-fill').style.width = `${pct}%`;
  if ($('wimt-progress-bus')) $('wimt-progress-bus').style.left = `${pct}%`;
  setText('wimt-current-time', `Time: ${updatedTimeText}`);

  if (!container) return;
  if (!Array.isArray(stopsList) || !stopsList.length) {
    container.innerHTML = '<p class="wimt-empty">No stops linked to this route yet.</p>';
    return;
  }

  if (countEl) countEl.textContent = `${stopsList.length} stops`;

  const firstUpcomingIdx = stopsList.findIndex(s => s.status === 'UPCOMING');
  const currentIdx = stopsList.findIndex(s => s.status === 'CURRENT');

  const rowsHtml = stopsList.map((stopDto, index) => {
    const st = String(stopDto.status || 'UPCOMING').toUpperCase();
    const isFrom = index === liveState.fromIndex;
    const isTo = index === liveState.toIndex;
    const isPassed = st === 'PASSED';
    const isCurrent = st === 'CURRENT';
    const isNext = index === firstUpcomingIdx;

    // const nodeClass = isPassed
    //   ? 'wimt-node wimt-node-passed'
    //   : isCurrent
    //     ? 'wimt-node wimt-node-current'
    //     : 'wimt-node wimt-node-upcoming';
    const isTransitLine = currentIdx === -1 && firstUpcomingIdx === index + 1 && status === 'RUNNING';
    const nodeClass = isPassed
        ? 'wimt-node wimt-node-passed'
        : isCurrent
            ? 'wimt-node wimt-node-current'
            : (isNext && status === 'RUNNING')
                ? 'wimt-node wimt-node-next-glow'
                : 'wimt-node wimt-node-upcoming';
    const nodeText = isPassed ? 'C' : isCurrent ? '🚌' : String(index + 1);
    //const railClass = isPassed ? 'wimt-rail-line wimt-rail-line-passed' : 'wimt-rail-line';
    const railClass = isTransitLine
        ? 'wimt-rail-line wimt-rail-line-active'
        : isPassed
            ? 'wimt-rail-line wimt-rail-line-passed'
            : 'wimt-rail-line';
    const cardClass = isCurrent
      ? 'wimt-stop-card wimt-stop-card-current'
      : isPassed
        ? 'wimt-stop-card wimt-stop-card-passed'
        : 'wimt-stop-card';

    const badges = [];
    if (isPassed) {
      // Point 6: Show "Passed" in Red for passed stops
      badges.push('<span class="wimt-tag wimt-tag-passed">Passed</span>');
    } else {
      if (isFrom) badges.push('<span class="wimt-tag wimt-tag-boarding">BOARDING</span>');
      if (isTo) badges.push('<span class="wimt-tag wimt-tag-dest">DESTINATION</span>');
      if (isCurrent && status === 'RUNNING') {
        badges.push('<span class="wimt-tag wimt-tag-here">🚌 Bus Here</span>');
      } else if (isNext && status === 'RUNNING') {
        badges.push('<span class="wimt-tag wimt-tag-next">Next Stop</span>');
      }
    }

    let subHtml;
    if (isPassed) {
      subHtml = `<span>Stop #${escapeHtml(stopDto.stopOrder ?? index + 1)}</span>`;
    } else if (isCurrent) {
      subHtml = `<span>Stop #${escapeHtml(stopDto.stopOrder ?? index + 1)}</span><strong>Bus is here now</strong>`;
    } else if (stopDto.etaMinutes != null) {
      const clock = stopDto.expectedArrivalTime ? ` · ≈ ${formatClock(stopDto.expectedArrivalTime)}` : '';
      subHtml = `<span>${escapeHtml(formatKm(stopDto.distanceKm))} away</span><strong>ETA ${escapeHtml(formatDuration(stopDto.etaMinutes))}${escapeHtml(clock)}</strong>`;
    } else {
      subHtml = `<span>Stop #${escapeHtml(stopDto.stopOrder ?? index + 1)}</span><strong>Waiting for GPS</strong>`;
    }

    let inTransitHtml = '';
    if (currentIdx === -1 && firstUpcomingIdx === index + 1 && status === 'RUNNING') {
      const nextStop = stopsList[firstUpcomingIdx];
      inTransitHtml = `
        <div class="wimt-in-transit-pill">
          <span>🚌 <strong>Live Bus</strong> in transit</span>
          <span>${formatKm(nextStop.distanceKm)} to ${escapeHtml(nextStop.stopName)} · ETA ${formatDuration(nextStop.etaMinutes)}</span>
        </div>`;
    }

    return `
      <div class="wimt-stop-row">
        <div class="wimt-rail-col">
          <div class="${nodeClass}">${nodeText}</div>
          ${index < stopsList.length - 1 ? `<div class="${railClass}"></div>` : ''}
        </div>
        <div class="wimt-stop-Body">
          <div class="${cardClass}">
            <div class="wimt-stop-top">
              <span class="wimt-stop-name">${index + 1}. ${escapeHtml(stopDto.stopName)}</span>
              <div class="wimt-stop-badges">${badges.join('')}</div>
            </div>
            <div class="wimt-stop-sub">${subHtml}</div>
          </div>
          ${inTransitHtml}
        </div>
      </div>`;
  });

  container.innerHTML = rowsHtml.join('');
}

async function refreshTracking(isManualRefresh = false) {
  if (!selectedBus) return;
  if (trackingInFlight && !isManualRefresh) return;
  trackingInFlight = true;
  try {
    const rParam = activeBusCard?.routeId ? `?routeId=${activeBusCard.routeId}` : '';
    const [tracking, details] = await Promise.all([
      api(`/api/passenger/tracking/${selectedBus}${rParam}`, {}, false),
      api(`/api/buses/${selectedBus}/details${rParam}`, {}, false).catch(() => null)
    ]);

    const busNum = tracking.busNumber || activeBusCard?.busNumber || `BUS-${selectedBus}`;
    const routeName = tracking.routeName || activeBusCard?.routename || `R${String(tracking.routeId || activeBusCard?.routeId || 1).padStart(3, '0')}`;
    const status = tracking.tripStatus || 'RUNNING';
    const hasEmergencyAbort = Boolean(tracking.abortReason && tracking.abortReason.trim());
    const stopsList = details?.stops || [];
    const originName = stopsList[0]?.stopName || liveState.path[0]?.name || $('fromStop').value || 'Origin';
    const finalDestName = stopsList[stopsList.length - 1]?.stopName || liveState.path[liveState.path.length - 1]?.name || $('toStop').value || 'Destination';
    const hasFix = tracking.currentLatitude != null && tracking.currentLongitude != null;
    const updatedTimeText = tracking.lastUpdated
      ? new Date(tracking.lastUpdated).toLocaleTimeString()
      : new Date().toLocaleTimeString();

    setText('tracking-bus-title', `Bus ${busNum} · ${routeName}`);
    setText('info-bus-number', busNum);
    setText('info-route-name', `${routeName} (${originName} → ${finalDestName})`);
    setText('track-trip-status', hasEmergencyAbort ? `${status} (Emergency Abort)` : status);
    setText('track-speed-note', `Average Speed: ${AVERAGE_SPEED_KMH} km/h`);

    const pulseDot = $('live-pulse-dot');
    if (pulseDot) {
      pulseDot.className = (hasFix && status === 'RUNNING') ? 'live-pulse-indicator' : 'live-pulse-indicator paused';
    }

    if (hasEmergencyAbort) {
      showBanner(`⚠️ Trip Completed via Emergency Abort: ${tracking.abortReason}`, 'danger');
    } else if (status === 'COMPLETED') {
      showBanner('✓ Trip Completed: This bus has reached its destination.', 'done');
    } else if (!hasFix) {
      showBanner('Trip is running. Waiting for initial GPS coordinates from the driver...', 'info');
    } else {
      showBanner('');
    }

    const liveSpeedKmh = Number.isFinite(Number(tracking.speedKmh)) ? Number(tracking.speedKmh) : 0.0;
    setText('track-speed', `${liveSpeedKmh.toFixed(1)} km/h`);

    const passedStops = stopsList.filter(s => s.status === 'PASSED');
    const hasCurrent = stopsList.some(s => s.status === 'CURRENT');
    const totalSegments = Math.max(1, stopsList.length - 1);
    const completedSegments = passedStops.length === 0 ? 0 : Math.min(totalSegments, passedStops.length - (hasCurrent ? 0 : 0.5));
    const pct = stopsList.length > 1
        ? Math.min(100, Math.round((completedSegments / totalSegments) * 100))
        : 0;
    if (!hasFix) {
      setText('track-latitude', 'Waiting for GPS');
      setText('track-longitude', 'Waiting for GPS');
      setText('track-updated-at', '—');
      renderWimtTimelineFromBackend(stopsList, status, pct, updatedTimeText);
      return;
    }

    const bus = { latitude: Number(tracking.currentLatitude), longitude: Number(tracking.currentLongitude) };
    setText('track-latitude', bus.latitude.toFixed(6));
    setText('track-longitude', bus.longitude.toFixed(6));
    setText('track-updated-at', updatedTimeText);

    drawStopMarkersOnLiveMap(passedStops.length);
    updateSplitRoadPolylines([bus.longitude, bus.latitude]);
    renderWimtTimelineFromBackend(stopsList, status, pct, updatedTimeText);
    animateBusMarker(bus, liveSpeedKmh < 0.5, Number(tracking.bearing));

    if (maps.live) {
      maps.live.resize();
      if (!liveState.firstFixShown) {
        liveState.firstFixShown = true;
        const allCoords = liveState.roadCoords.length
          ? [...liveState.roadCoords, [bus.longitude, bus.latitude]]
          : [[bus.longitude, bus.latitude]];
        fitMapToCoordinates(maps.live, allCoords);
      } else if (isManualRefresh || liveState.autoFollowBus) {
        maps.live.easeTo({ center: [bus.longitude, bus.latitude], duration: 650 });
      }
    }

    if (isManualRefresh) {
      toast('Live tracking data refreshed.');
    }
  } catch (err) {
    // Agar is bus ka koi trip start nahi hua hai, toh purani bus ka data clear kar do
    setText('info-bus-number', activeBusCard?.busNumber || `BUS-${selectedBus}`);
    setText('info-route-name', activeBusCard?.routename || '—');
    setText('track-trip-status', 'NOT STARTED');
    setText('track-speed', '0.0 km/h');
    setText('track-latitude', '—');
    setText('track-longitude', '—');
    setText('track-updated-at', '—');
    showBanner(`🕒 This bus has not started its journey on this route yet. Scheduled Time: ${formatTime24h(activeBusCard?.departureTime)}`, 'info');
    const previewStops = liveState.path.map((s, i) => ({
      stopId: s.id, stopName: s.name, stopOrder: i + 1, status: 'UPCOMING'
    }));
    renderWimtTimelineFromBackend(previewStops, 'ACTIVE', 0, 'Not Started', 0);
    if (isManualRefresh) toast(err.message);
  } finally {
    trackingInFlight = false;
  }
}

$('auto-follow-btn')?.addEventListener('click', ev => {
  liveState.autoFollowBus = !liveState.autoFollowBus;
  const btn = ev.currentTarget;
  btn.textContent = liveState.autoFollowBus ? '🎯 Auto-Follow: ON' : '🎯 Auto-Follow: OFF';
  btn.classList.toggle('active-toggle', liveState.autoFollowBus);
});

$('refresh-location-btn')?.addEventListener('click', () => {
  if (selectedBus) {
    trackingInFlight = false;
    refreshTracking(true);
  }
});

$('close-tracking-btn')?.addEventListener('click', closeLiveTracking);

/* ==================== AUTHENTICATION & MODAL CONTROLS ==================== */

$('loginOpen').addEventListener('click', () => showLogin(true));
document.querySelectorAll('[data-close]').forEach(el => el.addEventListener('click', () => showLogin(false)));
document.querySelectorAll('[data-action-close]').forEach(el => el.addEventListener('click', closeActionModal));

$('togglePassword').addEventListener('click', () => {
  const password = $('password');
  const visible = password.type === 'password';
  password.type = visible ? 'text' : 'password';
  $('togglePassword').textContent = visible ? 'Hide' : 'Show';
  $('togglePassword').setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
  $('togglePassword').setAttribute('aria-pressed', String(visible));
});

$('loginForm').addEventListener('submit', async e => {
  e.preventDefault();
  $('loginError').textContent = '';
  const username = $('username').value.trim();
  const password = $('password').value.trim();
  if (!username || !password) return;

  const submit = $('loginSubmit');
  submit.disabled = true;
  submit.textContent = 'Signing in…';

  authHeader = 'Basic ' + btoa(unescape(encodeURIComponent(username + ':' + password)));
  let me;
  try {
    me = await api('/api/auth/me');
  } catch (err) {
    authHeader = '';
    sessionStorage.removeItem('cityrideAuth');
    $('loginError').textContent = err.message || 'Invalid username or password';
    submit.disabled = false;
    submit.textContent = 'Sign in securely';
    return;
  }

  if (me.role !== 'ADMIN' && me.role !== 'DRIVER') {
    authHeader = '';
    $('loginError').textContent = 'This account does not have a staff dashboard role.';
    submit.disabled = false;
    submit.textContent = 'Sign in securely';
    return;
  }

  currentUser = me;
  sessionStorage.setItem('cityrideAuth', authHeader);
  showLogin(false);
  $('loginForm').reset();
  $('password').type = 'password';
  $('togglePassword').textContent = 'Show';
  $('togglePassword').setAttribute('aria-pressed', 'false');
  $('loginOpen').classList.add('hidden');
  $('logoutBtn').classList.remove('hidden');
  $('portalNavLink').classList.remove('hidden');
  $('portal').classList.remove('hidden');
  $('overviewMapSection')?.classList.add('hidden');
  $('portalUser').textContent = `Signed in as ${me.username} · ${me.role}`;
  submit.disabled = false;
  submit.textContent = 'Sign in securely';

  try {
    if (me.role === 'ADMIN') await renderAdmin();
    else await renderDriver();
    $('portal').scrollIntoView({ behavior: 'smooth' });
  } catch (err) {
    toast(`Signed in, but dashboard data could not load: ${err.message}`);
  }
});

$('logoutBtn').addEventListener('click', () => {
  handleUnauthorizedSession();
  toast('You have been logged out.');
});

/* ==================== ADMIN DASHBOARD ==================== */

const adminTabs = [
  { key: 'buses', title: 'Buses', path: '/api/buses' },
  { key: 'drivers', title: 'Drivers', path: '/api/drivers' },
  { key: 'routes', title: 'Routes', path: '/api/routes' },
  { key: 'stops', title: 'Stops', path: '/api/stops' },
  { key: 'route-stops', title: 'Route Stops', path: '/api/route-stops' },
  { key: 'trips', title: 'Trips', path: '/api/trips' },
  { key: 'bus-stop-history', title: 'Stop History', path: '/api/bus-stop-history' },
  { key: 'admins', title: 'Admins', path: '/api/admin/accounts' }
];

function getFirstStopForRoute(routeId) {
  const rStops = adminCache['route-stops']
    .filter(rs => Number(rs.routeId) === Number(routeId))
    .sort((a, b) => Number(a.stopOrder) - Number(b.stopOrder));
  if (!rStops.length) return null;
  return adminCache.stops.find(s => Number(s.id) === Number(rStops[0].stopId)) || null;
}

async function refreshAllAdminData() {
  const [buses, drivers, routes, stops, routeStops, trips, history, admins] = await Promise.all([
    api('/api/buses').catch(() => []),
    api('/api/drivers').catch(() => []),
    api('/api/routes').catch(() => []),
    api('/api/stops').catch(() => []),
    api('/api/route-stops').catch(() => []),
    api('/api/trips').catch(() => []),
    api('/api/bus-stop-history').catch(() => []),
    api('/api/admin/accounts').catch(() => [])
  ]);
  adminCache = {
    buses: Array.isArray(buses) ? buses : [],
    drivers: Array.isArray(drivers) ? drivers : [],
    routes: Array.isArray(routes) ? routes : [],
    stops: Array.isArray(stops) ? stops : [],
    'route-stops': Array.isArray(routeStops) ? routeStops : [],
    trips: Array.isArray(trips) ? trips : [],
    'bus-stop-history': Array.isArray(history) ? history : [],
    admins: Array.isArray(admins) ? admins : []
  };
  await loadStops();
  renderAdminOverviewStats();
}

function renderAdminOverviewStats() {
  const statsEl = $('adminStats');
  if (!statsEl) return;
  const runningTrips = adminCache.trips.filter(t => t.status === 'RUNNING').length;
  const activeDrivers = adminCache.drivers.filter(d => d.status === 'AVAILABLE').length;
  const items = [
    [adminCache.buses.length, 'Total Buses'],
    [`${adminCache.drivers.length} (${activeDrivers} avail)`, 'Drivers'],
    [adminCache.routes.length, 'Routes'],
    [adminCache.stops.length, 'Stops'],
    [adminCache['route-stops'].length, 'Route Stops'],
    [`${adminCache.trips.length} (${runningTrips} live)`, 'Trips'],
    [adminCache.admins.length, 'Admins']
  ];
  statsEl.innerHTML = items
    .map(([val, label]) => `<div><small>${escapeHtml(label.toUpperCase())}</small><strong>${escapeHtml(val)}</strong></div>`)
    .join('');
}

async function renderAdmin() {
  $('portalEyebrow').textContent = 'ADMINISTRATION';
  $('portalTitle').textContent = 'Control centre';
  $('adminPanel').classList.remove('hidden');
  $('driverPanel').classList.add('hidden');
  $('adminPanel').innerHTML = `
    <div class="panel-card">
      <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;margin-bottom:12px;">
        <h3 style="margin:0;">System Overview</h3>
        <button id="refreshAdminBtn" class="btn btn-light small-btn" type="button">↻ Refresh All Data</button>
      </div>
      <div id="adminStats" class="bus-stats"></div>
    </div>
    <div class="panel-card">
      <div class="toolbar" id="adminTabToolbar">
        ${adminTabs.map(t => `<button class="btn btn-light small-btn ${t.key === activeAdminTab ? 'active-tab' : ''}" type="button" data-tab="${t.key}">${escapeHtml(t.title)}</button>`).join('')}
      </div>
      <div id="adminContent"></div>
    </div>`;

  $('refreshAdminBtn').addEventListener('click', async () => {
    await refreshAllAdminData();
    await renderAdminResource(activeAdminTab);
    toast('Admin data refreshed.');
  });

  document.querySelectorAll('[data-tab]').forEach(btn => btn.addEventListener('click', () => {
    activeAdminTab = btn.dataset.tab;
    document.querySelectorAll('[data-tab]').forEach(b => b.classList.toggle('active-tab', b.dataset.tab === activeAdminTab));
    renderAdminResource(activeAdminTab);
  }));

  await refreshAllAdminData();
  await renderAdminResource(activeAdminTab);
}

function getRouteLabel(routeId) {
  if (routeId == null) return '—';
  const r = adminCache.routes.find(x => Number(x.id) === Number(routeId));
  return r ? `#${r.id} — ${r.name}` : `Route #${routeId}`;
}

function getStopLabel(stopId) {
  if (stopId == null) return '—';
  const s = adminCache.stops.find(x => Number(x.id) === Number(stopId));
  return s ? `#${s.id} — ${s.name}` : `Stop #${stopId}`;
}

function getBusLabel(busId) {
  if (busId == null) return '—';
  const b = adminCache.buses.find(x => Number(x.id) === Number(busId));
  return b ? `#${b.id} (${b.busNumber})` : `Bus #${busId}`;
}

function getDriverLabel(driverId) {
  if (driverId == null) return '—';
  const d = adminCache.drivers.find(x => Number(x.id) === Number(driverId));
  return d ? `#${d.id} — ${d.name} (${d.phone})` : `Driver #${driverId}`;
}

async function renderAdminResource(key) {
  const tab = adminTabs.find(x => x.key === key);
  if (!tab) return;
  $('adminContent').innerHTML = `<p class="muted">Loading ${escapeHtml(tab.title.toLowerCase())}…</p>`;

  try {
    await refreshAllAdminData();
  } catch (e) {
    $('adminContent').innerHTML = `<p class="error">${escapeHtml(e.message)}</p>`;
    return;
  }

  if (key === 'buses') renderAdminBuses();
  else if (key === 'drivers') renderAdminDrivers();
  else if (key === 'routes') renderAdminRoutes();
  else if (key === 'stops') renderAdminStops();
  else if (key === 'route-stops') renderAdminRouteStops();
  else if (key === 'trips') renderAdminTrips();
  else if (key === 'bus-stop-history') renderAdminStopHistory();
  else if (key === 'admins') renderAdminAdmins();
}

/* --- 1. Admin Buses (Point 3: Clean Starting Latitude & Longitude Handling) --- */
function renderAdminBuses() {
  const rows = adminCache.buses;
  const routeOptions = adminCache.routes.length
    ? adminCache.routes.map(r => `<option value="${r.id}">#${r.id} — ${escapeHtml(r.name)}</option>`).join('')
    : '<option value="">Create a route first</option>';

  $('adminContent').innerHTML = `
    <h3>Buses Management</h3>
    <form id="createBusForm" class="form-grid">
      <label>Bus Number<input name="busNumber" type="text" placeholder="e.g. WB01A1234" required></label>
      <label>Going Route<select name="routeId" id="busCreateRouteSelect" required ${adminCache.routes.length ? '' : 'disabled'}>${routeOptions}</select></label>
      <label>Going Time<input name="departureTime" type="time" required></label>
      <label>Return Route<select name="returnRouteId" required ${adminCache.routes.length ? '' : 'disabled'}>${routeOptions}</select></label>
      <label>Return Time<input name="returnDepartureTime" type="time" required></label>
      <label>Status
        <select name="status" required>
          <option value="ACTIVE">ACTIVE</option>
          <option value="INACTIVE">INACTIVE</option>
          <option value="MAINTENANCE">MAINTENANCE</option>
        </select>
      </label>
      <label>Starting Latitude<input name="currentLatitude" id="busCreateLat" type="number" step="any" min="-90" max="90" placeholder="e.g. 22.5726" required></label>
      <label>Starting Longitude<input name="currentLongitude" id="busCreateLon" type="number" step="any" min="-180" max="180" placeholder="e.g. 88.3639" required></label>
      <button class="btn btn-primary" type="submit" ${adminCache.routes.length ? '' : 'disabled'}>+ Add Bus</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>BUS NUMBER</th><th>GOING ROUTE &amp; TIME</th><th>RETURN ROUTE &amp; TIME</th><th>STATUS</th><th>LIVE COORDINATES</th><th>LAST UPDATED</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(row.busNumber)}</strong></td>
              <td>${escapeHtml(getRouteLabel(row.routeId))}<br><small class="muted">🕒 ${escapeHtml(formatTime24h(row.departureTime))}</small></td>
              <td>${escapeHtml(getRouteLabel(row.returnRouteId))}<br><small class="muted">🕒 ${escapeHtml(formatTime24h(row.returnDepartureTime))}</small></td>
              <td>${statusPill(row.status)}</td>
              <td>${row.currentLatitude != null && row.currentLongitude != null ? `${Number(row.currentLatitude).toFixed(5)}, ${Number(row.currentLongitude).toFixed(5)}` : '—'}</td>
              <td>${escapeHtml(row.lastUpdated || '—')}</td>
              <td>
                <div class="action-row">
                  <button class="btn btn-light small-btn" type="button" data-bus-edit="${row.id}">Edit</button>
                  <button class="btn btn-light small-btn" type="button" data-bus-gps="${row.id}">Update GPS</button>
                  <button class="btn btn-danger small-btn" type="button" data-bus-delete="${row.id}">Delete</button>
                </div>
              </td>
            </tr>`).join('') || '<tr><td colspan="8">No buses created yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  const syncBusFirstStopCoords = () => {
    const rId = Number($('busCreateRouteSelect')?.value);
    if (!rId) return;
    const firstStop = getFirstStopForRoute(rId);
    if (firstStop && firstStop.latitude != null && firstStop.longitude != null) {
      $('busCreateLat').value = firstStop.latitude;
      $('busCreateLon').value = firstStop.longitude;
    } else if (!$('busCreateLat').value || !$('busCreateLon').value) {
      $('busCreateLat').value = 22.5726;
      $('busCreateLon').value = 88.3639;
    }
  };
  syncBusFirstStopCoords();
  $('busCreateRouteSelect')?.addEventListener('change', syncBusFirstStopCoords);

  $('createBusForm').addEventListener('submit', async e => {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const busNumber = String(form.get('busNumber') || '').trim();
    const routeId = Number(form.get('routeId'));
    const status = String(form.get('status') || 'ACTIVE').trim();
    const lat = Number(form.get('currentLatitude'));
    const lon = Number(form.get('currentLongitude'));

    if (!busNumber || !routeId || !Number.isFinite(lat) || !Number.isFinite(lon)) {
      return toast('Please fill in Bus Number, Assigned Route, Starting Latitude, and Starting Longitude.');
    }

    const body = {
      busNumber,
      routeId,
      departureTime: form.get('departureTime'),
      returnRouteId: Number(form.get('returnRouteId')),
      returnDepartureTime: form.get('returnDepartureTime'),
      status,
      currentLatitude: lat,
      currentLongitude: lon
    };
    try {
      await api('/api/buses', { method: 'POST', body: JSON.stringify(body) });
      toast('Bus created successfully.');
      await renderAdminResource('buses');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-bus-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.busEdit);
    if (!row) return;
    const goingOpts = adminCache.routes.map(r => `<option value="${r.id}" ${Number(r.id) === Number(row.routeId) ? 'selected' : ''}>#${r.id} — ${escapeHtml(r.name)}</option>`).join('');
    const returnOpts = adminCache.routes.map(r => `<option value="${r.id}" ${Number(r.id) === Number(row.returnRouteId) ? 'selected' : ''}>#${r.id} — ${escapeHtml(r.name)}</option>`).join('');
    const goTime = row.departureTime ? String(row.departureTime).slice(0, 5) : '';
    const retTime = row.returnDepartureTime ? String(row.returnDepartureTime).slice(0, 5) : '';
    openActionModal('BUS MANAGEMENT', `Edit Bus #${row.id}`, `
      <form id="modalEditBusForm">
        <label>Bus Number<input name="busNumber" type="text" value="${escapeHtml(row.busNumber)}" required></label>
        <label>Going Route<select name="routeId" required>${goingOpts}</select></label>
        <label>Going Time<input name="departureTime" type="time" value="${escapeHtml(goTime)}" required></label>
        <label>Return Route<select name="returnRouteId" required>${returnOpts}</select></label>
        <label>Return Time<input name="returnDepartureTime" type="time" value="${escapeHtml(retTime)}" required></label>
        <label>Status
          <select name="status" required>
${['ACTIVE', 'INACTIVE', 'MAINTENANCE', ...(row.status === 'ON_TRIP' ? ['ON_TRIP'] : [])].map(s => `
    <option value="${s}" ${row.status === s ? 'selected' : ''} ${s === 'ON_TRIP' ? 'disabled' : ''}>
        ${s === 'ON_TRIP' ? 'ON TRIP (SYSTEM MANAGED)' : s}
    </option>`).join('')}
          </select>
        </label>
        <label>Starting Latitude<input name="currentLatitude" type="number" step="any" value="${row.currentLatitude ?? 22.5726}" required></label>
        <label>Starting Longitude<input name="currentLongitude" type="number" step="any" value="${row.currentLongitude ?? 88.3639}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Changes</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditBusForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        const payload = {
          busNumber: String(fd.get('busNumber') || '').trim(),
          routeId: Number(fd.get('routeId')),
          departureTime: fd.get('departureTime'),
          returnRouteId: Number(fd.get('returnRouteId')),
          returnDepartureTime: fd.get('returnDepartureTime'),
          status: row.status === 'ON_TRIP' ? 'ON_TRIP' : fd.get('status'),
          currentLatitude: Number(fd.get('currentLatitude')),
          currentLongitude: Number(fd.get('currentLongitude'))
        };
        try {
          await api(`/api/buses/${row.id}`, { method: 'PUT', body: JSON.stringify(payload) });
          closeActionModal();
          toast('Bus updated successfully.');
          await renderAdminResource('buses');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-bus-gps]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.busGps);
    if (!row) return;
    openActionModal('LIVE TELEMETRY', `Update GPS for ${row.busNumber}`, `
      <p class="hint">Note: The bus must have a RUNNING trip to accept live GPS updates.</p>
      <form id="modalBusGpsForm">
        <label>Latitude (-90 to 90)<input name="latitude" type="number" step="any" min="-90" max="90" value="${row.currentLatitude ?? 22.5726}" required></label>
        <label>Longitude (-180 to 180)<input name="longitude" type="number" step="any" min="-180" max="180" value="${row.currentLongitude ?? 88.3639}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Send Location Update</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalBusGpsForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        try {
          await api(`/api/buses/${row.id}/location`, {
            method: 'PUT',
            body: JSON.stringify({
              latitude: Number(fd.get('latitude')),
              longitude: Number(fd.get('longitude'))
            })
          });
          closeActionModal();
          toast('Bus GPS coordinates updated.');
          await renderAdminResource('buses');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-bus-delete]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.busDelete);
    if (!row) return;
    openActionModal('CONFIRM DELETE', `Delete Bus ${row.busNumber}?`, `
      <p>Are you sure you want to delete bus <strong>${escapeHtml(row.busNumber)}</strong> (#${row.id})?</p>
      <div class="action-row" style="margin-top:16px;">
        <button class="btn btn-danger" type="button" id="confirmDeleteBtn">Delete Bus</button>
        <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
      </div>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#confirmDeleteBtn').addEventListener('click', async () => {
        try {
          await api(`/api/buses/${row.id}`, { method: 'DELETE' });
          closeActionModal();
          toast('Bus deleted.');
          await renderAdminResource('buses');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));
}

/* --- 2. Admin Drivers (Max 2 Preferred Routes, Editable by Admin, No 3rd Priority) --- */
function renderAdminDrivers() {
  const rows = adminCache.drivers;
  const routes = adminCache.routes;

  const buildRouteOpts = (selectedId, allowNone = false) => {
    if (!routes.length) return '<option value="">Create a route first</option>';
    const noneOpt = allowNone ? '<option value="">— None —</option>' : '';
    return noneOpt + routes.map(r =>
      `<option value="${r.id}" ${Number(r.id) === Number(selectedId) ? 'selected' : ''}>#${r.id} — ${escapeHtml(r.name)}</option>`
    ).join('');
  };

  const defaultRoute1 = routes[0]?.id ?? '';
  const defaultRoute2 = routes[1]?.id ?? '';

  $('adminContent').innerHTML = `
    <h3>Drivers Management</h3>
    <form id="createDriverForm" class="form-grid">
      <label>Full Name<input name="name" type="text" placeholder="e.g. Rajesh Sharma" required></label>
      <label>Mobile Number<input name="phone" type="text" pattern="[0-9]{10}" maxlength="10" placeholder="e.g. 9876543210" required></label>
      <label>License Number<input name="licenseNumber" type="text" placeholder="e.g. WB01-2021-88912" required></label>
      <label>Address<input name="address" type="text" placeholder="Driver address" required></label>
      <label>Username<input name="username" type="text" placeholder="e.g. rajesh01" required></label>
      <label>Preferred Route 1<select name="routeId1" required ${routes.length ? '' : 'disabled'}>${buildRouteOpts(defaultRoute1, false)}</select></label>
      <label>Preferred Route 2<select name="routeId2" ${routes.length >= 2 ? 'required' : ''} ${routes.length ? '' : 'disabled'}>${buildRouteOpts(defaultRoute2, routes.length < 2)}</select></label>
      <button class="btn btn-primary" type="submit" ${routes.length ? '' : 'disabled'}>+ Create Driver</button>
    </form>
    <div style="overflow-x:auto;margin-top:16px;">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>FULL NAME</th><th>USERNAME</th><th>MOBILE NUMBER</th><th>LICENSE</th><th>STATUS</th><th>PREFERRED ROUTES</th><th>ADDRESS</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => {
            const prefLabels = Array.isArray(row.preferredRouteIds) && row.preferredRouteIds.length
              ? row.preferredRouteIds.slice(0, 2).map(id => getRouteLabel(id)).join(', ')
              : 'None selected';
            return `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(row.name)}</strong></td>
              <td><strong>${escapeHtml(row.username || row.name)}</strong></td>
              <td>${escapeHtml(row.phone)}</td>
              <td>${escapeHtml(row.licenseNumber)}</td>
              <td>${statusPill(row.status)}</td>
              <td>${escapeHtml(prefLabels)}</td>
              <td>${escapeHtml(row.address)}</td>
              <td>
                <div class="action-row">
                  <button class="btn btn-light small-btn" type="button" data-driver-routes="${row.id}">Preferred Routes</button>
                  <button class="btn btn-light small-btn" type="button" data-driver-status="${row.id}" data-current="${row.status}">
                    ${row.status === 'AVAILABLE' ? 'Set unavailable' : 'Set available'}
                  </button>
                  <button class="btn btn-light small-btn" type="button" data-driver-edit="${row.id}">Edit</button>
                  <button class="btn btn-danger small-btn" type="button" data-driver-delete="${row.id}">Delete</button>
                </div>
              </td>
            </tr>`;
          }).join('') || '<tr><td colspan="9">No drivers registered yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  $('createDriverForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const name = String(fd.get('name') || '').trim();
    const username = String(fd.get('username') || '').trim();
    const phone = String(fd.get('phone') || '').trim();
    const licenseNumber = String(fd.get('licenseNumber') || '').trim();
    const address = String(fd.get('address') || '').trim();
    const r1 = Number(fd.get('routeId1'));
    const r2 = Number(fd.get('routeId2'));

    if (!name || !username || !phone || !licenseNumber || !address || !r1) {
      return toast('All driver fields including Preferred Routes are mandatory.');
    }
    if (routes.length >= 2 && r1 === r2) {
      return toast('Please select two different Preferred Routes for the driver.');
    }

    const routeIds = [r1, r2].filter((v, idx, arr) => Number.isFinite(v) && v > 0 && arr.indexOf(v) === idx).slice(0, 2);

    const body = {
      name,
      username,
      phone,
      licenseNumber,
      address,
      status: 'AVAILABLE',
      routeIds
    };
    try {
      await api('/api/drivers', { method: 'POST', body: JSON.stringify(body) });
      toast('Driver created successfully.');
      await renderAdminResource('drivers');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-driver-status]').forEach(btn => btn.addEventListener('click', async () => {
    const nextStatus = btn.dataset.current === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE';
    try {
      await api(`/api/drivers/${btn.dataset.driverStatus}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: nextStatus })
      });
      toast(`Driver marked ${nextStatus.toLowerCase()}.`);
      await renderAdminResource('drivers');
    } catch (err) {
      toast(err.message);
    }
  }));

  document.querySelectorAll('[data-driver-routes]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.driverRoutes);
    if (!row) return;
    if (!routes.length) {
      return toast('Create routes in the Routes tab first.');
    }
    const currentIds = (row.preferredRouteIds || []).map(Number);
    const sel1 = currentIds[0] ?? routes[0].id;
    const sel2 = currentIds[1] ?? (routes.find(r => Number(r.id) !== Number(sel1))?.id ?? '');

    openActionModal('DRIVER PREFERRED ROUTES', `Preferred Routes for ${row.name}`, `
      <form id="modalDriverRoutesForm">
        <p class="hint" style="margin-top:0;">Select up to 2 Preferred Routes for this driver:</p>
        <label>Preferred Route 1<select name="pref1" required>${buildRouteOpts(sel1, false)}</select></label>
        <label>Preferred Route 2<select name="pref2" ${routes.length >= 2 ? 'required' : ''}>${buildRouteOpts(sel2, routes.length < 2)}</select></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Preferred Routes</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalDriverRoutesForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        const p1 = Number(fd.get('pref1'));
        const p2 = Number(fd.get('pref2'));
        if (routes.length >= 2 && p1 && p2 && p1 === p2) {
          return toast('Please select two different Preferred Routes.');
        }
        const selected = [p1, p2]
          .filter((v, i, arr) => Number.isFinite(v) && v > 0 && arr.indexOf(v) === i)
          .slice(0, 2);
        if (!selected.length) {
          return toast('Please select Preferred Routes.');
        }
        try {
          await api(`/api/drivers/${row.id}/preferred-routes`, {
            method: 'POST',
            body: JSON.stringify({ routeIds: selected })
          });
          closeActionModal();
          toast('Preferred routes updated.');
          await renderAdminResource('drivers');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-driver-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.driverEdit);
    if (!row) return;
    const currentIds = (row.preferredRouteIds || []).map(Number);
    const cur1 = currentIds[0] ?? (routes[0]?.id ?? '');
    const cur2 = currentIds[1] ?? (routes.find(r => Number(r.id) !== Number(cur1))?.id ?? '');

    openActionModal('DRIVER MANAGEMENT', `Edit Driver #${row.id}`, `
      <form id="modalEditDriverForm">
        <label>Full Name<input name="name" type="text" value="${escapeHtml(row.name)}" required></label>
        <label>Username<input name="username" type="text" value="${escapeHtml(row.username || row.name)}" required></label>
        <label>Mobile Number<input name="phone" type="text" pattern="[0-9]{10}" maxlength="10" value="${escapeHtml(row.phone)}" required></label>
        <label>License Number<input name="licenseNumber" type="text" value="${escapeHtml(row.licenseNumber)}" required></label>
        <label>Address<input name="address" type="text" value="${escapeHtml(row.address)}" required></label>
        <label>Preferred Route 1<select name="routeId1" required>${buildRouteOpts(cur1, false)}</select></label>
        <label>Preferred Route 2<select name="routeId2" ${routes.length >= 2 ? 'required' : ''}>${buildRouteOpts(cur2, routes.length < 2)}</select></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Update Driver</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditDriverForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        const r1 = Number(fd.get('routeId1'));
        const r2 = Number(fd.get('routeId2'));
        if (routes.length >= 2 && r1 && r2 && r1 === r2) {
          return toast('Please select two different Preferred Routes.');
        }
        const routeIds = [r1, r2]
          .filter((v, i, arr) => Number.isFinite(v) && v > 0 && arr.indexOf(v) === i)
          .slice(0, 2);
        const payload = {
          name: String(fd.get('name') || '').trim(),
          username: String(fd.get('username') || '').trim(),
          phone: String(fd.get('phone') || '').trim(),
          licenseNumber: String(fd.get('licenseNumber') || '').trim(),
          address: String(fd.get('address') || '').trim(),
          routeIds: routeIds.length ? routeIds : row.preferredRouteIds
        };
        try {
          await api(`/api/drivers/${row.id}`, { method: 'PUT', body: JSON.stringify(payload) });
          closeActionModal();
          toast('Driver updated successfully.');
          await renderAdminResource('drivers');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-driver-delete]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.driverDelete);
    if (!row) return;
    openActionModal('CONFIRM DELETE', `Delete Driver ${row.name}?`, `
      <p>Are you sure you want to delete driver <strong>${escapeHtml(row.name)}</strong> (${escapeHtml(row.phone)})?</p>
      <div class="action-row" style="margin-top:16px;">
        <button class="btn btn-danger" type="button" id="confirmDeleteDriverBtn">Delete Driver</button>
        <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
      </div>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#confirmDeleteDriverBtn').addEventListener('click', async () => {
        try {
          await api(`/api/drivers/${row.id}`, { method: 'DELETE' });
          closeActionModal();
          toast('Driver deleted.');
          await renderAdminResource('drivers');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));
}

/* --- 3. Admin Routes --- */
function renderAdminRoutes() {
  const rows = adminCache.routes;
  $('adminContent').innerHTML = `
    <h3>Routes Management</h3>
    <form id="createRouteForm" class="form-grid">
      <label>Route Name<input name="name" type="text" placeholder="e.g. R001" required></label>
      <label>Description<input name="description" type="text" placeholder="e.g. Sonarpur Station to New Garia" required></label>
      <button class="btn btn-primary" type="submit">+ Add Route</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>ROUTE NAME</th><th>DESCRIPTION</th><th>ORDERED STOPS</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => {
            const rStops = adminCache['route-stops']
              .filter(rs => Number(rs.routeId) === Number(row.id))
              .sort((a, b) => Number(a.stopOrder) - Number(b.stopOrder))
              .map(rs => `${rs.stopOrder}. ${getStopLabel(rs.stopId).replace(/^#\d+\s—\s/, '')}`)
              .join(' → ');
            return `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(row.name)}</strong></td>
              <td>${escapeHtml(row.description || '—')}</td>
              <td>${escapeHtml(rStops || 'No stops linked yet (use Route Stops tab)')}</td>
              <td>
                <div class="action-row">
                  <button class="btn btn-light small-btn" type="button" data-route-edit="${row.id}">Edit</button>
                  <button class="btn btn-danger small-btn" type="button" data-route-delete="${row.id}">Delete</button>
                </div>
              </td>
            </tr>`;
          }).join('') || '<tr><td colspan="5">No routes created yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  $('createRouteForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const name = String(fd.get('name') || '').trim();
    const description = String(fd.get('description') || '').trim();
    if (!name || !description) {
      return toast('Both Route Name and Description are mandatory.');
    }
    try {
      await api('/api/routes', {
        method: 'POST',
        body: JSON.stringify({ name, description })
      });
      toast('Route created successfully.');
      await renderAdminResource('routes');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-route-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.routeEdit);
    if (!row) return;
    openActionModal('ROUTE MANAGEMENT', `Edit Route #${row.id}`, `
      <form id="modalEditRouteForm">
        <label>Route Name<input name="name" type="text" value="${escapeHtml(row.name)}" required></label>
        <label>Description<input name="description" type="text" value="${escapeHtml(row.description || '')}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Route</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditRouteForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        try {
          await api(`/api/routes/${row.id}`, {
            method: 'PUT',
            body: JSON.stringify({
              name: String(fd.get('name') || '').trim(),
              description: String(fd.get('description') || '').trim()
            })
          });
          closeActionModal();
          toast('Route updated.');
          await renderAdminResource('routes');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-route-delete]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.routeDelete);
    if (!row) return;
    openActionModal('CONFIRM DELETE', `Delete Route ${row.name}?`, `
      <p>Are you sure you want to delete route <strong>${escapeHtml(row.name)}</strong> (#${row.id})?</p>
      <div class="action-row" style="margin-top:16px;">
        <button class="btn btn-danger" type="button" id="confirmDeleteRouteBtn">Delete Route</button>
        <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
      </div>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#confirmDeleteRouteBtn').addEventListener('click', async () => {
        try {
          await api(`/api/routes/${row.id}`, { method: 'DELETE' });
          closeActionModal();
          toast('Route deleted.');
          await renderAdminResource('routes');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));
}

/* --- 4. Admin Stops --- */
function renderAdminStops() {
  const rows = adminCache.stops;
  $('adminContent').innerHTML = `
    <h3>Stops Management</h3>
    <form id="createStopForm" class="form-grid">
      <label>Stop Name<input name="name" type="text" placeholder="e.g. Sonarpur Station" required></label>
      <label>Latitude<input name="latitude" type="number" step="any" min="-90" max="90" placeholder="e.g. 22.4435" required></label>
      <label>Longitude<input name="longitude" type="number" step="any" min="-180" max="180" placeholder="e.g. 88.4312" required></label>
      <button class="btn btn-primary" type="submit">+ Add Stop</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>STOP NAME</th><th>LATITUDE</th><th>LONGITUDE</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(row.name)}</strong></td>
              <td>${escapeHtml(row.latitude)}</td>
              <td>${escapeHtml(row.longitude)}</td>
              <td>
                <div class="action-row">
                  <button class="btn btn-light small-btn" type="button" data-stop-edit="${row.id}">Edit</button>
                  <button class="btn btn-danger small-btn" type="button" data-stop-delete="${row.id}">Delete</button>
                </div>
              </td>
            </tr>`).join('') || '<tr><td colspan="5">No stops created yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  $('createStopForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const name = String(fd.get('name') || '').trim();
    const latStr = String(fd.get('latitude') || '').trim();
    const lonStr = String(fd.get('longitude') || '').trim();
    if (!name || !latStr || !lonStr) {
      return toast('Stop Name, Latitude, and Longitude are all mandatory.');
    }
    try {
      await api('/api/stops', {
        method: 'POST',
        body: JSON.stringify({
          name,
          latitude: Number(latStr),
          longitude: Number(lonStr)
        })
      });
      toast('Stop saved.');
      await renderAdminResource('stops');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-stop-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.stopEdit);
    if (!row) return;
    openActionModal('STOP MANAGEMENT', `Edit Stop #${row.id}`, `
      <form id="modalEditStopForm">
        <label>Stop Name<input name="name" type="text" value="${escapeHtml(row.name)}" required></label>
        <label>Latitude<input name="latitude" type="number" step="any" min="-90" max="90" value="${escapeHtml(row.latitude)}" required></label>
        <label>Longitude<input name="longitude" type="number" step="any" min="-180" max="180" value="${escapeHtml(row.longitude)}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Stop</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditStopForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        try {
          await api(`/api/stops/${row.id}`, {
            method: 'PUT',
            body: JSON.stringify({
              name: String(fd.get('name') || '').trim(),
              latitude: Number(fd.get('latitude')),
              longitude: Number(fd.get('longitude'))
            })
          });
          closeActionModal();
          toast('Stop updated.');
          await renderAdminResource('stops');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-stop-delete]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.stopDelete);
    if (!row) return;
    openActionModal('CONFIRM DELETE', `Delete Stop ${row.name}?`, `
      <p>Are you sure you want to delete stop <strong>${escapeHtml(row.name)}</strong> (#${row.id})?</p>
      <div class="action-row" style="margin-top:16px;">
        <button class="btn btn-danger" type="button" id="confirmDeleteStopBtn">Delete Stop</button>
        <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
      </div>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#confirmDeleteStopBtn').addEventListener('click', async () => {
        try {
          await api(`/api/stops/${row.id}`, { method: 'DELETE' });
          closeActionModal();
          toast('Stop deleted.');
          await renderAdminResource('stops');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));
}

/* --- 5. Admin Route Stops --- */
function renderAdminRouteStops() {
  const rows = [...adminCache['route-stops']].sort((a, b) =>
    Number(a.routeId) - Number(b.routeId) || Number(a.stopOrder) - Number(b.stopOrder)
  );
  const canCreate = adminCache.routes.length > 0 && adminCache.stops.length > 0;
  const routeOpts = adminCache.routes.length
    ? adminCache.routes.map(r => `<option value="${r.id}">#${r.id} — ${escapeHtml(r.name)}</option>`).join('')
    : '<option value="">Create a route first</option>';
  const stopOpts = adminCache.stops.length
    ? adminCache.stops.map(s => `<option value="${s.id}">#${s.id} — ${escapeHtml(s.name)}</option>`).join('')
    : '<option value="">Create a stop first</option>';

  $('adminContent').innerHTML = `
    <h3>Route Stops Management</h3>
    <form id="createRouteStopForm" class="form-grid">
      <label>Route<select name="routeId" id="rsRouteSelect" required ${canCreate ? '' : 'disabled'}>${routeOpts}</select></label>
      <label>Stop<select name="stopId" required ${canCreate ? '' : 'disabled'}>${stopOpts}</select></label>
      <label>Stop Order<input name="stopOrder" id="rsOrderInput" type="number" min="1" step="1" value="1" required ${canCreate ? '' : 'disabled'}></label>
      <button class="btn btn-primary" type="submit" ${canCreate ? '' : 'disabled'}>+ Add Route Stop</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>ROUTE</th><th>STOP</th><th>STOP ORDER</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(getRouteLabel(row.routeId))}</strong></td>
              <td>${escapeHtml(getStopLabel(row.stopId))}</td>
              <td>Order #${escapeHtml(row.stopOrder)}</td>
              <td>
                <div class="action-row">
                  <button class="btn btn-light small-btn" type="button" data-rs-edit="${row.id}">Edit</button>
                  <button class="btn btn-danger small-btn" type="button" data-rs-delete="${row.id}">Delete</button>
                </div>
              </td>
            </tr>`).join('') || '<tr><td colspan="5">No route stops linked yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  const updateSuggestedOrder = () => {
    const rId = Number($('rsRouteSelect')?.value);
    if (!rId || !$('rsOrderInput')) return;
    const maxOrder = rows
      .filter(r => Number(r.routeId) === rId)
      .reduce((m, r) => Math.max(m, Number(r.stopOrder) || 0), 0);
    $('rsOrderInput').value = String(maxOrder + 1);
  };
  updateSuggestedOrder();
  $('rsRouteSelect')?.addEventListener('change', updateSuggestedOrder);

  $('createRouteStopForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const routeId = Number(fd.get('routeId'));
    const stopId = Number(fd.get('stopId'));
    const stopOrder = Number(fd.get('stopOrder'));
    if (!routeId || !stopId || !stopOrder) {
      return toast('Route, Stop, and Stop Order are all mandatory.');
    }
    try {
      await api('/api/route-stops', {
        method: 'POST',
        body: JSON.stringify({ routeId, stopId, stopOrder })
      });
      toast('Route stop added.');
      await renderAdminResource('route-stops');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-rs-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.rsEdit);
    if (!row) return;
    const rOpts = adminCache.routes.map(r => `<option value="${r.id}" ${Number(r.id) === Number(row.routeId) ? 'selected' : ''}>#${r.id} — ${escapeHtml(r.name)}</option>`).join('');
    const sOpts = adminCache.stops.map(s => `<option value="${s.id}" ${Number(s.id) === Number(row.stopId) ? 'selected' : ''}>#${s.id} — ${escapeHtml(s.name)}</option>`).join('');
    openActionModal('ROUTE STOP MANAGEMENT', `Edit Route Stop #${row.id}`, `
      <form id="modalEditRsForm">
        <label>Route<select name="routeId" required>${rOpts}</select></label>
        <label>Stop<select name="stopId" required>${sOpts}</select></label>
        <label>Stop Order<input name="stopOrder" type="number" min="1" step="1" value="${escapeHtml(row.stopOrder)}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Changes</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditRsForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        try {
          await api(`/api/route-stops/${row.id}`, {
            method: 'PUT',
            body: JSON.stringify({
              routeId: Number(fd.get('routeId')),
              stopId: Number(fd.get('stopId')),
              stopOrder: Number(fd.get('stopOrder'))
            })
          });
          closeActionModal();
          toast('Route stop updated.');
          await renderAdminResource('route-stops');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-rs-delete]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.rsDelete);
    if (!row) return;
    openActionModal('CONFIRM DELETE', `Delete Route Stop #${row.id}?`, `
      <p>Remove <strong>${escapeHtml(getStopLabel(row.stopId))}</strong> (Order #${escapeHtml(row.stopOrder)}) from <strong>${escapeHtml(getRouteLabel(row.routeId))}</strong>?</p>
      <div class="action-row" style="margin-top:16px;">
        <button class="btn btn-danger" type="button" id="confirmDeleteRsBtn">Delete Route Stop</button>
        <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
      </div>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#confirmDeleteRsBtn').addEventListener('click', async () => {
        try {
          await api(`/api/route-stops/${row.id}`, { method: 'DELETE' });
          closeActionModal();
          toast('Route stop deleted.');
          await renderAdminResource('route-stops');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));
}

/* --- 6. Admin Trips --- */
function renderAdminTrips() {
  const rows = [...adminCache.trips].sort((a, b) => Number(b.id) - Number(a.id));
  const activeBuses = adminCache.buses.filter(b => b.status === 'ACTIVE');
  const canCreate = activeBuses.length > 0 && adminCache.routes.length > 0;
  const busOpts = activeBuses.length
    ? activeBuses.map(b => `<option value="${b.id}" data-route-id="${b.routeId ?? ''}">#${b.id} — ${escapeHtml(b.busNumber)} (Route: ${escapeHtml(getRouteLabel(b.routeId))})</option>`).join('')
    : '<option value="">No ACTIVE buses available</option>';
  const routeOpts = adminCache.routes.length
    ? adminCache.routes.map(r => `<option value="${r.id}">#${r.id} — ${escapeHtml(r.name)}</option>`).join('')
    : '<option value="">Create a route first</option>';
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

  $('adminContent').innerHTML = `
    <h3>Trips Management &amp; Driver Assignment</h3>
    <form id="createTripForm" class="form-grid">
      <label>Select Bus<select name="busId" id="tripBusSelect" required ${canCreate ? '' : 'disabled'}>${busOpts}</select></label>
      <label>Matching Route<select name="routeId" id="tripRouteSelect" required ${canCreate ? '' : 'disabled'}>${routeOpts}</select></label>
      <label>Available Driver<select name="driverId" id="tripDriverSelect" required ${canCreate ? '' : 'disabled'}></select></label>
      <label>Scheduled Date<input type="date" name="tripDate" min="${today}" value="${today}" required ${canCreate ? '' : 'disabled'}></label>
      <label>Scheduled Time<input type="time" name="scheduledTime" required ${canCreate ? '' : 'disabled'}></label>
      <button class="btn btn-primary" type="submit" id="tripSubmitBtn" ${canCreate ? '' : 'disabled'}>+ Create &amp; Assign Trip</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>BUS</th><th>ROUTE</th><th>ASSIGNED DRIVER</th><th>STATUS</th><th>SCHEDULED (DATE &amp; TIME)</th><th>STARTED AT (DATE &amp; TIME)</th><th>ENDED AT (DATE &amp; TIME)</th><th>COMPLETION &amp; EMERGENCY ABORT DETAILS</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => {
            const isEmergency = Boolean(row.emergencyAbort || (row.abortReason && row.abortReason.trim()));
            const statusHtml = isEmergency
              ? `${statusPill('COMPLETED')} ${statusPill('EMERGENCY ABORT')}`
              : statusPill(row.status);
            const detailsHtml = isEmergency
              ? `<strong style="color:#b42318;">Emergency Abort:</strong> ${escapeHtml(row.abortReason)}`
              : (row.status === 'COMPLETED' ? '<span style="color:#148477;font-weight:700;">✓ Normal Trip Completion</span>' : 'In Progress / Scheduled');
            return `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(getBusLabel(row.busId))}</strong></td>
              <td>${escapeHtml(getRouteLabel(row.routeId))}</td>
              <td><strong>${escapeHtml(row.driverName ? `#${row.driverId} — ${row.driverName}` : getDriverLabel(row.driverId))}</strong></td>
              <td>${statusHtml}</td>
              <td>${escapeHtml(`${row.date || '—'} · ${row.scheduledTime || '—'}`)}</td>
              <td>${escapeHtml(formatDateTimeRecord(row.startedAtDateTime, row.date, row.startedAt))}</td>
              <td>${escapeHtml(formatDateTimeRecord(row.endedAtDateTime, row.date, row.endedAt))}</td>
              <td>${detailsHtml}</td>
              <td>
                <div class="action-row">
                  ${row.status === 'ACTIVE' ? `
                    <button class="btn btn-light small-btn" type="button" data-trip-edit="${row.id}">Edit</button>
                    <button class="btn btn-danger small-btn" type="button" data-trip-delete="${row.id}">Delete</button>
                  ` : '—'}
                </div>
              </td>
            </tr>`;
          }).join('') || '<tr><td colspan="10">No trips created yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  const busyDriverIds = new Set(
    adminCache.trips
      .filter(t => t.status === 'ACTIVE' || t.status === 'RUNNING')
      .map(t => Number(t.driverId))
  );

  const populateAvailableDriversForRoute = () => {
    const rId = Number($('tripRouteSelect')?.value);
    const driverSelect = $('tripDriverSelect');
    const submitBtn = $('tripSubmitBtn');
    if (!driverSelect) return;

    const matchingDrivers = adminCache.drivers.filter(d =>
      d.status === 'AVAILABLE' &&
      !busyDriverIds.has(Number(d.id)) &&
      Array.isArray(d.preferredRouteIds) &&
      d.preferredRouteIds.map(Number).includes(rId)
    );

    if (!matchingDrivers.length) {
      driverSelect.innerHTML = `<option value="">No AVAILABLE driver for Route #${rId || ''}</option>`;
      if (submitBtn) submitBtn.disabled = true;
    } else {
      driverSelect.innerHTML = matchingDrivers
        .map(d => `<option value="${d.id}">#${d.id} — ${escapeHtml(d.name)} (${escapeHtml(d.phone)})</option>`)
        .join('');
      if (submitBtn) submitBtn.disabled = !canCreate;
    }
  };

  const syncTripRouteAndDrivers = () => {
    const selectedOpt = $('tripBusSelect')?.selectedOptions?.[0];
    if (selectedOpt && selectedOpt.dataset.routeId && $('tripRouteSelect')) {
      $('tripRouteSelect').value = selectedOpt.dataset.routeId;
    }
    populateAvailableDriversForRoute();
  };

  syncTripRouteAndDrivers();
  $('tripBusSelect')?.addEventListener('change', syncTripRouteAndDrivers);
  $('tripRouteSelect')?.addEventListener('change', populateAvailableDriversForRoute);

  $('createTripForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const busId = Number(fd.get('busId'));
    const routeId = Number(fd.get('routeId'));
    const driverId = Number(fd.get('driverId'));
    const tripDate = fd.get('tripDate');
    const scheduledTime = fd.get('scheduledTime');

    if (!busId || !routeId || !driverId || !tripDate || !scheduledTime) {
      return toast('All trip fields including Driver Selection are mandatory.');
    }

    try {
      await api('/api/trips', {
        method: 'POST',
        body: JSON.stringify({
          busId,
          routeId,
          driverId,
          tripDate,
          scheduledTime
        })
      });
      toast('Trip scheduled and assigned to the selected driver.');
      await renderAdminResource('trips');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-trip-edit]').forEach(btn => btn.addEventListener('click', () => {
    const row = rows.find(x => String(x.id) === btn.dataset.tripEdit);
    if (!row) return;
    const rId = Number(row.routeId);
    const eligibleDrivers = adminCache.drivers.filter(d => {
      const isCurrent = Number(d.id) === Number(row.driverId);
      const isFree = d.status === 'AVAILABLE' && !busyDriverIds.has(Number(d.id));
      const matchesRoute = Array.isArray(d.preferredRouteIds) && d.preferredRouteIds.map(Number).includes(rId);
      return isCurrent || (isFree && matchesRoute);
    });
    const driverOpts = eligibleDrivers.map(d =>
      `<option value="${d.id}" ${Number(d.id) === Number(row.driverId) ? 'selected' : ''}>#${d.id} — ${escapeHtml(d.name)} (${escapeHtml(d.phone)})${Number(d.id) === Number(row.driverId) ? ' (Current)' : ''}</option>`
    ).join('');
    const schedTime = row.scheduledTime ? String(row.scheduledTime).slice(0, 5) : '';

    openActionModal('TRIP MANAGEMENT', `Edit Trip #${row.id}`, `
      <form id="modalEditTripForm">
        <p class="hint" style="margin-top:0;">Bus: <strong>${escapeHtml(getBusLabel(row.busId))}</strong> · Route: <strong>${escapeHtml(getRouteLabel(row.routeId))}</strong></p>
        <label>Reassign Driver<select name="driverId" required>${driverOpts}</select></label>
        <label>Scheduled Date<input name="tripDate" type="date" value="${escapeHtml(row.date || today)}" required></label>
        <label>Scheduled Time<input name="scheduledTime" type="time" value="${escapeHtml(schedTime)}" required></label>
        <div class="action-row" style="margin-top:16px;">
          <button class="btn btn-primary" type="submit">Save Changes</button>
          <button class="btn btn-outline" type="button" data-modal-cancel>Cancel</button>
        </div>
      </form>`, container => {
      container.querySelector('[data-modal-cancel]').addEventListener('click', closeActionModal);
      container.querySelector('#modalEditTripForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const fd = new FormData(ev.currentTarget);
        try {
          await api(`/api/trips/${row.id}`, {
            method: 'PUT',
            body: JSON.stringify({
              busId: Number(row.busId),
              routeId: Number(row.routeId),
              driverId: Number(fd.get('driverId')),
              tripDate: fd.get('tripDate'),
              scheduledTime: fd.get('scheduledTime')
            })
          });
          closeActionModal();
          toast('Trip updated successfully.');
          await renderAdminResource('trips');
        } catch (err) {
          toast(err.message);
        }
      });
    });
  }));

  document.querySelectorAll('[data-trip-delete]').forEach(btn => btn.addEventListener('click', async () => {
    try {
      await api(`/api/trips/${btn.dataset.tripDelete}`, { method: 'DELETE' });
      toast('Trip deleted.');
      await renderAdminResource('trips');
    } catch (err) {
      toast(err.message);
    }
  }));
}

/* --- 7. Admin Bus Stop History --- */
function renderAdminStopHistory() {
  const rows = [...adminCache['bus-stop-history']].sort((a, b) => Number(b.id) - Number(a.id));
  $('adminContent').innerHTML = `
    <h3>Bus Stop Arrival &amp; Departure History</h3>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>BUS</th><th>STOP</th><th>TRIP ID</th><th>ARRIVAL (DATE &amp; TIME)</th><th>DEPARTURE (DATE &amp; TIME)</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(getBusLabel(row.busId))}</strong></td>
              <td>${escapeHtml(row.stopName || getStopLabel(row.stopId))}</td>
              <td>Trip #${escapeHtml(row.tripId ?? '—')}</td>
              <td>${escapeHtml(row.arrivalTime ? new Date(row.arrivalTime).toLocaleString() : '—')}</td>
              <td>${escapeHtml(row.departureTime ? new Date(row.departureTime).toLocaleString() : 'At stop / Pending')}</td>
              <td>
                <button class="btn btn-danger small-btn" type="button" data-history-delete="${row.id}">Delete</button>
              </td>
            </tr>`).join('') || '<tr><td colspan="7">No stop arrival/departure history recorded yet.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  document.querySelectorAll('[data-history-delete]').forEach(btn => btn.addEventListener('click', async () => {
    try {
      await api(`/api/bus-stop-history/${btn.dataset.historyDelete}`, { method: 'DELETE' });
      toast('Stop history record deleted.');
      await renderAdminResource('bus-stop-history');
    } catch (err) {
      toast(err.message);
    }
  }));
}

/* --- 8. Admin Accounts Management (Point 1: Same-Level Alignment, Mobile Number as Initial Password, No Separate Password Input) --- */
function renderAdminAdmins() {
  const rows = adminCache.admins;
  $('adminContent').innerHTML = `
    <h3>Admin Accounts Management</h3>
    <form id="createAdminAccountForm" class="form-grid">
      <label>Admin Name<input name="name" type="text" placeholder="e.g. Subhajit Sen" required></label>
      <label>Admin Username<input name="username" type="text" minlength="3" placeholder="e.g. admin_subhajit" required></label>
      <label>Mobile Number<input name="phone" type="text" pattern="[0-9]{10}" maxlength="10" placeholder="e.g. 9876501234" required></label>
      <button class="btn btn-primary" type="submit">+ Create Admin</button>
    </form>
    <div style="overflow-x:auto">
      <table class="data-table">
        <thead>
          <tr><th>ID</th><th>ADMIN NAME</th><th>ADMIN USERNAME</th><th>MOBILE NUMBER (INITIAL PASSWORD)</th><th>ROLE</th><th>ACTION</th></tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              <td>#${escapeHtml(row.id)}</td>
              <td><strong>${escapeHtml(row.name)}</strong></td>
              <td><strong>${escapeHtml(row.username)}</strong></td>
              <td>${escapeHtml(row.phone)}</td>
              <td>${statusPill('ADMIN')}</td>
              <td>
                ${Number(row.id) === 0
                  ? '<span class="muted">System Primary</span>'
                  : `<button class="btn btn-danger small-btn" type="button" data-admin-delete="${row.id}">Delete</button>`}
              </td>
            </tr>`).join('') || '<tr><td colspan="6">No admin accounts found.</td></tr>'}
        </tbody>
      </table>
    </div>
    <p class="hint">The Admin's 10-digit Mobile Number is automatically used as their login password.</p>`;

  $('createAdminAccountForm').addEventListener('submit', async e => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const name = String(fd.get('name') || '').trim();
    const username = String(fd.get('username') || '').trim();
    const phone = String(fd.get('phone') || '').trim();
    if (!name || !username || !phone) {
      return toast('Admin Name, Admin Username, and Mobile Number are all mandatory.');
    }
    try {
      await api('/api/admin/accounts', {
        method: 'POST',
        body: JSON.stringify({ name, username, phone })
      });
      toast('New Admin account created successfully.');
      await renderAdminResource('admins');
    } catch (err) {
      toast(err.message);
    }
  });

  document.querySelectorAll('[data-admin-delete]').forEach(btn => btn.addEventListener('click', async () => {
    try {
      await api(`/api/admin/accounts/${btn.dataset.adminDelete}`, { method: 'DELETE' });
      toast('Admin account deleted.');
      await renderAdminResource('admins');
    } catch (err) {
      toast(err.message);
    }
  }));
}

/* ==================== DRIVER WORKSPACE ==================== */

function getActiveDriverCoordinates() {
  if (lastDriverGpsCoords && Number.isFinite(lastDriverGpsCoords.latitude) && Number.isFinite(lastDriverGpsCoords.longitude)) {
    return { ...lastDriverGpsCoords };
  }
  const latInput = $('manualLat');
  const lonInput = $('manualLon');
  if (latInput && lonInput && latInput.value !== '' && lonInput.value !== '') {
    const lat = Number(latInput.value);
    const lon = Number(lonInput.value);
    if (Number.isFinite(lat) && Number.isFinite(lon)) {
      return { latitude: lat, longitude: lon };
    }
  }
  return null;
}

async function renderDriver() {
  $('portalEyebrow').textContent = 'DRIVER WORKSPACE';
  $('portalTitle').textContent = 'Your shift';
  $('driverPanel').classList.remove('hidden');
  $('adminPanel').classList.add('hidden');

  const [me, trips] = await Promise.all([
    api('/api/driver/me'),
    api('/api/driver/trips')
  ]);
  const assigned = trips.filter(t => t.status === 'ACTIVE' || t.status === 'RUNNING');
  const runningTrip = assigned.find(t => t.status === 'RUNNING');
  const primaryTrip = runningTrip || assigned[0] || null;
  const routeStops = primaryTrip
      ? await api(`/api/passenger/routes/${primaryTrip.routeId}/stops`, {}, false).catch(() => [])
      : [];
  const prefRouteText = Array.isArray(me.preferredRouteIds) && me.preferredRouteIds.length
    ? me.preferredRouteIds.map(id => `#${id}`).join(', ')
    : 'Assigned by Admin';

  $('driverPanel').innerHTML = `
    <div class="panel-card">
      <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;">
        <div>
          <h3 style="margin:0 0 6px;">Hello, ${escapeHtml(me.name)}</h3>
          <p class="muted" style="margin:0;">Username: <strong>${escapeHtml(me.username || me.name)}</strong> · Mobile: ${escapeHtml(me.phone)} · License: ${escapeHtml(me.licenseNumber)} · Preferred Routes: ${escapeHtml(prefRouteText)}</p>
        </div>
        <div class="action-row">
          ${statusPill(me.status || 'UNAVAILABLE')}
          <button id="driverToggleStatusBtn" class="btn btn-light small-btn" type="button" ${assigned.length ? 'disabled' : ''}>
            ${assigned.length ? 'Trip Assigned' : `Mark as ${me.status === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE'}`}
          </button>
          <button id="driverRefreshBtn" class="btn btn-outline small-btn" type="button">↻ Refresh</button>
        </div>
      </div>
    </div>

    <div class="panel-card">
      <h3>Driver Position &amp; Live GPS Location Sharing</h3>
      <p class="muted">Turn on <strong>Start Live Browser GPS</strong> (or enter manual GPS coordinates below) before starting your assigned trip so your proximity to the bus (15–30 m) can be verified.</p>
      <div class="action-row" style="margin-bottom:10px;">
        <button id="liveGpsStart" class="btn btn-primary" type="button" ${primaryTrip && !isLiveBrowserGpsActive ? '' : 'disabled'}>📍 Start Live Browser GPS</button>
        <button id="liveGpsStop" class="btn btn-outline" type="button" ${isLiveBrowserGpsActive ? '' : 'disabled'}>Stop Live GPS</button>
      </div>
      <form id="manualGpsForm" class="form-grid" style="margin-top:12px;">
        <label>Manual Stoppage (If GPS fails)
          <select id="manualStopSelect" required ${primaryTrip && routeStops.length ? '' : 'disabled'}>
            <option value="">-- Select Current Stoppage --</option>
            ${routeStops.map((s, idx) => `<option value="${idx}">${idx + 1}. ${escapeHtml(s.name)}</option>`).join('')}
          </select>
        </label>
        <button class="btn btn-light" type="submit" ${primaryTrip && routeStops.length ? '' : 'disabled'}>📍 Update Selected Stoppage</button>
      </form>
      <p id="simStatus" class="hint">${isLiveBrowserGpsActive && lastDriverGpsCoords ? `● Live GPS ON: ${lastDriverGpsCoords.latitude.toFixed(6)}, ${lastDriverGpsCoords.longitude.toFixed(6)}` : 'Click "Start Live Browser GPS" to activate your live location before starting the trip.'}</p>
    </div>

    <div class="panel-card">
      <h3>Assigned Active &amp; Running Trips</h3>
      ${assigned.length ? assigned.map(t => `
        <div class="driver-trip" style="flex-direction:column;align-items:stretch;">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;">
            <div>
              <strong>Trip #${escapeHtml(t.id)} · Bus ${escapeHtml(t.busNumber || '#' + t.busId)} · Route ${escapeHtml(t.routeName || '#' + t.routeId)}</strong>
              <p>Status: <strong>${escapeHtml(t.status)}</strong> · Scheduled Date &amp; Time: <strong>${escapeHtml(t.date || '—')} at ${escapeHtml(t.scheduledTime || '—')}</strong> ${t.startedAtDateTime || t.startedAt ? `· Started: ${escapeHtml(formatDateTimeRecord(t.startedAtDateTime, t.date, t.startedAt))}` : ''}</p>
              <p class="muted" style="margin:2px 0 0;">Assigned Bus Location: <strong>${t.busLatitude != null && t.busLongitude != null ? `${Number(t.busLatitude).toFixed(5)}, ${Number(t.busLongitude).toFixed(5)}` : 'Route First Stop'}</strong> · Required: Live GPS ON &amp; within 15–30 m of bus at/after scheduled time.</p>
            </div>
            <div class="action-row">
              ${t.status === 'ACTIVE' ? `<button class="btn btn-primary small-btn" type="button" data-start="${t.id}">▶ Start Trip</button>` : ''}
              ${t.status === 'RUNNING' ? `<button class="btn btn-outline small-btn" type="button" data-complete="${t.id}">■ Complete Trip</button>` : ''}
              <button class="btn btn-danger small-btn" type="button" data-abort-toggle="${t.id}">⚠️ Emergency Abort</button>
            </div>
          </div>

          <!-- Inline Slide-Down Emergency Abort Box -->
          <div id="abort-box-${t.id}" class="emergency-abort-box hidden">
            <h4>⚠️ Emergency Trip Abort &amp; Completion</h4>
            <p class="hint" style="margin:0 0 8px;">Select the incident type and write the detailed reason below. Once confirmed, this trip will be marked as <strong>COMPLETED (Emergency Abort)</strong> and recorded in Trip Details.</p>
            <div class="form-grid">
              <label>Emergency Category
                <select id="abort-type-${t.id}" required>
                  <option value="Mechanical / Engine Breakdown">Mechanical / Engine Breakdown</option>
                  <option value="Tyre Puncture">Tyre Puncture</option>
                  <option value="Road Accident / Incident">Road Accident / Incident (Describe below)</option>
                  <option value="Severe Traffic Jam / Road Block">Severe Traffic Jam / Road Block</option>
                  <option value="Medical / Passenger Emergency">Medical / Passenger Emergency</option>
                  <option value="Other Emergency">Other Emergency</option>
                </select>
              </label>
              <label>Detailed Abort Reason
                <input type="text" id="abort-reason-${t.id}" placeholder="Write exact details of what happened (mandatory)..." required>
              </label>
            </div>
            <label class="checkbox-item" style="margin:8px 0 12px;">
              <input type="checkbox" id="abort-confirm-check-${t.id}">
              <span>I confirm this Emergency Abort and want to complete/close this trip immediately.</span>
            </label>
            <div class="action-row">
              <button type="button" class="btn btn-danger small-btn" data-abort-submit="${t.id}">✓ Confirm Emergency Abort &amp; Complete Trip</button>
              <button type="button" class="btn btn-outline small-btn" data-abort-cancel="${t.id}">Cancel</button>
            </div>
          </div>
        </div>`).join('') : '<p class="muted">No active or running trip assigned right now. Ensure your status is AVAILABLE so the administrator can assign you a trip.</p>'}
    </div>

    <div class="panel-card">
      <h3>Trip History &amp; Completion Details</h3>
      ${trips.length ? trips.map(t => {
        const isEmergency = Boolean(t.emergencyAbort || (t.abortReason && t.abortReason.trim()));
        return `
        <div class="driver-trip">
          <div>
            <strong>Trip #${escapeHtml(t.id)} · Bus ${escapeHtml(t.busNumber || '#' + t.busId)} · Route ${escapeHtml(t.routeName || '#' + t.routeId)}</strong>
            <p>Scheduled: ${escapeHtml(t.date || '—')} ${escapeHtml(t.scheduledTime || '—')} · Started: <strong>${escapeHtml(formatDateTimeRecord(t.startedAtDateTime, t.date, t.startedAt))}</strong> · Ended: <strong>${escapeHtml(formatDateTimeRecord(t.endedAtDateTime, t.date, t.endedAt))}</strong></p>
            ${isEmergency ? `<p style="color:#b42318;font-weight:700;margin:3px 0 0;">⚠️ Emergency Abort Reason: ${escapeHtml(t.abortReason)}</p>` : ''}
          </div>
          <div class="action-row">
            ${statusPill(t.status)}
            ${isEmergency ? statusPill('EMERGENCY ABORT') : ''}
          </div>
        </div>`;
      }).join('') : '<p class="muted">No trip history yet.</p>'}
    </div>`;

  $('driverRefreshBtn')?.addEventListener('click', () => renderDriver().catch(e => toast(e.message)));

  $('driverToggleStatusBtn')?.addEventListener('click', async () => {
    const nextStatus = me.status === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE';
    try {
      await api('/api/driver/status', {
        method: 'PATCH',
        body: JSON.stringify({ status: nextStatus })
      });
      toast(`Your status is now ${nextStatus}.`);
      await renderDriver();
    } catch (e) {
      toast(e.message);
    }
  });

  document.querySelectorAll('[data-start]').forEach(b => b.addEventListener('click', async () => {
    const coords = getActiveDriverCoordinates();
    if (!coords) {
      return toast('Please turn on "Start Live Browser GPS" first so your live location can be verified before starting the trip.');
    }
    b.disabled = true;
    try {
      await api(`/api/driver/trips/${b.dataset.start}/start`, {
        method: 'PUT',
        body: JSON.stringify(coords)
      });
      toast('Trip started successfully!');
      await renderDriver();
    } catch (e) {
      b.disabled = false;
      toast(e.message);
    }
  }));

  document.querySelectorAll('[data-complete]').forEach(b => b.addEventListener('click', async () => {
    const coords = getActiveDriverCoordinates();
    b.disabled = true;
    try {
      stopLiveGps();
      await api(`/api/driver/trips/${b.dataset.complete}/complete`, {
        method: 'PUT',
        body: JSON.stringify(coords || {})
      });
      toast('Trip completed successfully.');
      await renderDriver();
    } catch (e) {
      b.disabled = false;
      toast(e.message);
    }
  }));

  document.querySelectorAll('[data-abort-toggle]').forEach(b => b.addEventListener('click', () => {
    const box = $(`abort-box-${b.dataset.abortToggle}`);
    if (box) {
      box.classList.toggle('hidden');
      if (!box.classList.contains('hidden')) {
        $(`abort-reason-${b.dataset.abortToggle}`)?.focus();
      }
    }
  }));

  document.querySelectorAll('[data-abort-cancel]').forEach(b => b.addEventListener('click', () => {
    const box = $(`abort-box-${b.dataset.abortCancel}`);
    if (box) box.classList.add('hidden');
  }));

  document.querySelectorAll('[data-abort-submit]').forEach(b => b.addEventListener('click', async () => {
    const tripId = b.dataset.abortSubmit;
    const trip = assigned.find(x => String(x.id) === String(tripId));
    const category = $(`abort-type-${tripId}`)?.value || 'Emergency';
    const detail = String($(`abort-reason-${tripId}`)?.value || '').trim();
    const confirmed = Boolean($(`abort-confirm-check-${tripId}`)?.checked);

    if (!detail) {
      return toast('Abort Reason is mandatory. Please write why you are aborting the trip.');
    }
    if (!confirmed) {
      return toast('Please tick the confirmation checkbox to confirm Emergency Abort.');
    }

    b.disabled = true;
    try {
      stopLiveGps();
      const fullReason = `${category}: ${detail}`;
      await api(`/api/driver/trips/${tripId}/abort`, {
        method: 'PUT',
        body: JSON.stringify({ reason: fullReason })
      });
      toast('Emergency Abort confirmed. Trip has been marked as COMPLETED.');
      await renderDriver();
      if (trip && Number(selectedBus) === Number(trip.busId)) {
        refreshTracking(false);
      }
    } catch (err) {
      b.disabled = false;
      toast(err.message);
    }
  }));

  $('liveGpsStart')?.addEventListener('click', startLiveGps);
  $('liveGpsStop')?.addEventListener('click', stopLiveGps);

  $('manualGpsForm')?.addEventListener('submit', async e => {
    e.preventDefault();
    stopLiveGps();
    const idx = $('manualStopSelect')?.value;
    if (idx === '' || !routeStops[Number(idx)]) {
      return toast('Please select a stoppage from the dropdown.');
    }
    const stop = routeStops[Number(idx)];
    const latitude = Number(stop.latitude);
    const longitude = Number(stop.longitude);

    lastDriverGpsCoords = { latitude, longitude };
    isLiveBrowserGpsActive = true;

    if (!runningTrip) {
      if ($('simStatus')) {
        $('simStatus').textContent = `● Manual stoppage selected: ${stop.name}. You can now click Start Trip.`;
      }
      toast(`Position set to ${stop.name}. Ready to start trip.`);
      return;
    }

    try {
      const updated = await api('/api/driver/location', {
        method: 'PUT',
        body: JSON.stringify({ latitude, longitude })
      });
      if ($('simStatus')) {
        $('simStatus').textContent = `● Manual location updated to: ${stop.name} (${new Date().toLocaleTimeString()})`;
      }
      toast(`Location updated to ${stop.name}.`);
      if (Number(selectedBus) === Number(updated.id)) refreshTracking(false);
    } catch (err) {
      toast(err.message);
    }
  });
}

function startLiveGps() {
  if (!navigator.geolocation) {
    return toast('Geolocation is not supported by this browser.');
  }
  if (driverWatchId !== null) navigator.geolocation.clearWatch(driverWatchId);
  isLiveBrowserGpsActive = true;
  if ($('liveGpsStart')) $('liveGpsStart').disabled = true;
  if ($('liveGpsStop')) $('liveGpsStop').disabled = false;
  if ($('simStatus')) $('simStatus').textContent = 'Requesting a fresh, high-accuracy device GPS fix…';

  const sendLatestFix = async () => {
    if (driverSendInFlight || !pendingDriverFix) return;
    const fix = pendingDriverFix;
    pendingDriverFix = null;
    lastDriverGpsCoords = { latitude: fix.latitude, longitude: fix.longitude };

    driverSendInFlight = true;
    try {
      const location = await api('/api/driver/location', {
        method: 'PUT',
        body: JSON.stringify({ latitude: fix.latitude, longitude: fix.longitude })
      });
      if ($('simStatus')) {
        $('simStatus').textContent =
          `● Live device GPS active: ${Number(location.currentLatitude).toFixed(6)}, ${Number(location.currentLongitude).toFixed(6)} · accuracy ±${Math.round(fix.accuracy)} m · ${new Date().toLocaleTimeString()}`;
      }
    } catch (_) {
      if ($('simStatus')) {
        $('simStatus').textContent =
          `● Live device GPS acquired: ${fix.latitude.toFixed(6)}, ${fix.longitude.toFixed(6)} (±${Math.round(fix.accuracy)} m). Ready to verify proximity & start trip.`;
      }
    } finally {
      driverSendInFlight = false;
      if (pendingDriverFix) sendLatestFix();
    }
  };

  driverWatchId = navigator.geolocation.watchPosition(
    pos => {
      pendingDriverFix = {
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracy: Number.isFinite(pos.coords.accuracy) ? pos.coords.accuracy : 0,
        capturedAt: pos.timestamp
      };
      sendLatestFix();
    },
    err => {
      const reason = err.code === 1 ? 'Location permission denied'
        : err.code === 2 ? 'Device location is unavailable'
        : 'GPS request timed out';
      if ($('simStatus')) $('simStatus').textContent = `${reason}. Enable Location permission or enter Manual GPS coordinates.`;
      toast(reason);
      stopLiveGps();
    },
    { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 }
  );
}

function stopLiveGps() {
  pendingDriverFix = null;
  isLiveBrowserGpsActive = false;
  if (driverWatchId !== null && navigator.geolocation) {
    navigator.geolocation.clearWatch(driverWatchId);
    driverWatchId = null;
  }
  if ($('liveGpsStart')) $('liveGpsStart').disabled = false;
  if ($('liveGpsStop')) $('liveGpsStop').disabled = true;
  if ($('simStatus')) $('simStatus').textContent = 'Live GPS stopped.';
}

/* ==================== BOOTSTRAP ==================== */

(async function boot() {
  initOverviewMap();
  connectLocationSocket();
  await loadStops();
  if (authHeader) {
    try {
      const me = await api('/api/auth/me');
      currentUser = me;
      $('loginOpen').classList.add('hidden');
      $('logoutBtn').classList.remove('hidden');
      $('portalNavLink').classList.remove('hidden');
      $('portal').classList.remove('hidden');
      $('overviewMapSection')?.classList.add('hidden');
      $('portalUser').textContent = `Signed in as ${me.username} · ${me.role}`;
      if (me.role === 'ADMIN') await renderAdmin();
      else if (me.role === 'DRIVER') await renderDriver();
      else throw new Error('Unsupported staff role');
    } catch (_) {
      handleUnauthorizedSession();
    }
  }
})();
