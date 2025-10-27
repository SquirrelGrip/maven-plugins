package com.github.squirrelgrip.plugin.model

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import org.apache.maven.project.MavenProject

data class Version(
    val value: String
) : Comparable<Version> {
    private val semver: Semver by lazy {
        try {
            Semver(value, Semver.SemverType.STRICT)
        } catch (_: SemverException) {
            try {
                Semver(value, Semver.SemverType.LOOSE)
            } catch (_: SemverException) {
                Semver("0", Semver.SemverType.LOOSE)
            }
        }
    }

    val major: Int by lazy { semver.major }
    val minor: Int by lazy { semver.minor ?: 0 }
    val patch: Int by lazy { semver.patch ?: 0 }
    val suffixTokens: Array<String> by lazy { semver.suffixTokens ?: emptyArray<String>() }
    val suffix: Int by lazy { suffixTokens.toVersion() }

    companion object {
        val NO_VERSION = Version("0")
        val PROPERTY_REGEX = Regex(".*\\$\\{(.+?)}.*")
    }

    fun resolve(project: MavenProject): Version =
        Version(
            value.replace(PROPERTY_REGEX) {
                val key = it.groupValues[1]
                val replacement = project.properties[key.trim()]?.toString() ?: $$"${$$key}"
                value.replace(PROPERTY_REGEX, replacement)
            }
        )

    override fun compareTo(other: Version): Int {
        if (major == other.major) {
            if (minor == other.minor) {
                if (patch == other.patch) {
                    return suffix - other.suffix
                }
                return patch - other.patch
            } else {
                return minor - other.minor
            }
        } else {
            return major - other.major
        }
    }

    override fun toString(): String =
        if (value == "0") {
            ""
        } else {
            value
        }
}

private fun Array<String>.toVersion(): Int = this.filter { it.toIntOrNull() != null }.joinToString("").toIntOrNull() ?: 0
