// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.support

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataCallback
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataService

class RecordingMetadataService : IPdfMetadataService.Stub() {
    var processFailure: RemoteException? = null
    var linkFailure: RemoteException? = null
    var registration: Pair<Long, Long>? = null
    val processCalls = mutableListOf<Pair<SourceFingerprint, Int>>()
    val acknowledgements = mutableListOf<Pair<SourceFingerprint, Int>>()
    val memoryRequests = mutableListOf<Pair<Long, Long>>()

    override fun queryLocalInterface(descriptor: String): IInterface = this

    override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
        linkFailure?.let { throw it }
    }

    override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true

    override fun registerCallback(callback: IPdfMetadataCallback?, generation: Long, connectionToken: Long) {
        registration = generation to connectionToken
    }

    override fun sampleMemory(generation: Long, connectionToken: Long) {
        memoryRequests += generation to connectionToken
    }

    override fun process(
        generation: Long,
        connectionToken: Long,
        path: String,
        size: Long,
        lastModified: Long,
        debugBehavior: Int,
    ) {
        processCalls += SourceFingerprint(path, size, lastModified) to debugBehavior
        processFailure?.let { throw it }
    }

    override fun acknowledge(
        generation: Long,
        connectionToken: Long,
        path: String,
        size: Long,
        lastModified: Long,
        action: Int,
    ) {
        acknowledgements += SourceFingerprint(path, size, lastModified) to action
    }
}

class MetadataBindingContext : ContextWrapper(null) {
    var bindingAllowed = true
    var bindingFailure: SecurityException? = null
    val connections = mutableListOf<ServiceConnection>()
    val unbound = mutableListOf<ServiceConnection>()

    override fun getApplicationContext(): Context = this

    override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
        connections += connection
        bindingFailure?.let { throw it }
        return bindingAllowed
    }

    override fun unbindService(connection: ServiceConnection) {
        unbound += connection
    }
}
