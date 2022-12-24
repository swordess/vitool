/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.swordess.common.vitool.ported.jib

import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.http.ResponseException
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException
import org.apache.http.conn.HttpHostConnectException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutionException


/** Wraps an exception that happens during containerization.  */
class BuildStepsExecutionException internal constructor(message: String, cause: Throwable) :
    Exception(message, cause)

/** Runs Jib and builds helpful error messages.  */
class JibBuildRunner internal constructor(
    private val jibContainerBuilder: JibContainerBuilder,
    private val containerizer: Containerizer,
    private val logger: (LogEvent)->Unit,
    private val helpfulSuggestions: HelpfulSuggestions,
    private val startupMessage: String,
    private val successMessage: String
) {

    private var imageDigestOutputPath: Path? = null
    private var imageIdOutputPath: Path? = null
    private var imageJsonOutputPath: Path? = null

    /**
     * Runs the Jib build.
     *
     * @return the built [JibContainer]
     */
    fun runBuild(): JibContainer {
        try {
            logger.invoke(LogEvent.lifecycle(""))
            logger.invoke(LogEvent.lifecycle(startupMessage))
            val jibContainer = jibContainerBuilder.containerize(containerizer)
            logger.invoke(LogEvent.lifecycle(""))
            logger.invoke(LogEvent.lifecycle(successMessage))

            // when an image is built, write out the digest and id
            imageDigestOutputPath?.let {
                val imageDigest = jibContainer.digest.toString()
                Files.write(it, imageDigest.toByteArray(StandardCharsets.UTF_8))
            }
            imageIdOutputPath?.let {
                val imageId = jibContainer.imageId.toString()
                Files.write(it, imageId.toByteArray(StandardCharsets.UTF_8))
            }
            imageJsonOutputPath?.let {
                val metadataOutput: ImageMetadataOutput = ImageMetadataOutput.fromJibContainer(jibContainer)
                val imageJson: String = metadataOutput.toJson()
                Files.write(it, imageJson.toByteArray(StandardCharsets.UTF_8))
            }
            return jibContainer
        } catch (ex: HttpHostConnectException) {
            // Failed to connect to registry.
            throw BuildStepsExecutionException(helpfulSuggestions.forHttpHostConnect(), ex)
        } catch (ex: RegistryUnauthorizedException) {
            handleRegistryUnauthorizedException(ex, helpfulSuggestions)
        } catch (ex: RegistryCredentialsNotSentException) {
            throw BuildStepsExecutionException(helpfulSuggestions.forCredentialsNotSent(), ex)
        } catch (ex: RegistryAuthenticationFailedException) {
            if (ex.cause is ResponseException) {
                handleRegistryUnauthorizedException(
                    RegistryUnauthorizedException(
                        ex.serverUrl, ex.imageName, ex.cause as ResponseException?
                    ),
                    helpfulSuggestions
                )
            } else {
                // Unknown cause
                throw BuildStepsExecutionException(helpfulSuggestions.none(), ex)
            }
        } catch (ex: UnknownHostException) {
            throw BuildStepsExecutionException(helpfulSuggestions.forUnknownHost(), ex)
        } catch (ex: InsecureRegistryException) {
            throw BuildStepsExecutionException(helpfulSuggestions.forInsecureRegistry(), ex)
        } catch (ex: RegistryException) {
            throw BuildStepsExecutionException(ex.message!!, ex)
        } catch (ex: ExecutionException) {
            throw BuildStepsExecutionException(ex.cause?.message ?: "(null exception message)", ex.cause!!)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BuildStepsExecutionException(helpfulSuggestions.none(), ex)
        }
        throw IllegalStateException("unreachable")
    }

    /**
     * Set the location where the image digest will be saved. If `null` then digest is not
     * saved.
     *
     * @param imageDigestOutputPath the location to write the image digest or `null` to skip
     * @return this
     */
    fun writeImageDigest(imageDigestOutputPath: Path?): JibBuildRunner {
        this.imageDigestOutputPath = imageDigestOutputPath
        return this
    }

    /**
     * Set the location where the image id will be saved. If `null` then digest is not saved.
     *
     * @param imageIdOutputPath the location to write the image id or `null` to skip
     * @return this
     */
    fun writeImageId(imageIdOutputPath: Path?): JibBuildRunner {
        this.imageIdOutputPath = imageIdOutputPath
        return this
    }

    /**
     * Set the location where the image metadata json will be saved. If `null` then the metadata
     * is not saved.
     *
     * @param imageJsonOutputPath the location to write the image metadata, or `null` to skip
     * @return this
     */
    fun writeImageJson(imageJsonOutputPath: Path?): JibBuildRunner {
        this.imageJsonOutputPath = imageJsonOutputPath
        return this
    }

    companion object {

        private const val STARTUP_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY = "Containerizing application to "
        private const val SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY = "Built and pushed image as "

        private fun colorCyan(innerText: CharSequence): CharSequence {
            return StringBuilder().append("\u001B[36m").append(innerText).append("\u001B[0m")
        }

        private fun buildMessageWithTargetImageReferences(
            targetImageReference: ImageReference,
            additionalTags: Set<String>,
            prefix: String,
            suffix: String
        ): String {
            val successMessageBuilder = StringJoiner(", ", prefix, suffix)
            successMessageBuilder.add(colorCyan(targetImageReference.toString()))
            for (tag in additionalTags) {
                successMessageBuilder.add(colorCyan(targetImageReference.withQualifier(tag).toString()))
            }
            return successMessageBuilder.toString()
        }

        /**
         * Creates a runner to build an image. Creates a directory for the cache, if needed.
         *
         * @param jibContainerBuilder the [JibContainerBuilder]
         * @param containerizer the [Containerizer]
         * @param logger consumer for handling log events
         * @param helpfulSuggestions suggestions to use in help messages for exceptions
         * @param targetImageReference the target image reference
         * @param additionalTags additional tags to push to
         * @return a [JibBuildRunner] for building to a registry
         */
        fun forBuildImage(
            jibContainerBuilder: JibContainerBuilder,
            containerizer: Containerizer,
            logger: (LogEvent)->Unit,
            helpfulSuggestions: HelpfulSuggestions,
            targetImageReference: ImageReference,
            additionalTags: Set<String>
        ): JibBuildRunner {
            return JibBuildRunner(
                jibContainerBuilder,
                containerizer,
                logger,
                helpfulSuggestions,
                buildMessageWithTargetImageReferences(
                    targetImageReference,
                    additionalTags,
                    STARTUP_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY,
                    "..."
                ),
                buildMessageWithTargetImageReferences(
                    targetImageReference, additionalTags, SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY, ""
                )
            )
        }

        private fun handleRegistryUnauthorizedException(
            registryUnauthorizedException: RegistryUnauthorizedException,
            helpfulSuggestions: HelpfulSuggestions
        ) {
            if (registryUnauthorizedException.httpResponseException.statusCode == 403) {
                // No permissions for registry/repository.
                throw BuildStepsExecutionException(
                    helpfulSuggestions.forHttpStatusCodeForbidden(
                        registryUnauthorizedException.imageReference
                    ),
                    registryUnauthorizedException
                )
            } else {
                throw BuildStepsExecutionException(
                    helpfulSuggestions.forNoCredentialsDefined(
                        registryUnauthorizedException.imageReference
                    ),
                    registryUnauthorizedException
                )
            }
        }
    }

}
