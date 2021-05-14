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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idaas")
public class ConfigProperties {

    private String kafkaBrokers;

    private String hl7ADT_Directory;
    private String hl7ORM_Directory;
    private String hl7ORU_Directory;
    private String hl7MFN_Directory;
    private String hl7MDM_Directory;
    private String hl7SCH_Directory;
    private String hl7VXU_Directory;

    private int adtPort;

    private int ormPort;

    private int oruPort;

    private int rdePort;

    private int mfnPort;

    private int mdmPort;

    private int schPort;

    private int vxuPort;

    public String getKafkaBrokers() {
        return kafkaBrokers;
    }

    public String getHl7ADT_Directory() { return hl7ADT_Directory; }
    public String getHl7ORM_Directory() { return hl7ORM_Directory; }
    public String getHl7ORU_Directory() { return hl7ORU_Directory; }
    public String getHl7MDM_Directory() { return hl7MDM_Directory; }
    public String getHl7MFN_Directory() { return hl7MFN_Directory; }
    public String getHl7SCH_Directory() { return hl7SCH_Directory; }
    public String getHl7VXU_Directory() { return hl7VXU_Directory; }

    public int getAdtPort() {
        return adtPort;
    }

    public int getOrmPort() {
        return ormPort;
    }

    public int getOruPort() {
        return oruPort;
    }

    public int getRdePort() {
        return rdePort;
    }

    public int getMfnPort() {
        return mfnPort;
    }

    public int getMdmPort() {
        return mdmPort;
    }

    public int getSchPort() {
        return schPort;
    }

    public int getVxuPort() {
        return vxuPort;
    }

    public void setKafkaBrokers(String kafkaBrokers) {
        this.kafkaBrokers = kafkaBrokers;
    }

    public void setHl7ADT_Directory(String hl7ADT_Directory) { this.hl7ADT_Directory = hl7ADT_Directory; }
    public void setHl7ORM_Directory(String hl7ORM_Directory) { this.hl7ORM_Directory = hl7ORM_Directory; }
    public void setHl7ORU_Directory(String hl7ORU_Directory) { this.hl7ORU_Directory = hl7ORU_Directory; }
    public void setHl7MDM_Directory(String hl7MDM_Directory) { this.hl7MDM_Directory = hl7MDM_Directory; }
    public void setHl7MFN_Directory(String hl7MFN_Directory) { this.hl7MFN_Directory = hl7MFN_Directory; }
    public void setHl7SCH_Directory(String hl7SCH_Directory) { this.hl7SCH_Directory = hl7SCH_Directory; }
    public void setHl7VXU_Directory(String hl7VXU_Directory) { this.hl7VXU_Directory = hl7VXU_Directory; }

    public void setAdtPort(int adtPort) {
        this.adtPort = adtPort;
    }

    public void setOrmPort(int ormPort) {
        this.ormPort = ormPort;
    }

    public void setOruPort(int oruPort) {
        this.oruPort = oruPort;
    }

    public void setRdePort(int rdePort) {
        this.rdePort = rdePort;
    }

    public void setMfnPort(int mfnPort) {
        this.mfnPort = mfnPort;
    }

    public void setMdmPort(int mdmPort) {
        this.mdmPort = mdmPort;
    }

    public void setSchPort(int schPort) {
        this.schPort = schPort;
    }

    public void setVxuPort(int vxuPort) {
        this.vxuPort = vxuPort;
    }
}
