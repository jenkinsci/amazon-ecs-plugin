/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
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

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Amazon EC2 Container Service implementation of {@link hudson.model.Computer}
 *
 * This Computer should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSComputer extends AbstractCloudComputer {
    private static final Logger LOGGER = Logger.getLogger(ECSComputer.class.getName());

    public ECSComputer(ECSSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);

        LOGGER.log(Level.FINE, "Computer {0} taskAccepted", this);
        
        // Now that we have a task, we want to make sure to tell Jenkins
        // that this computer is no longer accepting any additional tasks.
        setAcceptingTasks(false);
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        
        LOGGER.log(Level.FINE, "Computer {0} taskCompleted", this);
        
        terminate();
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        
        LOGGER.log(Level.FINE, "Computer {0} taskCompletedWithProblems", this);
        
        terminate();
    }

    /**
     * Computer is terminated after build completion so we enforce it will only be used once.
     */
    private void terminate() {
        LOGGER.log(Level.INFO, "Attempting to terminate the node for computer: {0}", this);
        
        // The task has been completed, so we want to make sure to tell Jenkins
        // that this computer is no longer accepting tasks.
        AbstractCloudSlave node = getNode();
        if( node != null ) {
            LOGGER.log(Level.INFO, "Terminating the node for computer: {0}", this);
            try {
                node.terminate();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Failed to terminate computer: " + getName(), e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to terminate computer: " + getName(), e);
            }
        } else {
            LOGGER.log(Level.WARNING, "There is no node for computer: {0}", this);
        }
    }
}
