package com.netgents.saisiyat

import _root_.android._
  import app.Activity
  import os.Bundle
  import widget.{ArrayAdapter, ListView}

class SaisiyatActivity extends Activity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    val arrayAdapter = new ArrayAdapter[String](this, R.layout.items_textview)
    arrayAdapter.add("Hello")
    arrayAdapter.add("World")

    val items = findViewById(R.id.items).asInstanceOf[ListView]
    items.setAdapter(arrayAdapter)
  }
}
