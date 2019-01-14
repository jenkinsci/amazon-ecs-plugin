/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.amazonecs.pipeline

import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.apache.commons.lang.RandomStringUtils;


public class ECSDeclarativeAgentScript extends DeclarativeAgentScript<ECSDeclarativeAgent> {
    public ECSDeclarativeAgentScript(CpsScript s, ECSDeclarativeAgent a) {
        super(s, a)
    }

    @Override
    public Closure run(Closure body) {
        return {
            try {
                if (describable.label == null || describable.label == "") {
                    String projectName = script.getProperty("currentBuild").projectName.toLowerCase().replaceAll("[^a-z-]", "-")
                    String number = script.getProperty("currentBuild").number
                    String label = projectName + "-" + number + "-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
                    describable.setLabel(label)
                }
                script.ecsTaskTemplate(describable.asArgs) {
                    script.node(describable.label) {
                        body.call()
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Result.FAILURE
                throw e
            }
        }
    }
}