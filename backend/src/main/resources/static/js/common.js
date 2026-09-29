/**
 * Common JavaScript for Food Delivery & Customer Analytics System
 * Provides navigation, persistent filter bar, API client, and formatting helpers.
 */

const STORAGE_KEY = 'food_analytics_filters';

// Navigation definition
const NAV_ITEMS = [
  { name: 'Overview', href: 'index.html', id: 'nav-overview' },
  { name: 'Restaurants & Regions', href: 'restaurants.html', id: 'nav-restaurants' },
  { name: 'Customers', href: 'customers.html', id: 'nav-customers' },
  { name: 'Reviews', href: 'reviews.html', id: 'nav-reviews' },
  { name: 'Forecast', href: 'forecast.html', id: 'nav-forecast' }
];

/**
 * Renders the top navigation bar into #navbar-container
 * @param {string} activePage - e.g. 'index.html', 'restaurants.html'
 */
function renderNav(activePage) {
  const container = document.getElementById('navbar-container');
  if (!container) return;

  const linksHtml = NAV_ITEMS.map(item => {
    const isActive = (item.href === activePage || (activePage === '' && item.href === 'index.html'));
    return `
      <li class="nav-item">
        <a class="nav-link ${isActive ? 'active' : ''}" href="${item.href}">
          ${item.name}
        </a>
      </li>
    `;
  }).join('');

  container.innerHTML = `
    <nav class="navbar navbar-expand-lg navbar-light sticky-top">
      <div class="container-fluid px-lg-4">
        <a class="navbar-brand" href="index.html">
          <span class="brand-icon">
            <i class="bi bi-pie-chart-fill"></i>
          </span>
          <span>Food Analytics</span>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav" aria-controls="mainNav" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="mainNav">
          <ul class="navbar-nav ms-auto mb-2 mb-lg-0 gap-1">
            ${linksHtml}
          </ul>
        </div>
      </div>
    </nav>
  `;
}

/**
 * Gets saved filters from sessionStorage
 * @returns {Object}
 */
function getFilters() {
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    return saved ? JSON.parse(saved) : {};
  } catch (e) {
    console.error('Failed to parse sessionStorage filters', e);
    return {};
  }
}

/**
 * Saves filters to sessionStorage
 * @param {Object} filters 
 */
function saveFilters(filters) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
  } catch (e) {
    console.error('Failed to save filters to sessionStorage', e);
  }
}

/**
 * Builds a URL query string based on current filters and optional extra parameters
 * @param {Object} extraParams 
 * @returns {string}
 */
function buildQuery(extraParams = {}) {
  const filters = getFilters();
  const merged = { ...filters, ...extraParams };
  const query = new URLSearchParams();

  Object.entries(merged).forEach(([key, val]) => {
    if (val !== undefined && val !== null && String(val).trim() !== '') {
      query.append(key, String(val).trim());
    }
  });

  const str = query.toString();
  return str ? `?${str}` : '';
}

/**
 * Global API fetch wrapper with loading states and error handling
 * @param {string} path - e.g. '/api/kpis'
 * @returns {Promise<any>}
 */
async function api(path) {
  try {
    const res = await fetch(path);
    if (!res.ok) {
      throw new Error(`Server returned HTTP ${res.status}: ${res.statusText}`);
    }
    return await res.json();
  } catch (err) {
    console.error(`API request error on [${path}]:`, err);
    showGlobalError(`Failed to load data from ${path}. (${err.message})`);
    throw err;
  }
}

/**
 * Displays a friendly error banner in #error-container
 * @param {string} message 
 */
function showGlobalError(message) {
  let errContainer = document.getElementById('error-container');
  if (!errContainer) {
    errContainer = document.createElement('div');
    errContainer.id = 'error-container';
    const main = document.querySelector('main') || document.body;
    main.prepend(errContainer);
  }
  errContainer.innerHTML = `
    <div class="alert alert-danger alert-dismissible fade show app-alert mb-4" role="alert">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      <strong>Error:</strong> ${message}
      <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>
  `;
}

/**
 * Renders the filter bar into #filter-container and wires up Apply/Reset
 * @param {Function} onApply - Callback executed after filters change/applied
 */
