package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.AutoIncrementTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import kotlin.test.BeforeTest
import kotlin.test.Test

class AutoIncrementTest {
    @BeforeTest
    fun cleanUp() = cleanUp(AutoIncrementTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        db.suspendTransaction {
            AutoIncrementTestTable.batchInsert(
                data = listOf("a", "b", "c"),
            ) {
                this[AutoIncrementTestTable.key] = it
            }.map(AutoIncrementTestTable.Entity::create)
                .toList()
        }.also { rows ->
            val i = rows.first().idInt
            rows shouldBe listOf(
                AutoIncrementTestTable.Entity("a", i + 0, i + 0L),
                AutoIncrementTestTable.Entity("b", i + 1, i + 1L),
                AutoIncrementTestTable.Entity("c", i + 2, i + 2L),
            )
        }
    }
}