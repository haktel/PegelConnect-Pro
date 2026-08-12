package de.bais.pegelconnect;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MqttGateway implements AutoCloseable {

    private final MqttClient client;


    // =========================================================
    // KONSTRUKTOR
    // =========================================================

    public MqttGateway(
            AppConfig config
    ) throws MqttException {

        client =
                new MqttClient(
                        config.mqttBrokerUri(),
                        config.mqttClientId(),
                        new MemoryPersistence()
                );


        MqttConnectOptions options =
                new MqttConnectOptions();


        options.setAutomaticReconnect(
                true
        );


        options.setCleanSession(
                true
        );


        /*
         * Kein Hardcoding mehr:
         * Timeout kommt aus config.json / ENV.
         */

        options.setConnectionTimeout(
                config.mqttTimeoutSeconds()
        );


        options.setKeepAliveInterval(
                60
        );


        options.setWill(
                "pegel/status",
                "offline".getBytes(
                        StandardCharsets.UTF_8
                ),
                1,
                true
        );


        client.connect(
                options
        );


        publishText(
                "pegel/status",
                "online",
                true
        );
    }


    // =========================================================
    // STATUS
    // =========================================================

    public boolean isConnected() {

        return client != null
                && client.isConnected();
    }


    // =========================================================
    // PEGELDATEN PUBLISHEN
    // =========================================================

    public void publishReading(
            StationReading reading
    ) throws MqttException {

        String stationTopic =
                normalizeStationTopic(
                        reading.station()
                );


        publish(
                "pegel/"
                        + stationTopic
                        + "/wasserstand",
                reading
                        .toJson()
                        .toString(),
                true
        );
    }


    // =========================================================
    // TEXT PUBLISH
    // =========================================================

    private void publishText(
            String topic,
            String payload,
            boolean retained
    ) throws MqttException {

        publish(
                topic,
                payload,
                retained
        );
    }


    // =========================================================
    // GENERISCHES PUBLISH
    // =========================================================

    private void publish(
            String topic,
            String payload,
            boolean retained
    ) throws MqttException {

        if (
                !client.isConnected()
        ) {

            throw new MqttException(
                    MqttException.REASON_CODE_CLIENT_NOT_CONNECTED
            );
        }


        MqttMessage message =
                new MqttMessage(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        message.setQos(
                1
        );


        message.setRetained(
                retained
        );


        client.publish(
                topic,
                message
        );
    }


    // =========================================================
    // STATION TOPIC NORMALISIEREN
    // =========================================================

    private static String normalizeStationTopic(
            String station
    ) {

        if (station == null) {
            return "unknown";
        }


        return station
                .toLowerCase(
                        Locale.ROOT
                )
                .replace(
                        "ö",
                        "oe"
                )
                .replace(
                        "ä",
                        "ae"
                )
                .replace(
                        "ü",
                        "ue"
                )
                .replace(
                        "ß",
                        "ss"
                )
                .replace(
                        " ",
                        "-"
                );
    }


    // =========================================================
    // SHUTDOWN
    // =========================================================

    @Override
    public void close() {

        try {

            if (
                    client != null
                            && client.isConnected()
            ) {

                publishText(
                        "pegel/status",
                        "offline",
                        true
                );


                client.disconnect();
            }


            if (
                    client != null
            ) {

                client.close();
            }


        } catch (
                MqttException ignored
        ) {

            // Shutdown darf nicht blockieren.
        }
    }
}