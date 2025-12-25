package de.quati.pgen.plugin.intern.service

import com.squareup.kotlinpoet.FileSpec
import java.io.Closeable
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

internal class DirectorySyncService(
    private val directory: Path,
    private val silent: Boolean = false,
) : Closeable {
    private var filesCreated = mutableSetOf<Path>()
    private var filesUpdated = mutableSetOf<Path>()
    private var filesUnchanged = mutableSetOf<Path>()
    private var filesDeleted = mutableSetOf<Path>()

    fun sync(relativePath: String, content: String) = sync(
        path = directory.resolve(relativePath).absolute(),
        content = content,
    )

    fun sync(
        relativePath: String,
        content: FileSpec,
    ) = sync(relativePath = relativePath, content = content.toString())

    fun cleanup() {
        @OptIn(ExperimentalPathApi::class)
        val actualFiles = directory.walk().map { it.absolute() }.toSet()
        val filesToDelete = actualFiles - (filesCreated + filesUpdated + filesUnchanged)
        filesToDelete.forEach { it.deleteExisting() }
        filesDeleted += filesToDelete
    }

    private fun checkFilePath(path: Path) {
        val inDirectory = null != path.absolute().relativeToOrNull(directory.absolute())
        if (!inDirectory) error("path '$path' is not in output directory '$directory'")
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

    override fun close() {
        if (!silent) {
            fun Set<*>.printSize() = size.toString().padStart(3)
            println("#files unchanged = ${filesUnchanged.printSize()}")
            println("#files created   = ${filesCreated.printSize()}")
            println("#files updated   = ${filesUpdated.printSize()}")
            println("#files deleted   = ${filesDeleted.printSize()}")
        }
    }

    companion object {
        fun directorySync(
            directory: Path,
            silent: Boolean = false,
            block: DirectorySyncService.() -> Unit,
        ) = DirectorySyncService(directory, silent = silent).use(block)

        fun sync(
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