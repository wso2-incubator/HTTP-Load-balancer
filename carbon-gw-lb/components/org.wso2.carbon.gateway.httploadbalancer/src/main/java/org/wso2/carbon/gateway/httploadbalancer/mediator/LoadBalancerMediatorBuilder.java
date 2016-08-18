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
import org.wso2.carbon.gateway.core.config.GWConfigHolder;

import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;


/**
 * LoadBalancerMediatorBuilder.
 */
public class LoadBalancerMediatorBuilder {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerMediatorBuilder.class);


    /**
     * @param gwConfigHolder GWConfigHolder.
     * @param context        LoadBalancerConfigContex.
     *                       <p>
     *                       This method is where LoadBalancerMediator is added to Pipeline.
     *                       <p>
     *                       Groups are also handled here.
     */
    public static void configure(GWConfigHolder gwConfigHolder, LoadBalancerConfigContext context) {


        LoadBalancerMediator lbMediator = new LoadBalancerMediator(
                CommonUtil.getLBOutboundEndpointsList(context.getLbOutboundEndpoints()), context,
                // We will be using this name for our timer. If configHolder has a name we will use that.
                // Otherwise we will use InboundEndpoint's name.
                // Anyways, it will be unique and will be easy to debug.
                (gwConfigHolder.getName() == null || gwConfigHolder.getName().equals("default")) ?
                        gwConfigHolder.getInboundEndpoint().getName() : gwConfigHolder.getName());

        gwConfigHolder.
                getPipeline(gwConfigHolder.getInboundEndpoint().getPipeline()).addMediator(lbMediator);

    }

    public static void configureForGroup(GWConfigHolder gwConfigHolder,
                                         LoadBalancerConfigContext context, String groupPath) {

        LoadBalancerMediator lbMediator = new LoadBalancerMediator(
                CommonUtil.getLBOutboundEndpointsList(context.getLbOutboundEndpoints()), context,
                // We will be using this name for our timer. If configHolder has a name we will use that.
                // Otherwise we will use InboundEndpoint's name.
                // Anyways, it will be unique and will be easy to debug.
                // Here we are appending groupPath also.
                ((gwConfigHolder.getName() == null || gwConfigHolder.getName().equals("default")) ?
                        gwConfigHolder.getInboundEndpoint().getName() : gwConfigHolder.getName()) + groupPath);
        gwConfigHolder.
                getPipeline(gwConfigHolder.getGroup(groupPath).getPipeline()).addMediator(lbMediator);

    }

}
