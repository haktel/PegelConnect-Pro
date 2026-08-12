const stations = ["KÖLN", "BONN", "MAINZ"];

const domIds = {
    "KÖLN": "KOELN",
    "BONN": "BONN",
    "MAINZ": "MAINZ"
};

const palette = {
    "KÖLN": "#33d8ff",
    "BONN": "#43e6aa",
    "MAINZ": "#4e7cff"
};

const locations = {
    "KÖLN": {
        lat: 50.9375,
        lon: 6.9603,
        label: "Köln"
    },
    "BONN": {
        lat: 50.7374,
        lon: 7.0982,
        label: "Bonn"
    },
    "MAINZ": {
        lat: 49.9929,
        lon: 8.2473,
        label: "Mainz"
    }
};

let map;
let markers = {};

let latestState = null;
let weatherCache = {};

let historyCache = {};
let selectedHistoryPeriod = "24h";


// ============================================================
// FORMATIERUNG
// ============================================================

function fmtTime(value) {
    if (!value) {
        return "–";
    }

    const date = new Date(value);

    return Number.isNaN(date.getTime())
        ? value
        : date.toLocaleString(
            "de-DE",
            {
                dateStyle: "short",
                timeStyle: "short"
            }
        );
}


function trendSymbol(value) {
    if (value > 0) {
        return "↑";
    }

    if (value < 0) {
        return "↓";
    }

    return "→";
}


function weatherDescription(code) {
    const descriptions = {
        0: "Klar",
        1: "Überwiegend klar",
        2: "Teilweise bewölkt",
        3: "Bewölkt",
        45: "Nebel",
        48: "Reifnebel",
        51: "Leichter Nieselregen",
        53: "Nieselregen",
        55: "Starker Nieselregen",
        61: "Leichter Regen",
        63: "Regen",
        65: "Starker Regen",
        71: "Leichter Schneefall",
        73: "Schneefall",
        75: "Starker Schneefall",
        80: "Regenschauer",
        81: "Regenschauer",
        82: "Starke Regenschauer",
        95: "Gewitter",
        96: "Gewitter mit Hagel",
        99: "Starkes Gewitter"
    };

    return descriptions[code]
        ?? "Wetterlage unbekannt";
}


// ============================================================
// HISTORY ANALYSE
// ============================================================

function calculateHistoryStats(values) {
    if (!values || values.length === 0) {
        return null;
    }

    const clean = values
        .map(item => ({
            value: Number(item.value),
            timestamp: item.timestamp
        }))
        .filter(item => Number.isFinite(item.value));

    if (!clean.length) {
        return null;
    }

    const first = clean[0];
    const last = clean[clean.length - 1];

    const numbers =
        clean.map(item => item.value);

    const min =
        Math.min(...numbers);

    const max =
        Math.max(...numbers);

    const avg =
        numbers.reduce(
            (sum, value) => sum + value,
            0
        ) / numbers.length;

    const delta =
        last.value - first.value;

    let trend = 0;

    if (delta > 0.5) {
        trend = 1;
    }

    if (delta < -0.5) {
        trend = -1;
    }

    return {
        first: first.value,
        current: last.value,
        min,
        max,
        avg,
        delta,
        trend,
        count: clean.length,
        timestamp: last.timestamp
    };
}


function monitoringStatus(stats) {
    if (!stats) {
        return {
            label: "KEINE DATEN",
            level: "unknown"
        };
    }

    const absDelta =
        Math.abs(stats.delta);

    /*
     * Technischer Monitoring-Status.
     * Noch KEINE amtliche Hochwasserwarnstufe.
     */

    if (absDelta >= 30) {
        return {
            label: "STARKE ÄNDERUNG",
            level: "critical"
        };
    }

    if (absDelta >= 15) {
        return {
            label: "BEOBACHTEN",
            level: "warning"
        };
    }

    if (absDelta >= 5) {
        return {
            label: "BEWEGUNG",
            level: "info"
        };
    }

    return {
        label: "STABIL",
        level: "normal"
    };
}


