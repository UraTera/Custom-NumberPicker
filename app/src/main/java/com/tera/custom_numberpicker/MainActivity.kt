package com.tera.custom_numberpicker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.tera.custom_numberpicker.databinding.ActivityMainBinding

const val HOUR = "hour"
const val MIN = "min"
const val SEC = "sec"
const val KEY = "key"
const val SETTINGS = "settings"


class MainActivity : AppCompatActivity() {

    companion object {
        const val VALUE_1 = "value_1"
        const val VALUE_2 = "value_2"
        const val VALUE_3 = "value_3"
        const val VALUE_4 = "value_4"

        val arrayTime24 = arrayOf(
            "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23"
        )

        val arrayTime60 = arrayOf(
            "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
            "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38",
            "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52",
            "53", "54", "55", "56", "57", "58", "59"
        )
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var sp: SharedPreferences
    private var value1 = 0
    private var value2 = 0
    private var value3 = 0
    private var value4 = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val keyTime = intent.getBooleanExtra(KEY, false)
        if (keyTime)
            setTextTime()
        else
            binding.llTime.isVisible = false

        sp = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        value1 = sp.getInt(VALUE_1, 0)
        value2 = sp.getInt(VALUE_2, 0)
        value3 = sp.getInt(VALUE_3, 0)
        value4 = sp.getInt(VALUE_4, 0)

        setText()
        setPicker()
        initPicker()

        regBack()
    }

    private fun setTextTime()  = with(binding){
        binding.llTime.isVisible = true
        var str = intent.getIntExtra(HOUR, 0).toString()
        tvHour.text = str
        var value = intent.getIntExtra(MIN, 0)
        str = if (value < 10) ":0$value:"
        else ":$value:"
        tvMin.text = str
        value = intent.getIntExtra(SEC, 0)
        str = if (value < 10) "0$value"
        else "$value"
        tvSec.text = str
    }

    private fun setPicker() = with(binding) {
        picker1.displayedValues = arrayTime24
        picker2.displayedValues = arrayTime60
        picker1.value = value1
        picker2.value = value2
        picker3.value = value3
        picker4.value = value4
    }

    private fun setText() = with(binding) {
        var str = value1.toString()
        tvValue1.text = str
        str = value2.toString()
        tvValue2.text = str
        str = value3.toString()
        tvValue3.text = str
        str = value4.toString()
        tvValue4.text = str
    }

    private fun initPicker() = with(binding) {
        picker1.setOnChangeListener { _, value ->
            value1 = value
            setText()
        }
        picker2.setOnChangeListener { _, value ->
            value2 = value
            setText()
        }
        picker3.setOnChangeListener { _, value ->
            value3 = value
            setText()
        }
        picker4.setOnChangeListener { _, value ->
            value4 = value
            setText()
        }
        bnGetTime.setOnClickListener {
            startActivity(Intent(this@MainActivity, GetTimeActivity::class.java))
        }

    }

    override fun onStop() {
        super.onStop()
        val editor = sp.edit()
        editor.putInt(VALUE_1, value1)
        editor.putInt(VALUE_2, value2)
        editor.putInt(VALUE_3, value3)
        editor.putInt(VALUE_4, value4)
        editor.apply()
    }

    // Регистрация кнопки Back
    private fun regBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                exitOnBackPressed()
            }
        } else {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        exitOnBackPressed()
                    }
                })
        }
    }

    // Кнопка Back
    fun exitOnBackPressed() {
        finishAffinity() // Закрыть все
    }

}