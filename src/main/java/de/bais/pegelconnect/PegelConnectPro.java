package de.bais.pegelconnect;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PegelConnectPro {

    public static void main(String[] args) throws Exception {

        // =====================================================
        // KONFIGURATION LADEN
        // =====================================================

        AppConfig config =
                AppConfig.load();


        System.out.println(
                "=========================================="
        );

        System.out.println(
                " " + config.applicationName()
        );

        System.out.println(
                "=========================================="
        );

        System.out.println(
                " HTTP Port:       "
                        + config.httpPort()
        );

        System.out.println(
                " MQTT Broker:     "
                        + config.mqttBrokerUri()
        );

        System.out.println(
                " PEGELONLINE:     "
                        + config.pegelOnlineServer()
                        + config.pegelOnlineApiPath()
        );

        System.out.println(
                " Weather API:     "
                        + (
                        config.weatherEnabled()
                                ? config.weatherServer()
                                : "DISABLED"
                )
        );

        System.out.println(
                " Fetch-Intervall: "
                        + config.fetchIntervalSeconds()
                        + " Sekunden"
        );

        System.out.println(
                " Stationen:       "
                        + String.join(
                        ", ",
                        config.stationNames()
                )
        );

        System.out.println(
                "=========================================="
        );


        // =====================================================
        // DATA STORE
        // =====================================================

        ReadingStore store =
                new ReadingStore();


        // =====================================================
        // PEGELONLINE CLIENT
        // =====================================================

        /*
         * WICHTIG:
         * Der Client bekommt jetzt die zentrale Konfiguration.
         *
         * Damit kommen:
         * - Server
         * - API Path
         * - Timeout
         * - Stationen
         *
         * aus config.json / ENV.
         */

        PegelOnlineClient api =
                new PegelOnlineClient(
                        config
                );


        // =====================================================
        // MQTT
        // =====================================================

        MqttGateway mqtt =
                connectMqtt(
                        config,
                        store
                );


        // =====================================================
        // HTTP / WEB SERVER
        // =====================================================

        WebServer web =
                new WebServer(
                        config,
                        store,
                        mqtt::isConnected,
                        api
                );


        // =====================================================
        // SCHEDULER
        // =====================================================

        ScheduledExecutorService scheduler =
                Executors
                        .newSingleThreadScheduledExecutor();


        // =====================================================
        // PEGEL FETCH JOB
        // =====================================================

        Runnable fetchJob =
                () -> {

                    for (
                            String station
                            : config.stationNames()
                    ) {

                        try {

                            PegelOnlineClient.RawMeasurement raw =
                                    api.fetch(
                                            station
                                    );


                            Integer trend =
                                    store.calculateTrend(
                                            station,
                                            raw.value()
                                    );


                            StationReading reading =
                                    new StationReading(
                                            station,
                                            raw.timestamp(),
                                            raw.value(),
                                            "cm",
                                            trend
                                    );


                            store.accept(
                                    reading
                            );


                            if (
                                    mqtt.isConnected()
                            ) {

                                mqtt.publishReading(
                                        reading
                                );
                            }


                            System.out.printf(
                                    "[%s] %s %.1f cm trend=%s%n",
                                    Instant.now(),
                                    station,
                                    raw.value(),
                                    trend
                            );


                        } catch (Exception ex) {

                            String message =
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : ex
                                            .getClass()
                                            .getSimpleName();


                            store.setError(
                                    station
                                            + ": "
                                            + message
                            );


                            System.err.printf(
                                    "[%s] Fehler %s: %s%n",
                                    Instant.now(),
                                    station,
                                    message
                            );
                        }
                    }
                };


        // =====================================================
        // START
        // =====================================================

        web.start();


        /*
         * Direkt beim Start einmal abrufen.
         * Danach übernimmt der Scheduler.
         */

        fetchJob.run();


        scheduler.scheduleWithFixedDelay(
                fetchJob,
                config.fetchIntervalSeconds(),
                config.fetchIntervalSeconds(),
                TimeUnit.SECONDS
        );


        // =====================================================
        // CLEAN SHUTDOWN
        // =====================================================

        Runtime
                .getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {

                                    System.out.println(
                                            "PegelConnect Pro wird beendet..."
                                    );

                                    scheduler.shutdownNow();

                                    web.close();

                                    mqtt.close();
                                },
                                "pegelconnect-shutdown"
                        )
                );


        // =====================================================
        // READY
        // =====================================================

        System.out.println(
                config.applicationName()
                        + " läuft auf Port "
                        + config.httpPort()
        );

        System.out.println(
                "External Configuration: ACTIVE"
        );

        System.out.println(
                "Konfigurations-Priorität: "
                        + "ENV > config.json > Defaults"
        );


        // Main Thread am Leben halten.

        new CountDownLatch(
                1
        ).await();
    }


    // =========================================================
    // MQTT CONNECTION
    // =========================================================

    private static MqttGateway connectMqtt(
            AppConfig config,
            ReadingStore store
    ) throws Exception {

        try {

            return new MqttGateway(
                    config
            );

        } catch (
                MqttException ex
        ) {

            store.setError(
                    "MQTT-Verbindung fehlgeschlagen: "
                            + ex.getMessage()
            );

            throw ex;
        }
    }
}