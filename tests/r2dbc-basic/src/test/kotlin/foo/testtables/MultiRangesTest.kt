package foo.testtables

import de.quati.pgen.shared.PgenMultiRange
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.MultiRangesTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class MultiRangesTest {
    @BeforeTest
    fun cleanUp() = cleanUp(MultiRangesTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        val i1 = PgenMultiRange(setOf(13..47, 4..9))
        val i2 = PgenMultiRange(setOf<IntRange>())
        val l1 = PgenMultiRange(setOf(19L..49L, 3L..6L))
        val l2 = PgenMultiRange(setOf<LongRange>())

        db.suspendTransaction {
            MultiRangesTestTable.insert {
                it[MultiRangesTestTable.key] = "foo"
                it[MultiRangesTestTable.iMrange] = i1
                it[MultiRangesTestTable.lMrange] = l1
            }
            MultiRangesTestTable.selectAll().where { MultiRangesTestTable.key eq "foo" }.single()
        }.also { row ->
            row[MultiRangesTestTable.iMrange] shouldBe i1
            row[MultiRangesTestTable.iMrangeNullable] shouldBe null
            row[MultiRangesTestTable.lMrange] shouldBe l1
            row[MultiRangesTestTable.lMrangeNullable] shouldBe null
        }
        db.suspendTransaction {
            MultiRangesTestTable.insert {
                it[MultiRangesTestTable.key] = "bar"
                it[MultiRangesTestTable.iMrange] = i1
                it[MultiRangesTestTable.iMrangeNullable] = i2
                it[MultiRangesTestTable.lMrange] = l1
                it[MultiRangesTestTable.lMrangeNullable] = l2
            }
            MultiRangesTestTable.selectAll().where { MultiRangesTestTable.key eq "bar" }.single()
        }.also { row ->
            row[MultiRangesTestTable.iMrange] shouldBe i1
            row[MultiRangesTestTable.iMrangeNullable] shouldBe i2
            row[MultiRangesTestTable.lMrange] shouldBe l1
            row[MultiRangesTestTable.lMrangeNullable] shouldBe l2
        }
    }
}