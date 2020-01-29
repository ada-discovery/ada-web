# Ada Web [![version](https://img.shields.io/badge/version-0.8.1-green.svg)](https://ada-discovery.github.io) [![License: CC BY-NC 3.0](https://img.shields.io/badge/License-CC%20BY--NC%203.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/3.0/) [![Build Status](https://travis-ci.com/ada-discovery/ada-web.svg?branch=master)](https://travis-ci.com/ada-discovery/ada-web)

<img src="https://ada-discovery.github.io/images/logo.png" width="450px">
This is a web part of Ada Discovery Analytics.

#### Installation

All you need is **Scala 2.11**. To pull the library you have to add the following dependencies to *build.sbt*

```
"org.adada" %% "ada-web" % "0.8.1",
"org.adada" %% "ada-web" % "0.8.1" classifier "assets"
```

or to *pom.xml* (if you use maven)

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