function periodLabel() {
    if (selectedHistoryPeriod === "7d") {
        return "7 Tage";
    }

    if (selectedHistoryPeriod === "30d") {
        return "30 Tage";
    }

    return "24 Stunden";
}


// ============================================================
// STATION CARDS
// ============================================================

function updateStationCard(station, reading) {
    if (!reading) {
        return;
    }

    const domId =
        domIds[station];

    const valueEl =
        document.getElementById(
            `value-${domId}`
        );

    const trendEl =
        document.getElementById(
            `trend-${domId}`
        );

    const timeEl =
        document.getElementById(
            `time-${domId}`
        );

    const waterEl =
        document.getElementById(
            `water-${domId}`
        );

    const statusEl =
        document.getElementById(
            `status-${domId}`
        );

    if (valueEl) {
        valueEl.textContent =
            Number(reading.value)
                .toFixed(0);
    }

    if (timeEl) {
        timeEl.textContent =
            fmtTime(reading.timestamp);
    }

    if (waterEl) {
        const percent =
            Math.max(
                4,
                Math.min(
                    100,
                    Number(reading.value) / 6
                )
            );

        waterEl.style.width =
            `${percent}%`;
    }

    if (trendEl) {
        trendEl.textContent = "→";
        trendEl.title =
            "Trend wird aus PEGELONLINE-Historie berechnet";
    }

    if (statusEl) {
        statusEl.textContent =
            "LIVE";
    }
}


function updateHistoryIndicators() {
    stations.forEach(station => {
        const values =
            historyCache[station] || [];

        const stats =
            calculateHistoryStats(values);

        if (!stats) {
            return;
        }

        const domId =
            domIds[station];

        const trendEl =
            document.getElementById(
                `trend-${domId}`
            );

        const statusEl =
            document.getElementById(
                `status-${domId}`
            );

        const status =
            monitoringStatus(stats);

        if (trendEl) {
            const sign =
                stats.delta > 0
                    ? "+"
                    : "";

            trendEl.textContent =
                trendSymbol(stats.trend);

            trendEl.title =
                `${sign}${stats.delta.toFixed(0)} cm / ${periodLabel()}`;
        }

        if (statusEl) {
            const sign =
                stats.delta > 0
                    ? "+"
                    : "";

            statusEl.textContent =
                `${status.label} · ${sign}${stats.delta.toFixed(0)} cm`;

            statusEl.dataset.level =
                status.level;

            statusEl.title =
                `Min ${stats.min.toFixed(0)} cm · Max ${stats.max.toFixed(0)} cm · Ø ${stats.avg.toFixed(1)} cm · ${stats.count} Messwerte`;
        }
    });
}


// ============================================================
// WEATHER
// ============================================================

function updateWeatherCard(station, weather) {
    if (!weather) {
        return;
    }

    const domId =
        domIds[station];

    const temp =
        document.getElementById(
            `weather-temp-${domId}`
        );

    const feels =
        document.getElementById(
            `weather-feels-${domId}`
        );

    const wind =
        document.getElementById(
            `weather-wind-${domId}`
        );

    const rain =
        document.getElementById(
            `weather-rain-${domId}`
        );

    if (temp) {
        temp.textContent =
            `${Number(weather.temperature).toFixed(1)} °C`;
    }

    if (feels) {
        feels.textContent =
            `${Number(weather.apparentTemperature).toFixed(1)} °C`;
    }

    if (wind) {
        wind.textContent =
            `${Number(weather.windSpeed).toFixed(1)} km/h`;
    }

    if (rain) {
        rain.textContent =
            `${Number(weather.precipitation).toFixed(1)} mm`;
    }
}


// ============================================================
// CHART
// ============================================================

