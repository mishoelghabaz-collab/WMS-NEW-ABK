(function () {
  'use strict';

  var Core = window.PlantWmsCore;
  var ACTIVE_PLANT_KEY = 'plant_wms_active_plant_v1';

  var state = {
    db: Core.loadDb(),
    activePlantId: null,
    activeView: 'filter', // 'filter' | 'lines' | 'materials' | 'stock' | 'plants'

    // Smart Filter state (scoped to activePlantId)
    filterDropdownOpen: false,
    filterLineSearch: '',
    filterSelectedLineIds: [],
    filterMode: 'both',
    filterApplied: false,
    filterAppliedLineIds: [],
    filterAppliedMode: 'both',
    filterSort: { key: 'code', dir: 'asc' },

    // Line Management state
    lineSearch: '',
    bulkLineIds: [],
    dragLineId: null,
    isEditingLine: false,

    // Materials & Stock Management state
    materialSearch: '',
    stockSearch: '',
    materialModalLineSearch: '',
    materialModalLineIds: [],
    isEditingMaterial: false,

    // Material Details & Assignment Editor modal state
    detailsMaterialId: null,
    detailsAddLineSearch: '',
    detailsAddLineIds: [],

    // Confirm dialog callback
    confirmCallback: null
  };

  (function initActivePlant() {
    try {
      var savedId = Number(localStorage.getItem(ACTIVE_PLANT_KEY));
      if (savedId && state.db.plants.some(function (p) { return p.id === savedId; })) {
        state.activePlantId = savedId;
      } else if (state.db.plants.length > 0) {
        state.activePlantId = state.db.plants[0].id;
      }
    } catch (_e) {
      state.activePlantId = state.db.plants.length > 0 ? state.db.plants[0].id : null;
    }
  })();

  function persistDb(nextDb) {
    state.db = Core.saveDb(nextDb);
  }

  function getActivePlant() {
    if (!state.activePlantId) return null;
    return state.db.plants.find(function (p) { return p.id === state.activePlantId; }) || null;
  }

  function setActivePlant(plantId) {
    var pid = plantId ? Number(plantId) : null;
    if (pid && !state.db.plants.some(function (p) { return p.id === pid; })) {
      pid = null;
    }
    state.activePlantId = pid;
    try {
      if (pid) localStorage.setItem(ACTIVE_PLANT_KEY, String(pid));
      else localStorage.removeItem(ACTIVE_PLANT_KEY);
    } catch (_e) {}

    // STRICT ISOLATION: clear all transient selections & searches on plant switch
    state.filterDropdownOpen = false;
    state.filterLineSearch = '';
    state.filterSelectedLineIds = [];
    state.filterApplied = false;
    state.filterAppliedLineIds = [];
    state.lineSearch = '';
    state.bulkLineIds = [];
    state.materialSearch = '';
    state.stockSearch = '';
    state.materialModalLineSearch = '';
    state.materialModalLineIds = [];
    state.detailsMaterialId = null;
    state.detailsAddLineSearch = '';
    state.detailsAddLineIds = [];

    ['lineListSearchInput', 'materialListSearchInput', 'stockSearchInput', 'filterLineSearchInput'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.value = '';
    });

    renderAll();
  }

  function showToast(message, type) {
    var container = document.getElementById('toastContainer');
    var toast = document.createElement('div');
    toast.className = 'toast ' + (type === 'error' ? 'toast-error' : 'toast-success');
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(function () {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 3800);
  }

  function openModal(id) {
    var el = document.getElementById(id);
    if (el) el.classList.remove('hidden');
  }

  function closeModal(id) {
    var el = document.getElementById(id);
    if (el) el.classList.add('hidden');
  }

  function showFieldError(errorId, inputEl, message) {
    var errEl = document.getElementById(errorId);
    if (errEl) {
      if (message) {
        errEl.textContent = message;
        errEl.classList.remove('hidden');
      } else {
        errEl.textContent = '';
        errEl.classList.add('hidden');
      }
    }
    if (inputEl) {
      if (message) inputEl.classList.add('is-invalid');
      else inputEl.classList.remove('is-invalid');
    }
  }

  function switchView(viewName) {
    state.activeView = viewName;
    state.filterDropdownOpen = false;
    renderAll();
  }

  function buildLineStatusBadge(line) {
    var info = Core.getLineStatus(line);
    var span = document.createElement('span');
    if (info.status === 'complete') {
      span.className = 'badge badge-complete';
    } else if (info.status === 'in_progress') {
      span.className = 'badge badge-progress';
    } else {
      span.className = 'badge badge-empty';
    }
    span.textContent = info.label;
    return span;
  }

  function buildLineProgressBlock(line) {
    var info = Core.getLineStatus(line);
    var wrap = document.createElement('div');
    wrap.className = 'progress-wrap';

    var top = document.createElement('div');
    top.style.display = 'flex';
    top.style.alignItems = 'center';
    top.style.justifyContent = 'space-between';
    top.style.gap = '6px';
    top.appendChild(buildLineStatusBadge(line));

    var pct = document.createElement('span');
    pct.className = 'mono';
    pct.style.fontSize = '12px';
    pct.style.fontWeight = '700';
    pct.style.color = 'var(--text-muted)';
    pct.textContent = info.percent + '%';
    top.appendChild(pct);

    var track = document.createElement('div');
    track.className = 'progress-track';
    var fill = document.createElement('div');
    fill.className = 'progress-fill ' + info.status;
    fill.style.width = info.percent + '%';
    track.appendChild(fill);

    wrap.appendChild(top);
    wrap.appendChild(track);
    return wrap;
  }

  // =========================================================================
  // RENDERING
  // =========================================================================

  function renderAll() {
    renderHeaderAndNav();
    renderGateAndViews();

    if (state.activeView === 'plants') {
      renderPlantsView();
    } else if (getActivePlant()) {
      if (state.activeView === 'filter') renderFilterView();
      else if (state.activeView === 'lines') renderLinesView();
      else if (state.activeView === 'materials') renderMaterialsView();
      else if (state.activeView === 'stock') renderStockView();
    }

    // Keep open Material Details modal synced if open
    var detailsModal = document.getElementById('materialDetailsModal');
    if (detailsModal && !detailsModal.classList.contains('hidden') && state.detailsMaterialId) {
      renderMaterialDetailsModalContent();
    }
  }

  function renderHeaderAndNav() {
    var plants = Core.getPlants(state.db);
    var select = document.getElementById('activePlantSelect');
    select.innerHTML = '';

    var placeholderOpt = document.createElement('option');
    placeholderOpt.value = '';
    placeholderOpt.textContent = plants.length === 0 ? '-- لا توجد مصانع --' : '-- اختر المصنع --';
    select.appendChild(placeholderOpt);

    plants.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = String(p.id);
      opt.textContent = p.name;
      select.appendChild(opt);
    });
    select.value = state.activePlantId ? String(state.activePlantId) : '';

    var activePlant = getActivePlant();
    var plantLines = activePlant ? Core.getLinesByPlant(state.db, activePlant.id) : [];
    var plantMaterials = activePlant ? Core.getMaterialsByPlant(state.db, activePlant.id) : [];
    var lowStockCount = plantMaterials.filter(Core.isLowStock).length;

    document.getElementById('navLinesCount').textContent = String(plantLines.length);
    document.getElementById('navMaterialsCount').textContent = String(plantMaterials.length);
    document.getElementById('navStockAlertCount').textContent = String(lowStockCount);
    document.getElementById('navPlantsCount').textContent = String(plants.length);

    document.querySelectorAll('.nav-tab').forEach(function (btn) {
      btn.classList.toggle('active', btn.getAttribute('data-view') === state.activeView);
    });
  }

  function renderGateAndViews() {
    var activePlant = getActivePlant();
    var gateEl = document.getElementById('noPlantGate');
    document.querySelectorAll('.view-section').forEach(function (sec) { sec.classList.add('hidden'); });

    if (state.activeView === 'plants') {
      gateEl.classList.add('hidden');
      document.getElementById('view-plants').classList.remove('hidden');
      return;
    }

    if (!activePlant) {
      gateEl.classList.remove('hidden');
      var hasPlants = state.db.plants.length > 0;
      document.getElementById('noPlantTitle').textContent = hasPlants
        ? 'يرجى اختيار مصنع نشط أولاً'
        : 'لا توجد مصانع مسجلة بعد';
      document.getElementById('noPlantDesc').textContent = hasPlants
        ? 'اختر مصنعاً من القائمة العلوية لعرض خطوط الإنتاج والخامات والمخزون والخطط المعزولة الخاصة به.'
        : 'ابدأ بإنشاء مصنع جديد (مثل Plant 7 أو Plant 2) أو حمّل البيانات التجريبية لاختبار النظام.';
      return;
    }

    gateEl.classList.add('hidden');
    var activeSection = document.getElementById('view-' + state.activeView);
    if (activeSection) activeSection.classList.remove('hidden');
  }

  // -------------------------------------------------------------------------
  // VIEW 1: SMART FILTERING
  // -------------------------------------------------------------------------
  function renderFilterView() {
    var plant = getActivePlant();
    if (!plant) return;

    document.getElementById('filterPlantPill').textContent = 'النطاق: ' + plant.name;

    var allLines = Core.getLinesByPlant(state.db, plant.id);
    var validLineIdSet = Object.create(null);
    allLines.forEach(function (l) { validLineIdSet[l.id] = true; });

    state.filterSelectedLineIds = state.filterSelectedLineIds.filter(function (id) { return !!validLineIdSet[id]; });
    state.filterAppliedLineIds = state.filterAppliedLineIds.filter(function (id) { return !!validLineIdSet[id]; });
    if (state.filterApplied && state.filterAppliedLineIds.length === 0) {
      state.filterApplied = false;
    }

    var dropdown = document.getElementById('filterLinesDropdown');
    var trigger = document.getElementById('filterLinesTrigger');
    dropdown.classList.toggle('hidden', !state.filterDropdownOpen);
    trigger.setAttribute('aria-expanded', state.filterDropdownOpen ? 'true' : 'false');

    var triggerText = document.getElementById('filterLinesTriggerText');
    if (allLines.length === 0) {
      triggerText.textContent = 'لا توجد خطوط في هذا المصنع — أضف خطوطاً أولاً';
    } else if (state.filterSelectedLineIds.length === 0) {
      triggerText.textContent = 'اختر خطاً واحداً أو أكثر...';
    } else {
      triggerText.textContent = 'تم تحديد ' + state.filterSelectedLineIds.length + ' خط إنتاج';
    }

    var listEl = document.getElementById('filterLinesOptionsList');
    listEl.innerHTML = '';
    var search = state.filterLineSearch.trim().toLowerCase();
    var filteredLines = allLines.filter(function (l) {
      return !search || l.name.toLowerCase().indexOf(search) !== -1;
    });

    if (allLines.length === 0) {
      var emptyMsg = document.createElement('div');
      emptyMsg.style.padding = '12px';
      emptyMsg.style.color = 'var(--text-muted)';
      emptyMsg.style.fontSize = '13px';
      emptyMsg.textContent = 'لا توجد خطوط مسجلة لهذا المصنع. اضغط «إدارة الخطوط» لإضافة خط.';
      listEl.appendChild(emptyMsg);
    } else if (filteredLines.length === 0) {
      var noMatch = document.createElement('div');
      noMatch.style.padding = '12px';
      noMatch.style.color = 'var(--text-muted)';
      noMatch.style.fontSize = '13px';
      noMatch.textContent = 'لا يوجد خط يطابق "' + state.filterLineSearch + '"';
      listEl.appendChild(noMatch);
    } else {
      filteredLines.forEach(function (line) {
        var label = document.createElement('label');
        label.className = 'checkbox-option';

        var left = document.createElement('span');
        left.className = 'checkbox-option-main';

        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = state.filterSelectedLineIds.indexOf(line.id) !== -1;
        cb.addEventListener('change', function () {
          if (cb.checked) {
            if (state.filterSelectedLineIds.indexOf(line.id) === -1) {
              state.filterSelectedLineIds.push(line.id);
            }
          } else {
            state.filterSelectedLineIds = state.filterSelectedLineIds.filter(function (x) { return x !== line.id; });
          }
          renderFilterView();
        });

        var nameSpan = document.createElement('span');
        nameSpan.textContent = line.name;
        nameSpan.style.fontWeight = '600';

        left.appendChild(cb);
        left.appendChild(nameSpan);

        var rightGroup = document.createElement('span');
        rightGroup.style.display = 'inline-flex';
        rightGroup.style.alignItems = 'center';
        rightGroup.style.gap = '6px';
        rightGroup.appendChild(buildLineStatusBadge(line));

        var orderTag = document.createElement('span');
        orderTag.className = 'badge badge-neutral mono';
        orderTag.textContent = '#' + line.order;
        rightGroup.appendChild(orderTag);

        label.appendChild(left);
        label.appendChild(rightGroup);
        listEl.appendChild(label);
      });
    }

    var chipsRow = document.getElementById('filterSelectedChips');
    chipsRow.innerHTML = '';
    allLines.forEach(function (line) {
      if (state.filterSelectedLineIds.indexOf(line.id) === -1) return;
      var chip = document.createElement('span');
      chip.className = 'chip';
      chip.appendChild(document.createTextNode(line.name + ' '));

      var removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'chip-remove';
      removeBtn.textContent = '✕';
      removeBtn.setAttribute('aria-label', 'إزالة ' + line.name);
      removeBtn.addEventListener('click', function () {
        state.filterSelectedLineIds = state.filterSelectedLineIds.filter(function (x) { return x !== line.id; });
        renderFilterView();
      });
      chip.appendChild(removeBtn);
      chipsRow.appendChild(chip);
    });

    document.querySelectorAll('input[name="filterMode"]').forEach(function (r) {
      r.checked = r.value === state.filterMode;
    });

    var summaryBar = document.getElementById('filterSummaryBar');
    var initialEmpty = document.getElementById('filterInitialEmptyState');
    var noResults = document.getElementById('filterNoResultsState');
    var tableWrap = document.getElementById('filterTableWrap');
    var tbody = document.getElementById('filterResultsBody');

    if (!state.filterApplied) {
      summaryBar.classList.add('hidden');
      noResults.classList.add('hidden');
      tableWrap.classList.add('hidden');
      initialEmpty.classList.remove('hidden');
      return;
    }

    initialEmpty.classList.add('hidden');
    var result = Core.filterMaterials(
      state.db,
      plant.id,
      state.filterAppliedLineIds,
      state.filterAppliedMode,
      state.filterSort
    );

    summaryBar.classList.remove('hidden');
    document.getElementById('filterCounterText').textContent =
      result.totalCount + ' خامة — ' + result.exclusiveCount + ' حصري، ' + result.sharedCount + ' مشترك';
    document.getElementById('filterExclusivePill').textContent = 'حصري: ' + result.exclusiveCount;
    document.getElementById('filterSharedPill').textContent = 'مشترك: ' + result.sharedCount;

    document.querySelectorAll('.sort-indicator').forEach(function (ind) {
      var k = ind.getAttribute('data-indicator');
      if (k === state.filterSort.key) {
        ind.textContent = state.filterSort.dir === 'asc' ? '▲' : '▼';
      } else {
        ind.textContent = '⇅';
      }
    });

    if (result.rows.length === 0) {
      tableWrap.classList.add('hidden');
      noResults.classList.remove('hidden');
      return;
    }

    noResults.classList.add('hidden');
    tableWrap.classList.remove('hidden');
    tbody.innerHTML = '';

    result.rows.forEach(function (row) {
      var tr = document.createElement('tr');

      // Code
      var tdCode = document.createElement('td');
      var codeBtn = document.createElement('button');
      codeBtn.type = 'button';
      codeBtn.className = 'btn-link mono';
      codeBtn.style.fontWeight = '800';
      codeBtn.style.fontSize = '15px';
      codeBtn.textContent = row.code;
      codeBtn.title = 'فتح تفاصيل وتعيينات الخامة';
      codeBtn.addEventListener('click', function () {
        openMaterialDetailsModal(row.id);
      });
      tdCode.appendChild(codeBtn);

      // Name + stock badge
      var tdName = document.createElement('td');
      var nameDiv = document.createElement('div');
      nameDiv.style.fontWeight = '700';
      nameDiv.textContent = row.name;
      tdName.appendChild(nameDiv);
      var subMeta = document.createElement('div');
      subMeta.style.fontSize = '12px';
      subMeta.style.color = 'var(--text-muted)';
      subMeta.textContent = (row.description ? row.description + ' • ' : '') + 'المخزون: ' + row.stock_qty;
      tdName.appendChild(subMeta);

      // Lines (comma-separated + hover tooltip)
      var tdLines = document.createElement('td');
      tdLines.className = 'lines-tooltip-cell';
      var preview = document.createElement('span');
      preview.className = 'lines-preview-text';
      preview.title = row.lineNamesComma;
      preview.textContent = row.lineNamesComma;
      var tooltip = document.createElement('div');
      tooltip.className = 'custom-tooltip';
      tooltip.textContent = row.lineNamesComma;
      tdLines.addEventListener('mouseenter', function () {
        var rect = tdLines.getBoundingClientRect();
        tooltip.style.position = 'fixed';
        tooltip.style.right = Math.max(8, window.innerWidth - rect.right) + 'px';
        if (rect.top < 110) {
          tooltip.style.bottom = 'auto';
          tooltip.style.top = (rect.bottom + 6) + 'px';
        } else {
          tooltip.style.top = 'auto';
          tooltip.style.bottom = (window.innerHeight - rect.top + 6) + 'px';
        }
      });
      tdLines.appendChild(preview);
      tdLines.appendChild(tooltip);

      // Line Count
      var tdCount = document.createElement('td');
      var countSpan = document.createElement('span');
      countSpan.className = 'badge badge-neutral mono';
      countSpan.textContent = String(row.lineCount);
      tdCount.appendChild(countSpan);

      // Type (Exclusive green / Shared orange)
      var tdType = document.createElement('td');
      var badge = document.createElement('span');
      badge.className = 'badge ' + (row.type === 'exclusive' ? 'badge-exclusive' : 'badge-shared');
      badge.textContent = row.type === 'exclusive' ? 'حصري (Exclusive)' : 'مشترك (Shared)';
      tdType.appendChild(badge);

      // Supplied / Plan column
      var tdSupplied = document.createElement('td');
      var supWrap = document.createElement('div');
      supWrap.style.display = 'flex';
      supWrap.style.flexDirection = 'column';
      supWrap.style.gap = '6px';

      row.assignments.forEach(function (entry) {
        var lineItem = document.createElement('div');
        lineItem.style.display = 'flex';
        lineItem.style.alignItems = 'center';
        lineItem.style.justifyContent = 'space-between';
        lineItem.style.gap = '8px';
        lineItem.style.fontSize = '12.5px';

        var lName = document.createElement('span');
        lName.style.fontWeight = '600';
        lName.textContent = entry.line.name + ' (مورّد الصنف: ' + entry.supplied_qty + ')';

        lineItem.appendChild(lName);
        lineItem.appendChild(buildLineStatusBadge(entry.line));
        supWrap.appendChild(lineItem);
      });

      var quickSupplyBtn = document.createElement('button');
      quickSupplyBtn.type = 'button';
      quickSupplyBtn.className = 'btn btn-secondary btn-sm';
      quickSupplyBtn.style.alignSelf = 'flex-start';
      quickSupplyBtn.textContent = '+ توريد';
      quickSupplyBtn.addEventListener('click', function () {
        openSupplyModal(row.id, row.assignments[0] ? row.assignments[0].line_id : null);
      });
      supWrap.appendChild(quickSupplyBtn);
      tdSupplied.appendChild(supWrap);

      tr.appendChild(tdCode);
      tr.appendChild(tdName);
      tr.appendChild(tdLines);
      tr.appendChild(tdCount);
      tr.appendChild(tdType);
      tr.appendChild(tdSupplied);
      tbody.appendChild(tr);
    });
  }

  // -------------------------------------------------------------------------
  // VIEW 2: LINE MANAGEMENT
  // -------------------------------------------------------------------------
  function renderLinesView() {
    var plant = getActivePlant();
    if (!plant) return;

    document.getElementById('linesPlantPill').textContent = 'النطاق: ' + plant.name;

    var allLines = Core.getLinesByPlant(state.db, plant.id);
    var validSet = Object.create(null);
    allLines.forEach(function (l) { validSet[l.id] = true; });
    state.bulkLineIds = state.bulkLineIds.filter(function (id) { return !!validSet[id]; });

    var query = state.lineSearch.trim().toLowerCase();
    var visibleLines = allLines.filter(function (l) {
      return !query || l.name.toLowerCase().indexOf(query) !== -1;
    });

    var bulkBar = document.getElementById('linesBulkBar');
    if (state.bulkLineIds.length > 0) {
      bulkBar.classList.remove('hidden');
      document.getElementById('linesBulkCountText').textContent = 'تم تحديد ' + state.bulkLineIds.length + ' خط';
    } else {
      bulkBar.classList.add('hidden');
    }

    var emptyState = document.getElementById('linesEmptyState');
    var tableWrap = document.getElementById('linesTableWrap');
    var tbody = document.getElementById('linesTableBody');

    if (visibleLines.length === 0) {
      tableWrap.classList.add('hidden');
      emptyState.classList.remove('hidden');
      if (allLines.length === 0) {
        document.getElementById('linesEmptyTitle').textContent = 'لا توجد خطوط إنتاج في ' + plant.name;
        document.getElementById('linesEmptyDesc').textContent = 'أضف خطوط الإنتاج وحدد كمية الخطة (plan_qty) لكل خط.';
        document.getElementById('linesEmptyAddBtn').classList.remove('hidden');
      } else {
        document.getElementById('linesEmptyTitle').textContent = 'لا توجد نتائج مطابقة للبحث';
        document.getElementById('linesEmptyDesc').textContent = 'لا يوجد خط إنتاج باسم "' + state.lineSearch + '" داخل ' + plant.name + '.';
        document.getElementById('linesEmptyAddBtn').classList.add('hidden');
      }
      return;
    }

    emptyState.classList.add('hidden');
    tableWrap.classList.remove('hidden');
    tbody.innerHTML = '';

    var selectAllCb = document.getElementById('linesSelectAllCheckbox');
    selectAllCb.checked = visibleLines.length > 0 && visibleLines.every(function (l) {
      return state.bulkLineIds.indexOf(l.id) !== -1;
    });

    var isSearchFiltered = query.length > 0;

    visibleLines.forEach(function (line, visibleIdx) {
      var tr = document.createElement('tr');
      tr.className = 'drag-row';
      tr.setAttribute('data-line-id', String(line.id));
      tr.draggable = !isSearchFiltered;

      if (!isSearchFiltered) {
        tr.addEventListener('dragstart', function (e) {
          state.dragLineId = line.id;
          tr.classList.add('dragging');
          if (e.dataTransfer) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', String(line.id));
          }
        });
        tr.addEventListener('dragend', function () {
          state.dragLineId = null;
          tr.classList.remove('dragging');
          document.querySelectorAll('.drag-over').forEach(function (el) { el.classList.remove('drag-over'); });
        });
        tr.addEventListener('dragover', function (e) {
          e.preventDefault();
          if (state.dragLineId && state.dragLineId !== line.id) {
            tr.classList.add('drag-over');
          }
        });
        tr.addEventListener('dragleave', function () {
          tr.classList.remove('drag-over');
        });
        tr.addEventListener('drop', function (e) {
          e.preventDefault();
          tr.classList.remove('drag-over');
          var draggedId = state.dragLineId;
          if (!draggedId || draggedId === line.id) return;

          var orderedIds = allLines.map(function (item) { return item.id; });
          var fromIdx = orderedIds.indexOf(draggedId);
          var toIdx = orderedIds.indexOf(line.id);
          if (fromIdx === -1 || toIdx === -1) return;

          orderedIds.splice(fromIdx, 1);
          orderedIds.splice(toIdx, 0, draggedId);
          try {
            var res = Core.reorderLines(state.db, plant.id, orderedIds);
            persistDb(res.db);
            showToast('تم حفظ الترتيب الجديد للخطوط.', 'success');
            renderAll();
          } catch (err) {
            showToast(err.message, 'error');
          }
        });
      }

      // Checkbox
      var tdCheck = document.createElement('td');
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = state.bulkLineIds.indexOf(line.id) !== -1;
      cb.setAttribute('aria-label', 'تحديد ' + line.name);
      cb.addEventListener('change', function () {
        if (cb.checked) {
          if (state.bulkLineIds.indexOf(line.id) === -1) state.bulkLineIds.push(line.id);
        } else {
          state.bulkLineIds = state.bulkLineIds.filter(function (x) { return x !== line.id; });
        }
        renderLinesView();
      });
      tdCheck.appendChild(cb);

      // Order
      var tdOrder = document.createElement('td');
      var orderWrap = document.createElement('div');
      orderWrap.className = 'order-controls';

      if (!isSearchFiltered) {
        var dragIcon = document.createElement('span');
        dragIcon.className = 'drag-handle';
        dragIcon.title = 'اسحب لإعادة الترتيب';
        dragIcon.textContent = '⠿';
        orderWrap.appendChild(dragIcon);
      }

      var orderNum = document.createElement('span');
      orderNum.className = 'badge badge-neutral mono';
      orderNum.textContent = '#' + line.order;
      orderWrap.appendChild(orderNum);

      var upBtn = document.createElement('button');
      upBtn.type = 'button';
      upBtn.className = 'arrow-btn';
      upBtn.textContent = '↑';
      upBtn.title = 'تحريك لأعلى';
      upBtn.disabled = isSearchFiltered || visibleIdx === 0;
      upBtn.addEventListener('click', function () {
        try {
          var res = Core.moveLineStep(state.db, plant.id, line.id, -1);
          persistDb(res.db);
          renderAll();
        } catch (err) {
          showToast(err.message, 'error');
        }
      });

      var downBtn = document.createElement('button');
      downBtn.type = 'button';
      downBtn.className = 'arrow-btn';
      downBtn.textContent = '↓';
      downBtn.title = 'تحريك لأسفل';
      downBtn.disabled = isSearchFiltered || visibleIdx === visibleLines.length - 1;
      downBtn.addEventListener('click', function () {
        try {
          var res = Core.moveLineStep(state.db, plant.id, line.id, 1);
          persistDb(res.db);
          renderAll();
        } catch (err) {
          showToast(err.message, 'error');
        }
      });

      orderWrap.appendChild(upBtn);
      orderWrap.appendChild(downBtn);
      tdOrder.appendChild(orderWrap);

      // Line name
      var tdName = document.createElement('td');
      tdName.style.fontWeight = '700';
      tdName.textContent = line.name;

      // Plan & received progress
      var tdPlan = document.createElement('td');
      tdPlan.appendChild(buildLineProgressBlock(line));

      // Attached materials count
      var attachedCount = Core.countMaterialsForLine(state.db, plant.id, line.id);
      var tdAttached = document.createElement('td');
      var attachedBadge = document.createElement('span');
      attachedBadge.className = 'badge badge-neutral';
      attachedBadge.textContent = attachedCount + ' خامة';
      tdAttached.appendChild(attachedBadge);

      // Actions
      var tdActions = document.createElement('td');
      var actionsDiv = document.createElement('div');
      actionsDiv.style.display = 'flex';
      actionsDiv.style.gap = '6px';
      actionsDiv.style.flexWrap = 'wrap';

      var supplyBtn = document.createElement('button');
      supplyBtn.type = 'button';
      supplyBtn.className = 'btn btn-success btn-sm';
      supplyBtn.textContent = '+ توريد';
      supplyBtn.addEventListener('click', function () {
        openSupplyModal(null, line.id);
      });

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = 'تعديل';
      editBtn.addEventListener('click', function () {
        openLineModal(line);
      });

      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-danger-outline btn-sm';
      delBtn.textContent = 'حذف';
      delBtn.addEventListener('click', function () {
        promptDeleteLine(plant, line);
      });

      actionsDiv.appendChild(supplyBtn);
      actionsDiv.appendChild(editBtn);
      actionsDiv.appendChild(delBtn);
      tdActions.appendChild(actionsDiv);

      tr.appendChild(tdCheck);
      tr.appendChild(tdOrder);
      tr.appendChild(tdName);
      tr.appendChild(tdPlan);
      tr.appendChild(tdAttached);
      tr.appendChild(tdActions);
      tbody.appendChild(tr);
    });
  }

  // -------------------------------------------------------------------------
  // VIEW 3: MATERIALS MANAGEMENT
  // -------------------------------------------------------------------------
  function renderMaterialsView() {
    var plant = getActivePlant();
    if (!plant) return;

    document.getElementById('materialsPlantPill').textContent = 'النطاق: ' + plant.name;

    var allMaterials = Core.getMaterialsByPlant(state.db, plant.id).sort(function (a, b) {
      return a.code.localeCompare(b.code, undefined, { numeric: true });
    });
    var q = state.materialSearch.trim().toLowerCase();
    var visible = allMaterials.filter(function (m) {
      if (!q) return true;
      return m.code.toLowerCase().indexOf(q) !== -1 ||
        m.name.toLowerCase().indexOf(q) !== -1 ||
        m.description.toLowerCase().indexOf(q) !== -1;
    });

    var emptyState = document.getElementById('materialsEmptyState');
    var tableWrap = document.getElementById('materialsTableWrap');
    var tbody = document.getElementById('materialsTableBody');

    if (visible.length === 0) {
      tableWrap.classList.add('hidden');
      emptyState.classList.remove('hidden');
      if (allMaterials.length === 0) {
        document.getElementById('materialsEmptyTitle').textContent = 'لا توجد خامات في ' + plant.name;
        document.getElementById('materialsEmptyDesc').textContent = 'أضف أول خامة وحدد رصيد المخزون وخطوط الإنتاج الخاصة بها.';
        document.getElementById('materialsEmptyAddBtn').classList.remove('hidden');
      } else {
        document.getElementById('materialsEmptyTitle').textContent = 'لا توجد خامات مطابقة';
        document.getElementById('materialsEmptyDesc').textContent = 'لم يتم العثور على خامة تطابق "' + state.materialSearch + '" في ' + plant.name + '.';
        document.getElementById('materialsEmptyAddBtn').classList.add('hidden');
      }
      return;
    }

    emptyState.classList.add('hidden');
    tableWrap.classList.remove('hidden');
    tbody.innerHTML = '';

    visible.forEach(function (mat) {
      var assignments = Core.getAssignmentsForMaterial(state.db, mat.id, plant.id);
      var lowStock = Core.isLowStock(mat);
      var tr = document.createElement('tr');

      // Code (click opens Details & Assignment Editor)
      var tdCode = document.createElement('td');
      var codeBtn = document.createElement('button');
      codeBtn.type = 'button';
      codeBtn.className = 'btn-link mono';
      codeBtn.style.fontWeight = '800';
      codeBtn.style.fontSize = '15px';
      codeBtn.textContent = mat.code;
      codeBtn.addEventListener('click', function () {
        openMaterialDetailsModal(mat.id);
      });
      tdCode.appendChild(codeBtn);

      // Name & Description
      var tdName = document.createElement('td');
      var nameEl = document.createElement('div');
      nameEl.style.fontWeight = '700';
      nameEl.textContent = mat.name;
      tdName.appendChild(nameEl);
      if (mat.description) {
        var descEl = document.createElement('div');
        descEl.style.fontSize = '12.5px';
        descEl.style.color = 'var(--text-muted)';
        descEl.textContent = mat.description;
        tdName.appendChild(descEl);
      }

      // Stock cell
      var tdStock = document.createElement('td');
      var stockCol = document.createElement('div');
      stockCol.style.display = 'flex';
      stockCol.style.flexDirection = 'column';
      stockCol.style.gap = '4px';
      var stockNum = document.createElement('span');
      stockNum.className = 'mono';
      stockNum.style.fontWeight = '800';
      stockNum.textContent = String(mat.stock_qty);
      stockCol.appendChild(stockNum);
      if (mat.stock_qty === 0) {
        var zeroB = document.createElement('span');
        zeroB.className = 'badge badge-danger';
        zeroB.textContent = 'نفد المخزون';
        stockCol.appendChild(zeroB);
      } else if (lowStock) {
        var lowB = document.createElement('span');
        lowB.className = 'badge badge-warning';
        lowB.textContent = 'مخزون منخفض';
        stockCol.appendChild(lowB);
      }
      tdStock.appendChild(stockCol);

      // Lines cell (0 lines -> clear grey badge "غير مرتبط بأي لاين")
      var tdLines = document.createElement('td');
      if (assignments.length === 0) {
        var noLinesBadge = document.createElement('span');
        noLinesBadge.className = 'badge badge-empty';
        noLinesBadge.textContent = 'غير مرتبط بأي لاين';
        tdLines.appendChild(noLinesBadge);
      } else {
        var wrap = document.createElement('div');
        wrap.style.display = 'flex';
        wrap.style.flexWrap = 'wrap';
        wrap.style.gap = '5px';
        assignments.forEach(function (entry) {
          var chip = document.createElement('span');
          chip.className = 'chip';
          chip.textContent = entry.supplied_qty > 0
            ? entry.line.name + ' (' + entry.supplied_qty + ')'
            : entry.line.name;
          wrap.appendChild(chip);
        });
        tdLines.appendChild(wrap);
      }

      // Line count
      var tdCount = document.createElement('td');
      var countBadge = document.createElement('span');
      countBadge.className = 'badge badge-neutral mono';
      countBadge.textContent = String(assignments.length);
      tdCount.appendChild(countBadge);

      // Actions
      var tdActions = document.createElement('td');
      var actRow = document.createElement('div');
      actRow.style.display = 'flex';
      actRow.style.gap = '6px';
      actRow.style.flexWrap = 'wrap';

      var detailsBtn = document.createElement('button');
      detailsBtn.type = 'button';
      detailsBtn.className = 'btn btn-primary btn-sm';
      detailsBtn.textContent = 'التعيينات';
      detailsBtn.title = 'إدارة خطوط هذه الخامة (إضافة / حذف / مسح الكل)';
      detailsBtn.addEventListener('click', function () {
        openMaterialDetailsModal(mat.id);
      });

      var supplyBtn = document.createElement('button');
      supplyBtn.type = 'button';
      supplyBtn.className = 'btn btn-success btn-sm';
      supplyBtn.textContent = 'توريد';
      supplyBtn.addEventListener('click', function () {
        openSupplyModal(mat.id, assignments[0] ? assignments[0].line_id : null);
      });

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = 'تعديل';
      editBtn.addEventListener('click', function () {
        openMaterialModal(mat);
      });

      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-danger-outline btn-sm';
      delBtn.textContent = 'حذف';
      delBtn.addEventListener('click', function () {
        openConfirmDialog(
          'حذف الخامة ' + mat.code,
          'هل أنت متأكد من حذف الخامة «' + mat.name + '» (كود ' + mat.code + ') من ' + plant.name + '؟',
          assignments.length > 0 ? 'سيتم إزالة ' + assignments.length + ' ارتباط خط مرتبط بهذه الخامة تلقائياً.' : '',
          [],
          function () {
            try {
              var res = Core.deleteMaterial(state.db, plant.id, mat.id);
              persistDb(res.db);
              showToast('تم حذف الخامة وجميع تعييناتها بنجاح.', 'success');
              renderAll();
            } catch (err) {
              showToast(err.message, 'error');
            }
          }
        );
      });

      actRow.appendChild(detailsBtn);
      actRow.appendChild(supplyBtn);
      actRow.appendChild(editBtn);
      actRow.appendChild(delBtn);
      tdActions.appendChild(actRow);

      tr.appendChild(tdCode);
      tr.appendChild(tdName);
      tr.appendChild(tdStock);
      tr.appendChild(tdLines);
      tr.appendChild(tdCount);
      tr.appendChild(tdActions);
      tbody.appendChild(tr);
    });
  }

  // -------------------------------------------------------------------------
  // VIEW 4: STOCK / WAREHOUSE (PER PLANT)
  // -------------------------------------------------------------------------
  function renderStockView() {
    var plant = getActivePlant();
    if (!plant) return;

    document.getElementById('stockPlantPill').textContent = 'النطاق: ' + plant.name;

    var stats = Core.getPlantStats(state.db, plant.id);
    var allMaterials = Core.getMaterialsByPlant(state.db, plant.id).sort(function (a, b) {
      return a.code.localeCompare(b.code, undefined, { numeric: true });
    });
    var lowCount = allMaterials.filter(Core.isLowStock).length;

    document.getElementById('stockKpiCount').textContent = String(allMaterials.length);
    document.getElementById('stockKpiTotal').textContent = String(stats.totalStock);
    document.getElementById('stockKpiLow').textContent = String(lowCount);
    document.getElementById('stockKpiPlan').textContent = stats.totalReceived + ' / ' + stats.totalPlan;

    var q = state.stockSearch.trim().toLowerCase();
    var visible = allMaterials.filter(function (m) {
      if (!q) return true;
      return m.code.toLowerCase().indexOf(q) !== -1 || m.name.toLowerCase().indexOf(q) !== -1;
    });

    var emptyEl = document.getElementById('stockEmptyState');
    var tableWrap = document.getElementById('stockTableWrap');
    var tbody = document.getElementById('stockTableBody');

    if (visible.length === 0) {
      tableWrap.classList.add('hidden');
      emptyEl.classList.remove('hidden');
      var emptyTitleEl = emptyEl.querySelector('.empty-title');
      var emptyDescEl = emptyEl.querySelector('.empty-desc');
      var emptyJumpBtn = emptyEl.querySelector('[data-jump-view="materials"]');
      if (allMaterials.length === 0) {
        if (emptyTitleEl) emptyTitleEl.textContent = 'لا يوجد مخزون مسجل في ' + plant.name;
        if (emptyDescEl) emptyDescEl.textContent = 'أضف خامات مع أرصدة مخزون لعرضها وإدارتها هنا.';
        if (emptyJumpBtn) emptyJumpBtn.classList.remove('hidden');
      } else {
        if (emptyTitleEl) emptyTitleEl.textContent = 'لا توجد خامات مطابقة في المخزون';
        if (emptyDescEl) emptyDescEl.textContent = 'لم يتم العثور على خامة تطابق "' + state.stockSearch + '" في ' + plant.name + '.';
        if (emptyJumpBtn) emptyJumpBtn.classList.add('hidden');
      }
      return;
    }

    emptyEl.classList.add('hidden');
    tableWrap.classList.remove('hidden');
    tbody.innerHTML = '';

    visible.forEach(function (mat) {
      var tr = document.createElement('tr');
      var low = Core.isLowStock(mat);

      var tdCode = document.createElement('td');
      var codeSpan = document.createElement('span');
      codeSpan.className = 'mono';
      codeSpan.style.fontWeight = '800';
      codeSpan.textContent = mat.code;
      tdCode.appendChild(codeSpan);

      var tdName = document.createElement('td');
      tdName.style.fontWeight = '700';
      tdName.textContent = mat.name;

      var tdAvail = document.createElement('td');
      var availSpan = document.createElement('span');
      availSpan.className = 'mono';
      availSpan.style.fontWeight = '800';
      availSpan.style.fontSize = '15px';
      availSpan.textContent = String(mat.stock_qty);
      tdAvail.appendChild(availSpan);

      var tdInit = document.createElement('td');
      var initSpan = document.createElement('span');
      initSpan.className = 'mono';
      initSpan.style.color = 'var(--text-muted)';
      initSpan.textContent = String(mat.initial_stock_qty);
      tdInit.appendChild(initSpan);

      var tdStatus = document.createElement('td');
      var stBadge = document.createElement('span');
      if (mat.stock_qty === 0) {
        stBadge.className = 'badge badge-danger';
        stBadge.textContent = 'نفد المخزون (0)';
      } else if (low) {
        stBadge.className = 'badge badge-warning';
        stBadge.textContent = 'تحذير: مخزون منخفض (< 10%)';
      } else {
        stBadge.className = 'badge badge-complete';
        stBadge.textContent = 'متوفر بالمخزن';
      }
      tdStatus.appendChild(stBadge);

      var tdAct = document.createElement('td');
      var actRow = document.createElement('div');
      actRow.style.display = 'flex';
      actRow.style.gap = '6px';
      actRow.style.flexWrap = 'wrap';

      var restockBtn = document.createElement('button');
      restockBtn.type = 'button';
      restockBtn.className = 'btn btn-secondary btn-sm';
      restockBtn.textContent = '+ إضافة رصيد';
      restockBtn.addEventListener('click', function () {
        openRestockModal(mat);
      });

      var supBtn = document.createElement('button');
      supBtn.type = 'button';
      supBtn.className = 'btn btn-success btn-sm';
      supBtn.textContent = 'توريد لخط';
      supBtn.addEventListener('click', function () {
        openSupplyModal(mat.id, null);
      });

      actRow.appendChild(restockBtn);
      actRow.appendChild(supBtn);
      tdAct.appendChild(actRow);

      tr.appendChild(tdCode);
      tr.appendChild(tdName);
      tr.appendChild(tdAvail);
      tr.appendChild(tdInit);
      tr.appendChild(tdStatus);
      tr.appendChild(tdAct);
      tbody.appendChild(tr);
    });
  }

  // -------------------------------------------------------------------------
  // VIEW 5: PLANTS MANAGEMENT
  // -------------------------------------------------------------------------
  function renderPlantsView() {
    var plants = Core.getPlants(state.db);
    var emptyState = document.getElementById('plantsEmptyState');
    var tableWrap = document.getElementById('plantsTableWrap');
    var tbody = document.getElementById('plantsTableBody');

    if (plants.length === 0) {
      tableWrap.classList.add('hidden');
      emptyState.classList.remove('hidden');
      return;
    }

    emptyState.classList.add('hidden');
    tableWrap.classList.remove('hidden');
    tbody.innerHTML = '';

    plants.forEach(function (plant) {
      var stats = Core.getPlantStats(state.db, plant.id);
      var isCurrent = state.activePlantId === plant.id;

      var tr = document.createElement('tr');

      var tdName = document.createElement('td');
      tdName.style.fontWeight = '700';
      tdName.textContent = plant.name;

      var tdLines = document.createElement('td');
      var lBadge = document.createElement('span');
      lBadge.className = 'badge badge-neutral mono';
      lBadge.textContent = String(stats.lineCount);
      tdLines.appendChild(lBadge);

      var tdMats = document.createElement('td');
      var mBadge = document.createElement('span');
      mBadge.className = 'badge badge-neutral mono';
      mBadge.textContent = String(stats.materialCount);
      tdMats.appendChild(mBadge);

      var tdStock = document.createElement('td');
      var sBadge = document.createElement('span');
      sBadge.className = 'badge badge-neutral mono';
      sBadge.textContent = String(stats.totalStock);
      tdStock.appendChild(sBadge);

      var tdStatus = document.createElement('td');
      var statusBadge = document.createElement('span');
      statusBadge.className = isCurrent ? 'badge badge-complete' : 'badge badge-empty';
      statusBadge.textContent = isCurrent ? 'المصنع النشط' : 'معزول';
      tdStatus.appendChild(statusBadge);

      var tdActions = document.createElement('td');
      var act = document.createElement('div');
      act.style.display = 'flex';
      act.style.gap = '6px';
      act.style.flexWrap = 'wrap';

      if (!isCurrent) {
        var selectBtn = document.createElement('button');
        selectBtn.type = 'button';
        selectBtn.className = 'btn btn-primary btn-sm';
        selectBtn.textContent = 'تفعيل';
        selectBtn.addEventListener('click', function () {
          setActivePlant(plant.id);
          switchView('filter');
          showToast('تم الانتقال إلى نطاق ' + plant.name, 'success');
        });
        act.appendChild(selectBtn);
      }

      var renameBtn = document.createElement('button');
      renameBtn.type = 'button';
      renameBtn.className = 'btn btn-secondary btn-sm';
      renameBtn.textContent = 'تعديل';
      renameBtn.addEventListener('click', function () {
        openPlantModal(plant);
      });

      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-danger-outline btn-sm';
      delBtn.textContent = 'حذف';
      delBtn.addEventListener('click', function () {
        var warn = 'سيتم حذف جميع بيانات هذا المصنع نهائياً: ' +
          stats.lineCount + ' خط إنتاج، و' +
          stats.materialCount + ' خامة، ومخزونها (' + stats.totalStock + ')، و' +
          stats.assignmentCount + ' تعيين.';
        openConfirmDialog(
          'حذف المصنع: ' + plant.name,
          'هل أنت متأكد من حذف المصنع «' + plant.name + '» بالكامل؟',
          warn,
          [],
          function () {
            try {
              var res = Core.deletePlant(state.db, plant.id);
              persistDb(res.db);
              if (state.activePlantId === plant.id) {
                var nextPlant = Core.getPlants(state.db)[0] || null;
                setActivePlant(nextPlant ? nextPlant.id : null);
              } else {
                renderAll();
              }
              showToast('تم حذف المصنع وكافة بياناته المعزولة بنجاح.', 'success');
            } catch (err) {
              showToast(err.message, 'error');
            }
          }
        );
      });

      act.appendChild(renameBtn);
      act.appendChild(delBtn);
      tdActions.appendChild(act);

      tr.appendChild(tdName);
      tr.appendChild(tdLines);
      tr.appendChild(tdMats);
      tr.appendChild(tdStock);
      tr.appendChild(tdStatus);
      tr.appendChild(tdActions);
      tbody.appendChild(tr);
    });
  }

  // =========================================================================
  // MODALS & FORM CONTROLLERS
  // =========================================================================

  function openPlantModal(plant) {
    document.getElementById('plantEditId').value = plant ? String(plant.id) : '';
    document.getElementById('plantModalTitle').textContent = plant ? 'تعديل اسم المصنع' : 'إضافة مصنع جديد';
    var input = document.getElementById('plantNameInput');
    input.value = plant ? plant.name : '';
    showFieldError('plantNameError', input, '');
    openModal('plantModal');
    setTimeout(function () {
      input.focus();
      if (plant) input.select();
    }, 30);
  }

  function openLineModal(line) {
    var plant = getActivePlant();
    if (!plant) {
      showToast('يجب اختيار مصنع أولاً.', 'error');
      return;
    }
    var nameInput = document.getElementById('lineNameInput');
    var planInput = document.getElementById('linePlanQtyInput');
    var planClearBtn = document.getElementById('linePlanQtyClearBtn');

    showFieldError('lineNameError', nameInput, '');
    showFieldError('linePlanQtyError', planInput, '');

    if (line) {
      state.isEditingLine = true;
      document.getElementById('lineEditId').value = String(line.id);
      document.getElementById('lineModalTitle').textContent = 'تعديل خط الإنتاج: ' + line.name;
      nameInput.value = line.name;
      planInput.value = String(line.plan_qty);
      planClearBtn.classList.toggle('hidden', !planInput.value);
    } else {
      // CRITICAL NUMERIC INPUT RULE: opens completely empty!
      state.isEditingLine = false;
      document.getElementById('lineEditId').value = '';
      document.getElementById('lineModalTitle').textContent = 'إضافة خط إنتاج جديد (' + plant.name + ')';
      nameInput.value = '';
      planInput.value = '';
      planClearBtn.classList.add('hidden');
    }

    openModal('lineModal');
    setTimeout(function () {
      nameInput.focus();
      if (line) nameInput.select();
    }, 30);
  }

  function promptDeleteLine(plant, line) {
    var details = Core.getLineDeletionDetails(state.db, plant.id, line.id);
    var attached = details.attachedMaterials;
    var warn = '';
    var listItems = [];

    if (attached.length > 0) {
      warn = 'تحذير: هذا الخط مرتبط بـ ' + attached.length + ' خامة. عند الحذف سيتم إزالة جميع ارتباطاته وإرجاع الكميات المورّدة (' + details.totalRollbackQty + ') إلى مخزون الخامات تلقائياً:';
      listItems = attached.map(function (m) {
        return m.code + ' — ' + m.name + (m.supplied_qty > 0 ? ' (سيتم إرجاع ' + m.supplied_qty + ' للمخزون)' : '');
      });
    }

    openConfirmDialog(
      'حذف خط الإنتاج: ' + line.name,
      'هل تريد حذف خط الإنتاج «' + line.name + '» من ' + plant.name + '؟',
      warn,
      listItems,
      function () {
        try {
          var res = Core.deleteLine(state.db, plant.id, line.id);
          persistDb(res.db);
          var msg = 'تم حذف الخط وإزالة ' + res.removedAssignments + ' ارتباط.';
          if (res.rolledBackQty > 0) {
            msg += ' وتمت إعادة ' + res.rolledBackQty + ' إلى المخزون.';
          }
          showToast(msg, 'success');
          renderAll();
        } catch (err) {
          showToast(err.message, 'error');
        }
      }
    );
  }

  function openMaterialModal(material) {
    var plant = getActivePlant();
    if (!plant) {
      showToast('يجب اختيار مصنع أولاً.', 'error');
      return;
    }

    var codeInput = document.getElementById('materialCodeInput');
    var codeClearBtn = document.getElementById('materialCodeClearBtn');
    var stockInput = document.getElementById('materialStockInput');
    var stockClearBtn = document.getElementById('materialStockClearBtn');
    var nameInput = document.getElementById('materialNameInput');
    var descInput = document.getElementById('materialDescInput');
    var lineSearchInput = document.getElementById('materialLineSearchInput');

    showFieldError('materialCodeError', codeInput, '');
    showFieldError('materialStockError', stockInput, '');
    showFieldError('materialNameError', nameInput, '');
    showFieldError('materialLinesError', null, '');

    state.materialModalLineSearch = '';
    lineSearchInput.value = '';

    if (material) {
      state.isEditingMaterial = true;
      document.getElementById('materialEditId').value = String(material.id);
      document.getElementById('materialModalTitle').textContent = 'تعديل الخامة (' + material.code + ')';
      codeInput.value = material.code;
      stockInput.value = String(material.stock_qty);
      nameInput.value = material.name;
      descInput.value = material.description || '';
      codeClearBtn.classList.toggle('hidden', !codeInput.value);
      stockClearBtn.classList.toggle('hidden', !stockInput.value);
      state.materialModalLineIds = Core.getLinesForMaterial(state.db, material.id, plant.id).map(function (l) {
        return l.id;
      });
    } else {
      // CRITICAL NUMERIC INPUT RULE:
      // Code & Stock inputs open COMPLETELY EMPTY — NO default numbers, grey placeholders only!
      state.isEditingMaterial = false;
      document.getElementById('materialEditId').value = '';
      document.getElementById('materialModalTitle').textContent = 'إضافة خامة جديدة (' + plant.name + ')';
      codeInput.value = '';
      stockInput.value = '';
      nameInput.value = '';
      descInput.value = '';
      codeClearBtn.classList.add('hidden');
      stockClearBtn.classList.add('hidden');
      state.materialModalLineIds = [];
    }

    renderMaterialLinesChecklist();
    openModal('materialModal');

    setTimeout(function () {
      codeInput.focus();
      if (material) {
        codeInput.select();
      }
    }, 30);
  }

  function renderMaterialLinesChecklist() {
    var plant = getActivePlant();
    var listEl = document.getElementById('materialLinesChecklist');
    listEl.innerHTML = '';
    if (!plant) return;

    var plantLines = Core.getLinesByPlant(state.db, plant.id);
    var q = state.materialModalLineSearch.trim().toLowerCase();
    var filtered = plantLines.filter(function (l) {
      return !q || l.name.toLowerCase().indexOf(q) !== -1;
    });

    if (plantLines.length === 0) {
      var msg = document.createElement('div');
      msg.style.padding = '10px';
      msg.style.fontSize = '13px';
      msg.style.color = 'var(--text-muted)';
      msg.textContent = 'لا توجد خطوط إنتاج مسجلة في ' + plant.name + ' بعد. يمكنك الحفظ الآن وإضافة الخطوط لاحقاً.';
      listEl.appendChild(msg);
      return;
    }

    if (filtered.length === 0) {
      var noRes = document.createElement('div');
      noRes.style.padding = '10px';
      noRes.style.fontSize = '13px';
      noRes.style.color = 'var(--text-muted)';
      noRes.textContent = 'لا يوجد خط يطابق البحث داخل ' + plant.name + '.';
      listEl.appendChild(noRes);
      return;
    }

    filtered.forEach(function (line) {
      var label = document.createElement('label');
      label.className = 'checkbox-option';

      var main = document.createElement('span');
      main.className = 'checkbox-option-main';

      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = state.materialModalLineIds.indexOf(line.id) !== -1;
      cb.addEventListener('change', function () {
        if (cb.checked) {
          if (state.materialModalLineIds.indexOf(line.id) === -1) {
            state.materialModalLineIds.push(line.id);
          }
        } else {
          state.materialModalLineIds = state.materialModalLineIds.filter(function (id) { return id !== line.id; });
        }
      });

      var nameSpan = document.createElement('span');
      nameSpan.textContent = line.name;
      nameSpan.style.fontWeight = '600';

      main.appendChild(cb);
      main.appendChild(nameSpan);

      var orderTag = document.createElement('span');
      orderTag.className = 'badge badge-neutral mono';
      orderTag.textContent = '#' + line.order;

      label.appendChild(main);
      label.appendChild(orderTag);
      listEl.appendChild(label);
    });
  }

  // -------------------------------------------------------------------------
  // MATERIAL DETAILS & FULL ASSIGNMENT EDITOR MODAL (SECTION 4)
  // -------------------------------------------------------------------------
  function openMaterialDetailsModal(materialId) {
    state.detailsMaterialId = Number(materialId);
    state.detailsAddLineSearch = '';
    state.detailsAddLineIds = [];
    document.getElementById('detailsAddLineSearchInput').value = '';
    renderMaterialDetailsModalContent();
    openModal('materialDetailsModal');
  }

  function renderMaterialDetailsModalContent() {
    var plant = getActivePlant();
    if (!plant || !state.detailsMaterialId) return;

    var mat = Core.getMaterialsByPlant(state.db, plant.id).find(function (m) {
      return m.id === state.detailsMaterialId;
    });
    if (!mat) {
      closeModal('materialDetailsModal');
      return;
    }

    document.getElementById('detailsMatCodeBadge').textContent = mat.code;
    document.getElementById('detailsMatName').textContent = mat.name;
    document.getElementById('detailsMatDesc').textContent = mat.description || 'بدون وصف إضافي';
    var stockBadge = document.getElementById('detailsMatStockBadge');
    stockBadge.textContent = 'المخزون المتاح: ' + mat.stock_qty;
    stockBadge.className = 'badge mono ' + (mat.stock_qty === 0 ? 'badge-danger' : (Core.isLowStock(mat) ? 'badge-warning' : 'badge-complete'));

    var assignments = Core.getAssignmentsForMaterial(state.db, mat.id, plant.id);
    document.getElementById('detailsAssignedCount').textContent = String(assignments.length);

    var removeAllBtn = document.getElementById('detailsRemoveAllLinesBtn');
    var zeroBox = document.getElementById('detailsZeroLinesBox');
    var listEl = document.getElementById('detailsAssignedLinesList');
    listEl.innerHTML = '';

    if (assignments.length === 0) {
      removeAllBtn.classList.add('hidden');
      zeroBox.classList.remove('hidden');
    } else {
      removeAllBtn.classList.remove('hidden');
      zeroBox.classList.add('hidden');

      assignments.forEach(function (entry) {
        var row = document.createElement('div');
        row.className = 'assigned-line-row';

        var info = document.createElement('div');
        info.className = 'assigned-line-info';

        var titleLine = document.createElement('div');
        titleLine.style.display = 'flex';
        titleLine.style.alignItems = 'center';
        titleLine.style.gap = '8px';
        titleLine.style.flexWrap = 'wrap';

        var orderB = document.createElement('span');
        orderB.className = 'badge badge-neutral mono';
        orderB.textContent = '#' + entry.line.order;

        var lName = document.createElement('strong');
        lName.textContent = entry.line.name;

        titleLine.appendChild(orderB);
        titleLine.appendChild(lName);
        titleLine.appendChild(buildLineStatusBadge(entry.line));

        var metaLine = document.createElement('div');
        metaLine.style.fontSize = '12.5px';
        metaLine.style.color = 'var(--text-muted)';
        metaLine.textContent = 'الكمية المورّدة لهذه الخامة على هذا الخط: ' + entry.supplied_qty;

        info.appendChild(titleLine);
        info.appendChild(metaLine);

        var actions = document.createElement('div');
        actions.className = 'assigned-line-actions';

        var supBtn = document.createElement('button');
        supBtn.type = 'button';
        supBtn.className = 'btn btn-success btn-sm';
        supBtn.textContent = '+ توريد';
        supBtn.addEventListener('click', function () {
          openSupplyModal(mat.id, entry.line.id);
        });

        // Instant "X" button to unassign ONLY this single line
        var removeXBtn = document.createElement('button');
        removeXBtn.type = 'button';
        removeXBtn.className = 'remove-line-x-btn';
        removeXBtn.textContent = '✕';
        removeXBtn.title = 'إزالة ارتباط هذا الخط فقط';
        removeXBtn.setAttribute('aria-label', 'إزالة ' + entry.line.name);
        removeXBtn.addEventListener('click', function () {
          try {
            var res = Core.removeLineFromMaterial(state.db, plant.id, mat.id, entry.line.id);
            persistDb(res.db);
            var msg = 'تمت إزالة الخط «' + entry.line.name + '» من الخامة ' + mat.code + ' بنجاح.';
            if (res.rolledBackQty > 0) {
              msg += ' وتمت إعادة ' + res.rolledBackQty + ' للمخزون.';
            }
            showToast(msg, 'success');
            renderAll();
          } catch (err) {
            showToast(err.message, 'error');
          }
        });

        actions.appendChild(supBtn);
        actions.appendChild(removeXBtn);

        row.appendChild(info);
        row.appendChild(actions);
        listEl.appendChild(row);
      });
    }

    // Available unassigned lines of CURRENT plant sorted by order
    var assignedSet = Object.create(null);
    assignments.forEach(function (a) { assignedSet[a.line_id] = true; });

    var allLines = Core.getLinesByPlant(state.db, plant.id);
    var availableLines = allLines.filter(function (l) { return !assignedSet[l.id]; });

    var availSet = Object.create(null);
    availableLines.forEach(function (l) { availSet[l.id] = true; });
    state.detailsAddLineIds = state.detailsAddLineIds.filter(function (id) { return !!availSet[id]; });

    var q = state.detailsAddLineSearch.trim().toLowerCase();
    var filteredAvail = availableLines.filter(function (l) {
      return !q || l.name.toLowerCase().indexOf(q) !== -1;
    });

    var addListEl = document.getElementById('detailsAddLinesChecklist');
    addListEl.innerHTML = '';

    if (allLines.length === 0) {
      var noPlantLines = document.createElement('div');
      noPlantLines.style.padding = '10px';
      noPlantLines.style.fontSize = '13px';
      noPlantLines.style.color = 'var(--text-muted)';
      noPlantLines.textContent = 'لا توجد خطوط إنتاج في ' + plant.name + '. اضغط «إدارة الخطوط ↗» لإضافة خط.';
      addListEl.appendChild(noPlantLines);
    } else if (availableLines.length === 0) {
      var allAssignedMsg = document.createElement('div');
      allAssignedMsg.style.padding = '10px';
      allAssignedMsg.style.fontSize = '13px';
      allAssignedMsg.style.color = 'var(--text-muted)';
      allAssignedMsg.textContent = 'هذه الخامة مرتبطة بالفعل بجميع خطوط ' + plant.name + '.';
      addListEl.appendChild(allAssignedMsg);
    } else if (filteredAvail.length === 0) {
      var noSearchMatch = document.createElement('div');
      noSearchMatch.style.padding = '10px';
      noSearchMatch.style.fontSize = '13px';
      noSearchMatch.style.color = 'var(--text-muted)';
      noSearchMatch.textContent = 'لا يوجد خط غير مرتبط يطابق البحث.';
      addListEl.appendChild(noSearchMatch);
    } else {
      filteredAvail.forEach(function (line) {
        var label = document.createElement('label');
        label.className = 'checkbox-option';

        var main = document.createElement('span');
        main.className = 'checkbox-option-main';

        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = state.detailsAddLineIds.indexOf(line.id) !== -1;
        cb.addEventListener('change', function () {
          if (cb.checked) {
            if (state.detailsAddLineIds.indexOf(line.id) === -1) {
              state.detailsAddLineIds.push(line.id);
            }
          } else {
            state.detailsAddLineIds = state.detailsAddLineIds.filter(function (id) { return id !== line.id; });
          }
        });

        var nameSpan = document.createElement('span');
        nameSpan.textContent = line.name;
        nameSpan.style.fontWeight = '600';

        main.appendChild(cb);
        main.appendChild(nameSpan);

        var orderTag = document.createElement('span');
        orderTag.className = 'badge badge-neutral mono';
        orderTag.textContent = '#' + line.order;

        label.appendChild(main);
        label.appendChild(orderTag);
        addListEl.appendChild(label);
      });
    }
  }

  // -------------------------------------------------------------------------
  // SUPPLY QUANTITY MODAL (STRICT EMPTY INPUT + LIVE PREVIEW)
  // -------------------------------------------------------------------------
  function openSupplyModal(preferredMaterialId, preferredLineId) {
    var plant = getActivePlant();
    if (!plant) {
      showToast('يجب اختيار مصنع أولاً.', 'error');
      return;
    }

    var materials = Core.getMaterialsByPlant(state.db, plant.id).sort(function (a, b) {
      return a.code.localeCompare(b.code, undefined, { numeric: true });
    });
    var lines = Core.getLinesByPlant(state.db, plant.id);

    if (materials.length === 0) {
      showToast('أضف خامة واحدة على الأقل في ' + plant.name + ' قبل التوريد.', 'error');
      return;
    }
    if (lines.length === 0) {
      showToast('أضف خط إنتاج واحداً على الأقل في ' + plant.name + ' قبل التوريد.', 'error');
      return;
    }

    var matSelect = document.getElementById('supplyMaterialSelect');
    matSelect.innerHTML = '';
    materials.forEach(function (m) {
      var opt = document.createElement('option');
      opt.value = String(m.id);
      opt.textContent = m.code + ' — ' + m.name + ' (المخزون: ' + m.stock_qty + ')';
      matSelect.appendChild(opt);
    });
    if (preferredMaterialId && materials.some(function (m) { return m.id === Number(preferredMaterialId); })) {
      matSelect.value = String(preferredMaterialId);
    } else {
      matSelect.value = String(materials[0].id);
    }

    document.getElementById('supplyLineSelect').innerHTML = '';
    populateSupplyLinesDropdown(preferredLineId);

    // CRITICAL NUMERIC INPUT RULE: ALWAYS opens completely empty every single time!
    var qtyInput = document.getElementById('supplyQtyInput');
    var clearBtn = document.getElementById('supplyQtyClearBtn');
    qtyInput.value = '';
    clearBtn.classList.add('hidden');
    showFieldError('supplyQtyError', qtyInput, '');

    updateSupplyLivePreview();
    openModal('supplyModal');

    setTimeout(function () {
      qtyInput.focus();
    }, 30);
  }

  function populateSupplyLinesDropdown(preferredLineId) {
    var plant = getActivePlant();
    if (!plant) return;

    var matId = Number(document.getElementById('supplyMaterialSelect').value);
    var assignedSet = Object.create(null);
    Core.getLinesForMaterial(state.db, matId, plant.id).forEach(function (l) {
      assignedSet[l.id] = true;
    });

    var lines = Core.getLinesByPlant(state.db, plant.id);
    var lineSelect = document.getElementById('supplyLineSelect');
    var prevVal = preferredLineId ? String(preferredLineId) : lineSelect.value;

    lineSelect.innerHTML = '';
    lines.forEach(function (l) {
      var opt = document.createElement('option');
      opt.value = String(l.id);
      var statusInfo = Core.getLineStatus(l);
      var tag = assignedSet[l.id] ? ' [مرتبط]' : '';
      opt.textContent = '#' + l.order + ' ' + l.name + tag + ' (' + statusInfo.received_qty + ' / ' + statusInfo.plan_qty + ')';
      lineSelect.appendChild(opt);
    });

    var firstAssigned = lines.find(function (l) { return !!assignedSet[l.id]; });
    var hasPrevInLines = prevVal && lines.some(function (l) { return String(l.id) === prevVal; });
    if (preferredLineId && hasPrevInLines) {
      lineSelect.value = prevVal;
    } else if (hasPrevInLines && (assignedSet[Number(prevVal)] || !firstAssigned)) {
      lineSelect.value = prevVal;
    } else {
      lineSelect.value = String(firstAssigned ? firstAssigned.id : lines[0].id);
    }
  }

  function updateSupplyLivePreview() {
    var plant = getActivePlant();
    if (!plant) return;

    var matId = Number(document.getElementById('supplyMaterialSelect').value);
    var lineId = Number(document.getElementById('supplyLineSelect').value);

    var mat = Core.getMaterialsByPlant(state.db, plant.id).find(function (m) { return m.id === matId; });
    var line = Core.getLinesByPlant(state.db, plant.id).find(function (l) { return l.id === lineId; });

    if (!mat || !line) return;

    var st = Core.getLineStatus(line);
    var badgeEl = document.getElementById('supplyLineStatusBadge');
    badgeEl.className = 'badge ' + (st.status === 'complete' ? 'badge-complete' : (st.status === 'in_progress' ? 'badge-progress' : 'badge-empty'));
    badgeEl.textContent = st.label;

    var fillEl = document.getElementById('supplyLineProgressFill');
    fillEl.className = 'progress-fill ' + st.status;
    fillEl.style.width = st.percent + '%';

    document.getElementById('supplyPlanVal').textContent = String(st.plan_qty);
    document.getElementById('supplyReceivedVal').textContent = String(st.received_qty);
    document.getElementById('supplyRemainingVal').textContent = String(st.remaining);
    document.getElementById('supplyStockVal').textContent = String(mat.stock_qty);
  }

  function openRestockModal(material) {
    document.getElementById('restockMaterialId').value = String(material.id);
    document.getElementById('restockMaterialLabel').textContent =
      'الخامة: ' + material.code + ' — ' + material.name + ' (الرصيد الحالي: ' + material.stock_qty + ')';
    var input = document.getElementById('restockQtyInput');
    var clearBtn = document.getElementById('restockQtyClearBtn');
    // CRITICAL: opens completely empty!
    input.value = '';
    clearBtn.classList.add('hidden');
    showFieldError('restockQtyError', input, '');
    openModal('restockModal');
    setTimeout(function () { input.focus(); }, 30);
  }

  function openConfirmDialog(title, message, warningText, listItems, onConfirm) {
    document.getElementById('confirmModalTitle').textContent = title;
    document.getElementById('confirmModalMessage').textContent = message;

    var warnEl = document.getElementById('confirmModalWarning');
    if (warningText) {
      warnEl.textContent = warningText;
      warnEl.classList.remove('hidden');
    } else {
      warnEl.textContent = '';
      warnEl.classList.add('hidden');
    }

    var listEl = document.getElementById('confirmModalList');
    listEl.innerHTML = '';
    if (Array.isArray(listItems) && listItems.length > 0) {
      listItems.forEach(function (txt) {
        var li = document.createElement('li');
        li.textContent = txt;
        listEl.appendChild(li);
      });
      listEl.classList.remove('hidden');
    } else {
      listEl.classList.add('hidden');
    }

    state.confirmCallback = onConfirm;
    openModal('confirmModal');
  }

  function loadDemoScenario() {
    var db = Core.createEmptyDb();

    // Plant 7 with Volvo Lift, Volvo Right, Line X, Line Y (each plan_qty: 500)
    var p7Res = Core.createPlant(db, 'Plant 7');
    db = p7Res.db;
    var p7 = p7Res.plant;

    var lLift = Core.createLine(db, p7.id, { name: 'Volvo Lift', plan_qty: 500 }); db = lLift.db;
    var lRight = Core.createLine(db, p7.id, { name: 'Volvo Right', plan_qty: 500 }); db = lRight.db;
    var lX = Core.createLine(db, p7.id, { name: 'Line X', plan_qty: 500 }); db = lX.db;
    var lY = Core.createLine(db, p7.id, { name: 'Line Y', plan_qty: 500 }); db = lY.db;

    db = Core.saveMaterial(db, p7.id, {
      code: '100',
      name: 'حامل رفع فولفو (مصنع 7)',
      description: 'خامة مصنع 7 الحصرية لخط Volvo Lift',
      stock_qty: 2000,
      lineIds: [lLift.line.id]
    }).db;
    db = Core.saveMaterial(db, p7.id, {
      code: '200',
      name: 'طقم تثبيت مشترك',
      description: 'يعمل على Volvo Lift و Volvo Right',
      stock_qty: 1500,
      lineIds: [lLift.line.id, lRight.line.id]
    }).db;
    db = Core.saveMaterial(db, p7.id, {
      code: '300',
      name: 'موصل هيدروليك متعدد الخطوط',
      description: 'مشترك بين Volvo Lift و Line X و Line Y',
      stock_qty: 3000,
      lineIds: [lLift.line.id, lX.line.id, lY.line.id]
    }).db;
    db = Core.saveMaterial(db, p7.id, {
      code: '400',
      name: 'صامولة احتياطية غير معينة',
      description: 'مثال لخامة بدون أي خطوط حالياً',
      stock_qty: 1000,
      lineIds: []
    }).db;

    // Plant 2 with Volvo Right + identical code "100" completely isolated
    var p2Res = Core.createPlant(db, 'Plant 2');
    db = p2Res.db;
    var p2 = p2Res.plant;

    var p2Right = Core.createLine(db, p2.id, { name: 'Volvo Right', plan_qty: 500 }); db = p2Right.db;
    db = Core.saveMaterial(db, p2.id, {
      code: '100',
      name: 'حساس يمين مستقل (مصنع 2)',
      description: 'كيان منفصل تماماً بمخزونه وخطته عن Plant 7',
      stock_qty: 2000,
      lineIds: [p2Right.line.id]
    }).db;

    persistDb(db);
    setActivePlant(p7.id);
    switchView('filter');
    showToast('تم تحميل Plant 7 و Plant 2 بالبيانات التجريبية المعزولة.', 'success');
  }

  // =========================================================================
  // NUMERIC INPUT ENFORCER HELPER (STRICT EMPTY + DIGITS ONLY + CLEAR + SELECT)
  // =========================================================================
  function wireNumericInput(inputId, clearBtnId, errorId, shouldAutoSelectFn) {
    var inputEl = document.getElementById(inputId);
    var clearBtnEl = document.getElementById(clearBtnId);

    inputEl.addEventListener('input', function () {
      var clean = Core.sanitizeNumericInput(inputEl.value);
      if (inputEl.value !== clean) {
        inputEl.value = clean;
      }
      clearBtnEl.classList.toggle('hidden', clean.length === 0);
      showFieldError(errorId, inputEl, '');
    });

    inputEl.addEventListener('focus', function () {
      if (typeof shouldAutoSelectFn === 'function' && shouldAutoSelectFn() && inputEl.value.length > 0) {
        setTimeout(function () { inputEl.select(); }, 0);
      }
    });

    clearBtnEl.addEventListener('click', function () {
      inputEl.value = '';
      clearBtnEl.classList.add('hidden');
      showFieldError(errorId, inputEl, '');
      inputEl.focus();
    });
  }

  // =========================================================================
  // EVENT WIRING
  // =========================================================================
  function bindEvents() {
    document.getElementById('activePlantSelect').addEventListener('change', function (e) {
      setActivePlant(e.target.value ? Number(e.target.value) : null);
    });

    document.querySelectorAll('.nav-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchView(btn.getAttribute('data-view'));
      });
    });

    document.querySelectorAll('[data-jump-view]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchView(btn.getAttribute('data-jump-view'));
      });
    });

    document.getElementById('modalManageLinesLink').addEventListener('click', function () {
      closeModal('materialModal');
      switchView('lines');
    });

    document.getElementById('detailsManageLinesLink').addEventListener('click', function () {
      closeModal('materialDetailsModal');
      switchView('lines');
    });

    ['headerAddPlantBtn', 'gateCreatePlantBtn', 'plantsAddBtn', 'plantsEmptyAddBtn'].forEach(function (id) {
      document.getElementById(id).addEventListener('click', function () { openPlantModal(null); });
    });

    ['headerSupplyBtn', 'materialsSupplyBtn', 'stockSupplyBtn'].forEach(function (id) {
      document.getElementById(id).addEventListener('click', function () { openSupplyModal(null, null); });
    });

    ['gateLoadDemoBtn', 'plantsLoadDemoBtn'].forEach(function (id) {
      document.getElementById(id).addEventListener('click', loadDemoScenario);
    });

    document.querySelectorAll('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        closeModal(btn.getAttribute('data-close-modal'));
      });
    });

    document.querySelectorAll('.modal-backdrop').forEach(function (backdrop) {
      backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) backdrop.classList.add('hidden');
      });
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        document.querySelectorAll('.modal-backdrop:not(.hidden)').forEach(function (m) {
          m.classList.add('hidden');
        });
        if (state.filterDropdownOpen) {
          state.filterDropdownOpen = false;
          renderFilterView();
        }
      }
    });

    document.addEventListener('click', function (e) {
      var box = document.getElementById('filterLinesBox');
      if (state.filterDropdownOpen && box && !box.contains(e.target)) {
        state.filterDropdownOpen = false;
        renderFilterView();
      }
    });

    // Wire ALL numeric inputs with strict empty/digits-only/clear/auto-select rules
    wireNumericInput('linePlanQtyInput', 'linePlanQtyClearBtn', 'linePlanQtyError', function () {
      return state.isEditingLine;
    });
    wireNumericInput('materialCodeInput', 'materialCodeClearBtn', 'materialCodeError', function () {
      return state.isEditingMaterial;
    });
    wireNumericInput('materialStockInput', 'materialStockClearBtn', 'materialStockError', function () {
      return state.isEditingMaterial;
    });
    wireNumericInput('supplyQtyInput', 'supplyQtyClearBtn', 'supplyQtyError', function () {
      return false;
    });
    wireNumericInput('restockQtyInput', 'restockQtyClearBtn', 'restockQtyError', function () {
      return false;
    });

    // Plant Form Submit
    document.getElementById('plantForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var idVal = document.getElementById('plantEditId').value;
      var input = document.getElementById('plantNameInput');
      try {
        if (idVal) {
          var res = Core.renamePlant(state.db, Number(idVal), input.value);
          persistDb(res.db);
          showToast('تم تحديث اسم المصنع بنجاح.', 'success');
        } else {
          var created = Core.createPlant(state.db, input.value);
          persistDb(created.db);
          setActivePlant(created.plant.id);
          showToast('تم إنشاء المصنع «' + created.plant.name + '» بنجاح.', 'success');
        }
        closeModal('plantModal');
        renderAll();
      } catch (err) {
        showFieldError('plantNameError', input, err.message);
      }
    });

    // Filter Controls
    document.getElementById('filterLinesTrigger').addEventListener('click', function () {
      state.filterDropdownOpen = !state.filterDropdownOpen;
      renderFilterView();
      if (state.filterDropdownOpen) {
        setTimeout(function () { document.getElementById('filterLineSearchInput').focus(); }, 20);
      }
    });

    document.getElementById('filterDropdownDoneBtn').addEventListener('click', function () {
      state.filterDropdownOpen = false;
      renderFilterView();
    });

    document.getElementById('filterLineSearchInput').addEventListener('input', function (e) {
      state.filterLineSearch = e.target.value;
      renderFilterView();
    });

    document.getElementById('filterSelectAllLinesBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant) return;
      var q = state.filterLineSearch.trim().toLowerCase();
      Core.getLinesByPlant(state.db, plant.id)
        .filter(function (l) { return !q || l.name.toLowerCase().indexOf(q) !== -1; })
        .forEach(function (l) {
          if (state.filterSelectedLineIds.indexOf(l.id) === -1) state.filterSelectedLineIds.push(l.id);
        });
      renderFilterView();
    });

    document.getElementById('filterClearLinesBtn').addEventListener('click', function () {
      state.filterSelectedLineIds = [];
      renderFilterView();
    });

    document.querySelectorAll('input[name="filterMode"]').forEach(function (radio) {
      radio.addEventListener('change', function () {
        if (radio.checked) state.filterMode = radio.value;
      });
    });

    document.getElementById('applyFilterBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant) return;
      if (state.filterSelectedLineIds.length === 0) {
        showToast('يرجى تحديد خط إنتاج واحد على الأقل قبل تطبيق الفلتر.', 'error');
        return;
      }
      state.filterDropdownOpen = false;
      state.filterApplied = true;
      state.filterAppliedLineIds = state.filterSelectedLineIds.slice();
      state.filterAppliedMode = state.filterMode;
      renderFilterView();
    });

    function resetFilter() {
      state.filterSelectedLineIds = [];
      state.filterMode = 'both';
      state.filterApplied = false;
      state.filterAppliedLineIds = [];
      state.filterLineSearch = '';
      document.getElementById('filterLineSearchInput').value = '';
      renderFilterView();
    }

    document.getElementById('resetFilterBtn').addEventListener('click', resetFilter);
    document.getElementById('emptyResetFilterBtn').addEventListener('click', resetFilter);

    document.querySelectorAll('.sort-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var key = btn.getAttribute('data-sort-key');
        if (state.filterSort.key === key) {
          state.filterSort.dir = state.filterSort.dir === 'asc' ? 'desc' : 'asc';
        } else {
          state.filterSort.key = key;
          state.filterSort.dir = 'asc';
        }
        renderFilterView();
      });
    });

    // Line Management Controls
    document.getElementById('lineListSearchInput').addEventListener('input', function (e) {
      state.lineSearch = e.target.value;
      renderLinesView();
    });

    ['addLineBtn', 'linesEmptyAddBtn'].forEach(function (id) {
      document.getElementById(id).addEventListener('click', function () { openLineModal(null); });
    });

    document.getElementById('lineNameInput').addEventListener('input', function (e) {
      showFieldError('lineNameError', e.target, '');
    });

    document.getElementById('lineForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var plant = getActivePlant();
      if (!plant) return;
      var idVal = document.getElementById('lineEditId').value;
      var nameInput = document.getElementById('lineNameInput');
      var planInput = document.getElementById('linePlanQtyInput');

      var hasError = false;
      if (!nameInput.value.trim()) {
        showFieldError('lineNameError', nameInput, 'اسم خط الإنتاج مطلوب.');
        hasError = true;
      }
      var planCheck = Core.validateRequiredNumber(planInput.value, { min: 1 });
      if (!planCheck.valid) {
        showFieldError('linePlanQtyError', planInput, planCheck.error);
        hasError = true;
      }
      if (hasError) return;

      try {
        if (idVal) {
          var updated = Core.updateLine(state.db, plant.id, Number(idVal), {
            name: nameInput.value,
            plan_qty: planCheck.number
          });
          persistDb(updated.db);
          showToast('تم تحديث خط الإنتاج وخطته وجميع ارتباطاته بنجاح.', 'success');
        } else {
          var created = Core.createLine(state.db, plant.id, {
            name: nameInput.value,
            plan_qty: planCheck.number
          });
          persistDb(created.db);
          showToast('تم إضافة خط الإنتاج «' + created.line.name + '» بخطة ' + created.line.plan_qty + '.', 'success');
        }
        closeModal('lineModal');
        renderAll();
      } catch (err) {
        if (err.message.indexOf('الخطة') !== -1 || err.message.indexOf('رقم') !== -1) {
          showFieldError('linePlanQtyError', planInput, err.message);
        } else {
          showFieldError('lineNameError', nameInput, err.message);
        }
      }
    });

    document.getElementById('linesSelectAllCheckbox').addEventListener('change', function (e) {
      var plant = getActivePlant();
      if (!plant) return;
      var q = state.lineSearch.trim().toLowerCase();
      var visibleIds = Core.getLinesByPlant(state.db, plant.id)
        .filter(function (l) { return !q || l.name.toLowerCase().indexOf(q) !== -1; })
        .map(function (l) { return l.id; });

      if (e.target.checked) {
        visibleIds.forEach(function (id) {
          if (state.bulkLineIds.indexOf(id) === -1) state.bulkLineIds.push(id);
        });
      } else {
        state.bulkLineIds = state.bulkLineIds.filter(function (id) {
          return visibleIds.indexOf(id) === -1;
        });
      }
      renderLinesView();
    });

    document.getElementById('bulkClearLinesSelectionBtn').addEventListener('click', function () {
      state.bulkLineIds = [];
      renderLinesView();
    });

    document.getElementById('bulkDeleteLinesBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant || state.bulkLineIds.length === 0) return;
      var count = state.bulkLineIds.length;
      openConfirmDialog(
        'حذف ' + count + ' خطوط إنتاج',
        'هل أنت متأكد من حذف الخطوط المحددة من ' + plant.name + '؟',
        'سيتم إزالة جميع تعيينات الخامات المرتبطة بهذه الخطوط وإرجاع أي كميات مورّدة إلى مخزون الخامات تلقائياً.',
        [],
        function () {
          try {
            var res = Core.bulkDeleteLines(state.db, plant.id, state.bulkLineIds);
            persistDb(res.db);
            state.bulkLineIds = [];
            showToast('تم حذف الخطوط المحددة وإزالة ' + res.removedAssignments + ' ارتباط.', 'success');
            renderAll();
          } catch (err) {
            showToast(err.message, 'error');
          }
        }
      );
    });

    document.getElementById('bulkPrefixLinesBtn').addEventListener('click', function () {
      if (state.bulkLineIds.length === 0) return;
      var input = document.getElementById('bulkPrefixInput');
      input.value = '';
      showFieldError('bulkPrefixError', input, '');
      openModal('bulkPrefixModal');
      setTimeout(function () { input.focus(); }, 30);
    });

    document.getElementById('bulkPrefixForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var plant = getActivePlant();
      if (!plant) return;
      var input = document.getElementById('bulkPrefixInput');
      try {
        var res = Core.bulkAddPrefixToLines(state.db, plant.id, state.bulkLineIds, input.value);
        persistDb(res.db);
        state.bulkLineIds = [];
        closeModal('bulkPrefixModal');
        showToast('تم تحديث أسماء الخطوط المحددة بالبادئة.', 'success');
        renderAll();
      } catch (err) {
        showFieldError('bulkPrefixError', input, err.message);
      }
    });

    // Materials & Stock Controls
    document.getElementById('materialListSearchInput').addEventListener('input', function (e) {
      state.materialSearch = e.target.value;
      renderMaterialsView();
    });

    document.getElementById('stockSearchInput').addEventListener('input', function (e) {
      state.stockSearch = e.target.value;
      renderStockView();
    });

    ['addMaterialBtn', 'materialsEmptyAddBtn'].forEach(function (id) {
      document.getElementById(id).addEventListener('click', function () { openMaterialModal(null); });
    });

    document.getElementById('materialNameInput').addEventListener('input', function (e) {
      showFieldError('materialNameError', e.target, '');
    });

    document.getElementById('materialLineSearchInput').addEventListener('input', function (e) {
      state.materialModalLineSearch = e.target.value;
      renderMaterialLinesChecklist();
    });

    document.getElementById('materialForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var plant = getActivePlant();
      if (!plant) return;

      var editIdVal = document.getElementById('materialEditId').value;
      var codeInput = document.getElementById('materialCodeInput');
      var stockInput = document.getElementById('materialStockInput');
      var nameInput = document.getElementById('materialNameInput');
      var descInput = document.getElementById('materialDescInput');

      var hasError = false;
      var codeCheck = Core.validateRequiredNumber(codeInput.value, { min: 0 });
      if (!codeCheck.valid) {
        showFieldError('materialCodeError', codeInput, codeCheck.error);
        hasError = true;
      }
      var stockCheck = Core.validateRequiredNumber(stockInput.value, { min: 0 });
      if (!stockCheck.valid) {
        showFieldError('materialStockError', stockInput, stockCheck.error);
        hasError = true;
      }
      if (!nameInput.value.trim()) {
        showFieldError('materialNameError', nameInput, 'يرجى إدخال اسم الخامة');
        hasError = true;
      }
      if (hasError) return;

      try {
        var res = Core.saveMaterial(state.db, plant.id, {
          id: editIdVal ? Number(editIdVal) : null,
          code: codeCheck.value,
          stock_qty: stockCheck.number,
          name: nameInput.value,
          description: descInput.value,
          lineIds: state.materialModalLineIds
        });
        persistDb(res.db);
        closeModal('materialModal');
        showToast(
          editIdVal
            ? 'تم تحديث الخامة «' + res.material.code + '» بنجاح.'
            : 'تمت إضافة الخامة «' + res.material.code + '» في ' + plant.name + '.',
          'success'
        );
        renderAll();
      } catch (err) {
        showFieldError('materialCodeError', codeInput, err.message);
      }
    });

    // Material Details & Full Assignment Editor Events
    document.getElementById('detailsAddLineSearchInput').addEventListener('input', function (e) {
      state.detailsAddLineSearch = e.target.value;
      renderMaterialDetailsModalContent();
    });

    document.getElementById('detailsAddSelectedLinesBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant || !state.detailsMaterialId) return;
      if (state.detailsAddLineIds.length === 0) {
        showToast('حدد خطاً واحداً على الأقل لإضافته.', 'error');
        return;
      }
      try {
        var res = Core.addLinesToMaterial(state.db, plant.id, state.detailsMaterialId, state.detailsAddLineIds);
        persistDb(res.db);
        state.detailsAddLineIds = [];
        showToast('تم ربط الخطوط المحددة بالخامة بنجاح.', 'success');
        renderAll();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    document.getElementById('detailsRemoveAllLinesBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant || !state.detailsMaterialId) return;
      var mat = Core.getMaterialsByPlant(state.db, plant.id).find(function (m) {
        return m.id === state.detailsMaterialId;
      });
      if (!mat) return;

      openConfirmDialog(
        'إزالة جميع الخطوط عن الخامة ' + mat.code,
        'هل أنت متأكد من إزالة جميع خطوط الإنتاج المرتبطة بالخامة «' + mat.name + '»؟',
        'ستبقى الخامة محفوظة في قائمة الخامات مع شارة «غير مرتبط بأي لاين»، ولن تظهر في نتائج الفلترة الحصرية أو المشتركة حتى يتم تعيين خطوط لها.',
        [],
        function () {
          try {
            var res = Core.removeAllLinesFromMaterial(state.db, plant.id, mat.id);
            persistDb(res.db);
            var msg = 'تمت إزالة جميع الخطوط (' + res.removedCount + ') وبقيت الخامة محفوظة.';
            if (res.rolledBackQty > 0) msg += ' وتمت إعادة ' + res.rolledBackQty + ' للمخزون.';
            showToast(msg, 'success');
            renderAll();
          } catch (err) {
            showToast(err.message, 'error');
          }
        }
      );
    });

    document.getElementById('detailsSupplyBtn').addEventListener('click', function () {
      if (!state.detailsMaterialId) return;
      openSupplyModal(state.detailsMaterialId, null);
    });

    document.getElementById('detailsRestockBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant || !state.detailsMaterialId) return;
      var mat = Core.getMaterialsByPlant(state.db, plant.id).find(function (m) {
        return m.id === state.detailsMaterialId;
      });
      if (mat) openRestockModal(mat);
    });

    document.getElementById('detailsEditMatBtn').addEventListener('click', function () {
      var plant = getActivePlant();
      if (!plant || !state.detailsMaterialId) return;
      var mat = Core.getMaterialsByPlant(state.db, plant.id).find(function (m) {
        return m.id === state.detailsMaterialId;
      });
      if (mat) {
        closeModal('materialDetailsModal');
        openMaterialModal(mat);
      }
    });

    // Supply Modal Events
    document.getElementById('supplyMaterialSelect').addEventListener('change', function () {
      populateSupplyLinesDropdown(null);
      updateSupplyLivePreview();
      showFieldError('supplyQtyError', document.getElementById('supplyQtyInput'), '');
    });

    document.getElementById('supplyLineSelect').addEventListener('change', function () {
      updateSupplyLivePreview();
      showFieldError('supplyQtyError', document.getElementById('supplyQtyInput'), '');
    });

    document.getElementById('supplyForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var plant = getActivePlant();
      if (!plant) return;

      var matId = Number(document.getElementById('supplyMaterialSelect').value);
      var lineId = Number(document.getElementById('supplyLineSelect').value);
      var qtyInput = document.getElementById('supplyQtyInput');

      var qtyCheck = Core.validateRequiredNumber(qtyInput.value, { min: 1 });
      if (!qtyCheck.valid) {
        showFieldError('supplyQtyError', qtyInput, qtyCheck.error);
        return;
      }

      try {
        var res = Core.supplyMaterialToLine(state.db, plant.id, {
          materialId: matId,
          lineId: lineId,
          quantity: qtyCheck.number
        });
        persistDb(res.db);
        closeModal('supplyModal');
        showToast(res.message, 'success');
        renderAll();
      } catch (err) {
        showFieldError('supplyQtyError', qtyInput, err.message);
        showToast(err.message, 'error');
      }
    });

    // Restock Modal Events
    document.getElementById('restockForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var plant = getActivePlant();
      if (!plant) return;
      var matId = Number(document.getElementById('restockMaterialId').value);
      var qtyInput = document.getElementById('restockQtyInput');

      var qtyCheck = Core.validateRequiredNumber(qtyInput.value, { min: 1 });
      if (!qtyCheck.valid) {
        showFieldError('restockQtyError', qtyInput, qtyCheck.error);
        return;
      }

      try {
        var res = Core.restockMaterial(state.db, plant.id, matId, qtyCheck.number);
        persistDb(res.db);
        closeModal('restockModal');
        showToast('تمت إضافة الرصيد. المخزون الحالي: ' + res.material.stock_qty, 'success');
        renderAll();
      } catch (err) {
        showFieldError('restockQtyError', qtyInput, err.message);
      }
    });

    // Confirmation Dialog Execute
    document.getElementById('confirmModalYesBtn').addEventListener('click', function () {
      var cb = state.confirmCallback;
      state.confirmCallback = null;
      closeModal('confirmModal');
      if (typeof cb === 'function') cb();
    });
  }

  bindEvents();
  renderAll();
})();
