// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PdfAnnotationSaveDestinationDao {
    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE sourceKey = :sourceKey LIMIT 1")
    fun findBySourceKey(sourceKey: String): PdfAnnotationSaveDestination?

    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE destinationUri = :destinationUri LIMIT 1")
    fun findByDestinationUri(destinationUri: String): PdfAnnotationSaveDestination?

    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE lastSavedHash = :hash LIMIT 1")
    fun findByLastSavedHash(hash: String): PdfAnnotationSaveDestination?

    @Query("UPDATE PdfAnnotationSaveDestination SET lastSavedHash = :newHash WHERE lastSavedHash = :oldHash")
    fun rekeyLastSavedHash(oldHash: String, newHash: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(destination: PdfAnnotationSaveDestination)

    @Query("DELETE FROM PdfAnnotationSaveDestination WHERE sourceKey = :sourceKey")
    fun deleteBySourceKey(sourceKey: String): Int

    @Query("DELETE FROM PdfAnnotationSaveDestination WHERE lastSavedHash = :hash")
    fun deleteByLastSavedHash(hash: String): Int

    @Query("DELETE FROM PdfAnnotationSaveDestination")
    fun deleteAll(): Int
}
