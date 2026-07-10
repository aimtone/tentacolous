## Example entity {#entity}

Tentacolous uses the entity to know which database table it should listen to.

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

    public Person() {
    }

    // getters and setters
}
```

If the entity has `@Table(name = "person")`, Tentacolous listens to the `person` table. If it does not have `@Table`, Tentacolous infers the table name from the class name.

| Entity | Inferred table |
| --- | --- |
| `Person` | `person` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

That is why the annotations do not have a `table` parameter: the selected entity already represents the table.
