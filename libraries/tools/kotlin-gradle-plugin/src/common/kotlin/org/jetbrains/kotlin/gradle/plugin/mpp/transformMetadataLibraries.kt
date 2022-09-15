/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution.ChooseVisibleSourceSets.MetadataProvider.JarMetadataProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution.ChooseVisibleSourceSets.MetadataProvider.ProjectMetadataProvider
import java.io.File

/**
 * Returns a map from 'visibleSourceSetName' to the transformed metadata libraries.
 * The map is necessary to support [MetadataDependencyTransformation]'s shape, which
 * is used in import and therefore hard to change.
 *
 * When this function will also support project dependencies and just return the compiled output files.
 */
internal fun Project.transformMetadataLibrariesForIde(
    resolution: MetadataDependencyResolution.ChooseVisibleSourceSets
): Map<String /* visibleSourceSetName */, Iterable<File>> {
    return when (val metadataProvider = resolution.metadataProvider) {
        is ProjectMetadataProvider -> resolution.visibleSourceSetNamesExcludingDependsOn.associateWith { visibleSourceSetName ->
            metadataProvider.getSourceSetCompiledMetadata(visibleSourceSetName)
        }

        is JarMetadataProvider -> transformMetadataLibrariesForIde(
            kotlinTransformedMetadataLibraryDirectoryForIde, resolution, metadataProvider
        )
    }
}

/**
 * Will transform the [CompositeMetadataArtifact] extracting the visible source sets specified in the [resolution]
 * @param materializeFiles: If true, the klib files will actually be created and extracted
 *
 * In case the [resolution] points to a project dependency, then the output file collections will be returned.
 */
internal fun Project.transformMetadataLibrariesForBuild(
    resolution: MetadataDependencyResolution.ChooseVisibleSourceSets, outputDirectory: File, materializeFiles: Boolean
): Iterable<File> {
    return when (resolution.metadataProvider) {
        is ProjectMetadataProvider -> project.files(
            resolution.visibleSourceSetNamesExcludingDependsOn.map { visibleSourceSetName ->
                resolution.metadataProvider.getSourceSetCompiledMetadata(visibleSourceSetName)
            }
        )

        is JarMetadataProvider -> transformMetadataLibrariesForBuild(
            resolution, outputDirectory, materializeFiles, resolution.metadataProvider
        )
    }
}

/* Implementations for transforming the Composite Metadata Artifact */

private fun transformMetadataLibrariesForIde(
    baseOutputDirectory: File,
    resolution: MetadataDependencyResolution.ChooseVisibleSourceSets,
    compositeMetadataArtifact: CompositeMetadataArtifact
): Map<String /* visibleSourceSetName */, Iterable<File>> {
    return compositeMetadataArtifact.read { artifactHandle ->
        resolution.visibleSourceSetNamesExcludingDependsOn.mapNotNull { visibleSourceSetName ->
            val sourceSet = artifactHandle.findSourceSet(visibleSourceSetName) ?: return@mapNotNull null
            val sourceSetMetadataLibrary = sourceSet.metadataLibrary ?: return@mapNotNull null
            val metadataLibraryOutputFile = baseOutputDirectory.resolve(sourceSetMetadataLibrary.relativeFile)
            metadataLibraryOutputFile.parentFile.mkdirs()
            if (!metadataLibraryOutputFile.exists()) {
                sourceSetMetadataLibrary.copyTo(metadataLibraryOutputFile)
                if (!metadataLibraryOutputFile.exists()) return@mapNotNull null
            }

            visibleSourceSetName to listOf(metadataLibraryOutputFile)
        }.toMap()
    }
}

private fun transformMetadataLibrariesForBuild(
    resolution: MetadataDependencyResolution.ChooseVisibleSourceSets,
    outputDirectory: File,
    materializeFiles: Boolean,
    compositeMetadataArtifact: CompositeMetadataArtifact
): Set<File> {
    return compositeMetadataArtifact.read { artifactHandle ->
        resolution.visibleSourceSetNamesExcludingDependsOn.mapNotNull { visibleSourceSetName ->
            val sourceSet = artifactHandle.findSourceSet(visibleSourceSetName) ?: return@mapNotNull null
            val metadataLibrary = sourceSet.metadataLibrary ?: return@mapNotNull null
            val metadataLibraryFile = outputDirectory.resolve(metadataLibrary.relativeFile)
            if (materializeFiles) {
                metadataLibraryFile.parentFile?.mkdirs()
                metadataLibrary.copyTo(metadataLibraryFile)
            }
            metadataLibraryFile
        }
    }.toSet()
}