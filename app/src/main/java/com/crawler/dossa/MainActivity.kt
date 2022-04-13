package com.crawler.dossa

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.method.KeyListener
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {
    private val PERIOD_DEFAULT: Long = 10

    private val btnListener= View.OnClickListener { p0 ->
        lateinit var url: String
        when(p0?.id){
            R.id.btnDossa -> url = getString(R.string.url_base)
            R.id.btnUrl -> {
                val textUrl: EditText = findViewById(R.id.textUrl)
                url = textUrl.text.toString()
            }
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textUrl: EditText = findViewById(R.id.textUrl)
        val textPeriod: EditText = findViewById(R.id.textPeriod)
        val switchCrawler: SwitchCompat = findViewById(R.id.switchCrawler)
        val btnDossa: Button = findViewById(R.id.btnDossa)
        val btnUrl: Button = findViewById(R.id.btnUrl)
        val btnUrlReset: Button = findViewById(R.id.btnUrlReset)

        val preferences: SharedPreferences = getPreferences(Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = preferences.edit()
        val prevUrl = preferences.getString("URL", "")
        val prevPeriod = preferences.getLong("PERIOD", PERIOD_DEFAULT)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        var intent = Intent()
        if(!powerManager.isIgnoringBatteryOptimizations(packageName)){
            Log.e("APP", "system does not ignore battery optimization")
            intent = Intent().setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent);
        }

        textUrl.setText(prevUrl)
        textPeriod.setText(prevPeriod.toString())

        btnDossa.setOnClickListener(btnListener)
        btnUrl.setOnClickListener(btnListener)

        btnUrlReset.setOnClickListener{
            if(!switchCrawler.isChecked){
                textUrl.text.clear()
            }else{
                Toast.makeText(this, "Turn off crawler before reset", Toast.LENGTH_SHORT).show()
            }
        }

        switchCrawler.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                Log.d("VIEW", "switchCrawler checked")
                textPeriod.tag = textPeriod.keyListener
                textPeriod.keyListener = null
                textUrl.tag = textUrl.keyListener
                textUrl.keyListener = null

                editor.putLong("PERIOD", textPeriod.text.toString().toLong())
                editor.putString("URL", textUrl.text.toString())
                editor.commit()

                var period: Long?

                try {
                    period = textPeriod.text.toString().toLong()
                    if (period <= 0){
                        throw IndexOutOfBoundsException()
                    }
                } catch (e: Exception){
                    Log.e("VIEW", e.toString())
                    period = PERIOD_DEFAULT
                }

                val intent = Intent(applicationContext, CrawlerService::class.java)
                    .putExtra("requestUrl", textUrl.text.toString())
                    .putExtra("period", period)

                startForegroundService(intent)

            }else{
                Log.d("VIEW", "switchCrawler unchecked")
                textUrl.keyListener = textUrl.tag as KeyListener
                textPeriod.keyListener = textPeriod.tag as KeyListener

                val intent = Intent(applicationContext, CrawlerService::class.java)
                stopService(intent)
            }

        }
    }
}