# Ada Web [![version](https://img.shields.io/badge/version-0.8.0-green.svg)](https://ada-discovery.org) [![License: CC BY-NC 3.0](https://img.shields.io/badge/License-CC%20BY--NC%203.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/3.0/)

<img src="https://ada-discovery.github.io/images/logo.png" width="450px">

This is a web part of Ada Discovery Analytics.

#### Installation

All you need is **Scala 2.11**. To pull the library you have to add the following dependencies to *build.sbt*

```
"org.adada" %% "ada-web" % "0.8.0",
"org.adada" %% "ada-web" % "0.8.0" classifier "assets"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.8.0</version>
</dependency>
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.8.0</version>
    <classifier>assets</classifier>
</dependency>
```
