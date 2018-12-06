/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.io.sqs.sink;

import org.wso2.extension.siddhi.io.sqs.api.SQSBuilder;
import org.wso2.extension.siddhi.io.sqs.util.SQSConstants;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.stream.output.sink.Sink;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.DynamicOptions;
import org.wso2.siddhi.core.util.transport.OptionHolder;
import org.wso2.siddhi.query.api.definition.StreamDefinition;
import org.wso2.siddhi.query.api.exception.SiddhiAppValidationException;

import java.util.Map;

/**
 * SQS Sink Extension
 */

@Extension(
        name = "SQS",
        namespace = "sink",
        description = "The SQS sink allows users to connect and publish messages to an AWS SQS queue. This sink" +
                " publishes messages only in the 'text' format.",
        parameters = {
                @Parameter(
                        name = SQSConstants.QUEUE_URL_NAME,
                        description = "The URL of the queue to which the SQS Sink should connect.",
                        type = DataType.STRING
                ),
                @Parameter(
                        name = SQSConstants.ACCESS_KEY_NAME,
                        description = "The access key for the Amazon Web Services. It is required to specify an " +
                                "access key either in the '<SP_HOME>/conf/<PROFILE>/deployment.yaml' file or in the" +
                                " sink definition itself.",
                        type = DataType.STRING,
                        optional = true,
                        defaultValue = "none"
                ),
                @Parameter(
                        name = SQSConstants.SECRET_KEY_NAME,
                        description = "The secret key of the Amazon User. It is required to specify a secret key " +
                                "either in the '<SP_HOME>/conf/<PROFILE>/deployment.yaml' file or in the sink " +
                                "definition itself.",
                        type = DataType.STRING,
                        optional = true,
                        defaultValue = "none"
                ),
                @Parameter(
                        name = SQSConstants.REGION_NAME,
                        description = "The region of the Amazon Web Service.",
                        type = DataType.STRING
                ),
                @Parameter(
                        name = SQSConstants.MESSAGE_GROUP_ID_NAME,
                        description = "The ID of the group to which the message belongs. This is only applicable for" +
                                " FIFO queues)",
                        type = DataType.STRING,
                        optional = true,
                        dynamic = true,
                        defaultValue = "null"
                ),
                @Parameter(
                        name = SQSConstants.DEDUPLICATION_ID_NAME,
                        description = "The ID by which a FIFO queue identifies the duplication in the queue. This is" +
                                " only applicable only for FIFO queues.)",
                        type = DataType.STRING,
                        optional = true,
                        dynamic = true,
                        defaultValue = "null"
                ),
                @Parameter(
                        name = SQSConstants.DELAY_INTERVAL_NAME,
                        description = "The number of seconds the message remains in the queue before it is " +
                                "available to the consumers.",
                        type = DataType.INT,
                        optional = true,
                        defaultValue = "" + SQSConstants.DEFAULT_DELAY_INTERVAL
                )
        },
        examples = {
                @Example(
                        syntax = "@sink(type='sqs'," +
                                "queue='<queue_url>'," +
                                "access.key='<aws_access_key>'," +
                                "secret.key='<aws_secret_key>'," +
                                "region='<region>'," +
                                "delay.interval='5'," +
                                "deduplication.id='{{deduplicationID}}'," +
                                "message.group.id='charuka',@map(type='xml') )" +
                                "define stream outStream(symbol string, deduplicationID string);",
                        description = "This example shows how to define an SQS sink to publish messages to " +
                                "the service."
                )
        }
)

// for more information refer https://wso2.github.io/siddhi/documentation/siddhi-4.0/#sinks

public class SQSSink extends Sink {
    private SQSSinkConfig sinkConfig;
    private SQSMessagePublisher sqsMessagePublisher;
    private OptionHolder optionHolder;

    /**
     * Returns the list of classes which this sink can consume.
     * Based on the type of the sink, it may be limited to being able to publish specific type of classes.
     * For example, a sink of type file can only write objects of type String .
     *
     * @return array of supported classes , if extension can support of any types of classes
     * then return empty array .
     */
    @Override
    public Class[] getSupportedInputEventClasses() {
        return new Class[] {String.class};
    }

