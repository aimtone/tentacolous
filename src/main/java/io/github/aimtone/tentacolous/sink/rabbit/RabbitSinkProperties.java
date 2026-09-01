package io.github.aimtone.tentacolous.sink.rabbit;

import io.github.aimtone.tentacolous.sink.MessageFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tentacolous.rabbitmq")
public class RabbitSinkProperties {

    /** Enables the RabbitMQ sink. Disabled by default. */
    private boolean enabled = false;

    /** Topic exchange every change is published to. */
    private String exchange = "tentacolous";

    /**
     * Prefix added to the routing key. The routing key is
     * {@code routingKeyPrefix + entityName + "." + operation}, lower-cased, for example
     * {@code person.update}.
     */
    private String routingKeyPrefix = "";

    /** Wire format of the message body. */
    private MessageFormat format = MessageFormat.ENVELOPE;

    /** Declares a durable topic exchange named {@link #exchange} on startup. */
    private boolean declareExchange = true;

    /** Adds {@code tentacolous-*} metadata headers to every message. */
    private boolean addHeaders = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKeyPrefix() {
        return routingKeyPrefix;
    }

    public void setRoutingKeyPrefix(String routingKeyPrefix) {
        this.routingKeyPrefix = routingKeyPrefix;
    }

    public MessageFormat getFormat() {
        return format;
    }

    public void setFormat(MessageFormat format) {
        this.format = format;
    }

    public boolean isDeclareExchange() {
        return declareExchange;
    }

    public void setDeclareExchange(boolean declareExchange) {
        this.declareExchange = declareExchange;
    }

    public boolean isAddHeaders() {
        return addHeaders;
    }

    public void setAddHeaders(boolean addHeaders) {
        this.addHeaders = addHeaders;
    }

    public String resolveRoutingKey(String entityName, String operation) {
        String prefix = routingKeyPrefix == null ? "" : routingKeyPrefix;
        return (prefix + entityName + "." + operation).toLowerCase();
    }
}
