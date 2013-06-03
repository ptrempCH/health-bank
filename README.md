health-bank
===========

First draft of HealthBank application

- Directory GUI contains the Graphical User Interface or in other words the website behind. It is written in HTML5 with the help of CSS3 and JavaScript. We used additional JavaScript libraries such as jQuery, jQuery UI, moment.js etc. 

- Directory Libraries contains libraries used in the Java API

- Directory HealthBank contains the code for the Java API which is done using Java Servlets. As a backbone this API uses a mongoDB database. The more this directory contains two important files. First the HealthBank.war file that contains the war directory for the API, this file can be run on any Tomcat v6 or heigher server. The second file is the HB_Configuration.xml file, that contains all the configuration needed for the API. If you would like to run the API, this file needs to go in the C:\Temp folder on your Windwos machine or in /opt/ on a Unix machine. 
