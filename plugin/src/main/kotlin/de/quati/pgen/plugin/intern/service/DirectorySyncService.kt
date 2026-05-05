package de.quati.pgen.plugin.intern.service

import com.squareup.kotlinpoet.FileSpec
import org.gradle.api.logging.Logger
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.walk
import kotlin.io.path.writeText


internal open class DirectorySyncService(
    private val outDir: Path,
    private val name: String,
    private val logger: Logger,
) {
    private var filesCreated = mutableSetOf<Path>()
    private var filesUpdated = mutableSetOf<Path>()
    private var filesUnchanged = mutableSetOf<Path>()
    private var filesDeleted = mutableSetOf<Path>()

    fun sync(relativePath: String, content: String) = sync(
        path = outDir.resolve(relativePath).absolute(),
        content = content,
    )

    fun sync(
        relativePath: String,
        content: FileSpec,
    ) = sync(relativePath = relativePath, content = content.toString())

    private fun cleanup() {
        @OptIn(ExperimentalPathApi::class)
        val actualFiles = outDir.walk().map { it.absolute() }.toSet()
        val filesToDelete = actualFiles - (filesCreated + filesUpdated + filesUnchanged)
        filesToDelete.forEach { it.deleteExisting() }
        filesDeleted += filesToDelete

        fun Set<*>.printSize() = size.toString().padStart(3)
        logger.info("package '$name' synced:")
        logger.info("   #files unchanged = ${filesUnchanged.printSize()}")
        logger.info("   #files created   = ${filesCreated.printSize()}")
        logger.info("   #files updated   = ${filesUpdated.printSize()}")
        logger.info("   #files deleted   = ${filesDeleted.printSize()}")
        logger.info("")
    }

    private fun checkFilePath(path: Path) {
        val inDirectory = null != path.absolute().relativeToOrNull(outDir.absolute())
        if (!inDirectory) error("path '$path' is not in output directory '$outDir'")
    }

    private fun sync(path: Path, content: String) {
        checkFilePath(path)
        val type = DirectorySyncService.sync(path = path, content = content)
        when (type) {
            FileSyncType.UNCHANGED -> filesUnchanged.add(path)
            FileSyncType.UPDATED -> filesUpdated.add(path)
            FileSyncType.CREATED -> filesCreated.add(path)
        }
    }

    enum class FileSyncType {
        UNCHANGED, UPDATED, CREATED
    }

    fun <T> useWith(block: DirectorySyncService.() -> T): T = try {
        val result = block(this)
        cleanup()
        result
    } finally {
    }

    fun <T> use(block: (DirectorySyncService) -> T): T = try {
        val result = block(this)
        cleanup()
        result
    } finally {
    }

    companion object {
        private fun sync(
            path: Path,
            content: String,
        ): FileSyncType {
            if (!path.isAbsolute) return sync(path = path.absolute(), content = content)
            return if (path.exists()) {
                if (path.readText() == content) {
                    FileSyncType.UNCHANGED
                } else {
                    path.writeText(content)
                    FileSyncType.UPDATED
                }
            } else {
                path.parent.createDirectories()
                path.writeText(content)
                FileSyncType.CREATED
            }
        }
    }
}