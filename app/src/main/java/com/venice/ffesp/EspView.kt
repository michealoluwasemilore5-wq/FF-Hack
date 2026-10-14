package com.venice.ffesp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class EspView(context: Context) : View(context) {

    private val box = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GREEN
        isAntiAlias = true
    }
    private val label = Paint().apply {
        color = Color.YELLOW
        textSize = 34f
        isAntiAlias = true
    }

    private val mm: ByteBuffer

    init {
        val f = File(context.filesDir, "ffesp.shm")
        val raf = RandomAccessFile(f, "rw")
        raf.setLength(SHM_SIZE.toLong())
        mm = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
        mm.order(ByteOrder.LITTLE_ENDIAN)

        val helper = context.applicationInfo.nativeLibraryDir + "/libmemhelper.so"
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "$helper ${f.absolutePath}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val loop = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(loop)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = mm.getInt(0).coerceIn(0, 64)
        var off = 16
        for (i in 0 until count) {
            val x = mm.getFloat(off)
            val y = mm.getFloat(off + 4)
            val dist = mm.getFloat(off + 12)
            val hp = mm.getInt(off + 16)
            val valid = mm.getInt(off + 20)
            off += 32
            if (valid == 0) continue

            val h = (2000f / (dist + 1f)).coerceIn(60f, 400f)
            canvas.drawRect(x - 30f, y - h, x + 30f, y, box)
            canvas.drawLine(x, y - h, x, y - h - 22f, box)
            canvas.drawText(hp.toString(), x - 30f, y - h - 6f, label)
        }
    }

    companion object { const val SHM_SIZE = 16 + 64 * 32 }
}
