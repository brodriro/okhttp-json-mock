import io.github.brodriro.okhttpjsonmock.MockLogger;
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor;
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Uses the library the way a Java project does: a lambda provider, the builder and a 3.0 constructor. */
public final class JavaConsumer {

    private JavaConsumer() {
    }

    public static Map<String, Boolean> run() throws IOException {
        Map<String, Boolean> results = new LinkedHashMap<>();
        InputStreamProvider provider = path -> "api/v1/users.json".equals(path)
            ? new ByteArrayInputStream("[{\"id\":1}]".getBytes(StandardCharsets.UTF_8))
            : null;
        Request request = new Request.Builder().url("http://example.com/api/v1/users").build();

        OkHttpClient built = new OkHttpClient.Builder()
            .addInterceptor(new OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE).build())
            .build();
        try (Response response = built.newCall(request).execute()) {
            results.put("builder and lambda provider", response.code() == 200);
        }

        OkHttpClient legacy = new OkHttpClient.Builder()
            .addInterceptor(new OkHttpMockInterceptor(provider, 0, 0, 0))
            .build();
        try (Response response = legacy.newCall(request).execute()) {
            results.put("constructor kept from 3.0", response.code() == 200);
        }
        return results;
    }
}
