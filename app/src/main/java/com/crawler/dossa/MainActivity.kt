package com.crawler.dossa

import android.app.Dialog
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.method.KeyListener
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {
    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var textRunnable: Runnable

    private lateinit var mService: CrawlerService
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection{
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as CrawlerService.CrawlerBinder
            mService = binder.getService()
            mBound = true
            Log.d("APP", "Service Connected")

            startLog()
        }
        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound = false
            Log.d("APP", "Service Crashed")
        }
    }

    private var period: Long? = null
    private val PERIOD_DEFAULT: Long = 10
    private val TAG_POSITION_DEFAULT: Int = 310
    private val MAX_LINE: Int = 50
    private var tagPosition: Int = TAG_POSITION_DEFAULT

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
        val btnSetting: Button = findViewById(R.id.btnSetting)

        val preferences: SharedPreferences = getPreferences(Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = preferences.edit()
        val prevUrl = preferences.getString("URL", "")
        val prevPeriod = preferences.getLong("PERIOD", PERIOD_DEFAULT)
        tagPosition = preferences.getInt("TagPosition", TAG_POSITION_DEFAULT)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val powerManagerIntent: Intent
        if(!powerManager.isIgnoringBatteryOptimizations(packageName)){
            Log.e("APP", "system does not ignore battery optimization")
            powerManagerIntent = Intent().setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(powerManagerIntent)
        }

        textUrl.setText(prevUrl)
        textPeriod.setText(prevPeriod.toString())

        btnDossa.setOnClickListener(btnListener)
        btnUrl.setOnClickListener(btnListener)

        btnSetting.setOnClickListener{
            val dialogView = layoutInflater.inflate(R.layout.dialog_setting, null)
            val textTagPosition: TextView = dialogView.findViewById(R.id.textTagPosition)
            val alertDialog = AlertDialog.Builder(this).setView(dialogView)
                .setPositiveButton("Ok") { _, _ ->
                    tagPosition = textTagPosition.text.toString().toInt()
                    editor.putInt("TagPosition", tagPosition)
                    Log.d("DIALOG", "tag position updated to $tagPosition")
                }.setNegativeButton("Cancel") { _, _ ->

                }.setNeutralButton("Reset") { _, _ ->
                    textTagPosition.text = TAG_POSITION_DEFAULT.toString()
                    Log.d("DIALOG", "tag position reset to $TAG_POSITION_DEFAULT")
                }.create()

            textTagPosition.text = tagPosition.toString()
            alertDialog.show()
        }

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
                editor.apply()

                try {
                    period = textPeriod.text.toString().toLong()
                    if (period!! <= 0){
                        throw IndexOutOfBoundsException()
                    }
                } catch (e: Exception){
                    Log.e("VIEW", e.toString())
                    period = PERIOD_DEFAULT
                }

                val crawlerIntent = Intent(applicationContext, CrawlerService::class.java)
                    .putExtra("requestUrl", textUrl.text.toString())
                    .putExtra("period", period)
                    .putExtra("tagPosition", tagPosition)

                bindService(crawlerIntent, connection, Context.BIND_AUTO_CREATE)
            }else{
                Log.d("VIEW", "switchCrawler unchecked")
                textUrl.keyListener = textUrl.tag as KeyListener
                textPeriod.keyListener = textPeriod.tag as KeyListener

                if(mBound){
                    stopLog()
                    unbindService(connection)
                    mBound = false

                    Log.d("APP", "Service Disconnected")
                }
            }
        }
    }

    private fun startLog(){
        val textLog: TextView = findViewById(R.id.textLog)
        textLog.text = ""
        textRunnable = Runnable {
            val logArrayList = mService.getLogs()
            logArrayList.forEach{textLog.append(it)}
            val excessNumber = textLog.lineCount - MAX_LINE
            if(excessNumber > 0){
                var eolIndex = -1
                val charSequence: CharSequence = textLog.text
                for (i in 0 until excessNumber) {
                    do {
                        eolIndex++
                    } while (eolIndex < charSequence.length && charSequence[eolIndex] != '\n')
                }
                if (eolIndex < charSequence.length) {
                    textLog.editableText.delete(0, eolIndex + 1)
                } else {
                    textLog.text = ""
                }

                Log.d("APP", "Log overflowed remove last")
            }

            Log.d("APP", "Log updated")
            mHandler.postDelayed(textRunnable, (period?:5) * 500)
        }

        mHandler.post(textRunnable)
        Log.d("APP", "Log started")
    }

    private fun stopLog(){
        val textLog: TextView = findViewById(R.id.textLog)
        mHandler.removeCallbacks(textRunnable)
        Log.d("APP", "Log stopped")
    }
}