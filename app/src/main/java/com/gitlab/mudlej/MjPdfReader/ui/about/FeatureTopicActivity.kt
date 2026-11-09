// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityFeatureTopicBinding
import com.gitlab.mudlej.MjPdfReader.databinding.FeatureRowItemBinding

class FeatureTopicActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureTopicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureTopicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()

        val topicName = intent.getStringExtra(EXTRA_TOPIC)
        val topic = FeatureTopic.entries.find { it.name == topicName }
        if (topic == null) {
            finish()
            return
        }
        setTitle(topic.titleRes)
        bindEntries(topic)
    }

    private fun bindEntries(topic: FeatureTopic) {
        for (entry in topic.entries) {
            val row = FeatureRowItemBinding.inflate(layoutInflater, binding.entriesContainer, true)
            row.rowTitle.setText(entry.titleRes)
            row.rowBody.text = formatBody(getString(entry.bodyRes))
        }
    }

    private fun formatBody(body: String): CharSequence {
        if (!body.contains('\n')) {
            return body
        }
        val gapWidth = (8 * resources.displayMetrics.density).toInt()
        val builder = SpannableStringBuilder()
        val lines = body.split('\n')
        for ((index, line) in lines.withIndex()) {
            val start = builder.length
            builder.append(line)
            val isLead = index == 0 && line.endsWith(":")
            if (!isLead) {
                builder.setSpan(
                    BulletSpan(gapWidth),
                    start,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (index != lines.lastIndex) {
                builder.append('\n')
            }
        }
        return builder
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TOPIC = "topic"
    }
}
