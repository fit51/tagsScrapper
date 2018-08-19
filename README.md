# StackOverflow Tags Scrapper

Shows StackOverflow tags statistics

## Build

```
sbt assembly
```

## Run
Run the fat-jar and specify config path

```
java -Dconfig.file=application.conf -jar tags-scrapper-0.1.jar
```

### Config Example Setup

```
server {
  host = localhost
  port = 8080
}
scrapper {
  queue: 10000
  //Controls max HttpConnections established to one host at once
  maxConnections: 10
  //Controlls max parallel HttpRequests
  maxParallelism: 10
  //Proxy setting, are optional
  proxy {
    host = "99.99.99.99"
    port = 8080
    //Credentials are optional
    credentials {
      user: "admin"
      password: "password"
    }
  }
}
```

## Call Http Search Method

http://localhost:8080/search?tag=scala?tag=clojure