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

package org.wso2.carbon.gateway.httploadbalancer.constants;

import java.util.concurrent.TimeUnit;

/**
 * Constants for Load Balancer.
 */
public class LoadBalancerConstants {

    //public static final String USER_AGENT = "carbon-gw-LB";

    /**
     * Config Keys.
     */
    public static final String ENDPOINTS = "endpoints";
    public static final String ALGORITHM_NAME = "algorithmName";
    public static final String PERSISTENCE_TYPE = "persistenceType";

    // As of now, only passive is supported.
    public static final String HEALTH_CHECK_TYPE = "healthCheckType";

    // Amount of time for which LB has to wait for response from OutboundEndpoint.
    public static final String HEALTH_CHECK_REQUEST_TIMEOUT = "requestTimeout";

    // no of request failure or timeouts to mark an OutboundEndpoint as unhealthy.
    public static final String HEALTH_CHECK_UNHEALTHY_RETRIES = "unHealthyRetries";

    // no of successful responses to be received from an OutboundEndpoint to mark it as healthy again.
    public static final String HEALTH_CHECK_HEALTHY_RETRIES = "healthyRetries";

    // Scheduled time interval after which LB has to check if an OutboundEndpoint is healthy again.
    public static final String HEALTH_CHECK_HEALTHY_CHECK_INTERVAL = "healthyCheckInterval";



    /**
     * LB Algorithm related Constants.
     */

    public static final String ROUND_ROBIN = "ROUND_ROBIN";

    //Persistence based on Client's IP address.
    //If client IP address is not available, NO ENDPOINT will be chosen.
    //If this algorithm is chosen, on should choose NO_PERSISTENCE as persistence policy.
    //Because, it has implicit session handling.
    public static final String STRICT_IP_HASHING = "STRICT_IP_HASHING";

    /**
     * Common HTTP Request Headers related to Client IP address.
     * These are according HTTP Specification.
     * <p>
     * If you want to support more Client IP related headers, kindly create a constant here and
     * also make appropriate changes in getClientIP() method available in CommonUtil Class.
     */
    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String CLIENT_IP_HEADER = "Client-IP";
    public static final String REMOTE_ADDR_HEADER = "Remote-Addr";

    //This value will be used in ConsistentHash algorithm.
    public static final int REPLICATION_FACTOR = 1;


    public static final String LEAST_RESPONSE_TIME = "LEAST_RESPONSE_TIME";

    public static final String RANDOM = "RANDOM";

    public static final String WEIGHTED_ROUND_ROBIN = "WEIGHTED_ROUND_ROBIN";

    public static final String WEIGHTED_RANDOM = "WEIGHTED_RANDOM";



    /**
     * Session Persistence related Constants.
     */
    //No persistence will be maintained.
    public static final String NO_PERSISTENCE = "NO_PERSISTENCE";

    //Session Timeout is handled by application cookie.
    //If application cookie is not available in server's response,
    //LB will insert its own cookie to maintain persistence.
    //If cookie is not available from client side, endpoint will be chosen based on algorithm.
    public static final String APPLICATION_COOKIE = "APPLICATION_COOKIE";

    //Session Timeout is handled by LB_COOKIE.
    // Use this mode ONLY if application doesn't use its own cookies.
    // If there is any cookie available in server's response it will be discarded.
    //If cookie is not available from client side, endpoint will be chosen based on algorithm.
    public static final String LB_COOKIE = "LB_COOKIE";

    //Persistence based on Client's IP address.
    //If client IP address is not available, endpoint will be chosen based on algorithm.
    public static final String CLIENT_IP_HASHING = "CLIENT_IP_HASHING";



    //This will be used as cookie name.
    public static final String LB_COOKIE_NAME = "LB_COOKIE";


    //These will be used to write cookie in response header.
    public static final String SET_COOKIE_HEADER = "Set-Cookie"; //RFC 2109.
    public static final String SET_COOKIE2_HEADER = "Set-Cookie2"; //RFC 2965.

    //This will be used to read cookie if any from request header.
    public static final String COOKIE_HEADER = "Cookie";

    //This will be used as delimiter between Existing cookies & LB_COOKIE.
    public static final String LB_COOKIE_DELIMITER = "---";

    // eg: LB_COOKIE:EP1.
    public static final String COOKIE_NAME_VALUE_SEPARATOR = ":";

    //Cookie HttpOnly.
    public static final String HTTP_ONLY = "HttpOnly";

    //Cookie Secure.
    public static final String SECURE = "secure";


    /**
     * Health Checking related Constants.
     */
    public static final String ACTIVE_HEALTH_CHECK = "ACTIVE";
    public static final String PASSIVE_HEALTH_CHECK = "PASSIVE";
    public static final String NO_HEALTH_CHECK = "NO_HEALTH_CHECK";
    public static final String DEFAULT_HEALTH_CHECK = "DEFAULT_HEALTH_CHECK";

    /**
     * TODO: Check these values with mentor. Should there be specific values based on type..?
     */
    public static final int MAX_TIMEOUT_VAL = (int) TimeUnit.HOURS.toMillis(5); //5 hours.

    public static final int DEFAULT_REQ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(5); //5 sec.

    public static final int DEFAULT_RETRIES = 3;

    //Time interval to be elapsed after which, LB has to check whether an endpoint is back to healthy.
    public static final int DEFAULT_HEALTHY_CHECK_INTERVAL = (int) TimeUnit.MINUTES.toMillis(1); //1 mins.

    //Scheduled time Interval to execute TimeoutHandler.
    public static final int DEFAULT_TIMEOUT_TIMER_PERIOD = (int) TimeUnit.SECONDS.toMillis(10); //10 sec

    //Default grace period to be added to timeOut value (in milliseconds) while creating LBMediatorCallBack.
    public static final int DEFAULT_GRACE_PERIOD = 5; //5 ms

    //This value will be used in ACTIVE and PASSIVE health check handlers
    public static final int DEFAULT_CONN_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(5); //5 sec

    //TODO: Get IP address from carbon server. Check with mentor.
   // public static final String LB_IP_ADDR = "127.0.0.1";


}
