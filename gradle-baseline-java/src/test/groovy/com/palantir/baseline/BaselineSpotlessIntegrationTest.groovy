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

package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineSpotless
import nebula.test.IntegrationSpec
import org.apache.commons.io.FileUtils
import org.gradle.api.logging.LogLevel
import org.gradle.language.base.plugins.LifecycleBasePlugin

class BaselineSpotlessIntegrationTest extends IntegrationSpec {
    def license = '''
        /**
        (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at
        
            http://www.apache.org/licenses/LICENSE-2.0
        
        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
        */
    '''.stripIndent()

    def setup() {
        logLevel = LogLevel.DEBUG
        buildFile << '''
            plugins { id 'nebula.kotlin' version '1.2.40' apply false }
            // Need this to resolve things like kotlin-stdlib-jdk8
            repositories { jcenter() }
        '''.stripIndent()
        buildFile << applyPlugin(BaselineSpotless) + "\n"

        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

    def runsOnCheck() {
        buildFile << applyPlugin(LifecycleBasePlugin) + "\n"

        when:
        def result = runTasksSuccessfully("check")

        then:
        result.wasExecuted(':spotlessCheck')
    }

    def lintsKotlin() {
        setupFixableKotlin()

        when:
        def result = runTasksWithFailure("check")

        then:
        result.wasExecuted(':spotlessCheck')
        result.wasExecuted(':spotlessKotlin')
    }

    def fixesKotlin() {
        setupFixableKotlin()

        when:
        def result = runTasks("spotlessApply")
        println(result.standardOutput)
        println(result.standardError)

        then:
        result.wasExecuted(':spotlessApply')
//        result.wasExecuted(':spotlessKotlin')
        file("src/main/kotlin/foo/BadlyFormatted.kt").text.endsWith '''
            package foo
            
            class BadlyFormatted {
                val x = 5
                val y = 9
            }
        '''.stripIndent()
    }

    private void setupFixableKotlin() {
        buildFile << applyPlugin(LifecycleBasePlugin) + "\n"
        buildFile << """apply plugin: "nebula.kotlin"\n"""

        createFile("src/main/kotlin/foo/BadlyFormatted.kt") << license << '''
            package foo;

            class BadlyFormatted   {
                val x =  5
                val y =   9;
            }
        '''.stripIndent()
    }

}
