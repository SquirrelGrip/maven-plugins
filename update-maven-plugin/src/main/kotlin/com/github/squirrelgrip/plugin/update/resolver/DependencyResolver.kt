package com.github.squirrelgrip.plugin.update.resolver

import com.github.squirrelgrip.plugin.update.model.ArtifactDetails
import org.apache.maven.project.MavenProject

interface DependencyResolver {
    fun getDependencyArtifacts(
        project: MavenProject,
        processDependencies: Boolean,
        processDependencyManagement: Boolean,
        processTransitive: Boolean,
    ): Collection<ArtifactDetails>

    fun getPluginArtifacts(
        project: MavenProject,
        processPluginDependencies: Boolean,
        processPluginDependenciesInPluginManagement: Boolean,
    ): Collection<ArtifactDetails>
}
