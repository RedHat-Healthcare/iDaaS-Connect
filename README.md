# Pre-Requisites
For all iDaaS design patterns it should be assumed that you will either install as part of this effort, or have the following:

1. Java JDK
   Java is what everything is developed in. The current supported JDK release(s) are 1.8 and 11.
   <a href="https://developers.redhat.com/products/openjdk/download" target=_blank>OpenJDK Download Site</a>
2. An existing Kafka (or some flavor of it) up and running. Red Hat currently implements AMQ-Streams based on Apache Kafka; 
   however, we have implemented iDaaS with numerous Kafka implementations. Please see the following files we have 
   included to try and help: <br/>
   [Kafka](https://github.com/Project-Herophilus/Project-Herophilus-Assets/blob/main/Kafka.md) <br/>
   [KafkaWindows](https://github.com/Project-Herophilus/Project-Herophilus-Assets/blob/main/KafkaWindows.md) <br/>
   No matter the platform chosen it is important to know that the Kafka out of the box implementation might require some changes depending
   upon your implementation needs. Here are a few we have made to ensure: <br/>
   In <kafka>/config/consumer.properties file we will be enhancing the property of auto.offset.reset to earliest. This is intended to enable any new
   system entering the group to read ALL the messages from the start. <br/>
   auto.offset.reset=earliest <br/>
3. Some understanding of building, deploying Java artifacts and the commands associated. If using Maven commands then 
Maven would need to be intalled and runing for the environment you are using. More details about Maven can be found
[here](https://maven.apache.org/install.html). This can all be done from an editor or command line, whatever the implementer is most comfortable with.
4. An internet connection with active internet connectivity, this is to ensure that if any Maven commands are
   run and any libraries need to be pulled down they can.<br/>
5. Something to view Kafka topics with as you are developing, validating and implementing any solution.

# Specific Instructions
The following are special instructions developed based on specific implementations. While some of these might be documented
for specific OSes that the overall issue is key to understand and resolve.

## General Issues: HL7
1. IPV6 needs to be disabled and IPv4 needs to be enabled. 

### Red Hat Enterprise Linux

1. Specific to iDaaS Connect HL7 design patterns/reference architecture IPv4 must be enabled at the OS level, IPv6 will cause connectivity issues and in many cases outright failure of the components to function.<br/>
   https://access.redhat.com/solutions/8709
   But here's the current specifics: <br/>
   Disabling IPv6 in NetworkManager
   For all systems that run NetworkManager, IPv6 must be disabled on each interface with the option ipv6.method set to ignore (RHEL7) or disabled (RHEL8+). This step must be done in addition to IPv6 being disabled using the methods below.
   For RHEL 8 and later: <br/>
```
nmcli connection modify <Connection Name> ipv6.method "disabled" <br/>
(Replace <Connection Name> with interface)
```
AND <br/>
```
Create a new file named /etc/sysctl.d/ipv6.conf and add the following options:

# First, disable for all interfaces
net.ipv6.conf.all.disable_ipv6 = 1
net.ipv6.conf.default.disable_ipv6 = 1
net.ipv6.conf.lo.disable_ipv6 = 1
# If using the sysctl method, the protocol must be disabled all specific interfaces as well.
net.ipv6.conf.<interface>.disable_ipv6 = 1
The new settings would then need to be reloaded with the following command line command:

# sysctl -p /etc/sysctl.d/ipv6.conf
```

# Start The Engine!!!
This section covers the running any of the design patterns/accelerators. There are several options to start the Engine Up!!!

## Step 1: Kafka Server To Connect To
In order for ANY processing to occur you must have a Kafka server running that this accelerator is configured to connect to.
Please see the following files we have included to try and help: <br/>
[Kafka](https://github.com/Project-Herophilus/Project-Herophilus-Assets/blob/main/Kafka.md) <br/>
[KafkaWindows](https://github.com/Project-Herophilus/Project-Herophilus-Assets/blob/main/KafkaWindows.md) <br/>

## (When Applicable) Step 2: Make Sure ANY Technologies Needed is Up and Running
Depending upon which iDaaS Connect components are being used there is a need to ensure that any third party software or 
services are up and running. This could be anything from external systems hosting files that you
may be asked to pickup or external servers you will be connecting to, like FHIR servers.

## Step 3: Running the App: Maven Commands or Code Editor
This section covers how to get the application started.
+ Maven: The following steps are needed to run the code. Either through your favorite IDE or command line
```
git clone <repo name>
For example:
git clone https://github.com/Project-Herophilus/iDaaS-Connect.git
 ```
You can either compile at the base directory or go to the specific iDaaS-Connect acceelerator. Specifically, you want to
be at the same level as the POM.xml file and execute the following command: <br/>
```
mvn clean install
```
You can run the individual efforts with a specific command, it is always recommended you run the mvn clean install first.
Here is the command to run the design pattern from the command line: <br/>
```
mvn spring-boot:run
 ```
Depending upon if you have every run this code before and what libraries you have already in your local Maven instance
it could take a few minutes.
+ Code Editor: You can right click on the Application.java in the /src/<application namespace> and select Run

# Running the Java JAR
If you don't run the code from an editor or from the maven commands above. You can compile the code through the maven
commands above to build a jar file. Then, go to the /target directory and run the following command: <br/>
```
java -jar <jarfile>.jar 
 ```

# Additional Configuration Detials
In numerous cases we have seen some well documented issues with using IPV6. For all iDaaS design patterns/accelerators
it should be assumed that you will either install as part of this effort, or have the following:

## Design Pattern/Accelerator Configuration
Each design pattern/accelerator has a unique and specific application.properties for its usage and benefit. Please make
sure to look at these as there is a lot of power in these and the goal is to minimize hard coded anything.
Leverage the respective application.properties file in the correct location to ensure the properties are properly set
and use a custom location. You can compile the code through the maven commands above to build a jar file. Then, go
to the /target directory and run the following command: <br/>
```
java -jar <jarfile>.jar --spring.config.location=file:./config/application.properties
 ```

# Admin Interface - Management and Insight of Components
Within each specific repository there is an administrative user interface that allows for monitoring and insight into the
connectivity of any endpoint. Additionally, there is also the implementation to enable implementations to build there own
by exposing the metadata. The data is exposed and can be used in numerous very common tools like Data Dog, Prometheus and so forth.
This capability to enable would require a few additional properties to be set.

Below is a generic visual of how this looks (the visual below is specific to iDaaS Connect HL7): <br/>

![iDaaS Platform - Visuals - iDaaS Data Flow - Detailed.png](https://github.com/RedHat-Healthcare/iDAAS/blob/master/Platform/Images/iDAAS-Platform/iDaaS-Mgmt-UI.png)

Every asset has its own defined specific port, we have done this to ensure multiple solutions can be run simultaneously.

## Administrative Interface(s) Specifics
For all the URL links we have made them localhost based, simply change them to the server the solution is running on.

|<b> iDaaS Connect Asset | Port | Admin URL / JMX URL |
| :---        | :----   | :--- | 
|iDaaS Connect HL7 | 9980| http://localhost:9980/actuator/hawtio/index.html / http://localhost:9980/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=* | 
|iDaaS Connect FHIR | 9981| http://localhost:9981/actuator/hawtio/index.html / http://localhost:9981/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  
|iDaaS Connect BlueButton| 9982| http://localhost:9982/actuator/hawtio/index.html / http://localhost:9982/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  
|iDaaS Connect Third Party | 9983| http://localhost:9983/actuator/hawtio/index.html / http://localhost:9983/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  
|iDaaS Connect EDI | 9984| http://localhost:9984/actuator/hawtio/index.html / http://localhost:9984/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  
|iDaaS Connect Compliance Automation | 9985| http://localhost:9985/actuator/hawtio/index.html / http://localhost:9985/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  
|iDaaS Connect ePrescribe | 9986| http://localhost:9986/actuator/hawtio/index.html / http://localhost:9986/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*|  

# Testing
In order to assist anyone implementing or testing this specific reference architecture we have in the put a series of
assets in place.

## Test Data
To ensure resources have a variety of data to leverage for complete testing into our main assets [area](https://github.com/Project-Herophilus/Project-Herophilus-Assets/tree/main/Testing/TestData). We have put testing data into specifically defined directories to ensure it is simply to find and leverage.

## API - Testing
When it comes to any solution that leverages APIs they can be tested by leveraging our published Postman collections.
  
### API - Published Postman Content
If there are any specific APIs we try and maintain an active Postman collection. You can find the collection here: 
https://www.postman.com/winter-robot-787282/workspace/intelligent-daas/overview

# iDaaS Components
Below are all the specific iDaaS branded repositories designed to provide a complete set of tools, utlities and 
components that can help solve various data related issues.

|Repository | Repository Benefit |
| ------------ | ----------- |
| [iDaaS Connect](https://github.com/RedHat-Healthcare/iDaaS-Connect) | Connecting to and inbound or outbound data processing |
| [iDaaS Route](https://github.com/RedHat-Healthcare/iDaaS-Route) | Routing data and processing data dynamically |
| [iDaaS DReAM](https://github.com/RedHat-Healthcare/iDAAS-DREAM) | Business Process, Rules and Complex Event Based Assets |
| [iDaaS KIC](https://github.com/RedHat-Healthcare/iDaaS-KIC) | Knowledge, Insight and Conformance - platform for ensuring any activities with data are captured |
| [iDaaS Data Simulators](https://github.com/RedHat-Healthcare/iDaaS-Data-Simulators) | Data Simulators to help with iDaaS and industry needs to demonstrate capabilities|

You will also see several other repositories of note that we natively forked from upstream community efforts.

Repository | Repository Benefit |
| ------------ | ----------- |
| [DataSynthesis](https://github.com/Project-Herophilus/DataSynthesis) | Synthetic Data |
| [Defianz](https://github.com/Project-Herophilus/Defianz) | De-identification and Anonymization of data |
| [Event Builder](https://github.com/Project-Herophilus/Event-Builder) | Ability to process, parse, generate and transform data in real time |

# iDaaS Connect Design Patterns
Below are the specific iDaaS Connect branded repositories designed to solve data connectivity
issues.

## iDaaS-Connect-BlueButton
BlueButton is intended to be a very specific implementation to support puling of data to support  
several defined and specific US government initiatives. We have implemented a reusable open source  
design pattern to help meet this critical mandated set of requirements.
<br>
[Blue Button Readme](iDaaS-Connect-BlueButton/README.md)
## iDaaS-Connect-EDI
EDI has been a standard around for decades, this repository does not introduce capabilities that compete
with capabilities vailable for claims processing or other EDI very specific needs. The intent
of this repository it to enable the processing of EDI data such as cliams and
Supply chain.<br>
[EDI Readme](iDaaS-Connect-EDI/README.md)
## iDaaS-Connect-FHIR
FHIR is a modern based integration standard that has been adopted by the government to assist them in addressing new federal
mandates such as the Interoperability and Patient Access Rule. The iDaaS-Connect-FHIR component fully supports integrating to multiple
external vendor FHIR servers in a consistent design pattern manner.  
[FHIR Readme](iDaaS-Connect-FHIR/README.md)
## iDaaS-Connect-HL7
HL7 is a very legacy based client server socket protocol.
<br>
[HL7 Readme](iDaaS-Connect-HL7/README.md)
## iDaaS-Connect-ThirdParty
[Third Party Readme](iDaaS-Connect-ThirdParty/README.md)
