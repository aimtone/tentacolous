## Snippets de dependencias {#dependency}

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

### Sinks opcionales para colas de mensajes

Agrega una de estas solo si activas el sink correspondiente (ver [Colas de mensajes](../concepts/message-brokers.md)). Tentacolous las declara como opcionales, asi que no se arrastran de forma transitiva.

```xml
<!-- Sink de Kafka -->
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Sink de RabbitMQ -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
