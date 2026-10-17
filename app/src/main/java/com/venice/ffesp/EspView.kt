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
        style = Paint.Style.STROKE; strokeWidth = 4f
        color = Color.GREEN; isAntiAlias = true
    }
    private val label = Paint().apply {
        color = Color.YELLOW; textSize = 30f; isAntiAlias = true
    }
    private val dot = Paint().apply { isAntiAlias = true }

    private val mm: ByteBuffer
    private val logFile = File(context.filesDir, "su.log")

    init {
        val f = File(context.filesDir, "ffesp.shm")
        val raf = RandomAccessFile(f, "rw")
        raf.setLength(SHM_SIZE.toLong())
        mm = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
        mm.order(ByteOrder.LITTLE_ENDIAN)

        val helper = context.applicationInfo.nativeLibraryDir + "/libmemhelper.so"
        val cmd = "exec > ${logFile.absolutePath} 2>&1; " +
                  "echo ROOTCHECK; id; " +
                  "echo HELPER=$helper; " +
                  "ls -l $helper; " +
                  "cp $helper /data/local/tmp/ffhelper; " +
                  "chmod 755 /data/local/tmp/ffhelper; " +
                  "echo RUNNING; " +
                  "/data/local/tmp/ffhelper ${f.absolutePath}"
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        } catch (e: Exception) { e.printStackTrace() }
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

        val magic = mm.getInt(4)
        dot.color = when (magic) {
            0      -> Color.RED
            0xAAAA -> Color.rgb(255,165,0)
            0xBBBB -> Color.BLUE
            else   -> Color.GREEN
        }
        canvas.drawCircle(60f, 60f, 24f, dot)

        val dbg = "st=%x pid=%d base=%08x n=%d".format(
            magic, mm.getInt(DBG_PID), mm.getInt(DBG_BASE), mm.getInt(DBG_N))
        canvas.drawText(dbg, 30f, 130f, label)

        // show su log
        val log = try {
            if (logFile.exists()) logFile.readText().take(300) else "NO LOG - su never ran"
        } catch (e: Exception) { "log err" }
        var ly = 180f
        for (line in log.split("\n")) {
            canvas.drawText(line, 30f, ly, label)
            ly += 38f
        }

        val count = mm.getInt(0).coerceIn(0, 64)
        var off = HEADER
        for (i in 0 until count) {
            val x = mm.getFloat(off)
            val y = mm.getFloat(off + 4)
            val dist = mm.getFloat(off + 12)
            val hp = mm.getInt(off + 16)
            val valid = mm.getInt(off + 20)
            off += STRIDE
            if (valid == 0) continue
            val h = (2000f / (dist + 1f)).coerceIn(60f, 400f)
            canvas.drawRect(x - 30f, y - h, x + 30f, y, box)
            canvas.drawLine(x, y - h, x, y - h - 22f, box)
            canvas.drawText(hp.toString(), x - 30f, y - h - 6f, label)
        }
    }

    companion object {
        const val HEADER = 20
        const val STRIDE = 24
        const val DBG_BASE = 1556
        const val DBG_MGR = 1560
        const val DBG_LOCAL = 1564
        const val DBG_N = 1568
        const val DBG_OUT = 1572
        const val DBG_PID = 1576
        const val SHM_SIZE = 1600
    }
}
