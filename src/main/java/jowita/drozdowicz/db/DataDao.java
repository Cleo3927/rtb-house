package jowita.drozdowicz.db;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.aerospike.client.*;
import com.aerospike.client.Record;
import com.aerospike.client.async.*;
import com.aerospike.client.cdt.*;
import com.aerospike.client.exp.Exp;
import com.aerospike.client.listener.DeleteListener;
import com.aerospike.client.listener.RecordListener;
import com.aerospike.client.listener.WriteListener;
import com.aerospike.client.policy.*;
import com.aerospike.client.query.*;
import com.aerospike.client.task.IndexTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jowita.drozdowicz.domain.*;
import org.apache.logging.log4j.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;

import com.aerospike.client.lua.LuaConfig;
import com.aerospike.client.task.RegisterTask;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

@Component
public class DataDao {

    private static final Logger logger = LoggerFactory.getLogger(DataDao.class);

    private static final String NAMESPACE = "mimuw";
    private static final String SET_BUY = "buys";
    private static final String SET_VIEW = "views";
    private static final String SET_TRIMMED = "tags_trimmed";

    private static final String DATA_BIN = "data";
    private static final String TIME_BIN = "time";
    private static final String COOKIE_BIN = "cookie";
    private static final String ORIGIN_BIN = "origin";
    private static final String BRAND_ID_BIN = "brand_id";
    private static final String CATEGORY_ID_BIN = "category_id";
    private static final String PRICE_BIN = "price";

    private static final String TIME_INDEX_VIEW = "time_index_view";
    private static final String TIME_INDEX_BUY = "time_index_buy";
    private static final String COUNT_BIN = "count";
    private final WebClientCodecCustomizer exchangeStrategiesCustomizer;

    private AerospikeClient client;
    private RecordListener emptyRecordListener;
    private RecordListener deleteTooLongListsListener;

    private static ClientPolicy defaultClientPolicy() {
        ClientPolicy defaultClientPolicy = new ClientPolicy();
        defaultClientPolicy.readPolicyDefault.replica = Replica.MASTER_PROLES;
        defaultClientPolicy.readPolicyDefault.socketTimeout = 100;
        defaultClientPolicy.readPolicyDefault.totalTimeout = 100;
        defaultClientPolicy.writePolicyDefault.socketTimeout = 150;
        defaultClientPolicy.writePolicyDefault.totalTimeout = 1500;
        defaultClientPolicy.writePolicyDefault.maxRetries = 1;
        defaultClientPolicy.writePolicyDefault.commitLevel = CommitLevel.COMMIT_MASTER;
        defaultClientPolicy.writePolicyDefault.recordExistsAction = RecordExistsAction.REPLACE;
        defaultClientPolicy.eventLoops = new NioEventLoops(new EventPolicy(), Runtime.getRuntime().availableProcessors());
        return defaultClientPolicy;
    }

    public DataDao(@Value("${aerospike.seeds}") String[] aerospikeSeeds, @Value("${aerospike.port}") int port, WebClientCodecCustomizer exchangeStrategiesCustomizer) {
        this.exchangeStrategiesCustomizer = exchangeStrategiesCustomizer;

        this.emptyRecordListener = new RecordListener() {
            @Override
            public void onSuccess(Key key, Record record) {

            }

            @Override
            public void onFailure(AerospikeException e) {

            }
        };

        this.client = new AerospikeClient(defaultClientPolicy(), Arrays.stream(aerospikeSeeds).map(seed -> new Host(seed, port)).toArray(Host[]::new));
    }

    @PostConstruct
    public void registerUdfs() throws IOException {
        LuaConfig.SourceDirectory = "src/main/resources/udf/";
        RegisterTask registerTask = client.register(null, "src/main/resources/udf/count.lua", "count.lua", Language.LUA);
        registerTask.waitTillComplete(1000, 10000);

        client.truncate(null, NAMESPACE, SET_BUY, null);
        client.truncate(null, NAMESPACE, SET_VIEW, null);
        client.truncate(null, NAMESPACE, SET_TRIMMED, null);

        try {
            IndexTask indexTask = client.createIndex(null, NAMESPACE, SET_BUY, TIME_INDEX_BUY, TIME_BIN, IndexType.NUMERIC);
            indexTask.waitTillComplete();
        } catch (Exception e) {
            logger.info("Index already exists");
        }

        try {
            IndexTask indexTask = client.createIndex(null, NAMESPACE, SET_VIEW, TIME_INDEX_VIEW, TIME_BIN, IndexType.NUMERIC);
            indexTask.waitTillComplete();
        } catch (Exception e) {
            logger.info("Index already exists");
        }

//        runTests();
    }

