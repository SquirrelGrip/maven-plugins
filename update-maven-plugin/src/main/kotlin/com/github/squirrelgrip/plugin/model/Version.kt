package com.github.squirrelgrip.plugin.model

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import org.apache.maven.project.MavenProject

data class Version(
    val value: String,
) : Comparable<Version> {
    private val semver: Semver by lazy {
        try {
            Semver(value, Semver.SemverType.LOOSE)
        } catch (e: SemverException) {
            Semver("0", Semver.SemverType.LOOSE)
        }
    }

    val major: Int by lazy { semver.major }
    val minor: Int by lazy { semver.minor ?: 0 }
    val patch: Int by lazy { semver.patch ?: 0 }

    companion object {
        val NO_VERSION = Version("0")
        val PROPERTY_REGEX = Regex(".*\\$\\{(.+?)\\}.*")
    }

    fun resolve(project: MavenProject): Version =
        Version(
            value.replace(PROPERTY_REGEX) {
                val key = it.groupValues[1]
                val replacement = project.properties[key.trim()]?.toString() ?: "\${$key}"
                value.replace(PROPERTY_REGEX, replacement)
            }
        )

    override fun compareTo(other: Version): Int =
        semver.compareTo(other.semver)

    override fun toString(): String =
        if (value == "0")
            ""
        else
            value
}
