# iDaaS-Connect: General Background
This Repository is meant to provide a single repository for all  
the specific ways iDaaS can Connect to data from various systems. The key that we wanted to provide the industry
with was the ability to have small components that could provide the capability to
connect and route data for specific healthcare industry standards/protocols.
Do not think of these capabilities as anything more than a set of  
design patterns to provide comprehensive connectivity and routing of data.
Data is the asset and this set of repositories is intended to help anyone connect and
build innovative platforms.

Below please find a visual that does visualize the entire iDaaS capabilities set.
The key thing to note is while each specific iDaaS capability is purpose built and designed
for any type of customer public or hybrid cloud our focus is on meeting data where it is securely  
and at scale.
scripts  
![iDAAS Platform - Visuals - iDaaS Data Flow - Detailed.png](Repo-General/Visuals/iDAAS%20Platform%20-%20Visuals%20-%20iDaaS%20Data%20Flow%20-%20Detailed.png)

# Pre-Requisites
For each one of the iDaaS Connect specific design patterns they are established to work with AMQ-Streams
out of the box, so you will need to have this setup and running.

[Kafka](Kafka.md)<br/>
[KafkaWindows](KafkaWindows.md)<br/>

We also leverage [Kafka Tools](https://kafkatool.com/) to help us show Kafka details and transactions..

# iDaaS Connect
Below are the specific iDaaS Connect branded repositories designed to solve data connectivity
issues.

## iDaaS-Connect-BlueButton
BlueButton is intended to be a very specific implementation to support puling of data to support  
several defined and specific government initiatives. We have implemented a reusable open source  
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
[FHIR Readme](iDaaS-Connect-FHIR/README.md)
## iDaaS-Connect-HL7
HL7 is a very legacy based client server socket protocol.
<br>
[HL7 Readme](iDaaS-Connect-HL7/README.md)
## iDaaS-Connect-ThirdParty
[Third Party Readme](iDaaS-Connect-ThirdParty/README.md)
