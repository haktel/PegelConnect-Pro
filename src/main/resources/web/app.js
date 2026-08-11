const stations = ["KOELN", "MAINZ", "BONN"];
const palette = { KOELN: "#35d7ff", MAINZ: "#4d7cff", BONN: "#40e0a1" };

function fmtTime(value) {
  if (!value) return "–";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("de-DE", {dateStyle:"short", timeStyle:"short"});
}

function trendSymbol(v) {
  return v === 1 ? "↑" : v === -1 ? "↓" : v === 0 ? "→" : "•";
}

function updateCard(station, reading) {
  if (!reading) return;
  document.getElementById(`value-${station}`).textContent = Number(reading.value).toFixed(0);
  document.getElementById(`trend-${station}`).textContent = trendSymbol(reading.trend);
  document.getElementById(`time-${station}`).textContent = fmtTime(reading.timestamp);
  const percent = Math.max(4, Math.min(100, Number(reading.value) / 6));
  document.getElementById(`water-${station}`).style.width = `${percent}%`;
}

function drawChart(history) {
  const canvas = document.getElementById("chart");
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.clientWidth;
  const cssH = canvas.clientHeight;
  canvas.width = cssW * dpr;
  canvas.height = cssH * dpr;
  const ctx = canvas.getContext("2d");
  ctx.scale(dpr, dpr);

  ctx.clearRect(0, 0, cssW, cssH);
  const pad = {l:46,r:16,t:18,b:34};
  const w = cssW-pad.l-pad.r, h = cssH-pad.t-pad.b;

  const all = stations.flatMap(s => history?.[s] || []);
  if (!all.length) {
    ctx.fillStyle="#8da5bd"; ctx.font="14px system-ui";
    ctx.fillText("Noch keine Verlaufsdaten", pad.l, pad.t+30);
    return;
  }

  const values = all.map(x => Number(x.value));
  let min = Math.min(...values), max = Math.max(...values);
  if (min === max) { min -= 10; max += 10; }
  const margin = (max-min)*0.12; min -= margin; max += margin;

  ctx.strokeStyle="#17344f"; ctx.lineWidth=1;
  ctx.fillStyle="#7890a8"; ctx.font="11px system-ui";
  for(let i=0;i<=4;i++){
    const y=pad.t+h*(i/4);
    ctx.beginPath();ctx.moveTo(pad.l,y);ctx.lineTo(pad.l+w,y);ctx.stroke();
    const label=(max-(max-min)*(i/4)).toFixed(0)+" cm";
    ctx.fillText(label,4,y+4);
  }

  stations.forEach(station=>{
    const arr=history?.[station]||[];
    if(!arr.length) return;
    ctx.strokeStyle=palette[station];ctx.lineWidth=2.5;ctx.beginPath();
    arr.forEach((p,i)=>{
      const x=pad.l+(arr.length===1?w/2:w*i/(arr.length-1));
      const y=pad.t+h-(Number(p.value)-min)/(max-min)*h;
      if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
    });
    ctx.stroke();
  });
}

async function refresh() {
  const error = document.getElementById("errorBox");
  try {
    const res = await fetch("/api/state", {cache:"no-store"});
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    stations.forEach(s => updateCard(s, data.stations?.[s]));
    drawChart(data.history || {});

    document.getElementById("lastUpdate").textContent = fmtTime(data.lastUpdate);
    document.getElementById("mqttState").textContent = `MQTT: ${data.mqttConnected ? "verbunden" : "getrennt"}`;
    document.getElementById("brokerStatus").textContent = data.mqttConnected ? "VERBUNDEN" : "GETRENNT";
    document.getElementById("systemText").textContent = data.mqttConnected ? "System online" : "Backend online · MQTT getrennt";
    document.getElementById("liveDot").classList.toggle("online", Boolean(data.mqttConnected));

    if (data.lastError) {
      error.hidden=false; error.textContent=data.lastError;
    } else {
      error.hidden=true;
    }
  } catch (e) {
    document.getElementById("systemText").textContent="Verbindung zum Backend unterbrochen";
    document.getElementById("liveDot").classList.remove("online");
    error.hidden=false; error.textContent=`Dashboard-Fehler: ${e.message}`;
  }
}

window.addEventListener("resize", () => refresh());
refresh();
setInterval(refresh, 5000);
