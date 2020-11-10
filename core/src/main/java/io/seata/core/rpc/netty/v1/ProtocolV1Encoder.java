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
package io.seata.core.rpc.netty.v1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.core.serializer.Serializer;
import io.seata.core.compressor.Compressor;
import io.seata.core.compressor.CompressorFactory;
import io.seata.core.protocol.ProtocolConstants;
import io.seata.core.protocol.RpcMessage;
import io.seata.core.serializer.SerializerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * <pre>
 * 0     1     2     3     4     5     6     7     8     9    10     11    12    13    14    15    16
 * +-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+
 * |   magic   |Proto|     Full length       |    Head   | Msg |Seria|Compr|     RequestId         |
 * |   code    |colVer|    (head+body)      |   Length  |Type |lizer|ess  |                       |
 * +-----------+-----------+-----------+-----------+-----------+-----------+-----------+-----------+
 * |                                                                                               |
 * |                                   Head Map [Optional]                                         |
 * +-----------+-----------+-----------+-----------+-----------+-----------+-----------+-----------+
 * |                                                                                               |
 * |                                         body                                                  |
 * |                                                                                               |
 * |                                        ... ...                                                |
 * +-----------------------------------------------------------------------------------------------+
 * </pre>
 * <p>
 * <li>Full Length: include all data </li>
 * <li>Head Length: include head data from magic code to head map. </li>
 * <li>Body Length: Full Length - Head Length</li>
 * </p>
 * https://github.com/seata/seata/issues/893
 *
 * @author Geng Zhang
 * @see ProtocolV1Decoder
 * @since 0.7.0
 */
public class ProtocolV1Encoder extends MessageToByteEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolV1Encoder.class);

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        try {
            if (msg instanceof RpcMessage) {
                RpcMessage rpcMessage = (RpcMessage) msg;

                int fullLength = ProtocolConstants.V1_HEAD_LENGTH;
                int headLength = ProtocolConstants.V1_HEAD_LENGTH;

                byte messageType = rpcMessage.getMessageType();
                // 魔术位2byte: 11011010 11011010
                out.writeBytes(ProtocolConstants.MAGIC_CODE_BYTES);
                // 协议版本1byte: 00000001
                out.writeByte(ProtocolConstants.VERSION);
                // full Length(4B) and head length(2B) will fix in the end.
                // 跳过6byte，后面解析完body知道实际长度后，再将总长度和头长度写入这6byte
                out.writerIndex(out.writerIndex() + 6);
                // 消息类型1byte
                out.writeByte(messageType);
                // body的序列化方式1byte：类型枚举 io.seata.core.serializer.SerializerType，目前支持：SEATA、PROTOBUF、KRYO、FST、HESSIAN
                out.writeByte(rpcMessage.getCodec());
                // 压缩算法1byte：transport.compressor 配置，默认为 none，类型枚举：io.seata.core.compressor.CompressorType，目前支持：NONE、GZIP、ZIP、SEVENZ、BZIP2、LZ4
                out.writeByte(rpcMessage.getCompressor());
                // 当前消息通信ID，从0开始，递增，int类型 io.seata.core.rpc.netty.AbstractNettyRemoting.getNextMessageId
                out.writeInt(rpcMessage.getId());

                // direct write head with zero-copy
                // 消息额外带的map信息，直接操作 ByteBuf，这个是不定长度
                Map<String, String> headMap = rpcMessage.getHeadMap();
                if (headMap != null && !headMap.isEmpty()) {
                    int headMapBytesLength = HeadMapSerializer.getInstance().encode(headMap, out);
                    headLength += headMapBytesLength;
                    fullLength += headMapBytesLength;
                }

                byte[] bodyBytes = null;
                if (messageType != ProtocolConstants.MSGTYPE_HEARTBEAT_REQUEST
                        && messageType != ProtocolConstants.MSGTYPE_HEARTBEAT_RESPONSE) {
                    // heartbeat has no body
                    Serializer serializer = EnhancedServiceLoader.load(Serializer.class, SerializerType.getByCode(rpcMessage.getCodec()).name());
                    bodyBytes = serializer.serialize(rpcMessage.getBody());
                    Compressor compressor = CompressorFactory.getCompressor(rpcMessage.getCompressor());
                    bodyBytes = compressor.compress(bodyBytes);
                    fullLength += bodyBytes.length;
                }
                // 如果是心跳请求/响应，则没有body，否则根据序列化方式进行序列化后写入body，不定长度
                if (bodyBytes != null) {
                    out.writeBytes(bodyBytes);
                }
                // 解析了所有信息后，可以得出总长度和头长度，最后写入之前预留的6byte中
                // fix fullLength and headLength
                int writeIndex = out.writerIndex();
                // skip magic code(2B) + version(1B)
                out.writerIndex(writeIndex - fullLength + 3);
                out.writeInt(fullLength);
                out.writeShort(headLength);
                out.writerIndex(writeIndex);
            } else {
                throw new UnsupportedOperationException("Not support this class:" + msg.getClass());
            }
        } catch (Throwable e) {
            LOGGER.error("Encode request error!", e);
        }
    }
}
