/**
 * Click router for the dynamic sidebar (fragments/sidebar.html).
 *
 * Every dynamically-rendered menu link carries data-url and data-code
 * attributes instead of a hard-coded onclick. This file decides, at click
 * time, which of your EXISTING page functions to call:
 *
 *   - DASHBOARD              -> showDashboard(this)          (SPA toggle)
 *   - real module_url        -> navigateTo(event, this, url) (page nav)
 *   - module_url is '#'/null -> openReportModule(...)        (modal/report)
 *
 * openReportModule() maps module_code -> the specific show*Report()
 * function you already wrote for that quick-report modal. If you add a new
 * report module row with url '#' in the `modules` table, add one line here
 * too so its click has somewhere to go.
 */

function handleMenuClick(event, el) {
    event.preventDefault();

    const url = el.getAttribute('data-url');
    const code = el.getAttribute('data-code');

    if (code === 'DASHBOARD') {
        if (typeof showDashboard === 'function') {
            showDashboard(el);
        }
        return;
    }

    if (url && url !== '#' && url !== 'null' && url !== '') {
        navigateTo(event, el, url);
        return;
    }

    openReportModule(event, el, code);
}

function openReportModule(event, el, moduleCode) {
    const handlers = {
        REPORT_BIRTH: () => typeof showBirthReport === 'function' && showBirthReport(event, el),
        REPORT_TREATMENT: () => typeof showTreatmentReport === 'function' && showTreatmentReport(event, el),
        REPORT_SICK: () => typeof showSickReport === 'function' && showSickReport(event, el),
        REPORT_ABORTION: () => typeof showAbortionReport === 'function' && showAbortionReport(event, el),
        REPORT_SALES: () => typeof showSalesReport === 'function' && showSalesReport(event, el),
        REPORT_DEATHS: () => typeof showDeathsReport === 'function' && showDeathsReport(event, el),
        REPORT_OVERVIEW: () => typeof showOverviewReport === 'function' && showOverviewReport(event, el)
    };

    if (handlers[moduleCode]) {
        handlers[moduleCode]();
    } else {
        console.warn('[dynamic-menu] No click handler registered for module code:', moduleCode,
            '- add one in dynamic-menu.js (openReportModule).');
    }
}
