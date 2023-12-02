package com.github.squirrelgrip.plugin.gitflow

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.shared.release.versions.VersionParseException
import org.codehaus.plexus.util.StringUtils
import org.codehaus.plexus.util.cli.CommandLineException

@Mojo(name = "release-start", aggregator = true)
class GitFlowReleaseStartMojo : AbstractGitFlowMojo() {
    /**
     * Whether to use the same name of the release branch for every release.
     * Default is `false`, i.e. project version will be added to
     * release branch prefix. <br></br>
     * Will have no effect if the `branchName` parameter is set.
     * <br></br>
     *
     * Note: By itself the default releaseBranchPrefix is not a valid branch
     * name. You must change it when setting sameBranchName to `true`
     * .
     *
     * @since 1.2.0
     */
    @Parameter(property = "sameBranchName", defaultValue = "false")
    private val sameBranchName = false

    /**
     * Whether to allow SNAPSHOT versions in dependencies.
     *
     * @since 1.2.2
     */
    @Parameter(property = "allowSnapshots", defaultValue = "false")
    private val allowSnapshots = false

    /**
     * Release version to use instead of the default next release version in non
     * interactive mode.
     *
     * @since 1.3.1
     */
    @Parameter(property = "releaseVersion", defaultValue = "")
    private val releaseVersion = ""
        get() {
            // get current project version from pom
            val currentVersion = currentProjectVersion
            val defaultVersion = if (tychoBuild) {
                currentVersion
            } else {
                // get default release version
                GitFlowVersionInfo(currentVersion, versionPolicy).getReleaseVersionString()
            }
            var version = if (settings!!.isInteractiveMode) {
                prompter!!.prompt("What is release version? [$defaultVersion]") { version: String ->
                    validVersion(
                        version
                    )
                }
            } else {
                field
            }
            if (StringUtils.isBlank(version)) {
                log.info("Version is blank. Using default version.")
                version = defaultVersion
            }
            return version
        }

    /**
     * Whether to push to the remote.
     *
     * @since 1.6.0
     */
    @Parameter(property = "pushRemote", defaultValue = "false")
    private val pushRemote = false

    /**
     * Whether to commit development version when starting the release (vs when
     * finishing the release which is the default). Has effect only when there
     * are separate development and production branches.
     *
     * @since 1.7.0
     */
    @Parameter(property = "commitDevelopmentVersionAtStart", defaultValue = "false")
    private var commitDevelopmentVersionAtStart = false

    /**
     * Whether to remove qualifiers from the next development version.
     *
     * @since 1.7.0
     */
    @Parameter(property = "digitsOnlyDevVersion", defaultValue = "false")
    private val digitsOnlyDevVersion = false

    /**
     * Development version to use instead of the default next development
     * version in non interactive mode.
     *
     * @since 1.7.0
     */
    @Parameter(property = "developmentVersion", defaultValue = "")
    private val developmentVersion = ""

    /**
     * Which digit to increment in the next development version. Starts from
     * zero.
     *
     * @since 1.7.0
     */
    @Parameter(property = "versionDigitToIncrement")
    private val versionDigitToIncrement: Int? = null

    /**
     * Start a release branch from this commit (SHA).
     *
     * @since 1.7.0
     */
    @Parameter(property = "fromCommit")
    private val fromCommit: String? = null

    /**
     * Whether to use snapshot in release.
     *
     * @since 1.10.0
     */
    @Parameter(property = "useSnapshotInRelease", defaultValue = "false")
    private val useSnapshotInRelease = false

    /**
     * Name of the created release branch.<br></br>
     * The effective branch name will be a composite of this branch name and the
     * `releaseBranchPrefix`.
     *
     * @since 1.14.0
     */
    @Parameter(property = "branchName")
    private val branchName: String? = null

