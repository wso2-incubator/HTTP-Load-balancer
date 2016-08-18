/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.gateway.httploadbalancer.algorithm.hashing;

import java.util.List;

/**
 * Implementation of Modulo Hash for ClientIPHashing based Load balancing.
 * <p>
 * This is the most basic method of hashing.  If the performance from this method
 * is sufficient, one can go with this.
 * <p>
 * Changes to endpoints will have more effect on hash results.
 */
public class ModuloHash implements Hash {

    private List<String> endpoints;

    /**
     * @param endpoints List of OutboundEndpoints of form (hostname:port).
     */
    public ModuloHash(List<String> endpoints) {

        this.endpoints = endpoints;
    }

    /**
     * @param endpoint add an endpoint of form (hostname:port).
     */
    @Override
    public void addEndpoint(String endpoint) {
        endpoints.add(endpoint);

    }

    /**
     *
     * @param endpoints List of Endpoints of form (hostname:port) to be added.
     */
    @Override
    public void addEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * @param endpoint remove an endpoint of form (hostname:port).
     */
    @Override
    public void removeEndpoint(String endpoint) {

        endpoints.remove(endpoint);
    }

    /**
     *
     * @param endpoints List of Endpoint of form (hostname:port) to be removed.
     */
    @Override
    public void removeAllEndpoints(List<String> endpoints) {

        endpoints.forEach(this::removeEndpoint);
    }

    /**
     * @param ipAddress Client IP Address.
     * @return Chosen Endpoint of form (hostname:port) based on Modulo Hash implementation.
     */
    @Override
    public String get(String ipAddress) {

        if (endpoints.size() == 0) {
            return null;
        }

        return endpoints.get(Math.abs(ipAddress.hashCode() % endpoints.size()));
    }
}
