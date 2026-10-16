package io.github.brodriro.okhttpjsonmock.sample;

import static org.junit.Assert.assertEquals;

import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor;
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** The Java snippet of the README "Quick start", copied as it is, run on a device. */
@RunWith(AndroidJUnit4.class)
public class ReadmeJavaSnippetTest {

    /** Stands for the {@code getAssets()} of the Activity the snippet is written in. */
    private AssetManager getAssets() {
        return InstrumentationRegistry.getInstrumentation().getContext().getAssets();
    }

    @Test
    public void quickStartInJava() throws IOException {
        InputStreamProvider assets = path -> {
            try {
                return getAssets().open(path);
            } catch (IOException e) {
                return null; // no mock at this path
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new OkHttpMockInterceptor.Builder(assets).build())
            .build();

        Request request = new Request.Builder().url("https://example.com/api/v1/users/1").build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            assertEquals("{ \"id\": 1, \"name\": \"John\" }\n", response.body().string());
        }
    }
}