    override fun execute() {
        validateConfiguration()
        try {
            // set git flow configuration
            initGitFlowConfig()

            // check uncommitted changes
            checkUncommittedChanges()
            val releaseBranch = gitFindBranches(gitFlowConfig.releaseBranchPrefix, true)
            if (StringUtils.isNotBlank(releaseBranch)) {
                throw MojoFailureException("Release branch already exists. Cannot start release.")
            }
            if (fetchRemote) {
                gitFetchRemoteAndCompareCreate(gitFlowConfig.developmentBranch)
            }
            val startPoint: String?
            startPoint = if (StringUtils.isNotBlank(fromCommit) && notSameProdDevName()) {
                fromCommit
            } else {
                gitFlowConfig.developmentBranch
            }

            // need to be in develop to check snapshots and to get correct project version
            gitCheckout(startPoint!!)

            // check snapshots dependencies
            if (!allowSnapshots) {
                checkSnapshotDependencies()
            }
            if (commitDevelopmentVersionAtStart && !notSameProdDevName()) {
                log.warn(
                    "The commitDevelopmentVersionAtStart will not have effect. "
                        + "It can be enabled only when there are separate branches for development and production."
                )
                commitDevelopmentVersionAtStart = false
            }

            // get release version
            val releaseVersion = releaseVersion

            // get release branch
            var fullBranchName: String? = gitFlowConfig.releaseBranchPrefix
            if (StringUtils.isNotBlank(branchName)) {
                fullBranchName += branchName
            } else if (!sameBranchName) {
                fullBranchName += releaseVersion
            }
            var projectVersion = releaseVersion
            if (useSnapshotInRelease && !ArtifactUtils.isSnapshot(projectVersion)) {
                projectVersion = projectVersion + "-" + Artifact.SNAPSHOT_VERSION
            }
            if (useSnapshotInRelease && mavenSession!!.userProperties["useSnapshotInRelease"] != null) {
                log.warn(
                    "The useSnapshotInRelease parameter is set from the command line."
                        + " Don't forget to use it in the finish goal as well."
                        + " It is better to define it in the project's pom file."
                )
            }
            if (commitDevelopmentVersionAtStart) {
                commitProjectVersion(projectVersion, commitMessages.releaseStartMessage)

                // git branch release/... ...
                gitCreateBranch(fullBranchName!!, startPoint)
                val nextSnapshotVersion = getNextSnapshotVersion(releaseVersion)
                commitProjectVersion(nextSnapshotVersion, commitMessages.releaseVersionUpdateMessage)

                // git checkout release/...
                gitCheckout(fullBranchName)
            } else {
                // git checkout -b release/... ...
                gitCreateAndCheckout(fullBranchName!!, startPoint)
                commitProjectVersion(projectVersion, commitMessages.releaseStartMessage)
            }
            if (installProject) {
                mvnCleanInstall()
            }
            if (pushRemote) {
                if (commitDevelopmentVersionAtStart) {
                    gitPush(gitFlowConfig.developmentBranch, false)
                }
                gitPush(fullBranchName, false)
            }
        } catch (e: CommandLineException) {
            throw MojoFailureException("release-start", e)
        } catch (e: VersionParseException) {
            throw MojoFailureException("release-start", e)
        }
    }

    fun getNextSnapshotVersion(currentVersion: String): String {
        val nextSnapshotVersion: String
        nextSnapshotVersion = if (!settings!!.isInteractiveMode && StringUtils.isNotBlank(developmentVersion)) {
            developmentVersion
        } else {
            var versionInfo = GitFlowVersionInfo(currentVersion, versionPolicy)
            if (digitsOnlyDevVersion) {
                versionInfo = versionInfo.digitsVersionInfo()
            }
            versionInfo.nextSnapshotVersion(versionDigitToIncrement)
        }
        if (StringUtils.isBlank(nextSnapshotVersion)) {
            throw MojoFailureException("Next snapshot version is blank.")
        }
        return nextSnapshotVersion
    }

    fun commitProjectVersion(version: String, commitMessage: String) {
        if (version != currentProjectVersion) {
            mvnSetVersions(version)
            gitCommit(commitMessage, mapOf("version" to version))
        }
    }
}
