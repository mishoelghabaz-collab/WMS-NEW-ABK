const test = require('node:test');
const assert = require('node:assert/strict');
const Core = require('./core.js');

function createMemoryStorage() {
  const map = new Map();
  return {
    getItem(k) { return map.has(k) ? map.get(k) : null; },
    setItem(k, v) { map.set(k, String(v)); },
    removeItem(k) { map.delete(k); }
  };
}

test('Mandatory Scenarios 1-7 & 14: Material-Line Assignment Editing (single X, add, remove all, 0 lines, line delete, rename, plant isolation)', () => {
  let db = Core.createEmptyDb();

  // 1. Create Plant 7 -> material 100 with stock 2000 -> assign to Volvo Lift & Volvo Right
  let r = Core.createPlant(db, 'Plant 7'); db = r.db; const p7 = r.plant;
  r = Core.createLine(db, p7.id, { name: 'Volvo Lift', plan_qty: 500 }); db = r.db; const p7Lift = r.line;
  r = Core.createLine(db, p7.id, { name: 'Volvo Right', plan_qty: 500 }); db = r.db; const p7Right = r.line;

  r = Core.saveMaterial(db, p7.id, {
    code: '100',
    name: 'حامل رفع',
    description: 'وصف تجريبي',
    stock_qty: 2000,
    lineIds: [p7Lift.id, p7Right.id]
  });
  db = r.db;
  const p7Mat100 = r.material;
  assert.equal(p7Mat100.stock_qty, 2000);
  assert.equal(Core.getLinesForMaterial(db, p7Mat100.id, p7.id).length, 2);

  // 2. Remove Volvo Right via X (removeLineFromMaterial) -> only Volvo Lift remains; Volvo Right line still exists
  r = Core.removeLineFromMaterial(db, p7.id, p7Mat100.id, p7Right.id);
  db = r.db;
  assert.equal(r.removed, true);
  assert.deepEqual(Core.getLinesForMaterial(db, p7Mat100.id, p7.id).map((l) => l.name), ['Volvo Lift']);
  assert.equal(Core.getLinesByPlant(db, p7.id).length, 2);

  // 3. Add Line X -> assign material 100 to it -> material now on Volvo Lift + Line X
  r = Core.createLine(db, p7.id, { name: 'Line X', plan_qty: 500 }); db = r.db; const p7LineX = r.line;
  r = Core.addLinesToMaterial(db, p7.id, p7Mat100.id, [p7LineX.id]); db = r.db;
  assert.deepEqual(
    Core.getLinesForMaterial(db, p7Mat100.id, p7.id).map((l) => l.name),
    ['Volvo Lift', 'Line X']
  );

  // 4. Remove ALL lines from material 100 -> stays in materials list with 0 lines; excluded from filter results
  r = Core.removeAllLinesFromMaterial(db, p7.id, p7Mat100.id);
  db = r.db;
  assert.equal(r.removedCount, 2);
  assert.equal(Core.getMaterialsByPlant(db, p7.id).length, 1);
  assert.equal(Core.getLinesForMaterial(db, p7Mat100.id, p7.id).length, 0);
  assert.equal(Core.filterMaterials(db, p7.id, [p7Lift.id], 'exclusive').totalCount, 0);
  assert.equal(Core.filterMaterials(db, p7.id, [p7Lift.id], 'shared').totalCount, 0);
  assert.equal(Core.filterMaterials(db, p7.id, [p7Lift.id], 'both').totalCount, 0);

  // 5. Re-assign material 100 to Volvo Lift + Line X -> appears in Shared for Volvo Lift; re-assign only Volvo Lift -> Exclusive
  db = Core.addLinesToMaterial(db, p7.id, p7Mat100.id, [p7Lift.id, p7LineX.id]).db;
  assert.equal(Core.filterMaterials(db, p7.id, [p7Lift.id], 'shared').totalCount, 1);

  // 6. Delete Volvo Lift line -> material 100 loses that assignment automatically; Line X assignment stays
  db = Core.deleteLine(db, p7.id, p7Lift.id).db;
  assert.deepEqual(Core.getLinesForMaterial(db, p7Mat100.id, p7.id).map((l) => l.name), ['Line X']);

  // 7. Rename Volvo Right -> "Volvo Right A", assign material 100 to it -> assignment list updates automatically
  db = Core.renameLine(db, p7.id, p7Right.id, 'Volvo Right A').db;
  db = Core.addLinesToMaterial(db, p7.id, p7Mat100.id, [p7Right.id]).db;
  assert.deepEqual(
    Core.getLinesForMaterial(db, p7Mat100.id, p7.id).map((l) => l.name),
    ['Volvo Right A', 'Line X']
  );

  // 14. Create Plant 2 -> material 100 -> Volvo Right -> zero cross-plant leakage
  r = Core.createPlant(db, 'Plant 2'); db = r.db; const p2 = r.plant;
  r = Core.createLine(db, p2.id, { name: 'Volvo Right', plan_qty: 500 }); db = r.db; const p2Right = r.line;
  r = Core.saveMaterial(db, p2.id, {
    code: '100',
    name: 'خامة مصنع 2',
    stock_qty: 1200,
    lineIds: [p2Right.id]
  });
  db = r.db;
  const p2Mat100 = r.material;

  assert.notEqual(p7Mat100.id, p2Mat100.id);
  const f7 = Core.filterMaterials(db, p7.id, [p7Right.id], 'both');
  const f2 = Core.filterMaterials(db, p2.id, [p2Right.id], 'exclusive');
  assert.equal(f7.totalCount, 1);
  assert.equal(f7.rows[0].id, p7Mat100.id);
  assert.equal(f2.totalCount, 1);
  assert.equal(f2.rows[0].id, p2Mat100.id);
});

