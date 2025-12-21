package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.CitextTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class CiTextTest {
    @BeforeTest
    fun cleanUp() = cleanUp(CitextTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        db.suspendTransaction {
            CitextTestTable.insert {
                it[CitextTestTable.key] = "foobar"
                it[CitextTestTable.text] = "FooBar"
            }
            CitextTestTable.selectAll().where { CitextTestTable.text eq "foobar" }.single()
        }.also { row ->
            row[CitextTestTable.key] shouldBe "foobar"
            row[CitextTestTable.text] shouldBe "FooBar"
            row[CitextTestTable.textNullable] shouldBe null
        }
        db.suspendTransaction {
            CitextTestTable.insert {
                it[CitextTestTable.key] = "hello world"
                it[CitextTestTable.text] = "hello world"
                it[CitextTestTable.textNullable] = "hello WORLD"
            }
            CitextTestTable.selectAll().where { CitextTestTable.textNullable eq "hello world" }.single()
        }.also { row ->
            row[CitextTestTable.key] shouldBe "hello world"
            row[CitextTestTable.text] shouldBe "hello world"
            row[CitextTestTable.textNullable] shouldBe "hello WORLD"
        }
    }
}