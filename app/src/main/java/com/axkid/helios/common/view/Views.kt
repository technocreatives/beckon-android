package com.axkid.helios.common.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun View.activity(): Activity? {
    var context = context
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun EditText.text() = this.text.toString()

fun TextView.setText(value: String, default: String) {
    val newText = when {
        value.isEmpty() -> default
        else -> value
    }
    this.text = newText
}

fun RecyclerView.init(adapter: RecyclerView.Adapter<*>, layoutManager: RecyclerView.LayoutManager) {
    this.setHasFixedSize(true)
    this.layoutManager = layoutManager
    this.adapter = adapter
}

fun Context.linearLayoutManager(direction: Int): LinearLayoutManager {
    val linearLayoutManage = LinearLayoutManager(this)
    linearLayoutManage.orientation = direction
    return linearLayoutManage
}

fun Context.horizontalLayoutManager() =
    linearLayoutManager(LinearLayoutManager.HORIZONTAL)

fun Context.verticalLayoutManager() =
    linearLayoutManager(LinearLayoutManager.VERTICAL)
