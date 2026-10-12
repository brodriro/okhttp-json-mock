package io.github.brodriro.okhttpjsonmock.providers;

import java.io.InputStream;

public interface InputStreamProvider {
    InputStream provide(String path);
}
