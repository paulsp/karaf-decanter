/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.appender.elasticsearch;

import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.MockNode;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.service.event.Event;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
@ThreadLeakScope(value = ThreadLeakScope.Scope.NONE)
public class TestElasticsearchAppender {

    private static final String CLUSTER_NAME = "elasticsearch-test";
    private static final String HOST = "127.0.0.1";
    private static final int HTTP_PORT = 9201;
    private static final int TRANSPORT_PORT = 9301;

    private Node node;

    @Before
    public void setup() throws Exception {
        Collection plugins = Arrays.asList(Netty4Plugin.class);
        Settings settings = Settings.builder()
                .put(ClusterName.CLUSTER_NAME_SETTING.getKey(), CLUSTER_NAME)
                .put(Node.NODE_NAME_SETTING.getKey(), "test")
                .put(NetworkModule.HTTP_TYPE_KEY, Netty4Plugin.NETTY_HTTP_TRANSPORT_NAME)
                .put(Environment.PATH_HOME_SETTING.getKey(), "target/data")
                .put(Environment.PATH_DATA_SETTING.getKey(), "target/data")
                .put("network.host", HOST)
                .put("http.port", HTTP_PORT)
                .put(NetworkModule.TRANSPORT_TYPE_KEY, Netty4Plugin.NETTY_TRANSPORT_NAME)
                .put("transport.port", TRANSPORT_PORT)
                .build();
        node = new MockNode(settings, plugins);
        node.start();
    }

    @After
    public void teardown() throws Exception {
        node.close();
    }

    @Test(timeout = 60000L)
    public void test() throws Exception {
        Marshaller marshaller = new JsonMarshaller();
        ElasticsearchAppender appender = new ElasticsearchAppender();
        appender.marshaller = marshaller;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(ElasticsearchAppender.ADDRESSES_PROPERTY, "http://" + HOST + ":" + HTTP_PORT);
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, ".*refused.*");
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, ".*refused.*");
        appender.open(config);
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("refused", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "refused").put("c", "d").map()));
        appender.close();

        HttpHost host = new HttpHost(HOST, HTTP_PORT, "http");
        RestClient client = RestClient.builder(new HttpHost[]{ host }).build();

        String responseString = "";
        while (!responseString.contains("\"count\":3")) {
            Thread.sleep(200);
            Request request = new Request("GET", "/_count");
            Response response = client.performRequest(request);
            responseString = EntityUtils.toString(response.getEntity());
        }
    }

}
