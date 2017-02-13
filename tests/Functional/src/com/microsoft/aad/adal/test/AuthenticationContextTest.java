// Copyright © Microsoft Open Technologies, Inc.
//
// All Rights Reserved
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// THIS CODE IS PROVIDED *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS
// OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION
// ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A
// PARTICULAR PURPOSE, MERCHANTABILITY OR NON-INFRINGEMENT.
//
// See the Apache License, Version 2.0 for the specific language
// governing permissions and limitations under the License.

package com.microsoft.aad.adal.test;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;
import android.util.Log;

import com.microsoft.aad.adal.ADALError;
import com.microsoft.aad.adal.AuthenticationActivity;
import com.microsoft.aad.adal.AuthenticationCallback;
import com.microsoft.aad.adal.AuthenticationConstants;
import com.microsoft.aad.adal.AuthenticationContext;
import com.microsoft.aad.adal.AuthenticationException;
import com.microsoft.aad.adal.AuthenticationResult;
import com.microsoft.aad.adal.AuthenticationSettings;
import com.microsoft.aad.adal.HttpWebResponse;
import com.microsoft.aad.adal.IConnectionService;
import com.microsoft.aad.adal.IDiscovery;
import com.microsoft.aad.adal.ITokenCacheStore;
import com.microsoft.aad.adal.Logger;
import com.microsoft.aad.adal.PromptBehavior;
import com.microsoft.aad.adal.TokenCache;
import com.microsoft.aad.adal.TokenCacheItem;
import com.microsoft.aad.adal.UserIdentifier;
import com.microsoft.aad.adal.UserIdentifier.UserIdentifierType;
import com.microsoft.aad.adal.UserInfo;

public class AuthenticationContextTest extends AndroidTestCase {

    private static final String[] TEST_SCOPE = new String[] {
        "resource"
    };

    /**
     * Check case-insensitive lookup
     */
    private static final String VALID_AUTHORITY = "https://Login.windows.net/Omercantest.Onmicrosoft.com";

    protected final static int CONTEXT_REQUEST_TIME_OUT = 2000000;

    protected final static int ACTIVITY_TIME_OUT = 1000000;

    private final static String TEST_AUTHORITY = "https://login.windows.net/ComMon/";

    private static final String TEST_PACKAGE_NAME = "com.microsoft.aad.adal.testapp";

    static final String testClientId = "650a6609-5463-4bc4-b7c6-19df7990a8bc";

    static final String testResource = "https://omercantest.onmicrosoft.com/spacemonkey";

    static final String TEST_IDTOKEN = "eyJ2ZXIiOiIxLjAiLCJ0aWQiOiI5NzNlNjJmYy01YzI3LTQ1OGMtOTgwYy04NTEwZTY3N2Q0NWYiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJhZGZyZWlAYXBwY29udmVyZ2VuY2UuY2NzY3RwLm5ldCIsInN1YiI6IjQweHNQQ29XZFdWbWIyWmRDTGN1TUYzYmJwVnZ2N0RCLVNIWlFZRkpmUEkiLCJuYW1lIjoiYWRyaWFuIGZyZWkifQ";

    static final UserIdentifier TEST_IDTOKEN_USERID = new UserIdentifier(
            "40xsPCoWdWVmb2ZdCLcuMF3bbpVvv7DB-SHZQYFJfPI", UserIdentifierType.UniqueId);

    static final String TEST_IDTOKEN_UPN = "adfrei@appconvergence.ccsctp.net";

    private byte[] testSignature;

    private String testTag;

    private static final String TAG = "AuthenticationContextTest";

    protected void setUp() throws Exception {
        super.setUp();
        Log.d(TAG, "setup key at settings");
        getContext().getCacheDir();
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());
        if (AuthenticationSettings.INSTANCE.getSecretKeyData() == null) {
            // use same key for tests
            SecretKeyFactory keyFactory = SecretKeyFactory
                    .getInstance("PBEWithSHA256And256BitAES-CBC-BC");
            SecretKey tempkey = keyFactory.generateSecret(new PBEKeySpec("test".toCharArray(),
                    "abcdedfdfd".getBytes("UTF-8"), 100, 256));
            SecretKey secretKey = new SecretKeySpec(tempkey.getEncoded(), "AES");
            AuthenticationSettings.INSTANCE.setSecretKey(secretKey.getEncoded());
        }
        AuthenticationSettings.INSTANCE.setSkipBroker(true);
        // ADAL is set to this signature for now
        PackageInfo info = mContext.getPackageManager().getPackageInfo(TEST_PACKAGE_NAME,
                PackageManager.GET_SIGNATURES);

