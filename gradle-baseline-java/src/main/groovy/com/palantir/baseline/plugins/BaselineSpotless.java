/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.plugins;

import com.diffplug.gradle.spotless.KotlinExtension;
import com.diffplug.gradle.spotless.KotlinGradleExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.diffplug.spotless.FormatterProperties;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/**
 * Configures the Gradle 'com.diffplug.gradle.spotless' plugin with Baseline settings.
 */
public final class BaselineSpotless extends AbstractBaselinePlugin {
    public void apply(final Project project) {
        this.project = project;

        BaselineSpotlessExtension extension =
                project.getExtensions().create("baselineSpotless", BaselineSpotlessExtension.class);

        project.getPlugins().apply(SpotlessPlugin.class);

        File copyrightFile = findCopyrightFile();

        // afterEvaluate because it's not easy to re-configure
        project.afterEvaluate(p -> project.getExtensions().configure(SpotlessExtension.class, spotlessExtension -> {
            Path ktLintPropertiesFile = getSpotlessConfigDir().resolve("ktlint.properties");
            Optional<Map<String, String>> userData;
            if (Files.exists(ktLintPropertiesFile)) {
                userData = Optional.of(FormatterProperties
                        .from(ktLintPropertiesFile.toFile())
                        .getProperties()
                        .entrySet()
                        .stream()
                        .map(entry -> Maps.immutableEntry(entry.getKey().toString(), entry.getValue().toString()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
            } else {
                userData = Optional.empty();
            }

            // Kotlin
            Optional<String> ktlintVersion = Optional.ofNullable(extension.getKtlintVersion());
            spotlessExtension.kotlinGradle(kotlinGradleExtension -> {
                KotlinGradleExtension.KotlinFormatExtension ktlint =
                        ktlintVersion.map(kotlinGradleExtension::ktlint).orElseGet(kotlinGradleExtension::ktlint);
                userData.ifPresent(ktlint::userData);
            });
            spotlessExtension.kotlin(kotlinExtension -> {
                kotlinExtension.licenseHeaderFile(copyrightFile);
                KotlinExtension.KotlinFormatExtension ktlint =
                        ktlintVersion.map(kotlinExtension::ktlint).orElseGet(kotlinExtension::ktlint);
                userData.ifPresent(ktlint::userData);
            });

            // Gradle
            File grEclipsePropertiesFile = getSpotlessConfigDir().resolve("greclipse.properties").toFile();
            spotlessExtension.groovyGradle(groovyGradleExtension -> {
                groovyGradleExtension.target("*.gradle", "gradle/*.gradle");
                groovyGradleExtension
                        .greclipse()
                        .configFile(grEclipsePropertiesFile);
                groovyGradleExtension.indentWithSpaces(4);
            });
            spotlessExtension.groovy(groovyExtension -> {
                groovyExtension
                        .greclipse()
                        .configFile(grEclipsePropertiesFile);
                groovyExtension.licenseHeaderFile(copyrightFile);
            });
        }));
    }

    private File findCopyrightFile() {
        Path copyrightDir = Paths.get(getConfigDir()).resolve("copyright");
        Stream<Path> copyrightFiles;
        try {
            copyrightFiles = Files.list(copyrightDir);
        } catch (IOException e) {
            throw new GradleException("Couldn't list the copyright directory: " + copyrightDir, e);
        }
        return copyrightFiles
                .sorted()
                .findFirst()
                .orElseThrow(() -> new GradleException("Couldn't find any copyright inside " + copyrightDir))
                .toFile();
    }

    private Path getSpotlessConfigDir() {
        return Paths.get(getConfigDir()).resolve("spotless");
    }
}
