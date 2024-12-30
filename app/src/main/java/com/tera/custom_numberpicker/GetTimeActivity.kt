package com.tera.custom_numberpicker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.tera.custom_numberpicker.databinding.ActivityGetTimeBinding

class GetTimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGetTimeBinding
    private lateinit var sp: SharedPreferences
    private var hour = 0
    private var min = 0
    private var sec = 0

    private var sounds: SoundPool? = null
    private var soundId = 0

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sp = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        hour = sp.getInt(HOUR, 0)
        min = sp.getInt(MIN, 0)
        sec = sp.getInt(SEC, 0)

        setPicker()
        setText()

        initPicker()
        initButtons()
        createSoundPool()
        loadSounds()
        setSize()
    }

    private val timeMillis = 2000L
    private val interval = 50L

    // Старт таймера
    private fun startTimer() = with(binding){
        timer?.cancel()
        var count = timeMillis / interval
        timer = object : CountDownTimer(timeMillis, interval){
            override fun onTick(timeM: Long) {
                count--
                runOnUiThread{
                    val str = count.toString()
                    tvValueH.text = str
                    npHour.value = count.toInt()
                }
                Log.d("myLogs", "Timer, count: $count")
            }
            override fun onFinish() {
                Log.d("myLogs", "Timer, СТОП")
            }
        }.start()
    }

    private fun initPicker() = with(binding) {
        npHour.setOnChangeListener { _, value ->
            hour = value
            setText()
            playAlarm()
        }
        npMin.setOnChangeListener { _, value ->
            min = value
            setText()
            playAlarm()
        }
        npSec.setOnChangeListener { _, value ->
            sec = value
            setText()
            playAlarm()
        }
    }

    private fun setPicker() = with(binding) {
        npHour.value = hour
        npMin.value = min
        npSec.value = sec
    }

    private fun setText() = with(binding) {
        var str = hour.toString()
        tvValueH.text = str
        str = min.toString()
        tvValueM.text = str
        str = sec.toString()
        tvValueS.text = str
    }

    private fun initButtons() {
        binding.bnCancel.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.bnOc.setOnClickListener {
            home()
        }

        binding.bnStart.setOnClickListener {
            startTimer()
        }
    }

    private fun home() {
        val intent = Intent(this, MainActivity ::class.java)
        intent.putExtra(KEY, true)
        intent.putExtra(HOUR, hour)
        intent.putExtra(MIN, min)
        intent.putExtra(SEC, sec)
        this.startActivity(intent)
    }

    // Signal
    private fun createSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        sounds = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build()
    }

    private fun loadSounds() {
        soundId = sounds!!.load(this, R.raw.click, 1)
    }

    private fun playAlarm() {
        sounds?.play(soundId, 0.5f, 0.5f, 1, 0, 1f)
    }

    private fun setSize() = with(binding) {
        val screen = resources.displayMetrics
        val h = screen.heightPixels
        val k = 0.6
        val hSp: Int = (h * k).toInt()
        npHour.updateLayoutParams { height = hSp }
        npMin.updateLayoutParams { height = hSp }
        npSec.updateLayoutParams { height = hSp }
    }

    override fun onStop() {
        super.onStop()
        val editor = sp.edit()
        editor.putInt(HOUR, hour)
        editor.putInt(MIN, min)
        editor.putInt(SEC, sec)
        editor.apply()
        timer?.cancel()
    }

}