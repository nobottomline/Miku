package miku.server.service

import io.minio.*
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream

class MinioStorageService(
    endpoint: String = System.getenv("MINIO_ENDPOINT") ?: "http://localhost:9000",
    accessKey: String = System.getenv("MINIO_ACCESS_KEY") ?: "miku-admin",
    secretKey: String = System.getenv("MINIO_SECRET_KEY") ?: "miku-secret-key",
    private val bucket: String = System.getenv("MINIO_BUCKET") ?: "miku-storage",
) {
    private val logger = LoggerFactory.getLogger(MinioStorageService::class.java)
    private val client: MinioClient?

    init {
        client = try {
            val c = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build()

            // Ensure bucket exists
            if (!c.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                c.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
                logger.info("Created MinIO bucket: $bucket")
            }

            logger.info("MinIO connected at $endpoint (bucket: $bucket)")
            c
        } catch (e: Exception) {
            logger.warn("MinIO not available at $endpoint — object storage disabled: ${e.message}")
            null
        }
    }

    /**
     * Upload a file to MinIO.
     * @param objectName The path in the bucket (e.g. "apk/extension-name.apk")
     * @param file The local file to upload
     * @param contentType MIME type
     */
    fun uploadFile(objectName: String, file: File, contentType: String = "application/octet-stream"): Boolean {
        if (client == null) return false
        return try {
            client.uploadObject(
                UploadObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .filename(file.absolutePath)
                    .contentType(contentType)
                    .build()
            )
            logger.debug("Uploaded to MinIO: $objectName (${file.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            logger.error("MinIO upload failed: $objectName", e)
            false
        }
    }

    /**
     * Upload bytes to MinIO.
     */
    fun uploadBytes(objectName: String, data: ByteArray, contentType: String = "application/octet-stream"): Boolean {
        if (client == null) return false
        return try {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .stream(data.inputStream(), data.size.toLong(), -1)
                    .contentType(contentType)
                    .build()
            )
            true
        } catch (e: Exception) {
            logger.error("MinIO upload failed: $objectName", e)
            false
        }
    }

    /**
     * Download a file from MinIO.
     */
    fun downloadFile(objectName: String, targetFile: File): Boolean {
        if (client == null) return false
        return try {
            client.downloadObject(
                DownloadObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .filename(targetFile.absolutePath)
                    .build()
            )
            logger.debug("Downloaded from MinIO: $objectName -> ${targetFile.name}")
            true
        } catch (e: Exception) {
            logger.debug("MinIO download failed: $objectName: ${e.message}")
            false
        }
    }

    /**
     * Get an input stream for an object.
     */
    fun getObject(objectName: String): InputStream? {
        if (client == null) return null
        return try {
            client.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .build()
            )
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchKey") null
            else throw e
        }
    }

    /**
     * Check if an object exists in MinIO.
     */
    fun exists(objectName: String): Boolean {
        if (client == null) return false
        return try {
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .build()
            )
            true
        } catch (e: ErrorResponseException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete an object from MinIO.
     */
    fun deleteObject(objectName: String): Boolean {
        if (client == null) return false
        return try {
            client.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .build()
            )
            true
        } catch (e: Exception) {
            logger.error("MinIO delete failed: $objectName", e)
            false
        }
    }

    fun isAvailable(): Boolean = client != null
}
