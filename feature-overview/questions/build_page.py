#!/usr/bin/env python3
"""Builds review.html (one self-contained page) from every questions/*.json. Run: python3 build_page.py"""
import json, glob, os
here = os.path.dirname(os.path.abspath(__file__))
clusters = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(here, "*.json")))]
ANSWERED = {"GUI-PACK"}
tpl = r'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Phoenix Skyblock - owner decisions</title>
<style>
:root{--red:#c62828;--bg:#14161a;--card:#1d2026;--line:#2d323b;--txt:#e8eaed;--mut:#9aa3b0;--ok:#2e7d32;--yel:#ffd54f}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--txt);font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif}
header{position:sticky;top:0;z-index:5;background:#0e1013;border-bottom:2px solid var(--red);padding:10px 16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
header h1{font-size:17px;margin:0;flex:1;min-width:200px}header .prog{color:var(--mut)}
button{background:var(--red);color:#fff;border:0;border-radius:6px;padding:8px 14px;font-weight:600;cursor:pointer}button.sec{background:#2d323b}button:hover{filter:brightness(1.15)}
main{max-width:860px;margin:0 auto;padding:16px}h2{margin:28px 0 4px;border-left:4px solid var(--red);padding-left:10px}
.sub{color:var(--mut);margin:0 0 12px}.q{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px;margin:14px 0}
.q.done{border-color:#355a38}.q h3{margin:0 0 4px;font-size:16px}.tag{display:inline-block;background:#2d323b;border-radius:4px;padding:1px 7px;font-size:12px;color:var(--mut);margin-right:8px}
.ctx{color:var(--mut);margin:0 0 10px}label.opt{display:block;border:1px solid var(--line);border-radius:8px;padding:9px 12px;margin:6px 0;cursor:pointer}
label.opt:hover{border-color:#555}label.opt.sel{border-color:var(--yel);background:#2a2713}label.opt b{font-size:15px}label.opt span{display:block;color:var(--mut);font-size:13px;margin-left:24px}
.rec{background:var(--ok);color:#fff;border-radius:4px;font-size:11px;padding:1px 6px;margin-left:6px;vertical-align:middle}
input[type=radio]{margin-right:8px}textarea{width:100%;background:#111317;color:var(--txt);border:1px solid var(--line);border-radius:6px;padding:8px;margin-top:6px;font:inherit;min-height:38px}
.note{margin-top:8px}dialog{max-width:760px;width:92%;background:var(--card);color:var(--txt);border:1px solid var(--red);border-radius:10px}dialog textarea{min-height:320px;font-family:ui-monospace,Menlo,monospace;font-size:13px}
</style></head><body>
<header><h1>Phoenix Skyblock: owner decisions</h1><span class="prog" id="prog"></span>
<button class="sec" id="acc">Accept all suggested</button><button class="sec" id="clr">Reset</button><button id="exp">Finish: copy my answers</button></header>
<main><p class="sub">Click an option, or choose &quot;Other&quot; and write your own. The yellow outline is your pick; the green tag is what the developer suggests. Nothing is saved anywhere except this browser. When done, press <b>Finish</b> and paste the text into the chat.</p><div id="toc"></div><div id="root"></div></main>
<dialog id="dlg"><p>Select all, copy, and paste into the chat:</p><textarea id="out" readonly></textarea><p><button id="cp">Copy</button> <button class="sec" onclick="dlg.close()">Close</button></p></dialog>
<script>
const DATA=__DATA__;const KEY="phoenix-owner-answers-v1";let ans=JSON.parse(localStorage.getItem(KEY)||"{}");
const toc=document.getElementById("toc");toc.innerHTML="<p class=sub><b>Jump to:</b> "+DATA.map(c=>`<a style=\"color:#ffd54f\" href=\"#c-${c.cluster}\">${c.title}</a> (${c.questions.length})`).join(" &middot; ")+"</p>";const root=document.getElementById("root"),prog=document.getElementById("prog");const all=[];
DATA.forEach(c=>c.questions.forEach(q=>all.push(q)));
function save(){localStorage.setItem(KEY,JSON.stringify(ans));paint()}
function esc(s){return String(s).replace(/[&<>"]/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[m]))}
function render(){root.innerHTML="";DATA.forEach(c=>{const h=document.createElement("div");h.innerHTML=`<h2 id="c-${esc(c.cluster)}">${esc(c.title)}</h2><p class="sub">Cards ${esc(c.cluster)}</p>`;root.appendChild(h);
c.questions.forEach(q=>{const d=document.createElement("div");d.className="q";d.id="q-"+q.id;
let html=`<h3><span class="tag">${esc(q.id)}</span>${esc(q.title)}</h3><p class="ctx">${esc(q.context)}</p>`;
q.options.forEach(o=>{html+=`<label class="opt" data-q="${q.id}" data-k="${o.key}"><input type="radio" name="${q.id}" value="${o.key}"><b>${esc(o.label)}</b>${o.recommended?'<span class="rec" style="display:inline;margin-left:6px;color:#fff">suggested</span>':''}<span>${esc(o.detail)}</span></label>`});
html+=`<label class="opt" data-q="${q.id}" data-k="no"><input type="radio" name="${q.id}" value="no"><b>No (don't do this)</b></label>`;
html+=`<label class="opt" data-q="${q.id}" data-k="other"><input type="radio" name="${q.id}" value="other"><b>Other (write your own)</b></label><textarea class="note" data-q="${q.id}" placeholder="Your own answer, or a comment on the choice above..."></textarea>`;
d.innerHTML=html;root.appendChild(d)})});
root.querySelectorAll("input[type=radio]").forEach(r=>r.onchange=()=>{(ans[r.name]=ans[r.name]||{}).pick=r.value;save()});
root.querySelectorAll("textarea").forEach(t=>t.oninput=()=>{(ans[t.dataset.q]=ans[t.dataset.q]||{}).note=t.value;save()});paint(true)}
function paint(init){let n=0;all.forEach(q=>{const a=ans[q.id]||{};const el=document.getElementById("q-"+q.id);
const done=!!a.pick&&(a.pick!=="other"||(a.note||"").trim());if(done)n++;el.classList.toggle("done",done);
el.querySelectorAll("label.opt").forEach(l=>{const on=a.pick===l.dataset.k;l.classList.toggle("sel",on);l.querySelector("input").checked=on});
const t=el.querySelector("textarea");if(init||document.activeElement!==t)t.value=a.note||""});prog.textContent=n+" / "+all.length+" answered"}
document.getElementById("acc").onclick=()=>{all.forEach(q=>{const r=q.options.find(o=>o.recommended);const a=ans[q.id]=ans[q.id]||{};if(!a.pick&&r)a.pick=r.key});save()};
document.getElementById("clr").onclick=()=>{if(confirm("Reset all answers?")){ans={};save()}};
document.getElementById("exp").onclick=()=>{const L=["OWNER ANSWERS"];let miss=0;all.forEach(q=>{const a=ans[q.id]||{};let t;
if(!a.pick){t="NO ANSWER";miss++}else if(a.pick==="no")t="NO: do not do this"+(a.note&&a.note.trim()?" | note: "+a.note.trim():"");else if(a.pick==="other")t="OTHER: "+(a.note||"").trim();else{const o=q.options.find(o=>o.key===a.pick);t=a.pick+") "+o.label+(o.recommended?" [suggested]":"")+(a.note&&a.note.trim()?" | note: "+a.note.trim():"")}
L.push(q.id+" "+q.title+": "+t)});if(miss)L.push("("+miss+" unanswered: use the suggested answer)");
out.value=L.join("\n");dlg.showModal();out.select()};
document.getElementById("cp").onclick=()=>{out.select();navigator.clipboard&&navigator.clipboard.writeText(out.value)};
const dlg=document.getElementById("dlg"),out=document.getElementById("out");render();
</script></body></html>'''
open(os.path.join(here, "review.html"), "w").write(tpl.replace("__DATA__", json.dumps([c for c in clusters if c["cluster"] not in ANSWERED])))
for c in clusters:
    open(os.path.join(here, "review-%s.html" % c["cluster"]), "w").write(tpl.replace("__DATA__", json.dumps([c])).replace('KEY="phoenix-owner-answers-v1"', 'KEY="phoenix-owner-answers-v1"'))
print("wrote review.html with", sum(len(c["questions"]) for c in clusters if c["cluster"] not in ANSWERED), "questions")
