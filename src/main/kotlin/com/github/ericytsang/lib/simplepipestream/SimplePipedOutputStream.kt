package com.github.ericytsang.lib.simplepipestream

import com.github.ericytsang.lib.abstractstream.AbstractOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SimplePipedOutputStream(val bufferSize:Int = SimplePipedOutputStream.DEFAULT_BUFFER_SIZE):AbstractOutputStream()
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

    override fun doWrite(b:Int) = mutex.withLock()
    {
        // wait for room to become available
        while (buffer.remainingCapacity() == 0)
        {
            unblockOnRead.await()
        }

        // write the data
        check(!isClosed)
        buffer.put(b.and(0xFF))

        // notify write
        unblockOnWriteOrEof.signal()
    }

    private var isEof = false

    internal fun doRead(b:ByteArray,off:Int,len:Int):Int = mutex.withLock()
    {
        // if it's not EOF...
        val result = if (!isEof)
        {
            // wait for bytes to become available for reading
            while (buffer.size == 0 && !isClosed)
            {
                unblockOnWriteOrEof.await()
            }

            // if there are readable bytes that available for reading...
            if (buffer.size > 0)
            {
                val availableBytesToTransfer = Math.min(len,buffer.size)

                // transfer them to the client's buffer
                for (i in 0..availableBytesToTransfer-1)
                {
                    b[off+i] = buffer.take().toByte()
                }

                availableBytesToTransfer
            }

            // ...else return -1 indicating EOF
            else
            {
                require(buffer.size == 0 && isClosed)
                isEof = true
                -1
            }
        }
        else
        {
            -1
        }

        // notify read
        unblockOnRead.signal()

        result
    }

    override fun oneShotClose() = mutex.withLock()
    {
        // notify write
        unblockOnWriteOrEof.signal()
    }
}
