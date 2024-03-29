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

import org.springframework.boot.context.properties.ConfigurationProperties;

@SuppressWarnings("ConfigurationProperties")
@ConfigurationProperties(prefix = "idaas")
public class ConfigProperties {

    private String kafkaBrokers;
    private String integrationTopic;
    private String terminologyTopic;

    private String processTerminologies;

    private String fhirServerVendor;
    private String ibmURI;
    private String hapiURI;
    private String microsoftURI;

    public String getKafkaBrokers() {
        return kafkaBrokers;
    }
    public void setKafkaBrokers(String KafkaBrokers) {
        this.kafkaBrokers = KafkaBrokers;
    }

    public String getIntegrationTopic() {return integrationTopic;}
    public void setIntegrationTopic(String integrationTopic) { this.integrationTopic = integrationTopic;}
    public String getTerminologyTopic() {return terminologyTopic;}
    public void setTerminologyTopic(String terminologyTopic) { this.terminologyTopic = terminologyTopic;}

    public String getProcessTerminologies() {return processTerminologies;}
    public void setProcessTerminologies(String processTerminologies) { this.processTerminologies = processTerminologies;}

    public String getFhirServerVendor() {
        return fhirServerVendor;
    }
    public void setFhirVendor(String FhirVendor) {
        this.fhirServerVendor = FhirVendor;
    }

    public String getIbmURI() {
        return ibmURI;
    }
    public void setIbmURI (String ibmURI) { this.ibmURI = ibmURI; }
    public String getHapiURI() {
        return hapiURI;
    }
    public void setHapiURI (String hapiURI) { this.hapiURI = hapiURI; }
    public String getMicrosoftURI() { return microsoftURI; }
    public void setMicrosoftURI (String microsoftURI) { this.microsoftURI = microsoftURI; }










}
