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
let selectedReportPeriod = "24h";

const eventLog = [];
const MAX_EVENTS = 100;

const previousValues = {};
let previousMqttState = null;


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


function fmtEventTime(value = new Date()) {
    return value.toLocaleTimeString(
        "de-DE",
        {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        }
    );
}


function periodLabel(period = selectedHistoryPeriod) {
    if (period === "7d") {
        return "7 Tage";
    }

    if (period === "30d") {
        return "30 Tage";
    }

    return "24 Stunden";
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


function signed(value, decimals = 0) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return "–";
    }

    return `${number > 0 ? "+" : ""}${number.toFixed(decimals)}`;
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
    if (!values || !values.length) {
        return null;
    }

    const clean = values
        .map(item => ({
            timestamp: item.timestamp,
            value: Number(item.value)
        }))
        .filter(item => Number.isFinite(item.value));

    if (!clean.length) {
        return null;
    }

    const numbers =
        clean.map(item => item.value);

    const first =
        clean[0];

    const last =
        clean[clean.length - 1];

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
        firstTimestamp: first.timestamp,
        lastTimestamp: last.timestamp
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
     * KEINE amtliche Hochwasserwarnstufe.
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


// ============================================================
// EVENT LOG
// ============================================================

function addEvent(
    source,
    message,
    type = "info"
) {
    const last =
        eventLog[0];

    /*
     * Identische Meldungen nicht
     * sekündlich mehrfach eintragen.
     */
    if (
        last
        && last.source === source
        && last.message === message
    ) {
        return;
    }

    eventLog.unshift({
        timestamp: new Date().toISOString(),
        displayTime: fmtEventTime(),
        source,
        message,
        type
    });

    if (eventLog.length > MAX_EVENTS) {
        eventLog.length =
            MAX_EVENTS;
    }

    renderEventLog();
}


function renderEventLog() {
    const container =
        document.getElementById(
            "eventLog"
        );

    const counter =
        document.getElementById(
            "eventCounter"
        );

    if (!container) {
        return;
    }

    if (!eventLog.length) {
        container.innerHTML = `
            <div class="event-row">
                <time>–</time>
                <span class="event-source">
                    SYSTEM
                </span>
                <span>
                    Noch keine Ereignisse.
                </span>
            </div>
        `;

        return;
    }

    container.innerHTML =
        eventLog
            .slice(0, 50)
            .map(event => `
                <div
                    class="event-row"
                    data-type="${event.type}"
                >
                    <time datetime="${event.timestamp}">
                        ${event.displayTime}
                    </time>

                    <span class="event-source">
                        ${event.source}
                    </span>

                    <span>
                        ${event.message}
                    </span>
                </div>
            `)
            .join("");

    if (counter) {
        counter.textContent =
            `${eventLog.length} EVENTS`;
    }
}


// ============================================================
// STATION CARDS
// ============================================================

function updateStationCard(
    station,
    reading
) {
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

    const currentValue =
        Number(reading.value);

    if (valueEl) {
        valueEl.textContent =
            currentValue.toFixed(0);
    }

    if (timeEl) {
        timeEl.textContent =
            fmtTime(
                reading.timestamp
            );
    }

    if (waterEl) {
        const maxGaugeValue =
            600;

        const clampedValue =
            Math.max(
                0,
                Math.min(
                    maxGaugeValue,
                    currentValue
                )
            );

        const angle =
            -90
            + (
                clampedValue
                / maxGaugeValue
            )
            * 180;

        waterEl.style.setProperty(
            "--gauge-angle",
            `${angle}deg`
        );
    }

    if (trendEl) {
        trendEl.textContent =
            "→";

        trendEl.title =
            "Trend wird aus PEGELONLINE-Historie berechnet";
    }

    if (statusEl) {
        statusEl.textContent =
            "LIVE";
    }


    /*
     * Pegeländerung als Event erkennen.
     */

    if (
        Object.prototype.hasOwnProperty.call(
            previousValues,
            station
        )
    ) {
        const previous =
            previousValues[station];

        if (previous !== currentValue) {
            const delta =
                currentValue - previous;

            addEvent(
                "PEGEL",
                `${locations[station].label}: ${previous.toFixed(0)} cm → ${currentValue.toFixed(0)} cm (${signed(delta)} cm)`,
                delta > 0
                    ? "warning"
                    : "info"
            );
        }
    }

    previousValues[station] =
        currentValue;
}


