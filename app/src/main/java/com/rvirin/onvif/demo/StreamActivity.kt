package com.rvirin.onvif.demo

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import com.pedro.vlc.VlcListener
import com.pedro.vlc.VlcVideoLibrary
import com.rvirin.onvif.R
import kotlinx.android.synthetic.main.activity_stream.*
import com.rvirin.onvif.R.id.textureView
import android.provider.MediaStore.Images.Media.getBitmap
import android.util.Log
import android.util.Rational
import android.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


/**
 * This activity helps us to show the live stream of an ONVIF camera thanks to VLC library.
 */
class StreamActivity : AppCompatActivity(), VlcListener, View.OnClickListener {


    private var vlcVideoLibrary: VlcVideoLibrary? = null
    public var textureView: TextureView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)

        textureView = findViewById<TextureView>(R.id.textureView)

        val bStartStop = findViewById<Button>(R.id.b_start_stop)
        bStartStop.setOnClickListener(this)

        val screenshot_button = findViewById<Button>(R.id.screenshot_button)
        screenshot_button.setOnClickListener(this)
        screenshot_button.visibility = View.GONE

        vlcVideoLibrary = VlcVideoLibrary(this, this, textureView)

        //val options = arrayOf(":fullscreen")

        //vlcVideoLibrary?.setOptions( )

        setTitle("Streaming")

    }
    /**
     * Called by VLC library when the video is loading
     */
    override fun onComplete() {
        Toast.makeText(this, "Loading video...", Toast.LENGTH_LONG).show()

        val screenshot_button = findViewById<Button>(R.id.screenshot_button)
        screenshot_button.visibility = View.VISIBLE

    }

    /**
     * Called by VLC library when an error occured (most of the time, a problem in the URI)
     */
    override fun onError() {
        Toast.makeText(this, "Error, make sure your endpoint is correct", Toast.LENGTH_SHORT).show()
        vlcVideoLibrary?.stop()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.stream, menu)
        return true
    }
    /*@Override
    public void onPictureInPictureModeChanged (boolean isInPictureInPictureMode, Configuration newConfig) {
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
        } else {
            // Restore the full-screen UI.
            ...
        }
    }*/

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        if (isInPictureInPictureMode)
        {
            findViewById<Button>(R.id.b_start_stop).visibility = View.GONE
            findViewById<Button>(R.id.screenshot_button).visibility = View.GONE
            supportActionBar?.hide()
            //val url = intent.getStringExtra(RTSP_URL)
            //vlcVideoLibrary?.play(url)

        }
        else
        {
            findViewById<Button>(R.id.b_start_stop).visibility = View.VISIBLE
            findViewById<Button>(R.id.screenshot_button).visibility = View.VISIBLE

            supportActionBar?.show()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem?): Boolean
    {
        when(item?.itemId)
        {
            R.id.bgplay ->
            {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {
                    val mPictureInPictureParamsBuilder : PictureInPictureParams.Builder = PictureInPictureParams.Builder()
                    mPictureInPictureParamsBuilder.setAspectRatio(Rational(textureView!!.width, textureView!!.height))
                    enterPictureInPictureMode(mPictureInPictureParamsBuilder.build())
                }
                else
                {
                    Toast.makeText(applicationContext, "Your Android Version does not support this! Please upgrade to Nougat and above for this to work!", Toast.LENGTH_LONG).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public var m: MediaRecorder?= null

    private fun getOutputMediaFile(): File?
    {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        val folderName = "ONVIFScreenshots"
        val mediaStorageDir = File(Environment.getExternalStorageDirectory().toString()+ File.separator + folderName )

        Log.i("File-Folder", mediaStorageDir.path)

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }
        // Create a media file name
        val timeStamp = SimpleDateFormat("ddMMyyyy_HHmm").format(Date())
        val mediaFile: File
        val mImageName = "MI_$timeStamp.jpg"

        var filename = mediaStorageDir.path + File.separator +  mImageName
        Log.i("File-Folder", mediaStorageDir.path)
        mediaFile = File(filename)
        return mediaFile
    }

    fun takeScreenshot()
    {
        //m = MediaRecorder()
        val bitmap: Bitmap?= textureView?.getBitmap()

        var out: FileOutputStream? = null
        try
        {
            var f : File? = getOutputMediaFile()
            out = FileOutputStream(f)
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
            Toast.makeText(applicationContext, "Saved at " + f?.path, Toast.LENGTH_SHORT).show()

            // PNG is a lossless format, the compression factor (100) is ignored
        }

        catch (e: Exception)
        {
            e.printStackTrace()
        }
        finally
        {
            try
            {
                if (out != null)
                {
                    out!!.close()
                }
            }
            catch (e: IOException)
            {
                e.printStackTrace()
            }
        }
    }
    override fun onClick(v: View?) {

        when(v?.id)
        {
            R.id.b_start_stop ->
            {
                vlcVideoLibrary?.let { vlcVideoLibrary ->

                    if (!vlcVideoLibrary.isPlaying) {
                        val url = intent.getStringExtra(RTSP_URL)
                        vlcVideoLibrary.play(url)

                    }
                    else
                    {
                        vlcVideoLibrary.stop()

                    }
                }
            }
            R.id.screenshot_button ->
            {
                Toast.makeText(applicationContext, "Taking Screenshot", Toast.LENGTH_SHORT).show()
                takeScreenshot()
            }
        }

    }
}

