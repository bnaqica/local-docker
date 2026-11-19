package com.bnaqica.keycloak.events;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jboss.logging.Logger;

/**
 * Publishes to one Kafka topic. Sending is asynchronous so a login never waits on Kafka; a failed send is logged with
 * the event's key and never thrown, because an event must not break the login or admin action that caused it.
 */
public class KafkaEventPublisher implements EventPublisher, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    private final Producer<String, String> producer;
    private final String topic;

    public KafkaEventPublisher(Producer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void publish(String key, String json) {
        try {
            producer.send(new ProducerRecord<>(topic, key, json), (metadata, error) -> {
                if (error != null) {
                    LOG.errorf(error, "Couldn't publish Keycloak event for %s to %s", key, topic);
                }
            });
        } catch (RuntimeException ex) {
            LOG.errorf(ex, "Couldn't publish Keycloak event for %s to %s", key, topic);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
