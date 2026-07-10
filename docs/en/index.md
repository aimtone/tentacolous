Spring Boot + PostgreSQL

# Tentacolous {#top}

Run Java code when someone inserts, updates or deletes data in PostgreSQL, even if the change comes from another application, a SQL script or a legacy system.

Stop constantly polling the database or coupling your whole application to JPA. Tentacolous detects real changes through triggers and runs annotated Java methods in a simple, fast and reliable way.

[Get started](#start){ .md-button .md-button--primary }
[Documentation](documentation/index.md){ .md-button }
[GitHub](https://github.com/aimtone/tentacolous){ .md-button }

![Tentacolous](../assets/logonegro.png){ .content-logo }

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    notify(person.getEmail());
}
```

Essentials

## Connect PostgreSQL changes with your Java code. {#features}

Tentacolous detects real changes in your database and automatically runs the code you define. It does not matter if the change comes from your application, another API, a SQL script or a legacy system.

### Detect real changes

It does not only listen to operations performed by your application. It also captures changes made by other APIs, SQL tools, ETL processes or legacy systems.

### Write only Java

Annotate a method with `@UponInserting`, `@UponUpdating` or `@UponDeleting` and receive the modified entity ready to use. No extra queries or repetitive code.

### Works reliably

Tentacolous uses PostgreSQL triggers, an event table and a configurable processor to ensure each change is detected and processed correctly.

Quick start

## Start in less than 5 minutes. {#start}

Just add the dependency, configure Tentacolous and start reacting to PostgreSQL changes with Java.

1. Add the dependencyMaven Central

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

2. Add propertiesSpring config

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

3. Use annotationsJava

Generic
Insert
Update
Delete

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

// or

@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
    syncProfile(newPerson);
}

// or

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

// or

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

### Need details about filters, schema modes, security or manual testing?

Open the technical guide for advanced configuration, complete examples and production recommendations.

[Open documentation](documentation/index.md){ .md-button .md-button--primary }
