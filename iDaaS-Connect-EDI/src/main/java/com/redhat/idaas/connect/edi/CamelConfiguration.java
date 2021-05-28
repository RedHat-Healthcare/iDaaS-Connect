package com.redhat.idaas.connect.edi;

import java.util.ArrayList;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/*
 *
 * General Links
 * https://camel.apache.org/components/latest/eips/split-eip.html
 * Basic Links for Implementations
 * Kafka implementation based on
 * https://camel.apache.org/components/latest/kafka-component.html JDBC
 * implementation based on
 * https://camel.apache.org/components/latest/dataformats/hl7-dataformat.html
 * JPA implementayion based on
 * https://camel.apache.org/components/latest/jpa-component.html File
 * implementation based on
 * https://camel.apache.org/components/latest/file-component.html FileWatch
 * implementation based on
 * https://camel.apache.org/components/latest/file-watch-component.html FTP/SFTP
 * and FTPS implementations based on
 * https://camel.apache.org/components/latest/ftp-component.html JMS
 * implementation based on
 * https://camel.apache.org/components/latest/jms-component.html JT400 (AS/400)
 * implementation based on
 * https://camel.apache.org/components/latest/jt400-component.html HTTP
 * implementation based on
 * https://camel.apache.org/components/latest/http-component.html HDFS
 * implementation based on
 * https://camel.apache.org/components/latest/hdfs-component.html jBPMN
 * implementation based on
 * https://camel.apache.org/components/latest/jbpm-component.html MongoDB
 * implementation based on
 * https://camel.apache.org/components/latest/mongodb-component.html RabbitMQ
 * implementation based on
 * https://camel.apache.org/components/latest/rabbitmq-component.html There are
 * lots of third party implementations to support cloud storage from Amazon AC2,
 * Box and so forth There are lots of third party implementations to support
 * cloud for Amazon Cloud Services Awaiting update to 3.1 for functionality
 * Apache Kudu implementation REST API implementations
 */

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Autowired
  private ConfigProperties config;

  @Bean
  private KafkaEndpoint kafkaEndpoint() {
    KafkaEndpoint kafkaEndpoint = new KafkaEndpoint();
    return kafkaEndpoint;
  }

  @Bean
  private KafkaComponent kafkaComponent(KafkaEndpoint kafkaEndpoint) {
    KafkaComponent kafka = new KafkaComponent();
    return kafka;
  }

  private String getKafkaTopicUri(String topic) {
    return "kafka:" + topic + "?brokers=" + config.getKafkaBrokers();
  }

  @Override
  public void configure() throws Exception {

    /*
     *   HIDN
     *   HIDN - Health information Data Network
     *   Intended to enable simple movement of data aside from specific standards
     *   Common Use Cases are areas to support remote (iOT/Edge) and any other need for small footprints to larger
     *   footprints
     *
     */
    from("direct:hidn")
        .routeId("HIDN Processing")
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
     * Direct actions used across platform
     *
     */
    from("direct:auditing")
        .routeId("KIC-KnowledgeInsightConformance")
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
        .convertBodyTo(String.class).to(getKafkaTopicUri("opsmgmt_platformtransactions"));
    /*
     * Direct Logging
     */
    from("direct:logging")
        .routeId("Logging")
        .log(LoggingLevel.INFO, log, "Transaction Message: [${body}]");


    /*
     *  Servlet common endpoint accessable to process transactions
     */
    from("servlet://hidn")
        .routeId("HIDN Servlet")
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
     *  Sample: CSV ETL Process to Topic and MySQL
     *  Covid John Hopkins Data
     */
    //from("file:{{covid.reporting.directory}}/?fileName={{covid.reporting.extension}}")
    from("file:{{270.inputdirectory}}/")
        .routeId("270-EDI-File")
        .choice()
          .when(simple("${file:ext} == 'edi'"))
          .to(getKafkaTopicUri("edi_270"))
        .to("file:{{output.directory}}/");

    from("file:{{271.inputdirectory}}/")
        .routeId("271-EDI-File")
        .choice()
          .when(simple("${file:ext} == 'edi'"))
          .to(getKafkaTopicUri("edi_271"))
        .to("file:{{output.directory}}/");

    from("file:{{276.inputdirectory}}/")
        .routeId("276-EDI-File")
        .choice()
            .when(simple("${file:ext} == 'edi'"))
            .to(getKafkaTopicUri("edi_276"))
        .to("file:{{output.directory}}/");

    from("file:{{277.inputdirectory}}/")
        .routeId("277-EDI-File")
        .choice()
          .when(simple("${file:ext} == 'edi'"))
          .to(getKafkaTopicUri("edi_277"))
        .to("file:{{output.directory}}/");

    from("file:{{834.inputdirectory}}/")
        .routeId("834-EDI-File")
        .choice()
            .when(simple("${file:ext} == 'edi'"))
            .to(getKafkaTopicUri("edi_834"))
        .to("file:{{output.directory}}/");

    from("file:{{835.inputdirectory}}/")
        .routeId("835-EDI-File")
        .choice()
           .when(simple("${file:ext} == 'edi'"))
           .to(getKafkaTopicUri("edi_835"))
        .to("file:{{output.directory}}/");

    from("file:{{837.inputdirectory}}/")
        .routeId("837-EDI-File")
        .choice()
           .when(simple("${file:ext} == 'edi'"))
           .to(getKafkaTopicUri("edi_837"))
        .to("file:{{output.directory}}/");
  }
}
