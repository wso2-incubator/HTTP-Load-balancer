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

package org.wso2.carbon.gateway.httploadbalancer.utils.handlers.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.LoadBalancingAlgorithm;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;
import org.wso2.carbon.gateway.httploadbalancer.utils.exception.LBException;


import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;


/**
 * This handler is responsible for periodic checking of
 * UnHealthyLBOutboundEndpoint list to see if any endpoint is back to healthy state again.
 * <p>
 * Tries to establish Socket Connection to unHealthyEndpoints.
 */

public class BackToHealthyHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BackToHealthyHandler.class);

    private final LoadBalancerConfigContext context;
    private final String handlerName;
    private final LoadBalancingAlgorithm algorithm;
    //To avoid race condition if any.
    private volatile boolean isRunning = false;

    public BackToHealthyHandler(LoadBalancerConfigContext context, LoadBalancingAlgorithm algorithm,
                                String configName) {

        this.context = context;
        this.algorithm = algorithm;
        this.handlerName = configName + "-" + this.getName();

        log.info(this.getHandlerName() + " started.");

    }

    public String getName() {

        return "BackToHealthyHandler";
    }

    private String getHandlerName() {
        return handlerName;
    }

    @Override
    public void run() {

        if (isRunning) {
            return;
        }

        processUnHealthyEndpointList();

        isRunning = false;

    }

    private void processUnHealthyEndpointList() {

        boolean hasContent = false;

        // Only operations on ConcurrentLinkedQueue are thread safe.
        // Since we are retrieving it's size locking is better.
        // Lock is released after getting size.
        synchronized (context.getUnHealthyLBEPQueue()) {

            if (context.getUnHealthyLBEPQueue().size() > 0) {
                hasContent = true;
            }
        }


        //Tf there is no content in list no need to process.
        if (hasContent) {


            List<LBOutboundEndpoint> list = new ArrayList<>(context.getUnHealthyLBEPQueue());

            for (LBOutboundEndpoint lbOutboundEndpoint : list) {

                Socket connectionSock = null;
                while (true) {

                    if (connectionSock != null && connectionSock.isConnected()) {
                        try {
                            connectionSock.close();
                        } catch (IOException e) {
                            log.error(e.toString());
                        }
                    }
                    connectionSock = new Socket();
                    try {
                        String hostAndPort = CommonUtil.getHostAndPort(
                                lbOutboundEndpoint.getOutboundEndpoint().getUri());


                        if (hostAndPort != null) {

                            InetAddress inetAddr = InetAddress.getByName(hostAndPort.split(":")[0].trim());

                            SocketAddress socketAddr = new InetSocketAddress(inetAddr,
                                    Integer.parseInt(hostAndPort.split(":")[1].trim()));

                            connectionSock.connect(socketAddr, LoadBalancerConstants.DEFAULT_CONN_TIMEOUT);
                            //Waiting till timeOut..
                            Thread.sleep(LoadBalancerConstants.DEFAULT_CONN_TIMEOUT);

                            if (connectionSock.isConnected()) {
                                lbOutboundEndpoint.incrementHealthyRetries();

                                if (reachedHealthyRetriesThreshold(lbOutboundEndpoint)) {

                                    lbOutboundEndpoint.resetHealthPropertiesToDefault(); //Endpoint is back to healthy.
                                    log.info(lbOutboundEndpoint.getName() + " is back to healthy..");

                                    /**
                                     * When request is received at LoadBalancerMediator,
                                     *  1) It checks for persistence
                                     *  2) It checks for algorithm
                                     *  3) It checks with unHealthyList
                                     *
                                     * So here we are adding Endpoint in this order and finally
                                     * adding it to unHealthyEndpoint list.
                                     */

                                    //This case will only be true in case of CLIENT_IP_HASHING
                                    //as persistence policy.
                                    if (context.getStrictClientIPHashing() != null) {

                                        context.getStrictClientIPHashing().addLBOutboundEndpoint(lbOutboundEndpoint);
                                    }

                                    //We are acquiring lock on Object that is available in algorithm.
                                    //We are adding the HealthyEndpoint back in Algorithm List so that it
                                    //will be chosen by algorithm in future.
                                    //Locking here is MUST because we want the below
                                    //operations to happen without any interference.
                                    synchronized (algorithm.getLock()) {

                                        algorithm.addLBOutboundEndpoint(lbOutboundEndpoint);
                                        algorithm.reset();

                                    }

                                    /**
                                     * IMPORTANT: Removing endpoint from unHealthy Queue.
                                     */
                                    if (context.getUnHealthyLBEPQueue().contains(lbOutboundEndpoint)) {
                                        context.getUnHealthyLBEPQueue().remove(lbOutboundEndpoint);
                                    } else {
                                        log.warn(lbOutboundEndpoint.getName() +
                                                " already removed from unHealthy Queue..");
                                    }

                                } else {
                                    log.info("No of retries not yet reached...");
                                    continue;
                                }
                                //This break is MUST.
                                break;

                            } else {
                                throw new LBException("Connection timedOut for Endpoint : "
                                        + lbOutboundEndpoint.getName());
                            }

                        } else {
                            throw new NullPointerException("<host:port> value retrieved from "
                                    + lbOutboundEndpoint.getName() + " is null..");
                        }
                    } catch (InterruptedException | IOException | NullPointerException | LBException e) {
                        log.error(e.toString());
                        lbOutboundEndpoint.setHealthyRetriesCount(0);
                        log.warn(lbOutboundEndpoint.getName() + " is still unHealthy..");
                        break;
                    } finally {
                        if (connectionSock.isConnected()) {
                            try {
                                connectionSock.close();

                            } catch (IOException e) {
                                log.error(e.toString());

                            }
                        }
                    }
                }
            }
            if (context.getUnHealthyLBEPQueue().size() == 0) {

                log.info("All endpoints are back to healthy state.");

            } else {

                log.warn("There are " + context.getUnHealthyLBEPQueue().size() + " unHealthy endpoint(s).");
            }

        }

    }

    private boolean reachedHealthyRetriesThreshold(LBOutboundEndpoint lbOutboundEndpoint) {

        if (lbOutboundEndpoint.getHealthyRetriesCount() >= context.getHealthyRetries()) {
            return true;
        } else {
            return false;
        }

    }
}
