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

package org.wso2.carbon.gateway.httploadbalancer.mediator;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.flow.AbstractMediator;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.LoadBalancingAlgorithm;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.LeastResponseTime;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.Random;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.RoundRobin;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.StrictClientIPHashing;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.weighted.WeightedRandom;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.weighted.WeightedRoundRobin;
import org.wso2.carbon.gateway.httploadbalancer.callback.LoadBalancerMediatorCallBack;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.invokers.LBEndpointCallMediator;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;
import org.wso2.carbon.gateway.httploadbalancer.utils.handlers.scheduled.ActiveHealthCheckHandler;
import org.wso2.carbon.gateway.httploadbalancer.utils.handlers.scheduled.BackToHealthyHandler;
import org.wso2.carbon.gateway.httploadbalancer.utils.handlers.scheduled.TimeoutHandler;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LoadBalancerMediator.
 * <p>
 * This mediator receives client's request from InboundOutbound endpoint's pipeline.
 * <p>
 * This is responsible to choose Outbound endpoint based on the specified algorithm choice.
 * <p>
 * This mediator will also look for headers in client request to maintain persistence.
 * <p>
 * This mediator is responsible for choosing healthy OutboundEndpoint.
 */
public class LoadBalancerMediator extends AbstractMediator {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerMediator.class);

    private Map<String, LBEndpointCallMediator> lbCallMediatorMap;

    private final LoadBalancingAlgorithm lbAlgorithm;
    private final LoadBalancerConfigContext context;

    private String configName;


    @Override
    public String getName() {

        return "LoadBalancerMediator";
    }


    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    /**
     * @param lbOutboundEndpoints LBOutboundEndpoints List.
     * @param context             LoadBalancerConfigContext.
     */
    public LoadBalancerMediator(List<LBOutboundEndpoint> lbOutboundEndpoints,
                                LoadBalancerConfigContext context, String configName) {

        this.context = context;
        this.configName = configName;
        lbCallMediatorMap = new HashMap<>();

        if (context.getAlgorithmName().equals(LoadBalancerConstants.ROUND_ROBIN)) {

            lbAlgorithm = new RoundRobin(lbOutboundEndpoints);

        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.STRICT_IP_HASHING)) {

            lbAlgorithm = new StrictClientIPHashing(lbOutboundEndpoints);

        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.LEAST_RESPONSE_TIME)) {

            lbAlgorithm = new LeastResponseTime(lbOutboundEndpoints);

        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.RANDOM)) {

            lbAlgorithm = new Random(lbOutboundEndpoints);

        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.WEIGHTED_ROUND_ROBIN)) {

            lbAlgorithm = new WeightedRoundRobin(lbOutboundEndpoints,
                    CommonUtil.getWeightsList(lbOutboundEndpoints, context.getWeightsMap()));

        } else if (context.getAlgorithmName().equals(LoadBalancerConstants.WEIGHTED_RANDOM)) {

            lbAlgorithm = new WeightedRandom(lbOutboundEndpoints,
                    CommonUtil.getWeightsList(lbOutboundEndpoints, context.getWeightsMap()));
        } else {
            lbAlgorithm = null;
            return;
        }

        context.setLoadBalancingAlgorithm(lbAlgorithm);

        // Creating LoadBalancerCallMediators for OutboundEndpoints...
        for (LBOutboundEndpoint lbOutboundEP : lbOutboundEndpoints) {
            lbCallMediatorMap.put(lbOutboundEP.getName(), new LBEndpointCallMediator(lbOutboundEP, context));
        }
        //At this point everything is initialized.

        ScheduledThreadPoolExecutor threadPoolExecutor;

        //In case of NO_HEALTH_CHECK only TimeoutHandler has to be started.
        if (this.context.getHealthCheck().equals(LoadBalancerConstants.NO_HEALTH_CHECK)) {

            threadPoolExecutor = new ScheduledThreadPoolExecutor(1);

            TimeoutHandler timeOutHandler = new TimeoutHandler(this.context, this.lbAlgorithm, this.configName);

            //We will be shutting down executor, but this ensures scheduled tasks will be running even after shutdown.
            threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            threadPoolExecutor.scheduleAtFixedRate(
                    timeOutHandler, 0, LoadBalancerConstants.DEFAULT_TIMEOUT_TIMER_PERIOD, TimeUnit.MILLISECONDS);
            //This is a good practise.
            threadPoolExecutor.shutdown();

            //In case of ACTIVE_HEALTH_CHECK TimeoutHandler, ActiveHealthCheckHandler and
            // BackToHealthyHandler are to be started.
        } else if (this.context.getHealthCheck().equals(LoadBalancerConstants.ACTIVE_HEALTH_CHECK)) {

            threadPoolExecutor = new ScheduledThreadPoolExecutor(3);

            TimeoutHandler timeOutHandler = new TimeoutHandler(this.context, this.lbAlgorithm, this.configName);

            ActiveHealthCheckHandler activeHealthCheckHandler = new ActiveHealthCheckHandler(this.context,
                    this.lbAlgorithm, this.configName);

            BackToHealthyHandler backToHealthyHandler = new BackToHealthyHandler(this.context,
                    this.lbAlgorithm, this.configName);

            //We will be shutting down executor, but this ensures scheduled tasks will be running even after shutdown.
            threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            threadPoolExecutor.scheduleAtFixedRate(
                    timeOutHandler, 0, LoadBalancerConstants.DEFAULT_TIMEOUT_TIMER_PERIOD, TimeUnit.MILLISECONDS);
            threadPoolExecutor.scheduleAtFixedRate(
                    activeHealthCheckHandler, 0, context.getHealthycheckInterval(), TimeUnit.MILLISECONDS);
            threadPoolExecutor.scheduleAtFixedRate(
                    backToHealthyHandler, 0, context.getHealthycheckInterval(), TimeUnit.MILLISECONDS);
            //This is a good practise.
            threadPoolExecutor.shutdown();

        } else {
            // DEFAULT or PASSIVE
            //In this case, both TimeoutHandler and BackToHealthyHandler are to be started.
            threadPoolExecutor = new ScheduledThreadPoolExecutor(2);

            TimeoutHandler timeOutHandler = new TimeoutHandler(this.context, this.lbAlgorithm, this.configName);

            BackToHealthyHandler backToHealthyHandler = new BackToHealthyHandler(this.context,
                    this.lbAlgorithm, this.configName);

            //We will be shutting down executor, but this ensures scheduled tasks will be running even after shutdown.
            threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            threadPoolExecutor.scheduleAtFixedRate(
                    timeOutHandler, 0, LoadBalancerConstants.DEFAULT_TIMEOUT_TIMER_PERIOD, TimeUnit.MILLISECONDS);
            threadPoolExecutor.scheduleAtFixedRate(
                    backToHealthyHandler, 0, context.getHealthycheckInterval(), TimeUnit.MILLISECONDS);
            //This is a good practise.
            threadPoolExecutor.shutdown();

        }


    }

    @Override
    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback) throws Exception {


/**
 log.info("Inside LB mediator...");

 log.info("Transport Headers...");
 log.info(carbonMessage.getHeaders() + "\n\n");

 log.info("Properties...");
 log.info(carbonMessage.getProperties() + "\n\n");

 **/


        //log.info(" LB Mediator Cookie Header : " + carbonMessage.getHeader(LoadBalancerConstants.COOKIE_HEADER));

        LBOutboundEndpoint nextLBOutboundEndpoint = null;
        final String persistenceType = context.getPersistence();


        if (persistenceType.equals(LoadBalancerConstants.APPLICATION_COOKIE)
                || persistenceType.equals(LoadBalancerConstants.LB_COOKIE)) {

            String existingCookie = null;

            //Getting cookie from request header.
            existingCookie = carbonMessage.getHeader(LoadBalancerConstants.COOKIE_HEADER);

            /**NOTE: You can maintain persistence only if you have LB specific cookie.**/

            if (existingCookie == null || !(existingCookie.contains(LoadBalancerConstants.LB_COOKIE_NAME))) {
                //There is no cookie or no LB specific cookie.

                //Fetching endpoint according to algorithm (no persistence is maintained).
                log.warn("There is no LB specific cookie.." +
                        "Persistence cannot be maintained.." +
                        "Choosing Endpoint based on algorithm");
                nextLBOutboundEndpoint = lbAlgorithm.getNextLBOutboundEndpoint(carbonMessage, context);

            } else { //There is a LB specific cookie.

                String cookieName = null;


                if (persistenceType.equals(LoadBalancerConstants.APPLICATION_COOKIE)) {

                    boolean isError1 = false, isError2 = false;

                    //There are two possible cookie patterns in this case.
                    /**
                     * TYPE 1 FORMAT
                     */
                    //1) eg cookie: JSESSIONID=ghsgsdgsg---LB_COOKIE:SOME_HASHCODE---
                    // We need to retrieve SOME_HASHCODE in this case.
                    String regEx =
                            "(" +
                                    LoadBalancerConstants.LB_COOKIE_DELIMITER +
                                    LoadBalancerConstants.LB_COOKIE_NAME +
                                    LoadBalancerConstants.COOKIE_NAME_VALUE_SEPARATOR +
                                    ")" +
                                    "(.*)" +
                                    "(" +
                                    LoadBalancerConstants.LB_COOKIE_DELIMITER +
                                    ")";

                    Pattern p = Pattern.compile(regEx);
                    Matcher m = p.matcher(existingCookie);
                    if (m.find()) {
                        cookieName = m.group(2);
                    } else {
                        isError1 = true;
                        log.warn("Couldn't retrieve LB Key from cookie of type 1 for " +
                                "Persistence type : " + LoadBalancerConstants.APPLICATION_COOKIE);
                    }

                    if (isError1) {
                        /**
                         * TYPE 2 FORMAT
                         */
                        // 2) cookie: LB_COOKIE=SOME_HASHCODE
                        // We need to retrieve SOME_HASHCODEin this case.
                        regEx = "(" +
                                LoadBalancerConstants.LB_COOKIE_NAME +
                                "=)(.*)";
                        p = Pattern.compile(regEx);
                        m = p.matcher(existingCookie);

                        if (m.find()) {
                            cookieName = m.group(2);

                        } else {
                            isError2 = true;
                            log.warn("Couldn't retrieve LB Key from cookie of type 2 for " +
                                    "Persistence type :" + LoadBalancerConstants.APPLICATION_COOKIE);

                        }
                    }

                    if (isError1 && isError2) {

                        log.error("Cookie matching didn't match any of the two types for " +
                                "Persistence type :" + LoadBalancerConstants.APPLICATION_COOKIE);

                        //TODO: is this okay or should we send error..?
                        log.error("Persistence cannot be maintained.. Endpoint will be chosen based on algorithm.");


                    } else if (isError1) {

                        log.info("LB key of type 2 has been retrieved from cookie for" +
                                "Persistence type :" + LoadBalancerConstants.APPLICATION_COOKIE);
                    }


                } else { // persistenceType is LoadBalancerConstants.LB_COOKIE.

                    //eg cookie: LB_COOKIE=SOME_HASHCODE
                    //We need to retrieve SOME_HASHCODE in this case.
                    String regEx = "(" +
                            LoadBalancerConstants.LB_COOKIE_NAME +
                            "=)(.*)";

                    Pattern p = Pattern.compile(regEx);
                    Matcher m = p.matcher(existingCookie);
                    if (m.find()) {
                        cookieName = m.group(2);
                    } else {

                        log.error("Couldn't retrieve LB Key from cookie for " +
                                "Persistence type :" + LoadBalancerConstants.LB_COOKIE);

                        //TODO: is this okay or should we send error..?
                        log.error("Persistence cannot be maintained.. Endpoint will be chosen based on algorithm.");

                    }

                }


                if (context.getOutboundEPKeyFromCookie(cookieName) != null) {

                    String outboundEPKey = context.getOutboundEPKeyFromCookie(cookieName);


                    /** Removing LB specific cookie before forwarding req to server. */

                    //If there is delimiter, there exists a BE server's cookie.
                    if (existingCookie.contains(LoadBalancerConstants.LB_COOKIE_DELIMITER)) {

                        //Removing our LB specific cookie from BE cookie. We don't want it be sent to BE.
                        existingCookie = existingCookie.substring(0,
                                existingCookie.indexOf(LoadBalancerConstants.LB_COOKIE_DELIMITER));

                        carbonMessage.setHeader(LoadBalancerConstants.COOKIE_HEADER, existingCookie);

                    } else {

                        //There is only LB specific cookie. We don't want it to be sent to BE.
                        carbonMessage.removeHeader(LoadBalancerConstants.COOKIE_HEADER);
                    }


                    //Choosing endpoint based on persistence.
                    if (outboundEPKey != null) {
                        nextLBOutboundEndpoint = context.getLBOutboundEndpoint(outboundEPKey);
                    }


                } else {

                    log.error("LB Key extraction using RegEx has gone for a toss." +
                            "Check the logic. ");

                    //TODO: is this okay or should we send error..?
                    log.error("Persistence cannot be maintained.. Choosing endpoint based on algorithm.");

                    //Fetching endpoint according to algorithm.
                    nextLBOutboundEndpoint = lbAlgorithm.getNextLBOutboundEndpoint(carbonMessage, context);
                }


            }

        } else if (persistenceType.equals(LoadBalancerConstants.CLIENT_IP_HASHING)) {

            String ipAddress = CommonUtil.getClientIP(carbonMessage);
            log.info("IP address retrieved is : " + ipAddress);
            if (CommonUtil.isValidIP(ipAddress)) {

                //getting endpoint name for this ipAddress.
                String endpointName = context.getStrictClientIPHashing().getOutboundEndpointName(ipAddress);

                //Chosing endpoint based on IP Hashing.
                // If no endpoints are available, hash will return null.
                if (endpointName != null) {
                    nextLBOutboundEndpoint = context.getLBOutboundEndpoint(endpointName);

                    if (log.isDebugEnabled()) {
                        log.debug("Endpoint name : " + nextLBOutboundEndpoint.getName());
                    }
                }

            } else {

                log.error("The IP Address retrieved is : " + ipAddress +
                        " which is invalid according to our validation. " +
                        "Endpoint will be chosen based on algorithm");

                //Fetching endpoint according to algorithm.
                nextLBOutboundEndpoint = lbAlgorithm.getNextLBOutboundEndpoint(carbonMessage, context);

            }

        } else { //Policy is NO_PERSISTENCE

            //Fetching endpoint according to algorithm.
            nextLBOutboundEndpoint = lbAlgorithm.getNextLBOutboundEndpoint(carbonMessage, context);


        }

        if (nextLBOutboundEndpoint != null) {

            // Chosen Endpoint is healthy.
            // If there is any persistence, it will be maintained.

            // Calling chosen LBOutboundEndpoint's LBEndpointCallMediator receive...

            //log.info("Chosen endpoint by LB is.." + nextLBOutboundEndpoint.getName());

            lbCallMediatorMap.get(nextLBOutboundEndpoint.getName()).
                    receive(carbonMessage, new LoadBalancerMediatorCallBack(carbonCallback, this,
                            this.context, nextLBOutboundEndpoint));
            return true;


        } else {

            /**
             * Algorithm returns null if no endpoints are available.
             * In that case, we have to check if all endpoints are unHealthy
             */
            if (areAllEndpointsUnhealthy()) {

                CommonUtil.sendErrorResponse(carbonCallback, false);
            } else { //Something has gone wrong.

                log.error("Unable to choose endpoint for forwarding the request." +
                        " Check logs to see what went wrong.");
                CommonUtil.sendErrorResponse(carbonCallback, true);
            }
            return false;
        }


    }

    private boolean areAllEndpointsUnhealthy() {

        int unHealthyListSize;
        unHealthyListSize = context.getUnHealthyEPQueueSize();

        if (context.getLbOutboundEndpoints().size() == unHealthyListSize) {
            return true;
        } else {
            return false;
        }
    }

}
