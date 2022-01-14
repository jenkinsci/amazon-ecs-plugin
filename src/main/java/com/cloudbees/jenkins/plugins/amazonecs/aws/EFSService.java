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

package com.cloudbees.jenkins.plugins.amazonecs.aws;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.amazonaws.services.elasticfilesystem.AmazonElasticFileSystem;
import com.amazonaws.services.elasticfilesystem.AmazonElasticFileSystemClientBuilder;
import com.amazonaws.services.elasticfilesystem.model.AccessPointDescription;
import com.amazonaws.services.elasticfilesystem.model.DescribeAccessPointsRequest;
import com.amazonaws.services.elasticfilesystem.model.DescribeAccessPointsResult;
import com.amazonaws.services.elasticfilesystem.model.DescribeFileSystemsRequest;
import com.amazonaws.services.elasticfilesystem.model.DescribeFileSystemsResult;
import com.amazonaws.services.elasticfilesystem.model.FileSystemDescription;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import org.apache.commons.lang.StringUtils;

/**
 * Encapsulates interactions with Amazon EFS.
 *
 * @author Dan Krieger {@literal <dan@codingislife.com> }
 */
public class EFSService extends BaseAWSService {
    private static final Logger LOGGER = Logger.getLogger(EFSService.class.getName());

    @Nonnull
    private final Supplier<AmazonElasticFileSystem> clientSupplier;

    public EFSService(String credentialsId, String regionName) {
        this.clientSupplier = () -> {
            AmazonElasticFileSystemClientBuilder builder = AmazonElasticFileSystemClientBuilder
                    .standard()
                    .withClientConfiguration(createClientConfiguration())
                    .withRegion(regionName);

            AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
            if (credentials != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                    String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                    LOGGER.log(Level.FINE, "Connect to Amazon EFS with IAM Access Key {1}", new Object[]{obfuscatedAccessKeyId});
                }
                builder
                        .withCredentials(credentials);
            }
            LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);

            return builder.build();
        };
    }
    public EFSService(Supplier<AmazonElasticFileSystem> clientSupplier){
        this.clientSupplier = clientSupplier;
    }

    AmazonElasticFileSystem getAmazonEFSClient() {
        return clientSupplier.get();
    }

    public List<FileSystemDescription> getAllFileSystems() {
        AmazonElasticFileSystem client = this.getAmazonEFSClient();
        List<FileSystemDescription> allFileSystems = new ArrayList<>();

        String lastMarker = null;
        do {
            DescribeFileSystemsResult result = client.describeFileSystems(new DescribeFileSystemsRequest().withMarker(lastMarker));
            allFileSystems.addAll(result.getFileSystems());
            lastMarker = result.getNextMarker();
        } while (lastMarker != null);

        return allFileSystems;
    }

    public List<AccessPointDescription> getAccessPointsForFileSystem(String fileSystemId) {
        List<AccessPointDescription> accessPointsList = new ArrayList<>();

        if (StringUtils.isEmpty(fileSystemId)) {
            return accessPointsList;
        }

        AmazonElasticFileSystem client = this.getAmazonEFSClient();
        String nextToken = null;
        do {
            DescribeAccessPointsResult describeAccessPointsResult =
                    client.describeAccessPoints(new DescribeAccessPointsRequest().withFileSystemId(fileSystemId)
                                                                                 .withNextToken(nextToken));

            accessPointsList.addAll(describeAccessPointsResult.getAccessPoints());
            nextToken = describeAccessPointsResult.getNextToken();
        } while (nextToken != null);

        return accessPointsList;
    }
}
