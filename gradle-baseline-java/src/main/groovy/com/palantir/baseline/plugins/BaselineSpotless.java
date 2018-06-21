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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the Gradle 'com.diffplug.gradle.spotless' plugin with Baseline settings.
 */
public final class BaselineSpotless extends AbstractBaselinePlugin {
    private static final Logger log = LoggerFactory.getLogger(BaselineSpotless.class);

    public void apply(final Project project) {
        this.project = project;

        BaselineSpotlessExtension extension =
                project.getExtensions().create("baselineSpotless", BaselineSpotlessExtension.class);

        project.getPlugins().apply(SpotlessPlugin.class);

        Optional<String> copyright = loadCopyright();

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
                // Don't use method reference, the return type is not public and they will break.
                copyright.ifPresent(licenseHeader -> kotlinExtension.licenseHeader(licenseHeader));
                KotlinExtension.KotlinFormatExtension ktlint =
                        ktlintVersion.map(kotlinExtension::ktlint).orElseGet(kotlinExtension::ktlint);
                userData.ifPresent(ktlint::userData);
            });

            // Gradle
            File grEclipsePropertiesFile = getSpotlessConfigDir().resolve("greclipse.properties").toFile();
            spotlessExtension.groovyGradle(groovyGradleExtension -> {
                groovyGradleExtension.target("*.gradle", "gradle/*.gradle");
                groovyGradleExtension.greclipse().configFile(grEclipsePropertiesFile);
                groovyGradleExtension.indentWithSpaces(4);
            });
            spotlessExtension.groovy(groovyExtension -> {
                groovyExtension.greclipse().configFile(grEclipsePropertiesFile);
                // Don't use method reference, the return type is not public and they will break.
                copyright.ifPresent(licenseHeader -> groovyExtension.licenseHeader(licenseHeader));
            });
        }));
    }

    /**
     * Loads the first copyright inside the {@code getConfigDir() + "/copyright"} directory and converts it to the
     * format that spotless expects - using {@code $YEAR} instead of {@code ${today.year}}.
     */
    private Optional<String> loadCopyright() {
        Path copyrightDir = Paths.get(getConfigDir()).resolve("copyright");
        if (!Files.isDirectory(copyrightDir)) {
            log.warn("Copyright directory doesn't exist: {}", copyrightDir);
            return Optional.empty();
        }
        try (Stream<Path> copyrightFiles = Files.list(copyrightDir)) {
            Optional<Path> fileOpt = copyrightFiles.sorted().findFirst();
            return fileOpt.map(file -> {
                try (Stream<String> lines = Files.lines(file)) {
                    return lines
                            .map(line -> line.replaceAll("\\$\\{today\\.year}", "$YEAR"))
                            .collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    throw new GradleException("Error while reading copyright file " + file, e);
                }
            });
        } catch (IOException e) {
            log.warn("Encountered exception listing copyright directory: {}", copyrightDir, e);
            return Optional.empty();
        }


    }

    private Path getSpotlessConfigDir() {
        return Paths.get(getConfigDir()).resolve("spotless");
    }
}
