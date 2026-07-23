let currentUserTypeId = null;
let currentMatrix = {};   // moduleId -> {canView, canCreate, canEdit, canDelete}
let dirty = false;

function selectUserType(tabEl) {
    document.querySelectorAll('.type-tab').forEach(t => t.classList.remove('active'));
    tabEl.classList.add('active');

    currentUserTypeId = tabEl.getAttribute('data-type-id');
    document.getElementById('activeTypeLabel').textContent = tabEl.textContent.trim();
    loadMatrix(currentUserTypeId);
}

function reloadCurrentType() {
    if (currentUserTypeId) loadMatrix(currentUserTypeId);
}

async function loadMatrix(userTypeId) {
    setStatus('Loading…', 'text-muted');
    try {
        const res = await fetch(`/settings/module-assignment/api/${userTypeId}`);
        if (!res.ok) throw new Error('Failed to load (' + res.status + ')');
        const rows = await res.json();

        currentMatrix = {};
        rows.forEach(r => { currentMatrix[r.moduleId] = r; });

        renderTable();
        dirty = false;
        document.getElementById('saveBtn').disabled = false;
        setStatus('', '');
    } catch (err) {
        console.error(err);
        setStatus('Could not load permissions for this user type.', 'text-danger');
    }
}

function renderTable() {
    // ALL_MODULES is injected server-side (settings-module-assignment.html)
    const parents = ALL_MODULES.filter(m => !m.parentModuleId)
        .sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));
    const childrenOf = pid => ALL_MODULES.filter(m => m.parentModuleId === pid)
        .sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));

    const body = document.getElementById('matrixBody');
    body.innerHTML = '';

    parents.forEach(parent => {
        body.appendChild(buildRow(parent, true));
        childrenOf(parent.moduleId).forEach(child => {
            body.appendChild(buildRow(child, false));
        });
    });
}

function buildRow(module, isParent) {
    const perms = currentMatrix[module.moduleId] || {
        canView: false, canCreate: false, canEdit: false, canDelete: false
    };

    const tr = document.createElement('tr');
    tr.className = isParent ? 'parent-row' : 'child-row';
    tr.dataset.moduleId = module.moduleId;

    tr.innerHTML = `
        <td class="module-col"><i class="bi ${module.icon || 'bi-dot'}"></i>${module.moduleName}</td>
        ${checkboxCell(module.moduleId, 'canView', perms.canView)}
        ${checkboxCell(module.moduleId, 'canCreate', perms.canCreate)}
        ${checkboxCell(module.moduleId, 'canEdit', perms.canEdit)}
        ${checkboxCell(module.moduleId, 'canDelete', perms.canDelete)}
    `;
    return tr;
}

function checkboxCell(moduleId, field, checked) {
    return `<td>
        <input type="checkbox" class="form-check-input" data-module-id="${moduleId}" data-field="${field}"
               ${checked ? 'checked' : ''} onchange="onCheckChange(this)">
    </td>`;
}

function onCheckChange(checkbox) {
    const moduleId = checkbox.getAttribute('data-module-id');
    const field = checkbox.getAttribute('data-field');
    if (!currentMatrix[moduleId]) {
        currentMatrix[moduleId] = { canView: false, canCreate: false, canEdit: false, canDelete: false };
    }
    currentMatrix[moduleId][field] = checkbox.checked;

    // Checking create/edit/delete implies view — a user can't edit
    // something they can't see.
    if (checkbox.checked && field !== 'canView') {
        currentMatrix[moduleId].canView = true;
        const viewBox = document.querySelector(
            `input[data-module-id="${moduleId}"][data-field="canView"]`);
        if (viewBox) viewBox.checked = true;
    }

    dirty = true;
    setStatus('Unsaved changes', 'text-warning');
}

function toggleColumn(field) {
    const boxes = document.querySelectorAll(`input[data-field="${field}"]`);
    const allChecked = Array.from(boxes).every(b => b.checked);
    boxes.forEach(b => {
        b.checked = !allChecked;
        onCheckChange(b);
    });
}

async function saveMatrix() {
    if (!currentUserTypeId) return;

    const rows = ALL_MODULES.map(m => {
        const p = currentMatrix[m.moduleId] || { canView: false, canCreate: false, canEdit: false, canDelete: false };
        return {
            moduleId: m.moduleId,
            moduleName: m.moduleName,
            moduleCode: m.moduleCode,
            parentModuleId: m.parentModuleId,
            icon: m.icon,
            displayOrder: m.displayOrder,
            canView: !!p.canView,
            canCreate: !!p.canCreate,
            canEdit: !!p.canEdit,
            canDelete: !!p.canDelete
        };
    });

    setStatus('Saving…', 'text-muted');
    try {
        const res = await fetch(`/settings/module-assignment/api/${currentUserTypeId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rows)
        });
        if (!res.ok) throw new Error('Save failed (' + res.status + ')');
        dirty = false;
        setStatus('Saved ✓', 'text-success');
    } catch (err) {
        console.error(err);
        setStatus('Save failed — try again.', 'text-danger');
    }
}

function setStatus(text, cls) {
    const el = document.getElementById('saveStatus');
    el.textContent = text;
    el.className = cls || 'text-muted';
}

window.addEventListener('beforeunload', (e) => {
    if (dirty) {
        e.preventDefault();
        e.returnValue = '';
    }
});
