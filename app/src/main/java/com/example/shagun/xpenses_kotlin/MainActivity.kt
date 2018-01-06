package com.example.shagun.xpenses_kotlin

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.example.shagun.xpenses_kotlin.R.array.mode_array
import com.example.shagun.xpenses_kotlin.R.layout.layout_old_record
import com.example.shagun.xpenses_kotlin.R.layout.layout_record
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.layout_date_picker.view.*
import kotlinx.android.synthetic.main.layout_record.view.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var sharedPrefrences: SharedPreferences
    var recordViews: ArrayList<View> = ArrayList()
    var allRecords: ArrayList<Record> = ArrayList()
    lateinit var arrayAdapter: ArrayAdapter<CharSequence>
    private var sheet: Sheet? = null
    lateinit var calendar: Calendar
    private var date: String? = null
    private var month:String? = null
    private var currView: View? = null
    private var commits: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        sharedPrefrences=this.getSharedPreferences("stuff",android.content.Context.MODE_PRIVATE)
        arrayAdapter=ArrayAdapter.createFromResource(this, R.array.mode_array, android.R.layout.simple_spinner_item)
        sheet = Sheet()
        calendar = Calendar.getInstance()
        toolbar.subtitle = toMonth(calendar.get(Calendar.MONTH), false)
        month = toMonth(calendar.get(Calendar.MONTH), true)
        // permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 121)

        // check for network and sync
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo != null && networkInfo.isConnectedOrConnecting)
            read()
            Log.d("MAIN","GOOD!")

    }

    private fun read() {
        val progressDialog = ProgressDialog.show(this, "Getting Expenses", "Accessing network...")
        val databaseReference = FirebaseDatabase.getInstance().getReference(month)
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val iterator = dataSnapshot.children.iterator()
                while (iterator.hasNext()) {

                    val comments: String?
                    val amount: String?
                    val mode: String?
                    val date: String?
                    val xpense = iterator.next()

                    val newRecordView = layoutInflater.inflate(layout_old_record, null, false)

                    // init mode spinner
                    val spinner = newRecordView.mode as Spinner
                    val arrayAdapter = ArrayAdapter
                            .createFromResource(this@MainActivity, mode_array, android.R.layout.simple_spinner_item)
                    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = arrayAdapter

                    // setting fields
                    comments=xpense.child("comments").toString()
                    (newRecordView.comments as EditText).setText(comments)
                    if (comments!!.equals("atm", ignoreCase = true)) {
                        (newRecordView.comments as EditText).setTextColor(Color.parseColor("#4CAF50"))
                        (newRecordView.amount as EditText).setTextColor(Color.parseColor("#4CAF50"))
                        (newRecordView.date as Button).backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    }
                    amount = xpense.child("amount").toString()
                    (newRecordView.amount as EditText).setText(amount)
                    mode = xpense.child("mode").getValue(String::class.java)
                    (newRecordView.mode as Spinner).setSelection(arrayAdapter.getPosition(mode))
                    date = xpense.child("date").getValue(String::class.java)
                    (newRecordView.date as Button).text = date

                    allRecords.add(Record(comments, amount, mode!!, date!!))
                    tableLayout.addView(newRecordView)

                }

                progressDialog.cancel()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, databaseError.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun commit() {
        if (sheet == null)
            return
        commits = 0
        val r = Random().nextInt()
        val databaseReference = FirebaseDatabase.getInstance().reference
        databaseReference.child("lock").setValue(r)
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val lock = dataSnapshot.child("lock").getValue(Int::class.java)!!
                if (lock != 0 && lock != r) {
                    Toast.makeText(this@MainActivity, "Someone else is syncing at the moment - try again", Toast.LENGTH_LONG).show()
                    return
                }
                var c = dataSnapshot.child(month).childrenCount
                Toast.makeText(this@MainActivity, c.toString() + "", Toast.LENGTH_SHORT).show()
                for (i in 0 until sheet!!.list.size) {
                    val record = sheet!!.list[i]
                    databaseReference.child(month).child((++c).toString() + "").setValue(record)
                    allRecords.add(record)
                }
                databaseReference.child("lock").setValue(0)
                //Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();

                // re-init
                recordViews = ArrayList()
                sheet = Sheet()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                databaseReference.child("lock").setValue(0)
                Toast.makeText(this@MainActivity, "Failed - try again", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun toMonth(i: Int, shortHand: Boolean): String {
        return when (i) {
            0 -> if (shortHand) "JAN" else "January"
            1 -> if (shortHand) "FEB" else "February"
            2 -> if (shortHand) "MAR" else "March"
            3 -> if (shortHand) "APR" else "April"
            4 -> if (shortHand) "MAY" else "May"
            5 -> if (shortHand) "JUN" else "June"
            6 -> if (shortHand) "JUL" else "July"
            7 -> if (shortHand) "AUG" else "August"
            8 -> if (shortHand) "SEP" else "September"
            9 -> if (shortHand) "OCT" else "October"
            10 -> if (shortHand) "NOV" else "November"
            else -> if (shortHand) "DEC" else "December"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_new -> {

                val newRecordView = layoutInflater.inflate(layout_record, null, false)

                // init mode spinner
                val spinner = newRecordView.mode as Spinner
                val arrayAdapter = ArrayAdapter
                        .createFromResource(this@MainActivity, R.array.mode_array, android.R.layout.simple_spinner_item)
                arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = arrayAdapter

                // init date selector
                date = calendar.get(Calendar.DAY_OF_MONTH).toString() + "-" + toMonth(calendar.get(Calendar.MONTH), true) + "-" + (calendar.get(Calendar.YEAR).toString() + "").substring(2)
                (newRecordView.date as Button).text = date
                newRecordView.date.setOnClickListener(View.OnClickListener {
                    val datePickerView = layoutInflater.inflate(R.layout.layout_date_picker, null, false)
                    AlertDialog.Builder(this@MainActivity)
                            .setView(datePickerView)
                            .setPositiveButton("Done", DialogInterface.OnClickListener { _, _ ->
                                val datePicker = datePickerView.datePicker
                                calendar.set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
                                calendar.set(Calendar.MONTH, datePicker.month)
                                calendar.set(Calendar.YEAR, datePicker.year)
                                date = calendar.get(Calendar.DAY_OF_MONTH).toString() + "-" + toMonth(calendar.get(Calendar.MONTH), true) + "-" + (calendar.get(Calendar.YEAR).toString() + "").substring(2)
                                (newRecordView.date as Button).text = date
                            })
                            .create()
                            .show()
                })

                // dynamic read | comments
                newRecordView.comments.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                    currView = view
                    false
                })
                (newRecordView.comments as EditText).addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

                    }

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                        if (charSequence.toString().equals("atm", ignoreCase = true)) {
                            ((currView?.parent as View).comments as EditText).setTextColor(Color.parseColor("#4CAF50"))
                            ((currView?.parent as View).amount as EditText).setTextColor(Color.parseColor("#4CAF50"))
                            ((currView?.parent as View).date as Button).backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                            spinner.setSelection(1)
                        } else {
                            ((currView?.parent as View).comments as EditText).setTextColor(Color.parseColor("#000000"))
                            ((currView?.parent as View).amount as EditText).setTextColor(Color.parseColor("#000000"))
                            ((currView?.parent as View).date as Button).backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B0BEC5"))
                            spinner.setSelection(0)
                        }
                    }

                    override fun afterTextChanged(editable: Editable) {

                    }
                })

                recordViews.add(newRecordView)
                tableLayout.addView(newRecordView)

                (scrollView as ScrollView).post { (scrollView as ScrollView).fullScroll(View.FOCUS_DOWN) }

                return true
            }

            R.id.action_save -> {
                if (recordViews.size == 0)
                    return true
                for (recordView in recordViews) {

                    var comments = (recordView.comments as EditText).text.toString() // comments
                    if (TextUtils.isEmpty(comments))
                        comments = "Unknown"
                    val amount = (recordView.amount as EditText).text.toString() // amount
                    if (TextUtils.isEmpty(amount))
                        continue
                    var mode = (recordView.mode as Spinner).selectedItem.toString() // mode
                    if (comments.equals("atm", ignoreCase = true))
                        mode = "Card"
                    val date = (recordView.date as Button).text.toString() // date

                    sheet?.list?.add(Record(comments, amount, mode, date))
                }
                sharedPrefrences.edit().putString("list", Gson().toJson(sheet)).apply()
                commit()
                //startService(new Intent(MainActivity.this, SyncService.class));

                return true
            }

            R.id.action_export -> {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Xpenses")
                dir.mkdirs()
                val file = File(dir, month + ".csv")
                if (file.exists())
                    file.delete()
                try {
                    val bufferedWriter = BufferedWriter(FileWriter(file))
                    bufferedWriter.write("Comments, Amount, Mode, Date\n")
                    for (record in allRecords)
                        bufferedWriter.write(record.comments + "," + record.amount + "," + record.mode + "," + record.date + "\n")
                    bufferedWriter.close()
                } catch (e: IOException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }

                Toast.makeText(this, "Export complete", Toast.LENGTH_SHORT).show()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}
