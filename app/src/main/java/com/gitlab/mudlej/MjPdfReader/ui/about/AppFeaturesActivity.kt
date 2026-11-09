// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.AboutRowItemBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityAppFeaturesBinding

class AppFeaturesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppFeaturesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppFeaturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()
        bindTopics()
    }

    private fun bindTopics() {
        for (topic in FeatureTopic.entries) {
            val row = AboutRowItemBinding.inflate(layoutInflater, binding.topicsContainer, true)
            row.rowIcon.setImageResource(topic.iconRes)
            row.rowTitle.setText(topic.titleRes)
            row.rowSubtitle.setText(topic.subtitleRes)
            row.rowSubtitle.visibility = View.VISIBLE
            row.root.setOnClickListener {
                startActivity(
                    Intent(this, FeatureTopicActivity::class.java)
                        .putExtra(FeatureTopicActivity.EXTRA_TOPIC, topic.name)
                )
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
