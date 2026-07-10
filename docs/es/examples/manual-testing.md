## Pruebas manuales {#manual-testing}

Asume esta entidad y este listener:

```java
@Entity
@Table(name = "person")
public class Person {
    // ...
}

@UponInserting(entity = Person.class)
public void inserted(Person person) {
    System.out.println("Person inserted: " + person.getEmail());
}
```

Inicia tu aplicacion Spring Boot. Tentacolous deberia imprimir logs similares a:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'person' and entity 'Person'
Starting Tentacolous poller
```

Luego ejecuta este SQL en PostgreSQL:

```sql
INSERT INTO public.person (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

Deberias ver esto en la consola de tu aplicacion:

```java
Person inserted: test@example.com
```

Inspecciona eventos y triggers con:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'person';
```
