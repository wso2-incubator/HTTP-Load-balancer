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

package org.wso2.carbon.gateway.httploadbalancer.algorithm.weighted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.outbound.WeightedLBOutboundEndpoint;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of weighted Round Robin Algorithm.
 * <p>
 * User has to define weights for each endpoint. By default weight is 1.
 */
public class WeightedRoundRobin implements WeightedAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(WeightedRoundRobin.class);
    private final Object lock = new Object();

    private List<WeightedLBOutboundEndpoint> weightedLBOutboundEndpoints = new ArrayList<>();
    private Map<String, WeightedLBOutboundEndpoint> map = new ConcurrentHashMap<>();

    private int index = 0;
    private int weightsWindow = 0; // Sum of weights of all endpoints.
    private int weightsWindowTracker = 0; // To keep track whether weightsWindow number has elapsed or not.


    public WeightedRoundRobin(List<LBOutboundEndpoint> lbOutboundEPs, List<Integer> weights) {
        this.setLBOutboundEndpoints(lbOutboundEPs, weights);
    }

    /**
     * @return the name of implemented LB algorithm.
     */
    @Override
    public String getName() {

        return LoadBalancerConstants.WEIGHTED_ROUND_ROBIN;
    }


    /**
     * @param lbOutboundEPs List of LBOutboundEndpoints
     * @param weights       Their corresponding weights.
     *                      <p>
     *                      NOTE: All validations must be done before.
     *                      This method expects ordered list of
     *                      endpoints and their corresponding weights.
     */
    @Override
    public void setLBOutboundEndpoints(List<LBOutboundEndpoint> lbOutboundEPs, List<Integer> weights) {

        synchronized (this.lock) {
            for (int i = 0; i < lbOutboundEPs.size(); i++) {
                this.weightedLBOutboundEndpoints.
                        add(new WeightedLBOutboundEndpoint(lbOutboundEPs.get(i), weights.get(i)));
                map.put(lbOutboundEPs.get(i).getName(), this.weightedLBOutboundEndpoints.get(i));
            }

            calculateWeightsWindow();
        }

    }

    /**
     *
     * @param carbonMessage CarbonMessage
     * @param carbonCallback CarbonCallback
     * @param context LoadBalancerConfigContext
     * @param lbOutboundEndpoint LBOutboundEndpoint
     * @return
     * @throws Exception
     */
    @Override
    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback,
                           LoadBalancerConfigContext context,
                           LBOutboundEndpoint lbOutboundEndpoint) throws Exception {


        incrementWeightsWindowTracker(); // To keep track of no requests elapsed for this current window
        map.get(lbOutboundEndpoint.getName()).receive(carbonMessage, carbonCallback, context);
        return false;
    }

    private void calculateWeightsWindow() {

        this.weightsWindow = 0;
        for (WeightedLBOutboundEndpoint endpoint : this.weightedLBOutboundEndpoints) {
            this.weightsWindow += endpoint.getMaxWeight();
        }
        if (log.isDebugEnabled()) {
            log.debug("Weights Window = " + this.weightsWindow);
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
            if (map.containsKey(lbOutboundEndpoint.getName())) {

                if (this.weightedLBOutboundEndpoints.contains(map.get(lbOutboundEndpoint.getName()))) {
                    log.info(lbOutboundEndpoint.getName() + " already exists in list..");
                } else {
                    map.get(lbOutboundEndpoint.getName()).resetCurrentWeight(); //This is MUST.
                    this.weightedLBOutboundEndpoints.add(map.get(lbOutboundEndpoint.getName()));
                }

            } else {
                log.error("Cannot add a new endpoint like this. Use setLBOutboundEndpoints method" +
                        " or Constructor..");

            }
        }

    }

    /**
     * @param lbOutboundEndpoint outboundEndpoint to be removed from existing list.
     *                           <p>
     *                           This method will be used to remove an unHealthyEndpoint.
     *                           <p>
     *                           NOTE: for this algorithm, we are not removing from map.
     *                           But, we are removing from list.
     *                           <p>
     *                           We are doing this because, for health check we need it.
     */
    @Override
    public void removeLBOutboundEndpoint(LBOutboundEndpoint lbOutboundEndpoint) {

        synchronized (this.lock) {
            if (map.containsKey(lbOutboundEndpoint.getName())) {

                if (this.weightedLBOutboundEndpoints.contains(map.get(lbOutboundEndpoint.getName()))) {

                    this.weightedLBOutboundEndpoints.remove(map.get(lbOutboundEndpoint.getName()));
                } else {
                    log.info(lbOutboundEndpoint.getName() + " has already been removed from list..");
                }

            } else {
                log.error(lbOutboundEndpoint.getName() + " is not in map..");
            }
        }
    }

    private void resetAllCurrentWeights() {

        this.weightedLBOutboundEndpoints.forEach(WeightedLBOutboundEndpoint::resetCurrentWeight);

    }

    private void incrementWeightsWindowTracker() {
        this.weightsWindowTracker++;
    }

    private void incrementIndex() {
        this.index++;
        this.index %= this.weightedLBOutboundEndpoints.size();
    }

    private WeightedLBOutboundEndpoint getNextEndpoint() {

        WeightedLBOutboundEndpoint endPoint = null;
        int counter = 0;

        while (true) {

            if (this.weightedLBOutboundEndpoints.get(this.index).getCurrentWeight() <
                    this.weightedLBOutboundEndpoints.get(this.index).getMaxWeight()) {

                endPoint = this.weightedLBOutboundEndpoints.get(this.index);
                break;
            } else {
                incrementIndex();
            }

            if (counter > weightedLBOutboundEndpoints.size()) {
                // This case is just for safety.
                endPoint = this.weightedLBOutboundEndpoints.get(this.index);
                break;
            }
            counter++;
        }

        incrementIndex();

        return endPoint;

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
            if (this.weightedLBOutboundEndpoints != null && this.weightedLBOutboundEndpoints.size() > 0) {


                if (this.weightedLBOutboundEndpoints.size() > 1 && this.weightsWindowTracker >= this.weightsWindow) {

                    resetAllCurrentWeights();
                    this.weightsWindowTracker = 0;
                }

                // It is okay to do roundRobin for first few requests till it reaches WINDOW size.
                // After that it'll be proper LeastResponseTime based load distribution.

                WeightedLBOutboundEndpoint weightedLBOutboundEP = this.getNextEndpoint();

                endPoint = weightedLBOutboundEP.getLbOutboundEndpoint();

            } else {

                log.error("No OutboundEndpoint is available..");

            }
        }

        return endPoint;
    }

    /**
     * Each implementation of LB algorithm will have certain values pertained to it.
     * (Eg: Round robin keeps track of index of OutboundEndpoint).
     * Implementation of this method will resetHealthPropertiesToDefault them.
     * <p>
     * NOTE: In this case weightsWindow is dependant on no of endpoints.
     * So, we have to take care of that too.
     */
    @Override
    public void reset() {

        synchronized (this.lock) {

            if (this.weightedLBOutboundEndpoints.size() > 0 &&
                    this.index >= this.weightedLBOutboundEndpoints.size()) {

                this.index %= this.weightedLBOutboundEndpoints.size();
                this.calculateWeightsWindow(); //Here in this case weights must be atleast one.
                this.weightsWindowTracker %= this.weightsWindow;

            } else if (this.weightedLBOutboundEndpoints.size() == 0) {

                this.index = 0;
                this.weightsWindow = 0;
                this.weightsWindowTracker = 0;
            }

        }
    }

    /**
     * @return Object used for locking.
     */
    @Override
    public Object getLock() {
        return this.lock;
    }

}
