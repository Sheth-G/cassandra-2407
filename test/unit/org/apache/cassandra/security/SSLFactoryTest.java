/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.security;

import org.apache.cassandra.io.util.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.EncryptionOptions;
import org.apache.cassandra.config.EncryptionOptions.ServerEncryptionOptions;
import org.apache.cassandra.config.ParameterizedClass;

public class SSLFactoryTest
{
    private static final Logger logger = LoggerFactory.getLogger(SSLFactoryTest.class);

    static final SelfSignedCertificate ssc;
    static
    {
        DatabaseDescriptor.daemonInitialization();
        try
        {
            ssc = new SelfSignedCertificate();
        }
        catch (CertificateException e)
        {
            throw new RuntimeException("failed to create test certs");
        }
    }

    private ServerEncryptionOptions encryptionOptions;

    @Before
    public void setup()
    {
        SSLFactory.clearSslContextCache();
        encryptionOptions = new ServerEncryptionOptions()
                            .withTrustStore("test/conf/cassandra_ssl_test.truststore")
                            .withTrustStorePassword("cassandra")
                            .withRequireClientAuth(false)
                            .withCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
    }

    private ServerEncryptionOptions addKeystoreOptions(ServerEncryptionOptions options)
    {
        return options.withKeyStore("test/conf/cassandra_ssl_test.keystore")
                      .withKeyStorePassword("cassandra");
    }

    private ServerEncryptionOptions addPEMKeystoreOptions(ServerEncryptionOptions options)
    {
        ParameterizedClass sslContextFactoryClass = new ParameterizedClass("org.apache.cassandra.security.PEMBasedSslContextFactory",
                                                                           new HashMap<>());
        return options.withSslContextFactory(sslContextFactoryClass)
                      .withKeyStore("test/conf/cassandra_ssl_test.keystore.pem")
                      .withKeyStorePassword("cassandra")
                      .withTrustStore("test/conf/cassandra_ssl_test.truststore.pem");
    }

    @Test
    public void testSslContextReload_HappyPath() throws IOException, InterruptedException
    {
        try
        {
            ServerEncryptionOptions options = addKeystoreOptions(encryptionOptions)
                                              .withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.all);
            ServerEncryptionOptions legacyOptions = options.withOptional(false).withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.all);
            options.sslContextFactoryInstance.initHotReloading();
            legacyOptions.sslContextFactoryInstance.initHotReloading();

            SslContext oldCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext oldLegacyCtx = SSLFactory.getOrCreateSslContext(legacyOptions, true, ISslContextFactory.SocketType.CLIENT, "test legacy");
            File keystoreFile = new File(options.keystore);

            SSLFactory.checkCertFilesForHotReloading();

            keystoreFile.trySetLastModified(System.currentTimeMillis() + 15000);

            SSLFactory.checkCertFilesForHotReloading();
            SslContext newCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext newLegacyCtx = SSLFactory.getOrCreateSslContext(legacyOptions, true, ISslContextFactory.SocketType.CLIENT, "test legacy");

