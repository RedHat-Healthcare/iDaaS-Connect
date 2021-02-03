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
package com.redhat.idaas.connect.fhir;

import ca.uhn.fhir.store.IAuditDataStore;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jms.connection.JmsTransactionManager;
//import javax.jms.ConnectionFactory;
import org.springframework.stereotype.Component;
import sun.util.calendar.BaseCalendar;
import java.time.LocalDate;

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

  @Bean
  ServletRegistrationBean camelServlet() {
    // use a @Bean to register the Camel servlet which we need to do
    // because we want to use the camel-servlet component for the Camel REST service
    ServletRegistrationBean mapping = new ServletRegistrationBean();
    mapping.setName("CamelServlet");
    mapping.setLoadOnStartup(1);
    mapping.setServlet(new CamelHttpTransportServlet());
    mapping.addUrlMappings("/camel/*");
    return mapping;
  }

  private String getKafkaTopicUri(String topic) {
    return "kafka:" + topic +
            "?brokers=" +
            config.getKafkaBrokers();
  }

  private String getFHIRServerUri(String fhirResource) {
    String fhirServerVendor = config.getFhirVendor();
    String fhirServerURI = null;
    if (fhirServerVendor.equals("ibm"))
    {
      //.to("jetty:http://localhost:8090/fhir-server/api/v4/AdverseEvents?bridgeEndpoint=true&exchangePattern=InOut")
      fhirServerURI = "jetty:"+config.getIbmURI()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    if (fhirServerVendor.equals("hapi"))
    {
      fhirServerURI = "jetty:"+config.getHapiURI()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    if (fhirServerVendor.equals("microsoft"))
    {
      fhirServerURI = "jetty:"+config.getMicrosoftURI()+fhirResource+"?bridgeEndpoint=true&exchangePattern=InOut";
    }
    return fhirServerURI;
  }

  /*
   * Kafka implementation based upon https://camel.apache.org/components/latest/kafka-component.html
   *
   */
  @Override
  public void configure() throws Exception {

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
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=opsmgmt_platformtransactions&brokers=localhost:9092")
    ;
    /*
    *  Logging
    */
    from("direct:logging")
        .log(LoggingLevel.INFO, log, "HL7 Admissions Message: [${body}]")
        //To invoke Logging
        //.to("direct:logging")
    ;

    /*
     *  Any endpoint defined would be managed by access http(s)://<servername>:<serverport>/<resource name>
     */

    /*
     *  Clinical FHIR
     */
    from("servlet://adverseevent")
        .routeId("FHIRAdverseEvent")
        // set Auditing Properties
        .convertBodyTo(String.class)
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AdverseEvent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Adverse Event message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_adverseevent&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/AdverseEvents?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("adverseevents")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("adverseevents FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
      ;
    from("servlet://alergyintollerance")
        .routeId("FHIRAllergyIntollerance")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("messagetrigger").constant("AllergyIntollerance")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Allergy Intollerance message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_allergyintellorance&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/AllergyIntollerance?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("allergyintollerance")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("allergyintollerance FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://appointment")
         .routeId("FHIRAppointment")
         .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-FHIR")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("Appointment")
         .setProperty("component").simple("${routeId}")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("processname").constant("Input")
         .setProperty("auditdetails").constant("Appointment message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_appointment&brokers=localhost:9092")
         //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/Appointment?bridgeEndpoint=true&exchangePattern=InOut")
         //Process Response
         //.convertBodyTo(String.class)
         // set Auditing Properties
         //.setProperty("processingtype").constant("data")
         //.setProperty("appname").constant("iDAAS-Connect-FHIR")
         //.setProperty("industrystd").constant("FHIR")
         //.setProperty("messagetrigger").constant("appointment")
         //.setProperty("component").simple("${routeId}")
         //.setProperty("processname").constant("Response")
         //.setProperty("camelID").simple("${camelId}")
         //.setProperty("exchangeID").simple("${exchangeId}")
         //.setProperty("internalMsgID").simple("${id}")
         //.setProperty("bodyData").simple("${body}")
         //.setProperty("auditdetails").constant("appointment FHIR response message received")
         // iDAAS DataHub Processing
         //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://appointmentresponse")
        .routeId("FHIRAppointmentResponse")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AppointmentResponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Appointment Response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_appointmentresponse&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/AppointmentResponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("appointmentresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("appointmentresponse FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://careplan")
        .routeId("FHIRCarePlan")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CarePlan")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CarePlan message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_careplan&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/CarePlan?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("careplan")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("careplan FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://careteam")
        .routeId("FHIRCareTeam")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CareTeam")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CareTeam message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_careteam&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/CareTeam?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("careteam")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("careteam FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
        // Send To Topic
        //.wireTap("direct:auditing")
    ;
    from("servlet://clincialimpression")
        .routeId("FHIRClinicalImpression")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ClinicalImpression")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("ClinicalImpression message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_clinicalimpression&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ClinicalImpression?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("clinicalimpression")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("clinicalimpression FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://codesystem")
        .routeId("FHIRCodeSystem")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CodeSystem")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CodeSystem message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_codesystem&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/CodeSystem?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("codesystem")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("codesystem FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://consent")
        .routeId("FHIRConsent")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Consent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Consent message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_consent&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Consent?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("consent")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("consent FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://communication")
        .routeId("FHIRCommunication")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Communication")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Communication message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_communication&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Communication?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("communication")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("communication FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://condition")
        .routeId("FHIRCondition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Condition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_condition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Condition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("condition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("condition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://consent")
        .routeId("FHIRConsent")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Consent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Consent message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_consent&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Consent?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("consent")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("consent FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://detectedissue")
        .routeId("FHIRDetectedIssue")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DetectedIssue")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Detected Issue message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_detectedissue&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/DetectedIssue?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("detectedissue")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("detectedissue FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://device")
        .routeId("FHIRDevice")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Device")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_device&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Device?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("device")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("device FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://devicerequest")
        .routeId("FHIRDeviceRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DeviceRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_devicerequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/DeviceRequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("devicerequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("devicerequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://deviceusestatement")
        .routeId("FHIRDeviceUseStatement")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DeviceUseStatement")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device Use Statement message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_deviceusestatement&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/DeviceUseStatement?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("deviceusestatement")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("deviceusestatement FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://diagnosticreport")
        .routeId("FHIRDiagnosticReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DiagnosticReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Diagnostic Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_diagnosticreport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/DiagnosticReport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("DiagnosticReport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Diagnostic Report FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://documentreference")
        .routeId("FHIRDocumentReference")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DocumentReference")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("DocumentReference message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_documentreference&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/DocumentReference?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("documentreference")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("documentreference FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://encounter")
        .routeId("FHIREncounter")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Encounter")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Encounter message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_encounter&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Encounter?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("encounter")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("encounter FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://episodeofcare")
        .routeId("FHIREpisodeOfCare")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EpisodeOfCare")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("EpisodeOfCare message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_episodeofcare&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/EpisodeOfCare?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("episodeofcare")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("episodeofcare FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://familymemberhistory")
        .routeId("FHIRMemberHistory")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Family Member History")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Family Member History message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_familymemberhistory&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/FamilyMemberHistory?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("Family Member History")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Family Member History FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://goal")
        .routeId("FHIRGoal")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Goal")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Goal message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_goal&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Goal?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("goal")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("goal FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://healthcareservice")
        .routeId("FHIRHealthcareService")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("HealthcareService")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("HealthcareService message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_healthcareservice&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/HealthcareService?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("healthcareservice")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("healthcareservice FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://imagingstudy")
        .routeId("FHIRImagingStudy")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ImagingStudy")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Imaging Study message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_imagingstudy&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ImagingStudy?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("imagingstudy")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("imagingstudy FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
     from("servlet://immunization")
        .routeId("FHIRImmunization")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Immunization")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Immunization message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_immunization&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Immunization?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("Immunization")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Immunization FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://location")
        .routeId("FHIRLocation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Location")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Location message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_location&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Location?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("location")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("location FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medication")
        .routeId("FHIRMedication")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Medication")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_medication&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Medication?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("Medication")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Medication FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationadministration")
        .routeId("FHIRMedicationAdministration")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationAdministration")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Admin message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_medicationadmiinistration&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/MedicationAdministration?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationadministration")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Medication Admin FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationdispense")
        .routeId("FHIRMedicationDispense")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationDispense")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Dispense message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_medicationdispense&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/MedicationDispense?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationdispense")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Medication Dispense response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationrequest")
        .routeId("FHIRMedicationRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_medicationrequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/MedicationRequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("meedicationrequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationstatement")
        .routeId("FHIRMedicationStatement")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationStatement")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Statement message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_medicationstatement&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/MedicationStatement?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationstatement")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("Medication Statement FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://observation")
        .routeId("FHIRObservation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Observation")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Observation message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_observation&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Observation?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("observation")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("observation FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://organization")
        .routeId("FHIROrganization")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Organization")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Organization message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_organization&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Organization?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("organization")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("organization FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://organizationaffiliation")
        .routeId("FHIROrganizationAffiliation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("OrganizationAffiliation")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Organization Affiliation message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_organizationaffiliation&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/OrganizationAffiliation?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("organizationaffiliation")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("organizationaffiliation FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://patient")
        .routeId("FHIRPatient")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Patient")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Patient message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_patient&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Patient?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("patient")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("patient FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://person")
        .routeId("FHIRPerson")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Person")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Person message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_person&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Person?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("person")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("person FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://practitioner")
        .routeId("FHIRPractitioner")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Practitioner")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Practitioner message received")
        .setProperty("bodyData").simple("${body}")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_practitioner&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Practitioner?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("practitioner")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("practitioner FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://practitionerrole")
        .routeId("FHIRPractitionerRole")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("PractitionerRole")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Practitioner Role message received")
        .setProperty("bodyData").simple("${body}")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_practitionerrole&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/PractitionerRole?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("practitionerrole")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("practitioner role response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://procedure")
        .routeId("FHIRProcedure")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Procedure")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Procedure message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_procedure&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Procedure?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("procedure")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("procedure FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://questionaire")
        .routeId("FHIRQuestionaire")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Questionaire")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Questionaire message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_questionaire&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Questionaire?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("questionaire")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("questionaire FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://questionaireresponse")
        .routeId("FHIRQuestionaireResponse")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("QuestionaireResponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Questionaire Response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_questionaireresponse&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/QuestionaireResponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("questionaireresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("questionaireresponse FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://researchstudy")
        .routeId("FHIRResearchStudy")
        .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-Connect-FHIR")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("ResearchStudy")
         .setProperty("component").simple("${routeId}")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("processname").constant("Input")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("processname").constant("Input")
         .setProperty("auditdetails").constant("Research Study message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchstudy&brokers=localhost:9092")
         //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/ResearchStudy?bridgeEndpoint=true&exchangePattern=InOut")
         //Process Response
         //.convertBodyTo(String.class)
         // set Auditing Properties
         //.setProperty("processingtype").constant("data")
         //.setProperty("appname").constant("iDAAS-Connect-FHIR")
         //.setProperty("industrystd").constant("FHIR")
         //.setProperty("messagetrigger").constant("researchstudy")
         //.setProperty("component").simple("${routeId}")
         //.setProperty("processname").constant("Response")
         //.setProperty("camelID").simple("${camelId}")
         //.setProperty("exchangeID").simple("${exchangeId}")
         //.setProperty("internalMsgID").simple("${id}")
         //.setProperty("bodyData").simple("${body}")
         //.setProperty("auditdetails").constant("researchstudy FHIR response message received")
         // iDAAS DataHub Processing
         //.wireTap("direct:auditing")
    ;
    from("servlet://schedule")
        .routeId("FHIRSchedule")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Schedule")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Schedule message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_schedule&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Schedule?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("schedule")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("schedule FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://servicerequest")
        .routeId("FHIRServiceRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ServiceRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Service Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_servicerequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ServiceRequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("servicerequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("servicerequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://specimen")
        .routeId("FHIRSpecimen")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Specimen")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Specimen message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_specimen&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Specimen?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("specimen")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("specimen FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://substance")
        .routeId("FHIRSubstance")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Substance")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Substance message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_sustance&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Substance?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("substance")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("substance FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://supplydelivery")
        .routeId("FHIRSupplyDelivery")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("SupplyDelivery")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Supply Delivery message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_supplydelivery&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/SupplyDelivery?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("supplydelivery")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("supplydelivery FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://supplyrequest")
        .routeId("FHIRSupplyRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("SupplyRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Supply Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_supplyrequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/SupplyRequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("supplyrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("supplyrequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://testreport")
        .routeId("FHIRTestReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("TestReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Test Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_testreport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/TestReport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("testreport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("testreport FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://verificationresult")
        .routeId("FHIRVerificationResult")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("VerificationResult")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Verification Result message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_verificationresult&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/VerificationResult?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("verificationresult")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("verificationresult FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    /*
     *  FHIR: Financial
     */
    from("servlet://account")
        .routeId("FHIRAccount")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("account")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("account message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_account&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Account?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("account")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Input")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("account FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://chargeitem")
        .routeId("FHIRChargeItem")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("chargeitem")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("charge item message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_chargeitem&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ChargeItem?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("chargeitem")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Input")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("chargeitem FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://chargeitemdefinition")
        .routeId("FHIRChargeItemDefintion")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("chargeitemdefinition")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("charge item definition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_chargeitemdefinintion&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ChargeItemDefinition?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("chargeitemdefintion")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Input")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("chargeitemdefintion FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://claim")
        .routeId("FHIRClaim")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("claim")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("claim message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_claim&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Claim?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("claim")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Input")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("claim FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://claimresponse")
        .routeId("FHIRClaimResponse")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("claimresponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("claim response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_claimresponse&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ClaimResponse?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("claimresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Input")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("claim response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://contract")
        .routeId("FHIRContract")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("contract")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("contract message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_contract&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Contract?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("contract")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("contract FHIR message response received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://coverage")
        .routeId("FHIRCoverage")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("coverage")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("coverage message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_coverage&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Coverage?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("coverage")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("coverage FHIR message response received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet:/coverageeligibilityrequest")
        .routeId("FHIRCoverageEligibilityRequest")
        // set Auditing Properties
        .convertBodyTo(String.class)
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("coverageeligibilityrequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("coverageeligibilityrequest message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_coverageeligibilityrequest&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/CoverageEligibilityRequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("coverageeligibilityrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("coverage eligibility request FHIR Response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://coverageeligibilityresponse")
        .routeId("FHIRCoverageEligibilityResponse")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("coverageeligibilityresponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("coverageeligibilityresponse message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_coverageeligibilityresponse&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/CoverageEligibilityResponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("coverageeligibilityresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("coverage eligibility response response received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://enrollmentrequest")
        .routeId("FHIREnrollmentrequest")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("enrollmentrequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Enrollment Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_enrollmentrequest&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/EnrollmentRequest?bridgeEndpoint=true&exchangePattern=InOut")
        // Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("enrollmentrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("enrollment request message  received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://enrollmentresponse")
        .routeId("FHIREnrollmentresponse")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("enrollmentresponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Enroll Response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_enrollmentresponse&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/EnrollmentResponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("enrollmentresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("enrollment response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://explanationofbenefits")
        .routeId("FHIRExplanationofbenefits")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("explanationofbenefits")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("explanationofbenefits message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_explanationofbenefits&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ExplanationOfBenefit?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("explanationofbenefits")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("explanationofbenefits response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://insuranceplan")
        .routeId("FHIRInsuranceplan")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("insuranceplan")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("insuranceplan message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_insuranceplan&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        // Invoke External FHIR Server
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/InsurancePlan?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("insuranceplan")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("insuranceplan response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://invoice")
        .routeId("FHIRInvoice")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("invoice")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("invoice message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_invoice&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Invoice?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("invoice")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("invoice FHIR message response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://paymentnotice")
        .routeId("FHIRPaymentNotice")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("paymentnotice")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("paymentnotice message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_paymentnotice&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/PaymentNotice?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("paymentnotice")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("paymentnotice FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet:paymentreconciliation")
        .routeId("FHIRPaymentreconciliation")
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("paymentreconciliation")
        .setProperty("component").simple("${routeId}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("paymentreconciliation message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_paymentreconciliation&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/PaymentReconciliation?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("paymentreconciliation")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("paymentreconciliation FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    /*
     *  FHIR: Public Health
     */
    from("servlet://researchstudy")
        .routeId("FHIRResearchStudy")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchStudy")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Stduy message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchstudy&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ResearchStudy?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchstudy")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchstudy response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://researchsubject")
        .routeId("FHIRResearchSubject")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchSubject")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Subject message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchsubject&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ResearchSubject?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchsubject")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchsubject FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    /*
     *  FHIR: Evidence Based Medicine
     */
     from("servlet://researchdefinition")
        .routeId("FHIRResearchDefinition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchDefinition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Definition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchdefinition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ResearchDefinition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchdefinition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchdefinition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
        ;
    from("servlet://researchelementdefinition")
        .routeId("FHIRResearchElementDefinition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchElementDefinition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Element Definition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_researchelementdefinition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/ResearchElementDefinition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchelementdefinition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchelementdefinition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://evidence")
        .routeId("FHIREvidence")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Evidence")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Evidence message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_evidence&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Evidence?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("evidence")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("evidence FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://evidencevariable")
        .routeId("FHIREvidenceVariable")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EvidenceVariable")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Evidence Variable message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_evidencevariable&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/EvidenceVariable?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("evidencevariable")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("evidencevariable FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://effectevidencesynthesis")
        .routeId("FHIREffectEvidenceSynthesis")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EffectEvidenceSynthesis")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Effect Evidence Synthesis message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_effectevidencesynthesis&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/EffectEvidenceSynthesis?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("effectevidencesynthesis")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("effectevidencesynthesis FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://riskevidencesynthesis")
        .routeId("FHIRRiskEvidenceSynthesis")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("RiskEvidenceSynthesis")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Risk Evidence Synthesis message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_riskevidencesynthesis&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/RiskEvidenceSynthesis?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("riskevidencesynthesis")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("riskevidencesynthesis FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    /*
     *  FHIR: Quality Reporting
     */
    from("servlet://measure")
        .routeId("FHIRMeasure")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Measure")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_measure&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/Measure?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measure")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measure FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://measurereport")
        .routeId("FHIRMeasureReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MeasureReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_measurereport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/MeasureReport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measurereport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measurereport FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://testreport")
        .routeId("FHIRTestReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("TestReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Test Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_testreport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/TestReport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("testreport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("testreport response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://testscript")
        .routeId("FHIRTestScript")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-Connect-FHIR")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("TestScript")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Test Script message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=fhirsvr_testscript&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/TestScript?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-Connect-FHIR")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("testscript")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("testscript FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
  }
}