package com.crawler.dossa

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class CrawlerService: Service() {
    private val crawlerBinder = CrawlerBinder()

    private var lastUrl: String? = null
    private val logArrayList: ArrayList<String> = ArrayList()

    private var serviceHandler: Handler? = null
    private var runnable: Runnable? = null

    private val NOTIFICATION_CHANNEL_SERVICE = "crawler service"
    private val NOTIFICATION_CHANNEL_FOUND = "crawler found"

    inner class CrawlerBinder: Binder(){
        fun getService(): CrawlerService = this@CrawlerService
    }

    override fun onCreate() {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivities(applicationContext, PendingIntent.FLAG_UPDATE_CURRENT, arrayOf(it), PendingIntent.FLAG_IMMUTABLE)
        }

        val channel = NotificationChannel(NOTIFICATION_CHANNEL_SERVICE,
            NOTIFICATION_CHANNEL_SERVICE, NotificationManager.IMPORTANCE_DEFAULT)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification: Notification = Notification.Builder(this, NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle("crawler running")
            .setSmallIcon(R.mipmap.dossa)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1337, notification)

        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_DEFAULT).apply {
            start()
            serviceHandler = Handler(looper)
        }
    }

    override fun onDestroy() {
        Toast.makeText(this, "Crawler shutdown", Toast.LENGTH_SHORT).show()
        runnable?.let { serviceHandler?.removeCallbacks(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "Crawler start", Toast.LENGTH_SHORT).show()
        runnable = Runnable {
            val url = intent!!.extras!!.getString("requestUrl")
            val period = intent.extras!!.getLong("period")

            Log.i("CRAWLER", "handler running. url $url, period $period")
            httpTask(url!!, period)
            serviceHandler?.postDelayed(runnable!!, period* 1000)
        }

        serviceHandler?.post(runnable!!)
        return START_STICKY
    }

    fun getLogs(): ArrayList<String> {
        val array = ArrayList<String>()
        logArrayList.forEach{array.add(it)}
        logArrayList.clear()
        return array
    }

    private fun httpTask(url: String, period: Long){
        val queue = Volley.newRequestQueue(applicationContext)
        Log.d("CRAWLER", "request for $url")

        val request = object: StringRequest(
            Method.GET, url,
            { response ->
                try {
                    val urlFound = findHref(response)
                    val timeDelayed = findDelay(response)
                    val postTitle = findTitle(response)

                    Log.d("CRAWLER", "title $postTitle delay $timeDelayed post $urlFound")

                    if(timeDelayed < period && urlFound != lastUrl){
                        Log.w("DOSSA", "found item")
                        lastUrl = urlFound

                        val dossaIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlFound))
                        val pDossaIntent = PendingIntent.getActivity(
                            applicationContext, 0, dossaIntent, PendingIntent.FLAG_IMMUTABLE
                        )

                        val channel = NotificationChannel(NOTIFICATION_CHANNEL_FOUND,
                            NOTIFICATION_CHANNEL_FOUND, NotificationManager.IMPORTANCE_DEFAULT)

                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.createNotificationChannel(channel)

                        val notification: Notification = Notification.Builder(this, NOTIFICATION_CHANNEL_SERVICE)
                            .setContentTitle("Found")
                            .setContentText(postTitle)
                            .setSmallIcon(R.mipmap.dossa)
                            .setContentIntent(pDossaIntent)
                            .build()

                        manager.notify(1, notification)
                    }else{
                        Log.w("DOSSA", "there is no satisfying item")
                        val timeNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        logArrayList.add("[$timeNow] <$postTitle> posted $timeDelayed before.\n")
                    }
                }catch (e: Exception){
                    Log.e("DOSSA", e.toString())
                    Toast.makeText(this, "Error on crawler $e", Toast.LENGTH_SHORT).show()
                }
            },
            { error -> Log.w("CRAWLER", "error on request $error") }
        ){
            override fun getHeaders(): MutableMap<String, String> {
                val header = HashMap<String, String>()
                header["User-agent"] = "Mozilla/5.0"
                return header
            }

        }

        queue.add(request)
    }

    private fun findTitle(response: String): String{
        val doc: Document = Jsoup.parse(response)
        // for mobile page
        val elements: Elements = doc.getElementsByClass("bd_ls_tt")
        return elements[0].text()
        /*
        // for pc page
        val elements: Elements = doc.getElementsByTag("a")
        return elements[23].text()
        */
    }

    private fun findHref(response: String): String{
        val doc: Document = Jsoup.parse(response)
        val elements: Elements = doc.getElementsByClass("hand")
        val url = elements[18].attr("onclick")
        /*
        // for pc page
        val elements: Elements = doc.getElementsByTag("a")
        val url = elements[23].attr("href")
        return URL_BASE + url.drop(1)
        */
        return getString(R.string.url_base) + "/board" + url.drop(16).dropLast(2)
    }

    private fun findDelay(response: String): Long{
        val doc = Jsoup.parse(response)

        // for mobile page
        val elements = doc.getElementsByTag("td")
        val strUpload = elements[310].text()
        /*
        // for pc page
        val elements = doc.getElementsByClass("small_99")
        val strUpload = elements[0].text().split("|")[1].replace(" ", "")
        */
        Log.d("DOSSA", "recent post uploaded time $strUpload")
        return if(strUpload.contains(":")){
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeCurrent = fmt.parse(fmt.format(Date()))
            val timeUpload = fmt.parse(strUpload)
            (timeCurrent!!.time - timeUpload!!.time) / 1000
        }else{
            Log.d("DOSSA", "post is not uploaded recently")
            9999
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        Toast.makeText(this, "Crawler start", Toast.LENGTH_SHORT).show()
        runnable = Runnable {
            val url = p0!!.extras!!.getString("requestUrl")
            val period = p0.extras!!.getLong("period")

            Log.i("CRAWLER", "handler running. url $url, period $period")
            httpTask(url!!, period)
            serviceHandler?.postDelayed(runnable!!, period* 1000)
        }

        serviceHandler?.post(runnable!!)
        return crawlerBinder
    }
}