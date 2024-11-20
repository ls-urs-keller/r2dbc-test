# RDS read/write replicas transaction issue with different drivers

We had issues with transaction on RDS in distributed dbs when going through a read replica with write forwards enabled..

## Findings:
Both miku and asyncer issue the statement 
```
START TRANSACTION READ WRITE
````
if the transaction is read-write.

Mariadb issues only
```
START TRANSACTION
```
for read-write transactions.

There seems to be a bug in AWS RDS distributed Mysql with read forwarding if you start a transaction
with `START TRANSACTION READ WRITE` from a read replica. It will complain even with write forwards enabled. 
Using `START TRANSACTION` works fine.

E.g. this works:
```
START TRANSACTION; insert into test values (1, 2); COMMIT;
``` 
This doesn't
```
START TRANSACTION READ WRITE; insert into test values (1, 2); COMMIT;
```

The error is
```
ERROR 1290 (HY000): The MySQL server is running with the --read-only option so it cannot execute this statement
```

## Code use to reproduce the issue
This repo contains a bit of code to reproduce the problem, here are the steps:
- Create an RDS with a read and write replica, the read replica needs write forwarding enabled
- Create a table test `create table test (driver varchar(255), tx varchar(255));`
- Set the envs HOST, PORT, DB, USER, PASSWORD in your run config 
- run the class R2dbcWriteTest

The output:
```
dev.miku.r2dbc.mysql.MySqlConnectionFactory
IsolationLevel{sql='READ COMMITTED'}
IsolationLevel{sql='READ UNCOMMITTED'}
IsolationLevel{sql='REPEATABLE READ'}
IsolationLevel{sql='SERIALIZABLE'}
rw-IsolationLevel{sql='READ COMMITTED'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='READ UNCOMMITTED'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='REPEATABLE READ'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='SERIALIZABLE'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
ro-IsolationLevel{sql='READ COMMITTED'}
ro-IsolationLevel{sql='READ UNCOMMITTED'}
ro-IsolationLevel{sql='REPEATABLE READ'}
ro-IsolationLevel{sql='SERIALIZABLE'}

org.mariadb.r2dbc.MariadbConnectionFactory
IsolationLevel{sql='READ COMMITTED'}
IsolationLevel{sql='READ UNCOMMITTED'}
IsolationLevel{sql='REPEATABLE READ'}
IsolationLevel{sql='SERIALIZABLE'}
rw-IsolationLevel{sql='READ COMMITTED'}
rw-IsolationLevel{sql='READ UNCOMMITTED'}
rw-IsolationLevel{sql='REPEATABLE READ'}
rw-IsolationLevel{sql='SERIALIZABLE'}
ro-IsolationLevel{sql='READ COMMITTED'}
14:06:19.595 [reactor-tcp-nio-7] WARN  o.m.r2dbc.message.server.ErrorPacket -- Error: 'The MySQL server is running with the --read-only option so it cannot execute this statement' sqlState='HY000' code=1290
ro-IsolationLevel{sql='READ UNCOMMITTED'}
14:06:20.676 [reactor-tcp-nio-8] WARN  o.m.r2dbc.message.server.ErrorPacket -- Error: 'The MySQL server is running with the --read-only option so it cannot execute this statement' sqlState='HY000' code=1290
ro-IsolationLevel{sql='REPEATABLE READ'}
14:06:21.585 [reactor-tcp-nio-1] WARN  o.m.r2dbc.message.server.ErrorPacket -- Error: 'The MySQL server is running with the --read-only option so it cannot execute this statement' sqlState='HY000' code=1290
ro-IsolationLevel{sql='SERIALIZABLE'}
14:06:22.615 [reactor-tcp-nio-2] WARN  o.m.r2dbc.message.server.ErrorPacket -- Error: 'The MySQL server is running with the --read-only option so it cannot execute this statement' sqlState='HY000' code=1290

io.asyncer.r2dbc.mysql.MySqlConnectionFactory
IsolationLevel{sql='READ COMMITTED'}
IsolationLevel{sql='READ UNCOMMITTED'}
IsolationLevel{sql='REPEATABLE READ'}
IsolationLevel{sql='SERIALIZABLE'}
rw-IsolationLevel{sql='READ COMMITTED'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='READ UNCOMMITTED'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='REPEATABLE READ'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
rw-IsolationLevel{sql='SERIALIZABLE'}
ERROR: The MySQL server is running with the --read-only option so it cannot execute this statement
ro-IsolationLevel{sql='READ COMMITTED'}
ro-IsolationLevel{sql='READ UNCOMMITTED'}
ro-IsolationLevel{sql='REPEATABLE READ'}
ro-IsolationLevel{sql='SERIALIZABLE'}
```
  
The content in the DB:
```
> select * from test;
+-----------------------------------------------+-------------------------------------------+
| id                                            | value                                     |
+-----------------------------------------------+-------------------------------------------+
| dev.miku.r2dbc.mysql.MySqlConnectionFactory   | IsolationLevel{sql='READ COMMITTED'}      |
| dev.miku.r2dbc.mysql.MySqlConnectionFactory   | IsolationLevel{sql='READ UNCOMMITTED'}    |
| dev.miku.r2dbc.mysql.MySqlConnectionFactory   | IsolationLevel{sql='REPEATABLE READ'}     |
| dev.miku.r2dbc.mysql.MySqlConnectionFactory   | IsolationLevel{sql='SERIALIZABLE'}        |
| org.mariadb.r2dbc.MariadbConnectionFactory    | IsolationLevel{sql='READ COMMITTED'}      |
| org.mariadb.r2dbc.MariadbConnectionFactory    | IsolationLevel{sql='READ UNCOMMITTED'}    |
| org.mariadb.r2dbc.MariadbConnectionFactory    | IsolationLevel{sql='REPEATABLE READ'}     |
| org.mariadb.r2dbc.MariadbConnectionFactory    | IsolationLevel{sql='SERIALIZABLE'}        |
| org.mariadb.r2dbc.MariadbConnectionFactory    | rw-IsolationLevel{sql='READ COMMITTED'}   |
| org.mariadb.r2dbc.MariadbConnectionFactory    | rw-IsolationLevel{sql='READ UNCOMMITTED'} |
| org.mariadb.r2dbc.MariadbConnectionFactory    | rw-IsolationLevel{sql='REPEATABLE READ'}  |
| org.mariadb.r2dbc.MariadbConnectionFactory    | rw-IsolationLevel{sql='SERIALIZABLE'}     |
| io.asyncer.r2dbc.mysql.MySqlConnectionFactory | IsolationLevel{sql='READ COMMITTED'}      |
| io.asyncer.r2dbc.mysql.MySqlConnectionFactory | IsolationLevel{sql='READ UNCOMMITTED'}    |
| io.asyncer.r2dbc.mysql.MySqlConnectionFactory | IsolationLevel{sql='REPEATABLE READ'}     |
| io.asyncer.r2dbc.mysql.MySqlConnectionFactory | IsolationLevel{sql='SERIALIZABLE'}        |
 +-----------------------------------------------+-------------------------------------------+
 16 rows in set (0.106 sec)

```

In wireshark we can see the following:

For RW=null, miku issues `BEGIN ... COMMIT`
![miku no rw.png](images/miku%20no%20rw.png)

For RW=true, miku issues `START TRANSACTION READ WRITE`
![miku with rw.png](images/miku%20with%20rw.png)


For RW=null, mariadb issues `START TRANSACTION`
![mariadb no rw.png](images/mariadb%20no%20rw.png)
For RW=true, mariadb issues `START TRANSACTION`
![mariadb with rw.png](images/mariadb%20with%20rw.png)