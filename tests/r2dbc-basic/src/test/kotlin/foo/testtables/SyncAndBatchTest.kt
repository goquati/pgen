package foo.testtables

import de.quati.pgen.r2dbc.util.batchUpdate
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.r2dbc.util.sync
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.SyncTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.BeforeTest
import kotlin.test.Test

class SyncAndBatchTest {
    @BeforeTest
    fun cleanUp() = cleanUp(SyncTestTable)

    @Test
    fun `test sync statement`(): Unit = runBlocking {
        suspend fun loadData() = db.suspendTransaction(readOnly = false) {
            SyncTestTable.selectAll().toList()
        }.groupBy({ it[SyncTestTable.groupId] }, { it[SyncTestTable.name] })
            .mapValues { it.value.toSet() }

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to 47,
                data = listOf(1, 2, 3),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(47 to setOf("1", "2", "3"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to 3,
                data = listOf(2, 3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(47 to setOf("1", "2", "3"), 3 to setOf("2", "3", "4"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to 47,
                data = listOf(3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(47 to setOf("3", "4"), 3 to setOf("2", "3", "4"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to 3,
                data = listOf<Int>(),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(47 to setOf("3", "4"))
    }

    @Test
    fun `test batchUpdate`(): Unit = runBlocking {
        val d1 = mapOf("foo" to 1, "bar" to 2, "baz" to 3, "qux" to 4)
        val d2 = mapOf("foo" to 10, "bar" to 2, "baz" to 30, "qux" to 40)
        db.suspendTransaction {
            SyncTestTable.batchInsert(d1.entries) { (k, v) ->
                this[SyncTestTable.name] = k
                this[SyncTestTable.groupId] = v
            }
            SyncTestTable.selectAll().toList().associate { it[SyncTestTable.name] to it[SyncTestTable.groupId] }
        } shouldBe d1

        db.suspendTransaction {
            SyncTestTable.batchUpdate(
                keys = listOf(SyncTestTable.name),
                data = d2.entries.filter { it.key != "bar" },
            ) {
                this[SyncTestTable.name] = it.key
                this[SyncTestTable.groupId] = it.value
            }
            SyncTestTable.selectAll().toList().associate { it[SyncTestTable.name] to it[SyncTestTable.groupId] }
        } shouldBe d2
    }
}