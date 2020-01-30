# Ada Web [![version](https://img.shields.io/badge/version-0.8.1-green.svg)](https://ada-discovery.github.io) [![License: CC BY-NC 3.0](https://img.shields.io/badge/License-CC%20BY--NC%203.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/3.0/) [![Build Status](https://travis-ci.com/ada-discovery/ada-web.svg?branch=master)](https://travis-ci.com/ada-discovery/ada-web)

<img src="https://ada-discovery.github.io/images/logo.png" width="450px">

This is a web part of Ada Discovery Analytics serving as a visual manifestation of [Ada server](https://github.com/ada-discovery/ada-server).  In a nutshell, _Ada web_ consists of controllers with actions whose results are rendered by views as actual presentation endpoints produced by an HTML templating engine. It is implemented by using [Play](https://www.playframework.com) Framework, a popular web framework for Scala built on asynchronous non-blocking processing.

Ada's main features include a visually pleasing and intuitive web UI for an interactive data set exploration and filtering, and configurable views with widgets presenting various statistical results, such as, distributions, scatters, correlations, independence tests, and box plots.  Ada facilitates robust access control through LDAP authentication and an in-house user management with fine-grained permissions backed by [Deadbolt](http://deadbolt.ws) library.

#### Installation

There are essentially two ways how to install a full-stack _Ada web_:

- Install all the components including Mongo and Elastic Search _manually_, which gives a full control and all configurability options at the expense of moderate installation and maintenance effort. The complete guides are availble for  [Linux](Installation_Linux.md) and [MacOS](Installation_MacOS.md).
  
- Use a dockerized version as described in [https://github.com/ada-discovery/ada-docker](https://github.com/ada-discovery/ada-docker), which is undoubtly the easier option. 

Note that if instead of installing a stand alone Ada app you want to use the _Ada web_ libraries in your project you can do so by adding the following dependencies in *build.sbt* (be sure the Scala compilation version is **2.11**)

```
"org.adada" %% "ada-web" % "0.8.1",
"org.adada" %% "ada-web" % "0.8.1" classifier "assets"
```

Alternativelly if you use maven  your *pom.xml* has to contain

```
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.8.1</version>
</dependency>
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.8.1</version>
    <classifier>assets</classifier>
</dependency>
```

#### License

The project and all source its code is distributed under the terms of the <a href="https://creativecommons.org/licenses/by-nc/3.0/">CC BY-NC 3.0 license</a>.

#### Acknowledgement and Support

Development of this project has been significantly supported by

* an FNR Grant (2015-2019, 2019-ongoing): *National Centre of Excellence in Research on Parkinson's Disease (NCER-PD)*: Phase I and Phase II

* a one-year MJFF Grant (2018-2019): *Scalable Machine Learning And Reservoir Computing Platform for Analyzing Temporal Data Sets in the Context of Parkinson’s Disease and Biomedicine*

<br/>

<a href="https://wwwen.uni.lu/lcsb"><img src="https://ada-discovery.github.io/images/logos/logoLCSB-long-230x97.jpg" width="184px"></a>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;<a href="https://www.fnr.lu"><img src="https://ada-discovery.github.io/images/logos/fnr_logo-350x94.png" width="280px"></a>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;<a href="https://www.michaeljfox.org"><img src="https://ada-discovery.github.io/images/logos/MJFF-logo-resized-300x99.jpg" width="240px"></a>
