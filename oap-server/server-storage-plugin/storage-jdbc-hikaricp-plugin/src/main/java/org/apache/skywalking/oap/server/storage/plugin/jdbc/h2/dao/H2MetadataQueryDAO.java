/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.query.entity.Attribute;
import org.apache.skywalking.oap.server.core.query.entity.Database;
import org.apache.skywalking.oap.server.core.query.entity.Endpoint;
import org.apache.skywalking.oap.server.core.query.entity.LanguageTrans;
import org.apache.skywalking.oap.server.core.query.entity.Service;
import org.apache.skywalking.oap.server.core.query.entity.ServiceInstance;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.HOST_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.IPV4S;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.LANGUAGE;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.OS_NAME;
import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.PROCESS_NO;

public class H2MetadataQueryDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();

    private JDBCHikariCPClient h2Client;
    private int metadataQueryMaxSize;

    public H2MetadataQueryDAO(JDBCHikariCPClient h2Client, int metadataQueryMaxSize) {
        this.h2Client = h2Client;
        this.metadataQueryMaxSize = metadataQueryMaxSize;
    }

    @Override
    public int numOfService(long startTimestamp, long endTimestamp) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select count(*) num from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        setTimeRangeCondition(sql, condition, startTimestamp, endTimestamp);
        sql.append(" and ").append(ServiceInventory.IS_ADDRESS).append("=" + BooleanUtils.FALSE);
        sql.append(" and ").append(ServiceInventory.NODE_TYPE).append("=" + NodeType.Normal.value());

        return getNum(sql, condition);
    }

    private Integer getNum(StringBuilder sql, List<Object> condition) throws IOException {
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                if (resultSet.next()) {
                    return resultSet.getInt("num");
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return 0;
    }

    @Override
    public int numOfEndpoint() throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select count(*) num from ").append(EndpointTraffic.INDEX_NAME).append(" where ");
        sql.append(EndpointTraffic.DETECT_POINT).append("=").append(DetectPoint.SERVER.value());

        return getNum(sql, condition);
    }

    @Override
    public int numOfConjectural(int nodeTypeValue) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select count(*) num from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        sql.append(ServiceInventory.NODE_TYPE).append("=?");
        condition.add(nodeTypeValue);

        return getNum(sql, condition);
    }

    @Override
    public List<Service> getAllServices(long startTimestamp, long endTimestamp) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        setTimeRangeCondition(sql, condition, startTimestamp, endTimestamp);
        sql.append(" and ").append(ServiceInventory.IS_ADDRESS).append("=?");
        condition.add(BooleanUtils.FALSE);
        sql.append(" and ").append(ServiceInventory.NODE_TYPE).append("=?");
        condition.add(NodeType.Normal.value());
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildServices(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Service> getAllBrowserServices(long startTimestamp, long endTimestamp) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        setTimeRangeCondition(sql, condition, startTimestamp, endTimestamp);
        sql.append(" and ").append(ServiceInventory.IS_ADDRESS).append("=?");
        condition.add(BooleanUtils.FALSE);
        sql.append(" and ").append(ServiceInventory.NODE_TYPE).append("=?");
        condition.add(NodeType.Browser.value());
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildServices(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        sql.append(ServiceInventory.NODE_TYPE).append("=? limit ").append(metadataQueryMaxSize);
        condition.add(NodeType.Database.value());

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                List<Database> databases = new ArrayList<>();
                while (resultSet.next()) {
                    Database database = new Database();
                    database.setId(resultSet.getInt(ServiceInventory.SEQUENCE));
                    database.setName(resultSet.getString(ServiceInventory.NAME));
                    String propertiesString = resultSet.getString(ServiceInstanceInventory.PROPERTIES);
                    if (!Strings.isNullOrEmpty(propertiesString)) {
                        JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                        if (properties.has(ServiceInventory.PropertyUtil.DATABASE)) {
                            database.setType(properties.get(ServiceInventory.PropertyUtil.DATABASE).getAsString());
                        } else {
                            database.setType("UNKNOWN");
                        }
                    }
                    databases.add(database);
                }
                return databases;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Service> searchServices(long startTimestamp, long endTimestamp, String keyword) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        setTimeRangeCondition(sql, condition, startTimestamp, endTimestamp);
        sql.append(" and ").append(ServiceInventory.IS_ADDRESS).append("=?");
        condition.add(BooleanUtils.FALSE);
        sql.append(" and ").append(ServiceInventory.NODE_TYPE).append("=?");
        condition.add(NodeType.Normal.value());
        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(ServiceInventory.NAME).append(" like \"%").append(keyword).append("%\"");
        }
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildServices(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceInventory.INDEX_NAME).append(" where ");
        sql.append(ServiceInventory.IS_ADDRESS).append("=?");
        condition.add(BooleanUtils.FALSE);
        sql.append(" and ").append(ServiceInventory.NAME).append(" = ?");
        condition.add(serviceCode);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    Service service = new Service();
                    service.setId(resultSet.getInt(ServiceInventory.SEQUENCE));
                    service.setName(resultSet.getString(ServiceInventory.NAME));
                    return service;
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return null;
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, int serviceId, int limit) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(EndpointTraffic.INDEX_NAME).append(" where ");
        sql.append(EndpointTraffic.SERVICE_ID).append("=?");
        condition.add(serviceId);
        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(EndpointTraffic.NAME).append(" like '%").append(keyword).append("%' ");
        }
        sql.append(" and ").append(EndpointTraffic.DETECT_POINT).append(" = ?");
        condition.add(DetectPoint.SERVER.value());
        /**
         * Query the dataset by a larger limit condition and distinct in the memory,
         * in order to avoid the storage level distinct.
         * This is a match query only, don't need 100% accurate.
         */
        sql.append(" limit ").append(limit * 7);

        List<Endpoint> endpoints = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    EndpointTraffic endpointTraffic = new EndpointTraffic();
                    endpointTraffic.setServiceId(resultSet.getInt(EndpointTraffic.SERVICE_ID));
                    endpointTraffic.setName(resultSet.getString(EndpointTraffic.NAME));
                    endpointTraffic.setDetectPoint(resultSet.getInt(EndpointTraffic.DETECT_POINT));
                    endpointTraffic.setTimeBucket(resultSet.getLong(EndpointTraffic.TIME_BUCKET));

                    Endpoint endpoint = new Endpoint();
                    endpoint.setId(EndpointTraffic.buildId(endpointTraffic));
                    endpoint.setName(endpointTraffic.getName());
                    endpoints.add(endpoint);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return endpoints;
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp,
                                                     String serviceId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceInstanceInventory.INDEX_NAME).append(" where ");
        setTimeRangeCondition(sql, condition, startTimestamp, endTimestamp);
        sql.append(" and ").append(ServiceInstanceInventory.SERVICE_ID).append("=?");
        condition.add(serviceId);

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    ServiceInstance serviceInstance = new ServiceInstance();
                    serviceInstance.setId(resultSet.getString(ServiceInstanceInventory.SEQUENCE));
                    serviceInstance.setName(resultSet.getString(ServiceInstanceInventory.NAME));
                    serviceInstance.setInstanceUUID(resultSet.getString(ServiceInstanceInventory.INSTANCE_UUID));

                    String propertiesString = resultSet.getString(ServiceInstanceInventory.PROPERTIES);
                    if (!Strings.isNullOrEmpty(propertiesString)) {
                        JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                            String key = property.getKey();
                            String value = property.getValue().getAsString();
                            if (key.equals(LANGUAGE)) {
                                serviceInstance.setLanguage(LanguageTrans.INSTANCE.value(value));
                            } else if (key.equals(OS_NAME)) {
                                serviceInstance.getAttributes().add(new Attribute(OS_NAME, value));
                            } else if (key.equals(HOST_NAME)) {
                                serviceInstance.getAttributes().add(new Attribute(HOST_NAME, value));
                            } else if (key.equals(PROCESS_NO)) {
                                serviceInstance.getAttributes().add(new Attribute(PROCESS_NO, value));
                            } else if (key.equals(IPV4S)) {
                                List<String> ipv4s = ServiceInstanceInventory.PropertyUtil.ipv4sDeserialize(
                                    properties.get(IPV4S)
                                              .getAsString());
                                for (String ipv4 : ipv4s) {
                                    serviceInstance.getAttributes()
                                                   .add(new Attribute(ServiceInstanceInventory.PropertyUtil.IPV4S,
                                                                      ipv4
                                                   ));
                                }
                            } else {
                                serviceInstance.getAttributes().add(new Attribute(key, value));
                            }
                        }
                    }

                    serviceInstances.add(serviceInstance);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return serviceInstances;
    }

    private void setTimeRangeCondition(StringBuilder sql, List<Object> conditions, long startTimestamp,
                                       long endTimestamp) {
        sql.append(" ( (")
           .append(RegisterSource.HEARTBEAT_TIME)
           .append(" >= ? and ")
           .append(RegisterSource.REGISTER_TIME)
           .append(" <= ? )");
        conditions.add(endTimestamp);
        conditions.add(endTimestamp);
        sql.append(" or (")
           .append(RegisterSource.REGISTER_TIME)
           .append(" <= ? and ")
           .append(RegisterSource.HEARTBEAT_TIME)
           .append(" >= ? ) ) ");
        conditions.add(endTimestamp);
        conditions.add(startTimestamp);
    }

    private List<Service> buildServices(ResultSet resultSet) throws SQLException {
        List<Service> services = new ArrayList<>();
        while (resultSet.next()) {
            Service service = new Service();
            service.setId(resultSet.getInt(ServiceInventory.SEQUENCE));
            service.setName(resultSet.getString(ServiceInventory.NAME));
            services.add(service);
        }

        return services;
    }
}
