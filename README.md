Simple relations management system in Scala and Akka.
## Overview

This sample application implements a CQRS-ES design that will side-effect in the read model on selected events persisted to Cassandra by the write model.

## Write model
The implementation is based on a sharded actor:  'Business' and 'Connection' are [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) entities.

Events from the 'Business' and 'Connection' are tagged and consumed by the read model.

Please note there is no 'Person' entity. Instead, 'Connection' has been used. If we use 'People' as main entity, we can face following issues :
1. When person A and B become friends, this operation is a multi-step process which requires transaction of some sort. Akka can not do multi-actor transaction easily. It needs saga, which is complicated to implement. 

## Read model

Since this is sample application, the read side is a cluster singleton which stores the state in-memory.A more practical example would be to send a message to a Kafka topic or update a relational database.


## Running the sample code

1. Start a Cassandra server by running:

```
sbt "runMain com.rp.rms.Main cassandra"
```

2. Start a node that runs the write model:

```
sbt -Dakka.cluster.roles.0=write-model "runMain com.rp.rms.Main 2551"
```

3. Start a node that runs the read model:

```
sbt -Dakka.cluster.roles.0=read-model "runMain com.rp.rms.Main 2552"
```

4. More write or read nodes can be started started by defining roles and port:

```
sbt -Dakka.cluster.roles.0=write-model "runMain com.rp.rms.Main 2553"
sbt -Dakka.cluster.roles.0=read-model "runMain com.rp.rms.Main 2554"
```

Try it with curl

```
# Alex and Julia are friends
curl -X POST http://127.0.0.1:8051/connections/Alex/friends/Julia

# Julia and Bob are related
curl -X POST http://127.0.0.1:8051/connections/Bob/relatives/Julia

# Bob works for IBM
curl -X POST http://127.0.0.1:8051/businesses/IBM/employees/Bob

# Relatives of Bob
curl -X GET http://127.0.0.1:8051/persons/Bob/relatives

# Relatives of Bob
curl -X GET http://127.0.0.1:8051/persons/Bob/relatives

# Relatives of persons working at IBM
curl -X GET http://127.0.0.1:8051/businesses/IBM/employees/relatives

# All businesses with more than 0 employees
curl -X GET http://127.0.0.1:8051/businesses?strengthGreaterThan=0

# All persons who have friends with employed relatives
curl -X GET http://127.0.0.1:8051/businesses/employees/relatives/friends

```

or same `curl` commands to port 8052.
