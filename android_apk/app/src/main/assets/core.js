(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.PlantWmsCore = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var STORAGE_KEY = 'plant_wms_isolated_db_v1';

  function createEmptyDb() {
    return {
      plants: [],
      lines: [],
      materials: [],
      assignments: []
    };
  }

  function toNonNegativeInt(val, fallback) {
    var n = Number(val);
    if (!isFinite(n) || isNaN(n) || n < 0) return fallback || 0;
    return Math.floor(n);
  }

  function cloneDb(db) {
    return {
      plants: (db && Array.isArray(db.plants) ? db.plants : []).map(function (p) {
        return {
          id: Number(p.id),
          name: String(p.name || '')
        };
      }),
      lines: (db && Array.isArray(db.lines) ? db.lines : []).map(function (l) {
        var planQty = toNonNegativeInt(l.plan_qty, 0);
        var receivedQty = toNonNegativeInt(l.received_qty, 0);
        if (planQty > 0 && receivedQty > planQty) receivedQty = planQty;
        return {
          id: Number(l.id),
          plant_id: Number(l.plant_id),
          name: String(l.name || ''),
          order: toNonNegativeInt(l.order, 0),
          plan_qty: planQty,
          received_qty: receivedQty
        };
      }),
      materials: (db && Array.isArray(db.materials) ? db.materials : []).map(function (m) {
        var stockQty = toNonNegativeInt(m.stock_qty, 0);
        var initialStock = m.initial_stock_qty != null
          ? toNonNegativeInt(m.initial_stock_qty, stockQty)
          : stockQty;
        if (stockQty > initialStock) initialStock = stockQty;
        return {
          id: Number(m.id),
          plant_id: Number(m.plant_id),
          code: String(m.code || ''),
          name: String(m.name || ''),
          description: String(m.description || ''),
          stock_qty: stockQty,
          initial_stock_qty: initialStock
        };
      }),
      assignments: (db && Array.isArray(db.assignments) ? db.assignments : []).map(function (a) {
        return {
          material_id: Number(a.material_id),
          line_id: Number(a.line_id),
          plant_id: Number(a.plant_id),
          supplied_qty: toNonNegativeInt(a.supplied_qty, 0)
        };
      })
    };
  }

  function nextId(items) {
    var max = 0;
    for (var i = 0; i < items.length; i++) {
      var id = Number(items[i].id) || 0;
      if (id > max) max = id;
    }
    return max + 1;
  }

  function normalizeArabicDigits(str) {
    var out = '';
    for (var i = 0; i < str.length; i++) {
      var code = str.charCodeAt(i);
      if (code >= 0x0660 && code <= 0x0669) {
        out += String.fromCharCode(48 + (code - 0x0660));
      } else if (code >= 0x06f0 && code <= 0x06f9) {
        out += String.fromCharCode(48 + (code - 0x06f0));
      } else {
        out += str.charAt(i);
      }
    }
    return out;
  }

  function sanitizeNumericInput(raw) {
    var text = normalizeArabicDigits(String(raw == null ? '' : raw));
    return text.replace(/[^0-9]/g, '');
  }

  function validateRequiredNumber(raw, options) {
    var opts = options || {};
    var trimmed = normalizeArabicDigits(String(raw == null ? '' : raw)).trim();
    if (!trimmed) {
      return { valid: false, error: 'يرجى إدخال رقم (please enter a number)' };
    }
    if (!/^\d+$/.test(trimmed)) {
      return { valid: false, error: 'يرجى إدخال أرقام صحيحة فقط' };
    }
    var num = Number(trimmed);
    if (!isFinite(num) || isNaN(num)) {
      return { valid: false, error: 'قيمة رقمية غير صالحة' };
    }
    if (opts.min != null && num < opts.min) {
      return {
        valid: false,
        error: opts.min === 1
          ? 'يجب أن تكون القيمة رقماً صحيحاً أكبر من أو يساوي 1'
          : 'يجب أن تكون القيمة أكبر من أو تساوي ' + opts.min
      };
    }
    return { valid: true, value: trimmed, number: num };
  }

  function loadDb(storage) {
    var store = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    if (!store) return createEmptyDb();
    try {
      var raw = store.getItem(STORAGE_KEY);
      if (!raw) return createEmptyDb();
      var parsed = JSON.parse(raw);
      return cloneDb(parsed);
    } catch (_e) {
      return createEmptyDb();
    }
  }

  function saveDb(db, storage) {
    var normalized = cloneDb(db);
    var store = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    if (store) {
      store.setItem(STORAGE_KEY, JSON.stringify(normalized));
    }
    return normalized;
  }

  function assertValidPlant(db, plantId) {
    var pid = Number(plantId);
    if (!pid || isNaN(pid)) {
      throw new Error('يجب اختيار مصنع صالح أولاً.');
    }
    var found = null;
    for (var i = 0; i < db.plants.length; i++) {
      if (db.plants[i].id === pid) {
        found = db.plants[i];
        break;
      }
    }
    if (!found) {
      throw new Error('المصنع المحدد غير موجود.');
    }
    return found;
  }

  function findLineInDb(db, lineId) {
    var lid = Number(lineId);
    for (var i = 0; i < db.lines.length; i++) {
      if (db.lines[i].id === lid) return db.lines[i];
    }
    return null;
  }

  function findMaterialInDb(db, materialId) {
    var mid = Number(materialId);
    for (var i = 0; i < db.materials.length; i++) {
      if (db.materials[i].id === mid) return db.materials[i];
    }
    return null;
  }

  // =========================================================================
  // PLANTS CRUD (STRICT NAMESPACE ROOT)
  // =========================================================================

  function getPlants(db) {
    return cloneDb(db).plants.sort(function (a, b) {
      return a.name.localeCompare(b.name, 'ar', { numeric: true });
    });
  }

  function createPlant(db, rawName) {
    var name = String(rawName || '').trim();
    if (!name) {
      throw new Error('اسم المصنع مطلوب.');
    }
    var next = cloneDb(db);
    var lower = name.toLowerCase();
    for (var i = 0; i < next.plants.length; i++) {
      if (next.plants[i].name.trim().toLowerCase() === lower) {
        throw new Error('يوجد مصنع بنفس هذا الاسم بالفعل.');
      }
    }
    var plant = { id: nextId(next.plants), name: name };
    next.plants.push(plant);
    return { db: next, plant: plant };
  }

  function renamePlant(db, plantId, rawName) {
    var name = String(rawName || '').trim();
    if (!name) {
      throw new Error('اسم المصنع مطلوب.');
    }
    var next = cloneDb(db);
    var target = assertValidPlant(next, plantId);
    var lower = name.toLowerCase();
    for (var i = 0; i < next.plants.length; i++) {
      if (next.plants[i].id !== target.id && next.plants[i].name.trim().toLowerCase() === lower) {
        throw new Error('يوجد مصنع آخر بنفس هذا الاسم بالفعل.');
      }
    }
    target.name = name;
    return { db: next, plant: target };
  }

  function getPlantStats(db, plantId) {
    var pid = Number(plantId);
    var lineCount = 0;
    var materialCount = 0;
    var assignmentCount = 0;
    var totalStock = 0;
    var totalPlan = 0;
    var totalReceived = 0;
    for (var i = 0; i < db.lines.length; i++) {
      if (db.lines[i].plant_id === pid) {
        lineCount++;
        totalPlan += toNonNegativeInt(db.lines[i].plan_qty, 0);
        totalReceived += toNonNegativeInt(db.lines[i].received_qty, 0);
      }
    }
    for (var j = 0; j < db.materials.length; j++) {
      if (db.materials[j].plant_id === pid) {
        materialCount++;
        totalStock += toNonNegativeInt(db.materials[j].stock_qty, 0);
      }
    }
    for (var k = 0; k < db.assignments.length; k++) {
      if (db.assignments[k].plant_id === pid) assignmentCount++;
    }
    return {
      lineCount: lineCount,
      materialCount: materialCount,
      assignmentCount: assignmentCount,
      totalStock: totalStock,
      totalPlan: totalPlan,
      totalReceived: totalReceived
    };
  }

  function deletePlant(db, plantId) {
    var next = cloneDb(db);
    var pid = Number(plantId);
    assertValidPlant(next, pid);
    var stats = getPlantStats(next, pid);

    next.plants = next.plants.filter(function (p) { return p.id !== pid; });
    next.lines = next.lines.filter(function (l) { return l.plant_id !== pid; });
    next.materials = next.materials.filter(function (m) { return m.plant_id !== pid; });
    next.assignments = next.assignments.filter(function (a) { return a.plant_id !== pid; });

    return { db: next, stats: stats };
  }

  // =========================================================================
  // LINES CRUD + PLAN STATUS + ORDERING (STRICTLY SCOPED TO plant_id)
  // =========================================================================

  function getLineStatus(line) {
    var plan = toNonNegativeInt(line && line.plan_qty, 0);
    var received = toNonNegativeInt(line && line.received_qty, 0);
    if (plan > 0 && received > plan) received = plan;
    var remaining = Math.max(0, plan - received);
    var percent = plan > 0 ? Math.min(100, Math.round((received / plan) * 100)) : 0;

    var status = 'empty';
    var label = 'فارغ ' + received + ' / ' + plan;
    if (plan > 0 && received >= plan) {
      status = 'complete';
      label = 'اكتملت الخطة (Plan complete)';
    } else if (received > 0) {
      status = 'in_progress';
      label = 'قيد التنفيذ ' + received + ' / ' + plan;
    }

    return {
      status: status,
      label: label,
      plan_qty: plan,
      received_qty: received,
      remaining: remaining,
      percent: percent
    };
  }

  function getLinesByPlant(db, plantId) {
    var pid = Number(plantId);
    if (!pid) return [];
    return db.lines
      .filter(function (l) { return l.plant_id === pid; })
      .map(function (l) { return Object.assign({}, l); })
      .sort(function (a, b) {
        if (a.order !== b.order) return a.order - b.order;
        return a.id - b.id;
      });
  }

  function normalizePlantLineOrders(db, plantId) {
    var pid = Number(plantId);
    var plantLines = db.lines
      .filter(function (l) { return l.plant_id === pid; })
      .sort(function (a, b) {
        if (a.order !== b.order) return a.order - b.order;
        return a.id - b.id;
      });
    for (var i = 0; i < plantLines.length; i++) {
      plantLines[i].order = i + 1;
    }
  }

  function countMaterialsForLine(db, plantId, lineId) {
    var pid = Number(plantId);
    var lid = Number(lineId);
    var seen = Object.create(null);
    var count = 0;
    for (var i = 0; i < db.assignments.length; i++) {
      var a = db.assignments[i];
      if (a.plant_id === pid && a.line_id === lid && !seen[a.material_id]) {
        seen[a.material_id] = true;
        count++;
      }
    }
    return count;
  }

  function getLineDeletionDetails(db, plantId, lineId) {
    var pid = Number(plantId);
    var lid = Number(lineId);
    var matMap = Object.create(null);
    for (var i = 0; i < db.materials.length; i++) {
      if (db.materials[i].plant_id === pid) {
        matMap[db.materials[i].id] = db.materials[i];
      }
    }
    var attachedMaterials = [];
    var totalRollbackQty = 0;
    for (var j = 0; j < db.assignments.length; j++) {
      var a = db.assignments[j];
      if (a.plant_id === pid && a.line_id === lid) {
        var mat = matMap[a.material_id];
        if (mat) {
          var sq = toNonNegativeInt(a.supplied_qty, 0);
          totalRollbackQty += sq;
          attachedMaterials.push({
            id: mat.id,
            code: mat.code,
            name: mat.name,
            supplied_qty: sq
          });
        }
      }
    }
    return {
      attachedMaterials: attachedMaterials,
      totalRollbackQty: totalRollbackQty
    };
  }

  function createLine(db, plantId, payloadOrName) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);

    var payload = typeof payloadOrName === 'string'
      ? { name: payloadOrName, plan_qty: 500 }
      : (payloadOrName || {});

    var name = String(payload.name || '').trim();
    if (!name) {
      throw new Error('اسم خط الإنتاج مطلوب.');
    }

    var planCheck = validateRequiredNumber(payload.plan_qty, { min: 1 });
    if (!planCheck.valid) {
      throw new Error(planCheck.error);
    }

    var lower = name.toLowerCase();
    var existing = getLinesByPlant(next, plant.id);
    var maxOrder = 0;
    for (var i = 0; i < existing.length; i++) {
      if (existing[i].name.trim().toLowerCase() === lower) {
        throw new Error('يوجد خط إنتاج بنفس الاسم داخل هذا المصنع بالفعل.');
      }
      if (existing[i].order > maxOrder) maxOrder = existing[i].order;
    }

    var line = {
      id: nextId(next.lines),
      plant_id: plant.id,
      name: name,
      order: maxOrder + 1,
      plan_qty: planCheck.number,
      received_qty: 0
    };
    next.lines.push(line);
    return { db: next, line: Object.assign({}, line) };
  }

  function updateLine(db, plantId, lineId, payload) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var lid = Number(lineId);
    var target = findLineInDb(next, lid);

    if (!target) {
      throw new Error('خط الإنتاج غير موجود.');
    }
    if (target.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: لا يمكن تعديل خط إنتاج تابع لمصنع آخر.');
    }

    if (payload.name != null) {
      var name = String(payload.name || '').trim();
      if (!name) {
        throw new Error('اسم خط الإنتاج مطلوب.');
      }
      var lower = name.toLowerCase();
      for (var i = 0; i < next.lines.length; i++) {
        var other = next.lines[i];
        if (other.plant_id === plant.id && other.id !== lid && other.name.trim().toLowerCase() === lower) {
          throw new Error('يوجد خط إنتاج آخر بنفس الاسم داخل هذا المصنع بالفعل.');
        }
      }
      target.name = name;
    }

    if (payload.plan_qty != null) {
      var planCheck = validateRequiredNumber(payload.plan_qty, { min: 1 });
      if (!planCheck.valid) {
        throw new Error(planCheck.error);
      }
      if (planCheck.number < target.received_qty) {
        throw new Error('لا يمكن تقليل الخطة عن الكمية المورّدة حالياً (' + target.received_qty + ').');
      }
      target.plan_qty = planCheck.number;
    }

    return { db: next, line: Object.assign({}, target) };
  }

  function renameLine(db, plantId, lineId, rawName) {
    return updateLine(db, plantId, lineId, { name: rawName });
  }

  function deleteLine(db, plantId, lineId) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var lid = Number(lineId);

    var target = findLineInDb(next, lid);
    if (!target) {
      throw new Error('خط الإنتاج غير موجود.');
    }
    if (target.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: لا يمكن حذف خط إنتاج تابع لمصنع آخر.');
    }

    var matMap = Object.create(null);
    for (var i = 0; i < next.materials.length; i++) {
      if (next.materials[i].plant_id === plant.id) {
        matMap[next.materials[i].id] = next.materials[i];
      }
    }

    var removedAssignments = 0;
    var rolledBackQty = 0;
    next.assignments = next.assignments.filter(function (a) {
      var shouldRemove = a.plant_id === plant.id && a.line_id === lid;
      if (shouldRemove) {
        removedAssignments++;
        var sq = toNonNegativeInt(a.supplied_qty, 0);
        if (sq > 0 && matMap[a.material_id]) {
          matMap[a.material_id].stock_qty += sq;
          if (matMap[a.material_id].stock_qty > matMap[a.material_id].initial_stock_qty) {
            matMap[a.material_id].initial_stock_qty = matMap[a.material_id].stock_qty;
          }
          rolledBackQty += sq;
        }
      }
      return !shouldRemove;
    });

    next.lines = next.lines.filter(function (l) { return l.id !== lid; });
    normalizePlantLineOrders(next, plant.id);

    return {
      db: next,
      removedAssignments: removedAssignments,
      rolledBackQty: rolledBackQty
    };
  }

  function bulkDeleteLines(db, plantId, lineIds) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var idSet = Object.create(null);
    for (var i = 0; i < lineIds.length; i++) {
      idSet[Number(lineIds[i])] = true;
    }

    for (var j = 0; j < next.lines.length; j++) {
      if (idSet[next.lines[j].id] && next.lines[j].plant_id !== plant.id) {
        throw new Error('خطأ عزل المصانع: تحتوي القائمة المحددة على خط من مصنع آخر.');
      }
    }

    var matMap = Object.create(null);
    for (var m = 0; m < next.materials.length; m++) {
      if (next.materials[m].plant_id === plant.id) {
        matMap[next.materials[m].id] = next.materials[m];
      }
    }

    var removedAssignments = 0;
    var rolledBackQty = 0;
    next.assignments = next.assignments.filter(function (a) {
      var remove = a.plant_id === plant.id && !!idSet[a.line_id];
      if (remove) {
        removedAssignments++;
        var sq = toNonNegativeInt(a.supplied_qty, 0);
        if (sq > 0 && matMap[a.material_id]) {
          matMap[a.material_id].stock_qty += sq;
          if (matMap[a.material_id].stock_qty > matMap[a.material_id].initial_stock_qty) {
            matMap[a.material_id].initial_stock_qty = matMap[a.material_id].stock_qty;
          }
          rolledBackQty += sq;
        }
      }
      return !remove;
    });
    next.lines = next.lines.filter(function (l) {
      return !(l.plant_id === plant.id && idSet[l.id]);
    });
    normalizePlantLineOrders(next, plant.id);

    return {
      db: next,
      removedAssignments: removedAssignments,
      rolledBackQty: rolledBackQty
    };
  }

  function bulkAddPrefixToLines(db, plantId, lineIds, rawPrefix) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var prefix = String(rawPrefix || '').trim();
    if (!prefix) {
      throw new Error('يرجى إدخال البادئة المطلوب إضافتها.');
    }

    var idSet = Object.create(null);
    for (var i = 0; i < lineIds.length; i++) {
      idSet[Number(lineIds[i])] = true;
    }

    var plantLines = next.lines.filter(function (l) { return l.plant_id === plant.id; });
    var proposedLower = Object.create(null);

    for (var j = 0; j < next.lines.length; j++) {
      var l = next.lines[j];
      if (idSet[l.id] && l.plant_id !== plant.id) {
        throw new Error('خطأ عزل المصانع: لا يمكن تعديل خطوط مصنع آخر.');
      }
    }

    for (var k = 0; k < plantLines.length; k++) {
      var item = plantLines[k];
      var candidate = idSet[item.id] ? (prefix + ' ' + item.name).trim() : item.name.trim();
      var lower = candidate.toLowerCase();
      if (proposedLower[lower]) {
        throw new Error('تعارض في الأسماء بعد إضافة البادئة: "' + candidate + '" مكرر داخل المصنع.');
      }
      proposedLower[lower] = true;
    }

    for (var m = 0; m < next.lines.length; m++) {
      if (next.lines[m].plant_id === plant.id && idSet[next.lines[m].id]) {
        next.lines[m].name = (prefix + ' ' + next.lines[m].name).trim();
      }
    }

    return { db: next };
  }

  function reorderLines(db, plantId, orderedLineIds) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var plantLines = next.lines.filter(function (l) { return l.plant_id === plant.id; });

    if (orderedLineIds.length !== plantLines.length) {
      throw new Error('قائمة إعادة الترتيب غير مكتملة لهذا المصنع.');
    }

    var orderMap = Object.create(null);
    for (var i = 0; i < orderedLineIds.length; i++) {
      var lid = Number(orderedLineIds[i]);
      if (orderMap[lid]) {
        throw new Error('يوجد تكرار في معرفات الخطوط.');
      }
      orderMap[lid] = i + 1;
    }

    for (var j = 0; j < plantLines.length; j++) {
      var newOrder = orderMap[plantLines[j].id];
      if (!newOrder) {
        throw new Error('خطأ عزل المصانع أو خط مفقود أثناء إعادة الترتيب.');
      }
      plantLines[j].order = newOrder;
    }

    return { db: next };
  }

  function moveLineStep(db, plantId, lineId, direction) {
    var sorted = getLinesByPlant(db, plantId);
    var lid = Number(lineId);
    var idx = -1;
    for (var i = 0; i < sorted.length; i++) {
      if (sorted[i].id === lid) {
        idx = i;
        break;
      }
    }
    if (idx === -1) {
      throw new Error('خط الإنتاج غير موجود داخل هذا المصنع.');
    }
    var targetIdx = idx + direction;
    if (targetIdx < 0 || targetIdx >= sorted.length) {
      return { db: cloneDb(db) };
    }
    var ids = sorted.map(function (l) { return l.id; });
    var tmp = ids[idx];
    ids[idx] = ids[targetIdx];
    ids[targetIdx] = tmp;
    return reorderLines(db, plantId, ids);
  }

  // =========================================================================
  // MATERIALS, STOCK & FULL ASSIGNMENT EDITING (SCOPED TO plant_id)
  // =========================================================================

  function isLowStock(material) {
    if (!material) return false;
    var initial = toNonNegativeInt(material.initial_stock_qty, 0);
    var current = toNonNegativeInt(material.stock_qty, 0);
    if (initial <= 0) return false;
    return current < initial * 0.1;
  }

  function getMaterialsByPlant(db, plantId) {
    var pid = Number(plantId);
    if (!pid) return [];
    return db.materials
      .filter(function (m) { return m.plant_id === pid; })
      .map(function (m) { return Object.assign({}, m); });
  }

  function getAssignmentsForMaterial(db, materialId, plantId) {
    var pid = Number(plantId);
    var mid = Number(materialId);
    if (!pid || !mid) return [];

    var lineById = Object.create(null);
    for (var i = 0; i < db.lines.length; i++) {
      var line = db.lines[i];
      if (line.plant_id === pid) {
        lineById[line.id] = line;
      }
    }

    var seen = Object.create(null);
    var matched = [];
    for (var j = 0; j < db.assignments.length; j++) {
      var a = db.assignments[j];
      if (a.plant_id === pid && a.material_id === mid && !seen[a.line_id]) {
        var resolved = lineById[a.line_id];
        if (resolved && resolved.plant_id === pid) {
          seen[a.line_id] = true;
          matched.push({
            material_id: mid,
            line_id: resolved.id,
            plant_id: pid,
            supplied_qty: toNonNegativeInt(a.supplied_qty, 0),
            line: Object.assign({}, resolved)
          });
        }
      }
    }

    matched.sort(function (a, b) {
      if (a.line.order !== b.line.order) return a.line.order - b.line.order;
      return a.line.id - b.line.id;
    });
    return matched;
  }

  function getLinesForMaterial(db, materialId, plantId) {
    return getAssignmentsForMaterial(db, materialId, plantId).map(function (entry) {
      return entry.line;
    });
  }

  function validateLineIdsForPlant(db, plantId, lineIds) {
    var pid = Number(plantId);
    var lineById = Object.create(null);
    for (var i = 0; i < db.lines.length; i++) {
      lineById[db.lines[i].id] = db.lines[i];
    }

    var cleanIds = [];
    var seen = Object.create(null);
    for (var j = 0; j < lineIds.length; j++) {
      var lid = Number(lineIds[j]);
      var line = lineById[lid];
      if (!line) {
        throw new Error('أحد خطوط الإنتاج المحددة غير موجود.');
      }
      if (line.plant_id !== pid) {
        throw new Error('خطأ عزل المصانع: لا يمكن تعيين خامة إلى خط إنتاج تابع لمصنع آخر.');
      }
      if (!seen[lid]) {
        seen[lid] = true;
        cleanIds.push(lid);
      }
    }
    return cleanIds;
  }

  function applyAssignmentSetForMaterial(next, plantId, materialRecord, cleanLineIds) {
    var wantedSet = Object.create(null);
    for (var i = 0; i < cleanLineIds.length; i++) {
      wantedSet[cleanLineIds[i]] = true;
    }

    var existingByLine = Object.create(null);
    var remainingAssignments = [];
    for (var j = 0; j < next.assignments.length; j++) {
      var a = next.assignments[j];
      if (a.plant_id === plantId && a.material_id === materialRecord.id) {
        if (wantedSet[a.line_id]) {
          existingByLine[a.line_id] = a;
          remainingAssignments.push(a);
        } else {
          // Removed line link: roll back any supplied_qty cleanly
          var sq = toNonNegativeInt(a.supplied_qty, 0);
          if (sq > 0) {
            var line = findLineInDb(next, a.line_id);
            if (line && line.plant_id === plantId) {
              line.received_qty = Math.max(0, line.received_qty - sq);
            }
            materialRecord.stock_qty += sq;
            if (materialRecord.stock_qty > materialRecord.initial_stock_qty) {
              materialRecord.initial_stock_qty = materialRecord.stock_qty;
            }
          }
        }
      } else {
        remainingAssignments.push(a);
      }
    }

    for (var k = 0; k < cleanLineIds.length; k++) {
      var lid = cleanLineIds[k];
      if (!existingByLine[lid]) {
        remainingAssignments.push({
          material_id: materialRecord.id,
          line_id: lid,
          plant_id: plantId,
          supplied_qty: 0
        });
      }
    }

    next.assignments = remainingAssignments;
  }

  function saveMaterial(db, plantId, payload) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);

    var codeValidation = validateRequiredNumber(payload.code, { min: 0 });
    if (!codeValidation.valid) {
      throw new Error(codeValidation.error);
    }
    var code = codeValidation.value;

    var stockValidation = validateRequiredNumber(payload.stock_qty, { min: 0 });
    if (!stockValidation.valid) {
      throw new Error(stockValidation.error);
    }
    var stockQty = stockValidation.number;

    var name = String(payload.name || '').trim();
    var description = String(payload.description || '').trim();
    if (!name) {
      throw new Error('اسم الخامة مطلوب.');
    }

    var editingId = payload.id ? Number(payload.id) : null;

    // Duplicate code check strictly scoped to plant.id
    for (var i = 0; i < next.materials.length; i++) {
      var existing = next.materials[i];
      if (existing.plant_id === plant.id && existing.code === code && existing.id !== editingId) {
        throw new Error('كود الخامة "' + code + '" مسجل بالفعل داخل هذا المصنع.');
      }
    }

    var cleanLineIds = validateLineIdsForPlant(next, plant.id, payload.lineIds || []);

    var materialRef;
    if (editingId) {
      materialRef = findMaterialInDb(next, editingId);
      if (!materialRef) {
        throw new Error('الخامة المطلوب تعديلها غير موجودة.');
      }
      if (materialRef.plant_id !== plant.id) {
        throw new Error('خطأ عزل المصانع: لا يمكن تعديل خامة تابعة لمصنع آخر.');
      }
      materialRef.code = code;
      materialRef.name = name;
      materialRef.description = description;
      materialRef.stock_qty = stockQty;
      if (stockQty > materialRef.initial_stock_qty || materialRef.initial_stock_qty === 0) {
        materialRef.initial_stock_qty = stockQty;
      }
    } else {
      materialRef = {
        id: nextId(next.materials),
        plant_id: plant.id,
        code: code,
        name: name,
        description: description,
        stock_qty: stockQty,
        initial_stock_qty: stockQty
      };
      next.materials.push(materialRef);
    }

    if (payload.lineIds != null) {
      applyAssignmentSetForMaterial(next, plant.id, materialRef, cleanLineIds);
    }

    return { db: next, material: Object.assign({}, materialRef) };
  }

  function assignMaterialToLines(db, plantId, materialId, lineIds) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mat = findMaterialInDb(next, materialId);

    if (!mat) {
      throw new Error('الخامة غير موجودة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة تنتمي لمصنع آخر.');
    }

    var cleanLineIds = validateLineIdsForPlant(next, plant.id, lineIds || []);
    applyAssignmentSetForMaterial(next, plant.id, mat, cleanLineIds);

    return { db: next, material: Object.assign({}, mat) };
  }

  function addLinesToMaterial(db, plantId, materialId, lineIdsToAdd) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mat = findMaterialInDb(next, materialId);

    if (!mat) {
      throw new Error('الخامة غير موجودة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة تنتمي لمصنع آخر.');
    }

    var currentIds = getLinesForMaterial(next, mat.id, plant.id).map(function (l) { return l.id; });
    var merged = currentIds.concat(lineIdsToAdd || []);
    var cleanIds = validateLineIdsForPlant(next, plant.id, merged);
    applyAssignmentSetForMaterial(next, plant.id, mat, cleanIds);

    return { db: next, material: Object.assign({}, mat) };
  }

  function removeLineFromMaterial(db, plantId, materialId, lineId) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mat = findMaterialInDb(next, materialId);
    var lid = Number(lineId);

    if (!mat) {
      throw new Error('الخامة غير موجودة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة تنتمي لمصنع آخر.');
    }

    var line = findLineInDb(next, lid);
    if (line && line.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخط ينتمي لمصنع آخر.');
    }

    var removed = false;
    var rolledBackQty = 0;
    next.assignments = next.assignments.filter(function (a) {
      var match = a.plant_id === plant.id && a.material_id === mat.id && a.line_id === lid;
      if (match) {
        removed = true;
        var sq = toNonNegativeInt(a.supplied_qty, 0);
        if (sq > 0) {
          if (line && line.plant_id === plant.id) {
            line.received_qty = Math.max(0, line.received_qty - sq);
          }
          mat.stock_qty += sq;
          if (mat.stock_qty > mat.initial_stock_qty) {
            mat.initial_stock_qty = mat.stock_qty;
          }
          rolledBackQty += sq;
        }
      }
      return !match;
    });

    return { db: next, removed: removed, rolledBackQty: rolledBackQty };
  }

  function removeAllLinesFromMaterial(db, plantId, materialId) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mat = findMaterialInDb(next, materialId);

    if (!mat) {
      throw new Error('الخامة غير موجودة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة تنتمي لمصنع آخر.');
    }

    var removedCount = 0;
    var rolledBackQty = 0;
    next.assignments = next.assignments.filter(function (a) {
      var match = a.plant_id === plant.id && a.material_id === mat.id;
      if (match) {
        removedCount++;
        var sq = toNonNegativeInt(a.supplied_qty, 0);
        if (sq > 0) {
          var line = findLineInDb(next, a.line_id);
          if (line && line.plant_id === plant.id) {
            line.received_qty = Math.max(0, line.received_qty - sq);
          }
          mat.stock_qty += sq;
          if (mat.stock_qty > mat.initial_stock_qty) {
            mat.initial_stock_qty = mat.stock_qty;
          }
          rolledBackQty += sq;
        }
      }
      return !match;
    });

    return { db: next, removedCount: removedCount, rolledBackQty: rolledBackQty };
  }

  function restockMaterial(db, plantId, materialId, addQtyRaw) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mat = findMaterialInDb(next, materialId);
    if (!mat) {
      throw new Error('الخامة غير موجودة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة تنتمي لمصنع آخر.');
    }

    var qtyCheck = validateRequiredNumber(addQtyRaw, { min: 1 });
    if (!qtyCheck.valid) {
      throw new Error(qtyCheck.error);
    }

    mat.stock_qty += qtyCheck.number;
    if (mat.stock_qty > mat.initial_stock_qty) {
      mat.initial_stock_qty = mat.stock_qty;
    }

    return { db: next, material: Object.assign({}, mat) };
  }

  function deleteMaterial(db, plantId, materialId) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);
    var mid = Number(materialId);

    var target = findMaterialInDb(next, mid);
    if (!target) {
      throw new Error('الخامة غير موجودة.');
    }
    if (target.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: لا يمكن حذف خامة تابعة لمصنع آخر.');
    }

    next.assignments = next.assignments.filter(function (a) {
      var match = a.material_id === mid && a.plant_id === plant.id;
      if (match && a.supplied_qty > 0) {
        var line = findLineInDb(next, a.line_id);
        if (line && line.plant_id === plant.id) {
          line.received_qty = Math.max(0, line.received_qty - a.supplied_qty);
        }
      }
      return !match;
    });

    next.materials = next.materials.filter(function (m) { return m.id !== mid; });

    return { db: next };
  }

  // =========================================================================
  // LINE PLAN & SUPPLY ENGINE (STRICT ATOMIC VALIDATION)
  // =========================================================================

  function supplyMaterialToLine(db, plantId, payload) {
    var next = cloneDb(db);
    var plant = assertValidPlant(next, plantId);

    var mat = findMaterialInDb(next, payload.materialId);
    if (!mat) {
      throw new Error('يرجى اختيار خامة صالحة.');
    }
    if (mat.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: الخامة المختارة لا تنتمي لهذا المصنع.');
    }

    var line = findLineInDb(next, payload.lineId);
    if (!line) {
      throw new Error('يرجى اختيار خط إنتاج صالح.');
    }
    if (line.plant_id !== plant.id) {
      throw new Error('خطأ عزل المصانع: خط الإنتاج المختار لا ينتمي لهذا المصنع.');
    }

    var qtyCheck = validateRequiredNumber(payload.quantity, { min: 1 });
    if (!qtyCheck.valid) {
      throw new Error(qtyCheck.error);
    }
    var qty = qtyCheck.number;

    var remaining = Math.max(0, line.plan_qty - line.received_qty);

    // Check plan limit first — exact message required by spec
    if (qty > remaining) {
      throw new Error("تجاوزت الخطة — You exceeded the plan — you broke the line's plan with quantity " + qty);
    }

    // Check stock limit — exact message required by spec
    if (qty > mat.stock_qty) {
      throw new Error('المخزون غير كافٍ — Not enough stock in warehouse');
    }

    // Atomic update: line received, material stock, and assignment supplied_qty
    line.received_qty += qty;
    mat.stock_qty -= qty;

    var assignment = null;
    for (var i = 0; i < next.assignments.length; i++) {
      var a = next.assignments[i];
      if (a.plant_id === plant.id && a.material_id === mat.id && a.line_id === line.id) {
        a.supplied_qty += qty;
        assignment = Object.assign({}, a);
        break;
      }
    }
    if (!assignment) {
      assignment = {
        material_id: mat.id,
        line_id: line.id,
        plant_id: plant.id,
        supplied_qty: qty
      };
      next.assignments.push(assignment);
      assignment = Object.assign({}, assignment);
    }

    var completed = line.received_qty === line.plan_qty;
    var message = completed
      ? 'تم التوريد بنجاح — Line has completed its plan'
      : 'تم توريد ' + qty + ' إلى خط «' + line.name + '» (' + line.received_qty + ' / ' + line.plan_qty + ')';

    return {
      db: next,
      line: Object.assign({}, line),
      material: Object.assign({}, mat),
      assignment: assignment,
      completed: completed,
      message: message
    };
  }

  // =========================================================================
  // ADVANCED SMART FILTERING ENGINE
  // =========================================================================

  function filterMaterials(db, plantId, selectedLineIds, mode, sortConfig) {
    var pid = Number(plantId);
    if (!pid) {
      return { rows: [], totalCount: 0, exclusiveCount: 0, sharedCount: 0 };
    }
    assertValidPlant(db, pid);

    var cleanSelectedIds = validateLineIdsForPlant(db, pid, selectedLineIds || []);
    var selectedCount = cleanSelectedIds.length;
    if (selectedCount === 0) {
      return { rows: [], totalCount: 0, exclusiveCount: 0, sharedCount: 0 };
    }

    var filterMode = mode || 'both';
    var materials = getMaterialsByPlant(db, pid);
    var rows = [];

    for (var i = 0; i < materials.length; i++) {
      var m = materials[i];
      var assignments = getAssignmentsForMaterial(db, m.id, pid);
      var lineCount = assignments.length;

      // Materials with 0 assigned lines MUST NEVER appear in Exclusive/Shared/Both
      if (lineCount === 0) continue;

      var lineIdSet = Object.create(null);
      var totalSupplied = 0;
      var lines = [];
      for (var j = 0; j < assignments.length; j++) {
        lineIdSet[assignments[j].line_id] = true;
        totalSupplied += assignments[j].supplied_qty;
        lines.push(assignments[j].line);
      }

      var allSelectedPresent = true;
      for (var k = 0; k < cleanSelectedIds.length; k++) {
        if (!lineIdSet[cleanSelectedIds[k]]) {
          allSelectedPresent = false;
          break;
        }
      }

      if (!allSelectedPresent) continue;

      var isExclusive = lineCount === selectedCount;
      var isShared = lineCount > selectedCount;

      if (filterMode === 'exclusive' && !isExclusive) continue;
      if (filterMode === 'shared' && !isShared) continue;
      if (!isExclusive && !isShared) continue;

      var type = isExclusive ? 'exclusive' : 'shared';
      rows.push({
        id: m.id,
        plant_id: m.plant_id,
        code: m.code,
        name: m.name,
        description: m.description,
        stock_qty: m.stock_qty,
        initial_stock_qty: m.initial_stock_qty,
        lowStock: isLowStock(m),
        lines: lines,
        assignments: assignments,
        lineNamesComma: lines.map(function (l) { return l.name; }).join(', '),
        lineCount: lineCount,
        totalSupplied: totalSupplied,
        type: type
      });
    }

    var exclusiveCount = 0;
    var sharedCount = 0;
    for (var r = 0; r < rows.length; r++) {
      if (rows[r].type === 'exclusive') exclusiveCount++;
      else sharedCount++;
    }

    var sortKey = (sortConfig && sortConfig.key) || 'code';
    var sortDir = (sortConfig && sortConfig.dir) === 'desc' ? -1 : 1;

    rows.sort(function (a, b) {
      var cmp = 0;
      if (sortKey === 'lineCount') {
        cmp = a.lineCount - b.lineCount;
      } else if (sortKey === 'supplied') {
        cmp = a.totalSupplied - b.totalSupplied;
      } else if (sortKey === 'name') {
        cmp = a.name.localeCompare(b.name, 'ar', { numeric: true });
      } else {
        cmp = a.code.localeCompare(b.code, undefined, { numeric: true });
      }
      if (cmp === 0) {
        cmp = a.code.localeCompare(b.code, undefined, { numeric: true });
      }
      return cmp * sortDir;
    });

    return {
      rows: rows,
      totalCount: rows.length,
      exclusiveCount: exclusiveCount,
      sharedCount: sharedCount
    };
  }

  return {
    STORAGE_KEY: STORAGE_KEY,
    createEmptyDb: createEmptyDb,
    loadDb: loadDb,
    saveDb: saveDb,
    normalizeArabicDigits: normalizeArabicDigits,
    sanitizeNumericInput: sanitizeNumericInput,
    validateRequiredNumber: validateRequiredNumber,
    getPlants: getPlants,
    createPlant: createPlant,
    renamePlant: renamePlant,
    deletePlant: deletePlant,
    getPlantStats: getPlantStats,
    getLineStatus: getLineStatus,
    getLinesByPlant: getLinesByPlant,
    countMaterialsForLine: countMaterialsForLine,
    getLineDeletionDetails: getLineDeletionDetails,
    createLine: createLine,
    updateLine: updateLine,
    renameLine: renameLine,
    deleteLine: deleteLine,
    bulkDeleteLines: bulkDeleteLines,
    bulkAddPrefixToLines: bulkAddPrefixToLines,
    reorderLines: reorderLines,
    moveLineStep: moveLineStep,
    isLowStock: isLowStock,
    getMaterialsByPlant: getMaterialsByPlant,
    getAssignmentsForMaterial: getAssignmentsForMaterial,
    getLinesForMaterial: getLinesForMaterial,
    saveMaterial: saveMaterial,
    assignMaterialToLines: assignMaterialToLines,
    addLinesToMaterial: addLinesToMaterial,
    removeLineFromMaterial: removeLineFromMaterial,
    removeAllLinesFromMaterial: removeAllLinesFromMaterial,
    restockMaterial: restockMaterial,
    deleteMaterial: deleteMaterial,
    supplyMaterialToLine: supplyMaterialToLine,
    filterMaterials: filterMaterials
  };
});
