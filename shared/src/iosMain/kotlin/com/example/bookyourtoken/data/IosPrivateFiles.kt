package com.example.bookyourtoken.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

/**
 * Application Support/qr inside the app's sandbox: excluded from iCloud/iTunes backups, and written
 * with complete file protection (unreadable while the phone is locked).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPrivateFiles : PrivateFiles {

    private val dir: NSURL? by lazy {
        val fileManager = NSFileManager.defaultManager
        val base = fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val qr = base?.URLByAppendingPathComponent("qr", isDirectory = true) ?: return@lazy null
        fileManager.createDirectoryAtURL(qr, withIntermediateDirectories = true, attributes = null, error = null)
        qr.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        qr
    }

    private fun url(name: String): NSURL? = dir?.URLByAppendingPathComponent(name)

    override fun read(name: String): ByteArray? {
        val data = url(name)?.let { NSData.dataWithContentsOfURL(it) } ?: return null
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    override fun write(name: String, bytes: ByteArray) {
        val target = url(name) ?: error("App storage isn't available")
        val written = bytes.toNSData().writeToURL(
            target,
            options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete,
            error = null
        )
        if (!written) error("Couldn't save $name")
    }

    override fun delete(name: String) {
        url(name)?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
    }

    @OptIn(BetaInteropApi::class)
    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
