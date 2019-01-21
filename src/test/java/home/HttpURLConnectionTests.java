package home;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HttpURLConnectionTests {
    @Test
    public void test() throws Exception {
        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("name", "my name");
        queryParameters.put("nick name", "my nick name");
        String result = connect("http://localhost:4567/api/a", queryParameters);
        System.out.println(result);
    }

    private String connect(String _url, Map<String, Object> parameters) throws Exception {
        BufferedReader reader = null;
        try {
            String queryString = parameters.entrySet().stream()
                    .map(e -> {
                        try {
                            return URLEncoder.encode(e.getKey(), "UTF-8") + "=" +
                                    URLEncoder.encode(e.getValue().toString(), "UTF-8");
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("&"));

            URL url = new URL(_url + "?" + queryString);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setReadTimeout(15 * 1000);           // 15 seconds
            con.connect();

            reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null) stringBuilder.append(line).append('\n');
            return stringBuilder.toString();
        }
        finally {
            if (reader != null) reader.close();
        }
    }
}
