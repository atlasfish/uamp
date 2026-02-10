/*
 * Copyright 2019 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

// The amount of time to wait for the album art file to download before timing out.
const val DOWNLOAD_TIMEOUT_SECONDS = 30L

internal class AlbumArtContentProvider : ContentProvider() {

    companion object {
        private val uriMap = mutableMapOf<Uri, Uri>()

        fun mapUri(uri: Uri): Uri {
            val path = uri.encodedPath
            if (path.isNullOrEmpty() || path.length <= 1) {
                return Uri.EMPTY
            }
            val contentPath = path.substring(1).replace('/', ':')
            val contentUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.example.android.uamp")
                .path(contentPath)
                .build()
            uriMap[contentUri] = uri
            return contentUri
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = this.context ?: return null

        // Remove the leading slash from the path to ensure it's treated as a relative path
        // within the cache directory, rather than an absolute path.
        val path = uri.path?.removePrefix("/") ?: return null
        var file = File(context.cacheDir, path)

        if (!file.exists()) {
            val remoteUri = uriMap[uri] ?: throw FileNotFoundException(uri.path)
            var lastException: Exception? = null
            var success = false

            // Retry loop to handle transient network failures
            for (i in 1..3) {
                try {
                    // Use Glide to download the album art.
                    // Use RequestOptions to set specific timeout and cache strategy
                    val requestOptions = RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.DATA) // Ensure we cache the original file data
                        .timeout(10000) // Set explicit connection timeout (10s)

                    val cacheFile = Glide.with(context)
                        .asFile()
                        .apply(requestOptions)
                        .load(remoteUri.toString()) // Convert Uri to String to ensure HttpUrlFetcher is used
                        .submit()
                        // Use a slightly shorter timeout for the get() than the global one,
                        // or rely on Glide's timeout.
                        // We give it enough time to complete the download.
                        .get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)

                    // Rename the file Glide created to match our own scheme.
                    if (cacheFile.renameTo(file)) {
                        success = true
                    } else {
                        // If rename fails, we use the cacheFile directly.
                        // This creates a temporary solution where we serve the file from Glide's cache,
                        // but we won't have it at our desired location for next time.
                        file = cacheFile
                        success = true
                    }

                    if (success) break

                } catch (e: Exception) {
                    lastException = e
                    if (e is java.util.concurrent.ExecutionException && e.cause is com.bumptech.glide.load.engine.GlideException) {
                        // Log but don't throw yet
                        val glideException = e.cause as com.bumptech.glide.load.engine.GlideException
                        glideException.logRootCauses("AlbumArtContentProvider - Attempt $i")
                    } else {
                        e.printStackTrace()
                    }

                    // Wait a bit before retrying
                    try { Thread.sleep(200) } catch (ignored: InterruptedException) {}
                }
            }

            if (!success) {
               val e = lastException ?: FileNotFoundException("Failed to download $remoteUri after 3 attempts")
               throw FileNotFoundException("Failed to download $remoteUri: ${e.message}")
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    override fun getType(uri: Uri): String? = null

}
