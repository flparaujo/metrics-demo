package com.example;

import com.splunk.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
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

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder("splunk_license_quota_used", this, value -> licenseUsedPercentage())
                .description("Today's Percentage of Daily License Quota Used")
                .tags(Tags.of(Tag.of("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))))
                .baseUnit("percentage")
                .register(meterRegistry);
    }

    public Double licenseUsedPercentage() {
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
        service.setToken("Splunk eyJraWQiOiJzcGx1bmsuc2VjcmV0IiwiYWxnIjoiSFM1MTIiLCJ2ZXIiOiJ2MiIsInR0eXAiOiJzdGF0aWMifQ.eyJpc3MiOiJhZG1pbiBmcm9tIERFU0tUT1AtR0RFSk9ERyIsInN1YiI6ImFkbWluIiwiYXVkIjoiYXV0aGVudGljYXRlIGZyb20gamF2YSBhcHBsaWNhdGlvbiIsImlkcCI6IlNwbHVuayIsImp0aSI6IjUzMWZlMjVjMzBiZGZmODdjMTY4MmQ4OTlmMDMxMGI5NzIzNGY0NGE2MDkzZWU3MGMxOTU3YTg1ZjU2NzQ3ZGIiLCJpYXQiOjE2MDU3NDc5ODEsImV4cCI6MTYzNzI4Mzk4MSwibmJyIjoxNjA1NzQ3OTgxfQ.IPH5hw9hQSmSlwh4sBBdMVBVrP7MCRsW_86DMIFJvEVxEt_1C_bgTv3MeRBF8ofIIM6s4rw3HgjdiKv7SWdeUQ");
    }
}