        // Broker App can be signed with multiple certificates. It will look
        // all of them
        // until it finds the correct one for ADAL broker.
        for (Signature signature : info.signatures) {
            testSignature = signature.toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA");
            md.update(testSignature);
            testTag = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
            break;
        }
    }

    protected void tearDown() throws Exception {
        Logger.getInstance().setExternalLogger(null);
        super.tearDown();
    }

    /**
     * test constructor to make sure authority parameter is set
     * 
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    public void testConstructor() throws NoSuchAlgorithmException, NoSuchPaddingException {
        testAuthorityTrim("authorityFail");
        testAuthorityTrim("https://msft.com////");
        testAuthorityTrim("https:////");
        AuthenticationContext context2 = new AuthenticationContext(getContext(),
                "https://github.com/MSOpenTech/some/some", false);
        assertEquals("https://github.com/MSOpenTech", context2.getAuthority());
    }

    private void testAuthorityTrim(String authority) throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        try {
            new AuthenticationContext(getContext(), authority, false);
            Assert.fail("expected to fail");
        } catch (IllegalArgumentException e) {
            assertTrue("authority in the msg", e.getMessage().contains("authority"));
        }
    }

    public void testConstructorNoCache() {
        String authority = "https://github.com/MSOpenTech";
        AuthenticationContext context = new AuthenticationContext(getContext(), authority, false,
                null);
        assertNull(context.getCache());
    }

    public void testConstructorWithCache() throws NoSuchAlgorithmException, NoSuchPaddingException {
        String authority = "https://github.com/MSOpenTech";
        TokenCache expected = new TokenCache(getContext());
        AuthenticationContext context = new AuthenticationContext(getContext(), authority, false,
                expected);
        assertEquals("Cache object is expected to be same", expected, context.getCache());

        AuthenticationContext contextDefaultCache = new AuthenticationContext(getContext(),
                authority, false);
        assertNotNull(contextDefaultCache.getCache());
    }

    public void testConstructor_InternetPermission() throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        String authority = "https://github.com/MSOpenTech";
        FileMockContext mockContext = new FileMockContext(getContext());
        mockContext.requestedPermissionName = "android.permission.INTERNET";
        mockContext.responsePermissionFlag = PackageManager.PERMISSION_GRANTED;

        // no exception
        new AuthenticationContext(mockContext, authority, false);

        try {
            mockContext.responsePermissionFlag = PackageManager.PERMISSION_DENIED;
            new AuthenticationContext(mockContext, authority, false);
            Assert.fail("Supposed to fail");
        } catch (Exception e) {

            assertEquals("Permission related message",
                    ADALError.DEVELOPER_INTERNET_PERMISSION_MISSING,
                    ((AuthenticationException)e).getCode());
        }
    }

    public void testConstructorValidateAuthority() throws NoSuchAlgorithmException,
            NoSuchPaddingException {

        String authority = "https://github.com/MSOpenTech";
        AuthenticationContext context = getAuthenticationContext(getContext(), authority, true,
                null);
        assertTrue("Validate flag is expected to be same", context.getValidateAuthority());

        context = new AuthenticationContext(getContext(), authority, false);
        assertFalse("Validate flag is expected to be same", context.getValidateAuthority());
    }

    public void testCorrelationId_setAndGet() throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        UUID requestCorrelationId = UUID.randomUUID();
        AuthenticationContext context = new AuthenticationContext(getContext(), TEST_AUTHORITY,
                true);
        context.setRequestCorrelationId(requestCorrelationId);
        assertEquals("Verifier getter and setter", requestCorrelationId,
                context.getRequestCorrelationId());
    }

    /**
     * External call to Service to get real error response. Add expired item in
     * cache to try refresh token request. Web Request should have correlationId
     * in the header.
     * 
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws InterruptedException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     * @throws InvocationTargetException 
     * @throws IllegalArgumentException 
     */
    @MediumTest
    @UiThreadTest
    public void testCorrelationId_InWebRequest() throws NoSuchFieldException,
            IllegalAccessException, InterruptedException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalArgumentException, InvocationTargetException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException {

        if (Build.VERSION.SDK_INT <= 15) {
            Log.v(TAG,
                    "Server is returning 401 status code without challange. HttpUrlConnection does not return error stream for that in SDK 15. Without error stream, this test is useless.");
            return;
        }

        FileMockContext mockContext = new FileMockContext(getContext());
        String expectedAccessToken = "TokenFortestAcquireToken" + UUID.randomUUID().toString();
        String expectedClientId = "client" + UUID.randomUUID().toString();
        String[] expectedResource = new String[] {
            "resource" + UUID.randomUUID().toString()
        };
        UserIdentifier expectedUser = new UserIdentifier("userid" + UUID.randomUUID().toString(),
                UserIdentifierType.RequiredDisplayableId);
        MockTokenCache mockCache = getMockCache(-30, expectedAccessToken, expectedResource,
                expectedClientId, expectedUser, false);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        UUID requestCorrelationId = UUID.randomUUID();
        Log.d(TAG, "test correlationId:" + requestCorrelationId.toString());
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        final TestLogResponse response = new TestLogResponse();
        response.listenLogForMessageSegments(signal, "OAuth2 error", "correlation_id:\"\""
                + requestCorrelationId.toString());

        // Call acquire token with prompt never to prevent activity launch
        context.setRequestCorrelationId(requestCorrelationId);
        context.acquireTokenSilent(expectedResource, expectedClientId, expectedUser, callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Verify that web request send correct headers
        Log.v(TAG, "Response msg:" + response.message);
        assertTrue("Server response has same correlationId",
                response.message.contains(requestCorrelationId.toString()));
    }

    /**
     * if package does not have declaration for activity, it should return false
     * 
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testResolveIntent() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, NoSuchMethodException,
            InstantiationException, NoSuchAlgorithmException, NoSuchPaddingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        AuthenticationContext context = new AuthenticationContext(mockContext, VALID_AUTHORITY,
                false);
        Method m = ReflectionUtils.getTestMethod(context, "resolveIntent", Intent.class);
        Intent intent = new Intent();
        intent.setClass(mockContext, AuthenticationActivity.class);

        boolean actual = (Boolean)m.invoke(context, intent);
        assertTrue("Intent is expected to resolve", actual);

        mockContext.resolveIntent = false;
        actual = (Boolean)m.invoke(context, intent);
        assertFalse("Intent is not expected to resolve", actual);
    }

    /**
     * Test throws for different missing arguments
     * 
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testAcquireTokenNegativeArguments() throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false);
        final MockActivity testActivity = new MockActivity();
        final MockAuthenticationCallback testEmptyCallback = new MockAuthenticationCallback();

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "callback",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(testActivity, TEST_SCOPE, null, "clientId",
                                "redirectUri", UserIdentifier.getAnyUser(), null);
                    }
                });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "scope", new Runnable() {

            @Override
            public void run() {
                context.acquireToken(testActivity, null, null, "clientId", "redirectUri",
                        UserIdentifier.getAnyUser(), testEmptyCallback);
            }
        });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "scope", new Runnable() {

            @Override
            public void run() {
                context.acquireToken(testActivity, null, null, "clientId", "redirectUri",
                        UserIdentifier.getAnyUser(), testEmptyCallback);
            }
        });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "clientid",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(testActivity, TEST_SCOPE, null, null, "redirectUri",
                                UserIdentifier.getAnyUser(), testEmptyCallback);
                    }
                });
    }

    @SmallTest
    public void testAcquireToken_UserId() throws ClassNotFoundException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException, NoSuchAlgorithmException,
            NoSuchPaddingException, InterruptedException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                "https://login.windows.net/common", false, null);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        testActivity.mSignal = signal;

        context.acquireToken(testActivity, new String[] {
            "scope123"
        }, new String[] {
            "additionalScope"
        }, "clientId345", "redirect123", UserIdentifier.getAnyUser(), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // verify request
        Intent intent = testActivity.mStartActivityIntent;
        assertNotNull(intent);
        Serializable request = intent
                .getSerializableExtra(AuthenticationConstants.Browser.REQUEST_MESSAGE);
        assertEquals("AuthenticationRequest inside the intent", request.getClass(),
                Class.forName("com.microsoft.aad.adal.AuthenticationRequest"));
        String redirect = (String)ReflectionUtils.getFieldValue(request, "mRedirectUri");
        assertEquals("Redirect uri is same as package", "redirect123", redirect);
        String client = (String)ReflectionUtils.getFieldValue(request, "mClientId");
        assertEquals("client is same", "clientId345", client);
        String authority = (String)ReflectionUtils.getFieldValue(request, "mAuthority");
        assertEquals("authority is same", "https://login.windows.net/common", authority);
        String[] scope = (String[])ReflectionUtils.getFieldValue(request, "mScope");
        assertEquals("resource is same", "scope123", scope[0]);
    }

    @SmallTest
    public void testEmptyRedirect() throws ClassNotFoundException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException, NoSuchAlgorithmException,
            NoSuchPaddingException, InterruptedException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                "https://login.windows.net/common", false, null);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        testActivity.mSignal = signal;

        context.acquireToken(testActivity, TEST_SCOPE, null, "clientId", "", getUserId("userid"),
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        Intent intent = testActivity.mStartActivityIntent;
        assertNotNull(intent);
        Serializable request = intent
                .getSerializableExtra(AuthenticationConstants.Browser.REQUEST_MESSAGE);
        assertEquals("AuthenticationRequest inside the intent", request.getClass(),
                Class.forName("com.microsoft.aad.adal.AuthenticationRequest"));
        String redirect = (String)ReflectionUtils.getFieldValue(request, "mRedirectUri");
        assertEquals("Redirect uri is same as package", "com.microsoft.aad.adal.testapp", redirect);
    }

    @SmallTest
    public void testRequestInIntent() throws IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException, ClassNotFoundException, NoSuchAlgorithmException,
            NoSuchPaddingException, InterruptedException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                "https://login.windows.net/common", false, null);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        String expected = "&extraParam=1";
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        testActivity.mSignal = signal;

        // 1 - Send prompt always
        context.acquireToken(testActivity, new String[] {
            "Resource"
        }, null, "testExtraParamsClientId", "testExtraParamsredirectUri", PromptBehavior.Always,
                expected, callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Get intent from activity to verify extraparams are send
        Intent intent = testActivity.mStartActivityIntent;
        Serializable request = intent
                .getSerializableExtra(AuthenticationConstants.Browser.REQUEST_MESSAGE);

        PromptBehavior prompt = (PromptBehavior)ReflectionUtils.getFieldValue(request, "mPrompt");
        assertEquals("Prompt param is same", PromptBehavior.Always, prompt);
        String extraparm = (String)ReflectionUtils.getFieldValue(request,
                "mExtraQueryParamsAuthentication");
        assertEquals("Extra query param is same", expected, extraparm);
    }

    public static Object createAuthenticationRequest(String authority, String[] scope,
            String client, String redirect, UserIdentifier userid) throws ClassNotFoundException,
            NoSuchMethodException, IllegalArgumentException, InstantiationException,
            IllegalAccessException, InvocationTargetException {

        Class<?> c = Class.forName("com.microsoft.aad.adal.AuthenticationRequest");
        Constructor<?> constructor = c.getDeclaredConstructor(String.class, String[].class,
                String.class, String.class, UserIdentifier.class, PromptBehavior.class,
                String.class, UUID.class);
        constructor.setAccessible(true);
        Object o = constructor.newInstance(authority, scope, client, redirect, userid,
                PromptBehavior.Auto, "", null);
        return o;
    }

    private void setConnectionAvailable(final AuthenticationContext context, final boolean status)
            throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtils.setFieldValue(context, "mConnectionService", new IConnectionService() {

            @Override
            public boolean isConnectionAvailable() {
                return status;
            }
        });
    }

    private MockWebRequestHandler setMockWebRequest(final AuthenticationContext context, String id,
            String refreshToken, String scopeInMockResponse) throws NoSuchFieldException, IllegalAccessException {
        MockWebRequestHandler mockWebRequest = new MockWebRequestHandler();
        String json = "{\"access_token\":\"accessToken"
                + id
                + "\",\"token_type\":\"Bearer\",\"expires_in\":\"29344\",\"expires_on\":\"1368768616\",\"refresh_token\":\""
                + refreshToken + "\",\"scope\":\"" + scopeInMockResponse + "\",\"id_token\":\""
                + TEST_IDTOKEN + "\"}";
        mockWebRequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", mockWebRequest);
        return mockWebRequest;
    }

    private void removeMockWebRequest(final AuthenticationContext context)
            throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtils.setFieldValue(context, "mWebRequest", null);
    }

    /**
     * authority is malformed and error should come back in callback
     * 
     * @throws InterruptedException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testAcquireTokenAuthorityMalformed() throws InterruptedException,
            NoSuchAlgorithmException, NoSuchPaddingException {
        // Malformed url error will come back in callback
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                "abcd://vv../v", false);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        context.acquireToken(testActivity, TEST_SCOPE, null, "clientId", "redirectUri",
                getUserId("userid"), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNotNull("Error is not null", callback.mException);
        assertEquals("NOT_VALID_URL", ADALError.DEVELOPER_AUTHORITY_IS_NOT_VALID_URL,
                ((AuthenticationException)callback.mException).getCode());
    }

    /**
     * authority is validated and intent start request is sent
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testAcquireTokenValidateAuthorityReturnsValid() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        AuthenticationContext context = new AuthenticationContext(mockContext, VALID_AUTHORITY,
                true);
        final CountDownLatch signal = new CountDownLatch(1);
        MockActivity testActivity = new MockActivity();
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockDiscovery discovery = new MockDiscovery(true);
        ReflectionUtils.setFieldValue(context, "mDiscovery", discovery);

        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                getUserId("userid"), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNull("Error is null", callback.mException);
        assertEquals("Activity was attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);
    }

    @SmallTest
    public void testCorrelationId_InDiscovery() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        AuthenticationContext context = getAuthenticationContext(mockContext, VALID_AUTHORITY,
                true, null);
        final CountDownLatch signal = new CountDownLatch(1);
        UUID correlationId = UUID.randomUUID();
        MockActivity testActivity = new MockActivity();
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockDiscovery discovery = new MockDiscovery(true);
        ReflectionUtils.setFieldValue(context, "mDiscovery", discovery);

        // API call
        context.setRequestCorrelationId(correlationId);
        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                getUserId("userid"), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check correlationID that was set in the Discovery obj
        assertEquals("CorrelationId in discovery needs to be same as in request", correlationId,
                discovery.correlationId);
        assertNull("Error is null", callback.mException);
        assertEquals("Activity was attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);
    }

    /**
     * Invalid authority returns
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testAcquireTokenValidateAuthorityReturnsInValid() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, true);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockDiscovery discovery = new MockDiscovery(false);
        ReflectionUtils.setFieldValue(context, "mDiscovery", discovery);

        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                getUserId("userid"), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNotNull("Error is not null", callback.mException);
        assertEquals("NOT_VALID_URL", ADALError.DEVELOPER_AUTHORITY_IS_NOT_VALID_INSTANCE,
                ((AuthenticationException)callback.mException).getCode());
        assertTrue(
                "Activity was not attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW != testActivity.mStartActivityRequestCode);

        // Sync test
        try {
            context.acquireTokenSilentSync(TEST_SCOPE, "clientid", getUserId("userid"));
            Assert.fail("Validation should throw");
        } catch (AuthenticationException exc) {
            assertEquals("NOT_VALID_URL", ADALError.DEVELOPER_AUTHORITY_IS_NOT_VALID_INSTANCE,
                    exc.getCode());
        }

        clearCache(context);
    }

    /**
     * acquire token without validation
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testAcquireTokenWithoutValidation() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, null);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                getUserId("userid"), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNull("Error is null", callback.mException);
        assertEquals("Activity was attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);
        clearCache(context);
    }

    /**
     * acquire token uses refresh token, but web request returns error
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testRefreshTokenWebRequestHasError() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            InvocationTargetException, NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        MockTokenCache mockCache = getCacheForRefreshToken(TEST_IDTOKEN_USERID.getId(), TEST_IDTOKEN_UPN);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        webrequest.setReturnResponse(new HttpWebResponse(500, null, null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);
        final TestLogResponse response = new TestLogResponse();
        response.listenLogForMessageSegments(signal, "Refresh token did not return accesstoken");

        context.acquireTokenSilent(TEST_SCOPE, "clientid", TEST_IDTOKEN_USERID, callback);

        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertTrue("Log message has same webstatus code",
                response.errorCode.equals(ADALError.AUTH_FAILED_NO_TOKEN));
        assertNotNull("Cache item is not removed for this item",
                mockCache.getItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, TEST_SCOPE, "",
                        "clientId", false, TEST_IDTOKEN_USERID)));
        clearCache(context);
    }

    /**
     * acquire token using refresh token. All web calls are mocked. Refresh
     * token response must match to result and cache.
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    @SmallTest
    public void testRefreshTokenPositive() throws InterruptedException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException,
            NoSuchAlgorithmException, NoSuchPaddingException {

        FileMockContext mockContext = new FileMockContext(getContext());
        MockTokenCache mockCache = getCacheForRefreshToken(TEST_IDTOKEN_USERID.getId(), TEST_IDTOKEN_UPN);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        String json = "{\"id_token\":\""
                + TEST_IDTOKEN
                + "\",\"access_token\":\"TokenFortestRefreshTokenPositive\",\"token_type\":\"Bearer\",\"expires_in\":\"-10\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);

        // Call acquire token which will try refresh token based on cache
        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                new UserIdentifier(TEST_IDTOKEN_UPN, UserIdentifierType.RequiredDisplayableId),
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        verifyRefreshTokenResponse(mockCache, callback.mException, callback.mResult);

        // Do silent token request and return idtoken in the result
        json = "{\"profile_info\":\""
                + TEST_IDTOKEN
                + "\",\"access_token\":\"TokenReturnsWithIdToken\",\"token_type\":\"Bearer\",\"expires_in\":\"10\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refreshABC\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);
        AuthenticationResult result = context.acquireTokenSilentSync(TEST_SCOPE, "clientid",
                TEST_IDTOKEN_USERID);
        assertEquals("Access Token", "TokenReturnsWithIdToken", result.getToken());
        assertEquals("IdToken", TEST_IDTOKEN, result.getProfileInfo());
        clearCache(context);
    }

    @SmallTest
    public void testScenario_UserId_LoginHint_Use() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            InvocationTargetException, NoSuchAlgorithmException, NoSuchPaddingException,
            UnsupportedEncodingException {
        scenario_UserId_LoginHint("test@user.com", "test@user.com", "test@user.com");
    }

    private void scenario_UserId_LoginHint(String idTokenUpn, String responseIntentHint,
            String acquireTokenHint) throws InterruptedException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException,
            NoSuchAlgorithmException, NoSuchPaddingException, UnsupportedEncodingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false);
        context.getCache().clear();
        setConnectionAvailable(context, true);
        final CountDownLatch signal = new CountDownLatch(1);
        final CountDownLatch signalCallback = new CountDownLatch(1);
        final MockActivity testActivity = new MockActivity(signal);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signalCallback);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        ProfileInfo idtoken = new ProfileInfo();
        idtoken.upn = idTokenUpn;
        idtoken.oid = "userid123";
        String json = "{\"profile_info\":\""
                + idtoken.getIdToken()
                + "\",\"access_token\":\"TokenUserIdTest\",\"token_type\":\"Bearer\",\"expires_in\":\"28799\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);
        Intent intent = getResponseIntent(callback, TEST_SCOPE, "clientid", "redirectUri",
                responseIntentHint);

        // Get token from onActivityResult after Activity returns
        tokenWithAuthenticationActivity(context, testActivity, signal, signalCallback, intent,
                TEST_SCOPE, "clientid", "redirectUri", acquireTokenHint, callback);

        // Token will return to callback with idToken
        verifyTokenResult(idtoken, callback.mResult);

        // Same call should get token from cache
        final CountDownLatch signalCallback2 = new CountDownLatch(1);
        callback.mSignal = signalCallback2;
        context.acquireToken(testActivity, TEST_SCOPE, null, "clientid", "redirectUri",
                new UserIdentifier(acquireTokenHint, UserIdentifierType.OptionalDisplayableId),
                callback);
        signalCallback2.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);
        verifyTokenResult(idtoken, callback.mResult);

        // Call with userId should return from cache as well
        AuthenticationResult result = context.acquireTokenSilentSync(TEST_SCOPE, "clientid",
                new UserIdentifier(idtoken.oid, UserIdentifierType.UniqueId));
        verifyTokenResult(idtoken, result);

        clearCache(context);
    }

    @SmallTest
    public void testScenario_NullUser_IdToken() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            InvocationTargetException, NoSuchAlgorithmException, NoSuchPaddingException,
            UnsupportedEncodingException {
        scenario_UserId_LoginHint("test@user.com", "", "");
    }

    @SmallTest
    public void testScenario_LoginHint_IdToken_Different() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            InvocationTargetException, NoSuchAlgorithmException, NoSuchPaddingException,
            UnsupportedEncodingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false);
        context.getCache().clear();
        setConnectionAvailable(context, true);
        final CountDownLatch signal = new CountDownLatch(1);
        final CountDownLatch signalCallback = new CountDownLatch(1);
        final MockActivity testActivity = new MockActivity(signal);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signalCallback);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        ProfileInfo idtoken = new ProfileInfo();
        idtoken.upn = "admin@user.com";
        idtoken.oid = "admin123";
        String loginHint = "user1@user.com";
        String json = "{\"profile_info\":\""
                + idtoken.getIdToken()
                + "\",\"access_token\":\"TokenUserIdTest\",\"token_type\":\"Bearer\",\"expires_in\":\"28799\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);
        Intent intent = getResponseIntent(callback, TEST_SCOPE, "clientid", "redirectUri",
                loginHint);

        // Get token from onActivityResult after Activity returns
        tokenWithAuthenticationActivity(context, testActivity, signal, signalCallback, intent,
                TEST_SCOPE, "clientid", "redirectUri", loginHint, callback);

        // Token will return to callback with idToken
        verifyTokenResult(idtoken, callback.mResult);

        // Same call with correct upn will return from cache
        final CountDownLatch signalCallback2 = new CountDownLatch(1);
        callback.mSignal = signalCallback2;
        context.acquireToken(null, TEST_SCOPE, null, "clientid", "redirectUri", new UserIdentifier(
                idtoken.upn, UserIdentifierType.RequiredDisplayableId), callback);
        signalCallback2.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);
        verifyTokenResult(idtoken, callback.mResult);

        // Call with userId should return from cache as well
        AuthenticationResult result = context.acquireTokenSilentSync(TEST_SCOPE, "clientid",
                new UserIdentifier(idtoken.oid, UserIdentifierType.UniqueId));
        verifyTokenResult(idtoken, result);

        clearCache(context);
    }

    @SmallTest
    public void testScenario_Empty_IdToken() throws InterruptedException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException,
            NoSuchAlgorithmException, NoSuchPaddingException, UnsupportedEncodingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false);
        context.getCache().clear();
        setConnectionAvailable(context, true);
        final CountDownLatch signal = new CountDownLatch(1);
        final CountDownLatch signalCallback = new CountDownLatch(1);
        final MockActivity testActivity = new MockActivity(signal);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signalCallback);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        String json = "{\"access_token\":\"TokenUserIdTest\",\"token_type\":\"Bearer\",\"expires_in\":\"28799\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);
        Intent intent = getResponseIntent(callback, TEST_SCOPE, "clientid", "redirectUri", null);

        // Get token from onActivityResult after Activity returns
        tokenWithAuthenticationActivity(context, testActivity, signal, signalCallback, intent,
                TEST_SCOPE, "clientid", "redirectUri", null, callback);

        // Token will return to callback with idToken
        verifyTokenResult(null, callback.mResult);

        // Call with userId should return from cache as well
        AuthenticationResult result = context.acquireTokenSilentSync(TEST_SCOPE, "clientid",
                new UserIdentifier("", UserIdentifierType.OptionalDisplayableId));
        verifyTokenResult(null, result);

        clearCache(context);
    }

    private Intent getResponseIntent(MockAuthenticationCallback callback, String[] scope,
            String clientid, String redirect, String loginHint) throws IllegalArgumentException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        // Provide mock result for activity that returns code and proper state
        Intent intent = new Intent();
        intent.putExtra(AuthenticationConstants.Browser.REQUEST_ID, callback.hashCode());
        Object authRequest = createAuthenticationRequest(VALID_AUTHORITY, scope, clientid,
                redirect, new UserIdentifier(loginHint, UserIdentifierType.OptionalDisplayableId));
        intent.putExtra(AuthenticationConstants.Browser.RESPONSE_REQUEST_INFO,
                (Serializable)authRequest);
        intent.putExtra(AuthenticationConstants.Browser.RESPONSE_FINAL_URL, VALID_AUTHORITY
                + "/oauth2/authorize?code=123");
        return intent;
    }

    private void tokenWithAuthenticationActivity(final AuthenticationContext context,
            final MockActivity testActivity, CountDownLatch signal,
            CountDownLatch signalOnActivityResult, Intent responseIntent, String[] scope,
            String clientid, String redirect, String loginHint, MockAuthenticationCallback callback)
            throws InterruptedException {

        // Call acquire token
        context.acquireToken(testActivity, scope, null, clientid, redirect, new UserIdentifier(
                loginHint, UserIdentifierType.OptionalDisplayableId), callback);
        signal.await(ACTIVITY_TIME_OUT, TimeUnit.MILLISECONDS);

        // Activity will start
        assertEquals("Activity was attempted to start.",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);

        context.onActivityResult(testActivity.mStartActivityRequestCode,
                AuthenticationConstants.UIResponse.BROWSER_CODE_COMPLETE, responseIntent);
        signalOnActivityResult.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);
    }

    private void verifyTokenResult(ProfileInfo idtoken, AuthenticationResult result) {
        assertEquals("Check access token", "TokenUserIdTest", result.getToken());
        if (idtoken != null) {
            assertEquals("Result has username", idtoken.upn, result.getUserInfo()
                    .getDisplayableId());
        }
    }

    public void testAcquireTokenSilentSync_Positive() throws NoSuchAlgorithmException,
            NoSuchPaddingException, NoSuchFieldException, IllegalAccessException,
            InterruptedException, ExecutionException, IllegalArgumentException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            InvocationTargetException {
   FileMockContext mockContext = new FileMockContext(getContext());
        MockTokenCache mockCache = getCacheForRefreshToken(TEST_IDTOKEN_USERID.getId(), TEST_IDTOKEN_UPN);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        String json = "{\"access_token\":\"TokenFortestRefreshTokenPositive\",\"token_type\":\"Bearer\",\"expires_in\":\"28799\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);

        // Call refresh token in silent API method
        AuthenticationResult result = context.acquireTokenSilentSync(TEST_SCOPE, "clientid",
                TEST_IDTOKEN_USERID);
        verifyRefreshTokenResponse(mockCache, null, result);

        clearCache(context);
    }

    public void testAcquireTokenSilentSync_Negative() throws NoSuchAlgorithmException,
            NoSuchPaddingException, NoSuchFieldException, IllegalAccessException,
            InterruptedException, ExecutionException, IllegalArgumentException,
            InvocationTargetException, ClassNotFoundException, NoSuchMethodException,
            InstantiationException {
        FileMockContext mockContext = new FileMockContext(getContext());
        MockTokenCache mockCache = getCacheForRefreshToken(TEST_IDTOKEN_USERID.getId(),
                TEST_IDTOKEN_UPN);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        String responseBody = "{\"error\":\"invalid_grant\",\"error_description\":\"AADSTS70000: Authentication failed. Refresh Token is not valid.\r\nTrace ID: bb27293d-74e4-4390-882b-037a63429026\r\nCorrelation ID: b73106d5-419b-4163-8bc6-d2c18f1b1a13\r\nTimestamp: 2014-11-06 18:39:47Z\",\"error_codes\":[70000],\"timestamp\":\"2014-11-06 18:39:47Z\",\"trace_id\":\"bb27293d-74e4-4390-882b-037a63429026\",\"correlation_id\":\"b73106d5-419b-4163-8bc6-d2c18f1b1a13\",\"submit_url\":null,\"context\":null}";
        webrequest.setReturnResponse(new HttpWebResponse(400, responseBody.getBytes(), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);

        // Call refresh token in silent API method
        try {
            context.acquireTokenSilentSync(null, "clientid", TEST_IDTOKEN_USERID);
            Assert.fail("Expected argument exception");
        } catch (IllegalArgumentException e) {
            assertTrue("Scope is missin", e.getMessage().contains("scope"));
        }

        try {
            context.acquireTokenSilentSync(TEST_SCOPE, null, TEST_IDTOKEN_USERID);
            Assert.fail("Expected argument exception");
        } catch (IllegalArgumentException e) {
            assertTrue("Resource is missin", e.getMessage().contains("clientId"));
        }

        try {
            context.acquireTokenSilentSync(TEST_SCOPE, "clientid", TEST_IDTOKEN_USERID);
        } catch (AuthenticationException e) {
            assertEquals("Token is not exchanged",
                    ADALError.AUTH_REFRESH_FAILED_PROMPT_NOT_ALLOWED, e.getCode());
        }

        clearCache(context);
    }

    private void verifyRefreshTokenResponse(MockTokenCache mockCache, Exception resultException,
            AuthenticationResult result) throws IllegalArgumentException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
        assertNull("Error is null", resultException);
        assertEquals("Token is same", "TokenFortestRefreshTokenPositive", result.getToken());
        assertNotNull("Cache is NOT empty for this userid for regular token",
                mockCache.getItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, TEST_SCOPE, "",
                        "clientId", false, TEST_IDTOKEN_USERID)));
        assertNotNull("Cache is NOT empty for this userid for regular token",
                mockCache.getItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, TEST_SCOPE, "",
                        "clientId", false, TEST_IDTOKEN_USERID)));
        assertTrue("Refresh token has userinfo", result.getUserInfo().getUniqueId()
                .equalsIgnoreCase(TEST_IDTOKEN_USERID.getId()));
    }

    /**
     * authority and resource are case insensitive. Cache lookup will return
     * item from cache.
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvocationTargetException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     */
    @SmallTest
    public void testAcquireTokenCacheLookup() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException {

        FileMockContext mockContext = new FileMockContext(getContext());
        String tokenToTest = "accessToken=" + UUID.randomUUID();
        String[] resource = new String[]{"Resource" + UUID.randomUUID()};
        MockTokenCache mockCache = new MockTokenCache(getContext());
        mockCache.clear();
        addItemToCache(mockCache, tokenToTest, "refreshToken", VALID_AUTHORITY, resource,
                "clientId", "userId124", "name", "familyName", "userA", "tenantId", false);
        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        // acquire token call will return from cache
        context.acquireToken(testActivity, resource, null, "ClienTid", "redirectUri", new UserIdentifier("userA", UserIdentifierType.RequiredDisplayableId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNull("Error is null", callback.mException);
        assertEquals("Same access token in cache", tokenToTest, callback.mResult.getToken());
        assertEquals("Same userid in cache", "userId124", callback.mResult.getUserInfo()
                .getUniqueId());
        assertEquals("Same name in cache", "name", callback.mResult.getUserInfo().getName());
        assertEquals("Same displayid in cache", "userA", callback.mResult.getUserInfo()
                .getDisplayableId());
        assertEquals("Same tenantid in cache", "tenantId", callback.mResult.getTenantId());
        clearCache(context);
    }

    @SmallTest
    public void testAcquireTokenCacheLookup_WrongUserId() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException {
        FileMockContext mockContext = new FileMockContext(getContext());
        String[] scope = new String[]{"Resource" + UUID.randomUUID()};
        String clientId = "clientid" + UUID.randomUUID();
        MockTokenCache mockCache = new MockTokenCache(getContext());
        mockCache.clear();
        Calendar timeAhead = new GregorianCalendar();
        timeAhead.add(Calendar.MINUTE, 10);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(VALID_AUTHORITY);
        refreshItem.setScope(scope);
        refreshItem.setClientId(clientId);
        refreshItem.setAccessToken("token");
        refreshItem.setRefreshToken("refreshToken");
        refreshItem.setExpiresOn(timeAhead.getTime());
        refreshItem.setIsMultiResourceRefreshToken(false);
        UserInfo userinfo = new UserInfo("user2", "test", "idp", "user2");
        refreshItem.setUserInfo(userinfo);
        MockTokenCacheKey key = MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, scope, "",
                clientId, false, new UserIdentifier("user2",
                        UserIdentifierType.RequiredDisplayableId));
        mockCache.setItem(key, refreshItem);
        TokenCacheItem item = mockCache.getItem(key);
        assertNotNull("item is in cache", item);

        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        // Acquire token call will return from cache
        context.acquireTokenSilent(scope, clientId, new UserIdentifier("user1", UserIdentifierType.RequiredDisplayableId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNotNull("Error is not null", callback.mException);
        clearCache(context);
    }

    @SmallTest
    public void testAcquireTokenCacheLookup_MultipleUser_LoginHint() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException {
        FileMockContext mockContext = new FileMockContext(getContext());

        String[] resource = new String[]{"Resource" + UUID.randomUUID()};
        String clientId = "clientid" + UUID.randomUUID();
        MockTokenCache mockCache = new MockTokenCache(getContext());
        mockCache.clear();
        addItemToCache(mockCache, "token1", "refresh1", VALID_AUTHORITY, resource, clientId,
                "userid1", "userAname", "userAfamily", "userName1", "tenant", false);
        addItemToCache(mockCache, "token2", "refresh2", VALID_AUTHORITY, resource, clientId,
                "userid2", "userBname", "userBfamily", "userName2", "tenant", false);

        final AuthenticationContext context = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);

        // User1
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        // Acquire token call will return from cache
        context.acquireTokenSilent(resource, clientId, new UserIdentifier("userid1", UserIdentifierType.UniqueId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNull("Error is null", callback.mException);
        assertEquals("token for user1", "token1", callback.mResult.getToken());
        assertEquals("idtoken for user1", "userName1", callback.mResult.getUserInfo()
                .getDisplayableId());
        assertEquals("idtoken for user1", "userAname", callback.mResult.getUserInfo()
                .getName());

        // User2 with userid call
        final CountDownLatch signal2 = new CountDownLatch(1);
        MockAuthenticationCallback callback2 = new MockAuthenticationCallback(signal2);

        // Acquire token call will return from cache
        context.acquireTokenSilent(resource, clientId, new UserIdentifier("userid2", UserIdentifierType.UniqueId), callback2);
        signal2.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNull("Error is null", callback2.mException);
        assertEquals("token for user1", "token2", callback2.mResult.getToken());
        assertEquals("idtoken for user1", "userName2", callback2.mResult.getUserInfo()
                .getDisplayableId());

        // User2 with loginHint call
        final CountDownLatch signal3 = new CountDownLatch(1);
        MockAuthenticationCallback callback3 = new MockAuthenticationCallback(signal3);

        context.acquireToken(null, resource, null, clientId, "http://redirectUri",
                new UserIdentifier("userName1", UserIdentifierType.RequiredDisplayableId),
                callback3);
        signal3.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNull("Error is null", callback3.mException);
        assertEquals("token for user1", "token1", callback3.mResult.getToken());
        assertEquals("idtoken for user1", "userName1", callback3.mResult.getUserInfo()
                .getDisplayableId());

        clearCache(context);
    }

    @SmallTest
    public void testOnActivityResult_MissingIntentData() throws NoSuchAlgorithmException,
            NoSuchPaddingException {
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, null);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.TOKEN_BROKER_RESPONSE;
        TestLogResponse logResponse = new TestLogResponse();
        String msgToCheck = "onActivityResult BROWSER_FLOW data is null";
        logResponse.listenLogForMessageSegments(null, msgToCheck);

        // act
        authContext.onActivityResult(requestCode, resultCode, null);

        // assert
        assertTrue(logResponse.message.contains(msgToCheck));
    }

    @SmallTest
    public void testOnActivityResult_MissingCallbackRequestId() throws NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = getAuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.TOKEN_BROKER_RESPONSE;
        Intent data = new Intent();
        data.putExtra("Test", "value");
        TestLogResponse logResponse = new TestLogResponse();
        String msgToCheck = "onActivityResult did not find waiting request for RequestId";
        logResponse.listenLogForMessageSegments(null, msgToCheck);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertTrue(logResponse.message.contains(msgToCheck));
    }

    @SmallTest
    public void testOnActivityResult_ResultCode_Cancel() throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.BROWSER_CODE_CANCEL;
        TestAuthCallBack callback = new TestAuthCallBack();
        Intent data = setWaitingRequestToContext(authContext, callback);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertTrue("Returns cancel error",
                callback.callbackException instanceof AuthenticationException);
        assertTrue("Cancel error has message",
                callback.callbackException.getMessage().contains("User cancelled the flow"));
    }

    private Intent setWaitingRequestToContext(final AuthenticationContext authContext,
            TestAuthCallBack callback) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object authRequestState = getRequestState(callback);
        Intent data = new Intent();
        data.putExtra(AuthenticationConstants.Browser.REQUEST_ID, callback.hashCode());
        Method m = ReflectionUtils.getTestMethod(authContext, "putWaitingRequest", int.class,
                Class.forName("com.microsoft.aad.adal.AuthenticationRequestState"));
        m.invoke(authContext, callback.hashCode(), authRequestState);
        return data;
    }

    @SmallTest
    public void testOnActivityResult_ResultCode_Error() throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.BROWSER_CODE_ERROR;
        TestAuthCallBack callback = new TestAuthCallBack();
        Intent data = setWaitingRequestToContext(authContext, callback);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertTrue("Returns error", callback.callbackException instanceof AuthenticationException);
    }

    @SmallTest
    public void testOnActivityResult_ResultCode_Exception() throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.BROWSER_CODE_AUTHENTICATION_EXCEPTION;
        TestAuthCallBack callback = new TestAuthCallBack();
        Intent data = setWaitingRequestToContext(authContext, callback);
        AuthenticationException exception = new AuthenticationException(ADALError.AUTH_FAILED);
        data.putExtra(AuthenticationConstants.Browser.RESPONSE_AUTHENTICATION_EXCEPTION,
                (Serializable)exception);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertTrue("Returns authentication exception",
                callback.callbackException instanceof AuthenticationException);
        assertTrue(
                "Returns authentication exception",
                ((AuthenticationException)callback.callbackException).getCode() == ADALError.AUTH_FAILED);
    }

    @SmallTest
    public void testOnActivityResult_ResultCode_ExceptionMissing() throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.BROWSER_CODE_AUTHENTICATION_EXCEPTION;
        TestAuthCallBack callback = new TestAuthCallBack();
        Intent data = setWaitingRequestToContext(authContext, callback);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertTrue("Returns authentication exception",
                callback.callbackException instanceof AuthenticationException);
        assertTrue(
                "Returns authentication exception",
                ((AuthenticationException)callback.callbackException).getCode() == ADALError.WEBVIEW_RETURNED_INVALID_AUTHENTICATION_EXCEPTION);
    }

    @SmallTest
    public void testOnActivityResult_BrokerResponse() throws IllegalArgumentException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException, NoSuchFieldException, NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        FileMockContext mockContext = new FileMockContext(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, cache);
        int requestCode = AuthenticationConstants.UIRequest.BROWSER_FLOW;
        int resultCode = AuthenticationConstants.UIResponse.TOKEN_BROKER_RESPONSE;
        TestAuthCallBack callback = new TestAuthCallBack();
        Object authRequestState = getRequestState(callback);
        Intent data = new Intent();
        data.putExtra(AuthenticationConstants.Browser.REQUEST_ID, callback.hashCode());
        data.putExtra(AuthenticationConstants.Broker.ACCOUNT_ACCESS_TOKEN, "testAccessToken");
        Method m = ReflectionUtils.getTestMethod(authContext, "putWaitingRequest", int.class,
                Class.forName("com.microsoft.aad.adal.AuthenticationRequestState"));
        m.invoke(authContext, callback.hashCode(), authRequestState);

        // act
        authContext.onActivityResult(requestCode, resultCode, data);

        // assert
        assertEquals("Same token in response", "testAccessToken",
                callback.callbackResult.getToken());
    }

    private Object getRequestState(TestAuthCallBack callback) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException {
        Class<?> c = Class.forName("com.microsoft.aad.adal.AuthenticationRequestState");
        Class<?> c2 = Class.forName("com.microsoft.aad.adal.AuthenticationRequest");
        Constructor<?> constructorParams = c.getDeclaredConstructor(int.class, c2,
                AuthenticationCallback.class);
        constructorParams.setAccessible(true);
        Object o = constructorParams.newInstance(callback.hashCode(), null, callback);
        return o;
    }

    class TestAuthCallBack implements AuthenticationCallback<AuthenticationResult> {

        public AuthenticationResult callbackResult;

        public Exception callbackException;

        @Override
        public void onSuccess(AuthenticationResult result) {
            callbackResult = result;
        }

        @Override
        public void onError(Exception exc) {
            callbackException = exc;
        }

    }

    /**
     * setup cache with userid for normal token and multiresource refresh token
     * bound to one userid. test calls for different resources and users.
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvocationTargetException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     */
    @SmallTest
    public void testAcquireTokenMultiResourceToken_UserId() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchAlgorithmException, NoSuchPaddingException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, InvocationTargetException {
        FileMockContext mockContext = new FileMockContext(getContext());
        String tokenToTest = "accessToken=" + UUID.randomUUID();
        String tokenId = "id" + UUID.randomUUID().toString().replace("-", "");
        String tokenInfo = "accessToken" + tokenId;
        String[] resource = new String[]{"Resource" + UUID.randomUUID()};
        MockTokenCache mockCache = new MockTokenCache(getContext());
        mockCache.clear();
        addItemToCache(mockCache, tokenToTest, "refreshTokenNormal", VALID_AUTHORITY, resource,
                "ClienTid", TEST_IDTOKEN_USERID.getId(), "name", "familyName", TEST_IDTOKEN_UPN,
                "tenantId", true);
         
        // only one MRRT for same user, client, authority
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        setConnectionAvailable(context, true);
        String scopeInMockResponse = "anotherScope12";
        MockWebRequestHandler mockWebRequest = setMockWebRequest(context, tokenId, "refreshToken"
                + tokenId, scopeInMockResponse);

        CountDownLatch signal = new CountDownLatch(1);
        MockActivity testActivity = new MockActivity(signal);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        // Acquire token call will return from cache
        context.acquireToken(testActivity, resource, null, "ClienTid", "redirectUri",
                new UserIdentifier(TEST_IDTOKEN_UPN, UserIdentifierType.RequiredDisplayableId),
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        assertNull("Error is null", callback.mException);
        assertEquals("Same token in response as in cache", tokenToTest,
                callback.mResult.getToken());

        // Acquire token call will not return from cache for broad
        // Token-cached item does not have access token since it was broad
        // refresh token
        signal = new CountDownLatch(1);
        callback = new MockAuthenticationCallback(signal);
        context.acquireToken(testActivity, new String[]{"dummyResource2"}, null, "ClienTid", "redirectUri",
                new UserIdentifier(TEST_IDTOKEN_UPN, UserIdentifierType.RequiredDisplayableId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        assertNull("Error is null", callback.mException);
        assertEquals("Same token as refresh token result", tokenInfo,
                callback.mResult.getToken());

        // Same call again to use it from cache
        signal = new CountDownLatch(1);
        callback = new MockAuthenticationCallback(signal);
        callback.mResult = null;
        removeMockWebRequest(context);
        context.acquireToken(testActivity, new String[]{scopeInMockResponse}, null, "ClienTid", "redirectUri",
                new UserIdentifier(TEST_IDTOKEN_UPN, UserIdentifierType.RequiredDisplayableId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        assertEquals("Same token in response as in cache for same call", tokenInfo,
                callback.mResult.getToken());

        // Empty userid will prompt.
        // Items are linked to userid. If it is not there, it can't use for
        // refresh or access token.
        signal = new CountDownLatch(1);
        testActivity = new MockActivity(signal);
        callback = new MockAuthenticationCallback(signal);
        context.acquireToken(testActivity, resource, null, "ClienTid", "redirectUri", new UserIdentifier("not_exists", UserIdentifierType.RequiredDisplayableId), callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        assertNull("Result is null since it tries to start activity", callback.mResult);
        assertEquals("Activity was attempted to start.",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);

        clearCache(context);
    }

    @SmallTest
    public void testBrokerRedirectUri() throws UnsupportedEncodingException,
            NoSuchAlgorithmException, NoSuchPaddingException {
        MockTokenCache cache = new MockTokenCache(getContext());
        final AuthenticationContext authContext = new AuthenticationContext(getContext(),
                VALID_AUTHORITY, false, cache);

        // act
        String actual = authContext.getRedirectUriForBroker();

        // assert
        assertTrue("should have packagename", actual.contains(TEST_PACKAGE_NAME));
        assertTrue("should have signature url encoded",
                actual.contains(URLEncoder.encode(testTag, AuthenticationConstants.ENCODING_UTF8)));
    }

    private AuthenticationContext getAuthenticationContext(Context mockContext, String authority,
            boolean validate, MockTokenCache mockCache) {
        AuthenticationContext context = new AuthenticationContext(mockContext, authority, validate,
                mockCache);

        return context;
    }

    private MockTokenCache getCacheForRefreshToken(String userid, String displayableId)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalArgumentException,
            IllegalAccessException, InvocationTargetException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException {
        MockTokenCache cache = new MockTokenCache(getContext());
        cache.clear();
        Calendar expiredTime = new GregorianCalendar();
        Log.d("Test", "Time now:" + expiredTime.toString());
        expiredTime.add(Calendar.MINUTE, -60);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(VALID_AUTHORITY);
        refreshItem.setScope(TEST_SCOPE);
        refreshItem.setClientId("clientId");
        refreshItem.setAccessToken("accessToken");
        refreshItem.setRefreshToken("refreshToken=");
        refreshItem.setExpiresOn(expiredTime.getTime());
        refreshItem
                .setUserInfo(new UserInfo(userid, "givenName", "identityProvider", displayableId));
        UserIdentifier useridKey = new UserIdentifier(userid, UserIdentifierType.UniqueId);
        UserIdentifier useridDisplayableId = new UserIdentifier(displayableId, UserIdentifierType.RequiredDisplayableId);
        cache.setItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, TEST_SCOPE, "", "clientId", false,
                useridKey), refreshItem);
        cache.setItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, TEST_SCOPE, "", "clientId", false,
                useridDisplayableId), refreshItem);
        return cache;
    }

    private MockTokenCache getMockCache(int minutes, String token, String[] expectedScope,
            String client, UserIdentifier expectedUser, boolean isMultiResource)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalArgumentException,
            IllegalAccessException, InvocationTargetException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException {
        MockTokenCache cache = new MockTokenCache(getContext());
        // Code response
        Calendar timeAhead = new GregorianCalendar();
        Log.d("Test", "Time now:" + timeAhead.toString());
        timeAhead.add(Calendar.MINUTE, minutes);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(VALID_AUTHORITY);
        refreshItem.setScope(expectedScope);
        refreshItem.setClientId(client);
        refreshItem.setAccessToken(token);
        refreshItem.setRefreshToken("refreshToken=");
        refreshItem.setExpiresOn(timeAhead.getTime());
        cache.setItem(MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, expectedScope, "", client,
                isMultiResource, expectedUser), refreshItem);
        return cache;
    }

    private ITokenCacheStore addItemToCache(MockTokenCache cache, String token,
            String refreshToken, String authority, String[] scope, String clientId, String userId,
            String name, String familyName, String displayId, String tenantId,
            boolean isMultiResource) throws IllegalArgumentException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
  // Code response
        Calendar timeAhead = new GregorianCalendar();
        Log.d(TAG, "addItemToCache Time now:" + timeAhead.toString());
        timeAhead.add(Calendar.MINUTE, 10);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(authority);
        refreshItem.setScope(scope);
        refreshItem.setClientId(clientId);
        refreshItem.setAccessToken(token);
        refreshItem.setRefreshToken(refreshToken);
        refreshItem.setExpiresOn(timeAhead.getTime());
        refreshItem.setIsMultiResourceRefreshToken(isMultiResource);
        refreshItem.setTenantId(tenantId);
        refreshItem.setUserInfo(new UserInfo(userId, name, "", displayId));
        MockTokenCacheKey key = MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, scope, "", clientId, isMultiResource,
               new UserIdentifier( userId, UserIdentifierType.UniqueId));
        Log.d(TAG, "Key: " + key);
        cache.setItem(key, refreshItem);
        TokenCacheItem item = cache.getItem(key);
        assertNotNull("item is in cache", item);

        key = MockTokenCacheKey.createCacheKey(VALID_AUTHORITY, scope, "", clientId,
                isMultiResource, new UserIdentifier(displayId,
                        UserIdentifierType.RequiredDisplayableId));
        Log.d(TAG, "Key: " + key);
        cache.setItem(key, refreshItem);
        item = cache.getItem(key);
        assertNotNull("item is in cache", item);

        return cache;
    }

    private void clearCache(AuthenticationContext context) {
        if (context.getCache() != null) {
            context.getCache().clear();
        }
    }

    class MockDiscovery implements IDiscovery {

        private boolean isValid = false;

        private URL authorizationUrl;

        private UUID correlationId;

        MockDiscovery(boolean validFlag) {
            isValid = validFlag;
        }

        @Override
        public boolean isValidAuthority(URL authorizationEndpoint) {
            authorizationUrl = authorizationEndpoint;
            return isValid;
        }

        public URL getAuthorizationUrl() {
            return authorizationUrl;
        }

        @Override
        public void setCorrelationId(UUID requestCorrelationId) {
            correlationId = requestCorrelationId;
        }
    }

    /**
     * Mock activity
     */
    class MockActivity extends Activity {

        private static final String TAG = "MockActivity";

        int mStartActivityRequestCode = -123;

        Intent mStartActivityIntent;

        CountDownLatch mSignal;

        Bundle mStartActivityOptions;

        public MockActivity(CountDownLatch signal) {
            mSignal = signal;
        }

        @SuppressLint("Registered")
        public MockActivity() {
            // TODO Auto-generated constructor stub
        }

        @Override
        public String getPackageName() {
            return ReflectionUtils.TEST_PACKAGE_NAME;
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            Log.d(TAG, "startActivityForResult:" + requestCode);
            mStartActivityIntent = intent;
            mStartActivityRequestCode = requestCode;
            // test call needs to stop the tests at this point. If it reaches
            // here, it means authenticationActivity was attempted to launch.
            // Since it is mock activity, it will not launch something.
            if (mSignal != null)
                mSignal.countDown();
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
            Log.d(TAG, "startActivityForResult:" + requestCode);
            mStartActivityIntent = intent;
            mStartActivityRequestCode = requestCode;
            mStartActivityOptions = options;
            // test call needs to stop the tests at this point. If it reaches
            // here, it means authenticationActivity was attempted to launch.
            // Since it is mock activity, it will not launch something.
            if (mSignal != null)
                mSignal.countDown();
        }

    }

    private UserIdentifier getUserId(String uid) {
        return new UserIdentifier(uid, UserIdentifierType.RequiredDisplayableId);
    }
}