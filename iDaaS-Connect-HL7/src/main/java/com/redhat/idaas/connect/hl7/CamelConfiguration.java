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
package com.redhat.idaas.connect.hl7;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7;
import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
//import org.springframework.jms.connection.JmsTransactionManager;
//import javax.jms.ConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Autowired
  private ConfigProperties config;

  @Bean
  private HL7MLLPNettyEncoderFactory hl7Encoder() {
    HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
    encoder.setCharset("iso-8859-1");
    //encoder.setConvertLFtoCR(true);
    return encoder;
  }
  @Bean
  private HL7MLLPNettyDecoderFactory hl7Decoder() {
    HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
    decoder.setCharset("iso-8859-1");
    return decoder;
  }
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
  @Bean
  ServletRegistrationBean camelServlet() {
    // use a @Bean to register the Camel servlet which we need to do
    // because we want to use the camel-servlet component for the Camel REST service
    ServletRegistrationBean mapping = new ServletRegistrationBean();
    mapping.setName("CamelServlet");
    mapping.setLoadOnStartup(1);
    mapping.setServlet(new CamelHttpTransportServlet());
    mapping.addUrlMappings("/iDaaS/*");
    return mapping;
  }

  private String getKafkaTopicUri(String topic) {
    return "kafka:" + topic +
            "?brokers=" +
            config.getKafkaBrokers();
  }
  private String getHL7Uri(int port) {
    String s = "netty4:tcp://0.0.0.0:" + port + "?sync=true&decoder=#hl7Decoder&encoder=#hl7Encoder";
    return s;
  }

  private String getHL7UriDirectory(String dirName) {
    return "file:src/" + dirName + "?delete=true?noop=true";
  }

  /*
   * Kafka implementation based upon https://camel.apache.org/components/latest/kafka-component.html
   *
   */
  @Override
  public void configure() throws Exception {

    /*
     *   HIDN
     *   HIDN - Health information Data Network
     *   Intended to enable simple movement of data aside from specific standards
     *   Common Use Cases are areas to support remote (iOT/Edge) and any other need for small footprints to larger
     *   footprints
     * : Unstructured data, st
     */
    from("direct:hidn")
        .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("eventdate").simple("eventdate")
        .setHeader("eventtime").simple("eventtime")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("organization").exchangeProperty("organization")
        .setHeader("careentity").exchangeProperty("careentity")
        .setHeader("customattribute1").exchangeProperty("customattribute1")
        .setHeader("customattribute2").exchangeProperty("customattribute2")
        .setHeader("customattribute3").exchangeProperty("customattribute3")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        .setHeader("bodySize").exchangeProperty("bodySize")
        .convertBodyTo(String.class).to(getKafkaTopicUri("hidn"))
    ;

    /*
     * Audit
     *
     * Direct component within platform to ensure we can centralize logic
     * There are some values we will need to set within every route
     * We are doing this to ensure we dont need to build a series of beans
     * and we keep the processing as lightweight as possible
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
        .log(LoggingLevel.INFO, log, "HL7 Message: [${body}]")
        //To invoke Logging
        //.to("direct:logging")
    ;

    /*
     *  Servlet common endpoint accessable to process transactions
     */
    from("servlet://hidn")
        .routeId("HIDN")
        // Data Parsing and Conversions
        // Normal Processing
        .convertBodyTo(String.class)
        .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("eventdate").simple("eventdate")
        .setHeader("eventtime").simple("eventtime")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("organization").exchangeProperty("organization")
        .setHeader("careentity").exchangeProperty("careentity")
        .setHeader("customattribute1").exchangeProperty("customattribute1")
        .setHeader("customattribute2").exchangeProperty("customattribute2")
        .setHeader("customattribute3").exchangeProperty("customattribute3")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        .setHeader("bodySize").exchangeProperty("bodySize")
        .wireTap("direct:hidn")
    ;

    /*
	 *
	 * HL7 v2x Server Implementations
	 *  ------------------------------
	 *  HL7 implementation based upon https://camel.apache.org/components/latest/dataformats/hl7-dataformat.html
	 *  For leveraging HL7 based files:
	 *  from("file:src/data-in/hl7v2/adt?delete=true?noop=true")
	 *
     *   Simple language reference
     *   https://camel.apache.org/components/latest/languages/simple-language.html
     *
     */
     // HL7 File Based Implementation
     // ADT
    from(getHL7UriDirectory(config.getHl7ADT_Directory()))
        .routeId("hl7FileBasedAdmissions")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("ADT")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("ADT message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_adt"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.adtTopicName}}")))
    ;
    // ORM
    from(getHL7UriDirectory(config.getHl7ORM_Directory()))
        .routeId("hl7FileBasedOrders")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("ORM")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("ORM message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.ormTopicName}}")))
    ;
    // ORU
    from(getHL7UriDirectory(config.getHl7ORU_Directory()))
        .routeId("hl7FileBasedResults")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("ORU")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("ORU message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.oruTopicName}}")))
    ;
    // MFN
    from(getHL7UriDirectory(config.getHl7MFN_Directory()))
        .routeId("hl7FileBasedMasterFiles")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("MFN")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("MFN message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.mfnTopicName}}")))
    ;
    // MDM
    from(getHL7UriDirectory(config.getHl7MDM_Directory()))
         .routeId("hl7FileBasedMasterDocs")
         .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-HL7")
         .setProperty("industrystd").constant("HL7")
         .setProperty("messagetrigger").constant("MDM")
         .setProperty("componentname").simple("${routeId}")
         .setProperty("processname").constant("Input")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("auditdetails").constant("MDM message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send to Topic
         //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
         .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.mdmTopicName}}")))
    ;
    // RDE
    from(getHL7UriDirectory(config.getHl7RDE_Directory()))
        .routeId("hl7FilePharmacy")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("RDE")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("RDE message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.rdeTopicName}}")))
    ;
    // SCH
    from(getHL7UriDirectory(config.getHl7RDE_Directory()))
        .routeId("hl7FileSchedules")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-HL7")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("SCH")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("SCH message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.schTopicName}}")))
    ;
    // VXU
    from(getHL7UriDirectory(config.getHl7VXU_Directory()))
         .routeId("hl7FileVaccinations")
         .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-HL7")
         .setProperty("industrystd").constant("HL7")
         .setProperty("messagetrigger").constant("VXU")
         .setProperty("componentname").simple("${routeId}")
         .setProperty("processname").constant("Input")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("auditdetails").constant("VXU message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send to Topic
         //.convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
         .convertBodyTo(String.class).to(getKafkaTopicUri(simple("{{idaas.vxuTopicName}}")))
    ;

	  // HL7 Server Sockets
      // ADT
	  from(getHL7Uri(config.getAdtPort()))
          .routeId("hl7Admissions")
          .convertBodyTo(String.class)
          // set Auditing Properties
          .setProperty("processingtype").constant("data")
          .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
          .setProperty("industrystd").constant("HL7")
          .setProperty("messagetrigger").constant("ADT")
          .setProperty("componentname").simple("${routeId}")
          .setProperty("processname").constant("Input")
          .setProperty("camelID").simple("${camelId}")
          .setProperty("exchangeID").simple("${exchangeId}")
          .setProperty("internalMsgID").simple("${id}")
          .setProperty("bodyData").simple("${body}")
          .setProperty("auditdetails").constant("ADT message received")
          // iDAAS DataHub Processing
          .wireTap("direct:auditing")
          // Send to Topic
          .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_adt"))
          //Response to HL7 Message Sent Built by platform
          .choice().when(simple("{{idaas.adtACKResponse}}"))
              .transform(HL7.ack())
              // This would enable persistence of the ACK
              .convertBodyTo(String.class)
              .setProperty("bodyData").simple("${body}")
              .setProperty("processingtype").constant("data")
              .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
              .setProperty("industrystd").constant("HL7")
              .setProperty("messagetrigger").constant("ADT")
              .setProperty("componentname").simple("${routeId}")
              .setProperty("camelID").simple("${camelId}")
              .setProperty("exchangeID").simple("${exchangeId}")
              .setProperty("internalMsgID").simple("${id}")
              .setProperty("processname").constant("Input")
              .setProperty("auditdetails").constant("ACK Processed")
              // iDAAS DataHub Processing
              .wireTap("direct:auditing")
          .endChoice()
    ;

    // ORM
    from(getHL7Uri(config.getOrmPort()))
        .routeId("hl7Orders")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("ORM")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("ORM message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_orm"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.ormACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("ORM")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;

    // ORU
    from(getHL7Uri(config.getOruPort()))
        .routeId("hl7Results")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("ORU")
        .setProperty("componentname").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("ORU message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_oru"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.oruACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("ORU")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;

    // RDE
    from(getHL7Uri(config.getRdePort()))
        .routeId("hl7Pharmacy")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("RDE")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("RDE message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_rde"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.rdeACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("RDE")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;

    // MFN
    from(getHL7Uri(config.getMfnPort()))
        .routeId("hl7MasterFiles")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("MFN")
        .setProperty("component").simple("{$routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("MFN message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send to Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_mfn"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.mfnACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("MFN")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;

    // MDM
    from(getHL7Uri(config.getMdmPort()))
        .routeId("hl7MasterDocs")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("MDM")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("MDM message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        //Send To Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_mdm"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.mdmACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("MDM")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
       .endChoice()
    ;

    // SCH
    from(getHL7Uri(config.getSchPort()))
        .routeId("hl7Schedule")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("SCH")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("SCH message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_sch"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.schACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("SCH")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;

    // VXU
    from(getHL7Uri(config.getVxuPort()))
        .routeId("hl7Vaccination")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("HL7")
        .setProperty("messagetrigger").constant("VXU")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("VXU message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to(getKafkaTopicUri("mctn_mms_vxu"))
        //Response to HL7 Message Sent Built by platform
        .choice().when(simple("{{idaas.vxuACKResponse}}"))
            .transform(HL7.ack())
            // This would enable persistence of the ACK
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
            .setProperty("industrystd").constant("HL7")
            .setProperty("messagetrigger").constant("VXU")
            .setProperty("componentname").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("ACK Processed")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
        .endChoice()
    ;
  }


}