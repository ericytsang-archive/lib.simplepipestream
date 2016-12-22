package com.github.ericytsang.lib.simplepipestream

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SimplePipedOutputStream(val bufferSize:Int = SimplePipedOutputStream.DEFAULT_BUFFER_SIZE):OutputStream()
{
    companion object
    {
        const val DEFAULT_BUFFER_SIZE = 8196
        const val VARIABLE_BUFFER_SIZE = -1
    }

    internal val buffer:BlockingQueue<Int> = when (bufferSize)
    {
        VARIABLE_BUFFER_SIZE -> LinkedBlockingQueue()
        else -> ArrayBlockingQueue(bufferSize)
    }

    internal val mutex = ReentrantLock()
    internal val unblockOnRead = mutex.newCondition()
    internal val unblockOnWriteOrEof = mutex.newCondition()

    override fun write(b:Int) = mutex.withLock()
    {
        // return early if already closed
        if (isClosed) throw IOException("stream closed")

        // wait for room to become available
        while (buffer.remainingCapacity() == 0)
        {
            unblockOnRead.await()
        }

        // write the data
        buffer.put(b.and(0xFF))

        // notify write
        unblockOnWriteOrEof.signal()
    }

    override fun close() = mutex.withLock()
    {
        // return early if already closed
        if (isClosed) return@withLock
        isClosed = true

        // notify write
        unblockOnWriteOrEof.signal()
    }

    internal var isClosed = false
}
