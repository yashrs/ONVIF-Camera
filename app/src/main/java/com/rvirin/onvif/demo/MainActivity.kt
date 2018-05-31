package com.rvirin.onvif.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.rvirin.onvif.R
import com.rvirin.onvif.onvifcamera.*

import com.rvirin.onvif.onvifcamera.OnvifRequest.Type.GetStreamURI
import com.rvirin.onvif.onvifcamera.OnvifRequest.Type.GetProfiles
import com.rvirin.onvif.onvifcamera.OnvifRequest.Type.GetDeviceInformation
import com.rvirin.onvif.onvifcamera.OnvifRequest.Type.GetServices
import android.R.id.edit
import android.content.Context
import android.content.SharedPreferences


const val RTSP_URL = "com.rvirin.onvif.onvifcamera.demo.RTSP_URL"

/**
 * Main activity of this demo project. It allows the user to type his camera IP address,
 * login and password.
 */
class MainActivity : AppCompatActivity(), OnvifListener {

    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPermissions()
        loadFromSharedPrefs()
    }
    fun loadFromSharedPrefs()
    {
        val sp :SharedPreferences =  getSharedPreferences("general", Context.MODE_PRIVATE)
        val ip: String = sp.getString("ip", "")
        val username: String = sp.getString("username", "")
        val password: String = sp.getString("password", "")

        (findViewById<EditText>(R.id.ipAddress)).setText(ip)
        (findViewById<EditText>(R.id.login)).setText(username)
        (findViewById<EditText>(R.id.password)).setText(password)

    }
    fun saveToSharedPrefs()
    {
        val sp :SharedPreferences =  getSharedPreferences("general", Context.MODE_PRIVATE)

        val editor :SharedPreferences.Editor = sp.edit()

        val ipAddress = (findViewById<EditText>(R.id.ipAddress)).text.toString()
        val login = (findViewById<EditText>(R.id.login)).text.toString()
        val password = (findViewById<EditText>(R.id.password)).text.toString()

        editor.putString("ip", ipAddress)
        editor.putString("username", login)
        editor.putString("password", password)

        editor.commit()

    }
    override fun requestPerformed(response: OnvifResponse)
    {

        Log.d("INFO", response.parsingUIMessage)

        toast?.cancel()

        if (!response.success) {
            Log.e("ERROR", "request failed: ${response.request.type} \n Response: ${response.error}")
            toast = Toast.makeText(this, "⛔️ Request failed: ${response.request.type}", Toast.LENGTH_SHORT)
            toast?.show()
        }
        // if GetServices have been completed, we request the device information
            else if (response.request.type == GetServices) {
            currentDevice.getDeviceInformation()
        }
        // if GetDeviceInformation have been completed, we request the profiles
        else if (response.request.type == GetDeviceInformation)
        {

            val textView = findViewById<TextView>(R.id.explanationTextView)
            textView.text = response.parsingUIMessage
            toast = Toast.makeText(this, "Device information retrieved 👍", Toast.LENGTH_SHORT)
            toast?.show()

            val c : CheckBox  = findViewById<CheckBox>(R.id.remember_details)
            if(c.isChecked())
            {
                saveToSharedPrefs()
                Toast.makeText(this, "Saved Information for next time!", Toast.LENGTH_SHORT).show()

            }
            currentDevice.getProfiles()

        }
        // if GetProfiles have been completed, we request the Stream URI
        else if (response.request.type == GetProfiles) {
            val profilesCount = currentDevice.mediaProfiles.count()
            toast = Toast.makeText(this, "$profilesCount profiles retrieved 😎", Toast.LENGTH_SHORT)
            toast?.show()

            currentDevice.getStreamURI()

        }
        // if GetStreamURI have been completed, we're ready to play the video
        else if (response.request.type == GetStreamURI) {

            val button = findViewById<TextView>(R.id.button)
            button.text = getString(R.string.Play)

            toast = Toast.makeText(this, "Stream URI retrieved,\nready for the movie 🍿", Toast.LENGTH_SHORT)
            toast?.show()
        }
    }

    fun buttonClicked(view: View)
    {

        // If we were able to retrieve information from the camera, and if we have a rtsp uri,
        // We open StreamActivity and pass the rtsp URI
        if (currentDevice.isConnected) {
            currentDevice.rtspURI?.let { uri ->
                val intent = Intent(this, StreamActivity::class.java).apply {
                    putExtra(RTSP_URL, uri)
                }
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "RTSP URI haven't been retrieved", Toast.LENGTH_SHORT).show()
            }
        } else {

            // get the information type by the user to create the Onvif device
            val ipAddress = (findViewById<EditText>(R.id.ipAddress)).text.toString()
            val login = (findViewById<EditText>(R.id.login)).text.toString()
            val password = (findViewById<EditText>(R.id.password)).text.toString()

            if (ipAddress.isNotEmpty() &&
                    login.isNotEmpty() &&
                    password.isNotEmpty())
            {

                // Create ONVIF device with user inputs and retrieve camera informations
                currentDevice = OnvifDevice(ipAddress, login, password)
                currentDevice.listener = this
                currentDevice.getServices()

            } else {
                toast?.cancel()
                toast = Toast.makeText(this,
                        "Please enter an IP Address, Login and Password",
                        Toast.LENGTH_SHORT)
                toast?.show()
            }
        }
    }

    private val TAG_P = "Permissions"
    private val WRITE_REQUEST_CODE = 101

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG_P, "Permission to Write External Storage not granted")
            makeRequest()
        }
    }
    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                WRITE_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode)
        {
            WRITE_REQUEST_CODE->
            {
                if(grantResults.isEmpty() || grantResults[0]!=PackageManager.PERMISSION_GRANTED)
                {
                    Log.i(TAG_P, "Permission denied by user")
                    Toast.makeText(applicationContext, "The Write Permission is required in order to record videos. Please grant it.", Toast.LENGTH_LONG).show()
                    makeRequest()
                }
                else
                {
                    Log.i(TAG_P, "Permission granted by user")
                }
            }
        }
    }
}