            Assert.assertNotSame(oldCtx, newCtx);
            Assert.assertNotSame(oldLegacyCtx, newLegacyCtx);
        }
        catch (Exception e)
        {
            throw e;
        }
        finally
        {
            DatabaseDescriptor.loadConfig();
        }
    }

    @Test
    public void testPEMSslContextReload_HappyPath() throws IOException, InterruptedException
    {
        try
        {
            ServerEncryptionOptions options = addPEMKeystoreOptions(encryptionOptions)
                                              .withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.dc);
            // emulate InboundSockets and share the cert but with different options, no extra hot reloading init
            ServerEncryptionOptions legacyOptions = options.withOptional(false).withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.all);
            options.sslContextFactoryInstance.initHotReloading();
            legacyOptions.sslContextFactoryInstance.initHotReloading();

            SslContext oldCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext oldLegacyCtx = SSLFactory.getOrCreateSslContext(legacyOptions, true, ISslContextFactory.SocketType.CLIENT, "test legacy");
            File keystoreFile = new File(options.keystore);

            SSLFactory.checkCertFilesForHotReloading();

            keystoreFile.trySetLastModified(System.currentTimeMillis() + 15000);

            SSLFactory.checkCertFilesForHotReloading();
            SslContext newCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext newLegacyCtx = SSLFactory.getOrCreateSslContext(legacyOptions, true, ISslContextFactory.SocketType.CLIENT, "test legacy");

            Assert.assertNotSame(oldCtx, newCtx);
            Assert.assertNotSame(oldLegacyCtx, newLegacyCtx);
        }
        catch (Exception e)
        {
            throw e;
        }
        finally
        {
            DatabaseDescriptor.loadConfig();
        }
    }

    @Test(expected = IOException.class)
    public void testSslFactorySslInit_BadPassword_ThrowsException() throws IOException
    {
        ServerEncryptionOptions options = addKeystoreOptions(encryptionOptions)
                                    .withKeyStorePassword("bad password")
                                    .withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.all);

        SSLFactory.validateSslContext("testSslFactorySslInit_BadPassword_ThrowsException", options, false, true);
    }

    @Test
    public void testSslFactoryHotReload_BadPassword_DoesNotClearExistingSslContext() throws IOException
    {
        try
        {
            ServerEncryptionOptions options = addKeystoreOptions(encryptionOptions);
            // emulate InboundSockets and share the cert but with different options, no extra hot reloading init
            ServerEncryptionOptions legacyOptions = options.withOptional(false).withInternodeEncryption(ServerEncryptionOptions.InternodeEncryption.all);

            File testKeystoreFile = new File(options.keystore + ".test");
            FileUtils.copyFile(new File(options.keystore).toJavaIOFile(), testKeystoreFile.toJavaIOFile());
            options = options.withKeyStore(testKeystoreFile.path());

            SSLFactory.initHotReloading(options, options, true);  // deliberately not initializing with legacyOptions to match InboundSockets.addBindings

            SslContext oldCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext oldLegacyCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test legacy");

            changeKeystorePassword(options.keystore, options.keystore_password, "bad password");

            SSLFactory.checkCertFilesForHotReloading();
            SslContext newCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SslContext newLegacyCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test legacy");

            Assert.assertSame(oldCtx, newCtx);
            Assert.assertSame(oldLegacyCtx, newLegacyCtx);
        }
        finally
        {
            DatabaseDescriptor.loadConfig();
        }
    }

    @Test
    public void testSslFactoryHotReload_CorruptOrNonExistentFile_DoesNotClearExistingSslContext() throws IOException
    {
        try
        {
            ServerEncryptionOptions options = addKeystoreOptions(encryptionOptions);

            File testKeystoreFile = new File(options.keystore + ".test");
            FileUtils.copyFile(new File(options.keystore).toJavaIOFile(), testKeystoreFile.toJavaIOFile());
            options = options.withKeyStore(testKeystoreFile.path());


            SSLFactory.initHotReloading(options, options, true);
            SslContext oldCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");
            SSLFactory.checkCertFilesForHotReloading();

            testKeystoreFile.trySetLastModified(System.currentTimeMillis() + 15000);
            FileUtils.forceDelete(testKeystoreFile.toJavaIOFile());

            SSLFactory.checkCertFilesForHotReloading();
            SslContext newCtx = SSLFactory.getOrCreateSslContext(options, true, ISslContextFactory.SocketType.CLIENT, "test");

            Assert.assertSame(oldCtx, newCtx);
        }
        catch (Exception e)
        {
            throw e;
        }
        finally
        {
            DatabaseDescriptor.loadConfig();
            FileUtils.deleteQuietly(new File(encryptionOptions.keystore + ".test").toJavaIOFile());
        }
    }

    @Test
    public void getSslContext_ParamChanges() throws IOException
    {
        EncryptionOptions options = addKeystoreOptions(encryptionOptions)
                                    .withEnabled(true)
                                    .withCipherSuites("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");

        SslContext ctx1 = SSLFactory.getOrCreateSslContext(options, true,
                                                           ISslContextFactory.SocketType.SERVER, "test");

        Assert.assertTrue(ctx1.isServer());
        Assert.assertEquals(ctx1.cipherSuites(), options.cipher_suites);

        options = options.withCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

        SslContext ctx2 = SSLFactory.getOrCreateSslContext(options, true,
                                                           ISslContextFactory.SocketType.CLIENT, "test");

        Assert.assertTrue(ctx2.isClient());
        Assert.assertEquals(ctx2.cipherSuites(), options.cipher_suites);
    }

    @Test
    public void testCacheKeyEqualityForCustomSslContextFactory() {

        Map<String,String> parameters1 = new HashMap<>();
        parameters1.put("key1", "value1");
        parameters1.put("key2", "value2");
        EncryptionOptions encryptionOptions1 =
        new EncryptionOptions()
        .withSslContextFactory(new ParameterizedClass(DummySslContextFactoryImpl.class.getName(), parameters1))
        .withProtocol("TLSv1.1")
        .withRequireClientAuth(true)
        .withRequireEndpointVerification(false);

        SSLFactory.CacheKey cacheKey1 = new SSLFactory.CacheKey(encryptionOptions1, ISslContextFactory.SocketType.SERVER, "test"
        );

        Map<String,String> parameters2 = new HashMap<>();
        parameters2.put("key1", "value1");
        parameters2.put("key2", "value2");
        EncryptionOptions encryptionOptions2 =
        new EncryptionOptions()
        .withSslContextFactory(new ParameterizedClass(DummySslContextFactoryImpl.class.getName(), parameters2))
        .withProtocol("TLSv1.1")
        .withRequireClientAuth(true)
        .withRequireEndpointVerification(false);

        SSLFactory.CacheKey cacheKey2 = new SSLFactory.CacheKey(encryptionOptions2, ISslContextFactory.SocketType.SERVER, "test"
        );

        Assert.assertEquals(cacheKey1, cacheKey2);
    }

    @Test
    public void testCacheKeyInequalityForCustomSslContextFactory() {

        Map<String,String> parameters1 = new HashMap<>();
        parameters1.put("key1", "value11");
        parameters1.put("key2", "value12");
        EncryptionOptions encryptionOptions1 =
        new EncryptionOptions()
        .withSslContextFactory(new ParameterizedClass(DummySslContextFactoryImpl.class.getName(), parameters1))
        .withProtocol("TLSv1.1");

        SSLFactory.CacheKey cacheKey1 = new SSLFactory.CacheKey(encryptionOptions1, ISslContextFactory.SocketType.SERVER, "test"
        );

        Map<String,String> parameters2 = new HashMap<>();
        parameters2.put("key1", "value21");
        parameters2.put("key2", "value22");
        EncryptionOptions encryptionOptions2 =
        new EncryptionOptions()
        .withSslContextFactory(new ParameterizedClass(DummySslContextFactoryImpl.class.getName(), parameters2))
        .withProtocol("TLSv1.1");

        SSLFactory.CacheKey cacheKey2 = new SSLFactory.CacheKey(encryptionOptions2, ISslContextFactory.SocketType.SERVER, "test"
        );

        Assert.assertNotEquals(cacheKey1, cacheKey2);
    }

    void changeKeystorePassword(String filename, String currentPassword, String newPassword)
    {
        try
        {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] loadPasswd = currentPassword.toCharArray();
            char[] storePasswd = newPassword.toCharArray();
            try (FileInputStream is = new FileInputStream(filename))
            {
                keystore.load(is, loadPasswd);
            }
            try (FileOutputStream os = new FileOutputStream(filename))
            {
                keystore.store(os, storePasswd);
            }
        }
        catch (Throwable tr)
        {
            throw new RuntimeException(tr);
        }
    }
}