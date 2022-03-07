# TinyURL

TinyURL is a Simple URL Shortener build on Java. Project is Open source (Apache License, Version 2.0) 

### Current Stable Version is [1.0.6](https://maven-release.s3.amazonaws.com/release/org/javastack/tinyurl/1.0.6/tinyurl-1.0.6.war)

---

## DOC

#### Config File

    # SystemProperty / Default value
    org.javastack.tinyurl.config=[classpath]/org.javastack.tinyurl.properties

###### Note: If file isn't in classpath, use URL format like ```file:///etc/tinyurl/org.javastack.tinyurl.properties``` or ```https://config-server/tinyurl/org.javastack.tinyurl.properties```

#### Config Parameters

    # Parameter / Default value
    storage.dir=[webapp]/WEB-INF/storage/
    whitelist.file=file://[storage.dir]/whitelist.conf
    check.flags=WHITELIST,CONNECTION
    check.cache.millis=60000
    connection.timeout.millis=10000
    read.timeout.millis=30000
    dump.key=[random]
    #
    # Default KVStore Persistence
    storage.class=org.javastack.tinyurl.PersistentKVStore
    #
    # Optional MySQL Persistence (default: not enabled)
    #storage.class=org.javastack.tinyurl.jdbc.PersistentMySQL
    #storage.url=jdbc:mysql://localhost:3306/tinyurl
    #storage.username=tinyurl
    #storage.password=secret
    #
    # QR Codes
    #base.url=https://tiny.javastack.org/r/
    #qr.size.min=50
    #qr.size.max=1000
    #qr.size.default=300

* **storage.dir**: Where the local files are stored.
* **whitelist.file**: Where the whitelist file are stored.
* **check.flags**: That checks are made against URLs.
    * WHITELIST: Check URL domain against whitelist file, if not found, shortener will be denied.
    * SURBL: Check URL domain against SURBL service, if found, shortener will be denied.
    * CONNECTION: Check URL with a HTTP connection (GET). 
* **check.cache.millis**: Cache time for URL domain checks (WhiteList / SURBL).
* **connection.timeout.millis**: Connection timeout in millis.
* **read.timeout.millis**: Read timeout in millis.
* **dump.key**: Dump Key for export all storage in CSV.
* **storage.class**: Class used for persistence:
    * `org.javastack.tinyurl.PersistentKVStore`: KVStore persistence (default, portable)
    * `org.javastack.tinyurl.jdbc.PersistentMySQL`: MySQL persistence
        * **storage.url**: URL for jdbc connection
        * **storage.username**: username
        * **storage.password**: password
        * **storage.XXX**: see extra [parameters](https://tomcat.apache.org/tomcat-7.0-doc/jdbc-pool.html#Common_Attributes), all prefixed with **storage.**
* **base.url**: Base URL of TinyURL redirector (by default try to discover from request) `example: https://tiny.javastack.org/r/`
* **qr.size.min**: Min size allowed in QR (pixels, square), default=50
* **qr.size.max**: Max size allowed in QR (pixels, square), default=1000
* **qr.size.default**: Default size in QR (pixels, square), default=300


###### More examples

* More examples in [sampleconf directory](https://github.com/ggrandes/tinyurl/tree/master/sampleconf/)

## Running (Tomcat)

* Set `CATALINA_OPTS="-Dorg.javastack.tinyurl.config=file://${CATALINA_BASE}/conf/org.javastack.tinyurl.properties"` in tomcat/bin/setenv.sh (linux)
* Copy war file inside webapps directory with name: ROOT.war
* For MySQL persistence: 
    * Copy [mysql-connector-java-X.X.XX.jar](http://search.maven.org/#search|gav|1|g%3A"mysql"%20AND%20a%3A"mysql-connector-java") in tomcat/lib/
    * Create MySQL user With: `GRANT ALL ON tinyurl.* TO 'tinyurl'@'%' IDENTIFIED BY 'secret';`

## API Usage

The API for shortening is very simple:

#### To shorten a URL:

    # Method: POST
    # Path: /tiny
    # Content-Type: application/x-www-form-urlencoded
    # Parameter: "url=${longURLencoded}"
    # Example: curl -i -d "url=https%3A%2F%2Fgithub.com%2Fggrandes%2Ftinyurl%2F" ${BASE_URL}/tiny

Return something like this:

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=ISO-8859-1
    Content-Length: 19
    Cache-control: must-revalidate, max-age=0
    
    { "id": "iN8diz" }

#### To retrieve the QR image of short URL:

    # Method: GET
    # Path: /q/{id}
    # Example: curl -i ${BASE_URL}/q/iN8diz

Return something like this:

    HTTP/1.1 200 OK
    Cache-Control: public
    Content-Type: image/png
    Content-Length: 475
    
    .PNG...binary-data...

#### To retrieve the original/long URL:

    # Method: GET
    # Path: /r/{id}
    # Example: curl -i ${BASE_URL}/r/iN8diz

Return something like this:

    HTTP/1.1 302 Found
    Location: https://github.com/ggrandes/tinyurl/
    Content-Length: 0
    Cache-control: must-revalidate, max-age=0


---

## MISC
Current hardcoded values:

* Default config file (searched in classpath): org.javastack.tinyurl.properties
* Default checks for URLs are: WHITELIST,CONNECTION
* Default checks cache (millis): 60000
* Default Connection Timeout (millis): 10000
* Default Read Timeout (millis): 30000
* Algorithm for generate Keys from URL: MD5
* The `KEY_SPACE` is: 6 characters (base64 is 64^KS(6) = 68.719.476.736 keys max)
* Default Backend for storage is: [KVStore](https://github.com/ggrandes/kvstore/) (portable)


---
Inspired in [goo.gl](https://goo.gl/), [bit.ly](https://bitly.com/) and [cort.as](http://cortas.elpais.com/), this code is Java-minimalistic version.
