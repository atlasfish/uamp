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
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

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

            // Use Coil to download the album art.
            val imageLoader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(remoteUri.toString())
                .diskCachePolicy(CachePolicy.ENABLED) // Ensure we download to disk
                .memoryCachePolicy(CachePolicy.DISABLED) // We just need the file
                .build()

            try {
                // Execute the request synchronously
                val result = runBlocking {
                     imageLoader.execute(request)
                }

                if (result is SuccessResult) {
                    val diskCacheKey = result.diskCacheKey
                    if (diskCacheKey != null) {
                        val snapshot = imageLoader.diskCache?.openSnapshot(diskCacheKey)
                        if (snapshot != null) {
                            val data = snapshot.data
                            // Copy the file from Coil's cache to our destination
                            // Coil 2.0+ uses java.nio.file.Path for data
                            val sourceFile = data.toFile()

                            file.parentFile?.mkdirs()
                            sourceFile.copyTo(file, overwrite = true)

                            // Close the snapshot
                            snapshot.close()
                        } else {
                            throw FileNotFoundException("File not found in Coil disk cache")
                        }
                    } else {
                         throw FileNotFoundException("Coil did not return a disk cache key")
                    }
                } else if (result is ErrorResult) {
                    throw FileNotFoundException("Coil failed to download image: ${result.throwable.message}")
                }

            } catch (e: Exception) {
                val errorMessage = "Failed to download $remoteUri with Coil: ${e.message}"
                System.err.println(errorMessage)
                throw FileNotFoundException(errorMessage)
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
