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

    public MqttGateway(AppConfig config) throws MqttException {
        client = new MqttClient(config.mqttBrokerUri(), config.mqttClientId(), new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        options.setWill("pegel/status", "offline".getBytes(StandardCharsets.UTF_8), 1, true);

        client.connect(options);
        publishText("pegel/status", "online", true);
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public void publishReading(StationReading reading) throws MqttException {
        String stationTopic = reading.station()
                .toLowerCase(Locale.ROOT)
                .replace("ö", "oe")
                .replace("ä", "ae")
                .replace("ü", "ue")
                .replace("ß", "ss");
        publish("pegel/" + stationTopic + "/wasserstand", reading.toJson().toString(), true);
    }

    private void publishText(String topic, String payload, boolean retained) throws MqttException {
        publish(topic, payload, retained);
    }

    private void publish(String topic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        client.publish(topic, message);
    }

    @Override
    public void close() {
        try {
            if (client.isConnected()) {
                publishText("pegel/status", "offline", true);
                client.disconnect();
            }
            client.close();
        } catch (MqttException ignored) {
        }
    }
}
