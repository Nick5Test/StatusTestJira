package org.example;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import okhttp3.*;


public class Main {
    public static void main(String[] args) {
        String resp = null;
        getJson("https://api.zephyrscale.smartbear.com/v2/testcases?projectKey=NEX&maxResults=2000&startAt=0", "NexiPay");
        getJson("https://api.zephyrscale.smartbear.com/v2/testcases?maxResults=2000&startAt=0&projectKey=DCR", "PortaleTitolari");
        getJson("https://api.zephyrscale.smartbear.com/v2/testcases?maxResults=2000&startAt=0&projectKey=PAYX", "Yap");


    }

    public static Object getJson(String url, String project) {
        String StrResponse = null;
        int doneCount = 0, suspended = 0, wip = 0, todo = 0, notunnable = 0, NEW = 0;

        OkHttpClient newClient = createCustomOkHttpClient();
        MediaType mediaType = MediaType.parse("text/plain");
        //RequestBody body = RequestBody.create(mediaType, "");
        //creazione della chiamata
        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJjb250ZXh0Ijp7ImJ" +
                        "hc2VVcmwiOiJodHRwczovL2ljYnBpamlyYS5hdGxhc3NpYW4ubmV0IiwidXNlciI6eyJhY2NvdW50SWQiOiI1Y2NiMWEzMz" +
                        "MyNTJiNjExYzhlYzE4ZjEifX0sImlzcyI6ImNvbS5rYW5vYWgudGVzdC1tYW5hZ2VyIiwic3ViIjoiamlyYTo1NmJmOWZhMi1jMTk5LTRiMTItOGVkMi0zMjNkMmJkMDA3NDIiLCJle" +
                        "HAiOjE3MjQ1MDI5MzQsImlhdCI6MTY5Mjk2NjkzNH0.ws-as8CPRISDIDD_HcTiuVq_fN-7UHgit4bCPqtXA0c")
                .build();
        String testautomation;
        String stringaWIP;
        float automated, numAutomated,denomAutomated ;
        float automatable, numAutomatable, denomAutomatable;
        try {
            Response response = newClient.newCall(request).execute();
            StrResponse = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseNode = mapper.readTree(StrResponse);
            JsonNode valuesArray = responseNode.path("values");
            for (JsonNode valueNode : valuesArray) {
                JsonNode testAutomationNode = valueNode.path("customFields").path("Test Automation");
                if (testAutomationNode.isArray()) {
                    for (JsonNode subNode : testAutomationNode) {
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("Done")) {
                            doneCount++;
                        }
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("To Do")) {
                            todo++;
                        }
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("Suspended")) {
                            suspended++;
                        }
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("WIP")) {
                            wip++;
                        }
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("NEW")) {
                            NEW++;
                        }
                        if (subNode.isTextual() && subNode.asText().equalsIgnoreCase("Not Runnable")) {
                            notunnable++;
                        }
                    }
                }
            }
            numAutomated = doneCount + notunnable;
            denomAutomated = notunnable + doneCount + todo + wip;
            automated = (numAutomated / denomAutomated)*100;

            numAutomatable = todo + wip + doneCount + notunnable;
            denomAutomatable = doneCount + todo + wip + NEW + suspended + notunnable;
            automatable = (numAutomatable/denomAutomatable)*100;

            //stringaWIP = String.valueOf(wip) + "%";

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Done Testcases: " + doneCount);
        System.out.println("To Do Testcases: " + todo);
        System.out.println("Suspended Testcases: " + suspended);
        System.out.println("Wip Testcases: " + wip);
        System.out.println("New Testcases: " + NEW);
        System.out.println("Not runnable Testcases: " + notunnable);


        String token = "vKIaG6BmsvfG-MpVjCeUH9XTT18JGci5DaSsdZDvrDn7LYb21vHqa6j7POBzstP09mAMgtPakbtVvT_4vsSz4g==";
        String org = "JiraStatus";
        String bucket = "StatusJira";
        InfluxDBClient client = InfluxDBClientFactory.create("http://localhost:8087", token.toCharArray());

        //creazione del punto dati (table)
        Point point = Point
                .measurement("StatusJira")
                .addTag("tag1", project)
                .addField("Done", doneCount)
                .addField("ToDo", todo)
                .addField("Suspended", suspended)
                .addField("Wip", wip)
                .addField("New", NEW)
                .addField("NotRunnable", notunnable)
                .addField("percentage automated on automatable", automated)
                .addField("percentage automatable on total", automatable)
                .time(Instant.now(), WritePrecision.NS);
        WriteApiBlocking writeApi = client.getWriteApiBlocking();
        writeApi.writePoint(bucket, org, point);



        /*String query = "from(bucket: \""+bucket+"\") |> range(start: -1h)";
        List<FluxTable> tables = client.getQueryApi().query(query, org);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                System.out.println(record);
            }
        }
        */
        client.close();


        return null;
    }
    private static void searchLabel (JsonNode node, String target){
        if (node.isArray()){
            //itero attraverso gli elmementi dell'array
        }
    }

    public static OkHttpClient createCustomOkHttpClient() {
        //creo l'istaza del trust manager personalizzato
        X509TrustManager customTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        //Crazione SSLContext inizializzando con il trust manager personalizzato
        SSLContext sslContext;
        try {
            //configurazione dell'sslContext per utilizzare il protocollo TLS (Transport Layer Security)
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{customTrustManager}, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException("Errore durante la configurazione del SSLContext.", e);
        }
        //creazione OkHttpClient.Builder e configurazione del custom trust manager
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), customTrustManager)
                .hostnameVerifier((hostname, session) -> true);
        return builder.build();
    }


}

