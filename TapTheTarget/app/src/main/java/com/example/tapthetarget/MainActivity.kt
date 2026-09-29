package com.example.tapthetarget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    // ==== 1. Data Permainan Dasar ====
    private var skor = 0
    private val durasiPermainan = 30_000L
    private var sisaWaktuDetik = 30
    private var timer: CountDownTimer? = null
    private var sedangDijeda = false
    private val acak = Random()

    // ==== 2. PENGEMBANGAN: Tingkat Kesulitan ====
    private var jumlahKlik = 0

    // ==== 3. PENGEMBANGAN: Menghilang Sesaat ====
    private val handlerDelay = Handler(Looper.getMainLooper())
    private val runnableTimeout = Runnable {
        // Hilangkan target selama 1 detik jika tidak ditekan dalam waktu tertentu
        if (!sedangDijeda && sisaWaktuDetik > 0) {
            imgTarget.visibility = View.INVISIBLE
            handlerDelay.postDelayed({
                if (!sedangDijeda && sisaWaktuDetik > 0) {
                    imgTarget.visibility = View.VISIBLE
                    pindahkanTarget()
                }
            }, 1000L) // Waktu target menghilang
        }
    }

    // ==== 4. PENGEMBANGAN: Efek Suara ====
    private lateinit var toneGen: ToneGenerator

    // ==== 5. PENGEMBANGAN: Skor Tertinggi ====
    private lateinit var sharedPrefs: SharedPreferences

    // ==== View ====
    private lateinit var frameGame: FrameLayout
    private lateinit var imgBackground: ImageView
    private lateinit var imgTarget: ImageView
    private lateinit var tvSkor: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var btnPause: Button
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var tvSkorJeda: TextView
    private lateinit var tvSkorAkhir: TextView
    private lateinit var tvSkorTertinggi: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi Fitur Pengembangan
        sharedPrefs = getSharedPreferences("TargetGamePrefs", Context.MODE_PRIVATE)
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        // Hubungkan View
        frameGame = findViewById(R.id.frameGame)
        imgBackground = findViewById(R.id.imgBackground)
        imgTarget = findViewById(R.id.imgTarget)
        tvSkor = findViewById(R.id.tvSkor)
        tvWaktu = findViewById(R.id.tvWaktu)
        btnPause = findViewById(R.id.btnPause)
        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        tvSkorJeda = findViewById(R.id.tvSkorJeda)
        tvSkorAkhir = findViewById(R.id.tvSkorAkhir)
        tvSkorTertinggi = findViewById(R.id.tvSkorTertinggi)

        // Set Listener
        imgTarget.setOnClickListener { targetDitekan() }
        btnPause.setOnClickListener { jedaPermainan() }
        findViewById<Button>(R.id.btnLanjutkan).setOnClickListener { lanjutkanPermainan() }
        findViewById<Button>(R.id.btnUlangi).setOnClickListener { mulaiPermainan() }
        findViewById<Button>(R.id.btnMainLagi).setOnClickListener { mulaiPermainan() }

        // ==== 6. PENGEMBANGAN: Tahan Saat Diputar (Rotate) ====
        if (savedInstanceState != null) {
            // Pulihkan data setelah layar diputar
            skor = savedInstanceState.getInt("SKOR", 0)
            sisaWaktuDetik = savedInstanceState.getInt("WAKTU", 30)
            jumlahKlik = savedInstanceState.getInt("KLIK", 0)

            tvSkor.text = getString(R.string.label_skor, skor)
            tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            ubahUkuranTarget(max(40, 72 - (jumlahKlik / 5) * 5))

            // Paksa masuk mode jeda agar pemain siap saat rotate selesai
            jedaPermainan()
        } else {
            mulaiPermainan()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Simpan data permainan saat layar ter-rotate
        outState.putInt("SKOR", skor)
        outState.putInt("WAKTU", sisaWaktuDetik)
        outState.putInt("KLIK", jumlahKlik)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        handlerDelay.removeCallbacksAndMessages(null)
        toneGen.release() // Bebaskan memori suara
    }

    private fun mulaiPermainan() {
        skor = 0
        sisaWaktuDetik = 30
        jumlahKlik = 0
        sedangDijeda = false

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)

        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        overlayGameOver.alpha = 1f

        imgTarget.visibility = View.VISIBLE
        imgTarget.isEnabled = true
        btnPause.isEnabled = true

        ubahUkuranTarget(72) // Reset target ke ukuran awal

        // ==== 7. PENGEMBANGAN: Pola Latar Berbeda ====
        // Memberikan tint filter ke background agar warnanya berganti secara acak
        val warnaLatar = arrayOf(0xFF16341F.toInt(), 0xFF1F1634.toInt(), 0xFF341616.toInt(), 0xFF343116.toInt())
        imgBackground.setColorFilter(warnaLatar.random(), PorterDuff.Mode.MULTIPLY)

        frameGame.post { pindahkanTarget() }
        jalankanTimer(durasiPermainan)
    }

    private fun ubahUkuranTarget(ukuranDp: Int) {
        val px = (ukuranDp * resources.displayMetrics.density).toInt()
        imgTarget.layoutParams.width = px
        imgTarget.layoutParams.height = px
        imgTarget.requestLayout()
    }

    private fun pindahkanTarget() {
        val lebarArea = frameGame.width
        val tinggiArea = frameGame.height
        if (lebarArea == 0 || tinggiArea == 0) return

        val jarakAman = 24
        val maxX = lebarArea - imgTarget.layoutParams.width - (jarakAman * 2)
        val maxY = tinggiArea - imgTarget.layoutParams.height - (jarakAman * 2)
        if (maxX <= 0 || maxY <= 0) return

        imgTarget.x = (jarakAman + acak.nextInt(maxX)).toFloat()
        imgTarget.y = (jarakAman + acak.nextInt(maxY)).toFloat()

        // ==== PENGEMBANGAN: Logik Menghilang Sesaat ====
        // Jika target tidak ditekan dalam 2 detik, jalankan timeout
        handlerDelay.removeCallbacks(runnableTimeout)
        handlerDelay.postDelayed(runnableTimeout, 2000L)
    }

    private fun targetDitekan() {
        if (sedangDijeda) return

        // ==== PENGEMBANGAN: Efek Suara Saat Ditekan ====
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)

        // ==== PENGEMBANGAN: Tingkat Kesulitan ====
        skor += 20 // Skor jadi 20 per klik
        jumlahKlik++
        tvSkor.text = getString(R.string.label_skor, skor)

        // Perkecil target setiap 5 klik, hingga batas minimum 40dp
        val ukuranBaru = max(40, 72 - (jumlahKlik / 5) * 5)
        ubahUkuranTarget(ukuranBaru)

        imgTarget.animate()
            .scaleX(0.6f).scaleY(0.6f).setDuration(70)
            .withEndAction {
                imgTarget.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }
            .start()

        pindahkanTarget()
    }

    private fun jalankanTimer(durasi: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durasi, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = (millisUntilFinished / 1000).toInt()
                tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            }

            override fun onFinish() {
                sisaWaktuDetik = 0
                tvWaktu.text = getString(R.string.label_waktu, 0)
                gameOver()
            }
        }.start()
    }

    private fun jedaPermainan() {
        if (sedangDijeda || overlayGameOver.visibility == View.VISIBLE) return

        sedangDijeda = true
        timer?.cancel()
        handlerDelay.removeCallbacks(runnableTimeout) // Hentikan timeout menghilangkan target
        imgTarget.isEnabled = false

        tvSkorJeda.text = skor.toString()
        overlayPause.alpha = 0f
        overlayPause.visibility = View.VISIBLE
        overlayPause.animate().alpha(1f).setDuration(200).start()
    }

    private fun lanjutkanPermainan() {
        if (!sedangDijeda) return

        sedangDijeda = false
        overlayPause.visibility = View.GONE
        imgTarget.isEnabled = true

        handlerDelay.postDelayed(runnableTimeout, 2000L) // Mulai ulang timeout target
        jalankanTimer(sisaWaktuDetik * 1000L)
    }

    private fun gameOver() {
        timer?.cancel()
        handlerDelay.removeCallbacksAndMessages(null)
        sedangDijeda = false

        imgTarget.isEnabled = false
        imgTarget.visibility = View.GONE
        btnPause.isEnabled = false
        overlayPause.visibility = View.GONE

        tvSkorAkhir.text = skor.toString()

        // ==== PENGEMBANGAN: Simpan Skor Tertinggi (SharedPreferences) ====
        var highScore = sharedPrefs.getInt("HIGH_SCORE", 0)
        if (skor > highScore) {
            highScore = skor
            sharedPrefs.edit().putInt("HIGH_SCORE", highScore).apply()
        }
        tvSkorTertinggi.text = highScore.toString()

        overlayGameOver.alpha = 0f
        overlayGameOver.visibility = View.VISIBLE
        overlayGameOver.animate().alpha(1f).setDuration(350).start()
    }
}