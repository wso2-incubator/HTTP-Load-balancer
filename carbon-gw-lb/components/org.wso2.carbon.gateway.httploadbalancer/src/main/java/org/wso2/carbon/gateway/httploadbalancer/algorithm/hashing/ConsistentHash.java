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

import org.wso2.carbon.gateway.httploadbalancer.algorithm.hashing.hashcodegenerators.HashFunction;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Implementation of Consistent Hash for ClientIPHashing based Load balancing.
 * <p>
 * This implementation is done in reference with the following articles:
 * 1) http://www.tom-e-white.com/2007/11/consistent-hashing.html
 * 2) http://www.mikeperham.com/2009/01/14/consistent-hashing-in-memcache-client/
 * <p>
 * By ConsistentHashing, changes to endpoints can be made with minimal effect on hash results.
 * (i.e) only clients that were mapped to that endpoint are to be re-shuffled, while removing
 * an unhealthy endpoint.
 * Also, while adding a new endpoint, very few clients with a small range are alone affected.
 * The above mentioned articles have clear explanation and logic behind this.
 * <p>
 * Regarding HashFunctions, MD5 is suggested. Simple hashcode implementation is also
 * provided.  But, MD5 has better distribution than hashcode. Custom implementation
 * can also be done.
 * <p>
 * Regarding the replicas mentioned in article (1),
 * "LoadBalancerConstants.REPLICATION_FACTOR" has to be used.
 */
public class ConsistentHash implements Hash {

    private final HashFunction hashFunction;
    private final SortedMap<String, String> circle = new TreeMap<>();


    /**
     * @param hashFunction Any custom implementation of hashFunction.
     *                     <p>
     *                     You can also implement your own by implementing HashFunction interface.
     * @param endpoints    List of OutboundEndpoints of form (hostname:port)
     */
    public ConsistentHash(HashFunction hashFunction, List<String> endpoints) {

        this.hashFunction = hashFunction;
        endpoints.forEach(this::addEndpoint);
    }


    /**
     * @param endpoint add an endpoint of form (hostname:port).
     */
    @Override
    public void addEndpoint(String endpoint) {

        for (int i = 0; i < LoadBalancerConstants.REPLICATION_FACTOR; i++) {
            circle.put(hashFunction.hash(endpoint + i), endpoint);
        }
    }

    /**
     * @param endpoints List of Endpoints of form (hostname:port) to be added.
     */
    @Override
    public void addEndpoints(List<String> endpoints) {
        endpoints.forEach(this::addEndpoint);
    }


    /**
     * @param endpoint remove an endpoint of form (hostname:port).
     */
    @Override
    public void removeEndpoint(String endpoint) {


        for (int i = 0; i < LoadBalancerConstants.REPLICATION_FACTOR; i++) {
            circle.remove(hashFunction.hash(endpoint + i));
        }


    }

    /**
     * @param endpoints List of Endpoints of form (hostname:port) to be removed.
     */
    @Override
    public void removeAllEndpoints(List<String> endpoints) {

        endpoints.forEach(this::removeEndpoint);
    }


    /**
     * @param ipAddress Client IP Address.
     * @return Chosen Endpoint of form (hostname:port) based on HashFunction implementation.
     */
    @Override
    public String get(String ipAddress) {
        if (circle.isEmpty()) {
            return null;
        }

        String hash = hashFunction.hash(ipAddress);

        if (!circle.containsKey(hash)) {

            SortedMap<String, String> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }

        return circle.get(hash);
    }
}