test('Mandatory Scenarios 8-11 & Stock Validation: Plan enforcement, exact messages, stock deduction, rollback on line delete', () => {
  let db = Core.createEmptyDb();
  let r = Core.createPlant(db, 'Plant 7'); db = r.db; const p7 = r.plant;

  r = Core.createLine(db, p7.id, { name: 'Volvo Lift', plan_qty: 500 }); db = r.db; const lLift = r.line;
  r = Core.createLine(db, p7.id, { name: 'Fresh Line', plan_qty: 500 }); db = r.db; const lFresh = r.line;

  r = Core.saveMaterial(db, p7.id, {
    code: '100',
    name: 'Mat 100',
    stock_qty: 2000,
    lineIds: [lLift.id]
  });
  db = r.db;
  const mat100 = r.material;

  // 8. Supply 400 to Volvo Lift (plan 500) -> In progress 400 / 500, stock -> 1600
  r = Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lLift.id, quantity: 400 });
  db = r.db;
  assert.equal(r.completed, false);
  assert.equal(r.line.received_qty, 400);
  assert.equal(r.material.stock_qty, 1600);
  assert.equal(Core.getLineStatus(r.line).status, 'in_progress');
  assert.match(Core.getLineStatus(r.line).label, /400 \/ 500/);

  // 9. Supply 100 -> Plan complete + exact success phrase
  r = Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lLift.id, quantity: 100 });
  db = r.db;
  assert.equal(r.completed, true);
  assert.equal(r.line.received_qty, 500);
  assert.equal(r.material.stock_qty, 1500);
  assert.equal(Core.getLineStatus(r.line).status, 'complete');
  assert.match(r.message, /Line has completed its plan/);

  // 10. Try 1 more -> exact error message with quantity 1
  assert.throws(() => {
    Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lLift.id, quantity: 1 });
  }, (err) => {
    assert.ok(err.message.includes("You exceeded the plan — you broke the line's plan with quantity 1"));
    assert.ok(!err.message.toLowerCase().includes('quantity is low'));
    return true;
  });

  // 11. Try 600 to a fresh line with plan 500 -> exact error message with quantity 600
  assert.throws(() => {
    Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lFresh.id, quantity: 600 });
  }, (err) => {
    assert.ok(err.message.includes("You exceeded the plan — you broke the line's plan with quantity 600"));
    assert.ok(!err.message.toLowerCase().includes('quantity is low'));
    return true;
  });

  // Delete Volvo Lift -> rolls back supplied 500 to mat100.stock_qty (1500 + 500 = 2000)
  const delLineRes = Core.deleteLine(db, p7.id, lLift.id);
  db = delLineRes.db;
  assert.equal(delLineRes.rolledBackQty, 500);
  assert.equal(Core.getMaterialsByPlant(db, p7.id)[0].stock_qty, 2000);

  // Oversupply vs stock -> blocked with "Not enough stock in warehouse"
  r = Core.createLine(db, p7.id, { name: 'Big Line', plan_qty: 5000 }); db = r.db; const lBig = r.line;
  assert.throws(() => {
    Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lBig.id, quantity: 2500 });
  }, /Not enough stock in warehouse/);

  // Low-stock warning (< 10% of initial)
  db = Core.supplyMaterialToLine(db, p7.id, { materialId: mat100.id, lineId: lBig.id, quantity: 1850 }).db;
  const updatedMat = Core.getMaterialsByPlant(db, p7.id)[0];
  assert.equal(updatedMat.stock_qty, 150); // 150 < 10% of 2000 (200)
  assert.equal(Core.isLowStock(updatedMat), true);
});

