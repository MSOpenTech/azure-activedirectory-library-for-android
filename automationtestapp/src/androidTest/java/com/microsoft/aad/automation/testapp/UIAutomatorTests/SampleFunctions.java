package com.microsoft.aad.automation.testapp.UIAutomatorTests;

import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;


import junit.framework.Assert;

import static android.support.test.InstrumentationRegistry.getInstrumentation;


public class SampleFunctions {

    private static UiDevice mDevice;

    private static final String AUTHENTICATOR_APP_NAME = "Authenticator";

    private static final String PORTAL_APP_NAME = "Company Portal";

    private static final int WINDOW_TIMEOUT = 3000;

    private static String mAppName;

    private static String mUPN;
    private static String mSecret;
    private static Boolean mFederated;

    private static void click_by_Text(String text)
    {
        set_device();
        UiObject appButton = mDevice.findObject(new UiSelector()
                .text(text));
        try{
            appButton.click();
        } catch (UiObjectNotFoundException e) {
            Assert.fail("Didn't find text to click on: " + text);
        }
    }

    private static void click_by_resourceID(String resourceID)
    {
        set_device();
        UiObject appButton = mDevice.findObject(new UiSelector()
                .resourceId(resourceID));
        try{
            appButton.click();
        } catch (UiObjectNotFoundException e) {
            Assert.fail("Didn't find resourceID to click on: " + resourceID);
        }
    }

    private static void click_and_await(String text)
    {
        set_device();
        UiObject appButton = mDevice.findObject(new UiSelector()
                .text(text));
        try{
            appButton.clickAndWaitForNewWindow(WINDOW_TIMEOUT);
        } catch (UiObjectNotFoundException e) {
            Assert.fail("Didn't find text to click on: " + text);
        }
    }

    private static void set_text_by_Text(String UIElementText,String text_to_set)
    {
        set_device();
        UiObject appButton = mDevice.findObject(new UiSelector()
                .text(UIElementText));
        try{
            appButton.setText(text_to_set);
        } catch (UiObjectNotFoundException e) {
            Assert.fail("Didn't find element");
        }
    }

    private static void set_device()
    {
        if(mDevice == null){
            mDevice = UiDevice.getInstance(getInstrumentation());
        }
    }

    private static void open_Applications_View()
    {
        set_device();
        // press the home button
        mDevice.pressHome();
        // Bring up the default launcher by searching for a UI component
        // that matches the content description for the launcher button.
        UiObject allAppsButton = mDevice.findObject(new UiSelector().description("Apps"));
        // Perform a click on the button to load the launcher.
        try{
            allAppsButton.clickAndWaitForNewWindow();
        } catch (Exception e){
            Assert.fail("Didnt find apps");
        }
    }

    public static void launch_App(String AppName)
    {
        set_device();
        mAppName = AppName;
        open_Applications_View();
        click_by_Text(mAppName);
    }




    public static void uninstall_App(String AppName)
    {
        set_device();
        mAppName = AppName;

        UiObject settingsButton = mDevice.findObject(new UiSelector().description("Settings"));
        // Perform a click on the button to load the launcher.
        try{
            settingsButton.clickAndWaitForNewWindow();
        } catch (Exception e){
            Assert.fail("Didnt find settings");
        }

        click_by_Text("Apps");

        click_and_await(mAppName);

        click_by_Text("Uninstall");

        click_by_Text("OK");

    }

    public static void clear_app_data(String AppName)
    {
        set_device();
        mDevice.pressHome();
        mAppName = AppName;

        UiObject settingsButton = mDevice.findObject(new UiSelector().description("Settings"));
        // Perform a click on the button to load the launcher.
        try{
            settingsButton.clickAndWaitForNewWindow();
        } catch (Exception e){
            Assert.fail("Didnt find settings");
        }

        click_by_Text("Apps");

        click_and_await(mAppName);

        click_by_Text("Storage");

        click_by_Text("Clear data");

        click_by_Text("OK");

    }

    private static void allow_permission()
    {
        click_by_resourceID("com.android.packageinstaller:id/permission_allow_button");
    }

    public static void enroll_authenticator_personal(String UPN,String Secret,Boolean Federated)
    {
        mFederated = Federated;
        mUPN = UPN;
        mSecret = Secret;

        launch_App(AUTHENTICATOR_APP_NAME);
        click_by_resourceID("com.azure.authenticator:id/next");
        click_by_resourceID("com.azure.authenticator:id/next");
        click_by_resourceID("com.azure.authenticator:id/done");
        click_by_resourceID("com.azure.authenticator:id/zero_accounts_add_account_button");
        //personal account
        click_by_resourceID("com.azure.authenticator:id/add_account_personal_btn");
        // OTHER_ACCOUNT click_by_resourceID("com.azure.authenticator:id/add_account_other_btn");
        // WORK_ACCOUNT click_by_resourceID("com.azure.authenticator:id/add_account_work_btn");
        authenticate_webview();
    }

    private static void authenticate_webview()
    {
        set_text_by_Text("Enter your email, phone, or Skype.",mUPN);
        click_and_await("Next");
    }


    public static void unenroll_authenticator()
    {
        launch_App("Authenticator");
    }
}
