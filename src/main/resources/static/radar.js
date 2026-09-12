const state = {
    watches: [],
    selectedId: null,
    history: [],
    loading: false
};

const $ = (selector) => document.querySelector(selector);

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            ...(options.body ? {"Content-Type": "application/json"} : {}),
            ...(options.headers || {})
        }
    });
    if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
            const body = await response.json();
            message = body.message || message;
        } catch (_) {
            // Keep the HTTP status when the server did not return JSON.
        }
        throw new Error(message);
    }
    if (response.status === 204) return null;
    return response.json();
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function formatNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number.toLocaleString("zh-CN") : "—";
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("zh-CN", {
        year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });
}

function sourceLabel(snapshot) {
    return snapshot?.metrics?.sourceType === "github-api" ? "GITHUB API" : "DEMO DATA";
}

function decisionClass(decision) {
    return String(decision || "watch").toLowerCase();
}

function showNotice(message, error = false) {
    const notice = $("#notice");
    notice.textContent = message;
    notice.classList.toggle("error", error);
    notice.hidden = false;
    window.clearTimeout(showNotice.timer);
    showNotice.timer = window.setTimeout(() => { notice.hidden = true; }, 5000);
}

function renderSummary() {
    const withSnapshots = state.watches.filter((watch) => watch.latestSnapshot);
    const scores = withSnapshots.map((watch) => Number(watch.latestSnapshot.score)).filter(Number.isFinite);
    const risks = withSnapshots.reduce((total, watch) => total + (watch.latestSnapshot.risks || []).length, 0);
    const recommendations = withSnapshots.filter((watch) => watch.latestSnapshot.decision === "RECOMMEND").length;
    $("#repoCount").textContent = formatNumber(state.watches.length);
    $("#averageScore").textContent = scores.length ? `${Math.round(scores.reduce((a, b) => a + b, 0) / scores.length)}` : "—";
    $("#recommendCount").textContent = formatNumber(recommendations);
    $("#riskCount").textContent = formatNumber(risks);
}

