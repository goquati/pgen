package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.shared.RegClass
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.RegclassTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class RegclassTest {
    @BeforeTest
    fun cleanUp() = cleanUp(RegclassTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        val d1 = RegClass("public.regclass_test_table")
        val d2 = RegClass("public.duration_test_table")
        db.suspendTransaction {
            RegclassTestTable.insert {
                it[RegclassTestTable.key] = "foo"
                it[RegclassTestTable.table] = d1
            }
            RegclassTestTable.selectAll().where { RegclassTestTable.key eq "foo" }.single()
        }.also { row ->
            row[RegclassTestTable.table] shouldBe d1
            row[RegclassTestTable.tableNullable] shouldBe null
        }
        db.suspendTransaction {
            RegclassTestTable.insert {
                it[RegclassTestTable.key] = "bar"
                it[RegclassTestTable.table] = d1
                it[RegclassTestTable.tableNullable] = d2
            }
            RegclassTestTable.selectAll().where { RegclassTestTable.key eq "bar" }.single()
        }.also { row ->
            row[RegclassTestTable.table] shouldBe d1
            row[RegclassTestTable.tableNullable] shouldBe d2
        }
    }
}