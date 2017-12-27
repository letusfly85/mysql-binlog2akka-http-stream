# MySQL BinLog to Akka Http Stream Example

## Kick MySQL container with BinLog mode

```sh
docker run --name example_v2 \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=example \
  -v $HOME/work/docker/mysql/example:/var/lib/mysql \
  -p 3306:3306 \
  -d mysql \
  mysqld \
  --datadir=/var/lib/mysql \
  --user=mysql \
  --server-id=1 \
  --log-bin=/var/log/mysql/mysql-bin.log \
  --binlog_do_db=example \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

## Access to stream

```sh
curl -XGET localhost:8082/v1/streams
```