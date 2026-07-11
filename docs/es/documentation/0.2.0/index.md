# Tentacolous 0.2.0 {#top}

La version `0.2.0` hace que Tentacolous sea agnostico a la base de datos, conservando un solo artefacto y la API existente de listeners.

## Bases soportadas

| Base de datos | Tabla y triggers automaticos | Payload anterior | Historial |
|---|---:|---:|---:|
| PostgreSQL 16 | Si | Si | Si |
| MySQL 8.4 | Si | Si | Si |
| MariaDB 11.8 | Si | Si | Si |
| SQL Server 16 | Si | Si | Si |
| Oracle Database 23 | Si | Si | Si |
| SQLite 3.53 con JSON1 | Si | Si | Si |

La aplicacion proporciona el driver JDBC correspondiente. Tentacolous detecta el producto mediante metadata JDBC y selecciona automaticamente su dialecto.

## Instalacion

```xml
<dependency>
  <groupId>io.github.aimtone</groupId>
  <artifactId>tentacolous</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Contrato de compatibilidad

Los seis dialectos superaron el mismo escenario de extremo a extremo con 28 listeners, incluidas anotaciones especificas y genericas, filtros declarativos y personalizados, orden, columnas excluidas, entidad anterior e historial como `List<Entity>` y `Entity[]`.

## Consideracion de SQLite

SQLite puede reutilizar un `INTEGER PRIMARY KEY` eliminado. Como el historial se correlaciona mediante nombre de entidad y clave de registro, usa `INTEGER PRIMARY KEY AUTOINCREMENT`, UUID u otra clave que no se reutilice durante la retencion del historial.

Consulta la [guia tecnica actual](../index.md) para ver la referencia completa de API y configuracion.
