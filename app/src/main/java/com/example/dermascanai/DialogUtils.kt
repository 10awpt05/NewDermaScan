import android.content.Context
import android.content.SharedPreferences
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

fun showWarningDialog(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    val dontShowAgain = prefs.getBoolean("dontShowWarning", false)

    if (!dontShowAgain) {
        // Create checkbox
        val checkBox = CheckBox(context)
        checkBox.text = "Don't show again"

        // Add padding to checkbox
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)
        layout.addView(checkBox)

        val builder = AlertDialog.Builder(context)
        builder.setTitle("⚠️ AI Accuracy Warning")
        builder.setMessage(
            "This AI system may not always provide 100% accurate results. " +
                    "Please consult a medical professional for proper diagnosis and treatment."
        )
        builder.setView(layout)

        builder.setPositiveButton("OK") { dialog, _ ->
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("dontShowWarning", true).apply()
            }
            dialog.dismiss()
        }

        builder.setCancelable(false) // Prevent closing without pressing OK
        builder.show()
    }
}
