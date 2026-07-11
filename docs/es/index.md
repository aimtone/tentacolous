Spring Boot + seis bases de datos SQL

# Tentacolous {#top}

Ejecuta codigo Java cuando alguien inserta, modifica o elimina datos en PostgreSQL, MySQL, MariaDB, SQL Server, Oracle o SQLite, aunque el cambio provenga de otra aplicacion, un script SQL o un sistema legado.

Olvidate de consultar la base de datos constantemente o de conectar toda tu aplicacion a JPA. Tentacolous detecta cambios reales mediante triggers y ejecuta metodos Java anotados de forma simple, rapida y confiable.

[Empezar](#start){ .md-button .md-button--primary }
[Documentación](documentation/index.md){ .md-button }
[GitHub](https://github.com/aimtone/tentacolous){ .md-button }

![Tentacolous](../assets/logonegro.png){ .content-logo }

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    notify(person.getEmail());
}
```

Lo esencial

## Conecta los cambios de tu base de datos con codigo Java. {#features}

Tentacolous detecta cambios reales en tu base de datos y ejecuta automaticamente el codigo que tu defines. Sin importar si el cambio proviene de tu aplicacion, otra API, un script SQL o un sistema legado.

### Detecta cambios reales

No solo escucha las operaciones realizadas por tu aplicacion. Tambien captura cambios hechos desde otras APIs, herramientas SQL, procesos ETL o sistemas legados.

### Escribe solo Java

Anota un metodo con `@UponInserting`, `@UponUpdating` o `@UponDeleting` y recibe la entidad modificada lista para trabajar. Sin escribir consultas adicionales ni codigo repetitivo.

### Funciona de forma confiable

Tentacolous utiliza triggers especificos para cada motor, una tabla de eventos y un procesador configurable para garantizar que cada cambio sea detectado y procesado correctamente.

Inicio rapido

## Empieza en menos de 5 minutos. {#start}

Agrega la dependencia y el driver JDBC, configura Tentacolous y comienza a reaccionar a cambios de base de datos con Java.

1. Agrega la dependenciaMaven Central

Maven
Gradle

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.2.0</version>
</dependency>
```

```groovy
implementation 'io.github.aimtone:tentacolous:0.2.0'
```

2. Agrega propertiesConfig Spring

application.yml
application.properties

```yaml
tentacolous:
  enabled: true
  schema-management: auto
  event-table: db_change_event
  poll-interval: 1s
  initial-delay: 0s
  batch-size: 100
  max-attempts: 3
```

```properties
tentacolous.enabled=true
tentacolous.schema-management=auto
tentacolous.event-table=db_change_event
tentacolous.poll-interval=1s
tentacolous.initial-delay=0s
tentacolous.batch-size=100
tentacolous.max-attempts=3
```

3. Usa las anotacionesJava

Generica
Insertar
Actualizar
Eliminar

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    notify(person.getEmail());
}
```

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    syncProfile(person);
}

// o

@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
    syncProfile(newPerson);
}

// o

@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
    syncProfile(newPerson);
}
```

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    removeFromIndex(person.getId());
}

// o

@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person, List<Person> history) {
    removeFromIndex(person.getId());
}
```

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE
)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
    syncProfile(newPerson);
}
```

### Necesitas detalles de filtros, modos de schema, seguridad o pruebas manuales?

Consulta la guia tecnica para ver configuracion avanzada, ejemplos completos y recomendaciones de produccion.

[Abrir documentación](documentation/index.md){ .md-button .md-button--primary }
