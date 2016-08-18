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
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.LeastResponseTime;
import org.wso2.carbon.gateway.httploadbalancer.callback.LoadBalancerMediatorCallBack;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;
import org.wso2.carbon.gateway.httploadbalancer.utils.handlers.error.LBErrorHandler;
import org.wso2.carbon.messaging.DefaultCarbonMessage;

import java.util.concurrent.TimeUnit;

/**
 * TimeoutHandler for LoadBalancerMediatorCallBack.
 */
public class TimeoutHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TimeoutHandler.class);


    private final LoadBalancerConfigContext context;
    private final String handlerName;
    private final LoadBalancingAlgorithm algorithm;

    //To avoid race condition if any.
    private volatile boolean isRunning = false;

    public TimeoutHandler(LoadBalancerConfigContext context, LoadBalancingAlgorithm algorithm, String configName) {

        this.context = context;
        this.algorithm = algorithm;
        this.handlerName = configName + "-" + this.getName();

        log.info(this.getHandlerName() + " started.");
    }

    public String getName() {

        return "TimeoutHandler";
    }

    private String getHandlerName() {
        return handlerName;
    }

    @Override
    public void run() {

        if (isRunning) {
            return;
        }

        processCallBackPool();

        isRunning = false;

    }

    private void processCallBackPool() {

        boolean hasContent = false;

        // Only operations on Concurrent HashMap are thread safe.
        // Since we are retrieving it's size locking is better.
        // Lock is released after getting size.
        synchronized (context.getCallBackPool()) {

            if (context.getCallBackPool().size() > 0) {
                hasContent = true;
            }
        }

        //If there is no object in pool no need to process.
        if (hasContent) {


            /**
             * Here also we are not locking callBackPool, because lock on concurrent HashMap will be overkill.
             * At this point 2 cases might occur.
             *
             *  1) A new object will be added to the call back pool. We need not worry about it because,
             *     it will be added just now so it will not get timedOut.
             *
             *  2) A response arrives and corresponding object is removed from pool. So don't worry.
             *     We got response and it would have been sent to client.
             *
             *     NOTE: By iterating through Key Set we are getting callback object
             *     from our callBackPool.
             */

            for (LoadBalancerMediatorCallBack callBack : context.getCallBackPool().keySet()) {

                long currentTime = this.getCurrentTime();

                /**
                 * CallBack might be null because, we are iterating using keySet. So when getting keySet()
                 * we will get keys of all objects present in pool at that point.
                 *
                 * Suppose a response arrives after getting keySet(), that callBack will be removed from
                 * pool and it will also be reflected here because we are accessing callbackPool through
                 * context.getCallBackPool(), instead of having a local reference to it.
                 *
                 * So doing null check is better.
                 */
                if (callBack != null) {

                    if (((currentTime - (callBack.getCreatedTime()
                            - LoadBalancerConstants.DEFAULT_GRACE_PERIOD)) > context.getReqTimeout())) {
                        //This callBack is in pool after it has timedOut.

                        //This operation is on Concurrent HashMap, so no synchronization is required.
                        if (!context.isInCallBackPool(callBack)) {
                            //If response arrives at this point, callBack would have been removed from pool
                            //and it would have been sent back to client. So break here.
                            break;
                        } else {
                            context.removeFromCallBackPool(callBack);
                            //From this point, this callback will not be available in pool.
                            //So if response arrives it will be discarded.


                            new LBErrorHandler().handleFault
                                    ("504", new Throwable("Gateway TimeOut"),
                                            new DefaultCarbonMessage(true), callBack);


                        }


                        if (!this.context.getHealthCheck().equals(LoadBalancerConstants.NO_HEALTH_CHECK)) {
                            /**
                             * But here we need synchronization because, this LBOutboundEndpoint might be
                             * used in CallMediator and LoadBalancerMediatorCallBack.
                             *
                             * We will be changing LBOutboundEndpoint's properties here.
                             *
                             * If an LBOutboundEndpoint is unHealthy it should not be available else where.
                             * So we are locking on it, till we remove it from all the places
                             * where it is available.
                             *
                             * NOTE: The below code does only HealthCheck related activities.
                             *
                             */


                            callBack.getLbOutboundEndpoint().incrementUnHealthyRetries();
                            /**
                             * IMPORTANT: Here in case of LeastResponseTime algorithm,
                             * we are doing send response time as 0,
                             * other wise detection of unHealthyEndpoint will be late.
                             */
                            if (context.getAlgorithmName().equals(LoadBalancerConstants.LEAST_RESPONSE_TIME)) {

                                ((LeastResponseTime) context.getLoadBalancingAlgorithm()).
                                        setAvgResponseTime(callBack.getLbOutboundEndpoint(), 0);

                            }

                            if (this.reachedUnHealthyRetriesThreshold(callBack.getLbOutboundEndpoint())) {

                                CommonUtil.removeUnHealthyEndpoint(context, algorithm,
                                        callBack.getLbOutboundEndpoint());
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


    private long getCurrentTime() {

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
