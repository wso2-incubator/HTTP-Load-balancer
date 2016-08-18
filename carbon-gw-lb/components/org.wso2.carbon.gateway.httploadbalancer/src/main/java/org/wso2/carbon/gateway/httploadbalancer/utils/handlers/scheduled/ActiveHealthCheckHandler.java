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
 * This handler is responsible for periodically checking whether OutboundEndpoints are healthy or not.
 * <p>
 * This is Active Health Checking and it can detect unHealthy endpoints before failure occurs.
 * <p>
 * Tries to establish socket connection to OutboundEndpoints.
 */
public class ActiveHealthCheckHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ActiveHealthCheckHandler.class);

    private final LoadBalancerConfigContext context;
    private final String handlerName;
    private final LoadBalancingAlgorithm algorithm;
    //To avoid race condition if any.
    private volatile boolean isRunning = false;

    public ActiveHealthCheckHandler(LoadBalancerConfigContext context, LoadBalancingAlgorithm algorithm,
                                    String configName) {

        this.context = context;
        this.algorithm = algorithm;
        this.handlerName = configName + "-" + this.getName();

        log.info(this.getHandlerName() + " started.");

    }

    public String getName() {

        return "ActiveHealthCheckHandler";
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


        if (context.getLbOutboundEndpoints().size() > 0) {
            hasContent = true;
        }


        //Tf there are no LBOutboundEndpoints, no need to process.
        if (hasContent) {


            List<LBOutboundEndpoint> list = new ArrayList<>(context.getLbOutboundEndpoints().values());

            for (LBOutboundEndpoint lbOutboundEndpoint : list) {

                //If it is in UnHealthyQueue, we need not establish connection and check.
                if (context.getUnHealthyLBEPQueue().contains(lbOutboundEndpoint)) {
                    continue;
                }

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

                                log.info(lbOutboundEndpoint.getName() + " is healthy..");
                                break;

                            } else {

                                throw new LBException("Connection timedOut for Endpoint : "
                                        + lbOutboundEndpoint.getName());
                            }

                        } else {
                            throw new NullPointerException("<host:port> value retrieved from "
                                    + lbOutboundEndpoint.getName() + " is null..");

                        }
                    } catch (IOException | InterruptedException | NullPointerException | LBException e) {

                        log.error(e.toString());

                        lbOutboundEndpoint.incrementUnHealthyRetries();

                        if (reachedUnHealthyRetriesThreshold(lbOutboundEndpoint)) {

                            CommonUtil.removeUnHealthyEndpoint(context, algorithm, lbOutboundEndpoint);

                        } else {
                            log.info("No of unHealthy retries not yet reached...");
                            continue;
                        }
                        //This break is MUST.
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

        }

    }


    private boolean reachedUnHealthyRetriesThreshold(LBOutboundEndpoint lbOutboundEndpoint) {

        if (lbOutboundEndpoint.getUnHealthyRetriesCount() >= context.getUnHealthyRetries()) {
            return true;
        } else {
            return false;
        }

    }
}
