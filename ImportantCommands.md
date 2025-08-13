## redis ip check 
```
hostname -I
```

## Redis start command in wsl
```
sudo service redis-server start
```

## Check if the redis is running
```
sudo service redis-server status
```

## Commands to run kafka
- Run the Zookeper first
- note that to open the terminal with admin mode or has necessary permissions
## For windows 
```
cd \Kafka\kafka_2.12-3.9.1
```
```
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
```

- Then run kafka in another terminal
```aiignore
cd \Kafka\kafka_2.12-3.9.1
```
```
.\bin\windows\kafka-server-start.bat .\config\server.properties
```

## For Linux/wsl
- replace with your actual path
```aiignore
cd /mnt/e/Kafka/kafka_2.12-3.9.1
```

- run zookeper
```aiignore
bin/zookeeper-server-start.sh config/zookeeper.properties
```

- run kafka in another terminal with same dir
```aiignore
bin/kafka-server-start.sh config/server.properties
```

- Forward the port, run this command in windows cmd as admin if using wsl
```aiignore
netsh interface portproxy add v4tov4 listenaddress=127.0.0.1 listenport=9092 connectaddress=172.31.240.114 connectport=9092
```