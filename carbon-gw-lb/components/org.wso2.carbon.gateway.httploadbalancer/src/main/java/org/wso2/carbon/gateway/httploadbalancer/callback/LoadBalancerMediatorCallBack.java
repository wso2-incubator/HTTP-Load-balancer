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

package org.wso2.carbon.gateway.httploadbalancer.callback;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.flow.Mediator;
import org.wso2.carbon.gateway.httploadbalancer.algorithm.simple.LeastResponseTime;
import org.wso2.carbon.gateway.httploadbalancer.constants.LoadBalancerConstants;
import org.wso2.carbon.gateway.httploadbalancer.context.LoadBalancerConfigContext;
import org.wso2.carbon.gateway.httploadbalancer.outbound.LBOutboundEndpoint;
import org.wso2.carbon.gateway.httploadbalancer.utils.CommonUtil;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.Constants;

import java.util.concurrent.TimeUnit;


/**
 * Callback related to LoadBalancerMediator.
 * In case of cookie persistence, appropriate cookie will be appended with response here.
 */
public class LoadBalancerMediatorCallBack implements CarbonCallback {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerMediatorCallBack.class);

    //Incoming callback.
    private final CarbonCallback parentCallback;

    //LoadBalancer Mediator.
    private final Mediator mediator;

    //LoadBalancerConfigContext context.
    private final LoadBalancerConfigContext context;

    //LBOutboundEndpoint.
    //This will be used to locate specific lbOutboundEndpoint for healthChecking purposes.
    private final LBOutboundEndpoint lbOutboundEndpoint;

    //Time in milli seconds at which request has been made.
    private final long createdTime;

    public long getCreatedTime() {

        return this.createdTime;
    }

    public LBOutboundEndpoint getLbOutboundEndpoint() {

        return this.lbOutboundEndpoint;
    }


    public CarbonCallback getParentCallback() {

        return this.parentCallback;
    }

    private long getCurrentTime() {

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    /**
     * Constructor.
     *
     * @param parentCallback CarbonCallback.
     * @param mediator       LoadBalancerMediator.
     */
    public LoadBalancerMediatorCallBack(CarbonCallback parentCallback, Mediator mediator,
                                        LoadBalancerConfigContext context, LBOutboundEndpoint lbOutboundEndpoint) {


        this.parentCallback = parentCallback;
        this.mediator = mediator;
        this.lbOutboundEndpoint = lbOutboundEndpoint;
        this.context = context;
        // Note that we are assigning scheduled value way ahead before invoking outboundEndpoint.
        // this will be atleast 2 to 5 milli second difference, which might cause removal of
        // object from pool before response arrives. So we are adding a grace period of 5 ms time to it.
        this.createdTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());


    }


    @Override
    public void done(CarbonMessage carbonMessage) {


        /**
         log.info("Inside LB call back mediator...");

         log.info("Transport Headers...");
         log.info(carbonMessage.getHeaders().toString());

         log.info("Properties...");
         log.info(carbonMessage.getProperties().toString());
         **/

        if (parentCallback instanceof LoadBalancerMediatorCallBack) {

            //Locking is not required as we are operating on ConcurrentHashMap.
            if (this.context.isInCallBackPool((LoadBalancerMediatorCallBack)
                    carbonMessage.getProperty(Constants.CALL_BACK))) {

                LoadBalancerMediatorCallBack callBack = (LoadBalancerMediatorCallBack)
                        carbonMessage.getProperty(Constants.CALL_BACK);

                this.context.removeFromCallBackPool(callBack);
                //From this point, this callback will not be available in pool.

                /**
                 * Resetting unHealthyRetries count, to avoid any false detection.
                 */
                if (!context.getHealthCheck().equals(LoadBalancerConstants.NO_HEALTH_CHECK)
                        && callBack.getLbOutboundEndpoint().isHealthy()
                        && callBack.getLbOutboundEndpoint().getUnHealthyRetriesCount() > 0) {
                    callBack.getLbOutboundEndpoint().resetUnhealthyRetriesCount();
                }


                //We are using running average technique for Least Response Time algorithm.
                //So, we are doing this.
                if (context.getAlgorithmName().equals(LoadBalancerConstants.LEAST_RESPONSE_TIME)) {

                    ((LeastResponseTime) context.getLoadBalancingAlgorithm()).
                            setAvgResponseTime(callBack.getLbOutboundEndpoint(), (int)
                                    (this.getCurrentTime() - callBack.getCreatedTime()));

                }

            } else {
                log.error("Response received after removing callback from pool.." +
                        "This response will be discarded. ");
                return;
            }


            if (this.context.getPersistence().equals(LoadBalancerConstants.APPLICATION_COOKIE)) {


                /**Checking if there is any cookie already available in response from BE. **/

                if (carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE_HEADER) != null ||
                        carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE2_HEADER) != null) {
                    //Cookie exists.

                    //Appending LB_COOKIE along with existing cookie.
                    carbonMessage.setHeader(
                            (//Appending to appropriate header that is present in response from BE.
                                    carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE_HEADER) != null ?
                                            LoadBalancerConstants.SET_COOKIE_HEADER :
                                            LoadBalancerConstants.SET_COOKIE2_HEADER),
                            //NOTE: Here we are appending our cooke to existing cookie's value from BE.
                            CommonUtil.addLBCookieToExistingCookie(
                                    carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE_HEADER),
                                    CommonUtil.getCookieValue(carbonMessage, this.context)));

                } else { //There is no cookie in response from BE.

                    //Adding LB specific cookie.
                    log.error("BE endpoint doesn't has it's own cookie. LB will insert it's own cookie for " +
                            "the sake of maintaining persistence. ");

                    //Here we are not looking for Set-Cookie2 header coz, it is only for LB purpose.
                    //i.e., we are only inserting cookie. So using Set-Cookie itself.
                    carbonMessage.setHeader(LoadBalancerConstants.SET_COOKIE_HEADER,
                            CommonUtil.getSessionCookie(//here we are finding endpoint to insert appropriate cookie.
                                    CommonUtil.getCookieValue(carbonMessage, this.context)));

                }

            } else if (this.context.getPersistence().equals(LoadBalancerConstants.LB_COOKIE)) {


                if (carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE_HEADER) != null ||
                        carbonMessage.getHeader(LoadBalancerConstants.SET_COOKIE2_HEADER) != null) {
                    //Cookie exists.

                    log.error("BE endpoint has it's own cookie, LB will DISCARD this. If" +
                            "you want your application cookie to be used, please choose " +
                            LoadBalancerConstants.APPLICATION_COOKIE +
                            " mode of persistence");
                }

                //Adding LB specific cookie.
                carbonMessage.setHeader(LoadBalancerConstants.SET_COOKIE_HEADER,
                        CommonUtil.getSessionCookie(//here we are finding endpoint to insert appropriate cookie.
                                CommonUtil.getCookieValue(carbonMessage, this.context)));

            }

            //In case of NO_PERSISTENCE and CLIENT_IP_HASHING type persistence type, we need
            // not add anything to the response. So we'll simply send it back.
            // For other type of persistence cases, necessary changes are made.
            parentCallback.done(carbonMessage);


        } else if (mediator.hasNext()) { // If Mediator has a sibling after this

            try {
                mediator.next(carbonMessage, parentCallback);
            } catch (Exception e) {
                log.error("Error while mediating from Callback", e);
                try {
                    CommonUtil.sendErrorResponse(parentCallback, true);
                } catch (Exception e1) {
                    log.error(e1.toString());
                }
            }
        }


    }
}