function updateHistoryIndicators() {
    stations.forEach(station => {
        const stats =
            calculateHistoryStats(
                historyCache[station]
                || []
            );

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
            trendEl.textContent =
                trendSymbol(
                    stats.trend
                );

            trendEl.title =
                `${signed(stats.delta)} cm / ${periodLabel()}`;
        }

        if (statusEl) {
            statusEl.textContent =
                `${status.label} · ${signed(stats.delta)} cm`;

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

function updateWeatherCard(
    station,
    weather
) {
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
// REPORTS
// ============================================================

function updateReports() {
    stations.forEach(station => {
        const domId =
            domIds[station];

        const stats =
            calculateHistoryStats(
                historyCache[station]
                || []
            );

        const elements = {
            min:
                document.getElementById(
                    `report-min-${domId}`
                ),

            max:
                document.getElementById(
                    `report-max-${domId}`
                ),

            avg:
                document.getElementById(
                    `report-avg-${domId}`
                ),

            delta:
                document.getElementById(
                    `report-delta-${domId}`
                ),

            count:
                document.getElementById(
                    `report-count-${domId}`
                ),

            status:
                document.getElementById(
                    `report-status-${domId}`
                ),

            trend:
                document.getElementById(
                    `report-trend-${domId}`
                )
        };

        if (!stats) {
            Object
                .values(elements)
                .forEach(element => {
                    if (element) {
                        element.textContent =
                            "–";
                    }
                });

            return;
        }

        const status =
            monitoringStatus(stats);

        if (elements.min) {
            elements.min.textContent =
                `${stats.min.toFixed(0)} cm`;
        }

        if (elements.max) {
            elements.max.textContent =
                `${stats.max.toFixed(0)} cm`;
        }

        if (elements.avg) {
            elements.avg.textContent =
                `${stats.avg.toFixed(1)} cm`;
        }

        if (elements.delta) {
            elements.delta.textContent =
                `${signed(stats.delta)} cm`;
        }

        if (elements.count) {
            elements.count.textContent =
                String(stats.count);
        }

        if (elements.status) {
            elements.status.textContent =
                status.label;

            elements.status.dataset.level =
                status.level;
        }

        if (elements.trend) {
            elements.trend.textContent =
                trendSymbol(stats.trend);

            elements.trend.title =
                `${signed(stats.delta)} cm / ${periodLabel(selectedReportPeriod)}`;
        }
    });
}


// ============================================================
// CSV / JSON EXPORT
// ============================================================

function createExportPayload() {
    const reports = {};

    stations.forEach(station => {
        const values =
            historyCache[station]
            || [];

        reports[station] = {
            station,
            label:
                locations[station].label,
            period:
                selectedReportPeriod,
            statistics:
                calculateHistoryStats(
                    values
                ),
            weather:
                weatherCache[station]
                || null,
            measurements:
                values
        };
    });

    return {
        generatedAt:
            new Date().toISOString(),

        application:
            "PegelConnect Pro",

        period:
            selectedReportPeriod,

        reports,

        events:
            eventLog
    };
}


function downloadTextFile(
    filename,
    content,
    mimeType
) {
    const blob =
        new Blob(
            [content],
            {
                type: mimeType
            }
        );

    const url =
        URL.createObjectURL(
            blob
        );

    const link =
        document.createElement(
            "a"
        );

    link.href =
        url;

    link.download =
        filename;

    document.body.appendChild(
        link
    );

    link.click();

    link.remove();

    URL.revokeObjectURL(
        url
    );
}


function exportJson() {
    const payload =
        createExportPayload();

    const filename =
        `pegelconnect-report-${selectedReportPeriod}-${new Date().toISOString().slice(0, 10)}.json`;

    downloadTextFile(
        filename,
        JSON.stringify(
            payload,
            null,
            2
        ),
        "application/json;charset=utf-8"
    );

    addEvent(
        "REPORT",
        `JSON-Report ${selectedReportPeriod} exportiert.`,
        "success"
    );
}


function exportCsv() {
    const rows = [
        [
            "Station",
            "Timestamp",
            "Value_cm"
        ]
    ];

    stations.forEach(station => {
        const values =
            historyCache[station]
            || [];

        values.forEach(item => {
            rows.push([
                locations[station].label,
                item.timestamp,
                Number(item.value)
                    .toFixed(1)
            ]);
        });
    });

    const csv =
        rows
            .map(row =>
                row
                    .map(value =>
                        `"${String(value).replaceAll("\"", "\"\"")}"`
                    )
                    .join(";")
            )
            .join("\r\n");

    const filename =
        `pegelconnect-history-${selectedReportPeriod}-${new Date().toISOString().slice(0, 10)}.csv`;

    downloadTextFile(
        filename,
        "\uFEFF" + csv,
        "text/csv;charset=utf-8"
    );

    addEvent(
        "REPORT",
        `CSV-Export ${selectedReportPeriod} erstellt.`,
        "success"
    );
}


// ============================================================
// CHART
// ============================================================

function drawChart(history) {
    const canvas =
        document.getElementById(
            "chart"
        );

    if (!canvas) {
        return;
    }

    const dpr =
        window.devicePixelRatio
        || 1;

    const cssW =
        canvas.clientWidth;

    const cssH =
        canvas.clientHeight;

    canvas.width =
        cssW * dpr;

    canvas.height =
        cssH * dpr;

    const ctx =
        canvas.getContext(
            "2d"
        );

    ctx.scale(
        dpr,
        dpr
    );

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
        cssW
        - pad.l
        - pad.r;

    const height =
        cssH
        - pad.t
        - pad.b;

    const all =
        stations.flatMap(
            station =>
                history?.[station]
                || []
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
        Math.min(
            ...numericValues
        );

    let max =
        Math.max(
            ...numericValues
        );

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


    /*
     * Raster + Y-Achse
     */

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
            + height
            * (i / 4);

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
                - (
                    max - min
                )
                * (
                    i / 4
                )
            ).toFixed(0)
            + " cm";

        ctx.fillText(
            label,
            5,
            y + 4
        );
    }


    /*
     * Drei Stationslinien
     */

    stations.forEach(station => {
        const values =
            history?.[station]
            || [];

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
            (
                reading,
                index
            ) => {
                const x =
                    pad.l
                    + (
                        values.length === 1
                            ? width / 2
                            : width
                            * index
                            / (
                                values.length
                                - 1
                            )
                    );

                const value =
                    Number(
                        reading.value
                    );

                const y =
                    pad.t
                    + height
                    - (
                        (
                            value
                            - min
                        )
                        / (
                            max
                            - min
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
        `Zeitraum: ${periodLabel(selectedHistoryPeriod)}`,
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

    addEvent(
        "MAP",
        "OpenStreetMap initialisiert.",
        "success"
    );
}


function updateMapPopups(state) {
    stations.forEach(station => {
        const marker =
            markers[station];

        if (!marker) {
            return;
        }

        const reading =
            state?.stations?.[
                station
            ];

        const weather =
            weatherCache[
                station
            ];

        const location =
            locations[
                station
            ];

        const stats =
            calculateHistoryStats(
                historyCache[station]
                || []
            );

        const status =
            monitoringStatus(
                stats
            );

        const waterText =
            reading
                ? `${Number(reading.value).toFixed(0)} cm`
                : "Keine Daten";

        const weatherText =
            weather
                ? `${Number(weather.temperature).toFixed(1)} °C`
                : "Keine Wetterdaten";

        const trendText =
            stats
                ? `${trendSymbol(stats.trend)} ${signed(stats.delta)} cm / ${periodLabel()}`
                : "Keine Historie";

        marker.setPopupContent(`
            <strong>
                ${location.label}
            </strong>

            <br><br>

            Wasserstand:
            <strong>
                ${waterText}
            </strong>

            <br>

            Trend:
            <strong>
                ${trendText}
            </strong>

            <br>

            Monitoring:
            <strong>
                ${status.label}
            </strong>

            <br>

            Temperatur:
            <strong>
                ${weatherText}
            </strong>

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
// LIVE MELDUNGEN
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
            state.stations?.[
                station
            ];

        const stats =
            calculateHistoryStats(
                historyCache[station]
                || []
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
            if (stats.delta >= 5) {
                alerts.push({
                    type: "warning",
                    title:
                        `${locations[station].label}: Pegel steigt`,
                    message:
                        `${signed(stats.delta)} cm innerhalb von ${periodLabel()}.`
                });
            }

            if (stats.delta <= -5) {
                alerts.push({
                    type: "info",
                    title:
                        `${locations[station].label}: Pegel fällt`,
                    message:
                        `${signed(stats.delta)} cm innerhalb von ${periodLabel()}.`
                });
            }
        }

        const weather =
            weatherCache[
                station
            ];

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
            String(
                alerts.length
            );
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
                || card.dataset.station
                === value
            ) {
                card.style.display =
                    "";
            } else {
                card.style.display =
                    "none";
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
    let successCount =
        0;

    for (
        const station
        of stations
    ) {
        try {
            const weather =
                await fetchWeather(
                    station
                );

            weatherCache[
                station
            ] = weather;

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

            addEvent(
                "WEATHER",
                `${locations[station].label}: ${error.message}`,
                "error"
            );
        }
    }

    const weatherStatus =
        document.getElementById(
            "weatherApiStatus"
        );

    if (weatherStatus) {
        weatherStatus.textContent =
            successCount
            === stations.length
                ? "CONNECTED"
                : "DEGRADED";
    }

    if (
        successCount
        === stations.length
    ) {
        addEvent(
            "WEATHER",
            "Wetterdaten für Köln, Bonn und Mainz aktualisiert.",
            "success"
        );
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

    selectedReportPeriod =
        period;

    const newHistory =
        {};

    try {
        await Promise.all(
            stations.map(
                async station => {
                    newHistory[
                        station
                    ] =
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

        updateReports();

        syncPeriodButtons(
            period
        );

        if (latestState) {
            updateMapPopups(
                latestState
            );

            generateAlerts(
                latestState
            );
        }

        const counts =
            stations
                .map(
                    station =>
                        `${locations[station].label}: ${historyCache[station]?.length || 0}`
                )
                .join(" · ");

        addEvent(
            "HISTORY",
            `${periodLabel(period)} geladen · ${counts}`,
            "success"
        );

    } catch (error) {
        console.error(
            "History Fehler:",
            error
        );

        addEvent(
            "HISTORY",
            error.message,
            "error"
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
                    cache:
                        "no-store"
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
                    data.stations?.[
                        station
                    ]
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

        const backendStatus =
            document.getElementById(
                "backendStatus"
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

        if (backendStatus) {
            backendStatus.textContent =
                "ONLINE";
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


        /*
         * MQTT Statuswechsel loggen.
         */

        if (
            previousMqttState
            !== data.mqttConnected
        ) {
            addEvent(
                "MQTT",
                data.mqttConnected
                    ? "Mosquitto Broker verbunden."
                    : "Mosquitto Broker getrennt.",
                data.mqttConnected
                    ? "success"
                    : "error"
            );

            previousMqttState =
                data.mqttConnected;
        }


        if (data.lastError) {
            if (errorBox) {
                errorBox.hidden =
                    false;

                errorBox.textContent =
                    data.lastError;
            }

            addEvent(
                "BACKEND",
                data.lastError,
                "error"
            );

        } else if (errorBox) {
            errorBox.hidden =
                true;
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
            errorBox.hidden =
                false;

            errorBox.textContent =
                `Dashboard-Fehler: ${error.message}`;
        }

        addEvent(
            "BACKEND",
            `Backend nicht erreichbar: ${error.message}`,
            "error"
        );
    }
}


// ============================================================
// ZEITRAUM-BUTTONS
// ============================================================

function syncPeriodButtons(period) {
    document
        .querySelectorAll(
            ".chart-range, .report-range"
        )
        .forEach(button => {
            button.classList.toggle(
                "active",
                button.dataset.period
                === period
            );
        });
}


function bindPeriodButtons() {
    document
        .querySelectorAll(
            ".chart-range, .report-range"
        )
        .forEach(button => {
            button.addEventListener(
                "click",
                () => {
                    const period =
                        button.dataset.period
                        || "24h";

                    refreshHistory(
                        period
                    );
                }
            );
        });
}


// ============================================================
// EXPORT BUTTONS
// ============================================================

function bindExportButtons() {
    const csv =
        document.getElementById(
            "exportCsv"
        );

    const json =
        document.getElementById(
            "exportJson"
        );

    if (csv) {
        csv.addEventListener(
            "click",
            exportCsv
        );
    }

    if (json) {
        json.addEventListener(
            "click",
            exportJson
        );
    }
}


// ============================================================
// INITIALISIERUNG
// ============================================================

document.addEventListener(
    "DOMContentLoaded",
    () => {
        addEvent(
            "SYSTEM",
            "PegelConnect Pro Dashboard gestartet.",
            "success"
        );

        initMap();

        bindPeriodButtons();

        bindExportButtons();

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


        /*
         * Initiale Daten.
         */

        refreshState();

        refreshWeather();

        refreshHistory(
            "24h"
        );


        /*
         * Live Status:
         * alle 5 Sekunden.
         */

        setInterval(
            refreshState,
            5000
        );


        /*
         * Wetter:
         * alle 5 Minuten.
         */

        setInterval(
            refreshWeather,
            300000
        );


        /*
         * Historie:
         * alle 5 Minuten.
         */

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
