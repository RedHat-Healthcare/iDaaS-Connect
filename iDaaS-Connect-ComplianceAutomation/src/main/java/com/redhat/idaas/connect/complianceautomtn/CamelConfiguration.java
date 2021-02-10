/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.idaas.connect.complianceautomtn;

import javax.jms.ConnectionFactory;

import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.stereotype.Component;

/*
 *  Kafka implementation based on https://camel.apache.org/components/latest/kafka-component.html
 *  JDBC implementation based on https://camel.apache.org/components/latest/dataformats/hl7-dataformat.html
 *  JPA implementayion based on https://camel.apache.org/components/latest/jpa-component.html
 *  File implementation based on https://camel.apache.org/components/latest/file-component.html
 *  FileWatch implementation based on https://camel.apache.org/components/latest/file-watch-component.html
 *  FTP/SFTP and FTPS implementations based on https://camel.apache.org/components/latest/ftp-component.html
 *  JMS implementation based on https://camel.apache.org/components/latest/jms-component.html
 *  JT400 (AS/400) implementation based on https://camel.apache.org/components/latest/jt400-component.html
 *  HTTP implementation based on https://camel.apache.org/components/latest/http-component.html
 *  HDFS implementation based on https://camel.apache.org/components/latest/hdfs-component.html
 *  jBPMN implementation based on https://camel.apache.org/components/latest/jbpm-component.html
 *  MongoDB implementation based on https://camel.apache.org/components/latest/mongodb-component.html
 *  RabbitMQ implementation based on https://camel.apache.org/components/latest/rabbitmq-component.html
 *  There are lots of third party implementations to support cloud storage from Amazon AC2, Box and so forth
 *  There are lots of third party implementations to support cloud for Amazon Cloud Services
 *  Awaiting update to 3.1 for functionality
 *  Apache Kudu implementation
 *  REST API implementations
 */
@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Autowired
  private ConfigProperties config;

  @Bean
  private KafkaEndpoint kafkaEndpoint(){
    KafkaEndpoint kafkaEndpoint = new KafkaEndpoint();
    return kafkaEndpoint;
  }
  @Bean
  private KafkaComponent kafkaComponent(KafkaEndpoint kafkaEndpoint){
    KafkaComponent kafka = new KafkaComponent();
    return kafka;
  }

  private String getKafkaTopicUri(String topic) {
    return "kafka:" + topic +
            "?brokers=" +
            config.getKafkaBrokers();
  }

  @Override
  public void configure() throws Exception {

    /*
     *  Direct actions used across platform
     *
     */
    from("direct:auditing")
        .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("messagetrigger").exchangeProperty("messagetrigger")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("auditdetails").exchangeProperty("auditdetails")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        .convertBodyTo(String.class).to(getKafkaTopicUri("opsmgmt_platformtransactions"))
    ;
    /*
     *  Logging
     */
    from("direct:logging")
        .log(LoggingLevel.INFO, log, "Transaction Message: [${body}]")
    ;
    /*
    *  Kafka Implementation for implementing Third Party FHIR Server direct connection
    */

    // Sample Using Kafka Topic
    // FHIR: Adverse Events
/*    from(getKafkaTopicUri("fhirsvr_adverseevent"))
        .routeId("AdverseEvent-MiddleTier")
        // Auditing
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AdverseEvent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("MTier")
        .setProperty("auditdetails").constant("Adverse Event to Enterprise By Data Type middle tier")
        //.wireTap("direct:auditing")
        // Enterprise Message By Type
        .convertBodyTo(String.class).to(getKafkaTopicUri("ent_fhirsvr_adverseevent"))*/
    ;

  }
}
