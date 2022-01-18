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
