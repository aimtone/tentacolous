## Combinaciones invalidas {#invalid}

Estas combinaciones deberian fallar cuando inicia la aplicacion.

### Falta field

```java
@UponInserting(
    entity = Person.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Person person) {
}
```

### Falta value

```java
@UponInserting(
    entity = Person.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Person person) {
}
```

### Falta valueType

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    value = "Anthony"
)
public void invalid(Person person) {
}
```

Usa el filtro declarativo completo:

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    valueType = ValueType.STRING,
    value = "Anthony"
)
public void valid(Person person) {
}
```

### Misma operacion declarada dos veces

```java
@UponUpdating(entity = Person.class)
@TentacolousListener(entity = Person.class, action = ActionListener.UPDATE)
public void invalid(Person person) {
}
```

Un metodo puede escuchar operaciones distintas, pero no la misma operacion dos veces.

### Metodo con mas de un parametro

```java
@UponInserting(entity = Person.class)
public void invalid(Person person, String other) {
}
```

### Parametro incompatible

```java
@UponInserting(entity = Person.class)
public void invalid(String person) {
}
```
