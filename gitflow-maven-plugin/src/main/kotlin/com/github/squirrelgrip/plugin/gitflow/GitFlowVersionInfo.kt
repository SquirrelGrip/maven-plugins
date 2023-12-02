package com.github.squirrelgrip.plugin.gitflow

import org.apache.maven.artifact.Artifact
import org.apache.maven.shared.release.policy.PolicyException
import org.apache.maven.shared.release.policy.version.VersionPolicy
import org.apache.maven.shared.release.policy.version.VersionPolicyRequest
import org.apache.maven.shared.release.versions.DefaultVersionInfo
import org.apache.maven.shared.release.versions.VersionParseException
import org.codehaus.plexus.util.StringUtils

/**
 * Git flow [org.apache.maven.shared.release.versions.VersionInfo]
 * implementation. Adds few convenient methods.
 *
 */
class GitFlowVersionInfo(
    version: String?, private val versionPolicy: VersionPolicy?
) : DefaultVersionInfo(version) {
    /**
     * Returns a new GitFlowVersionInfo that holds only digits in the version.
     *
     * @return Digits only GitFlowVersionInfo instance.
     */
    fun digitsVersionInfo(): GitFlowVersionInfo {
        return GitFlowVersionInfo(joinDigitString(digits), versionPolicy)
    }

    override fun getReleaseVersionString(): String =
        if (versionPolicy != null) {
            try {
                val request = VersionPolicyRequest().setVersion(this.toString())
                versionPolicy.getReleaseVersion(request).version
            } catch (ex: PolicyException) {
                throw RuntimeException("Unable to get release version from policy.", ex)
            } catch (ex: VersionParseException) {
                throw RuntimeException("Unable to get release version from policy.", ex)
            }
        } else {
            super.getReleaseVersionString()
        }

    /**
     * Gets next SNAPSHOT version.
     *
     * @param index
     * Which part of version to increment.
     * @return Next SNAPSHOT version.
     */
    fun nextSnapshotVersion(index: Int? = null): String? =
        nextVersion(index, true)

    /**
     * Gets next version. If index is `null` or not valid then it
     * delegates to [.getNextVersion] method.
     *
     * @param index
     * Which part of version to increment.
     * @param snapshot
     * Whether to use SNAPSHOT version.
     * @return Next version.
     */
    private fun nextVersion(index: Int?, snapshot: Boolean): String? {
        if (versionPolicy != null) {
            return try {
                val request = VersionPolicyRequest().setVersion(this.toString())
                if (snapshot) {
                    versionPolicy.getDevelopmentVersion(request).version
                } else {
                    versionPolicy.getReleaseVersion(request).version
                }
            } catch (ex: PolicyException) {
                throw RuntimeException("Unable to get development version from policy.", ex)
            } catch (ex: VersionParseException) {
                throw RuntimeException("Unable to get development version from policy.", ex)
            }
        }
        val digits = digits
        var nextVersion: String? = null
        if (digits != null) {
            if (index != null && index >= 0 && index < digits.size) {
                val origDigitsLength = joinDigitString(digits).length
                digits[index] = incrementVersionString(digits[index])
                for (i in index + 1 until digits.size) {
                    digits[i] = "0"
                }
                val digitsStr = joinDigitString(digits)
                nextVersion =
                    digitsStr + if (snapshot) snapshotVersionString.substring(origDigitsLength) else releaseVersionString.substring(
                        origDigitsLength
                    )
            } else {
                nextVersion =
                    if (snapshot) getNextVersion().snapshotVersionString else getNextVersion().releaseVersionString
            }
        } else {
            nextVersion = if (snapshot) snapshotVersionString else releaseVersionString
        }
        return nextVersion
    }

    /**
     * Gets version with appended feature name.
     *
     * @param featureName
     * Feature name to append.
     * @return Version with appended feature name.
     */
    fun featureVersion(featureName: String?): String {
        var version = toString()
        if (featureName != null) {
            version = (releaseVersionString + "-" + featureName
                + if (isSnapshot) "-" + Artifact.SNAPSHOT_VERSION else "")
        }
        return version
    }

    /**
     * Gets next hotfix version.
     *
     * @param preserveSnapshot
     * Whether to preserve SNAPSHOT in the version.
     * @param index
     * Which part of version to increment.
     * @return Next version.
     */
    fun hotfixVersion(preserveSnapshot: Boolean, index: Int?): String? =
        nextVersion(index, preserveSnapshot && isSnapshot)

    companion object {
        /**
         * Validates version.
         *
         * @param version
         * Version to validate.
         * @return `true` when version is valid, `false`
         * otherwise.
         */
        fun isValidVersion(version: String): Boolean {
            return (StringUtils.isNotBlank(version) && (ALTERNATE_PATTERN.matcher(version).matches() || STANDARD_PATTERN.matcher(version).matches()))
        }
    }
}
