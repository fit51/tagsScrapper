# StackOverflow Tags Scrapper

Shows StackOverflow tags statistics

## Build

```
sbt assembly
```

## Run
Run the fat-jar with config path parameter



## TypeSafe Config

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
    credentials {
      user: "admin"
      password: "password"
    }
  }
}
```