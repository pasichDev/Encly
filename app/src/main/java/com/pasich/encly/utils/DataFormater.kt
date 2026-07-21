package com.pasich.encly.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


fun formatNoteDate(date: Date = Date()): String {
    val calendar = Calendar.getInstance()
    val currentDate = Calendar.getInstance()
    currentDate.time = Date()

    // Check for today
    if (calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == currentDate.get(
            Calendar.DAY_OF_YEAR
        )
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "Сьогодні ${timeFormat.format(date)}"
    }

    // Check for yesterday
    currentDate.add(Calendar.DAY_OF_YEAR, -1)
    if (calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == currentDate.get(
            Calendar.DAY_OF_YEAR
        )
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "Вчора ${timeFormat.format(date)}"
    }

    // Formatting for other dates
    val dateFormat = SimpleDateFormat("dd MMMM.yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(date)
}