function drawChart(history) {
    const canvas =
        document.getElementById("chart");

    if (!canvas) {
        return;
    }

    const dpr =
        window.devicePixelRatio || 1;

    const cssW =
        canvas.clientWidth;

    const cssH =
        canvas.clientHeight;

    canvas.width =
        cssW * dpr;

    canvas.height =
        cssH * dpr;

    const ctx =
        canvas.getContext("2d");

    ctx.scale(dpr, dpr);

    ctx.clearRect(
        0,
        0,
        cssW,
        cssH
    );

    const pad = {
        l: 52,
        r: 20,
        t: 22,
        b: 42
    };

    const width =
        cssW - pad.l - pad.r;

    const height =
        cssH - pad.t - pad.b;

    const all =
        stations.flatMap(
            station =>
                history?.[station] || []
        );

    if (!all.length) {
        ctx.fillStyle =
            "#7f9ab2";

        ctx.font =
            "14px system-ui";

        ctx.fillText(
            "Noch keine Verlaufsdaten",
            pad.l,
            pad.t + 30
        );

        return;
    }

    const numericValues =
        all
            .map(
                item =>
                    Number(item.value)
            )
            .filter(
                Number.isFinite
            );

    if (!numericValues.length) {
        return;
    }

    let min =
        Math.min(...numericValues);

    let max =
        Math.max(...numericValues);

    if (min === max) {
        min -= 10;
        max += 10;
    }

    const margin =
        Math.max(
            2,
            (max - min) * 0.15
        );

    min -= margin;
    max += margin;

    ctx.strokeStyle =
        "rgba(120,170,210,0.18)";

    ctx.fillStyle =
        "#6f8da6";

    ctx.font =
        "11px system-ui";

    for (
        let i = 0;
        i <= 4;
        i++
    ) {
        const y =
            pad.t
            + height * (i / 4);

        ctx.beginPath();

        ctx.moveTo(
            pad.l,
            y
        );

        ctx.lineTo(
            pad.l + width,
            y
        );

        ctx.stroke();

        const label =
            (
                max
                - (max - min)
                * (i / 4)
            ).toFixed(0)
            + " cm";

        ctx.fillText(
            label,
            5,
            y + 4
        );
    }

    stations.forEach(station => {
        const values =
            history?.[station] || [];

        if (!values.length) {
            return;
        }

        ctx.strokeStyle =
            palette[station];

        ctx.lineWidth =
            2.5;

        ctx.lineJoin =
            "round";

        ctx.lineCap =
            "round";

        ctx.beginPath();

        values.forEach(
            (reading, index) => {
                const x =
                    pad.l
                    + (
                        values.length === 1
                            ? width / 2
                            : width
                            * index
                            / (
                                values.length - 1
                            )
                    );

                const value =
                    Number(reading.value);

                const y =
                    pad.t
                    + height
                    - (
                        (
                            value - min
                        )
                        / (
                            max - min
                        )
                    )
                    * height;

                if (index === 0) {
                    ctx.moveTo(
                        x,
                        y
                    );
                } else {
                    ctx.lineTo(
                        x,
                        y
                    );
                }
            }
        );

        ctx.stroke();
    });

    ctx.fillStyle =
        "#6f8da6";

    ctx.font =
        "11px system-ui";

    ctx.fillText(
        `Zeitraum: ${periodLabel()}`,
        pad.l,
        cssH - 10
    );
}


// ============================================================
// MAP
// ============================================================

function initMap() {
    if (!window.L) {
        return;
    }

    map =
        L.map(
            "stationMap",
            {
                zoomControl: true
            }
        ).setView(
            [50.4, 7.3],
            7
        );

    L.tileLayer(
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        {
            maxZoom: 19,
            attribution:
                "&copy; OpenStreetMap contributors"
        }
    ).addTo(map);

    stations.forEach(station => {
        const location =
            locations[station];

        const marker =
            L.marker([
                location.lat,
                location.lon
            ]).addTo(map);

        markers[station] =
            marker;
    });
}


