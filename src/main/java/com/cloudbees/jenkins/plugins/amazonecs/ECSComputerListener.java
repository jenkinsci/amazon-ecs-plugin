/*
 * The MIT License
 *
 *  Copyright (c) Amazon.com, Inc. or its affiliates. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ECSComputerListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(ECSComputerListener.class.getName());

    public static ECSComputerListener getInstance() {
        return ExtensionList.lookupSingleton(ECSComputerListener.class);
    }

    @Override
    public void onOffline(@NonNull Computer c, OfflineCause cause) {
        if (c instanceof ECSComputer) {
            LOGGER.log(Level.INFO, "{0} is offline. Cause: {1}", new Object[]{c.getName(), cause});
            ECSSlave node = ((ECSComputer) c).getNode();
            /* TODO: Use jenkins.util.Listeners.notify to broadcast events if there are multiple subscribers */
            terminateNodeIfUnsurvivable(node);
        }
    }

    private void terminateNodeIfUnsurvivable(ECSSlave node) {
        if (node != null && !node.isSurvivable()) {
            try {
                LOGGER.log(Level.INFO, "Terminating unsurvivable node {0}", new Object[]{node.getTaskArn()});
                node.terminate();
            } catch (InterruptedException | IOException e) {
                LOGGER.log(Level.WARNING, "Unable to terminate node {0}", new Object[]{e});
                throw new RuntimeException(e);
            }
        }
    }
}
