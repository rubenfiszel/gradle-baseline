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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class NoUnusedPinCheckTask extends DefaultTask {

    @TaskAction
    public final void checkNoUnusedPin() {
        Set<String> artifacts = BaselineVersions.getResolvedArtifacts(getProject());
        List<String> unusedProps = new LinkedList<>();
        BaselineVersions.checkVersionsProp(getProject(),
                pair -> {
                    String propName = pair.getLeft();
                    String regex = propName.replaceAll("\\*", ".*");
                    if (!artifacts.stream().anyMatch(artifact -> artifact.matches(regex))) {
                        unusedProps.add(propName);
                    }
                    return null;
                });

        if (!unusedProps.isEmpty()) {
            String unusedPropsString = unusedProps.stream().collect(Collectors.joining("\n"));
            throw new RuntimeException("There are unused pins in your versions.props: \n" + unusedPropsString);
        }
    }

}