test('Smart Filtering Exclusive / Shared / Both + Line Ordering + Scoped Search + Safety Rules', () => {
  let db = Core.createEmptyDb();
  let r = Core.createPlant(db, 'Plant 7'); db = r.db; const p7 = r.plant;
  r = Core.createPlant(db, 'Plant 2'); db = r.db; const p2 = r.plant;

  const lLift = Core.createLine(db, p7.id, { name: 'Volvo Lift', plan_qty: 500 }); db = lLift.db;
  const lRight = Core.createLine(db, p7.id, { name: 'Volvo Right', plan_qty: 500 }); db = lRight.db;
  const lX = Core.createLine(db, p7.id, { name: 'Line X', plan_qty: 500 }); db = lX.db;
  const lP2 = Core.createLine(db, p2.id, { name: 'Volvo P2', plan_qty: 500 }); db = lP2.db;

  db = Core.saveMaterial(db, p7.id, { code: '100', name: 'Unique Lift', stock_qty: 1000, lineIds: [lLift.line.id] }).db;
  db = Core.saveMaterial(db, p7.id, { code: '200', name: 'Shared Lift+Right', stock_qty: 1000, lineIds: [lLift.line.id, lRight.line.id] }).db;
  db = Core.saveMaterial(db, p7.id, { code: '300', name: 'Unassigned', stock_qty: 1000, lineIds: [] }).db;

  // Supply 150 of mat 100 to Volvo Lift
  const m100 = Core.getMaterialsByPlant(db, p7.id).find((m) => m.code === '100');
  db = Core.supplyMaterialToLine(db, p7.id, { materialId: m100.id, lineId: lLift.line.id, quantity: 150 }).db;

  const excl = Core.filterMaterials(db, p7.id, [lLift.line.id], 'exclusive');
  assert.deepEqual(excl.rows.map((row) => row.code), ['100']);
  assert.equal(excl.rows[0].totalSupplied, 150);

  const shared = Core.filterMaterials(db, p7.id, [lLift.line.id], 'shared');
  assert.deepEqual(shared.rows.map((row) => row.code), ['200']);

  const both = Core.filterMaterials(db, p7.id, [lLift.line.id], 'both');
  assert.deepEqual(both.rows.map((row) => row.code), ['100', '200']); // 300 with 0 lines excluded

  // Cross-plant assignment rejection
  assert.throws(() => {
    Core.saveMaterial(db, p7.id, { code: '999', name: 'Bad', stock_qty: 100, lineIds: [lP2.line.id] });
  }, /عزل المصانع/);

  // Reorder & step move
  db = Core.moveLineStep(db, p7.id, lX.line.id, -1).db;
  assert.deepEqual(
    Core.getLinesByPlant(db, p7.id).map((l) => `${l.order}:${l.name}`),
    ['1:Volvo Lift', '2:Line X', '3:Volvo Right']
  );

  // Scoped search "Volvo"
  const p7Volvo = Core.getLinesByPlant(db, p7.id).filter((l) => l.name.toLowerCase().includes('volvo'));
  assert.deepEqual(p7Volvo.map((l) => l.name), ['Volvo Lift', 'Volvo Right']);

  // Empty numeric validation & sanitization
  assert.equal(Core.sanitizeNumericInput('abc١٠٠xyz'), '100');
  assert.equal(Core.validateRequiredNumber('').valid, false);
  assert.match(Core.validateRequiredNumber('').error, /please enter a number/);
  assert.deepEqual(Core.validateRequiredNumber('500', { min: 1 }), { valid: true, value: '500', number: 500 });

  // LocalStorage round-trip
  const storage = createMemoryStorage();
  Core.saveDb(db, storage);
  const reloaded = Core.loadDb(storage);
  assert.equal(reloaded.plants.length, 2);
  assert.equal(reloaded.lines[0].plan_qty, 500);
});
