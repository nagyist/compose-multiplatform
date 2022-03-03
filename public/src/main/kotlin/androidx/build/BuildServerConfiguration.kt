/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.build

import androidx.build.gradle.isRoot
import java.io.File
import org.gradle.api.Project

/**
 * @return build id string for current build
 *
 * The build server does not pass the build id so we infer it from the last folder of the
 * distribution directory name.
 */
fun getBuildId(): String {
    return if (System.getenv("BUILD_NUMBER") != null) {
        System.getenv("BUILD_NUMBER")
    } else {
        "0"
    }
}

/**
 * Gets set to true when the build id is prefixed with P.
 *
 * In AffectedModuleDetector, we return a different ProjectSubset in presubmit vs.
 * postsubmit, to get the desired test behaviors.
 */
fun isPresubmitBuild(): Boolean {
    return if (System.getenv("BUILD_NUMBER") != null) {
        System.getenv("BUILD_NUMBER").startsWith("P")
    } else {
        false
    }
}

/**
 * The DIST_DIR is where you want to save things from the build. The build server will copy
 * the contents of DIST_DIR to somewhere and make it available.
 */
fun Project.getDistributionDirectory(): File {
    val envVar = project.providers.environmentVariable("DIST_DIR").getOrElse("")
    return if (envVar != "") {
        File(envVar)
    } else {
        File(getRootOutDirectory(), "dist")
    }.also { distDir ->
        distDir.mkdirs()
    }
}

/**
 * Returns the out directory (an ancestor of all files generated by the build)
 */
fun Project.getRootOutDirectory(): File {
    return project.rootProject.extensions.extraProperties.get("outDir") as File
}

/**
 * Directory to put build info files for release service dependency files.
 */
fun Project.getBuildInfoDirectory(): File =
    File(getDistributionDirectory(), "build-info")

/**
 * Directory for android test configuration files that get consumed by Tradefed in CI. These
 * configs cause all the tests to be run, except in cases where buildSrc changes.
 */
fun Project.getTestConfigDirectory(): File =
    File(getDistributionDirectory(), "test-xml-configs")

/**
 * Directory for android test configuration files that get consumed by Tradefed in CI. These
 * "constrained" configs cause only small and medium tests to be run for dependent projects.
 */
fun Project.getConstrainedTestConfigDirectory(): File =
    File(getDistributionDirectory(), "constrained-test-xml-configs")

/**
 * Directory to put release note files for generate release note tasks.
 */
fun Project.getReleaseNotesDirectory(): File =
    File(getDistributionDirectory(), "release-notes")

/**
 * Directory to put host test results so they can be consumed by the testing dashboard.
 */
fun Project.getHostTestResultDirectory(): File =
    File(getDistributionDirectory(), "host-test-reports")

/**
 * Directory to put json metrics so they can be consumed by the metrics dashboards.
 */
fun Project.getLibraryMetricsDirectory(): File =
    File(getDistributionDirectory(), "librarymetrics")

/**
 * Directory to put test apk hash json files.
 */
fun Project.getApkHashDumpDirectory(): File =
    File(getDistributionDirectory(), "test-apk-hashes")

/**
 * Directory to put json metrics so they can be consumed by the metrics dashboards.
 */
fun Project.getLibraryReportsDirectory(): File =
    File(getDistributionDirectory(), "libraryreports")

/**
 * Whether the build should force all versions to be snapshots.
 */
fun isSnapshotBuild() = System.getenv("SNAPSHOT") != null

/**
 * Directory in a maven format to put all the publishing libraries.
 */
fun Project.getRepositoryDirectory(): File {
    val actualRootProject = if (project.isRoot) project else project.rootProject
    val directory = if (isSnapshotBuild()) {
        // For snapshot builds we put artifacts directly where downstream users can find them.
        File(actualRootProject.getDistributionDirectory(), "repository")
    } else {
        File(actualRootProject.buildDir, "support_repo")
    }
    directory.mkdirs()
    return directory
}
