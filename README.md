# Tentacolous

![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-4169E1?logo=postgresql&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/io.github.aimtone/tentacolous)
![GitHub stars](https://img.shields.io/github/stars/aimtone/tentacolous?style=social)
![GitHub forks](https://img.shields.io/github/forks/aimtone/tentacolous?style=social)

## Table of Contents

- [Introduction](#introduction)
- [Features](#what-makes-tentacolous-different)
- [Requirements](#requirements)
- [Quick Example](#quick-example)
- [Generic Listener](#generic-listener)
- [Custom Filters](#custom-filters)
- [Listener Ordering](#listener-ordering)
- [Migrating from 0.1.7](#migrating-from-017)
- [Installation](#installation)
- [Configuration](#configuration)
- [Use Cases](#use-cases)
- [Why Tentacolous?](#why-tentacolous)
- [Resources](#resources)

## Introduction

In modern development, many applications need to react in real time to changes in the database. However, most solutions depend on those changes happening inside the application itself.

But what if another system, a script, or even a database console user modifies the data?

**Tentacolous** is a Spring Boot library that automatically executes Java methods whenever an **INSERT**, **UPDATE**, or **DELETE** occurs in your database tables, even if the change originates **outside** your application.

## What makes Tentacolous different?

- **Detects external changes**: Reacts whether the modification comes from your API, another application, an SQL script, or a database console.
- **Automatic infrastructure**: Creates database-specific triggers and the internal event table automatically.
- **Transparent integration**: Converts the database event payload into your Java entity and invokes the corresponding annotated method.
- **Flexible listeners**: Receive the current entity, the previous entity, or a simple history when you need it.
- **Flexible configuration**: Configure polling interval, batch size, retry attempts, event table name, and more.

## Requirements

- Java 17+
- Spring Boot application with a configured `DataSource`
- A supported database and its JDBC driver

Database support:

| Database | Automatic infrastructure | Payload/old payload | History | Status |
|---|---:|---:|---:|---|
| PostgreSQL | Yes | Yes | Yes | Stable |
| MySQL 8+ | Yes | Yes | Yes | Experimental |
| MariaDB | Yes | Yes | Yes | Experimental |
| SQL Server 2016+ | Yes | Yes | Yes | Experimental |
| Oracle 19c+ | Yes | Yes | Yes | Experimental |
| SQLite with JSON1 | Yes | Yes | Yes | Experimental |

`Experimental` means that the dialect implements the complete Tentacolous contract but still needs
end-to-end validation against the supported server and JDBC-driver versions before being declared stable.

## Quick Example

Suppose you have the following entity:

```java
@Entity
@Table(name = "person")
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String lastname;
    private String email;
}
```

React to new records by annotating a method:

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    System.out.println("New record: " + person.getEmail());
}
```

Listen for updates:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    System.out.println("Record updated: " + person.getEmail());
}
```

When you need the previous value too, add a second parameter:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson) {
}
```

When you need the previous changes for that record, add a history parameter:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

Listen for deletions:

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    System.out.println("Record deleted: " + person.getEmail());
}
```

Deletions can also receive history:

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

## Generic Listener

Version `0.1.8` adds `@TentacolousListener` as a generic alternative to the three operation-specific annotations. Select the operation with `ActionListener.INSERT`, `ActionListener.UPDATE`, or `ActionListener.DELETE`:

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.INSERT
)
public void onPersonInserted(Person person) {
}
```

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE
)
public void onPersonUpdated(Person newPerson, Person oldPerson, List<Person> history) {
}
```

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.DELETE
)
public void onPersonDeleted(Person person, List<Person> history) {
}
```

The generic annotation supports the same `entityName`, `field`, `valueType`, `value`, `filter`, `order`, and `exclude` parameters as the operation-specific annotations.

## Custom Filters

All four listener annotations support Spring-managed programmatic filters:

```java
@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE,
    filter = ActivePersonFilter.class
)
public void onActivePersonUpdated(Person newPerson, Person oldPerson) {
}
```

```java
@Component
public class ActivePersonFilter extends TentacolousFilter<Person> {

    @Override
    public boolean accept(TentacolousFilterContext<Person> context) {
        Person person = context.getEntity();
        Person oldPerson = context.getOldEntity();

        return person.isActive()
                && oldPerson != null
                && !Objects.equals(person.getStatus(), oldPerson.getStatus());
    }
}
```

`getOldEntity()` is available for updates and is `null` for inserts and deletes. `getOperation()` identifies the current database operation. When `filter` is configured together with `field`, `valueType`, or `value`, the custom filter has priority. Tentacolous logs a warning and the declarative filter is not executed.

The context also exposes event metadata:

```java
context.getEventId();
context.getEntityName();
context.getRecordKey();
```

For updates, the context reports which payload fields changed:

```java
context.getChangedFields();
context.hasChanged("status");
```

For inserts and deletes, `getChangedFields()` returns an empty set and `hasChanged(...)` returns `false`.

## Listener Ordering

Use `order` when several listeners handle the same entity and operation. Lower values run first; the default is `0`.

```java
@UponUpdating(entity = Person.class, order = 10)
public void updateProfile(Person person) {
}

@TentacolousListener(
    entity = Person.class,
    action = ActionListener.UPDATE,
    order = 20
)
public void registerAudit(Person person) {
}
```

If a listener fails, dispatch stops and the event follows the normal retry flow. Listener side effects should therefore be idempotent.

## Migrating from 0.1.7

Version `0.1.8` is backward compatible with existing listener annotations. You do not need to replace `@UponInserting`, `@UponUpdating`, or `@UponDeleting`, and there are no new database infrastructure requirements.

- Use `@TentacolousListener` only when you prefer one generic annotation selected with `action`.
- Add `filter = YourFilter.class` only when you need programmatic filtering.
- Custom filters must be Spring beans and must extend `TentacolousFilter` with a compatible entity type.
- If a custom filter is declared together with `field`, `valueType`, or `value`, the custom filter has priority and Tentacolous logs a warning.
- Declarative filters must define `field`, `valueType`, and `value` together.
- A method may declare one listener annotation per operation. For example, `@UponInserting` and `@UponUpdating` may share a method, but `@UponUpdating` and `@TentacolousListener(action = ActionListener.UPDATE)` may not.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.aimtone</groupId>
    <artifactId>tentacolous</artifactId>
    <version>0.1.8</version>
</dependency>
```

## Configuration

Configure Tentacolous in your `application.yml`:

```yaml
tentacolous:
  enabled: true
  schema-management: auto
  event-table: db_change_event
  poll-interval: 1s
  batch-size: 100
  max-attempts: 3
```

Tentacolous will automatically create and manage the required database infrastructure.

## Use Cases

- Send emails when new users are created.
- Generate real-time notifications.
- Run application workflows after database updates.
- Build automatic audit logs.
- Synchronize data across multiple applications.
- Trigger business workflows without modifying existing code.

## Why Tentacolous?

Unlike traditional application events (`@EventListener`, `ApplicationEventPublisher`, etc.), Tentacolous reacts to **database changes themselves**.

That means your listeners are executed even when the data is modified by:

- Another microservice
- An ETL process
- A scheduled job
- A SQL script
- A database administrator
- Any external application connected to the same database

## Resources

- **Official Website**  
  https://aimtone.github.io/tentacolous/

- **Documentation**  
  https://aimtone.github.io/tentacolous/en/documentation/0.1.8/

- **GitHub Repository**  
  https://github.com/aimtone/tentacolous

Made with love for the Spring Boot community.
