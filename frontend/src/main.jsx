import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Clock3, Download, Eye, Pencil, Play, Plus, Save, Search, Trash2, X } from "lucide-react";
import "./style.css";

const API = "/rental/api";
const TABS = [["in-use", "사용"], ["recovered", "회수"]];
const COLUMNS = {
  "in-use": [["item_name", "품명"], ["ip_address", "IP"], ["mac_address", "MAC"], ["model", "모델"], ["specification", "규격"], ["unit_price", "단가"], ["location", "위치"], ["location_detail", "위치 (세부)"], ["remarks", "비고"], ["contract_date", "계약일"]],
  recovered: [["item_name", "품명"], ["ip_address", "IP"], ["mac_address", "MAC"], ["model", "모델"], ["specification", "규격"], ["unit_price", "단가"], ["location", "위치"], ["location_detail", "위치 (세부)"], ["remarks", "비고"], ["contract_date", "계약일"]],
};
const VISIBLE = {
  "in-use": COLUMNS["in-use"].filter(([key]) => !["mac_address", "contract_date"].includes(key)),
  recovered: COLUMNS.recovered.filter(([key]) => !["mac_address", "specification", "contract_date"].includes(key)),
};
const NUMERIC = new Set(["unit_price"]);
const display = (value) => String(value ?? "").trim() || "—";

function useEscape(onClose) {
  useEffect(() => {
    const handler = (event) => { if (event.key === "Escape") onClose(); };
    addEventListener("keydown", handler);
    return () => removeEventListener("keydown", handler);
  }, [onClose]);
}

function EntryModal({ tab, row, saving, onClose, onSubmit }) {
  useEscape(onClose);
  const submit = (event) => {
    event.preventDefault();
    onSubmit(Object.fromEntries(new FormData(event.currentTarget).entries()));
  };
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <form className="modal entry-modal" onSubmit={submit} autoComplete="off">
      <div className="modal-header"><h2>{row ? "수정" : "추가"}</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="entry-grid">{COLUMNS[tab].map(([key, label]) =>
        <label key={key}>{label}
          <input name={key} type={NUMERIC.has(key) ? "number" : "text"}
            defaultValue={row?.[key] ?? ""} autoFocus={key === COLUMNS[tab][0][0]}
            autoComplete="off" />
        </label>)}</div>
      <div className="modal-footer"><button type="button" className="cancel" onClick={onClose}>취소</button>
        <button type="submit" className="save" disabled={saving}><Save size={15} />{saving ? "저장 중" : "확인"}</button></div>
    </form>
  </div>;
}

function DetailModal({ tab, row, onClose }) {
  useEscape(onClose);
  const title = TABS.find(([key]) => key === tab)?.[1] || "상세";
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal hardware-detail-modal" role="dialog" aria-modal="true" aria-label={title + " 상세"}>
      <div className="modal-header"><h2>{title} 상세</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <dl className="detail-grid">{COLUMNS[tab].map(([key, label]) => <div key={key}>
        <dt>{label}</dt><dd>{display(row[key])}</dd>
      </div>)}</dl>
      <div className="modal-footer">
        <button type="button" className="save" onClick={onClose}>확인</button>
      </div>
    </div>
  </div>;
}

