/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.utils;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

public class SSLCAChainExtractor {

    private static class SavingTrustManager implements X509TrustManager {
        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return tm.getAcceptedIssuers();
        }


        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }

    public static List<X509Certificate> extract(String host, int port) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init((KeyStore) null);
            final SavingTrustManager savingTrustManager = new SavingTrustManager((X509TrustManager) tmf
                    .getTrustManagers()[0]
            );
            context.init(
                    null,
                    new TrustManager[]{
                            savingTrustManager
                    },
                    null
            );

            SSLSocketFactory factory = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            socket.setSoTimeout(10000);
            socket.startHandshake();
            socket.close();


            X509Certificate[] chain = savingTrustManager.chain;
            if (chain == null) {
                throw new IllegalStateException("Could not get CA chain from " + host + ":" + port);
            }

            return Arrays.asList(chain);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalStateException(e);
        }
    }

}