async function renderFilterBar(onApply) {
  const container = document.getElementById('filter-container');
  if (!container) return;

  const currentFilters = getFilters();

  container.innerHTML = `
    <div class="filter-card">
      <form id="filter-form" class="row g-2 align-items-end">
        <div class="col-6 col-md-2">
          <label class="filter-label" for="filter-start">From Date</label>
          <input type="date" class="form-control" id="filter-start" value="${currentFilters.start || ''}">
        </div>
        <div class="col-6 col-md-2">
          <label class="filter-label" for="filter-end">To Date</label>
          <input type="date" class="form-control" id="filter-end" value="${currentFilters.end || ''}">
        </div>
        <div class="col-12 col-md-2">
          <label class="filter-label" for="filter-city">City</label>
          <select class="form-select" id="filter-city">
            <option value="">All Cities</option>
          </select>
        </div>
        <div class="col-6 col-md-2">
          <label class="filter-label" for="filter-cuisine">Cuisine</label>
          <select class="form-select" id="filter-cuisine">
            <option value="">All Cuisines</option>
          </select>
        </div>
        <div class="col-6 col-md-2">
          <label class="filter-label" for="filter-restaurant">Restaurant</label>
          <select class="form-select" id="filter-restaurant">
            <option value="">All Restaurants</option>
          </select>
        </div>
        <div class="col-12 col-md-2 d-flex gap-2">
          <button type="submit" class="btn btn-primary flex-grow-1" id="btn-apply-filters">
            <i class="bi bi-funnel-fill me-1"></i> Apply
          </button>
          <button type="button" class="btn btn-outline-secondary" id="btn-reset-filters" title="Reset Filters">
            <i class="bi bi-arrow-counterclockwise"></i>
          </button>
        </div>
      </form>
    </div>
  `;

  // Fetch filter options from /api/filters
  try {
    const meta = await api('/api/filters');
    populateDropdown('filter-city', meta.cities || [], currentFilters.city);
    populateDropdown('filter-cuisine', meta.cuisines || [], currentFilters.cuisine);
    populateDropdown('filter-restaurant', meta.restaurants || [], currentFilters.restaurant);

    if (meta.minDate && !currentFilters.start) {
      document.getElementById('filter-start').min = meta.minDate;
    }
    if (meta.maxDate && !currentFilters.end) {
      document.getElementById('filter-end').max = meta.maxDate;
    }
  } catch (e) {
    console.warn('Could not populate filter dropdowns', e);
  }

  // Handle Apply
  const form = document.getElementById('filter-form');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const updated = {
      start: document.getElementById('filter-start').value.trim(),
      end: document.getElementById('filter-end').value.trim(),
      city: document.getElementById('filter-city').value.trim(),
      cuisine: document.getElementById('filter-cuisine').value.trim(),
      restaurant: document.getElementById('filter-restaurant').value.trim()
    };
    saveFilters(updated);
    if (typeof onApply === 'function') {
      onApply(updated);
    }
  });

  // Handle Reset
  const resetBtn = document.getElementById('btn-reset-filters');
  resetBtn.addEventListener('click', () => {
    sessionStorage.removeItem(STORAGE_KEY);
    document.getElementById('filter-start').value = '';
    document.getElementById('filter-end').value = '';
    document.getElementById('filter-city').value = '';
    document.getElementById('filter-cuisine').value = '';
    document.getElementById('filter-restaurant').value = '';
    if (typeof onApply === 'function') {
      onApply({});
    }
  });
}

function populateDropdown(elemId, items, selectedValue) {
  const select = document.getElementById(elemId);
  if (!select) return;

  const defaultOption = select.firstElementChild ? select.firstElementChild.outerHTML : '<option value="">All</option>';
  const optionsHtml = items.map(item => {
    const isSelected = item === selectedValue ? 'selected' : '';
    return `<option value="${escapeHtml(item)}" ${isSelected}>${escapeHtml(item)}</option>`;
  }).join('');

  select.innerHTML = defaultOption + optionsHtml;
}

function escapeHtml(text) {
  if (!text) return '';
  return text.replace(/[&<>"']/g, function(m) {
    return {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    }[m];
  });
}

// Formatting helpers
/**
 * Formats a number as INR currency (e.g. ₹1,23,456.78)
 */
function formatCurrency(num) {
  if (num === null || num === undefined || isNaN(num)) return '₹0.00';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(num);
}

/**
 * Formats an integer with Indian comma notation
 */
function formatNumber(num) {
  if (num === null || num === undefined || isNaN(num)) return '0';
  return new Intl.NumberFormat('en-IN').format(num);
}