function updateMapPopups(state) {
    stations.forEach(station => {
        const marker =
            markers[station];

        if (!marker) {
            return;
        }

        const reading =
            state?.stations?.[station];

        const weather =
            weatherCache[station];

        const location =
            locations[station];

        const stats =
            calculateHistoryStats(
                historyCache[station]
                || []
            );

        const status =
            monitoringStatus(stats);

        const waterText =
            reading
                ? `${Number(reading.value).toFixed(0)} cm`
                : "Keine Daten";

        const weatherText =
            weather
                ? `${Number(weather.temperature).toFixed(1)} °C`
                : "Keine Wetterdaten";

        let trendText =
            "Noch keine Historie";

        if (stats) {
            const sign =
                stats.delta > 0
                    ? "+"
                    : "";

            trendText =
                `${trendSymbol(stats.trend)} ${sign}${stats.delta.toFixed(0)} cm / ${periodLabel()}`;
        }

        marker.setPopupContent(`
            <strong>${location.label}</strong>
            <br><br>

            Wasserstand:
            <strong>${waterText}</strong>
            <br>

            Trend:
            <strong>${trendText}</strong>
            <br>

            Monitoring:
            <strong>${status.label}</strong>
            <br>

            Temperatur:
            <strong>${weatherText}</strong>
            <br>

            ${
                weather
                    ? weatherDescription(
                        weather.weatherCode
                    )
                    : ""
            }
        `);
    });
}


// ============================================================
// ALERTS
// ============================================================

function generateAlerts(state) {
    const container =
        document.getElementById(
            "alertsList"
        );

    const counter =
        document.getElementById(
            "alertCounter"
        );

    if (!container) {
        return;
    }

    const alerts = [];

    if (state.mqttConnected) {
        alerts.push({
            type: "success",
            title:
                "MQTT Broker verbunden",
            message:
                "Mosquitto verarbeitet die Live-Daten."
        });
    } else {
        alerts.push({
            type: "error",
            title:
                "MQTT Verbindung unterbrochen",
            message:
                "Keine Verbindung zum Broker."
        });
    }

    stations.forEach(station => {
        const reading =
            state.stations?.[station];

        const stats =
            calculateHistoryStats(
                historyCache[station] || []
            );

        if (!reading) {
            alerts.push({
                type: "warning",
                title:
                    `${locations[station].label}: keine Pegeldaten`,
                message:
                    "Für diese Station liegen aktuell keine Messdaten vor."
            });

            return;
        }

        if (stats) {
            const sign =
                stats.delta > 0
                    ? "+"
                    : "";

            if (stats.delta >= 5) {
                alerts.push({
                    type: "warning",
                    title:
                        `${locations[station].label}: Pegel steigt`,
                    message:
                        `${sign}${stats.delta.toFixed(0)} cm innerhalb von ${periodLabel()}.`
                });
            }

            if (stats.delta <= -5) {
                alerts.push({
                    type: "info",
                    title:
                        `${locations[station].label}: Pegel fällt`,
                    message:
                        `${stats.delta.toFixed(0)} cm innerhalb von ${periodLabel()}.`
                });
            }
        }

        const weather =
            weatherCache[station];

        if (
            weather
            && Number(
                weather.precipitation
            ) > 0
        ) {
            alerts.push({
                type: "info",
                title:
                    `${locations[station].label}: Niederschlag`,
                message:
                    `${Number(weather.precipitation).toFixed(1)} mm aktuelle Niederschlagsmenge.`
            });
        }

        if (
            weather
            && Number(
                weather.windSpeed
            ) >= 50
        ) {
            alerts.push({
                type: "warning",
                title:
                    `${locations[station].label}: starker Wind`,
                message:
                    `${Number(weather.windSpeed).toFixed(1)} km/h.`
            });
        }
    });

    if (state.lastError) {
        alerts.push({
            type: "error",
            title:
                "Backend Meldung",
            message:
                state.lastError
        });
    }

    if (!alerts.length) {
        alerts.push({
            type: "success",
            title:
                "Keine Meldungen",
            message:
                "Alle Systeme arbeiten normal."
        });
    }

    container.innerHTML =
        alerts
            .slice(0, 8)
            .map(alert => `
                <div class="alert-item ${alert.type}">
                    <span class="alert-dot"></span>

                    <div>
                        <strong>
                            ${alert.title}
                        </strong>

                        <p>
                            ${alert.message}
                        </p>
                    </div>
                </div>
            `)
            .join("");

    if (counter) {
        counter.textContent =
            String(alerts.length);
    }
}


