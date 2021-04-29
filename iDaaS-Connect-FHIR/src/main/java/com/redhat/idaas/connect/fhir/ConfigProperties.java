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

    private String fhirVendor;

    private String ibmFhirServer;
    private String hapiFhirServer;
    private String microsoftFhirServer;

    public String getKafkaBrokers() {
        return kafkaBrokers;
    }

    public String getFhirVendor() {
        return fhirVendor;
    }

    public String getIbmFhirServer() {
        return ibmFhirServer;
    }

    public String getHapiFhirServer() {
        return hapiFhirServer;
    }
    public String getMicrosoftFhirServer() {
        return microsoftFhirServer;
    }

    public void setKafkaBrokers(String KafkaBrokers) {
        this.kafkaBrokers = KafkaBrokers;
    }

    public void setFhirVendor(String fhirVendor) {
        this.fhirVendor = fhirVendor;
    }

    public void setIbmFhirServer(String ibmFhirServer) { this.ibmFhirServer = ibmFhirServer; }
    public void setHapiFhirServer(String hapiFhirServer) { this.hapiFhirServer = hapiFhirServer; }
    public void setMicrosoftFhirServer(String microsoftFhirServer) { this.microsoftFhirServer = microsoftFhirServer; }

}
