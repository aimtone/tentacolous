Spring Boot + PostgreSQL

# Tentacolous {#top}

Ejecuta codigo Java cuando alguien inserta, modifica o elimina datos en PostgreSQL, aunque el cambio provenga de otra aplicacion, un script SQL o un sistema legado.

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

## Conecta los cambios de PostgreSQL con tu codigo Java. {#features}

Tentacolous detecta cambios reales en tu base de datos y ejecuta automaticamente el codigo que tu defines. Sin importar si el cambio proviene de tu aplicacion, otra API, un script SQL o un sistema legado.

### Detecta cambios reales

No solo escucha las operaciones realizadas por tu aplicacion. Tambien captura cambios hechos desde otras APIs, herramientas SQL, procesos ETL o sistemas legados.

### Escribe solo Java

Anota un metodo con `@UponInserting`, `@UponUpdating` o `@UponDeleting` y recibe la entidad modificada lista para trabajar. Sin escribir consultas adicionales ni codigo repetitivo.

### Funciona de forma confiable

Tentacolous utiliza triggers de PostgreSQL, una tabla de eventos y un procesador configurable para garantizar que cada cambio sea detectado y procesado correctamente.

Inicio rapido

## Empieza en menos de 5 minutos. {#start}

Solo agrega la dependencia, configura Tentacolous y comienza a reaccionar a los cambios de PostgreSQL con Java.

1. Agrega la dependenciaMaven Central

Maven
Gradle

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.1.8</version>
</dependency>
```

```groovy
implementation 'io.github.aimtone:tentacolous:0.1.8'
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

Por que existe

## Porque no todos los cambios pasan por tu aplicacion. {#why}

En muchos proyectos, la base de datos es modificada por mas de un sistema: otras APIs, scripts SQL, procesos ETL o aplicaciones legadas. Tentacolous permite que tu aplicacion reaccione a esos cambios sin depender de quien los realizo.

**01**

**PostgreSQL es la fuente de la verdad.** Cada `INSERT`, `UPDATE` o `DELETE` genera un evento desde la propia base de datos. No importa que aplicacion realizo el cambio.

**02**

**Tu logica sigue viviendo en Java.** Solo escribes metodos anotados. Tentacolous recibe el cambio, reconstruye la entidad y ejecuta tu codigo automaticamente.

**03**

**Disenado para produccion.** Controla como se crean los objetos de infraestructura, configura reintentos y adapta el comportamiento segun tu entorno.

Usos reales

## Donde Tentacolous aporta valor de verdad. {#use-cases}

Tentacolous encaja en sistemas donde la base de datos la toca mas de un actor y tu aplicacion Spring Boot aun necesita reaccionar con codigo Java claro y testeable.

### Auditoria tecnica

Captura cambios reales de tablas, conserva estados de procesamiento e inspecciona eventos fallidos sin esconder todo en memoria.

### Sincronizacion de sistemas

Reacciona cuando se insertan, actualizan o eliminan registros y sincroniza otro servicio, cache, indice de busqueda o API interna.

### Integraciones legacy

Permite que una app Spring Boot moderna escuche cambios hechos por sistemas antiguos que todavia escriben directo en PostgreSQL.

### Automatizacion de negocio

Dispara flujos Java cuando aparece un pago, orden, solicitud o usuario en la base, aunque venga de otro proceso.

### Reacciones post-cambio

Envia notificaciones, recalcula datos derivados, invalida cache o publica un evento despues de una operacion real en base de datos.

### Visibilidad operacional

Usa estados como pending, processed y failed para entender que se ejecuto, que se reintento y que necesita atencion.

Como funciona

## Un flujo corto y observable. {#how}

Tentacolous registra listeners, crea o valida infraestructura PostgreSQL, captura cambios y despacha eventos hacia tus metodos.

**Tabla PostgreSQL**INSERT / UPDATE / DELETE

->

**db\_change\_event**payload JSON + estado

->

**Poller Spring**lotes + reintentos

->

**Listener Java**@UponInserting, @UponUpdating, @UponDeleting

![Tentacolous](../assets/logonegro.png){ .content-logo .content-logo--small }
Listo cuando tu base cambia

## Manten la base de datos observable y la reaccion en Java.

Detecta cambios reales, ejecuta tu logica de negocio y conserva visibilidad sobre cada evento procesado.

[Empezar con 0.1.8](#start){ .md-button .md-button--primary }
[Leer documentación](documentation/index.md){ .md-button }
[Ver en GitHub](https://github.com/aimtone/tentacolous){ .md-button }
