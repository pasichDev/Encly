package com.pasich.encly.desktop

import com.pasich.encly.desktop.platform.DroppedKind
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Only one Encly may run per data directory: two processes on the same encrypted database and the same key
 * slots file would corrupt each other's state. The lock is an OS file lock, so it is released when the process
 * ends, however it ends. A second start does not open a second window; it asks the first one, through
 * [InstanceChannel], to come to the front and to take its files, and exits.
 */
class SingleInstanceLock private constructor(private val channel: FileChannel, private val lock: FileLock) :
    AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        private const val LOCK_FILE = ".instance.lock"

        /**
         * The lock for [dataDirectory], or null if another Encly of this user holds it. An [IOException] says the lock
         * file itself cannot be opened: that is no other Encly, and the start must not be taken for a second one.
         */
        @Suppress("ReturnCount") // Each failure to get the lock is a different way to say no.
        @Throws(IOException::class)
        fun tryAcquire(dataDirectory: Path): SingleInstanceLock? {
            val channel = FileChannel.open(
                dataDirectory.resolve(LOCK_FILE),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: IOException) {
                null
            } catch (_: java.nio.channels.OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstanceLock(channel, lock)
        }
    }
}

/** The files named on the command line: what the OS passes for a double click on a backup (Windows, Linux). */
object LaunchFiles {
    fun fromArguments(args: Array<String>): List<Path> = args.mapNotNull(::accept)

    /** The path [name] names, if it is a file or folder Encly can use. */
    fun accept(name: String): Path? = try {
        Paths.get(name).takeIf { Files.exists(it) && DroppedKind.of(it) != null }
    } catch (_: InvalidPathException) {
        null
    }
}

/**
 * How a second start reaches the first: a Unix-domain socket in the private data directory (`.instance.sock`; the
 * directory is the user's alone, and the socket file is made owner-only as well). It never leaves the machine and
 * is no network. The second start sends the files it was given, the first brings its window to the front and takes
 * them as if they were dropped on it. Only paths travel, and the first checks them as it checks its own arguments
 * (see [LaunchFiles]).
 *
 * Only the process that holds [SingleInstanceLock] listens, so a socket file found at start is a leftover.
 */
class InstanceChannel private constructor(private val server: ServerSocketChannel, private val socket: Path) :
    AutoCloseable {
    private val closed = AtomicBoolean(false)

    private fun serve(onRequest: (List<Path>) -> Unit) {
        thread(name = "encly-instance-channel", isDaemon = true) {
            while (!closed.get()) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    return@thread
                }
                // Its own thread: a client that connects and says nothing must not stop the next one being served.
                thread(name = "encly-instance-request", isDaemon = true) {
                    client.use { runCatching { onRequest(read(it)) } }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        runCatching { Files.deleteIfExists(socket) }
    }

    companion object {
        private const val SOCKET_FILE = ".instance.sock"
        private const val MAX_FILES = 64
        private const val MAX_PATH_CHARS = 4_096
        private const val CONNECT_TIMEOUT_MILLIS = 1_000L

        /**
         * Starts listening in [dataDirectory]; [onRequest] gets the files of each second start (none: just come to
         * the front), on the channel's own thread. Null when no socket can be made here (a path too long for a
         * socket, a system without them): a second start then only gets a message.
         */
        fun listen(dataDirectory: Path, onRequest: (List<Path>) -> Unit): InstanceChannel? {
            val socket = dataDirectory.resolve(SOCKET_FILE)
            return try {
                Files.deleteIfExists(socket)
                val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                server.bind(UnixDomainSocketAddress.of(socket))
                runCatching {
                    Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"))
                }
                InstanceChannel(server, socket).also { it.serve(onRequest) }
            } catch (_: IOException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
        }

        /** Asks the Encly of [dataDirectory] to come to the front and take [files]; false when none answers. */
        fun send(dataDirectory: Path, files: List<Path>): Boolean {
            val socket = dataDirectory.resolve(SOCKET_FILE)
            if (!Files.exists(socket)) return false
            return try {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                    channel.connect(UnixDomainSocketAddress.of(socket))
                    val out = DataOutputStream(Channels.newOutputStream(channel))
                    out.writeInt(files.size)
                    files.forEach { out.writeUTF(it.toAbsolutePath().toString()) }
                    out.flush()
                    // The first answers once it has the request, so a dead socket is not taken for a reply.
                    awaitAcknowledgement(channel)
                }
            } catch (_: IOException) {
                false
            } catch (_: UnsupportedOperationException) {
                false
            }
        }

        private fun awaitAcknowledgement(channel: SocketChannel): Boolean {
            val deadline = System.nanoTime() + CONNECT_TIMEOUT_MILLIS * NANOS_PER_MILLI
            channel.configureBlocking(false)
            val buffer = ByteBuffer.allocate(1)
            var read = 0
            while (read == 0 && System.nanoTime() < deadline) {
                read = channel.read(buffer)
                if (read == 0) Thread.sleep(POLL_MILLIS)
            }
            return read > 0
        }

        private fun read(client: SocketChannel): List<Path> {
            val input = DataInputStream(Channels.newInputStream(client))
            val count = input.readInt().coerceIn(0, MAX_FILES)
            val files = List(count) { input.readUTF().takeIf { it.length <= MAX_PATH_CHARS } }
            client.write(ByteBuffer.wrap(byteArrayOf(1)))
            return files.mapNotNull { name -> name?.let(LaunchFiles::accept) }
        }

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val POLL_MILLIS = 10L
    }
}
