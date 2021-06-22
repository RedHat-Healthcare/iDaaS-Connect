# General Background
Intelligent DaaS (Data as a Service) is intended to be a tier of reusable and extensible capabilities.
As such, it is all about enabling a consistent design pattern/accelerator based mindset to help healthcare 
organizations innovate in a new and comprehensive manner. Our focus has been, and will continue to be, how 
we simplify data access within healthcare and focus on ensuring <b>data is the asset</b>. 

Here are a few key things to know as you read this:
* Intelligent DaaS, or any of its components, IS NOT A PRODUCT AND WILL NOT BECOME A PRODUCT.
* iDaaS-Connect is all about enabling connectivity to data. For ease of use and scale we have isolated each 
  specific type of connectivity into its own specific solution. This repository is all about showcasing 
  capabilities through software and is a value add.
* Do not think of these capabilities as anything more than a set of design patterns/accelerators to provide 
  comprehensive connectivity and routing of data. Data is the asset and this set of repositories is intended to 
  help anyone connect and build innovative platforms.

Below please find a visual that does visualize the entire iDaaS capabilities set. The key thing to note is while each specific iDaaS capability is purpose built and designed
for any type of customer public or hybrid cloud our focus is on meeting data where it is securely and at scale.

![iDaaS Platform - Visuals - iDaaS Data Flow - Detailed.png](https://github.com/RedHat-Healthcare/iDAAS/blob/master/images/iDAAS-Platform/iDAAS%20Platform%20-%20Visuals%20-%20iDaaS%20Data%20Flow%20-%20Detailed.png)

# Pre-Requisites
For all iDaaS design patterns it should be assumed that you will either install as part of this effort, or have the following:

1. Java JDK
Java is what everything is developed in. While we list JDK 8 SDK you can run a Java SDKs from
version 8, 11, 13,14 and 15. We have leveraged all these JDKs and been able to build, compile and run the
code within the community leveraging iDaaS. We always recommend to consult upstream technologies to ensure these products
are certified and will run beyond a developer machine. We have also seen some tweaks needed in POM files for
specific Java versions.
<a href="https://developers.redhat.com/products/openjdk/download" target=_blank>OpenJDK Download Site</a>
2. An existing Kafka (or some flavor of it) up and running. Red Hat currently implements AMQ-Streams based on Apache Kafka; however, we
have implemented iDaaS with numerous Kafka implementations. Please see the following files we have included to try and help: <br/>
[Kafka](https://github.com/RedHat-Healthcare/iDaaS-Demos/blob/master/Kafka.md)<br/>
[KafkaWindows](https://github.com/RedHat-Healthcare/iDaaS-Demos/blob/master/KafkaWindows.md)<br/>
No matter the platform chosen it is important to know that the Kafka out of the box implementation might require some changes depending
upon your implementation needs. Here are a few we have made to ensure: <br/>
In <kafka>/config/consumer.properties file we will be enhancing the property of auto.offset.reset to earliest. This is intended to enable any new 
system entering the group to read ALL the messages from the start. <br/>
auto.offset.reset=earliest <br/>
2. Some understanding of building, deploying Java artifacts and the commands associated. If using Maven commands then Maven would need to be intalled and runing for the environment you are using. More details about Maven can be found [here](https://maven.apache.org/install.html). This can all be done from an editor or command line, whatever the implementer is most comfortable with.
3. An internet connection with active internet connectivity, this is to ensure that if any Maven commands are
run and any libraries need to be pulled down they can.<br/>

# Additional Configuration Detials  
For IP addressing we have seen well documented issues with IPV6. For all iDaaS design patterns it should be assumed that you will either install as part of this effort, or have the following:
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
[Kafka](https://github.com/RedHat-Healthcare/iDaaS-Demos/blob/master/Kafka.md)<br/>
[KafkaWindows](https://github.com/RedHat-Healthcare/iDaaS-Demos/blob/master/KafkaWindows.md)<br/>

## Step 2: Running the App: Maven Commands or Code Editor
This section covers how to get the application started.
+ Maven: The following steps are needed to run the code. Either through your favorite IDE or command line
```
git clone <repo name>
For example:
git clone https://github.com/RedHat-Healthcare/iDaaS-Connect.git
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
  
![iDaaS Platform - Visuals - iDaaS Data Flow - Detailed.png](https://github.com/RedHat-Healthcare/iDAAS/blob/master/images/iDAAS-Platform/iDaaS-Mgmt-UI.png)

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

# iDaaS Connect Design Patterns
Below are the specific iDaaS Connect branded repositories designed to solve data connectivity
issues.

## iDaaS-Connect-BlueButton
BlueButton is intended to be a very specific implementation to support puling of data to support  
several defined and specific government initiatives. We have implemented a reusable open source  
design pattern to help meet this critical mandated set of requirements.
<br>
[Blue Button Readme](iDaaS-Connect-BlueButton/README.md)
## iDaaS-Connect-Compliance
This is intended to be an enabler for processing data from various IT systems and devices for the purpose of supporting activities
related to Automation and Compliance.

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
