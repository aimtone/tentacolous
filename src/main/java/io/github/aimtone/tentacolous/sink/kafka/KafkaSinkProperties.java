package io.github.aimtone.tentacolous.sink.kafka;

import io.github.aimtone.tentacolous.sink.MessageFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tentacolous.kafka")
public class KafkaSinkProperties {

    /** Enables the Kafka sink. Disabled by default. */
    private boolean enabled = false;

    /**
     * Fixed topic every change is published to. When empty, the topic is
     * {@code topicPrefix + entityName} (see {@link #topicPrefix}).
     */
    private String topic = "";

    /** Prefix used to build a per-entity topic when {@link #topic} is empty, for example {@code "cdc."}. */
    private String topicPrefix = "";

    /** Wire format of the message body. */
    private MessageFormat format = MessageFormat.ENVELOPE;

    /** Adds {@code tentacolous-*} metadata headers to every record. */
    private boolean addHeaders = true;

    /** How long to wait for the broker acknowledgement before failing the event (and retrying later). */
    private Duration sendTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public MessageFormat getFormat() {
        return format;
    }

    public void setFormat(MessageFormat format) {
        this.format = format;
    }

    public boolean isAddHeaders() {
        return addHeaders;
    }

    public void setAddHeaders(boolean addHeaders) {
        this.addHeaders = addHeaders;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public String resolveTopic(String entityName) {
        if (topic != null && !topic.isBlank()) {
            return topic;
        }

        return (topicPrefix == null ? "" : topicPrefix) + entityName;
    }
}
