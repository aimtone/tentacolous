package io.github.aimtone.tentacolous.schema;

import java.util.Locale;

public class MariaDbDialect extends MySqlDialect {
    @Override
    public boolean supports(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("mariadb");
    }
}
