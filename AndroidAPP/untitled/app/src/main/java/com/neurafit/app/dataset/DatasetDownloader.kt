package com.neurafit.app.dataset

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest

class DatasetDownloader {
    fun downloadManifestFiles(
        manifest: DatasetManifest,
        outputDirectory: File,
    ): List<File> {
        val safeDirectory = outputDirectory.canonicalFile
        if (!safeDirectory.exists() && !safeDirectory.mkdirs()) {
            error("Unable to create dataset output directory: ${safeDirectory.path}")
        }

        return manifest.files.map { datasetFile ->
            val outputFile = resolveSafeOutputFile(safeDirectory, datasetFile.fileName)
            downloadFile(
                url = datasetFile.url,
                outputFile = outputFile,
                expectedSha256 = datasetFile.sha256,
            )
            outputFile
        }
    }

    fun downloadFile(
        url: String,
        outputFile: File,
        expectedSha256: String? = null,
        maxBytes: Long = MAX_DOWNLOAD_BYTES,
    ) {
        val safeOutputFile = outputFile.canonicalFile
        val parentDirectory = safeOutputFile.parentFile ?: error("Dataset output file must have a parent directory.")
        if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
            error("Unable to create dataset output directory: ${parentDirectory.path}")
        }
        val tempFile = File.createTempFile("${safeOutputFile.name}.", ".download", parentDirectory)

        try {
            val connection = openValidatedConnection(url)
            try {
                val responseCode = connection.responseCode
                require(responseCode in 200..299) {
                    "Dataset download failed with HTTP $responseCode."
                }
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        copyBounded(input, output, maxBytes)
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!expectedSha256.isNullOrBlank()) {
                require(isSha256(expectedSha256)) {
                    "Dataset checksum must be a SHA-256 hex digest."
                }
                val actualSha256 = tempFile.sha256()
                require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "Dataset checksum mismatch for ${safeOutputFile.name}. Expected $expectedSha256 but got $actualSha256."
                }
            }

            moveIntoPlace(tempFile, safeOutputFile)
        } catch (throwable: Throwable) {
            tempFile.delete()
            throw throwable
        }
    }

    private fun openValidatedConnection(url: String, redirectsRemaining: Int = MAX_REDIRECTS): HttpURLConnection {
        val parsedUrl = URL(url)
        validateDownloadUrl(parsedUrl)
        val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = false
        }

        val responseCode = connection.responseCode
        if (responseCode in REDIRECT_STATUS_CODES) {
            require(redirectsRemaining > 0) {
                "Dataset download exceeded redirect limit."
            }
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) {
                "Dataset download redirect is missing a Location header."
            }
            val redirectedUrl = URL(parsedUrl, location).toString()
            return openValidatedConnection(redirectedUrl, redirectsRemaining - 1)
        }

        return connection
    }

    private fun validateDownloadUrl(url: URL) {
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Dataset downloads must use HTTPS."
        }
        val host = url.host
        require(!host.isNullOrBlank()) {
            "Dataset download URL must include a host."
        }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.none { it.isPrivateOrLocal() }) {
            "Dataset download URL must not resolve to a local or private address."
        }
    }

    private fun resolveSafeOutputFile(outputDirectory: File, fileName: String): File {
        require(fileName.isNotBlank()) {
            "Dataset file name must not be blank."
        }
        require(File(fileName).name == fileName && !fileName.contains('/') && !fileName.contains('\\')) {
            "Dataset file name must not contain path separators."
        }

        val outputFile = File(outputDirectory, fileName).canonicalFile
        require(outputFile.toPath().startsWith(outputDirectory.canonicalFile.toPath())) {
            "Dataset file must stay inside the output directory."
        }
        return outputFile
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long) {
        require(maxBytes > 0) {
            "Maximum dataset download size must be positive."
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalBytes += read
            require(totalBytes <= maxBytes) {
                "Dataset download exceeded ${maxBytes} bytes."
            }
            output.write(buffer, 0, read)
        }
    }

    private fun moveIntoPlace(tempFile: File, outputFile: File) {
        if (outputFile.exists() && !outputFile.delete()) {
            error("Unable to replace existing dataset file: ${outputFile.path}")
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun InetAddress.isPrivateOrLocal(): Boolean =
        isAnyLocalAddress ||
            isLoopbackAddress ||
            isLinkLocalAddress ||
            isSiteLocalAddress ||
            isMulticastAddress

    private fun isSha256(value: String): Boolean =
        SHA256_HEX.matches(value)

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_DOWNLOAD_BYTES = 100L * 1024L * 1024L
        const val MAX_REDIRECTS = 5
        val REDIRECT_STATUS_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
        val SHA256_HEX = Regex("^[A-Fa-f0-9]{64}$")
    }
}