    private void runTests() {
        Instant exampleTime = Instant.parse("2021-01-01T05:23:00Z");
        Instant exampleTimeFrom2 = exampleTime.plusSeconds(1);

        UserTagEvent test1 = new UserTagEvent(
                exampleTime,
                "cookie1",
                "PL",
                Device.MOBILE,
                Action.VIEW,
                "origin1",
                new Product(0, "brand1", "category1", 100)
        );

        UserTagEvent test2 = new UserTagEvent(
                exampleTimeFrom2,
                test1.getCookie(),
                test1.getCountry(),
                test1.getDevice(),
                test1.getAction(),
                test1.getOrigin(),
                test1.getProductInfo()
        );

        put(test1);
        put(test2);

        UserProfileResult result = get(
                test1.getCookie(), test1.getTime(), test2.getTime().plusSeconds(60), 10
        );
        assert result.getViews().size() == 2;

        AggregatesQueryResult aggregatesQueryResult = aggregate(
                Action.VIEW, test1.getTime(), test1.getTime().plusSeconds(120), Arrays.asList(Aggregate.COUNT, Aggregate.SUM_PRICE), null, null, null
        );
        assert aggregatesQueryResult.getRows().size() == 2;

        for (int i = 0; i < 300; i++) {
            UserTagEvent event = new UserTagEvent(
                    exampleTime.plusSeconds(i),
                    "cookie2",
                    "PL",
                    Device.MOBILE,
                    Action.VIEW,
                    "origin1",
                    new Product(0, "brand1", "category1", 100)
            );
            put(event);
        }

        UserProfileResult result2 = get(
                "cookie2", exampleTime, exampleTime.plusSeconds(400), 200
        );
        assert result2.getViews().size() == 200;
        assert (result2.getViews().get(0).getTime().toEpochMilli() - exampleTime.toEpochMilli()) == 299 * 1000;
        assert (result2.getViews().get(199).getTime().toEpochMilli() - exampleTime.toEpochMilli()) == 100 * 1000;
    }

    private String getItemKey(String cookie, Action action) {
        return cookie + action.toString();
    }

    private String getItemKeyAggregation(Long roundedTime, String origin, String brandId, String categoryId) {
        // round time to 1m
        return roundedTime + origin + brandId + categoryId;
    }

    private void putForTrimmed(UserTagEvent event) {
        WritePolicy writePolicy = new WritePolicy(client.writePolicyDefault);
        writePolicy.recordExistsAction = RecordExistsAction.UPDATE;

        Key key = new Key(NAMESPACE, SET_TRIMMED, getItemKey(event.getCookie(), event.getAction()));

        byte[] data = SerializationUtils.serialize(new SerializableUserTagEvent(event));

        // store data with prefix of event time, to make it sortable
        Long eventTime = event.getTime().toEpochMilli();
        byte[] prefix = new byte[8];
        for (int i = 0; i < 8; i++) {
            prefix[7 - i] = (byte) ((byte) 255 - ((byte) (eventTime >> (i * 8))));
        }

        byte[] dataWithPrefix = new byte[prefix.length + data.length];
        System.arraycopy(prefix, 0, dataWithPrefix, 0, prefix.length);
        System.arraycopy(data, 0, dataWithPrefix, prefix.length, data.length);

        com.aerospike.client.Value dataValue = com.aerospike.client.Value.get(dataWithPrefix);


        ListPolicy policy = new ListPolicy(ListOrder.ORDERED, ListWriteFlags.ADD_UNIQUE);
        final int TRIMMED_SET_LIMIT = 250;

        client.operate(
                null,
                emptyRecordListener,
                writePolicy,
                key,
                ListOperation.append(policy, DATA_BIN, dataValue),
                ListOperation.trim(DATA_BIN, 0, TRIMMED_SET_LIMIT)
        );
    }

    private void putForAggregation(UserTagEvent event) {
        WritePolicy writePolicy = new WritePolicy(client.writePolicyDefault);
        writePolicy.recordExistsAction = RecordExistsAction.UPDATE;

        String usedSet = event.getAction() == Action.BUY ? SET_BUY : SET_VIEW;
        Long roundedTime = (event.getTime().toEpochMilli() / 60000) * 60000;
        String uniqueKey = getItemKeyAggregation(roundedTime, event.getOrigin(), event.getProductInfo().getBrandId(), event.getProductInfo().getCategoryId());

        Key key = new Key(NAMESPACE, usedSet, uniqueKey);

        Bin timeBin = new Bin(TIME_BIN, roundedTime);
        Bin originBin = new Bin(ORIGIN_BIN, event.getOrigin());
        Bin brandIdBin = new Bin(BRAND_ID_BIN, event.getProductInfo().getBrandId());
        Bin categoryIdBin = new Bin(CATEGORY_ID_BIN, event.getProductInfo().getCategoryId());

        client.operate(
                null,
                emptyRecordListener,
                writePolicy,
                key,
                Operation.put(timeBin),
                Operation.put(originBin),
                Operation.put(brandIdBin),
                Operation.put(categoryIdBin),
                Operation.add(new Bin(COUNT_BIN, 1)),
                Operation.add(new Bin(PRICE_BIN, event.getProductInfo().getPrice()))
        );
    }

    public void put(UserTagEvent event) {
        putForTrimmed(event);
        putForAggregation(event);
    }