    /**
     * Returns a list of supported dynamic options (that means for each event value of the option can change) by
     * the transport
     *
     * @return the list of supported dynamic option keys
     */
    @Override
    public String[] getSupportedDynamicOptions() {
        return new String[] {SQSConstants.MESSAGE_GROUP_ID_NAME, SQSConstants.DEDUPLICATION_ID_NAME};
    }

    /**
     * The initialization method for {@link Sink}, will be called before other methods. It used to validate
     * all configurations and to get initial values.
     *
     * @param streamDefinition containing stream definition bind to the {@link Sink}
     * @param optionHolder     Option holder containing static and dynamic configuration related
     *                         to the {@link Sink}
     * @param configReader     to read the sink related system configuration.
     * @param siddhiAppContext the context of the {@link org.wso2.siddhi.query.api.SiddhiApp} used to
     *                         get siddhi related utility functions.
     */
    @Override
    protected void init(StreamDefinition streamDefinition, OptionHolder optionHolder, ConfigReader configReader,
                        SiddhiAppContext siddhiAppContext) {
        this.sinkConfig = new SQSSinkConfig(optionHolder);
        this.optionHolder = optionHolder;
        this.sqsMessagePublisher = null;

        if (this.sinkConfig.getAccessKey() == null || sinkConfig.getAccessKey().isEmpty()) {
            this.sinkConfig.setAccessKey(configReader.readConfig(SQSConstants.ACCESS_KEY_NAME, null));
        }

        if (this.sinkConfig.getSecretKey() == null || sinkConfig.getSecretKey().isEmpty()) {
            this.sinkConfig.setSecretKey(configReader.readConfig(SQSConstants.SECRET_KEY_NAME, null));
        }

        if (sinkConfig.getAccessKey() == null || sinkConfig.getSecretKey() == null ||
                sinkConfig.getAccessKey().isEmpty() || sinkConfig.getSecretKey().isEmpty()) {
            throw new SiddhiAppValidationException("Access key and Secret key are mandatory parameters for" +
                    " the SQS client");
        }
    }

    /**
     * This method will be called when events need to be published via this sink
     *
     * @param payload        payload of the event based on the supported event class exported by the extensions
     * @param dynamicOptions holds the dynamic options of this sink and Use this object to obtain dynamic options.
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     */
    @Override
    public void publish(Object payload, DynamicOptions dynamicOptions) throws ConnectionUnavailableException {
        sqsMessagePublisher.sendMessageRequest(payload, dynamicOptions);
    }

    /**
     * This method will be called before the processing method.
     * Intention to establish connection to publish event.
     *
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     */
    @Override
    public void connect() throws ConnectionUnavailableException {
        sqsMessagePublisher = new SQSBuilder(sinkConfig)
                .buildSinkPublisher(optionHolder, checkFIFO(sinkConfig.getQueueUrl()));
    }

    /**
     * Called after all publishing is done, or when {@link ConnectionUnavailableException} is thrown
     * Implementation of this method should contain the steps needed to disconnect from the sink.
     */
    @Override
    public void disconnect() {
        // client uses a rest api
    }

    /**
     * The method can be called when removing an event receiver.
     * The cleanups that have to be done after removing the receiver could be done here.
     */
    @Override
    public void destroy() {
        // client uses a rest api
    }

    /**
     * Used to collect the serializable state of the processing element, that need to be
     * persisted for reconstructing the element to the same state on a different point of time
     * This is also used to identify the internal states and debugging
     *
     * @return all internal states should be return as an map with meaning full keys
     */
    @Override
    public Map<String, Object> currentState() {
        return null;
    }

    /**
     * Used to restore serialized state of the processing element, for reconstructing
     * the element to the same state as if was on a previous point of time.
     *
     * @param map the stateful objects of the processing element as a map.
     *            This map will have the  same keys that is created upon calling currentState() method.
     */
    @Override
    public void restoreState(Map<String, Object> map) {
        // no state.
    }

    private boolean checkFIFO(String queueURL) {
        return (queueURL.endsWith(".fifo") ||
                queueURL.substring(0, queueURL.length() - 1).endsWith(".fifo"));
    }
}

