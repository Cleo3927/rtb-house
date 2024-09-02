package jowita.drozdowicz;

import java.time.Instant;
import java.util.List;

import jowita.drozdowicz.db.DataDao;
import jowita.drozdowicz.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EchoClient {

    private static final Logger log = LoggerFactory.getLogger(EchoClient.class);

    @Autowired
    private DataDao dataDao;

    @PostMapping("/user_tags")
    public ResponseEntity<Void> addUserTag(@RequestBody(required = false) UserTagEvent userTag) {
        dataDao.put(userTag);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/user_profiles/{cookie}")
    public ResponseEntity<UserProfileResult> getUserProfile(@PathVariable("cookie") String cookie,
            @RequestParam("time_range") String timeRangeStr,
            @RequestParam(defaultValue = "200") int limit,
            @RequestBody(required = false) UserProfileResult expectedResult) {

        String[] timeRange = timeRangeStr.split("_");
        String startTime = timeRange[0];
        String endTime = timeRange[1];

        Instant start = Instant.parse(startTime + "Z");
        Instant end = Instant.parse(endTime + "Z");

        UserProfileResult result = dataDao.get(cookie, start, end, limit);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/aggregates")
    public ResponseEntity<AggregatesQueryResult> getAggregates(@RequestParam("time_range") String timeRangeStr,
            @RequestParam("action") Action action,
            @RequestParam("aggregates") List<Aggregate> aggregates,
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "brand_id", required = false) String brandId,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestBody(required = false) AggregatesQueryResult expectedResult) {

        String[] timeRange = timeRangeStr.split("_");
        String startTime = timeRange[0];
        String endTime = timeRange[1];

        Instant start = Instant.parse(startTime + "Z");
        Instant end = Instant.parse(endTime + "Z");

        AggregatesQueryResult result = dataDao.aggregate(action, start, end, aggregates, origin, brandId, categoryId);
        return ResponseEntity.ok(result);
    }
}
