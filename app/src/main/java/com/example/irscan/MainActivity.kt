package com.example.irscan

import android.app.Activity
import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private var ir: ConsumerIrManager? = null
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var cur = 0

    private lateinit var etStart: EditText
    private lateinit var etEnd: EditText
    private lateinit var etFixed: EditText
    private lateinit var etDelay: EditText
    private lateinit var cbCmdMode: CheckBox
    private lateinit var tvCur: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnGo: Button
    private lateinit var btnStop: Button

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }
        fun field(label: String, def: String, number: Boolean = true): EditText {
            root.addView(TextView(this).apply { text = label })
            return EditText(this).apply {
                setText(def)
                if (number) inputType = InputType.TYPE_CLASS_NUMBER
                root.addView(this)
            }
        }
        etStart = field("Mulai (desimal)", "0")
        etEnd = field("Akhir (desimal)", "255")
        etFixed = field("Nilai tetap (HEX): cmd Power kalau mode Address, address kalau mode Command", "02", false)
        etDelay = field("Jeda (ms)", "500")
        cbCmdMode = CheckBox(this).apply { text = "Mode scan COMMAND (address tetap)" }
        root.addView(cbCmdMode)

        tvCur = TextView(this).apply { textSize = 40f; gravity = Gravity.CENTER; text = "--" }
        tvInfo = TextView(this).apply {
            gravity = Gravity.CENTER
            text = if (ir?.hasIrEmitter() == true) "IR blaster terdeteksi" else "IR blaster TIDAK terdeteksi"
        }
        root.addView(tvCur); root.addView(tvInfo)

        btnGo = Button(this).apply { text = "MULAI"; setOnClickListener { start() } }
        btnStop = Button(this).apply { text = "STOP (TV bereaksi!)"; isEnabled = false; setOnClickListener { stop() } }
        val back = Button(this).apply { text = "Mundur 1"; setOnClickListener { step(-1) } }
        val fwd = Button(this).apply { text = "Maju 1"; setOnClickListener { step(1) } }
        root.addView(btnGo); root.addView(btnStop)
        root.addView(LinearLayout(this).apply {
            addView(back, LinearLayout.LayoutParams(0, -2, 1f))
            addView(fwd, LinearLayout.LayoutParams(0, -2, 1f))
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun fixed(): Int = etFixed.text.toString().trim().toIntOrNull(16) ?: 0

    private fun fire(v: Int) {
        val a: Int; val c: Int
        if (cbCmdMode.isChecked) { a = fixed(); c = v } else { a = v; c = fixed() }
        ir?.transmit(38000, nec(a, c))
        tvCur.text = v.toString()
        tvInfo.text = "addr 0x%02X  cmd 0x%02X".format(a, c)
    }

    private fun start() {
        if (ir?.hasIrEmitter() != true) { tvInfo.text = "HP ini tidak punya IR blaster"; return }
        cur = etStart.text.toString().toIntOrNull() ?: 0
        running = true; btnGo.isEnabled = false; btnStop.isEnabled = true
        loop()
    }

    private fun loop() {
        if (!running) return
        val end = etEnd.text.toString().toIntOrNull() ?: 255
        if (cur > end) { stop(); tvInfo.text = "Range habis, tidak ketemu"; return }
        fire(cur)
        cur++
        h.postDelayed({ loop() }, etDelay.text.toString().toLongOrNull() ?: 500L)
    }

    private fun stop() {
        running = false; h.removeCallbacksAndMessages(null)
        btnGo.isEnabled = true; btnStop.isEnabled = false
        // reaksi TV biasanya telat 1 langkah, jadi kandidat = posisi - 2 s/d - 1
        cur = (cur - 2).coerceAtLeast(0)
        tvInfo.text = "Berhenti. Tekan Maju 1 sampai TV bereaksi lagi, itu nilainya."
    }

    private fun step(d: Int) {
        if (running) return
        cur = (cur + d).coerceIn(0, 255)
        fire(cur)
    }

    // NEC standar: leader 9000/4500, 32 bit (addr, ~addr, cmd, ~cmd) LSB dulu, stop bit 562
    private fun nec(a: Int, c: Int): IntArray {
        val l = mutableListOf(9000, 4500)
        for (b in intArrayOf(a and 255, a.inv() and 255, c and 255, c.inv() and 255)) {
            for (i in 0..7) {
                l.add(562)
                l.add(if (((b shr i) and 1) == 1) 1687 else 562)
            }
        }
        l.add(562)
        return l.toIntArray()
    }

    override fun onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null) }
}
