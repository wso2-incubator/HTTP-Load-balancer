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

package org.wso2.carbon.gateway.httploadbalancer.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.outbound.OutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.callback.LoadBalancerMediatorCallBack;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * An instance of this class has a reference to an OutboundEndpoint.
 * It also holds other LB HealthCheck related values pertained to it.
 * <p>
 * NOTE: Inside LB all OutboundEndpoints MUST be accessed via this Object only.
 */
public class LBOutboundEndpoint {

    private static final Logger log = LoggerFactory.getLogger(LBOutboundEndpoint.class);

    // HTTP or HTTPS Endpoint.
    private OutboundEndpoint outboundEndpoint;

    // Healthy or not.
    private AtomicBoolean isHealthy = new AtomicBoolean(true);

    // No of retries to be done to see whether it is alive again.
    private AtomicInteger healthyRetriesCount = new AtomicInteger(0);

    // No of retries to be done to mark an endpoint as unHealthy.
    private AtomicInteger unHealthyRetriesCount = new AtomicInteger(0);


    public LBOutboundEndpoint(OutboundEndpoint outboundEndpoint) {
        this.outboundEndpoint = outboundEndpoint;
    }

    public OutboundEndpoint getOutboundEndpoint() {
        return outboundEndpoint;
    }

    public boolean isHealthy() {

        return isHealthy.get();

    }

    public int getHealthyRetriesCount() {

        return healthyRetriesCount.get();

    }

    public void setHealthyRetriesCount(int healthyRetriesCount) {

        this.healthyRetriesCount.set(healthyRetriesCount);
    }


    public int getUnHealthyRetriesCount() {

        return unHealthyRetriesCount.get();

    }

    public String getName() {
        return this.outboundEndpoint.getName();
    }

    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback,
                           LoadBalancerConfigContext context) throws Exception {


        // carbonMessage = CommonUtil.appendLBIP(carbonMessage, true);
        //No need to synchronize as we are operating on concurrent HashMap.
        context.addToCallBackPool((LoadBalancerMediatorCallBack) carbonCallback);

        this.outboundEndpoint.receive(carbonMessage, carbonCallback);

        return false;
    }

    public void incrementUnHealthyRetries() {

        this.unHealthyRetriesCount.incrementAndGet();

        log.warn("Incremented UnHealthyRetries count for endPoint : " + this.getName());
    }

    public void incrementHealthyRetries() {

        this.healthyRetriesCount.incrementAndGet();

        if (log.isDebugEnabled()) {
            log.info("Incremented HealthyRetries count for endPoint : " + this.getName());
        }
    }

    public void markAsUnHealthy() {

        isHealthy.set(false);

        log.warn(this.getName() + " is unHealthy");
    }

    /**
     * Call this method when unHealthy LBEndpoint becomes Healthy
     * and also when when we receive a successful response from backend
     * so that we can avoid any false alarms (i.e when unHealthy endpoint is
     * incremented due to some timeOut but the actual endpoint is actually healthy).
     */
    public void resetHealthPropertiesToDefault() {

        this.isHealthy.set(true);
        this.unHealthyRetriesCount.set(0);
        this.healthyRetriesCount.set(0);
    }

    public void resetUnhealthyRetriesCount() {

        this.unHealthyRetriesCount.set(0);

    }

}
