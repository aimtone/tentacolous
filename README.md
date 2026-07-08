# Tentacolous

![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-4169E1?logo=postgresql&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/io.github.aimtone/tentacolous)
![GitHub stars](https://img.shields.io/github/stars/aimtone/tentacolous?style=social)
![GitHub forks](https://img.shields.io/github/forks/aimtone/tentacolous?style=social)

## Table of Contents

- [Introduction](#introduction)
- [Features](#-what-makes-tentacolous-different)
- [Requirements](#-requirements)
- [Quick Example](#quick-example)
- [Installation](#installation)
- [Configuration](#configuration)
- [Use Cases](#use-cases)
- [Why Tentacolous?](#why-tentacolous)
- [Conclusion](#conclusion)
- [Resources](#resources)

## Introduction

In modern development, many applications need to react in real time to changes in the database. However, most solutions depend on those changes happening inside the application itself.

But what if another system, a script, or even a database console user modifies the data?

**Tentacolous** is a Spring Boot library that automatically executes Java methods whenever an **INSERT**, **UPDATE**, or **DELETE** occurs in your database tables — even if the change originates **outside** your application.

---

## ✨ What makes Tentacolous different?

- 🔄 **Detects external changes** Reacts whether the modification comes from your API, another application, an SQL script, or a database console.

- ⚙️ **Automatic infrastructure** Creates PostgreSQL functions, triggers, and the internal event table automatically.

- 🎯 **Transparent integration** Converts the database event payload into your Java entity and invokes the corresponding annotated method.

- 🛠️ **Flexible configuration** Configure polling interval, batch size, retry attempts, event table name, and more.

---

## 📋 Requirements

- Java 17+
- Spring Boot application with a configured `DataSource`
- PostgreSQL (for automatic trigger generation)

---

# Quick Example

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

React to new records by simply annotating a method:

```java
@UponInserting(entity = Person.class)
public void onPersonInserted(Person person) {
    System.out.println("New record: " + person.getEmail());
}
```

This method is executed automatically every time a new record is inserted into the `person` table, regardless of where the insertion originated.

You can also listen for updates:

```java
@UponUpdating(entity = Person.class)
public void onPersonUpdated(Person person) {
    System.out.println("Record updated: " + person.getEmail());
}
```

Or deletions:

```java
@UponDeleting(entity = Person.class)
public void onPersonDeleted(Person person) {
    System.out.println("Record deleted: " + person.getEmail());
}
```

---

# Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.aimtone</groupId>
    <artifactId>tentacolous</artifactId>
    <version>0.1.6</version>
</dependency>
```

---

# Configuration

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

---

# Use Cases

- 📧 Send emails when new users are created.
- 🔔 Generate real-time notifications.
- 🚀 Publish Kafka events after database updates.
- 📝 Build automatic audit logs.
- 🔄 Synchronize data across multiple applications.
- ⚡ Trigger business workflows without modifying existing code.

---

# Why Tentacolous?

Unlike traditional application events (`@EventListener`, `ApplicationEventPublisher`, etc.), Tentacolous reacts to **database changes themselves**.

That means your listeners are executed even when the data is modified by:

- Another microservice
- An ETL process
- A scheduled job
- A SQL script
- A database administrator
- Any external application connected to the same database

---

# Conclusion

Tentacolous introduces a simple yet powerful way to build **reactive Spring Boot applications** driven by database events.

Whether you need auditing, synchronization, notifications, or event-driven integrations, Tentacolous lets you react to database changes with just a simple annotation.

---

# Resources

- 🌐 **Official Website**  
  https://aimtone.github.io/tentacolous/

- 📚 **Documentation**  
  https://aimtone.github.io/tentacolous/en/documentation/

- 💻 **GitHub Repository**  
  https://github.com/aimtone/tentacolous

---

Made with ❤️ for the Spring Boot community.
