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


/** Builds messages that provides suggestions on how to fix the error.  */
class HelpfulSuggestions
/**
 * Creates a new [HelpfulSuggestions] with frontend-specific texts.
 *
 * @param messagePrefix the initial message text
 */(
    private val messagePrefix: String
) {
    fun forHttpHostConnect(): String {
        return suggest("make sure your Internet is up and that the registry you are pushing to exists")
    }

    fun forUnknownHost(): String {
        return suggest("make sure that the registry you configured exists/is spelled properly")
    }

    fun forHttpStatusCodeForbidden(imageReference: String): String =
        suggest("make sure you have permissions for $imageReference and set correct credentials.")

    fun forNoCredentialsDefined(imageReference: String): String =
        suggest("make sure your credentials for '$imageReference' are set up correctly.")

    fun forCredentialsNotSent(): String = suggest(
        "use a registry that supports HTTPS so credentials can be sent safely, or set the 'sendCredentialsOverHttp' system property to true"
    )

    fun forInsecureRegistry(): String =
        suggest("use a registry that supports HTTPS or set the configuration parameter 'allowInsecureRegistries'")

    fun none(): String = messagePrefix

    /**
     * Helper for suggestions with configured message prefix.
     *
     * @param suggestion a suggested fix for the problem described by [.messagePrefix]
     * @return the message containing the suggestion
     */
    private fun suggest(suggestion: String): String = "$messagePrefix, perhaps you should $suggestion"

}