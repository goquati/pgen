import io.kotest.matchers.shouldBe
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test

class SpecTest {
    @Test
    fun `test spec generation`() {
        val expectedSpec = Path("../specs/wal.yaml").readText()
        val actualSpec = SpecTest::class.java.getResource("/pgen-spec.yaml")!!.readText()
        expectedSpec shouldBe actualSpec
    }
}