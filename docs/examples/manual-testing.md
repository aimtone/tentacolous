## Manual testing {#manual-testing}

Assume this entity and listener:

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

Start your Spring Boot application. Tentacolous should print logs similar to:

```text
Tentacolous registered 1 listener method(s)
Initializing Tentacolous schema using event table 'db_change_event'
Creating Tentacolous INSERT trigger for table 'person' and entity 'Person'
Starting Tentacolous poller
```

Then run this SQL in PostgreSQL:

```sql
INSERT INTO public.person (email, lastname, "name")
VALUES ('test@example.com', 'Perez', 'Ana');
```

You should see this in your application console:

```java
Person inserted: test@example.com
```

Inspect events and triggers with:

```sql
select *
from db_change_event
order by id desc;

select trigger_name, event_object_schema, event_object_table
from information_schema.triggers
where event_object_table = 'person';
```
