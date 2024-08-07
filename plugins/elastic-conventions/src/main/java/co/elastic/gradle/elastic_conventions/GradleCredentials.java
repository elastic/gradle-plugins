package co.elastic.gradle.elastic_conventions;

/*
 * ELASTICSEARCH CONFIDENTIAL
 * __________________
 *
 *  Copyright Elasticsearch B.V. All rights reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Elasticsearch B.V. and its suppliers, if any.
 * The intellectual and technical concepts contained herein
 * are proprietary to Elasticsearch B.V. and its suppliers and
 * may be covered by U.S. and Foreign Patents, patents in
 * process, and are protected by trade secret or copyright
 * law.  Dissemination of this information or reproduction of
 * this material is strictly forbidden unless prior written
 * permission is obtained from Elasticsearch B.V.
 */

import org.gradle.api.Action;
import org.gradle.caching.http.HttpBuildCacheCredentials;

public class GradleCredentials extends HttpBuildCacheCredentials implements Action {
    @Override
    public void execute(Object o) {

    }

    public GradleCredentials withUsername(String username) {
        setUsername(username);
        return this;
    }

    public GradleCredentials withPassword(String password) {
        setPassword(password);
        return this;
    }
}
