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

package org.wso2.carbon.gateway.httploadbalancer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.config.Parameter;
import org.wso2.carbon.gateway.core.config.ParameterHolder;
import org.wso2.carbon.gateway.core.config.dsl.external.WUMLConfigurationBuilder;
import org.wso2.carbon.gateway.core.outbound.OutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.hashing.hashcodegenerators.HashFunction;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.hashing.hashcodegenerators.MD5;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.mediator.LoadBalancerMediatorBuilder;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Class responsible for loading LB config from WUMLBaseListenerImpl.java to LoadBalancerConfigContext.
 * <p>
 * All validations and conversions are done here.
 * <p>
 * This holds configurations from .iflow config files.
 */
public class LoadBalancerConfigHolder {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerConfigHolder.class);

    private ParameterHolder loadbalancerConfigs;

    private List<String> outboundEndpoints = null;

    private WUMLConfigurationBuilder.IntegrationFlow integrationFlow;

    private final LoadBalancerConfigContext context;

    /**
     * Default Constructor.
     */
    public LoadBalancerConfigHolder() {

        this.loadbalancerConfigs = new ParameterHolder();
        this.context = new LoadBalancerConfigContext();

    }

    public void initializeOutboundEPList() {
        outboundEndpoints = new ArrayList<>();
    }

    public boolean containsEndpoint(String endpoint) {
        return outboundEndpoints.contains(endpoint);
    }

    public void addToOutboundEPList(String endpoint) {
        this.outboundEndpoints.add(endpoint);
    }


    /**
     * This method will be used to free loadbalancerConfigs.
     * <p>
     * Once all configs are loaded in LBConfigContext, this is redundant and unnecessary.
     */
    private void releaseParamHolder() {
        this.loadbalancerConfigs = null;
    }

    public ParameterHolder getLoadbalancerConfigs() {
        return loadbalancerConfigs;
    }

    public void setLoadbalancerConfigs(ParameterHolder loadbalancerConfigs) {
        this.loadbalancerConfigs = loadbalancerConfigs;
    }

    public WUMLConfigurationBuilder.IntegrationFlow getIntegrationFlow() {
        return integrationFlow;
    }

    public void setIntegrationFlow(WUMLConfigurationBuilder.IntegrationFlow integrationFlow) {
        this.integrationFlow = integrationFlow;
    }

    /**
     * @param param Parameter to be added to Config,
     */
    public void addToConfig(Parameter param) {
        loadbalancerConfigs.addParameter(param);
        //Parameter addedParam = this.getFromConfig(param.getName());
        //log.info(addedParam.getName() + " : " + addedParam.getValue());
    }

    /**
     * @param paramName parameterName to be removed from config.
     */
    public void removeFromConfig(String paramName) {

        loadbalancerConfigs.removeParameter(paramName);
    }

    /**
     * @return returns all configs.
     */
    public ParameterHolder getAllConfigs() {

        return loadbalancerConfigs;
    }

    /**
     * @param paramName parameterName
     * @return Parameter object corresponding to that name.
     */
    private Parameter getFromConfig(String paramName) {

        return loadbalancerConfigs.getParameter(paramName);
    }

    /**
     * @param paramName parameterName.
     * @return Whether specified parameter is present or not.
     */
    private boolean contains(String paramName) {

        return (loadbalancerConfigs.getParameter(paramName) != null);
    }


    /**
     * @param integrationFlow integrationFlow object.
     *                        <p>
     *                        It performs validation and also initializes LoadBalancerConfigHolder.
     */
    public void configureLoadBalancerMediator(WUMLConfigurationBuilder.IntegrationFlow integrationFlow) {


        this.integrationFlow = integrationFlow;

        Set<Map.Entry<String, OutboundEndpoint>> entrySet = integrationFlow.
                getGWConfigHolder().getOutboundEndpoints().entrySet();

        /**
         * Since all OutboundEndpoint Objects MUST be accessed via LBOutboundEndpoint, we are doing this.
         * Here we are creating LBOutboundEndpoint Map similar to OutboundEndpoint Map.
         * See LBOutboundEndpoint class to understand it better.
         */
        Map<String, LBOutboundEndpoint> lbOutboundEndpointMap = new ConcurrentHashMap<>();

        for (Map.Entry entry : entrySet) {

            lbOutboundEndpointMap.put(entry.getKey().toString(),
                    new LBOutboundEndpoint((OutboundEndpoint) entry.getValue()));
        }

        context.setLbOutboundEndpoints(lbOutboundEndpointMap);

        validateConfig();

        LoadBalancerMediatorBuilder.configure(this.integrationFlow.getGWConfigHolder(), context);

        releaseParamHolder();
    }

    public void configureGroupLoadbalancerMediator(WUMLConfigurationBuilder.IntegrationFlow integrationFlow,
                                                   String groupPath) {
        this.integrationFlow = integrationFlow;

        Set<Map.Entry<String, OutboundEndpoint>> entrySet = integrationFlow.
                getGWConfigHolder().getOutboundEndpoints().entrySet();

        Map<String, LBOutboundEndpoint> lbOutboundEndpointMap = new ConcurrentHashMap<>();

        for (Map.Entry entry : entrySet) {

            //Adding only those endpoints that are specified within group context.
            if (outboundEndpoints.contains(entry.getKey().toString())) {
                lbOutboundEndpointMap.put(entry.getKey().toString(),
                        new LBOutboundEndpoint((OutboundEndpoint) entry.getValue()));
            }
        }

        context.setLbOutboundEndpoints(lbOutboundEndpointMap);

        validateConfig();

        LoadBalancerMediatorBuilder.configureForGroup(this.integrationFlow.getGWConfigHolder(), context, groupPath);

        releaseParamHolder();

    }

    /**
     * @param timeOut
     * @return boolean
     * <p>
     * This method is used to check whether timeOut is within limit or not.
     */
    private boolean isWithInLimit(int timeOut) {

        if (timeOut <= LoadBalancerConstants.MAX_TIMEOUT_VAL) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param lbOutboundEndpoints <p>
     *                            Populates cookie handling maps.
     */
    private void populateCookieMaps(Map<String, LBOutboundEndpoint> lbOutboundEndpoints) {

        //MUST: Initializing cookie maps.
        context.initCookieMaps();

        // For the purpose of changing hashCode generating algorithm easily in future, we are doing this.
        HashFunction hashFunction = new MD5();

        Set<Map.Entry<String, LBOutboundEndpoint>> entrySet = lbOutboundEndpoints.entrySet();
        for (Map.Entry entry : entrySet) {

            String hostAndPort = CommonUtil.getHostAndPort(
                    ((LBOutboundEndpoint) entry.getValue()).getOutboundEndpoint().getUri());


            String hashCode = hashFunction.hash(hostAndPort);
            if (!context.getCookieToOutboundEPKeyMap().containsKey(hashCode)) {

                context.addToCookieToOutboundEPKeyMap(hashCode, entry.getKey().toString());
                context.addToOutboundEPTOCookieMap(hostAndPort, hashCode);

            } else {
                log.error("Same hash code exists in map. Kindly contact administrator to resolve this issue.." +
                        " Persistence will not be maintained for the endpoint : " + entry.getKey().toString());
            }

        }

    }

    /**
     * @param lbOutboundEndpoints Populates weight handling maps.
     *                            <p>
     *                            This method also does validation.
     *                            <p>
     *                            If weight is not defined for and endpoint, default value of 1 will be used.
     */
    private void populateWeightsMap(Map<String, LBOutboundEndpoint> lbOutboundEndpoints) {

        // MUST: Initializing WeightMaps.
        context.initWeightsMap();

        Set<Map.Entry<String, LBOutboundEndpoint>> entrySet = lbOutboundEndpoints.entrySet();

        for (Map.Entry entry : entrySet) {
            String key = entry.getKey().toString();

            if (this.contains(key)) {
                try {
                    context.addToWeightsMap(key, Integer.parseInt(this.getFromConfig(key).getValue()));
                } catch (Exception ex) {
                    log.error(ex.toString());
                    log.error("Exception occurred while adding weight to OutboundEndpoint : "
                            + key + " Default weight of 1 will be used..");
                }

            } else {
                log.warn("No weight specified for OutboundEndpoint : " + key
                        + " Default weight of 1 will be used..");
                context.addToWeightsMap(key, 1);
            }
        }


    }

    /**
     * Algorithm related validations.
     */
    private void validateAlgorithm() {

        String algorithmName = this.getFromConfig(LoadBalancerConstants.ALGORITHM_NAME).getValue();
        if (// For Simple algorithms.
                algorithmName.equals(LoadBalancerConstants.ROUND_ROBIN) ||

                        algorithmName.equals(LoadBalancerConstants.STRICT_IP_HASHING) ||

                        algorithmName.equals(LoadBalancerConstants.LEAST_RESPONSE_TIME) ||

                        algorithmName.equals(LoadBalancerConstants.RANDOM)
                ) {

            context.setAlgorithmName(algorithmName);

            // For weighted algorithms.
        } else if (
                algorithmName.equals(LoadBalancerConstants.WEIGHTED_ROUND_ROBIN) ||
                        algorithmName.equals(LoadBalancerConstants.WEIGHTED_RANDOM)

                ) {

            context.setAlgorithmName(algorithmName);
            populateWeightsMap(context.getLbOutboundEndpoints());

        } else {
            log.error("Currently this algorithm type is not supported...");

        }

        log.info("Algorithm : " + context.getAlgorithmName());

    }

    /**
     * Session persistence related validations.
     */

    private void validatePersistence() {

        String persistenceType = this.getFromConfig(LoadBalancerConstants.PERSISTENCE_TYPE).getValue();

        // Weighted algorithms cannot have CLIENT_IP_HASHING as persistence policy.
        if (context.getAlgorithmName().equals(LoadBalancerConstants.WEIGHTED_ROUND_ROBIN)) {

            if (!persistenceType.equals(LoadBalancerConstants.CLIENT_IP_HASHING)) {
                context.setPersistence(persistenceType);
            } else {
                context.setPersistence(LoadBalancerConstants.NO_PERSISTENCE);
                log.error(context.getAlgorithmName() + " cannot only have " +
                        LoadBalancerConstants.CLIENT_IP_HASHING +
                        " as persistence policy. It has been changed to " + LoadBalancerConstants.NO_PERSISTENCE);
            }
            // For Algorithm STRICT_IP_HASHING, persistence policy MUST be NO_PERSISTENCE.
        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.STRICT_IP_HASHING)) {

            if (persistenceType.equals(LoadBalancerConstants.NO_PERSISTENCE)) {
                context.setPersistence(persistenceType);
            } else {
                context.setPersistence(LoadBalancerConstants.NO_PERSISTENCE);
                log.error(LoadBalancerConstants.STRICT_IP_HASHING + " can only have " +
                        LoadBalancerConstants.NO_PERSISTENCE + " as persistence policy.." +
                        " It has been changed..");
            }

        } else {


            if (persistenceType.equals(LoadBalancerConstants.NO_PERSISTENCE)) {

                context.setPersistence(persistenceType);

            } else if (persistenceType.equals(LoadBalancerConstants.CLIENT_IP_HASHING)) {

                context.setPersistence(persistenceType);
                context.initStrictClientIPHashing
                        (CommonUtil.getLBOutboundEndpointsList(context.getLbOutboundEndpoints()));

            } else if (persistenceType.equals(LoadBalancerConstants.APPLICATION_COOKIE) ||
                    persistenceType.equals(LoadBalancerConstants.LB_COOKIE)) {

                context.setPersistence(persistenceType);
                populateCookieMaps(context.getLbOutboundEndpoints());
            }

        }
        log.info("Persistence : " + context.getPersistence());
    }


    /**
     * HealthCheck related validations.
     */

    private void validateHealthCheck() {

        String healthCheckType = this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_TYPE).getValue();

        //For ACTIVE or PASSIVE.
        if (healthCheckType.equals(LoadBalancerConstants.PASSIVE_HEALTH_CHECK) ||
                healthCheckType.equals(LoadBalancerConstants.ACTIVE_HEALTH_CHECK)) {

            context.setHealthCheck(healthCheckType);

            if (this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_REQUEST_TIMEOUT) != null) {

                String hcReqTimeOut = this.getFromConfig
                        (LoadBalancerConstants.HEALTH_CHECK_REQUEST_TIMEOUT).getValue();

                int timeout = CommonUtil.getTimeInMilliSeconds(hcReqTimeOut);

                if (isWithInLimit(timeout)) {
                    context.setReqTimeout(timeout);
                    log.info("Request TIME_OUT : " + context.getReqTimeout());
                } else {
                    //TODO: Is this okay..?
                    context.setReqTimeout(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);
                    log.error("Exceeded TIMEOUT LIMIT. Loading DEFAULT value for " +
                            "Request TIME_OUT : " + context.getReqTimeout());
                }


            } else {
                //TODO: Is this okay..?
                context.setReqTimeout(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);
                log.error("LB_REQUEST_TIMEOUT NOT SPECIFIED. Loading DEFAULT value for " +
                        "Request TIME_OUT : " + context.getReqTimeout());
            }

            if (this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_UNHEALTHY_RETRIES) != null) {

                String hcUHRetries = this.getFromConfig
                        (LoadBalancerConstants.HEALTH_CHECK_UNHEALTHY_RETRIES).getValue();


                int uhRetries = CommonUtil.getRetriesCount(hcUHRetries);
                context.setUnHealthyRetries(uhRetries);
                log.info(LoadBalancerConstants.HEALTH_CHECK_UNHEALTHY_RETRIES + " : " + context.getUnHealthyRetries());

            } else {
                //TODO: Is this okay..?
                context.setUnHealthyRetries(LoadBalancerConstants.DEFAULT_RETRIES);
                log.error("UNHEALTHY_RETRIES_VALUE NOT SPECIFIED.. Loading default value." +
                        LoadBalancerConstants.HEALTH_CHECK_UNHEALTHY_RETRIES + " : " +
                        context.getUnHealthyRetries());

            }

            if (this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_HEALTHY_RETRIES) != null) {

                String hcHRetries = this.getFromConfig
                        (LoadBalancerConstants.HEALTH_CHECK_HEALTHY_RETRIES).getValue();

                int hRetries = CommonUtil.getRetriesCount(hcHRetries);
                context.setHealthyRetries(hRetries);
                log.info(LoadBalancerConstants.HEALTH_CHECK_HEALTHY_RETRIES + " : " + context.getHealthyRetries());

            } else {
                //TODO: Is this okay..?
                context.setHealthyRetries(LoadBalancerConstants.DEFAULT_RETRIES);
                log.error("HEALTHY_RETRIES_VALUE NOT SPECIFIED.. Loading default value." +
                        LoadBalancerConstants.HEALTH_CHECK_HEALTHY_RETRIES + " : " + context.getHealthyRetries());
            }

            if (this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_HEALTHY_CHECK_INTERVAL) != null) {

                String hcHCInterval = this.getFromConfig
                        (LoadBalancerConstants.HEALTH_CHECK_HEALTHY_CHECK_INTERVAL).getValue();

                int interval = CommonUtil.getTimeInMilliSeconds(hcHCInterval);

                if (isWithInLimit(interval)) {

                    context.setHealthycheckInterval(interval);
                    log.info(LoadBalancerConstants.HEALTH_CHECK_HEALTHY_CHECK_INTERVAL + " : " +
                            context.getHealthycheckInterval());

                } else {
                    //TODO: Is this okay..?

                    context.setHealthycheckInterval(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);

                    log.error("Exceeded HEALTHY_CHECK_TIMEOUT LIMIT. Loading DEFAULT value for " +
                            LoadBalancerConstants.HEALTH_CHECK_HEALTHY_CHECK_INTERVAL + " : " +
                            context.getHealthycheckInterval());
                }


            } else {
                //TODO: Is this okay..?

                context.setHealthycheckInterval(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);

                log.error("HEALTHY_CHECK_TIMEOUT LIMIT NOT SPECIFIED. Loading DEFAULT value for " +
                        LoadBalancerConstants.HEALTH_CHECK_HEALTHY_CHECK_INTERVAL + " : " +
                        context.getHealthycheckInterval());

            }


            //For DEFAULT_HEALTH_CHECK
        } else if (healthCheckType.equals(LoadBalancerConstants.DEFAULT_HEALTH_CHECK)) {

            //Default health check is PASSIVE
            context.setHealthCheck(this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_TYPE).getValue());

            context.setUnHealthyRetries(LoadBalancerConstants.DEFAULT_RETRIES);
            context.setHealthyRetries(LoadBalancerConstants.DEFAULT_RETRIES);
            context.setReqTimeout(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);
            context.setHealthycheckInterval(LoadBalancerConstants.DEFAULT_HEALTHY_CHECK_INTERVAL);


            //FOR NO_HEALTH_CHECK
        } else if (healthCheckType.equals(LoadBalancerConstants.NO_HEALTH_CHECK)) {

            context.setHealthCheck(this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_TYPE).getValue());

            if (this.getFromConfig(LoadBalancerConstants.HEALTH_CHECK_REQUEST_TIMEOUT) != null) {

                String hcReqTimeOut = this.getFromConfig
                        (LoadBalancerConstants.HEALTH_CHECK_REQUEST_TIMEOUT).getValue();

                int timeout = CommonUtil.getTimeInMilliSeconds(hcReqTimeOut);

                if (isWithInLimit(timeout)) {
                    context.setReqTimeout(timeout);
                    log.info("Request TIME_OUT : " + context.getReqTimeout());
                } else {
                    //TODO: Is this okay..?
                    context.setReqTimeout(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);
                    log.error("Exceeded TIMEOUT LIMIT. Loading DEFAULT value for " +
                            "Request TIME_OUT : " + context.getReqTimeout());
                }
            } else {
                //TODO: Is this okay..?
                context.setReqTimeout(LoadBalancerConstants.DEFAULT_REQ_TIMEOUT);
                log.error("LB_REQUEST_TIMEOUT NOT SPECIFIED. Loading DEFAULT value for " +
                        "Request TIME_OUT : " + context.getReqTimeout());
            }

        }

        log.info("HEALTH CHECK TYPE : " + context.getHealthCheck());

    }


    /**
     * This method validates a given configuration, if anything is missing default value will be added.
     *
     */

    private void validateConfig() {

        validateAlgorithm();
        validatePersistence();
        validateHealthCheck();

    }

}
