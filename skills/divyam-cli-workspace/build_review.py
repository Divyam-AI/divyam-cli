#!/usr/bin/env python3
"""Build a fully self-contained review page (no fetch, no CDN) from the eval workspace."""
import json
import pathlib

ITER = pathlib.Path(__file__).parent / "iteration-1"
OUT = pathlib.Path(__file__).parent / "review.html"

evals = []
for d in sorted(ITER.glob("eval-*")):
    meta = json.load(open(d / "eval_metadata.json"))
    entry = {"id": meta["eval_id"], "name": meta["eval_name"], "prompt": meta["prompt"], "configs": {}}
    for cfg in ("with_skill", "without_skill"):
        rd = d / cfg
        if not rd.exists():
            continue
        resp = (rd / "outputs" / "response.md")
        grading = json.load(open(rd / "grading.json"))["expectations"]
        timing = json.load(open(rd / "timing.json"))
        entry["configs"][cfg] = {
            "response": resp.read_text() if resp.exists() else "(no response captured)",
            "grades": grading,
            "timing": timing,
        }
    evals.append(entry)

benchmark = json.load(open(ITER / "benchmark.json"))
data = {"evals": evals, "benchmark": benchmark}
payload = json.dumps(data, ensure_ascii=False)

HTML = r"""<title>divyam-cli — eval review</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root{
  --ground:#f6f7f9; --surface:#ffffff; --surface-2:#f0f2f6; --ink:#1a1d24; --muted:#5b6472;
  --border:#e4e8ee; --accent:#3a5bd9; --accent-soft:#e7ecfd; --pass:#1a7f4b; --pass-soft:#e3f3ea;
  --fail:#c0392b; --fail-soft:#fbe9e7; --code-bg:#f4f6fb; --code-ink:#243044;
}
:root:not([data-theme="light"]){}
@media (prefers-color-scheme: dark){
  :root:not([data-theme="light"]){
    --ground:#0f1217; --surface:#171b22; --surface-2:#1e232c; --ink:#e6e9ef; --muted:#9aa4b2;
    --border:#272d38; --accent:#8098ff; --accent-soft:#20283f; --pass:#4ade80; --pass-soft:#14301f;
    --fail:#f87171; --fail-soft:#331a1a; --code-bg:#12161d; --code-ink:#cdd6e5;
  }
}
:root[data-theme="dark"]{
  --ground:#0f1217; --surface:#171b22; --surface-2:#1e232c; --ink:#e6e9ef; --muted:#9aa4b2;
  --border:#272d38; --accent:#8098ff; --accent-soft:#20283f; --pass:#4ade80; --pass-soft:#14301f;
  --fail:#f87171; --fail-soft:#331a1a; --code-bg:#12161d; --code-ink:#cdd6e5;
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;line-height:1.55;
  font-size:15px;-webkit-font-smoothing:antialiased}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
header{padding:22px clamp(16px,4vw,40px) 0;max-width:1280px;margin:0 auto}
h1{font-size:22px;margin:0 0 2px;letter-spacing:-.01em}
.sub{color:var(--muted);font-size:13.5px;margin:0 0 16px}
.tabs{display:flex;gap:4px;border-bottom:1px solid var(--border)}
.tab{appearance:none;background:none;border:0;color:var(--muted);font:inherit;font-weight:600;
  padding:9px 14px;cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-1px}
.tab[aria-selected="true"]{color:var(--accent);border-bottom-color:var(--accent)}
.tab:focus-visible{outline:2px solid var(--accent);outline-offset:2px;border-radius:6px}
main{max-width:1280px;margin:0 auto;padding:18px clamp(16px,4vw,40px) 60px}
.wrap{display:grid;grid-template-columns:288px 1fr;gap:22px;align-items:start}
@media (max-width:820px){.wrap{grid-template-columns:1fr}}
.rail{display:flex;flex-direction:column;gap:6px;position:sticky;top:14px}
.rail-item{text-align:left;background:var(--surface);border:1px solid var(--border);border-radius:10px;
  padding:10px 12px;cursor:pointer;color:inherit;font:inherit;display:flex;flex-direction:column;gap:6px}
.rail-item:hover{border-color:var(--accent)}
.rail-item[aria-current="true"]{border-color:var(--accent);box-shadow:0 0 0 1px var(--accent)}
.rail-item:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.rail-top{display:flex;align-items:center;gap:8px;justify-content:space-between}
.rail-name{font-weight:600;font-size:13.5px}
.rail-prompt{color:var(--muted);font-size:12px;overflow:hidden;text-overflow:ellipsis;
  display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.badge{font-size:11px;font-weight:700;padding:2px 7px;border-radius:20px;white-space:nowrap;
  font-variant-numeric:tabular-nums}
.badge.pass{background:var(--pass-soft);color:var(--pass)}
.badge.part{background:var(--fail-soft);color:var(--fail)}
.panel{display:flex;flex-direction:column;gap:16px;min-width:0}
.card{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px 18px}
.eyebrow{text-transform:uppercase;letter-spacing:.08em;font-size:11px;font-weight:700;color:var(--muted);margin:0 0 8px}
.prompt-quote{font-size:16px;margin:0}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}
@media (max-width:1040px){.cols{grid-template-columns:1fr}}
.col h3{margin:0;font-size:13px;display:flex;align-items:center;gap:8px}
.dot{width:9px;height:9px;border-radius:50%}
.dot.ws{background:var(--accent)} .dot.bl{background:var(--muted)}
.col-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}
.meta{color:var(--muted);font-size:11.5px;font-variant-numeric:tabular-nums}
.md{font-size:13.5px}
.md h1,.md h2,.md h3{font-size:14px;margin:14px 0 6px;letter-spacing:-.01em}
.md h1:first-child,.md h2:first-child{margin-top:0}
.md p{margin:8px 0}
.md ul{margin:8px 0;padding-left:20px} .md li{margin:3px 0}
.md code{background:var(--code-bg);color:var(--code-ink);padding:1px 5px;border-radius:5px;font-size:12.5px}
.md pre{background:var(--code-bg);color:var(--code-ink);padding:11px 13px;border-radius:9px;
  overflow-x:auto;border:1px solid var(--border)}
.md pre code{background:none;padding:0;font-size:12.5px;line-height:1.5}
.md table{border-collapse:collapse;width:100%;font-size:12.5px;margin:8px 0;display:block;overflow-x:auto}
.md th,.md td{border:1px solid var(--border);padding:5px 9px;text-align:left}
.md th{background:var(--surface-2)}
.grades{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:6px}
.grade{display:flex;gap:9px;align-items:flex-start;font-size:12.5px}
.chk{flex:none;width:17px;height:17px;border-radius:5px;display:grid;place-items:center;
  font-size:11px;font-weight:800;margin-top:1px}
.chk.y{background:var(--pass-soft);color:var(--pass)} .chk.n{background:var(--fail-soft);color:var(--fail)}
.score-line{display:flex;gap:14px;flex-wrap:wrap;align-items:center;margin-bottom:2px}
.bench table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums;font-size:13.5px}
.bench th,.bench td{border-bottom:1px solid var(--border);padding:9px 12px;text-align:left}
.bench th{font-size:11.5px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted)}
.bench td.num{text-align:right;font-family:ui-monospace,Menlo,monospace}
.bar{height:8px;border-radius:6px;background:var(--surface-2);overflow:hidden;min-width:90px}
.bar>span{display:block;height:100%;background:var(--accent)}
.bar.bl>span{background:var(--muted)}
.notes{margin:16px 0 0;padding-left:20px;color:var(--muted);font-size:13px}
.notes li{margin:6px 0}
.hidden{display:none}
.kbd{font-size:11px;color:var(--muted)}
.pill{font-size:11px;font-weight:700;padding:2px 8px;border-radius:20px;background:var(--accent-soft);color:var(--accent)}
</style>

<header>
  <h1>divyam-cli · eval review</h1>
  <p class="sub">6 scenarios · with-skill vs. no-skill baseline · <span id="hsummary"></span> &nbsp;<span class="kbd">↑/↓ to move between scenarios</span></p>
  <div class="tabs" role="tablist">
    <button class="tab" role="tab" id="tab-out" aria-selected="true" aria-controls="p-out">Outputs</button>
    <button class="tab" role="tab" id="tab-bench" aria-selected="false" aria-controls="p-bench">Benchmark</button>
  </div>
</header>

<main>
  <section id="p-out" role="tabpanel" aria-labelledby="tab-out">
    <div class="wrap">
      <nav class="rail" id="rail" aria-label="scenarios"></nav>
      <div class="panel" id="detail"></div>
    </div>
  </section>
  <section id="p-bench" role="tabpanel" aria-labelledby="tab-bench" class="hidden bench"></section>
</main>

<script id="data" type="application/json">__DATA__</script>
<script>
const DATA = JSON.parse(document.getElementById('data').textContent);
const CFG = [["with_skill","With skill","ws"],["without_skill","No skill (baseline)","bl"]];
const esc = s => s.replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));

// Minimal, safe markdown -> HTML for the response format (headings, fences, tables, lists, bold, inline code).
function md(src){
  const lines = src.replace(/\r/g,'').split('\n');
  let html='', i=0;
  const inline = t => esc(t)
    .replace(/`([^`]+)`/g,'<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>')
    .replace(/(^|[\s(])\*([^*\s][^*]*)\*/g,'$1<em>$2</em>');
  while(i<lines.length){
    let ln=lines[i];
    if(/^```/.test(ln)){ let buf=[]; i++; while(i<lines.length && !/^```/.test(lines[i])){buf.push(lines[i]);i++;} i++;
      html+='<pre><code>'+esc(buf.join('\n'))+'</code></pre>'; continue; }
    let m=ln.match(/^(#{1,6})\s+(.*)/); if(m){ html+='<h3>'+inline(m[2])+'</h3>'; i++; continue; }
    if(/^\s*\|.*\|\s*$/.test(ln)){ let rows=[]; while(i<lines.length && /^\s*\|.*\|\s*$/.test(lines[i])){rows.push(lines[i]);i++;}
      const cells=r=>r.trim().replace(/^\||\|$/g,'').split('|').map(c=>c.trim());
      let t='<table>'; rows.forEach((r,ri)=>{ if(/^\s*\|?[\s:|-]+\|?\s*$/.test(r)) return;
        const tag=ri===0?'th':'td'; t+='<tr>'+cells(r).map(c=>`<${tag}>${inline(c)}</${tag}>`).join('')+'</tr>'; });
      html+=t+'</table>'; continue; }
    if(/^\s*[-*]\s+/.test(ln)){ let items=[]; while(i<lines.length && /^\s*[-*]\s+/.test(lines[i])){items.push(lines[i].replace(/^\s*[-*]\s+/,''));i++;}
      html+='<ul>'+items.map(x=>'<li>'+inline(x)+'</li>').join('')+'</ul>'; continue; }
    if(ln.trim()===''){ i++; continue; }
    let para=[]; while(i<lines.length && lines[i].trim()!=='' && !/^```|^#{1,6}\s|^\s*\|.*\|\s*$|^\s*[-*]\s+/.test(lines[i])){para.push(lines[i]);i++;}
    html+='<p>'+inline(para.join(' '))+'</p>';
  }
  return html;
}

function score(cfg){ const g=cfg.grades; return [g.filter(x=>x.passed).length, g.length]; }

const rail=document.getElementById('rail'), detail=document.getElementById('detail');
let active=0;

DATA.evals.forEach((ev,idx)=>{
  const [p,t]=score(ev.configs.with_skill);
  const b=document.createElement('button');
  b.className='rail-item'; b.setAttribute('role','button');
  b.innerHTML=`<div class="rail-top"><span class="rail-name">${esc(ev.name)}</span>
    <span class="badge ${p===t?'pass':'part'}">${p}/${t}</span></div>
    <div class="rail-prompt">${esc(ev.prompt)}</div>`;
  b.onclick=()=>select(idx);
  rail.appendChild(b);
});

function grades(list){
  return '<ul class="grades">'+list.map(g=>`<li class="grade">
    <span class="chk ${g.passed?'y':'n'}">${g.passed?'✓':'✕'}</span><span>${esc(g.text)}</span></li>`).join('')+'</ul>';
}

function select(idx){
  active=idx;
  [...rail.children].forEach((c,i)=>c.setAttribute('aria-current', i===idx?'true':'false'));
  const ev=DATA.evals[idx];
  let cols=CFG.map(([key,label,cls])=>{
    const cfg=ev.configs[key]; if(!cfg) return '';
    const [p,t]=score(cfg); const secs=cfg.timing.total_duration_seconds;
    return `<div class="col card">
      <div class="col-head"><h3><span class="dot ${cls}"></span>${label}</h3>
        <span class="badge ${p===t?'pass':'part'}">${p}/${t}</span></div>
      <div class="md">${md(cfg.response)}</div>
      <p class="eyebrow" style="margin:16px 0 8px">Grader checks</p>
      ${grades(cfg.grades)}
      <p class="meta" style="margin-top:10px">${secs}s · ${cfg.timing.total_tokens.toLocaleString()} tokens</p>
    </div>`;
  }).join('');
  detail.innerHTML=`<div class="card">
      <p class="eyebrow">Scenario ${idx+1} of ${DATA.evals.length} · <span class="pill">${esc(ev.name)}</span></p>
      <p class="prompt-quote mono">“${esc(ev.prompt)}”</p></div>
    <div class="cols">${cols}</div>`;
  detail.scrollTop=0;
}

// header summary
(function(){
  let ws=0,wt=0,bs=0,bt=0;
  DATA.evals.forEach(ev=>{ let a=score(ev.configs.with_skill); ws+=a[0];wt+=a[1];
    if(ev.configs.without_skill){let b=score(ev.configs.without_skill); bs+=b[0];bt+=b[1];} });
  document.getElementById('hsummary').innerHTML=
    `with-skill <strong>${ws}/${wt}</strong> · baseline <strong>${bs}/${bt}</strong>`;
})();

// Benchmark tab
(function(){
  const bm=DATA.benchmark, s=bm.run_summary, host=document.getElementById('p-bench');
  const pct=x=>Math.round(x*100);
  let rows=DATA.evals.map(ev=>{
    const w=score(ev.configs.with_skill), b=ev.configs.without_skill?score(ev.configs.without_skill):[0,0];
    const wp=w[1]?w[0]/w[1]:0, bp=b[1]?b[0]/b[1]:0;
    return `<tr><td>${esc(ev.name)}</td>
      <td><div class="bar"><span style="width:${pct(wp)}%"></span></div></td>
      <td class="num">${pct(wp)}%</td>
      <td><div class="bar bl"><span style="width:${pct(bp)}%"></span></div></td>
      <td class="num">${pct(bp)}%</td></tr>`;
  }).join('');
  const notes=(bm.notes||[]).map(n=>`<li>${esc(n)}</li>`).join('');
  host.innerHTML=`<div class="card">
    <p class="eyebrow">Pass rate · with-skill vs. baseline</p>
    <div class="score-line" style="margin-bottom:14px">
      <span class="pill">with-skill mean ${pct(s.with_skill.pass_rate.mean)}%</span>
      <span class="pill" style="background:var(--surface-2);color:var(--muted)">baseline mean ${pct(s.without_skill.pass_rate.mean)}%</span>
      <span class="pill" style="background:var(--pass-soft);color:var(--pass)">Δ ${s.delta.pass_rate}</span>
    </div>
    <table><thead><tr><th>Scenario</th><th>With skill</th><th class="num">%</th><th>Baseline</th><th class="num">%</th></tr></thead>
      <tbody>${rows}</tbody></table>
    <p class="meta" style="margin-top:12px">Time: with-skill ${s.with_skill.time_seconds.mean}s vs baseline ${s.without_skill.time_seconds.mean}s (Δ ${s.delta.time_seconds}s). Tokens Δ ${s.delta.tokens}. Single run per config — directional, not variance-tested.</p>
    <p class="eyebrow" style="margin-top:18px">Observations</p>
    <ul class="notes">${notes}</ul></div>`;
})();

// Tabs
const tOut=document.getElementById('tab-out'), tBench=document.getElementById('tab-bench');
const pOut=document.getElementById('p-out'), pBench=document.getElementById('p-bench');
function tab(which){
  const out=which==='out';
  tOut.setAttribute('aria-selected',out); tBench.setAttribute('aria-selected',!out);
  pOut.classList.toggle('hidden',!out); pBench.classList.toggle('hidden',out);
}
tOut.onclick=()=>tab('out'); tBench.onclick=()=>tab('bench');

document.addEventListener('keydown',e=>{
  if(pOut.classList.contains('hidden')) return;
  if(e.key==='ArrowDown'){e.preventDefault();select(Math.min(active+1,DATA.evals.length-1));}
  if(e.key==='ArrowUp'){e.preventDefault();select(Math.max(active-1,0));}
});

select(0);
</script>
"""

OUT.write_text(HTML.replace("__DATA__", payload), encoding="utf-8")
print("wrote", OUT, OUT.stat().st_size, "bytes")