function renderWatchlist() {
    const body = $("#watchlistBody");
    const empty = $("#emptyState");
    if (!state.watches.length) {
        body.innerHTML = "";
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    body.innerHTML = state.watches.map((watch) => {
        const snapshot = watch.latestSnapshot;
        const risks = snapshot?.risks || [];
        const freshness = snapshot?.freshness || "未扫描";
        const freshnessClass = freshness.toLowerCase();
        return `<tr data-watch-id="${watch.id}" class="${watch.id === state.selectedId ? "selected" : ""}">
            <td class="repo-cell"><span class="repo-name">${escapeHtml(watch.repoName)}</span><span class="repo-display">${escapeHtml(watch.displayName)}</span></td>
            <td class="score-cell">${snapshot ? escapeHtml(snapshot.score) : "—"}</td>
            <td>${snapshot ? `<span class="decision decision-${decisionClass(snapshot.decision)}">${escapeHtml(snapshot.decision)}</span>` : "<span class=\"freshness\">未扫描</span>"}</td>
            <td>${snapshot ? `<span class="risk-count">${risks.length ? `${risks.length} 个信号` : "无明显风险"}</span>` : "—"}</td>
            <td><span class="freshness freshness-${freshnessClass}">${escapeHtml(freshness === "FRESH" ? "新鲜" : freshness === "AGING" ? "渐旧" : freshness === "STALE" ? "过期" : freshness)}</span></td>
        </tr>`;
    }).join("");
    body.querySelectorAll("tr[data-watch-id]").forEach((row) => {
        row.addEventListener("click", () => selectWatch(Number(row.dataset.watchId)));
    });
}

function renderComparison() {
    const chart = $("#comparisonChart");
    const ranked = state.watches
        .filter((watch) => watch.latestSnapshot)
        .sort((a, b) => Number(b.latestSnapshot.score) - Number(a.latestSnapshot.score));
    if (!ranked.length) {
        chart.innerHTML = '<div class="empty-cell">扫描仓库后，这里会显示评分分布。</div>';
        return;
    }
    chart.innerHTML = ranked.map((watch) => {
        const score = Number(watch.latestSnapshot.score) || 0;
        return `<div class="comparison-row">
            <div class="comparison-label"><strong>${escapeHtml(watch.displayName)}</strong><small>${escapeHtml(watch.repoName)}</small></div>
            <div class="comparison-track"><span style="width:${Math.max(0, Math.min(100, score))}%"></span></div>
            <div class="comparison-score">${score}</div>
        </div>`;
    }).join("");
}

function renderDetails(watch) {
    const snapshot = watch?.latestSnapshot;
    $("#selectedTitle").textContent = watch ? watch.displayName : "选择一个仓库";
    $("#selectedSource").textContent = snapshot ? sourceLabel(snapshot) : "等待扫描";
    $("#detailPlaceholder").hidden = Boolean(snapshot);
    $("#detailContent").hidden = !snapshot;
    if (!snapshot) return;

    $("#selectedDecision").textContent = snapshot.decision;
    $("#selectedScore").textContent = snapshot.score;
    $("#selectedSummary").textContent = snapshot.summary || "暂无摘要";
    $("#freshnessLabel").textContent = `${snapshot.freshness || "UNKNOWN"} · ${formatDate(snapshot.createdTime)}`;
    $("#collectedAt").textContent = `采集于 ${formatDate(snapshot.metrics?.collectedAt)}`;

    const risks = snapshot.risks || [];
    $("#riskList").innerHTML = risks.length ? risks.map((risk) => `<div class="risk-item ${String(risk.severity || "").toLowerCase()}">
        <strong>${escapeHtml(risk.title)} <small>${escapeHtml(risk.severity)}</small></strong>
        <p>${escapeHtml(risk.detail)}</p>
    </div>`).join("") : '<div class="no-risk">✓ 当前快照没有明显风险信号</div>';

    const criteria = snapshot.criteriaScores || {};
    $("#criteriaList").innerHTML = Object.entries(criteria).map(([name, value]) => `<div class="criteria-row">
        <label title="${escapeHtml(name)}">${escapeHtml(name)}</label>
        <div class="criteria-track"><span style="width:${Math.max(0, Math.min(100, Number(value) || 0))}%"></span></div>
        <span class="criteria-value">${escapeHtml(value)}</span>
    </div>`).join("") || '<span class="field-help">暂无评分拆解</span>';

    $("#evidenceList").innerHTML = (snapshot.evidence || []).map((evidence) => `<div class="evidence-item">
        <span class="evidence-dot"></span>
        <a href="${escapeHtml(evidence.source)}" target="_blank" rel="noreferrer">${escapeHtml(evidence.metric)} · ${escapeHtml(evidence.value)}</a>
        <span>${escapeHtml(evidence.note || "原始来源")}</span>
    </div>`).join("") || '<span class="field-help">暂无证据</span>';
}

function renderHistory() {
    $("#historyCount").textContent = `${state.history.length} 条快照`;
    $("#historyList").innerHTML = state.history.length ? state.history.slice(0, 6).map((snapshot) => `<div class="history-item">
        <time>${escapeHtml(formatDate(snapshot.createdTime))}</time>
        <strong>${escapeHtml(snapshot.score)} · ${escapeHtml(snapshot.decision)}</strong>
    </div>`).join("") : '<span class="field-help">还没有历史快照</span>';
}

async function selectWatch(id) {
    state.selectedId = id;
    renderWatchlist();
    const watch = state.watches.find((item) => item.id === id);
    renderDetails(watch);
    if (!watch) return;
    try {
        state.history = await api(`/api/radar/watchlist/${id}/snapshots`);
        renderHistory();
    } catch (error) {
        state.history = [];
        renderHistory();
        showNotice(`读取历史失败：${error.message}`, true);
    }
}

async function loadWatches(preferredId = state.selectedId) {
    try {
        const watches = await api("/api/radar/watchlist");
        state.watches = Array.isArray(watches) ? watches : [];
        renderSummary();
        renderWatchlist();
        renderComparison();
        const nextId = state.watches.some((watch) => watch.id === preferredId)
            ? preferredId
            : state.watches[0]?.id;
        if (nextId) await selectWatch(nextId);
        else {
            state.selectedId = null;
            state.history = [];
            renderDetails(null);
            renderHistory();
        }
        $("#runtimeBadge").textContent = "API 已连接";
        $("#runtimeBadge").classList.remove("offline");
    } catch (error) {
        $("#runtimeBadge").textContent = "API 不可用";
        $("#runtimeBadge").classList.add("offline");
        showNotice(`无法读取雷达数据：${error.message}`, true);
    }
}

async function scanAll() {
    if (state.loading) return;
    state.loading = true;
    const button = $("#scanButton");
    button.disabled = true;
    button.textContent = "扫描中…";
    try {
        const result = await api("/api/radar/scan-all", {method: "POST"});
        await loadWatches();
        showNotice(`扫描完成：${Array.isArray(result) ? result.length : 0} 个仓库已更新`);
    } catch (error) {
        showNotice(`扫描失败：${error.message}`, true);
    } finally {
        state.loading = false;
        button.disabled = false;
        button.textContent = "扫描全部";
    }
}

function openDialog() {
    const dialog = $("#watchDialog");
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
    $("#repoName").focus();
}

function closeDialog() {
    const dialog = $("#watchDialog");
    if (typeof dialog.close === "function") dialog.close();
    else dialog.removeAttribute("open");
}

async function addWatch(event) {
    event.preventDefault();
    const repoName = $("#repoName").value.trim();
    const displayName = $("#displayName").value.trim() || repoName;
    const submit = $("#watchForm button[type=submit]");
    submit.disabled = true;
    try {
        const created = await api("/api/radar/watchlist", {
            method: "POST",
            body: JSON.stringify({repoName, displayName, enabled: true})
        });
        closeDialog();
        $("#watchForm").reset();
        await loadWatches(created.id);
        showNotice(`${repoName} 已加入 Watchlist，可以开始扫描。`);
    } catch (error) {
        showNotice(`添加失败：${error.message}`, true);
    } finally {
        submit.disabled = false;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    $("#refreshButton").addEventListener("click", () => loadWatches());
    $("#scanButton").addEventListener("click", scanAll);
    $("#addButton").addEventListener("click", openDialog);
    $("#closeDialogButton").addEventListener("click", closeDialog);
    $("#cancelDialogButton").addEventListener("click", closeDialog);
    $("#watchForm").addEventListener("submit", addWatch);
    document.querySelectorAll("[data-open-add]").forEach((button) => button.addEventListener("click", openDialog));
    loadWatches();
});
