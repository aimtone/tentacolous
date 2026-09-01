## Dependency snippets {#dependency}

### Gradle

```groovy
implementation 'io.github.aimtone:tentacolous:0.3.0'
```

### Maven

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.3.0</version>
</dependency>
```

### Optional message-broker sinks

Add one of these only if you enable the matching sink (see [Message brokers](../concepts/message-brokers.md)). Tentacolous declares them as optional, so they are not pulled in transitively.

```xml
<!-- Kafka sink -->
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>

<!-- RabbitMQ sink -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
