"use strict";

document.addEventListener("DOMContentLoaded", () => {

    const pdfButton =
        document.getElementById("exportPdf");

    if (!pdfButton) {
        return;
    }

    pdfButton.addEventListener(
        "click",
        createPdfReport
    );
});


function text(id, fallback = "–") {

    const element =
        document.getElementById(id);

    if (!element) {
        return fallback;
    }

    const value =
        element.textContent?.trim();

    return value || fallback;
}


function getActivePeriod() {

    const active =
        document.querySelector(
            ".report-range.active"
        )
        ||
        document.querySelector(
            ".chart-range.active"
        );

    if (!active) {
        return "24H";
    }

    return active
        .textContent
        .trim();
}


function stationReport(
    name,
    domId
) {

    return {
        name,

        current:
            text(`value-${domId}`) + " cm",

        timestamp:
            text(`time-${domId}`),

        temperature:
            text(`weather-temp-${domId}`),

        apparent:
            text(`weather-feels-${domId}`),

        wind:
            text(`weather-wind-${domId}`),

        precipitation:
            text(`weather-rain-${domId}`),

        minimum:
            text(`report-min-${domId}`),

        maximum:
            text(`report-max-${domId}`),

        average:
            text(`report-avg-${domId}`),

        delta:
            text(`report-delta-${domId}`),

        count:
            text(`report-count-${domId}`),

        status:
            text(`report-status-${domId}`),

        trend:
            text(`report-trend-${domId}`)
    };
}


function escapeHtml(value) {

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}


