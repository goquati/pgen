package de.quati.pgen.tests.jdbc.basic.shared

import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@JvmInline
value class UserId(val value: Uuid)
