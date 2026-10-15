package com.abhayshah.localmusic;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Small on-device HTTP bridge used by NewPipe Extractor. No proxy or LocalMusic server. */
final class ExtractorDownloader extends Downloader {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36";

    @Override public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(18_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(request.httpMethod());
        connection.setRequestProperty("User-Agent", USER_AGENT);
        for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
            for (String value : header.getValue()) connection.addRequestProperty(header.getKey(), value);
        }
        byte[] body = request.dataToSend();
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
        }
        int code = connection.getResponseCode();
        if (code == 429) throw new ReCaptchaException("Streaming service requested verification", request.url());
        InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = input == null ? "" : readAll(input);
        Response response = new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), responseBody, connection.getURL().toString());
        connection.disconnect();
        return response;
    }

    private static String readAll(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            for (int read; (read = stream.read(buffer)) >= 0; ) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
