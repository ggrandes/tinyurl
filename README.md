# TinyURL

TinyURL is a Simple URL Shortener build on Java. Project is Open source (Apache License, Version 2.0) 

### Current Stable Version is [1.0.0](https://maven-release.s3.amazonaws.com/release/org/javastack/tinyurl/1.0.0/tinyurl-1.0.0.war)

---

## DOC

#### Config File

    # SystemProperty / Default value
    org.javastack.tinyurl.config=[classpath]/org.javastack.tinyurl.properties

#### Config Parameters

    # Parameter / Default value
	storage.dir=[webapp]/WEB-INF/storage/
	whitelist.file=file://[storage.dir]/whitelist.conf
	check.flags=WHITELIST,SURBL,CONNECTION
	connection.timeout.millis=10000
	read.timeout.millis=30000

* **storage.dir**: Where the files are stored.
* **whitelist.file**: Where the whitelist file are stored.
* **check.flags**: That checks are made against URLs.
    * WHITELIST: Check URL domain against whitelist file, if not found, shortener will be denied.
    * SURBL: Check URL domain against SURBL service, if found, shortener will be denied.
    * CONNECTION: Check URL host with a HTTP connection (HEAD). 
* **connection.timeout.millis**: Connection timeout in millis.
* **read.timeout.millis**: Read timeout in millis.

###### More examples

* More examples in [sampleconf directory](https://github.com/ggrandes/tinyurl/tree/master/sampleconf/)

## Running (Tomcat)

* Set `CATALINA_OPTS="-Dorg.javastack.tinyurl.config=file://${CATALINA_BASE}/conf/org.javastack.tinyurl.properties"` in tomcat/bin/setenv.sh (linux)
* Copy war file inside webapps directory with name: ROOT.war

---

## TODOs

* MySQL backed for storage

## MISC
Current harcoded values:

* Default config file (searched in classpath): org.javastack.tinyurl.properties
* Default checks for URLs are: WHITELIST,SURBL,CONNECTION
* Default Connection Timeout (millis): 10000
* Default Read Timeout (millis): 30000
* Algorithm for generate Keys from URL: MD5
* The `KEY_SPACE` (defined in Constants.java; base64 is 6^KEY_SPACE = 46.656 keys) is: 6 characters 
* Backend for storage is: [KVStore](https://github.com/ggrandes/kvstore/) (portable)


---
Inspired in [goo.gl](https://goo.gl/), [bit.ly](https://bitly.com/) and [cort.as](http://cortas.elpais.com/), this code is Java-minimalistic version.
