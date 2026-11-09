// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.crash

import android.content.Context
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.config.CoreConfiguration
import org.acra.config.ReportingAdministrator
import org.acra.file.CrashReportPersister
import org.acra.interaction.ReportInteraction
import org.acra.startup.Report
import org.acra.startup.StartupProcessor
import java.io.File

class CrashConsentGuard : ReportingAdministrator, StartupProcessor {

    override fun shouldStartCollecting(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
    ): Boolean {
        if (reportBuilder.isSendSilently) {
            return false
        }
        return config.pluginLoader.loadEnabled(config, ReportInteraction::class.java).isNotEmpty()
    }

    override fun processReports(
        context: Context,
        config: CoreConfiguration,
        reports: List<Report>,
    ) {
        for (report in reports) {
            if (!consented(report.file)) {
                report.delete = true
            }
        }
    }

    private fun consented(file: File): Boolean {
        val stored = runCatching {
            CrashReportPersister().load(file).get(ReportField.IS_SILENT.name)
        }.getOrNull() ?: return false
        return stored.toString().toBoolean().not()
    }
}