function createPdfReport() {

    const period =
        getActivePeriod();

    const generated =
        new Date().toLocaleString(
            "de-DE",
            {
                dateStyle: "long",
                timeStyle: "medium"
            }
        );

    const stations = [

        stationReport(
            "Köln",
            "KOELN"
        ),

        stationReport(
            "Bonn",
            "BONN"
        ),

        stationReport(
            "Mainz",
            "MAINZ"
        )
    ];


    const stationHtml =
        stations
            .map(station => `

                <section class="station">

                    <div class="station-head">

                        <div>
                            <span class="label">
                                PEGELSTATION
                            </span>

                            <h2>
                                ${escapeHtml(station.name)}
                            </h2>
                        </div>

                        <div class="current">
                            ${escapeHtml(station.current)}
                        </div>

                    </div>


                    <div class="stats">

                        <div>
                            <span>Minimum</span>
                            <strong>
                                ${escapeHtml(station.minimum)}
                            </strong>
                        </div>

                        <div>
                            <span>Maximum</span>
                            <strong>
                                ${escapeHtml(station.maximum)}
                            </strong>
                        </div>

                        <div>
                            <span>Durchschnitt</span>
                            <strong>
                                ${escapeHtml(station.average)}
                            </strong>
                        </div>

                        <div>
                            <span>Änderung</span>
                            <strong>
                                ${escapeHtml(station.delta)}
                            </strong>
                        </div>

                        <div>
                            <span>Messpunkte</span>
                            <strong>
                                ${escapeHtml(station.count)}
                            </strong>
                        </div>

                        <div>
                            <span>Status</span>
                            <strong>
                                ${escapeHtml(station.status)}
                            </strong>
                        </div>

                    </div>


                    <div class="weather">

                        <div>
                            <span>Temperatur</span>
                            <strong>
                                ${escapeHtml(station.temperature)}
                            </strong>
                        </div>

                        <div>
                            <span>Gefühlt</span>
                            <strong>
                                ${escapeHtml(station.apparent)}
                            </strong>
                        </div>

                        <div>
                            <span>Wind</span>
                            <strong>
                                ${escapeHtml(station.wind)}
                            </strong>
                        </div>

                        <div>
                            <span>Niederschlag</span>
                            <strong>
                                ${escapeHtml(station.precipitation)}
                            </strong>
                        </div>

                    </div>


                    <div class="station-footer">

                        Letzte Messung:
                        ${escapeHtml(station.timestamp)}

                        · Trend:
                        ${escapeHtml(station.trend)}

                    </div>

                </section>
            `)
            .join("");


    const reportHtml = `

<!doctype html>

<html lang="de">

<head>

<meta charset="utf-8">

<title>
PegelConnect Pro Report
</title>

<style>

@page {
    size: A4;
    margin: 15mm;
}

* {
    box-sizing: border-box;
}

body {
    margin: 0;

    font-family:
        Arial,
        Helvetica,
        sans-serif;

    color: #172230;

    background: white;
}

header.report-header {
    display: flex;
    justify-content: space-between;
    gap: 30px;

    padding-bottom: 18px;

    border-bottom:
        3px solid #172230;
}

.brand {
    font-size: 22px;
    font-weight: 800;
}

.brand span {
    font-size: 11px;
    font-weight: 600;

    letter-spacing: 0.14em;
}

.meta {
    text-align: right;

    font-size: 11px;

    line-height: 1.6;
}

h1 {
    margin:
        25px 0 5px;

    font-size: 30px;
}

.subtitle {
    margin-bottom: 24px;

    color: #657080;
}

.summary {
    display: grid;
    grid-template-columns:
        repeat(4, 1fr);

    gap: 10px;

    margin-bottom: 25px;
}

.summary div {
    padding: 12px;

    border:
        1px solid #d9dfe5;

    border-radius: 7px;
}

.summary span,
.stats span,
.weather span {
    display: block;

    margin-bottom: 4px;

    color: #657080;

    font-size: 9px;

    text-transform: uppercase;

    letter-spacing: 0.05em;
}

.summary strong {
    font-size: 12px;
}

.station {
    margin-bottom: 20px;

    padding: 17px;

    border:
        1px solid #d6dde4;

    border-radius: 9px;

    break-inside: avoid;
}

.station-head {
    display: flex;
    justify-content: space-between;
    align-items: center;

    margin-bottom: 14px;
}

.station h2 {
    margin: 2px 0 0;

    font-size: 22px;
}

.label {
    color: #657080;

    font-size: 8px;

    letter-spacing: 0.12em;
}

.current {
    font-size: 25px;
    font-weight: 800;
}

.stats {
    display: grid;
    grid-template-columns:
        repeat(3, 1fr);

    gap: 8px;
}

.stats div,
.weather div {
    padding: 9px;

    background: #f4f6f8;

    border-radius: 6px;
}

.stats strong,
.weather strong {
    font-size: 12px;
}

.weather {
    display: grid;
    grid-template-columns:
        repeat(4, 1fr);

    gap: 8px;

    margin-top: 8px;
}

.station-footer {
    margin-top: 11px;

    color: #657080;

    font-size: 10px;
}

.report-note {
    margin-top: 20px;

    padding: 12px;

    border-left:
        4px solid #738295;

    background: #f4f6f8;

    font-size: 10px;

    line-height: 1.6;
}

footer {
    margin-top: 28px;

    padding-top: 10px;

    border-top:
        1px solid #d6dde4;

    color: #657080;

    font-size: 9px;

    display: flex;
    justify-content: space-between;
}

@media print {

    button {
        display: none;
    }

}

</style>

</head>


<body>


<header class="report-header">

    <div class="brand">

        PEGELCONNECT PRO

        <br>

        <span>
            HYDRO & WEATHER OPERATIONS CENTER
        </span>

    </div>


    <div class="meta">

        Report erstellt:
        <strong>
            ${escapeHtml(generated)}
        </strong>

        <br>

        Zeitraum:
        <strong>
            ${escapeHtml(period)}
        </strong>

    </div>

</header>


<h1>
    Pegel- und Wetterbericht
</h1>


<div class="subtitle">

    Historische Auswertung und aktueller
    Betriebszustand der Stationen Köln,
    Bonn und Mainz.

</div>


<div class="summary">

    <div>

        <span>
            Datenquelle
        </span>

        <strong>
            PEGELONLINE
        </strong>

    </div>


    <div>

        <span>
            Zeitraum
        </span>

        <strong>
            ${escapeHtml(period)}
        </strong>

    </div>


    <div>

        <span>
            MQTT
        </span>

        <strong>
            ${escapeHtml(text("brokerStatus"))}
        </strong>

    </div>


    <div>

        <span>
            Weather API
        </span>

        <strong>
            ${escapeHtml(text("weatherApiStatus"))}
        </strong>

    </div>

</div>


${stationHtml}


<div class="report-note">

    <strong>Hinweis:</strong>

    Die angezeigten Monitoring-Statuswerte dienen
    der technischen Auswertung innerhalb des
    PegelConnect-Pro-Projekts.

    Sie stellen keine amtliche
    Hochwasserwarnung dar.

</div>


<footer>

    <div>
        PegelConnect Pro
    </div>

    <div>
        Java 17 · MQTT · NGINX · PEGELONLINE
    </div>

</footer>


<script>

window.addEventListener(
    "load",
    () => {

        setTimeout(
            () => {
                window.print();
            },
            300
        );
    }
);

<\/script>


</body>

</html>
`;


    const reportWindow = window.open("", "_blank");

if (!reportWindow) {
    alert(
        "PDF-Report konnte nicht geöffnet werden. Bitte Pop-ups für 127.0.0.1 erlauben."
    );
    return;
}


    reportWindow.document.open();

    reportWindow.document.write(
        reportHtml
    );

    reportWindow.document.close();
}