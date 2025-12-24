package de.quati.pgen.plugin

import de.quati.pgen.plugin.intern.model.config.Config

public enum class CRUD {
    CREATE, READ, READ_ALL, UPDATE, DELETE;

    internal val intern get() = when (this) {
        CREATE -> Config.Oas.CRUD.CREATE
        READ -> Config.Oas.CRUD.READ
        READ_ALL -> Config.Oas.CRUD.READ_ALL
        UPDATE -> Config.Oas.CRUD.UPDATE
        DELETE -> Config.Oas.CRUD.DELETE
    }
}
