package com.github.ericytsang.lib.simplepipestream

import com.github.ericytsang.lib.abstractstream.AbstractInputStream

/**
 * Created by surpl on 10/28/2016.
 */
class SimplePipedInputStream(internal val src:SimplePipedOutputStream):AbstractInputStream()
{
    override fun available():Int = src.buffer.size

    override fun oneShotClose()
    {
        src.close()
    }

    override fun doRead(b:ByteArray,off:Int,len:Int):Int = src.doRead(b,off,len)
}
