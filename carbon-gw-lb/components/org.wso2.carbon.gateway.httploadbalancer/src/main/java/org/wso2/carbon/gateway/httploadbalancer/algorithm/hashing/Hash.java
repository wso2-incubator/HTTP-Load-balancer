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
 * Interface for IPHashing.
 * All types of hashing methods must implement this interface.
 */
public interface Hash {

    /**
     * @param endpoint add an endpoint of form (hostname:port).
     */
    void addEndpoint(String endpoint);

    /**
     *
     * @param endpoints List of Endpoints of form (hostname:port) to be added.
     */
    void addEndpoints(List<String> endpoints);

    /**
     * @param endpoint remove an endpoint of form (hostname:port).
     */
    void removeEndpoint(String endpoint);

    /**
     *
     * @param endpoints List of Endpoint of form (hostname:port) to be removed.
     */
    void removeAllEndpoints(List<String> endpoints);

    /**
     * @param ipAddress Client IP Address.
     * @return Chosen endpoint of form (hostname:port) based on hashing method.
     */
    String get(String ipAddress);
}
