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

package org.wso2.carbon.gateway.httploadbalancer.algorithm.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.messaging.CarbonMessage;

import java.util.List;

/**
 * Implementation of Random Algorithm.
 * <p>
 * All Endpoints are assumed to have equal weights.
 */
public class Random implements SimpleAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(RoundRobin.class);
    private final Object lock = new Object();

    private List<LBOutboundEndpoint> lbOutboundEndpoints;

    public Random(List<LBOutboundEndpoint> lbOutboundEndpoints) {

        this.setLBOutboundEndpoints(lbOutboundEndpoints);
    }

    /**
     * @return the name of implemented LB algorithm.
     */

    @Override
    public String getName() {
        return LoadBalancerConstants.RANDOM;
    }

    /**
     * @param lbOutboundEPs list of all Outbound Endpoints to be load balanced.
     */
    @Override
    public void setLBOutboundEndpoints(List<LBOutboundEndpoint> lbOutboundEPs) {

        synchronized (this.lock) {
            this.lbOutboundEndpoints = lbOutboundEPs;
        }
    }

    /**
     * @param lbOutboundEndpoint outboundEndpoint to be added to the existing list.
     *                           <p>
     *                           This method will be used to add an endpoint once it
     *                           is back to healthy state.
     */
    @Override
    public void addLBOutboundEndpoint(LBOutboundEndpoint lbOutboundEndpoint) {

        synchronized (this.lock) {
            if (!this.lbOutboundEndpoints.contains(lbOutboundEndpoint)) {
                this.lbOutboundEndpoints.add(lbOutboundEndpoint);
            } else {
                log.info(lbOutboundEndpoint.getName() + " already exists in list..");
            }
        }
    }

    /**
     * @param lbOutboundEndpoint outboundEndpoint to be removed from existing list.
     *                           <p>
     *                           This method will be used to remove an unHealthyEndpoint.
     */
    @Override
    public void removeLBOutboundEndpoint(LBOutboundEndpoint lbOutboundEndpoint) {

        synchronized (this.lock) {
            if (this.lbOutboundEndpoints.contains(lbOutboundEndpoint)) {
                this.lbOutboundEndpoints.remove(lbOutboundEndpoint);
            } else {
                log.info(lbOutboundEndpoint.getName() + " has already been removed from list..");
            }
        }

    }

    /**
     * @param cMsg    Carbon Message has all headers required to make decision.
     * @param context LoadBalancerConfigContext.
     * @return the next LBOutboundEndpoint according to implemented LB algorithm.
     */
    @Override
    public LBOutboundEndpoint getNextLBOutboundEndpoint(CarbonMessage cMsg, LoadBalancerConfigContext context) {
        LBOutboundEndpoint endPoint = null;

        synchronized (this.lock) {
            if (this.lbOutboundEndpoints != null && this.lbOutboundEndpoints.size() > 0) {

                endPoint = this.lbOutboundEndpoints.get((int) (Math.random() * (this.lbOutboundEndpoints.size())));
            } else {

                log.error("No OutboundEndpoint is available..");

            }
        }

        return endPoint;
    }

    /**
     * Nothing to reset in this algorithm.
     */
    @Override
    public void reset() {

    }

    /**
     * @return Object used for locking.
     */
    @Override
    public Object getLock() {
        return this.lock;
    }
}
