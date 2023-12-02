package com.github.squirrelgrip.plugin.update.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper

data class PluginManagements(
    @JsonProperty("pluginManagement")
    @JacksonXmlElementWrapper(useWrapping = false)
    val pluginManagement: List<UpdateArtifact>? = emptyList(),
)
