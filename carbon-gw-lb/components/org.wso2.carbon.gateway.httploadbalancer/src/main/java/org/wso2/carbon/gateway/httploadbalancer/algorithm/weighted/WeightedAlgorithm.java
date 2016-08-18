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

import org.wso2.carbon.gateway.httploadbalancer.algorithm.LoadBalancingAlgorithm;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;

import java.util.List;

/**
 * All Weighted Algorithms must implement this interface.
 */
public interface WeightedAlgorithm extends LoadBalancingAlgorithm {

    /**
     * @param lbOutboundEPs List of LBOutboundEndpoints
     * @param weights       Their corresponding weights.
     *                      <p>
     *                      NOTE: All validations must be done before.
     *                      This method expects ordered list of
     *                      endpoints and their corresponding weights.
     */
    void setLBOutboundEndpoints(List<LBOutboundEndpoint> lbOutboundEPs, List<Integer> weights);

    /**
     * @param carbonMessage      CarbonMessage
     * @param carbonCallback     CarbonCallback
     * @param context            LoadBalancerConfigContext
     * @param lbOutboundEndpoint LBOutboundEndpoint
     * @return
     * @throws Exception NOTE: Unlike Simple algorithms, in case if decision is made based on persistence,
     *                   algorithm should know that request is being sent to an endpoint.  So that it
     *                   can increment window tracker and weighted load distribution can be done in
     *                   a much efficient way.
     */
    boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback,
                    LoadBalancerConfigContext context,
                    LBOutboundEndpoint lbOutboundEndpoint) throws Exception;


}
