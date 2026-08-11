package de.bais.pegelconnect;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PegelConnectPro {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        ReadingStore store = new ReadingStore();
        PegelOnlineClient api = new PegelOnlineClient();

        MqttGateway mqtt = connectMqtt(config, store);
        WebServer web = new WebServer(config.httpPort(), store, mqtt::isConnected);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable fetchJob = () -> {
            for (String station : config.stations()) {
                try {
                    PegelOnlineClient.RawMeasurement raw = api.fetch(station);
                    Integer trend = store.calculateTrend(station, raw.value());
                    StationReading reading = new StationReading(
                            station, raw.timestamp(), raw.value(), "cm", trend
                    );
                    store.accept(reading);
                    if (mqtt.isConnected()) {
                        mqtt.publishReading(reading);
                    }
                    System.out.printf("[%s] %s %.1f cm trend=%s%n",
                            Instant.now(), station, raw.value(), trend);
                } catch (Exception ex) {
                    store.setError(station + ": " + ex.getMessage());
                    System.err.printf("[%s] Fehler %s: %s%n",
                            Instant.now(), station, ex.getMessage());
                }
            }
        };

        web.start();
        fetchJob.run();
        scheduler.scheduleAtFixedRate(
                fetchJob,
                config.fetchIntervalSeconds(),
                config.fetchIntervalSeconds(),
                TimeUnit.SECONDS
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            web.close();
            mqtt.close();
        }));

        System.out.println("PegelConnect Pro läuft auf Port " + config.httpPort());
        new CountDownLatch(1).await();
    }

    private static MqttGateway connectMqtt(AppConfig config, ReadingStore store) throws Exception {
        try {
            return new MqttGateway(config);
        } catch (MqttException ex) {
            store.setError("MQTT-Verbindung fehlgeschlagen: " + ex.getMessage());
            throw ex;
        }
    }
}