// ============================================================
// STATION FILTER
// ============================================================

function filterStations(value) {
    document
        .querySelectorAll(
            ".station-card"
        )
        .forEach(card => {
            if (
                value === "ALL"
                || card.dataset.station === value
            ) {
                card.style.display = "";
            } else {
                card.style.display = "none";
            }
        });

    if (
        map
        && value !== "ALL"
        && markers[value]
    ) {
        const location =
            locations[value];

        map.setView(
            [
                location.lat,
                location.lon
            ],
            11
        );

        markers[value]
            .openPopup();
    }

    if (
        map
        && value === "ALL"
    ) {
        map.setView(
            [50.4, 7.3],
            7
        );
    }
}


// ============================================================
// WEATHER API
// ============================================================

async function fetchWeather(station) {
    const response =
        await fetch(
            `/api/weather?station=${encodeURIComponent(station)}`,
            {
                cache: "no-store"
            }
        );

    if (!response.ok) {
        throw new Error(
            `Weather HTTP ${response.status}`
        );
    }

    return response.json();
}


async function refreshWeather() {
    let successCount = 0;

    for (
        const station
        of stations
    ) {
        try {
            const weather =
                await fetchWeather(
                    station
                );

            weatherCache[station] =
                weather;

            updateWeatherCard(
                station,
                weather
            );

            successCount++;

        } catch (error) {
            console.error(
                "Weather Fehler",
                station,
                error
            );
        }
    }

    const weatherStatus =
        document.getElementById(
            "weatherApiStatus"
        );

    if (weatherStatus) {
        weatherStatus.textContent =
            successCount === stations.length
                ? "CONNECTED"
                : "DEGRADED";
    }

    if (latestState) {
        updateMapPopups(
            latestState
        );

        generateAlerts(
            latestState
        );
    }
}


// ============================================================
// HISTORY API
// ============================================================

async function fetchHistory(
    station,
    period
) {
    const response =
        await fetch(
            `/api/history?station=${encodeURIComponent(station)}&period=${encodeURIComponent(period)}`,
            {
                cache: "no-store"
            }
        );

    if (!response.ok) {
        throw new Error(
            `History HTTP ${response.status} für ${station}`
        );
    }

    const data =
        await response.json();

    return data.measurements
        || [];
}


async function refreshHistory(
    period = selectedHistoryPeriod
) {
    selectedHistoryPeriod =
        period;

    const newHistory = {};

    try {
        await Promise.all(
            stations.map(
                async station => {
                    newHistory[station] =
                        await fetchHistory(
                            station,
                            period
                        );
                }
            )
        );

        historyCache =
            newHistory;

        drawChart(
            historyCache
        );

        updateHistoryIndicators();

        if (latestState) {
            updateMapPopups(
                latestState
            );

            generateAlerts(
                latestState
            );
        }

    } catch (error) {
        console.error(
            "History Fehler:",
            error
        );

        const errorBox =
            document.getElementById(
                "errorBox"
            );

        if (errorBox) {
            errorBox.hidden =
                false;

            errorBox.textContent =
                `Pegel-Historie konnte nicht geladen werden: ${error.message}`;
        }
    }
}


// ============================================================
// LIVE STATE
// ============================================================

