# Using this library

Import this library as a maven dependency, or include it directly in your docker project.

Remember to add these packages to your spring boot components:

```
@ComponentScan({"com.biit.kafka.plugins", "..."})
@ConfigurationPropertiesScan({"com.biit.kafka.plugins", "..."})
```

If needed, add the kafka configuration settings:

```
#Kafka
spring.kafka.enabled=true
spring.kafka.topic=form
spring.kafka.send.topic=processedForm
spring.kafka.client.id=
spring.kafka.group.id=
spring.kafka.bootstrap-servers=PLAINTEXT://testing.biit-solutions.com:29092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=com.biit.kafka.events.EventSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=com.biit.kafka.events.EventDeserializer
kafka.encryption.key=
```