function App() {
  const [tab, setTab] = useState("in-use");
  const [rows, setRows] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const [detail, setDetail] = useState(null);
  const [saving, setSaving] = useState(false);
  const [movingId, setMovingId] = useState(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const tools = document.querySelector(".sticky-tools");
    if (!tools) return;
    const update = () => document.documentElement.style.setProperty("--list-tools-height", `${tools.offsetHeight}px`);
    const observer = new ResizeObserver(update);
    observer.observe(tools);
    update();
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    fetch(API + "/" + tab, { signal: controller.signal, cache: "no-store" })
      .then((response) => response.ok ? response.json() : Promise.reject(new Error("HTTP " + response.status)))
      .then(setRows)
      .catch((failure) => { if (failure.name !== "AbortError") setError("임대 목록을 불러오지 못했습니다."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tab]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase("ko");
    return needle ? rows.filter((row) => COLUMNS[tab].some(([key]) => display(row[key]).toLocaleLowerCase("ko").includes(needle))) : rows;
  }, [rows, query, tab]);
  const inUsePriceTotal = useMemo(() => tab === "in-use"
    ? filtered.reduce((total, row) => total + (Number(row.unit_price) || 0), 0)
    : 0, [filtered, tab]);

  const changeTab = (next) => {
    setTab(next); setQuery(""); setNotice(""); setEditing(null); setDetail(null); setAdding(false);
  };
  const openRecord = async (row, target) => {
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { cache: "no-store" });
      if (!response.ok) throw new Error();
      const full = await response.json();
      if (target === "edit") setEditing(full); else setDetail(full);
    } catch { setNotice("상세 내용을 불러오지 못했습니다."); }
  };
  const save = async (values) => {
    setSaving(true); setNotice("");
    try {
      const response = await fetch(API + "/" + tab + (editing ? "/" + editing.source_row : ""), {
        method: editing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const saved = await response.json();
      setRows((current) => editing
        ? current.map((item) => item.source_row === editing.source_row ? saved : item)
        : [...current, saved].sort((a, b) => a.source_row - b.source_row));
      setAdding(false); setEditing(null);
      setNotice(editing ? "수정했습니다." : "추가했습니다.");
    } catch { setNotice("저장하지 못했습니다. 입력 내용을 확인해 주세요."); }
    finally { setSaving(false); }
  };
  const remove = async (row) => {
    if (!confirm("해당 항목을 삭제하시겠습니까? 삭제한 데이터는 복구할 수 없습니다.")) return;
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { method: "DELETE" });
      if (!response.ok) throw new Error();
      setRows((current) => current.filter((item) => item.source_row !== row.source_row));
      setNotice("삭제했습니다.");
    } catch { setNotice("삭제하지 못했습니다."); }
  };
  const move = async (row) => {
    const destination = tab === "in-use" ? "회수" : "사용";
    if (!confirm(`정말 ${destination} 탭으로 옮기시겠습니까?`)) return;
    setMovingId(row.source_row); setNotice("");
    try {
      const response = await fetch(`${API}/${tab}/${row.source_row}/move`, { method: "POST" });
      if (!response.ok) throw new Error();
      setRows((current) => current.filter((item) => item.source_row !== row.source_row));
      setNotice(`${destination} 탭으로 옮겼습니다.`);
    } catch { setNotice("항목을 옮기지 못했습니다."); }
    finally { setMovingId(null); }
  };
  const exportExcel = async () => {
    setExporting(true); setNotice("");
    try {
      const response = await fetch(`${API}/in-use/export?search=${encodeURIComponent(query.trim())}`, { cache: "no-store" });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = "SRGH 다한오에이 임대프린터.xlsx";
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch { setNotice("엑셀 파일을 내려받지 못했습니다."); }
    finally { setExporting(false); }
  };

  return <main className="app">
    <header className="hero"><div className="hero-title"><h1>임대</h1></div></header>
    <section className="sticky-tools"><div className="control-row">
      <nav className="tabs" aria-label="임대 목록 종류">{TABS.map(([key, label]) =>
        <button key={key} className={tab === key ? "active" : ""} onClick={() => changeTab(key)}>{label}</button>)}</nav>
      <label className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="검색" aria-label="임대 목록 검색" />
        <button type="button" onClick={() => setQuery("")} aria-label="검색어 지우기">{query && <X size={15} />}</button></label>
      {tab === "in-use" && <div className="list-summary" aria-live="polite">
        <span className="summary-item"><span className="summary-label">총</span><strong>{filtered.length.toLocaleString()}개</strong></span>
        <span className="summary-item"><span className="summary-label">단가 합계</span><strong>{inUsePriceTotal.toLocaleString("ko-KR")}원</strong></span>
      </div>}
      <div className="filter-end">
        {tab === "in-use" && <button className="export-button" onClick={exportExcel} disabled={loading || !!error || exporting}><Download size={15} />{exporting ? "출력 중" : "출력"}</button>}
        <button className="add-button" onClick={() => setAdding(true)}><Plus size={15} />추가</button>
      </div>
    </div></section>
    {notice && <div className="notice">{notice}</div>}
    {loading && <div className="state">임대 목록을 불러오는 중입니다.</div>}
    {error && <div className="error">{error}</div>}
    {!loading && !error && <section className="table-card" aria-label={TABS.find(([key]) => key === tab)?.[1]}>
      <table className={"table-" + tab}><thead><tr>
        {VISIBLE[tab].map(([key, label]) => <th key={key}>{label}</th>)}
        <th className="manage-heading">관리</th>
      </tr></thead><tbody>{filtered.map((row) => <React.Fragment key={row.source_row}>
        <tr>
        {VISIBLE[tab].map(([key]) => <td key={key} title={display(row[key])}><span className="cell-text">{display(row[key])}</span></td>)}
        <td className="manage-cell"><div className="actions">
          <button className="detail-button icon-only" title="자세히" aria-label={row.source_row + "번 행 자세히"} onClick={() => openRecord(row, "detail")}><Eye size={14} /></button>
          <button className="edit icon-only" title="수정" aria-label={row.source_row + "번 행 수정"} onClick={() => openRecord(row, "edit")}><Pencil size={15} /></button>
          {tab === "in-use"
            ? <button className="temporary icon-only" title="회수" aria-label={row.source_row + "번 행 회수"} disabled={movingId === row.source_row} onClick={() => move(row)}><Clock3 size={14} /></button>
            : <button className="use-button icon-only" title="사용" aria-label={row.source_row + "번 행 사용"} disabled={movingId === row.source_row} onClick={() => move(row)}><Play size={14} /></button>}
          <button className="danger icon-only" title="삭제" aria-label={row.source_row + "번 행 삭제"} onClick={() => remove(row)}><Trash2 size={15} /></button>
        </div></td>
      </tr></React.Fragment>)}</tbody></table>
      {!filtered.length && <div className="state">검색 결과가 없습니다.</div>}
    </section>}
    {(adding || editing) && <EntryModal key={tab + (editing?.source_row ?? "new")} tab={tab} row={editing}
      saving={saving} onClose={() => { setAdding(false); setEditing(null); }} onSubmit={save} />}
    {detail && <DetailModal tab={tab} row={detail} onClose={() => setDetail(null)} />}
  </main>;
}

createRoot(document.getElementById("root")).render(<App />);
