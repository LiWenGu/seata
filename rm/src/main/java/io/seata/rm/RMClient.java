/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm;

import io.seata.core.rpc.netty.RmNettyRemotingClient;

/**
 * The Rm client Initiator.
 *
 * @author slievrly
 */
public class RMClient {

    /**
     * Init.
     *
     * @param applicationId           the application id
     * @param transactionServiceGroup the transaction service group
     */
    public static void init(String applicationId, String transactionServiceGroup) {
        RmNettyRemotingClient rmNettyRemotingClient = RmNettyRemotingClient.getInstance(applicationId, transactionServiceGroup);
        // 支持 AT TCC SAGA XA 四种 reousrce manager，
        // 其中SPI接口为：io.seata.core.model.ResourceManager。AT和XA在rm-datasource模块定义，saga在seata-saga-rm模块定义，tcc在seata-tcc模块定义
        rmNettyRemotingClient.setResourceManager(DefaultResourceManager.get());
        // 其中SPI接口为：io.seata.rm.AbstractRMHandler。支持 AT TCC SAGA XA 四种 rmhandle，具体定义位置和 resourceManager一样
        rmNettyRemotingClient.setTransactionMessageHandler(DefaultRMHandler.get());
        rmNettyRemotingClient.init();
    }

}
