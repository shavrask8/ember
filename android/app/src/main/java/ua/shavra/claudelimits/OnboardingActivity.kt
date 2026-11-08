package ua.shavra.claudelimits

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Перший запуск: вибір сервісу та вхід до нього. Стиль — Claude (за замовчуванням). */
class OnboardingActivity : Activity() {
    private var chosen: Provider? = null

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(window)
        val d = { v: Int -> Ui.dp(this, v) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(48), d(24), d(24))
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_spark)
            imageTintList = ColorStateList.valueOf(Ui.color(context, R.attr.cAccent))
        }, LinearLayout.LayoutParams(d(48), d(48)))
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 36f
            typeface = Typeface.SERIF
            setTextColor(Ui.color(context, R.attr.cText))
            setPadding(0, d(18), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.ob_subtitle)
            textSize = 16f
            setTextColor(Ui.color(context, R.attr.cAccent))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.ob_text)
            textSize = 15f
            setTextColor(Ui.color(context, R.attr.cText2))
            setPadding(0, d(8), 0, d(18))
        })

        Provider.values().forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.card)
                setPadding(d(18), d(18), d(18), d(18))
                isClickable = true
                foreground = getDrawable(android.R.drawable.list_selector_background)
                setOnClickListener { choose(p) }
            }
            card.addView(ImageView(this).apply {
                setImageResource(p.icon)
                imageTintList = ColorStateList.valueOf(
                    if (p == Provider.CLAUDE) p.accent else Ui.color(context, R.attr.cText),
                )
            }, LinearLayout.LayoutParams(d(36), d(36)))
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(16), 0, 0, 0)
            }
            texts.addView(TextView(this).apply {
                text = p.title
                textSize = 19f
                setTextColor(Ui.color(context, R.attr.cText))
                typeface = if (p == Provider.CLAUDE) Typeface.SERIF else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            texts.addView(TextView(this).apply {
                text = getString(p.note)
                textSize = 13f
                setTextColor(Ui.color(context, R.attr.cText2))
            })
            card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 26f
                setTextColor(Ui.color(context, R.attr.cText2))
            })
            root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(12) })
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.later)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Ui.color(context, R.attr.cText2))
            setPadding(0, d(24), 0, d(8))
            setOnClickListener { finishOnboarding() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.color(context, R.attr.cBg))
            addView(root)
        })
        Ui.insetScroll(this)
    }

    private fun choose(p: Provider) {
        chosen = p
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_PROVIDER, p.id), REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val p = chosen ?: return
        if (requestCode == REQ && resultCode == RESULT_OK) {
            Prefs.setEnabled(this, p, true)
            Prefs.setSelected(this, p)
            Prefs.setTileProvider(this, p)
            Features.scheduleJob(this)
            Repo.refresh(this)
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        Prefs.setOnboarded(this, true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private companion object {
        const val REQ = 7
    }
}