/**
 * Formats compact numbers (e.g. 19.7k, 8.7M)
 */
function formatCompactNumber(num) {
  if (num === null || num === undefined || isNaN(num)) return '0';
  return new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(num);
}

// Chart and Table State Handlers (Loading, Empty, Error)
/**
 * Sets a chart container into a Loading state overlay
 * @param {string} containerId - Element ID of the .chart-container
 * @param {string} message - Optional loading message
 */
function setChartLoading(containerId, message = 'Loading chart data...') {
  const container = document.getElementById(containerId);
  if (!container) return;
  removeChartState(containerId);
  const overlay = document.createElement('div');
  overlay.className = 'card-state-overlay state-loading';
  overlay.innerHTML = `
    <div class="spinner-border spinner-border-sm text-primary mb-2" role="status" style="width: 2rem; height: 2rem;">
      <span class="visually-hidden">Loading...</span>
    </div>
    <div class="state-text">${escapeHtml(message)}</div>
  `;
  container.appendChild(overlay);
}

/**
 * Sets a chart container into an Empty state overlay
 * @param {string} containerId - Element ID of the .chart-container
 * @param {string} message - Optional message
 */
function setChartEmpty(containerId, message = 'No data available for the current selection.') {
  const container = document.getElementById(containerId);
  if (!container) return;
  removeChartState(containerId);
  const overlay = document.createElement('div');
  overlay.className = 'card-state-overlay state-empty';
  overlay.innerHTML = `
    <div class="state-icon text-muted"><i class="bi bi-inbox"></i></div>
    <div class="state-title">No Data Available</div>
    <div class="state-text">${escapeHtml(message)}</div>
  `;
  container.appendChild(overlay);
}

/**
 * Sets a chart container into an Error state overlay
 * @param {string} containerId - Element ID of the .chart-container
 * @param {string} message - Optional error message
 */
function setChartError(containerId, message = 'Failed to load chart data.') {
  const container = document.getElementById(containerId);
  if (!container) return;
  removeChartState(containerId);
  const overlay = document.createElement('div');
  overlay.className = 'card-state-overlay state-error';
  overlay.innerHTML = `
    <div class="state-icon text-danger"><i class="bi bi-exclamation-octagon"></i></div>
    <div class="state-title">Unable to Load Chart</div>
    <div class="state-text">${escapeHtml(message)}</div>
  `;
  container.appendChild(overlay);
}

/**
 * Removes any state overlay from a container
 * @param {string} containerId 
 */
function removeChartState(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  const existing = container.querySelector('.card-state-overlay');
  if (existing) {
    existing.remove();
  }
}

/**
 * Helper to set Table Body state (loading, empty, error)
 * @param {string} tbodyId
 * @param {'loading'|'empty'|'error'} state
 * @param {number} colSpan
 * @param {string} message
 */
function setTableState(tbodyId, state, colSpan = 5, message = '') {
  const tbody = document.getElementById(tbodyId);
  if (!tbody) return;
  if (state === 'loading') {
    tbody.innerHTML = `
      <tr>
        <td colspan="${colSpan}" class="text-center py-4 text-muted">
          <div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>
          <span>${message || 'Loading table data...'}</span>
        </td>
      </tr>
    `;
  } else if (state === 'empty') {
    tbody.innerHTML = `
      <tr>
        <td colspan="${colSpan}" class="text-center py-4 text-muted">
          <i class="bi bi-inbox fs-4 d-block mb-1 text-secondary"></i>
          <span>${message || 'No records found matching current criteria.'}</span>
        </td>
      </tr>
    `;
  } else if (state === 'error') {
    tbody.innerHTML = `
      <tr>
        <td colspan="${colSpan}" class="text-center py-4 text-danger">
          <i class="bi bi-exclamation-triangle fs-4 d-block mb-1"></i>
          <span>${message || 'Failed to load table records.'}</span>
        </td>
      </tr>
    `;
  }
}

/**
 * Renders the standardized footer across pages
 */
function renderFooter() {
  const footerText = 'Food Delivery & Customer Analytics System | Dataset is synthetic';
  let footer = document.querySelector('footer');
  if (!footer) {
    footer = document.createElement('footer');
    document.body.appendChild(footer);
  }
  footer.className = 'text-center py-3 border-top bg-white';
  footer.innerHTML = `
    <div class="container-fluid px-4">
      <span class="text-muted small">${footerText}</span>
    </div>
  `;
}
