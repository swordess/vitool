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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.cloud.tools.jib.api.JibContainer
import com.google.cloud.tools.jib.json.JsonTemplate
import com.google.cloud.tools.jib.json.JsonTemplateMapper


class ImageMetadataOutput @JsonCreator internal constructor(
    @param:JsonProperty(value = "image", required = true) val image: String,
    @param:JsonProperty(value = "imageId", required = true) val imageId: String,
    @param:JsonProperty(value = "imageDigest", required = true) val imageDigest: String,
    @param:JsonProperty(value = "tags", required = true) val tags: List<String>,
    @param:JsonProperty(value = "imagePushed", required = true) val isImagePushed: Boolean
) : JsonTemplate {

    fun toJson(): String {
        return JsonTemplateMapper.toUtf8String(this)
    }

    companion object {

        fun fromJson(json: String): ImageMetadataOutput {
            return JsonTemplateMapper.readJson(json, ImageMetadataOutput::class.java)
        }

        /**
         * Create reproducible image build metadata from [JibContainer] information.
         *
         * @param jibContainer the metadata source
         * @return a json template populated with image metadata
         */
        fun fromJibContainer(jibContainer: JibContainer): ImageMetadataOutput {
            val image = jibContainer.targetImage.toString()
            val imageId = jibContainer.imageId.toString()
            val imageDigest = jibContainer.digest.toString()
            val imagePushed = jibContainer.isImagePushed

            // Make sure tags always appear in a predictable way, by sorting them into a list
            val tags: List<String> = jibContainer.tags.sorted()
            return ImageMetadataOutput(image, imageId, imageDigest, tags, imagePushed)
        }

    }

}