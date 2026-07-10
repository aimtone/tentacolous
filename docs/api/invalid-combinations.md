## Invalid combinations {#invalid}

These combinations should fail when the application starts.

### Missing field

```java
@UponInserting(
    entity = Person.class,
    valueType = ValueType.BOOLEAN,
    value = "true"
)
public void invalid(Person person) {
}
```

### Missing value

```java
@UponInserting(
    entity = Person.class,
    field = "active",
    valueType = ValueType.BOOLEAN
)
public void invalid(Person person) {
}
```

### Missing valueType

```java
@UponUpdating(
    entity = Person.class,
    field = "name",
    value = "Anthony"
)
public void invalid(Person person) {
}
```

Use the complete declarative filter instead:

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

### Same operation declared twice

```java
@UponUpdating(entity = Person.class)
@TentacolousListener(entity = Person.class, action = ActionListener.UPDATE)
public void invalid(Person person) {
}
```

A method can listen to different operations, but not to the same operation twice.

### Method with more than one parameter

```java
@UponInserting(entity = Person.class)
public void invalid(Person person, String other) {
}
```

### Incompatible parameter

```java
@UponInserting(entity = Person.class)
public void invalid(String person) {
}
```
