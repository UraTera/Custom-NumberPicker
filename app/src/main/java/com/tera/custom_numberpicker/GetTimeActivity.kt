package com.tera.custom_numberpicker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.tera.custom_numberpicker.databinding.ActivityGetTimeBinding

class GetTimeActivity : AppCompatActivity() {

    companion object {
        const val KEY_SOUND = "key_sound"
    }

    private lateinit var binding: ActivityGetTimeBinding
    private lateinit var sp: SharedPreferences
    private var hour = 0
    private var min = 0
    private var sec = 0
    private var volume = 0.1f
    private var keySound = true

    private var sounds: SoundPool? = null
    private var soundId = 0

    private var timer: CountDownTimer? = null
    private val timeMillis = 500L
    private val interval = 20L
    private var scrollSize = 80
    private var handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sp = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        hour = sp.getInt(HOUR, 0)
        min = sp.getInt(MIN, 0)
        sec = sp.getInt(SEC, 0)
        keySound = sp.getBoolean(KEY_SOUND, true)

        setPicker()
        setText()
        setIconButton()
        initPicker()
        initButtons()

        createSoundPool()
        loadSounds()

        handler.postDelayed({
            startTimer()
        }, 500)
    }

    private fun setPicker() = with(binding) {
        npHour.value = hour
        npMin.value = min
        npSec.value = sec
    }

    private fun setText() = with(binding) {
        var str = hour.toString()
        tvHour.text = str
        str = min.toString()
        tvMin.text = str
        str = sec.toString()
        tvSec.text = str
    }

    private fun setIconButton() = with(binding) {
        if (keySound)
            bnSound.setImageResource(R.drawable.ic_volume_up_gray)
        else
            bnSound.setImageResource(R.drawable.ic_volume_off_gray)
    }

    private fun initPicker() = with(binding) {
        npHour.setOnChangeListener {
                hour = it
                setText()
                if (keySound) playAlarm()
        }
        npMin.setOnChangeListener {
                min = it
                setText()
            if (keySound) playAlarm()
        }
        npSec.setOnChangeListener {
                sec = it
                setText()
            if (keySound) playAlarm()
        }
    }

    private fun initButtons() = with(binding) {
        bnCancel.setOnClickListener {
            startActivity(Intent(this@GetTimeActivity, MainActivity::class.java))
        }
        bnOc.setOnClickListener {
            home()
        }
        bnSound.setOnClickListener {
            keySound = !keySound
            setIconButton()
        }
    }

    private fun startTimer() = with(binding) {
        volume = 0.3f
        timer?.cancel()
        timer = object : CountDownTimer(timeMillis, interval) {
            override fun onTick(timeM: Long) {
                if (keySound) playAlarm()
                runOnUiThread {
                    npHour.scroll = scrollSize
                    npMin.scroll = -scrollSize
                    npSec.scroll = scrollSize
                }
            }
            override fun onFinish() {
                handler.postDelayed({
                    volume = 0.1f
                    runOnUiThread {
                        npHour.value = hour
                        npMin.value = min
                        npSec.value = sec
                    }
                }, 200)
            }
        }.start()
    }

    private fun home() {
        val intent = Intent(this, MainActivity::class.java)
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
        sounds?.play(soundId, volume, volume, 1, 0, 1f)
    }

    override fun onStop() {
        super.onStop()
        val editor = sp.edit()
        editor.putInt(HOUR, hour)
        editor.putInt(MIN, min)
        editor.putInt(SEC, sec)
        editor.putBoolean(KEY_SOUND, keySound)
        editor.apply()
        timer?.cancel()
    }

}