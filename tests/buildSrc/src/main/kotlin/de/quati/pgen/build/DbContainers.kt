package de.quati.pgen.build

import com.github.dockerjava.api.model.PortBinding
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object DbContainers {
    private val containers = ConcurrentHashMap<Int, PostgreSQLContainer<*>>()
    private fun removeExistingContainer(name: String) {
        val client = DockerClientFactory.instance().client()
        val containerId = client.listContainersCmd()
            .withShowAll(true)
            .exec()
            .find { it?.names?.contains("/$name") == true }
            ?.id ?: return
        client.removeContainerCmd(containerId)
            .withForce(true)
            .exec()
    }

    private fun waitUntilSqlReady(config: DbTestcontainerConfig) {
        val deadline = System.currentTimeMillis() + 60_000
        while (true) {
            try {
                DriverManager.getConnection(config.jdbcUrl, "postgres", "postgres").use { conn ->
                    conn.createStatement().use { it.execute("select 1") }
                }
                println("PostgreSQL at ${config.jdbcUrl} is ready for SQL")
                return
            } catch (_: Exception) {
                if (System.currentTimeMillis() > deadline)
                    error("PostgreSQL did not become ready for SQL queries (url=${config.jdbcUrl})")
                Thread.sleep(250)
            }
        }
    }

    fun start(config: DbTestcontainerConfig) = containers.compute(config.port) { _, _ ->
        removeExistingContainer(config.containerName)
        PostgreSQLContainer(config.type.image).apply {
            withCreateContainerCmdModifier { cmd ->
                cmd.withName(config.containerName)
                cmd.withLabels((cmd.labels?.toMutableMap() ?: mutableMapOf()).apply {
                    put("com.docker.compose.project", "pgen-dev")
                })
                cmd.hostConfig!!.withPortBindings(PortBinding.parse("${config.port}:5432"))
            }
            withDatabaseName("postgres")
            withUsername("postgres")
            withPassword("postgres")
            withStartupTimeout(Duration.ofMinutes(1))
            start()
        }
    }.also {
        waitUntilSqlReady(config)
    }

    fun stopAll() {
        containers.values.forEach { runCatching { it.stop() } }
        containers.clear()
    }
}
