package com.github.ericytsang.lib.simplepipestream

import java.io.InputStream
import kotlin.concurrent.withLock

/**
 * Created by surpl on 10/28/2016.
 */
class SimplePipedInputStream(internal val src:SimplePipedOutputStream):InputStream()
{
    override fun available():Int = src.buffer.size

    private var isEof = false

    override fun close()
    {
        src.close()
    }

    override fun read():Int
    {
        val data = ByteArray(1)
        val result = read(data)

        when (result)
        {
        // if EOF, return -1 as specified by java docs
            -1 -> return result

        // if data was actually read, return the read data
            1 -> return data[0].toInt().and(0xFF)

        // throw an exception in all other cases
            else -> throw RuntimeException("unhandled case in when statement!")
        }
    }
    override fun read(b:ByteArray):Int = read(b,0,b.size)
    override fun read(b:ByteArray,off:Int,len:Int):Int = src.mutex.withLock()
    {
        // if it's not EOF...
        val result = if (!isEof)
        {
            // wait for bytes to become available for reading
            while (src.buffer.size == 0 && !src.isClosed)
            {
                src.unblockOnWriteOrEof.await()
            }

            // if there are readable bytes that available for reading...
            if (available() > 0)
            {
                val availableBytesToTransfer = Math.min(len,available())

                // transfer them to the client's buffer
                for (i in 0..availableBytesToTransfer-1)
                {
                    b[off+i] = src.buffer.take().toByte()
                }

                availableBytesToTransfer
            }

            // ...else return -1 indicating EOF
            else
            {
                require(available() == 0 && src.isClosed)
                isEof = true
                -1
            }
        }
        else
        {
            -1
        }

        // notify read
        src.unblockOnRead.signal()

        result
    }
}