    public UserProfileResult get(String cookie, Instant timeFrom, Instant timeTo, int limit) {
        Key buyKey = new Key(NAMESPACE, SET_TRIMMED, getItemKey(cookie, Action.BUY));
        Key viewKey = new Key(NAMESPACE, SET_TRIMMED, getItemKey(cookie, Action.VIEW));

        Record buyRecord = client.get(null, buyKey);
        Record viewRecord = client.get(null, viewKey);

        List<byte[]> combinedList = new ArrayList<>();
        if (buyRecord != null)
            combinedList.addAll((List<byte[]>) buyRecord.getList(DATA_BIN));

        List<byte[]> viewList = new ArrayList<>();
        if (viewRecord != null)
            combinedList.addAll((List<byte[]>) viewRecord.getList(DATA_BIN));

        Set<UserTagEvent> views = new TreeSet<>(Comparator.comparing(UserTagEvent::getTime));
        Set<UserTagEvent> buys = new TreeSet<>(Comparator.comparing(UserTagEvent::getTime));

        for (byte[] data : combinedList) {
            // remove first 8 bytes of prefix
            byte[] dataWithoutPrefix = new byte[data.length - 8];
            System.arraycopy(data, 8, dataWithoutPrefix, 0, dataWithoutPrefix.length);
            UserTagEvent event = ((SerializableUserTagEvent) SerializationUtils.deserialize(dataWithoutPrefix)).toUserTagEvent();
            if (event.getTime().compareTo(timeFrom) < 0 || event.getTime().compareTo(timeTo) >= 0)
                continue;
            if (event.getAction() == Action.VIEW) {
                views.add(event);
                if (views.size() > limit)
                    views.remove(views.iterator().next());
            } else {
                buys.add(event);
                if (buys.size() > limit)
                    buys.remove(buys.iterator().next());
            }
        }

        List<UserTagEvent> viewsList = new ArrayList<>(views);
        List<UserTagEvent> buysList = new ArrayList<>(buys);

        viewsList.sort(Comparator.comparing(UserTagEvent::getTime).reversed());
        buysList.sort(Comparator.comparing(UserTagEvent::getTime).reversed());

        return new UserProfileResult(cookie, viewsList, buysList);
    }

    public AggregatesQueryResult aggregate(Action action, Instant timeFrom, Instant timeTo, List<Aggregate> aggregates, String origin, String brandId, String categoryId) {
        Statement statement = new Statement();
        statement.setNamespace(NAMESPACE);
        statement.setSetName(action == Action.VIEW ? SET_VIEW : SET_BUY);
        statement.setFilter(Filter.range(TIME_BIN, timeFrom.toEpochMilli(), timeTo.toEpochMilli() - 1));
        statement.setIndexName(action == Action.VIEW ? TIME_INDEX_VIEW : TIME_INDEX_BUY);

        com.aerospike.client.Value originValue = com.aerospike.client.Value.get(origin);
        com.aerospike.client.Value brandIdValue = com.aerospike.client.Value.get(brandId);
        com.aerospike.client.Value categoryIdValue = com.aerospike.client.Value.get(categoryId);

        HashMap<Long, HashMap<String, Long>> result = new HashMap<>();

        try (ResultSet resultSet = client.queryAggregate(null, statement, "count", "count", originValue, brandIdValue, categoryIdValue)) {
            Iterator<Object> iterator = resultSet.iterator();

            if (iterator.hasNext()) {
                result = (HashMap<Long, HashMap<String, Long>>) iterator.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        columns.add("1m_bucket");
        columns.add("action");
        if (origin != null) {
            columns.add("origin");
        }
        if (brandId != null) {
            columns.add("brand_id");
        }
        if (categoryId != null) {
            columns.add("category_id");
        }
        for (Aggregate aggregate : aggregates) {
            columns.add(aggregate.toString().toLowerCase());
        }

        for (Instant instant = timeFrom; instant.isBefore(timeTo); instant = instant.plusSeconds(60)) {
            List<String> row = new ArrayList<>();
            row.add(instant.toString().replace("Z", ""));
            row.add(action.toString());
            if (origin != null) {
                row.add(origin);
            }
            if (brandId != null) {
                row.add(brandId);
            }
            if (categoryId != null) {
                row.add(categoryId);
            }
            for (Aggregate aggregate : aggregates) {
                switch (aggregate) {
                    case COUNT:
                        if (result.get(instant.toEpochMilli()) == null) {
                            row.add("0");
                        } else {
                            row.add(String.valueOf(result.get(instant.toEpochMilli()).get("count")));
                        }
                        break;
                    case SUM_PRICE:
                        if (result.get(instant.toEpochMilli()) == null) {
                            row.add("0");
                        } else {
                            row.add(String.valueOf(result.get(instant.toEpochMilli()).get("total")));
                        }
                        break;
                }
            }
            rows.add(row);
        }

        return new AggregatesQueryResult(columns, rows);
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}

