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

package org.wso2.carbon.gateway.httploadbalancer.context;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.LoadBalancingAlgorithm;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.StrictClientIPHashing;
import org.wso2.carbon.gateway.httploadbalancer.callback.LoadBalancerMediatorCallBack;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;


import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds runtime LB Configuration context.
 * <p>
 * Context object will be passed to mediators so that they can use config whenever necessary.
 */
public class LoadBalancerConfigContext {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerConfigContext.class);

    private String algorithmName;
    private LoadBalancingAlgorithm loadBalancingAlgorithm;

    private String persistence;

    //There are the values as specified in config.
    private String healthCheck;
    private int reqTimeout;
    private int unHealthyRetries;
    private int healthyRetries;
    private int healthycheckInterval;


    private Map<String, LBOutboundEndpoint> lbOutboundEndpoints;

    /**
     * Used to identify corresponding BackEnd Endpoint Key for a given cookie.
     * <p>
     * This map will be used when request comes from Client to LB
     * and based on cookie, BE endpoint will be chosen.
     * Each cookie value will point to a BE endpoint. Eg: EP1,EP2 etc.,
     */
    private Map<String, String> cookieToEPKeyMap;

    /**
     * Used to identify corresponding Cookie for a given BackEnd Endpoint.
     * <p>
     * This map will be used once response arrives from BE and
     * to use appropriate cookie for the endpoint.
     * <p>
     * NOTE: EndpointName will be of the form (hostName:port)
     */
    private Map<String, String> endpointToCookieMap;

    /**
     * A list that holds all unHealthyLBOutboundEndpoints.
     */
    private final Queue<LBOutboundEndpoint> unHealthyLBEPQueue = new ConcurrentLinkedQueue<>();

    /**
     * A LoadBalancerMediatorCallBack Pool for storing active callbacks.
     * <p>
     * NOTE: This pool stores only LoadBalancerMediatorCallBack objects.
     * <p>
     * TimeoutHandler will use this pool to check for Timeout of requests.
     */
    private final Map<LoadBalancerMediatorCallBack, String> callBackPool = new ConcurrentHashMap<>(10000);

    /**
     * This map will be used in case of Weighted Algorithms.
     */
    private Map<String, Integer> weightsMap;

    /**
     * This will be used if persistence is chosen as CLIENT_IP_HASHING.
     */
    private StrictClientIPHashing strictClientIPHashing;


    public String getAlgorithmName() {
        return algorithmName;
    }

    public void setAlgorithmName(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public LoadBalancingAlgorithm getLoadBalancingAlgorithm() {
        return loadBalancingAlgorithm;
    }

    public void setLoadBalancingAlgorithm(LoadBalancingAlgorithm loadBalancingAlgorithm) {
        this.loadBalancingAlgorithm = loadBalancingAlgorithm;
    }

    public String getPersistence() {
        return persistence;
    }

    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public String getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(String healthCheck) {
        this.healthCheck = healthCheck;
    }

    public int getReqTimeout() {
        return reqTimeout;
    }

    public void setReqTimeout(int reqTimeout) {
        this.reqTimeout = reqTimeout;
    }

    public int getUnHealthyRetries() {
        return unHealthyRetries;
    }

    public void setUnHealthyRetries(int unHealthyRetries) {
        this.unHealthyRetries = unHealthyRetries;
    }

    public int getHealthyRetries() {
        return healthyRetries;
    }

    public void setHealthyRetries(int healthyRetries) {
        this.healthyRetries = healthyRetries;
    }

    public int getHealthycheckInterval() {
        return healthycheckInterval;
    }

    public void setHealthycheckInterval(int healthycheckInterval) {
        this.healthycheckInterval = healthycheckInterval;
    }


    /**
     * This method MUST be called before accessing cookie related maps.
     */
    public void initCookieMaps() {

        cookieToEPKeyMap = new ConcurrentHashMap<>();
        endpointToCookieMap = new ConcurrentHashMap<>();
    }

    /**
     * This method MUST be called before accessing weightsMap.
     */
    public void initWeightsMap() {
        this.weightsMap = new ConcurrentHashMap<>();
    }

    public Map<String, Integer> getWeightsMap() {
        return weightsMap;
    }

    /**
     * @param endpointName OutboundEndpoint's name
     * @param weight       OutboundEndpoint's weight.
     */
    public void addToWeightsMap(String endpointName, Integer weight) {
        this.weightsMap.putIfAbsent(endpointName, weight);
    }

    public void initStrictClientIPHashing(List<LBOutboundEndpoint> lbOutboundEndpoints) {
        strictClientIPHashing = new StrictClientIPHashing(lbOutboundEndpoints);
    }

    /**
     * @param cookieName
     * @param outboundEPKey OutboundEndpoint name. Maps cookie to an outbound EP.
     */
    public void addToCookieToOutboundEPKeyMap(String cookieName, String outboundEPKey) {

        cookieToEPKeyMap.putIfAbsent(cookieName, outboundEPKey);

    }

    /**
     * @param cookieName
     * @return OutboundEndpointKey.
     * Returns an OutboundEndpointKey for a given cookieName.
     */
    public String getOutboundEPKeyFromCookie(String cookieName) {

        return cookieToEPKeyMap.get(cookieName);
    }


    /**
     * @param endpoint   of form (host:port)
     * @param cookieName Maps OutboundEP to a cookieName.
     */
    public void addToOutboundEPTOCookieMap(String endpoint, String cookieName) {

        endpointToCookieMap.putIfAbsent(endpoint, cookieName);

    }

    /**
     * @param endpoint
     * @return CookieName.
     * Returns a cookie for a given OutboundEP.
     */
    public String getCookieFromOutboundEP(String endpoint) {

        return endpointToCookieMap.get(endpoint);
    }

    public Map<String, String> getCookieToOutboundEPKeyMap() {
        return cookieToEPKeyMap;
    }


    public Map<String, LBOutboundEndpoint> getLbOutboundEndpoints() {
        return lbOutboundEndpoints;
    }

    public void setLbOutboundEndpoints(Map<String, LBOutboundEndpoint> lbOutboundEndpoints) {
        this.lbOutboundEndpoints = lbOutboundEndpoints;
    }

    /**
     * @param name LBOutboundEndpoint's name.
     * @return Corresponding LBOutboundEndpoint object.
     */
    public LBOutboundEndpoint getLBOutboundEndpoint(String name) {

        return this.lbOutboundEndpoints.get(name);
    }

    public int getUnHealthyEPQueueSize() {

        return this.unHealthyLBEPQueue.size();
    }

    public Queue<LBOutboundEndpoint> getUnHealthyLBEPQueue() {
        return unHealthyLBEPQueue;
    }


    public Map<LoadBalancerMediatorCallBack, String> getCallBackPool() {

        return callBackPool;
    }


    /**
     * @param callback LoadBalancerMediatorCallBack.
     *                 <p>
     *                 NOTE: This operation is always thread safe.
     */
    public void addToCallBackPool(LoadBalancerMediatorCallBack callback) {


        this.callBackPool.putIfAbsent(callback, "");


    }


    /**
     * @param callback LoadBalancerMediatorCallBack.
     * @return present or not.
     * <p>
     * NOTE: This operation is always thread safe.
     */
    public boolean isInCallBackPool(LoadBalancerMediatorCallBack callback) {


        if (this.callBackPool.containsKey(callback)) {

            return true;
        } else {

            return false;
        }
    }


    /**
     * @param callback LoadBalancerMediatorCallBack.
     *                 <p>
     *                 NOTE: This operation is always thread safe.
     */
    public void removeFromCallBackPool(LoadBalancerMediatorCallBack callback) {


        this.callBackPool.remove(callback);


    }

    public StrictClientIPHashing getStrictClientIPHashing() {
        return strictClientIPHashing;
    }
}