async function refreshState() {
    const errorBox =
        document.getElementById(
            "errorBox"
        );

    try {
        const response =
            await fetch(
                "/api/state",
                {
                    cache: "no-store"
                }
            );

        if (!response.ok) {
            throw new Error(
                `HTTP ${response.status}`
            );
        }

        const data =
            await response.json();

        latestState =
            data;

        stations.forEach(
            station => {
                updateStationCard(
                    station,
                    data.stations?.[station]
                );
            }
        );

        const lastUpdate =
            document.getElementById(
                "lastUpdate"
            );

        const mqttState =
            document.getElementById(
                "mqttState"
            );

        const brokerStatus =
            document.getElementById(
                "brokerStatus"
            );

        const systemText =
            document.getElementById(
                "systemText"
            );

        const liveDot =
            document.getElementById(
                "liveDot"
            );

        if (lastUpdate) {
            lastUpdate.textContent =
                fmtTime(
                    data.lastUpdate
                );
        }

        if (mqttState) {
            mqttState.textContent =
                data.mqttConnected
                    ? "MQTT: verbunden"
                    : "MQTT: getrennt";
        }

        if (brokerStatus) {
            brokerStatus.textContent =
                data.mqttConnected
                    ? "CONNECTED"
                    : "OFFLINE";
        }

        if (systemText) {
            systemText.textContent =
                data.mqttConnected
                    ? "Alle Systeme online"
                    : "Backend online · MQTT getrennt";
        }

        if (liveDot) {
            liveDot.classList.toggle(
                "online",
                Boolean(
                    data.mqttConnected
                )
            );
        }

        if (data.lastError) {
            if (errorBox) {
                errorBox.hidden = false;
                errorBox.textContent =
                    data.lastError;
            }
        } else {
            if (errorBox) {
                errorBox.hidden = true;
            }
        }

        updateHistoryIndicators();

        updateMapPopups(
            data
        );

        generateAlerts(
            data
        );

    } catch (error) {
        const systemText =
            document.getElementById(
                "systemText"
            );

        const liveDot =
            document.getElementById(
                "liveDot"
            );

        if (systemText) {
            systemText.textContent =
                "Backend nicht erreichbar";
        }

        if (liveDot) {
            liveDot.classList.remove(
                "online"
            );
        }

        if (errorBox) {
            errorBox.hidden = false;

            errorBox.textContent =
                `Dashboard-Fehler: ${error.message}`;
        }
    }
}


// ============================================================
// INITIALISIERUNG
// ============================================================

document.addEventListener(
    "DOMContentLoaded",
    () => {
        initMap();

        const stationSelect =
            document.getElementById(
                "stationSelect"
            );

        if (stationSelect) {
            stationSelect.addEventListener(
                "change",
                event =>
                    filterStations(
                        event.target.value
                    )
            );
        }

        document
            .querySelectorAll(
                ".chart-range"
            )
            .forEach(button => {
                button.addEventListener(
                    "click",
                    () => {
                        document
                            .querySelectorAll(
                                ".chart-range"
                            )
                            .forEach(
                                item =>
                                    item.classList.remove(
                                        "active"
                                    )
                            );

                        button.classList.add(
                            "active"
                        );

                        const text =
                            button
                                .textContent
                                .trim()
                                .toUpperCase();

                        let period =
                            "24h";

                        if (
                            text === "7T"
                            || text === "7D"
                        ) {
                            period =
                                "7d";
                        }

                        if (
                            text === "30T"
                            || text === "30D"
                        ) {
                            period =
                                "30d";
                        }

                        if (
                            button.dataset.period
                        ) {
                            period =
                                button.dataset.period;
                        }

                        refreshHistory(
                            period
                        );
                    }
                );
            });

        refreshState();

        refreshWeather();

        refreshHistory(
            "24h"
        );

        setInterval(
            refreshState,
            5000
        );

        setInterval(
            refreshWeather,
            300000
        );

        setInterval(
            () =>
                refreshHistory(
                    selectedHistoryPeriod
                ),
            300000
        );
    }
);


// ============================================================
// RESPONSIVE
// ============================================================

window.addEventListener(
    "resize",
    () => {
        if (
            Object.keys(
                historyCache
            ).length
        ) {
            drawChart(
                historyCache
            );
        }

        if (map) {
            map.invalidateSize();
        }
    }
);