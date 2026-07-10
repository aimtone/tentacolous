## Entidad de ejemplo {#entity}

Tentacolous usa la entidad para saber que tabla de base de datos debe escuchar.

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

Si la entidad tiene `@Table(name = "person")`, Tentacolous escucha la tabla `person`. Si no tiene `@Table`, Tentacolous infiere el nombre de tabla desde el nombre de clase.

| Entidad | Tabla inferida |
| --- | --- |
| `Person` | `person` |
| `UserAccount` | `user_account` |
| `PaymentTransaction` | `payment_transaction` |

Por eso las anotaciones no tienen un parametro `table`: la entidad seleccionada ya representa la tabla.
