package com.example;

import com.splunk.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;

@Component
public class MetricsDemo implements MeterBinder {

    private Service service;

    @Value("${AUTH_TOKEN}")
    private String authToken;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder("splunk_license_quota_used", this, value -> licenseUsedPercentage())
                .description("Today's Percentage of Daily License Quota Used")
                .tags(Tags.of(Tag.of("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))))
                .baseUnit("percentage")
                .register(meterRegistry);
    }

    private Double licenseUsedPercentage() {
        String res = "";

        connectToSplunk();

        // Create a simple search job
        String mySearch = "| rest splunk_server=DESKTOP-GDEJODG /services/licenser/pools | rename title AS Pool | " +
                "search [rest splunk_server=DESKTOP-GDEJODG /services/licenser/groups | search is_active=1 " +
                "| eval stack_id=stack_ids | fields stack_id] | eval quota=if(isnull(effective_quota),quota,effective_quota) " +
                "| eval \"% used\"=round(used_bytes/quota*100,2) | fields Pool \"% used\"";
        Job job = service.getJobs().create(mySearch);

        // Wait for the job to finish
        while (!job.isDone()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Display results
        JobResultsArgs resultsArgs = new JobResultsArgs();
        // Specify JSON as the output mode for results
        resultsArgs.setOutputMode(JobResultsArgs.OutputMode.JSON);

        // Display results in JSON using ResultsReaderJson
        InputStream results = job.getResults(resultsArgs);
        ResultsReaderJson resultsReader;

        try {
            resultsReader = new ResultsReaderJson(results);
            HashMap<String, String> event;
            event = resultsReader.getNextEvent(); //Only one event is outputted
            if(event != null) {
                Iterator<String> iterator = event.keySet().iterator();
                //This event has only one key
                if(iterator.hasNext()) {
                    String key = iterator.next();
                    res = event.get(key);
                }
            }
            resultsReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Double.valueOf(res);
    }

    private void connectToSplunk() {
        HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2);
        service = new Service("localhost", 8089);

        //log in with an existing token
        service.setToken("Splunk ".concat(authToken));
    }
}
