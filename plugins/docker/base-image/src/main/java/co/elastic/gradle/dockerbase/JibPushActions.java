/*
 *
 *  * ELASTICSEARCH CONFIDENTIAL
 *  * __________________
 *  *
 *  *  Copyright Elasticsearch B.V. All rights reserved.
 *  *
 *  * NOTICE:  All information contained herein is, and remains
 *  * the property of Elasticsearch B.V. and its suppliers, if any.
 *  * The intellectual and technical concepts contained herein
 *  * are proprietary to Elasticsearch B.V. and its suppliers and
 *  * may be covered by U.S. and Foreign Patents, patents in
 *  * process, and are protected by trade secret or copyright
 *  * law.  Dissemination of this information or reproduction of
 *  * this material is strictly forbidden unless prior written
 *  * permission is obtained from Elasticsearch B.V.
 *
 */

package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.RetryUtils;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

public class JibPushActions {

    private Logger logger = LoggerFactory.getLogger(JibPushActions.class);


    private RegistryImage getAuthenticatedRegistryImage(String reference) throws InvalidImageReferenceException {
        final ImageReference imageRef = ImageReference.parse(reference);
        return RegistryImage.named(imageRef)
                .addCredentialRetriever(
                        getCredentialRetriever(imageRef)
                );
    }

    private CredentialRetriever getCredentialRetriever(ImageReference parse) {
        return CredentialRetrieverFactory.forImage(
                parse,
                credentialEvent -> logger.info(credentialEvent.getMessage())
        ).dockerConfig();
    }

    public JibContainer pushImage(Path imageArchive, String tag, Instant createdAt) {
        final JibContainer container = RetryUtils.retry(() -> {
                    try {
                        return Jib.from(TarImage.at(imageArchive))
                                .setCreationTime(createdAt)
                                .containerize(
                                        Containerizer.to(getAuthenticatedRegistryImage(tag))
                                );
                    }
                    catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
                        throw new GradleException("Error pushing image archive in registry (" + tag + ").", e);
                    }
                })
                .maxAttempt(6)
                .exponentialBackoff(1000, 30000)
                .onRetryError(error -> logger.warn("Error while pushing image with Jib. Retrying", error))
                .execute();
        return container;
    }
}
