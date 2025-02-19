package com.tera.custom_numberpicker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
        const val VALUE_STR = "value_str"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var sp: SharedPreferences
    private var value1 = 0
    private var value2 = 0
    private var value3 = 0
    private var value4 = 0
    private var valueStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val keyTime = intent.getBooleanExtra(KEY, false)
        if (keyTime)
            setTextTime()
        else
            binding.llTime.visibility = View.INVISIBLE

        sp = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        value1 = sp.getInt(VALUE_1, 0)
        value2 = sp.getInt(VALUE_2, 0)
        value3 = sp.getInt(VALUE_3, 0)
        value4 = sp.getInt(VALUE_4, 0)
        valueStr = sp.getString(VALUE_STR, "").toString()

        setText()
        setPicker()
        initPicker()
        regBack()
    }

    private fun setTextTime() = with(binding) {
        llTime.visibility = View.VISIBLE
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
        picker1.value = value1
        picker2.value = value2
        picker3.value = value3
        picker4.value = value4
    }

    private fun setText() = with(binding) {
        var str = value1.toString()
        tvValue1.text = str
        tvValue2.text = valueStr
        str = value3.toString()
        tvValue3.text = str
        str = value4.toString()
        str = picker4.valueString
        tvValue4.text = str
    }

    private fun initPicker() = with(binding) {
        picker1.setOnChangeListener {
            value1 = it
            setText()
        }
        picker2.setOnChangeListener {
            value2 = it
            valueStr = picker2.valueString
            setText()
        }
        picker3.setOnChangeListener {
            value3 = it
            setText()
        }
        picker4.setOnChangeListener {
            value4 = it
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
        editor.putString(VALUE_STR, valueStr)
        editor.apply()
    }

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

    fun exitOnBackPressed() {
        finishAffinity() // Закрыть все
    }

}